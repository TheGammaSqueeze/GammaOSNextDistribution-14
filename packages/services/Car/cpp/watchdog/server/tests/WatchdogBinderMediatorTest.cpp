/*
 * Copyright 2020 The Android Open Source Project
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

#include "MockIoOveruseMonitor.h"
#include "MockResourceOveruseListener.h"
#include "MockWatchdogInternalHandler.h"
#include "MockWatchdogPerfService.h"
#include "MockWatchdogProcessService.h"
#include "MockWatchdogServiceHelper.h"
#include "WatchdogBinderMediator.h"

#include <android-base/stringprintf.h>
#include <binder/IBinder.h>
#include <gmock/gmock.h>
#include <gtest/gtest.h>
#include <utils/String16.h>

#include <errno.h>

namespace android {
namespace automotive {
namespace watchdog {

using ::aidl::android::automotive::watchdog::ICarWatchdogClient;
using ::aidl::android::automotive::watchdog::ICarWatchdogClientDefault;
using ::aidl::android::automotive::watchdog::IoOveruseStats;
using ::aidl::android::automotive::watchdog::IResourceOveruseListener;
using ::aidl::android::automotive::watchdog::ResourceOveruseStats;
using ::aidl::android::automotive::watchdog::ResourceType;
using ::aidl::android::automotive::watchdog::StateType;
using ::aidl::android::automotive::watchdog::TimeoutLength;
using ::android::sp;
using ::android::String16;
using ::android::base::Result;
using ::android::base::StringAppendF;
using ::ndk::ICInterface;
using ::ndk::ScopedAStatus;
using ::ndk::SharedRefBase;
using ::testing::_;
using ::testing::ByMove;
using ::testing::DoAll;
using ::testing::Return;
using ::testing::SetArgPointee;
using ::testing::UnorderedElementsAreArray;

namespace {

const std::function<android::base::Result<void>(const char*, ICInterface*, bool, int)>
        kAddServiceFunctionStub =
                [](const char*, ICInterface*, bool, int) -> Result<void> { return Result<void>{}; };

std::string toString(const std::vector<ResourceOveruseStats>& resourceOveruseStats) {
    std::string buffer;
    for (const auto& stats : resourceOveruseStats) {
        StringAppendF(&buffer, "%s\n", stats.toString().c_str());
    }
    return buffer;
}

}  // namespace

namespace internal {

class WatchdogBinderMediatorPeer final {
public:
    explicit WatchdogBinderMediatorPeer(WatchdogBinderMediator* mediator) : mMediator(mediator) {}

    void setWatchdogInternalHandler(
            const std::shared_ptr<WatchdogInternalHandlerInterface>& watchdogInternalHandler) {
        mMediator->mWatchdogInternalHandler = watchdogInternalHandler;
    }

private:
    WatchdogBinderMediator* mMediator;
};

};  // namespace internal

class WatchdogBinderMediatorTest : public ::testing::Test {
protected:
    virtual void SetUp() {
        mMockWatchdogProcessService = sp<MockWatchdogProcessService>::make();
        mMockWatchdogPerfService = sp<MockWatchdogPerfService>::make();
        mMockIoOveruseMonitor = sp<MockIoOveruseMonitor>::make();
        mWatchdogBinderMediator =
                SharedRefBase::make<WatchdogBinderMediator>(mMockWatchdogProcessService,
                                                            mMockWatchdogPerfService,
                                                            sp<MockWatchdogServiceHelper>::make(),
                                                            mMockIoOveruseMonitor,
                                                            kAddServiceFunctionStub);
        mMockWatchdogInternalHandler = SharedRefBase::make<MockWatchdogInternalHandler>();
        internal::WatchdogBinderMediatorPeer peer(mWatchdogBinderMediator.get());
        peer.setWatchdogInternalHandler(mMockWatchdogInternalHandler);
    }

    virtual void TearDown() {
        mMockWatchdogProcessService.clear();
        mMockWatchdogPerfService.clear();
        mMockIoOveruseMonitor.clear();
        mWatchdogBinderMediator.reset();
    }

    sp<MockWatchdogProcessService> mMockWatchdogProcessService;
    sp<MockWatchdogPerfService> mMockWatchdogPerfService;
    sp<MockIoOveruseMonitor> mMockIoOveruseMonitor;
    std::shared_ptr<MockWatchdogInternalHandler> mMockWatchdogInternalHandler;
    std::shared_ptr<WatchdogBinderMediator> mWatchdogBinderMediator;
};

TEST_F(WatchdogBinderMediatorTest, TestInit) {
    std::shared_ptr<WatchdogBinderMediator> mediator =
            SharedRefBase::make<WatchdogBinderMediator>(sp<MockWatchdogProcessService>::make(),
                                                        sp<MockWatchdogPerfService>::make(),
                                                        sp<MockWatchdogServiceHelper>::make(),
                                                        sp<MockIoOveruseMonitor>::make(),
                                                        kAddServiceFunctionStub);

    ASSERT_RESULT_OK(mediator->init());

    ASSERT_NE(mediator->mWatchdogProcessService, nullptr);
    ASSERT_NE(mediator->mWatchdogPerfService, nullptr);
    ASSERT_NE(mediator->mIoOveruseMonitor, nullptr);
    ASSERT_NE(mediator->mWatchdogInternalHandler, nullptr);
}

TEST_F(WatchdogBinderMediatorTest, TestErrorOnInitWithNullServiceInstances) {
    auto mockWatchdogProcessService = sp<MockWatchdogProcessService>::make();
    auto mockWatchdogPerfservice = sp<MockWatchdogPerfService>::make();
    auto mockWatchdogServiceHelper = sp<MockWatchdogServiceHelper>::make();
    auto mockIoOveruseMonitor = sp<MockIoOveruseMonitor>::make();
    std::shared_ptr<WatchdogBinderMediator> mediator =
            SharedRefBase::make<WatchdogBinderMediator>(nullptr, mockWatchdogPerfservice,
                                                        mockWatchdogServiceHelper,
                                                        mockIoOveruseMonitor,
                                                        kAddServiceFunctionStub);

    EXPECT_FALSE(mediator->init().ok()) << "No error returned on nullptr watchdog process service";
    mediator.reset();

    mediator = SharedRefBase::make<WatchdogBinderMediator>(mockWatchdogProcessService, nullptr,
                                                           mockWatchdogServiceHelper,
                                                           mockIoOveruseMonitor,
                                                           kAddServiceFunctionStub);

    EXPECT_FALSE(mediator->init().ok()) << "No error returned on nullptr watchdog perf service";
    mediator.reset();

    mediator = SharedRefBase::make<WatchdogBinderMediator>(mockWatchdogProcessService,
                                                           mockWatchdogPerfservice, nullptr,
                                                           mockIoOveruseMonitor,
                                                           kAddServiceFunctionStub);

    EXPECT_FALSE(mediator->init().ok()) << "No error returned on nullptr watchdog service helper";
    mediator.reset();

    mediator = SharedRefBase::make<WatchdogBinderMediator>(mockWatchdogProcessService,
                                                           mockWatchdogPerfservice,
                                                           mockWatchdogServiceHelper, nullptr,
                                                           kAddServiceFunctionStub);

    EXPECT_FALSE(mediator->init().ok()) << "No error returned on nullptr I/O overuse monitor";
    mediator.reset();

    mediator = SharedRefBase::make<WatchdogBinderMediator>(nullptr, nullptr, nullptr, nullptr,
                                                           kAddServiceFunctionStub);

    EXPECT_FALSE(mediator->init().ok()) << "No error returned on null services";
    mediator.reset();
}

TEST_F(WatchdogBinderMediatorTest, TestDump) {
    const char* args[] = {kStartCustomCollectionFlag, kIntervalFlag, "10", kMaxDurationFlag, "200"};
    EXPECT_CALL(*mMockWatchdogInternalHandler, dump(-1, args, /*numArgs=*/5)).WillOnce(Return(OK));

    ASSERT_EQ(mWatchdogBinderMediator->dump(-1, args, /*numArgs=*/5), OK);
}

