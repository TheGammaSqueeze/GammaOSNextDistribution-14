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

#ifndef CPP_WATCHDOG_SERVER_TESTS_MOCKCARWATCHDOGSERVICEFORSYSTEM_H_
#define CPP_WATCHDOG_SERVER_TESTS_MOCKCARWATCHDOGSERVICEFORSYSTEM_H_

#include <aidl/android/automotive/watchdog/internal/BnCarWatchdogServiceForSystem.h>
#include <aidl/android/automotive/watchdog/internal/TimeoutLength.h>
#include <gmock/gmock.h>

namespace android {
namespace automotive {
namespace watchdog {

class MockCarWatchdogServiceForSystem :
      public aidl::android::automotive::watchdog::internal::BnCarWatchdogServiceForSystem {
public:
    MockCarWatchdogServiceForSystem() {}

    MOCK_METHOD(ndk::ScopedAStatus, checkIfAlive,
                (int32_t, aidl::android::automotive::watchdog::internal::TimeoutLength),
                (override));
    MOCK_METHOD(ndk::ScopedAStatus, prepareProcessTermination, (), (override));
    MOCK_METHOD(ndk::ScopedAStatus, getPackageInfosForUids,
                (const std::vector<int32_t>&, const std::vector<std::string>&,
                 std::vector<aidl::android::automotive::watchdog::internal::PackageInfo>*),
                (override));
    MOCK_METHOD(ndk::ScopedAStatus, latestIoOveruseStats,
                (const std::vector<
                        aidl::android::automotive::watchdog::internal::PackageIoOveruseStats>&),
                (override));
    MOCK_METHOD(ndk::ScopedAStatus, resetResourceOveruseStats, (const std::vector<std::string>&),
                (override));
    MOCK_METHOD(
            ndk::ScopedAStatus, getTodayIoUsageStats,
            (std::vector<aidl::android::automotive::watchdog::internal::UserPackageIoUsageStats>*),
            (override));
    MOCK_METHOD(ndk::ScopedAStatus, onLatestResourceStats,
                (const std::vector<aidl::android::automotive::watchdog::internal::ResourceStats>&),
                (override));
    MOCK_METHOD(ndk::ScopedAStatus, requestAidlVhalPid, (), (override));
    MOCK_METHOD(ndk::ScopedAStatus, requestTodayIoUsageStats, (), (override));
};

}  // namespace watchdog
}  // namespace automotive
}  // namespace android

#endif  //  CPP_WATCHDOG_SERVER_TESTS_MOCKCARWATCHDOGSERVICEFORSYSTEM_H_
