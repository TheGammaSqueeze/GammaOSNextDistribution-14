// Copyright (C) 2021 The Android Open Source Project
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

#include <vector>

#include <ditto/logger.h>
#include <ditto/read_directory.h>
#include <ditto/shared_variables.h>

namespace dittosuite {

ReadDirectory::ReadDirectory(const Params& params, const std::string& directory_name,
                             int output_key)
    : Instruction(kName, params),
      directory_name_(GetAbsolutePath() + directory_name),
      output_key_(output_key) {}

void ReadDirectory::RunSingle() {
  std::vector<std::string> output;

  DIR* directory = syscall_.OpenDir(directory_name_);

  if (directory == nullptr) {
    PLOGF("Cannot open \"" + directory_name_ + "\"");
  }

  struct dirent* entry;
  while ((entry = syscall_.ReadDir(directory)) != nullptr) {
    // Only collect regular files
    if (entry->d_type == DT_REG) {
      std::string path_name = directory_name_;
      // Add a slash if the current directory name does not end with a slash
      if (!path_name.empty() && path_name.back() != '/') {
        path_name += "/";
      }
      path_name += entry->d_name;
      output.push_back(path_name);
    }
  }
  SharedVariables::Set(output_key_, output);

  syscall_.CloseDir(directory);
}

}  // namespace dittosuite
