/*
 * Copyright 2019 The Android Open Source Project
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

#include "os/handler.h"

#include <cstring>

#include "common/bind.h"
#include "common/callback.h"
#include "os/log.h"
#include "os/reactor.h"
#include "os/utils.h"

namespace bluetooth {
namespace os {
using common::OnceClosure;

Handler::Handler(Thread* thread) : tasks_(new std::queue<OnceClosure>()), thread_(thread) {
  event_ = thread_->GetReactor()->NewEvent();
  reactable_ = thread_->GetReactor()->Register(
      event_->Id(), common::Bind(&Handler::handle_next_event, common::Unretained(this)), common::Closure());
}

Handler::~Handler() {
  {
    std::lock_guard<std::mutex> lock(mutex_);
    ASSERT_LOG(was_cleared(), "Handlers must be cleared before they are destroyed");
  }
  event_->Close();
}

void Handler::Post(OnceClosure closure) {
  {
    std::lock_guard<std::mutex> lock(mutex_);
    if (was_cleared()) {
      LOG_WARN("Posting to a handler which has been cleared");
      return;
    }
    tasks_->emplace(std::move(closure));
  }
  event_->Notify();
}

void Handler::Clear() {
  std::queue<OnceClosure>* tmp = nullptr;
  {
    std::lock_guard<std::mutex> lock(mutex_);
    ASSERT_LOG(!was_cleared(), "Handlers must only be cleared once");
    std::swap(tasks_, tmp);
  }
  delete tmp;

  event_->Clear();

  thread_->GetReactor()->Unregister(reactable_);
  reactable_ = nullptr;
}

void Handler::WaitUntilStopped(std::chrono::milliseconds timeout) {
  ASSERT(reactable_ == nullptr);
  ASSERT(thread_->GetReactor()->WaitForUnregisteredReactable(timeout));
}

void Handler::handle_next_event() {
  common::OnceClosure closure;
  {
    std::lock_guard<std::mutex> lock(mutex_);
    bool has_data = event_->Read();

    if (was_cleared()) {
      return;
    }
    ASSERT_LOG(has_data, "Notified for work but no work available");

    closure = std::move(tasks_->front());
    tasks_->pop();
  }
  std::move(closure).Run();
}

}  // namespace os
}  // namespace bluetooth
