#pragma once

#include <linux/input.h>
#include <string>
#include <unordered_map>

namespace gammapad {

struct AxisInfo {
    int min = -32768;
    int max = 32767;
    int fuzz = 0;
    int flat = 0;
};

class KeyLayoutParser {
public:
    // Parse .kl file for given vendor/product.
    // Tries: Vendor_XXXX_Product_YYYY.kl in /system/usr/keylayout,
    //        /vendor/usr/keylayout, then Generic.kl as fallback.
    // Populates absMap (scancode->final axis code) and keyMap (scancode->final key code).
    // Existing entries in absMap/keyMap are overwritten by .kl mappings.
    static bool parse(uint16_t vendor, uint16_t product,
                      std::unordered_map<int, int>& absMap,
                      std::unordered_map<int, int>& keyMap);

    // Resolve collisions: when two scancodes map to the same final axis,
    // prefer trigger scancodes (ABS_Z=0x02, ABS_RZ=0x05) or larger range.
    static void resolveAxisCollisions(
            std::unordered_map<int, int>& absMap,
            const std::unordered_map<int, AxisInfo>& absInfo);

    // Apply heuristic axis remapping for controllers without a .kl file.
    // Detects non-standard layouts where ABS_Z/ABS_RZ are bipolar sticks
    // and ABS_GAS/ABS_BRAKE are unipolar triggers, and remaps them to
    // standard Xbox layout (right stick on ABS_RX/ABS_RY, triggers on ABS_Z/ABS_RZ).
    static void applyHeuristicMapping(
            std::unordered_map<int, int>& absMap,
            const std::unordered_map<int, AxisInfo>& absInfo);

    // Normalize a device's axis layout to match the target preset PID.
    // Detects the device's native right-stick/trigger layout from absInfo
    // and remaps axis codes so all devices output in the same coordinate space.
    //   0x0b13 (Xbox BT native): right stick=Z/RZ, triggers=GAS/BRAKE
    //   0x02fd (Xbox 360 standard): right stick=RX/RY, triggers=Z/RZ
    static void normalizeToPreset(
            std::unordered_map<int, int>& absMap,
            const std::unordered_map<int, AxisInfo>& absInfo,
            int presetPid);

private:
    static void parseLine(const std::string& line,
                          std::unordered_map<int, int>& absMap,
                          std::unordered_map<int, int>& keyMap);

    static int axisNameToCode(const std::string& name);
    static int keyNameToCode(const std::string& name);
};

} // namespace gammapad
