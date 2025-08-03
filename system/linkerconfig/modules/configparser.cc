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

#include "linkerconfig/configparser.h"

#include <android-base/file.h>
#include <android-base/strings.h>
#include <linker_config.pb.h>

#include "linkerconfig/log.h"

using android::base::ReadFileToString;
using android::base::Result;
using ::android::linkerconfig::proto::LinkerConfig;

namespace android {
namespace linkerconfig {
namespace modules {

// Format of the file can be found from README.md file
Result<LinkerConfig> ParseLinkerConfig(const std::string& config_path) {
  std::string file_content;

  if (!ReadFileToString(config_path, &file_content)) {
    return ErrnoErrorf("Cannot read file {}", config_path);
  }

  LinkerConfig config;
  if (!config.ParseFromString(file_content)) {
    return Errorf("Failed to parse file {}", config_path);
  }
  return config;
}

}  // namespace modules
}  // namespace linkerconfig
}  // namespace android
