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

#include <set>
#include <string>
#include <vector>

#include <android-base/result.h>

#include "linkerconfig/apex.h"
#include "linkerconfig/configwriter.h"
#include "linkerconfig/link.h"

namespace android {
namespace linkerconfig {
namespace modules {

struct ApexSource {
  std::string name;
  bool in_vendor;
};

class Namespace {
 public:
  explicit Namespace(std::string name, bool is_isolated = false,
                     bool is_visible = false)
      : is_isolated_(is_isolated),
        is_visible_(is_visible),
        name_(std::move(name)) {
  }

  Namespace(const Namespace& ns) = delete;
  Namespace(Namespace&& ns) = default;
  Namespace& operator=(Namespace&& ns) = default;

  // Add path to search path
  void AddSearchPath(const std::string& path);

  // Add path to permitted path
  void AddPermittedPath(const std::string& path);

  // Returns a link from this namespace to the given one. If one already exists
  // it is returned, otherwise one is created and pushed back to tail.
  Link& GetLink(const std::string& target_namespace);

  void WriteConfig(ConfigWriter& writer) const;
  void AddAllowedLib(const std::string& path);

  std::string GetName() const;

  void SetVisible(bool visible) {
    is_visible_ = visible;
  }
  bool IsVisible() const {
    return is_visible_;
  }

  void SetApexSource(ApexSource source) {
    apex_source_ = source;
  }

  const ApexSource& GetApexSource() const {
    return apex_source_;
  }

  // For test usage
  const std::vector<Link>& Links() const {
    return links_;
  }
  std::vector<std::string> SearchPaths() const {
    return search_paths_;
  }
  std::vector<std::string> PermittedPaths() const {
    return permitted_paths_;
  }
  std::vector<std::string> AsanSearchPaths() const {
    return asan_search_paths_;
  }
  std::vector<std::string> AsanPermittedPaths() const {
    return asan_permitted_paths_;
  }

  template <typename Vec>
  void AddProvides(const Vec& list) {
    provides_.insert(list.begin(), list.end());
  }
  template <typename Vec>
  void AddRequires(const Vec& list) {
    requires_.insert(list.begin(), list.end());
  }
  const std::set<std::string>& GetProvides() const {
    return provides_;
  }
  const std::set<std::string>& GetRequires() const {
    return requires_;
  }

 private:
  bool is_isolated_;
  bool is_visible_;
  std::string name_;
  std::vector<std::string> search_paths_;
  std::vector<std::string> permitted_paths_;
  std::vector<std::string> asan_search_paths_;
  std::vector<std::string> asan_permitted_paths_;
  std::vector<std::string> allowed_libs_;
  std::vector<Link> links_;
  std::set<std::string> provides_;
  std::set<std::string> requires_;
  // Verification context
  ApexSource apex_source_;

  void WritePathString(ConfigWriter& writer, const std::string& path_type,
                       const std::vector<std::string>& path_list);
  bool RequiresAsanPath(const std::string& path);
  const std::string CreateAsanPath(const std::string& path);
  android::base::Result<void> VerifyContents() const;
};

void InitializeWithApex(Namespace& ns, const ApexInfo& apex_info);
}  // namespace modules
}  // namespace linkerconfig
}  // namespace android
