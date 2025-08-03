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

#ifndef CPP_WATCHDOG_SERVER_TESTS_MOCKWATCHDOGPERFSERVICE_H_
#define CPP_WATCHDOG_SERVER_TESTS_MOCKWATCHDOGPERFSERVICE_H_

#include "WatchdogPerfService.h"
#include "WatchdogServiceHelper.h"

#include <aidl/android/automotive/watchdog/internal/UserState.h>
#include <android-base/result.h>
#include <android/util/ProtoOutputStream.h>
#include <gmock/gmock.h>
#include <utils/String16.h>
#include <utils/Vector.h>

namespace android {
namespace automotive {
namespace watchdog {

class MockWatchdogPerfService : public WatchdogPerfServiceInterface {
public:
    MockWatchdogPerfService() {}
    ~MockWatchdogPerfService() {}
    MOCK_METHOD(android::base::Result<void>, registerDataProcessor,
                (android::sp<DataProcessorInterface>), (override));
    MOCK_METHOD(android::base::Result<void>, start, (), (override));
    MOCK_METHOD(void, terminate, (), (override));
    MOCK_METHOD(void, setSystemState, (SystemState), (override));
    MOCK_METHOD(void, onCarWatchdogServiceRegistered, (), (override));
    MOCK_METHOD(android::base::Result<void>, onBootFinished, (), (override));
    MOCK_METHOD(android::base::Result<void>, onUserStateChange,
                (userid_t userId, const aidl::android::automotive::watchdog::internal::UserState&),
                (override));
    MOCK_METHOD(android::base::Result<void>, onSuspendExit, (), (override));
    MOCK_METHOD(android::base::Result<void>, onShutdownEnter, (), (override));
    MOCK_METHOD(android::base::Result<void>, onCustomCollection, (int fd, const char**, uint32_t),
                (override));
    MOCK_METHOD(android::base::Result<void>, onDump, (int), (const, override));
    MOCK_METHOD(android::base::Result<void>, onDumpProto, (android::util::ProtoOutputStream&),
                (const, override));
    MOCK_METHOD(bool, dumpHelpText, (int fd), (const, override));
    MOCK_METHOD(void, handleMessage, (const Message&), (override));
};

}  // namespace watchdog
}  // namespace automotive
}  // namespace android

#endif  //  CPP_WATCHDOG_SERVER_TESTS_MOCKWATCHDOGPERFSERVICE_H_
