/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#define LOG_TAG "BatteryDefender"

#include <android-base/file.h>
#include <android-base/logging.h>
#include <android-base/parsebool.h>
#include <android-base/parseint.h>
#include <android-base/properties.h>
#include <cutils/klog.h>
#include <dirent.h>
#include <pixelhealth/BatteryDefender.h>
#include <pixelhealth/HealthHelper.h>
#include <stdio.h>
#include <sys/types.h>
#include <time.h>
#include <utils/Timers.h>

#include <unordered_map>

using aidl::android::hardware::health::BatteryHealth;
using aidl::android::hardware::health::HealthInfo;

namespace hardware {
namespace google {
namespace pixel {
namespace health {

BatteryDefender::BatteryDefender(const std::string pathWirelessPresent,
                                 const std::string pathChargeLevelStart,
                                 const std::string pathChargeLevelStop,
                                 const int32_t timeToActivateSecs,
                                 const int32_t timeToClearTimerSecs, const bool useTypeC)

    : mPathWirelessPresent(pathWirelessPresent),
      kPathChargeLevelStart(pathChargeLevelStart),
      kPathChargeLevelStop(pathChargeLevelStop),
      kTimeToActivateSecs(timeToActivateSecs),
      kTimeToClearTimerSecs(timeToClearTimerSecs),
      kUseTypeC(useTypeC) {
    mTimePreviousSecs = getTime();
}

void BatteryDefender::clearStateData(void) {
    mHasReachedHighCapacityLevel = false;
    mTimeActiveSecs = 0;
    mTimeChargerNotPresentSecs = 0;
    mTimeChargerPresentSecs = 0;
}

void BatteryDefender::setWirelessNotSupported(void) {
    mPathWirelessPresent = PATH_NOT_SUPPORTED;
}

void BatteryDefender::loadPersistentStorage(void) {
    if (mIsPowerAvailable) {
        // Load accumulated time from persisted storage
        mTimeChargerPresentSecs = readFileToInt(kPathPersistChargerPresentTime);
        mTimeActiveSecs = readFileToInt(kPathPersistDefenderActiveTime);
    }
}

int64_t BatteryDefender::getTime(void) {
    return nanoseconds_to_seconds(systemTime(SYSTEM_TIME_BOOTTIME));
}

int64_t BatteryDefender::getDeltaTimeSeconds(int64_t *timeStartSecs) {
    const int64_t timeCurrentSecs = getTime();
    const int64_t timePreviousSecs = *timeStartSecs;
    *timeStartSecs = timeCurrentSecs;
    return timeCurrentSecs - timePreviousSecs;
}

void BatteryDefender::removeLineEndings(std::string *str) {
    str->erase(std::remove(str->begin(), str->end(), '\n'), str->end());
    str->erase(std::remove(str->begin(), str->end(), '\r'), str->end());
}

int BatteryDefender::readFileToInt(const std::string &path, const bool silent) {
    std::string buffer;
    int value = 0;  // default

    if (path == PATH_NOT_SUPPORTED) {
        return value;
    }

    if (!android::base::ReadFileToString(path, &buffer)) {
        if (silent == false) {
            LOG(ERROR) << "Failed to read " << path;
        }
    } else {
        removeLineEndings(&buffer);
        if (!android::base::ParseInt(buffer, &value)) {
            LOG(ERROR) << "Failed to parse " << path;
        }
    }

    return value;
}

bool BatteryDefender::writeIntToFile(const std::string &path, const int value) {
    bool success = android::base::WriteStringToFile(std::to_string(value), path);
    if (!success) {
        LOG(ERROR) << "Failed to write " << path;
    }

    return success;
}

void BatteryDefender::writeTimeToFile(const std::string &path, const int value, int64_t *previous) {
    // Some number of seconds delay before repeated writes
    const bool hasTimeChangedSignificantly =
            ((value == 0) || (*previous == -1) || (value > (*previous + kWriteDelaySecs)) ||
             (value < (*previous - kWriteDelaySecs)));
    if ((value != *previous) && hasTimeChangedSignificantly) {
        writeIntToFile(path, value);
        *previous = value;
    }
}

void BatteryDefender::writeChargeLevelsToFile(const int vendorStart, const int vendorStop) {
    int chargeLevelStart = vendorStart;
    int chargeLevelStop = vendorStop;
    if (mCurrentState == STATE_ACTIVE) {
        const int newDefenderLevelStart = android::base::GetIntProperty(
                kPropBatteryDefenderCtrlStartSOC, kChargeLevelDefenderStart, 0, 100);
        const int newDefenderLevelStop = android::base::GetIntProperty(
                kPropBatteryDefenderCtrlStopSOC, kChargeLevelDefenderStop, 0, 100);
        const bool overrideLevelsValid =
                (newDefenderLevelStart <= newDefenderLevelStop) && (newDefenderLevelStop != 0);

        if (overrideLevelsValid) {
            chargeLevelStart = newDefenderLevelStart;
            chargeLevelStop = newDefenderLevelStop;
        } else {
            chargeLevelStart = kChargeLevelDefenderStart;
            chargeLevelStop = kChargeLevelDefenderStop;
        }
    }

    // Disable battery defender effects in charger mode until
    // b/149598262 is resolved
    if (android::base::GetProperty(kPropBootmode, "undefined") != "charger") {
        if (chargeLevelStart != mChargeLevelStartPrevious) {
            if (writeIntToFile(kPathChargeLevelStart, chargeLevelStart)) {
                mChargeLevelStartPrevious = chargeLevelStart;
            }
        }
        if (chargeLevelStop != mChargeLevelStopPrevious) {
            if (writeIntToFile(kPathChargeLevelStop, chargeLevelStop)) {
                mChargeLevelStopPrevious = chargeLevelStop;
            }
        }
    }
}

bool BatteryDefender::isTypeCSink(const std::string &path) {
    std::string buffer;

    if (!android::base::ReadFileToString(path, &buffer)) {
        LOG(ERROR) << "Failed to read " << path;
    }

    return (buffer.find("[sink]") != std::string::npos);
}

bool BatteryDefender::isWiredPresent(void) {
    // Default to USB "present" if type C is not used.
    if (!kUseTypeC) {
        return readFileToInt(kPathUSBChargerPresent) != 0;
    }

    DIR *dp = opendir(kTypeCPath.c_str());
    if (dp == NULL) {
        LOG(ERROR) << "Failed to read " << kTypeCPath;
        return false;
    }

    struct dirent *ep;
    std::unordered_map<std::string, bool> names;
    while ((ep = readdir(dp))) {
        if (ep->d_type == DT_LNK) {
            if (std::string::npos != std::string(ep->d_name).find("-partner")) {
                std::string portName = std::strtok(ep->d_name, "-");
                std::string path = kTypeCPath + portName + "/power_role";
                if (isTypeCSink(path)) {
                    closedir(dp);
                    return true;
                }
            }
        }
    }
    closedir(dp);

    return false;
}

bool BatteryDefender::isDockPresent(void) {
    return readFileToInt(kPathDOCKChargerPresent, true) != 0;
}

bool BatteryDefender::isChargePowerAvailable(void) {
    // USB presence is an indicator of power availability
    const bool chargerPresentWired = isWiredPresent();
    const bool chargerPresentWireless = readFileToInt(mPathWirelessPresent) != 0;
    const bool chargerPresentDock = isDockPresent();
    mIsWiredPresent = chargerPresentWired;
    mIsWirelessPresent = chargerPresentWireless;
    mIsDockPresent = chargerPresentDock;

    return chargerPresentWired || chargerPresentWireless || chargerPresentDock;
}

bool BatteryDefender::isDefaultChargeLevel(const int start, const int stop) {
    return ((start == kChargeLevelDefaultStart) && (stop == kChargeLevelDefaultStop));
}

bool BatteryDefender::isBatteryDefenderDisabled(const int vendorStart, const int vendorStop) {
    const bool isDefaultVendorChargeLevel = isDefaultChargeLevel(vendorStart, vendorStop);
    const bool isOverrideDisabled =
            android::base::GetBoolProperty(kPropBatteryDefenderDisable, false);
    const bool isCtrlEnabled =
            android::base::GetBoolProperty(kPropBatteryDefenderCtrlEnable, kDefaultEnable);

    return isOverrideDisabled || (isDefaultVendorChargeLevel == false) || (isCtrlEnabled == false);
}

bool BatteryDefender::isDockDefendTrigger(void) {
    return readFileToInt(kPathDockState, true) == 1;
}

void BatteryDefender::addTimeToChargeTimers(void) {
    if (mIsPowerAvailable) {
        if (mHasReachedHighCapacityLevel) {
            mTimeChargerPresentSecs += mTimeBetweenUpdateCalls;
        }
        mTimeChargerNotPresentSecs = 0;
    } else {
        mTimeChargerNotPresentSecs += mTimeBetweenUpdateCalls;
    }
}

int32_t BatteryDefender::getTimeToActivate(void) {
    // Use the default constructor value if the modified property is not between 60 and INT_MAX
    // (seconds)
    const int32_t timeToActivateOverride =
            android::base::GetIntProperty(kPropBatteryDefenderThreshold, kTimeToActivateSecs,
                                          (int32_t)ONE_MIN_IN_SECONDS, INT32_MAX);

    const bool overrideActive = timeToActivateOverride != kTimeToActivateSecs;
    if (overrideActive) {
        return timeToActivateOverride;
    } else {
        // No overrides taken; apply ctrl time to activate...
        // Note; do not allow less than 1 day trigger time
        return android::base::GetIntProperty(kPropBatteryDefenderCtrlActivateTime,
                                             kTimeToActivateSecs, (int32_t)ONE_DAY_IN_SECONDS,
                                             INT32_MAX);
    }
}

void BatteryDefender::stateMachine_runAction(const state_E state, const HealthInfo &health_info) {
    switch (state) {
        case STATE_INIT:
            loadPersistentStorage();
            if (health_info.chargerUsbOnline || health_info.chargerAcOnline) {
                mWasAcOnline = health_info.chargerAcOnline;
                mWasUsbOnline = health_info.chargerUsbOnline;
            }
            break;

        case STATE_DISABLED:
        case STATE_DISCONNECTED:
            clearStateData();
            break;

        case STATE_CONNECTED: {
            addTimeToChargeTimers();

            const int triggerLevel = android::base::GetIntProperty(
                    kPropBatteryDefenderCtrlTriggerSOC, kChargeHighCapacityLevel, 0, 100);
            if (health_info.batteryLevel >= triggerLevel) {
                mHasReachedHighCapacityLevel = true;
            }
        } break;

        case STATE_ACTIVE:
            addTimeToChargeTimers();
            mTimeActiveSecs += mTimeBetweenUpdateCalls;
            break;

        default:
            break;
    }

    // Must be loaded after init has set the property
    mTimeToActivateSecsModified = getTimeToActivate();
}

BatteryDefender::state_E BatteryDefender::stateMachine_getNextState(const state_E state) {
    state_E nextState = state;

    if (mIsDefenderDisabled) {
        nextState = STATE_DISABLED;
    } else {
        switch (state) {
            case STATE_INIT:
                if (mIsPowerAvailable) {
                    if (mTimeChargerPresentSecs > mTimeToActivateSecsModified) {
                        nextState = STATE_ACTIVE;
                    } else {
                        nextState = STATE_CONNECTED;
                    }
                } else {
                    nextState = STATE_DISCONNECTED;
                }
                break;

            case STATE_DISABLED:
                nextState = STATE_DISCONNECTED;
                break;

            case STATE_DISCONNECTED:
                if (mIsPowerAvailable) {
                    nextState = STATE_CONNECTED;
                }
                break;

            case STATE_CONNECTED:
                if (mTimeChargerPresentSecs > mTimeToActivateSecsModified) {
                    nextState = STATE_ACTIVE;
                }
                FALLTHROUGH_INTENDED;

            case STATE_ACTIVE: {
                const int timeToClear = android::base::GetIntProperty(
                        kPropBatteryDefenderCtrlResumeTime, kTimeToClearTimerSecs, 0, INT32_MAX);

                const int bdClear = android::base::GetIntProperty(kPropBatteryDefenderCtrlClear, 0);

                if (bdClear > 0) {
                    android::base::SetProperty(kPropBatteryDefenderCtrlClear, "0");
                    nextState = STATE_DISCONNECTED;
                }

                /* Check for mIsPowerAvailable in case timeToClear is 0 */
                if ((mTimeChargerNotPresentSecs >= timeToClear) && (mIsPowerAvailable == false)) {
                    nextState = STATE_DISCONNECTED;
                }
            } break;

            default:
                break;
        }
    }

    return nextState;
}

// This will run once at the rising edge of a new state transition,
// in addition to runAction()
void BatteryDefender::stateMachine_firstAction(const state_E state) {
    switch (state) {
        case STATE_DISABLED:
            LOG(INFO) << "Disabled!";
            FALLTHROUGH_INTENDED;

        case STATE_DISCONNECTED:
            clearStateData();
            break;

        case STATE_CONNECTED:
            // Time already accumulated on state transition implies that there has
            // already been a full charge cycle (this could happen on boot).
            if (mTimeChargerPresentSecs > 0) {
                mHasReachedHighCapacityLevel = true;
            }
            break;

        case STATE_ACTIVE:
            mHasReachedHighCapacityLevel = true;
            LOG(INFO) << "Started with " << mTimeChargerPresentSecs
                      << " seconds of power availability!";
            break;

        case STATE_INIT:
        default:
            // No actions
            break;
    }
}

void BatteryDefender::updateDefenderProperties(
        aidl::android::hardware::health::HealthInfo *health_info) {
    /**
     * Override the OVERHEAT flag for UI updates to settings.
     * Also, force AC/USB online if active and still connected to power.
     */
    if (mCurrentState == STATE_ACTIVE) {
        health_info->batteryHealth = BatteryHealth::OVERHEAT;
    }

    /* Do the same as above when dock-defend triggers */
    if (mIsDockDefendTrigger) {
        health_info->batteryHealth = BatteryHealth::OVERHEAT;
    }

    /**
     * If the kernel is forcing the input current limit to 0, then the online status may
     * need to be overwritten. Also, setting a charge limit below the current charge level
     * may disable the adapter.
     * Note; only override "online" if necessary (all "online"s are false).
     */
    if (health_info->chargerUsbOnline == false && health_info->chargerAcOnline == false) {
        /* Override if the USB is connected and a battery defender is active */
        if (mIsWiredPresent && health_info->batteryHealth == BatteryHealth::OVERHEAT) {
            if (mWasAcOnline) {
                health_info->chargerAcOnline = true;
            }
            if (mWasUsbOnline) {
                health_info->chargerUsbOnline = true;
            }
        }
    } else {
        /* One of these booleans will always be true if updated here */
        mWasAcOnline = health_info->chargerAcOnline;
        mWasUsbOnline = health_info->chargerUsbOnline;
    }

    /* Do the same as above for wireless adapters */
    if (health_info->chargerWirelessOnline == false) {
        if (mIsWirelessPresent && health_info->batteryHealth == BatteryHealth::OVERHEAT) {
            health_info->chargerWirelessOnline = true;
        }
    }

    /* Do the same as above for dock adapters */
    if (health_info->chargerDockOnline == false) {
        /* Override if the USB is connected and a battery defender is active */
        if (mIsDockPresent && health_info->batteryHealth == BatteryHealth::OVERHEAT) {
            health_info->chargerDockOnline = true;
        }
    }
}

void BatteryDefender::update(HealthInfo *health_info) {
    if (!health_info) {
        return;
    }

    // Update module inputs
    const int chargeLevelVendorStart =
            android::base::GetIntProperty(kPropChargeLevelVendorStart, kChargeLevelDefaultStart);
    const int chargeLevelVendorStop =
            android::base::GetIntProperty(kPropChargeLevelVendorStop, kChargeLevelDefaultStop);
    mIsDefenderDisabled = isBatteryDefenderDisabled(chargeLevelVendorStart, chargeLevelVendorStop);
    mIsPowerAvailable = isChargePowerAvailable();
    mTimeBetweenUpdateCalls = getDeltaTimeSeconds(&mTimePreviousSecs);
    mIsDockDefendTrigger = isDockDefendTrigger();

    // Run state machine
    stateMachine_runAction(mCurrentState, *health_info);
    const state_E nextState = stateMachine_getNextState(mCurrentState);
    if (nextState != mCurrentState) {
        stateMachine_firstAction(nextState);
    }
    mCurrentState = nextState;

    // Verify/update battery defender battery properties
    updateDefenderProperties(health_info); /* May override battery properties */

    // Store outputs
    writeTimeToFile(kPathPersistChargerPresentTime, mTimeChargerPresentSecs,
                    &mTimeChargerPresentSecsPrevious);
    writeTimeToFile(kPathPersistDefenderActiveTime, mTimeActiveSecs, &mTimeActiveSecsPrevious);
    writeChargeLevelsToFile(chargeLevelVendorStart, chargeLevelVendorStop);
    android::base::SetProperty(kPropBatteryDefenderState, kStateStringMap[mCurrentState]);
}

void BatteryDefender::update(struct android::BatteryProperties *props) {
    if (!props) {
        return;
    }
    HealthInfo health_info = ToHealthInfo(props);
    update(&health_info);
    // Propagate the changes to props
    props->chargerAcOnline = health_info.chargerAcOnline;
    props->chargerUsbOnline = health_info.chargerUsbOnline;
    props->chargerWirelessOnline = health_info.chargerWirelessOnline;
    props->chargerDockOnline = health_info.chargerDockOnline;
    props->batteryHealth = static_cast<int>(health_info.batteryHealth);
    // update() doesn't change other fields.
}

}  // namespace health
}  // namespace pixel
}  // namespace google
}  // namespace hardware
