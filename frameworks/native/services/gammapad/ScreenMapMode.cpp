#define LOG_TAG "gammapad"

#include "ScreenMapMode.h"

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
#include <sstream>

namespace gammapad {

// 60Hz tick rate for smooth stick/DPAD touch movement
static constexpr int TICK_INTERVAL_MS = 16;

// Deadzone for analog sticks in screen map mode
static constexpr int STICK_DEADZONE = 4096;

// Config file directory
static constexpr const char* SCREENMAP_DIR = "/data/misc/gammapad/screenmap";

ScreenMapMode::ScreenMapMode()
    : mActive(false),
      mEnabled(false),
      mHasConfig(false),
      mHasLeftStick(false),
      mHasRightStick(false),
      mHasDpad(false),
      mLeftStickIdx(-1),
      mRightStickIdx(-1),
      mDpadIdx(-1),
      mLeftStickX(0), mLeftStickY(0),
      mRightStickX(0), mRightStickY(0),
      mDpadX(0), mDpadY(0),
      mScreenW(0), mScreenH(0),
      mOrientation(0),
      mTimerFd(-1),
      mNextSlot(0) {
}

ScreenMapMode::~ScreenMapMode() {
    if (mTimerFd >= 0) {
        close(mTimerFd);
    }
}

// --- Screen size / orientation detection (same logic as MouseMode) ---

void ScreenMapMode::detectScreenSize() {
    int w = android::base::GetIntProperty("persist.gammaos.gamepad.screen_w", 0);
    int h = android::base::GetIntProperty("persist.gammaos.gamepad.screen_h", 0);
    if (w > 0 && h > 0) {
        mScreenW = w;
        mScreenH = h;
        LOG(INFO) << "ScreenMapMode: screen size from property: " << mScreenW << "x" << mScreenH;
        return;
    }

    int fbFd = open("/dev/graphics/fb0", O_RDONLY);
    if (fbFd >= 0) {
        struct fb_var_screeninfo vinfo = {};
        if (ioctl(fbFd, FBIOGET_VSCREENINFO, &vinfo) == 0 &&
            vinfo.xres > 0 && vinfo.yres > 0) {
            mScreenW = vinfo.xres;
            mScreenH = vinfo.yres;
            close(fbFd);
            LOG(INFO) << "ScreenMapMode: screen size from fb ioctl: " << mScreenW << "x" << mScreenH;
            return;
        }
        close(fbFd);
    }

    std::ifstream fb0("/sys/class/graphics/fb0/virtual_size");
    if (fb0.is_open()) {
        std::string line;
        if (std::getline(fb0, line)) {
            w = 0; h = 0;
            if (sscanf(line.c_str(), "%d,%d", &w, &h) == 2 && w > 0 && h > 0) {
                if (h > w * 2) h = h / 3;
                mScreenW = w;
                mScreenH = h;
                LOG(INFO) << "ScreenMapMode: screen size from fb0 sysfs: " << mScreenW << "x" << mScreenH;
                return;
            }
        }
    }

    for (int card = 0; card < 4; card++) {
        for (int conn = 0; conn < 4; conn++) {
            char path[256];
            snprintf(path, sizeof(path), "/sys/class/drm/card%d-HDMI-A-%d/modes", card, conn + 1);
            std::ifstream drm(path);
            if (!drm.is_open()) {
                snprintf(path, sizeof(path), "/sys/class/drm/card%d-DSI-%d/modes", card, conn + 1);
                drm.open(path);
            }
            if (!drm.is_open()) {
                snprintf(path, sizeof(path), "/sys/class/drm/card%d-eDP-%d/modes", card, conn + 1);
                drm.open(path);
            }
            if (drm.is_open()) {
                std::string mode;
                if (std::getline(drm, mode)) {
                    w = 0; h = 0;
                    if (sscanf(mode.c_str(), "%dx%d", &w, &h) == 2 && w > 0 && h > 0) {
                        mScreenW = w;
                        mScreenH = h;
                        LOG(INFO) << "ScreenMapMode: screen size from DRM (" << path << "): "
                                  << mScreenW << "x" << mScreenH;
                        return;
                    }
                }
            }
        }
    }

    LOG(WARNING) << "ScreenMapMode: could not detect screen size";
}

void ScreenMapMode::detectTouchOrientation() {
    std::string orient = android::base::GetProperty(
        "ro.surface_flinger.primary_display_orientation", "ORIENTATION_0");
    if (orient == "ORIENTATION_90") mOrientation = 90;
    else if (orient == "ORIENTATION_180") mOrientation = 180;
    else if (orient == "ORIENTATION_270") mOrientation = 270;
    else mOrientation = 0;

    if ((mOrientation == 90 || mOrientation == 270) && mScreenW > 0 && mScreenH > 0) {
        std::swap(mScreenW, mScreenH);
        LOG(INFO) << "ScreenMapMode: swapped to display dims for orientation "
                  << mOrientation << "°: " << mScreenW << "x" << mScreenH;
    }
}

void ScreenMapMode::displayToRaw(int displayX, int displayY,
                                   int& rawX, int& rawY) const {
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

// --- Config loading ---

void ScreenMapMode::loadConfig() {
    detectScreenSize();
    detectTouchOrientation();

    // Deactivate current mapping so it can be re-established with fresh config
    if (mActive) {
        setActive(false);
    }

    // Re-read the config file for the current app (handles editor save + config bump)
    if (!mCurrentPkg.empty()) {
        mHasConfig = loadConfigFile(mCurrentPkg);
        if (mHasConfig && mEnabled) {
            setActive(true);
        }
    }

    LOG(INFO) << "ScreenMapMode config: screen=" << mScreenW << "x" << mScreenH
              << " orientation=" << mOrientation << "°"
              << " pkg=" << mCurrentPkg << " hasConfig=" << mHasConfig
              << " enabled=" << mEnabled << " active=" << mActive;
}

bool ScreenMapMode::loadConfigFile(const std::string& pkg) {
    mMappings.clear();
    mButtonToMapping.clear();
    mHasLeftStick = false;
    mHasRightStick = false;
    mHasDpad = false;
    mLeftStickIdx = -1;
    mRightStickIdx = -1;
    mDpadIdx = -1;
    mNextSlot = 0;

    std::string path = std::string(SCREENMAP_DIR) + "/" + pkg + ".conf";
    std::ifstream file(path);
    if (!file.is_open()) {
        return false;
    }

    std::string line;
    while (std::getline(file, line)) {
        // Skip comments and empty lines
        if (line.empty() || line[0] == '#') continue;

        std::istringstream ss(line);
        std::string typeStr;
        int x, y, radius, buttonCode;

        ss >> typeStr >> x >> y >> radius >> buttonCode;
        if (ss.fail()) continue;

        if (mNextSlot >= VirtualTouchscreen::MAX_SLOTS) {
            LOG(WARNING) << "ScreenMapMode: too many mapping points, max is "
                         << VirtualTouchscreen::MAX_SLOTS;
            break;
        }

        ScreenMapPoint point;
        point.x = x;
        point.y = y;
        point.radius = radius;
        point.buttonCode = buttonCode;
        point.slot = mNextSlot++;

        if (typeStr == "button") {
            point.type = ScreenMapPoint::BUTTON;
            int idx = mMappings.size();
            mMappings.push_back(point);
            mButtonToMapping.insert({buttonCode, idx});
        } else if (typeStr == "stick_left") {
            point.type = ScreenMapPoint::STICK_LEFT;
            mLeftStickIdx = mMappings.size();
            mMappings.push_back(point);
            mHasLeftStick = true;
        } else if (typeStr == "stick_right") {
            point.type = ScreenMapPoint::STICK_RIGHT;
            mRightStickIdx = mMappings.size();
            mMappings.push_back(point);
            mHasRightStick = true;
        } else if (typeStr == "dpad") {
            point.type = ScreenMapPoint::DPAD;
            mDpadIdx = mMappings.size();
            mMappings.push_back(point);
            mHasDpad = true;
        } else {
            LOG(WARNING) << "ScreenMapMode: unknown type: " << typeStr;
            mNextSlot--; // reclaim slot
            continue;
        }

        LOG(INFO) << "ScreenMapMode: loaded " << typeStr << " at (" << x << "," << y
                  << ") r=" << radius << " btn=0x" << std::hex << buttonCode << std::dec
                  << " slot=" << point.slot;
    }

    LOG(INFO) << "ScreenMapMode: loaded " << mMappings.size() << " mappings for " << pkg;
    return !mMappings.empty();
}

// --- Activation ---

int ScreenMapMode::init() {
    mTimerFd = timerfd_create(CLOCK_MONOTONIC, TFD_NONBLOCK);
    if (mTimerFd < 0) {
        LOG(ERROR) << "ScreenMapMode: timerfd_create failed: " << strerror(errno);
        return -1;
    }
    LOG(INFO) << "ScreenMapMode initialized (timerfd=" << mTimerFd << ")";
    return mTimerFd;
}

void ScreenMapMode::startTimer() {
    if (mTimerFd < 0) return;
    struct itimerspec ts = {};
    ts.it_interval.tv_nsec = TICK_INTERVAL_MS * 1000000L;
    ts.it_value.tv_nsec = TICK_INTERVAL_MS * 1000000L;
    timerfd_settime(mTimerFd, 0, &ts, nullptr);
}

void ScreenMapMode::stopTimer() {
    if (mTimerFd < 0) return;
    struct itimerspec ts = {};
    timerfd_settime(mTimerFd, 0, &ts, nullptr);
}

void ScreenMapMode::showToast(const std::string& message) {
    if (mToastCallback) mToastCallback(message);
}

void ScreenMapMode::setActive(bool active) {
    if (mActive == active) return;
    mActive = active;

    if (active) {
        // Re-detect screen size in case it changed
        detectScreenSize();
        detectTouchOrientation();

        if (mScreenW <= 0 || mScreenH <= 0) {
            LOG(ERROR) << "ScreenMapMode: cannot activate, no screen size";
            mActive = false;
            return;
        }

        // Create virtual touchscreen
        mTouchscreen = std::make_unique<VirtualTouchscreen>();
        if (!mTouchscreen->create(mScreenW, mScreenH)) {
            LOG(ERROR) << "ScreenMapMode: failed to create virtual touchscreen";
            mTouchscreen.reset();
            mActive = false;
            return;
        }

        // Reset input state
        mLeftStickX = mLeftStickY = 0;
        mRightStickX = mRightStickY = 0;
        mDpadX = mDpadY = 0;

        // Start tick timer for stick/DPAD continuous touch
        if (mHasLeftStick || mHasRightStick || mHasDpad) {
            startTimer();
        }

        mEnabled = true;
        android::base::SetProperty("sys.gammaos.screenmap.active", "1");

        LOG(INFO) << "ScreenMapMode: activated for " << mCurrentPkg
                  << " (" << mMappings.size() << " mappings)";
    } else {
        // Release all active touches and destroy touchscreen
        stopTimer();
        mTouchscreen.reset();

        LOG(INFO) << "ScreenMapMode: deactivated";
    }
}

void ScreenMapMode::setEnabled(bool enabled) {
    mEnabled = enabled;
    android::base::SetProperty("sys.gammaos.screenmap.active", enabled ? "1" : "0");
    if (!enabled && mActive) {
        setActive(false);
    }
}

bool ScreenMapMode::checkExternalToggle() {
    std::string val = android::base::GetProperty("sys.gammaos.screenmap.active", "0");
    std::string persist = android::base::GetProperty("persist.gammaos.screenmap.enabled", "0");
    bool wantEnabled = (val == "1") || (persist == "1");

    if (wantEnabled != mEnabled) {
        mEnabled = wantEnabled;
        if (wantEnabled && !mActive && mHasConfig) {
            setActive(true);
            return true;
        } else if (!wantEnabled && mActive) {
            setActive(false);
            return true;
        }
    }
    return false;
}

void ScreenMapMode::checkForegroundApp(const std::string& pkg) {
    if (pkg == mCurrentPkg) return;

    // Deactivate current mapping (but DON'T change mEnabled — user's intent persists)
    if (mActive) {
        setActive(false);
    }

    mCurrentPkg = pkg;

    // Try to load config for new foreground app
    mHasConfig = loadConfigFile(pkg);

    if (mHasConfig) {
        LOG(INFO) << "ScreenMapMode: found config for " << pkg;
        // Auto-activate if the user has mapping enabled
        if (mEnabled) {
            setActive(true);
        }
    } else {
        LOG(INFO) << "ScreenMapMode: no config for " << pkg;
    }
}

// --- Event processing ---

bool ScreenMapMode::processEvent(const struct input_event& ev) {
    if (!mActive || !mHasConfig || !mTouchscreen) return false;

    // EV_KEY: check if this button is mapped (may have multiple points)
    if (ev.type == EV_KEY) {
        auto range = mButtonToMapping.equal_range(ev.code);
        if (range.first != range.second) {
            for (auto it = range.first; it != range.second; ++it) {
                const ScreenMapPoint& point = mMappings[it->second];
                int rawX, rawY;
                displayToRaw(point.x, point.y, rawX, rawY);

                if (ev.value == 1) {
                    mTouchscreen->touchDown(point.slot, rawX, rawY);
                } else if (ev.value == 0) {
                    mTouchscreen->touchUp(point.slot);
                }
            }
            return true;
        }
        return false;
    }

    // EV_ABS: check if this axis belongs to a mapped stick, DPAD, or trigger
    if (ev.type == EV_ABS) {
        switch (ev.code) {
            case ABS_X:
                if (mHasLeftStick) { mLeftStickX = ev.value; return true; }
                break;
            case ABS_Y:
                if (mHasLeftStick) { mLeftStickY = ev.value; return true; }
                break;
            case ABS_Z:
            case ABS_RX:
                if (mHasRightStick) { mRightStickX = ev.value; return true; }
                break;
            case ABS_RZ:
            case ABS_RY:
                if (mHasRightStick) { mRightStickY = ev.value; return true; }
                break;
            case ABS_HAT0X:
                if (mHasDpad) { mDpadX = ev.value; return true; }
                break;
            case ABS_HAT0Y:
                if (mHasDpad) { mDpadY = ev.value; return true; }
                break;
            // Triggers: LT mapped as BTN_TL2 (0x138), RT mapped as BTN_TR2 (0x139)
            case ABS_GAS:
            case ABS_BRAKE: {
                int triggerBtn = (ev.code == ABS_GAS) ? 0x139 : 0x138;
                auto range = mButtonToMapping.equal_range(triggerBtn);
                if (range.first != range.second) {
                    bool pressed = ev.value > 8000;
                    bool released = ev.value < 5000;
                    for (auto it = range.first; it != range.second; ++it) {
                        const ScreenMapPoint& point = mMappings[it->second];
                        int rawX, rawY;
                        displayToRaw(point.x, point.y, rawX, rawY);
                        if (pressed && !mTouchscreen->isSlotTouching(point.slot)) {
                            mTouchscreen->touchDown(point.slot, rawX, rawY);
                        } else if (released && mTouchscreen->isSlotTouching(point.slot)) {
                            mTouchscreen->touchUp(point.slot);
                        }
                    }
                    return true;
                }
                break;
            }
        }
        return false;
    }

    // SYN events: don't consume — let them flow to virtual gamepad for unmapped buttons
    return false;
}

// --- Tick (60Hz) ---

void ScreenMapMode::tick() {
    if (!mActive || !mTouchscreen) return;

    // Drain timerfd
    uint64_t expirations;
    read(mTimerFd, &expirations, sizeof(expirations));

    // Process left stick mapping
    if (mHasLeftStick && mLeftStickIdx >= 0) {
        const ScreenMapPoint& p = mMappings[mLeftStickIdx];
        float fx = static_cast<float>(mLeftStickX) / 32767.0f;
        float fy = static_cast<float>(mLeftStickY) / 32767.0f;
        float mag = std::sqrt(fx * fx + fy * fy);

        if (std::abs(mLeftStickX) > STICK_DEADZONE || std::abs(mLeftStickY) > STICK_DEADZONE) {
            // Compute touch position within the mapping circle
            float clampedMag = std::min(mag, 1.0f);
            float nx = (mag > 0.001f) ? fx / mag * clampedMag : 0.0f;
            float ny = (mag > 0.001f) ? fy / mag * clampedMag : 0.0f;

            int touchX = p.x + static_cast<int>(nx * p.radius);
            int touchY = p.y + static_cast<int>(ny * p.radius);

            int rawX, rawY;
            displayToRaw(touchX, touchY, rawX, rawY);

            if (mTouchscreen->isSlotTouching(p.slot)) {
                mTouchscreen->touchMove(p.slot, rawX, rawY);
            } else {
                mTouchscreen->touchDown(p.slot, rawX, rawY);
            }
        } else {
            // Stick centered, release touch
            if (mTouchscreen->isSlotTouching(p.slot)) {
                mTouchscreen->touchUp(p.slot);
            }
        }
    }

    // Process right stick mapping
    if (mHasRightStick && mRightStickIdx >= 0) {
        const ScreenMapPoint& p = mMappings[mRightStickIdx];
        float fx = static_cast<float>(mRightStickX) / 32767.0f;
        float fy = static_cast<float>(mRightStickY) / 32767.0f;
        float mag = std::sqrt(fx * fx + fy * fy);

        if (std::abs(mRightStickX) > STICK_DEADZONE || std::abs(mRightStickY) > STICK_DEADZONE) {
            float clampedMag = std::min(mag, 1.0f);
            float nx = (mag > 0.001f) ? fx / mag * clampedMag : 0.0f;
            float ny = (mag > 0.001f) ? fy / mag * clampedMag : 0.0f;

            int touchX = p.x + static_cast<int>(nx * p.radius);
            int touchY = p.y + static_cast<int>(ny * p.radius);

            int rawX, rawY;
            displayToRaw(touchX, touchY, rawX, rawY);

            if (mTouchscreen->isSlotTouching(p.slot)) {
                mTouchscreen->touchMove(p.slot, rawX, rawY);
            } else {
                mTouchscreen->touchDown(p.slot, rawX, rawY);
            }
        } else {
            if (mTouchscreen->isSlotTouching(p.slot)) {
                mTouchscreen->touchUp(p.slot);
            }
        }
    }

    // Process DPAD mapping
    if (mHasDpad && mDpadIdx >= 0) {
        const ScreenMapPoint& p = mMappings[mDpadIdx];

        if (mDpadX != 0 || mDpadY != 0) {
            int touchX = p.x + mDpadX * p.radius;
            int touchY = p.y + mDpadY * p.radius;

            int rawX, rawY;
            displayToRaw(touchX, touchY, rawX, rawY);

            if (mTouchscreen->isSlotTouching(p.slot)) {
                mTouchscreen->touchMove(p.slot, rawX, rawY);
            } else {
                mTouchscreen->touchDown(p.slot, rawX, rawY);
            }
        } else {
            if (mTouchscreen->isSlotTouching(p.slot)) {
                mTouchscreen->touchUp(p.slot);
            }
        }
    }
}

} // namespace gammapad
