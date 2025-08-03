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

#ifndef BERBERIS_RUNTIME_PRIMITIVES_SIGNAL_QUEUE_H_
#define BERBERIS_RUNTIME_PRIMITIVES_SIGNAL_QUEUE_H_

#include <csignal>

#include <atomic>

#include "berberis/base/macros.h"

namespace berberis {

// Enqueue signals from multiple threads and signal handlers.
// Dequeue signals from target thread only.
// Signals with smaller numbers have higher priority.
// Signals with equal numbers are FIFO.
//
// ATTENTION: It's somewhat subtle that we need priorities here.
// As we sit on top of host signals, they are already delivered by priorities,
// thus we might use non-priority queue... But this is not completely true!
// The issue is that we don't run signal handlers immediately when signals are
// delivered. If a signal handler raises another signal with high priority, it
// must be delivered before already-queued signals with lower priorities.
//
// In fact this is multi producer, single consumer lock-free queue.
// Enqueue by pushing to shared lock-free single-linked 'produced' list.
// Dequeue by moving everything from 'produced' to non-shared 'consumed' list
// and then doing linear search by priority. As expected count of pending
// signals is small, this should have acceptable performance.
// No ABA as there is only one consumer.
class SignalQueue {
 public:
  constexpr SignalQueue() : produced_(nullptr), consumed_(nullptr) {}

  // Allocate signal.
  siginfo_t* AllocSignal();

  // Add allocated signal to the queue.
  // Can be called from signal handlers.
  // Can be called concurrently from multiple threads.
  void EnqueueSignal(siginfo_t* info);

  // Get next signal from the queue according to priorities.
  // ATTENTION: call from single thread only!
  siginfo_t* DequeueSignalUnsafe();

  // Free dequeued signal.
  void FreeSignal(siginfo_t* info);

 private:
  // Can reinterpret_cast siginfo_t* -> Node*!
  struct Node {
    siginfo_t info;
    Node* next;
  };

  std::atomic<Node*> produced_;
  Node* consumed_;

  DISALLOW_COPY_AND_ASSIGN(SignalQueue);
};

}  // namespace berberis

#endif  // BERBERIS_RUNTIME_PRIMITIVES_SIGNAL_QUEUE_H_
