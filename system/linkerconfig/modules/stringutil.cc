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
#include "linkerconfig/stringutil.h"

#include <type_traits>
#include <unordered_set>
#include <vector>

#include <android-base/strings.h>

namespace android {
namespace linkerconfig {
namespace modules {
std::string TrimPrefix(const std::string& s, const std::string& prefix) {
  if (android::base::StartsWith(s, prefix)) {
    return s.substr(prefix.size());
  }
  return s;
}

// merge a list of libs into a single value (concat with ":")
std::string MergeLibs(const std::vector<std::string>& libs) {
  std::unordered_set<std::string_view> seen;
  std::string out;
  bool first = true;
  for (const auto& part : libs) {
    const char* part_str = part.c_str();
    const char* part_end = part.c_str() + part.size();
    while (part_str != part_end) {
      const void* end = memchr(part_str, ':', part_end - part_str);
      const char* end_str = end ? static_cast<const char*>(end) : part_end;
      std::string_view lib(part_str, end_str - part_str);
      static_assert(
          std::is_const_v<typeof(libs)>,
          "libs needs to be const so we can use a string_view in seen");
      if (!lib.empty() && seen.insert(lib).second) {  // for a new lib
        if (!first) {
          out += ':';
        }
        out += lib;
        first = false;
      }
      if (end == nullptr) break;
      part_str = end_str + 1;
    }
  }
  return out;
}
}  // namespace modules
}  // namespace linkerconfig
}  // namespace android