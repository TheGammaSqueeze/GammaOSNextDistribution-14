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

#pragma once

#include <functional>
#include <memory>
#include <mutex>
#include <queue>

#include "common/bind.h"
#include "common/callback.h"
#include "common/contextual_callback.h"
#include "os/thread.h"
#include "os/utils.h"

namespace bluetooth {
namespace os {

// A message-queue style handler for reactor-based thread to handle incoming events from different threads. When it's
// constructed, it will register a reactable on the specified thread; when it's destroyed, it will unregister itself
// from the thread.
class Handler : public common::IPostableContext {
 public:
  // Create and register a handler on given thread
  explicit Handler(Thread* thread);

  Handler(const Handler&) = delete;
  Handler& operator=(const Handler&) = delete;

  // Unregister this handler from the thread and release resource. Unhandled events will be discarded and not executed.
  virtual ~Handler();

  // Enqueue a closure to the queue of this handler
  virtual void Post(common::OnceClosure closure) override;

  // Remove all pending events from the queue of this handler
  void Clear();

  // Die if the current reactable doesn't stop before the timeout.  Must be called after Clear()
  void WaitUntilStopped(std::chrono::milliseconds timeout);

  template <typename Functor, typename... Args>
  void Call(Functor&& functor, Args&&... args) {
    Post(common::BindOnce(std::forward<Functor>(functor), std::forward<Args>(args)...));
  }

  template <typename T, typename Functor, typename... Args>
  void CallOn(T* obj, Functor&& functor, Args&&... args) {
    Post(common::BindOnce(std::forward<Functor>(functor), common::Unretained(obj), std::forward<Args>(args)...));
  }

  template <typename Functor, typename... Args>
  auto BindOnce(Functor&& functor, Args&&... args) {
    return common::ContextualOnceCallback(
        common::BindOnce(std::forward<Functor>(functor), std::forward<Args>(args)...), this);
  }

  template <typename Functor, typename T, typename... Args>
  auto BindOnceOn(T* obj, Functor&& functor, Args&&... args) {
    return common::ContextualOnceCallback(
        common::BindOnce(std::forward<Functor>(functor), common::Unretained(obj), std::forward<Args>(args)...), this);
  }

  template <typename Functor, typename... Args>
  auto Bind(Functor&& functor, Args&&... args) {
    return common::ContextualCallback(
        common::Bind(std::forward<Functor>(functor), std::forward<Args>(args)...), this);
  }

  template <typename Functor, typename T, typename... Args>
  auto BindOn(T* obj, Functor&& functor, Args&&... args) {
    return common::ContextualCallback(
        common::Bind(std::forward<Functor>(functor), common::Unretained(obj), std::forward<Args>(args)...), this);
  }

  template <typename T>
  friend class Queue;

  friend class Alarm;

  friend class RepeatingAlarm;

 private:
  inline bool was_cleared() const {
    return tasks_ == nullptr;
  };
  std::queue<common::OnceClosure>* tasks_;
  Thread* thread_;
  std::unique_ptr<Reactor::Event> event_;
  Reactor::Reactable* reactable_;
  mutable std::mutex mutex_;
  void handle_next_event();
};

}  // namespace os
}  // namespace bluetooth
