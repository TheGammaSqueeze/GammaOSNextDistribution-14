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
#include <tuple>

namespace android::kver {

// KMI version describes the version of the stable kernel module interface.
// Example: 5.4-android12-0
class KmiVersion {
 public:
  // Parse a string like "5.4-android12-0" to a KmiVersion object.
  // Return nullopt if any error.
  static std::optional<KmiVersion> Parse(const std::string& s);

  // String representation of the KMI version object.
  // e.g. "5.4-android12-0"
  std::string string() const;

  // Getters of each field.
  uint64_t version() const { return version_; }
  uint64_t patch_level() const { return patch_level_; }
  uint64_t android_release() const { return release_; }
  uint64_t generation() const { return gen_; }

  // Let a KMI version be w.x-androidz-k.
  // Returns (w, x, z, k).
  std::tuple<uint64_t, uint64_t, uint64_t, uint64_t> tuple() const;

 private:
  friend class KernelRelease;
  KmiVersion() = default;
  uint64_t version_ = 0;
  uint64_t patch_level_ = 0;
  uint64_t release_ = 0;
  uint64_t gen_ = 0;
};

inline bool operator==(const KmiVersion& left, const KmiVersion& right) {
  return left.tuple() == right.tuple();
}

inline bool operator!=(const KmiVersion& left, const KmiVersion& right) {
  return left.tuple() != right.tuple();
}

}  // namespace android::kver
