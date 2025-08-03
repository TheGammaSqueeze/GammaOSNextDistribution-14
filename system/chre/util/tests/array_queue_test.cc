/*
 * Copyright (C) 2016 The Android Open Source Project
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

#include "chre/util/array_queue.h"
#include "gtest/gtest.h"

#include <algorithm>
#include <memory>
#include <type_traits>
#include <utility>
#include <vector>

using chre::ArrayQueue;
using chre::ArrayQueueExt;

namespace {
constexpr int kMaxTestCapacity = 10;
int destructor_count[kMaxTestCapacity];
int constructor_count;
int total_destructor_count;

class FakeElement {
 public:
  FakeElement() {
    constructor_count++;
  };
  FakeElement(int i) {
    val_ = i;
    constructor_count++;
  };
  ~FakeElement() {
    total_destructor_count++;
    if (val_ >= 0 && val_ < kMaxTestCapacity) {
      destructor_count[val_]++;
    }
  };
  void setValue(int i) {
    val_ = i;
  }

 private:
  int val_ = kMaxTestCapacity - 1;
};
}  // namespace

TEST(ArrayQueueTest, IsEmptyInitially) {
  ArrayQueue<int, 4> q;
  EXPECT_TRUE(q.empty());
  EXPECT_EQ(0, q.size());
}

TEST(ArrayQueueTest, SimplePushPop) {
  ArrayQueue<int, 3> q;
  EXPECT_TRUE(q.push(1));
  EXPECT_TRUE(q.push(2));
  q.pop();
  EXPECT_TRUE(q.push(3));
}

TEST(ArrayQueueTest, SimplePushPopBackPush) {
  ArrayQueue<int, 3> q;
  EXPECT_TRUE(q.push(0));
  EXPECT_TRUE(q.push(1));
  EXPECT_TRUE(q.push(2));
  q.pop_back();
  EXPECT_EQ(2, q.size());
  EXPECT_EQ(0, q[0]);
  EXPECT_EQ(1, q[1]);

  EXPECT_TRUE(q.push(3));
  EXPECT_EQ(3, q.size());
  EXPECT_EQ(0, q[0]);
  EXPECT_EQ(1, q[1]);
  EXPECT_EQ(3, q[2]);

  q.pop_back();
  q.pop_back();
  q.pop_back();

  EXPECT_EQ(0, q.size());
  EXPECT_TRUE(q.push(4));
  EXPECT_TRUE(q.push(5));
  EXPECT_TRUE(q.push(6));
  EXPECT_EQ(3, q.size());
  EXPECT_EQ(4, q[0]);
  EXPECT_EQ(5, q[1]);
  EXPECT_EQ(6, q[2]);

  q.pop();

  EXPECT_TRUE(q.push(7));
  EXPECT_EQ(5, q[0]);
  EXPECT_EQ(6, q[1]);
  EXPECT_EQ(7, q[2]);

  q.pop_back();

  EXPECT_EQ(5, q[0]);
  EXPECT_EQ(6, q[1]);

  q.pop();

  EXPECT_EQ(6, q[0]);
}

TEST(ArrayQueueTest, TestSize) {
  ArrayQueue<int, 2> q;
  q.push(1);
  EXPECT_EQ(1, q.size());
  q.push(2);
  EXPECT_EQ(2, q.size());
  q.pop();
  EXPECT_EQ(1, q.size());
  q.pop();
}

TEST(ArrayQueueTest, TestEmpty) {
  ArrayQueue<int, 2> q;
  q.push(1);
  EXPECT_FALSE(q.empty());
  q.push(2);
  EXPECT_FALSE(q.empty());
  q.pop();
  EXPECT_FALSE(q.empty());
  q.pop();
  EXPECT_TRUE(q.empty());
}

TEST(ArrayQueueTest, KickPushWhenNotFull) {
  ArrayQueue<int, 2> q;
  q.kick_push(1);
  EXPECT_EQ(1, q.size());
  EXPECT_EQ(1, q[0]);
  q.kick_push(2);
  EXPECT_EQ(2, q.size());
  EXPECT_EQ(2, q[1]);
}

TEST(ArrayQueueTest, KickPushWhenFull) {
  ArrayQueue<int, 2> q;
  q.kick_push(1);
  q.push(2);
  EXPECT_EQ(2, q.size());
  q.kick_push(3);
  EXPECT_EQ(2, q.size());
  EXPECT_EQ(2, q[0]);
  EXPECT_EQ(3, q[1]);
}

TEST(ArrayQueueTest, PopWhenEmpty) {
  ArrayQueue<int, 4> q;
  q.pop();
  EXPECT_EQ(0, q.size());
}

TEST(ArrayQueueTest, PopBackWhenEmpty) {
  ArrayQueue<int, 4> q;
  q.pop_back();
  EXPECT_EQ(0, q.size());
}

TEST(ArrayQueueTest, PushWhenFull) {
  ArrayQueue<int, 2> q;
  q.push(1);
  q.push(2);
  EXPECT_FALSE(q.push(3));
}

TEST(ArrayQueueDeathTest, FrontWhenEmpty) {
  ArrayQueue<int, 4> q;
  EXPECT_DEATH(q.front(), "");
}

TEST(ArrayQueueDeathTest, BackWhenEmpty) {
  ArrayQueue<int, 4> q;
  EXPECT_DEATH(q.back(), "");
}

TEST(ArrayQueueTest, TestFront) {
  ArrayQueue<int, 3> q;
  q.push(1);
  EXPECT_EQ(1, q.front());
  q.pop();
  q.push(2);
  EXPECT_EQ(2, q.front());
  q.push(3);
  EXPECT_EQ(2, q.front());
}

TEST(ArrayQueueTest, TestBack) {
  ArrayQueue<int, 3> q;
  q.push(1);
  EXPECT_EQ(1, q.back());  // 1 x x
  q.push(2);
  EXPECT_EQ(2, q.back());  // 1 2 x
  q.pop();
  EXPECT_EQ(2, q.back());  // x 2 x
  q.push(3);
  EXPECT_EQ(3, q.back());  // x 2 3
  q.push(4);
  EXPECT_EQ(4, q.back());  // 4 2 3 (forward wrap-around)
  q.pop_back();
  EXPECT_EQ(3, q.back());  // x 2 3 (backwards wrap-around)
  q.pop();
  EXPECT_EQ(3, q.back());  // x x 3
}

TEST(ArrayQueueDeathTest, InvalidSubscript) {
  ArrayQueue<int, 2> q;
  EXPECT_DEATH(q[0], "");
}

TEST(ArrayQueueTest, Subscript) {
  ArrayQueue<int, 2> q;
  q.push(1);
  q.push(2);
  EXPECT_EQ(1, q[0]);
  EXPECT_EQ(2, q[1]);
  q.pop();
  EXPECT_EQ(2, q[0]);
}

TEST(ArrayQueueTest, RemoveWithInvalidIndex) {
  ArrayQueue<int, 3> q;
  EXPECT_FALSE(q.remove(0));
}

TEST(ArrayQueueTest, RemoveWithIndex) {
  ArrayQueue<int, 5> q;
  q.push(1);
  q.push(2);
  q.remove(0);
  EXPECT_EQ(2, q.front());
  EXPECT_EQ(1, q.size());
  q.push(3);
  q.push(4);
  q.push(5);
  q.remove(3);
  int sampleArray[] = {2, 3, 5};
  EXPECT_EQ(3, q.size());
  for (size_t i = 0; i < q.size(); ++i) {
    EXPECT_EQ(sampleArray[i], q.front());
    q.remove(0);
  }
}

TEST(ArrayQueueTest, DestructorCalledOnPop) {
  for (size_t i = 0; i < kMaxTestCapacity; ++i) {
    destructor_count[i] = 0;
  }

  ArrayQueue<FakeElement, 3> q;
  FakeElement e;
  q.push(e);
  q.push(e);

  q.front().setValue(0);
  q.pop();
  EXPECT_EQ(1, destructor_count[0]);

  q.front().setValue(1);
  q.pop();
  EXPECT_EQ(1, destructor_count[1]);
}

TEST(ArrayQueueTest, ElementsDestructedWhenQueueDestructed) {
  for (size_t i = 0; i < kMaxTestCapacity; ++i) {
    destructor_count[i] = 0;
  }

  // Put q and e in the scope so their destructor will be called going
  // out of scope.
  {
    ArrayQueue<FakeElement, 4> q;
    FakeElement e;

    for (size_t i = 0; i < 3; ++i) {
      q.push(e);
      q[i].setValue(i);
    }
  }

  // q should now be destroyed - check destructor count.
  for (size_t i = 0; i < 3; ++i) {
    EXPECT_EQ(1, destructor_count[i]);
  }
  EXPECT_EQ(0, destructor_count[3]);
  EXPECT_EQ(1, destructor_count[kMaxTestCapacity - 1]);
}

TEST(ArrayQueueTest, EmplaceTest) {
  constructor_count = 0;
  ArrayQueue<FakeElement, 2> q;

  EXPECT_TRUE(q.emplace(0));
  EXPECT_EQ(1, constructor_count);
  EXPECT_EQ(1, q.size());

  EXPECT_TRUE(q.emplace(1));
  EXPECT_EQ(2, constructor_count);
  EXPECT_EQ(2, q.size());

  EXPECT_FALSE(q.emplace(2));
  EXPECT_EQ(2, constructor_count);
  EXPECT_EQ(2, q.size());
}

TEST(ArrayQueueTest, EmptyQueueIterator) {
  ArrayQueue<int, 4> q;

  ArrayQueue<int, 4>::iterator it = q.begin();
  EXPECT_TRUE(it == q.end());
  EXPECT_FALSE(it != q.end());
}

TEST(ArrayQueueTest, SimpleIterator) {
  ArrayQueue<int, 4> q;
  for (size_t i = 0; i < 3; ++i) {
    q.push(i);
  }
  EXPECT_NE(q.begin(), q.end());

  size_t index = 0;
  for (ArrayQueue<int, 4>::iterator it = q.begin(); it != q.end(); ++it) {
    EXPECT_EQ(q[index++], *it);
  }
  index = 0;
  for (ArrayQueue<int, 4>::iterator it = q.begin(); it != q.end(); it++) {
    EXPECT_EQ(q[index++], *it);
  }

  index = 0;
  ArrayQueue<int, 4>::iterator it = q.begin();
  while (it != q.end()) {
    EXPECT_EQ(q[index++], *it++);
  }

  for (size_t i = 0; i < 3; ++i) {
    q.pop();
    q.push(i + 3);
  }

  index = 0;
  it = q.begin();
  while (it != q.end()) {
    EXPECT_EQ(q[index++], *it++);
  }

  // Iterator concept checks: default constructible, copy assignable, copy
  // constructible
  ArrayQueue<int, 4>::iterator it2;
  it2 = it;
  EXPECT_EQ(it, it2);

  ArrayQueue<int, 4>::iterator it3(it);
  EXPECT_EQ(it, it3);
}

TEST(ArrayQueueTest, IteratorSwap) {
  ArrayQueue<int, 2> q;
  q.push(1);
  q.push(2);

  auto it1 = q.begin(), it2 = q.end();
  std::swap(it1, it2);
  EXPECT_EQ(it1, q.end());
  EXPECT_EQ(it2, q.begin());
}

TEST(ArrayQueueTest, IteratorAndPush) {
  ArrayQueue<int, 4> q;
  for (size_t i = 0; i < 2; ++i) {
    q.push(i);
  }

  ArrayQueue<int, 4>::iterator it_b = q.begin();
  ArrayQueue<int, 4>::iterator it_e = q.end();
  q.push(3);

  size_t index = 0;
  while (it_b != it_e) {
    EXPECT_EQ(q[index++], *it_b++);
  }
}

TEST(ArrayQueueTest, IteratorAndPop) {
  ArrayQueue<int, 4> q;
  for (size_t i = 0; i < 3; ++i) {
    q.push(i);
  }

  ArrayQueue<int, 4>::iterator it_b = q.begin();
  q.pop();
  it_b++;

  for (size_t i = 0; i < 2; ++i) {
    EXPECT_EQ(q[i], *it_b++);
  }
}

TEST(ArrayQueueTest, IteratorAndRemove) {
  ArrayQueue<int, 4> q;
  for (size_t i = 0; i < 2; ++i) {
    q.push(i);
  }

  ArrayQueue<int, 4>::iterator it_b = q.begin();
  q.remove(1);

  EXPECT_EQ(q[0], *it_b);
}

TEST(ArrayQueueTest, IteratorAndEmplace) {
  ArrayQueue<int, 4> q;
  for (size_t i = 0; i < 2; ++i) {
    q.push(i);
  }

  ArrayQueue<int, 4>::iterator it_b = q.begin();
  ArrayQueue<int, 4>::iterator it_e = q.end();
  q.emplace(3);

  size_t index = 0;
  while (it_b != it_e) {
    EXPECT_EQ(q[index++], *it_b++);
  }
}

TEST(ArrayQueueTest, SimpleConstIterator) {
  ArrayQueue<int, 4> q;
  for (size_t i = 0; i < 3; ++i) {
    q.push(i);
  }

  size_t index = 0;
  for (ArrayQueue<int, 4>::const_iterator cit = q.cbegin(); cit != q.cend();
       ++cit) {
    EXPECT_EQ(q[index++], *cit);
  }

  index = 0;
  ArrayQueue<int, 4>::const_iterator cit = q.cbegin();
  while (cit != q.cend()) {
    EXPECT_EQ(q[index++], *cit++);
  }

  for (size_t i = 0; i < 3; ++i) {
    q.pop();
    q.push(i + 3);
  }

  index = 0;
  cit = q.cbegin();
  while (cit != q.cend()) {
    EXPECT_EQ(q[index++], *cit++);
  }
}

TEST(ArrayQueueTest, Full) {
  ArrayQueue<size_t, 4> q;
  for (size_t i = 0; i < 4; i++) {
    EXPECT_FALSE(q.full());
    q.push(i);
  }

  EXPECT_TRUE(q.full());
}

TEST(ArrayQueueTest, ArrayCopy) {
  constexpr size_t kSize = 8;
  ArrayQueue<size_t, kSize> q;
  std::vector<size_t> v;
  v.resize(kSize);

  for (size_t i = 0; i < kSize; i++) {
    q.push(i);

    v.assign(kSize, 0xdeadbeef);
    std::copy(q.begin(), q.end(), v.begin());

    for (size_t j = 0; j < i; j++) {
      EXPECT_EQ(q[j], v[j]);
      EXPECT_EQ(*std::next(q.begin(), j), v[j]);
    }
  }
}

TEST(ArrayQueueTest, IteratorTraits) {
  ArrayQueue<int, 2> q;
  q.push(1234);
  q.push(5678);

  using traits = std::iterator_traits<decltype(q)::iterator>;
  typename traits::difference_type diff = std::distance(q.begin(), q.end());
  EXPECT_EQ(diff, q.size());

  typename traits::value_type v = *q.begin();
  EXPECT_EQ(v, q[0]);

  typename traits::reference r = *q.begin();
  r = 999;
  EXPECT_EQ(r, q[0]);

  typename traits::pointer p = &r;
  EXPECT_EQ(*p, q[0]);

  // Note: if the implementation is upgraded to another category like random
  // access, then this static assert should be updated. It exists primarily to
  // confirm that we are declaring an iterator_category
  static_assert(
      std::is_same<traits::iterator_category, std::forward_iterator_tag>::value,
      "ArrayQueueIterator should be a forward iterator");
}

TEST(ArrayQueueTest, ArrayClear) {
  ArrayQueue<size_t, 4> q;

  q.clear();
  EXPECT_TRUE(q.empty());

  for (size_t i = 0; i < 4; i++) {
    q.push(i);
  }

  q.clear();
  EXPECT_TRUE(q.empty());

  // Make sure that insertion/access still work after a clear.
  for (size_t i = 0; i < 4; i++) {
    q.push(i);
  }
  for (size_t i = 0; i < 4; i++) {
    EXPECT_EQ(q[i], i);
  }
}

TEST(ArrayQueueTest, ElementsDestructedArrayClear) {
  for (size_t i = 0; i < kMaxTestCapacity; ++i) {
    destructor_count[i] = 0;
  }
  total_destructor_count = 0;

  ArrayQueue<FakeElement, 4> q;
  for (size_t i = 0; i < 3; ++i) {
    q.emplace(i);
  }

  q.clear();

  for (size_t i = 0; i < 3; ++i) {
    EXPECT_EQ(1, destructor_count[i]);
  }
  EXPECT_EQ(3, total_destructor_count);
}

TEST(ArrayQueueExtTest, BasicTest) {
  constexpr size_t kNumElements = 32;
  int32_t array[kNumElements];
  ArrayQueueExt<int32_t> q(array, kNumElements);

  ASSERT_EQ(q.data(), array);
  EXPECT_EQ(q.capacity(), kNumElements);

  for (int i = 0; i < kNumElements; i++) {
    q.push(i);
  }

  for (int i = 0; i < kNumElements; i++) {
    EXPECT_EQ(array[i], q[i]);
  }
}

TEST(ArrayQueueTest, KickPushNonCopyable) {
  ArrayQueue<std::unique_ptr<int>, 2> q;
  auto p1 = std::make_unique<int>(42);
  auto p2 = std::make_unique<int>(43);
  auto p3 = std::make_unique<int>(44);

  q.kick_push(std::move(p1));
  EXPECT_EQ(q.size(), 1);
  EXPECT_EQ(*q.front(), 42);
  EXPECT_EQ(*q.back(), 42);

  q.kick_push(std::move(p2));
  EXPECT_EQ(q.size(), 2);
  EXPECT_EQ(*q.front(), 42);
  EXPECT_EQ(*q.back(), 43);

  q.kick_push(std::move(p3));
  EXPECT_EQ(q.size(), 2);
  EXPECT_EQ(*q.front(), 43);
  EXPECT_EQ(*q.back(), 44);
}
