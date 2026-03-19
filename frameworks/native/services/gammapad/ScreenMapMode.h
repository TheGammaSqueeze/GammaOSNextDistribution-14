#pragma once

#include "VirtualTouchscreen.h"

#include <linux/input.h>
#include <chrono>
#include <functional>
#include <memory>
#include <string>
#include <unordered_map>
#include <vector>

namespace gammapad {

struct ScreenMapPoint {
    enum Type { BUTTON, STICK_LEFT, STICK_RIGHT, DPAD };
    Type type;
    int x, y;          // display-space center coordinates
    int radius;         // radius in pixels
    int buttonCode;     // for BUTTON type: linux button code (BTN_A=0x130, etc.)
    int slot;           // assigned touch slot (0..9)
};

class ScreenMapMode {
public:
    using ToastCallback = std::function<void(const std::string&)>;

    ScreenMapMode();
    ~ScreenMapMode();

    void loadConfig();

    bool isActive() const { return mActive; }
    bool isEnabled() const { return mEnabled; }
    bool hasConfig() const { return mHasConfig; }

    // Enable/disable screen mapping globally (user intent — persists across app changes)
    void setEnabled(bool enabled);

    // Initialize: create timerfd. Returns timerfd for caller to add to epoll,
    // or -1 on failure.
    int init();

    // Process a transformed input event. Returns true if the event was consumed
    // (button/axis is mapped and screen map mode is active).
    bool processEvent(const struct input_event& ev);

    // Called when the timerfd fires (60Hz). Updates stick/DPAD touch positions.
    void tick();

    // Check for external toggle via sys.gammaos.screenmap.active property.
    // Returns true if mode was toggled.
    bool checkExternalToggle();

    // Called when foreground app changes. Loads config if available.
    void checkForegroundApp(const std::string& pkg);

    void setToastCallback(ToastCallback cb) { mToastCallback = std::move(cb); }

    int timerFd() const { return mTimerFd; }

private:
    void setActive(bool active);
    void showToast(const std::string& message);
    void startTimer();
    void stopTimer();
    void detectScreenSize();
    void detectTouchOrientation();
    void displayToRaw(int displayX, int displayY, int& rawX, int& rawY) const;

    bool loadConfigFile(const std::string& pkg);

    bool mActive;
    bool mEnabled;       // user's global intent (persists across app changes)
    bool mHasConfig;
    std::string mCurrentPkg;

    // Mapping configuration for current app
    std::vector<ScreenMapPoint> mMappings;

    // Button code -> indices into mMappings (supports multiple points per button)
    std::unordered_multimap<int, int> mButtonToMapping;

    // Whether a stick/dpad mapping exists (for axis event interception)
    bool mHasLeftStick;
    bool mHasRightStick;
    bool mHasDpad;
    int mLeftStickIdx;   // index into mMappings
    int mRightStickIdx;
    int mDpadIdx;

    // Current stick/DPAD input state
    int mLeftStickX, mLeftStickY;     // -32768..32767
    int mRightStickX, mRightStickY;
    int mDpadX, mDpadY;              // -1, 0, 1

    // Screen dimensions and orientation
    int mScreenW, mScreenH;
    int mOrientation;

    std::unique_ptr<VirtualTouchscreen> mTouchscreen;
    int mTimerFd;

    // Next slot to assign when loading config
    int mNextSlot;

    ToastCallback mToastCallback;
};

} // namespace gammapad
