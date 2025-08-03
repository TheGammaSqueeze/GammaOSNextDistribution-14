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

#pragma once
// OS specific utility functions.

#include <memory>
#include <optional>
#include <string>

namespace netsim {
namespace osutils {

/**
 * Return the path containing runtime user files.
 */
std::string GetDiscoveryDirectory();

/**
 * Return the path of netsim ini file.
 */
std::string GetNetsimIniFilepath(uint16_t instance_num);

/**
 * Return the frontend grpc port.
 */
std::optional<std::string> GetServerAddress(uint16_t instance_num = 1);

/**
 * Redirect stdout and stderr to file.
 */
void RedirectStdStream(const std::string &netsim_temp_dir,
                       const std::string &instance_name);

}  // namespace osutils
}  // namespace netsim
