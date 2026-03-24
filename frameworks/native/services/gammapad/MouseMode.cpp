#define LOG_TAG "gammapad"

#include "MouseMode.h"

#include <android-base/logging.h>
#include <android-base/properties.h>
#include <sys/timerfd.h>
#include <unistd.h>

#include <linux/fb.h>
#include <sys/ioctl.h>
#include <fcntl.h>
#include <algorithm>
#include <cmath>
#include <cstdio>
#include <fstream>

namespace gammapad {

// 60Hz tick rate for smooth cursor movement
static constexpr int TICK_INTERVAL_MS = 16;

// Default combo: Select (BTN_SELECT=0x13a) + R1 (BTN_TR=0x137)
static constexpr int DEFAULT_COMBO_BTN1 = 0x13a; // BTN_SELECT
static constexpr int DEFAULT_COMBO_BTN2 = 0x137; // BTN_TR
static constexpr int DEFAULT_COMBO_HOLD_MS = 2000;

// Default movement speeds (pixels per 16ms tick)
static constexpr float DEFAULT_STICK_SPEED = 12.0f;  // ~750 px/s at max deflection
static constexpr float DEFAULT_DPAD_SPEED = 6.0f;    // ~375 px/s
static constexpr float DEFAULT_BOOST_MULTIPLIER = 2.0f;  // 20 / 10.0
static constexpr float DEFAULT_SCROLL_SPEED = 0.4f;  // scroll ticks per tick

// Deadzone for analog stick in mouse mode
static constexpr int MOUSE_DEADZONE = 4096;

// Deadzone for right stick scroll
static constexpr int SCROLL_DEADZONE = 6000;

MouseMode::MouseMode()
    : mActive(false),
      mComboBtn1Held(false),
      mComboBtn2Held(false),
      mComboBothHeld(false),
      mComboBtn1Code(DEFAULT_COMBO_BTN1),
      mComboBtn2Code(DEFAULT_COMBO_BTN2),
      mComboHoldMs(DEFAULT_COMBO_HOLD_MS),
      mStickX(0),
      mStickY(0),
      mDpadX(0),
      mDpadY(0),
      mSpeedBoost(false),
      mRStickX(0),
      mRStickY(0),
      mAccumX(0.0f),
      mAccumY(0.0f),
      mScrollAccumX(0.0f),
      mScrollAccumY(0.0f),
      mCursorX(0.0f),
      mCursorY(0.0f),
      mScreenW(0),
      mScreenH(0),
      mOrientation(0),
      mCursorPosFd(-1),
      mDragX(0.0f),
      mDragY(0.0f),
      mStickSpeed(DEFAULT_STICK_SPEED),
      mDpadSpeed(DEFAULT_DPAD_SPEED),
      mBoostMultiplier(DEFAULT_BOOST_MULTIPLIER),
      mScrollSpeed(DEFAULT_SCROLL_SPEED),
      mClickBtnCode(BTN_A),
      mBackBtnCode(BTN_B),
      mRightClickBtnCode(BTN_Y),
      mBoostBtnCode(BTN_X),
      mTimerFd(-1) {
}

MouseMode::~MouseMode() {
    if (mCursorPosFd >= 0) {
        close(mCursorPosFd);
    }
    if (mTimerFd >= 0) {
        close(mTimerFd);
    }
}

void MouseMode::detectScreenSize() {
    // 1. User-specified override via system properties (highest priority)
    int w = android::base::GetIntProperty("persist.gammaos.gamepad.screen_w", 0);
    int h = android::base::GetIntProperty("persist.gammaos.gamepad.screen_h", 0);
    if (w > 0 && h > 0) {
        mScreenW = w;
        mScreenH = h;
        LOG(INFO) << "MouseMode: screen size from property: " << mScreenW << "x" << mScreenH;
        return;
    }

    // 2. FBIOGET_VSCREENINFO ioctl on /dev/graphics/fb0 (most reliable)
    int fbFd = open("/dev/graphics/fb0", O_RDONLY);
    if (fbFd >= 0) {
        struct fb_var_screeninfo vinfo = {};
        if (ioctl(fbFd, FBIOGET_VSCREENINFO, &vinfo) == 0 &&
            vinfo.xres > 0 && vinfo.yres > 0) {
            mScreenW = vinfo.xres;
            mScreenH = vinfo.yres;
            close(fbFd);
            LOG(INFO) << "MouseMode: screen size from fb ioctl: " << mScreenW << "x" << mScreenH;
            return;
        }
        close(fbFd);
    }

    // 3. sysfs fb0 virtual_size fallback
    std::ifstream fb0("/sys/class/graphics/fb0/virtual_size");
    if (fb0.is_open()) {
        std::string line;
        if (std::getline(fb0, line)) {
            w = 0; h = 0;
            if (sscanf(line.c_str(), "%d,%d", &w, &h) == 2 && w > 0 && h > 0) {
                // virtual_size may report multi-buffered height (e.g. 1280,2880 for 3x)
                // Heuristic: if height > width*2, assume triple-buffered
                if (h > w * 2) {
                    h = h / 3;
                }
                mScreenW = w;
                mScreenH = h;
                LOG(INFO) << "MouseMode: screen size from fb0 sysfs: " << mScreenW << "x" << mScreenH;
                return;
            }
        }
    }

    // 4. Try DRM mode info from sysfs
    // Look for connected DRM connectors with active mode.
    // Try each connector type independently — a file may exist but be empty
    // (e.g., HDMI-A-1 with no display attached), so don't let it block DSI/eDP.
    {
        static const char* kConnTypes[] = { "DSI", "eDP", "HDMI-A", "DP" };
        for (int card = 0; card < 4; card++) {
            for (const char* type : kConnTypes) {
                for (int conn = 1; conn <= 4; conn++) {
                    char path[256];
                    snprintf(path, sizeof(path),
                             "/sys/class/drm/card%d-%s-%d/modes", card, type, conn);
                    std::ifstream drm(path);
                    if (!drm.is_open()) continue;
                    std::string mode;
                    if (std::getline(drm, mode)) {
                        w = 0; h = 0;
                        if (sscanf(mode.c_str(), "%dx%d", &w, &h) == 2 && w > 0 && h > 0) {
                            mScreenW = w;
                            mScreenH = h;
                            LOG(INFO) << "MouseMode: screen size from DRM (" << path << "): "
                                      << mScreenW << "x" << mScreenH;
                            return;
                        }
                    }
                }
            }
        }
    }

    LOG(WARNING) << "MouseMode: could not detect screen size from any source";
}

void MouseMode::detectTouchOrientation() {
    // Two separate orientation properties control different things:
    //
    // 1. primary_display_orientation: how SurfaceFlinger rotates the display.
    //    A native 1080x1920 panel with ORIENTATION_270 becomes 1920x1080 display.
    //    We use this to swap mScreenW/mScreenH to get logical display dimensions.
    //
    // 2. primary_touch_orientation: how InputFlinger rotates virtual touchscreen
    //    raw coordinates. If NOT set, InputFlinger defaults to ROTATION_0 (no
    //    rotation). We use this for displayToRaw() inverse compensation.
    //
    // These can differ: a device may rotate the display but not set touch
    // orientation (e.g., when the vendor's physical touch panel driver handles
    // rotation internally).

    // Display orientation — for W/H swap
    std::string displayOrient = android::base::GetProperty(
        "ro.surface_flinger.primary_display_orientation", "ORIENTATION_0");
    int displayDeg = 0;
    if (displayOrient == "ORIENTATION_90") displayDeg = 90;
    else if (displayOrient == "ORIENTATION_180") displayDeg = 180;
    else if (displayOrient == "ORIENTATION_270") displayDeg = 270;

    if ((displayDeg == 90 || displayDeg == 270) && mScreenW > 0 && mScreenH > 0) {
        std::swap(mScreenW, mScreenH);
        LOG(INFO) << "MouseMode: swapped to display dims for display orientation "
                  << displayDeg << "°: " << mScreenW << "x" << mScreenH;
    }

    // Touch orientation — for displayToRaw() inverse rotation
    std::string touchOrient = android::base::GetProperty(
        "ro.input_flinger.primary_touch_orientation", "");
    mOrientation = 0;
    if (touchOrient == "ORIENTATION_90") mOrientation = 90;
    else if (touchOrient == "ORIENTATION_180") mOrientation = 180;
    else if (touchOrient == "ORIENTATION_270") mOrientation = 270;

    LOG(INFO) << "MouseMode: display orientation=" << displayDeg
              << "° touch orientation=" << mOrientation
              << "° screen=" << mScreenW << "x" << mScreenH;
}

void MouseMode::displayToRaw(int displayX, int displayY,
                               int& rawX, int& rawY) const {
    // InputFlinger applies primary_touch_orientation to our virtual touchscreen
    // (confirmed: InputDeviceOrientation=1, RawToDisplay=ROT_270 for 90°).
    //
    // The virtual touchscreen uses display dimensions (mScreenW x mScreenH)
    // because using panel dimensions causes InputFlinger to DISABLE the mapper.
    // This means the ROT includes non-square scaling.
    //
    // For 90° (ROT_270 raw→display, empirically confirmed):
    //   displayX = rawY * W / H
    //   displayY = (rawMaxX - rawX) * H / W
    // Inverse:
    //   rawX = (W-1) - displayY * W / H
    //   rawY = displayX * H / W
    switch (mOrientation) {
        case 90:
            rawX = (mScreenW - 1) - displayY * mScreenW / mScreenH;
            rawY = displayX * mScreenH / mScreenW;
            break;
        case 180:
            rawX = (mScreenW - 1) - displayX;
            rawY = (mScreenH - 1) - displayY;
            break;
        case 270:
            rawX = displayY * mScreenW / mScreenH;
            rawY = (mScreenH - 1) - displayX * mScreenH / mScreenW;
            break;
        default:
            rawX = displayX;
            rawY = displayY;
            break;
    }
}

bool MouseMode::readCursorPosition() {
    if (mCursorPosFd < 0) {
        mCursorPosFd = open("/data/misc/gammapad/cursor_pos", O_RDONLY);
        if (mCursorPosFd < 0) return false;
    }
    float pos[2];
    if (pread(mCursorPosFd, pos, sizeof(pos), 0) == sizeof(pos)) {
        mCursorX = pos[0];
        mCursorY = pos[1];
        return true;
    }
    return false;
}

void MouseMode::loadConfig() {
    using android::base::GetIntProperty;

    mComboBtn1Code = GetIntProperty("persist.gammaos.gamepad.mouse_combo1",
                                     DEFAULT_COMBO_BTN1);
    mComboBtn2Code = GetIntProperty("persist.gammaos.gamepad.mouse_combo2",
                                     DEFAULT_COMBO_BTN2);
    mComboHoldMs = GetIntProperty("persist.gammaos.gamepad.mouse_hold_ms",
                                   DEFAULT_COMBO_HOLD_MS);

    mStickSpeed = static_cast<float>(
        GetIntProperty("persist.gammaos.gamepad.mouse_stick_speed", 12));
    mDpadSpeed = static_cast<float>(
        GetIntProperty("persist.gammaos.gamepad.mouse_dpad_speed", 6));
    mBoostMultiplier = static_cast<float>(
        GetIntProperty("persist.gammaos.gamepad.mouse_boost", 20)) / 10.0f;
    mScrollSpeed = static_cast<float>(
        GetIntProperty("persist.gammaos.gamepad.mouse_scroll_speed", 4)) / 10.0f;

    mClickBtnCode = GetIntProperty("persist.gammaos.gamepad.mouse_btn_click",
                                    BTN_A);
    mBackBtnCode = GetIntProperty("persist.gammaos.gamepad.mouse_btn_back",
                                   BTN_B);
    mRightClickBtnCode = GetIntProperty("persist.gammaos.gamepad.mouse_btn_rclick",
                                         BTN_Y);
    mBoostBtnCode = GetIntProperty("persist.gammaos.gamepad.mouse_btn_boost",
                                    BTN_X);

    detectScreenSize();
    detectTouchOrientation();

    LOG(INFO) << "MouseMode config: combo=" << mComboBtn1Code << "+" << mComboBtn2Code
              << " hold=" << mComboHoldMs << "ms"
              << " stickSpeed=" << mStickSpeed << " dpadSpeed=" << mDpadSpeed
              << " boost=" << mBoostMultiplier << " scrollSpeed=" << mScrollSpeed
              << " click=" << mClickBtnCode << " back=" << mBackBtnCode
              << " rclick=" << mRightClickBtnCode << " boostBtn=" << mBoostBtnCode
              << " screen=" << mScreenW << "x" << mScreenH;
}

int MouseMode::init() {
    mTimerFd = timerfd_create(CLOCK_MONOTONIC, TFD_NONBLOCK);
    if (mTimerFd < 0) {
        LOG(ERROR) << "MouseMode: timerfd_create failed: " << strerror(errno);
        return -1;
    }

    LOG(INFO) << "MouseMode initialized (timerfd=" << mTimerFd << ")";
    return mTimerFd;
}

void MouseMode::startTimer() {
    if (mTimerFd < 0) return;

    struct itimerspec ts = {};
    ts.it_interval.tv_nsec = TICK_INTERVAL_MS * 1000000L;
    ts.it_value.tv_nsec = TICK_INTERVAL_MS * 1000000L;
    timerfd_settime(mTimerFd, 0, &ts, nullptr);

    LOG(INFO) << "MouseMode timer started (" << TICK_INTERVAL_MS << "ms interval)";
}

void MouseMode::stopTimer() {
    if (mTimerFd < 0) return;

    struct itimerspec ts = {};
    timerfd_settime(mTimerFd, 0, &ts, nullptr);

    LOG(INFO) << "MouseMode timer stopped";
}

void MouseMode::setActive(bool active) {
    if (mActive == active) return;
    mActive = active;

    // Reset input state
    mStickX = 0;
    mStickY = 0;
    mDpadX = 0;
    mDpadY = 0;
    mSpeedBoost = false;
    mRStickX = 0;
    mRStickY = 0;
    mAccumX = 0.0f;
    mAccumY = 0.0f;
    mScrollAccumX = 0.0f;
    mScrollAccumY = 0.0f;

    if (mActive) {
        // Re-detect screen size and orientation in case they changed
        detectScreenSize();
        detectTouchOrientation();

        if (mScreenW <= 0 || mScreenH <= 0) {
            LOG(ERROR) << "MouseMode: cannot activate - screen size unknown";
            mActive = false;
            showToast("Mouse mode FAILED: unknown screen size");
            return;
        }

        // Read initial cursor position from framework
        if (!readCursorPosition()) {
            mCursorX = mScreenW / 2.0f;
            mCursorY = mScreenH / 2.0f;
        }

        // Create virtual mouse device on demand
        mMouse = std::make_unique<VirtualMouse>();
        if (!mMouse->create()) {
            LOG(ERROR) << "MouseMode: failed to create VirtualMouse";
            mActive = false;
            return;
        }

        // Create virtual touchscreen with display dimensions. Using panel
        // dimensions causes InputFlinger to DISABLE the mapper, so we must
        // use display dims and compensate for the rotation in displayToRaw().
        mTouchscreen = std::make_unique<VirtualTouchscreen>();
        if (!mTouchscreen->create(mScreenW, mScreenH)) {
            LOG(ERROR) << "MouseMode: failed to create VirtualTouchscreen";
            mMouse.reset();
            mActive = false;
            return;
        }

        startTimer();
        showToast("Mouse mode ON");

        // Flush release events for combo buttons to the virtual gamepad
        // so they don't get stuck as "pressed"
        struct input_event rel = {};
        rel.type = EV_KEY;
        rel.value = 0;

        rel.code = mComboBtn1Code;
        mFlushEvents.push_back(rel);
        rel.code = mComboBtn2Code;
        mFlushEvents.push_back(rel);

        // SYN after the releases
        struct input_event syn = {};
        syn.type = EV_SYN;
        syn.code = SYN_REPORT;
        syn.value = 0;
        mFlushEvents.push_back(syn);
    } else {
        // Release any active touch before destroying
        if (mTouchscreen && mTouchscreen->isTouching()) {
            mTouchscreen->touchUp();
        }
        stopTimer();
        mMouse.reset();
        mTouchscreen.reset();
        showToast("Mouse mode OFF");
    }

    android::base::SetProperty("sys.gammaos.gamepad.mouse_active",
                                mActive ? "1" : "0");

    LOG(INFO) << "Mouse mode " << (mActive ? "ENABLED" : "DISABLED");
}

void MouseMode::showToast(const std::string& message) {
    if (mToastCallback) {
        mToastCallback(message);
    }
}

bool MouseMode::checkComboTimeout() {
    if (!mComboBothHeld) return false;

    auto elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::steady_clock::now() - mComboBothHeldSince).count();
    if (elapsed >= mComboHoldMs) {
        setActive(!mActive);
        mComboBothHeld = false;
        return true;
    }
    return false;
}

