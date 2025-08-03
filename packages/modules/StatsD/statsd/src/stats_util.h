/*
 * Copyright (C) 2017 The Android Open Source Project
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

#pragma once

#include <android-modules-utils/sdk_level.h>

#include <unordered_map>

#include "HashableDimensionKey.h"

namespace android {
namespace os {
namespace statsd {

// Possible update states for a component. PRESERVE means we should keep the existing one.
// REPLACE means we should create a new one because the existing one changed
// NEW means we should create a new one because one does not currently exist.
enum UpdateStatus {
    UPDATE_UNKNOWN = 0,
    UPDATE_PRESERVE = 1,
    UPDATE_REPLACE = 2,
    UPDATE_NEW = 3,
};

const HashableDimensionKey DEFAULT_DIMENSION_KEY = HashableDimensionKey();
const MetricDimensionKey DEFAULT_METRIC_DIMENSION_KEY = MetricDimensionKey();

typedef std::map<int64_t, HashableDimensionKey> ConditionKey;

typedef std::unordered_map<MetricDimensionKey, int64_t> DimToValMap;

using ConditionLinks = google::protobuf::RepeatedPtrField<MetricConditionLink>;

using StateLinks = google::protobuf::RepeatedPtrField<MetricStateLink>;

struct Empty {};

inline bool isAtLeastS() {
    const static bool isAtLeastS = android::modules::sdklevel::IsAtLeastS();
    return isAtLeastS;
}

inline bool isAtLeastU() {
    const static bool isAtLeastU = android::modules::sdklevel::IsAtLeastU();
    return isAtLeastU;
}

inline bool shouldKeepRandomSample(int samplingPercentage) {
    return (rand() % (100) + 1) <= samplingPercentage;
}

}  // namespace statsd
}  // namespace os
}  // namespace android
