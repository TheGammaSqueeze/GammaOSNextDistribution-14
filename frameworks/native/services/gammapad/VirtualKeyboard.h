#pragma once

#include <linux/input.h>
#include <linux/uinput.h>
#include <set>

namespace gammapad {

// A companion uinput keyboard used by the button-action model to emit
// keyboard / media / system KEY_* codes.  Keeping these off the virtual
// gamepad avoids apps misclassifying keyboard keys reported by a device
// that also advertises gamepad buttons and axes.
class VirtualKeyboard {
public:
    VirtualKeyboard();
    ~VirtualKeyboard();

    // Create the uinput keyboard advertising a broad, fixed KEY_* set so any
    // keyboard-routed action target works without recreating the device.
    bool create();
    void destroy();

    bool isValid() const { return mFd >= 0; }

    // Emit a single key press or release (value 1 = down, 0 = up) followed by
    // a SYN_REPORT.  Returns false if the device is not valid.
    bool emitKey(int code, bool down);

    // Emit a full tap (down + up) of a key.
    bool tapKey(int code);

    // True if this code is advertised by the keyboard device.
    bool supportsCode(int code) const { return mCodes.count(code) != 0; }

private:
    bool writeEvent(int type, int code, int value);

    int mFd;
    std::set<int> mCodes;
};

} // namespace gammapad
