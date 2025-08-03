/*
 * Copyright 2021 The Android Open Source Project
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

#pragma once

#include <unistd.h>

#include <functional>
#include <mutex>
#include <queue>

#include "common/bidi_queue.h"
#include "common/bind.h"
#include "common/callback.h"
#include "os/handler.h"
#include "os/log.h"
#include "os/queue.h"

namespace bluetooth {
namespace os {

template <typename T>
class MockIQueueEnqueue : public IQueueEnqueue<T> {
 public:
  using EnqueueCallback = common::Callback<std::unique_ptr<T>()>;

  virtual void RegisterEnqueue(Handler* handler, EnqueueCallback callback) {
    ASSERT(registered_handler == nullptr);
    registered_handler = handler;
    registered_enqueue_callback = callback;
  }

  virtual void UnregisterEnqueue() {
    ASSERT(registered_handler != nullptr);
    registered_handler = nullptr;
    registered_enqueue_callback = {};
  }

  void run_enqueue(unsigned times = 1) {
    while (registered_handler != nullptr && times > 0) {
      times--;
      enqueued.push(registered_enqueue_callback.Run());
    }
  }

  Handler* registered_handler = nullptr;
  EnqueueCallback registered_enqueue_callback = {};
  std::queue<std::unique_ptr<T>> enqueued = {};
};

template <typename T>
class MockIQueueDequeue : public IQueueDequeue<T> {
 public:
  using DequeueCallback = common::Callback<void()>;

  virtual void RegisterDequeue(Handler* handler, DequeueCallback callback) {
    ASSERT(registered_handler == nullptr);
    registered_handler = handler;
    registered_dequeue_callback = callback;
  }

  virtual void UnregisterDequeue() {
    ASSERT(registered_handler != nullptr);
    registered_handler = nullptr;
    registered_dequeue_callback = {};
  }

  virtual std::unique_ptr<T> TryDequeue() {
    std::unique_ptr<T> front = std::move(enqueued.front());
    enqueued.pop();
    return front;
  }

  void run_dequeue(unsigned times = 1) {
    while (registered_handler != nullptr && times > 0) {
      times--;
      registered_dequeue_callback.Run();
    }
  }

  Handler* registered_handler = nullptr;
  DequeueCallback registered_dequeue_callback = {};
  std::queue<std::unique_ptr<T>> enqueued = {};
};

}  // namespace os
}  // namespace bluetooth
