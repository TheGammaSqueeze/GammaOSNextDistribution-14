/*
 * Copyright (C) 2023 The Android Open Source Project
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

#include <stdlib.h>

#include <map>

#include "stats_annotations.h"

namespace android {
namespace os {
namespace statsd {

#define DEFAULT_RESTRICTED_CATEGORY_TTL_DAYS 7

// Restricted categories used internally by statsd.
enum StatsdRestrictionCategory : int32_t {
    CATEGORY_UNKNOWN = -1,
    CATEGORY_NO_RESTRICTION = 0,
    CATEGORY_DIAGNOSTIC = ASTATSLOG_RESTRICTION_CATEGORY_DIAGNOSTIC,
    CATEGORY_SYSTEM_INTELLIGENCE = ASTATSLOG_RESTRICTION_CATEGORY_SYSTEM_INTELLIGENCE,
    CATEGORY_AUTHENTICATION = ASTATSLOG_RESTRICTION_CATEGORY_AUTHENTICATION,
    CATEGORY_FRAUD_AND_ABUSE = ASTATSLOG_RESTRICTION_CATEGORY_FRAUD_AND_ABUSE,
};

// Single instance shared across the process.
class RestrictedPolicyManager {
    RestrictedPolicyManager() = default;

public:
    static RestrictedPolicyManager& getInstance();

    // Gets the TTL in days for a particular restricted category. Returns the default for unknown
    // categories.
    int32_t getRestrictedCategoryTtl(StatsdRestrictionCategory categoryId) const;

private:
    std::map<StatsdRestrictionCategory, int32_t> mRestrictionCategoryTtlInDaysMap;
};
}  // namespace statsd
}  // namespace os
}  // namespace android
