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

#pragma once

#include <array>
#include <queue>

namespace bluetooth {
namespace common {

/**
 * A queue implementation which supports items with multiple priorities.
 * Items with greater priority value will be dequeued first.
 * When Enqueuing, the user can specify the priority (0 by default).
 * This can be used by ACL or L2CAP lower queue end sender to prioritize some link or channel, used by A2DP.
 */
template <typename T, int NUM_PRIORITY_LEVELS = 2>
class MultiPriorityQueue {
  static_assert(NUM_PRIORITY_LEVELS > 1);

 public:
  // Get the front item with the highest priority.  Queue must be non-empty.
  T& front() {
    return queues_[next_to_dequeue_.top()].front();
  }

  [[nodiscard]] bool empty() const {
    return next_to_dequeue_.empty();
  }

  [[nodiscard]] size_t size() const {
    return next_to_dequeue_.size();
  }

  // Push the item with specified priority
  void push(const T& t, int priority = 0) {
    queues_[priority].push(t);
    next_to_dequeue_.push(priority);
  }

  // Push the item with specified priority
  void push(T&& t, int priority = 0) {
    queues_[priority].push(std::forward<T>(t));
    next_to_dequeue_.push(priority);
  }

  // Pop the item in the front
  void pop() {
    queues_[next_to_dequeue_.top()].pop();
    next_to_dequeue_.pop();
  }

 private:
  std::array<std::queue<T>, NUM_PRIORITY_LEVELS> queues_;
  std::priority_queue<int> next_to_dequeue_;
};

}  // namespace common
}  // namespace bluetooth
