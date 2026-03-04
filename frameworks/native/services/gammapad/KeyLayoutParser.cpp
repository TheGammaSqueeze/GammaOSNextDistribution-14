#define LOG_TAG "gammapad"

#include "KeyLayoutParser.h"

#include <android-base/logging.h>
#include <algorithm>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <fstream>
#include <set>
#include <sstream>
#include <vector>

namespace gammapad {

static const char* kBaseDirs[] = {
    "/system/usr/keylayout",
    "/vendor/usr/keylayout",
};

bool KeyLayoutParser::parse(uint16_t vendor, uint16_t product,
                            std::unordered_map<int, int>& absMap,
                            std::unordered_map<int, int>& keyMap) {
    // Try vendor/product-specific .kl first
    char filename[64];
    snprintf(filename, sizeof(filename), "Vendor_%04x_Product_%04x.kl",
             vendor, product);

    std::ifstream file;
    std::string klPath;

    for (const char* baseDir : kBaseDirs) {
        klPath = std::string(baseDir) + "/" + filename;
        file.open(klPath);
        if (file.is_open()) {
            LOG(INFO) << "KeyLayout: found " << klPath;
            break;
        }
    }

    // Fallback to Generic.kl
    if (!file.is_open()) {
        for (const char* baseDir : kBaseDirs) {
            klPath = std::string(baseDir) + "/Generic.kl";
            file.open(klPath);
            if (file.is_open()) {
                LOG(INFO) << "KeyLayout: using fallback " << klPath;
                break;
            }
        }
    }

    if (!file.is_open()) {
        LOG(INFO) << "KeyLayout: no .kl found for "
                  << std::hex << vendor << ":" << product;
        return false;
    }

    std::string line;
    int lineCount = 0;
    while (std::getline(file, line)) {
        parseLine(line, absMap, keyMap);
        lineCount++;
    }

    LOG(INFO) << "KeyLayout: parsed " << lineCount << " lines from " << klPath
              << " (absMap=" << absMap.size() << " keyMap=" << keyMap.size() << ")";
    return true;
}

void KeyLayoutParser::parseLine(const std::string& line,
                                std::unordered_map<int, int>& absMap,
                                std::unordered_map<int, int>& keyMap) {
    // Skip comments and empty lines
    size_t start = line.find_first_not_of(" \t");
    if (start == std::string::npos || line[start] == '#') return;

    std::istringstream ss(line);
    std::string type, scodeStr, name;
    ss >> type >> scodeStr >> name;

    if (type.empty() || scodeStr.empty() || name.empty()) return;

    // Parse scancode (hex or decimal)
    int sc = 0;
    if (scodeStr.size() > 2 &&
        (scodeStr[0] == '0' && (scodeStr[1] == 'x' || scodeStr[1] == 'X'))) {
        sc = static_cast<int>(strtol(scodeStr.c_str(), nullptr, 16));
    } else {
        sc = atoi(scodeStr.c_str());
    }
    if (sc < 0) return;

    if (strcasecmp(type.c_str(), "key") == 0) {
        if (sc > KEY_MAX) return;
        int finalCode = keyNameToCode(name);
        if (finalCode < 0) finalCode = sc; // fallback: identity
        keyMap[sc] = finalCode;
        LOG(VERBOSE) << "KeyLayout: key " << scodeStr << " " << name
                     << " => sc=" << sc << " final=" << finalCode;
    } else if (strcasecmp(type.c_str(), "axis") == 0) {
        if (sc > ABS_MAX) return;
        int finalCode = axisNameToCode(name);
        if (finalCode < 0) finalCode = sc; // fallback: identity
        absMap[sc] = finalCode;
        LOG(VERBOSE) << "KeyLayout: axis " << scodeStr << " " << name
                     << " => sc=" << sc << " final=" << finalCode;
    }
}

int KeyLayoutParser::axisNameToCode(const std::string& name) {
    if (strcasecmp(name.c_str(), "X") == 0) return ABS_X;
    if (strcasecmp(name.c_str(), "Y") == 0) return ABS_Y;
    if (strcasecmp(name.c_str(), "Z") == 0) return ABS_Z;
    if (strcasecmp(name.c_str(), "RX") == 0) return ABS_RX;
    if (strcasecmp(name.c_str(), "RY") == 0) return ABS_RY;
    if (strcasecmp(name.c_str(), "RZ") == 0) return ABS_RZ;
    if (strcasecmp(name.c_str(), "LTRIGGER") == 0) return ABS_BRAKE;
    if (strcasecmp(name.c_str(), "RTRIGGER") == 0) return ABS_GAS;
    if (strcasecmp(name.c_str(), "GAS") == 0) return ABS_GAS;
    if (strcasecmp(name.c_str(), "BRAKE") == 0) return ABS_BRAKE;
    if (strcasecmp(name.c_str(), "HAT_X") == 0) return ABS_HAT0X;
    if (strcasecmp(name.c_str(), "HAT_Y") == 0) return ABS_HAT0Y;
    if (strcasecmp(name.c_str(), "THROTTLE") == 0) return ABS_THROTTLE;
    if (strcasecmp(name.c_str(), "RUDDER") == 0) return ABS_RUDDER;
    if (strcasecmp(name.c_str(), "WHEEL") == 0) return ABS_WHEEL;
    return -1; // unknown → will use identity fallback
}

int KeyLayoutParser::keyNameToCode(const std::string& name) {
    if (strcasecmp(name.c_str(), "BUTTON_A") == 0) return BTN_A;
    if (strcasecmp(name.c_str(), "BUTTON_B") == 0) return BTN_B;
    if (strcasecmp(name.c_str(), "BUTTON_X") == 0) return BTN_X;
    if (strcasecmp(name.c_str(), "BUTTON_Y") == 0) return BTN_Y;
    if (strcasecmp(name.c_str(), "BUTTON_L1") == 0) return BTN_TL;
    if (strcasecmp(name.c_str(), "BUTTON_R1") == 0) return BTN_TR;
    if (strcasecmp(name.c_str(), "BUTTON_L2") == 0) return BTN_TL2;
    if (strcasecmp(name.c_str(), "BUTTON_R2") == 0) return BTN_TR2;
    if (strcasecmp(name.c_str(), "BUTTON_SELECT") == 0) return BTN_SELECT;
    if (strcasecmp(name.c_str(), "BUTTON_START") == 0) return BTN_START;
    if (strcasecmp(name.c_str(), "BUTTON_MODE") == 0) return BTN_MODE;
    if (strcasecmp(name.c_str(), "BUTTON_THUMBL") == 0) return BTN_THUMBL;
    if (strcasecmp(name.c_str(), "BUTTON_THUMBR") == 0) return BTN_THUMBR;
    if (strcasecmp(name.c_str(), "BUTTON_Z") == 0) return BTN_Z;
    if (strcasecmp(name.c_str(), "BUTTON_C") == 0) return BTN_C;
    if (strcasecmp(name.c_str(), "BUTTON_GAMEPAD") == 0) return BTN_GAMEPAD;
    if (strcasecmp(name.c_str(), "BUTTON_BACK") == 0) return BTN_BACK;
    if (strcasecmp(name.c_str(), "BUTTON_1") == 0) return BTN_1;
    if (strcasecmp(name.c_str(), "BUTTON_2") == 0) return BTN_2;
    if (strcasecmp(name.c_str(), "BUTTON_3") == 0) return BTN_3;
    if (strcasecmp(name.c_str(), "BUTTON_4") == 0) return BTN_4;
    if (strcasecmp(name.c_str(), "BACK") == 0) return KEY_BACK;
    if (strcasecmp(name.c_str(), "F10") == 0) return KEY_F10;
    return -1; // unknown → will use identity fallback
}

void KeyLayoutParser::resolveAxisCollisions(
        std::unordered_map<int, int>& absMap,
        const std::unordered_map<int, AxisInfo>& absInfo) {
    // Build reverse map: finalAxis -> (scancode, range)
    struct Winner {
        int scancode;
        int range;
    };
    std::unordered_map<int, Winner> finalUsed;

    // Track identity-mapped losers for potential reassignment
    // (identity = .kl didn't explicitly map this scancode, so absMap[sc] == sc)
    std::vector<int> pendingReassign;

    // Collect all entries to iterate (copy keys to avoid modifying while iterating)
    std::vector<int> scancodes;
    scancodes.reserve(absMap.size());
    for (const auto& [sc, finalAxis] : absMap) {
        scancodes.push_back(sc);
    }

    for (int sc : scancodes) {
        auto mapIt = absMap.find(sc);
        if (mapIt == absMap.end()) continue;
        int finalAxis = mapIt->second;
        if (finalAxis < 0 || finalAxis > ABS_MAX) continue;

        int range = 0;
        auto infoIt = absInfo.find(sc);
        if (infoIt != absInfo.end()) {
            range = infoIt->second.max - infoIt->second.min;
            if (range < 0) range = -range;
        }

        auto usedIt = finalUsed.find(finalAxis);
        if (usedIt == finalUsed.end()) {
            finalUsed[finalAxis] = {sc, range};
        } else {
            int oldSc = usedIt->second.scancode;
            int oldRange = usedIt->second.range;

            // Trigger scancodes (ABS_Z=0x02, ABS_RZ=0x05) get priority
            bool isNewTrigger = (sc == ABS_Z || sc == ABS_RZ);
            bool isOldTrigger = (oldSc == ABS_Z || oldSc == ABS_RZ);

            bool newWins = false;
            if (!isOldTrigger && isNewTrigger) {
                newWins = true;
            } else if (isOldTrigger && !isNewTrigger) {
                newWins = false;
            } else {
                // Both triggers or both not: bigger range wins
                newWins = (range > oldRange);
            }

            if (newWins) {
                LOG(INFO) << "KeyLayout: collision finalAxis=" << finalAxis
                          << " oldSc=" << oldSc << " replaced by sc=" << sc;
                // If the loser was identity-mapped, queue for reassignment
                if (oldSc == finalAxis) {
                    pendingReassign.push_back(oldSc);
                }
                absMap.erase(oldSc);
                usedIt->second = {sc, range};
            } else {
                LOG(INFO) << "KeyLayout: collision finalAxis=" << finalAxis
                          << " sc=" << sc << " overshadowed by oldSc=" << oldSc;
                // If the loser was identity-mapped, queue for reassignment
                if (sc == finalAxis) {
                    pendingReassign.push_back(sc);
                }
                absMap.erase(sc);
            }
        }
    }

    // Try to reassign identity-mapped collision losers to unused standard axes.
    // This handles pads where the .kl doesn't know about extra axes (e.g., Xbox 360
    // variant with right stick on ABS_GAS/ABS_BRAKE instead of ABS_RX/ABS_RY).
    if (!pendingReassign.empty()) {
        // Sort so lower scancodes get lower axis codes (e.g., sc=9→ABS_RX, sc=10→ABS_RY)
        std::sort(pendingReassign.begin(), pendingReassign.end());

        std::set<int> usedFinals;
        for (const auto& [sc, fa] : absMap) {
            if (fa >= 0 && fa <= ABS_MAX) usedFinals.insert(fa);
        }

        // Standard analog axes to try, in priority order
        static const int kCandidates[] = {
            ABS_RX, ABS_RY, ABS_X, ABS_Y, ABS_Z, ABS_RZ,
            ABS_GAS, ABS_BRAKE, ABS_THROTTLE, ABS_RUDDER,
        };

        for (int sc : pendingReassign) {
            bool reassigned = false;
            for (int candidate : kCandidates) {
                if (usedFinals.find(candidate) == usedFinals.end()) {
                    absMap[sc] = candidate;
                    usedFinals.insert(candidate);
                    LOG(INFO) << "KeyLayout: reassigned orphan axis sc=" << sc
                              << " to finalAxis=" << candidate;
                    reassigned = true;
                    break;
                }
            }
            if (!reassigned) {
                LOG(INFO) << "KeyLayout: no available axis for orphan sc=" << sc
                          << ", dropping";
            }
        }
    }
}

void KeyLayoutParser::applyHeuristicMapping(
        std::unordered_map<int, int>& absMap,
        const std::unordered_map<int, AxisInfo>& absInfo) {
    // Detect AYANEO-style layout:
    //   ABS_Z (sc=2) bipolar → right stick X (not a trigger)
    //   ABS_RZ (sc=5) bipolar → right stick Y (not a trigger)
    //   ABS_GAS (sc=9) unipolar → RT trigger
    //   ABS_BRAKE (sc=10) unipolar → LT trigger
    //
    // We only apply this when all four axes exist with the bipolar/unipolar pattern
    // AND the device doesn't have physical ABS_RX/ABS_RY axes.
    // Remap to standard Xbox layout:
    //   ABS_Z → ABS_RX, ABS_RZ → ABS_RY, ABS_BRAKE → ABS_Z, ABS_GAS → ABS_RZ

    // Check that all four axes exist with physical absinfo
    auto zInfo = absInfo.find(ABS_Z);
    auto rzInfo = absInfo.find(ABS_RZ);
    auto gasInfo = absInfo.find(ABS_GAS);
    auto brakeInfo = absInfo.find(ABS_BRAKE);

    if (zInfo == absInfo.end() || rzInfo == absInfo.end() ||
        gasInfo == absInfo.end() || brakeInfo == absInfo.end())
        return;

    // Check that ABS_Z and ABS_RZ are identity-mapped (not remapped by .kl)
    auto zIt = absMap.find(ABS_Z);
    auto rzIt = absMap.find(ABS_RZ);
    if (zIt == absMap.end() || rzIt == absMap.end()) return;
    if (zIt->second != ABS_Z || rzIt->second != ABS_RZ) return;

    // Check physical ranges: Z/RZ must be bipolar (sticks), GAS/BRAKE must be unipolar (triggers)
    bool zBipolar = (zInfo->second.min < 0);
    bool rzBipolar = (rzInfo->second.min < 0);
    bool gasUnipolar = (gasInfo->second.min >= 0);
    bool brakeUnipolar = (brakeInfo->second.min >= 0);

    if (!zBipolar || !rzBipolar || !gasUnipolar || !brakeUnipolar)
        return;

    // Ensure the device does NOT have physical ABS_RX/ABS_RY axes
    // (check absInfo which only contains axes actually present on the hardware)
    if (absInfo.count(ABS_RX) || absInfo.count(ABS_RY))
        return;

    LOG(INFO) << "Heuristic: detected AYANEO-style layout, remapping to Xbox standard";

    // Remove .kl-generated entries for ABS_RX/ABS_RY (from Generic.kl)
    // since the physical device doesn't have these scancodes
    absMap.erase(ABS_RX);
    absMap.erase(ABS_RY);

    // Remap: ABS_Z→ABS_RX, ABS_RZ→ABS_RY (right stick)
    absMap[ABS_Z] = ABS_RX;
    absMap[ABS_RZ] = ABS_RY;
    // Remap: ABS_BRAKE→ABS_Z (LT), ABS_GAS→ABS_RZ (RT)
    absMap[ABS_BRAKE] = ABS_Z;
    absMap[ABS_GAS] = ABS_RZ;

    LOG(INFO) << "Heuristic: ABS_Z->ABS_RX, ABS_RZ->ABS_RY, ABS_BRAKE->ABS_Z, ABS_GAS->ABS_RZ";
}

} // namespace gammapad
