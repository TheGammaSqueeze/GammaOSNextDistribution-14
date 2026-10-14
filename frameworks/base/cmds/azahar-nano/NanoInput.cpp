#define LOG_TAG "azahar-nano"

#include "NanoInput.h"

#include <utility>
#include "AzaharNanoApi.h"
#include "AzaharSettings.h"

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

bool NanoInput::start(const InputCallbacks& cb, bool dualPanel, int defaultPanel) {
    mCb = cb;
    mNintendoLayout = settings::getBool("persist.gammaos.azahar_nano.nintendo_layout", false);
    mSwapStickDpad = settings::getBool("persist.gammaos.azahar_nano.stick_dpad_swap", false);
    mDualPanel = dualPanel;
    mDefaultPanel = defaultPanel;
    char v[PROPERTY_VALUE_MAX] = {};
    property_get("persist.gammaos.nano.cc.touchdev", v, "");
    mBottomTouchName = v;
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
        // Which panel a digitizer sits on: on a two-panel device the bottom one is named by
        // the control centre's touchdev prop and any other is the top panel's.
        n.panel = mDefaultPanel;
        if (n.touch && mDualPanel && !mBottomTouchName.empty()) {
            n.panel = (mBottomTouchName == name) ? 1 : 0;
        }
        ALOGI("input node %s '%s' pad=%d touch=%d panel=%d", p.c_str(), name, n.pad, n.touch,
              n.panel);
        mNodes.push_back(n);
    }
    closedir(d);
}

namespace {
constexpr int kDirButton[4] = {AZN_DPAD_LEFT, AZN_DPAD_RIGHT, AZN_DPAD_UP, AZN_DPAD_DOWN};
} // namespace

// The swap applies while the menu is closed. A change (menu opened or closed, the option
// switched) first releases whatever was held under the old mapping, so nothing sticks.
bool NanoInput::swapActive() {
    const bool active = mSwapStickDpad && !(mCb.menuOpen && mCb.menuOpen());
    if (active != mSwapApplied) {
        for (int d = 0; d < 4; d++) {
            if (mStickDir[d] && mCb.button) mCb.button(kDirButton[d], false);
            mStickDir[d] = false;
            if (mDpadHeld[d] && mCb.button) mCb.button(kDirButton[d], false);
        }
        if (mCb.joystick) mCb.joystick(AZN_CIRCLEPAD, 0.0f, 0.0f);
        mSwapApplied = active;
        // Re-send what is held now under the new mapping.
        if (active) {
            leftStick();
            const float x = (mDpadHeld[kRight] ? 1.0f : 0.0f) - (mDpadHeld[kLeft] ? 1.0f : 0.0f);
            const float y = (mDpadHeld[kUp] ? 1.0f : 0.0f) - (mDpadHeld[kDown] ? 1.0f : 0.0f);
            if (mCb.joystick && (x != 0.0f || y != 0.0f)) mCb.joystick(AZN_CIRCLEPAD, x, y);
        } else {
            for (int d = 0; d < 4; d++) {
                if (mDpadHeld[d] && mCb.button) mCb.button(kDirButton[d], true);
            }
            if (mCb.joystick) mCb.joystick(AZN_CIRCLEPAD, mLx, -mLy);
        }
    }
    return active;
}

void NanoInput::sendDir(Dir d, bool pressed) {
    if (mCb.button) mCb.button(kDirButton[d], pressed);
}

// A physical d-pad direction (button or hat): the d-pad, or the circle pad when swapped.
void NanoInput::physicalDpad(Dir d, bool pressed) {
    if (mDpadHeld[d] == pressed) return;
    mDpadHeld[d] = pressed;
    if (!swapActive()) {
        sendDir(d, pressed);
        return;
    }
    const float x = (mDpadHeld[kRight] ? 1.0f : 0.0f) - (mDpadHeld[kLeft] ? 1.0f : 0.0f);
    const float y = (mDpadHeld[kUp] ? 1.0f : 0.0f) - (mDpadHeld[kDown] ? 1.0f : 0.0f);
    // A diagonal stays on the unit circle like a real stick at full tilt.
    const float k = (x != 0.0f && y != 0.0f) ? 0.70710678f : 1.0f;
    if (mCb.joystick) mCb.joystick(AZN_CIRCLEPAD, x * k, y * k);
}

// The left stick moved: the circle pad, or d-pad presses when swapped (pressed past half
// way, released below a third, so a stick resting near the threshold does not chatter).
void NanoInput::leftStick() {
    if (!swapActive()) {
        if (mCb.joystick) mCb.joystick(AZN_CIRCLEPAD, mLx, -mLy);
        return;
    }
    const float v[4] = {-mLx, mLx, -mLy, mLy};
    for (int d = 0; d < 4; d++) {
        const bool on = mStickDir[d] ? v[d] > 0.33f : v[d] > 0.5f;
        if (on != mStickDir[d]) {
            mStickDir[d] = on;
            sendDir((Dir)d, on);
        }
    }
}

