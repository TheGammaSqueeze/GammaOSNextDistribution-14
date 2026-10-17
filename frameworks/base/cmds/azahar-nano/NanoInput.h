// NanoInput: evdev gamepad and touch input for azahar-nano.
//
// Reads every /dev/input node that looks like a gamepad (the GammaOS virtual
// pad gammapad publishes, or a raw joypad) and forwards 3DS buttons, the circle
// pad, the C stick and the triggers to the core through the C ABI. Touch on the
// bottom panel's touchscreen is forwarded as bottom-screen touch. A long press
// of the Back/Home key requests exit.
#pragma once

#include <atomic>
#include <cstdint>
#include <functional>
#include <string>
#include <thread>
#include <vector>

namespace azahar_nano {

struct InputCallbacks {
    std::function<bool(int button, bool pressed)> button;
    std::function<bool(int axisButton, float x, float y)> joystick;
    std::function<bool(int axisButton, float value)> analogButton;
    // Touch as a 0..1 fraction of the panel it was made on (0 top, 1 bottom), in the panel's
    // own scanout frame: the digitizers are wired to the panels' native (unrotated) axes and
    // azahar-nano scans its buffers out unrotated, so no rotation applies.
    std::function<void(int panel, float x, float y, bool pressed)> touch;
    std::function<void(int panel, float x, float y)> touchMoved;
    std::function<void()> menuToggle;   // short press of the menu/back key
    std::function<void()> exitRequest;  // long press of the menu/back key
    std::function<void()> fastForwardToggle;   // R3 (the 3DS has no stick click)
    // True while the menu is open: the d-pad then always navigates it (no stick swap).
    std::function<bool()> menuOpen;
    // The device's physical lid (EV_SW SW_LID): reported at start and on every change.
    std::function<void(bool closed)> lid;
};

class NanoInput {
public:
    // dualPanel: touch digitizers sit on two panels (the bottom one named by
    // persist.gammaos.nano.cc.touchdev); otherwise every touch belongs to defaultPanel.
    bool start(const InputCallbacks& cb, bool dualPanel, int defaultPanel);
    void stop();
    // Button arrangement, switchable live from the menu.
    void setNintendoLayout(bool on) { mNintendoLayout = on; }
    // Left stick and d-pad swapped: the stick presses the d-pad and the d-pad moves the
    // circle pad (at full deflection). Switchable live from the menu.
    void setStickDpadSwap(bool on) { mSwapStickDpad = on; }
    // From a button callback: true when Back is held, and that Back press then no longer
    // opens the menu or fires its long press (it was used as a combo modifier).
    bool consumeBackHeld() {
        if (mBackDownNs == 0) return false;
        if (mBackLongFired && !mBackCombo) return false;
        mBackLongFired = true;
        mBackCombo = true;
        return true;
    }

private:
    struct Node {
        int fd = -1;
        std::string path;
        bool pad = false;
        bool touch = false;
        bool lid = false;       // reports SW_LID
        int panel = 0;          // touch: the panel this digitizer sits on
        float tx = 0, ty = 0;   // touch: last position, 0..1 of the panel
        bool down = false, dirty = false;
        int absMin[64] = {};
        int absMax[64] = {};
        bool hasAbs[64] = {};
    };
    void scan();
    void loop();
    void handlePad(Node& n, uint16_t type, uint16_t code, int32_t value);
    void handleTouch(Node& n, uint16_t type, uint16_t code, int32_t value);
    enum Dir { kLeft, kRight, kUp, kDown };
    bool swapActive();
    void physicalDpad(Dir d, bool pressed);
    void leftStick();
    void sendDir(Dir d, bool pressed);

    InputCallbacks mCb;
    bool mLidClosed = false, mLidKnown = false;
    std::vector<Node> mNodes;
    std::thread mThread;
    std::atomic<bool> mRun{false};
    int mInotify = -1;
    bool mDualPanel = false;
    int mDefaultPanel = 0;
    std::string mBottomTouchName;
    bool mNintendoLayout = false;
    std::atomic<bool> mSwapStickDpad{false};
    bool mSwapApplied = false;          // the swap state the held inputs were sent under
    bool mDpadHeld[4] = {};             // physical d-pad (buttons or hat)
    bool mStickDir[4] = {};             // d-pad presses the stick has sent (swap or menu)
    bool mStickInMenu = false;          // the stick is stepping through the menu
    // per-pad state
    float mLx = 0, mLy = 0, mRx = 0, mRy = 0;
    int mHatX = 0, mHatY = 0;
    int64_t mBackDownNs = 0;
    std::atomic<int64_t> mBackHoldNs{400LL * 1000000LL};   // see backHoldNs (NanoInput.cpp)
    bool mBackLongFired = false;
    bool mBackCombo = false;  // this Back press was used as a combo modifier
};

} // namespace azahar_nano
