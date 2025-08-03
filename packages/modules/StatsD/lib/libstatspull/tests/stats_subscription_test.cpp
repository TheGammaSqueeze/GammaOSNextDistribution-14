/*
 * Copyright (C) 2023, The Android Open Source Project
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

#include <binder/ProcessState.h>
#include <gmock/gmock.h>
#include <gtest/gtest.h>
#include <gtest_matchers.h>
#include <stats_subscription.h>
#include <stdint.h>
#include <utils/Looper.h>

#include <chrono>
#include <string>
#include <thread>
#include <vector>

#include "packages/modules/StatsD/statsd/src/shell/shell_config.pb.h"
#include "packages/modules/StatsD/statsd/src/shell/shell_data.pb.h"
#include "statslog_statsdtest.h"

#ifdef __ANDROID__

using namespace testing;
using android::Looper;
using android::ProcessState;
using android::sp;
using android::os::statsd::Atom;
using android::os::statsd::ShellData;
using android::os::statsd::ShellSubscription;
using android::os::statsd::TestAtomReported_State_OFF;
using android::os::statsd::TrainExperimentIds;
using android::os::statsd::util::BytesField;
using android::os::statsd::util::SCREEN_BRIGHTNESS_CHANGED;
using android::os::statsd::util::stats_write;
using android::os::statsd::util::TEST_ATOM_REPORTED;
using android::os::statsd::util::TEST_ATOM_REPORTED__REPEATED_ENUM_FIELD__OFF;
using std::string;
using std::vector;
using std::this_thread::sleep_for;

namespace {

class SubscriptionTest : public Test {
public:
    SubscriptionTest() : looper(Looper::prepare(/*opts=*/0)) {
        const TestInfo* const test_info = UnitTest::GetInstance()->current_test_info();
        ALOGD("**** Setting up for %s.%s\n", test_info->test_case_name(), test_info->name());

        *trainExpIds.mutable_experiment_id() = {expIds.begin(), expIds.end()};
        trainExpIds.SerializeToString(&trainExpIdsBytes);
    }

    ~SubscriptionTest() {
        const TestInfo* const test_info = UnitTest::GetInstance()->current_test_info();
        ALOGD("**** Tearing down after %s.%s\n", test_info->test_case_name(), test_info->name());
    }

protected:
    void SetUp() override {
        // Start the Binder thread pool.
        ProcessState::self()->startThreadPool();
    }

    void TearDown() {
        // Clear any dangling subscriptions from statsd.
        if (__builtin_available(android __STATSD_SUBS_MIN_API__, *)) {
            AStatsManager_removeSubscription(subId);
        }
    }

    void LogTestAtomReported(int32_t intFieldValue) {
        const BytesField bytesField(trainExpIdsBytes.data(), trainExpIdsBytes.size());
        stats_write(TEST_ATOM_REPORTED, uids.data(), uids.size(), tags, intFieldValue,
                    /*long_field=*/2LL, /*float_field=*/3.0F,
                    /*string_field=*/string1.c_str(),
                    /*boolean_field=*/false, /*state=*/TEST_ATOM_REPORTED__REPEATED_ENUM_FIELD__OFF,
                    bytesField, repeatedInts, repeatedLongs, repeatedFloats, repeatedStrings,
                    &(repeatedBool[0]), /*repeatedBoolSize=*/2, repeatedEnums);
    }

    int32_t subId;

    // TestAtomReported fields.
    const vector<int32_t> uids = {1};
    const string tag = "test";
    const vector<char const*> tags = {tag.c_str()};

    // 100 int64s for the MODE_BYTES field to push atom size to over 1K.
    const vector<int64_t> expIds = vector<int64_t>(100, INT64_MAX);

    const vector<int32_t> repeatedInts{1};
    const vector<int64_t> repeatedLongs{2LL};
    const vector<float> repeatedFloats{3.0F};
    const string string1 = "ABC";
    const vector<char const*> repeatedStrings = {string1.c_str()};
    const bool repeatedBool[2] = {false, true};
    const vector<int32_t> repeatedEnums = {TEST_ATOM_REPORTED__REPEATED_ENUM_FIELD__OFF};
    TrainExperimentIds trainExpIds;
    string trainExpIdsBytes;

private:
    sp<Looper> looper;
};

// Stores arguments passed in subscription callback.
struct CallbackData {
    int32_t subId;
    AStatsManager_SubscriptionCallbackReason reason;
    vector<uint8_t> payload;
    int count;  // Stores number of times the callback is invoked.
};

