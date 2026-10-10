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
    // Touch in panel pixel coordinates of the bottom panel.
    std::function<void(float x, float y, bool pressed)> touch;
    std::function<void(float x, float y)> touchMoved;
    std::function<void()> menuToggle;   // short press of the menu/back key
    std::function<void()> exitRequest;  // long press of the menu/back key
};

class NanoInput {
public:
    bool start(const InputCallbacks& cb, uint32_t touchW, uint32_t touchH);
    void stop();
    // Button arrangement, switchable live from the menu.
    void setNintendoLayout(bool on) { mNintendoLayout = on; }
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
        int absMin[64] = {};
        int absMax[64] = {};
        bool hasAbs[64] = {};
    };
    void scan();
    void loop();
    void handlePad(Node& n, uint16_t type, uint16_t code, int32_t value);
    void handleTouch(Node& n, uint16_t type, uint16_t code, int32_t value);

    InputCallbacks mCb;
    std::vector<Node> mNodes;
    std::thread mThread;
    std::atomic<bool> mRun{false};
    int mInotify = -1;
    uint32_t mTouchW = 0, mTouchH = 0;
    bool mNintendoLayout = false;
    // per-pad state
    float mLx = 0, mLy = 0, mRx = 0, mRy = 0;
    int mHatX = 0, mHatY = 0;
    int64_t mBackDownNs = 0;
    bool mBackLongFired = false;
    bool mBackCombo = false;  // this Back press was used as a combo modifier
    // touch state
    int mTx = 0, mTy = 0;
    bool mTouchDown = false;
    bool mTouchDirty = false;
};

} // namespace azahar_nano
