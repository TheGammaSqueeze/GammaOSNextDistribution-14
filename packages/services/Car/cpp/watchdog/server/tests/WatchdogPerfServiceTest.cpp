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

#include "LooperStub.h"
#include "MockDataProcessor.h"
#include "MockProcDiskStatsCollector.h"
#include "MockProcStatCollector.h"
#include "MockUidStatsCollector.h"
#include "MockWatchdogServiceHelper.h"
#include "ProcStatCollector.h"
#include "UidStatsCollector.h"
#include "WatchdogPerfService.h"

#include <WatchdogProperties.sysprop.h>
#include <aidl/android/automotive/watchdog/internal/ResourceOveruseStats.h>
#include <aidl/android/automotive/watchdog/internal/ResourceStats.h>
#include <aidl/android/automotive/watchdog/internal/ResourceUsageStats.h>
#include <aidl/android/automotive/watchdog/internal/SystemSummaryUsageStats.h>
#include <aidl/android/automotive/watchdog/internal/UidResourceUsageStats.h>
#include <aidl/android/automotive/watchdog/internal/UserState.h>
#include <android-base/file.h>
#include <android-base/stringprintf.h>
#include <android/binder_auto_utils.h>
#include <android/binder_interface_utils.h>
#include <gmock/gmock.h>
#include <utils/RefBase.h>

#include <future>  // NOLINT(build/c++11)
#include <queue>
#include <string>
#include <vector>

namespace android {
namespace automotive {
namespace watchdog {

namespace {

using ::aidl::android::automotive::watchdog::internal::ResourceOveruseStats;
using ::aidl::android::automotive::watchdog::internal::ResourceStats;
using ::aidl::android::automotive::watchdog::internal::ResourceUsageStats;
using ::aidl::android::automotive::watchdog::internal::SystemSummaryUsageStats;
using ::aidl::android::automotive::watchdog::internal::UidResourceUsageStats;
using ::aidl::android::automotive::watchdog::internal::UserState;
using ::android::RefBase;
using ::android::sp;
using ::android::String16;
using ::android::wp;
using ::android::automotive::watchdog::testing::LooperStub;
using ::android::base::Error;
using ::android::base::Result;
using ::android::base::StringAppendF;
using ::testing::_;
using ::testing::ByMove;
using ::testing::Eq;
using ::testing::InSequence;
using ::testing::Mock;
using ::testing::NiceMock;
using ::testing::Return;
using ::testing::StrictMock;
using ::testing::UnorderedElementsAreArray;

constexpr std::chrono::seconds kTestPostSystemEventDurationSecs = 10s;
constexpr std::chrono::seconds kTestSystemEventCollectionIntervalSecs = 1s;
constexpr std::chrono::seconds kTestPeriodicCollectionIntervalSecs = 5s;
constexpr std::chrono::seconds kTestCustomCollectionIntervalSecs = 3s;
constexpr std::chrono::seconds kTestCustomCollectionDurationSecs = 11s;
constexpr std::chrono::seconds kTestPeriodicMonitorIntervalSecs = 2s;
constexpr std::chrono::seconds kTestUserSwitchTimeoutSecs = 15s;
constexpr std::chrono::seconds kTestWakeUpDurationSecs = 20s;

std::string toString(const std::vector<ResourceStats>& resourceStats) {
    std::string buffer;
    StringAppendF(&buffer, "{");
    for (const auto& stats : resourceStats) {
        StringAppendF(&buffer, "%s,\n", stats.toString().c_str());
    }
    if (buffer.size() > 2) {
        buffer.resize(buffer.size() - 2);  // Remove ",\n" from last element
    }
    StringAppendF(&buffer, "}");
    return buffer;
}

ResourceUsageStats constructResourceUsageStats(
        int64_t startTimeEpochMillis, std::chrono::seconds durationInSecs,
        const SystemSummaryUsageStats& systemSummaryUsageStats,
        std::vector<UidResourceUsageStats> uidResourceUsageStats) {
    ResourceUsageStats resourceUsageStats;
    resourceUsageStats.startTimeEpochMillis = startTimeEpochMillis;
    resourceUsageStats.durationInMillis =
            std::chrono::duration_cast<std::chrono::milliseconds>(durationInSecs).count();
    resourceUsageStats.systemSummaryUsageStats = systemSummaryUsageStats;
    resourceUsageStats.uidResourceUsageStats = uidResourceUsageStats;

    return resourceUsageStats;
}

ResourceStats constructResourceStats(
        const std::optional<ResourceUsageStats>& resourceUsageStats,
        const std::optional<ResourceOveruseStats>& resourceOveruseStats) {
    ResourceStats resourceStats = {};
    resourceStats.resourceUsageStats = resourceUsageStats;
    resourceStats.resourceOveruseStats = resourceOveruseStats;

    return resourceStats;
}

}  // namespace

namespace internal {

class WatchdogPerfServicePeer final : public RefBase {
public:
    explicit WatchdogPerfServicePeer(const sp<WatchdogPerfService>& service) : mService(service) {}
    WatchdogPerfServicePeer() = delete;

    void init(const sp<LooperWrapper>& looper,
              const sp<UidStatsCollectorInterface>& uidStatsCollector,
              const sp<ProcStatCollectorInterface>& procStatCollector,
              const sp<ProcDiskStatsCollectorInterface>& procDiskStatsCollector) {
        Mutex::Autolock lock(mService->mMutex);
        mService->mHandlerLooper = looper;
        mService->mUidStatsCollector = uidStatsCollector;
        mService->mProcStatCollector = procStatCollector;
        mService->mProcDiskStatsCollector = procDiskStatsCollector;
    }

    void updateIntervals() {
        Mutex::Autolock lock(mService->mMutex);
        mService->mPostSystemEventDurationNs = kTestPostSystemEventDurationSecs;
        mService->mBoottimeCollection.pollingIntervalNs = kTestSystemEventCollectionIntervalSecs;
        mService->mPeriodicCollection.pollingIntervalNs = kTestPeriodicCollectionIntervalSecs;
        mService->mUserSwitchCollection.pollingIntervalNs = kTestSystemEventCollectionIntervalSecs;
        mService->mPeriodicMonitor.pollingIntervalNs = kTestPeriodicMonitorIntervalSecs;
        mService->mUserSwitchTimeoutNs = kTestUserSwitchTimeoutSecs;
        mService->mWakeUpDurationNs = kTestWakeUpDurationSecs;
    }

    void clearPostSystemEventDuration() {
        Mutex::Autolock lock(mService->mMutex);
        mService->mPostSystemEventDurationNs = 0ns;
    }

    EventType getCurrCollectionEvent() {
        Mutex::Autolock lock(mService->mMutex);
        return mService->mCurrCollectionEvent;
    }

    void setCurrCollectionEvent(EventType eventType) {
        Mutex::Autolock lock(mService->mMutex);
        mService->mCurrCollectionEvent = eventType;
    }

    int64_t getCurrentCollectionIntervalMillis() {
        // This method is always called while WatchdogPerfService is already
        // holding the lock.
        auto metadata = mService->getCurrentCollectionMetadataLocked();
        if (metadata == nullptr) {
            return std::chrono::duration_cast<std::chrono::milliseconds>(
                           kTestSystemEventCollectionIntervalSecs)
                    .count();
        }
        return std::chrono::duration_cast<std::chrono::milliseconds>(metadata->pollingIntervalNs)
                .count();
    }

    std::future<void> joinCollectionThread() {
        return std::async([&]() {
            if (mService->mCollectionThread.joinable()) {
                mService->mCollectionThread.join();
            }
        });
    }

protected:
    sp<WatchdogPerfService> mService;
};

}  // namespace internal

namespace {

class WatchdogPerfServiceTest : public ::testing::Test {
protected:
    virtual void SetUp() {
        mElapsedTimeSinceBootMs = 0;
        mMockUidStatsCollector = sp<MockUidStatsCollector>::make();
        mMockWatchdogServiceHelper = sp<MockWatchdogServiceHelper>::make();
        mMockDataProcessor = sp<StrictMock<MockDataProcessor>>::make();
        mMockProcDiskStatsCollector = sp<NiceMock<MockProcDiskStatsCollector>>::make();
        mMockProcStatCollector = sp<NiceMock<MockProcStatCollector>>::make();
        mService = sp<WatchdogPerfService>::
                make(mMockWatchdogServiceHelper,
                     std::bind(&WatchdogPerfServiceTest::incrementAndGetElapsedRealtimeSinceBootMs,
                               this));
        mServicePeer = sp<internal::WatchdogPerfServicePeer>::make(mService);
        mLooperStub = sp<LooperStub>::make();
    }

    virtual void TearDown() {
        if (auto event = mServicePeer->getCurrCollectionEvent();
            event != EventType::INIT && event != EventType::TERMINATED) {
            EXPECT_CALL(*mMockDataProcessor, terminate()).Times(1);
            mService->terminate();
        }
        mService.clear();
        mServicePeer.clear();
        mLooperStub.clear();
        mMockUidStatsCollector.clear();
        mMockWatchdogServiceHelper.clear();
        mMockDataProcessor.clear();
        mMockProcDiskStatsCollector.clear();
        mMockProcStatCollector.clear();
    }

    void startService() {
        mServicePeer->init(mLooperStub, mMockUidStatsCollector, mMockProcStatCollector,
                           mMockProcDiskStatsCollector);

        EXPECT_CALL(*mMockDataProcessor, init()).Times(1);
        EXPECT_CALL(*mMockDataProcessor, onSystemStartup()).Times(1);

        ASSERT_RESULT_OK(mService->registerDataProcessor(mMockDataProcessor));

        EXPECT_CALL(*mMockUidStatsCollector, init()).Times(1);
        EXPECT_CALL(*mMockProcStatCollector, init()).Times(1);
        EXPECT_CALL(*mMockProcDiskStatsCollector, init()).Times(1);

        ASSERT_RESULT_OK(mService->start());

        mServicePeer->updateIntervals();
    }

