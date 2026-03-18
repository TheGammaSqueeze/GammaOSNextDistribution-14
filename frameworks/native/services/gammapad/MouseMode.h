#pragma once

#include "VirtualMouse.h"
#include "VirtualTouchscreen.h"

#include <linux/input.h>
#include <chrono>
#include <functional>
#include <memory>
#include <string>
#include <vector>

namespace gammapad {

class MouseMode {
public:
    // Callback to send a toast message over the bridge socket
    using ToastCallback = std::function<void(const std::string&)>;

    MouseMode();
    ~MouseMode();

    void loadConfig();

    bool isActive() const { return mActive; }

    // Initialize: create timerfd. Returns timerfd for caller to add to epoll,
    // or -1 on failure. VirtualMouse is created on-demand when mode activates.
    int init();

    // Process a transformed input event. Returns true if the event was consumed
    // (either by combo detection or by mouse mode mapping).
    // When true, the event should NOT be forwarded to the virtual gamepad.
    bool processEvent(const struct input_event& ev);

    // Called when the timerfd fires. Generates mouse movement based on
    // current stick/DPAD state.
    void tick();

    // Periodic check for combo timeout (called from main loop when no
    // input events arrive). Returns true if mode was toggled.
    bool checkComboTimeout();

    // Check for external mouse mode toggle (e.g. QS tile setting
    // sys.gammaos.gamepad.mouse_active). Returns true if mode was toggled.
    bool checkExternalToggle();

    // Set the toast callback (called when mouse mode toggles)
    void setToastCallback(ToastCallback cb) { mToastCallback = std::move(cb); }

    // Get events that need to be flushed to the virtual gamepad
    // (e.g., combo button releases when entering mouse mode)
    const std::vector<struct input_event>& getFlushEvents() const { return mFlushEvents; }
    void clearFlushEvents() { mFlushEvents.clear(); }

    // Get the timerfd (for epoll)
    int timerFd() const { return mTimerFd; }

private:
    void setActive(bool active);
    void showToast(const std::string& message);
    void startTimer();
    void stopTimer();
    void detectScreenSize();
    void detectTouchOrientation();

    // Transform display-space coordinates to raw touchscreen coordinates,
    // counteracting InputFlinger's primary_touch_orientation rotation.
    void displayToRaw(int displayX, int displayY, int& rawX, int& rawY) const;

    // Read actual cursor position from framework shared file.
    // Updates mCursorX/mCursorY. Returns true on success.
    bool readCursorPosition();

    bool mActive;

    // Combo detection
    bool mComboBtn1Held;
    bool mComboBtn2Held;
    std::chrono::steady_clock::time_point mComboBothHeldSince;
    bool mComboBothHeld;
    int mComboBtn1Code;
    int mComboBtn2Code;
    int mComboHoldMs;

    // Current input state for mouse movement (left stick)
    int mStickX;     // left analog X: -32768..32767
    int mStickY;     // left analog Y: -32768..32767
    int mDpadX;      // -1, 0, 1
    int mDpadY;      // -1, 0, 1
    bool mSpeedBoost; // speed boost button held

    // Right stick state for scroll wheel
    int mRStickX;    // right analog X: -32768..32767
    int mRStickY;    // right analog Y: -32768..32767

    // Sub-pixel accumulators for smooth movement
    float mAccumX;
    float mAccumY;

    // Sub-pixel accumulators for scroll wheel
    float mScrollAccumX;
    float mScrollAccumY;

    // Cursor position (read from framework via shared file)
    float mCursorX;
    float mCursorY;
    int mScreenW;
    int mScreenH;
    int mOrientation;  // 0, 90, 180, 270 from ro.input_flinger.primary_touch_orientation
    int mCursorPosFd;  // cached fd for reading cursor position file

    // Drag position tracking (independent of mouse cursor during drags)
    float mDragX;
    float mDragY;

    // Configuration
    float mStickSpeed;       // pixels per tick at max stick deflection
    float mDpadSpeed;        // pixels per tick for DPAD
    float mBoostMultiplier;  // speed multiplier when boost button held
    float mScrollSpeed;      // scroll ticks per tick at max deflection

    // Configurable button mappings for mouse actions
    int mClickBtnCode;       // gamepad button for touch tap (default: BTN_A)
    int mBackBtnCode;        // gamepad button for KEY_BACK (default: BTN_B)
    int mRightClickBtnCode;  // gamepad button for right click (default: BTN_Y)
    int mBoostBtnCode;       // gamepad button for speed boost (default: BTN_X)

    std::unique_ptr<VirtualMouse> mMouse;
    std::unique_ptr<VirtualTouchscreen> mTouchscreen;
    int mTimerFd;

    ToastCallback mToastCallback;
    std::vector<struct input_event> mFlushEvents;
};

} // namespace gammapad
