/*
 * Copyright (C) 2019 The Android Open Source Project
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

#include "berberis/base/arena_alloc.h"
#include "berberis/base/arena_zeroed_array.h"

namespace berberis {

namespace {

TEST(ArenaZeroedArray, Smoke) {
  Arena arena;
  ArenaZeroedArray<int> array(3, &arena);

  ASSERT_EQ(array[0], 0);
  ASSERT_EQ(array[1], 0);
  ASSERT_EQ(array[2], 0);

  array[0] = 10;

  ASSERT_EQ(array.at(0), 10);
  ASSERT_EQ(array.at(1), 0);
  ASSERT_EQ(array.at(2), 0);

  array.at(1) = 11;

  ASSERT_EQ(array[0], 10);
  ASSERT_EQ(array[1], 11);
  ASSERT_EQ(array[2], 0);

  array[2] = 12;

  ASSERT_EQ(array.at(0), 10);
  ASSERT_EQ(array.at(1), 11);
  ASSERT_EQ(array.at(2), 12);
}

TEST(ArenaZeroedArray_DeathTest, OutOfRange) {
  Arena arena;
  ArenaZeroedArray<int> array(3, &arena);

  ASSERT_DEATH(array.at(3), "");
}

}  // namespace

}  // namespace berberis
