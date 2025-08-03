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

#ifndef HARDWARE_GOOGLE_PIXEL_HEALTH_BATTERYDEFENDER_H
#define HARDWARE_GOOGLE_PIXEL_HEALTH_BATTERYDEFENDER_H

#include <aidl/android/hardware/health/HealthInfo.h>
#include <batteryservice/BatteryService.h>
#include <stdbool.h>
#include <time.h>

#include <string>

namespace hardware {
namespace google {
namespace pixel {
namespace health {

const uint32_t ONE_MIN_IN_SECONDS = 60;
const uint32_t ONE_HOUR_IN_MINUTES = 60;
const uint32_t ONE_DAY_IN_HOURS = 24;
const uint32_t ONE_DAY_IN_SECONDS = ONE_DAY_IN_HOURS * ONE_HOUR_IN_MINUTES * ONE_MIN_IN_SECONDS;

const uint32_t DEFAULT_TIME_TO_ACTIVATE_SECONDS = (4 * ONE_DAY_IN_SECONDS);
const uint32_t DEFAULT_TIME_TO_CLEAR_SECONDS = (5 * ONE_MIN_IN_SECONDS);
const int DEFAULT_CHARGE_LEVEL_START = 0;
const int DEFAULT_CHARGE_LEVEL_STOP = 100;
const int DEFAULT_CHARGE_LEVEL_DEFENDER_START = 70;
const int DEFAULT_CHARGE_LEVEL_DEFENDER_STOP = 80;
const int DEFAULT_CAPACITY_LEVEL = 100;
const int WRITE_DELAY_SECS = 2 * ONE_MIN_IN_SECONDS;

const char *const PATH_NOT_SUPPORTED = "";
const char *const DEFAULT_START_LEVEL_PATH =
        "/sys/devices/platform/soc/soc:google,charger/charge_start_level";
const char *const DEFAULT_STOP_LEVEL_PATH =
        "/sys/devices/platform/soc/soc:google,charger/charge_stop_level";

class BatteryDefender {
  public:
    // Set default google charger paths - can be overridden for other devices
    BatteryDefender(const std::string pathWirelessPresent = PATH_NOT_SUPPORTED,
                    const std::string pathChargeLevelStart = DEFAULT_START_LEVEL_PATH,
                    const std::string pathChargeLevelStop = DEFAULT_STOP_LEVEL_PATH,
                    const int32_t timeToActivateSecs = DEFAULT_TIME_TO_ACTIVATE_SECONDS,
                    const int32_t timeToClearTimerSecs = DEFAULT_TIME_TO_CLEAR_SECONDS,
                    const bool useTypeC = true);

    // Either of the update() function shall be called periodically in HealthService
    // Deprecated. Use update(HealthInfo*)
    void update(struct android::BatteryProperties *props);
    void update(aidl::android::hardware::health::HealthInfo *health_info);

    // Set wireless not supported if this is not a device with a wireless charger
    // (must be checked at runtime)
    void setWirelessNotSupported(void);

  private:
    enum state_E {
        STATE_INIT,
        STATE_DISABLED,
        STATE_DISCONNECTED,
        STATE_CONNECTED,
        STATE_ACTIVE,
        STATE_COUNT,
    };
    const char *const kStateStringMap[STATE_COUNT] = {
            [STATE_INIT] = "INIT",
            [STATE_DISABLED] = "DISABLED",
            [STATE_DISCONNECTED] = "DISCONNECTED",
            [STATE_CONNECTED] = "CONNECTED",
            [STATE_ACTIVE] = "ACTIVE",
    };

    // Constructor
    std::string mPathWirelessPresent;
    const std::string kPathChargeLevelStart;
    const std::string kPathChargeLevelStop;
    const int32_t kTimeToActivateSecs;
    const int32_t kTimeToClearTimerSecs;
    const bool kUseTypeC;

    // Sysfs
    const std::string kPathUSBChargerPresent = "/sys/class/power_supply/usb/present";
    const std::string kPathDOCKChargerPresent = "/sys/class/power_supply/dock/present";
    const std::string kTypeCPath = "/sys/class/typec/";
    const std::string kPathPersistChargerPresentTime =
            "/mnt/vendor/persist/battery/defender_charger_time";
    const std::string kPathPersistDefenderActiveTime =
            "/mnt/vendor/persist/battery/defender_active_time";
    const std::string kPathDockState = "/sys/devices/platform/google,charger/dd_state";

