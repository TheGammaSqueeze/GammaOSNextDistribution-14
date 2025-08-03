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

#include "linkerconfig/configwriter.h"

#include "linkerconfig/log.h"
#include "linkerconfig/variables.h"

namespace android {
namespace linkerconfig {
namespace modules {

ConfigWriter::ConfigWriter() noexcept {
  content_.reserve(4096);
}

void ConfigWriter::WriteVars(const std::string& var,
                             const std::vector<std::string>& values) {
  bool is_first = true;
  for (const auto& value : values) {
    content_ += var;
    content_ += (is_first ? " = " : " += ");
    content_ += value;
    content_ += "\n";
    is_first = false;
  }
}

void ConfigWriter::WriteVars(const std::string& var,
                             const std::vector<std::string>& values,
                             const std::string& suffix) {
  bool is_first = true;
  for (const auto& value : values) {
    content_ += var;
    content_ += (is_first ? " = " : " += ");
    content_ += value;
    content_ += suffix;
    content_ += "\n";
    content_ += var;
    content_ += " += ";
    content_ += value;
    content_ += "\n";
    is_first = false;
  }
}

void ConfigWriter::WriteVar(const std::string& var, const std::string& value) {
  if (!value.empty()) {
    content_ += var;
    content_ += " = ";
    content_ += value;
    content_ += "\n";
  }
}

void ConfigWriter::WriteLine(const std::string& line) {
  content_ += line;
  content_ += "\n";
}

std::string ConfigWriter::ToString() {
  return content_;
}
}  // namespace modules
}  // namespace linkerconfig
}  // namespace android