    void startPeriodicCollection() {
        int bootIterations = static_cast<int>(kTestPostSystemEventDurationSecs.count() /
                                              kTestSystemEventCollectionIntervalSecs.count());

        // Add the boot collection event done during startService()
        bootIterations += 1;

        EXPECT_CALL(*mMockUidStatsCollector, collect()).Times(bootIterations);
        EXPECT_CALL(*mMockProcStatCollector, collect()).Times(bootIterations);
        EXPECT_CALL(*mMockDataProcessor,
                    onBoottimeCollection(_, Eq(mMockUidStatsCollector), Eq(mMockProcStatCollector),
                                         _))
                .Times(bootIterations);

        // Make sure the collection event changes from EventType::INIT to
        // EventType::BOOT_TIME_COLLECTION.
        ASSERT_RESULT_OK(mLooperStub->pollCache());

        // Mark boot complete.
        ASSERT_RESULT_OK(mService->onBootFinished());

        // Poll all post boot-time collections
        for (int i = 1; i < bootIterations; i++) {
            ASSERT_RESULT_OK(mLooperStub->pollCache());
        }

        // Process |SwitchMessage::END_BOOTTIME_COLLECTION| and switch to periodic collection.
        ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::PERIODIC_COLLECTION)
                << "Invalid collection event";

        ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());
    }

    void skipPeriodicMonitorEvents() {
        EXPECT_CALL(*mMockDataProcessor, onPeriodicMonitor(_, _, _)).Times(2);
        ASSERT_RESULT_OK(mLooperStub->pollCache());
        ASSERT_RESULT_OK(mLooperStub->pollCache());
    }

    void removePeriodicMonitorEvents() {
        mLooperStub->removeMessages(mService, EventType::PERIODIC_MONITOR);
    }

    void skipPeriodicCollection() {
        EXPECT_CALL(*mMockDataProcessor, onPeriodicCollection(_, SystemState::NORMAL_MODE, _, _, _))
                .Times(1);
        ASSERT_RESULT_OK(mLooperStub->pollCache());
    }

    void verifyAndClearExpectations() {
        Mock::VerifyAndClearExpectations(mMockUidStatsCollector.get());
        Mock::VerifyAndClearExpectations(mMockProcStatCollector.get());
        Mock::VerifyAndClearExpectations(mMockProcDiskStatsCollector.get());
        Mock::VerifyAndClearExpectations(mMockDataProcessor.get());
        Mock::VerifyAndClearExpectations(mMockWatchdogServiceHelper.get());
    }

    int64_t incrementAndGetElapsedRealtimeSinceBootMs() {
        int64_t timeSinceBootMs = mElapsedTimeSinceBootMs;
        mElapsedTimeSinceBootMs += mServicePeer->getCurrentCollectionIntervalMillis();
        return timeSinceBootMs;
    }

    sp<WatchdogPerfService> mService;
    sp<internal::WatchdogPerfServicePeer> mServicePeer;
    sp<LooperStub> mLooperStub;
    sp<MockUidStatsCollector> mMockUidStatsCollector;
    sp<MockProcStatCollector> mMockProcStatCollector;
    sp<MockProcDiskStatsCollector> mMockProcDiskStatsCollector;
    sp<MockWatchdogServiceHelper> mMockWatchdogServiceHelper;
    sp<MockDataProcessor> mMockDataProcessor;
    int64_t mElapsedTimeSinceBootMs;
};

}  // namespace

TEST_F(WatchdogPerfServiceTest, TestServiceStartAndTerminate) {
    mServicePeer->init(mLooperStub, mMockUidStatsCollector, mMockProcStatCollector,
                       mMockProcDiskStatsCollector);

    EXPECT_CALL(*mMockDataProcessor, init()).Times(1);
    EXPECT_CALL(*mMockDataProcessor, onSystemStartup()).Times(1);

    ASSERT_RESULT_OK(mService->registerDataProcessor(mMockDataProcessor));

    EXPECT_CALL(*mMockUidStatsCollector, init()).Times(1);
    EXPECT_CALL(*mMockProcStatCollector, init()).Times(1);
    EXPECT_CALL(*mMockProcDiskStatsCollector, init()).Times(1);

    ASSERT_RESULT_OK(mService->start());

    ASSERT_TRUE(mService->mCollectionThread.joinable()) << "Collection thread not created";

    EXPECT_CALL(*mMockUidStatsCollector, collect()).Times(1);
    EXPECT_CALL(*mMockProcStatCollector, collect()).Times(1);
    EXPECT_CALL(*mMockDataProcessor,
                onBoottimeCollection(_, Eq(mMockUidStatsCollector), Eq(mMockProcStatCollector), _))
            .Times(1);

    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mLooperStub->numSecondsElapsed(), 0)
            << "Boot-time collection didn't start immediately";
    ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::BOOT_TIME_COLLECTION)
            << "Invalid collection event";

    ASSERT_FALSE(mService->start().ok())
            << "No error returned when WatchdogPerfService was started more than once";

    ASSERT_TRUE(sysprop::systemEventCollectionInterval().has_value());
    ASSERT_EQ(std::chrono::duration_cast<std::chrono::seconds>(
                      mService->mBoottimeCollection.pollingIntervalNs)
                      .count(),
              sysprop::systemEventCollectionInterval().value());
    ASSERT_TRUE(sysprop::periodicCollectionInterval().has_value());
    ASSERT_EQ(std::chrono::duration_cast<std::chrono::seconds>(
                      mService->mPeriodicCollection.pollingIntervalNs)
                      .count(),
              sysprop::periodicCollectionInterval().value());

    EXPECT_CALL(*mMockDataProcessor, terminate()).Times(1);

    mService->terminate();

    ASSERT_FALSE(mService->mCollectionThread.joinable()) << "Collection thread did not terminate";
}

