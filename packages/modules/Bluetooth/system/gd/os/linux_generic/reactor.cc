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

#include "os/reactor.h"

#include <fcntl.h>
#include <sys/epoll.h>
#include <sys/eventfd.h>
#include <unistd.h>

#include <algorithm>
#include <cerrno>
#include <cinttypes>
#include <cstring>

#include "os/log.h"

namespace {

// Use at most sizeof(epoll_event) * kEpollMaxEvents kernel memory
constexpr int kEpollMaxEvents = 64;
constexpr uint64_t kStopReactor = 1 << 0;
constexpr uint64_t kWaitForIdle = 1 << 1;

}  // namespace

namespace bluetooth {
namespace os {
using common::Closure;

struct Reactor::Event::impl {
  impl() {
    fd_ = eventfd(0, EFD_SEMAPHORE | EFD_NONBLOCK);
    ASSERT_LOG(fd_ != -1, "Unable to create nonblocking event file descriptor semaphore");
  }
  ~impl() {
    ASSERT_LOG(fd_ != -1, "Unable to close a never-opened event file descriptor");
    close(fd_);
    fd_ = -1;
  }
  int fd_ = -1;
};

Reactor::Event::Event() : pimpl_(new impl()) {}
Reactor::Event::~Event() {
  delete pimpl_;
}

bool Reactor::Event::Read() {
  uint64_t val = 0;
  return eventfd_read(pimpl_->fd_, &val) == 0;
}
int Reactor::Event::Id() const {
  return pimpl_->fd_;
}
void Reactor::Event::Clear() {
  uint64_t val;
  while (eventfd_read(pimpl_->fd_, &val) == 0) {
  }
}
void Reactor::Event::Close() {
  int close_status;
  RUN_NO_INTR(close_status = close(pimpl_->fd_));
  ASSERT(close_status != -1);
}
void Reactor::Event::Notify() {
  uint64_t val = 1;
  auto write_result = eventfd_write(pimpl_->fd_, val);
  ASSERT(write_result != -1);
}

class Reactor::Reactable {
 public:
  Reactable(int fd, Closure on_read_ready, Closure on_write_ready)
      : fd_(fd),
        on_read_ready_(std::move(on_read_ready)),
        on_write_ready_(std::move(on_write_ready)),
        is_executing_(false),
        removed_(false) {}
  const int fd_;
  Closure on_read_ready_;
  Closure on_write_ready_;
  bool is_executing_;
  bool removed_;
  std::mutex mutex_;
  std::unique_ptr<std::promise<void>> finished_promise_;
};

Reactor::Reactor() : epoll_fd_(0), control_fd_(0), is_running_(false) {
  RUN_NO_INTR(epoll_fd_ = epoll_create1(EPOLL_CLOEXEC));
  ASSERT_LOG(epoll_fd_ != -1, "could not create epoll fd: %s", strerror(errno));

  control_fd_ = eventfd(0, EFD_NONBLOCK);
  ASSERT(control_fd_ != -1);

  epoll_event control_epoll_event = {EPOLLIN, {.ptr = nullptr}};
  int result;
  RUN_NO_INTR(result = epoll_ctl(epoll_fd_, EPOLL_CTL_ADD, control_fd_, &control_epoll_event));
  ASSERT(result != -1);
}

Reactor::~Reactor() {
  int result;
  RUN_NO_INTR(result = epoll_ctl(epoll_fd_, EPOLL_CTL_DEL, control_fd_, nullptr));
  ASSERT(result != -1);

  RUN_NO_INTR(result = close(control_fd_));
  ASSERT(result != -1);

  RUN_NO_INTR(result = close(epoll_fd_));
  ASSERT(result != -1);
}

void Reactor::Run() {
  bool already_running = is_running_.exchange(true);
  ASSERT(!already_running);

  int timeout_ms = -1;
  bool waiting_for_idle = false;
  for (;;) {
    {
      std::unique_lock<std::mutex> lock(mutex_);
      invalidation_list_.clear();
    }
    epoll_event events[kEpollMaxEvents];
    int count;
    RUN_NO_INTR(count = epoll_wait(epoll_fd_, events, kEpollMaxEvents, timeout_ms));
    ASSERT(count != -1);
    if (waiting_for_idle && count == 0) {
      timeout_ms = -1;
      waiting_for_idle = false;
      std::scoped_lock<std::mutex> lock(mutex_);
      idle_promise_->set_value();
      idle_promise_ = nullptr;
    }

    for (int i = 0; i < count; ++i) {
      auto event = events[i];
      ASSERT(event.events != 0u);

      // If the ptr stored in epoll_event.data is nullptr, it means the control fd triggered
      if (event.data.ptr == nullptr) {
        uint64_t value;
        eventfd_read(control_fd_, &value);
        if ((value & kStopReactor) != 0) {
          is_running_ = false;
          return;
        } else if ((value & kWaitForIdle) != 0) {
          timeout_ms = 30;
          waiting_for_idle = true;
          continue;
        } else {
          LOG_ERROR("Unknown control_fd value %" PRIu64 "x", value);
          continue;
        }
      }
      auto* reactable = static_cast<Reactor::Reactable*>(event.data.ptr);
      std::unique_lock<std::mutex> lock(mutex_);
      executing_reactable_finished_ = nullptr;
      // See if this reactable has been removed in the meantime.
      if (std::find(invalidation_list_.begin(), invalidation_list_.end(), reactable) != invalidation_list_.end()) {
        continue;
      }

      {
        std::lock_guard<std::mutex> reactable_lock(reactable->mutex_);
        lock.unlock();
        reactable->is_executing_ = true;
      }
      if (event.events & (EPOLLIN | EPOLLHUP | EPOLLRDHUP | EPOLLERR) && !reactable->on_read_ready_.is_null()) {
        reactable->on_read_ready_.Run();
      }
      if (event.events & EPOLLOUT && !reactable->on_write_ready_.is_null()) {
        reactable->on_write_ready_.Run();
      }
      {
        std::unique_lock<std::mutex> reactable_lock(reactable->mutex_);
        reactable->is_executing_ = false;
        if (reactable->removed_) {
          reactable->finished_promise_->set_value();
          reactable_lock.unlock();
          delete reactable;
        }
      }
    }
  }
}

void Reactor::Stop() {
  if (!is_running_) {
    LOG_WARN("not running, will stop once it's started");
  }
  auto control = eventfd_write(control_fd_, kStopReactor);
  ASSERT(control != -1);
}

std::unique_ptr<Reactor::Event> Reactor::NewEvent() const {
  return std::make_unique<Reactor::Event>();
}

Reactor::Reactable* Reactor::Register(int fd, Closure on_read_ready, Closure on_write_ready) {
  uint32_t poll_event_type = 0;
  if (!on_read_ready.is_null()) {
    poll_event_type |= (EPOLLIN | EPOLLRDHUP);
  }
  if (!on_write_ready.is_null()) {
    poll_event_type |= EPOLLOUT;
  }
  auto* reactable = new Reactable(fd, on_read_ready, on_write_ready);
  epoll_event event = {
      .events = poll_event_type,
      .data = {.ptr = reactable},
  };
  int register_fd;
  RUN_NO_INTR(register_fd = epoll_ctl(epoll_fd_, EPOLL_CTL_ADD, fd, &event));
  ASSERT(register_fd != -1);
  return reactable;
}

void Reactor::Unregister(Reactor::Reactable* reactable) {
  ASSERT(reactable != nullptr);
  {
    std::lock_guard<std::mutex> lock(mutex_);
    invalidation_list_.push_back(reactable);
  }
  bool delaying_delete_until_callback_finished = false;
  {
    int result;
    std::lock_guard<std::mutex> reactable_lock(reactable->mutex_);
    RUN_NO_INTR(result = epoll_ctl(epoll_fd_, EPOLL_CTL_DEL, reactable->fd_, nullptr));
    if (result == -1 && errno == ENOENT) {
      LOG_INFO("reactable is invalid or unregistered");
    } else {
      ASSERT(result != -1);
    }

    // If we are unregistering during the callback event from this reactable, we delete it after the callback is
    // executed. reactable->is_executing_ is protected by reactable->mutex_, so it's thread safe.
    if (reactable->is_executing_) {
      reactable->removed_ = true;
      reactable->finished_promise_ = std::make_unique<std::promise<void>>();
      executing_reactable_finished_ = std::make_shared<std::future<void>>(reactable->finished_promise_->get_future());
      delaying_delete_until_callback_finished = true;
    }
  }
  // If we are unregistering outside of the callback event from this reactable, we delete it now
  if (!delaying_delete_until_callback_finished) {
    delete reactable;
  }
}

bool Reactor::WaitForUnregisteredReactable(std::chrono::milliseconds timeout) {
  std::lock_guard<std::mutex> lock(mutex_);
  if (executing_reactable_finished_ == nullptr) {
    return true;
  }
  auto stop_status = executing_reactable_finished_->wait_for(timeout);
  if (stop_status != std::future_status::ready) {
    LOG_ERROR("Unregister reactable timed out");
  }
  return stop_status == std::future_status::ready;
}

bool Reactor::WaitForIdle(std::chrono::milliseconds timeout) {
  auto promise = std::make_shared<std::promise<void>>();
  auto future = std::make_unique<std::future<void>>(promise->get_future());
  {
    std::lock_guard<std::mutex> lock(mutex_);
    idle_promise_ = promise;
  }

  auto control = eventfd_write(control_fd_, kWaitForIdle);
  ASSERT(control != -1);

  auto idle_status = future->wait_for(timeout);
  return idle_status == std::future_status::ready;
}

void Reactor::ModifyRegistration(Reactor::Reactable* reactable, ReactOn react_on) {
  ASSERT(reactable != nullptr);

  uint32_t poll_event_type = 0;
  if (react_on == REACT_ON_READ_ONLY || react_on == REACT_ON_READ_WRITE) {
    poll_event_type |= (EPOLLIN | EPOLLRDHUP);
  }
  if (react_on == REACT_ON_WRITE_ONLY || react_on == REACT_ON_READ_WRITE) {
    poll_event_type |= EPOLLOUT;
  }
  epoll_event event = {
      .events = poll_event_type,
      .data = {.ptr = reactable},
  };
  int modify_fd;
  RUN_NO_INTR(modify_fd = epoll_ctl(epoll_fd_, EPOLL_CTL_MOD, reactable->fd_, &event));
  ASSERT(modify_fd != -1);
}

}  // namespace os
}  // namespace bluetooth
