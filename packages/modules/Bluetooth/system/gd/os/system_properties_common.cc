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

#include <string>

#include "common/strings.h"
#include "os/system_properties.h"

namespace bluetooth {
namespace os {

uint32_t GetSystemPropertyUint32(const std::string& property, uint32_t default_value) {
  return GetSystemPropertyUint32Base(property, default_value, 10);
}

uint32_t GetSystemPropertyUint32Base(
    const std::string& property, uint32_t default_value, int base) {
  std::optional<std::string> result = GetSystemProperty(property);
  if (result.has_value()) {
    return static_cast<uint32_t>(std::stoul(*result, nullptr, base));
  }
  return default_value;
}

bool GetSystemPropertyBool(const std::string& property, bool default_value) {
  std::optional<std::string> result = GetSystemProperty(property);
  if (result.has_value()) {
    std::string trimmed_val = common::StringTrim(result.value());
    if (trimmed_val == "true" || trimmed_val == "1") {
      return true;
    }
    if (trimmed_val == "false" || trimmed_val == "0") {
      return false;
    }
  }
  return default_value;
}

}  // namespace os
}  // namespace bluetooth
