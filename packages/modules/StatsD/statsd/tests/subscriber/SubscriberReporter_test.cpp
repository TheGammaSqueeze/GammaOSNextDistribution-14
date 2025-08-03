// Copyright (C) 2020 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#include "subscriber/SubscriberReporter.h"

#include <android/binder_interface_utils.h>
#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include "tests/statsd_test_util.h"

using namespace testing;
using ::ndk::SharedRefBase;
using std::unordered_map;

#ifdef __ANDROID__

namespace android {
namespace os {
namespace statsd {

namespace {

const ConfigKey configKey1(0, 12345);
const ConfigKey configKey2(0, 123456);
const int64_t subscriptionId1 = 1, subscriptionId2 = 2;

}  // anonymous namespace

class SubscriberReporterTest : public ::testing::Test {
public:
    SubscriberReporterTest() {
    }
    const shared_ptr<MockPendingIntentRef> pir1 =
            SharedRefBase::make<StrictMock<MockPendingIntentRef>>();
    const shared_ptr<MockPendingIntentRef> pir2 =
            SharedRefBase::make<StrictMock<MockPendingIntentRef>>();
    const shared_ptr<MockPendingIntentRef> pir3 =
            SharedRefBase::make<StrictMock<MockPendingIntentRef>>();
    // Two subscribers on config key 1, one on config key 2.
    void SetUp() override {
        SubscriberReporter::getInstance().setBroadcastSubscriber(configKey1, subscriptionId1, pir1);
        SubscriberReporter::getInstance().setBroadcastSubscriber(configKey1, subscriptionId2, pir2);
        SubscriberReporter::getInstance().setBroadcastSubscriber(configKey2, subscriptionId1, pir3);
    }

    void TearDown() override {
        SubscriberReporter::getInstance();
        SubscriberReporter::getInstance().unsetBroadcastSubscriber(configKey1, subscriptionId1);
        SubscriberReporter::getInstance().unsetBroadcastSubscriber(configKey1, subscriptionId2);
        SubscriberReporter::getInstance().unsetBroadcastSubscriber(configKey2, subscriptionId1);
        EXPECT_EQ(pir1.use_count(), 1);
        EXPECT_EQ(pir2.use_count(), 1);
        EXPECT_EQ(pir3.use_count(), 1);
        ASSERT_TRUE(SubscriberReporter::getInstance().mIntentMap.empty());
    }
};

TEST_F(SubscriberReporterTest, TestBroadcastSubscriberDeathRemovesPir) {
    SubscriberReporter::broadcastSubscriberDied(pir1.get());
    EXPECT_EQ(pir1.use_count(), 1);

    unordered_map<ConfigKey, unordered_map<int64_t, shared_ptr<IPendingIntentRef>>>
            expectedIntentMap = {{configKey1, {{subscriptionId2, pir2}}},
                                 {configKey2, {{subscriptionId1, pir3}}}};
    EXPECT_THAT(SubscriberReporter::getInstance().mIntentMap, ContainerEq(expectedIntentMap));
}

TEST_F(SubscriberReporterTest, TestBroadcastSubscriberDeathRemovesPirAndConfigKey) {
    SubscriberReporter::broadcastSubscriberDied(pir3.get());

    EXPECT_EQ(pir3.use_count(), 1);
    unordered_map<ConfigKey, unordered_map<int64_t, shared_ptr<IPendingIntentRef>>>
            expectedIntentMap = {{configKey1, {{subscriptionId1, pir1}, {subscriptionId2, pir2}}}};
    EXPECT_THAT(SubscriberReporter::getInstance().mIntentMap, ContainerEq(expectedIntentMap));
}

TEST_F(SubscriberReporterTest, TestBroadcastSubscriberDeathKeepsReplacedPir) {
    const shared_ptr<MockPendingIntentRef> pir4 =
            SharedRefBase::make<StrictMock<MockPendingIntentRef>>();
    SubscriberReporter::getInstance().setBroadcastSubscriber(configKey1, subscriptionId1, pir4);

    // pir1 dies, but pir4 has replaced it with the same keys. The map should remain the same.
    SubscriberReporter::broadcastSubscriberDied(pir1.get());

    unordered_map<ConfigKey, unordered_map<int64_t, shared_ptr<IPendingIntentRef>>>
            expectedIntentMap = {{configKey1, {{subscriptionId1, pir4}, {subscriptionId2, pir2}}},
                                 {configKey2, {{subscriptionId1, pir3}}}};
    EXPECT_THAT(SubscriberReporter::getInstance().mIntentMap, ContainerEq(expectedIntentMap));
}
}  // namespace statsd
}  // namespace os
}  // namespace android
#else
GTEST_LOG_(INFO) << "This test does nothing.\n";
#endif
