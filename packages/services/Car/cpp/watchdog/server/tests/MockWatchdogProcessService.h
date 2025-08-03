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

#ifndef CPP_WATCHDOG_SERVER_TESTS_MOCKWATCHDOGPROCESSSERVICE_H_
#define CPP_WATCHDOG_SERVER_TESTS_MOCKWATCHDOGPROCESSSERVICE_H_

#include "WatchdogProcessService.h"
#include "WatchdogServiceHelper.h"

#include <aidl/android/automotive/watchdog/ICarWatchdogClient.h>
#include <aidl/android/automotive/watchdog/internal/ICarWatchdogMonitor.h>
#include <aidl/android/automotive/watchdog/internal/ICarWatchdogServiceForSystem.h>
#include <aidl/android/automotive/watchdog/internal/PowerCycle.h>
#include <aidl/android/automotive/watchdog/internal/ProcessIdentifier.h>
#include <aidl/android/automotive/watchdog/internal/UserState.h>
#include <android-base/result.h>
#include <android/util/ProtoOutputStream.h>
#include <binder/Status.h>
#include <gmock/gmock.h>
#include <utils/String16.h>
#include <utils/Vector.h>

#include <vector>

namespace android {
namespace automotive {
namespace watchdog {

class MockWatchdogProcessService : public WatchdogProcessServiceInterface {
public:
    MockWatchdogProcessService() {}
    MOCK_METHOD(android::base::Result<void>, start, (), (override));
    MOCK_METHOD(void, terminate, (), (override));
    MOCK_METHOD(void, onDump, (int), (override));
    MOCK_METHOD(void, onDumpProto, (android::util::ProtoOutputStream&), (override));
    MOCK_METHOD(void, doHealthCheck, (int), (override));
    MOCK_METHOD(void, handleBinderDeath, (void*), (override));
    MOCK_METHOD(ndk::ScopedAStatus, registerClient,
                (const std::shared_ptr<aidl::android::automotive::watchdog::ICarWatchdogClient>&,
                 aidl::android::automotive::watchdog::TimeoutLength),
                (override));
    MOCK_METHOD(ndk::ScopedAStatus, unregisterClient,
                (const std::shared_ptr<aidl::android::automotive::watchdog::ICarWatchdogClient>&),
                (override));
    MOCK_METHOD(ndk::ScopedAStatus, registerCarWatchdogService,
                (const ndk::SpAIBinder&, const android::sp<WatchdogServiceHelperInterface>&),
                (override));
    MOCK_METHOD(void, unregisterCarWatchdogService, (const ndk::SpAIBinder&), (override));
    MOCK_METHOD(ndk::ScopedAStatus, registerMonitor,
                (const std::shared_ptr<
                        aidl::android::automotive::watchdog::internal::ICarWatchdogMonitor>&),
                (override));
    MOCK_METHOD(ndk::ScopedAStatus, unregisterMonitor,
                (const std::shared_ptr<
                        aidl::android::automotive::watchdog::internal::ICarWatchdogMonitor>&),
                (override));
    MOCK_METHOD(ndk::ScopedAStatus, tellClientAlive,
                (const std::shared_ptr<aidl::android::automotive::watchdog::ICarWatchdogClient>&,
                 int32_t),
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
    MOCK_METHOD(void, setEnabled, (bool), (override));
    MOCK_METHOD(void, onUserStateChange, (userid_t, bool), (override));
    MOCK_METHOD(void, onAidlVhalPidFetched, (int32_t), (override));
};

}  // namespace watchdog
}  // namespace automotive
}  // namespace android

#endif  //  CPP_WATCHDOG_SERVER_TESTS_MOCKWATCHDOGPROCESSSERVICE_H_
