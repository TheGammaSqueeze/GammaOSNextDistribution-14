/*
 * Copyright (C) 2023 The Android Open Source Project
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

#include "gtest/gtest.h"

#include <inttypes.h>

TEST(Div, Div) {
  int numerator = 5;
  int denominator = 2;
  div_t res = div(numerator, denominator);
  EXPECT_EQ(2, res.quot);
  EXPECT_EQ(1, res.rem);
}

TEST(Div, LDiv) {
  long numerator = 5;    // NOLINT(runtime/int)
  long denominator = 2;  // NOLINT(runtime/int)
  ldiv_t res = ldiv(numerator, denominator);
  EXPECT_EQ(2, res.quot);
  EXPECT_EQ(1, res.rem);
}

TEST(Div, LLDiv) {
  long long numerator = 5;    // NOLINT(runtime/int)
  long long denominator = 2;  // NOLINT(runtime/int)
  lldiv_t res = lldiv(numerator, denominator);
  EXPECT_EQ(2, res.quot);
  EXPECT_EQ(1, res.rem);
}

TEST(Div, IMaxDiv) {
  intmax_t numerator = 5;
  intmax_t denominator = 2;
  imaxdiv_t res = imaxdiv(numerator, denominator);
  EXPECT_EQ(2, res.quot);
  EXPECT_EQ(1, res.rem);
}