TEST_F(WatchdogBinderMediatorTest, TestRegisterClient) {
    std::shared_ptr<ICarWatchdogClient> client = SharedRefBase::make<ICarWatchdogClientDefault>();
    TimeoutLength timeout = TimeoutLength::TIMEOUT_MODERATE;

    EXPECT_CALL(*mMockWatchdogProcessService, registerClient(client, timeout))
            .WillOnce(Return(ByMove(ScopedAStatus::ok())));

    auto status = mWatchdogBinderMediator->registerClient(client, timeout);

    ASSERT_TRUE(status.isOk()) << status.getMessage();
}

TEST_F(WatchdogBinderMediatorTest, TestUnregisterClient) {
    std::shared_ptr<ICarWatchdogClient> client = SharedRefBase::make<ICarWatchdogClientDefault>();

    EXPECT_CALL(*mMockWatchdogProcessService, unregisterClient(client))
            .WillOnce(Return(ByMove(ScopedAStatus::ok())));

    auto status = mWatchdogBinderMediator->unregisterClient(client);

    ASSERT_TRUE(status.isOk()) << status.getMessage();
}

TEST_F(WatchdogBinderMediatorTest, TestTellClientAlive) {
    std::shared_ptr<ICarWatchdogClient> client = SharedRefBase::make<ICarWatchdogClientDefault>();

    EXPECT_CALL(*mMockWatchdogProcessService, tellClientAlive(client, 456))
            .WillOnce(Return(ByMove(ScopedAStatus::ok())));

    auto status = mWatchdogBinderMediator->tellClientAlive(client, 456);

    ASSERT_TRUE(status.isOk()) << status.getMessage();
}

