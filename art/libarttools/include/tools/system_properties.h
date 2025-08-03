/*
 * Copyright (C) 2022 The Android Open Source Project
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

#ifndef ART_LIBARTTOOLS_INCLUDE_TOOLS_SYSTEM_PROPERTIES_H_
#define ART_LIBARTTOOLS_INCLUDE_TOOLS_SYSTEM_PROPERTIES_H_

#include <string>

#include "android-base/parsebool.h"
#include "android-base/properties.h"

namespace art {
namespace tools {

// A class for getting system properties with fallback lookup support. Different from
// android::base::GetProperty, this class is mockable.
class SystemProperties {
 public:
  virtual ~SystemProperties() = default;

  // Returns the current value of the system property `key`, or `default_value` if the property
  // doesn't have a value.
  std::string Get(const std::string& key, const std::string& default_value) const {
    std::string value = GetProperty(key);
    if (!value.empty()) {
      return value;
    }
    return default_value;
  }

  // Same as above, but allows specifying one or more fallback keys. The last argument is a string
  // default value that will be used if none of the given keys has a value.
  //
  // Usage:
  //
  // Look up for "key_1", then "key_2", then "key_3". If none of them has a value, return "default":
  //   Get("key_1", "key_2", "key_3", /*default_value=*/"default")
  template <typename... Args>
  std::string Get(const std::string& key, const std::string& fallback_key, Args... args) const {
    return Get(key, Get(fallback_key, args...));
  }

  // Returns the current value of the system property `key` with zero or more fallback keys, or an
  // empty string if none of the given keys has a value.
  //
  // Usage:
  //
  // Look up for "key_1". If it doesn't have a value, return an empty string:
  //  GetOrEmpty("key_1")
  //
  // Look up for "key_1", then "key_2", then "key_3". If none of them has a value, return an empty
  // string:
  //  GetOrEmpty("key_1", "key_2", "key_3")
  template <typename... Args>
  std::string GetOrEmpty(const std::string& key, Args... fallback_keys) const {
    return Get(key, fallback_keys..., /*default_value=*/"");
  }

  // Returns the current value of the boolean system property `key`, or `default_value` if the
  // property doesn't have a value. See `android::base::ParseBool` for how the value is parsed.
  bool GetBool(const std::string& key, bool default_value) const {
    android::base::ParseBoolResult result = android::base::ParseBool(GetProperty(key));
    if (result != android::base::ParseBoolResult::kError) {
      return result == android::base::ParseBoolResult::kTrue;
    }
    return default_value;
  }

  // Same as above, but allows specifying one or more fallback keys. The last argument is a bool
  // default value that will be used if none of the given keys has a value.
  //
  // Usage:
  //
  // Look up for "key_1", then "key_2", then "key_3". If none of them has a value, return true:
  //   Get("key_1", "key_2", "key_3", /*default_value=*/true)
  template <typename... Args>
  bool GetBool(const std::string& key, const std::string& fallback_key, Args... args) const {
    return GetBool(key, GetBool(fallback_key, args...));
  }

 protected:
  // The single source of truth of system properties. Can be mocked in unit tests.
  virtual std::string GetProperty(const std::string& key) const {
    return android::base::GetProperty(key, /*default_value=*/"");
  }
};

}  // namespace tools
}  // namespace art

#endif  // ART_LIBARTTOOLS_INCLUDE_TOOLS_SYSTEM_PROPERTIES_H_
