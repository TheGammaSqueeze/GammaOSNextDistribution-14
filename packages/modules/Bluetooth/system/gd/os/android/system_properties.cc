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

#include "os/system_properties.h"

#include <cutils/properties.h>

#include <array>
#include <cctype>

#include "common/strings.h"
#include "os/log.h"

namespace bluetooth {
namespace os {

std::optional<std::string> GetSystemProperty(const std::string& property) {
  std::array<char, PROPERTY_VALUE_MAX> value_array{0};
  auto value_len = property_get(property.c_str(), value_array.data(), nullptr);
  if (value_len <= 0) {
    return std::nullopt;
  }
  return std::string(value_array.data(), value_len);
}

bool SetSystemProperty(const std::string& property, const std::string& value) {
  if (value.size() >= PROPERTY_VALUE_MAX) {
    LOG_ERROR("Property value's maximum size is %d, but %zu chars were given", PROPERTY_VALUE_MAX - 1, value.size());
    return false;
  }
  auto ret = property_set(property.c_str(), value.c_str());
  if (ret != 0) {
    LOG_ERROR("Set property %s failed with error code %d", property.c_str(), ret);
    return false;
  }
  return true;
}

bool IsRootCanalEnabled() {
  auto value = GetSystemProperty("ro.vendor.build.fingerprint");
  if (value.has_value()) {
    LOG_INFO("ro.vendor.build.fingerprint='%s', length=%zu", value->c_str(), value->length());
  } else {
    LOG_INFO("ro.vendor.build.fingerprint is not found");
  }
  // aosp_cf_x86_64_phone is just one platform that currently runs root canal
  // When other platforms appears, or there is a better signal, add them here
  if (value->find("generic/aosp_cf_x86_64_phone") == std::string::npos) {
    LOG_INFO("Not on generic/aosp_cf_x86_64_phone and hence not root canal");
    return false;
  }
  return true;
}

int GetAndroidVendorReleaseVersion() {
  auto value = GetSystemProperty("ro.vendor.build.version.release_or_codename");
  if (!value) {
    LOG_INFO("ro.vendor.build.version.release_or_codename does not exist");
    return 0;
  }
  LOG_INFO("ro.vendor.build.version.release_or_codename='%s', length=%zu", value->c_str(), value->length());
  auto int_value = common::Int64FromString(*value);
  if (int_value) {
    return static_cast<int>(*int_value);
  }
  LOG_INFO("value '%s' cannot be parsed to int", value->c_str());
  if (value->empty()) {
    LOG_INFO("value '%s' is empty", value->c_str());
    return 0;
  }
  if (value->length() > 1) {
    LOG_INFO("value '%s' length is %zu, which is > 1", value->c_str(), value->length());
  }
  char release_code = toupper(value->at(0));
  switch (release_code) {
    case 'S':
      return 11;
    case 'R':
      return 10;
    case 'P':
      return 9;
    case 'O':
      return 8;
    default:
      // Treble not enabled before Android O
      return 0;
  }
}

bool ClearSystemPropertiesForHost() {
  return false;
}

}  // namespace os
}  // namespace bluetooth