TEST_F(WatchdogBinderMediatorTest, TestAddResourceOveruseListener) {
    std::shared_ptr<IResourceOveruseListener> listener =
            SharedRefBase::make<MockResourceOveruseListener>();

    EXPECT_CALL(*mMockIoOveruseMonitor, addIoOveruseListener(listener))
            .WillOnce(Return(Result<void>{}));

    auto status = mWatchdogBinderMediator->addResourceOveruseListener({ResourceType::IO}, listener);

    ASSERT_TRUE(status.isOk()) << status.getMessage();
}

TEST_F(WatchdogBinderMediatorTest, TestErrorsAddResourceOveruseListenerOnInvalidArgs) {
    std::shared_ptr<IResourceOveruseListener> listener =
            SharedRefBase::make<MockResourceOveruseListener>();
    EXPECT_CALL(*mMockIoOveruseMonitor, addIoOveruseListener(listener)).Times(0);

    ASSERT_FALSE(mWatchdogBinderMediator->addResourceOveruseListener({}, listener).isOk())
            << "Should fail on empty resource types";

    ASSERT_FALSE(
            mWatchdogBinderMediator->addResourceOveruseListener({ResourceType::IO}, nullptr).isOk())
            << "Should fail on null listener";
}

TEST_F(WatchdogBinderMediatorTest, TestRemoveResourceOveruseListener) {
    std::shared_ptr<IResourceOveruseListener> listener =
            SharedRefBase::make<MockResourceOveruseListener>();

    EXPECT_CALL(*mMockIoOveruseMonitor, removeIoOveruseListener(listener))
            .WillOnce(Return(Result<void>{}));

    auto status = mWatchdogBinderMediator->removeResourceOveruseListener(listener);

    ASSERT_TRUE(status.isOk()) << status.getMessage();
}

