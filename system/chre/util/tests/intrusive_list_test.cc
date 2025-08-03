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

#include "chre/util/intrusive_list.h"

#include "gtest/gtest.h"

using chre::IntrusiveList;
using chre::ListNode;

TEST(IntrusiveList, EmptyByDefault) {
  IntrusiveList<int> testLinkedList;
  EXPECT_EQ(testLinkedList.size(), 0);
  EXPECT_TRUE(testLinkedList.empty());
}

TEST(IntrusiveList, PushReadAndPop) {
  typedef ListNode<int> ListIntNode;

  ListIntNode nodeA(0);
  ListIntNode nodeB(1);
  ListIntNode nodeC(2);
  ListIntNode nodeD(3);
  IntrusiveList<int> testLinkedList;

  testLinkedList.link_back(&nodeA);
  testLinkedList.link_back(&nodeB);
  testLinkedList.link_back(&nodeC);
  EXPECT_EQ(testLinkedList.size(), 3);

  EXPECT_EQ(testLinkedList.front().item, nodeA.item);
  EXPECT_EQ(testLinkedList.back().item, nodeC.item);

  testLinkedList.unlink_front();
  EXPECT_EQ(testLinkedList.size(), 2);
  EXPECT_EQ(testLinkedList.front().item, nodeB.item);

  testLinkedList.unlink_back();
  EXPECT_EQ(testLinkedList.size(), 1);
  EXPECT_EQ(testLinkedList.back().item, nodeB.item);

  testLinkedList.unlink_back();
  EXPECT_EQ(testLinkedList.size(), 0);
  EXPECT_TRUE(testLinkedList.empty());

  testLinkedList.link_back(&nodeD);
  EXPECT_EQ(testLinkedList.size(), 1);
  EXPECT_EQ(testLinkedList.back().item, nodeD.item);
  EXPECT_EQ(testLinkedList.front().item, nodeD.item);
}

TEST(IntrusiveList, CatchInvalidCallToEmptyList) {
  IntrusiveList<int> testList;
  ASSERT_DEATH(testList.front(), "");
  ASSERT_DEATH(testList.back(), "");
  ASSERT_DEATH(testList.unlink_front(), "");
  ASSERT_DEATH(testList.unlink_back(), "");
}

TEST(IntrusiveList, DestructorCleanUpLink) {
  typedef ListNode<int> ListIntNode;

  ListIntNode testInput[]{
      ListIntNode(0), ListIntNode(1), ListIntNode(2),
      ListIntNode(3), ListIntNode(4),
  };

  {
    IntrusiveList<int> testLinkedList;
    for (auto &node : testInput) {
      testLinkedList.link_back(&node);
    }

    int idx = 0;
    for (auto const &node : testLinkedList) {
      EXPECT_EQ(node.item, idx++);
    }
  }

  for (auto &node : testInput) {
    EXPECT_EQ(node.node.next, nullptr);
    EXPECT_EQ(node.node.prev, nullptr);
  }
}

TEST(IntrusiveList, AccessMiddleTest) {
  typedef ListNode<int> ListIntNode;

  ListIntNode testListIntNodes[]{
      ListIntNode(0), ListIntNode(1), ListIntNode(2),
      ListIntNode(3), ListIntNode(4),
  };

  IntrusiveList<int> testLinkedList;

  for (auto &node : testListIntNodes) {
    testLinkedList.link_back(&node);
  }

  testLinkedList.unlink_node(&testListIntNodes[1]);  // removes ListIntNode(1)
  EXPECT_EQ(testListIntNodes[0].node.next, &testListIntNodes[2].node);
  EXPECT_EQ(testLinkedList.size(), 4);

  testLinkedList.link_after(&testListIntNodes[0],
                            &testListIntNodes[1]);  // add back ListIntNode(1)
  EXPECT_EQ(testListIntNodes[0].node.next, &testListIntNodes[1].node);
  EXPECT_EQ(testLinkedList.size(), 5);
}
