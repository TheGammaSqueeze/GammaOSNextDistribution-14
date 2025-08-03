/*
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

#ifndef CPP_WATCHDOG_SERVER_TESTS_MOCKUIDCPUSTATSCOLLECTOR_H_
#define CPP_WATCHDOG_SERVER_TESTS_MOCKUIDCPUSTATSCOLLECTOR_H_

#include "UidCpuStatsCollector.h"

#include <android-base/result.h>
#include <gmock/gmock.h>

#include <string>
#include <unordered_map>

namespace android {
namespace automotive {
namespace watchdog {

class MockUidCpuStatsCollector : public UidCpuStatsCollectorInterface {
public:
    MockUidCpuStatsCollector() { ON_CALL(*this, enabled()).WillByDefault(::testing::Return(true)); }
    MOCK_METHOD(void, init, (), (override));
    MOCK_METHOD(android::base::Result<void>, collect, (), (override));
    MOCK_METHOD((const std::unordered_map<uid_t, int64_t>), latestStats, (), (const, override));
    MOCK_METHOD((const std::unordered_map<uid_t, int64_t>), deltaStats, (), (const, override));
    MOCK_METHOD(bool, enabled, (), (const, override));
    MOCK_METHOD(const std::string, filePath, (), (const, override));
};

}  // namespace watchdog
}  // namespace automotive
}  // namespace android

#endif  // CPP_WATCHDOG_SERVER_TESTS_MOCKUIDCPUSTATSCOLLECTOR_H_
