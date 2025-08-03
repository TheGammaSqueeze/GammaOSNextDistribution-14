/*
 * Copyright (c) 2021, The Android Open Source Project
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

#ifndef CPP_WATCHDOG_SERVER_TESTS_MOCKDATAPROCESSOR_H_
#define CPP_WATCHDOG_SERVER_TESTS_MOCKDATAPROCESSOR_H_

#include "WatchdogPerfService.h"

#include <gmock/gmock.h>

namespace android {
namespace automotive {
namespace watchdog {

class MockDataProcessor : virtual public DataProcessorInterface {
public:
    MockDataProcessor() {
        EXPECT_CALL(*this, name()).WillRepeatedly(::testing::Return("MockedDataProcessor"));
    }
    MOCK_METHOD(std::string, name, (), (const, override));
    MOCK_METHOD(android::base::Result<void>, init, (), (override));
    MOCK_METHOD(void, terminate, (), (override));
    MOCK_METHOD(android::base::Result<void>, onSystemStartup, (), (override));
    MOCK_METHOD(void, onCarWatchdogServiceRegistered, (), (override));
    MOCK_METHOD(android::base::Result<void>, onBoottimeCollection,
                (time_point_millis, const wp<UidStatsCollectorInterface>&,
                 const wp<ProcStatCollectorInterface>&,
                 aidl::android::automotive::watchdog::internal::ResourceStats*),
                (override));
    MOCK_METHOD(android::base::Result<void>, onWakeUpCollection,
                (time_point_millis, const wp<UidStatsCollectorInterface>&,
                 const wp<ProcStatCollectorInterface>&),
                (override));
    MOCK_METHOD(android::base::Result<void>, onPeriodicCollection,
                (time_point_millis, SystemState, const wp<UidStatsCollectorInterface>&,
                 const wp<ProcStatCollectorInterface>&,
                 aidl::android::automotive::watchdog::internal::ResourceStats*),
                (override));
    MOCK_METHOD(android::base::Result<void>, onUserSwitchCollection,
                (time_point_millis, userid_t, userid_t, const wp<UidStatsCollectorInterface>&,
                 const wp<ProcStatCollectorInterface>&),
                (override));
    MOCK_METHOD(android::base::Result<void>, onCustomCollection,
                (time_point_millis, SystemState, const std::unordered_set<std::string>&,
                 const wp<UidStatsCollectorInterface>&, const wp<ProcStatCollectorInterface>&,
                 aidl::android::automotive::watchdog::internal::ResourceStats*),
                (override));
    MOCK_METHOD(android::base::Result<void>, onPeriodicMonitor,
                (time_t, const android::wp<ProcDiskStatsCollectorInterface>&,
                 const std::function<void()>&),
                (override));
    MOCK_METHOD(android::base::Result<void>, onDump, (int), (const, override));
    MOCK_METHOD(android::base::Result<void>, onDumpProto,
                (const CollectionIntervals&, android::util::ProtoOutputStream&), (const, override));
    MOCK_METHOD(android::base::Result<void>, onCustomCollectionDump, (int), (override));
};

}  // namespace watchdog
}  // namespace automotive
}  // namespace android

#endif  //  CPP_WATCHDOG_SERVER_TESTS_MOCKDATAPROCESSOR_H_