bool MouseMode::checkExternalToggle() {
    bool externalActive = android::base::GetIntProperty(
        "sys.gammaos.gamepad.mouse_active", 0) != 0;
    if (externalActive != mActive) {
        setActive(externalActive);
        return true;
    }
    return false;
}

bool MouseMode::processEvent(const struct input_event& ev) {
    // --- Combo detection (always runs, in both modes) ---
    if (ev.type == EV_KEY) {
        int code = ev.code;
        bool pressed = (ev.value != 0);

        // Track combo button states
        if (code == mComboBtn1Code) {
            mComboBtn1Held = pressed;
        }
        if (code == mComboBtn2Code) {
            mComboBtn2Held = pressed;
        }

        // Check if both combo buttons are held
        if (mComboBtn1Held && mComboBtn2Held) {
            if (!mComboBothHeld) {
                mComboBothHeld = true;
                mComboBothHeldSince = std::chrono::steady_clock::now();
            }
        } else {
            mComboBothHeld = false;
        }
    }

    // Check combo timeout on every event type (stick noise ensures timely detection)
    if (mComboBothHeld) {
        auto elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::steady_clock::now() - mComboBothHeldSince).count();
        if (elapsed >= mComboHoldMs) {
            setActive(!mActive);
            mComboBothHeld = false;
            return true;
        }

        // While waiting for hold time, let combo button events pass through
        // so they still function as normal buttons. They will be "recalled"
        // (released) via flush events when mouse mode actually toggles.
    }

    // If mouse mode is not active, don't consume non-combo events
    if (!mActive) return false;

    // --- Mouse mode is active: consume all events and map them ---

    if (ev.type == EV_KEY) {
        int code = ev.code;
        bool pressed = (ev.value != 0);

        // Combo buttons already handled above
        if (code == mComboBtn1Code || code == mComboBtn2Code) {
            return true;
        }

        if (code == mClickBtnCode) {
            // Touch simulation: tap/drag at actual cursor position
            if (mTouchscreen && mTouchscreen->isValid()) {
                if (pressed) {
                    readCursorPosition();
                    mDragX = mCursorX;
                    mDragY = mCursorY;
                    int rawX, rawY;
                    displayToRaw(static_cast<int>(mDragX),
                                 static_cast<int>(mDragY), rawX, rawY);
                    LOG(INFO) << "MouseMode: touch at cursor (" << mDragX << "," << mDragY
                              << ") raw=(" << rawX << "," << rawY << ")";
                    mTouchscreen->touchDown(rawX, rawY);
                } else {
                    mTouchscreen->touchUp();
                }
            }
            return true;
        }

        if (code == mBackBtnCode) {
            // KEY_BACK via mouse device
            if (pressed) {
                mMouse->buttonDown(KEY_BACK);
            } else {
                mMouse->buttonUp(KEY_BACK);
            }
            return true;
        }

        if (code == mRightClickBtnCode) {
            // Right click
            if (pressed) {
                mMouse->buttonDown(BTN_RIGHT);
            } else {
                mMouse->buttonUp(BTN_RIGHT);
            }
            return true;
        }

        if (code == mBoostBtnCode) {
            // Speed boost toggle
            mSpeedBoost = pressed;
            return true;
        }

        // All other buttons consumed in mouse mode
        return true;
    }

    if (ev.type == EV_ABS) {
        int code = ev.code;
        int value = ev.value;

        // Left stick → cursor movement
        if (code == ABS_X) {
            mStickX = value;
            return true;
        }
        if (code == ABS_Y) {
            mStickY = value;
            return true;
        }

        // DPAD → cursor movement
        if (code == ABS_HAT0X) {
            mDpadX = value;
            return true;
        }
        if (code == ABS_HAT0Y) {
            mDpadY = value;
            return true;
        }

        // Right stick → scroll wheel
        // Handle both native (ABS_Z/ABS_RZ) and remapped (ABS_RX/ABS_RY) layouts
        if (code == ABS_RX || code == ABS_Z) {
            mRStickX = value;
            return true;
        }
        if (code == ABS_RY || code == ABS_RZ) {
            mRStickY = value;
            return true;
        }

        // Consume all other axes in mouse mode
        return true;
    }

    // Consume SYN and all other events in mouse mode
    return true;
}

