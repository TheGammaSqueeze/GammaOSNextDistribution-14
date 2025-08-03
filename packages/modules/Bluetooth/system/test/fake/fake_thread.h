/*
 * Copyright 2023 The Android Open Source Project
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

#include <pthread.h>
#include <stdlib.h>
#include <unistd.h>

#include <condition_variable>
#include <cstdint>
#include <mutex>
#include <queue>
#include <string>

#include "osi/include/thread.h"

using thread_func = std::function<void(void* context)>;
using thread_data = void*;
using work_item = std::pair<thread_func, thread_data>;

class semaphore_t {
  std::condition_variable condition_;
  unsigned long count_ = 0;  // Initialized as locked.

 public:
  std::mutex mutex_;
  void notify() {
    std::lock_guard<decltype(mutex_)> lock(mutex_);
    ++count_;
    condition_.notify_one();
  }

  void wait() {
    std::unique_lock<decltype(mutex_)> lock(mutex_);
    while (!count_)  // Handle spurious wake-ups.
      condition_.wait(lock);
    --count_;
  }

  bool try_wait() {
    std::lock_guard<decltype(mutex_)> lock(mutex_);
    if (count_) {
      --count_;
      return true;
    }
    return false;
  }
};

struct thread_start_arg_t {
  thread_t* thread;
  int thread_id;
  semaphore_t start_sem;
};

struct thread_t {
  enum class State {
    STOPPED,
    RUNNING,
    QUIESCE,
  };

 private:
  State is_running_{State::STOPPED};
  mutable std::mutex is_running_lock_;
  semaphore_t thread_finish_semaphore;

 public:
  std::queue<work_item> work_queue;
  semaphore_t work_queue_semaphore;

  bool is_running() const;
  void set_state(State state);
  void quiesce();
  void notify_finished();

  pthread_t pthread_;
  pid_t tid_;
  std::string name_;
};
