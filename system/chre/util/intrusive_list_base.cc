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

#include "chre/util/intrusive_list_base.h"

#include "chre/util/container_support.h"

namespace chre {
namespace intrusive_list_internal {

void IntrusiveListBase::doLinkBack(Node *newNode) {
  Node *prevNode = mSentinelNode.prev;
  prevNode->next = newNode;
  newNode->prev = prevNode;
  newNode->next = &mSentinelNode;
  mSentinelNode.prev = newNode;
  mSize++;
}

void IntrusiveListBase::doUnlinkNode(Node *node) {
  node->prev->next = node->next;
  node->next->prev = node->prev;
  node->next = nullptr;
  node->prev = nullptr;
  mSize--;
}

void IntrusiveListBase::doLinkAfter(Node *frontNode, Node *newNode) {
  Node *backNode = frontNode->next;
  frontNode->next = newNode;
  newNode->prev = frontNode;
  newNode->next = backNode;
  backNode->prev = newNode;
  mSize++;
}

void IntrusiveListBase::doUnlinkAll() {
  Node *currentNodePtr, *nextNodePtr;
  currentNodePtr = mSentinelNode.next;

  while (currentNodePtr != &mSentinelNode) {
    nextNodePtr = currentNodePtr->next;
    currentNodePtr->next = nullptr;
    currentNodePtr->prev = nullptr;
    currentNodePtr = nextNodePtr;
  }
}

}  // namespace intrusive_list_internal
}  // namespace chre