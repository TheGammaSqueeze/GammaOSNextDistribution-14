#pragma once

#include <linux/input.h>
#include <linux/uinput.h>

namespace gammapad {

class VirtualMouse {
public:
    VirtualMouse();
    ~VirtualMouse();

    bool create();
    void destroy();

    bool writeEvent(const struct input_event& ev);
    bool writeSyn();

    // Move the cursor by (dx, dy) pixels
    void move(int dx, int dy);

    // Scroll wheel: vertical (positive = up) and horizontal (positive = right)
    void scroll(int vertical, int horizontal);

    // Press/release a button (BTN_LEFT, BTN_RIGHT, KEY_BACK)
    void buttonDown(int button);
    void buttonUp(int button);

    int fd() const { return mFd; }
    bool isValid() const { return mFd >= 0; }

private:
    int mFd;
};

} // namespace gammapad