void MouseMode::tick() {
    if (!mActive || !mMouse || !mMouse->isValid()) return;

    // Drain the timerfd
    uint64_t expirations;
    if (read(mTimerFd, &expirations, sizeof(expirations)) < 0) return;

    float dx = 0.0f;
    float dy = 0.0f;

    // Analog stick contribution (proportional speed with non-linear curve)
    if (std::abs(mStickX) > MOUSE_DEADZONE || std::abs(mStickY) > MOUSE_DEADZONE) {
        float fx = static_cast<float>(mStickX) / 32767.0f;
        float fy = static_cast<float>(mStickY) / 32767.0f;

        // Apply quadratic curve for better precision at low speeds
        float mag = std::sqrt(fx * fx + fy * fy);
        if (mag > 0.0f) {
            float adjustedMag = mag * mag;
            fx = fx / mag * adjustedMag;
            fy = fy / mag * adjustedMag;
        }

        dx += fx * mStickSpeed;
        dy += fy * mStickSpeed;
    }

    // DPAD contribution (fixed speed)
    if (mDpadX != 0 || mDpadY != 0) {
        dx += mDpadX * mDpadSpeed;
        dy += mDpadY * mDpadSpeed;
    }

    // Apply speed boost
    if (mSpeedBoost) {
        dx *= mBoostMultiplier;
        dy *= mBoostMultiplier;
    }

    // Accumulate fractional movement for sub-pixel precision
    mAccumX += dx;
    mAccumY += dy;

    int intDx = static_cast<int>(mAccumX);
    int intDy = static_cast<int>(mAccumY);
    mAccumX -= intDx;
    mAccumY -= intDy;

    if (intDx != 0 || intDy != 0) {
        bool dragging = mTouchscreen && mTouchscreen->isTouching();
        if (dragging) {
            mDragX += intDx;
            mDragY += intDy;
            // Clamp to screen bounds
            if (mDragX < 0) mDragX = 0;
            if (mDragY < 0) mDragY = 0;
            if (mDragX >= mScreenW) mDragX = mScreenW - 1;
            if (mDragY >= mScreenH) mDragY = mScreenH - 1;
            int rawX, rawY;
            displayToRaw(static_cast<int>(mDragX),
                         static_cast<int>(mDragY), rawX, rawY);
            mTouchscreen->touchMove(rawX, rawY);
        }
        // Always move mouse cursor (even during drag so cursor follows the touch)
        mMouse->move(intDx, intDy);
    }

    // --- Right stick scroll ---
    if (std::abs(mRStickX) > SCROLL_DEADZONE || std::abs(mRStickY) > SCROLL_DEADZONE) {
        float sx = 0.0f;
        float sy = 0.0f;

        if (std::abs(mRStickX) > SCROLL_DEADZONE) {
            sx = static_cast<float>(mRStickX) / 32767.0f;
        }
        if (std::abs(mRStickY) > SCROLL_DEADZONE) {
            sy = static_cast<float>(mRStickY) / 32767.0f;
        }

        mScrollAccumX += sx * mScrollSpeed;
        mScrollAccumY += sy * mScrollSpeed;

        // Horizontal scroll: negate so stick-right scrolls right
        int scrollH = -static_cast<int>(mScrollAccumX);
        // Vertical scroll: stick down = scroll down = negative REL_WHEEL
        int scrollV = -static_cast<int>(mScrollAccumY);
        mScrollAccumX -= static_cast<int>(mScrollAccumX);
        mScrollAccumY -= static_cast<int>(mScrollAccumY);

        if (scrollV != 0 || scrollH != 0) {
            mMouse->scroll(scrollV, scrollH);
        }
    } else {
        // Reset accumulators when stick returns to center
        mScrollAccumX = 0.0f;
        mScrollAccumY = 0.0f;
    }
}

} // namespace gammapad
