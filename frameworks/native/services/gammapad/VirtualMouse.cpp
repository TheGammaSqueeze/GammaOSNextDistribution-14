#define LOG_TAG "gammapad"

#include "VirtualMouse.h"

#include <android-base/logging.h>
#include <errno.h>
#include <fcntl.h>
#include <string.h>
#include <unistd.h>

namespace gammapad {

static constexpr const char* kPhysId = "gammapad-mouse";

VirtualMouse::VirtualMouse() : mFd(-1) {}

VirtualMouse::~VirtualMouse() {
    destroy();
}

bool VirtualMouse::create() {
    mFd = open("/dev/uinput", O_RDWR | O_NONBLOCK);
    if (mFd < 0) {
        LOG(ERROR) << "VirtualMouse: failed to open /dev/uinput: " << strerror(errno);
        return false;
    }

    // Enable relative axes for mouse movement
    if (ioctl(mFd, UI_SET_EVBIT, EV_REL) < 0) goto fail;
    if (ioctl(mFd, UI_SET_RELBIT, REL_X) < 0) goto fail;
    if (ioctl(mFd, UI_SET_RELBIT, REL_Y) < 0) goto fail;
    // Scroll wheel support (R stick maps here in mouse mode)
    if (ioctl(mFd, UI_SET_RELBIT, REL_WHEEL) < 0) goto fail;
    if (ioctl(mFd, UI_SET_RELBIT, REL_HWHEEL) < 0) goto fail;

    // Enable key events for mouse buttons
    if (ioctl(mFd, UI_SET_EVBIT, EV_KEY) < 0) goto fail;
    if (ioctl(mFd, UI_SET_KEYBIT, BTN_LEFT) < 0) goto fail;
    if (ioctl(mFd, UI_SET_KEYBIT, BTN_RIGHT) < 0) goto fail;
    if (ioctl(mFd, UI_SET_KEYBIT, KEY_BACK) < 0) goto fail;

    {
        struct uinput_setup setup = {};
        strncpy(setup.name, "GammaOS Virtual Mouse", UINPUT_MAX_NAME_SIZE - 1);
        setup.id.bustype = BUS_VIRTUAL;
        setup.id.vendor = 0x045e;
        setup.id.product = 0x0F01;
        setup.id.version = 0x0100;

        ioctl(mFd, UI_SET_PHYS, kPhysId);

        if (ioctl(mFd, UI_DEV_SETUP, &setup) < 0) {
            LOG(ERROR) << "VirtualMouse: UI_DEV_SETUP failed: " << strerror(errno);
            goto fail;
        }

        if (ioctl(mFd, UI_DEV_CREATE) < 0) {
            LOG(ERROR) << "VirtualMouse: UI_DEV_CREATE failed: " << strerror(errno);
            goto fail;
        }
    }

    LOG(INFO) << "VirtualMouse created successfully";
    return true;

fail:
    close(mFd);
    mFd = -1;
    return false;
}

void VirtualMouse::destroy() {
    if (mFd >= 0) {
        ioctl(mFd, UI_DEV_DESTROY);
        close(mFd);
        mFd = -1;
        LOG(INFO) << "VirtualMouse destroyed";
    }
}

bool VirtualMouse::writeEvent(const struct input_event& ev) {
    if (mFd < 0) return false;
    ssize_t n = write(mFd, &ev, sizeof(ev));
    return n == sizeof(ev);
}

bool VirtualMouse::writeSyn() {
    struct input_event syn = {};
    syn.type = EV_SYN;
    syn.code = SYN_REPORT;
    syn.value = 0;
    return writeEvent(syn);
}

void VirtualMouse::move(int dx, int dy) {
    if (mFd < 0) return;

    if (dx != 0) {
        struct input_event ev = {};
        ev.type = EV_REL;
        ev.code = REL_X;
        ev.value = dx;
        writeEvent(ev);
    }

    if (dy != 0) {
        struct input_event ev = {};
        ev.type = EV_REL;
        ev.code = REL_Y;
        ev.value = dy;
        writeEvent(ev);
    }

    if (dx != 0 || dy != 0) {
        writeSyn();
    }
}

void VirtualMouse::scroll(int vertical, int horizontal) {
    if (mFd < 0) return;

    if (vertical != 0) {
        struct input_event ev = {};
        ev.type = EV_REL;
        ev.code = REL_WHEEL;
        ev.value = vertical;
        writeEvent(ev);
    }

    if (horizontal != 0) {
        struct input_event ev = {};
        ev.type = EV_REL;
        ev.code = REL_HWHEEL;
        ev.value = horizontal;
        writeEvent(ev);
    }

    if (vertical != 0 || horizontal != 0) {
        writeSyn();
    }
}

void VirtualMouse::buttonDown(int button) {
    if (mFd < 0) return;
    struct input_event ev = {};
    ev.type = EV_KEY;
    ev.code = button;
    ev.value = 1;
    writeEvent(ev);
    writeSyn();
}

void VirtualMouse::buttonUp(int button) {
    if (mFd < 0) return;
    struct input_event ev = {};
    ev.type = EV_KEY;
    ev.code = button;
    ev.value = 0;
    writeEvent(ev);
    writeSyn();
}

} // namespace gammapad
