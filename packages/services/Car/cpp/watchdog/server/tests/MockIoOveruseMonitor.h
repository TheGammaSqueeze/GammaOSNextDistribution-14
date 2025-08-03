/**
 * Copyright (c) 2020, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef CPP_WATCHDOG_SERVER_TESTS_MOCKIOOVERUSEMONITOR_H_
#define CPP_WATCHDOG_SERVER_TESTS_MOCKIOOVERUSEMONITOR_H_

#include "IoOveruseMonitor.h"
#include "MockDataProcessor.h"

#include <android-base/result.h>
#include <gmock/gmock.h>

namespace android {
namespace automotive {
namespace watchdog {

class MockIoOveruseMonitor : public MockDataProcessor, public IoOveruseMonitorInterface {
public:
    MockIoOveruseMonitor() {
        ON_CALL(*this, name()).WillByDefault(::testing::Return("MockIoOveruseMonitor"));
    }
    ~MockIoOveruseMonitor() {}
    MOCK_METHOD(bool, isInitialized, (), (const, override));
    MOCK_METHOD(bool, dumpHelpText, (int), (const, override));
    MOCK_METHOD(void, onCarWatchdogServiceRegistered, (), (override));
    MOCK_METHOD(
            android::base::Result<void>, updateResourceOveruseConfigurations,
            (const std::vector<
                    aidl::android::automotive::watchdog::internal::ResourceOveruseConfiguration>&),
            (override));
    MOCK_METHOD(
            android::base::Result<void>, getResourceOveruseConfigurations,
            (std::vector<
                    aidl::android::automotive::watchdog::internal::ResourceOveruseConfiguration>*),
            (const, override));
    MOCK_METHOD(android::base::Result<void>, onTodayIoUsageStatsFetched,
                (const std::vector<
                        aidl::android::automotive::watchdog::internal::UserPackageIoUsageStats>&),
                (override));
    MOCK_METHOD(
            android::base::Result<void>, addIoOveruseListener,
            (const std::shared_ptr<aidl::android::automotive::watchdog::IResourceOveruseListener>&),
            (override));
    MOCK_METHOD(
            android::base::Result<void>, removeIoOveruseListener,
            (const std::shared_ptr<aidl::android::automotive::watchdog::IResourceOveruseListener>&),
            (override));
    MOCK_METHOD(void, handleBinderDeath, (void*), (override));
    MOCK_METHOD(android::base::Result<void>, getIoOveruseStats,
                (aidl::android::automotive::watchdog::IoOveruseStats*), (const, override));
    MOCK_METHOD(android::base::Result<void>, resetIoOveruseStats, (const std::vector<std::string>&),
                (override));
    MOCK_METHOD(void, removeStatsForUser, (userid_t), (override));
};

}  // namespace watchdog
}  // namespace automotive
}  // namespace android

#endif  //  CPP_WATCHDOG_SERVER_TESTS_MOCKIOOVERUSEMONITOR_H_
