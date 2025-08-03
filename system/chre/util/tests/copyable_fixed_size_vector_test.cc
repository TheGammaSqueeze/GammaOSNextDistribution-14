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

#include "chre/util/copyable_fixed_size_vector.h"
#include "chre/util/fixed_size_vector.h"

using chre::CopyableFixedSizeVector;
using chre::FixedSizeVector;

TEST(CopyableFixedSizeVector, CopyConstructible) {
  constexpr int kValue = 1234;
  CopyableFixedSizeVector<int, 2> a;
  a.push_back(kValue);

  CopyableFixedSizeVector<int, 2> b(a);
  EXPECT_EQ(b.size(), 1);
  EXPECT_EQ(a[0], kValue);
  EXPECT_EQ(b[0], kValue);
}

TEST(CopyableFixedSizeVector, CopyAssignable) {
  constexpr int kValue = 1234;
  CopyableFixedSizeVector<int, 2> a;
  a.push_back(kValue);

  CopyableFixedSizeVector<int, 2> b;
  EXPECT_EQ(b.size(), 0);
  b = a;
  EXPECT_EQ(b.size(), 1);
  EXPECT_EQ(a[0], kValue);
  EXPECT_EQ(b[0], kValue);
}

TEST(CopyableFixedSizeVector, NonTrivialElement) {
  static int ctorCount;
  static int dtorCount;
  class Foo {
   public:
    Foo() {
      ctorCount++;
    }
    Foo(const Foo & /*other*/) {
      ctorCount++;
    }

    ~Foo() {
      dtorCount++;
    }
  };

  ctorCount = dtorCount = 0;
  {
    CopyableFixedSizeVector<Foo, 4> v;
    {
      Foo f;
      EXPECT_EQ(ctorCount, 1);
      v.push_back(f);
    }
    EXPECT_EQ(ctorCount, 2);
    EXPECT_EQ(dtorCount, 1);
    v.pop_back();
    EXPECT_EQ(dtorCount, 2);
    v.emplace_back();
    EXPECT_EQ(ctorCount, 3);
  }
  EXPECT_EQ(dtorCount, 3);
}

TEST(CopyableFixedSizeVector, Nestable) {
  struct Foo {
    int id;
    CopyableFixedSizeVector<float, 3> vec;
  };

  FixedSizeVector<Foo, 4> container;

  Foo f;
  f.id = 1;
  container.push_back(f);
  container.emplace_back();
  container.back().id = 2;
  container.back().vec.push_back(1.23f);
  container.back().vec.push_back(3.21f);
  EXPECT_EQ(container.front().id, 1);
  container.erase(0);
  EXPECT_EQ(container.front().id, 2);
  EXPECT_EQ(container.front().vec.size(), 2);
  EXPECT_EQ(container.front().vec[0], 1.23f);
}
