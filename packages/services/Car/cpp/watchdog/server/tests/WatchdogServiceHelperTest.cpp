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

#include "MockAIBinderDeathRegistrationWrapper.h"
#include "MockCarWatchdogServiceForSystem.h"
#include "MockWatchdogProcessService.h"
#include "PackageInfoTestUtils.h"
#include "WatchdogServiceHelper.h"

#include <binder/IBinder.h>
#include <gmock/gmock.h>
#include <gtest/gtest.h>
#include <utils/RefBase.h>

namespace android {
namespace automotive {
namespace watchdog {

namespace {

using ::aidl::android::automotive::watchdog::TimeoutLength;
using ::aidl::android::automotive::watchdog::internal::ApplicationCategoryType;
using ::aidl::android::automotive::watchdog::internal::ComponentType;
using ::aidl::android::automotive::watchdog::internal::ICarWatchdogServiceForSystem;
using ::aidl::android::automotive::watchdog::internal::PackageInfo;
using ::aidl::android::automotive::watchdog::internal::PackageIoOveruseStats;
using ::aidl::android::automotive::watchdog::internal::ResourceOveruseStats;
using ::aidl::android::automotive::watchdog::internal::ResourceStats;
using ::aidl::android::automotive::watchdog::internal::ResourceUsageStats;
using ::aidl::android::automotive::watchdog::internal::UidType;
using ::aidl::android::automotive::watchdog::internal::UserPackageIoUsageStats;
using ::android::RefBase;
using ::android::sp;
using ::android::base::Error;
using ::android::base::Result;
using ::ndk::ScopedAStatus;
using ::ndk::SharedRefBase;
using ::testing::_;
using ::testing::ByMove;
using ::testing::DoAll;
using ::testing::Eq;
using ::testing::IsEmpty;
using ::testing::Return;
using ::testing::SetArgPointee;
using ::testing::UnorderedElementsAreArray;

using InternalTimeoutLength = ::aidl::android::automotive::watchdog::internal::TimeoutLength;

constexpr const char kFailOnNoCarWatchdogServiceMessage[] =
        "should fail when no car watchdog service registered with the helper";
constexpr const char kFailOnCarWatchdogServiceErrMessage[] =
        "should fail when car watchdog service API return error";

}  // namespace

namespace internal {

class WatchdogServiceHelperPeer : public RefBase {
public:
    explicit WatchdogServiceHelperPeer(const sp<WatchdogServiceHelper>& helper) : mHelper(helper) {}
    ~WatchdogServiceHelperPeer() { mHelper.clear(); }

    Result<void> init(
            const sp<WatchdogProcessServiceInterface>& watchdogProcessService,
            const sp<AIBinderDeathRegistrationWrapperInterface>& deathRegistrationWrapper) {
        mHelper->mDeathRegistrationWrapper = deathRegistrationWrapper;
        return mHelper->init(watchdogProcessService);
    }

    void terminate() { mHelper->terminate(); }

private:
    sp<WatchdogServiceHelper> mHelper;
};

}  // namespace internal

class WatchdogServiceHelperTest : public ::testing::Test {
protected:
    virtual void SetUp() {
        mMockWatchdogProcessService = sp<MockWatchdogProcessService>::make();
        mMockDeathRegistrationWrapper = sp<MockAIBinderDeathRegistrationWrapper>::make();
        mWatchdogServiceHelper = sp<WatchdogServiceHelper>::make();
        mWatchdogServiceHelperPeer =
                sp<internal::WatchdogServiceHelperPeer>::make(mWatchdogServiceHelper);
        mMockCarWatchdogServiceForSystem = SharedRefBase::make<MockCarWatchdogServiceForSystem>();

        auto result = mWatchdogServiceHelperPeer->init(mMockWatchdogProcessService,
                                                       mMockDeathRegistrationWrapper);
        ASSERT_RESULT_OK(result);
    }