void NanoInput::handlePad(Node& n, uint16_t type, uint16_t code, int32_t value) {
    if (type == EV_KEY) {
        const bool pressed = value != 0;
        int btn = -1;
        switch (code) {
        // The pad reports each button under its label's kernel code (BTN_A = south,
        // BTN_B = east, BTN_X = BTN_NORTH, BTN_Y = BTN_WEST), so the labelled X and
        // Y are X and Y; the Nintendo-layout option maps by position instead (east =
        // A, south = B, north = X, west = Y), the same as drastic-nano.
        case BTN_SOUTH: btn = mNintendoLayout ? AZN_BUTTON_B : AZN_BUTTON_A; break;
        case BTN_EAST: btn = mNintendoLayout ? AZN_BUTTON_A : AZN_BUTTON_B; break;
        case BTN_NORTH: btn = mNintendoLayout ? AZN_BUTTON_Y : AZN_BUTTON_X; break;
        case BTN_WEST: btn = mNintendoLayout ? AZN_BUTTON_X : AZN_BUTTON_Y; break;
        case BTN_TL: btn = AZN_TRIGGER_L; break;
        case BTN_TR: btn = AZN_TRIGGER_R; break;
        case BTN_TL2: btn = AZN_BUTTON_ZL; break;
        case BTN_TR2: btn = AZN_BUTTON_ZR; break;
        case BTN_START: btn = AZN_BUTTON_START; break;
        case BTN_SELECT: btn = AZN_BUTTON_SELECT; break;
        case BTN_DPAD_UP: physicalDpad(kUp, pressed); return;
        case BTN_DPAD_DOWN: physicalDpad(kDown, pressed); return;
        case BTN_DPAD_LEFT: physicalDpad(kLeft, pressed); return;
        case BTN_DPAD_RIGHT: physicalDpad(kRight, pressed); return;
        case BTN_THUMBR:
            // R3 toggles fast forward (the 3DS has no stick click to pass on).
            if (pressed && mCb.fastForwardToggle) mCb.fastForwardToggle();
            return;
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
    case ABS_X: mLx = norm; leftStick(); break;
    case ABS_Y: mLy = norm; leftStick(); break;
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
        if (mHatX != value) {
            if (mHatX < 0) physicalDpad(kLeft, false);
            if (mHatX > 0) physicalDpad(kRight, false);
            if (value < 0) physicalDpad(kLeft, true);
            if (value > 0) physicalDpad(kRight, true);
        }
        mHatX = value;
        break;
    case ABS_HAT0Y:
        if (mHatY != value) {
            if (mHatY < 0) physicalDpad(kUp, false);
            if (mHatY > 0) physicalDpad(kDown, false);
            if (value < 0) physicalDpad(kUp, true);
            if (value > 0) physicalDpad(kDown, true);
        }
        mHatY = value;
        break;
    default:
        break;
    }
}

void NanoInput::handleTouch(Node& n, uint16_t type, uint16_t code, int32_t value) {
    // Report the node's position as a fraction of its panel.
    const auto report = [&](bool edge) {
        const float x = n.tx, y = n.ty;
        if (edge) {
            if (mCb.touch) mCb.touch(n.panel, x, y, n.down);
        } else if (mCb.touchMoved) {
            mCb.touchMoved(n.panel, x, y);
        }
    };
    if (type == EV_ABS) {
        if (code == ABS_MT_POSITION_X || code == ABS_X) {
            const float r = (float)(n.absMax[code] - n.absMin[code]);
            n.tx = r > 0 ? (value - n.absMin[code]) / r : 0.0f;
            n.dirty = true;
        } else if (code == ABS_MT_POSITION_Y || code == ABS_Y) {
            const float r = (float)(n.absMax[code] - n.absMin[code]);
            n.ty = r > 0 ? (value - n.absMin[code]) / r : 0.0f;
            n.dirty = true;
        } else if (code == ABS_MT_TRACKING_ID) {
            const bool down = value >= 0;
            if (down != n.down) {
                n.down = down;
                report(true);
                n.dirty = false;
            }
        }
    } else if (type == EV_KEY && code == BTN_TOUCH) {
        const bool down = value != 0;
        if (down != n.down) {
            n.down = down;
            report(true);
            n.dirty = false;
        }
    } else if (type == EV_SYN && code == SYN_REPORT) {
        if (n.down && n.dirty) report(false);
        n.dirty = false;
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
