/**
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

#ifndef CPP_WATCHDOG_SERVER_TESTS_MOCKPACKAGEINFORESOLVER_H_
#define CPP_WATCHDOG_SERVER_TESTS_MOCKPACKAGEINFORESOLVER_H_

#include "PackageInfoResolver.h"

#include <android-base/result.h>
#include <gmock/gmock.h>

#include <string>
#include <unordered_map>

namespace android {
namespace automotive {
namespace watchdog {

class MockPackageInfoResolver : public PackageInfoResolverInterface {
public:
    MockPackageInfoResolver() {}
    MOCK_METHOD(android::base::Result<void>, initWatchdogServiceHelper,
                (const android::sp<WatchdogServiceHelperInterface>& watchdogServiceHelper),
                (override));
    MOCK_METHOD(void, asyncFetchPackageNamesForUids,
                (const std::vector<uid_t>&,
                 const std::function<void(std::unordered_map<uid_t, std::string>)>&),
                (override));
    MOCK_METHOD(
            (std::unordered_map<uid_t, aidl::android::automotive::watchdog::internal::PackageInfo>),
            getPackageInfosForUids, (const std::vector<uid_t>& uids), (override));
    MOCK_METHOD(void, setPackageConfigurations,
                ((const std::unordered_set<std::string>&),
                 (const std::unordered_map<
                         std::string,
                         aidl::android::automotive::watchdog::internal::ApplicationCategoryType>&)),
                (override));
};

}  // namespace watchdog
}  // namespace automotive
}  // namespace android

#endif  //  CPP_WATCHDOG_SERVER_TESTS_MOCKPACKAGEINFORESOLVER_H_
