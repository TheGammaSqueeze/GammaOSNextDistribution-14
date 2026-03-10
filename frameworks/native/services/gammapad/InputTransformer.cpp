#define LOG_TAG "gammapad"

#include "InputTransformer.h"

#include <android-base/logging.h>
#include <android-base/properties.h>
#include <cmath>
#include <cstdlib>
#include <sstream>
#include <algorithm>

namespace gammapad {

InputTransformer::InputTransformer()
    : mAnalogToDpad(false),
      mDpadToAnalog(false),
      mDpadThreshold(16384),
      mAbxySwap(false),
      mInvertLeft(false),
      mInvertRight(false),
      mGlobalSensitivity(0),
      mMouseModeActive(false) {
}

void InputTransformer::loadConfig() {
    using android::base::GetProperty;
    using android::base::GetIntProperty;

    mButtonRemap.clear();
    mAxisRemap.clear();
    mCalibration.clear();
    mAxisButtons.clear();

    // Parse button remaps: "from1:to1,from2:to2,..."
    std::string btnRemap = GetProperty("persist.gammaos.gamepad.remap_btn", "");
    if (!btnRemap.empty()) {
        std::istringstream ss(btnRemap);
        std::string pair;
        while (std::getline(ss, pair, ',')) {
            size_t colon = pair.find(':');
            if (colon != std::string::npos) {
                int from = std::atoi(pair.substr(0, colon).c_str());
                int to = std::atoi(pair.substr(colon + 1).c_str());
                if (from != 0 && to != 0) {
                    mButtonRemap[from] = to;
                }
            }
        }
    }

    // Parse axis remaps: "from1:to1,from2:to2,..."
    std::string axisRemap = GetProperty("persist.gammaos.gamepad.remap_axis", "");
    if (!axisRemap.empty()) {
        std::istringstream ss(axisRemap);
        std::string pair;
        while (std::getline(ss, pair, ',')) {
            size_t colon = pair.find(':');
            if (colon != std::string::npos) {
                int from = std::atoi(pair.substr(0, colon).c_str());
                int to = std::atoi(pair.substr(colon + 1).c_str());
                mAxisRemap[from] = to;
            }
        }
    }

    // Parse per-axis calibration: "center,min,max,deadzone,sensitivity,invert"
    static const int calibAxes[] = {
        ABS_X, ABS_Y, ABS_RX, ABS_RY, ABS_Z, ABS_RZ,
        ABS_GAS, ABS_BRAKE
    };
    for (int axis : calibAxes) {
        std::string key = "persist.gammaos.gamepad.cal_axis" + std::to_string(axis);
        std::string val = GetProperty(key, "");
        if (!val.empty()) {
            CalibrationData cal;
            if (sscanf(val.c_str(), "%d,%d,%d,%d,%f,%d",
                       &cal.center, &cal.min, &cal.max, &cal.deadzone,
                       &cal.sensitivity, reinterpret_cast<int*>(&cal.invert)) >= 4) {
                mCalibration[axis] = cal;
            }
        }
    }

    // Parse passthrough button blacklist: comma-separated hex scan codes
    mBlacklistPass.clear();
    std::string blacklistPassStr = GetProperty("persist.gammaos.gamepad.blacklist_pass", "");
    if (!blacklistPassStr.empty()) {
        std::istringstream bps(blacklistPassStr);
        std::string tok;
        while (std::getline(bps, tok, ',')) {
            if (!tok.empty()) {
                int code = (int)strtol(tok.c_str(), nullptr, 0);
                if (code > 0) mBlacklistPass.insert(code);
            }
        }
    }

    mAnalogToDpad = GetIntProperty("persist.gammaos.gamepad.analog_to_dpad", 0) != 0;
    mDpadToAnalog = GetIntProperty("persist.gammaos.gamepad.dpad_to_analog", 0) != 0;

    int threshold = GetIntProperty("persist.gammaos.gamepad.dpad_threshold", 50);
    // Convert percentage (0-100) to absolute value (0-32767)
    mDpadThreshold = (threshold * 32767) / 100;

    // Parse axis-to-button triggers: "axis:btn:on%:off%[:mode],..."
    // mode: 'h' = hijack (suppress ABS), 'b' = broadcast (emit both). Default: 'b'.
    // Example: "2:312:80:60:h,5:313:80:60:b"
    std::string axisBtnStr = GetProperty("persist.gammaos.gamepad.axis_btn", "");
    if (!axisBtnStr.empty()) {
        std::istringstream ss(axisBtnStr);
        std::string entry;
        while (std::getline(ss, entry, ',')) {
            int axis, btn, onPct, offPct;
            char mode = 'b';
            int parsed = sscanf(entry.c_str(), "%d:%d:%d:%d:%c",
                                &axis, &btn, &onPct, &offPct, &mode);
            if (parsed >= 4) {
                AxisButtonTrigger trigger;
                trigger.axisCode = axis;
                trigger.buttonCode = btn;
                // Store percentages; actual thresholds computed when we know axis range
                trigger.thresholdOn = onPct;
                trigger.thresholdOff = offPct;
                trigger.axisMin = 0;
                trigger.axisMax = 0;
                trigger.pressed = false;
                trigger.hijack = (mode == 'h' || mode == 'H');
                mAxisButtons.push_back(trigger);
                LOG(INFO) << "Axis-to-button: axis=" << axis << " btn=" << btn
                          << " on=" << onPct << "% off=" << offPct << "%"
                          << " mode=" << (trigger.hijack ? "hijack" : "broadcast");
            }
        }
    }

    // Global quick-access toggles
    mAbxySwap = GetIntProperty("persist.gammaos.gamepad.abxy_swap", 0) != 0;
    mInvertLeft = GetIntProperty("persist.gammaos.gamepad.invert_left", 0) != 0;
    mInvertRight = GetIntProperty("persist.gammaos.gamepad.invert_right", 0) != 0;
    mGlobalSensitivity = GetIntProperty("persist.gammaos.gamepad.global_sensitivity", 0);
    mGlobalSensitivity = std::max(-3, std::min(3, mGlobalSensitivity));

    LOG(INFO) << "InputTransformer config loaded: "
              << mButtonRemap.size() << " button remaps, "
              << mAxisRemap.size() << " axis remaps, "
              << mCalibration.size() << " axis calibrations, "
              << mAxisButtons.size() << " axis-to-button triggers, "
              << "blacklistPass=" << mBlacklistPass.size() << ", "
              << "a2d=" << mAnalogToDpad << " d2a=" << mDpadToAnalog << ", "
              << "abxySwap=" << mAbxySwap
              << " invertL=" << mInvertLeft << " invertR=" << mInvertRight
              << " globalSens=" << mGlobalSensitivity;
}

void InputTransformer::setDeviceMaps(const std::unordered_map<int, int>& absMap,
                                      const std::unordered_map<int, int>& keyMap) {
    mDeviceAbsMap = absMap;
    mDeviceKeyMap = keyMap;

    LOG(INFO) << "Device maps set: " << mDeviceAbsMap.size() << " abs, "
              << mDeviceKeyMap.size() << " key";
}

std::set<int> InputTransformer::getAxisButtonCodes() const {
    std::set<int> codes;
    for (const auto& trigger : mAxisButtons) {
        codes.insert(trigger.buttonCode);
    }
    return codes;
}

bool InputTransformer::transform(struct input_event& ev,
                                  const std::unordered_map<int, AxisInfo>& deviceAbsInfo) {
    if (ev.type == EV_KEY) {
        // 1. Apply .kl key mapping (physical scancode -> mapped code)
        auto klIt = mDeviceKeyMap.find(ev.code);
        if (klIt != mDeviceKeyMap.end()) {
            ev.code = klIt->second;
        }

        // 1b. ABXY swap (before user button remap so swaps stack correctly)
        if (mAbxySwap) {
            if (ev.code == BTN_A) ev.code = BTN_B;
            else if (ev.code == BTN_B) ev.code = BTN_A;
            else if (ev.code == BTN_X) ev.code = BTN_Y;
            else if (ev.code == BTN_Y) ev.code = BTN_X;
        }

        // 2. Apply user button remap
        auto it = mButtonRemap.find(ev.code);
        if (it != mButtonRemap.end()) {
            ev.code = it->second;
        }

        // 3. Suppress blacklisted passthrough buttons
        if (mBlacklistPass.count(ev.code)) {
            return false;
        }
        return true;
    }

    if (ev.type == EV_ABS) {
        int physicalCode = ev.code;
        int value = ev.value;

        // 1. Apply .kl axis mapping (physical scancode -> mapped code)
        // This replaces the old hardcoded ABS_GAS->ABS_RZ / ABS_BRAKE->ABS_Z
        auto klIt = mDeviceAbsMap.find(physicalCode);
        if (klIt != mDeviceAbsMap.end()) {
            ev.code = klIt->second;
        }
        int code = ev.code;

        // Drop HAT1X/HAT1Y — non-standard axes not present on real Xbox controllers
        if (code == ABS_HAT1X || code == ABS_HAT1Y) return false;

        // 2. Normalize raw value based on per-device physical absinfo.
        auto infoIt = deviceAbsInfo.find(physicalCode);
        if (infoIt != deviceAbsInfo.end()) {
            int pMin = infoIt->second.min;
            int pMax = infoIt->second.max;
            int pRange = pMax - pMin;

            bool isTriggerCode = (code == ABS_Z || code == ABS_RZ ||
                                  code == ABS_GAS || code == ABS_BRAKE);
            bool wasRemapped = (physicalCode != code);

            if (isTriggerCode && pMin < 0 && pRange > 1000 && wasRemapped) {
                // Bipolar trigger: .kl remapped a bipolar axis to a trigger code
                // (e.g., Xbox 360: ABS_Z→ABS_BRAKE). Rest at 0, active [0, pMax].
                if (value < 0) value = 0;
                if (pMax > 0) {
                    value = (int)((int64_t)value * 32767 / pMax);
                }
            } else if (isTriggerCode && pMin >= 0 && pRange > 2) {
                // Distinguish unsigned sticks from actual triggers:
                // Large range (>4096) identity-mapped on Z/RZ = unsigned stick axis,
                // not a trigger (e.g., Xbox BT right stick on Z/RZ with 0..65535)
                if (!wasRemapped && pRange > 4096) {
                    // Unsigned stick — normalize to signed 16-bit like standard axes
                    value = (int)((int64_t)(value - pMin) * 65535 / pRange) - 32768;
                } else {
                    // Unipolar trigger: normalize [pMin, pMax] to [0, 32767].
                    value = (int)((int64_t)(value - pMin) * 32767 / pRange);
                }
            } else if (pRange > 2) {
                // Standard axis: rescale full range to signed 16-bit
                value = (int)((int64_t)(value - pMin) * 65535 / pRange) - 32768;
            }
        }

        // 3. Process axis-to-button triggers (after normalization, before calibration)
        // Use mapped code (not physical scancode) so rules match virtual pad axis codes.
        // Skip in mouse mode — MouseMode handles all output directly.
        bool suppressAxis = false;
        if (!mMouseModeActive) {
            suppressAxis = processAxisButton(code, value);
        }

        // If a hijack-mode axis-to-button rule matched, suppress the ABS event
        if (suppressAxis) return false;

        // 4. Calibration (for stick axes)
        value = applyCalibration(code, value);

        // 4b. Stick inversion (after calibration so center offsets are correct)
        if (mInvertLeft && (code == ABS_X || code == ABS_Y)) value = -value;
        if (mInvertRight && (code == ABS_RX || code == ABS_RY ||
                              code == ABS_Z || code == ABS_RZ)) value = -value;

        // 4d. Global sensitivity (layered on top of calibration + inversion)
        // Skip in mouse mode — MouseMode has its own speed settings.
        if (!mMouseModeActive && mGlobalSensitivity != 0 &&
            (code == ABS_X || code == ABS_Y || code == ABS_RX || code == ABS_RY ||
             code == ABS_Z || code == ABS_RZ)) {
            // -3→50%, -2→75%, -1→90%, 0→100%, 1→110%, 2→125%, 3→150%
            static const float kSensTable[] = {0.50f, 0.75f, 0.90f, 1.0f, 1.10f, 1.25f, 1.50f};
            float mult = kSensTable[mGlobalSensitivity + 3];
            value = std::max(-32768, std::min(32767, static_cast<int>(value * mult)));
        }

        // 5. DPAD/analog swap
        // Skip in mouse mode — MouseMode needs raw stick and DPAD values separately.
        if (mMouseModeActive) {
            ev.value = value;
            return true;
        }

        if (mAnalogToDpad) {
            // Convert left stick to DPAD
            if (code == ABS_X) {
                ev.type = EV_ABS;
                ev.code = ABS_HAT0X;
                if (value > mDpadThreshold) ev.value = 1;
                else if (value < -mDpadThreshold) ev.value = -1;
                else ev.value = 0;
                return true;
            } else if (code == ABS_Y) {
                ev.type = EV_ABS;
                ev.code = ABS_HAT0Y;
                if (value > mDpadThreshold) ev.value = 1;
                else if (value < -mDpadThreshold) ev.value = -1;
                else ev.value = 0;
                return true;
            }
        }

        if (mDpadToAnalog) {
            // Convert DPAD to left stick
            if (code == ABS_HAT0X) {
                ev.code = ABS_X;
                ev.value = value * 32767;
                return true;
            } else if (code == ABS_HAT0Y) {
                ev.code = ABS_Y;
                ev.value = value * 32767;
                return true;
            }
        }

        ev.value = value;
        return true;
    }

    // Pass through SYN and other events
    return true;
}

bool InputTransformer::processAxisButton(int axisCode, int value) {
    bool suppress = false;
    for (auto& trigger : mAxisButtons) {
        if (trigger.axisCode != axisCode) continue;

        // Lazy-init: compute absolute thresholds from percentages.
        // Values arrive already normalized by the transform pipeline:
        //   triggers: 0..32767, sticks: -32768..32767
        if (trigger.axisMin == 0 && trigger.axisMax == 0) {
            if (axisCode == ABS_Z || axisCode == ABS_RZ ||
                axisCode == ABS_GAS || axisCode == ABS_BRAKE) {
                trigger.axisMin = 0;
                trigger.axisMax = 32767;
            } else {
                trigger.axisMin = -32768;
                trigger.axisMax = 32767;
            }
            int range = trigger.axisMax - trigger.axisMin;
            int onPct = trigger.thresholdOn;
            int offPct = trigger.thresholdOff;
            trigger.thresholdOn = trigger.axisMin + (range * onPct / 100);
            trigger.thresholdOff = trigger.axisMin + (range * offPct / 100);
            LOG(INFO) << "Axis-to-button init: axis=" << axisCode
                      << " range=[" << trigger.axisMin << "," << trigger.axisMax << "]"
                      << " threshOn=" << trigger.thresholdOn
                      << " threshOff=" << trigger.thresholdOff
                      << " hijack=" << trigger.hijack;
        }

        bool wantDown = trigger.pressed;
        if (value >= trigger.thresholdOn) {
            wantDown = true;
        } else if (value <= trigger.thresholdOff) {
            wantDown = false;
        }

        if (wantDown != trigger.pressed) {
            trigger.pressed = wantDown;

            // Emit key event
            struct input_event keyEv = {};
            keyEv.type = EV_KEY;
            keyEv.code = trigger.buttonCode;
            keyEv.value = wantDown ? 1 : 0;
            mExtraEvents.push_back(keyEv);

            // Emit SYN
            struct input_event synEv = {};
            synEv.type = EV_SYN;
            synEv.code = SYN_REPORT;
            synEv.value = 0;
            mExtraEvents.push_back(synEv);

            LOG(VERBOSE) << "Axis-to-button: axis=" << axisCode
                         << " val=" << value << " -> btn=" << trigger.buttonCode
                         << " " << (wantDown ? "PRESS" : "RELEASE");
        }

        if (trigger.hijack) suppress = true;
    }
    return suppress;
}

int InputTransformer::getStickPartner(int axis) {
    switch (axis) {
        case ABS_X:  return ABS_Y;
        case ABS_Y:  return ABS_X;
        case ABS_RX: return ABS_RY;
        case ABS_RY: return ABS_RX;
        case ABS_Z:  return ABS_RZ;
        case ABS_RZ: return ABS_Z;
        default:     return -1;
    }
}

int InputTransformer::applyCalibration(int axis, int value) {
    auto it = mCalibration.find(axis);
    if (it == mCalibration.end()) return value;

    const CalibrationData& cal = it->second;

    // Subtract center offset
    value -= cal.center;

    // Normalize to full range based on physical min/max.
    int posRange = cal.max - cal.center;
    int negRange = cal.center - cal.min;
    if (value > 0 && posRange > 0 && posRange < 32767) {
        value = (int)((int64_t)value * 32767 / posRange);
    } else if (value < 0 && negRange > 0 && negRange < 32767) {
        value = (int)((int64_t)value * 32767 / negRange);
    }

    // Store normalized value for circular deadzone partner lookup
    mLastNormalized[axis] = value;

    // Apply deadzone: circular for paired stick axes, per-axis for triggers
    int partner = getStickPartner(axis);
    if (partner >= 0 && mCalibration.count(partner)) {
        // Circular deadzone: use magnitude of both axes
        int partnerVal = 0;
        auto partnerIt = mLastNormalized.find(partner);
        if (partnerIt != mLastNormalized.end()) {
            partnerVal = partnerIt->second;
        }

        float fx = static_cast<float>(value);
        float fy = static_cast<float>(partnerVal);
        float mag = std::sqrt(fx * fx + fy * fy);
        float dz = static_cast<float>(cal.deadzone);

        if (mag < dz) {
            value = 0;
        } else if (mag > 0.0f) {
            // Scale remaining range so movement starts from 0 outside deadzone
            float newMag = (mag - dz) / (32767.0f - dz) * 32767.0f;
            value = static_cast<int>(fx / mag * newMag);
        }
    } else {
        // Per-axis deadzone for triggers and unpaired axes
        value = applyDeadzone(value, cal.deadzone);
    }

    // Apply inversion
    if (cal.invert) {
        value = -value;
    }

    // Apply sensitivity
    value = applySensitivity(value, cal.sensitivity);

    // Clamp to int16 range
    value = std::max(-32768, std::min(32767, value));

    return value;
}

int InputTransformer::applyDeadzone(int value, int deadzone) {
    if (deadzone <= 0) return value;

    if (value > -deadzone && value < deadzone) {
        return 0;
    }

    // Scale remaining range so movement starts from 0 outside deadzone
    if (value > 0) {
        return ((value - deadzone) * 32767) / (32767 - deadzone);
    } else {
        return ((value + deadzone) * 32767) / (32767 - deadzone);
    }
}

int InputTransformer::applySensitivity(int value, float sensitivity) {
    return static_cast<int>(value * sensitivity);
}

} // namespace gammapad
