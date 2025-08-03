/*
 * Copyright (C) 2019 The Android Open Source Project
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

#include <string>
#include <utility>
#include <vector>

#include "linkerconfig/configwriter.h"

namespace android {
namespace linkerconfig {
namespace modules {
class Link {
 public:
  Link(std::string origin_namespace, std::string target_namespace)
      : origin_namespace_(std::move(origin_namespace)),
        target_namespace_(std::move(target_namespace)) {
  }
  Link(const Link&) = delete;
  Link& operator=(const Link&) = delete;
  Link(Link&&) = default;
  Link& operator=(Link&&) = default;

  template <typename... Args>
  void AddSharedLib(const std::string& lib_name, Args&&... lib_names);
  void AddSharedLib(const std::vector<std::string>& lib_names);
  void AllowAllSharedLibs();
  void WriteConfig(ConfigWriter& writer) const;

  bool IsAllSharedLibsAllowed() const {
    return allow_all_shared_libs_;
  }

  // accessors
  bool Empty() const {
    return !allow_all_shared_libs_ && shared_libs_.empty();
  }
  std::vector<std::string> GetSharedLibs() const {
    return shared_libs_;
  }
  std::string To() const {
    return target_namespace_;
  }

 private:
  std::string origin_namespace_;
  std::string target_namespace_;
  std::vector<std::string> shared_libs_;
  bool allow_all_shared_libs_ = false;
};

template <typename... Args>
void Link::AddSharedLib(const std::string& lib_name, Args&&... lib_names) {
  if (!allow_all_shared_libs_) {
    shared_libs_.push_back(lib_name);
    if constexpr (sizeof...(Args) > 0) {
      AddSharedLib(std::forward<Args>(lib_names)...);
    }
  }
}
}  // namespace modules
}  // namespace linkerconfig
}  // namespace android