TEST_F(WatchdogPerfServiceTest, TestValidCollectionSequence) {
    ASSERT_NO_FATAL_FAILURE(startService());

    // #1 Boot-time collection
    // TODO(b/266008677): Add more data to the ResourceStats.
    std::optional<ResourceUsageStats> boottimeResourceUsageStats =
            std::make_optional<ResourceUsageStats>({});

    EXPECT_CALL(*mMockUidStatsCollector, collect()).Times(1);
    EXPECT_CALL(*mMockProcStatCollector, collect()).Times(1);
    EXPECT_CALL(*mMockDataProcessor,
                onBoottimeCollection(_, Eq(mMockUidStatsCollector), Eq(mMockProcStatCollector), _))
            .Times(1)
            .WillOnce([&](auto, auto, auto, auto* resourceStats) -> Result<void> {
                resourceStats->resourceUsageStats = boottimeResourceUsageStats;
                return {};
            });
    EXPECT_CALL(*mMockWatchdogServiceHelper, isServiceConnected()).Times(1);
    // Even though the resource stats are not empty the service is not
    // connected, therefore stats are not sent to CarWatchdogService.
    EXPECT_CALL(*mMockWatchdogServiceHelper, onLatestResourceStats(_)).Times(0);

    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mLooperStub->numSecondsElapsed(), 0)
            << "Boot-time collection didn't start immediately";
    ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::BOOT_TIME_COLLECTION)
            << "Invalid collection event";
    ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());

    // #2 Boot-time collection
    EXPECT_CALL(*mMockUidStatsCollector, collect()).Times(1);
    EXPECT_CALL(*mMockProcStatCollector, collect()).Times(1);
    EXPECT_CALL(*mMockDataProcessor,
                onBoottimeCollection(_, Eq(mMockUidStatsCollector), Eq(mMockProcStatCollector), _))
            .Times(1);
    EXPECT_CALL(*mMockWatchdogServiceHelper, isServiceConnected()).Times(1);
    EXPECT_CALL(*mMockWatchdogServiceHelper, onLatestResourceStats(_)).Times(0);

    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mLooperStub->numSecondsElapsed(), kTestSystemEventCollectionIntervalSecs.count())
            << "Subsequent boot-time collection didn't happen at "
            << kTestSystemEventCollectionIntervalSecs.count() << " seconds interval";
    ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::BOOT_TIME_COLLECTION)
            << "Invalid collection event";
    ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());

    // #3 Post system event collection - boot-time
    int maxIterations = static_cast<int>(kTestPostSystemEventDurationSecs.count() /
                                         kTestSystemEventCollectionIntervalSecs.count());

    EXPECT_CALL(*mMockUidStatsCollector, collect()).Times(maxIterations);
    EXPECT_CALL(*mMockProcStatCollector, collect()).Times(maxIterations);
    EXPECT_CALL(*mMockDataProcessor,
                onBoottimeCollection(_, Eq(mMockUidStatsCollector), Eq(mMockProcStatCollector), _))
            .Times(maxIterations);
    EXPECT_CALL(*mMockWatchdogServiceHelper, isServiceConnected()).Times(maxIterations);
    EXPECT_CALL(*mMockWatchdogServiceHelper, onLatestResourceStats(_)).Times(0);

    ASSERT_RESULT_OK(mService->onBootFinished());

    // Poll all post system event collections - boot-time except last
    for (int i = 0; i < maxIterations - 1; i++) {
        ASSERT_RESULT_OK(mLooperStub->pollCache());

        ASSERT_EQ(mLooperStub->numSecondsElapsed(), kTestSystemEventCollectionIntervalSecs.count())
                << "Subsequent post boot-time collection didn't happen at "
                << kTestSystemEventCollectionIntervalSecs.count() << " seconds interval";
        ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::BOOT_TIME_COLLECTION)
                << "Invalid collection event";
    }

    // Poll the last post system event collection - boot-time. The last boot-time collection should
    // switch to periodic collection.
    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mLooperStub->numSecondsElapsed(), kTestSystemEventCollectionIntervalSecs.count())
            << "Last boot-time collection didn't happen immediately after sending "
            << "END_BOOTTIME_COLLECTION message";
    ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::PERIODIC_COLLECTION)
            << "Invalid collection event";
    ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());

    // #4 Periodic monitor
    EXPECT_CALL(*mMockProcDiskStatsCollector, collect()).Times(1);
    EXPECT_CALL(*mMockDataProcessor, onPeriodicMonitor(_, Eq(mMockProcDiskStatsCollector), _))
            .Times(1);

    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mLooperStub->numSecondsElapsed(), kTestPeriodicMonitorIntervalSecs.count())
            << "First periodic monitor didn't happen at "
            << kTestPeriodicMonitorIntervalSecs.count() << " seconds interval";
    ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());

    // #5 Periodic monitor
    EXPECT_CALL(*mMockProcDiskStatsCollector, collect()).Times(1);
    EXPECT_CALL(*mMockDataProcessor, onPeriodicMonitor(_, Eq(mMockProcDiskStatsCollector), _))
            .Times(1);

    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mLooperStub->numSecondsElapsed(), kTestPeriodicMonitorIntervalSecs.count())
            << "Second periodic monitor didn't happen at "
            << kTestPeriodicMonitorIntervalSecs.count() << " seconds interval";
    ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());

    // #6 Periodic collection
    std::vector<ResourceStats> actualResourceStats = {};
    ResourceOveruseStats expectedResourceOveruseStats = {};
    std::vector<ResourceStats> expectedResourceStats = {
            // Handle the resource stats send during boottime.
            constructResourceStats(boottimeResourceUsageStats,
                                   /*resourceOveruseStats=*/std::nullopt),
            constructResourceStats(/*resourceUsageStats=*/std::nullopt,
                                   expectedResourceOveruseStats),
    };
    EXPECT_CALL(*mMockUidStatsCollector, collect()).Times(1);
    EXPECT_CALL(*mMockProcStatCollector, collect()).Times(1);
    EXPECT_CALL(*mMockDataProcessor,
                onPeriodicCollection(_, SystemState::NORMAL_MODE, Eq(mMockUidStatsCollector),
                                     Eq(mMockProcStatCollector), _))
            .Times(1)
            .WillOnce([&](auto, auto, auto, auto, auto* resourceStats) -> Result<void> {
                resourceStats->resourceOveruseStats =
                        std::make_optional<ResourceOveruseStats>(expectedResourceOveruseStats);
                return {};
            });
    EXPECT_CALL(*mMockWatchdogServiceHelper, isServiceConnected()).Times(1).WillOnce(Return(true));
    EXPECT_CALL(*mMockWatchdogServiceHelper, onLatestResourceStats(_))
            .Times(1)
            .WillOnce([&](auto& resourceStats) -> ndk::ScopedAStatus {
                actualResourceStats = resourceStats;
                return ndk::ScopedAStatus::ok();
            });

    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mLooperStub->numSecondsElapsed(), 1)
            << "First periodic collection didn't happen at 1 second interval";
    ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::PERIODIC_COLLECTION)
            << "Invalid collection event";

    // Handle the SEND_RESOURCE_STATS message
    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(actualResourceStats, expectedResourceStats)
            << "Expected: " << toString(expectedResourceStats)
            << "\nActual: " << toString(actualResourceStats);

    ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());

    std::string customCollectionIntervalStr =
            std::to_string(kTestCustomCollectionIntervalSecs.count());
    std::string customCollectionDurationStr =
            std::to_string(kTestCustomCollectionDurationSecs.count());
    // #7 Custom collection
    actualResourceStats = {};
    const char* firstArgs[] = {kStartCustomCollectionFlag, kIntervalFlag,
                               customCollectionIntervalStr.c_str(), kMaxDurationFlag,
                               customCollectionDurationStr.c_str()};

    ASSERT_RESULT_OK(mService->onCustomCollection(-1, firstArgs, /*numArgs=*/5));

    ResourceUsageStats expectedResourceUsageStats =
            constructResourceUsageStats(/*startTimeEpochMillis=*/0,
                                        /*durationInSecs=*/kTestPeriodicCollectionIntervalSecs,
                                        /*systemSummaryUsageStats=*/{},
                                        /*uidResourceUsageStats=*/{});
    expectedResourceStats = {
            constructResourceStats(expectedResourceUsageStats,
                                   /*resourceOveruseStats=*/std::nullopt),
    };

    EXPECT_CALL(*mMockUidStatsCollector, collect()).Times(1);
    EXPECT_CALL(*mMockProcStatCollector, collect()).Times(1);
    EXPECT_CALL(*mMockDataProcessor,
                onCustomCollection(_, SystemState::NORMAL_MODE, _, Eq(mMockUidStatsCollector),
                                   Eq(mMockProcStatCollector), _))
            .Times(1)
            .WillOnce([&](auto, auto, auto, auto, auto, auto* resourceStats) -> Result<void> {
                resourceStats->resourceUsageStats =
                        expectedResourceStats.front().resourceUsageStats;
                return {};
            });
    EXPECT_CALL(*mMockWatchdogServiceHelper, isServiceConnected()).Times(1).WillOnce(Return(true));
    EXPECT_CALL(*mMockWatchdogServiceHelper, onLatestResourceStats(_))
            .Times(1)
            .WillOnce([&](auto& resourceStats) -> ndk::ScopedAStatus {
                actualResourceStats = resourceStats;
                return ndk::ScopedAStatus::ok();
            });

    ASSERT_RESULT_OK(mLooperStub->pollCache());

    // Handle the SEND_RESOURCE_STATS message
    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mLooperStub->numSecondsElapsed(), 0) << "Custom collection didn't start immediately";
    ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::CUSTOM_COLLECTION)
            << "Invalid collection event";
    ASSERT_EQ(actualResourceStats, expectedResourceStats)
            << "Expected: " << toString(expectedResourceStats)
            << "\nActual: " << toString(actualResourceStats);

    ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());

    // #8 Custom collection
    EXPECT_CALL(*mMockUidStatsCollector, collect()).Times(1);
    EXPECT_CALL(*mMockProcStatCollector, collect()).Times(1);
    EXPECT_CALL(*mMockDataProcessor,
                onCustomCollection(_, SystemState::NORMAL_MODE, _, Eq(mMockUidStatsCollector),
                                   Eq(mMockProcStatCollector), _))
            .Times(1);
    EXPECT_CALL(*mMockWatchdogServiceHelper, isServiceConnected()).Times(0);
    EXPECT_CALL(*mMockWatchdogServiceHelper, onLatestResourceStats(_)).Times(0);

    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mLooperStub->numSecondsElapsed(), kTestCustomCollectionIntervalSecs.count())
            << "Subsequent custom collection didn't happen at "
            << kTestCustomCollectionIntervalSecs.count() << " seconds interval";
    ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::CUSTOM_COLLECTION)
            << "Invalid collection event";
    ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());

    // #9 End custom collection
    TemporaryFile customDump;
    {
        InSequence s;
        EXPECT_CALL(*mMockDataProcessor, onCustomCollectionDump(customDump.fd)).Times(1);
        EXPECT_CALL(*mMockDataProcessor, onCustomCollectionDump(-1)).Times(1);
    }

    const char* secondArgs[] = {kEndCustomCollectionFlag};
    ASSERT_RESULT_OK(mService->onCustomCollection(customDump.fd, secondArgs, /*numArgs=*/1));
    ASSERT_RESULT_OK(mLooperStub->pollCache());
    ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::PERIODIC_COLLECTION)
            << "Invalid collection event";

    // #10 Switch to periodic collection
    EXPECT_CALL(*mMockUidStatsCollector, collect()).Times(1);
    EXPECT_CALL(*mMockProcStatCollector, collect()).Times(1);
    EXPECT_CALL(*mMockDataProcessor,
                onPeriodicCollection(_, SystemState::NORMAL_MODE, Eq(mMockUidStatsCollector),
                                     Eq(mMockProcStatCollector), _))
            .Times(1);
    EXPECT_CALL(*mMockWatchdogServiceHelper, isServiceConnected()).Times(0);
    EXPECT_CALL(*mMockWatchdogServiceHelper, onLatestResourceStats(_)).Times(0);

    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mLooperStub->numSecondsElapsed(), 0)
            << "Periodic collection didn't start immediately after ending custom collection";
    ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::PERIODIC_COLLECTION)
            << "Invalid collection event";
    ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());

    // #11 Periodic monitor.
    EXPECT_CALL(*mMockProcDiskStatsCollector, collect()).Times(1);
    EXPECT_CALL(*mMockDataProcessor, onPeriodicMonitor(_, Eq(mMockProcDiskStatsCollector), _))
            .Times(1);

    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mLooperStub->numSecondsElapsed(), kTestPeriodicMonitorIntervalSecs.count());
    ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());

    EXPECT_CALL(*mMockDataProcessor, terminate()).Times(1);
}

TEST_F(WatchdogPerfServiceTest, TestCollectionTerminatesOnZeroEnabledCollectors) {
    ASSERT_NO_FATAL_FAILURE(startService());

    ON_CALL(*mMockUidStatsCollector, enabled()).WillByDefault(Return(false));
    ON_CALL(*mMockProcStatCollector, enabled()).WillByDefault(Return(false));

    // Collection should terminate and call data processor's terminate method on error.
    EXPECT_CALL(*mMockDataProcessor, terminate()).Times(1);

    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mServicePeer->joinCollectionThread().wait_for(1s), std::future_status::ready)
            << "Collection thread didn't terminate within 1 second.";
    ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::TERMINATED);
}

TEST_F(WatchdogPerfServiceTest, TestCollectionTerminatesOnDataCollectorError) {
    ASSERT_NO_FATAL_FAILURE(startService());

    // Inject data collector error.
    Result<void> errorRes = Error() << "Failed to collect data";
    EXPECT_CALL(*mMockUidStatsCollector, collect()).WillOnce(Return(errorRes));

    // Collection should terminate and call data processor's terminate method on error.
    EXPECT_CALL(*mMockDataProcessor, terminate()).Times(1);

    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mServicePeer->joinCollectionThread().wait_for(1s), std::future_status::ready)
            << "Collection thread didn't terminate within 1 second.";
    ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::TERMINATED);
}

TEST_F(WatchdogPerfServiceTest, TestCollectionTerminatesOnDataProcessorError) {
    ASSERT_NO_FATAL_FAILURE(startService());

    // Inject data processor error.
    Result<void> errorRes = Error() << "Failed to process data";
    EXPECT_CALL(*mMockDataProcessor,
                onBoottimeCollection(_, Eq(mMockUidStatsCollector), Eq(mMockProcStatCollector), _))
            .WillOnce(Return(errorRes));

    // Collection should terminate and call data processor's terminate method on error.
    EXPECT_CALL(*mMockDataProcessor, terminate()).Times(1);

    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mServicePeer->joinCollectionThread().wait_for(1s), std::future_status::ready)
            << "Collection thread didn't terminate within 1 second.";
    ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::TERMINATED);
}

