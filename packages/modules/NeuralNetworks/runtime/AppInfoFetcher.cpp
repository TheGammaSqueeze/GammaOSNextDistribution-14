/*
 * Copyright (C) 2021 The Android Open Source Project
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

#define LOG_TAG "AppInfoFetcher"

#include "AppInfoFetcher.h"

#include <Utils.h>
#include <android-base/file.h>
#include <android-base/properties.h>
#include <binder/IServiceManager.h>

#include <algorithm>
#include <limits>
#include <map>
#include <memory>
#include <string>
#include <string_view>
#include <vector>

#ifdef __ANDROID__
#include <PackageInfo.h>
#include <procpartition/procpartition.h>
#endif  // __ANDROID__

namespace android {
namespace nn {

#ifdef __ANDROID__
namespace {

// Query PackageManagerNative service about Android app properties.
// On success, it will populate appInfo->app* fields.
bool fetchAppPackageLocationInfo(uid_t uid, AppInfoFetcher::AppInfo* appInfo) {
    ANeuralNetworks_PackageInfo packageInfo;
    if (!ANeuralNetworks_fetch_PackageInfo(uid, &packageInfo)) {
        return false;
    }
    appInfo->appPackageName = packageInfo.appPackageName;
    appInfo->appIsSystemApp = packageInfo.appIsSystemApp;
    appInfo->appIsOnVendorImage = packageInfo.appIsOnVendorImage;
    appInfo->appIsOnProductImage = packageInfo.appIsOnProductImage;

    ANeuralNetworks_free_PackageInfo(&packageInfo);
    return true;
}

}  // namespace

AppInfoFetcher::AppInfoFetcher()
    : appInfo({.binaryPath = ::android::procpartition::getExe(getpid()),
               .appPackageName = "",
               .appIsSystemApp = false,
               .appIsOnVendorImage = false,
               .appIsOnProductImage = false}) {
    if (appInfo.binaryPath == "/system/bin/app_process64" ||
        appInfo.binaryPath == "/system/bin/app_process32") {
        if (!fetchAppPackageLocationInfo(getuid(), &appInfo)) {
            LOG(ERROR) << "Failed to get app information from package_manager_native";
        }
    }
}

#else  // __ANDROID__

AppInfoFetcher::AppInfoFetcher()
    : appInfo({.binaryPath = "/system/bin/app_process64",
               .appPackageName = "",
               .appIsSystemApp = false,
               .appIsOnVendorImage = false,
               .appIsOnProductImage = false}) {}

#endif  // __ANDROID__

}  // namespace nn
}  // namespace android
