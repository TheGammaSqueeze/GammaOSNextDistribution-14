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

#include "gtest/gtest.h"

#include <pthread.h>

#include "berberis/base/lock_free_stack.h"

namespace berberis {

namespace {

struct Node {
  int data;
  Node* next;
};

TEST(LockFreeStackTest, Basic) {
  Node node_0{0, nullptr};
  Node node_1{1, nullptr};
  Node node_2{2, nullptr};
  Node node_3{3, nullptr};

  LockFreeStack<Node> st;

  ASSERT_TRUE(st.Empty());

  st.Push(&node_0);
  st.Push(&node_1);
  st.Push(&node_2);

  ASSERT_FALSE(st.Empty());

  ASSERT_EQ(st.Pop(), &node_2);
  ASSERT_EQ(st.Pop(), &node_1);
  ASSERT_EQ(st.Pop(), &node_0);

  ASSERT_TRUE(st.Empty());

  st.Push(&node_3);

  node_0.next = &node_3;
  node_1.next = &node_0;
  node_2.next = &node_1;
  st.PushRange(&node_2, &node_0);

  ASSERT_FALSE(st.Empty());

  ASSERT_EQ(st.Pop(), &node_2);
  ASSERT_EQ(st.Pop(), &node_1);

  ASSERT_FALSE(st.Empty());

  st.Push(&node_2);

  ASSERT_EQ(st.Pop(), &node_2);
  ASSERT_EQ(st.Pop(), &node_0);
  ASSERT_EQ(st.Pop(), &node_3);

  ASSERT_TRUE(st.Empty());
}

constexpr size_t kNumThreads = 50;
constexpr size_t kNumNodesPerThread = 50;
constexpr size_t kNumItersPerThread = 2000;

Node g_nodes[kNumThreads * kNumNodesPerThread];
LockFreeStack<Node> g_st;

void CheckStressPushPop(size_t idx) {
  Node* nodes[kNumNodesPerThread];

  // Get initial set of nodes.
  for (size_t i = 0; i < kNumNodesPerThread; ++i) {
    nodes[i] = &g_nodes[kNumNodesPerThread * idx + i];
  }

  // On each iteration, push and pop idx + 1 nodes.
  for (size_t j = 0; j < kNumItersPerThread; ++j) {
    // Push nodes
    for (size_t i = 0; i < idx + 1; ++i) {
      g_st.Push(nodes[i]);
    }
    ASSERT_FALSE(g_st.Empty());

    // Pop nodes
    for (size_t i = 0; i < idx + 1; ++i) {
      nodes[i] = g_st.Pop();
      ASSERT_NE(nodes[i], nullptr);
    }

    // Push range
    Node* next = nullptr;
    for (size_t i = 0; i < idx + 1; ++i) {
      nodes[i]->next = next;
      next = nodes[i];
    }
    g_st.PushRange(next, nodes[0]);
    ASSERT_FALSE(g_st.Empty());

    // Pop nodes
    for (size_t i = 0; i < idx + 1; ++i) {
      nodes[i] = g_st.Pop();
      ASSERT_NE(nodes[i], nullptr);
    }
  }
}

void* StressFunc(void* arg) {
  CheckStressPushPop(reinterpret_cast<size_t>(arg));
  return nullptr;
}

TEST(LockFreeStackTest, Stress) {
  ASSERT_TRUE(g_st.Empty());

  pthread_t threads[kNumThreads];

  for (size_t i = 0; i < kNumThreads; ++i) {
    int res = pthread_create(&threads[i], nullptr, StressFunc, reinterpret_cast<void*>(i));
    ASSERT_EQ(res, 0);
  }

  for (size_t i = 0; i < kNumThreads; ++i) {
    int res = pthread_join(threads[i], nullptr);
    ASSERT_EQ(res, 0);
  }

  ASSERT_TRUE(g_st.Empty());
}

}  // namespace

}  // namespace berberis
