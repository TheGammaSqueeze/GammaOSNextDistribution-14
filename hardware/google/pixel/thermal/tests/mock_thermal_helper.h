/*
 * Copyright (C) 2023 The Android Open Source Project
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

#include <aidl/android/hardware/thermal/IThermal.h>
#include <gmock/gmock.h>
#include <log/log.h>

#include <unordered_map>

#include "thermal-helper.h"

namespace aidl::android::hardware::thermal::implementation {

class MockThermalHelper : public ThermalHelper {
  public:
    MockThermalHelper();
    ~MockThermalHelper() override;
    MOCK_METHOD(bool, fillCurrentTemperatures,
                (bool, bool, TemperatureType, std::vector<Temperature> *), (override));
    MOCK_METHOD(bool, fillTemperatureThresholds,
                (bool, TemperatureType, std::vector<TemperatureThreshold> *), (const, override));
    MOCK_METHOD(bool, fillCurrentCoolingDevices, (bool, CoolingType, std::vector<CoolingDevice> *),
                (const, override));
    MOCK_METHOD(bool, emulTemp, (std::string_view, const float, const bool), (override));
    MOCK_METHOD(bool, emulSeverity, (std::string_view, const int, const bool), (override));
    MOCK_METHOD(bool, emulClear, (std::string_view), (override));
    MOCK_METHOD(bool, isInitializedOk, (), (const, override));
    MOCK_METHOD(bool, readTemperature,
                (std::string_view, Temperature *out,
                 (std::pair<ThrottlingSeverity, ThrottlingSeverity> *), const bool),
                (override));
    MOCK_METHOD(bool, readTemperatureThreshold, (std::string_view, TemperatureThreshold *),
                (const, override));
    MOCK_METHOD(bool, readCoolingDevice, (std::string_view, CoolingDevice *), (const, override));
    MOCK_METHOD((const std::unordered_map<std::string, SensorInfo> &), GetSensorInfoMap, (),
                (const, override));
    MOCK_METHOD((const std::unordered_map<std::string, CdevInfo> &), GetCdevInfoMap, (),
                (const, override));
    MOCK_METHOD((const std::unordered_map<std::string, SensorStatus> &), GetSensorStatusMap, (),
                (const, override));
    MOCK_METHOD((const std::unordered_map<std::string, ThermalThrottlingStatus> &),
                GetThermalThrottlingStatusMap, (), (const, override));
    MOCK_METHOD((const std::unordered_map<std::string, PowerRailInfo> &), GetPowerRailInfoMap, (),
                (const, override));
    MOCK_METHOD((const std::unordered_map<std::string, PowerStatus> &), GetPowerStatusMap, (),
                (const, override));
    MOCK_METHOD((const std::unordered_map<std::string, SensorTempStats>),
                GetSensorTempStatsSnapshot, (), (override));
    MOCK_METHOD((const std::unordered_map<std::string,
                                          std::unordered_map<std::string, ThermalStats<int>>>),
                GetSensorCoolingDeviceRequestStatsSnapshot, (), (override));
    MOCK_METHOD(bool, isAidlPowerHalExist, (), (override));
    MOCK_METHOD(bool, isPowerHalConnected, (), (override));
    MOCK_METHOD(bool, isPowerHalExtConnected, (), (override));
};

}  // namespace aidl::android::hardware::thermal::implementation
