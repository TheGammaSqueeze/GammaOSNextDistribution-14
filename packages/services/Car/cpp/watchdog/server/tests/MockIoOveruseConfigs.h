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

#ifndef CPP_WATCHDOG_SERVER_TESTS_MOCKIOOVERUSECONFIGS_H_
#define CPP_WATCHDOG_SERVER_TESTS_MOCKIOOVERUSECONFIGS_H_

#include "IoOveruseConfigs.h"

#include <aidl/android/automotive/watchdog/PerStateBytes.h>
#include <aidl/android/automotive/watchdog/internal/ApplicationCategoryType.h>
#include <aidl/android/automotive/watchdog/internal/ComponentType.h>
#include <aidl/android/automotive/watchdog/internal/PackageInfo.h>
#include <aidl/android/automotive/watchdog/internal/ResourceOveruseConfiguration.h>
#include <android-base/result.h>
#include <gmock/gmock.h>

namespace android {
namespace automotive {
namespace watchdog {

class MockIoOveruseConfigs : public IoOveruseConfigsInterface {
public:
    MockIoOveruseConfigs() {}
    ~MockIoOveruseConfigs() {}
    MOCK_METHOD(
            android::base::Result<void>, update,
            (const std::vector<
                    aidl::android::automotive::watchdog::internal::ResourceOveruseConfiguration>&),
            (override));

    MOCK_METHOD(
            void, get,
            (std::vector<
                    aidl::android::automotive::watchdog::internal::ResourceOveruseConfiguration>*),
            (const, override));

    MOCK_METHOD(android::base::Result<void>, writeToDisk, (), (override));

    MOCK_METHOD((const std::unordered_set<std::string>&), vendorPackagePrefixes, (), (override));

    MOCK_METHOD((const std::unordered_map<
                        std::string,
                        aidl::android::automotive::watchdog::internal::ApplicationCategoryType>&),
                packagesToAppCategories, (), (override));

    MOCK_METHOD(aidl::android::automotive::watchdog::PerStateBytes, fetchThreshold,
                (const aidl::android::automotive::watchdog::internal::PackageInfo&),
                (const, override));

    MOCK_METHOD(bool, isSafeToKill,
                (const aidl::android::automotive::watchdog::internal::PackageInfo&),
                (const, override));

    MOCK_METHOD((const IoOveruseAlertThresholdSet&), systemWideAlertThresholds, (), (override));

    struct PackageConfig {
        aidl::android::automotive::watchdog::PerStateBytes threshold;
        bool isSafeToKill = false;
    };

    void injectPackageConfigs(
            const std::unordered_map<std::string, PackageConfig>& perPackageConfig) {
        ON_CALL(*this, fetchThreshold(testing::_))
                .WillByDefault(
                        [perPackageConfig = perPackageConfig](
                                const aidl::android::automotive::watchdog::internal::PackageInfo&
                                        packageInfo) {
                            const std::string packageName = packageInfo.packageIdentifier.name;
                            if (const auto it = perPackageConfig.find(packageName);
                                it != perPackageConfig.end()) {
                                return it->second.threshold;
                            }
                            return defaultThreshold().perStateWriteBytes;
                        });
        ON_CALL(*this, isSafeToKill(testing::_))
                .WillByDefault(
                        [perPackageConfig = perPackageConfig](
                                const aidl::android::automotive::watchdog::internal::PackageInfo&
                                        packageInfo) {
                            const std::string packageName = packageInfo.packageIdentifier.name;
                            if (const auto it = perPackageConfig.find(packageName);
                                it != perPackageConfig.end()) {
                                return it->second.isSafeToKill;
                            }
                            return true;
                        });
    }
};

}  // namespace watchdog
}  // namespace automotive
}  // namespace android

#endif  //  CPP_WATCHDOG_SERVER_TESTS_MOCKIOOVERUSECONFIGS_H_