TEST_F(WatchdogPerfServiceTest, TestBoottimeCollectionWithNoPostSystemEventDuration) {
    ASSERT_NO_FATAL_FAILURE(startService());

    mServicePeer->clearPostSystemEventDuration();

    // #1 Boot-time collection
    EXPECT_CALL(*mMockUidStatsCollector, collect()).Times(1);
    EXPECT_CALL(*mMockProcStatCollector, collect()).Times(1);
    EXPECT_CALL(*mMockDataProcessor,
                onBoottimeCollection(_, Eq(mMockUidStatsCollector), Eq(mMockProcStatCollector), _))
            .Times(1);

    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mLooperStub->numSecondsElapsed(), 0)
            << "Boot-time collection didn't start immediately";
    ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::BOOT_TIME_COLLECTION)
            << "Invalid collection event";
    ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());

    // #2 Boot-time collection
    EXPECT_CALL(*mMockUidStatsCollector, collect()).Times(1);
    EXPECT_CALL(*mMockProcStatCollector, collect()).Times(1);
    EXPECT_CALL(*mMockDataProcessor,
                onBoottimeCollection(_, Eq(mMockUidStatsCollector), Eq(mMockProcStatCollector), _))
            .Times(1);

    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mLooperStub->numSecondsElapsed(), kTestSystemEventCollectionIntervalSecs.count())
            << "Subsequent boot-time collection didn't happen at "
            << kTestSystemEventCollectionIntervalSecs.count() << " seconds interval";
    ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::BOOT_TIME_COLLECTION)
            << "Invalid collection event";
    ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());

    // #3 Last boot-time collection
    EXPECT_CALL(*mMockUidStatsCollector, collect()).Times(1);
    EXPECT_CALL(*mMockProcStatCollector, collect()).Times(1);
    EXPECT_CALL(*mMockDataProcessor,
                onBoottimeCollection(_, Eq(mMockUidStatsCollector), Eq(mMockProcStatCollector), _))
            .Times(1);

    ASSERT_RESULT_OK(mService->onBootFinished());

    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mLooperStub->numSecondsElapsed(), 0)
            << "Last boot-time collection didn't happen immediately after receiving boot complete "
            << "notification";
    ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::PERIODIC_COLLECTION)
            << "Invalid collection event";
    ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());
}

TEST_F(WatchdogPerfServiceTest, TestCustomCollection) {
    ASSERT_NO_FATAL_FAILURE(startService());

    ASSERT_NO_FATAL_FAILURE(startPeriodicCollection());

    std::string customCollectionIntervalStr =
            std::to_string(kTestCustomCollectionIntervalSecs.count());
    std::string customCollectionDurationStr =
            std::to_string(kTestCustomCollectionDurationSecs.count());
    // Start custom collection with filter packages option.
    const char* args[] = {kStartCustomCollectionFlag,          kIntervalFlag,
                          customCollectionIntervalStr.c_str(), kMaxDurationFlag,
                          customCollectionDurationStr.c_str(), kFilterPackagesFlag,
                          "android.car.cts,system_server"};

    ASSERT_RESULT_OK(mService->onCustomCollection(-1, args, /*numArgs=*/7));

    // Poll until custom collection auto terminates.
    int maxIterations = static_cast<int>(kTestCustomCollectionDurationSecs.count() /
                                         kTestCustomCollectionIntervalSecs.count());
    for (int i = 0; i <= maxIterations; ++i) {
        EXPECT_CALL(*mMockUidStatsCollector, collect()).Times(1);
        EXPECT_CALL(*mMockProcStatCollector, collect()).Times(1);
        EXPECT_CALL(*mMockDataProcessor,
                    onCustomCollection(_, SystemState::NORMAL_MODE,
                                       UnorderedElementsAreArray(
                                               {"android.car.cts", "system_server"}),
                                       Eq(mMockUidStatsCollector), Eq(mMockProcStatCollector), _))
                .Times(1);

        ASSERT_RESULT_OK(mLooperStub->pollCache());

        int secondsElapsed = (i == 0 ? 0 : kTestCustomCollectionIntervalSecs.count());
        ASSERT_EQ(mLooperStub->numSecondsElapsed(), secondsElapsed)
                << "Custom collection didn't happen at " << secondsElapsed
                << " seconds interval in iteration " << i;
        ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::CUSTOM_COLLECTION)
                << "Invalid collection event";
        ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());
    }

    EXPECT_CALL(*mMockDataProcessor, onCustomCollectionDump(-1)).Times(1);

    // Next looper message was injected during startCustomCollection to end the custom collection
    // after |kTestCustomCollectionDurationSecs|. On processing this message, the custom collection
    // should auto terminate.
    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mLooperStub->numSecondsElapsed(),
              kTestCustomCollectionDurationSecs.count() % kTestCustomCollectionIntervalSecs.count())
            << "Custom collection did't end after " << kTestCustomCollectionDurationSecs.count()
            << " seconds";
    ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::PERIODIC_COLLECTION)
            << "Invalid collection event";
    EXPECT_CALL(*mMockDataProcessor, terminate()).Times(1);
}

TEST_F(WatchdogPerfServiceTest, TestCustomCollectionAlwaysStarts) {
    ASSERT_NO_FATAL_FAILURE(startService());

    ASSERT_NO_FATAL_FAILURE(startPeriodicCollection());

    for (int eventInt = EventType::BOOT_TIME_COLLECTION; eventInt < EventType::PERIODIC_MONITOR;
         ++eventInt) {
        EventType eventType = static_cast<EventType>(eventInt);
        if (eventType == EventType::CUSTOM_COLLECTION) {
            continue;
        }
        mServicePeer->setCurrCollectionEvent(static_cast<EventType>(eventInt));

        EXPECT_CALL(*mMockUidStatsCollector, collect()).Times(1);
        EXPECT_CALL(*mMockProcStatCollector, collect()).Times(1);
        EXPECT_CALL(*mMockDataProcessor,
                    onCustomCollection(_, SystemState::NORMAL_MODE,
                                       UnorderedElementsAreArray(
                                               {"android.car.cts", "system_server"}),
                                       Eq(mMockUidStatsCollector), Eq(mMockProcStatCollector), _))
                .Times(1);

        std::string customCollectionIntervalStr =
                std::to_string(kTestCustomCollectionIntervalSecs.count());
        std::string customCollectionDurationStr =
                std::to_string(kTestCustomCollectionDurationSecs.count());
        // Start custom collection with filter packages option.
        const char* args[] = {kStartCustomCollectionFlag,          kIntervalFlag,
                              customCollectionIntervalStr.c_str(), kMaxDurationFlag,
                              customCollectionDurationStr.c_str(), kFilterPackagesFlag,
                              "android.car.cts,system_server"};

        ASSERT_RESULT_OK(mService->onCustomCollection(-1, args, /*numArgs=*/7));

        ASSERT_RESULT_OK(mLooperStub->pollCache());

        ASSERT_EQ(mLooperStub->numSecondsElapsed(), 0)
                << "Custom collection didn't happen immediately";
        ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::CUSTOM_COLLECTION)
                << "Invalid collection event";
        ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());
    }
}

TEST_F(WatchdogPerfServiceTest, TestUserSwitchCollection) {
    ASSERT_NO_FATAL_FAILURE(startService());

    ASSERT_NO_FATAL_FAILURE(startPeriodicCollection());

    userid_t fromUserId = 0;
    userid_t toUserId = 100;

    // #1 Start user switch collection
    EXPECT_CALL(*mMockUidStatsCollector, collect()).Times(1);
    EXPECT_CALL(*mMockProcStatCollector, collect()).Times(1);
    EXPECT_CALL(*mMockDataProcessor,
                onUserSwitchCollection(_, Eq(fromUserId), Eq(toUserId), Eq(mMockUidStatsCollector),
                                       Eq(mMockProcStatCollector)))
            .Times(1);

    ASSERT_RESULT_OK(mService->onUserStateChange(100, UserState::USER_STATE_SWITCHING));

    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mLooperStub->numSecondsElapsed(), 0)
            << "User switch collection didn't start immediately";
    ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::USER_SWITCH_COLLECTION)
            << "Invalid collection event";
    ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());

    // #2 User switch collection
    EXPECT_CALL(*mMockUidStatsCollector, collect()).Times(1);
    EXPECT_CALL(*mMockProcStatCollector, collect()).Times(1);
    EXPECT_CALL(*mMockDataProcessor,
                onUserSwitchCollection(_, Eq(fromUserId), Eq(toUserId), Eq(mMockUidStatsCollector),
                                       Eq(mMockProcStatCollector)))
            .Times(1);

    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mLooperStub->numSecondsElapsed(), kTestSystemEventCollectionIntervalSecs.count())
            << "Subsequent user switch collection didn't happen at "
            << kTestSystemEventCollectionIntervalSecs.count() << " seconds interval";
    ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::USER_SWITCH_COLLECTION)
            << "Invalid collection event";
    ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());

    // #3 Post system event collection - user switch
    int maxIterations = static_cast<int>(kTestPostSystemEventDurationSecs.count() /
                                         kTestSystemEventCollectionIntervalSecs.count());

    EXPECT_CALL(*mMockUidStatsCollector, collect()).Times(maxIterations);
    EXPECT_CALL(*mMockProcStatCollector, collect()).Times(maxIterations);
    EXPECT_CALL(*mMockDataProcessor,
                onUserSwitchCollection(_, Eq(fromUserId), Eq(toUserId), Eq(mMockUidStatsCollector),
                                       Eq(mMockProcStatCollector)))
            .Times(maxIterations);

    ASSERT_RESULT_OK(mService->onUserStateChange(100, UserState::USER_STATE_POST_UNLOCKED));

    // Poll all post user switch collections except last
    for (int i = 0; i < maxIterations - 1; ++i) {
        ASSERT_RESULT_OK(mLooperStub->pollCache());

        ASSERT_EQ(mLooperStub->numSecondsElapsed(), kTestSystemEventCollectionIntervalSecs.count())
                << "Subsequent post system event collection - user switch didn't happen at "
                << kTestSystemEventCollectionIntervalSecs.count() << " seconds interval";
        ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::USER_SWITCH_COLLECTION)
                << "Invalid collection event";
    }

    // Poll the last post system event collection - user switch. The last user switch collection
    // event should switch to periodic collection.
    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mLooperStub->numSecondsElapsed(), kTestSystemEventCollectionIntervalSecs.count())
            << "Last user switch collection didn't happen immediately after sending "
            << "END_USER_SWITCH_COLLECTION message";
    ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::PERIODIC_COLLECTION)
            << "Invalid collection event";
    ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());
}

