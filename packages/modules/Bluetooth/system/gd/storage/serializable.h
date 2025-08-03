/*
 * Copyright 2020 The Android Open Source Project
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
#include <type_traits>

namespace bluetooth {
namespace storage {

// A config serializable module
template <typename T>
class Serializable {
 public:
  Serializable() = default;
  virtual ~Serializable() = default;

  // GD stack code

  // Serialize to string used in GD stack
  virtual std::string ToString() const = 0;
  // T must implement FromString(const std::string&), otherwise, it will fail to compile
  // Parse string from GD stack
  static std::optional<T> FromString(const std::string& str) {
    return T::FromString(str);
  }

  // Legacy handling

  // Serialize to string used in legacy stack config, this may not be the same as ToString()
  virtual std::string ToLegacyConfigString() const = 0;
  // T must implement FromLegacyConfigString(const std::string&), otherwise, it will fail to compile
  // Parse string from legacy config
  static std::optional<T> FromLegacyConfigString(const std::string& str) {
    return T::FromLegacyConfigString(str);
  }
};

}  // namespace storage
}  // namespace bluetooth