#define LOG_TAG "gammapad"

#include "ForceFeedback.h"
#include "GamepadManager.h"

#include <android-base/logging.h>
#include <android-base/properties.h>
#include <algorithm>
#include <chrono>
#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <fstream>
#include <stddef.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>

namespace gammapad {

static constexpr const char* BRIDGE_SOCKET_NAME = "gammapad_vibrate";
static constexpr int MAX_EFFECTS = 16;

// Resolve a device name (e.g. "sc27xx:vibrator") to its /dev/input/eventN path
// by opening each event device and querying EVIOCGNAME.
// If the input already starts with '/', return it as-is (literal path).
static std::string resolveFFDevicePath(const std::string& nameOrPath) {
    if (nameOrPath.empty()) return "";
    if (nameOrPath[0] == '/') return nameOrPath;

    std::string result;
    for (int i = 0; i < 20; i++) {
        std::string devPath = "/dev/input/event" + std::to_string(i);
        int fd = open(devPath.c_str(), O_RDONLY);
        if (fd < 0) continue;

        char name[256] = {};
        if (ioctl(fd, EVIOCGNAME(sizeof(name)), name) >= 0) {
            // Trim trailing whitespace
            size_t len = strlen(name);
            while (len > 0 && (name[len-1] == '\n' || name[len-1] == ' '))
                name[--len] = '\0';
            if (nameOrPath == name) {
                result = devPath;
            }
        }
        close(fd);
        if (!result.empty()) break;
    }
    return result;
}

ForceFeedback::ForceFeedback()
    : mPwmEnabled(true),
      mPwmIntensity(255),
      mBridgeFd(-1),
      mDirectFFfd(-1),
      mDirectFFEffectId(-1) {
}

ForceFeedback::~ForceFeedback() {
    closeDirectFF();
    disconnectBridge();
}

void ForceFeedback::loadConfig() {
    using android::base::GetIntProperty;
    using android::base::GetProperty;

    mPwmEnabled = GetIntProperty("persist.gammaos.gamepad.pwm_enable", 1) != 0;
    mPwmIntensity = GetIntProperty("persist.gammaos.gamepad.pwm_intensity", 255);
    if (mPwmIntensity < 0) mPwmIntensity = 0;
    if (mPwmIntensity > 255) mPwmIntensity = 255;

    std::string ffDevice = GetProperty("persist.gammaos.gamepad.ff_vibrate_device", "");
    std::string newPath = resolveFFDevicePath(ffDevice);
    if (!ffDevice.empty() && newPath.empty()) {
        LOG(WARNING) << "Could not resolve FF device: " << ffDevice;
    }
    if (newPath != mDirectFFPath) {
        closeDirectFF();
        mDirectFFPath = newPath;
    }

    LOG(INFO) << "ForceFeedback config: pwm=" << mPwmEnabled
              << " intensity=" << mPwmIntensity
              << " directFF=" << (ffDevice.empty() ? "(none)" : ffDevice)
              << " resolved=" << (mDirectFFPath.empty() ? "(none)" : mDirectFFPath);
}

bool ForceFeedback::connectBridge() {
    if (mBridgeFd >= 0) return true;
    if (!mPwmEnabled) return false;

    mBridgeFd = socket(AF_UNIX, SOCK_STREAM | SOCK_NONBLOCK, 0);
    if (mBridgeFd < 0) {
        LOG(ERROR) << "Failed to create bridge socket: " << strerror(errno);
        return false;
    }

    struct sockaddr_un addr = {};
    addr.sun_family = AF_UNIX;
    // Abstract namespace socket: first byte is \0, then the name
    addr.sun_path[0] = '\0';
    strncpy(addr.sun_path + 1, BRIDGE_SOCKET_NAME, sizeof(addr.sun_path) - 2);
    socklen_t addrLen = offsetof(struct sockaddr_un, sun_path) + 1
                        + strlen(BRIDGE_SOCKET_NAME);

    if (connect(mBridgeFd, reinterpret_cast<struct sockaddr*>(&addr),
                addrLen) < 0) {
        LOG(WARNING) << "Failed to connect to vibration bridge: " << strerror(errno);
        close(mBridgeFd);
        mBridgeFd = -1;
        return false;
    }

    LOG(INFO) << "Connected to vibration bridge";
    return true;
}

void ForceFeedback::disconnectBridge() {
    if (mBridgeFd >= 0) {
        close(mBridgeFd);
        mBridgeFd = -1;
    }
}

void ForceFeedback::handleUpload(int uinputFd,
                                 std::unordered_map<int, PhysicalDevice>& devices,
                                 int requestId) {
    struct uinput_ff_upload upload = {};
    upload.request_id = requestId;

    // Read the upload request
    if (ioctl(uinputFd, UI_BEGIN_FF_UPLOAD, &upload) < 0) {
        LOG(ERROR) << "UI_BEGIN_FF_UPLOAD failed (req=" << requestId << "): " << strerror(errno);
        return;
    }

    upload.retval = 0;

    // Try native FF path first: find a physical device with FF support
    int physFd = findPhysicalWithFF(devices);
    if (physFd >= 0) {
        struct ff_effect physEffect = upload.effect;
        physEffect.id = -1;  // Let kernel assign ID

        if (ioctl(physFd, EVIOCSFF, &physEffect) >= 0) {
            // Track mapping: virtual effect ID -> physical
            auto& dev = devices[physFd];
            dev.ffEffects[upload.effect.id] = physEffect.id;
            LOG(VERBOSE) << "FF upload: virtual " << upload.effect.id
                         << " -> physical " << physEffect.id;
        } else {
            LOG(WARNING) << "Physical FF upload failed: " << strerror(errno);
        }
    }

    // Also store locally for PWM fallback path
    VirtualEffect ve;
    ve.effect = upload.effect;
    ve.playing = false;
    mEffects[upload.effect.id] = ve;
    LOG(INFO) << "FF upload stored: id=" << upload.effect.id
              << " type=" << upload.effect.type
              << " strong=" << upload.effect.u.rumble.strong_magnitude
              << " weak=" << upload.effect.u.rumble.weak_magnitude
              << " total_effects=" << mEffects.size();

    if (ioctl(uinputFd, UI_END_FF_UPLOAD, &upload) < 0) {
        LOG(ERROR) << "UI_END_FF_UPLOAD failed: " << strerror(errno);
    }
}

void ForceFeedback::handleErase(int uinputFd,
                                std::unordered_map<int, PhysicalDevice>& devices,
                                int requestId) {
    struct uinput_ff_erase erase = {};
    erase.request_id = requestId;

    if (ioctl(uinputFd, UI_BEGIN_FF_ERASE, &erase) < 0) {
        LOG(ERROR) << "UI_BEGIN_FF_ERASE failed (req=" << requestId << "): " << strerror(errno);
        return;
    }

    erase.retval = 0;

    // Erase from physical devices
    for (auto& [fd, dev] : devices) {
        auto it = dev.ffEffects.find(erase.effect_id);
        if (it != dev.ffEffects.end()) {
            ioctl(fd, EVIOCRMFF, it->second);
            dev.ffEffects.erase(it);
        }
    }

    // Erase from local tracking
    mEffects.erase(erase.effect_id);

    if (ioctl(uinputFd, UI_END_FF_ERASE, &erase) < 0) {
        LOG(ERROR) << "UI_END_FF_ERASE failed: " << strerror(errno);
    }
}

void ForceFeedback::handlePlayStop(const struct input_event& ev,
                                   std::unordered_map<int, PhysicalDevice>& devices) {
    int effectId = ev.code;
    bool play = (ev.value != 0);

    LOG(INFO) << "FF handlePlayStop: effect=" << effectId << " play=" << play
              << " tracked_effects=" << mEffects.size();

    // Forward to physical devices with native FF
    for (auto& [fd, dev] : devices) {
        if (!dev.hasFF) continue;
        auto it = dev.ffEffects.find(effectId);
        if (it != dev.ffEffects.end()) {
            struct input_event playEv = {};
            playEv.type = EV_FF;
            playEv.code = it->second;
            playEv.value = ev.value;
            write(fd, &playEv, sizeof(playEv));
            LOG(INFO) << "FF forwarded to physical device fd=" << fd;
            return;  // Native FF handled it
        }
    }

    // PWM fallback path
    auto it = mEffects.find(effectId);
    if (it == mEffects.end()) {
        LOG(WARNING) << "FF effect " << effectId << " not found in tracked effects";
        return;
    }

    VirtualEffect& ve = it->second;
    ve.playing = play;

    if (play && ve.effect.type == FF_RUMBLE) {
        uint16_t strong = ve.effect.u.rumble.strong_magnitude;
        uint16_t weak = ve.effect.u.rumble.weak_magnitude;
        uint32_t duration = ve.effect.replay.length;

        LOG(INFO) << "FF PWM rumble: strong=" << strong << " weak=" << weak
                  << " duration=" << duration;

        // Clamp duration for safety; allow longer effects for stronger feedback
        if (duration == 0 || duration > 1000) {
            duration = 1000;
        }

        if (!mDirectFFPath.empty()) {
            sendDirectFFVibration(strong, weak, duration);
        } else {
            sendPwmVibration(strong, weak, duration);
        }
    } else if (!play) {
        // Stop vibration
        LOG(INFO) << "FF PWM stop";
        if (!mDirectFFPath.empty()) {
            sendDirectFFVibration(0, 0, 0);
        } else {
            sendPwmVibration(0, 0, 0);
        }
    }
}

void ForceFeedback::cancelDevice(int physicalFd) {
    auto devIt = mEffects.begin();
    while (devIt != mEffects.end()) {
        devIt->second.playing = false;
        ++devIt;
    }
    // Send stop
    if (!mDirectFFPath.empty()) {
        sendDirectFFVibration(0, 0, 0);
    } else {
        sendPwmVibration(0, 0, 0);
    }
}

void ForceFeedback::sendPwmVibration(uint16_t strong, uint16_t weak,
                                     uint32_t durationMs) {
    if (!mPwmEnabled) {
        LOG(INFO) << "PWM disabled, skipping vibration";
        return;
    }

    // Connect on demand
    if (mBridgeFd < 0 && !connectBridge()) {
        LOG(WARNING) << "Cannot connect to vibration bridge";
        return;
    }

    // Scale magnitudes (0-65535) by intensity (0-255) to get bridge value (0-65535)
    strong = static_cast<uint16_t>((static_cast<uint32_t>(strong) * mPwmIntensity) / 255);
    weak = static_cast<uint16_t>((static_cast<uint32_t>(weak) * mPwmIntensity) / 255);

    // Enforce minimum vibration floor so PWM motors actually move
    if (strong > 0 && strong < 16384) strong = 16384;
    if (weak > 0 && weak < 8192) weak = 8192;

    VibrationMessage msg = {};
    msg.magic = VIBRATION_MAGIC;
    msg.strong = strong;
    msg.weak = weak;
    msg.duration = durationMs;

    LOG(INFO) << "Sending PWM vibration: strong=" << msg.strong
              << " weak=" << msg.weak << " duration=" << msg.duration
              << " bridge_fd=" << mBridgeFd;

    ssize_t n = send(mBridgeFd, &msg, sizeof(msg), MSG_NOSIGNAL);
    if (n < 0) {
        LOG(WARNING) << "Bridge send failed: " << strerror(errno);
        disconnectBridge();
    } else {
        LOG(INFO) << "Bridge send OK: " << n << " bytes";
    }
}

bool ForceFeedback::openDirectFF() {
    if (mDirectFFfd >= 0) return true;
    if (mDirectFFPath.empty()) return false;

    mDirectFFfd = open(mDirectFFPath.c_str(), O_RDWR);
    if (mDirectFFfd < 0) {
        LOG(WARNING) << "Failed to open direct FF device " << mDirectFFPath
                     << ": " << strerror(errno);
        return false;
    }

    // Verify the device supports FF_RUMBLE
    // Use uint8_t array to avoid unsigned long size ambiguity across architectures
    uint8_t ffBits[(FF_MAX / 8) + 1] = {};
    if (ioctl(mDirectFFfd, EVIOCGBIT(EV_FF, sizeof(ffBits)), ffBits) < 0) {
        LOG(WARNING) << "Cannot query FF capabilities: " << strerror(errno);
        close(mDirectFFfd);
        mDirectFFfd = -1;
        return false;
    }

    bool hasRumble = (ffBits[FF_RUMBLE / 8] >> (FF_RUMBLE % 8)) & 1;
    if (!hasRumble) {
        LOG(WARNING) << "Device " << mDirectFFPath << " does not support FF_RUMBLE"
                     << " (byte " << (FF_RUMBLE / 8) << " = 0x"
                     << std::hex << (int)ffBits[FF_RUMBLE / 8] << std::dec << ")";
        close(mDirectFFfd);
        mDirectFFfd = -1;
        return false;
    }

    // Upload a persistent FF_RUMBLE effect (kernel assigns ID)
    struct ff_effect effect = {};
    effect.type = FF_RUMBLE;
    effect.id = -1;
    effect.replay.length = 0;
    effect.replay.delay = 0;
    if (ioctl(mDirectFFfd, EVIOCSFF, &effect) < 0) {
        LOG(WARNING) << "Failed to upload FF effect: " << strerror(errno);
        close(mDirectFFfd);
        mDirectFFfd = -1;
        return false;
    }
    mDirectFFEffectId = effect.id;

    LOG(INFO) << "Direct FF opened: " << mDirectFFPath
              << " effectId=" << mDirectFFEffectId;
    return true;
}

void ForceFeedback::closeDirectFF() {
    stopDirectPwm();
    if (mDirectFFfd >= 0) {
        if (mDirectFFEffectId >= 0) {
            struct input_event ev = {};
            ev.type = EV_FF;
            ev.code = static_cast<uint16_t>(mDirectFFEffectId);
            ev.value = 0;
            write(mDirectFFfd, &ev, sizeof(ev));

            ioctl(mDirectFFfd, EVIOCRMFF, mDirectFFEffectId);
            mDirectFFEffectId = -1;
        }
        close(mDirectFFfd);
        mDirectFFfd = -1;
    }
}

void ForceFeedback::stopDirectPwm() {
    mDirectPwmStop.store(true, std::memory_order_relaxed);
    if (mDirectPwmThread.joinable()) {
        mDirectPwmThread.join();
    }
}

void ForceFeedback::directPwmLoop(int onUs, int offUs, int totalMs) {
    auto endTime = std::chrono::steady_clock::now()
                   + std::chrono::milliseconds(totalMs);

    struct input_event evOn = {};
    evOn.type = EV_FF;
    evOn.code = static_cast<uint16_t>(mDirectFFEffectId);
    evOn.value = 1;

    struct input_event evOff = {};
    evOff.type = EV_FF;
    evOff.code = static_cast<uint16_t>(mDirectFFEffectId);
    evOff.value = 0;

    // Upload a short-duration effect for PWM pulses
    struct ff_effect effect = {};
    effect.type = FF_RUMBLE;
    effect.id = mDirectFFEffectId;
    effect.u.rumble.strong_magnitude = 0xFFFF;
    effect.u.rumble.weak_magnitude = 0xFFFF;
    effect.replay.length = static_cast<uint16_t>((onUs + offUs) / 1000 + 2);
    effect.replay.delay = 0;
    ioctl(mDirectFFfd, EVIOCSFF, &effect);

    while (!mDirectPwmStop.load(std::memory_order_relaxed)
           && std::chrono::steady_clock::now() < endTime) {
        // ON
        write(mDirectFFfd, &evOn, sizeof(evOn));
        usleep(onUs);

        if (mDirectPwmStop.load(std::memory_order_relaxed)) break;

        // OFF
        write(mDirectFFfd, &evOff, sizeof(evOff));
        usleep(offUs);
    }

    // Ensure motor is off
    write(mDirectFFfd, &evOff, sizeof(evOff));
}

// PWM parameters matching the bridge path
static constexpr int DIRECT_PWM_PERIOD_US = 8000;  // 8ms
static constexpr int DIRECT_PWM_STEADY_THRESHOLD = 60000;  // ~92% of 65535

void ForceFeedback::sendDirectFFVibration(uint16_t strong, uint16_t weak,
                                          uint32_t durationMs) {
    if (!mPwmEnabled) return;

    // Open on demand
    if (mDirectFFfd < 0 && !openDirectFF()) {
        sendPwmVibration(strong, weak, durationMs);
        return;
    }

    // Apply intensity scaling
    strong = static_cast<uint16_t>((static_cast<uint32_t>(strong) * mPwmIntensity) / 255);
    weak = static_cast<uint16_t>((static_cast<uint32_t>(weak) * mPwmIntensity) / 255);

    // Stop command
    if (durationMs == 0 || (strong == 0 && weak == 0)) {
        stopDirectPwm();
        struct input_event ev = {};
        ev.type = EV_FF;
        ev.code = static_cast<uint16_t>(mDirectFFEffectId);
        ev.value = 0;
        write(mDirectFFfd, &ev, sizeof(ev));
        LOG(INFO) << "Direct FF stop";
        return;
    }

    // Enforce minimum vibration floor
    if (strong > 0 && strong < 16384) strong = 16384;
    if (weak > 0 && weak < 8192) weak = 8192;

    int magnitude = std::max(strong, weak);

    // High magnitude: steady vibration (no PWM needed)
    if (magnitude >= DIRECT_PWM_STEADY_THRESHOLD) {
        stopDirectPwm();

        struct ff_effect effect = {};
        effect.type = FF_RUMBLE;
        effect.id = mDirectFFEffectId;
        effect.u.rumble.strong_magnitude = strong;
        effect.u.rumble.weak_magnitude = weak;
        effect.replay.length = static_cast<uint16_t>(
                durationMs > 65535 ? 65535 : durationMs);
        effect.replay.delay = 0;

        if (ioctl(mDirectFFfd, EVIOCSFF, &effect) < 0) {
            LOG(WARNING) << "Direct FF effect update failed: " << strerror(errno);
            closeDirectFF();
            return;
        }

        struct input_event ev = {};
        ev.type = EV_FF;
        ev.code = static_cast<uint16_t>(mDirectFFEffectId);
        ev.value = 1;
        write(mDirectFFfd, &ev, sizeof(ev));

        LOG(INFO) << "Direct FF steady: strong=" << strong << " weak=" << weak
                  << " duration=" << durationMs;
        return;
    }

    // PWM: duty cycle based on magnitude
    int onUs = (magnitude * DIRECT_PWM_PERIOD_US) / 65535;
    int offUs = DIRECT_PWM_PERIOD_US - onUs;

    if (onUs <= 0) return;
    if (offUs <= 0) {
        // Full duty cycle — treat as steady
        stopDirectPwm();
        struct ff_effect effect = {};
        effect.type = FF_RUMBLE;
        effect.id = mDirectFFEffectId;
        effect.u.rumble.strong_magnitude = 0xFFFF;
        effect.u.rumble.weak_magnitude = 0xFFFF;
        effect.replay.length = static_cast<uint16_t>(
                durationMs > 65535 ? 65535 : durationMs);
        effect.replay.delay = 0;
        ioctl(mDirectFFfd, EVIOCSFF, &effect);

        struct input_event ev = {};
        ev.type = EV_FF;
        ev.code = static_cast<uint16_t>(mDirectFFEffectId);
        ev.value = 1;
        write(mDirectFFfd, &ev, sizeof(ev));
        return;
    }

    // Stop any existing PWM thread, then start a new one
    stopDirectPwm();
    mDirectPwmStop.store(false, std::memory_order_relaxed);
    mDirectPwmThread = std::thread(&ForceFeedback::directPwmLoop,
                                   this, onUs, offUs,
                                   static_cast<int>(durationMs));

    LOG(INFO) << "Direct FF PWM: magnitude=" << magnitude
              << " onUs=" << onUs << " offUs=" << offUs
              << " duration=" << durationMs;
}

void ForceFeedback::sendToast(const std::string& text) {
    // Connect on demand
    if (mBridgeFd < 0 && !connectBridge()) {
        LOG(WARNING) << "Cannot send toast: bridge not connected";
        return;
    }

    uint32_t magic = TOAST_MAGIC;
    uint16_t len = static_cast<uint16_t>(
        std::min(text.size(), static_cast<size_t>(1024)));

    // Send in one buffer to avoid partial writes
    size_t totalLen = 4 + 2 + len;
    std::vector<uint8_t> buf(totalLen);
    memcpy(buf.data(), &magic, 4);
    memcpy(buf.data() + 4, &len, 2);
    memcpy(buf.data() + 6, text.c_str(), len);

    ssize_t n = send(mBridgeFd, buf.data(), totalLen, MSG_NOSIGNAL);
    if (n < 0) {
        LOG(WARNING) << "Toast send failed: " << strerror(errno);
        disconnectBridge();
    } else {
        LOG(INFO) << "Toast sent: \"" << text << "\"";
    }
}

int ForceFeedback::findPhysicalWithFF(
        std::unordered_map<int, PhysicalDevice>& devices) {
    for (auto& [fd, dev] : devices) {
        if (dev.hasFF) return fd;
    }
    return -1;
}

} // namespace gammapad
