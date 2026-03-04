#pragma once

#include <linux/input.h>
#include <linux/uinput.h>
#include <unordered_map>

namespace gammapad {

struct PhysicalDevice;

// Wire protocol for PWM vibration bridge socket
struct __attribute__((packed)) VibrationMessage {
    uint32_t magic;     // 0x47504144 ("GPAD")
    uint16_t strong;    // Strong magnitude 0-65535
    uint16_t weak;      // Weak magnitude 0-65535
    uint32_t duration;  // Duration in ms (0 = stop)
};

static constexpr uint32_t VIBRATION_MAGIC = 0x47504144;

class ForceFeedback {
public:
    ForceFeedback();
    ~ForceFeedback();

    void loadConfig();

    // Handle FF upload request from uinput.
    // uinputFd: the virtual device fd
    // devices: map of physical devices for native FF forwarding
    void handleUpload(int uinputFd,
                      std::unordered_map<int, PhysicalDevice>& devices,
                      int requestId);

    // Handle FF erase request from uinput.
    void handleErase(int uinputFd,
                     std::unordered_map<int, PhysicalDevice>& devices,
                     int requestId);

    // Handle EV_FF play/stop event from uinput.
    void handlePlayStop(const struct input_event& ev,
                        std::unordered_map<int, PhysicalDevice>& devices);

    // Connect to the PWM vibration bridge socket.
    bool connectBridge();

    // Disconnect from the bridge.
    void disconnectBridge();

    // Cancel all active effects on a physical device (e.g., on disconnect).
    void cancelDevice(int physicalFd);

private:
    void sendPwmVibration(uint16_t strong, uint16_t weak, uint32_t durationMs);
    int findPhysicalWithFF(std::unordered_map<int, PhysicalDevice>& devices);

    bool mPwmEnabled;
    int mPwmIntensity;  // 0-255
    int mBridgeFd;

    // Virtual effect ID -> effect details for PWM path
    struct VirtualEffect {
        struct ff_effect effect;
        bool playing;
    };
    std::unordered_map<int, VirtualEffect> mEffects;
};

} // namespace gammapad
