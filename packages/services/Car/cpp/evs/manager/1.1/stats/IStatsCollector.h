/*
 * Copyright 2022 The Android Open Source Project
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

#ifndef CPP_EVS_MANAGER_1_1_STATS_ISTATSCOLLECTOR_H_
#define CPP_EVS_MANAGER_1_1_STATS_ISTATSCOLLECTOR_H_

#include "VirtualCamera.h"

#include <android-base/result.h>
#include <android/hardware/automotive/evs/1.1/types.h>

#include <string>
#include <unordered_map>

namespace android::automotive::evs::V1_1::implementation {

class IStatsCollector {
public:
    virtual ~IStatsCollector() = default;

    virtual android::base::Result<void> startCollection() = 0;
    virtual android::base::Result<void> startCustomCollection(
            std::chrono::nanoseconds interval, std::chrono::nanoseconds duration) = 0;
    virtual android::base::Result<std::string> stopCustomCollection(std::string id) = 0;

    virtual std::unordered_map<std::string, std::string> toString(const char* indent) = 0;
    virtual android::base::Result<void> registerClientToMonitor(
            const android::sp<HalCamera>& camera) = 0;
    virtual android::base::Result<void> unregisterClientToMonitor(const std::string& id) = 0;
};

}  // namespace android::automotive::evs::V1_1::implementation

#endif  // CPP_EVS_MANAGER_1_1_STATS_ISTATSCOLLECTOR_H_
