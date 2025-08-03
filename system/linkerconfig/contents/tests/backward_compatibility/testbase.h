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

#include <android-base/strings.h>

#include <algorithm>

#include "linkerconfig/namespace.h"
#include "linkerconfig/variables.h"

inline void MockVndkVariables(const std::string& partition,
                              const std::string& vndk_ver) {
  using android::linkerconfig::modules::Variables;

  Variables::AddValue(partition + "_VNDK_VERSION", vndk_ver);
  Variables::AddValue("LLNDK_LIBRARIES_" + partition, "llndk_libraries");
  Variables::AddValue("PRIVATE_LLNDK_LIBRARIES_" + partition,
                      "private_llndk_libraries");
  Variables::AddValue("VNDK_SAMEPROCESS_LIBRARIES_" + partition,
                      "vndk_sameprocess_libraries");
  Variables::AddValue("VNDK_CORE_LIBRARIES_" + partition, "vndk_core_libraries");
  Variables::AddValue("SANITIZER_DEFAULT_" + partition,
                      "sanitizer_default_libraries");
}

inline void MockVariables(std::string vndk_ver = "Q") {
  using android::linkerconfig::modules::Variables;

  MockVndkVariables("VENDOR", vndk_ver);
  Variables::AddValue("ro.vndk.version", vndk_ver);

  MockVndkVariables("PRODUCT", vndk_ver);
  Variables::AddValue("ro.product.vndk.version", vndk_ver);

  Variables::AddValue("ro.treble.enabled", "true");

  Variables::AddValue("SYSTEM_EXT", "/system_ext");
  Variables::AddValue("PRODUCT", "/procut");

  Variables::AddValue("VNDK_USING_CORE_VARIANT_LIBRARIES",
                      "vndk_using_core_variant_libraries");
  Variables::AddValue("SANITIZER_RUNTIME_LIBRARIES",
                      "sanitizer_runtime_libraries");
}

inline bool ContainsPath(const std::vector<std::string>& path_list,
                         const std::string& path) {
  return std::any_of(path_list.begin(),
                     path_list.end(),
                     [&](const std::string& item) { return item == path; });
}

inline bool ContainsSearchPath(
    const android::linkerconfig::modules::Namespace* ns,
    const std::string& path) {
  if (!ContainsPath(ns->SearchPaths(), path)) {
    return false;
  }

  auto asan_search_path = ns->AsanSearchPaths();

  if (!ContainsPath(asan_search_path, path)) {
    return false;
  }

  if (!android::base::StartsWith(path, "/apex") &&
      !ContainsPath(asan_search_path, "/data/asan" + path)) {
    return false;
  }

  return true;
}

inline bool ContainsPermittedPath(
    const android::linkerconfig::modules::Namespace* ns,
    const std::string& path) {
  if (!ContainsPath(ns->PermittedPaths(), path)) {
    return false;
  }

  auto asan_search_path = ns->AsanPermittedPaths();

  if (!ContainsPath(asan_search_path, path)) {
    return false;
  }

  if (!android::base::StartsWith(path, "/apex") &&
      !ContainsPath(asan_search_path, "/data/asan" + path)) {
    return false;
  }

  return true;
}