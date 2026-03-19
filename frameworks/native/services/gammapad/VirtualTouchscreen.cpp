#define LOG_TAG "gammapad"

#include "VirtualTouchscreen.h"

#include <android-base/logging.h>
#include <errno.h>
#include <fcntl.h>
#include <string.h>
#include <unistd.h>

namespace gammapad {

static constexpr const char* kPhysId = "gammapad-touch";

VirtualTouchscreen::VirtualTouchscreen()
    : mFd(-1),
      mScreenW(0), mScreenH(0),
      mNextTrackId(60000) {
    memset(mSlotTouching, 0, sizeof(mSlotTouching));
    memset(mSlotTrackId, 0, sizeof(mSlotTrackId));
}

VirtualTouchscreen::~VirtualTouchscreen() {
    destroy();
}

bool VirtualTouchscreen::create(int screenW, int screenH) {
    mScreenW = screenW;
    mScreenH = screenH;

    mFd = open("/dev/uinput", O_RDWR | O_NONBLOCK);
    if (mFd < 0) {
        LOG(ERROR) << "VirtualTouchscreen: failed to open /dev/uinput: " << strerror(errno);
        return false;
    }

    // Enable event types
    if (ioctl(mFd, UI_SET_EVBIT, EV_ABS) < 0) goto fail;
    if (ioctl(mFd, UI_SET_EVBIT, EV_KEY) < 0) goto fail;

    // Multitouch protocol B axes
    if (ioctl(mFd, UI_SET_ABSBIT, ABS_MT_SLOT) < 0) goto fail;
    if (ioctl(mFd, UI_SET_ABSBIT, ABS_MT_TRACKING_ID) < 0) goto fail;
    if (ioctl(mFd, UI_SET_ABSBIT, ABS_MT_POSITION_X) < 0) goto fail;
    if (ioctl(mFd, UI_SET_ABSBIT, ABS_MT_POSITION_Y) < 0) goto fail;
    if (ioctl(mFd, UI_SET_ABSBIT, ABS_MT_PRESSURE) < 0) goto fail;
    if (ioctl(mFd, UI_SET_ABSBIT, ABS_MT_TOUCH_MAJOR) < 0) goto fail;
    if (ioctl(mFd, UI_SET_ABSBIT, ABS_MT_WIDTH_MAJOR) < 0) goto fail;

    // BTN_TOUCH and BTN_TOOL_FINGER required for touch devices
    if (ioctl(mFd, UI_SET_KEYBIT, BTN_TOUCH) < 0) goto fail;
    if (ioctl(mFd, UI_SET_KEYBIT, BTN_TOOL_FINGER) < 0) goto fail;

    // INPUT_PROP_DIRECT marks this as a direct-input touchscreen (not a touchpad)
    if (ioctl(mFd, UI_SET_PROPBIT, INPUT_PROP_DIRECT) < 0) goto fail;

    {
        struct uinput_abs_setup abs_setup = {};

        // ABS_MT_SLOT: support up to MAX_SLOTS simultaneous touches.
        // max must be > 0 for Android InputReader to use Protocol B
        // (slot-based multitouch). With max=0, InputReader falls back to
        // Protocol A which resets slot data on every SYN_REPORT.
        abs_setup.code = ABS_MT_SLOT;
        abs_setup.absinfo.minimum = 0;
        abs_setup.absinfo.maximum = MAX_SLOTS - 1;
        if (ioctl(mFd, UI_ABS_SETUP, &abs_setup) < 0) goto fail;

        // ABS_MT_TRACKING_ID
        abs_setup.code = ABS_MT_TRACKING_ID;
        abs_setup.absinfo.minimum = 0;
        abs_setup.absinfo.maximum = 65535;
        if (ioctl(mFd, UI_ABS_SETUP, &abs_setup) < 0) goto fail;

        // ABS_MT_POSITION_X
        abs_setup.code = ABS_MT_POSITION_X;
        abs_setup.absinfo.minimum = 0;
        abs_setup.absinfo.maximum = mScreenW - 1;
        abs_setup.absinfo.resolution = mScreenW / 5;
        if (ioctl(mFd, UI_ABS_SETUP, &abs_setup) < 0) goto fail;

        // ABS_MT_POSITION_Y
        abs_setup.code = ABS_MT_POSITION_Y;
        abs_setup.absinfo.minimum = 0;
        abs_setup.absinfo.maximum = mScreenH - 1;
        abs_setup.absinfo.resolution = mScreenH / 5;
        if (ioctl(mFd, UI_ABS_SETUP, &abs_setup) < 0) goto fail;

        // ABS_MT_PRESSURE
        abs_setup.code = ABS_MT_PRESSURE;
        abs_setup.absinfo.minimum = 0;
        abs_setup.absinfo.maximum = 255;
        abs_setup.absinfo.resolution = 0;
        if (ioctl(mFd, UI_ABS_SETUP, &abs_setup) < 0) goto fail;

        // ABS_MT_TOUCH_MAJOR (contact area)
        abs_setup.code = ABS_MT_TOUCH_MAJOR;
        abs_setup.absinfo.minimum = 0;
        abs_setup.absinfo.maximum = 255;
        abs_setup.absinfo.resolution = 0;
        if (ioctl(mFd, UI_ABS_SETUP, &abs_setup) < 0) goto fail;

        // ABS_MT_WIDTH_MAJOR (approaching tool width)
        abs_setup.code = ABS_MT_WIDTH_MAJOR;
        abs_setup.absinfo.minimum = 0;
        abs_setup.absinfo.maximum = 255;
        abs_setup.absinfo.resolution = 0;
        if (ioctl(mFd, UI_ABS_SETUP, &abs_setup) < 0) goto fail;

        struct uinput_setup setup = {};
        strncpy(setup.name, "GammaOS Virtual Touchscreen", UINPUT_MAX_NAME_SIZE - 1);
        setup.id.bustype = BUS_VIRTUAL;
        setup.id.vendor = 0x045e;
        setup.id.product = 0x0F02;
        setup.id.version = 0x0100;

        ioctl(mFd, UI_SET_PHYS, kPhysId);

        if (ioctl(mFd, UI_DEV_SETUP, &setup) < 0) {
            LOG(ERROR) << "VirtualTouchscreen: UI_DEV_SETUP failed: " << strerror(errno);
            goto fail;
        }

        if (ioctl(mFd, UI_DEV_CREATE) < 0) {
            LOG(ERROR) << "VirtualTouchscreen: UI_DEV_CREATE failed: " << strerror(errno);
            goto fail;
        }
    }

    LOG(INFO) << "VirtualTouchscreen: created virtual device (" << mScreenW << "x" << mScreenH
              << ", " << MAX_SLOTS << " slots)";
    return true;

fail:
    LOG(ERROR) << "VirtualTouchscreen: device setup failed: " << strerror(errno);
    close(mFd);
    mFd = -1;
    return false;
}

void VirtualTouchscreen::destroy() {
    if (mFd >= 0) {
        // Release all active touches
        for (int i = 0; i < MAX_SLOTS; i++) {
            if (mSlotTouching[i]) {
                touchUp(i);
            }
        }
        ioctl(mFd, UI_DEV_DESTROY);
        close(mFd);
        mFd = -1;
        LOG(INFO) << "VirtualTouchscreen destroyed";
    }
}

void VirtualTouchscreen::sendEvent(int type, int code, int value) {
    if (mFd < 0) return;
    struct input_event ev = {};
    ev.type = type;
    ev.code = code;
    ev.value = value;
    ssize_t ret = write(mFd, &ev, sizeof(ev));
    if (ret < 0) {
        LOG(ERROR) << "VirtualTouchscreen: write failed: " << strerror(errno)
                   << " type=" << type << " code=" << code << " value=" << value;
    }
}

void VirtualTouchscreen::sendSync() {
    sendEvent(EV_SYN, SYN_REPORT, 0);
}

void VirtualTouchscreen::updateBtnTouch() {
    bool anyTouching = isAnyTouching();
    // BTN_TOUCH/BTN_TOOL_FINGER reflect whether any finger is down
    sendEvent(EV_KEY, BTN_TOUCH, anyTouching ? 1 : 0);
    sendEvent(EV_KEY, BTN_TOOL_FINGER, anyTouching ? 1 : 0);
}

bool VirtualTouchscreen::isAnyTouching() const {
    for (int i = 0; i < MAX_SLOTS; i++) {
        if (mSlotTouching[i]) return true;
    }
    return false;
}

bool VirtualTouchscreen::isSlotTouching(int slot) const {
    if (slot < 0 || slot >= MAX_SLOTS) return false;
    return mSlotTouching[slot];
}

// --- Multi-slot methods ---

void VirtualTouchscreen::touchDown(int slot, int x, int y) {
    if (mFd < 0) return;
    if (slot < 0 || slot >= MAX_SLOTS) return;
    if (mSlotTouching[slot]) return; // already touching in this slot

    // Clamp to screen bounds
    if (x < 0) x = 0;
    if (y < 0) y = 0;
    if (x >= mScreenW) x = mScreenW - 1;
    if (y >= mScreenH) y = mScreenH - 1;

    // Increment tracking ID to ensure kernel sees a new touch
    mNextTrackId++;
    if (mNextTrackId > 65000) mNextTrackId = 60000;
    mSlotTrackId[slot] = mNextTrackId;

    sendEvent(EV_ABS, ABS_MT_SLOT, slot);
    sendEvent(EV_ABS, ABS_MT_TRACKING_ID, mSlotTrackId[slot]);
    sendEvent(EV_ABS, ABS_MT_POSITION_X, x);
    sendEvent(EV_ABS, ABS_MT_POSITION_Y, y);
    sendEvent(EV_ABS, ABS_MT_PRESSURE, 200);
    sendEvent(EV_ABS, ABS_MT_TOUCH_MAJOR, 40);
    sendEvent(EV_ABS, ABS_MT_WIDTH_MAJOR, 40);

    mSlotTouching[slot] = true;
    updateBtnTouch();
    sendSync();

    LOG(INFO) << "TouchDown slot=" << slot << " (" << x << "," << y
              << ") tid=" << mSlotTrackId[slot];
}

void VirtualTouchscreen::touchMove(int slot, int x, int y) {
    if (mFd < 0) return;
    if (slot < 0 || slot >= MAX_SLOTS) return;
    if (!mSlotTouching[slot]) return;

    if (x < 0) x = 0;
    if (y < 0) y = 0;
    if (x >= mScreenW) x = mScreenW - 1;
    if (y >= mScreenH) y = mScreenH - 1;

    sendEvent(EV_ABS, ABS_MT_SLOT, slot);
    sendEvent(EV_ABS, ABS_MT_POSITION_X, x);
    sendEvent(EV_ABS, ABS_MT_POSITION_Y, y);
    sendEvent(EV_ABS, ABS_MT_PRESSURE, 200);
    sendEvent(EV_ABS, ABS_MT_TOUCH_MAJOR, 40);
    sendEvent(EV_ABS, ABS_MT_WIDTH_MAJOR, 40);
    sendSync();
}

void VirtualTouchscreen::touchUp(int slot) {
    if (mFd < 0) return;
    if (slot < 0 || slot >= MAX_SLOTS) return;
    if (!mSlotTouching[slot]) return;

    sendEvent(EV_ABS, ABS_MT_SLOT, slot);
    sendEvent(EV_ABS, ABS_MT_TRACKING_ID, -1);

    mSlotTouching[slot] = false;
    updateBtnTouch();
    sendSync();

    // Reset the kernel's cached position for this slot to an impossible value.
    // This ensures the next touchDown's real coordinates won't be suppressed
    // by the kernel's duplicate ABS event filtering.
    sendEvent(EV_ABS, ABS_MT_SLOT, slot);
    sendEvent(EV_ABS, ABS_MT_POSITION_X, -1);
    sendEvent(EV_ABS, ABS_MT_POSITION_Y, -1);
    sendEvent(EV_ABS, ABS_MT_PRESSURE, 0);
    sendEvent(EV_ABS, ABS_MT_TOUCH_MAJOR, 0);
    sendEvent(EV_ABS, ABS_MT_WIDTH_MAJOR, 0);
    sendSync();

    LOG(INFO) << "TouchUp slot=" << slot;
}

// --- Single-slot convenience methods (backward compat with MouseMode) ---

void VirtualTouchscreen::touchDown(int x, int y) {
    touchDown(0, x, y);
}

void VirtualTouchscreen::touchMove(int x, int y) {
    touchMove(0, x, y);
}

void VirtualTouchscreen::touchUp() {
    touchUp(0);
}

} // namespace gammapad
