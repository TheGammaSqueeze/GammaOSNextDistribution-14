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
#include <kver/kernel_release.h>

namespace android::kver {

#define KERNEL_RELEASE_PRINT_FORMAT \
  "%" PRIu64 ".%" PRIu64 ".%" PRIu64 "-android%" PRIu64 "-%" PRIu64
#define KERNEL_RELEASE_SCAN_FORMAT KERNEL_RELEASE_PRINT_FORMAT "%n"

// Not taking a string_view because sscanf requires null-termination.
std::optional<KernelRelease> KernelRelease::Parse(const std::string& s, bool allow_suffix) {
  if (s.size() > std::numeric_limits<int>::max()) return std::nullopt;
  int nchars = -1;
  KernelRelease ret;
  auto scan_res = sscanf(s.c_str(), KERNEL_RELEASE_SCAN_FORMAT, &ret.kmi_version_.version_,
                         &ret.kmi_version_.patch_level_, &ret.sub_level_,
                         &ret.kmi_version_.release_, &ret.kmi_version_.gen_, &nchars);
  if (scan_res != 5) return std::nullopt;
  if (nchars < 0) return std::nullopt;
  // If !allow_suffix, ensure the whole string is consumed.
  if (!allow_suffix && nchars != s.size()) return std::nullopt;
  return ret;
}

std::string KernelRelease::string() const {
  return android::base::StringPrintf(KERNEL_RELEASE_PRINT_FORMAT, version(), patch_level(),
                                     sub_level(), android_release(), generation());
}

std::tuple<uint64_t, uint64_t, uint64_t> KernelRelease::kernel_version_tuple() const {
  return std::make_tuple(version(), patch_level(), sub_level());
}

}  // namespace android::kver
