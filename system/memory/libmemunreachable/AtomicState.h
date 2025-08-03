/*
 * Copyright (C) 2022 The Android Open Source Project
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

#include <atomic>
#include <chrono>
#include <functional>
#include <thread>

#include <gtest/gtest_prod.h>

#include "android-base/macros.h"

namespace android {

/*
 * AtomicState manages updating or waiting on a state enum between multiple threads.
 */
template <typename T>
class AtomicState {
 public:
  explicit AtomicState(T state) : state_(state) {}
  ~AtomicState() = default;

  /*
   * Set the state to `to`.  Wakes up any waiters that are waiting on the new state.
   */
  void set(T to) { state_.store(to); }

  /*
   * If the state is `from`, change it to `to` and return true.  Otherwise don't change
   * it and return false.  If the state is changed, wakes up any waiters that are waiting
   * on the new state.
   */
  bool transition(T from, T to) { return state_.compare_exchange_strong(from, to); }

  /*
   * If the state is `from`, change it to `to` and return true.  Otherwise, call `or_func`,
   * set the state to the value it returns and return false.  Wakes up any waiters that are
   * waiting on the new state or on the state set by or_func.
   */
  bool transition_or(T from, T to, const std::function<T()>& orFunc) {
    if (!state_.compare_exchange_strong(from, to)) {
      state_.store(orFunc());
      return false;
    }
    return true;
  }

  /*
   * Block until the state is either `state1` or `state2`, or the time limit is reached.
   * Busy loops with short sleeps.  Returns true if the time limit was not reached, false
   * if it was reached.
   */
  bool wait_for_either_of(T state1, T state2, std::chrono::nanoseconds timeout) {
    using namespace std::chrono_literals;
    auto end = std::chrono::steady_clock::now() + timeout;
    const std::chrono::nanoseconds sleep_time = 500us;
    while (true) {
      T state = state_.load();
      if (state == state1 || state == state2) {
        return true;
      }
      auto now = std::chrono::steady_clock::now();
      if (now > end) {
        return false;
      }
      std::this_thread::sleep_for(std::min(sleep_time, end - now));
    }
  }

 private:
  std::atomic<T> state_;

  FRIEND_TEST(AtomicStateTest, transition);
  FRIEND_TEST(AtomicStateTest, wait);

  DISALLOW_COPY_AND_ASSIGN(AtomicState);
};

}  // namespace android
