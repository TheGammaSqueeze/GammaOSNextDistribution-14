// Copyright (C) 2017 The Android Open Source Project
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

#include "packages/UidMap.h"

#include <android/util/ProtoOutputStream.h>
#include <gtest/gtest.h>
#include <src/uid_data.pb.h>
#include <stdio.h>

#include "StatsLogProcessor.h"
#include "StatsService.h"
#include "config/ConfigKey.h"
#include "gtest_matchers.h"
#include "guardrail/StatsdStats.h"
#include "hash.h"
#include "logd/LogEvent.h"
#include "statsd_test_util.h"
#include "statslog_statsdtest.h"

using namespace android;

namespace android {
namespace os {
namespace statsd {

using android::util::ProtoOutputStream;
using android::util::ProtoReader;
using ::ndk::SharedRefBase;

#ifdef __ANDROID__

namespace {
const string kApp1 = "app1.sharing.1";
const string kApp2 = "app2.sharing.1";
const string kApp3 = "app3";

const vector<int32_t> kUids{1000, 1000, 1500};
const vector<int64_t> kVersions{4, 5, 6};
const vector<string> kVersionStrings{"v1", "v1", "v2"};
const vector<string> kApps{kApp1, kApp2, kApp3};
const vector<string> kInstallers{"", "", "com.android.vending"};
const vector<vector<uint8_t>> kCertificateHashes{{'a', 'z'}, {'b', 'c'}, {'d', 'e'}};
const vector<uint8_t> kDeleted(3, false);

void sendPackagesToStatsd(shared_ptr<StatsService> service, const vector<int32_t>& uids,
                          const vector<int64_t>& versions, const vector<string>& versionStrings,
                          const vector<string>& apps, const vector<string>& installers,
                          const vector<vector<uint8_t>>& certificateHashes) {
    // Populate UidData from app data.
    UidData uidData;
    for (size_t i = 0; i < uids.size(); i++) {
        ApplicationInfo* appInfo = uidData.add_app_info();
        appInfo->set_uid(uids[i]);
        appInfo->set_version(versions[i]);
        appInfo->set_version_string(versionStrings[i]);
        appInfo->set_package_name(apps[i]);
        appInfo->set_installer(installers[i]);
        appInfo->set_certificate_hash(certificateHashes[i].data(), certificateHashes[i].size());
    }

    // Create file descriptor from serialized UidData.
    // Create a file that lives in memory.
    ScopedFileDescriptor scopedFd(memfd_create("doesn't matter", MFD_CLOEXEC));
    const int fd = scopedFd.get();
    int f = fcntl(fd, F_GETFD);  // Read the file descriptor flags.
    ASSERT_NE(-1, f);            // Ensure there was no error while reading file descriptor flags.
    ASSERT_TRUE(f & FD_CLOEXEC);
    ASSERT_TRUE(uidData.SerializeToFileDescriptor(fd));
    ASSERT_EQ(0, lseek(fd, 0, SEEK_SET));

    // Send file descriptor containing app data to statsd.
    service->informAllUidData(scopedFd);
}

// Returns a vector of the same length as values param. Each i-th element in the returned vector is
// the index at which values[i] appears in the list denoted by the begin and end iterators.
template <typename Iterator, typename ValueType>
vector<uint32_t> computeIndices(const Iterator begin, const Iterator end,
                                const vector<ValueType>& values) {
    vector<uint32_t> indices;
    for (const ValueType& value : values) {
        Iterator it = find(begin, end, value);
        indices.emplace_back(distance(begin, it));
    }
    return indices;
}

}  // anonymous namespace

TEST(UidMapTest, TestIsolatedUID) {
    sp<UidMap> m = new UidMap();
    sp<StatsPullerManager> pullerManager = new StatsPullerManager();
    sp<AlarmMonitor> anomalyAlarmMonitor;
    sp<AlarmMonitor> subscriberAlarmMonitor;
    // Construct the processor with a no-op sendBroadcast function that does nothing.
    StatsLogProcessor p(
            m, pullerManager, anomalyAlarmMonitor, subscriberAlarmMonitor, 0,
            [](const ConfigKey& key) { return true; },
            [](const int&, const vector<int64_t>&) { return true; },
            [](const ConfigKey&, const string&, const vector<int64_t>&) {},
            std::make_shared<LogEventFilter>());

    std::unique_ptr<LogEvent> addEvent = CreateIsolatedUidChangedEvent(
            1 /*timestamp*/, 100 /*hostUid*/, 101 /*isolatedUid*/, 1 /*is_create*/);
    EXPECT_EQ(101, m->getHostUidOrSelf(101));
    p.OnLogEvent(addEvent.get());
    EXPECT_EQ(100, m->getHostUidOrSelf(101));

    std::unique_ptr<LogEvent> removeEvent = CreateIsolatedUidChangedEvent(
            1 /*timestamp*/, 100 /*hostUid*/, 101 /*isolatedUid*/, 0 /*is_create*/);
    p.OnLogEvent(removeEvent.get());
    EXPECT_EQ(101, m->getHostUidOrSelf(101));
}

TEST(UidMapTest, TestUpdateMap) {
    const sp<UidMap> uidMap = new UidMap();
    const shared_ptr<StatsService> service = SharedRefBase::make<StatsService>(
            uidMap, /* queue */ nullptr, std::make_shared<LogEventFilter>());
    sendPackagesToStatsd(service, kUids, kVersions, kVersionStrings, kApps, kInstallers,
                         kCertificateHashes);

    EXPECT_TRUE(uidMap->hasApp(1000, kApp1));
    EXPECT_TRUE(uidMap->hasApp(1000, kApp2));
    EXPECT_TRUE(uidMap->hasApp(1500, kApp3));
    EXPECT_FALSE(uidMap->hasApp(1000, "not.app"));

    std::set<string> name_set = uidMap->getAppNamesFromUid(1000u, true /* returnNormalized */);
    EXPECT_THAT(name_set, UnorderedElementsAre(kApp1, kApp2));

    name_set = uidMap->getAppNamesFromUid(1500u, true /* returnNormalized */);
    EXPECT_THAT(name_set, UnorderedElementsAre(kApp3));

    name_set = uidMap->getAppNamesFromUid(12345, true /* returnNormalized */);
    EXPECT_THAT(name_set, IsEmpty());

    vector<PackageInfo> expectedPackageInfos =
            buildPackageInfos(kApps, kUids, kVersions, kVersionStrings, kInstallers,
                              kCertificateHashes, kDeleted, /* installerIndices */ {},
                              /* hashStrings */ false);

    PackageInfoSnapshot packageInfoSnapshot = getPackageInfoSnapshot(uidMap);

    EXPECT_THAT(packageInfoSnapshot.package_info(),
                UnorderedPointwise(EqPackageInfo(), expectedPackageInfos));
}

TEST(UidMapTest, TestUpdateMapMultiple) {
    const sp<UidMap> uidMap = new UidMap();
    const shared_ptr<StatsService> service = SharedRefBase::make<StatsService>(
            uidMap, /* queue */ nullptr, std::make_shared<LogEventFilter>());
    sendPackagesToStatsd(service, kUids, kVersions, kVersionStrings, kApps, kInstallers,
                         kCertificateHashes);

    // Remove kApp3, and add NewApp
    vector<int32_t> uids(kUids);
    uids.back() = 2000;
    vector<string> apps(kApps);
    apps.back() = "NewApp";
    vector<string> installers(kInstallers);
    installers.back() = "NewInstaller";

    sendPackagesToStatsd(service, uids, kVersions, kVersionStrings, apps, installers,
                         kCertificateHashes);

    EXPECT_TRUE(uidMap->hasApp(1000, kApp1));
    EXPECT_TRUE(uidMap->hasApp(1000, kApp2));
    EXPECT_TRUE(uidMap->hasApp(2000, "NewApp"));
    EXPECT_FALSE(uidMap->hasApp(1500, kApp3));
    EXPECT_FALSE(uidMap->hasApp(1000, "not.app"));

    std::set<string> name_set = uidMap->getAppNamesFromUid(1000u, true /* returnNormalized */);
    EXPECT_THAT(name_set, UnorderedElementsAre(kApp1, kApp2));

    name_set = uidMap->getAppNamesFromUid(2000, true /* returnNormalized */);
    EXPECT_THAT(name_set, UnorderedElementsAre("newapp"));

    name_set = uidMap->getAppNamesFromUid(1500, true /* returnNormalized */);
    EXPECT_THAT(name_set, IsEmpty());

    vector<PackageInfo> expectedPackageInfos =
            buildPackageInfos(apps, uids, kVersions, kVersionStrings, installers,
                              kCertificateHashes, kDeleted, /* installerIndices */ {},
                              /* hashStrings */ false);

    PackageInfoSnapshot packageInfoSnapshot = getPackageInfoSnapshot(uidMap);

    EXPECT_THAT(packageInfoSnapshot.package_info(),
                UnorderedPointwise(EqPackageInfo(), expectedPackageInfos));
}

TEST(UidMapTest, TestRemoveApp) {
    const sp<UidMap> uidMap = new UidMap();
    const shared_ptr<StatsService> service = SharedRefBase::make<StatsService>(
            uidMap, /* queue */ nullptr, std::make_shared<LogEventFilter>());
    sendPackagesToStatsd(service, kUids, kVersions, kVersionStrings, kApps, kInstallers,
                         kCertificateHashes);

    service->informOnePackageRemoved(kApp1, 1000);
    EXPECT_FALSE(uidMap->hasApp(1000, kApp1));
    EXPECT_TRUE(uidMap->hasApp(1000, kApp2));
    EXPECT_TRUE(uidMap->hasApp(1500, kApp3));
    std::set<string> name_set = uidMap->getAppNamesFromUid(1000, true /* returnNormalized */);
    EXPECT_THAT(name_set, UnorderedElementsAre(kApp2));

    vector<uint8_t> deleted(kDeleted);
    deleted[0] = true;
    vector<PackageInfo> expectedPackageInfos =
            buildPackageInfos(kApps, kUids, kVersions, kVersionStrings, kInstallers,
                              kCertificateHashes, deleted, /* installerIndices */ {},
                              /* hashStrings */ false);
    PackageInfoSnapshot packageInfoSnapshot = getPackageInfoSnapshot(uidMap);
    EXPECT_THAT(packageInfoSnapshot.package_info(),
                UnorderedPointwise(EqPackageInfo(), expectedPackageInfos));

    service->informOnePackageRemoved(kApp2, 1000);
    EXPECT_FALSE(uidMap->hasApp(1000, kApp1));
    EXPECT_FALSE(uidMap->hasApp(1000, kApp2));
    EXPECT_TRUE(uidMap->hasApp(1500, kApp3));
    EXPECT_FALSE(uidMap->hasApp(1000, "not.app"));
    name_set = uidMap->getAppNamesFromUid(1000, true /* returnNormalized */);
    EXPECT_THAT(name_set, IsEmpty());

    deleted[1] = true;
    expectedPackageInfos = buildPackageInfos(kApps, kUids, kVersions, kVersionStrings, kInstallers,
                                             kCertificateHashes, deleted, /* installerIndices */ {},
                                             /* hashStrings */ false);
    packageInfoSnapshot = getPackageInfoSnapshot(uidMap);
    EXPECT_THAT(packageInfoSnapshot.package_info(),
                UnorderedPointwise(EqPackageInfo(), expectedPackageInfos));

    service->informOnePackageRemoved(kApp3, 1500);
    EXPECT_FALSE(uidMap->hasApp(1000, kApp1));
    EXPECT_FALSE(uidMap->hasApp(1000, kApp2));
    EXPECT_FALSE(uidMap->hasApp(1500, kApp3));
    EXPECT_FALSE(uidMap->hasApp(1000, "not.app"));
    name_set = uidMap->getAppNamesFromUid(1500, true /* returnNormalized */);
    EXPECT_THAT(name_set, IsEmpty());

    deleted[2] = true;
    expectedPackageInfos = buildPackageInfos(kApps, kUids, kVersions, kVersionStrings, kInstallers,
                                             kCertificateHashes, deleted, /* installerIndices */ {},
                                             /* hashStrings */ false);
    packageInfoSnapshot = getPackageInfoSnapshot(uidMap);
    EXPECT_THAT(packageInfoSnapshot.package_info(),
                UnorderedPointwise(EqPackageInfo(), expectedPackageInfos));
}

TEST(UidMapTest, TestUpdateApp) {
    const sp<UidMap> uidMap = new UidMap();
    const shared_ptr<StatsService> service = SharedRefBase::make<StatsService>(
            uidMap, /* queue */ nullptr, std::make_shared<LogEventFilter>());
    sendPackagesToStatsd(service, kUids, kVersions, kVersionStrings, kApps, kInstallers,
                         kCertificateHashes);

    // Update app1 version.
    service->informOnePackage(kApps[0].c_str(), kUids[0], /* version */ 40,
                              /* versionString */ "v40", kInstallers[0], kCertificateHashes[0]);
    EXPECT_THAT(uidMap->getAppVersion(kUids[0], kApps[0]), Eq(40));
    std::set<string> name_set = uidMap->getAppNamesFromUid(1000, true /* returnNormalized */);
    EXPECT_THAT(name_set, UnorderedElementsAre(kApp1, kApp2));

    // Add a new name for uid 1000.
    service->informOnePackage("NeW_aPP1_NAmE", 1000, /* version */ 40,
                              /* versionString */ "v40", /* installer */ "com.android.vending",
                              /* certificateHash */ {'a'});
    name_set = uidMap->getAppNamesFromUid(1000, true /* returnNormalized */);
    EXPECT_THAT(name_set, UnorderedElementsAre(kApp1, kApp2, "new_app1_name"));

    // Re-add the same name for another uid 2000
    service->informOnePackage("NeW_aPP1_NAmE", 2000, /* version */ 1,
                              /* versionString */ "v1", /* installer */ "",
                              /* certificateHash */ {'b'});
    name_set = uidMap->getAppNamesFromUid(2000, true /* returnNormalized */);
    EXPECT_THAT(name_set, UnorderedElementsAre("new_app1_name"));

    // Re-add existing package with different installer
    service->informOnePackage("NeW_aPP1_NAmE", 2000, /* version */ 1,
                              /* versionString */ "v1", /* installer */ "new_installer",
                              /* certificateHash */ {'b'});
    name_set = uidMap->getAppNamesFromUid(2000, true /* returnNormalized */);
    EXPECT_THAT(name_set, UnorderedElementsAre("new_app1_name"));

    vector<int32_t> uids = concatenate(kUids, {1000, 2000});
    vector<int64_t> versions = concatenate(kVersions, {40, 1});
    versions[0] = 40;
    vector<string> versionStrings = concatenate(kVersionStrings, {"v40", "v1"});
    versionStrings[0] = "v40";
    vector<string> apps = concatenate(kApps, {"NeW_aPP1_NAmE", "NeW_aPP1_NAmE"});
    vector<string> installers = concatenate(kInstallers, {"com.android.vending", "new_installer"});
    vector<uint8_t> deleted = concatenate(kDeleted, {false, false});
    vector<vector<uint8_t>> certHashes = concatenate(kCertificateHashes, {{'a'}, {'b'}});
    vector<PackageInfo> expectedPackageInfos =
            buildPackageInfos(apps, uids, versions, versionStrings, installers, certHashes, deleted,
                              /* installerIndices */ {},
                              /* hashStrings */ false);

    PackageInfoSnapshot packageInfoSnapshot = getPackageInfoSnapshot(uidMap);
    EXPECT_THAT(packageInfoSnapshot.package_info(),
                UnorderedPointwise(EqPackageInfo(), expectedPackageInfos));
}

// Test that uid map returns at least one snapshot even if we already obtained
// this snapshot from a previous call to getData.
TEST(UidMapTest, TestOutputIncludesAtLeastOneSnapshot) {
    UidMap m;
    // Initialize single config key.
    ConfigKey config1(1, StringToId("config1"));
    m.OnConfigUpdated(config1);

    UidData uidData;
    *uidData.add_app_info() = createApplicationInfo(/*uid*/ 1000, /*version*/ 5, "v1", kApp2);

    m.updateMap(1 /* timestamp */, uidData);

    // Set the last timestamp for this config key to be newer.
    m.mLastUpdatePerConfigKey[config1] = 2;

    ProtoOutputStream proto;
    m.appendUidMap(/* timestamp */ 3, config1, /* includeVersionStrings */ true,
                   /* includeInstaller */ true, /* truncatedCertificateHashSize */ 0,
                   /* str_set */ nullptr, &proto);

    // Check there's still a uidmap attached this one.
    UidMapping results;
    outputStreamToProto(&proto, &results);
    ASSERT_EQ(1, results.snapshots_size());
    EXPECT_EQ("v1", results.snapshots(0).package_info(0).version_string());
}

TEST(UidMapTest, TestRemovedAppRetained) {
    UidMap m;
    // Initialize single config key.
    ConfigKey config1(1, StringToId("config1"));
    m.OnConfigUpdated(config1);

    UidData uidData;
    *uidData.add_app_info() = createApplicationInfo(/*uid*/ 1000, /*version*/ 5, "v5", kApp2);

    m.updateMap(1 /* timestamp */, uidData);
    m.removeApp(2, kApp2, 1000);

    ProtoOutputStream proto;
    m.appendUidMap(/* timestamp */ 3, config1, /* includeVersionStrings */ true,
                   /* includeInstaller */ true, /* truncatedCertificateHashSize */ 0,
                   /* str_set */ nullptr, &proto);

    // Snapshot should still contain this item as deleted.
    UidMapping results;
    outputStreamToProto(&proto, &results);
    ASSERT_EQ(1, results.snapshots(0).package_info_size());
    EXPECT_EQ(true, results.snapshots(0).package_info(0).deleted());
}

TEST(UidMapTest, TestRemovedAppOverGuardrail) {
    UidMap m;
    // Initialize single config key.
    ConfigKey config1(1, StringToId("config1"));
    m.OnConfigUpdated(config1);
    const int maxDeletedApps = StatsdStats::kMaxDeletedAppsInUidMap;

    UidData uidData;
    for (int j = 0; j < maxDeletedApps + 10; j++) {
        *uidData.add_app_info() = createApplicationInfo(/*uid*/ j, /*version*/ j, "v", kApp1);
    }
    m.updateMap(1 /* timestamp */, uidData);

    // First, verify that we have the expected number of items.
    UidMapping results;
    ProtoOutputStream proto;
    m.appendUidMap(/* timestamp */ 3, config1, /* includeVersionStrings */ true,
                   /* includeInstaller */ true, /* truncatedCertificateHashSize */ 0,
                   /* str_set */ nullptr, &proto);
    outputStreamToProto(&proto, &results);
    ASSERT_EQ(maxDeletedApps + 10, results.snapshots(0).package_info_size());

    // Now remove all the apps.
    m.updateMap(1 /* timestamp */, uidData);
    for (int j = 0; j < maxDeletedApps + 10; j++) {
        m.removeApp(4, kApp1, j);
    }

    proto.clear();
    m.appendUidMap(/* timestamp */ 5, config1, /* includeVersionStrings */ true,
                   /* includeInstaller */ true, /* truncatedCertificateHashSize */ 0,
                   /* str_set */ nullptr, &proto);
    // Snapshot drops the first nine items.
    outputStreamToProto(&proto, &results);
    ASSERT_EQ(maxDeletedApps, results.snapshots(0).package_info_size());
}

TEST(UidMapTest, TestClearingOutput) {
    UidMap m;

    ConfigKey config1(1, StringToId("config1"));
    ConfigKey config2(1, StringToId("config2"));

    m.OnConfigUpdated(config1);

    UidData uidData;
    *uidData.add_app_info() = createApplicationInfo(/*uid*/ 1000, /*version*/ 4, "v4", kApp1);
    *uidData.add_app_info() = createApplicationInfo(/*uid*/ 1000, /*version*/ 5, "v5", kApp2);
    m.updateMap(1 /* timestamp */, uidData);

    ProtoOutputStream proto;
    m.appendUidMap(/* timestamp */ 2, config1, /* includeVersionStrings */ true,
                   /* includeInstaller */ true, /* truncatedCertificateHashSize */ 0,
                   /* str_set */ nullptr, &proto);
    UidMapping results;
    outputStreamToProto(&proto, &results);
    ASSERT_EQ(1, results.snapshots_size());

    // We have to keep at least one snapshot in memory at all times.
    proto.clear();
    m.appendUidMap(/* timestamp */ 2, config1, /* includeVersionStrings */ true,
                   /* includeInstaller */ true, /* truncatedCertificateHashSize */ 0,
                   /* str_set */ nullptr, &proto);
    outputStreamToProto(&proto, &results);
    ASSERT_EQ(1, results.snapshots_size());

    // Now add another configuration.
    m.OnConfigUpdated(config2);
    m.updateApp(5, kApp1, 1000, 40, "v40", "", /* certificateHash */ {});
    ASSERT_EQ(1U, m.mChanges.size());
    proto.clear();
    m.appendUidMap(/* timestamp */ 6, config1, /* includeVersionStrings */ true,
                   /* includeInstaller */ true, /* truncatedCertificateHashSize */ 0,
                   /* str_set */ nullptr, &proto);
    outputStreamToProto(&proto, &results);
    ASSERT_EQ(1, results.snapshots_size());
    ASSERT_EQ(1, results.changes_size());
    ASSERT_EQ(1U, m.mChanges.size());

    // Add another delta update.
    m.updateApp(7, kApp2, 1001, 41, "v41", "", /* certificateHash */ {});
    ASSERT_EQ(2U, m.mChanges.size());

    // We still can't remove anything.
    proto.clear();
    m.appendUidMap(/* timestamp */ 8, config1, /* includeVersionStrings */ true,
                   /* includeInstaller */ true, /* truncatedCertificateHashSize */ 0,
                   /* str_set */ nullptr, &proto);
    outputStreamToProto(&proto, &results);
    ASSERT_EQ(1, results.snapshots_size());
    ASSERT_EQ(1, results.changes_size());
    ASSERT_EQ(2U, m.mChanges.size());

    proto.clear();
    m.appendUidMap(/* timestamp */ 9, config2, /* includeVersionStrings */ true,
                   /* includeInstaller */ true, /* truncatedCertificateHashSize */ 0,
                   /* str_set */ nullptr, &proto);
    outputStreamToProto(&proto, &results);
    ASSERT_EQ(1, results.snapshots_size());
    ASSERT_EQ(2, results.changes_size());
    // At this point both should be cleared.
    ASSERT_EQ(0U, m.mChanges.size());
}

TEST(UidMapTest, TestMemoryComputed) {
    UidMap m;

    ConfigKey config1(1, StringToId("config1"));
    m.OnConfigUpdated(config1);

    size_t startBytes = m.mBytesUsed;
    UidData uidData;
    *uidData.add_app_info() = createApplicationInfo(/*uid*/ 1000, /*version*/ 1, "v1", kApp1);
    m.updateMap(1 /* timestamp */, uidData);

    m.updateApp(3, kApp1, 1000, 40, "v40", "", /* certificateHash */ {});

    ProtoOutputStream proto;
    m.appendUidMap(/* timestamp */ 2, config1, /* includeVersionStrings */ true,
                   /* includeInstaller */ true, /* truncatedCertificateHashSize */ 0,
                   /* str_set */ nullptr, &proto);
    size_t prevBytes = m.mBytesUsed;

    m.appendUidMap(/* timestamp */ 4, config1, /* includeVersionStrings */ true,
                   /* includeInstaller */ true, /* truncatedCertificateHashSize */ 0,
                   /* str_set */ nullptr, &proto);
    EXPECT_TRUE(m.mBytesUsed < prevBytes);
}

TEST(UidMapTest, TestMemoryGuardrail) {
    UidMap m;
    string buf;

    ConfigKey config1(1, StringToId("config1"));
    m.OnConfigUpdated(config1);

    size_t startBytes = m.mBytesUsed;
    UidData uidData;
    for (int i = 0; i < 100; i++) {
        buf = "EXTREMELY_LONG_STRING_FOR_APP_TO_WASTE_MEMORY." + to_string(i);
        *uidData.add_app_info() = createApplicationInfo(/*uid*/ 1, /*version*/ 1, "v1", buf);
    }
    m.updateMap(1 /* timestamp */, uidData);

    m.updateApp(3, "EXTREMELY_LONG_STRING_FOR_APP_TO_WASTE_MEMORY.0", 1000, 2, "v2", "",
                /* certificateHash */ {});
    ASSERT_EQ(1U, m.mChanges.size());

    // Now force deletion by limiting the memory to hold one delta change.
    m.maxBytesOverride = 120; // Since the app string alone requires >45 characters.
    m.updateApp(5, "EXTREMELY_LONG_STRING_FOR_APP_TO_WASTE_MEMORY.0", 1000, 4, "v4", "",
                /* certificateHash */ {});
    ASSERT_EQ(1U, m.mChanges.size());
}

class UidMapTestAppendUidMap : public Test {
protected:
    const ConfigKey config1;
    const sp<UidMap> uidMap;
    const shared_ptr<StatsService> service;