TEST_F(WatchdogPerfServiceTest, TestUserSwitchCollectionWithDelayedUnlocking) {
    ASSERT_NO_FATAL_FAILURE(startService());

    ASSERT_NO_FATAL_FAILURE(startPeriodicCollection());

    userid_t fromUserId = 0;
    userid_t toUserId = 100;

    // #1 Start user switch collection
    EXPECT_CALL(*mMockUidStatsCollector, collect()).Times(1);
    EXPECT_CALL(*mMockProcStatCollector, collect()).Times(1);
    EXPECT_CALL(*mMockDataProcessor,
                onUserSwitchCollection(_, Eq(fromUserId), Eq(toUserId), Eq(mMockUidStatsCollector),
                                       Eq(mMockProcStatCollector)))
            .Times(1);

    ASSERT_RESULT_OK(mService->onUserStateChange(100, UserState::USER_STATE_SWITCHING));

    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mLooperStub->numSecondsElapsed(), 0)
            << "User switch collection didn't start immediately";
    ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::USER_SWITCH_COLLECTION)
            << "Invalid collection event";
    ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());

    // #2 User switch collections before timeout
    int maxIterations = static_cast<int>(kTestUserSwitchTimeoutSecs.count() /
                                         kTestSystemEventCollectionIntervalSecs.count());

    EXPECT_CALL(*mMockUidStatsCollector, collect()).Times(maxIterations);
    EXPECT_CALL(*mMockProcStatCollector, collect()).Times(maxIterations);
    EXPECT_CALL(*mMockDataProcessor,
                onUserSwitchCollection(_, Eq(fromUserId), Eq(toUserId), Eq(mMockUidStatsCollector),
                                       Eq(mMockProcStatCollector)))
            .Times(maxIterations);

    // Poll all user switch collections except last
    for (int i = 0; i < maxIterations - 1; i++) {
        ASSERT_RESULT_OK(mLooperStub->pollCache());

        ASSERT_EQ(mLooperStub->numSecondsElapsed(), kTestSystemEventCollectionIntervalSecs.count())
                << "Subsequent user switch collection didn't happen at "
                << kTestSystemEventCollectionIntervalSecs.count() << " seconds interval";
        ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::USER_SWITCH_COLLECTION)
                << "Invalid collection event";
    }

    // Poll the last user switch collection. The last user switch collection event should start
    // periodic collection.
    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mLooperStub->numSecondsElapsed(), kTestSystemEventCollectionIntervalSecs.count())
            << "Last user switch collection didn't happen immediately after sending "
            << "END_USER_SWITCH_COLLECTION message";
    ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::PERIODIC_COLLECTION)
            << "Invalid collection event";
    ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());

    // #3 Start user switch collection with unlocking signal
    EXPECT_CALL(*mMockUidStatsCollector, collect()).Times(1);
    EXPECT_CALL(*mMockProcStatCollector, collect()).Times(1);
    EXPECT_CALL(*mMockDataProcessor,
                onUserSwitchCollection(_, Eq(fromUserId), Eq(toUserId), Eq(mMockUidStatsCollector),
                                       Eq(mMockProcStatCollector)))
            .Times(1);

    ASSERT_RESULT_OK(mService->onUserStateChange(100, UserState::USER_STATE_UNLOCKING));

    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mLooperStub->numSecondsElapsed(), 0)
            << "User switch collection didn't start immediately";
    ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::USER_SWITCH_COLLECTION)
            << "Invalid collection event";
    ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());

    // #4 User switch collections after unlocking
    EXPECT_CALL(*mMockUidStatsCollector, collect()).Times(1);
    EXPECT_CALL(*mMockProcStatCollector, collect()).Times(1);
    EXPECT_CALL(*mMockDataProcessor,
                onUserSwitchCollection(_, Eq(fromUserId), Eq(toUserId), Eq(mMockUidStatsCollector),
                                       Eq(mMockProcStatCollector)))
            .Times(1);

    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mLooperStub->numSecondsElapsed(), kTestSystemEventCollectionIntervalSecs.count())
            << "Subsequent user switch collection didn't happen at "
            << kTestSystemEventCollectionIntervalSecs.count() << " seconds interval";
    ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::USER_SWITCH_COLLECTION)
            << "Invalid collection event";
    ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());

    // #5 Post system event collection - user switch
    maxIterations = static_cast<int>(kTestPostSystemEventDurationSecs.count() /
                                     kTestSystemEventCollectionIntervalSecs.count());

    EXPECT_CALL(*mMockUidStatsCollector, collect()).Times(maxIterations);
    EXPECT_CALL(*mMockProcStatCollector, collect()).Times(maxIterations);
    EXPECT_CALL(*mMockDataProcessor,
                onUserSwitchCollection(_, Eq(fromUserId), Eq(toUserId), Eq(mMockUidStatsCollector),
                                       Eq(mMockProcStatCollector)))
            .Times(maxIterations);

    ASSERT_RESULT_OK(mService->onUserStateChange(100, UserState::USER_STATE_POST_UNLOCKED));

    // Poll all post user switch collections except last
    for (int i = 0; i < maxIterations - 1; ++i) {
        ASSERT_RESULT_OK(mLooperStub->pollCache());

        ASSERT_EQ(mLooperStub->numSecondsElapsed(), kTestSystemEventCollectionIntervalSecs.count())
                << "Subsequent post user switch collection didn't happen at "
                << kTestSystemEventCollectionIntervalSecs.count() << " seconds interval";
        ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::USER_SWITCH_COLLECTION)
                << "Invalid collection event";
    }

    // Poll the last post user switch collection
    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mLooperStub->numSecondsElapsed(), kTestSystemEventCollectionIntervalSecs.count())
            << "Last user switch collection didn't happen immediately after sending "
            << "END_USER_SWITCH_COLLECTION message";
    ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::PERIODIC_COLLECTION)
            << "Invalid collection event";
    ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());
}

TEST_F(WatchdogPerfServiceTest, TestUserSwitchEventDuringUserSwitchCollection) {
    ASSERT_NO_FATAL_FAILURE(startService());

    ASSERT_NO_FATAL_FAILURE(startPeriodicCollection());

    userid_t fromUserId = 0;
    userid_t toUserId = 100;

    // #1 Start user switch collection
    EXPECT_CALL(*mMockUidStatsCollector, collect()).Times(2);
    EXPECT_CALL(*mMockProcStatCollector, collect()).Times(2);
    EXPECT_CALL(*mMockDataProcessor,
                onUserSwitchCollection(_, Eq(fromUserId), Eq(toUserId), Eq(mMockUidStatsCollector),
                                       Eq(mMockProcStatCollector)))
            .Times(2);

    ASSERT_RESULT_OK(mService->onUserStateChange(toUserId, UserState::USER_STATE_SWITCHING));

    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mLooperStub->numSecondsElapsed(), 0)
            << "User switch collection didn't start immediately";
    ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::USER_SWITCH_COLLECTION)
            << "Invalid collection event";

    // #2 User switch collection
    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mLooperStub->numSecondsElapsed(), kTestSystemEventCollectionIntervalSecs.count())
            << "Subsequent user switch collection didn't happen at "
            << kTestSystemEventCollectionIntervalSecs.count() << " seconds interval";
    ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::USER_SWITCH_COLLECTION)
            << "Invalid collection event";
    ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());

    // #3 Start new user switch collection during prev user switch event
    userid_t newFromUserId = 100;
    userid_t newToUserId = 101;

    EXPECT_CALL(*mMockUidStatsCollector, collect()).Times(1);
    EXPECT_CALL(*mMockProcStatCollector, collect()).Times(1);
    EXPECT_CALL(*mMockDataProcessor,
                onUserSwitchCollection(_, Eq(newFromUserId), Eq(newToUserId),
                                       Eq(mMockUidStatsCollector), Eq(mMockProcStatCollector)))
            .Times(1);

    ASSERT_RESULT_OK(mService->onUserStateChange(newToUserId, UserState::USER_STATE_SWITCHING));

    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mLooperStub->numSecondsElapsed(), 0)
            << "New user switch collection didn't start immediately";
    ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::USER_SWITCH_COLLECTION)
            << "Invalid collection event";
    ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());

    // #4 New user switch collection
    EXPECT_CALL(*mMockUidStatsCollector, collect()).Times(1);
    EXPECT_CALL(*mMockProcStatCollector, collect()).Times(1);
    EXPECT_CALL(*mMockDataProcessor,
                onUserSwitchCollection(_, Eq(newFromUserId), Eq(newToUserId),
                                       Eq(mMockUidStatsCollector), Eq(mMockProcStatCollector)))
            .Times(1);

    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mLooperStub->numSecondsElapsed(), kTestSystemEventCollectionIntervalSecs.count())
            << "Subsequent new user switch collection didn't happen at "
            << kTestSystemEventCollectionIntervalSecs.count() << " seconds interval";
    ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::USER_SWITCH_COLLECTION)
            << "Invalid collection event";
    ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());

    // #5 Post system event collection - new user switch
    int maxIterations = static_cast<int>(kTestPostSystemEventDurationSecs.count() /
                                         kTestSystemEventCollectionIntervalSecs.count());

    EXPECT_CALL(*mMockUidStatsCollector, collect()).Times(maxIterations);
    EXPECT_CALL(*mMockProcStatCollector, collect()).Times(maxIterations);
    EXPECT_CALL(*mMockDataProcessor,
                onUserSwitchCollection(_, Eq(newFromUserId), Eq(newToUserId),
                                       Eq(mMockUidStatsCollector), Eq(mMockProcStatCollector)))
            .Times(maxIterations);

    ASSERT_RESULT_OK(mService->onUserStateChange(newToUserId, UserState::USER_STATE_POST_UNLOCKED));

    // Poll all post user switch collections except last
    for (int i = 0; i < maxIterations - 1; ++i) {
        ASSERT_RESULT_OK(mLooperStub->pollCache());

        ASSERT_EQ(mLooperStub->numSecondsElapsed(), kTestSystemEventCollectionIntervalSecs.count())
                << "Subsequent post system event collection -  new user switch didn't happen at "
                << kTestSystemEventCollectionIntervalSecs.count() << " seconds interval";
        ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::USER_SWITCH_COLLECTION)
                << "Invalid collection event";
    }

    // Poll the last post system event collection - user switch. The last user switch collection
    // event should switch to periodic collection.
    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mLooperStub->numSecondsElapsed(), kTestSystemEventCollectionIntervalSecs.count())
            << "Last new user switch collection didn't happen immediately after sending "
            << "END_USER_SWITCH_COLLECTION message";
    ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::PERIODIC_COLLECTION)
            << "Invalid collection event";
    ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());
}

