/*
 * Copyright 2022 The Android Open Source Project
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

#include "test_util.h"

#include <base/files/file_util.h>

namespace bluetooth {
namespace testing {

base::FilePath GetBinaryPath() {
  base::FilePath binary_path;
  base::ReadSymbolicLink(base::FilePath("/proc/self/exe"), &binary_path);
  return binary_path.DirName();
}

std::string GetWavFilePath(const std::string& relative_path) {
  return GetBinaryPath().Append(relative_path).value();
}

}  // namespace testing
}  // namespace bluetooth
