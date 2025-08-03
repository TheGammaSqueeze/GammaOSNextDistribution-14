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

#include "utils.h"

#include <android-base/properties.h>
#include <gtest/gtest.h>

#include <string>
#include <vector>

// Returns true iff the device has the specified feature.
bool deviceSupportsFeature(const char *feature) {
    bool device_supports_feature = false;
    FILE *p = popen("/system/bin/pm list features", "re");
    if (p) {
        char *line = NULL;
        size_t len = 0;
        while (getline(&line, &len, p) > 0) {
            if (strstr(line, feature)) {
                device_supports_feature = true;
                break;
            }
        }
        pclose(p);
    }
    return device_supports_feature;
}

int getFirstApiLevel() {
    int level = android::base::GetIntProperty("ro.product.first_api_level", 0);
    if (level == 0) {
        level = android::base::GetIntProperty("ro.build.version.sdk", 0);
    }
    if (level == 0) {
        ADD_FAILURE() << "Failed to determine first API level";
    }
    return level;
}

int getVendorApiLevel() {
    std::vector<std::string> BOARD_API_LEVEL_PROPS = {"ro.board.api_level",
                                                      "ro.board.first_api_level", "ro.vndk.version",
                                                      "ro.vendor.build.version.sdk"};
    const int API_LEVEL_CURRENT = 10000;
    for (const auto &api_level_prop : BOARD_API_LEVEL_PROPS) {
        int api_level = android::base::GetIntProperty(api_level_prop, API_LEVEL_CURRENT);
        if (api_level != API_LEVEL_CURRENT) {
            return api_level;
        }
    }
    return API_LEVEL_CURRENT;
}