// Copyright 2022 The Android Open Source Project
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

#include "util/ini_file.h"

#include <fstream>
#include <iostream>
#include <string>
#include <string_view>

#include "util/log.h"
#include "util/string_utils.h"

namespace netsim {

bool IniFile::Read() {
  data.clear();

  if (filepath.empty()) {
    BtsLogWarn("Read called without a backing ini file!");
    return false;
  }

  std::ifstream inFile(filepath);

  if (!inFile) {
    BtsLogWarn("Failed to process .ini file %s for reading.", filepath.c_str());
    return false;
  }
  std::string line;
  while (std::getline(inFile, line)) {
    auto argv = stringutils::Split(line, "=");

    if (argv.size() != 2) continue;
    auto key = stringutils::Trim(argv[0]);
    auto val = stringutils::Trim(argv[1]);
    data.emplace(key, val);
  }
  return true;
}

bool IniFile::Write() {
  if (filepath.empty()) {
    BtsLogWarn("Write called without a backing ini file!");
    return false;
  }

  std::ofstream outFile(filepath);

  if (!outFile) {
    BtsLogWarn("Failed to open .ini file %s for writing.", filepath.c_str());
    return false;
  }

  for (const auto &pair : data) {
    outFile << pair.first << "=" << pair.second << std::endl;
  }
  return true;
}

bool IniFile::HasKey(const std::string &key) const {
  return data.find(key) != std::end(data);
}

std::optional<std::string> IniFile::Get(const std::string &key) const {
  auto citer = data.find(key);
  return (citer == std::end(data)) ? std::nullopt
                                   : std::optional(citer->second);
}

void IniFile::Set(const std::string &key, std::string_view value) {
  data[key] = std::string(value);
}

}  // namespace netsim
