#pragma once

#include <linux/input.h>
#include <linux/uinput.h>

namespace gammapad {

class VirtualTouchscreen {
public:
    // Android InputReader caps at 16 pointers (MAX_POINTERS in Input.h)
    static constexpr int MAX_SLOTS = 16;

    VirtualTouchscreen();
    ~VirtualTouchscreen();

    // Create a virtual touchscreen device with the given screen dimensions.
    bool create(int screenW, int screenH);
    void destroy();

    // Single-slot convenience methods (operate on slot 0, backward compat with MouseMode)
    void touchDown(int x, int y);
    void touchMove(int x, int y);
    void touchUp();
    bool isTouching() const { return mSlotTouching[0]; }

    // Multi-slot methods for screen mapping
    void touchDown(int slot, int x, int y);
    void touchMove(int slot, int x, int y);
    void touchUp(int slot);
    bool isSlotTouching(int slot) const;

    bool isValid() const { return mFd >= 0; }
    int fd() const { return mFd; }

    // Returns true if any slot is currently touching
    bool isAnyTouching() const;

private:
    void sendEvent(int type, int code, int value);
    void sendSync();
    void updateBtnTouch();

    int mFd;
    bool mSlotTouching[MAX_SLOTS];
    int mScreenW;
    int mScreenH;
    int mSlotTrackId[MAX_SLOTS];
    int mNextTrackId;
};

} // namespace gammapad
