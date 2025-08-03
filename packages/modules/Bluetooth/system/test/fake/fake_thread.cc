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

#include "test/fake/fake_thread.h"

#include <base/strings/stringprintf.h>
#include <gtest/gtest.h>
#include <stdlib.h>

#include <mutex>

#include "include/check.h"

struct quiesce_t {
  thread_t* thread;
};

bool thread_t::is_running() const {
  std::lock_guard<decltype(is_running_lock_)> lock(is_running_lock_);
  return is_running_ == State::RUNNING;
}

void thread_t::set_state(State state) {
  std::lock_guard<decltype(is_running_lock_)> lock(is_running_lock_);
  is_running_ = state;
}

void thread_t::quiesce() {
  quiesce_t* quiesce = static_cast<quiesce_t*>(calloc(sizeof(quiesce_t), 1));
  CHECK(quiesce != nullptr);
  quiesce->thread = this;
  thread_post(
      this,
      [](void* context) {
        quiesce_t* quiesce = static_cast<quiesce_t*>(context);
        quiesce->thread->set_state(thread_t::State::QUIESCE);
      },
      static_cast<void*>(quiesce));

  // Wait for thread to stop
  thread_finish_semaphore.wait();
  // thread is queiesced so return
}

void thread_t::notify_finished() { thread_finish_semaphore.notify(); }