TEST_F(WatchdogBinderMediatorTest, TestGetResourceOveruseStats) {
    IoOveruseStats ioOveruseStats;
    ioOveruseStats.killableOnOveruse = true;
    ioOveruseStats.startTime = 99898;
    ioOveruseStats.durationInSeconds = 12345;
    ioOveruseStats.totalOveruses = 3;
    std::vector<ResourceOveruseStats> expected;
    ResourceOveruseStats stats;
    stats.set<ResourceOveruseStats::ioOveruseStats>(ioOveruseStats);
    expected.emplace_back(std::move(stats));

    EXPECT_CALL(*mMockIoOveruseMonitor, getIoOveruseStats(_))
            .WillOnce(DoAll(SetArgPointee<0>(ioOveruseStats), Return(Result<void>{})));

    std::vector<ResourceOveruseStats> actual;
    auto status = mWatchdogBinderMediator->getResourceOveruseStats({ResourceType::IO}, &actual);

    ASSERT_TRUE(status.isOk()) << status.getMessage();
    EXPECT_THAT(actual, UnorderedElementsAreArray(expected))
            << "Expected: " << toString(expected) << "\nActual: " << toString(actual);
}

TEST_F(WatchdogBinderMediatorTest, TestErrorsGetResourceOveruseStatsOnInvalidArgs) {
    EXPECT_CALL(*mMockIoOveruseMonitor, getIoOveruseStats(_)).Times(0);

    std::vector<ResourceOveruseStats> actual;
    ASSERT_FALSE(mWatchdogBinderMediator->getResourceOveruseStats({}, &actual).isOk())
            << "Should fail on empty resource types";

    ASSERT_FALSE(
            mWatchdogBinderMediator->getResourceOveruseStats({ResourceType::IO}, nullptr).isOk())
            << "Should fail on null listener";
}

TEST_F(WatchdogBinderMediatorTest, TestRegisterMediator) {
    auto status = mWatchdogBinderMediator->registerMediator(nullptr);
    ASSERT_EQ(status.getExceptionCode(), EX_UNSUPPORTED_OPERATION);
}

TEST_F(WatchdogBinderMediatorTest, TestUnregisterMediator) {
    auto status = mWatchdogBinderMediator->unregisterMediator(nullptr);
    ASSERT_EQ(status.getExceptionCode(), EX_UNSUPPORTED_OPERATION);
}

TEST_F(WatchdogBinderMediatorTest, TestRegisterMonitor) {
    auto status = mWatchdogBinderMediator->registerMonitor(nullptr);
    ASSERT_EQ(status.getExceptionCode(), EX_UNSUPPORTED_OPERATION);
}

TEST_F(WatchdogBinderMediatorTest, TestUnregisterMonitor) {
    auto status = mWatchdogBinderMediator->unregisterMonitor(nullptr);
    ASSERT_EQ(status.getExceptionCode(), EX_UNSUPPORTED_OPERATION);
}

TEST_F(WatchdogBinderMediatorTest, TestTellMediatorAlive) {
    auto status = mWatchdogBinderMediator->tellMediatorAlive(nullptr, {}, 0);
    ASSERT_EQ(status.getExceptionCode(), EX_UNSUPPORTED_OPERATION);
}

TEST_F(WatchdogBinderMediatorTest, TestTellDumpFinished) {
    auto status = mWatchdogBinderMediator->tellDumpFinished(nullptr, 0);
    ASSERT_EQ(status.getExceptionCode(), EX_UNSUPPORTED_OPERATION);
}

TEST_F(WatchdogBinderMediatorTest, TestNotifySystemStateChange) {
    auto status = mWatchdogBinderMediator->notifySystemStateChange(StateType::POWER_CYCLE, 0, 0);
    ASSERT_EQ(status.getExceptionCode(), EX_UNSUPPORTED_OPERATION);
}

}  // namespace watchdog
}  // namespace automotive
}  // namespace android
