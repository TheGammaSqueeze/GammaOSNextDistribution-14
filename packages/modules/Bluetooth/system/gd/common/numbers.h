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

#include <limits>
#include <type_traits>

namespace bluetooth {
namespace common {

// Check if input is within numeric limits of RawType
template <typename RawType, typename InputType>
bool IsNumberInNumericLimits(InputType input) {
  // Only arithmetic types are supported
  static_assert(std::is_arithmetic_v<RawType> && std::is_arithmetic_v<InputType>);
  // Either both are signed or both are unsigned
  static_assert(
      (std::is_signed_v<RawType> && std::is_signed_v<InputType>) ||
      (std::is_unsigned_v<RawType> && std::is_unsigned_v<InputType>));
  if (std::numeric_limits<InputType>::max() > std::numeric_limits<RawType>::max()) {
    if (input > std::numeric_limits<RawType>::max()) {
      return false;
    }
  }
  if (std::numeric_limits<InputType>::lowest() < std::numeric_limits<RawType>::lowest()) {
    if (input < std::numeric_limits<RawType>::lowest()) {
      return false;
    }
  }
  return true;
}

}  // namespace common
}  // namespace bluetooth