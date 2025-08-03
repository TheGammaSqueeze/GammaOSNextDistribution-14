/**
 * Copyright (c) 2022, The Android Open Source Project
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

#ifndef CPP_WATCHDOG_SERVER_TESTS_MOCKWATCHDOGINTERNALHANDLER_H_
#define CPP_WATCHDOG_SERVER_TESTS_MOCKWATCHDOGINTERNALHANDLER_H_

#include "WatchdogInternalHandler.h"

#include <android-base/result.h>
#include <gmock/gmock.h>
#include <utils/String16.h>
#include <utils/Vector.h>

namespace android {
namespace automotive {
namespace watchdog {

class MockWatchdogInternalHandler : public WatchdogInternalHandlerInterface {
public:
    MOCK_METHOD(binder_status_t, dump, (int, const char**, uint32_t), (override));
    MOCK_METHOD(
            ndk::ScopedAStatus, registerCarWatchdogService,
            (const std::shared_ptr<
                    aidl::android::automotive::watchdog::internal::ICarWatchdogServiceForSystem>&),
            (override));
    MOCK_METHOD(
            ndk::ScopedAStatus, unregisterCarWatchdogService,
            (const std::shared_ptr<
                    aidl::android::automotive::watchdog::internal::ICarWatchdogServiceForSystem>&),
            (override));
    MOCK_METHOD(ndk::ScopedAStatus, registerMonitor,
                (const std::shared_ptr<
                        aidl::android::automotive::watchdog::internal::ICarWatchdogMonitor>&),
                (override));
    MOCK_METHOD(ndk::ScopedAStatus, unregisterMonitor,
                (const std::shared_ptr<
                        aidl::android::automotive::watchdog::internal::ICarWatchdogMonitor>&),
                (override));
    MOCK_METHOD(
            ndk::ScopedAStatus, tellCarWatchdogServiceAlive,
            (const std::shared_ptr<
                     aidl::android::automotive::watchdog::internal::ICarWatchdogServiceForSystem>&,
             const std::vector<aidl::android::automotive::watchdog::internal::ProcessIdentifier>&,
             int32_t),
            (override));
    MOCK_METHOD(ndk::ScopedAStatus, tellDumpFinished,
                (const std::shared_ptr<
                         aidl::android::automotive::watchdog::internal::ICarWatchdogMonitor>&,
                 const aidl::android::automotive::watchdog::internal::ProcessIdentifier&),
                (override));
    MOCK_METHOD(ndk::ScopedAStatus, notifySystemStateChange,
                (aidl::android::automotive::watchdog::internal::StateType, int32_t, int32_t),
                (override));
    MOCK_METHOD(
            ndk::ScopedAStatus, updateResourceOveruseConfigurations,
            (const std::vector<
                    aidl::android::automotive::watchdog::internal::ResourceOveruseConfiguration>&),
            (override));
    MOCK_METHOD(
            ndk::ScopedAStatus, getResourceOveruseConfigurations,
            (std::vector<
                    aidl::android::automotive::watchdog::internal::ResourceOveruseConfiguration>*),
            (override));
    MOCK_METHOD(ndk::ScopedAStatus, controlProcessHealthCheck, (bool), (override));
    MOCK_METHOD(ndk::ScopedAStatus, setThreadPriority, (int, int, int, int, int), (override));
    MOCK_METHOD(ndk::ScopedAStatus, getThreadPriority,
                (int, int, int,
                 aidl::android::automotive::watchdog::internal::ThreadPolicyWithPriority*),
                (override));
    MOCK_METHOD(ndk::ScopedAStatus, onAidlVhalPidFetched, (int32_t), (override));
    MOCK_METHOD(ndk::ScopedAStatus, onTodayIoUsageStatsFetched,
                (const std::vector<
                        aidl::android::automotive::watchdog::internal::UserPackageIoUsageStats>&),
                (override));
    MOCK_METHOD(void, terminate, (), (override));
};

}  // namespace watchdog
}  // namespace automotive
}  // namespace android

#endif  //  CPP_WATCHDOG_SERVER_TESTS_MOCKWATCHDOGINTERNALHANDLER_H_
