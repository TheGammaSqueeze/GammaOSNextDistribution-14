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
#pragma once

#include <algorithm>
#include <map>
#include <string>
#include <vector>

#include <android-base/result.h>

namespace android {
namespace linkerconfig {
namespace modules {
struct ApexInfo {
  std::string name;
  std::string namespace_name;
  std::string path;
  std::string original_path;
  std::vector<std::string> provide_libs;
  std::vector<std::string> require_libs;
  std::vector<std::string> jni_libs;
  std::vector<std::string> permitted_paths;
  std::vector<std::string> public_libs;
  bool has_bin;
  bool has_lib;
  bool visible;
  bool has_shared_lib;

  ApexInfo() = default;  // for std::map::operator[]
  ApexInfo(std::string name, std::string path,
           std::vector<std::string> provide_libs,
           std::vector<std::string> require_libs,
           std::vector<std::string> jni_libs,
           std::vector<std::string> permitted_paths, bool has_bin, bool has_lib,
           bool visible, bool has_shared_lib)
      : name(std::move(name)),
        path(std::move(path)),
        provide_libs(std::move(provide_libs)),
        require_libs(std::move(require_libs)),
        jni_libs(std::move(jni_libs)),
        permitted_paths(std::move(permitted_paths)),
        has_bin(has_bin),
        has_lib(has_lib),
        visible(visible),
        has_shared_lib(has_shared_lib) {
    this->namespace_name = this->name;
    std::replace(
        this->namespace_name.begin(), this->namespace_name.end(), '.', '_');
  }

  bool InSystem() const;
  bool InProduct() const;
  bool InVendor() const;
};

android::base::Result<std::map<std::string, ApexInfo>> ScanActiveApexes(
    const std::string& root);
}  // namespace modules
}  // namespace linkerconfig
}  // namespace android