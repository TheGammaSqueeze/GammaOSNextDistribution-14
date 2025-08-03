/*
 * Copyright (C) 2021 The Android Open Source Project
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

#pragma once

#include <gtest/gtest_prod.h>
#include <server_configurable_flags/get_flags.h>

#include <functional>
#include <mutex>
#include <string>
#include <unordered_map>
#include <vector>

#include "stats_util.h"

namespace android {
namespace os {
namespace statsd {

using GetServerFlagFunc =
        std::function<std::string(const std::string&, const std::string&, const std::string&)>;
using IsAtLeastSFunc = std::function<bool()>;

const std::string STATSD_NATIVE_NAMESPACE = "statsd_native";
const std::string STATSD_NATIVE_BOOT_NAMESPACE = "statsd_native_boot";

const std::string STATSD_INIT_COMPLETED_NO_DELAY_FLAG = "statsd_init_completed_no_delay";

const std::string FLAG_TRUE = "true";
const std::string FLAG_FALSE = "false";
const std::string FLAG_EMPTY = "";

class FlagProvider {
public:
    static FlagProvider& getInstance();

    std::string getFlagString(const std::string& flagName, const std::string& defaultValue) const;

    // Returns true IFF flagName has a value of "true".
    bool getFlagBool(const std::string& flagName, const std::string& defaultValue) const;

    std::string getBootFlagString(const std::string& flagName,
                                  const std::string& defaultValue) const;

    // Returns true IFF flagName has a value of "true".
    bool getBootFlagBool(const std::string& flagName, const std::string& defaultValue) const;

    // Queries the boot flags. Should only be called once at boot.
    void initBootFlags(const std::vector<std::string>& flags);

private:
    FlagProvider();

    // TODO(b/194347008): Remove the GetServerConfigurableFlag override.
    void overrideFuncs(const IsAtLeastSFunc& isAtLeastSFunc = &isAtLeastS,
                       const GetServerFlagFunc& getServerFlagFunc =
                               &server_configurable_flags::GetServerConfigurableFlag);

    void overrideFuncsLocked(const IsAtLeastSFunc& isAtLeastSFunc = &isAtLeastS,
                             const GetServerFlagFunc& getServerFlagFunc =
                                     &server_configurable_flags::GetServerConfigurableFlag);

    inline void resetOverrides() {
        std::lock_guard<std::mutex> lock(mFlagsMutex);
        overrideFuncsLocked();
        mLocalFlags.clear();
    }

    void overrideFlag(const std::string& flagName, const std::string& flagValue,
                      const bool isBootFlag = false);

    std::string getFlagStringInternal(const std::string& flagName, const std::string& flagValue,
                                      const bool isBootFlag) const;

    std::string getLocalFlagKey(const std::string& flagName, const bool isBootFlag = false) const;

    IsAtLeastSFunc mIsAtLeastSFunc;
    GetServerFlagFunc mGetServerFlagFunc;

    // Flag values updated only at boot. Used to store boot flags.
    std::unordered_map<std::string, std::string> mBootFlags;

    // Flag values to be locally overwritten. Only used in tests.
    std::unordered_map<std::string, std::string> mLocalFlags;

    mutable std::mutex mFlagsMutex;

    friend class ConfigUpdateE2eTest;
    friend class ConfigUpdateTest;
    friend class EventMetricE2eTest;
    friend class ValueMetricE2eTest;
    friend class GaugeMetricE2ePulledTest;
    friend class GaugeMetricE2ePushedTest;
    friend class EventMetricProducerTest;
    friend class FlagProviderTest_RMinus;
    friend class FlagProviderTest_SPlus;
    friend class FlagProviderTest_SPlus_RealValues;
    friend class KllMetricE2eAbTest;
    friend class MetricsManagerTest;
    friend class StatsLogProcessorTest;
    friend class StatsLogProcessorTestRestricted;
    friend class RestrictedEventMetricProducerTest;
    friend class RestrictedConfigE2ETest;
    friend class RestrictedEventMetricE2eTest;
    friend class LogEvent_FieldRestrictionTest;

    FRIEND_TEST(ConfigUpdateE2eTest, TestEventMetric);
    FRIEND_TEST(ConfigUpdateE2eTest, TestGaugeMetric);
    FRIEND_TEST(ConfigUpdateE2eTest, TestConfigUpdateRestrictedDelegateCleared);
    FRIEND_TEST(EventMetricE2eTest, TestEventMetricDataAggregated);
    FRIEND_TEST(EventMetricProducerTest, TestOneAtomTagAggregatedEvents);
    FRIEND_TEST(EventMetricProducerTest, TestTwoAtomTagAggregatedEvents);
    FRIEND_TEST(GaugeMetricE2ePulledTest, TestRandomSamplePulledEventsNoCondition);
    FRIEND_TEST(FlagProviderTest_SPlus, TestGetFlagBoolServerFlagTrue);
    FRIEND_TEST(FlagProviderTest_SPlus, TestGetFlagBoolServerFlagFalse);
    FRIEND_TEST(FlagProviderTest_SPlus, TestOverrideLocalFlags);
    FRIEND_TEST(FlagProviderTest_SPlus, TestGetFlagBoolServerFlagEmptyDefaultFalse);
    FRIEND_TEST(FlagProviderTest_SPlus, TestGetFlagBoolServerFlagEmptyDefaultTrue);
    FRIEND_TEST(FlagProviderTest_SPlus_RealValues, TestGetBootFlagBoolServerFlagTrue);
    FRIEND_TEST(FlagProviderTest_SPlus_RealValues, TestGetBootFlagBoolServerFlagFalse);
    FRIEND_TEST(MetricsManagerTest_SPlus, TestRestrictedMetricsConfig);
    FRIEND_TEST(RestrictedEventMetricE2eTest, TestFlagDisabled);
    FRIEND_TEST(LogEventTest, TestRestrictionCategoryAnnotation);
    FRIEND_TEST(LogEventTest, TestInvalidRestrictionCategoryAnnotation);
    FRIEND_TEST(LogEvent_FieldRestrictionTest, TestFieldRestrictionAnnotation);
    FRIEND_TEST(LogEvent_FieldRestrictionTest, TestInvalidAnnotationIntType);
    FRIEND_TEST(LogEvent_FieldRestrictionTest, TestInvalidAnnotationAtomLevel);
};

}  // namespace statsd
}  // namespace os
}  // namespace android
