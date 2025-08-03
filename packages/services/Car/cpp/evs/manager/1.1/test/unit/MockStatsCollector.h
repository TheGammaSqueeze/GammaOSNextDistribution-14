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

#ifndef CPP_EVS_MANAGER_1_1_TEST_UNIT_MOCKSTATSCOLLECTOR_H_
#define CPP_EVS_MANAGER_1_1_TEST_UNIT_MOCKSTATSCOLLECTOR_H_

#include "IStatsCollector.h"
#include "MockEvsEnumerator.h"
#include "VirtualCamera.h"

#include <android-base/chrono_utils.h>
#include <android-base/result.h>
#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include <string>
#include <unordered_map>

namespace android::automotive::evs::V1_1::implementation {

class MockStatsCollector : public IStatsCollector {
public:
    ~MockStatsCollector() override = default;

    MOCK_METHOD(android::base::Result<void>, startCollection, (), (override));
    MOCK_METHOD(android::base::Result<void>, startCustomCollection,
                (std::chrono::nanoseconds interval, std::chrono::nanoseconds duration), (override));
    MOCK_METHOD(android::base::Result<std::string>, stopCustomCollection, (std::string id),
                (override));
    MOCK_METHOD((std::unordered_map<std::string, std::string>), toString, (const char*),
                (override));
    MOCK_METHOD(android::base::Result<void>, registerClientToMonitor,
                (const android::sp<HalCamera>&), (override));
    MOCK_METHOD(android::base::Result<void>, unregisterClientToMonitor, (const std::string& id),
                (override));
};

using NiceMockStatsCollector = ::testing::NiceMock<MockStatsCollector>;

}  // namespace android::automotive::evs::V1_1::implementation

#endif  // CPP_EVS_MANAGER_1_1_TEST_UNIT_MOCKSTATSCOLLECTOR_H_
