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

#include "MockWatchdogServiceHelper.h"
#include "PackageInfoResolver.h"
#include "PackageInfoTestUtils.h"

#include <aidl/android/automotive/watchdog/internal/ApplicationCategoryType.h>
#include <aidl/android/automotive/watchdog/internal/ComponentType.h>
#include <aidl/android/automotive/watchdog/internal/UidType.h>
#include <android-base/stringprintf.h>
#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include <future>  // NOLINT(build/c++11)

namespace android {
namespace automotive {
namespace watchdog {

using ::aidl::android::automotive::watchdog::internal::ApplicationCategoryType;
using ::aidl::android::automotive::watchdog::internal::ComponentType;
using ::aidl::android::automotive::watchdog::internal::PackageInfo;
using ::aidl::android::automotive::watchdog::internal::UidType;
using ::android::sp;
using ::android::base::StringAppendF;
using ::ndk::ScopedAStatus;
using ::testing::_;
using ::testing::ByMove;
using ::testing::DoAll;
using ::testing::NotNull;
using ::testing::Pair;
using ::testing::Return;
using ::testing::SetArgPointee;
using ::testing::UnorderedElementsAre;
using ::testing::UnorderedElementsAreArray;

namespace {

constexpr std::chrono::seconds FETCH_PACKAGE_NAMES_TIMEOUT_SECS = 1s;

using PackageToAppCategoryMap =
        std::unordered_map<std::string,
                           aidl::android::automotive::watchdog::internal::ApplicationCategoryType>;

std::string toString(const std::unordered_map<uid_t, PackageInfo>& mappings) {
    std::string buffer = "{";
    for (const auto& [uid, info] : mappings) {
        if (buffer.size() > 1) {
            StringAppendF(&buffer, ", ");
        }
        StringAppendF(&buffer, "{%d: %s}", uid, info.toString().c_str());
    }
    StringAppendF(&buffer, "}");
    return buffer;
}

}  // namespace

namespace internal {

class PackageInfoResolverPeer final {
public:
    PackageInfoResolverPeer() { mPackageInfoResolver = PackageInfoResolver::sInstance; }

    ~PackageInfoResolverPeer() {
        PackageInfoResolver::sGetpwuidHandler = &getpwuid;
        clearMappingCache();
    }

    void initWatchdogServiceHelper(
            const sp<WatchdogServiceHelperInterface>& watchdogServiceHelper) {
        ASSERT_RESULT_OK(mPackageInfoResolver->initWatchdogServiceHelper(watchdogServiceHelper));
    }

    void resetWatchdogServiceHelper() { mPackageInfoResolver->mWatchdogServiceHelper = nullptr; }

    void injectCacheMapping(const std::unordered_map<uid_t, PackageInfo>& mapping) {
        mPackageInfoResolver->mUidToPackageInfoMapping = mapping;
    }

    void setPackageConfigurations(const std::unordered_set<std::string>& vendorPackagePrefixes,
                                  const PackageToAppCategoryMap& packagesToAppCategories) {
        mPackageInfoResolver->setPackageConfigurations(vendorPackagePrefixes,
                                                       packagesToAppCategories);
    }

    void stubGetpwuid(const std::unordered_map<uid_t, std::string>& nativeUidToPackageNameMapping) {
        updateNativeUidToPackageNameMapping(nativeUidToPackageNameMapping);
        PackageInfoResolver::sGetpwuidHandler = [&](uid_t uid) -> struct passwd* {
            const auto& it = mNativeUidToPackageNameMapping.find(uid);
            if (it == mNativeUidToPackageNameMapping.end()) {
                return nullptr;
            }
            return &it->second;
        };
    }

private:
    void updateNativeUidToPackageNameMapping(
            const std::unordered_map<uid_t, std::string>& mapping) {
        clearMappingCache();
        for (const auto& it : mapping) {
            size_t packageNameLen = it.second.size() + 1;
            char* packageName = new char[packageNameLen];
            if (packageName == nullptr) {
                continue;
            }
            memset(packageName, 0, packageNameLen);
            snprintf(packageName, packageNameLen, "%s", it.second.c_str());

            struct passwd pwd {
                .pw_name = packageName, .pw_uid = it.first
            };
            mNativeUidToPackageNameMapping.insert(std::make_pair(it.first, pwd));
        }
    }

