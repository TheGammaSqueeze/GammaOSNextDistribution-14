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

#ifndef ART_ODREFRESH_ODR_COMMON_H_
#define ART_ODREFRESH_ODR_COMMON_H_

#include <initializer_list>
#include <string>
#include <string_view>

#include "android-base/result.h"

namespace art {
namespace odrefresh {

// Quotes a path with single quotes (').
std::string QuotePath(std::string_view path);

// Converts the security patch date to a comparable integer.
android::base::Result<int> ParseSecurityPatchStr(const std::string& security_patch_str);

// Returns true if partial compilation should be disabled. Takes a string from
// `ro.build.version.security_patch`, which represents the security patch date.
bool ShouldDisablePartialCompilation(const std::string& security_patch_str);

// Returns true if there is no need to load existing artifacts that are already up-to-date and write
// them back. See `OnDeviceRefresh::RefreshExistingArtifacts` for more details. Takes a string from
// `ro.build.version.sdk`, which represents the SDK version.
bool ShouldDisableRefresh(const std::string& sdk_version_str);

// Passes the name and the value for each system property to the provided callback.
void SystemPropertyForeach(std::function<void(const char* name, const char* value)> action);

// Returns true if the build-time UFFD GC matches the runtime's choice.
bool CheckBuildUserfaultFdGc(bool build_enable_uffd_gc,
                             bool is_at_most_u,
                             bool kernel_supports_uffd);

}  // namespace odrefresh
}  // namespace art

#endif  // ART_ODREFRESH_ODR_COMMON_H_
