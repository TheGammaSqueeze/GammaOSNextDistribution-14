/*
 * Copyright (C) 2023 The Android Open Source Project
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

#include <chrono>
#include <condition_variable>
#include <functional>
#include <thread>

namespace android {

// NOLINTNEXTLINE(misc-unused-using-decls): False positive
using std::chrono_literals::operator""s;

struct FlatConCallbacks {
  std::function<void()> trigger;
};

class FlatteningController {
 public:
  static auto CreateInstance(FlatConCallbacks &cbks)
      -> std::shared_ptr<FlatteningController>;

  void Disable() {
    auto lock = std::lock_guard<std::mutex>(mutex_);
    flatten_next_frame_ = false;
    disabled_ = true;
  }

  /* Compositor should call this every frame */
  bool NewFrame();

  auto ShouldFlatten() const {
    return flatten_next_frame_;
  }

  void StopThread() {
    auto lock = std::lock_guard<std::mutex>(mutex_);
    cbks_ = {};
    cv_.notify_all();
  }

  static constexpr auto kTimeout = 1s;

 private:
  FlatteningController() = default;
  static void ThreadFn(const std::shared_ptr<FlatteningController> &fc);

  bool flatten_next_frame_{};
  bool disabled_{};
  decltype(std::chrono::system_clock::now()) sleep_until_{};
  std::mutex mutex_;
  std::condition_variable cv_;
  FlatConCallbacks cbks_;
};

}  // namespace android