    void clearMappingCache() {
        for (const auto it : mNativeUidToPackageNameMapping) {
            // Delete the previously allocated char array before clearing the mapping.
            delete it.second.pw_name;
        }
        mNativeUidToPackageNameMapping.clear();
    }

    sp<PackageInfoResolver> mPackageInfoResolver;
    std::unordered_map<uid_t, struct passwd> mNativeUidToPackageNameMapping;
};

}  // namespace internal

class PackageInfoResolverTest : public ::testing::Test {
protected:
    virtual void SetUp() {
        mPackageInfoResolver = PackageInfoResolver::getInstance();
        mPackageInfoResolverPeer = std::make_unique<internal::PackageInfoResolverPeer>();
        mMockWatchdogServiceHelper = sp<MockWatchdogServiceHelper>::make();
        ASSERT_NO_FATAL_FAILURE(
                mPackageInfoResolverPeer->initWatchdogServiceHelper(mMockWatchdogServiceHelper));
    }

    virtual void TearDown() {
        PackageInfoResolver::terminate();
        mPackageInfoResolverPeer.reset();
        mMockWatchdogServiceHelper.clear();
    }

    sp<PackageInfoResolverInterface> mPackageInfoResolver;
    std::unique_ptr<internal::PackageInfoResolverPeer> mPackageInfoResolverPeer;
    sp<MockWatchdogServiceHelper> mMockWatchdogServiceHelper;
};

TEST_F(PackageInfoResolverTest, TestGetPackageInfosForUidsViaGetpwuid) {
    PackageToAppCategoryMap packagesToAppCategories = {
            // These mappings should be ignored for native packages.
            {"system.package.B", ApplicationCategoryType::MAPS},
            {"vendor.package.A", ApplicationCategoryType::MEDIA},
            {"vendor.pkg.maps", ApplicationCategoryType::MAPS},
    };
    mPackageInfoResolverPeer->setPackageConfigurations({"vendor.pkg"}, packagesToAppCategories);

    std::unordered_map<uid_t, PackageInfo> expectedMappings{
            {7700,
             constructPackageInfo("system.package.B", 7700, UidType::NATIVE, ComponentType::SYSTEM,
                                  ApplicationCategoryType::OTHERS)},
            {5100,
             constructPackageInfo("vendor.package.A", 5100, UidType::NATIVE, ComponentType::VENDOR,
                                  ApplicationCategoryType::OTHERS)},
            {6700,
             constructPackageInfo("vendor.package.B", 6700, UidType::NATIVE, ComponentType::VENDOR,
                                  ApplicationCategoryType::OTHERS)},
            {9997,
             constructPackageInfo("vendor.pkg.C", 9997, UidType::NATIVE, ComponentType::VENDOR,
                                  ApplicationCategoryType::OTHERS)},
    };

    mPackageInfoResolverPeer->stubGetpwuid({{7700, "system.package.B"},
                                            {5100, "vendor.package.A"},
                                            {6700, "vendor.package.B"},
                                            {9997, "vendor.pkg.C"}});
    EXPECT_CALL(*mMockWatchdogServiceHelper, getPackageInfosForUids(_, _, _)).Times(0);

    auto actualMappings = mPackageInfoResolver->getPackageInfosForUids({7700, 5100, 6700, 9997});

    EXPECT_THAT(actualMappings, UnorderedElementsAreArray(expectedMappings))
            << "Expected: " << toString(expectedMappings)
            << "\nActual: " << toString(actualMappings);
}

TEST_F(PackageInfoResolverTest, TestGetPackageInfosForUidsViaWatchdogService) {
    PackageToAppCategoryMap packagesToAppCategories = {
            // system.package.B is native package so this should be ignored.
            {"system.package.B", ApplicationCategoryType::MAPS},
            {"vendor.package.A", ApplicationCategoryType::MEDIA},
            {"shared:vendor.package.C", ApplicationCategoryType::MEDIA},
            {"vendor.package.shared.uid.D", ApplicationCategoryType::MAPS},
    };
    mPackageInfoResolverPeer->setPackageConfigurations({"vendor.pkg"}, packagesToAppCategories);
    /*
     * Shared UID should be resolved with car watchdog service as well to get the shared packages
     * list.
     */
    mPackageInfoResolverPeer->stubGetpwuid({{6100, "shared:system.package.A"}});

    std::unordered_map<uid_t, PackageInfo> expectedMappings{
            {6100,
             constructPackageInfo("shared:system.package.A", 6100, UidType::NATIVE,
                                  ComponentType::SYSTEM, ApplicationCategoryType::OTHERS,
                                  {"system.pkg.1", "system.pkg.2"})},
            {7700,
             constructPackageInfo("system.package.B", 7700, UidType::NATIVE, ComponentType::SYSTEM,
                                  ApplicationCategoryType::OTHERS)},
            {15100,
             constructPackageInfo("vendor.package.A", 15100, UidType::APPLICATION,
                                  ComponentType::VENDOR, ApplicationCategoryType::OTHERS)},
            {16700,
             constructPackageInfo("vendor.pkg", 16700, UidType::NATIVE, ComponentType::VENDOR,
                                  ApplicationCategoryType::OTHERS)},
            {18100,
             constructPackageInfo("shared:vendor.package.C", 18100, UidType::APPLICATION,
                                  ComponentType::VENDOR, ApplicationCategoryType::OTHERS)},
            {19100,
             constructPackageInfo("shared:vendor.package.D", 19100, UidType::APPLICATION,
                                  ComponentType::VENDOR, ApplicationCategoryType::OTHERS,
                                  {"vendor.package.shared.uid.D"})},
    };

    std::vector<int32_t> expectedUids = {6100, 7700, 15100, 16700, 18100, 19100};
    std::vector<std::string> expectedPrefixes = {"vendor.pkg"};
    std::vector<PackageInfo> injectPackageInfos = {expectedMappings.at(6100),
                                                   expectedMappings.at(7700),
                                                   expectedMappings.at(15100),
                                                   expectedMappings.at(16700),
                                                   expectedMappings.at(18100),
                                                   expectedMappings.at(19100)};

    expectedMappings.at(15100).appCategoryType = ApplicationCategoryType::MEDIA;
    expectedMappings.at(18100).appCategoryType = ApplicationCategoryType::MEDIA;
    expectedMappings.at(19100).appCategoryType = ApplicationCategoryType::MAPS;

    EXPECT_CALL(*mMockWatchdogServiceHelper, isServiceConnected()).WillOnce(Return(true));
    EXPECT_CALL(*mMockWatchdogServiceHelper,
                getPackageInfosForUids(expectedUids, expectedPrefixes, _))
            .WillOnce(DoAll(SetArgPointee<2>(injectPackageInfos),
                            Return(ByMove(ScopedAStatus::ok()))));

    auto actualMappings =
            mPackageInfoResolver->getPackageInfosForUids({6100, 7700, 15100, 16700, 18100, 19100});

    EXPECT_THAT(actualMappings, UnorderedElementsAreArray(expectedMappings))
            << "Expected: " << toString(expectedMappings)
            << "\nActual: " << toString(actualMappings);
}

TEST_F(PackageInfoResolverTest, TestGetPackageInfosForUidsWithoutWatchdogServiceHelper) {
    internal::PackageInfoResolverPeer peer;
    auto packageInfoResolver = PackageInfoResolver::getInstance();
    mPackageInfoResolverPeer->stubGetpwuid({{6100, "shared:system.package.A"}});

    std::unordered_map<uid_t, PackageInfo> expectedMappings{
            {6100,
             constructPackageInfo("shared:system.package.A", 6100, UidType::NATIVE,
                                  ComponentType::SYSTEM, ApplicationCategoryType::OTHERS, {})},
    };

    mPackageInfoResolverPeer->resetWatchdogServiceHelper();

    EXPECT_CALL(*mMockWatchdogServiceHelper, getPackageInfosForUids(_, _, _)).Times(0);

    auto actualMappings =
            mPackageInfoResolver->getPackageInfosForUids({6100, 7700, 15100, 16700, 18100, 19100});

    EXPECT_THAT(actualMappings, UnorderedElementsAreArray(expectedMappings))
            << "Expected: " << toString(expectedMappings)
            << "\nActual: " << toString(actualMappings);
}

TEST_F(PackageInfoResolverTest, TestGetPackageInfosForUidsMissingWatchdogServiceConnection) {
    internal::PackageInfoResolverPeer peer;
    auto packageInfoResolver = PackageInfoResolver::getInstance();
    mPackageInfoResolverPeer->stubGetpwuid({{6100, "shared:system.package.A"}});

    std::unordered_map<uid_t, PackageInfo> expectedMappings{
            {6100,
             constructPackageInfo("shared:system.package.A", 6100, UidType::NATIVE,
                                  ComponentType::SYSTEM, ApplicationCategoryType::OTHERS, {})},
    };

    EXPECT_CALL(*mMockWatchdogServiceHelper, isServiceConnected()).WillOnce(Return(false));
    EXPECT_CALL(*mMockWatchdogServiceHelper, getPackageInfosForUids(_, _, _)).Times(0);

    auto actualMappings =
            mPackageInfoResolver->getPackageInfosForUids({6100, 7700, 15100, 16700, 18100, 19100});

    EXPECT_THAT(actualMappings, UnorderedElementsAreArray(expectedMappings))
            << "Expected: " << toString(expectedMappings)
            << "\nActual: " << toString(actualMappings);
}

TEST_F(PackageInfoResolverTest, TestResolvesApplicationUidFromLocalCache) {
    internal::PackageInfoResolverPeer peer;
    auto packageInfoResolver = PackageInfoResolver::getInstance();
    std::unordered_map<uid_t, PackageInfo> expectedMappings{
            {1003456,
             constructPackageInfo("vendor.package", 1003456, UidType::NATIVE, ComponentType::SYSTEM,
                                  ApplicationCategoryType::OTHERS)}};
    mPackageInfoResolverPeer->injectCacheMapping(expectedMappings);

    mPackageInfoResolverPeer->stubGetpwuid({});

    EXPECT_CALL(*mMockWatchdogServiceHelper, getPackageInfosForUids(_, _, _)).Times(0);

    auto actualMappings = mPackageInfoResolver->getPackageInfosForUids({1003456});

    EXPECT_THAT(actualMappings, UnorderedElementsAreArray(expectedMappings))
            << "Expected: " << toString(expectedMappings)
            << "\nActual: " << toString(actualMappings);
}

TEST_F(PackageInfoResolverTest, TestAsyncFetchPackageNamesForUids) {
    internal::PackageInfoResolverPeer peer;
    auto packageInfoResolver = PackageInfoResolver::getInstance();
    uid_t callingUid = 1003456;
    std::unordered_map<uid_t, PackageInfo> expectedMappings{
            {callingUid,
             constructPackageInfo("vendor.package", callingUid, UidType::NATIVE,
                                  ComponentType::SYSTEM, ApplicationCategoryType::OTHERS)}};
    mPackageInfoResolverPeer->injectCacheMapping(expectedMappings);

    auto promise = std::promise<void>();
    auto future = promise.get_future();

    mPackageInfoResolver->asyncFetchPackageNamesForUids({callingUid},
                                                        [&](std::unordered_map<uid_t, std::string>
                                                                    packageNames) {
                                                            ASSERT_TRUE(
                                                                    packageNames.find(callingUid) !=
                                                                    packageNames.end());
                                                            ASSERT_EQ(packageNames[callingUid],
                                                                      "vendor.package");
                                                            promise.set_value();
                                                        });

    ASSERT_EQ(std::future_status::ready, future.wait_for(FETCH_PACKAGE_NAMES_TIMEOUT_SECS));
}

}  // namespace watchdog
}  // namespace automotive
}  // namespace android
