#define LOG_TAG "gammapad"

#include "VirtualGamepad.h"

#include <android-base/logging.h>
#include <errno.h>
#include <fcntl.h>
#include <string.h>
#include <unistd.h>

namespace gammapad {

// Unique phys identifier for self-detection during hotplug
static constexpr const char* kPhysId = "gammapad-virtual";

// Default button set
static const std::set<int> kDefaultButtons = {
    BTN_A, BTN_B, BTN_X, BTN_Y,
    BTN_TL, BTN_TR,
    BTN_SELECT, BTN_START, BTN_MODE,
    BTN_THUMBL, BTN_THUMBR,
};

// Default axis set — matches Xbox Wireless Controller (BT) layout:
// right stick on Z/RZ, triggers on GAS/BRAKE
static const std::set<int> kDefaultAxes = {
    ABS_X, ABS_Y, ABS_Z, ABS_RZ,
    ABS_GAS, ABS_BRAKE,
    ABS_HAT0X, ABS_HAT0Y,
};

VirtualGamepad::VirtualGamepad() : mFd(-1) {}

VirtualGamepad::~VirtualGamepad() {
    destroy();
}

bool VirtualGamepad::create() {
    return createDevice(kDefaultButtons, kDefaultAxes);
}

bool VirtualGamepad::create(const std::set<int>& buttons, const std::set<int>& axes) {
    return createDevice(buttons, axes);
}

bool VirtualGamepad::create(const std::set<int>& buttons,
                            const std::vector<AxisSetup>& axes,
                            const std::string& name,
                            uint16_t vendor, uint16_t product) {
    return createDevice(buttons, axes, name, vendor, product);
}

bool VirtualGamepad::createDevice(const std::set<int>& buttons, const std::set<int>& axes) {
    mFd = open("/dev/uinput", O_RDWR | O_NONBLOCK | O_CLOEXEC);
    if (mFd < 0) {
        LOG(ERROR) << "Failed to open /dev/uinput: " << strerror(errno);
        return false;
    }

    if (!setupButtons(buttons) || !setupAxes(axes) || !setupForceFeedback()) {
        close(mFd);
        mFd = -1;
        return false;
    }
    setupSwitches();   // re-advertise SW_TABLET_MODE so a controller-shared hardware switch survives the grab

    // Set up device identity (Xbox controller compatible)
    struct uinput_setup setup = {};
    strncpy(setup.name, "GammaOS Virtual Gamepad", UINPUT_MAX_NAME_SIZE - 1);
    setup.id.bustype = BUS_BLUETOOTH;
    setup.id.vendor = 0x045e;   // Microsoft
    setup.id.product = 0x0b13;  // Xbox Wireless Controller
    setup.id.version = 0x0100;
    setup.ff_effects_max = 16;

    // Set unique phys identifier so we can detect our own device during hotplug
    ioctl(mFd, UI_SET_PHYS, kPhysId);

    if (ioctl(mFd, UI_DEV_SETUP, &setup) < 0) {
        LOG(ERROR) << "UI_DEV_SETUP failed: " << strerror(errno);
        close(mFd);
        mFd = -1;
        return false;
    }

    if (ioctl(mFd, UI_DEV_CREATE) < 0) {
        LOG(ERROR) << "UI_DEV_CREATE failed: " << strerror(errno);
        close(mFd);
        mFd = -1;
        return false;
    }

    // Store the sets we were created with
    mButtons = buttons;
    mAxes = axes;

    LOG(INFO) << "Virtual gamepad created successfully (buttons=" << mButtons.size()
              << " axes=" << mAxes.size() << ")";
    return true;
}

bool VirtualGamepad::createDevice(const std::set<int>& buttons,
                                  const std::vector<AxisSetup>& axes,
                                  const std::string& name,
                                  uint16_t vendor, uint16_t product) {
    mFd = open("/dev/uinput", O_RDWR | O_NONBLOCK | O_CLOEXEC);
    if (mFd < 0) {
        LOG(ERROR) << "Failed to open /dev/uinput: " << strerror(errno);
        return false;
    }

    if (!setupButtons(buttons) || !setupAxes(axes) || !setupForceFeedback()) {
        close(mFd);
        mFd = -1;
        return false;
    }
    setupSwitches();   // re-advertise SW_TABLET_MODE so a controller-shared hardware switch survives the grab

    // Set up device identity with custom name/VID/PID
    struct uinput_setup setup = {};
    strncpy(setup.name, name.c_str(), UINPUT_MAX_NAME_SIZE - 1);
    setup.id.bustype = BUS_BLUETOOTH;
    setup.id.vendor = vendor;
    setup.id.product = product;
    setup.id.version = 0x0100;
    setup.ff_effects_max = 16;

    // Set unique phys identifier so we can detect our own device during hotplug
    ioctl(mFd, UI_SET_PHYS, kPhysId);

    if (ioctl(mFd, UI_DEV_SETUP, &setup) < 0) {
        LOG(ERROR) << "UI_DEV_SETUP failed: " << strerror(errno);
        close(mFd);
        mFd = -1;
        return false;
    }

    if (ioctl(mFd, UI_DEV_CREATE) < 0) {
        LOG(ERROR) << "UI_DEV_CREATE failed: " << strerror(errno);
        close(mFd);
        mFd = -1;
        return false;
    }

    // Store the sets we were created with
    mButtons = buttons;
    mAxes.clear();
    for (const auto& a : axes) {
        mAxes.insert(a.code);
    }

    LOG(INFO) << "Virtual gamepad created: name=\"" << name << "\""
              << " vid=0x" << std::hex << vendor << " pid=0x" << product << std::dec
              << " buttons=" << mButtons.size() << " axes=" << mAxes.size();
    return true;
}

void VirtualGamepad::destroy() {
    if (mFd >= 0) {
        ioctl(mFd, UI_DEV_DESTROY);
        close(mFd);
        mFd = -1;
        LOG(INFO) << "Virtual gamepad destroyed";
    }
}

bool VirtualGamepad::writeEvent(const struct input_event& ev) {
    if (mFd < 0) return false;
    ssize_t n = write(mFd, &ev, sizeof(ev));
    return n == sizeof(ev);
}

bool VirtualGamepad::writeSyn() {
    struct input_event syn = {};
    syn.type = EV_SYN;
    syn.code = SYN_REPORT;
    syn.value = 0;
    return writeEvent(syn);
}

bool VirtualGamepad::setupButtons(const std::set<int>& buttons) {
    if (ioctl(mFd, UI_SET_EVBIT, EV_KEY) < 0) return false;

    for (int btn : buttons) {
        if (ioctl(mFd, UI_SET_KEYBIT, btn) < 0) {
            LOG(ERROR) << "Failed to set key bit " << btn << ": " << strerror(errno);
            return false;
        }
    }

    return true;
}

bool VirtualGamepad::setupAxes(const std::set<int>& axes) {
    if (ioctl(mFd, UI_SET_EVBIT, EV_ABS) < 0) return false;

    for (int code : axes) {
        if (ioctl(mFd, UI_SET_ABSBIT, code) < 0) {
            LOG(ERROR) << "Failed to set abs bit " << code;
            return false;
        }

        struct uinput_abs_setup absSetup = {};
        absSetup.code = code;

        // Set appropriate ranges based on axis type
        if (code == ABS_HAT0X || code == ABS_HAT0Y) {
            absSetup.absinfo.minimum = -1;
            absSetup.absinfo.maximum = 1;
            absSetup.absinfo.fuzz = 0;
            absSetup.absinfo.flat = 0;
        } else if (code == ABS_GAS || code == ABS_BRAKE) {
            // Triggers (unipolar)
            absSetup.absinfo.minimum = 0;
            absSetup.absinfo.maximum = 32767;
            absSetup.absinfo.fuzz = 0;
            absSetup.absinfo.flat = 0;
        } else {
            // Stick axes: X, Y, Z, RZ (and RX, RY if used)
            absSetup.absinfo.minimum = -32768;
            absSetup.absinfo.maximum = 32767;
            absSetup.absinfo.fuzz = 16;
            absSetup.absinfo.flat = 128;
        }

        if (ioctl(mFd, UI_ABS_SETUP, &absSetup) < 0) {
            LOG(ERROR) << "UI_ABS_SETUP failed for axis " << code;
            return false;
        }
    }

    return true;
}

bool VirtualGamepad::setupAxes(const std::vector<AxisSetup>& axes) {
    if (ioctl(mFd, UI_SET_EVBIT, EV_ABS) < 0) return false;

    for (const auto& axis : axes) {
        if (ioctl(mFd, UI_SET_ABSBIT, axis.code) < 0) {
            LOG(ERROR) << "Failed to set abs bit " << axis.code;
            return false;
        }

        struct uinput_abs_setup absSetup = {};
        absSetup.code = axis.code;
        absSetup.absinfo.minimum = axis.min;
        absSetup.absinfo.maximum = axis.max;
        absSetup.absinfo.fuzz = axis.fuzz;
        absSetup.absinfo.flat = axis.flat;

        if (ioctl(mFd, UI_ABS_SETUP, &absSetup) < 0) {
            LOG(ERROR) << "UI_ABS_SETUP failed for axis " << axis.code
                       << " (min=" << axis.min << " max=" << axis.max << ")";
            return false;
        }

        LOG(INFO) << "VirtualGamepad axis " << axis.code << ": min=" << axis.min
                  << " max=" << axis.max << " fuzz=" << axis.fuzz
                  << " flat=" << axis.flat;
    }

    return true;
}

bool VirtualGamepad::setupForceFeedback() {
    if (ioctl(mFd, UI_SET_EVBIT, EV_FF) < 0) return false;
    if (ioctl(mFd, UI_SET_FFBIT, FF_RUMBLE) < 0) return false;
    return true;
}

bool VirtualGamepad::setupSwitches() {
    // SW_TABLET_MODE: some handhelds (TrimUI Brick) emit a hardware toggle from the SAME evdev node
    // as the gamepad. We grab that node, so re-advertise the switch here and forward its EV_SW events
    // (InputTransformer passes EV_SW through unchanged) so the framework still sees the toggle. Best
    // effort - failure to add the switch bit must not fail device creation.
    if (ioctl(mFd, UI_SET_EVBIT, EV_SW) < 0) return true;
    ioctl(mFd, UI_SET_SWBIT, SW_TABLET_MODE);
    return true;
}

} // namespace gammapad
