/*
 * Copyright (C) 2015 The Android Open Source Project
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
#include "berberis/base/arena_list.h"
#include "berberis/base/arena_vector.h"
#include "berberis/base/mmap.h"

namespace berberis {

class ArenaTest : public ::testing::Test {
 public:
  ArenaTest() = default;

  size_t ArenaMappedSize() {
    size_t size = 0;
    auto* block = arena_.blocks_;
    while (block) {
      size += block->size;
      block = block->next;
    }
    return size;
  }

  size_t BigMapSize(size_t requested_size, size_t align) {
    return AlignUpPageSize(AlignUp(sizeof(arena_internal::ArenaBlock), align) + requested_size);
  }

 protected:
  static constexpr size_t kBlockSize = arena_internal::kDefaultArenaBlockSize;
  static constexpr size_t kReallyBigSize = 8 * kBlockSize;
  Arena arena_;
};

namespace {

struct Node {
  unsigned elem1;
  unsigned elem2;

  explicit Node(unsigned e) : elem1(e), elem2(e + 11) {}
};

using FastList = ArenaList<Node*>;
using FastVector = ArenaVector<Node*>;

TEST_F(ArenaTest, Smoke) {
  char* p;

  p = static_cast<char*>(arena_.Alloc(1, 1));
  ASSERT_NE(p, nullptr);
  p[0] = 'a';
  ASSERT_EQ(ArenaMappedSize(), kBlockSize);

  p = static_cast<char*>(arena_.Alloc(100, 2));
  ASSERT_NE(p, nullptr);
  p[99] = 'b';
  ASSERT_EQ(ArenaMappedSize(), kBlockSize);

  p = static_cast<char*>(arena_.Alloc(kBlockSize / 10, 4));
  ASSERT_NE(p, nullptr);
  p[kBlockSize / 10 - 1] = 'c';
  ASSERT_EQ(ArenaMappedSize(), kBlockSize);

  p = static_cast<char*>(arena_.Alloc(kReallyBigSize, 4));
  ASSERT_NE(p, nullptr);
  p[kReallyBigSize - 1] = 'z';
  ASSERT_EQ(ArenaMappedSize(), kBlockSize + BigMapSize(kReallyBigSize, 4));
}

TEST_F(ArenaTest, Alloc) {
  size_t requested_size = 0;
  for (int i = 0; i < 4; ++i) {
    for (size_t n = 1; n < 2 * kReallyBigSize; n *= 2) {
      requested_size += n;
      char* p = static_cast<char*>(arena_.Alloc(n, 1));
      p[0] = 'a';
      p[n - 1] = 'z';
    }
  }
  ASSERT_GE(ArenaMappedSize(), requested_size);
}

TEST_F(ArenaTest, List) {
  FastList list(&arena_);

  list.push_back(NewInArena<Node>(&arena_, 1));
  list.push_back(NewInArena<Node>(&arena_, 2));

  ASSERT_EQ(2u, list.size());
  ASSERT_EQ(1u + 11, (*list.begin())->elem2);
  ASSERT_EQ(2u, (*--list.end())->elem1);
  ASSERT_EQ(ArenaMappedSize(), kBlockSize);
}

TEST_F(ArenaTest, Vector) {
  constexpr size_t kElems = 40000;
  FastVector vector(kElems, nullptr, &arena_);

  for (size_t i = 0; i < kElems; i++) {
    vector[i] = NewInArena<Node>(&arena_, i);
  }

  ASSERT_EQ(kElems, vector.size());
  ASSERT_EQ(0u, (*vector.begin())->elem1);
  ASSERT_EQ(kElems - 1, vector[kElems - 1]->elem1);
  ASSERT_GE(ArenaMappedSize(), kElems * sizeof(Node));
}

}  // namespace

}  // namespace berberis
