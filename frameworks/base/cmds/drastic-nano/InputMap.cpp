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
    st->touchFds.clear();
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

        // Touch panel detection. The known DRM unit names its panel
        // "gt9xx-0", but an arbitrary device (the SurfaceFlinger handhelds)
        // names it anything, so a panel is recognized by capability: a
        // multitouch digitizer reports ABS_MT_POSITION_X/Y. Both the exact
        // name and the capability open the node here. Gamepads never expose
        // ABS_MT_POSITION_X, so the analog-stick devices fall through to the
        // gamepad path below untouched.
        const bool namedTouch = (strcmp(name, kTouchDeviceName) == 0);
        struct input_absinfo absX = {}, absY = {};
        const bool hasMtX =
                ioctl(fd, EVIOCGABS(ABS_MT_POSITION_X), &absX) >= 0 &&
                absX.maximum > 0;
        const bool hasMtY =
                ioctl(fd, EVIOCGABS(ABS_MT_POSITION_Y), &absY) >= 0 &&
                absY.maximum > 0;
        if (namedTouch || (hasMtX && hasMtY)) {
            const int pw = absX.maximum > 0 ? absX.maximum : 1;
            const int ph = absY.maximum > 0 ? absY.maximum : 1;
            // The named panel's range wins; otherwise the first panel seen
            // sets it. A unit's touch nodes share one ABS range, so this is
            // the panel size whichever node ends up streaming events.
            if (namedTouch || st->touchPanelW == 0) {
                st->touchPanelW = pw;
                st->touchPanelH = ph;
            }
            st->touchFds.push_back(fd);
            ALOGI("DrasticNano::input: touch %s %s (panel %dx%d)%s",
                  name, p.c_str(), pw, ph, namedTouch ? " [named]" : " [mt]");
            continue;
        }
        unsigned long keys[(KEY_MAX + 8 * sizeof(long)) /
                            (8 * sizeof(long))] = {};
        if (ioctl(fd, EVIOCGBIT(EV_KEY, sizeof(keys)), keys) >= 0) {
            auto has = [&](int code) {
                return (keys[code / (8 * sizeof(long))] >>
                        (code % (8 * sizeof(long)))) & 1;
            };
            // KEY_POWER admits the gpio-keys power button device for the DRM
            // backend: there the framework consumes KEYCODE_POWER inertly while a
            // drastic session runs (the DRM home swallows it), so the power
            // gestures must be read from evdev. The SF backend runs as a normal
            // foreground app and leaves power entirely to PhoneWindowManager, so
            // it clears admitPowerKey and a power-only node is not opened here.
            const bool powerAdmit = st->admitPowerKey && has(KEY_POWER);
            if (has(BTN_SOUTH) || has(BTN_A) || has(KEY_BACK) ||
                has(KEY_UP) || has(KEY_VOLUMEUP) || powerAdmit) {
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
    for (int fd : st->touchFds) close(fd);
    st->touchFds.clear();
}

namespace {

// Drain all queued touchscreen events and update touchHeld + touchDs*.
void drainTouch(InputState* st) {
    struct input_event ev;
    for (int touchFd : st->touchFds) {
    while (read(touchFd, &ev, sizeof(ev)) == sizeof(ev)) {
        if (ev.type == EV_KEY && ev.code == BTN_TOUCH) {
            st->touchHeld = (ev.value != 0);
            st->touchReal = (ev.value != 0);
        } else if (ev.type == EV_ABS) {
            if (ev.code == ABS_MT_POSITION_X) {
                st->touchPendingX = ev.value;
                st->touchPendingValid = true;
            } else if (ev.code == ABS_MT_POSITION_Y) {
                st->touchPendingY = ev.value;
                st->touchPendingValid = true;
            } else if (ev.code == ABS_MT_TRACKING_ID && ev.value == -1) {
                st->touchHeld = false;
                st->touchReal = false;
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

// Right-stick normalized X / Y, robust to controller variety with no
// per-device hardcoding. We read raw evdev, so we classify axes the way the
// kernel reports them rather than assuming one controller's codes: a thumbstick
// axis is BIDIRECTIONAL (min < 0, it swings both ways about centre) while a
// trigger is unidirectional (min == 0). The right stick is therefore the
// bidirectional secondary pair, whichever code the pad uses for it -- ABS_RX/RY
// on retrogame and generic pads, ABS_Z/RZ on Xbox-style pads (where ABS_RX/RY
// are absent). This mirrors what Android's per-device key layouts normalize to
// AXIS_Z/AXIS_RZ. A unidirectional ABS_Z/RZ (an analog trigger) is skipped so a
// resting trigger never reads as a held direction.
float rightStickX(const InputState* st) {
    if (st->axRX.seen && st->axRX.min < 0)
        return normAxis(st->axRX, st->analogDeadzone);
    if (st->axLZ.seen && st->axLZ.min < 0)
        return normAxis(st->axLZ, st->analogDeadzone);
    return 0.0f;
}
float rightStickY(const InputState* st) {
    if (st->axRY.seen && st->axRY.min < 0)
        return normAxis(st->axRY, st->analogDeadzone);
    if (st->axRZ.seen && st->axRZ.min < 0)
        return normAxis(st->axRZ, st->analogDeadzone);
    return 0.0f;
}

// Portrait play: the RIGHT analog stick acts as the DS D-Pad. Same 8-way
// thresholding as the left-stick path but reading the resolved right-stick
// axes, so the user can drive the D-Pad with the stick that is ergonomic when
// the console is turned, on whatever controller is connected.
int stickDpadBitsRight(const InputState* st) {
    float nx = rightStickX(st);
    float ny = rightStickY(st);
    constexpr float kDpadThresh = 0.4f;
    int bits = 0;
    if (ny < -kDpadThresh) bits |= DrasticRunner::kDsBtnUp;
    if (ny >  kDpadThresh) bits |= DrasticRunner::kDsBtnDown;
    if (nx < -kDpadThresh) bits |= DrasticRunner::kDsBtnLeft;
    if (nx >  kDpadThresh) bits |= DrasticRunner::kDsBtnRight;
    return bits;
}

// Rotate the DS D-Pad and face-button (ABXY) bits by `deg` (0/90/180/270) in
// 90-degree clockwise steps, for portrait play where the console is physically
// turned. Both the D-Pad and the ABXY diamond rotate together so they stay
// natural in the rotated hold. Other bits (L/R/Start/Select) are untouched.
int rotateDsControls(int m, int deg) {
    using R = DrasticRunner;
    int dpad = m & (R::kDsBtnUp | R::kDsBtnDown | R::kDsBtnLeft | R::kDsBtnRight);
    int face = m & (R::kDsBtnA | R::kDsBtnB | R::kDsBtnX | R::kDsBtnY);
    int rest = m & ~(dpad | face);
    int steps = ((deg / 90) % 4 + 4) % 4;
    for (int t = 0; t < steps; t++) {
        int nd = 0;   // 90 CW: Up->Right, Right->Down, Down->Left, Left->Up
        if (dpad & R::kDsBtnUp)    nd |= R::kDsBtnRight;
        if (dpad & R::kDsBtnRight) nd |= R::kDsBtnDown;
        if (dpad & R::kDsBtnDown)  nd |= R::kDsBtnLeft;
        if (dpad & R::kDsBtnLeft)  nd |= R::kDsBtnUp;
        dpad = nd;
        int nf = 0;   // 90 CW around the DS diamond: X(top)->A(right)->B(bottom)->Y(left)->X
        if (face & R::kDsBtnX) nf |= R::kDsBtnA;
        if (face & R::kDsBtnA) nf |= R::kDsBtnB;
        if (face & R::kDsBtnB) nf |= R::kDsBtnY;
        if (face & R::kDsBtnY) nf |= R::kDsBtnX;
        face = nf;
    }
    return rest | dpad | face;
}

// Build the final DS button mask for portrait play. `rot` (90/180/270) turns
// the D-Pad + ABXY diamonds to match a physically rotated console. `scheme`
// picks the layout:
//   0 = Right Stick: the right analog stick is an extra D-Pad; the D-Pad keeps
//       its role. Everything rotates by `rot`.
//   1 = D-Pad as Face: the physical D-Pad presses the ABXY face buttons (mapped
//       around the diamond) and the LEFT stick drives the direction instead, so
//       you can hold the console the other way up. Everything rotates by `rot`.
int applyPortraitControls(const InputState* st, int physMask, int lsDpad,
                          int rot, int scheme) {
    using R = DrasticRunner;
    const int kDpad = R::kDsBtnUp | R::kDsBtnDown | R::kDsBtnLeft | R::kDsBtnRight;
    if (scheme != 1) {
        // Right-stick layout: the right stick adds to the D-Pad; rotate all.
        int m = physMask | lsDpad | stickDpadBitsRight(st);
        return rotateDsControls(m, rot);
    }
    // D-Pad-as-face layout: physical D-Pad -> ABXY diamond, left stick -> D-Pad.
    int physDpad = physMask & kDpad;
    int rest     = physMask & ~kDpad;     // physical ABXY + L/R/Start/Select
    int face     = 0;
    if (physDpad & R::kDsBtnUp)    face |= R::kDsBtnX;   // top
    if (physDpad & R::kDsBtnRight) face |= R::kDsBtnA;   // right
    if (physDpad & R::kDsBtnDown)  face |= R::kDsBtnB;   // bottom
    if (physDpad & R::kDsBtnLeft)  face |= R::kDsBtnY;   // left
    int m = rest | face | lsDpad;
    return rotateDsControls(m, rot);
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

// Move the virtual touch cursor for this frame. Direction comes from the left
// stick (fine, analog) or, when the stick is centered, the D-Pad (full speed).
// Holding X speeds movement up, holding Y slows it down, neither/both = normal.
// The position is kept in DS-native units (0..255 x, 0..191 y) so it feeds the
// touch injection directly. The render loops are paced to the DS-native 60 Hz,
// so a fixed per-frame step is used rather than a wall-clock dt.
void updateTouchCursor(InputState* st) {
    float dx = normAxis(st->axLX, st->analogDeadzone);
    float dy = normAxis(st->axLY, st->analogDeadzone);
    if (dx == 0.0f && dy == 0.0f) {
        const int m = st->dsBtnMask | stickDpadBits(st);
        if (m & DrasticRunner::kDsBtnLeft)  dx = -1.0f;
        if (m & DrasticRunner::kDsBtnRight) dx =  1.0f;
        if (m & DrasticRunner::kDsBtnUp)    dy = -1.0f;
        if (m & DrasticRunner::kDsBtnDown)  dy =  1.0f;
    }
    constexpr float kBase = 3.0f, kFast = 7.0f, kSlow = 1.0f;
    float speed = kBase;
    const bool xHeld = (st->dsBtnMask & DrasticRunner::kDsBtnX) != 0;
    const bool yHeld = (st->dsBtnMask & DrasticRunner::kDsBtnY) != 0;
    if (xHeld && !yHeld)      speed = kFast;
    else if (yHeld && !xHeld) speed = kSlow;
    st->cursorX += dx * speed;
    st->cursorY += dy * speed;
    if (st->cursorX < 0.0f)   st->cursorX = 0.0f;
    if (st->cursorX > 255.0f) st->cursorX = 255.0f;
    if (st->cursorY < 0.0f)   st->cursorY = 0.0f;
    if (st->cursorY > 191.0f) st->cursorY = 191.0f;
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
    case 99:  out->navX = true; return true;       // BUTTON_X (OSK backspace)
    case 102: out->navPrevTab = true; return true; // BUTTON_L1
    case 103: out->navNextTab = true; return true; // BUTTON_R1
    case 108: out->navStart = true; return true;   // BUTTON_START (OSK submit)
    default:  return false;
    }
}

} // anonymous namespace

void pollInputMap(InputState* st, bool overlayOpen, bool captureKey,
                  int64_t shortBackMs, int64_t longBackMs,
                  int64_t powerHoldMs, int64_t powerOffHoldMs, InputActions* out) {
    *out = {};

    // ---- Drain gamepad event devices ----
    for (int fd : st->fds) {
        struct input_event ev;
        while (read(fd, &ev, sizeof(ev)) == sizeof(ev)) {
            if (ev.type == EV_SW && ev.code == SW_LID) {
                // Hall-effect lid switch. value 1 = lid closed. A close
                // edge requests sleep (same as a short power press); the
                // open edge is consumed by the sleep loop as the wake.
                const bool closed = (ev.value != 0);
                if (closed && !st->lidClosed) {
                    out->sleepRequested = true;
                }
                st->lidClosed = closed;
                continue;
            }
            if (ev.type == EV_KEY) {
                const bool pressed = (ev.value != 0);

                // Volume keys drive the in-app volume / brightness HUDs.
                // VOL alone = volume (press only); SELECT+VOL = brightness
                // (press or repeat, so holding SELECT + tapping VOL ramps).
                // Never routed to a DS button.
                if (ev.code == KEY_VOLUMEUP || ev.code == KEY_VOLUMEDOWN) {
                    int dir = (ev.code == KEY_VOLUMEUP) ? 1 : -1;
                    bool selectHeld =
                            (st->dsBtnMask & DrasticRunner::kDsBtnSelect) != 0;
                    if (ev.value == 1 || ev.value == 2) {
                        if (selectHeld)        out->brightAdjust = dir;
                        else if (ev.value == 1) out->volAdjust   = dir;
                    }
                    continue;
                }

                if (ev.code == KEY_POWER) {
                    // POWER is handled before capture/keymap routing and
                    // regardless of overlayOpen: it can never be rebound
                    // and must work while the menu is up. Release before
                    // powerHoldMs = sleep; the hold action fires from the
                    // hold check below (one-shot via powerHoldFired).
                    if (pressed && !st->powerWasDown) {
                        st->powerPressStartMs = android::elapsedRealtime();
                        st->powerHoldFired = false;
                        st->powerOffFired = false;
                    }
                    if (!pressed && st->powerWasDown) {
                        int64_t held = android::elapsedRealtime() -
                                       st->powerPressStartMs;
                        if (!st->powerHoldFired && held < powerHoldMs) {
                            out->sleepRequested = true;
                        }
                        st->powerPressStartMs = 0;
                    }
                    st->powerWasDown = pressed;
                    continue;
                }

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
                        // Touch Cursor: edge-toggle the virtual cursor (the new
                        // default R3 behavior, replacing the momentary stylus).
                        case 28:
                            if (pressed && !st->cursorToggleWasDown) {
                                st->cursorMode = !st->cursorMode;
                                if (st->cursorMode) {
                                    st->cursorX = 128.0f;   // center on enter
                                    st->cursorY = 96.0f;
                                }
                            }
                            st->cursorToggleWasDown = pressed;
                            break;
                        // Edge-triggered (fire once on press only).
                        case 16: if (pressed) out->actSwapScreens = true; break;
                        // Menu action: SAME short-press-overlay /
                        // hold-exit semantics as the literal KEY_BACK
                        // button. The physical Back button on this
                        // hardware reaches us as the remapped "Menu"
                        // action (gammapad re-emits it through its
                        // virtual pad, which carries no KEY_BACK), so
                        // without this it could only TAP to open the
                        // overlay and never HOLD to exit. Reuse the
                        // backPressStartMs/backWasDown state so the
                        // shared hold check below fires exitRequested.
                        case 20:
                            if (pressed && !st->backWasDown) {
                                st->backPressStartMs =
                                        android::elapsedRealtime();
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
                            break;
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

    // POWER hold = raise the XMB overlay. One-shot latch (an edge
    // action; re-firing every frame would re-raise the instant the
    // user dismissed it while still holding the button).
    if (st->powerPressStartMs > 0 && !st->powerHoldFired) {
        int64_t held = android::elapsedRealtime() - st->powerPressStartMs;
        if (held >= powerHoldMs) {
            ALOGI("DrasticNano::input: KEY_POWER held %lldms -> XMB overlay",
                  (long long)held);
            out->xmbOverlayRequested = true;
            st->powerHoldFired = true;
        }
    }
    // POWER held past the longer threshold (~5s) = graceful power off. Its own
    // one-shot latch, independent of powerHoldFired, so continuing to hold past
    // the 1.5s overlay raise escalates to a shutdown. The run loop does the
    // slot-9 save + Quick Resume arming before it powers the device off.
    if (st->powerPressStartMs > 0 && !st->powerOffFired) {
        int64_t held = android::elapsedRealtime() - st->powerPressStartMs;
        if (held >= powerOffHoldMs) {
            ALOGI("DrasticNano::input: KEY_POWER held %lldms -> power off",
                  (long long)held);
            out->powerOffRequested = true;
            st->powerOffFired = true;
        }
    }

    drainTouch(st);

    // Stick-as-stylus gets the final word, after we know the real
    // touchscreen state for this frame.
    applyAnalogStylus(st);

    // Debug: force the virtual cursor on for headless shot validation (this
    // platform cannot inject controller input). Default off; gated behind a
    // non-default prop so it is inert in normal use.
    {
        char cdbg[PROPERTY_VALUE_MAX] = {};
        property_get("persist.gammaos.drastic_nano.cursor_dbg", cdbg, "0");
        if (cdbg[0] == '1') st->cursorMode = true;
    }

    // Held dpad level (keys + HAT + stick) for the overlay's hold-to-repeat
    // scroll. Independent of overlayOpen: out->dsBtnMask is zeroed for the
    // game while the menu is up, but the menu still needs the held level to
    // edge-detect press/release and auto-repeat.
    {
        // Overlay navigation always uses the un-rotated D-Pad / left stick.
        // Rotating menu navigation with Portrait Controls was jarring, so the
        // menu stays in its on-screen orientation regardless of the portrait
        // remap (which only applies to gameplay, below).
        int navLevel = st->dsBtnMask | stickDpadBits(st);
        out->navUpHeld    = (navLevel & DrasticRunner::kDsBtnUp)    != 0;
        out->navDownHeld  = (navLevel & DrasticRunner::kDsBtnDown)  != 0;
        out->navLeftHeld  = (navLevel & DrasticRunner::kDsBtnLeft)  != 0;
        out->navRightHeld = (navLevel & DrasticRunner::kDsBtnRight) != 0;
    }

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
    } else if (st->cursorMode) {
        // Virtual touch cursor active. It owns the D-Pad / left stick (move the
        // pointer), A (tap/hold a touch at the pointer), and X/Y (movement
        // speed); those are consumed here so they do not also reach the DS game,
        // while every other button still passes through. touchDirect tells the
        // render loop these are already final DS-native coordinates (skip the
        // real-panel remap). The pointer is drawn over the bottom screen by the
        // render loop.
        updateTouchCursor(st);
        out->dsBtnMask = st->dsBtnMask &
                ~(DrasticRunner::kDsBtnUp   | DrasticRunner::kDsBtnDown  |
                  DrasticRunner::kDsBtnLeft | DrasticRunner::kDsBtnRight |
                  DrasticRunner::kDsBtnA    | DrasticRunner::kDsBtnX     |
                  DrasticRunner::kDsBtnY);
        out->touchX = (int)(st->cursorX + 0.5f);
        out->touchY = (int)(st->cursorY + 0.5f);
        out->touchHeld = (st->dsBtnMask & DrasticRunner::kDsBtnA) != 0;
        out->touchDirect = true;
        out->actFastFwd = st->btnFastFwd;
    } else {
        // Layer stick-as-DPad bits on top of latched DPad / button state
        // so the stick acts as a secondary DPad for games that don't use
        // the touchscreen.
        int physMask = st->dsBtnMask;
        int lsDpad = stickDpadBits(st);
        int raw = physMask | lsDpad;
        // Portrait play: rotate the D-Pad + ABXY (and optionally swap which
        // control drives the direction) so they stay natural when the console
        // is physically turned. These are their OWN controller settings,
        // independent of the screen's Display Rotation:
        //   portrait_controls = 0/90/180/270  (0 = off, also the turn amount)
        //   portrait_layout    = 0 right-stick D-Pad / 1 D-Pad-as-face
        {
            char pc[PROPERTY_VALUE_MAX] = {};
            property_get("persist.gammaos.drastic_nano.portrait_controls", pc, "0");
            int pcRot = atoi(pc);
            if (pcRot != 0) {
                char sl[PROPERTY_VALUE_MAX] = {};
                property_get("persist.gammaos.drastic_nano.portrait_layout", sl, "0");
                raw = applyPortraitControls(st, physMask, lsDpad, pcRot, atoi(sl));
            }
        }
        out->dsBtnMask = raw;
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
