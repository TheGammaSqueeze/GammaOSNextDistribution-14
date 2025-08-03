/*
 * Copyright (C) 2021 The Android Open Source Project
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
#include <string_view>
#include <vector>

namespace android {
namespace derive_classpath {

constexpr std::string_view kGeneratedClasspathExportsFilepath = "/data/system/environ/classpath";

struct Args {
  std::string_view output_path;

  // Alternative *classpath.pb files if provided.
  std::string system_bootclasspath_fragment;
  std::string system_systemserverclasspath_fragment;

  // Test only. glob_pattern_prefix is appended to each glob pattern to allow adding mock configs in
  // /data/local/tmp for example.
  std::string glob_pattern_prefix;

  // Scan specified list of directories instead of using default glob patterns
  std::vector<std::string> scan_dirs;
};

bool GenerateClasspathExports(const Args& args);

}  // namespace derive_classpath
}  // namespace android