    set<string> installersSet;
    set<uint64_t> installerHashSet;
    vector<uint64_t> installerHashes;

    UidMapTestAppendUidMap()
        : config1(1, StringToId("config1")),
          uidMap(new UidMap()),
          service(SharedRefBase::make<StatsService>(uidMap, /* queue */ nullptr,
                                                    std::make_shared<LogEventFilter>())) {
    }

    void SetUp() override {
        sendPackagesToStatsd(service, kUids, kVersions, kVersionStrings, kApps, kInstallers,
                             kCertificateHashes);

        for (const string& installer : kInstallers) {
            installersSet.insert(installer);
            uint64_t installerHash = Hash64(installer);
            installerHashes.emplace_back(installerHash);
            installerHashSet.insert(installerHash);
        }
    }
};

TEST_F(UidMapTestAppendUidMap, TestInstallersInReportIncludeInstallerAndHashStrings) {
    ProtoOutputStream proto;
    set<string> strSet;
    uidMap->appendUidMap(/* timestamp */ 3, config1, /* includeVersionStrings */ true,
                         /* includeInstaller */ true, /* truncatedCertificateHashSize */ 0, &strSet,
                         &proto);

    UidMapping results;
    outputStreamToProto(&proto, &results);

    // Verify hashes for all installers are in the installer_hash list.
    EXPECT_THAT(results.installer_hash(), UnorderedElementsAreArray(installerHashSet));

    EXPECT_THAT(results.installer_name(), IsEmpty());

    // Verify all installer names are added to the strSet argument.
    EXPECT_THAT(strSet, IsSupersetOf(installersSet));

    ASSERT_THAT(results.snapshots_size(), Eq(1));

    // Compute installer indices for each package.
    // Find the location of each installerHash from the input in the results.
    // installerIndices[i] is the index in results.installer_hash() that matches installerHashes[i].
    vector<uint32_t> installerIndices = computeIndices(
            results.installer_hash().begin(), results.installer_hash().end(), installerHashes);

    vector<PackageInfo> expectedPackageInfos =
            buildPackageInfos(kApps, kUids, kVersions, kVersionStrings, kInstallers,
                              /* certHashes */ {}, kDeleted, installerIndices,
                              /* hashStrings */ true);

    EXPECT_THAT(strSet, IsSupersetOf(kApps));

    EXPECT_THAT(results.snapshots(0).package_info(),
                UnorderedPointwise(EqPackageInfo(), expectedPackageInfos));
}

TEST_F(UidMapTestAppendUidMap, TestInstallersInReportIncludeInstallerAndDontHashStrings) {
    ProtoOutputStream proto;
    uidMap->appendUidMap(/* timestamp */ 3, config1, /* includeVersionStrings */ true,
                         /* includeInstaller */ true, /* truncatedCertificateHashSize */ 0,
                         /* str_set */ nullptr, &proto);

    UidMapping results;
    outputStreamToProto(&proto, &results);

    // Verify all installers are in the installer_name list.
    EXPECT_THAT(results.installer_name(), UnorderedElementsAreArray(installersSet));

    EXPECT_THAT(results.installer_hash(), IsEmpty());

    ASSERT_THAT(results.snapshots_size(), Eq(1));

    vector<uint32_t> installerIndices = computeIndices(results.installer_name().begin(),
                                                       results.installer_name().end(), kInstallers);

    vector<PackageInfo> expectedPackageInfos =
            buildPackageInfos(kApps, kUids, kVersions, kVersionStrings, kInstallers,
                              /* certHashes */ {}, kDeleted, installerIndices,
                              /* hashStrings */ false);

    EXPECT_THAT(results.snapshots(0).package_info(),
                UnorderedPointwise(EqPackageInfo(), expectedPackageInfos));
}

// Set up parameterized test with set<string>* parameter to control whether strings are hashed
// or not in the report. A value of nullptr indicates strings should not be hashed and non-null
// values indicates strings are hashed in the report and the original strings are added to this set.
class UidMapTestAppendUidMapHashStrings : public UidMapTestAppendUidMap,
                                          public WithParamInterface<set<string>*> {
public:
    inline static set<string> strSet;

protected:
    void SetUp() override {
        strSet.clear();
    }
};

INSTANTIATE_TEST_SUITE_P(
        HashStrings, UidMapTestAppendUidMapHashStrings,
        Values(nullptr, &(UidMapTestAppendUidMapHashStrings::strSet)),
        [](const TestParamInfo<UidMapTestAppendUidMapHashStrings::ParamType>& info) {
            return info.param == nullptr ? "NoHashStrings" : "HashStrings";
        });

TEST_P(UidMapTestAppendUidMapHashStrings, TestNoIncludeInstallersInReport) {
    ProtoOutputStream proto;
    uidMap->appendUidMap(/* timestamp */ 3, config1, /* includeVersionStrings */ true,
                         /* includeInstaller */ false, /* truncatedCertificateHashSize */ 0,
                         /* str_set */ GetParam(), &proto);

    UidMapping results;
    outputStreamToProto(&proto, &results);

    // Verify installer lists are empty.
    EXPECT_THAT(results.installer_name(), IsEmpty());
    EXPECT_THAT(results.installer_hash(), IsEmpty());

    ASSERT_THAT(results.snapshots_size(), Eq(1));

    // Verify that none of installer, installer_hash, installer_index fields in PackageInfo are
    // populated.
    EXPECT_THAT(results.snapshots(0).package_info(),
                Each(Property(&PackageInfo::has_installer, IsFalse())));
    EXPECT_THAT(results.snapshots(0).package_info(),
                Each(Property(&PackageInfo::has_installer_hash, IsFalse())));
    EXPECT_THAT(results.snapshots(0).package_info(),
                Each(Property(&PackageInfo::has_installer_index, IsFalse())));
}

// Set up parameterized test for testing with different truncation hash sizes for the certificates.
class UidMapTestTruncateCertificateHash : public UidMapTestAppendUidMap,
                                          public WithParamInterface<uint8_t> {};

INSTANTIATE_TEST_SUITE_P(ZeroOneTwoThree, UidMapTestTruncateCertificateHash,
                         Range(uint8_t{0}, uint8_t{4}));

TEST_P(UidMapTestTruncateCertificateHash, TestCertificateHashesTruncated) {
    const uint8_t hashSize = GetParam();
    ProtoOutputStream proto;
    uidMap->appendUidMap(/* timestamp */ 3, config1, /* includeVersionStrings */ true,
                         /* includeInstaller */ false, hashSize, /* str_set */ nullptr, &proto);

    UidMapping results;
    outputStreamToProto(&proto, &results);

    ASSERT_THAT(results.snapshots_size(), Eq(1));

    vector<vector<uint8_t>> certHashes = kCertificateHashes;
    for (vector<uint8_t>& certHash : certHashes) {
        certHash.resize(certHash.size() < hashSize ? certHash.size() : hashSize);
    }
    vector<PackageInfo> expectedPackageInfos =
            buildPackageInfos(kApps, kUids, kVersions, kVersionStrings,
                              /* installers */ {}, certHashes, kDeleted,
                              /* installerIndices*/ {},
                              /* hashStrings */ false);

    EXPECT_THAT(results.snapshots(0).package_info(),
                UnorderedPointwise(EqPackageInfo(), expectedPackageInfos));
}

#else
GTEST_LOG_(INFO) << "This test does nothing.\n";
#endif

}  // namespace statsd
}  // namespace os
}  // namespace android
