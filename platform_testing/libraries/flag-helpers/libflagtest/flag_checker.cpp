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

#include <string>
#include <utility>
#include <functional>
#include <vector>

#include <flag_checker.h>
#include <gtest/gtest.h>
#include <android-base/strings.h>
#include <android-base/stringprintf.h>
#include <android-base/properties.h>

#define SYSTEM_PROPERTY_PREFIX "persist.device_config."

namespace android::test::flag {

std::vector<std::pair<bool, std::string>> GetFlagsNotMetRequirements(
  const std::vector<std::pair<bool, std::vector<p_flag>>> flag_conditions) {
  std::vector<std::pair<bool, std::string>> unsatisfied_flags;
  for (const std::pair<bool, std::vector<p_flag>> flag_condition : flag_conditions) {
    bool expected_condition = flag_condition.first;
    for (const p_flag feature_flag : flag_condition.second) {
      if (!CheckFlagCondition(expected_condition, feature_flag)) {
        // Records the feature flag if it doesn't meet the expected condition.
        unsatisfied_flags.push_back({expected_condition, feature_flag.second});
      }
    }
  }
  return unsatisfied_flags;
}

bool CheckFlagCondition(bool expected_condition, const p_flag feature_flag) {
  // Checks the aconfig flag.
  if (feature_flag.first) {
    return feature_flag.first() == expected_condition;
  }
  // Checks the legacy flag.
  std::vector<std::string> flag_args = android::base::Split(feature_flag.second, ",");
  if (flag_args.size() != 3) {
    return false;
  }
  std::string package_name = android::base::StringReplace(flag_args[1], "::", ".", true);
  std::string full_flag_name = android::base::StringPrintf(
    "%s.%s.%s",
    android::base::Trim(flag_args[0]).c_str(),
    android::base::Trim(package_name).c_str(),
    android::base::Trim(flag_args[2]).c_str()
  );
  return android::base::GetProperty(SYSTEM_PROPERTY_PREFIX + full_flag_name, "") ==
    (expected_condition ? "true":"false");
}

}  // namespace android::test::flag