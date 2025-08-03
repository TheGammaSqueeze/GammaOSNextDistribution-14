/*
 * Copyright 2020 The Android Open Source Project
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

#include "os/queue.h"

namespace bluetooth {
namespace os {
namespace fuzz {

// Drops stuff you send it, and banishes it into the void.
template <typename T>
class DevNullQueue {
 public:
  DevNullQueue(IQueueDequeue<T>* queue, Handler* handler) : queue_(queue), handler_(handler) {}
  ~DevNullQueue() {}

  void Start() {
    queue_->RegisterDequeue(handler_, common::Bind(&DevNullQueue::Dump, common::Unretained(this)));
  }

  void Stop() {
    queue_->UnregisterDequeue();
  }

  void Dump() {
    queue_->TryDequeue();
  }

 private:
  IQueueDequeue<T>* queue_;
  Handler* handler_;
};

}  // namespace fuzz
}  // namespace os
}  // namespace bluetooth
