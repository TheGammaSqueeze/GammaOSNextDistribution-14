/*
 * Copyright 2021 The Android Open Source Project
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

#include "MockPackageInfoResolver.h"
#include "MockUidCpuStatsCollector.h"
#include "MockUidIoStatsCollector.h"
#include "MockUidProcStatsCollector.h"
#include "PackageInfoTestUtils.h"
#include "UidIoStatsCollector.h"
#include "UidProcStatsCollector.h"
#include "UidProcStatsCollectorTestUtils.h"
#include "UidStatsCollector.h"

#include <android-base/stringprintf.h>
#include <gmock/gmock.h>
#include <utils/RefBase.h>

#include <inttypes.h>

#include <string>

namespace android {
namespace automotive {
namespace watchdog {

using ::aidl::android::automotive::watchdog::internal::PackageInfo;
using ::aidl::android::automotive::watchdog::internal::UidType;
using ::android::base::Error;
using ::android::base::Result;
using ::android::base::StringAppendF;
using ::android::base::StringPrintf;
using ::testing::AllOf;
using ::testing::Eq;
using ::testing::ExplainMatchResult;
using ::testing::Field;
using ::testing::IsEmpty;
using ::testing::Matcher;
using ::testing::Return;
using ::testing::UnorderedElementsAre;
using ::testing::UnorderedElementsAreArray;

namespace {

std::string toString(const UidStats& uidStats) {
    return StringPrintf("UidStats{packageInfo: %s, cpuTimeMillis: %" PRId64
                        ", ioStats: %s, procStats: %s}",
                        uidStats.packageInfo.toString().c_str(), uidStats.cpuTimeMillis,
                        uidStats.ioStats.toString().c_str(), uidStats.procStats.toString().c_str());
}

std::string toString(const std::vector<UidStats>& uidStats) {
    std::string buffer;
    StringAppendF(&buffer, "{");
    for (const auto& stats : uidStats) {
        StringAppendF(&buffer, "%s\n", toString(stats).c_str());
    }
    StringAppendF(&buffer, "}");
    return buffer;
}

MATCHER_P(UidStatsEq, expected, "") {
    return ExplainMatchResult(AllOf(Field("packageInfo", &UidStats::packageInfo,
                                          PackageInfoEq(expected.packageInfo)),
                                    Field("cpuTimeMillis", &UidStats::cpuTimeMillis,
                                          Eq(expected.cpuTimeMillis)),
                                    Field("ioStats", &UidStats::ioStats, Eq(expected.ioStats)),
                                    Field("procStats", &UidStats::procStats,
                                          UidProcStatsEq(expected.procStats))),
                              arg, result_listener);
}

std::vector<Matcher<const UidStats&>> UidStatsMatchers(const std::vector<UidStats>& uidStats) {
    std::vector<Matcher<const UidStats&>> matchers;
    for (const auto& stats : uidStats) {
        matchers.push_back(UidStatsEq(stats));
    }
    return matchers;
}

std::unordered_map<uid_t, PackageInfo> samplePackageInfoByUid() {
    return {{1001234, constructPackageInfo("system.daemon", 1001234, UidType::NATIVE)},
            {1005678, constructPackageInfo("kitchensink.app", 1005678, UidType::APPLICATION)}};
}

std::unordered_map<uid_t, UidIoStats> sampleUidIoStatsByUid() {
    return {{1001234,
             UidIoStats{/*fgRdBytes=*/3'000, /*bgRdBytes=*/0,
                        /*fgWrBytes=*/500,
                        /*bgWrBytes=*/0, /*fgFsync=*/20,
                        /*bgFsync=*/0}},
            {1005678,
             UidIoStats{/*fgRdBytes=*/30, /*bgRdBytes=*/100,
                        /*fgWrBytes=*/50, /*bgWrBytes=*/200,
                        /*fgFsync=*/45, /*bgFsync=*/60}}};
}