    virtual void TearDown() {
        if (mWatchdogServiceHelper->isServiceConnected()) {
            expectUnlinkToDeath(mMockCarWatchdogServiceForSystem->asBinder().get(),
                                std::move(ScopedAStatus::ok()));
            EXPECT_CALL(*mMockWatchdogProcessService, unregisterCarWatchdogService(_)).Times(1);
        }
        mWatchdogServiceHelperPeer->terminate();
        mWatchdogServiceHelperPeer.clear();
        mWatchdogServiceHelper.clear();

        mMockWatchdogProcessService.clear();
        mMockDeathRegistrationWrapper.clear();
        mMockCarWatchdogServiceForSystem.reset();
        mWatchdogServiceHelperPeer.clear();
    }

    void registerCarWatchdogService() {
        expectLinkToDeath(mMockCarWatchdogServiceForSystem->asBinder().get(),
                          std::move(ScopedAStatus::ok()));
        EXPECT_CALL(*mMockWatchdogProcessService, registerCarWatchdogService(_, _))
                .WillOnce(Return(ByMove(ScopedAStatus::ok())));

        auto status = mWatchdogServiceHelper->registerService(mMockCarWatchdogServiceForSystem);

        ASSERT_TRUE(status.isOk()) << status.getMessage();
        ASSERT_TRUE(mWatchdogServiceHelper->isServiceConnected());
    }

    void* getCarWatchdogServiceForSystemCookie() {
        return static_cast<void*>(mMockCarWatchdogServiceForSystem->asBinder().get());
    }

    void expectLinkToDeath(AIBinder* aiBinder, ndk::ScopedAStatus expectedStatus) {
        EXPECT_CALL(*mMockDeathRegistrationWrapper,
                    linkToDeath(Eq(aiBinder), _, static_cast<void*>(aiBinder)))
                .WillOnce(Return(ByMove(std::move(expectedStatus))));
    }

    void expectUnlinkToDeath(AIBinder* aiBinder, ndk::ScopedAStatus expectedStatus) {
        EXPECT_CALL(*mMockDeathRegistrationWrapper,
                    unlinkToDeath(Eq(aiBinder), _, static_cast<void*>(aiBinder)))
                .WillOnce(Return(ByMove(std::move(expectedStatus))));
    }

    void expectNoLinkToDeath(AIBinder* aiBinder) {
        EXPECT_CALL(*mMockDeathRegistrationWrapper,
                    linkToDeath(Eq(aiBinder), _, static_cast<void*>(aiBinder)))
                .Times(0);
    }

    void expectNoUnlinkToDeath(AIBinder* aiBinder) {
        EXPECT_CALL(*mMockDeathRegistrationWrapper,
                    unlinkToDeath(Eq(aiBinder), _, static_cast<void*>(aiBinder)))
                .Times(0);
    }

