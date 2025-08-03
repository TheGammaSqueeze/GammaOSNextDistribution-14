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

#include "berberis/base/forever_pool.h"

namespace berberis {

namespace {

TEST(ForeverPool, Smoke) {
  // ForeverPool maintains free lists by type.
  // Define unique type to test memory reusage.
  // ATTENTION: assume multiple instances of this test don't run in parallel!
  struct Foo {
    char c;
  };

  Foo* p1 = ForeverPool<Foo>::Alloc();
  ASSERT_TRUE(p1);

  Foo* p2 = ForeverPool<Foo>::Alloc();
  ASSERT_TRUE(p2);
  EXPECT_NE(p1, p2);

  ForeverPool<Foo>::Free(p1);

  // Expect memory is reused.
  Foo* p3 = ForeverPool<Foo>::Alloc();
  EXPECT_EQ(p1, p3);

  ForeverPool<Foo>::Free(p2);
  ForeverPool<Foo>::Free(p3);
}

}  // namespace

}  // namespace berberis
