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

#include "linkerconfig/namespace.h"

#include <android-base/strings.h>

#include "linkerconfig/apex.h"
#include "linkerconfig/environment.h"
#include "linkerconfig/log.h"

using android::base::Result;

namespace {
constexpr const char* kDataAsanPath = "/data/asan";

Result<void> VerifyIfApexNamespaceContainsAllSharedLink(
    const android::linkerconfig::modules::Namespace& ns) {
  auto apex = ns.GetApexSource();
  // If namespace is not from APEX there is no need to check this.
  // Vendor apexes are allowed to use 'allow_all_shared_libs'.
  if (apex.name == "" || apex.in_vendor) {
    return {};
  }

  const auto& links = ns.Links();
  for (const auto& link : links) {
    if (link.IsAllSharedLibsAllowed()) {
      return Errorf(
          "APEX namespace {} is not allowed to have link with all shared libs "
          "allowed.",
          ns.GetName());
    }
  }
  return {};
}

}  // namespace

namespace android {
namespace linkerconfig {
namespace modules {

void InitializeWithApex(Namespace& ns, const ApexInfo& apex_info) {
  ns.AddSearchPath(apex_info.path + "/${LIB}");
  if (apex_info.InVendor()) {
    // Adding an additional subdir(hw) to make the migration easier because
    // many vendor modules today are installed in ./hw subdir.
    ns.AddSearchPath(apex_info.path + "/${LIB}/hw");
  }
  ns.AddPermittedPath(apex_info.path + "/${LIB}");
  ns.AddPermittedPath("/system/${LIB}");
  ns.AddPermittedPath("/system_ext/${LIB}");
  for (const auto& permitted_path : apex_info.permitted_paths) {
    ns.AddPermittedPath(permitted_path);
  }
  if (apex_info.has_shared_lib) {
    ns.AddPermittedPath("/apex");
  }
  ns.AddProvides(apex_info.provide_libs);
  ns.AddRequires(apex_info.require_libs);
  ns.SetApexSource(ApexSource{apex_info.name, apex_info.InVendor()});
}

Link& Namespace::GetLink(const std::string& target_namespace) {
  for (auto& link : links_) {
    if (link.To() == target_namespace) {
      return link;
    }
  }
  return links_.emplace_back(name_, target_namespace);
}

void Namespace::WriteConfig(ConfigWriter& writer) const {
  auto verify_result = VerifyContents();
  if (!verify_result.ok()) {
    LOG(ERROR) << "Namespace " << name_
               << " is not valid : " << verify_result.error();
    return;
  }

  const auto prefix = "namespace." + name_ + ".";

  writer.WriteLine(prefix + "isolated = " + (is_isolated_ ? "true" : "false"));

  if (is_visible_) {
    writer.WriteLine(prefix + "visible = true");
  }

  writer.WriteVars(prefix + "search.paths", search_paths_);
  writer.WriteVars(prefix + "permitted.paths", permitted_paths_);
  writer.WriteVars(prefix + "asan.search.paths", asan_search_paths_);
  writer.WriteVars(prefix + "asan.permitted.paths", asan_permitted_paths_);
  writer.WriteVars(prefix + "hwasan.search.paths", search_paths_, "/hwasan");
  writer.WriteVars(
      prefix + "hwasan.permitted.paths", permitted_paths_, "/hwasan");
  writer.WriteVars(prefix + "allowed_libs", allowed_libs_);

  std::vector<std::string> link_list;
  link_list.reserve(links_.size());
  for (const auto& link : links_) {
    if (link.Empty()) continue;
    if (link.To() == name_) {
      LOG(WARNING) << "Ignore link to self namespace : " << name_;
      continue;
    }
    link_list.push_back(link.To());
  }
  if (!link_list.empty()) {
    writer.WriteLine(prefix + "links = " + android::base::Join(link_list, ","));
    for (const auto& link : links_) {
      if (link.Empty()) continue;
      if (link.To() == name_) continue;
      link.WriteConfig(writer);
    }
  }
}

void Namespace::AddSearchPath(const std::string& path) {
  search_paths_.push_back(path);

  if (RequiresAsanPath(path)) {
    asan_search_paths_.push_back(CreateAsanPath(path));
  }
  asan_search_paths_.push_back(path);
}

void Namespace::AddPermittedPath(const std::string& path) {
  permitted_paths_.push_back(path);

  if (RequiresAsanPath(path)) {
    asan_permitted_paths_.push_back(CreateAsanPath(path));
  }
  asan_permitted_paths_.push_back(path);
}

void Namespace::AddAllowedLib(const std::string& path) {
  allowed_libs_.push_back(path);
}

std::string Namespace::GetName() const {
  return name_;
}

bool Namespace::RequiresAsanPath(const std::string& path) {
  return !android::base::StartsWith(path, "/apex");
}

const std::string Namespace::CreateAsanPath(const std::string& path) {
  return kDataAsanPath + path;
}

Result<void> Namespace::VerifyContents() const {
  auto apex_with_all_shared_link =
      VerifyIfApexNamespaceContainsAllSharedLink(*this);
  if (!apex_with_all_shared_link.ok()) {
    return apex_with_all_shared_link.error();
  }

  return {};
}

}  // namespace modules
}  // namespace linkerconfig
}  // namespace android
