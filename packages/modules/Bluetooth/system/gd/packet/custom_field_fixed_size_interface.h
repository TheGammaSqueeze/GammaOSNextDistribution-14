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

#include <cstdint>
#include <type_traits>

namespace bluetooth {
namespace packet {

template <typename T>
class CustomFieldFixedSizeInterface {
 public:
  virtual ~CustomFieldFixedSizeInterface() = default;
  // Get a pointer to a modifiable and readable continuous array of data
  virtual uint8_t* data() = 0;
  virtual const uint8_t* data() const = 0;
  // Get the length of underlying data array, data() + length() would be invalid
  // subclass T must have kLength variable defined
  static constexpr size_t length() {
    static_assert(
        std::is_same_v<decltype(T::kLength), const size_t>, "T::kLength must be const size_t or constexpr size_t");
    static_assert(std::is_const_v<decltype(T::kLength)>, "T::kLength must be const");
    return T::kLength;
  };
};

}  // namespace packet
}  // namespace bluetooth