//
// Copyright (C) 2020 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//

#include <inttypes.h>

#include <android-base/stringprintf.h>
#include <kver/kmi_version.h>

namespace android::kver {

#define KMI_VERSION_PRINT_FORMAT "%" PRIu64 ".%" PRIu64 "-android%" PRIu64 "-%" PRIu64
#define KMI_VERSION_SCAN_FORMAT KMI_VERSION_PRINT_FORMAT "%n"

// Not taking a string_view because sscanf requires null-termination.
std::optional<KmiVersion> KmiVersion::Parse(const std::string& s) {
  if (s.size() > std::numeric_limits<int>::max()) return std::nullopt;
  int nchars = -1;
  KmiVersion ret;
  auto scan_res = sscanf(s.c_str(), KMI_VERSION_SCAN_FORMAT, &ret.version_, &ret.patch_level_,
                         &ret.release_, &ret.gen_, &nchars);
  if (scan_res != 4) return std::nullopt;
  if (nchars < 0) return std::nullopt;
  // Ensure the whole string is consumed.
  if (nchars != s.size()) return std::nullopt;
  return ret;
}

std::string KmiVersion::string() const {
  return android::base::StringPrintf(KMI_VERSION_PRINT_FORMAT, version(), patch_level(),
                                     android_release(), generation());
}

std::tuple<uint64_t, uint64_t, uint64_t, uint64_t> KmiVersion::tuple() const {
  return std::make_tuple(version(), patch_level(), android_release(), generation());
}

}  // namespace android::kver
