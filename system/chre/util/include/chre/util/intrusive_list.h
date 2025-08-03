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

#ifndef CHRE_UTIL_INTRUSIVE_LIST_H_
#define CHRE_UTIL_INTRUSIVE_LIST_H_

#include <type_traits>
#include <utility>

#include "chre/util/intrusive_list_base.h"

#include "chre/util/container_support.h"

namespace chre {

template <typename ElementType>
struct ListNode {
  // Check if the ElementType is appropriate. Inappropriate ElementType
  // will lead to wrong behavior of the reinterpret_cast between
  // Node and ListNode that we use the retrieve item.
  static_assert(std::is_standard_layout<ElementType>::value,
                "must be std layout to alias");

  /**
   * Node that allows the linked list to link data.
   * This need to be the first member of ListNode or the reinterpret_cast
   * between Node and ListNode will fail.
   */
  intrusive_list_internal::Node node;

  /**
   * The data that the user wants to store.
   */
  ElementType item;

  /**
   * Construct a new List Node object by forwarding the arguments to
   * the constructor of ElementType.
   *
   * This breaks C++ assumptions of which constructor is called (move/copy) when
   * using assignment operator. However, in this case, it is safe to do so since
   * ListNode is not copyable (Node is not copyable).
   */
  template <typename... Args>
  ListNode(Args &&...args) : item(std::forward<Args>(args)...) {}

  ~ListNode() {
    CHRE_ASSERT(node.prev == nullptr && node.next == nullptr);
  }
};

/**
 * A container for storing data in a linked list. Note that this container does
 * not allocate any memory, the caller need to manage the memory of the
 * data/node that it wants to insert.
 *
 * Caller need to turn the data into nodes by doing ListNode<ElementType> before
 * using the linked list to manage data. This approach is preferred over the
 * more intrusive way to let user create a new node structure that inherits from
 * Node, since user will not need to worry about handling the extra node type
 * but focus on interacting with the list.
 *
 * Note that although ListNode.node is accessible to client code, user should
 * not modify them directly without using the linked list.
 *
 * Example:
 *  typedef ListNode<int> ListIntNode;
 *  ListIntNode node(10);
 *  IntrusiveList<int> myList;
 *  myList.push_back(node);
 *
 * Note that myList is declared after node so that myList will be destructed
 * before node.
 *
 * @tparam ElementType: The data type that wants to be stored using the link
 * list.
 */
template <typename ElementType>
class IntrusiveList : private intrusive_list_internal::IntrusiveListBase {
  // Check if the ListNode layout is appropriate. Inappropriate or
  // ListNode will lead to wrong behavior of the reinterpret_cast between
  // Node and ListNode that we use the retrieve item.
  static_assert(offsetof(ListNode<ElementType>, node) == 0,
                "node must be the first element");

 public:
  class Iterator {
    using Node = ::chre::intrusive_list_internal::Node;

   public:
    Iterator(Node *node) : mNode(node){};

    ListNode<ElementType> &operator*() const {
      return *reinterpret_cast<ListNode<ElementType> *>(mNode);
    }

    ListNode<ElementType> *operator->() {
      return reinterpret_cast<ListNode<ElementType> *>(mNode);
    }

    Iterator &operator++() {
      mNode = mNode->next;
      return *this;
    }

    Iterator &operator--() {
      mNode = mNode->prev;
      return *this;
    }

    bool operator==(Iterator other) const {
      return mNode == other.mNode;
    }
    bool operator!=(Iterator other) const {
      return mNode != other.mNode;
    }

   private:
    Node *mNode;
  };

  /**
   * Default construct a new Intrusive Linked List.
   */
  IntrusiveList() = default;

  /**
   * Unlink all node when destroy the Intrusive List object.
   */
  ~IntrusiveList();

  /**
   * Examines if the linked list is empty.
   *
   * @return true if the linked list has no linked node.
   */
  bool empty() const {
    return mSize == 0;
  }

  /**
   * Returns the number of nodes stored in this linked list.
   *
   * @return The number of nodes in the linked list.
   */
  size_t size() const {
    return mSize;
  }

  /**
   * Link a new node to the end of the linked list.
   *
   * @param newNode: the node to push to the pack of the linked list.
   */
  void link_back(ListNode<ElementType> *newNode);

  /**
   * Returns a reference to the first node of the linked list.
   * It is not allowed to call this on a empty list.
   *
   * @return The first node of the linked list
   */
  ListNode<ElementType> &front();
  const ListNode<ElementType> &front() const;

  /**
   * Unlink the first node from the list.
   * It is not allowed to call this on a empty list.
   * Note that this function does not free the memory of the node.
   */
  void unlink_front();

  /**
   * Returns a reference to the last node of the linked list.
   * It is not allowed to call this on a empty list.
   *
   * @return The last node of the linked list
   */
  ListNode<ElementType> &back();
  const ListNode<ElementType> &back() const;

  /**
   * Unlink the last node from the list.
   * It is not allowed to call this on a empty list.
   * Note that this function does not free the memory of the node.
   */
  void unlink_back();

  /**
   * Remove a node from its list.
   *
   * @param node: Node that need to be unlinked.
   */
  void unlink_node(ListNode<ElementType> *node);

  /**
   * Link a node after a given node.
   *
   * @param frontNode the old node that will be in front of the new node.
   * @param newNode the new node that will be link to the list.
   */
  void link_after(ListNode<ElementType> *frontNode,
                  ListNode<ElementType> *newNode);

  /**
   * @return Iterator from the beginning of the linked list.
   */
  Iterator begin() {
    return mSentinelNode.next;
  }

  /**
   * @return Iterator from the end of the linked list.
   */
  Iterator end() {
    return &mSentinelNode;
  }
};

}  // namespace chre

#include "chre/util/intrusive_list_impl.h"

#endif  // CHRE_UTIL_INTRUSIVE_LIST_H_
