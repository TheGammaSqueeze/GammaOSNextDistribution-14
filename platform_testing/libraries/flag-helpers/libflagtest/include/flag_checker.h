/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0dd
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#pragma once

#include <vector>
#include <utility>
#include <functional>
#include <string>

// The pair describes a feature flag by an aconfig function and a raw
// flag name. For a legacy feature flag, the function pointer is null.
typedef std::pair<std::function<bool()>, std::string> p_flag;

namespace android::test::flag {

// Returns a group of flags that don't meet expected conditions. Each
// element in the returned group contains an expected condition and a
// feature flag represented by a string. The input parameter `flag_conditions`
// is a group of pairs, with each pair containing the expected condition
// and a group of feature flags.
std::vector<std::pair<bool, std::string>> GetFlagsNotMetRequirements(
    const std::vector<std::pair<bool, std::vector<p_flag>>> flag_conditions);

// Returns true if the value of `feature_flag` meets `expected_condition`,
// false when the condition doesn't meet.
bool CheckFlagCondition(bool expected_condition, const p_flag feature_flag);

}  // namespace android::test::flag