TEST_F(WatchdogPerfServiceTest, TestUserSwitchCollectionWithTwoTimeouts) {
    ASSERT_NO_FATAL_FAILURE(startService());

    ASSERT_NO_FATAL_FAILURE(startPeriodicCollection());

    userid_t fromUserId = 0;
    userid_t toUserId = 100;

    // #1 Start user switch collection
    EXPECT_CALL(*mMockUidStatsCollector, collect()).Times(1);
    EXPECT_CALL(*mMockProcStatCollector, collect()).Times(1);
    EXPECT_CALL(*mMockDataProcessor,
                onUserSwitchCollection(_, Eq(fromUserId), Eq(toUserId), Eq(mMockUidStatsCollector),
                                       Eq(mMockProcStatCollector)))
            .Times(1);

    ASSERT_RESULT_OK(mService->onUserStateChange(100, UserState::USER_STATE_SWITCHING));

    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mLooperStub->numSecondsElapsed(), 0)
            << "User switch collection didn't start immediately";
    ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::USER_SWITCH_COLLECTION)
            << "Invalid collection event";
    ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());

    // #2 User switch collections before timeout
    int maxIterations = static_cast<int>(kTestUserSwitchTimeoutSecs.count() /
                                         kTestSystemEventCollectionIntervalSecs.count());

    EXPECT_CALL(*mMockUidStatsCollector, collect()).Times(maxIterations);
    EXPECT_CALL(*mMockProcStatCollector, collect()).Times(maxIterations);
    EXPECT_CALL(*mMockDataProcessor,
                onUserSwitchCollection(_, Eq(fromUserId), Eq(toUserId), Eq(mMockUidStatsCollector),
                                       Eq(mMockProcStatCollector)))
            .Times(maxIterations);

    // Poll all user switch collections except last
    for (int i = 0; i < maxIterations - 1; ++i) {
        ASSERT_RESULT_OK(mLooperStub->pollCache());

        ASSERT_EQ(mLooperStub->numSecondsElapsed(), kTestSystemEventCollectionIntervalSecs.count())
                << "Subsequent post user switch collection didn't happen at "
                << kTestSystemEventCollectionIntervalSecs.count() << " seconds interval";
        ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::USER_SWITCH_COLLECTION)
                << "Invalid collection event";
    }

    // Poll the last user switch collection
    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mLooperStub->numSecondsElapsed(), kTestSystemEventCollectionIntervalSecs.count())
            << "Last user switch collection didn't happen immediately after sending "
            << "END_USER_SWITCH_COLLECTION message";
    ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::PERIODIC_COLLECTION)
            << "Invalid collection event";
    ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());

    // #3 Start user switch collection with unlocking signal
    EXPECT_CALL(*mMockUidStatsCollector, collect()).Times(1);
    EXPECT_CALL(*mMockProcStatCollector, collect()).Times(1);
    EXPECT_CALL(*mMockDataProcessor,
                onUserSwitchCollection(_, Eq(fromUserId), Eq(toUserId), Eq(mMockUidStatsCollector),
                                       Eq(mMockProcStatCollector)))
            .Times(1);

    ASSERT_RESULT_OK(mService->onUserStateChange(100, UserState::USER_STATE_UNLOCKING));

    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mLooperStub->numSecondsElapsed(), 0)
            << "User switch collection didn't start immediately";
    ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::USER_SWITCH_COLLECTION)
            << "Invalid collection event";
    ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());

    // #4 User switch collections after unlocking
    maxIterations = static_cast<int>(kTestUserSwitchTimeoutSecs.count() /
                                     kTestSystemEventCollectionIntervalSecs.count());

    EXPECT_CALL(*mMockUidStatsCollector, collect()).Times(maxIterations);
    EXPECT_CALL(*mMockProcStatCollector, collect()).Times(maxIterations);
    EXPECT_CALL(*mMockDataProcessor,
                onUserSwitchCollection(_, Eq(fromUserId), Eq(toUserId), Eq(mMockUidStatsCollector),
                                       Eq(mMockProcStatCollector)))
            .Times(maxIterations);

    // Poll all post user switch collections except last
    for (int i = 0; i < maxIterations - 1; ++i) {
        ASSERT_RESULT_OK(mLooperStub->pollCache());

        ASSERT_EQ(mLooperStub->numSecondsElapsed(), kTestSystemEventCollectionIntervalSecs.count())
                << "Subsequent post user switch collection didn't happen at "
                << kTestSystemEventCollectionIntervalSecs.count() << " seconds interval";
        ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::USER_SWITCH_COLLECTION)
                << "Invalid collection event";
    }

    // Poll the last post user switch collection
    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mLooperStub->numSecondsElapsed(), kTestSystemEventCollectionIntervalSecs.count())
            << "Last user switch collection didn't happen immediately after sending "
            << "END_USER_SWITCH_COLLECTION message";
    ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::PERIODIC_COLLECTION)
            << "Invalid collection event";
    ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());
}

TEST_F(WatchdogPerfServiceTest, TestUserSwitchCollectionUserUnlockingWithNoPrevTimeout) {
    ASSERT_NO_FATAL_FAILURE(startService());
    ASSERT_NO_FATAL_FAILURE(startPeriodicCollection());
    ASSERT_NO_FATAL_FAILURE(skipPeriodicMonitorEvents());

    EXPECT_CALL(*mMockUidStatsCollector, collect()).Times(1);
    EXPECT_CALL(*mMockProcStatCollector, collect()).Times(1);
    EXPECT_CALL(*mMockDataProcessor,
                onPeriodicCollection(_, SystemState::NORMAL_MODE, Eq(mMockUidStatsCollector),
                                     Eq(mMockProcStatCollector), _))
            .Times(1);
    EXPECT_CALL(*mMockDataProcessor, onUserSwitchCollection(_, _, _, _, _)).Times(0);

    ASSERT_RESULT_OK(mService->onUserStateChange(100, UserState::USER_STATE_UNLOCKING));

    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mLooperStub->numSecondsElapsed(), 1)
            << "First periodic collection didn't happen at 1 second interval";
    ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::PERIODIC_COLLECTION)
            << "Invalid collection event";
    ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());
}

TEST_F(WatchdogPerfServiceTest, TestIgnoreUserSwitchCollectionDuringCustomCollection) {
    ASSERT_NO_FATAL_FAILURE(startService());

    ASSERT_NO_FATAL_FAILURE(startPeriodicCollection());

    userid_t fromUserId = 0;
    userid_t toUserId = 100;

    // Start custom collection
    std::string customCollectionIntervalStr =
            std::to_string(kTestCustomCollectionIntervalSecs.count());
    std::string customCollectionDurationStr =
            std::to_string(kTestCustomCollectionDurationSecs.count());

    const char* firstArgs[] = {kStartCustomCollectionFlag, kIntervalFlag,
                               customCollectionIntervalStr.c_str(), kMaxDurationFlag,
                               customCollectionDurationStr.c_str()};

    ASSERT_RESULT_OK(mService->onCustomCollection(-1, firstArgs, /*numArgs=*/5));

    EXPECT_CALL(*mMockUidStatsCollector, collect()).Times(2);
    EXPECT_CALL(*mMockProcStatCollector, collect()).Times(2);
    EXPECT_CALL(*mMockDataProcessor,
                onCustomCollection(_, SystemState::NORMAL_MODE, _, Eq(mMockUidStatsCollector),
                                   Eq(mMockProcStatCollector), _))
            .Times(2);
    EXPECT_CALL(*mMockDataProcessor,
                onUserSwitchCollection(_, Eq(fromUserId), Eq(toUserId), Eq(mMockUidStatsCollector),
                                       Eq(mMockProcStatCollector)))
            .Times(0);

    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mLooperStub->numSecondsElapsed(), 0) << "Custom collection didn't start immediately";
    ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::CUSTOM_COLLECTION)
            << "Invalid collection event";

    // Custom collection while user switch signal is received
    ASSERT_RESULT_OK(mService->onUserStateChange(100, UserState::USER_STATE_SWITCHING));

    // Continued custom collection
    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mLooperStub->numSecondsElapsed(), kTestCustomCollectionIntervalSecs.count())
            << "Subsequent custom collection didn't happen at "
            << kTestCustomCollectionIntervalSecs.count() << " seconds interval";
    ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::CUSTOM_COLLECTION)
            << "Invalid collection event";
    ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());
}

