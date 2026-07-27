#define LOG_TAG "gammapad"

#include "VirtualKeyboard.h"

#include <android-base/logging.h>
#include <errno.h>
#include <fcntl.h>
#include <string.h>
#include <unistd.h>

namespace gammapad {

// Self-detection tag so GamepadManager::grabDevice skips this device on hotplug
// (it skips any node whose EVIOCGPHYS starts with "gammapad-").
static constexpr const char* kPhysId = "gammapad-keyboard";

VirtualKeyboard::VirtualKeyboard() : mFd(-1) {}

VirtualKeyboard::~VirtualKeyboard() {
    destroy();
}

bool VirtualKeyboard::create() {
    mFd = open("/dev/uinput", O_RDWR | O_NONBLOCK | O_CLOEXEC);
    if (mFd < 0) {
        LOG(ERROR) << "VirtualKeyboard: open /dev/uinput failed: " << strerror(errno);
        return false;
    }

    if (ioctl(mFd, UI_SET_EVBIT, EV_KEY) < 0 ||
        ioctl(mFd, UI_SET_EVBIT, EV_SYN) < 0) {
        LOG(ERROR) << "VirtualKeyboard: UI_SET_EVBIT failed: " << strerror(errno);
        close(mFd);
        mFd = -1;
        return false;
    }

    // Advertise a broad, fixed KEY_* set so any keyboard-routed action target
    // works without recreating the device.  Deliberately excludes the BTN_*
    // ranges (0x100..0x15f) so this device is classified as a keyboard, not a
    // pointer/gamepad.
    //   [KEY_ESC .. KEY_MICMUTE]   1  .. 248   (keyboard, volume, media, nav)
    //   [KEY_OK  .. 0x1ff]       0x160.. 0x1ff (consumer / TV / extended keys)
    mCodes.clear();
    for (int code = KEY_ESC; code <= 248; code++) mCodes.insert(code);
    for (int code = 0x160; code <= 0x1ff; code++) mCodes.insert(code);

    for (int code : mCodes) {
        if (ioctl(mFd, UI_SET_KEYBIT, code) < 0) {
            LOG(WARNING) << "VirtualKeyboard: UI_SET_KEYBIT " << code
                         << " failed: " << strerror(errno);
        }
    }

    struct uinput_setup setup = {};
    strncpy(setup.name, "GammaOS Virtual Keyboard", UINPUT_MAX_NAME_SIZE - 1);
    setup.id.bustype = BUS_VIRTUAL;
    setup.id.vendor = 0x045e;
    setup.id.product = 0x0b20;
    setup.id.version = 0x0100;

    ioctl(mFd, UI_SET_PHYS, kPhysId);

    if (ioctl(mFd, UI_DEV_SETUP, &setup) < 0) {
        LOG(ERROR) << "VirtualKeyboard: UI_DEV_SETUP failed: " << strerror(errno);
        close(mFd);
        mFd = -1;
        return false;
    }

    if (ioctl(mFd, UI_DEV_CREATE) < 0) {
        LOG(ERROR) << "VirtualKeyboard: UI_DEV_CREATE failed: " << strerror(errno);
        close(mFd);
        mFd = -1;
        return false;
    }

    LOG(INFO) << "VirtualKeyboard created (" << mCodes.size() << " keys)";
    return true;
}

void VirtualKeyboard::destroy() {
    if (mFd >= 0) {
        ioctl(mFd, UI_DEV_DESTROY);
        close(mFd);
        mFd = -1;
    }
}

bool VirtualKeyboard::writeEvent(int type, int code, int value) {
    if (mFd < 0) return false;
    struct input_event ev = {};
    ev.type = type;
    ev.code = code;
    ev.value = value;
    return write(mFd, &ev, sizeof(ev)) == sizeof(ev);
}

bool VirtualKeyboard::emitKey(int code, bool down) {
    if (mFd < 0) return false;
    if (!writeEvent(EV_KEY, code, down ? 1 : 0)) return false;
    return writeEvent(EV_SYN, SYN_REPORT, 0);
}

bool VirtualKeyboard::tapKey(int code) {
    if (!emitKey(code, true)) return false;
    return emitKey(code, false);
}

} // namespace gammapad
