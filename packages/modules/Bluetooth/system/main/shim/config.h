/*
 * Copyright 2019 The Android Open Source Project
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

#include <cstdint>
#include <optional>
#include <string>
#include <vector>

namespace bluetooth {
namespace shim {

class BtifConfigInterface {
 public:
  ~BtifConfigInterface() = default;
  static bool HasSection(const std::string& section);
  static bool HasProperty(const std::string& section,
                          const std::string& property);
  static bool GetInt(const std::string& section, const std::string& key,
                     int* value);
  static bool SetInt(const std::string& section, const std::string& key,
                     int value);
  static bool GetUint64(const std::string& section, const std::string& key,
                        uint64_t* value);
  static bool SetUint64(const std::string& section, const std::string& key,
                        uint64_t value);
  static bool GetStr(const std::string& section, const std::string& key,
                     char* value, int* size_bytes);
  static std::optional<std::string> GetStr(const std::string& section,
                                           const std::string& key);
  static bool SetStr(const std::string& section, const std::string& key,
                     const std::string& value);
  static bool GetBin(const std::string& section, const std::string& key,
                     uint8_t* value, size_t* length);
  static size_t GetBinLength(const std::string& section,
                             const std::string& key);
  static bool SetBin(const std::string& section, const std::string& key,
                     const uint8_t* value, size_t length);
  static bool RemoveProperty(const std::string& section,
                             const std::string& key);
  static void RemoveSection(const std::string& section);
  static std::vector<std::string> GetPersistentDevices();
  static void ConvertEncryptOrDecryptKeyIfNeeded();
  static void Clear();
};

}  // namespace shim
}  // namespace bluetooth
