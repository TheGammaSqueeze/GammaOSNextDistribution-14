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

#include <optional>
#include <string>
#include <string_view>
#include <unordered_map>

namespace netsim {

// A simple class to process init file. Modified from
// external/qemu/android/android-emu-base/android/base/files/IniFile.h
class IniFile {
 public:
  // Note that the constructor _does not_ read data from the backing file.
  // Call |Read| to read the data.
  explicit IniFile(std::string filepath = "") : filepath(std::move(filepath)) {}

  // Reads data into IniFile from the backing file, overwriting any
  // existing data.
  bool Read();

  // Writes the current IniFile to the backing file.
  bool Write();

  // Checks if a certain key exists in the file.
  bool HasKey(const std::string &key) const;

  // Gets value.
  std::optional<std::string> Get(const std::string &key) const;

  // Sets value.
  void Set(const std::string &key, std::string_view value);

 private:
  std::unordered_map<std::string, std::string> data;
  std::string filepath;
};

}  // namespace netsim
