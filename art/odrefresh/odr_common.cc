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

#include "odr_common.h"

#include <sys/system_properties.h>

#include <functional>
#include <initializer_list>
#include <regex>
#include <sstream>
#include <string>
#include <string_view>

#include "android-base/logging.h"
#include "android-base/parseint.h"
#include "android-base/result.h"
#include "base/macros.h"

namespace art {
namespace odrefresh {

namespace {
using ::android::base::Result;
}

std::string QuotePath(std::string_view path) { return ART_FORMAT("'{}'", path); }

Result<int> ParseSecurityPatchStr(const std::string& security_patch_str) {
  std::regex security_patch_regex(R"re((\d{4})-(\d{2})-(\d{2}))re");
  std::smatch m;
  if (!std::regex_match(security_patch_str, m, security_patch_regex)) {
    return Errorf("Invalid security patch string \"{}\"", security_patch_str);
  }
  int year = 0, month = 0, day = 0;
  if (!android::base::ParseInt(m[1], &year) ||
      !android::base::ParseInt(m[2], &month) ||
      !android::base::ParseInt(m[3], &day)) {
    // This should never happen because the string already matches the regex.
    return Errorf("Unknown error when parsing security patch string \"{}\"", security_patch_str);
  }
  return year * 10000 + month * 100 + day;
}

bool ShouldDisablePartialCompilation(const std::string& security_patch_str) {
  Result<int> security_patch_value = ParseSecurityPatchStr(security_patch_str);
  if (!security_patch_value.ok()) {
    LOG(ERROR) << security_patch_value.error();
    return false;
  }
  return security_patch_value.value() < ParseSecurityPatchStr("2022-03-05").value();
}

bool ShouldDisableRefresh(const std::string& sdk_version_str) {
  int sdk_version = 0;
  if (!android::base::ParseInt(sdk_version_str, &sdk_version)) {
    return false;
  }
  return sdk_version >= 32;
}

void SystemPropertyForeach(std::function<void(const char* name, const char* value)> action) {
  __system_property_foreach(
      [](const prop_info* pi, void* cookie) {
        __system_property_read_callback(
            pi,
            [](void* cookie, const char* name, const char* value, unsigned) {
              auto action =
                  reinterpret_cast<std::function<void(const char* name, const char* value)>*>(
                      cookie);
              (*action)(name, value);
            },
            cookie);
      },
      &action);
}

bool CheckBuildUserfaultFdGc(bool build_enable_uffd_gc,
                             bool is_at_most_u,
                             bool kernel_supports_uffd) {
  bool runtime_uses_uffd_gc = (build_enable_uffd_gc || is_at_most_u) && kernel_supports_uffd;
  return build_enable_uffd_gc == runtime_uses_uffd_gc;
}

}  // namespace odrefresh
}  // namespace art
