/*
 * Copyright (C) 2023 The Android Open Source Project
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

#include "berberis/runtime_primitives/signal_queue.h"

#include <atomic>

#include "berberis/base/forever_pool.h"

namespace berberis {

siginfo_t* SignalQueue::AllocSignal() {
  Node* node = ForeverPool<Node>::Alloc();
  return &node->info;
}

void SignalQueue::EnqueueSignal(siginfo_t* info) {
  Node* node = reinterpret_cast<Node*>(info);
  Node* produced = produced_.load(std::memory_order_relaxed);
  do {
    node->next = produced;
  } while (!produced_.compare_exchange_weak(produced, node, std::memory_order_release));
}

siginfo_t* SignalQueue::DequeueSignalUnsafe() {
  // Pick everything produced so far.
  Node* produced = produced_.exchange(nullptr, std::memory_order_acquire);

  // Put in front of consumed.
  if (produced) {
    Node* last = produced;
    while (last->next) {
      last = last->next;
    }
    last->next = consumed_;
    consumed_ = produced;
  }

  if (!consumed_) {
    return nullptr;
  }

  // Consumed is in reverse order of arrival.
  Node** best_p = &consumed_;
  for (Node** curr_p = &consumed_->next; *curr_p; curr_p = &(*curr_p)->next) {
    // As the list is in reverse order, use '<=' to get last match.
    if ((*curr_p)->info.si_signo <= (*best_p)->info.si_signo) {
      best_p = curr_p;
    }
  }

  Node* best = *best_p;
  *best_p = best->next;

  return &best->info;
}

void SignalQueue::FreeSignal(siginfo_t* info) {
  ForeverPool<Node>::Free(reinterpret_cast<Node*>(info));
}

}  // namespace berberis
