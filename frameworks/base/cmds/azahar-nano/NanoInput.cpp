#define LOG_TAG "azahar-nano"

#include "NanoInput.h"
#include "AzaharNanoApi.h"

#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <linux/input.h>
#include <poll.h>
#include <string.h>
#include <sys/inotify.h>
#include <time.h>
#include <unistd.h>

#include <cutils/properties.h>
#include <log/log.h>

namespace azahar_nano {

namespace {

int64_t nowNs() {
    timespec ts{};
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (int64_t)ts.tv_sec * 1000000000LL + ts.tv_nsec;
}

bool testBit(const unsigned long* bits, int bit) {
    return (bits[bit / (8 * sizeof(long))] >> (bit % (8 * sizeof(long)))) & 1UL;
}

constexpr int64_t kBackLongPressNs = 1500LL * 1000000LL;

} // namespace

bool NanoInput::start(const InputCallbacks& cb, uint32_t touchW, uint32_t touchH) {
    mCb = cb;
    mNintendoLayout = property_get_bool("persist.gammaos.azahar_nano.nintendo_layout", false);
    mTouchW = touchW;
    mTouchH = touchH;
    mInotify = inotify_init1(IN_NONBLOCK | IN_CLOEXEC);
    if (mInotify >= 0) {
        inotify_add_watch(mInotify, "/dev/input", IN_CREATE | IN_DELETE | IN_ATTRIB);
    }
    scan();
    mRun = true;
    mThread = std::thread([this] { loop(); });
    return true;
}

void NanoInput::stop() {
    mRun = false;
    if (mThread.joinable()) mThread.join();
    for (auto& n : mNodes) {
        if (n.fd >= 0) close(n.fd);
    }
    mNodes.clear();
    if (mInotify >= 0) {
        close(mInotify);
        mInotify = -1;
    }
}

void NanoInput::scan() {
    for (auto& n : mNodes) {
        if (n.fd >= 0) close(n.fd);
    }
    mNodes.clear();
    DIR* d = opendir("/dev/input");
    if (!d) return;
    while (dirent* e = readdir(d)) {
        if (strncmp(e->d_name, "event", 5) != 0) continue;
        std::string p = std::string("/dev/input/") + e->d_name;
        int fd = open(p.c_str(), O_RDONLY | O_NONBLOCK | O_CLOEXEC);
        if (fd < 0) continue;
        unsigned long ev[(EV_MAX + 1) / (8 * sizeof(long)) + 1] = {};
        unsigned long key[(KEY_MAX + 1) / (8 * sizeof(long)) + 1] = {};
        unsigned long abs[(ABS_MAX + 1) / (8 * sizeof(long)) + 1] = {};
        ioctl(fd, EVIOCGBIT(0, sizeof(ev)), ev);
        ioctl(fd, EVIOCGBIT(EV_KEY, sizeof(key)), key);
        ioctl(fd, EVIOCGBIT(EV_ABS, sizeof(abs)), abs);
        char name[128] = {};
        ioctl(fd, EVIOCGNAME(sizeof(name) - 1), name);
        Node n;
        n.fd = fd;
        n.path = p;
        const bool hasKeys = testBit(ev, EV_KEY);
        const bool hasAbs = testBit(ev, EV_ABS);
        n.pad = hasKeys && (testBit(key, BTN_SOUTH) || testBit(key, BTN_GAMEPAD) ||
                            testBit(key, BTN_START));
        n.touch = hasAbs && (testBit(abs, ABS_MT_POSITION_X) ||
                             (testBit(abs, ABS_X) && testBit(key, BTN_TOUCH) && !n.pad));
        // Back/menu-only devices (adc keys) count as pads for the exit key.
        if (!n.pad && hasKeys && (testBit(key, KEY_BACK) || testBit(key, KEY_HOMEPAGE))) {
            n.pad = true;
        }
        if (!n.pad && !n.touch) {
            close(fd);
            continue;
        }
        for (int a = 0; a < 64 && a <= ABS_MAX; a++) {
            if (!testBit(abs, a)) continue;
            input_absinfo ai{};
            if (ioctl(fd, EVIOCGABS(a), &ai) == 0) {
                n.hasAbs[a] = true;
                n.absMin[a] = ai.minimum;
                n.absMax[a] = ai.maximum;
            }
        }
        ALOGI("input node %s '%s' pad=%d touch=%d", p.c_str(), name, n.pad, n.touch);
        mNodes.push_back(n);
    }
    closedir(d);
}

void NanoInput::handlePad(Node& n, uint16_t type, uint16_t code, int32_t value) {
    if (type == EV_KEY) {
        const bool pressed = value != 0;
        int btn = -1;
        switch (code) {
        // The pad reports the Xbox arrangement (south = A, east = B, west = X,
        // north = Y), which is how the Duo Lite's buttons are labelled; the
        // Nintendo-layout option maps by position instead (east = A, south = B).
        case BTN_SOUTH: btn = mNintendoLayout ? AZN_BUTTON_B : AZN_BUTTON_A; break;
        case BTN_EAST: btn = mNintendoLayout ? AZN_BUTTON_A : AZN_BUTTON_B; break;
        case BTN_WEST: btn = mNintendoLayout ? AZN_BUTTON_Y : AZN_BUTTON_X; break;
        case BTN_NORTH: btn = mNintendoLayout ? AZN_BUTTON_X : AZN_BUTTON_Y; break;
        case BTN_TL: btn = AZN_TRIGGER_L; break;
        case BTN_TR: btn = AZN_TRIGGER_R; break;
        case BTN_TL2: btn = AZN_BUTTON_ZL; break;
        case BTN_TR2: btn = AZN_BUTTON_ZR; break;
        case BTN_START: btn = AZN_BUTTON_START; break;
        case BTN_SELECT: btn = AZN_BUTTON_SELECT; break;
        case BTN_DPAD_UP: btn = AZN_DPAD_UP; break;
        case BTN_DPAD_DOWN: btn = AZN_DPAD_DOWN; break;
        case BTN_DPAD_LEFT: btn = AZN_DPAD_LEFT; break;
        case BTN_DPAD_RIGHT: btn = AZN_DPAD_RIGHT; break;
        case BTN_MODE:
        case KEY_BACK:
        case KEY_HOMEPAGE:
            if (pressed) {
                mBackDownNs = nowNs();
                mBackLongFired = false;
                mBackCombo = false;
            } else {
                if (!mBackLongFired && mCb.menuToggle) mCb.menuToggle();
                mBackDownNs = 0;
            }
            return;
        default:
            return;
        }
        if (mCb.button) mCb.button(btn, pressed);
        return;
    }
    if (type != EV_ABS || code >= 64 || !n.hasAbs[code]) return;
    const float range = (float)(n.absMax[code] - n.absMin[code]);
    const float norm = range > 0 ? ((value - n.absMin[code]) / range) * 2.0f - 1.0f : 0.0f;
    const float unit = range > 0 ? (value - n.absMin[code]) / range : 0.0f;
    switch (code) {
    case ABS_X: mLx = norm; if (mCb.joystick) mCb.joystick(AZN_CIRCLEPAD, mLx, -mLy); break;
    case ABS_Y: mLy = norm; if (mCb.joystick) mCb.joystick(AZN_CIRCLEPAD, mLx, -mLy); break;
    case ABS_RX:
    case ABS_Z:
        if (code == ABS_Z && !n.hasAbs[ABS_RX]) {
            // Some pads report the left trigger on ABS_Z.
            if (mCb.analogButton) mCb.analogButton(AZN_BUTTON_ZL, unit);
            break;
        }
        mRx = norm; if (mCb.joystick) mCb.joystick(AZN_STICK_C, mRx, -mRy); break;
    case ABS_RY:
    case ABS_RZ:
        if (code == ABS_RZ && !n.hasAbs[ABS_RY]) {
            if (mCb.analogButton) mCb.analogButton(AZN_BUTTON_ZR, unit);
            break;
        }
        mRy = norm; if (mCb.joystick) mCb.joystick(AZN_STICK_C, mRx, -mRy); break;
    case ABS_BRAKE: if (mCb.analogButton) mCb.analogButton(AZN_BUTTON_ZL, unit); break;
    case ABS_GAS: if (mCb.analogButton) mCb.analogButton(AZN_BUTTON_ZR, unit); break;
    case ABS_HAT0X:
        if (mHatX != value && mCb.button) {
            if (mHatX < 0) mCb.button(AZN_DPAD_LEFT, false);
            if (mHatX > 0) mCb.button(AZN_DPAD_RIGHT, false);
            if (value < 0) mCb.button(AZN_DPAD_LEFT, true);
            if (value > 0) mCb.button(AZN_DPAD_RIGHT, true);
        }
        mHatX = value;
        break;
    case ABS_HAT0Y:
        if (mHatY != value && mCb.button) {
            if (mHatY < 0) mCb.button(AZN_DPAD_UP, false);
            if (mHatY > 0) mCb.button(AZN_DPAD_DOWN, false);
            if (value < 0) mCb.button(AZN_DPAD_UP, true);
            if (value > 0) mCb.button(AZN_DPAD_DOWN, true);
        }
        mHatY = value;
        break;
    default:
        break;
    }
}

void NanoInput::handleTouch(Node& n, uint16_t type, uint16_t code, int32_t value) {
    if (type == EV_ABS) {
        if (code == ABS_MT_POSITION_X || code == ABS_X) {
            const float r = (float)(n.absMax[code] - n.absMin[code]);
            mTx = r > 0 ? (int)((value - n.absMin[code]) / r * mTouchW) : value;
            mTouchDirty = true;
        } else if (code == ABS_MT_POSITION_Y || code == ABS_Y) {
            const float r = (float)(n.absMax[code] - n.absMin[code]);
            mTy = r > 0 ? (int)((value - n.absMin[code]) / r * mTouchH) : value;
            mTouchDirty = true;
        } else if (code == ABS_MT_TRACKING_ID) {
            const bool down = value >= 0;
            if (down != mTouchDown) {
                mTouchDown = down;
                if (mCb.touch) mCb.touch((float)mTx, (float)mTy, down);
                mTouchDirty = false;
            }
        }
    } else if (type == EV_KEY && code == BTN_TOUCH) {
        const bool down = value != 0;
        if (down != mTouchDown) {
            mTouchDown = down;
            if (mCb.touch) mCb.touch((float)mTx, (float)mTy, down);
            mTouchDirty = false;
        }
    } else if (type == EV_SYN && code == SYN_REPORT) {
        if (mTouchDown && mTouchDirty && mCb.touchMoved) mCb.touchMoved((float)mTx, (float)mTy);
        mTouchDirty = false;
    }
}

void NanoInput::loop() {
    while (mRun) {
        std::vector<pollfd> fds;
        for (auto& n : mNodes) fds.push_back({n.fd, POLLIN, 0});
        if (mInotify >= 0) fds.push_back({mInotify, POLLIN, 0});
        const int r = poll(fds.data(), fds.size(), 50);
        // Long press of the back key -> exit.
        if (mBackDownNs && !mBackLongFired && nowNs() - mBackDownNs > kBackLongPressNs) {
            mBackLongFired = true;
            if (mCb.exitRequest) mCb.exitRequest();
        }
        if (r <= 0) continue;
        bool rescan = false;
        for (size_t i = 0; i < fds.size(); i++) {
            if (!(fds[i].revents & (POLLIN | POLLERR | POLLHUP))) continue;
            if (mInotify >= 0 && fds[i].fd == mInotify) {
                char buf[1024];
                while (read(mInotify, buf, sizeof(buf)) > 0) {
                }
                rescan = true;
                continue;
            }
            if (fds[i].revents & (POLLERR | POLLHUP)) {
                rescan = true;
                continue;
            }
            Node& n = mNodes[i];
            input_event evs[32];
            ssize_t got;
            while ((got = read(n.fd, evs, sizeof(evs))) > 0) {
                const size_t cnt = got / sizeof(input_event);
                for (size_t k = 0; k < cnt; k++) {
                    if (n.pad) handlePad(n, evs[k].type, evs[k].code, evs[k].value);
                    if (n.touch) handleTouch(n, evs[k].type, evs[k].code, evs[k].value);
                }
            }
        }
        if (rescan) {
            usleep(200 * 1000);
            scan();
        }
    }
}

} // namespace azahar_nano
