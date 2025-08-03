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

#include <limits>

#include "gtest/gtest.h"

constexpr int kNumIterations = 1e8;

TEST(FPPerf, Adds) {
  double x = 0.0;
  for (int i = 0; i < kNumIterations; i++) {
    x += 3.14159265359;
  }
  EXPECT_NEAR(x, 3.14159265359 * kNumIterations, static_cast<double>(kNumIterations) / 1e8);
}

TEST(FPPerf, TinyAdds) {
  double x = 0.0;
  for (int i = 0; i < kNumIterations; i++) {
    x += std::numeric_limits<double>::min();
  }
  EXPECT_DOUBLE_EQ(x, std::numeric_limits<double>::min() * kNumIterations);
}

TEST(FPPerf, OverflowingAdds) {
  double x = 1.0e308;
  for (int i = 0; i < kNumIterations; i++) {
    x += x;
  }
  EXPECT_EQ(x, std::numeric_limits<double>::infinity());
}

TEST(FPPerf, OverflowingMuls) {
  double x = 1.0e308;
  for (int i = 0; i < kNumIterations; i++) {
    x *= 3.14159265359;
  }
  EXPECT_EQ(x, std::numeric_limits<double>::infinity());
}

TEST(FPPerf, UnderflowingMuls) {
  volatile double x = 1.0e-307;
  for (int i = 0; i < kNumIterations; i++) {
    x *= 0.0314159265359;
  }
  EXPECT_EQ(x, 0.0);
}
