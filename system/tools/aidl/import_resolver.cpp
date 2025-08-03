/*
 * Copyright (C) 2015, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "import_resolver.h"
#include "aidl_language.h"
#include "logging.h"

#include <algorithm>

#include <android-base/file.h>
#include <android-base/strings.h>
#include <unistd.h>

#ifdef _WIN32
#include <io.h>
#endif

#include "os.h"

using std::set;
using std::string;
using std::vector;

namespace android {
namespace aidl {

ImportResolver::ImportResolver(const IoDelegate& io_delegate, const string& input_file_name,
                               const set<string>& import_paths)
    : io_delegate_(io_delegate), input_file_name_(input_file_name) {
  for (string path : import_paths) {
    if (path.empty()) {
      path = ".";
    }
    if (path[path.size() - 1] != OS_PATH_SEPARATOR) {
      path += OS_PATH_SEPARATOR;
    }
    import_paths_.emplace(std::move(path));
  }
}

string ImportResolver::FindImportFile(const string& canonical_name) const {
  auto parts = base::Split(canonical_name, ".");
  while (!parts.empty()) {
    string relative_path = base::Join(parts, OS_PATH_SEPARATOR) + ".aidl";
    auto candidates = ScanImportPaths(relative_path);
    if (candidates.size() == 0) {
      // remove the last part & keep searching
      parts.pop_back();
      continue;
    }
    if (candidates.size() == 1) {
      // found! ("legacy" logic: may still be ambiguous path if we try other relative paths)
      return *candidates.begin();
    }
    if (candidates.size() > 1) {
      AIDL_ERROR(input_file_name_) << "Duplicate files found for " << canonical_name << " from:\n"
                                   << base::Join(candidates, "\n");
      return "";
    }
  }
  AIDL_ERROR(input_file_name_) << "Couldn't find import for class " << canonical_name
                               << ". Searched here:\n - " << base::Join(import_paths_, "\n - ");
  return "";
}

set<string> ImportResolver::ScanImportPaths(const string& relative_path) const {
  // Look for that relative path at each of our import roots.
  set<string> found;
  for (const auto& path : import_paths_) {
    if (io_delegate_.FileIsReadable(path + relative_path)) {
      found.emplace(path + relative_path);
    }
  }
  return found;
}

}  // namespace aidl
}  // namespace android
