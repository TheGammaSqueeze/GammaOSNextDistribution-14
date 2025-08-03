// Copyright (C) 2022 The Android Open Source Project
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

#include "statsd_test_util.h"

#include "gtest_matchers.h"
#include "src/stats_log.pb.h"

namespace android {
namespace os {
namespace statsd {

#ifdef __ANDROID__

namespace {
const string appName = "app1";
const int32_t uid = 1000;
const int64_t version = 1;
const string versionString = "v1";
const string installer = "com.android.vending";
}  // anonymous namespace

TEST(StatsdTestUtil_PackageInfo, TestBuildPackageInfo) {
    PackageInfo packageInfo = buildPackageInfo(appName, uid, version, versionString,
                                               /* installer */ nullopt, /* certHash */ {},
                                               /* deleted */ false, /* hashStrings */ false,
                                               /* installerIndex */ nullopt);

    EXPECT_THAT(packageInfo.version(), Eq(version));
    EXPECT_THAT(packageInfo.uid(), Eq(uid));
    EXPECT_THAT(packageInfo.deleted(), Eq(false));
}

// Set up parameterized test for testing with different values for pacakage certificate hashes.
class StatsdTestUtil_PackageInfo_CertificateHash : public TestWithParam<vector<uint8_t>> {};
INSTANTIATE_TEST_SUITE_P(
        CertificateHashSizes, StatsdTestUtil_PackageInfo_CertificateHash,
        Values(vector<uint8_t>(), vector<uint8_t>{'a'}, vector<uint8_t>{'a', 'b'}),
        [](const TestParamInfo<StatsdTestUtil_PackageInfo_CertificateHash::ParamType>& info) {
            if (info.param.empty()) {
                return string("empty");
            }
            return string(info.param.begin(), info.param.end());
        });
TEST_P(StatsdTestUtil_PackageInfo_CertificateHash, TestBuildPackageInfoCertificateHash) {
    const vector<uint8_t>& certHash = GetParam();
    PackageInfo packageInfo = buildPackageInfo(appName, uid, version, versionString,
                                               /* installer */ nullopt, certHash,
                                               /* deleted */ false, /* hashStrings */ false,
                                               /* installerIndex */ nullopt);

    EXPECT_THAT(packageInfo.has_truncated_certificate_hash(), !certHash.empty());
    string expectedCertHash(certHash.begin(), certHash.end());
    EXPECT_THAT(packageInfo.truncated_certificate_hash(), Eq(expectedCertHash));
}

TEST(StatsdTestUtil_PackageInfo, TestBuildPackageInfoHashStrings) {
    PackageInfo packageInfo = buildPackageInfo(appName, uid, version, versionString,
                                               /* installer */ nullopt, /* certHash */ {},
                                               /* deleted */ false, /* hashStrings */ true,
                                               /* installerIndex */ nullopt);

    EXPECT_TRUE(packageInfo.has_name_hash());
    EXPECT_THAT(packageInfo.name_hash(), Eq(Hash64(appName)));
    EXPECT_FALSE(packageInfo.has_name());

    EXPECT_TRUE(packageInfo.has_version_string_hash());
    EXPECT_THAT(packageInfo.version_string_hash(), Eq(Hash64(versionString)));
    EXPECT_FALSE(packageInfo.has_version_string());
}

TEST(StatsdTestUtil_PackageInfo, TestBuildPackageInfoNoHashStrings) {
    PackageInfo packageInfo = buildPackageInfo(appName, uid, version, versionString,
                                               /* installer */ nullopt, /* certHash */ {},
                                               /* deleted */ false, /* hashStrings */ false,
                                               /* installerIndex */ nullopt);

    EXPECT_TRUE(packageInfo.has_name());
    EXPECT_THAT(packageInfo.name(), Eq(appName));
    EXPECT_FALSE(packageInfo.has_name_hash());

    EXPECT_TRUE(packageInfo.has_version_string());
    EXPECT_THAT(packageInfo.version_string(), Eq(versionString));
    EXPECT_FALSE(packageInfo.has_version_string_hash());
}

// Test with multiple permutations of installerIndex(uint32_t) and hashString(bool) tuples.
class StatsdTestUtil_PackageInfo_InstallerIndexAndHashStrings
    : public TestWithParam<tuple<optional<uint32_t>, bool>> {};
INSTANTIATE_TEST_SUITE_P(InstallerIndexAndHashStrings,
                         StatsdTestUtil_PackageInfo_InstallerIndexAndHashStrings,
                         Combine(Values(optional(2), nullopt), Bool()));
TEST_P(StatsdTestUtil_PackageInfo_InstallerIndexAndHashStrings, TestBuildPackageInfoNoInstaller) {
    const auto& [installerIndex, hashStrings] = GetParam();

    PackageInfo packageInfo = buildPackageInfo(appName, uid, version, versionString,
                                               /* installer */ nullopt, /* certHash */ {},
                                               /* deleted */ false, hashStrings, installerIndex);

    EXPECT_FALSE(packageInfo.has_installer_index());
    EXPECT_FALSE(packageInfo.has_installer_hash());
    EXPECT_FALSE(packageInfo.has_installer());
}

// Set up parameterized test for testing with different boolean values for hashStrings parameter
// in buildPackageInfo()/buildPackageInfos()
class StatsdTestUtil_PackageInfo_HashStrings : public TestWithParam<bool> {};
INSTANTIATE_TEST_SUITE_P(HashStrings, StatsdTestUtil_PackageInfo_HashStrings, Bool(),
                         PrintToStringParamName());
TEST_P(StatsdTestUtil_PackageInfo_HashStrings, TestBuildPackageInfoWithInstallerAndInstallerIndex) {
    const bool hashStrings = GetParam();
    PackageInfo packageInfo = buildPackageInfo(appName, uid, version, versionString, installer,
                                               /* certHash */ {}, /* deleted */ false, hashStrings,
                                               /* installerIndex */ 1);

    EXPECT_THAT(packageInfo.installer_index(), Eq(1));
    EXPECT_FALSE(packageInfo.has_installer_hash());
    EXPECT_FALSE(packageInfo.has_installer());
}

TEST(StatsdTestUtil_PackageInfo, TestBuildPackageInfoWithInstallerNoInstallerIndexHashStrings) {
    PackageInfo packageInfo =
            buildPackageInfo(appName, uid, version, versionString, installer, /* certHash */ {},
                             /* deleted */ false, /* hashStrings */ true,
                             /* installerIndex */ nullopt);

    EXPECT_FALSE(packageInfo.has_installer_index());
    EXPECT_THAT(packageInfo.installer_hash(), Eq(Hash64(installer)));
    EXPECT_FALSE(packageInfo.has_installer());
}

TEST(StatsdTestUtil_PackageInfo, TestBuildPackageInfoWithInstallerNoInstallerIndexNoHashStrings) {
    PackageInfo packageInfo =
            buildPackageInfo(appName, uid, version, versionString, installer, /* certHash */ {},
                             /* deleted */ false, /* hashStrings */ false,
                             /* installerIndex */ nullopt);

    EXPECT_FALSE(packageInfo.has_installer_index());
    EXPECT_THAT(packageInfo.installer(), Eq(installer));
    EXPECT_FALSE(packageInfo.has_installer_hash());
}

TEST_P(StatsdTestUtil_PackageInfo_HashStrings, TestBuildPackageInfosEmptyOptionalParams) {
    const bool hashStrings = GetParam();
    vector<PackageInfo> packageInfos = buildPackageInfos(
            {appName}, {uid}, {version}, {versionString}, /* installers */ {}, /* certHashes */ {},
            /* deleted */ {false}, /* installerIndices */ {}, hashStrings);

    PackageInfo packageInfo = buildPackageInfo(
            appName, uid, version, versionString, /* installer */ nullopt, /* certHash */ {},
            /* deleted */ false, hashStrings, /* installerIndex */ nullopt);

    EXPECT_THAT(packageInfos, Pointwise(EqPackageInfo(), {packageInfo}));
}

TEST_P(StatsdTestUtil_PackageInfo_HashStrings, TestBuildPackageInfosNonEmptyOptionalParams) {
    const bool hashStrings = GetParam();
    vector<PackageInfo> packageInfos = buildPackageInfos(
            {appName}, {uid}, {version}, {versionString}, {installer}, /* certHashes */ {{'a'}},
            /* deleted */ {false}, /* installerIndices */ {3}, hashStrings);

    PackageInfo packageInfo =
            buildPackageInfo(appName, uid, version, versionString, installer, /* certHash */ {'a'},
                             /* deleted */ false, hashStrings, /* installerIndex */ 3);

    EXPECT_THAT(packageInfos, Pointwise(EqPackageInfo(), {packageInfo}));
}

#else
GTEST_LOG_(INFO) << "This test does nothing.\n";
#endif

}  // namespace statsd
}  // namespace os
}  // namespace android
