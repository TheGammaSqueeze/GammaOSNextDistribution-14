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

    // Create the uinput keyboard advertising EXACTLY the given KEY_* codes (the
    // keyboard-routed action targets actually configured). Advertising only what
    // is needed keeps a device with no keyboard actions from ever existing, and a
    // device with only e.g. media/nav targets from being misclassified as a full
    // alphabetic keyboard (which makes Android hide the soft IME). Must be called
    // with a NON-EMPTY set; the caller skips creation entirely when none apply.
    bool create(const std::set<int>& codes);
    void destroy();

    bool isValid() const { return mFd >= 0; }

    // The KEY_* codes this device currently advertises (for change detection).
    const std::set<int>& codes() const { return mCodes; }

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
