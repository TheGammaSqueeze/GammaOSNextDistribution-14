/******************************************************************************
 *
 *  Copyright 2016 Google, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at:
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/

#include "osi/include/properties.h"

#include <string.h>

#include <algorithm>
#include <optional>
#include <string>

#include "os/system_properties.h"

#ifdef __ANDROID__
#undef PROPERTY_VALUE_MAX
#include <cutils/properties.h>
#if BUILD_SANITY_PROPERTY_VALUE_MAX != PROPERTY_VALUE_MAX
#error "PROPERTY_VALUE_MAX from osi/include/properties.h != the Android value"
#endif  // GENERIC_PROPERTY_VALUE_MAX != PROPERTY_VALUE_MAX
#endif  // __ANDROID__

int osi_property_get(const char* key, char* value, const char* default_value) {
  std::optional<std::string> result = bluetooth::os::GetSystemProperty(key);
  if (result) {
    memcpy(value, result->data(), result->size());
    value[result->size()] = '\0';
    return result->size();
  } else if (default_value) {
    int len = std::min(strlen(default_value), (size_t)(PROPERTY_VALUE_MAX - 1));
    memcpy(value, default_value, len);
    value[len] = '\0';
    return len;
  } else {
    return 0;
  }
}

int osi_property_set(const char* key, const char* value) {
  bool success = bluetooth::os::SetSystemProperty(key, value);
  return success ? 0 : -1;
}

int32_t osi_property_get_int32(const char* key, int32_t default_value) {
  std::optional<std::string> result = bluetooth::os::GetSystemProperty(key);
  if (result) {
    return stoi(*result, nullptr);
  } else {
    return default_value;
  }
}

bool osi_property_get_bool(const char* key, bool default_value) {
  std::optional<std::string> result = bluetooth::os::GetSystemProperty(key);
  if (result) {
    return *result == std::string("true");
  } else {
    return default_value;
  }
}

std::vector<uint32_t> osi_property_get_uintlist(
    const char* key, const std::vector<uint32_t> default_value) {
  std::optional<std::string> result = bluetooth::os::GetSystemProperty(key);
  if (!result || result->empty() || result->size() > PROPERTY_VALUE_MAX) {
    return default_value;
  }

  std::vector<uint32_t> list;
  for (size_t i = 0; i < result->size(); i++) {
    // Build a string of all the chars until the next comma or end of the
    // string is reached. If any char is not a digit, then return the default.
    std::string value;
    while ((*result)[i] != ',' && i < result->size()) {
      char c = (*result)[i];
      if (!std::isdigit(c)) {
        return default_value;
      }
      value += c;
      i++;
    }

    // grab value
    list.push_back(static_cast<uint32_t>(std::stoul(value)));
  }

  return list;
}
