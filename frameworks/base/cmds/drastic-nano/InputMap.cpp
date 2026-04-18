/*
 * Copyright (C) 2026 GammaOS
 */

#define LOG_TAG "DrasticNano.Input"

#include "InputMap.h"

#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <linux/input.h>
#include <string.h>
#include <sys/ioctl.h>
#include <unistd.h>

#include <cutils/properties.h>
#include <utils/Log.h>
#include <utils/SystemClock.h>

#include "DrasticRunner.h"

namespace android {
namespace drastic_input {

namespace {

constexpr const char* kTouchDeviceName = "gt9xx-0";

// Touchscreen routes to drastic-nano's bottom-screen display. On this
// family of dual-DSI handhelds persist.gif.map.gt9xx_0=0 ties gt9xx-0
// to the DSI-1 panel (physical port 0) which is the one drastic-nano
// routes the bottom DS screen to. See main.cpp history.

void calibrateAxisIfPresent(int fd, int absCode, InputState::Axis* a) {
    struct input_absinfo abs = {};
    if (ioctl(fd, EVIOCGABS(absCode), &abs) >= 0) {
        a->min = abs.minimum;
        a->max = abs.maximum;
        // raw is initialized to 0 by default; seed it with the centered
        // value when the axis looks like a signed stick (min < 0 < max).
        if (abs.value != 0 || abs.minimum != 0 || abs.maximum != 0) {
            a->seen = true;
        }
        a->raw = abs.value;
    }
}

// DS button mask for the drastic action indices that cleanly map to
// bits in the kDsBtn* mask forwarded to updateInput. Slot numbers
// match drastic's internal action enum (see DrasticPrefs.h).
int actionIndexToDsBit(int action) {
    switch (action) {
    case 0:  return DrasticRunner::kDsBtnX;
    case 1:  return DrasticRunner::kDsBtnY;
    case 2:  return DrasticRunner::kDsBtnB;
    case 3:  return DrasticRunner::kDsBtnA;
    case 4:  return DrasticRunner::kDsBtnR;
    case 5:  return DrasticRunner::kDsBtnL;
    case 6:  return DrasticRunner::kDsBtnStart;
    case 7:  return DrasticRunner::kDsBtnSelect;
    case 12: return DrasticRunner::kDsBtnUp;
    case 13: return DrasticRunner::kDsBtnRight;
    case 14: return DrasticRunner::kDsBtnDown;
    case 15: return DrasticRunner::kDsBtnLeft;
    default: return 0;
    }
}

// Convert a raw axis to a signed normalized value in [-1, 1] with
// deadzone applied. Returns 0 when the axis has not been seen.
float normAxis(const InputState::Axis& a, float deadzone) {
    if (!a.seen || a.max == a.min) return 0.0f;
    float range = (float)(a.max - a.min);
    float n = ((float)(a.raw - a.min) / range) * 2.0f - 1.0f;
    if (n > 1.0f) n = 1.0f; else if (n < -1.0f) n = -1.0f;
    float an = n < 0 ? -n : n;
    if (an < deadzone) return 0.0f;
    float scaled = (an - deadzone) / (1.0f - deadzone);
    return n < 0 ? -scaled : scaled;
}

} // anonymous namespace

void scanInputDevices(InputState* st) {
    st->touchFd = -1;
    st->touchPanelW = 0;
    st->touchPanelH = 0;
    DIR* d = opendir("/dev/input");
    if (!d) return;
    struct dirent* e;
    while ((e = readdir(d)) != nullptr) {
        if (strncmp(e->d_name, "event", 5) != 0) continue;
        std::string p = std::string("/dev/input/") + e->d_name;
        int fd = open(p.c_str(), O_RDONLY | O_NONBLOCK | O_CLOEXEC);
        if (fd < 0) continue;
        char name[64] = {};
        ioctl(fd, EVIOCGNAME(sizeof(name) - 1), name);
        if (strcmp(name, kTouchDeviceName) == 0) {
            struct input_absinfo abs = {};
            if (ioctl(fd, EVIOCGABS(ABS_MT_POSITION_X), &abs) >= 0) {
                st->touchPanelW = abs.maximum > 0 ? abs.maximum : 1;
            }
            if (ioctl(fd, EVIOCGABS(ABS_MT_POSITION_Y), &abs) >= 0) {
                st->touchPanelH = abs.maximum > 0 ? abs.maximum : 1;
            }
            st->touchFd = fd;
            ALOGI("DrasticNano::input: touch %s %s (panel %dx%d)",
                  name, p.c_str(), st->touchPanelW, st->touchPanelH);
            continue;
        }
        unsigned long keys[(KEY_MAX + 8 * sizeof(long)) /
                            (8 * sizeof(long))] = {};
        if (ioctl(fd, EVIOCGBIT(EV_KEY, sizeof(keys)), keys) >= 0) {
            auto has = [&](int code) {
                return (keys[code / (8 * sizeof(long))] >>
                        (code % (8 * sizeof(long)))) & 1;
            };
            if (has(BTN_SOUTH) || has(BTN_A) || has(KEY_BACK) ||
                has(KEY_UP) || has(KEY_VOLUMEUP)) {
                st->fds.push_back(fd);
                // Read axis calibration for any sticks / triggers on
                // this device. Missing axes leave the struct at
                // defaults (seen=false).
                calibrateAxisIfPresent(fd, ABS_X,  &st->axLX);
                calibrateAxisIfPresent(fd, ABS_Y,  &st->axLY);
                calibrateAxisIfPresent(fd, ABS_RX, &st->axRX);
                calibrateAxisIfPresent(fd, ABS_RY, &st->axRY);
                calibrateAxisIfPresent(fd, ABS_Z,  &st->axLZ);
                calibrateAxisIfPresent(fd, ABS_RZ, &st->axRZ);
                ALOGI("DrasticNano::input: gamepad %s %s "
                      "(LX %d..%d LY %d..%d RX %d..%d RY %d..%d "
                      "Z %d..%d RZ %d..%d)",
                      name, p.c_str(),
                      st->axLX.min, st->axLX.max, st->axLY.min, st->axLY.max,
                      st->axRX.min, st->axRX.max, st->axRY.min, st->axRY.max,
                      st->axLZ.min, st->axLZ.max, st->axRZ.min, st->axRZ.max);
                continue;
            }
        }
        close(fd);
    }
    closedir(d);
}

void recalibrateAxes(InputState* st) {
    for (int fd : st->fds) {
        calibrateAxisIfPresent(fd, ABS_X,  &st->axLX);
        calibrateAxisIfPresent(fd, ABS_Y,  &st->axLY);
        calibrateAxisIfPresent(fd, ABS_RX, &st->axRX);
        calibrateAxisIfPresent(fd, ABS_RY, &st->axRY);
        calibrateAxisIfPresent(fd, ABS_Z,  &st->axLZ);
        calibrateAxisIfPresent(fd, ABS_RZ, &st->axRZ);
    }
}

void applyPrefs(InputState* st, const drastic_prefs::Prefs& p) {
    for (int a = 0; a < drastic_prefs::kNumActions; a++) {
        st->keymapPlayer0[a] = p.keymap[0][a];
    }
    st->keycodeToAction.clear();
    for (int a = 0; a < drastic_prefs::kNumActions; a++) {
        int kc = st->keymapPlayer0[a];
        if (kc > 0) st->keycodeToAction[kc] = a;
    }
    st->analogTouchEnabled = p.analogTouch;
    st->analogDeadzone = p.analogDeadzone;
    ALOGI("DrasticNano::input: applied prefs (keymap=%zu entries, "
          "analogTouch=%d deadzone=%.3f)",
          st->keycodeToAction.size(), p.analogTouch ? 1 : 0,
          p.analogDeadzone);
}

void closeInputDevices(InputState* st) {
    for (int fd : st->fds) close(fd);
    st->fds.clear();
    if (st->touchFd >= 0) {
        close(st->touchFd);
        st->touchFd = -1;
    }
}

namespace {

// Drain all queued touchscreen events and update touchHeld + touchDs*.
void drainTouch(InputState* st) {
    if (st->touchFd < 0) return;
    struct input_event ev;
    while (read(st->touchFd, &ev, sizeof(ev)) == sizeof(ev)) {
        if (ev.type == EV_KEY && ev.code == BTN_TOUCH) {
            st->touchHeld = (ev.value != 0);
        } else if (ev.type == EV_ABS) {
            if (ev.code == ABS_MT_POSITION_X) {
                st->touchPendingX = ev.value;
                st->touchPendingValid = true;
            } else if (ev.code == ABS_MT_POSITION_Y) {
                st->touchPendingY = ev.value;
                st->touchPendingValid = true;
            } else if (ev.code == ABS_MT_TRACKING_ID && ev.value == -1) {
                st->touchHeld = false;
            }
        } else if (ev.type == EV_SYN && ev.code == SYN_REPORT) {
            if (st->touchPendingValid && st->touchPanelW > 0 &&
                st->touchPanelH > 0) {
                int x = st->touchPendingX;
                int y = st->touchPendingY;
                if (x < 0) x = 0; if (y < 0) y = 0;
                if (x > st->touchPanelW) x = st->touchPanelW;
                if (y > st->touchPanelH) y = st->touchPanelH;
                st->touchDsX = x * 256 / st->touchPanelW;
                st->touchDsY = y * 192 / st->touchPanelH;
                if (st->touchDsX > 255) st->touchDsX = 255;
                if (st->touchDsY > 191) st->touchDsY = 191;
                st->touchPendingValid = false;
            }
        }
    }
}

// Derive DS D-Pad bits from the left analog stick position. Allows
// 8-way input (simultaneous horizontal + vertical when the stick is
// in a diagonal). Always layered on top of the real DPad / hat bits --
// if analog-touch mode is also on, the stick additionally drives the
// stylus; games that don't use the touchscreen just see the extra
// DPad input, games that only use the touchscreen ignore the DPad.
int stickDpadBits(const InputState* st) {
    float nx = normAxis(st->axLX, st->analogDeadzone);
    float ny = normAxis(st->axLY, st->analogDeadzone);
    constexpr float kDpadThresh = 0.4f; // 40% past deadzone
    int bits = 0;
    if (ny < -kDpadThresh) bits |= DrasticRunner::kDsBtnUp;
    if (ny >  kDpadThresh) bits |= DrasticRunner::kDsBtnDown;
    if (nx < -kDpadThresh) bits |= DrasticRunner::kDsBtnLeft;
    if (nx >  kDpadThresh) bits |= DrasticRunner::kDsBtnRight;
    return bits;
}

// Apply the LS -> stylus remap when analog touch is enabled. Called
// after gamepad axes are drained for the frame. Mutates st->touchDs*
// / st->touchHeld when the stick is outside the deadzone. The hard-
// touch path (gt9xx-0) always wins if the panel is being touched.
void applyAnalogStylus(InputState* st) {
    if (!st->analogTouchEnabled) return;
    if (st->touchHeld) return; // real touch wins
    float nx = normAxis(st->axLX, st->analogDeadzone);
    float ny = normAxis(st->axLY, st->analogDeadzone);
    if (nx == 0.0f && ny == 0.0f) {
        // Stick at rest: release virtual stylus but keep last coords
        // for drastic (so idle stylus sits on the bottom screen, not
        // at 0,0).
        return;
    }
    // Map normalized [-1, 1] to DS (0..255, 0..191), centered.
    int dx = (int)((nx + 1.0f) * 0.5f * 255.0f);
    int dy = (int)((ny + 1.0f) * 0.5f * 191.0f);
    if (dx < 0) dx = 0; if (dx > 255) dx = 255;
    if (dy < 0) dy = 0; if (dy > 191) dy = 191;
    st->touchDsX = dx;
    st->touchDsY = dy;
    st->touchHeld = true;
}

// Map digital press/release of an Android keycode into nav/action
// flags for this frame. Returns true if handled so the caller can
// skip the default DS-button path.
bool dispatchNav(int androidKc, bool pressed, InputActions* out) {
    if (!pressed) return false;
    switch (androidKc) {
    case 19:  out->navUp = true; return true;      // KEYCODE_DPAD_UP
    case 20:  out->navDown = true; return true;    // KEYCODE_DPAD_DOWN
    case 21:  out->navLeft = true; return true;    // KEYCODE_DPAD_LEFT
    case 22:  out->navRight = true; return true;   // KEYCODE_DPAD_RIGHT
    case 96:  out->navAccept = true; return true;  // BUTTON_A
    case 97:  out->navCancel = true; return true;  // BUTTON_B
    case 102: out->navPrevTab = true; return true; // BUTTON_L1
    case 103: out->navNextTab = true; return true; // BUTTON_R1
    default:  return false;
    }
}

} // anonymous namespace

void pollInputMap(InputState* st, bool overlayOpen, bool captureKey,
                  int64_t shortBackMs, int64_t longBackMs,
                  InputActions* out) {
    *out = {};

    // ---- Drain gamepad event devices ----
    for (int fd : st->fds) {
        struct input_event ev;
        while (read(fd, &ev, sizeof(ev)) == sizeof(ev)) {
            if (ev.type == EV_KEY) {
                const bool pressed = (ev.value != 0);

                if (ev.code == KEY_BACK) {
                    // Short / long press state tracked via backPressStartMs.
                    if (pressed && !st->backWasDown) {
                        st->backPressStartMs = android::elapsedRealtime();
                    }
                    if (!pressed && st->backWasDown) {
                        int64_t held = android::elapsedRealtime() -
                                       st->backPressStartMs;
                        if (held < shortBackMs) {
                            out->menuToggle = true;
                        }
                        st->backPressStartMs = 0;
                    }
                    st->backWasDown = pressed;
                    continue;
                }

                // Capture-key mode: expose the keycode to the overlay
                // for rebind.
                int androidKc = drastic_prefs::evdevToAndroidKeycode(
                        ev.code);
                if (pressed && captureKey && androidKc > 0 &&
                    out->capturedAndroidKc == 0) {
                    out->capturedAndroidKc = androidKc;
                    // Swallow this press so the overlay doesn't also
                    // treat it as a nav/action event.
                    continue;
                }

                // Update digital trigger / thumb state for axis-less
                // pads.
                if (ev.code == BTN_TL2) st->btnL2 = pressed;
                if (ev.code == BTN_TR2) st->btnR2 = pressed;
                if (ev.code == BTN_THUMBL) st->btnL3 = pressed;
                if (ev.code == BTN_THUMBR) st->btnR3 = pressed;

                if (overlayOpen) {
                    // Route keys to overlay nav. Dispatch edge events
                    // only on press.
                    dispatchNav(androidKc, pressed, out);
                    continue;
                }

                // Gameplay routing: prefer the user's keymap; fall
                // back to evdev-hardcoded defaults.
                int action = -1;
                auto it = st->keycodeToAction.find(androidKc);
                if (it != st->keycodeToAction.end()) action = it->second;

                if (action < 0) {
                    // Legacy hardcoded mapping for pads that have no
                    // user remap set yet. Matches the pre-overlay
                    // behavior.
                    auto bit = [&](int mask) {
                        if (pressed) st->dsBtnMask |=  mask;
                        else         st->dsBtnMask &= ~mask;
                    };
                    switch (ev.code) {
                    case BTN_SOUTH:  bit(DrasticRunner::kDsBtnA);     break;
                    case BTN_EAST:   bit(DrasticRunner::kDsBtnB);     break;
                    case BTN_NORTH:  bit(DrasticRunner::kDsBtnX);     break;
                    case BTN_WEST:   bit(DrasticRunner::kDsBtnY);     break;
                    case BTN_TL:
                    case KEY_L:      bit(DrasticRunner::kDsBtnL);     break;
                    case BTN_TR:
                    case KEY_R:      bit(DrasticRunner::kDsBtnR);     break;
                    case BTN_START:  bit(DrasticRunner::kDsBtnStart); break;
                    case BTN_SELECT: bit(DrasticRunner::kDsBtnSelect); break;
                    // Some pads emit KEY_UP/DOWN/LEFT/RIGHT, Xbox-style
                    // pads emit BTN_DPAD_* instead. Handle both.
                    case KEY_UP:
                    case BTN_DPAD_UP:    bit(DrasticRunner::kDsBtnUp);    break;
                    case KEY_DOWN:
                    case BTN_DPAD_DOWN:  bit(DrasticRunner::kDsBtnDown);  break;
                    case KEY_LEFT:
                    case BTN_DPAD_LEFT:  bit(DrasticRunner::kDsBtnLeft);  break;
                    case KEY_RIGHT:
                    case BTN_DPAD_RIGHT: bit(DrasticRunner::kDsBtnRight); break;
                    default: break;
                    }
                } else {
                    // User-remapped action. Convert to DS bit or to
                    // a special action.
                    int dsBit = actionIndexToDsBit(action);
                    if (dsBit) {
                        if (pressed) st->dsBtnMask |=  dsBit;
                        else         st->dsBtnMask &= ~dsBit;
                    } else {
                        switch (action) {
                        // Fast-forward and stylus-touch track press+
                        // release so the lever / touch stays active
                        // while the button is held.
                        case 17: st->btnFastFwd   = pressed; break;
                        case 28: st->stylusBtnHeld = pressed; break;
                        // Edge-triggered (fire once on press only).
                        case 16: if (pressed) out->actSwapScreens = true; break;
                        case 20: if (pressed) out->menuToggle     = true; break;
                        default: break;
                        }
                    }
                }
            } else if (ev.type == EV_ABS) {
                switch (ev.code) {
                case ABS_HAT0X: {
                    // Clear both horizontal dpad bits, then set based
                    // on sign.
                    st->dsBtnMask &= ~(DrasticRunner::kDsBtnLeft |
                                        DrasticRunner::kDsBtnRight);
                    if (ev.value < 0) st->dsBtnMask |= DrasticRunner::kDsBtnLeft;
                    if (ev.value > 0) st->dsBtnMask |= DrasticRunner::kDsBtnRight;
                    // Debounce: only emit a nav edge when the hat
                    // crosses from neutral/opposite into this
                    // direction. Without this, holding the hat sends
                    // a nav event every EV_ABS the kernel posts
                    // (typically one per poll but some pads spam).
                    if (overlayOpen && ev.value != 0 &&
                            ev.value != st->hat0xPrev) {
                        if (ev.value < 0) out->navLeft = true;
                        else              out->navRight = true;
                    }
                    st->hat0xPrev = ev.value;
                    break;
                }
                case ABS_HAT0Y: {
                    st->dsBtnMask &= ~(DrasticRunner::kDsBtnUp |
                                        DrasticRunner::kDsBtnDown);
                    if (ev.value < 0) st->dsBtnMask |= DrasticRunner::kDsBtnUp;
                    if (ev.value > 0) st->dsBtnMask |= DrasticRunner::kDsBtnDown;
                    if (overlayOpen && ev.value != 0 &&
                            ev.value != st->hat0yPrev) {
                        if (ev.value < 0) out->navUp = true;
                        else              out->navDown = true;
                    }
                    st->hat0yPrev = ev.value;
                    break;
                }
                case ABS_X:  st->axLX.raw = ev.value; st->axLX.seen = true; break;
                case ABS_Y:  st->axLY.raw = ev.value; st->axLY.seen = true; break;
                case ABS_RX: st->axRX.raw = ev.value; st->axRX.seen = true; break;
                case ABS_RY: st->axRY.raw = ev.value; st->axRY.seen = true; break;
                case ABS_Z:  st->axLZ.raw = ev.value; st->axLZ.seen = true; break;
                case ABS_RZ: st->axRZ.raw = ev.value; st->axRZ.seen = true; break;
                default: break;
                }
            }
        }
    }

    // Long-press exit detection. Fires when BACK has been held longer
    // than longBackMs; short-press was already handled on release.
    if (st->backPressStartMs > 0) {
        int64_t held = android::elapsedRealtime() - st->backPressStartMs;
        if (held >= longBackMs) {
            out->exitRequested = true;
        }
    }

    drainTouch(st);

    // Stick-as-stylus gets the final word, after we know the real
    // touchscreen state for this frame.
    applyAnalogStylus(st);

    if (overlayOpen) {
        // Suppress DS gameplay inputs while menu is up. Zero out the
        // stylus too so drastic sees "finger lifted" on open and does
        // not continue a stylus drag underneath the menu.
        out->dsBtnMask = 0;
        out->touchX = st->touchDsX;
        out->touchY = st->touchDsY;
        out->touchHeld = false;
        // Also force-off fast-forward: the menu pauses the emulator,
        // so the lever would get stuck ON if the player closes the
        // menu and then releases the FF button (we would not see
        // the release).
        out->actFastFwd = false;
    } else {
        // Layer stick-as-DPad bits on top of latched DPad / button state
        // so the stick acts as a secondary DPad for games that don't use
        // the touchscreen.
        out->dsBtnMask = st->dsBtnMask | stickDpadBits(st);
        out->touchX = st->touchDsX;
        out->touchY = st->touchDsY;
        // Real finger wins; otherwise the stylus-touch button synthesizes
        // a press at the last known cursor position.
        out->touchHeld = st->touchHeld || st->stylusBtnHeld;
        // Reflect the held state of the fast-forward button.
        out->actFastFwd = st->btnFastFwd;
    }
}

} // namespace drastic_input
} // namespace android
