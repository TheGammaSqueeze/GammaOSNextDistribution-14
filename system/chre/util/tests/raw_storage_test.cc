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

#include "gtest/gtest.h"

#include "chre/util/raw_storage.h"

using chre::RawStorage;

static_assert(sizeof(RawStorage<int, 10>) == sizeof(int[10]),
              "RawStorage must allocate the correct size");

TEST(RawStorageTest, Capacity) {
  RawStorage<int, 42> rs;
  EXPECT_EQ(rs.capacity(), 42);
}

TEST(RawStorageTest, ArraySubscript) {
  struct Foo {
    const int x = 1;
    int y = 2;
  };

  RawStorage<Foo, 2> rs;
  new (&rs[0]) Foo();
  EXPECT_EQ(rs[0].x, 1);
  EXPECT_EQ(rs[0].y, 2);
  EXPECT_EQ(++rs[0].y, 3);
  new (&rs[0]) Foo();
  EXPECT_EQ(rs[0].x, 1);
  EXPECT_EQ(rs[0].y, 2);
}