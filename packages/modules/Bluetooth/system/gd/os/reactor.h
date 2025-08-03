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

#include <sys/epoll.h>

#include <atomic>
#include <functional>
#include <future>
#include <list>
#include <memory>
#include <mutex>
#include <thread>

#include "common/callback.h"
#include "os/utils.h"

namespace bluetooth {
namespace os {

// A simple implementation of reactor-style looper.
// When a reactor is running, the main loop is polling and blocked until at least one registered reactable is ready to
// read or write. It will invoke on_read_ready() or on_write_ready(), which is registered with the reactor. Then, it
// blocks again until ready event.
class Reactor {
 public:
  // An object used for Unregister() and ModifyRegistration()
  class Reactable;

  // Construct a reactor on the current thread
  Reactor();

  Reactor(const Reactor&) = delete;
  Reactor& operator=(const Reactor&) = delete;

  // Destruct this reactor and release its resources
  ~Reactor();

  // Start the reactor. The current thread will be blocked until Stop() is invoked and handled.
  void Run();

  // Stop the reactor. Must be invoked from a different thread. Note: all registered reactables will not be unregistered
  // by Stop(). If the reactor is not running, it will be stopped once it's started.
  void Stop();

  // Register a reactable fd to this reactor. Returns a pointer to a Reactable. Caller must use this object to
  // unregister or modify registration. Ownership of the memory space is NOT transferred to user.
  Reactable* Register(int fd, common::Closure on_read_ready, common::Closure on_write_ready);

  // Unregister a reactable from this reactor
  void Unregister(Reactable* reactable);

  // Wait for up to timeout milliseconds, and return true if the reactable finished executing.
  bool WaitForUnregisteredReactable(std::chrono::milliseconds timeout);

  // Wait for up to timeout milliseconds, and return true if we reached idle.
  bool WaitForIdle(std::chrono::milliseconds timeout);

  enum ReactOn {
    REACT_ON_READ_ONLY,
    REACT_ON_WRITE_ONLY,
    REACT_ON_READ_WRITE,
  };

  // Modify subscribed poll events on the fly
  void ModifyRegistration(Reactable* reactable, ReactOn react_on);

  class Event {
   public:
    Event();
    ~Event();
    bool Read();
    int Id() const;
    void Clear();
    void Close();
    void Notify();

   private:
    Event(const Event& handler) = default;
    struct impl;
    impl* pimpl_{nullptr};
  };
  std::unique_ptr<Reactor::Event> NewEvent() const;

 private:
  mutable std::mutex mutex_;
  int epoll_fd_;
  int control_fd_;
  std::atomic<bool> is_running_;
  std::list<Reactable*> invalidation_list_;
  std::shared_ptr<std::future<void>> executing_reactable_finished_;
  std::shared_ptr<std::promise<void>> idle_promise_;
};

}  // namespace os
}  // namespace bluetooth
