/******************************************************************************
 *
 *  Copyright 2021 The Android Open Source Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at:
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/

#include <gtest/gtest.h>

#include "common/multi_priority_queue.h"

namespace bluetooth {
namespace common {

TEST(MultiPriorityQueueTest, without_high_priority_item) {
  common::MultiPriorityQueue<int, 2> q;
  ASSERT_TRUE(q.empty());
  q.push(0);
  q.push(1, 0);
  q.push(2);
  ASSERT_EQ(q.size(), 3ul);
  for (int i = 0; i < 3; i++) {
    ASSERT_EQ(q.front(), i);
    q.pop();
  }
  ASSERT_TRUE(q.empty());
}

TEST(MultiPriorityQueueTest, with_high_priority_item) {
  common::MultiPriorityQueue<int, 2> q;
  q.push(1);
  q.push(2);
  q.push(0, 1);
  for (int i = 0; i < 3; i++) {
    ASSERT_EQ(q.front(), i);
    q.pop();
  }
}

TEST(MultiPriorityQueueTest, with_multiple_priority_item) {
  common::MultiPriorityQueue<int, 3> q;
  q.push(1, 1);
  q.push(0, 2);
  q.push(2, 0);
  for (int i = 0; i < 3; i++) {
    ASSERT_EQ(q.front(), i);
    q.pop();
  }
}

}  // namespace common
}  // namespace bluetooth
