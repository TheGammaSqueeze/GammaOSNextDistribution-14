#define LOG_TAG "gammapad"

#include "InputTransformer.h"

#include <android-base/logging.h>
#include <android-base/properties.h>
#include <chrono>
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
      mMouseModeActive(false),
      mDpadKeysToHat(false),
      mDpadUpHeld(false),
      mDpadDownHeld(false),
      mDpadLeftHeld(false),
      mDpadRightHeld(false) {
}

void InputTransformer::loadConfig() {
    using android::base::GetProperty;
    using android::base::GetIntProperty;

    mButtonRemap.clear();
    mAxisRemap.clear();
    mButtonToAxis.clear();
    mCalibration.clear();
    mAxisButtons.clear();
    mCombos.clear();
    mButtonActions.clear();
    // NB: do NOT clear mActionPend here - a button physically held across a
    // config reload keeps its in-flight hold state, so its release still fires
    // the (surviving) short action instead of being silently swallowed. Stale
    // entries for removed codes are inert (processButtonAction/checkActionTimeouts
    // both re-resolve the rule and skip codes with no current action).
    mPhysicalHeld.clear();
    mDpadUpHeld = mDpadDownHeld = mDpadLeftHeld = mDpadRightHeld = false;
    mVirtualHeld.clear();

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

    // Parse button-to-axis rules: "btn:axis[:value[:keep]],..." (e.g. "312:10,313:9").
    // A digital button emulates an analog axis - press emits <value> (default
    // 32767 = full trigger), release emits 0. Primary use: drive ABS_BRAKE (10)
    // and ABS_GAS (9) from the digital L2/R2 shoulder buttons for racing games.
    // Optional 4th field keep=1 also forwards the original digital button event,
    // so apps that read L2/R2 as a button keep working (default 0 = axis only).
    std::string btnAxis = GetProperty("persist.gammaos.gamepad.btn_axis", "");
    if (!btnAxis.empty()) {
        std::istringstream ss(btnAxis);
        std::string rule;
        while (std::getline(ss, rule, ',')) {
            int btn = 0, axis = -1, value = 32767, keep = 0;
            int n = sscanf(rule.c_str(), "%d:%d:%d:%d", &btn, &axis, &value, &keep);
            if (n >= 2 && btn > 0 && axis >= 0) {
                mButtonToAxis[btn] = {axis, value, (n >= 4 && keep != 0)};
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

    // Parse button combo mappings: "btn1+btn2=emit,btn1+btn2=emit,..."
    std::string comboStr = GetProperty("persist.gammaos.gamepad.combo_map", "");
    parseCombos(comboStr);

    // Parse per-button action rules: act_count + actN_code/hold/s/l.
    // Each action spec is "type=arg": key=<evdev>, app=<pkg>, act=<pkg/comp>,
    // prop=<name=value>, sh=<command>, or none.
    int actCount = GetIntProperty("persist.gammaos.gamepad.act_count", 0);
    for (int i = 0; i < actCount && i < 64; i++) {
        std::string prefix = "persist.gammaos.gamepad.act" + std::to_string(i);
        int code = GetIntProperty(prefix + "_code", 0);
        if (code <= 0) continue;
        int hold = GetIntProperty(prefix + "_hold", 0);
        std::string shortStr = GetProperty(prefix + "_s", "");
        std::string longStr = GetProperty(prefix + "_l", "");
        setButtonAction(code, hold, shortStr, longStr);
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
              << mCombos.size() << " combo mappings, "
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

std::set<int> InputTransformer::getButtonAxisCodes() const {
    std::set<int> codes;
    for (const auto& [btn, ax] : mButtonToAxis) {
        codes.insert(ax.axis);
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

        // 1a. Convert DPAD key events to HAT axis events when the source
        //     device has DPAD buttons but no HAT axes.
        if (mDpadKeysToHat && ev.value != 2 /* skip autorepeat */) {
            bool isDpad = true;
            if (ev.code == KEY_UP) mDpadUpHeld = (ev.value != 0);
            else if (ev.code == KEY_DOWN) mDpadDownHeld = (ev.value != 0);
            else if (ev.code == KEY_LEFT) mDpadLeftHeld = (ev.value != 0);
            else if (ev.code == KEY_RIGHT) mDpadRightHeld = (ev.value != 0);
            else isDpad = false;

            if (isDpad) {
                if (ev.code == KEY_UP || ev.code == KEY_DOWN) {
                    ev.type = EV_ABS;
                    ev.code = ABS_HAT0Y;
                    ev.value = (mDpadDownHeld ? 1 : 0) - (mDpadUpHeld ? 1 : 0);
                } else {
                    ev.type = EV_ABS;
                    ev.code = ABS_HAT0X;
                    ev.value = (mDpadRightHeld ? 1 : 0) - (mDpadLeftHeld ? 1 : 0);
                }
                return true;
            }
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

        // 2b. Button-to-axis: a digital button emulates an analog axis (e.g.
        //     L2/R2 -> ABS_BRAKE/ABS_GAS trigger). Convert this key event into an
        //     absolute-axis event in place: press -> value, release -> 0. Emitted
        //     directly (fixed trigger extents), bypassing stick calibration.
        if (!mButtonToAxis.empty()) {
            auto bax = mButtonToAxis.find(ev.code);
            if (bax != mButtonToAxis.end()) {
                // Optionally forward the original digital button too, so apps
                // that read L2/R2 as a button still see it. Emitted as an extra
                // event (the axis event goes out in-place below); a SYN flushes
                // the axis+button together as one frame.
                if (bax->second.keepButton) {
                    struct input_event btnEv = ev;   // EV_KEY, mapped code, press/release
                    mExtraEvents.push_back(btnEv);
                    struct input_event synEv{};
                    synEv.type = EV_SYN;
                    synEv.code = SYN_REPORT;
                    synEv.value = 0;
                    mExtraEvents.push_back(synEv);
                }
                ev.type = EV_ABS;
                ev.code = bax->second.axis;
                ev.value = (ev.value != 0) ? bax->second.value : 0;
                return true;
            }
        }

        // 2a. Per-button action rules (short/long press). A button that carries
        //     a rule is repurposed: its normal passthrough is suppressed and its
        //     short/long actions fire instead. processButtonAction returns true
        //     when it consumed the event (drop); false means forward normally.
        if (!mButtonActions.empty()) {
            auto ait = mButtonActions.find(ev.code);
            if (ait != mButtonActions.end()) {
                return !processButtonAction(ev, ait->second);
            }
        }

        // 3. Button combo processing (after all remapping, before blacklist)
        if (!mCombos.empty() && processCombo(ev)) {
            return false; // consumed by combo logic
        }

        // 4. Suppress blacklisted passthrough buttons
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
                                  code == ABS_GAS || code == ABS_BRAKE)
                                 && !mForceStickAxes.count(code);
            bool wasRemapped = (physicalCode != code);
            // Physical stick axes should never be treated as triggers,
            // even when .kl remaps them to a trigger code (e.g., ABS_RX→ABS_Z).
            bool isPhysicalStick = (physicalCode == ABS_X || physicalCode == ABS_Y ||
                                    physicalCode == ABS_RX || physicalCode == ABS_RY);

            if (isTriggerCode && pMin < 0 && pRange > 1000 && wasRemapped
                    && !isPhysicalStick) {
                // Bipolar trigger: .kl remapped a bipolar axis to a trigger code
                // (e.g., Xbox 360: ABS_Z→ABS_BRAKE). Rest at 0, active [0, pMax].
                if (value < 0) value = 0;
                if (pMax > 0) {
                    value = (int)((int64_t)value * 32767 / pMax);
                }
            } else if (isTriggerCode && pMin >= 0 && pRange > 2 && !isPhysicalStick) {
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

        // 4c. Circle-to-square expansion for paired stick axes.
        // Physical sticks have a circular gate so diagonals only reach ~70%
        // of each axis. Radially scale so the circle maps to the full square.
        {
            int partner = getStickPartner(code);
            if (partner >= 0 && mCalibration.count(code) && mCalibration.count(partner)) {
                mLastOutput[code] = value;
                auto partnerIt = mLastOutput.find(partner);
                if (partnerIt != mLastOutput.end()) {
                    float fx = static_cast<float>(value);
                    float fy = static_cast<float>(partnerIt->second);
                    float maxComp = std::max(std::abs(fx), std::abs(fy));
                    if (maxComp > 0.0f) {
                        float mag = std::sqrt(fx * fx + fy * fy);
                        float scale = mag / maxComp;
                        value = std::max(-32768, std::min(32767,
                                static_cast<int>(fx * scale)));
                    }
                }
            }
        }

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

void InputTransformer::parseCombos(const std::string& comboStr) {
    if (comboStr.empty()) return;

    std::istringstream ss(comboStr);
    std::string entry;
    while (std::getline(ss, entry, ',')) {
        // Format: "btn1+btn2=emit"
        size_t plus = entry.find('+');
        size_t eq = entry.find('=');
        if (plus == std::string::npos || eq == std::string::npos || plus >= eq) continue;

        int btn1 = std::atoi(entry.substr(0, plus).c_str());
        int btn2 = std::atoi(entry.substr(plus + 1, eq - plus - 1).c_str());
        int emit = std::atoi(entry.substr(eq + 1).c_str());

        if (btn1 > 0 && btn2 > 0 && emit > 0) {
            ComboMapping combo;
            combo.btn1 = btn1;
            combo.btn2 = btn2;
            combo.emitBtn = emit;
            combo.active = false;
            mCombos.push_back(combo);
            LOG(INFO) << "Combo: " << btn1 << "+" << btn2 << " -> " << emit;
        }
    }
}

bool InputTransformer::processCombo(struct input_event& ev) {
    if (ev.type != EV_KEY) return false;

    int code = ev.code;

    if (ev.value == 1) {
        // Button press
        mPhysicalHeld.insert(code);

        // Check if any combo is now complete
        for (auto& combo : mCombos) {
            if (combo.active) continue;
            if (mPhysicalHeld.count(combo.btn1) && mPhysicalHeld.count(combo.btn2)) {
                // Combo triggered! Recall any source buttons already on virtual pad
                if (mVirtualHeld.count(combo.btn1)) {
                    struct input_event rel = {};
                    rel.type = EV_KEY;
                    rel.code = combo.btn1;
                    rel.value = 0;
                    mExtraEvents.push_back(rel);
                    struct input_event syn = {};
                    syn.type = EV_SYN;
                    syn.code = SYN_REPORT;
                    mExtraEvents.push_back(syn);
                    mVirtualHeld.erase(combo.btn1);
                }
                if (mVirtualHeld.count(combo.btn2)) {
                    struct input_event rel = {};
                    rel.type = EV_KEY;
                    rel.code = combo.btn2;
                    rel.value = 0;
                    mExtraEvents.push_back(rel);
                    struct input_event syn = {};
                    syn.type = EV_SYN;
                    syn.code = SYN_REPORT;
                    mExtraEvents.push_back(syn);
                    mVirtualHeld.erase(combo.btn2);
                }

                // Emit target press
                struct input_event press = {};
                press.type = EV_KEY;
                press.code = combo.emitBtn;
                press.value = 1;
                mExtraEvents.push_back(press);
                struct input_event syn = {};
                syn.type = EV_SYN;
                syn.code = SYN_REPORT;
                mExtraEvents.push_back(syn);

                mVirtualHeld.insert(combo.emitBtn);
                combo.active = true;

                LOG(VERBOSE) << "Combo activated: " << combo.btn1 << "+"
                             << combo.btn2 << " -> " << combo.emitBtn;
                return true; // consume this press
            }
        }

        // No combo triggered, forward normally
        mVirtualHeld.insert(code);
        return false;

    } else if (ev.value == 0) {
        // Button release
        mPhysicalHeld.erase(code);

        // Check if this release deactivates any combo
        for (auto& combo : mCombos) {
            if (!combo.active) continue;
            if (code == combo.btn1 || code == combo.btn2) {
                // Deactivate combo, release target
                struct input_event rel = {};
                rel.type = EV_KEY;
                rel.code = combo.emitBtn;
                rel.value = 0;
                mExtraEvents.push_back(rel);
                struct input_event syn = {};
                syn.type = EV_SYN;
                syn.code = SYN_REPORT;
                mExtraEvents.push_back(syn);

                mVirtualHeld.erase(combo.emitBtn);
                combo.active = false;

                LOG(VERBOSE) << "Combo deactivated: " << combo.btn1 << "+"
                             << combo.btn2 << " -> " << combo.emitBtn;
                return true; // consume source release
            }
        }

        // Not part of any active combo
        if (mVirtualHeld.count(code)) {
            mVirtualHeld.erase(code);
            return false; // forward release normally
        }
        return true; // was recalled or consumed, don't forward

    } else {
        // Key repeat (value == 2) — suppress if part of active combo
        for (const auto& combo : mCombos) {
            if (combo.active && (code == combo.btn1 || code == combo.btn2)) {
                return true; // consume repeat
            }
        }
        return false; // forward repeat
    }
}

std::set<int> InputTransformer::getComboEmitCodes() const {
    std::set<int> codes;
    for (const auto& combo : mCombos) {
        codes.insert(combo.emitBtn);
    }
    return codes;
}

void InputTransformer::applyPerAppOverrides(const std::string& btnRemapStr,
                                             const std::string& comboMapStr) {
    // Merge per-app button remaps (per-app wins on conflict with global)
    if (!btnRemapStr.empty()) {
        std::istringstream ss(btnRemapStr);
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

    // Append per-app combos to global combos
    parseCombos(comboMapStr);

    LOG(INFO) << "Per-app overrides applied: "
              << mButtonRemap.size() << " total button remaps, "
              << mCombos.size() << " total combos";
}

// ---------------------------------------------------------------------------
// Button-action model (short/long press -> key/app/activity/prop/shell)
// ---------------------------------------------------------------------------

int64_t InputTransformer::nowMs() {
    return std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::steady_clock::now().time_since_epoch()).count();
}

ActionType InputTransformer::parseActionSpec(const std::string& spec, std::string& argOut) {
    argOut.clear();
    if (spec.empty()) return ACT_NONE;
    size_t eq = spec.find('=');
    std::string type = (eq == std::string::npos) ? spec : spec.substr(0, eq);
    if (eq != std::string::npos) argOut = spec.substr(eq + 1);
    if (type == "key")  return ACT_KEY;
    if (type == "app")  return ACT_APP;
    if (type == "act")  return ACT_ACTIVITY;
    if (type == "prop") return ACT_PROP;
    if (type == "sh")   return ACT_SHELL;
    if (type == "dpadswap") return ACT_DPAD_SWAP;
    return ACT_NONE;
}

void InputTransformer::setButtonAction(int code, int holdMs,
                                       const std::string& shortStr,
                                       const std::string& longStr) {
    if (code <= 0) return;
    ButtonAction a;
    a.shortType = parseActionSpec(shortStr, a.shortArg);
    a.longType = parseActionSpec(longStr, a.longArg);
    if (a.shortType == ACT_NONE && a.longType == ACT_NONE) {
        mButtonActions.erase(code);
        return;
    }
    a.holdMs = holdMs;
    if (a.longType != ACT_NONE && a.holdMs <= 0) a.holdMs = 400;  // sane default
    mButtonActions[code] = a;
    LOG(INFO) << "Button action: code=" << code
              << " short=" << (int)a.shortType << "[" << a.shortArg << "]"
              << " long=" << (int)a.longType << "[" << a.longArg << "]"
              << " hold=" << a.holdMs;
}

void InputTransformer::fireAction(ActionType type, const std::string& arg) {
    if (type == ACT_NONE) return;
    if (mActionCb) mActionCb((int)type, arg);
}

void InputTransformer::emitNormalTap(int code) {
    struct input_event press = {};
    press.type = EV_KEY; press.code = code; press.value = 1;
    struct input_event syn = {};
    syn.type = EV_SYN; syn.code = SYN_REPORT; syn.value = 0;
    struct input_event rel = press; rel.value = 0;
    mExtraEvents.push_back(press);
    mExtraEvents.push_back(syn);
    mExtraEvents.push_back(rel);
    mExtraEvents.push_back(syn);
}

bool InputTransformer::processButtonAction(struct input_event& ev, const ButtonAction& a) {
    int code = ev.code;
    if (a.shortType == ACT_NONE && a.longType == ACT_NONE) return false; // no-op rule

    bool deferred = (a.longType != ACT_NONE);

    if (ev.value == 2) return true;  // suppress autorepeat for action buttons

    if (ev.value == 1) {  // press
        ActionPendState& st = mActionPend[code];
        st.pending = true;
        st.longFired = false;
        st.pressTimeMs = nowMs();
        if (!deferred) {
            // No long action: fire short immediately on press.
            if (a.shortType != ACT_NONE) fireAction(a.shortType, a.shortArg);
            st.pending = false;
        }
        return true;  // consume press
    }

    if (ev.value == 0) {  // release
        auto it = mActionPend.find(code);
        if (it != mActionPend.end()) {
            ActionPendState& st = it->second;
            if (deferred && st.pending && !st.longFired) {
                // Released before the hold threshold -> this was a tap.
                if (a.shortType == ACT_NONE) emitNormalTap(code);
                else fireAction(a.shortType, a.shortArg);
            }
            st.pending = false;
            st.longFired = false;
        }
        return true;  // consume release
    }
    return true;
}

void InputTransformer::checkActionTimeouts() {
    if (mActionPend.empty()) return;
    int64_t now = nowMs();
    for (auto& [code, st] : mActionPend) {
        if (!st.pending || st.longFired) continue;
        auto ait = mButtonActions.find(code);
        if (ait == mButtonActions.end()) continue;
        const ButtonAction& a = ait->second;
        if (a.longType == ACT_NONE) continue;
        if (now - st.pressTimeMs >= a.holdMs) {
            fireAction(a.longType, a.longArg);
            st.longFired = true;  // keep pending until release so short won't also fire
        }
    }
}

int InputTransformer::nextActionTimeoutMs() {
    if (mActionPend.empty()) return -1;
    int64_t now = nowMs();
    int64_t best = -1;
    for (auto& [code, st] : mActionPend) {
        if (!st.pending || st.longFired) continue;
        auto ait = mButtonActions.find(code);
        if (ait == mButtonActions.end() || ait->second.longType == ACT_NONE) continue;
        int64_t remain = ait->second.holdMs - (now - st.pressTimeMs);
        if (remain < 0) remain = 0;
        if (best < 0 || remain < best) best = remain;
    }
    return best < 0 ? -1 : static_cast<int>(best);
}

std::set<int> InputTransformer::getActionKeyCodes() const {
    std::set<int> codes;
    for (const auto& [code, a] : mButtonActions) {
        if (a.shortType == ACT_KEY) {
            int c = std::atoi(a.shortArg.c_str());
            if (c > 0) codes.insert(c);
        }
        if (a.longType == ACT_KEY) {
            int c = std::atoi(a.longArg.c_str());
            if (c > 0) codes.insert(c);
        }
    }
    return codes;
}

} // namespace gammapad