TEST_F(WatchdogPerfServiceTest, TestWakeUpCollection) {
    ASSERT_NO_FATAL_FAILURE(startService());

    ASSERT_NO_FATAL_FAILURE(startPeriodicCollection());

    // #1 Wake up collection
    EXPECT_CALL(*mMockUidStatsCollector, collect()).Times(1);
    EXPECT_CALL(*mMockProcStatCollector, collect()).Times(1);
    EXPECT_CALL(*mMockDataProcessor, onSystemStartup()).Times(1);
    EXPECT_CALL(*mMockDataProcessor,
                onWakeUpCollection(_, Eq(mMockUidStatsCollector), Eq(mMockProcStatCollector)))
            .Times(1);

    ASSERT_RESULT_OK(mService->onSuspendExit());

    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mLooperStub->numSecondsElapsed(), 0) << "Wake up collection didn't start immediately";
    ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::WAKE_UP_COLLECTION)
            << "Invalid collection event";
    ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());

    // #2 Wake up collections before duration expires
    int maxIterations = static_cast<int>(kTestWakeUpDurationSecs.count() /
                                         kTestSystemEventCollectionIntervalSecs.count());

    EXPECT_CALL(*mMockUidStatsCollector, collect()).Times(maxIterations);
    EXPECT_CALL(*mMockProcStatCollector, collect()).Times(maxIterations);
    EXPECT_CALL(*mMockDataProcessor,
                onWakeUpCollection(_, Eq(mMockUidStatsCollector), Eq(mMockProcStatCollector)))
            .Times(maxIterations);

    // Poll all remaining wake up collections except last
    for (int i = 0; i < maxIterations - 1; ++i) {
        ASSERT_RESULT_OK(mLooperStub->pollCache());

        ASSERT_EQ(mLooperStub->numSecondsElapsed(), kTestSystemEventCollectionIntervalSecs.count())
                << "Subsequent wake up collection didn't happen at "
                << kTestSystemEventCollectionIntervalSecs.count() << " seconds interval";
        ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::WAKE_UP_COLLECTION)
                << "Invalid collection event";
    }

    // Suspend exit signal should be ignored since already running wake up collection.
    ASSERT_RESULT_OK(mService->onSuspendExit());

    // Poll the last wake up collection
    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mLooperStub->numSecondsElapsed(), kTestSystemEventCollectionIntervalSecs.count())
            << "Last wake up collection didn't happen immediately after sending "
            << "END_WAKE_UP_COLLECTION message";
    ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::PERIODIC_COLLECTION)
            << "Invalid collection event";
    ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());
}

TEST_F(WatchdogPerfServiceTest, TestWakeUpCollectionDuringCustomCollection) {
    ASSERT_NO_FATAL_FAILURE(startService());

    ASSERT_NO_FATAL_FAILURE(startPeriodicCollection());

    // Start custom collection
    std::string customCollectionIntervalStr =
            std::to_string(kTestCustomCollectionIntervalSecs.count());
    std::string customCollectionDurationStr =
            std::to_string(kTestCustomCollectionDurationSecs.count());

    const char* firstArgs[] = {kStartCustomCollectionFlag, kIntervalFlag,
                               customCollectionIntervalStr.c_str(), kMaxDurationFlag,
                               customCollectionDurationStr.c_str()};

    ASSERT_RESULT_OK(mService->onCustomCollection(-1, firstArgs, /*numArgs=*/5));

    EXPECT_CALL(*mMockUidStatsCollector, collect()).Times(2);
    EXPECT_CALL(*mMockProcStatCollector, collect()).Times(2);
    EXPECT_CALL(*mMockDataProcessor,
                onCustomCollection(_, SystemState::NORMAL_MODE, _, Eq(mMockUidStatsCollector),
                                   Eq(mMockProcStatCollector), _))
            .Times(2);
    EXPECT_CALL(*mMockDataProcessor,
                onWakeUpCollection(_, Eq(mMockUidStatsCollector), Eq(mMockProcStatCollector)))
            .Times(0);

    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mLooperStub->numSecondsElapsed(), 0) << "Custom collection didn't start immediately";
    ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::CUSTOM_COLLECTION)
            << "Invalid collection event";

    // Custom collection while suspend exit signal is received
    ASSERT_RESULT_OK(mService->onSuspendExit());

    // Continued custom collection
    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mLooperStub->numSecondsElapsed(), kTestCustomCollectionIntervalSecs.count())
            << "Subsequent custom collection didn't happen at "
            << kTestCustomCollectionIntervalSecs.count() << " seconds interval";
    ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::CUSTOM_COLLECTION)
            << "Invalid collection event";
    ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());
}

TEST_F(WatchdogPerfServiceTest, TestPeriodicMonitorRequestsCollection) {
    ASSERT_NO_FATAL_FAILURE(startService());

    ASSERT_NO_FATAL_FAILURE(startPeriodicCollection());

    // Periodic monitor issuing an alert to start new collection.
    EXPECT_CALL(*mMockProcDiskStatsCollector, collect()).Times(1);
    EXPECT_CALL(*mMockDataProcessor, onPeriodicMonitor(_, Eq(mMockProcDiskStatsCollector), _))
            .WillOnce([&](auto, auto, const auto& alertHandler) -> Result<void> {
                alertHandler();
                return {};
            });

    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mLooperStub->numSecondsElapsed(), kTestPeriodicMonitorIntervalSecs.count())
            << "First periodic monitor didn't happen at "
            << kTestPeriodicMonitorIntervalSecs.count() << " seconds interval";
    ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());

    EXPECT_CALL(*mMockUidStatsCollector, collect()).Times(1);
    EXPECT_CALL(*mMockProcStatCollector, collect()).Times(1);
    EXPECT_CALL(*mMockDataProcessor,
                onPeriodicCollection(_, SystemState::NORMAL_MODE, Eq(mMockUidStatsCollector),
                                     Eq(mMockProcStatCollector), _))
            .Times(1);

    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mLooperStub->numSecondsElapsed(), 0)
            << "First periodic collection didn't happen immediately after the alert";

    ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());

    EXPECT_CALL(*mMockDataProcessor, terminate()).Times(1);
}

TEST_F(WatchdogPerfServiceTest, TestShutdownEnter) {
    ASSERT_NO_FATAL_FAILURE(startService());

    // Start boot-time collection
    EXPECT_CALL(*mMockUidStatsCollector, collect()).Times(1);
    EXPECT_CALL(*mMockProcStatCollector, collect()).Times(1);
    EXPECT_CALL(*mMockDataProcessor,
                onBoottimeCollection(_, Eq(mMockUidStatsCollector), Eq(mMockProcStatCollector), _))
            .Times(1);

    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mLooperStub->numSecondsElapsed(), 0)
            << "Boot-time collection didn't start immediately";
    ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::BOOT_TIME_COLLECTION)
            << "Invalid collection event";
    ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());

    ASSERT_RESULT_OK(mService->onShutdownEnter());

    // Switch to periodic collection
    EXPECT_CALL(*mMockUidStatsCollector, collect()).Times(1);
    EXPECT_CALL(*mMockProcStatCollector, collect()).Times(1);
    EXPECT_CALL(*mMockDataProcessor,
                onPeriodicCollection(_, SystemState::NORMAL_MODE, Eq(mMockUidStatsCollector),
                                     Eq(mMockProcStatCollector), _))
            .Times(1);

    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mLooperStub->numSecondsElapsed(), 0)
            << "Periodic collection didn't start immediately after receiving shutdown enter signal";
    ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::PERIODIC_COLLECTION)
            << "Invalid collection event";
    ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());
}

TEST_F(WatchdogPerfServiceTest, TestShutdownEnterWithCustomCollection) {
    ASSERT_NO_FATAL_FAILURE(startService());

    ASSERT_NO_FATAL_FAILURE(startPeriodicCollection());

    // Start custom collection
    std::string customCollectionIntervalStr =
            std::to_string(kTestCustomCollectionIntervalSecs.count());
    std::string customCollectionDurationStr =
            std::to_string(kTestCustomCollectionDurationSecs.count());
    const char* firstArgs[] = {kStartCustomCollectionFlag, kIntervalFlag,
                               customCollectionIntervalStr.c_str(), kMaxDurationFlag,
                               customCollectionDurationStr.c_str()};

    ASSERT_RESULT_OK(mService->onCustomCollection(-1, firstArgs, /*numArgs=*/5));

    EXPECT_CALL(*mMockUidStatsCollector, collect()).Times(1);
    EXPECT_CALL(*mMockProcStatCollector, collect()).Times(1);
    EXPECT_CALL(*mMockDataProcessor,
                onCustomCollection(_, SystemState::NORMAL_MODE, _, Eq(mMockUidStatsCollector),
                                   Eq(mMockProcStatCollector), _))
            .Times(1);

    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mLooperStub->numSecondsElapsed(), 0) << "Custom collection didn't start immediately";
    ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::CUSTOM_COLLECTION)
            << "Invalid collection event";
    ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());

    // Suspend in middle of custom collection
    ASSERT_RESULT_OK(mService->onShutdownEnter());

    // Custom collection
    EXPECT_CALL(*mMockUidStatsCollector, collect()).Times(1);
    EXPECT_CALL(*mMockProcStatCollector, collect()).Times(1);
    EXPECT_CALL(*mMockDataProcessor,
                onCustomCollection(_, SystemState::NORMAL_MODE, _, Eq(mMockUidStatsCollector),
                                   Eq(mMockProcStatCollector), _))
            .Times(1);

    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_EQ(mLooperStub->numSecondsElapsed(), kTestCustomCollectionIntervalSecs.count())
            << "Subsequent custom collection didn't happen at "
            << kTestCustomCollectionIntervalSecs.count() << " seconds interval";
    ASSERT_EQ(mServicePeer->getCurrCollectionEvent(), EventType::CUSTOM_COLLECTION)
            << "Invalid collection event";
    ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());
}

TEST_F(WatchdogPerfServiceTest, TestSystemStateSwitch) {
    ASSERT_NO_FATAL_FAILURE(startService());

    ASSERT_NO_FATAL_FAILURE(startPeriodicCollection());
    ASSERT_NO_FATAL_FAILURE(skipPeriodicMonitorEvents());

    EXPECT_CALL(*mMockDataProcessor, onPeriodicCollection(_, SystemState::NORMAL_MODE, _, _, _))
            .Times(1);

    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());

    ASSERT_NO_FATAL_FAILURE(skipPeriodicMonitorEvents());

    mService->setSystemState(SystemState::GARAGE_MODE);

    EXPECT_CALL(*mMockDataProcessor, onPeriodicCollection(_, SystemState::GARAGE_MODE, _, _, _))
            .Times(1);

    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());

    ASSERT_NO_FATAL_FAILURE(skipPeriodicMonitorEvents());

    mService->setSystemState(SystemState::NORMAL_MODE);

    EXPECT_CALL(*mMockDataProcessor, onPeriodicCollection(_, SystemState::NORMAL_MODE, _, _, _))
            .Times(1);

    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());

    EXPECT_CALL(*mMockDataProcessor, terminate()).Times(1);
}

