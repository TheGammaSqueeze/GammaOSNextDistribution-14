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

#ifndef CHRE_UTIL_INTRUSIVE_LIST_IMPL_H_
#define CHRE_UTIL_INTRUSIVE_LIST_IMPL_H_

#include "chre/util/intrusive_list.h"

#include "chre/util/container_support.h"

namespace chre {

template <typename ElementType>
IntrusiveList<ElementType>::~IntrusiveList() {
  IntrusiveListBase::doUnlinkAll();
}

template <typename ElementType>
void IntrusiveList<ElementType>::link_back(ListNode<ElementType> *newNode) {
  return IntrusiveListBase::doLinkBack(&newNode->node);
}

template <typename ElementType>
ListNode<ElementType> &IntrusiveList<ElementType>::front() {
  CHRE_ASSERT(mSize > 0);
  return *reinterpret_cast<ListNode<ElementType> *>(mSentinelNode.next);
}

template <typename ElementType>
const ListNode<ElementType> &IntrusiveList<ElementType>::front() const {
  CHRE_ASSERT(mSize > 0);
  return *reinterpret_cast<const ListNode<ElementType> *>(mSentinelNode.next);
}

template <typename ElementType>
void IntrusiveList<ElementType>::unlink_front() {
  CHRE_ASSERT(mSize > 0);
  IntrusiveListBase::doUnlinkNode(mSentinelNode.next);
}

template <typename ElementType>
ListNode<ElementType> &IntrusiveList<ElementType>::back() {
  CHRE_ASSERT(mSize > 0);
  return *reinterpret_cast<ListNode<ElementType> *>(mSentinelNode.prev);
}

template <typename ElementType>
const ListNode<ElementType> &IntrusiveList<ElementType>::back() const {
  CHRE_ASSERT(mSize > 0);
  return *reinterpret_cast<const ListNode<ElementType> *>(mSentinelNode.prev);
}

template <typename ElementType>
void IntrusiveList<ElementType>::unlink_back() {
  CHRE_ASSERT(mSize > 0);
  IntrusiveListBase::doUnlinkNode(mSentinelNode.prev);
}

template <typename ElementType>
void IntrusiveList<ElementType>::unlink_node(ListNode<ElementType> *node) {
  CHRE_ASSERT(mSize > 0);
  IntrusiveListBase::doUnlinkNode(&node->node);
}

template <typename ElementType>
void IntrusiveList<ElementType>::link_after(ListNode<ElementType> *frontNode,
                                            ListNode<ElementType> *newNode) {
  IntrusiveListBase::doLinkAfter(&frontNode->node, &newNode->node);
}

}  // namespace chre

#endif  // CHRE_UTIL_INTRUSIVE_LIST_IMPL_H_
