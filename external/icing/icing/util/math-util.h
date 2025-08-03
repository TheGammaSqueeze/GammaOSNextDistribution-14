// Copyright (C) 2019 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#ifndef ICING_UTIL_MATH_UTIL_H_
#define ICING_UTIL_MATH_UTIL_H_

#include <limits>

namespace icing {
namespace lib {

namespace math_util {

inline double SafeDivide(double first, double second) {
  if (second == 0) {
    return std::numeric_limits<double>::infinity();
  }
  return first / second;
}

// Returns the maximum integer value which is a multiple of rounding_value,
// and less than or equal to input_value.
//
// input_value must be greater than or equal to zero, or else returns 0.
// rounding_value must be greater than or equal to zero, or else returns 0.
template <typename IntType>
static IntType RoundDownTo(IntType input_value, IntType rounding_value) {
  static_assert(std::numeric_limits<IntType>::is_integer,
                "RoundDownTo() operation type is not integer");

  if (input_value <= 0) {
    return 0;
  }

  if (rounding_value <= 0) {
    return 0;
  }

  return (input_value / rounding_value) * rounding_value;
}

// Returns the minimum integer value which is a multiple of rounding_value,
// and greater than or equal to input_value.
//
// input_value must be greater than or equal to zero, or else returns 0.
// rounding_value must be greater than or equal to zero, or else returns 0.
template <typename IntType>
static IntType RoundUpTo(IntType input_value, IntType rounding_value) {
  static_assert(std::numeric_limits<IntType>::is_integer,
                "RoundUpTo() operation type is not integer");

  if (input_value <= 0) {
    return 0;
  }

  if (rounding_value <= 0) {
    return 0;
  }

  const IntType remainder = input_value % rounding_value;
  return (remainder == 0) ? input_value
                          : (input_value - remainder + rounding_value);
}

}  // namespace math_util

}  // namespace lib
}  // namespace icing

#endif  // ICING_UTIL_MATH_UTIL_H_
