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

#include "test/fake/fake_looper.h"

#include <base/strings/stringprintf.h>
#include <gtest/gtest.h>
#include <stddef.h>
#include <stdlib.h>

#include <condition_variable>
#include <deque>
#include <mutex>
#include <queue>

#include "os/log.h"
#include "osi/include/allocator.h"
#include "test/fake/fake_thread.h"

pid_t get_thread_id() {
#if defined(OS_MACOSX)
  return pthread_mach_thread_np(pthread_self());
#elif defined(OS_LINUX)
#include <sys/syscall.h> /* For SYS_xxx definitions */
#include <unistd.h>
  return syscall(__NR_gettid);
#elif defined(__ANDROID__)
#include <sys/types.h>
#include <unistd.h>
  return gettid();
#else
  return 0;
#endif
}

// message loop
void* run_message_loop(void* arg) {
  ASSERT_LOG(arg != nullptr, "Must pass in a thread start argument");
  thread_t* thread = nullptr;
  {
    // Decouple thread portion from |start_arg| wrapper
    thread_start_arg_t* start_arg = static_cast<thread_start_arg_t*>(arg);
    thread = start_arg->thread;
    thread->set_state(thread_t::State::RUNNING);
    start_arg->start_sem.notify();
  }  // Cannot touch any offsets from |start_arg| anymore

  // thread->tid_ = syscall(__NR_gettid);
  thread->tid_ = get_thread_id();
  LOG_DEBUG("Thread message loop is operational name:%s tid:%u",
            thread->name_.c_str(), thread->tid_);

  while (thread->is_running()) {
    thread->work_queue_semaphore.wait();
    work_item work_item;
    size_t num_work_items = 0UL;
    {
      std::lock_guard<std::mutex> lock(thread->work_queue_semaphore.mutex_);
      num_work_items = thread->work_queue.size();
    }

    while (num_work_items > 0) {
      num_work_items--;
      {
        std::lock_guard<std::mutex> lock(thread->work_queue_semaphore.mutex_);
        work_item = thread->work_queue.front();
        thread->work_queue.pop();
      }
      // Execute work item
      work_item.first(work_item.second);
      osi_free(work_item.second);
    }
  }

  // Flush the rest of the work items
  work_item work_item;
  size_t num_work_items = 0UL;
  {
    std::lock_guard<std::mutex> lock(thread->work_queue_semaphore.mutex_);
    num_work_items = thread->work_queue.size();
  }
  while (num_work_items > 0) {
    num_work_items--;
    {
      std::lock_guard<std::mutex> lock(thread->work_queue_semaphore.mutex_);
      work_item = thread->work_queue.front();
      thread->work_queue.pop();
    }
    // Execute work item
    work_item.first(work_item.second);
    osi_free(work_item.second);
  }
  thread->set_state(thread_t::State::STOPPED);

  // Release the finish_semaphore for any waiters
  thread->notify_finished();
  return NULL;
}
