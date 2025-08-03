/*
 * Copyright (c) 2021, The Android Open Source Project
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

#ifndef CPP_WATCHDOG_SERVER_TESTS_PACKAGEINFOTESTUTILS_H_
#define CPP_WATCHDOG_SERVER_TESTS_PACKAGEINFOTESTUTILS_H_

#include <aidl/android/automotive/watchdog/internal/ApplicationCategoryType.h>
#include <aidl/android/automotive/watchdog/internal/ComponentType.h>
#include <aidl/android/automotive/watchdog/internal/PackageInfo.h>
#include <aidl/android/automotive/watchdog/internal/UidType.h>
#include <gmock/gmock.h>

#include <string>
#include <vector>

namespace android {
namespace automotive {
namespace watchdog {

aidl::android::automotive::watchdog::internal::PackageInfo constructPackageInfo(
        const char* packageName, int32_t uid,
        aidl::android::automotive::watchdog::internal::UidType uidType =
                aidl::android::automotive::watchdog::internal::UidType::UNKNOWN,
        aidl::android::automotive::watchdog::internal::ComponentType componentType =
                aidl::android::automotive::watchdog::internal::ComponentType::UNKNOWN,
        aidl::android::automotive::watchdog::internal::ApplicationCategoryType appCategoryType =
                aidl::android::automotive::watchdog::internal::ApplicationCategoryType::OTHERS,
        std::vector<std::string> sharedUidPackages = {});

aidl::android::automotive::watchdog::internal::PackageInfo constructAppPackageInfo(
        const char* packageName,
        const aidl::android::automotive::watchdog::internal::ComponentType componentType,
        const aidl::android::automotive::watchdog::internal::ApplicationCategoryType
                appCategoryType = aidl::android::automotive::watchdog::internal::
                        ApplicationCategoryType::OTHERS,
        const std::vector<std::string>& sharedUidPackages = {});

MATCHER_P(PackageIdentifierEq, expected, "") {
    const auto& actual = arg;
    return ::testing::Value(actual.name, ::testing::Eq(expected.name)) &&
            ::testing::Value(actual.uid, ::testing::Eq(expected.uid));
}

MATCHER_P(PackageInfoEq, expected, "") {
    const auto& actual = arg;
    return ::testing::Value(actual.packageIdentifier,
                            PackageIdentifierEq(expected.packageIdentifier)) &&
            ::testing::Value(actual.uidType, ::testing::Eq(expected.uidType)) &&
            ::testing::Value(actual.sharedUidPackages,
                             ::testing::UnorderedElementsAreArray(expected.sharedUidPackages)) &&
            ::testing::Value(actual.componentType, ::testing::Eq(expected.componentType)) &&
            ::testing::Value(actual.appCategoryType, ::testing::Eq(expected.appCategoryType));
}

}  // namespace watchdog
}  // namespace automotive
}  // namespace android

#endif  // CPP_WATCHDOG_SERVER_TESTS_PACKAGEINFOTESTUTILS_H_
