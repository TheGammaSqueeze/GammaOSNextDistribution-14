/*
 * Copyright 2022 The Android Open Source Project
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

#include "ThreadPriorityController.h"

#include <gmock/gmock.h>
#include <gtest/gtest.h>

namespace android {
namespace automotive {
namespace watchdog {
namespace {

using ::aidl::android::automotive::watchdog::internal::ThreadPolicyWithPriority;
using ::android::base::Result;
using ::testing::_;
using ::testing::Return;

MATCHER_P(PriorityEq, priority, "") {
    return (arg->sched_priority) == priority;
}

class MockSystemCalls : public ThreadPriorityController::SystemCallsInterface {
public:
    MockSystemCalls(int tid, int uid, int pid) {
        ON_CALL(*this, readPidStatusFileForPid(tid))
                .WillByDefault(Return(std::make_tuple(uid, pid)));
    }

    MOCK_METHOD(int, setScheduler, (pid_t tid, int policy, const sched_param* param), (override));
    MOCK_METHOD(int, getScheduler, (pid_t tid), (override));
    MOCK_METHOD(int, getParam, (pid_t tid, sched_param* param), (override));
    MOCK_METHOD((Result<std::tuple<uid_t, pid_t>>), readPidStatusFileForPid, (pid_t pid),
                (override));
};

class ThreadPriorityControllerTest : public ::testing::Test {
public:
    virtual void SetUp() {
        std::unique_ptr<MockSystemCalls> mockSystemCalls =
                std::make_unique<MockSystemCalls>(TEST_TID, TEST_UID, TEST_PID);
        mMockSystemCalls = mockSystemCalls.get();
        mController = std::make_unique<ThreadPriorityController>(std::move(mockSystemCalls));
    }

protected:
    static constexpr pid_t TEST_PID = 1;
    static constexpr pid_t TEST_TID = 2;
    static constexpr uid_t TEST_UID = 3;

    std::unique_ptr<ThreadPriorityController> mController;
    MockSystemCalls* mMockSystemCalls;
};

TEST_F(ThreadPriorityControllerTest, TestSetThreadPriority) {
    int policy = SCHED_FIFO;
    int priority = 1;
    EXPECT_CALL(*mMockSystemCalls, setScheduler(TEST_TID, policy, PriorityEq(priority)))
            .WillOnce(Return(0));

    auto result = mController->setThreadPriority(TEST_PID, TEST_TID, TEST_UID, policy, priority);

    ASSERT_TRUE(result.ok()) << result.error().message();
}

TEST_F(ThreadPriorityControllerTest, TestSetThreadPriorityDefaultPolicy) {
    int policy = SCHED_OTHER;
    int setPriority = 1;
    // Default policy should ignore the provided priority.
    int expectedPriority = 0;
    EXPECT_CALL(*mMockSystemCalls, setScheduler(TEST_TID, policy, PriorityEq(expectedPriority)))
            .WillOnce(Return(0));

    auto result = mController->setThreadPriority(TEST_PID, TEST_TID, TEST_UID, policy, setPriority);

    ASSERT_TRUE(result.ok()) << result.error().message();
}

TEST_F(ThreadPriorityControllerTest, TestSetThreadPriorityInvalidPid) {
    auto result = mController->setThreadPriority(TEST_PID + 1, TEST_TID, TEST_UID, SCHED_FIFO, 1);

    EXPECT_FALSE(result.ok());
    EXPECT_EQ(result.error().code(), EX_ILLEGAL_STATE);
}

TEST_F(ThreadPriorityControllerTest, TestSetThreadPriorityInvalidTid) {
    auto result = mController->setThreadPriority(TEST_PID, TEST_TID + 1, TEST_UID, SCHED_FIFO, 1);

    EXPECT_FALSE(result.ok());
    EXPECT_EQ(result.error().code(), EX_ILLEGAL_STATE);
}

TEST_F(ThreadPriorityControllerTest, TestSetThreadPriorityInvalidUid) {
    auto result = mController->setThreadPriority(TEST_PID, TEST_TID, TEST_UID + 1, SCHED_FIFO, 1);

    EXPECT_FALSE(result.ok());
    EXPECT_EQ(result.error().code(), EX_ILLEGAL_STATE);
}

TEST_F(ThreadPriorityControllerTest, TestSetThreadPriorityInvalidPolicy) {
    auto result = mController->setThreadPriority(TEST_PID, TEST_TID, TEST_UID, -1, 1);

    EXPECT_FALSE(result.ok());
    EXPECT_EQ(result.error().code(), EX_ILLEGAL_ARGUMENT);
}

TEST_F(ThreadPriorityControllerTest, TestSetThreadPriorityInvalidPriority) {
    auto result = mController->setThreadPriority(TEST_PID, TEST_TID, TEST_UID, SCHED_FIFO, 0);

    EXPECT_FALSE(result.ok());
    EXPECT_EQ(result.error().code(), EX_ILLEGAL_ARGUMENT);
}

TEST_F(ThreadPriorityControllerTest, TestSetThreadPriorityFailed) {
    int expectedPolicy = SCHED_FIFO;
    int expectedPriority = 1;
    EXPECT_CALL(*mMockSystemCalls,
                setScheduler(TEST_TID, expectedPolicy, PriorityEq(expectedPriority)))
            .WillOnce(Return(-1));

    auto result = mController->setThreadPriority(TEST_PID, TEST_TID, TEST_UID, expectedPolicy,
                                                 expectedPriority);

    EXPECT_FALSE(result.ok());
    EXPECT_EQ(result.error().code(), EX_SERVICE_SPECIFIC);
}

TEST_F(ThreadPriorityControllerTest, TestGetThreadPriority) {
    int expectedPolicy = SCHED_FIFO;
    int expectedPriority = 1;
    EXPECT_CALL(*mMockSystemCalls, getScheduler(TEST_TID)).WillOnce(Return(expectedPolicy));
    EXPECT_CALL(*mMockSystemCalls, getParam(TEST_TID, _))
            .WillOnce([expectedPriority](pid_t, sched_param* param) {
                param->sched_priority = expectedPriority;
                return 0;
            });

    ThreadPolicyWithPriority actual;
    auto result = mController->getThreadPriority(TEST_PID, TEST_TID, TEST_UID, &actual);

    ASSERT_TRUE(result.ok()) << result.error().message();
    EXPECT_EQ(actual.policy, expectedPolicy);
    EXPECT_EQ(actual.priority, expectedPriority);
}

TEST_F(ThreadPriorityControllerTest, TestGetThreadPriorityInvalidPid) {
    ThreadPolicyWithPriority actual;
    auto result = mController->getThreadPriority(TEST_PID + 1, TEST_TID, TEST_UID, &actual);

    EXPECT_FALSE(result.ok());
    EXPECT_EQ(result.error().code(), EX_ILLEGAL_STATE);
}

TEST_F(ThreadPriorityControllerTest, TestGetThreadPriorityGetSchedulerFailed) {
    EXPECT_CALL(*mMockSystemCalls, getScheduler(TEST_TID)).WillOnce(Return(-1));

    ThreadPolicyWithPriority actual;
    auto result = mController->getThreadPriority(TEST_PID, TEST_TID, TEST_UID, &actual);

    EXPECT_FALSE(result.ok());
    EXPECT_EQ(result.error().code(), EX_SERVICE_SPECIFIC);
}

TEST_F(ThreadPriorityControllerTest, TestGetThreadPriorityGetParamFailed) {
    EXPECT_CALL(*mMockSystemCalls, getScheduler(TEST_TID)).WillOnce(Return(0));
    EXPECT_CALL(*mMockSystemCalls, getParam(TEST_TID, _)).WillOnce(Return(-1));

    ThreadPolicyWithPriority actual;
    auto result = mController->getThreadPriority(TEST_PID, TEST_TID, TEST_UID, &actual);

    EXPECT_FALSE(result.ok());
    EXPECT_EQ(result.error().code(), EX_SERVICE_SPECIFIC);
}

}  // namespace
}  // namespace watchdog
}  // namespace automotive
}  // namespace android
