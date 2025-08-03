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

#include <sys/stat.h>

#include <fstream>
#include <string>

namespace netsim {
namespace filesystem {

#ifdef _WIN32
static const std::string slash = "\\";
#else
static const std::string slash = "/";
#endif

/**
 * Return if a path exists.
 */
inline bool exists(const std::string &name) {
  struct stat stat_buffer;
  return stat(name.c_str(), &stat_buffer) == 0;
}

/**
 * Return if a file exists.
 */
inline bool is_regular_file(const std::string &name) {
  // NOTE: Use fstream instead because 'S_ISREG'is undeclared identifier in
  // windows.
  std::ifstream f(name.c_str());
  return f.good();
}

}  // namespace filesystem
}  // namespace netsim
