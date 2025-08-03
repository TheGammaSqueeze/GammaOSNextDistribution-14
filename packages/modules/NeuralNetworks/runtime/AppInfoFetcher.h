/*
 * Copyright (C) 2020 The Android Open Source Project
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

#ifndef ANDROID_PACKAGES_MODULES_NEURALNETWORKS_RUNTIME_APP_INFO_FETCHER_H
#define ANDROID_PACKAGES_MODULES_NEURALNETWORKS_RUNTIME_APP_INFO_FETCHER_H

#include <string>

namespace android {
namespace nn {

// Manages client app information.
//
// This class gathers information about client application
// and provides a unified way to access it.
class AppInfoFetcher {
   public:
    static AppInfoFetcher* get() {
        static AppInfoFetcher info;
        return &info;
    }

    // Collection of NNAPI client app-related information
    struct AppInfo {
        // Path of the binary (/proc/$PID/exe)
        std::string binaryPath;
        // Package name of the Android app (empty string if not Android app).
        std::string appPackageName;
        // Is the app a system app? (false if not an Android app)
        bool appIsSystemApp;
        // Is the app preinstalled on vendor image? (false if not an Android app)
        bool appIsOnVendorImage;
        // Is the app preinstalled on product image? (false if not an Android app)
        bool appIsOnProductImage;
    };

    // Get App-replated information
    const AppInfo& getAppInfo() const { return appInfo; }

   private:
    AppInfoFetcher();

    AppInfo appInfo;
};

}  // namespace nn
}  // namespace android

#endif  // ANDROID_PACKAGES_MODULES_NEURALNETWORKS_RUNTIME_APP_PACKAGE_INFO_H
