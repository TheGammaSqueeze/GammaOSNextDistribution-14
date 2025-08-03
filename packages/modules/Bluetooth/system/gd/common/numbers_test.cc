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

#include "common/numbers.h"

#include <cstdint>

#include <gtest/gtest.h>

namespace testing {

using bluetooth::common::IsNumberInNumericLimits;

TEST(NumbersTest, test_is_number_in_numeric_limits) {
  // INT32_MAX+1
  int64_t n = 2147483648L;
  ASSERT_FALSE(IsNumberInNumericLimits<int32_t>(n));
  ASSERT_TRUE(IsNumberInNumericLimits<int64_t>(n));
  ASSERT_FALSE(IsNumberInNumericLimits<int8_t>(int32_t(128)));
  ASSERT_FALSE(IsNumberInNumericLimits<uint8_t>(uint32_t(256)));
  ASSERT_FALSE(IsNumberInNumericLimits<int8_t>(int32_t(-129)));
}

}  // namespace testing