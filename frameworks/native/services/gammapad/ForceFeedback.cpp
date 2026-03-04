#define LOG_TAG "gammapad"

#include "ForceFeedback.h"
#include "GamepadManager.h"

#include <android-base/logging.h>
#include <android-base/properties.h>
#include <errno.h>
#include <stddef.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>

namespace gammapad {

static constexpr const char* BRIDGE_SOCKET_NAME = "gammapad_vibrate";
static constexpr int MAX_EFFECTS = 16;

ForceFeedback::ForceFeedback()
    : mPwmEnabled(true),
      mPwmIntensity(255),
      mBridgeFd(-1) {
}

ForceFeedback::~ForceFeedback() {
    disconnectBridge();
}

void ForceFeedback::loadConfig() {
    using android::base::GetIntProperty;

    mPwmEnabled = GetIntProperty("persist.gammaos.gamepad.pwm_enable", 1) != 0;
    mPwmIntensity = GetIntProperty("persist.gammaos.gamepad.pwm_intensity", 255);
    if (mPwmIntensity < 0) mPwmIntensity = 0;
    if (mPwmIntensity > 255) mPwmIntensity = 255;

    LOG(INFO) << "ForceFeedback config: pwm=" << mPwmEnabled
              << " intensity=" << mPwmIntensity;
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

        sendPwmVibration(strong, weak, duration);
    } else if (!play) {
        // Stop vibration
        LOG(INFO) << "FF PWM stop";
        sendPwmVibration(0, 0, 0);
    }
}

void ForceFeedback::cancelDevice(int physicalFd) {
    auto devIt = mEffects.begin();
    while (devIt != mEffects.end()) {
        devIt->second.playing = false;
        ++devIt;
    }
    // Send stop to bridge
    sendPwmVibration(0, 0, 0);
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

int ForceFeedback::findPhysicalWithFF(
        std::unordered_map<int, PhysicalDevice>& devices) {
    for (auto& [fd, dev] : devices) {
        if (dev.hasFF) return fd;
    }
    return -1;
}

} // namespace gammapad
