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

#ifndef CHRE_UTIL_INTRUSIVE_LIST_BASE_H_
#define CHRE_UTIL_INTRUSIVE_LIST_BASE_H_

#include <cstddef>

#include "chre/util/non_copyable.h"

namespace chre {
namespace intrusive_list_internal {

struct Node : public NonCopyable {
  Node *next = nullptr;
  Node *prev = nullptr;

  bool operator==(Node const &other) const {
    return &other == this;
  }

  bool operator!=(Node const &other) const {
    return &other != this;
  }
};

class IntrusiveListBase : public NonCopyable {
 protected:
  /**
   * The sentinel node for easier access to the first (mSentinelNode.next)
   * and last (mSentinelNode.prev) element of the linked list.
   */
  Node mSentinelNode;

  /**
   * Number of elements currently stored in the linked list.
   */
  size_t mSize = 0;

  IntrusiveListBase() {
    mSentinelNode.next = &mSentinelNode;
    mSentinelNode.prev = &mSentinelNode;
  }

  /**
   * Link a new node to the end of the linked list.
   *
   * @param newNode: The node to push onto the linked list.
   */
  void doLinkBack(Node *newNode);

  /**
   * Unlink a node from the linked list.
   *
   * @param node: The node to remove from the linked list.
   */
  void doUnlinkNode(Node *node);

  /**
   * Link a node after a given node.
   *
   * @param frontNode: The node that will lead the new node.
   * @param newNode: The new node to link.
   */
  void doLinkAfter(Node *frontNode, Node *newNode);

  /**
   * Unlinks all node in this list.
   */
  void doUnlinkAll();
};

}  // namespace intrusive_list_internal
}  // namespace chre

#endif  // CHRE_UTIL_INTRUSIVE_LIST_BASE_H_