std::unordered_map<uid_t, UidProcStats> sampleUidProcStatsByUid() {
    return {{1001234,
             UidProcStats{.cpuTimeMillis = 10,
                          .totalMajorFaults = 220,
                          .totalTasksCount = 2,
                          .ioBlockedTasksCount = 1,
                          .processStatsByPid = {{1, {"init", 0, 10, 220, 2, 1}}}}},
            {1005678,
             UidProcStats{.cpuTimeMillis = 43,
                          .totalMajorFaults = 600,
                          .totalTasksCount = 2,
                          .ioBlockedTasksCount = 2,
                          .processStatsByPid = {
                                  {1000, {"system_server", 13'400, 43, 600, 2, 2}}}}}};
}

std::unordered_map<uid_t, int64_t> sampleUidCpuStatsByUid() {
    return {{1001234, 15}, {1005678, 43}};
}

std::vector<UidStats> sampleUidStats() {
    return {{.packageInfo = constructPackageInfo("system.daemon", 1001234, UidType::NATIVE),
             .cpuTimeMillis = 15,
             .ioStats = UidIoStats{/*fgRdBytes=*/3'000, /*bgRdBytes=*/0, /*fgWrBytes=*/500,
                                   /*bgWrBytes=*/0, /*fgFsync=*/20, /*bgFsync=*/0},
             .procStats = UidProcStats{.cpuTimeMillis = 10,
                                       .totalMajorFaults = 220,
                                       .totalTasksCount = 2,
                                       .ioBlockedTasksCount = 1,
                                       .processStatsByPid = {{1, {"init", 0, 10, 220, 2, 1}}}}},
            {.packageInfo = constructPackageInfo("kitchensink.app", 1005678, UidType::APPLICATION),
             .cpuTimeMillis = 43,
             .ioStats = UidIoStats{/*fgRdBytes=*/30, /*bgRdBytes=*/100, /*fgWrBytes=*/50,
                                   /*bgWrBytes=*/200,
                                   /*fgFsync=*/45, /*bgFsync=*/60},
             .procStats = UidProcStats{.cpuTimeMillis = 43,
                                       .totalMajorFaults = 600,
                                       .totalTasksCount = 2,
                                       .ioBlockedTasksCount = 2,
                                       .processStatsByPid = {
                                               {1000, {"system_server", 13'400, 43, 600, 2, 2}}}}}};
}

}  // namespace

namespace internal {

class UidStatsCollectorPeer final : public RefBase {
public:
    explicit UidStatsCollectorPeer(sp<UidStatsCollector> collector) : mCollector(collector) {}
    ~UidStatsCollectorPeer() { mCollector.clear(); }

    void setPackageInfoResolver(const sp<PackageInfoResolverInterface>& packageInfoResolver) {
        mCollector->mPackageInfoResolver = packageInfoResolver;
    }

    void setUidIoStatsCollector(const sp<UidIoStatsCollectorInterface>& uidIoStatsCollector) {
        mCollector->mUidIoStatsCollector = uidIoStatsCollector;
    }

    void setUidProcStatsCollector(const sp<UidProcStatsCollectorInterface>& uidProcStatsCollector) {
        mCollector->mUidProcStatsCollector = uidProcStatsCollector;
    }

    void setUidCpuStatsCollector(const sp<UidCpuStatsCollectorInterface>& uidCpuStatsCollector) {
        mCollector->mUidCpuStatsCollector = uidCpuStatsCollector;
    }

private:
    sp<UidStatsCollector> mCollector;
};

}  // namespace internal

class UidStatsCollectorTest : public ::testing::Test {
protected:
    virtual void SetUp() {
        mUidStatsCollector = sp<UidStatsCollector>::make();
        mUidStatsCollectorPeer = sp<internal::UidStatsCollectorPeer>::make(mUidStatsCollector);
        mMockPackageInfoResolver = sp<MockPackageInfoResolver>::make();
        mMockUidIoStatsCollector = sp<MockUidIoStatsCollector>::make();
        mMockUidProcStatsCollector = sp<MockUidProcStatsCollector>::make();
        mMockUidCpuStatsCollector = sp<MockUidCpuStatsCollector>::make();
        mUidStatsCollectorPeer->setPackageInfoResolver(mMockPackageInfoResolver);
        mUidStatsCollectorPeer->setUidIoStatsCollector(mMockUidIoStatsCollector);
        mUidStatsCollectorPeer->setUidProcStatsCollector(mMockUidProcStatsCollector);
        mUidStatsCollectorPeer->setUidCpuStatsCollector(mMockUidCpuStatsCollector);
    }

    virtual void TearDown() {
        mUidStatsCollector.clear();
        mUidStatsCollectorPeer.clear();
        mMockPackageInfoResolver.clear();
        mMockUidIoStatsCollector.clear();
        mMockUidProcStatsCollector.clear();
        mMockUidCpuStatsCollector.clear();
    }

    sp<UidStatsCollector> mUidStatsCollector;
    sp<internal::UidStatsCollectorPeer> mUidStatsCollectorPeer;
    sp<MockPackageInfoResolver> mMockPackageInfoResolver;
    sp<MockUidIoStatsCollector> mMockUidIoStatsCollector;
    sp<MockUidProcStatsCollector> mMockUidProcStatsCollector;
    sp<MockUidCpuStatsCollector> mMockUidCpuStatsCollector;
};

TEST_F(UidStatsCollectorTest, TestInit) {
    EXPECT_CALL(*mMockUidIoStatsCollector, init()).Times(1);
    EXPECT_CALL(*mMockUidProcStatsCollector, init()).Times(1);

    mUidStatsCollector->init();
}

TEST_F(UidStatsCollectorTest, TestCollect) {
    EXPECT_CALL(*mMockUidIoStatsCollector, enabled()).WillOnce(Return(true));
    EXPECT_CALL(*mMockUidProcStatsCollector, enabled()).WillOnce(Return(true));

    EXPECT_CALL(*mMockUidIoStatsCollector, collect()).WillOnce(Return(Result<void>()));
    EXPECT_CALL(*mMockUidProcStatsCollector, collect()).WillOnce(Return(Result<void>()));

    EXPECT_CALL(*mMockUidIoStatsCollector, latestStats())
            .WillOnce(Return(std::unordered_map<uid_t, UidIoStats>()));
    EXPECT_CALL(*mMockUidProcStatsCollector, latestStats())
            .WillOnce(Return(std::unordered_map<uid_t, UidProcStats>()));

    EXPECT_CALL(*mMockUidIoStatsCollector, deltaStats())
            .WillOnce(Return(std::unordered_map<uid_t, UidIoStats>()));
    EXPECT_CALL(*mMockUidProcStatsCollector, deltaStats())
            .WillOnce(Return(std::unordered_map<uid_t, UidProcStats>()));

    ASSERT_RESULT_OK(mUidStatsCollector->collect());
}

TEST_F(UidStatsCollectorTest, TestFailsCollectOnUidIoStatsCollectorError) {
    Result<void> errorResult = Error() << "Failed to collect per-UID I/O stats";
    EXPECT_CALL(*mMockUidIoStatsCollector, collect()).WillOnce(Return(errorResult));

    ASSERT_FALSE(mUidStatsCollector->collect().ok())
            << "Must fail to collect when per-UID I/O stats collector fails";
}

TEST_F(UidStatsCollectorTest, TestFailsCollectOnUidProcStatsCollectorError) {
    Result<void> errorResult = Error() << "Failed to collect per-UID proc stats";
    EXPECT_CALL(*mMockUidProcStatsCollector, collect()).WillOnce(Return(errorResult));

    ASSERT_FALSE(mUidStatsCollector->collect().ok())
            << "Must fail to collect when per-UID proc stats collector fails";
}

TEST_F(UidStatsCollectorTest, TestCollectLatestStats) {
    const std::unordered_map<uid_t, PackageInfo> packageInfoByUid = samplePackageInfoByUid();
    const std::unordered_map<uid_t, UidIoStats> uidIoStatsByUid = sampleUidIoStatsByUid();
    const std::unordered_map<uid_t, UidProcStats> uidProcStatsByUid = sampleUidProcStatsByUid();
    const std::unordered_map<uid_t, int64_t> uidCpuStatsByUid = sampleUidCpuStatsByUid();

    EXPECT_CALL(*mMockPackageInfoResolver,
                getPackageInfosForUids(UnorderedElementsAre(1001234, 1005678)))
            .WillOnce(Return(packageInfoByUid));
    EXPECT_CALL(*mMockUidIoStatsCollector, latestStats()).WillOnce(Return(uidIoStatsByUid));
    EXPECT_CALL(*mMockUidProcStatsCollector, latestStats()).WillOnce(Return(uidProcStatsByUid));
    EXPECT_CALL(*mMockUidCpuStatsCollector, latestStats()).WillOnce(Return(uidCpuStatsByUid));

    ASSERT_RESULT_OK(mUidStatsCollector->collect());

    const std::vector<UidStats> expected = sampleUidStats();

    auto actual = mUidStatsCollector->latestStats();

    EXPECT_THAT(actual, UnorderedElementsAreArray(UidStatsMatchers(expected)))
            << "Latest UID stats doesn't match.\nExpected: " << toString(expected)
            << "\nActual: " << toString(actual);

    actual = mUidStatsCollector->deltaStats();

    EXPECT_THAT(actual, IsEmpty()) << "Delta UID stats isn't empty.\nActual: " << toString(actual);
}

TEST_F(UidStatsCollectorTest, TestCollectDeltaStats) {
    const std::unordered_map<uid_t, PackageInfo> packageInfoByUid = samplePackageInfoByUid();
    const std::unordered_map<uid_t, UidIoStats> uidIoStatsByUid = sampleUidIoStatsByUid();
    const std::unordered_map<uid_t, UidProcStats> uidProcStatsByUid = sampleUidProcStatsByUid();
    const std::unordered_map<uid_t, int64_t> uidCpuStatsByUid = sampleUidCpuStatsByUid();

    EXPECT_CALL(*mMockPackageInfoResolver,
                getPackageInfosForUids(UnorderedElementsAre(1001234, 1005678)))
            .WillOnce(Return(packageInfoByUid));
    EXPECT_CALL(*mMockUidIoStatsCollector, deltaStats()).WillOnce(Return(uidIoStatsByUid));
    EXPECT_CALL(*mMockUidProcStatsCollector, deltaStats()).WillOnce(Return(uidProcStatsByUid));
    EXPECT_CALL(*mMockUidCpuStatsCollector, deltaStats()).WillOnce(Return(uidCpuStatsByUid));

    ASSERT_RESULT_OK(mUidStatsCollector->collect());

    const std::vector<UidStats> expected = sampleUidStats();

    auto actual = mUidStatsCollector->deltaStats();

    EXPECT_THAT(actual, UnorderedElementsAreArray(UidStatsMatchers(expected)))
            << "Delta UID stats doesn't match.\nExpected: " << toString(expected)
            << "\nActual: " << toString(actual);

    actual = mUidStatsCollector->latestStats();

    EXPECT_THAT(actual, IsEmpty()) << "Latest UID stats isn't empty.\nActual: " << toString(actual);
}

TEST_F(UidStatsCollectorTest, TestCollectDeltaStatsWithMissingUidIoStats) {
    const std::unordered_map<uid_t, PackageInfo> packageInfoByUid = samplePackageInfoByUid();
    std::unordered_map<uid_t, UidIoStats> uidIoStatsByUid = sampleUidIoStatsByUid();
    uidIoStatsByUid.erase(1001234);
    const std::unordered_map<uid_t, UidProcStats> uidProcStatsByUid = sampleUidProcStatsByUid();
    const std::unordered_map<uid_t, int64_t> uidCpuStatsByUid = sampleUidCpuStatsByUid();

    EXPECT_CALL(*mMockPackageInfoResolver,
                getPackageInfosForUids(UnorderedElementsAre(1001234, 1005678)))
            .WillOnce(Return(packageInfoByUid));
    EXPECT_CALL(*mMockUidIoStatsCollector, deltaStats()).WillOnce(Return(uidIoStatsByUid));
    EXPECT_CALL(*mMockUidProcStatsCollector, deltaStats()).WillOnce(Return(uidProcStatsByUid));
    EXPECT_CALL(*mMockUidCpuStatsCollector, deltaStats()).WillOnce(Return(uidCpuStatsByUid));

    ASSERT_RESULT_OK(mUidStatsCollector->collect());

    std::vector<UidStats> expected = sampleUidStats();
    expected[0].ioStats = {};

    auto actual = mUidStatsCollector->deltaStats();

    EXPECT_THAT(actual, UnorderedElementsAreArray(UidStatsMatchers(expected)))
            << "Delta UID stats doesn't match.\nExpected: " << toString(expected)
            << "\nActual: " << toString(actual);

    actual = mUidStatsCollector->latestStats();

    EXPECT_THAT(actual, IsEmpty()) << "Latest UID stats isn't empty.\nActual: " << toString(actual);
}

TEST_F(UidStatsCollectorTest, TestCollectDeltaStatsWithMissingUidProcStats) {
    const std::unordered_map<uid_t, PackageInfo> packageInfoByUid = samplePackageInfoByUid();
    const std::unordered_map<uid_t, UidIoStats> uidIoStatsByUid = sampleUidIoStatsByUid();
    std::unordered_map<uid_t, UidProcStats> uidProcStatsByUid = sampleUidProcStatsByUid();
    uidProcStatsByUid.erase(1001234);
    const std::unordered_map<uid_t, int64_t> uidCpuStatsByUid = sampleUidCpuStatsByUid();

    EXPECT_CALL(*mMockPackageInfoResolver,
                getPackageInfosForUids(UnorderedElementsAre(1001234, 1005678)))
            .WillOnce(Return(packageInfoByUid));
    EXPECT_CALL(*mMockUidIoStatsCollector, deltaStats()).WillOnce(Return(uidIoStatsByUid));
    EXPECT_CALL(*mMockUidProcStatsCollector, deltaStats()).WillOnce(Return(uidProcStatsByUid));
    EXPECT_CALL(*mMockUidCpuStatsCollector, deltaStats()).WillOnce(Return(uidCpuStatsByUid));

    ASSERT_RESULT_OK(mUidStatsCollector->collect());

    std::vector<UidStats> expected = sampleUidStats();
    expected[0].procStats = {};

    auto actual = mUidStatsCollector->deltaStats();

    EXPECT_THAT(actual, UnorderedElementsAreArray(UidStatsMatchers(expected)))
            << "Delta UID stats doesn't match.\nExpected: " << toString(expected)
            << "\nActual: " << toString(actual);

    actual = mUidStatsCollector->latestStats();

    EXPECT_THAT(actual, IsEmpty()) << "Latest UID stats isn't empty.\nActual: " << toString(actual);
}

TEST_F(UidStatsCollectorTest, TestCollectDeltaStatsWithMissingUidCpuStats) {
    const std::unordered_map<uid_t, PackageInfo> packageInfoByUid = samplePackageInfoByUid();
    const std::unordered_map<uid_t, UidIoStats> uidIoStatsByUid = sampleUidIoStatsByUid();
    const std::unordered_map<uid_t, UidProcStats> uidProcStatsByUid = sampleUidProcStatsByUid();

    EXPECT_CALL(*mMockPackageInfoResolver,
                getPackageInfosForUids(UnorderedElementsAre(1001234, 1005678)))
            .WillOnce(Return(packageInfoByUid));
    EXPECT_CALL(*mMockUidIoStatsCollector, deltaStats()).WillOnce(Return(uidIoStatsByUid));
    EXPECT_CALL(*mMockUidProcStatsCollector, deltaStats()).WillOnce(Return(uidProcStatsByUid));

    ASSERT_RESULT_OK(mUidStatsCollector->collect());

    std::vector<UidStats> expected = sampleUidStats();
    // Since no Uid CPU time was recovered from the /proc/uid_cputime/show_uid_stat file, the
    // collector will default the UID CPU time to the UidProcStats' CPU time.
    expected[0].cpuTimeMillis = 10;

    auto actual = mUidStatsCollector->deltaStats();

    EXPECT_THAT(actual, UnorderedElementsAreArray(UidStatsMatchers(expected)))
            << "Delta UID stats doesn't match.\nExpected: " << toString(expected)
            << "\nActual: " << toString(actual);

    actual = mUidStatsCollector->latestStats();

    EXPECT_THAT(actual, IsEmpty()) << "Latest UID stats isn't empty.\nActual: " << toString(actual);
}

TEST_F(UidStatsCollectorTest, TestCollectDeltaStatsWithMissingPackageInfo) {
    std::unordered_map<uid_t, PackageInfo> packageInfoByUid = samplePackageInfoByUid();
    packageInfoByUid.erase(1001234);
    const std::unordered_map<uid_t, UidIoStats> uidIoStatsByUid = sampleUidIoStatsByUid();
    const std::unordered_map<uid_t, UidProcStats> uidProcStatsByUid = sampleUidProcStatsByUid();
    const std::unordered_map<uid_t, int64_t> uidCpuStatsByUid = sampleUidCpuStatsByUid();

    EXPECT_CALL(*mMockPackageInfoResolver,
                getPackageInfosForUids(UnorderedElementsAre(1001234, 1005678)))
            .WillOnce(Return(packageInfoByUid));
    EXPECT_CALL(*mMockUidIoStatsCollector, deltaStats()).WillOnce(Return(uidIoStatsByUid));
    EXPECT_CALL(*mMockUidProcStatsCollector, deltaStats()).WillOnce(Return(uidProcStatsByUid));
    EXPECT_CALL(*mMockUidCpuStatsCollector, deltaStats()).WillOnce(Return(uidCpuStatsByUid));

    ASSERT_RESULT_OK(mUidStatsCollector->collect());

    std::vector<UidStats> expected = sampleUidStats();
    expected[0].packageInfo = constructPackageInfo("", 1001234);

    auto actual = mUidStatsCollector->deltaStats();

    EXPECT_THAT(actual, UnorderedElementsAreArray(UidStatsMatchers(expected)))
            << "Delta UID stats doesn't match.\nExpected: " << toString(expected)
            << "\nActual: " << toString(actual);

    actual = mUidStatsCollector->latestStats();

    EXPECT_THAT(actual, IsEmpty()) << "Latest UID stats isn't empty.\nActual: " << toString(actual);
}

TEST_F(UidStatsCollectorTest, TestUidStatsHasPackageInfo) {
    std::unordered_map<uid_t, PackageInfo> packageInfoByUid = samplePackageInfoByUid();
    packageInfoByUid.erase(1001234);
    const std::unordered_map<uid_t, UidIoStats> uidIoStatsByUid = sampleUidIoStatsByUid();
    const std::unordered_map<uid_t, UidProcStats> uidProcStatsByUid = sampleUidProcStatsByUid();

    EXPECT_CALL(*mMockPackageInfoResolver,
                getPackageInfosForUids(UnorderedElementsAre(1001234, 1005678)))
            .WillOnce(Return(packageInfoByUid));
    EXPECT_CALL(*mMockUidIoStatsCollector, deltaStats()).WillOnce(Return(uidIoStatsByUid));
    EXPECT_CALL(*mMockUidProcStatsCollector, deltaStats()).WillOnce(Return(uidProcStatsByUid));

    ASSERT_RESULT_OK(mUidStatsCollector->collect());

    const auto actual = mUidStatsCollector->deltaStats();

    EXPECT_EQ(actual.size(), static_cast<size_t>(2));
    for (const auto stats : actual) {
        if (stats.packageInfo.packageIdentifier.uid == 1001234) {
            EXPECT_FALSE(stats.hasPackageInfo())
                    << "Stats without package info should return false";
        } else if (stats.packageInfo.packageIdentifier.uid == 1005678) {
            EXPECT_TRUE(stats.hasPackageInfo()) << "Stats without package info should return true";
        } else {
            FAIL() << "Unexpected uid " << stats.packageInfo.packageIdentifier.uid;
        }
    }
}

TEST_F(UidStatsCollectorTest, TestUidStatsGenericPackageName) {
    std::unordered_map<uid_t, PackageInfo> packageInfoByUid = samplePackageInfoByUid();
    packageInfoByUid.erase(1001234);
    const std::unordered_map<uid_t, UidIoStats> uidIoStatsByUid = sampleUidIoStatsByUid();
    const std::unordered_map<uid_t, UidProcStats> uidProcStatsByUid = sampleUidProcStatsByUid();

    EXPECT_CALL(*mMockPackageInfoResolver,
                getPackageInfosForUids(UnorderedElementsAre(1001234, 1005678)))
            .WillOnce(Return(packageInfoByUid));
    EXPECT_CALL(*mMockUidIoStatsCollector, deltaStats()).WillOnce(Return(uidIoStatsByUid));
    EXPECT_CALL(*mMockUidProcStatsCollector, deltaStats()).WillOnce(Return(uidProcStatsByUid));

    ASSERT_RESULT_OK(mUidStatsCollector->collect());

    const auto actual = mUidStatsCollector->deltaStats();

    EXPECT_EQ(actual.size(), static_cast<size_t>(2));
    for (const auto stats : actual) {
        if (stats.packageInfo.packageIdentifier.uid == 1001234) {
            EXPECT_EQ(stats.genericPackageName(), "1001234")
                    << "Stats without package info should return UID as package name";
        } else if (stats.packageInfo.packageIdentifier.uid == 1005678) {
            EXPECT_EQ(stats.genericPackageName(), "kitchensink.app")
                    << "Stats with package info should return corresponding package name";
        } else {
            FAIL() << "Unexpected uid " << stats.packageInfo.packageIdentifier.uid;
        }
    }
}

TEST_F(UidStatsCollectorTest, TestUidStatsUid) {
    std::unordered_map<uid_t, PackageInfo> packageInfoByUid = samplePackageInfoByUid();
    packageInfoByUid.erase(1001234);
    const std::unordered_map<uid_t, UidIoStats> uidIoStatsByUid = sampleUidIoStatsByUid();
    const std::unordered_map<uid_t, UidProcStats> uidProcStatsByUid = sampleUidProcStatsByUid();

    EXPECT_CALL(*mMockPackageInfoResolver,
                getPackageInfosForUids(UnorderedElementsAre(1001234, 1005678)))
            .WillOnce(Return(packageInfoByUid));
    EXPECT_CALL(*mMockUidIoStatsCollector, deltaStats()).WillOnce(Return(uidIoStatsByUid));
    EXPECT_CALL(*mMockUidProcStatsCollector, deltaStats()).WillOnce(Return(uidProcStatsByUid));

    ASSERT_RESULT_OK(mUidStatsCollector->collect());

    const auto actual = mUidStatsCollector->deltaStats();

    for (const auto stats : actual) {
        EXPECT_EQ(stats.uid(), static_cast<uid_t>(stats.packageInfo.packageIdentifier.uid));
    }
}

}  // namespace watchdog
}  // namespace automotive
}  // namespace android
