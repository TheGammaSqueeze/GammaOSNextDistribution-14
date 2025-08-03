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

#include "linkerconfig/librarylistloader.h"

#include <android-base/result.h>
#include <android-base/strings.h>
#include <algorithm>
#include <fstream>
#include <iterator>
#include <set>
#include <sstream>
#include <unordered_map>

#include "linkerconfig/environment.h"
#include "linkerconfig/log.h"

using LibraryList = std::set<std::string>;

using android::base::Result;

namespace {
std::unordered_map<std::string, LibraryList> library_file_cache;
Result<LibraryList> GetLibrariesFromFile(std::string file_path) {
  auto cached_data = library_file_cache.find(file_path);
  if (cached_data != library_file_cache.end()) {
    return cached_data->second;
  }

  std::string library_name;
  LibraryList library_list;
  std::ifstream library_file(file_path.c_str(), std::ifstream::in);

  if (!library_file) {
    return ErrnoErrorf("Failed to open file {}", file_path);
  }

  while (std::getline(library_file, library_name)) {
    library_name = android::base::Trim(library_name);
    if (!library_name.empty()) {
      library_list.insert(library_name);
    }
  }

  library_file_cache.insert({file_path, library_list});

  return library_list;
}
}  // namespace

namespace android {
namespace linkerconfig {
namespace generator {
std::string GetLibrariesString(const std::string& library_file_path) {
  auto library_list_result = GetLibrariesFromFile(library_file_path);
  if (library_list_result.ok()) {
    return android::base::Join(*library_list_result, ':');
  } else {
    // Consider unavailable library file as empty
    LOG(WARNING) << library_list_result.error();
    return "";
  }
}

std::string GetPublicLibrariesString(
    const std::string& library_file_path,
    const std::string& private_library_file_path) {
  auto library_list = GetLibrariesFromFile(library_file_path);
  auto private_library_list = GetLibrariesFromFile(private_library_file_path);

  if (!library_list.ok()) {
    // Consider unavailable library file as empty
    LOG(WARNING) << library_list.error();
    return "";
  }

  if (!private_library_list.ok()) {
    // No private library found. All libraries are public
    LOG(WARNING) << private_library_list.error();
    return android::base::Join(*library_list, ':');
  }

  LibraryList public_library_list;

  std::set_difference(
      library_list->begin(),
      library_list->end(),
      private_library_list->begin(),
      private_library_list->end(),
      std::inserter(public_library_list, public_library_list.begin()));

  return android::base::Join(public_library_list, ':');
}

std::string GetPrivateLibrariesString(
    const std::string& library_file_path,
    const std::string& private_library_file_path) {
  auto library_list = GetLibrariesFromFile(library_file_path);
  auto private_library_list = GetLibrariesFromFile(private_library_file_path);

  if (!library_list.ok()) {
    // Consider unavailable library file as empty
    LOG(WARNING) << library_list.error();
    return "";
  }

  if (!private_library_list.ok()) {
    // No private library found. All libraries are public
    LOG(WARNING) << private_library_list.error();
    return "";
  }

  LibraryList private_only_library_list;

  std::set_intersection(library_list->begin(),
                        library_list->end(),
                        private_library_list->begin(),
                        private_library_list->end(),
                        std::inserter(private_only_library_list,
                                      private_only_library_list.begin()));

  return android::base::Join(private_only_library_list, ':');
}
}  // namespace generator
}  // namespace linkerconfig
}  // namespace android
