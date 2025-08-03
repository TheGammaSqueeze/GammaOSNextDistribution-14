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

#include "PackageInfoTestUtils.h"

namespace android {
namespace automotive {
namespace watchdog {

using ::aidl::android::automotive::watchdog::internal::ApplicationCategoryType;
using ::aidl::android::automotive::watchdog::internal::ComponentType;
using ::aidl::android::automotive::watchdog::internal::PackageInfo;
using ::aidl::android::automotive::watchdog::internal::UidType;

PackageInfo constructPackageInfo(const char* packageName, int32_t uid, UidType uidType,
                                 ComponentType componentType,
                                 ApplicationCategoryType appCategoryType,
                                 std::vector<std::string> sharedUidPackages) {
    PackageInfo packageInfo;
    packageInfo.packageIdentifier.name = packageName;
    packageInfo.packageIdentifier.uid = uid;
    packageInfo.uidType = uidType;
    packageInfo.componentType = componentType;
    packageInfo.appCategoryType = appCategoryType;
    packageInfo.sharedUidPackages = sharedUidPackages;
    return packageInfo;
}

PackageInfo constructAppPackageInfo(const char* packageName, const ComponentType componentType,
                                    const ApplicationCategoryType appCategoryType,
                                    const std::vector<std::string>& sharedUidPackages) {
    return constructPackageInfo(packageName, 0, UidType::APPLICATION, componentType,
                                appCategoryType, sharedUidPackages);
}

}  // namespace watchdog
}  // namespace automotive
}  // namespace android
