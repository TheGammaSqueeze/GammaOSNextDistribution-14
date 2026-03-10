#pragma once

#include <linux/input.h>
#include <linux/uinput.h>

namespace gammapad {

class VirtualTouchscreen {
public:
    VirtualTouchscreen();
    ~VirtualTouchscreen();

    // Create a virtual touchscreen device with the given screen dimensions.
    bool create(int screenW, int screenH);
    void destroy();

    void touchDown(int x, int y);
    void touchMove(int x, int y);
    void touchUp();

    bool isTouching() const { return mTouching; }
    bool isValid() const { return mFd >= 0; }
    int fd() const { return mFd; }

private:
    void sendEvent(int type, int code, int value);
    void sendSync();

    int mFd;
    bool mTouching;
    int mScreenW;
    int mScreenH;
    int mTrackId;
};

} // namespace gammapad
