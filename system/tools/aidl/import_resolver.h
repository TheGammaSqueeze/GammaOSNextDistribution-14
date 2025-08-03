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

#pragma once

#include <set>
#include <string>

#include "io_delegate.h"

namespace android {
namespace aidl {

class ImportResolver {
 public:
  ImportResolver(const IoDelegate& io_delegate, const std::string& input_file_name,
                 const std::set<std::string>& import_paths);
  virtual ~ImportResolver() = default;

  // non-copyable, non-movable
  ImportResolver(const ImportResolver&) = delete;
  ImportResolver(ImportResolver&&) = delete;
  ImportResolver& operator=(const ImportResolver&) = delete;
  ImportResolver& operator=(ImportResolver&&) = delete;

  // Resolve the canonical name for a class to a file that exists
  // in one of the import paths given to the ImportResolver.
  std::string FindImportFile(const std::string& canonical_name) const;

 private:
  std::set<std::string> ScanImportPaths(const std::string& relative_path) const;

  const IoDelegate& io_delegate_;
  const std::string& input_file_name_;
  std::set<std::string> import_paths_;
};

}  // namespace aidl
}  // namespace android