TEST_F(WatchdogPerfServiceTest, TestHandlesInvalidDumpArguments) {
    ASSERT_NO_FATAL_FAILURE(startService());

    ASSERT_NO_FATAL_FAILURE(startPeriodicCollection());

    const char* firstArgs[] = {kStartCustomCollectionFlag, "Invalid flag", "Invalid value"};

    ASSERT_FALSE(mService->onCustomCollection(-1, firstArgs, /*numArgs=*/3).ok());

    const char* secondArgs[] = {kStartCustomCollectionFlag, kIntervalFlag, "Invalid interval"};

    ASSERT_FALSE(mService->onCustomCollection(-1, secondArgs, /*numArgs=*/3).ok());

    const char* thirdArgs[] = {kStartCustomCollectionFlag, kMaxDurationFlag, "Invalid duration"};

    ASSERT_FALSE(mService->onCustomCollection(-1, thirdArgs, /*numArgs=*/3).ok());

    const char* fourthArgs[] = {kEndCustomCollectionFlag, kMaxDurationFlag, "10"};

    ASSERT_FALSE(mService->onCustomCollection(-1, fourthArgs, /*numArgs=*/3).ok());

    const char* fifthArgs[] = {"Invalid flag"};

    ASSERT_FALSE(mService->onCustomCollection(-1, fifthArgs, /*numArgs=*/1).ok());
}

TEST_F(WatchdogPerfServiceTest, TestOnCarWatchdogServiceRegistered) {
    ASSERT_NO_FATAL_FAILURE(startService());
    ASSERT_NO_FATAL_FAILURE(startPeriodicCollection());
    ASSERT_NO_FATAL_FAILURE(skipPeriodicMonitorEvents());
    ASSERT_NO_FATAL_FAILURE(skipPeriodicCollection());

    // Expect because the next pollCache call will result in an onPeriodicMonitor call
    // because no message is sent to process unsent resource stats
    EXPECT_CALL(*mMockDataProcessor, onPeriodicMonitor(_, _, _)).Times(1);
    EXPECT_CALL(*mMockDataProcessor, onCarWatchdogServiceRegistered()).Times(1);
    EXPECT_CALL(*mMockWatchdogServiceHelper, onLatestResourceStats(_)).Times(0);

    mService->onCarWatchdogServiceRegistered();

    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());
}

TEST_F(WatchdogPerfServiceTest, TestOnCarWatchdogServiceRegisteredWithUnsentResourceStats) {
    ASSERT_NO_FATAL_FAILURE(startService());
    ASSERT_NO_FATAL_FAILURE(startPeriodicCollection());
    ASSERT_NO_FATAL_FAILURE(skipPeriodicMonitorEvents());

    EXPECT_CALL(*mMockUidStatsCollector, collect()).Times(1);
    EXPECT_CALL(*mMockProcStatCollector, collect()).Times(1);
    EXPECT_CALL(*mMockDataProcessor, onCarWatchdogServiceRegistered()).Times(1);
    EXPECT_CALL(*mMockDataProcessor,
                onPeriodicCollection(_, SystemState::NORMAL_MODE, Eq(mMockUidStatsCollector),
                                     Eq(mMockProcStatCollector), _))
            .Times(1)
            .WillOnce([&](auto, auto, auto, auto, auto* resourceStats) -> Result<void> {
                resourceStats->resourceOveruseStats = std::make_optional<ResourceOveruseStats>({});
                return {};
            });
    EXPECT_CALL(*mMockWatchdogServiceHelper, isServiceConnected()).Times(1).WillOnce(Return(false));
    // Called when CarWatchdogService is registered
    EXPECT_CALL(*mMockWatchdogServiceHelper, onLatestResourceStats(_))
            .Times(1)
            .WillOnce(Return(ByMove(ndk::ScopedAStatus::ok())));

    // Handle the periodic collection
    ASSERT_RESULT_OK(mLooperStub->pollCache());

    mService->onCarWatchdogServiceRegistered();

    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());
}

TEST_F(WatchdogPerfServiceTest, TestUnsentResourceStatsEviction) {
    ASSERT_NO_FATAL_FAILURE(startService());
    ASSERT_NO_FATAL_FAILURE(startPeriodicCollection());
    ASSERT_NO_FATAL_FAILURE(skipPeriodicMonitorEvents());

    EXPECT_CALL(*mMockUidStatsCollector, collect()).Times(1);
    EXPECT_CALL(*mMockProcStatCollector, collect()).Times(1);
    EXPECT_CALL(*mMockDataProcessor, onCarWatchdogServiceRegistered()).Times(1);
    EXPECT_CALL(*mMockDataProcessor,
                onPeriodicCollection(_, SystemState::NORMAL_MODE, Eq(mMockUidStatsCollector),
                                     Eq(mMockProcStatCollector), _))
            .Times(1)
            .WillOnce([&](auto, auto, auto, auto, auto* resourceStats) -> Result<void> {
                resourceStats->resourceOveruseStats = std::make_optional<ResourceOveruseStats>({});
                return {};
            });
    EXPECT_CALL(*mMockWatchdogServiceHelper, isServiceConnected()).Times(1).WillOnce(Return(false));
    // Should not be called once CarWatchdogService is registered
    EXPECT_CALL(*mMockWatchdogServiceHelper, onLatestResourceStats(_)).Times(0);

    // Handle the periodic collection
    ASSERT_RESULT_OK(mLooperStub->pollCache());

    // Increment time so that the unsent resource stat is evicted
    mLooperStub->incrementTime(kPrevUnsentResourceStatsMaxDurationNs);

    mService->onCarWatchdogServiceRegistered();

    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());
}

TEST_F(WatchdogPerfServiceTest, TestUnsentResourceStatsMaxCacheSize) {
    ASSERT_NO_FATAL_FAILURE(startService());
    ASSERT_NO_FATAL_FAILURE(startPeriodicCollection());
    ASSERT_NO_FATAL_FAILURE(removePeriodicMonitorEvents());

    int32_t maxCacheSize = 10;
    int64_t elapsedPeriodicIntervalMs = std::chrono::duration_cast<std::chrono::milliseconds>(
                                                kTestPeriodicCollectionIntervalSecs)
                                                .count();

    std::vector<ResourceStats> expectedResourceStats = {};

    // Handle the periodic collections.
    for (int64_t i = 0; i < maxCacheSize; ++i) {
        expectedResourceStats.push_back(ResourceStats{
                .resourceUsageStats = std::make_optional<ResourceUsageStats>({
                        .startTimeEpochMillis = i,
                        .durationInMillis = elapsedPeriodicIntervalMs,
                }),
        });

        EXPECT_CALL(*mMockUidStatsCollector, collect()).Times(1);
        EXPECT_CALL(*mMockProcStatCollector, collect()).Times(1);
        EXPECT_CALL(*mMockDataProcessor,
                    onPeriodicCollection(_, SystemState::NORMAL_MODE, Eq(mMockUidStatsCollector),
                                         Eq(mMockProcStatCollector), _))
                .Times(1)
                .WillOnce([&](auto, auto, auto, auto, auto* resourceStats) -> Result<void> {
                    resourceStats->resourceUsageStats =
                            expectedResourceStats.back().resourceUsageStats;
                    return {};
                });
        EXPECT_CALL(*mMockWatchdogServiceHelper, isServiceConnected())
                .Times(1)
                .WillRepeatedly(Return(false));

        ASSERT_RESULT_OK(mLooperStub->pollCache());
    }

    ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());

    // The first resource stats should be evicted.
    expectedResourceStats.erase(expectedResourceStats.begin());

    expectedResourceStats.push_back(ResourceStats{
            .resourceUsageStats = std::make_optional<ResourceUsageStats>({
                    .startTimeEpochMillis = maxCacheSize,
                    .durationInMillis = elapsedPeriodicIntervalMs,
            }),
    });

    std::vector<ResourceStats> actualResourceStats;

    EXPECT_CALL(*mMockUidStatsCollector, collect()).Times(1);
    EXPECT_CALL(*mMockProcStatCollector, collect()).Times(1);
    EXPECT_CALL(*mMockDataProcessor,
                onPeriodicCollection(_, SystemState::NORMAL_MODE, Eq(mMockUidStatsCollector),
                                     Eq(mMockProcStatCollector), _))
            .Times(1)
            .WillRepeatedly([&](auto, auto, auto, auto, auto* resourceStats) -> Result<void> {
                resourceStats->resourceUsageStats = expectedResourceStats.back().resourceUsageStats;
                return {};
            });
    EXPECT_CALL(*mMockWatchdogServiceHelper, isServiceConnected()).Times(1).WillOnce(Return(true));
    EXPECT_CALL(*mMockWatchdogServiceHelper, onLatestResourceStats(_))
            .Times(1)
            .WillOnce([&](auto unsentStats) -> ndk::ScopedAStatus {
                actualResourceStats = unsentStats;
                return ndk::ScopedAStatus::ok();
            });

    // Handle an extra periodic collection, where unsent resource cache should
    // evict the oldest stats.
    ASSERT_RESULT_OK(mLooperStub->pollCache());

    // Handle the SEND_RESOURCE_STATS message.
    ASSERT_RESULT_OK(mLooperStub->pollCache());

    ASSERT_NO_FATAL_FAILURE(verifyAndClearExpectations());
    ASSERT_EQ(actualResourceStats, expectedResourceStats)
            << "Expected: " << toString(expectedResourceStats)
            << "\nActual: " << toString(actualResourceStats);
}

}  // namespace watchdog
}  // namespace automotive
}  // namespace android
