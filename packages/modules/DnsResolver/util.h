/*
 * Copyright (C) 2019 The Android Open Source Project
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
 *
 */

#pragma once

#include <chrono>
#include <string>

#include <netinet/in.h>

#include <android-base/properties.h>
#include <android-modules-utils/sdk_level.h>

#include "Experiments.h"

socklen_t sockaddrSize(const sockaddr* sa);
socklen_t sockaddrSize(const sockaddr_storage& ss);

// TODO: getExperimentFlagString
// TODO: Migrate it to DnsResolverExperiments.cpp
int getExperimentFlagInt(const std::string& flagName, int defaultValue);

// Convert time_point to readable string format "hr:min:sec.ms".
std::string timestampToString(const std::chrono::system_clock::time_point& ts);

// When sdk X release branch is created, aosp's sdk version would still be X-1,
// internal would be X. Also there might be some different setting between real devices and
// CF. Below is the example for the sdk related properties in later R development stage. (internal
// should be cosidered as S and aosp should be considered as R.)
// R version is 30,                          rvc-dev  aosp(CF)  aosp(non-CF)  internal
// ro.build.version.sdk:                     [30]     [29]      [29]          [30]
// Note that, ro.product.first_api_level is device specific - so the value might be various.
// ro.product.first_api_level:               [26-30]  [31]      [28]          [26-30]
// ro.build.version.preview_sdk:             [0]      [1]       [1]           [1]
inline uint64_t getApiLevel() {
    uint64_t buildVersionSdk = android::base::GetUintProperty<uint64_t>("ro.build.version.sdk", 0);
    uint64_t buildVersionPreviewSdk =
            android::base::GetUintProperty<uint64_t>("ro.build.version.preview_sdk", 0);
    uint64_t firstApiLevel =
            android::base::GetUintProperty<uint64_t>("ro.product.first_api_level", 0);
    return std::max(buildVersionSdk + !!buildVersionPreviewSdk, firstApiLevel);
}

// It's the identical strategy as frameworks/base/core/java/android/os/Build.java did.
// There's also equivalent C++ code in system/core/init/property_service.cpp
// (and it is CTS tested in BuildTest.java)
inline bool isDebuggable() {
    return android::base::GetBoolProperty("ro.debuggable", false);
}

inline bool isAtLeastT() {
    const static bool isAtLeastT = android::modules::sdklevel::IsAtLeastT();
    return isAtLeastT;
}

inline bool isAtLeastU() {
    const static bool isAtLeastU = android::modules::sdklevel::IsAtLeastU();
    return isAtLeastU;
}
