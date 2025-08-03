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

#pragma once

#include <stdint.h>

#include <optional>
#include <string>

#include <kver/kmi_version.h>

namespace android::kver {

// Kernel release is a unique identifier of a GKI binary.
// Example: 5.4.42-android12-0
class KernelRelease {
 public:
  // Parse a string like "5.4.42-android12-0" to a KernelRelease object.
  // If allow_suffix is set, allow strings like "5.4.42-android12-0-something".
  // Suffix is discarded and not stored in this object.
  // Return nullopt if any error.
  static std::optional<KernelRelease> Parse(const std::string& s, bool allow_suffix = false);

  // Return string representation of the kernel release object, excluding the suffix.
  // e.g. "5.4.42-android12-0".
  // To get the KMI version string, see kmi_version().string().
  std::string string() const;

  // Return the KMI version corresponding to this KernelRelease.
  const KmiVersion& kmi_version() const { return kmi_version_; }

  // Getters of each field.
  uint64_t version() const { return kmi_version().version(); }
  uint64_t patch_level() const { return kmi_version().patch_level(); }
  uint64_t sub_level() const { return sub_level_; }
  uint64_t android_release() const { return kmi_version().android_release(); }
  uint64_t generation() const { return kmi_version().generation(); }

  // Let a kernel release be w.x.y-androidz-k.
  // Returns (w, x, y)
  std::tuple<uint64_t, uint64_t, uint64_t> kernel_version_tuple() const;

 private:
  KernelRelease() = default;
  KmiVersion kmi_version_;
  uint64_t sub_level_ = 0;
};

}  // namespace android::kver