    sp<WatchdogServiceHelper> mWatchdogServiceHelper;
    sp<MockWatchdogProcessService> mMockWatchdogProcessService;
    sp<MockAIBinderDeathRegistrationWrapper> mMockDeathRegistrationWrapper;
    std::shared_ptr<MockCarWatchdogServiceForSystem> mMockCarWatchdogServiceForSystem;
    sp<internal::WatchdogServiceHelperPeer> mWatchdogServiceHelperPeer;
};

TEST_F(WatchdogServiceHelperTest, TestInit) {
    sp<WatchdogServiceHelper> helper = sp<WatchdogServiceHelper>::make();
    sp<MockWatchdogProcessService> mockWatchdogProcessService =
            sp<MockWatchdogProcessService>::make();

    ASSERT_RESULT_OK(helper->init(mockWatchdogProcessService));
}

TEST_F(WatchdogServiceHelperTest, TestErrorOnInitWithNullWatchdogProcessServiceInstance) {
    sp<WatchdogServiceHelper> helper = sp<WatchdogServiceHelper>::make();

    auto result = helper->init(nullptr);

    ASSERT_FALSE(result.ok())
            << "Watchdog service helper init should fail on null watchdog process service instance";
}

TEST_F(WatchdogServiceHelperTest, TestTerminate) {
    ASSERT_NO_FATAL_FAILURE(registerCarWatchdogService());
    expectUnlinkToDeath(mMockCarWatchdogServiceForSystem->asBinder().get(),
                        std::move(ScopedAStatus::ok()));

    mWatchdogServiceHelper->terminate();

    ASSERT_EQ(mWatchdogServiceHelper->mService, nullptr);
}

TEST_F(WatchdogServiceHelperTest, TestRegisterService) {
    auto binder = mMockCarWatchdogServiceForSystem->asBinder();
    auto serviceHelperInterface = sp<WatchdogServiceHelperInterface>(mWatchdogServiceHelper);

    expectLinkToDeath(binder.get(), std::move(ScopedAStatus::ok()));
    EXPECT_CALL(*mMockWatchdogProcessService,
                registerCarWatchdogService(binder, serviceHelperInterface))
            .WillOnce(Return(ByMove(ScopedAStatus::ok())));

    auto status = mWatchdogServiceHelper->registerService(mMockCarWatchdogServiceForSystem);
    ASSERT_TRUE(status.isOk()) << status.getMessage();
    ASSERT_TRUE(mWatchdogServiceHelper->isServiceConnected());

    expectNoLinkToDeath(binder.get());
    EXPECT_CALL(*mMockWatchdogProcessService, registerCarWatchdogService(_, _)).Times(0);

    status = mWatchdogServiceHelper->registerService(mMockCarWatchdogServiceForSystem);
    ASSERT_TRUE(status.isOk()) << status.getMessage();
    ASSERT_TRUE(mWatchdogServiceHelper->isServiceConnected());
}

TEST_F(WatchdogServiceHelperTest, TestErrorOnRegisterServiceWithBinderDied) {
    auto binder = mMockCarWatchdogServiceForSystem->asBinder();
    auto serviceHelperInterface = sp<WatchdogServiceHelperInterface>(mWatchdogServiceHelper);
    expectLinkToDeath(binder.get(),
                      std::move(ScopedAStatus::fromExceptionCode(EX_TRANSACTION_FAILED)));
    EXPECT_CALL(*mMockWatchdogProcessService,
                registerCarWatchdogService(binder, serviceHelperInterface))
            .WillOnce(Return(ByMove(ScopedAStatus::ok())));
    EXPECT_CALL(*mMockWatchdogProcessService, unregisterCarWatchdogService(binder)).Times(1);

    ASSERT_FALSE(mWatchdogServiceHelper->registerService(mMockCarWatchdogServiceForSystem).isOk())
            << "Failed to return error on register service with dead binder";
    ASSERT_FALSE(mWatchdogServiceHelper->isServiceConnected());
}

TEST_F(WatchdogServiceHelperTest, TestErrorOnRegisterServiceWithWatchdogProcessServiceError) {
    auto binder = mMockCarWatchdogServiceForSystem->asBinder();
    auto serviceHelperInterface = sp<WatchdogServiceHelperInterface>(mWatchdogServiceHelper);
    expectNoLinkToDeath(binder.get());
    expectNoUnlinkToDeath(binder.get());
    EXPECT_CALL(*mMockWatchdogProcessService,
                registerCarWatchdogService(binder, serviceHelperInterface))
            .WillOnce(Return(ByMove(ScopedAStatus::fromExceptionCode(EX_ILLEGAL_STATE))));

    ASSERT_FALSE(mWatchdogServiceHelper->registerService(mMockCarWatchdogServiceForSystem).isOk())
            << "Failed to return error on error from watchdog process service";
    ASSERT_FALSE(mWatchdogServiceHelper->isServiceConnected());
}

TEST_F(WatchdogServiceHelperTest, TestErrorOnRegisterServiceWithDeadBinder) {
    auto binder = mMockCarWatchdogServiceForSystem->asBinder();
    auto serviceHelperInterface = sp<WatchdogServiceHelperInterface>(mWatchdogServiceHelper);
    expectLinkToDeath(binder.get(),
                      std::move(ScopedAStatus::fromExceptionCode(EX_TRANSACTION_FAILED)));
    EXPECT_CALL(*mMockWatchdogProcessService,
                registerCarWatchdogService(binder, serviceHelperInterface))
            .WillOnce(Return(ByMove(ScopedAStatus::ok())));
    EXPECT_CALL(*mMockWatchdogProcessService, unregisterCarWatchdogService(binder)).Times(1);

    ASSERT_FALSE(mWatchdogServiceHelper->registerService(mMockCarWatchdogServiceForSystem).isOk())
            << "Failed to return error on register service with dead binder";
    ASSERT_FALSE(mWatchdogServiceHelper->isServiceConnected());
}

TEST_F(WatchdogServiceHelperTest, TestUnregisterService) {
    ASSERT_NO_FATAL_FAILURE(registerCarWatchdogService());

    auto binder = mMockCarWatchdogServiceForSystem->asBinder();
    expectUnlinkToDeath(binder.get(), std::move(ScopedAStatus::ok()));
    EXPECT_CALL(*mMockWatchdogProcessService, unregisterCarWatchdogService(binder)).Times(1);

    auto status = mWatchdogServiceHelper->unregisterService(mMockCarWatchdogServiceForSystem);

    ASSERT_TRUE(status.isOk()) << status.getMessage();
    ASSERT_FALSE(mWatchdogServiceHelper->isServiceConnected());

    expectNoUnlinkToDeath(binder.get());
    EXPECT_CALL(*mMockWatchdogProcessService, unregisterCarWatchdogService(_)).Times(0);

    ASSERT_FALSE(mWatchdogServiceHelper->unregisterService(mMockCarWatchdogServiceForSystem).isOk())
            << "Unregistering an unregistered service should return an error";
}

TEST_F(WatchdogServiceHelperTest, TestHandleBinderDeath) {
    ASSERT_NO_FATAL_FAILURE(registerCarWatchdogService());

    auto binder = mMockCarWatchdogServiceForSystem->asBinder();
    EXPECT_CALL(*mMockWatchdogProcessService, unregisterCarWatchdogService(binder)).Times(1);

    mWatchdogServiceHelper->handleBinderDeath(static_cast<void*>(binder.get()));

    ASSERT_FALSE(mWatchdogServiceHelper->isServiceConnected());

    EXPECT_CALL(*mMockWatchdogProcessService, unregisterCarWatchdogService(_)).Times(0);

    ASSERT_FALSE(mWatchdogServiceHelper->unregisterService(mMockCarWatchdogServiceForSystem).isOk())
            << "Unregistering a dead service should return an error";
}

TEST_F(WatchdogServiceHelperTest, TestCheckIfAlive) {
    ASSERT_NO_FATAL_FAILURE(registerCarWatchdogService());

    EXPECT_CALL(*mMockCarWatchdogServiceForSystem,
                checkIfAlive(0, InternalTimeoutLength::TIMEOUT_CRITICAL))
            .WillOnce(Return(ByMove(ScopedAStatus::ok())));

    auto status = mWatchdogServiceHelper->checkIfAlive(mMockCarWatchdogServiceForSystem->asBinder(),
                                                       0, TimeoutLength::TIMEOUT_CRITICAL);

    ASSERT_TRUE(status.isOk()) << status.getMessage();
}

TEST_F(WatchdogServiceHelperTest,
       TestErrorOnCheckIfAliveWithNotRegisteredCarWatchdogServiceBinder) {
    ASSERT_NO_FATAL_FAILURE(registerCarWatchdogService());

    EXPECT_CALL(*mMockCarWatchdogServiceForSystem, checkIfAlive(_, _)).Times(0);

    auto notRegisteredService = SharedRefBase::make<MockCarWatchdogServiceForSystem>();
    auto status = mWatchdogServiceHelper->checkIfAlive(notRegisteredService->asBinder(), 0,
                                                       TimeoutLength::TIMEOUT_CRITICAL);

    ASSERT_FALSE(status.isOk()) << "checkIfAlive should fail when the given car watchdog service"
                                   "binder is not registered with the helper";
}

TEST_F(WatchdogServiceHelperTest, TestErrorOnCheckIfAliveWithNoCarWatchdogServiceRegistered) {
    EXPECT_CALL(*mMockCarWatchdogServiceForSystem, checkIfAlive(_, _)).Times(0);

    auto status = mWatchdogServiceHelper->checkIfAlive(mMockCarWatchdogServiceForSystem->asBinder(),
                                                       0, TimeoutLength::TIMEOUT_CRITICAL);

    ASSERT_FALSE(status.isOk()) << "checkIfAlive " << kFailOnNoCarWatchdogServiceMessage;
}

TEST_F(WatchdogServiceHelperTest, TestErrorOnCheckIfAliveWithErrorStatusFromCarWatchdogService) {
    ASSERT_NO_FATAL_FAILURE(registerCarWatchdogService());

    EXPECT_CALL(*mMockCarWatchdogServiceForSystem,
                checkIfAlive(0, InternalTimeoutLength::TIMEOUT_CRITICAL))
            .WillOnce(Return(ByMove(ScopedAStatus::fromExceptionCodeWithMessage(EX_ILLEGAL_STATE,
                                                                                "Illegal state"))));

    auto status = mWatchdogServiceHelper->checkIfAlive(mMockCarWatchdogServiceForSystem->asBinder(),
                                                       0, TimeoutLength::TIMEOUT_CRITICAL);
    ASSERT_FALSE(status.isOk()) << "checkIfAlive " << kFailOnCarWatchdogServiceErrMessage;
}

TEST_F(WatchdogServiceHelperTest, TestPrepareProcessTermination) {
    ASSERT_NO_FATAL_FAILURE(registerCarWatchdogService());

    EXPECT_CALL(*mMockCarWatchdogServiceForSystem, prepareProcessTermination())
            .WillOnce(Return(ByMove(ScopedAStatus::ok())));

    EXPECT_CALL(*mMockWatchdogProcessService, unregisterCarWatchdogService(_)).Times(0);

    auto status = mWatchdogServiceHelper->prepareProcessTermination(
            mMockCarWatchdogServiceForSystem->asBinder());

    ASSERT_TRUE(status.isOk()) << status.getMessage();

    ASSERT_FALSE(mWatchdogServiceHelper->isServiceConnected());
}

TEST_F(WatchdogServiceHelperTest,
       TestErrorOnPrepareProcessTerminationWithNotRegisteredCarWatchdogServiceBinder) {
    ASSERT_NO_FATAL_FAILURE(registerCarWatchdogService());

    EXPECT_CALL(*mMockCarWatchdogServiceForSystem, prepareProcessTermination()).Times(0);

    EXPECT_CALL(*mMockWatchdogProcessService, unregisterCarWatchdogService(_)).Times(0);

    auto notRegisteredService = SharedRefBase::make<MockCarWatchdogServiceForSystem>();
    auto status =
            mWatchdogServiceHelper->prepareProcessTermination(notRegisteredService->asBinder());

    ASSERT_FALSE(status.isOk()) << "prepareProcessTermination should fail when the given car "
                                   "watchdog service binder is not registered with the helper";
}

TEST_F(WatchdogServiceHelperTest,
       TestErrorOnPrepareProcessTerminationWithNoCarWatchdogServiceRegistered) {
    EXPECT_CALL(*mMockCarWatchdogServiceForSystem, prepareProcessTermination()).Times(0);

    EXPECT_CALL(*mMockWatchdogProcessService, unregisterCarWatchdogService(_)).Times(0);

    ASSERT_FALSE(mWatchdogServiceHelper
                         ->prepareProcessTermination(mMockCarWatchdogServiceForSystem->asBinder())
                         .isOk())
            << "prepareProcessTermination " << kFailOnNoCarWatchdogServiceMessage;
}

TEST_F(WatchdogServiceHelperTest,
       TestErrorOnPrepareProcessTerminationWithErrorStatusFromCarWatchdogService) {
    ASSERT_NO_FATAL_FAILURE(registerCarWatchdogService());

    EXPECT_CALL(*mMockCarWatchdogServiceForSystem, prepareProcessTermination())
            .WillOnce(Return(ByMove(ScopedAStatus::fromExceptionCodeWithMessage(EX_ILLEGAL_STATE,
                                                                                "Illegal state"))));

    EXPECT_CALL(*mMockWatchdogProcessService, unregisterCarWatchdogService(_)).Times(0);

    ASSERT_FALSE(mWatchdogServiceHelper
                         ->prepareProcessTermination(mMockCarWatchdogServiceForSystem->asBinder())
                         .isOk())
            << "prepareProcessTermination " << kFailOnCarWatchdogServiceErrMessage;
}

TEST_F(WatchdogServiceHelperTest, TestGetPackageInfosForUids) {
    ASSERT_NO_FATAL_FAILURE(registerCarWatchdogService());

    std::vector<int32_t> uids = {1000};
    std::vector<std::string> prefixesStr = {"vendor.package"};
    std::vector<PackageInfo> expectedPackageInfo{
            constructPackageInfo("vendor.package.A", 120000, UidType::NATIVE, ComponentType::VENDOR,
                                 ApplicationCategoryType::OTHERS),
            constructPackageInfo("third_party.package.B", 130000, UidType::APPLICATION,
                                 ComponentType::THIRD_PARTY, ApplicationCategoryType::OTHERS),
    };

    EXPECT_CALL(*mMockCarWatchdogServiceForSystem, getPackageInfosForUids(uids, prefixesStr, _))
            .WillOnce(DoAll(SetArgPointee<2>(expectedPackageInfo),
                            Return(ByMove(ScopedAStatus::ok()))));

    std::vector<PackageInfo> actualPackageInfo;
    auto status =
            mWatchdogServiceHelper->getPackageInfosForUids(uids, prefixesStr, &actualPackageInfo);

    ASSERT_TRUE(status.isOk()) << status.getMessage();
    EXPECT_THAT(actualPackageInfo, UnorderedElementsAreArray(expectedPackageInfo));
}

TEST_F(WatchdogServiceHelperTest,
       TestErrorOnGetPackageInfosForUidsWithNoCarWatchdogServiceRegistered) {
    EXPECT_CALL(*mMockCarWatchdogServiceForSystem, getPackageInfosForUids(_, _, _)).Times(0);

    std::vector<int32_t> uids;
    std::vector<std::string> prefixes;
    std::vector<PackageInfo> actualPackageInfo;
    auto status =
            mWatchdogServiceHelper->getPackageInfosForUids(uids, prefixes, &actualPackageInfo);

    ASSERT_FALSE(status.isOk()) << "getPackageInfosForUids " << kFailOnNoCarWatchdogServiceMessage;
    EXPECT_THAT(actualPackageInfo, IsEmpty());
}

TEST_F(WatchdogServiceHelperTest,
       TestErrorOnGetPackageInfosForUidsWithErrorStatusFromCarWatchdogService) {
    ASSERT_NO_FATAL_FAILURE(registerCarWatchdogService());

    EXPECT_CALL(*mMockCarWatchdogServiceForSystem, getPackageInfosForUids(_, _, _))
            .WillOnce(Return(ByMove(ScopedAStatus::fromExceptionCodeWithMessage(EX_ILLEGAL_STATE,
                                                                                "Illegal state"))));

    std::vector<int32_t> uids;
    std::vector<std::string> prefixes;
    std::vector<PackageInfo> actualPackageInfo;
    auto status =
            mWatchdogServiceHelper->getPackageInfosForUids(uids, prefixes, &actualPackageInfo);

    ASSERT_FALSE(status.isOk()) << "getPackageInfosForUids " << kFailOnCarWatchdogServiceErrMessage;
    ASSERT_TRUE(actualPackageInfo.empty());
}

TEST_F(WatchdogServiceHelperTest, TestResetResourceOveruseStats) {
    ASSERT_NO_FATAL_FAILURE(registerCarWatchdogService());

    std::vector<std::string> packageNames = {"system.daemon"};
    EXPECT_CALL(*mMockCarWatchdogServiceForSystem, resetResourceOveruseStats(packageNames))
            .WillOnce(Return(ByMove(ScopedAStatus::ok())));

    auto status = mWatchdogServiceHelper->resetResourceOveruseStats(packageNames);

    ASSERT_TRUE(status.isOk()) << status.getMessage();
}

TEST_F(WatchdogServiceHelperTest,
       TestErrorsOnResetResourceOveruseStatsWithNoCarWatchdogServiceRegistered) {
    EXPECT_CALL(*mMockCarWatchdogServiceForSystem, resetResourceOveruseStats(_)).Times(0);

    ASSERT_FALSE(mWatchdogServiceHelper->resetResourceOveruseStats({}).isOk())
            << "resetResourceOveruseStats " << kFailOnNoCarWatchdogServiceMessage;
}

TEST_F(WatchdogServiceHelperTest,
       TestErrorsOnResetResourceOveruseStatsWithErrorStatusFromCarWatchdogService) {
    ASSERT_NO_FATAL_FAILURE(registerCarWatchdogService());

    EXPECT_CALL(*mMockCarWatchdogServiceForSystem, resetResourceOveruseStats(_))
            .WillOnce(Return(ByMove(ScopedAStatus::fromExceptionCodeWithMessage(EX_ILLEGAL_STATE,
                                                                                "Illegal state"))));

    ASSERT_FALSE(mWatchdogServiceHelper->resetResourceOveruseStats({}).isOk())
            << "resetResourceOveruseStats " << kFailOnCarWatchdogServiceErrMessage;
}

TEST_F(WatchdogServiceHelperTest, TestRequestTodayIoUsageStats) {
    ASSERT_NO_FATAL_FAILURE(registerCarWatchdogService());

    EXPECT_CALL(*mMockCarWatchdogServiceForSystem, requestTodayIoUsageStats())
            .WillOnce(Return(ByMove(ScopedAStatus::ok())));

    auto status = mWatchdogServiceHelper->requestTodayIoUsageStats();

    ASSERT_TRUE(status.isOk()) << status.getMessage();
}

TEST_F(WatchdogServiceHelperTest,
       TestErrorOnRequestTodayIoUsageStatsWithNoCarWatchdogServiceRegistered) {
    EXPECT_CALL(*mMockCarWatchdogServiceForSystem, requestTodayIoUsageStats()).Times(0);

    ASSERT_FALSE(mWatchdogServiceHelper->requestTodayIoUsageStats().isOk())
            << "requestTodayIoUsageStats " << kFailOnNoCarWatchdogServiceMessage;
}

TEST_F(WatchdogServiceHelperTest,
       TestErrorOnRequestTodayIoUsageStatsWithErrorStatusFromCarWatchdogService) {
    ASSERT_NO_FATAL_FAILURE(registerCarWatchdogService());

    EXPECT_CALL(*mMockCarWatchdogServiceForSystem, requestTodayIoUsageStats())
            .WillOnce(Return(ByMove(ScopedAStatus::fromExceptionCodeWithMessage(EX_ILLEGAL_STATE,
                                                                                "Illegal state"))));

    ASSERT_FALSE(mWatchdogServiceHelper->requestTodayIoUsageStats().isOk())
            << "requestTodayIoUsageStats " << kFailOnCarWatchdogServiceErrMessage;
}

TEST_F(WatchdogServiceHelperTest, TestOnLatestResourceStats) {
    ASSERT_NO_FATAL_FAILURE(registerCarWatchdogService());

    PackageIoOveruseStats stats;
    stats.uid = 101000;
    stats.ioOveruseStats.killableOnOveruse = true;
    stats.ioOveruseStats.startTime = 99898;
    stats.ioOveruseStats.durationInSeconds = 12345;
    stats.ioOveruseStats.totalOveruses = 10;
    stats.shouldNotify = true;
    std::vector<PackageIoOveruseStats> expectedIoOveruseStats = {stats};

    std::vector<ResourceStats> expectedResourceStats;
    expectedResourceStats.push_back({
            .resourceOveruseStats = std::make_optional<ResourceOveruseStats>({
                    .packageIoOveruseStats = expectedIoOveruseStats,
            }),
    });

    EXPECT_CALL(*mMockCarWatchdogServiceForSystem, onLatestResourceStats(expectedResourceStats))
            .WillOnce(Return(ByMove(ScopedAStatus::ok())));

    auto status = mWatchdogServiceHelper->onLatestResourceStats(expectedResourceStats);

    ASSERT_TRUE(status.isOk()) << status.getMessage();
}

TEST_F(WatchdogServiceHelperTest,
       TestErrorsOnLatestResourceStatsWithNoCarWatchdogServiceRegistered) {
    EXPECT_CALL(*mMockCarWatchdogServiceForSystem, onLatestResourceStats(_)).Times(0);

    ASSERT_FALSE(mWatchdogServiceHelper->onLatestResourceStats({}).isOk())
            << "onLatestResourceStats " << kFailOnNoCarWatchdogServiceMessage;
}

TEST_F(WatchdogServiceHelperTest,
       TestErrorsOnLatestResourceStatsWithErrorStatusFromCarWatchdogService) {
    ASSERT_NO_FATAL_FAILURE(registerCarWatchdogService());

    EXPECT_CALL(*mMockCarWatchdogServiceForSystem, onLatestResourceStats(_))
            .WillOnce(Return(ByMove(ScopedAStatus::fromExceptionCodeWithMessage(EX_ILLEGAL_STATE,
                                                                                "Illegal state"))));

    ASSERT_FALSE(mWatchdogServiceHelper->onLatestResourceStats({}).isOk())
            << "onLatestResourceStats " << kFailOnCarWatchdogServiceErrMessage;
}

TEST_F(WatchdogServiceHelperTest, TestRequestAidlVhalPid) {
    ASSERT_NO_FATAL_FAILURE(registerCarWatchdogService());

    EXPECT_CALL(*mMockCarWatchdogServiceForSystem, requestAidlVhalPid())
            .WillOnce(Return(ByMove(ScopedAStatus::ok())));

    auto status = mWatchdogServiceHelper->requestAidlVhalPid();

    ASSERT_TRUE(status.isOk()) << status.getMessage();
}

TEST_F(WatchdogServiceHelperTest, TestRequestAidlVhalPidWithNoCarWatchdogServiceRegistered) {
    EXPECT_CALL(*mMockCarWatchdogServiceForSystem, requestAidlVhalPid()).Times(0);

    ASSERT_FALSE(mWatchdogServiceHelper->requestAidlVhalPid().isOk())
            << "requestAidlVhalPid " << kFailOnNoCarWatchdogServiceMessage;
}

TEST_F(WatchdogServiceHelperTest, TestRequestAidlVhalPidWithErrorStatusFromCarWatchdogService) {
    ASSERT_NO_FATAL_FAILURE(registerCarWatchdogService());

    EXPECT_CALL(*mMockCarWatchdogServiceForSystem, requestAidlVhalPid())
            .WillOnce(Return(ByMove(ScopedAStatus::fromExceptionCodeWithMessage(EX_ILLEGAL_STATE,
                                                                                "Illegal state"))));

    ASSERT_FALSE(mWatchdogServiceHelper->requestAidlVhalPid().isOk())
            << "requestAidlVhalPid " << kFailOnCarWatchdogServiceErrMessage;
}

}  // namespace watchdog
}  // namespace automotive
}  // namespace android