    // Properties
    const char *const kPropChargeLevelVendorStart = "persist.vendor.charge.start.level";
    const char *const kPropChargeLevelVendorStop = "persist.vendor.charge.stop.level";
    const char *const kPropBatteryDefenderState = "vendor.battery.defender.state";
    const char *const kPropBatteryDefenderDisable = "vendor.battery.defender.disable";
    const char *const kPropBatteryDefenderThreshold = "vendor.battery.defender.threshold";
    const char *const kPropBootmode = "ro.bootmode";

    const char *const kPropBatteryDefenderCtrlEnable = "vendor.battery.defender.ctrl.enable";
    const char *const kPropBatteryDefenderCtrlActivateTime =
            "vendor.battery.defender.ctrl.trigger_time";
    const char *const kPropBatteryDefenderCtrlResumeTime =
            "vendor.battery.defender.ctrl.resume_time";
    const char *const kPropBatteryDefenderCtrlStartSOC =
            "vendor.battery.defender.ctrl.recharge_soc_start";
    const char *const kPropBatteryDefenderCtrlStopSOC =
            "vendor.battery.defender.ctrl.recharge_soc_stop";
    const char *const kPropBatteryDefenderCtrlTriggerSOC =
            "vendor.battery.defender.ctrl.trigger_soc";
    const char *const kPropBatteryDefenderCtrlClear = "vendor.battery.defender.ctrl.clear";

    // Default thresholds
    const bool kDefaultEnable = true;
    const int kChargeLevelDefaultStart = DEFAULT_CHARGE_LEVEL_START;
    const int kChargeLevelDefaultStop = DEFAULT_CHARGE_LEVEL_STOP;
    const int kChargeLevelDefenderStart = DEFAULT_CHARGE_LEVEL_DEFENDER_START;
    const int kChargeLevelDefenderStop = DEFAULT_CHARGE_LEVEL_DEFENDER_STOP;
    const int kChargeHighCapacityLevel = DEFAULT_CAPACITY_LEVEL;
    const int kWriteDelaySecs = WRITE_DELAY_SECS;

    // Inputs
    int64_t mTimeBetweenUpdateCalls = 0;
    int64_t mTimePreviousSecs;
    bool mIsWiredPresent = false;
    bool mIsWirelessPresent = false;
    bool mIsDockPresent = false;
    bool mIsPowerAvailable = false;
    bool mIsDefenderDisabled = false;
    bool mIsDockDefendTrigger = false;
    int32_t mTimeToActivateSecsModified;

    // State
    state_E mCurrentState = STATE_INIT;
    int64_t mTimeChargerPresentSecs = 0;
    int64_t mTimeChargerPresentSecsPrevious = -1;
    int64_t mTimeChargerNotPresentSecs = 0;
    int64_t mTimeActiveSecs = 0;
    int64_t mTimeActiveSecsPrevious = -1;
    int mChargeLevelStartPrevious = DEFAULT_CHARGE_LEVEL_START;
    int mChargeLevelStopPrevious = DEFAULT_CHARGE_LEVEL_STOP;
    bool mHasReachedHighCapacityLevel = false;
    bool mWasAcOnline = false;
    bool mWasUsbOnline = true; /* Default; in case neither AC/USB online becomes 1 */

    // Process state actions
    void stateMachine_runAction(const state_E state,
                                const aidl::android::hardware::health::HealthInfo &health_info);

    // Check state transitions
    state_E stateMachine_getNextState(const state_E state);

    // Process state entry actions
    void stateMachine_firstAction(const state_E state);

    void updateDefenderProperties(aidl::android::hardware::health::HealthInfo *health_info);
    void clearStateData(void);
    void loadPersistentStorage(void);
    int64_t getTime(void);
    int64_t getDeltaTimeSeconds(int64_t *timeStartSecs);
    int32_t getTimeToActivate(void);
    void removeLineEndings(std::string *str);
    int readFileToInt(const std::string &path, const bool silent = false);
    bool writeIntToFile(const std::string &path, const int value);
    void writeTimeToFile(const std::string &path, const int value, int64_t *previous);
    void writeChargeLevelsToFile(const int vendorStart, const int vendorStop);
    bool isTypeCSink(const std::string &path);
    bool isWiredPresent(void);
    bool isDockPresent(void);
    bool isChargePowerAvailable(void);
    bool isDefaultChargeLevel(const int start, const int stop);
    bool isBatteryDefenderDisabled(const int vendorStart, const int vendorStop);
    void addTimeToChargeTimers(void);
    bool isDockDefendTrigger(void);
};

}  // namespace health
}  // namespace pixel
}  // namespace google
}  // namespace hardware

#endif /* HARDWARE_GOOGLE_PIXEL_HEALTH_BATTERYDEFENDER_H */