static void callback(int32_t subscription_id, AStatsManager_SubscriptionCallbackReason reason,
                     uint8_t* _Nonnull payload, size_t num_bytes, void* _Nullable cookie) {
    CallbackData* data = static_cast<CallbackData*>(cookie);
    data->subId = subscription_id;
    data->reason = reason;
    data->payload.assign(payload, payload + num_bytes);
    data->count++;
}

constexpr static int WAIT_MS = 500;

TEST_F(SubscriptionTest, TestSubscription) {
    if (__builtin_available(android __STATSD_SUBS_MIN_API__, *)) {
        ShellSubscription config;
        config.add_pushed()->set_atom_id(TEST_ATOM_REPORTED);
        config.add_pushed()->set_atom_id(SCREEN_BRIGHTNESS_CHANGED);

        string configBytes;
        config.SerializeToString(&configBytes);

        CallbackData callbackData{/*subId=*/0,
                                  ASTATSMANAGER_SUBSCRIPTION_CALLBACK_REASON_SUBSCRIPTION_ENDED,
                                  /*payload=*/{},
                                  /*count=*/0};

        // Add subscription.
        subId = AStatsManager_addSubscription(reinterpret_cast<const uint8_t*>(configBytes.data()),
                                              configBytes.size(), &callback, &callbackData);
        ASSERT_GT(subId, 0);
        sleep_for(std::chrono::milliseconds(WAIT_MS));

        // Log events without exceeding statsd cache.
        stats_write(SCREEN_BRIGHTNESS_CHANGED, 100);
        LogTestAtomReported(1);
        sleep_for(std::chrono::milliseconds(WAIT_MS));

        // Verify no callback occurred yet.
        EXPECT_EQ(callbackData.subId, 0);
        EXPECT_EQ(callbackData.reason,
                  ASTATSMANAGER_SUBSCRIPTION_CALLBACK_REASON_SUBSCRIPTION_ENDED);
        EXPECT_EQ(callbackData.count, 0);
        ASSERT_TRUE(callbackData.payload.empty());

        // Log another TestAtomReported to overflow cache.
        LogTestAtomReported(2);
        sleep_for(std::chrono::milliseconds(WAIT_MS));

        // Verify callback occurred.
        EXPECT_EQ(callbackData.subId, subId);
        EXPECT_EQ(callbackData.reason, ASTATSMANAGER_SUBSCRIPTION_CALLBACK_REASON_STATSD_INITIATED);
        EXPECT_EQ(callbackData.count, 1);
        ASSERT_GT(callbackData.payload.size(), 0);

        ShellData actualShellData;
        ASSERT_TRUE(actualShellData.ParseFromArray(callbackData.payload.data(),
                                                   callbackData.payload.size()));

        ASSERT_GE(actualShellData.elapsed_timestamp_nanos_size(), 3);
        EXPECT_THAT(actualShellData.elapsed_timestamp_nanos(), Each(Gt(0LL)));

        ASSERT_GE(actualShellData.atom_size(), 3);

        // Verify atom 1.
        Atom expectedAtom;
        expectedAtom.mutable_screen_brightness_changed()->set_level(100);
        EXPECT_THAT(actualShellData.atom(0), EqAtom(expectedAtom));

        // Verify atom 2.
        expectedAtom.Clear();
        auto* testAtomReported = expectedAtom.mutable_test_atom_reported();
        auto* attributionNode = testAtomReported->add_attribution_node();
        attributionNode->set_uid(uids[0]);
        attributionNode->set_tag(tag);
        testAtomReported->set_int_field(1);
        testAtomReported->set_long_field(2LL);
        testAtomReported->set_float_field(3.0F);
        testAtomReported->set_string_field(string1);
        testAtomReported->set_boolean_field(false);
        testAtomReported->set_state(TestAtomReported_State_OFF);
        *testAtomReported->mutable_bytes_field() = trainExpIds;
        *testAtomReported->mutable_repeated_int_field() = {repeatedInts.begin(),
                                                           repeatedInts.end()};
        *testAtomReported->mutable_repeated_long_field() = {repeatedLongs.begin(),
                                                            repeatedLongs.end()};
        *testAtomReported->mutable_repeated_float_field() = {repeatedFloats.begin(),
                                                             repeatedFloats.end()};
        *testAtomReported->mutable_repeated_string_field() = {repeatedStrings.begin(),
                                                              repeatedStrings.end()};
        *testAtomReported->mutable_repeated_boolean_field() = {&repeatedBool[0],
                                                               &repeatedBool[0] + 2};
        *testAtomReported->mutable_repeated_enum_field() = {repeatedEnums.begin(),
                                                            repeatedEnums.end()};
        EXPECT_THAT(actualShellData.atom(1), EqAtom(expectedAtom));

        // Verify atom 3.
        testAtomReported->set_int_field(2);
        EXPECT_THAT(actualShellData.atom(2), EqAtom(expectedAtom));

        // Log another ScreenBrightnessChanged atom. No callback should occur.
        stats_write(SCREEN_BRIGHTNESS_CHANGED, 99);
        sleep_for(std::chrono::milliseconds(WAIT_MS));
        EXPECT_EQ(callbackData.count, 1);

        // Flush subscription. Callback should occur.
        AStatsManager_flushSubscription(subId);
        sleep_for(std::chrono::milliseconds(WAIT_MS));

        EXPECT_EQ(callbackData.subId, subId);
        EXPECT_EQ(callbackData.reason, ASTATSMANAGER_SUBSCRIPTION_CALLBACK_REASON_FLUSH_REQUESTED);
        EXPECT_EQ(callbackData.count, 2);
        ASSERT_GT(callbackData.payload.size(), 0);

        ASSERT_TRUE(actualShellData.ParseFromArray(callbackData.payload.data(),
                                                   callbackData.payload.size()));

        ASSERT_GE(actualShellData.elapsed_timestamp_nanos_size(), 1);
        EXPECT_THAT(actualShellData.elapsed_timestamp_nanos(), Each(Gt(0LL)));

        ASSERT_GE(actualShellData.atom_size(), 1);

        // Verify atom 1.
        expectedAtom.Clear();
        expectedAtom.mutable_screen_brightness_changed()->set_level(99);
        EXPECT_THAT(actualShellData.atom(0), EqAtom(expectedAtom));

        // Log another ScreenBrightnessChanged atom. No callback should occur.
        stats_write(SCREEN_BRIGHTNESS_CHANGED, 98);
        sleep_for(std::chrono::milliseconds(WAIT_MS));
        EXPECT_EQ(callbackData.count, 2);

        // Trigger callback through cache timeout.
        // Two 500 ms sleeps have occurred already so the total sleep is 71000 ms since last
        // callback invocation.
        sleep_for(std::chrono::milliseconds(70'000));
        EXPECT_EQ(callbackData.subId, subId);
        EXPECT_EQ(callbackData.reason, ASTATSMANAGER_SUBSCRIPTION_CALLBACK_REASON_STATSD_INITIATED);
        EXPECT_EQ(callbackData.count, 3);
        ASSERT_GT(callbackData.payload.size(), 0);

        ASSERT_TRUE(actualShellData.ParseFromArray(callbackData.payload.data(),
                                                   callbackData.payload.size()));

        ASSERT_GE(actualShellData.elapsed_timestamp_nanos_size(), 1);
        EXPECT_THAT(actualShellData.elapsed_timestamp_nanos(), Each(Gt(0LL)));

        ASSERT_GE(actualShellData.atom_size(), 1);

        // Verify atom 1.
        expectedAtom.Clear();
        expectedAtom.mutable_screen_brightness_changed()->set_level(98);
        EXPECT_THAT(actualShellData.atom(0), EqAtom(expectedAtom));

        // Log another ScreenBrightnessChanged atom. No callback should occur.
        stats_write(SCREEN_BRIGHTNESS_CHANGED, 97);
        sleep_for(std::chrono::milliseconds(WAIT_MS));
        EXPECT_EQ(callbackData.count, 3);

        // End subscription. Final callback should occur.
        AStatsManager_removeSubscription(subId);
        sleep_for(std::chrono::milliseconds(WAIT_MS));

        EXPECT_EQ(callbackData.subId, subId);
        EXPECT_EQ(callbackData.reason,
                  ASTATSMANAGER_SUBSCRIPTION_CALLBACK_REASON_SUBSCRIPTION_ENDED);
        EXPECT_EQ(callbackData.count, 4);
        ASSERT_GT(callbackData.payload.size(), 0);

        ASSERT_TRUE(actualShellData.ParseFromArray(callbackData.payload.data(),
                                                   callbackData.payload.size()));

        ASSERT_GE(actualShellData.elapsed_timestamp_nanos_size(), 1);
        EXPECT_THAT(actualShellData.elapsed_timestamp_nanos(), Each(Gt(0LL)));

        ASSERT_GE(actualShellData.atom_size(), 1);

        // Verify atom 1.
        expectedAtom.Clear();
        expectedAtom.mutable_screen_brightness_changed()->set_level(97);
        EXPECT_THAT(actualShellData.atom(0), EqAtom(expectedAtom));
    } else {
        GTEST_SKIP();
    }
}

}  // namespace

#else
GTEST_LOG_(INFO) << "This test does nothing.\n";
#endif
