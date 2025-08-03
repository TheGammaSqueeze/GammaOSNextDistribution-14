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

template <typename T>
class FuzzInjectQueue {
 public:
  FuzzInjectQueue(IQueueEnqueue<T>* queue, Handler* handler) : handler_(handler) {
    buffer_ = new EnqueueBuffer<T>(queue);
  }
  ~FuzzInjectQueue() {
    delete buffer_;
  }

  void Inject(std::unique_ptr<T> data) {
    buffer_->Enqueue(std::move(data), handler_);
  }

 private:
  EnqueueBuffer<T>* buffer_;
  Handler* handler_;
};

}  // namespace fuzz
}  // namespace os
}  // namespace bluetooth
