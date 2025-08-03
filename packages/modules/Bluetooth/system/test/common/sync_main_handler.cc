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

#include <chrono>
#include <future>

#include "stack/include/main_thread.h"

constexpr int sync_timeout_in_ms = 3000;

void sync_main_handler() {
  std::promise promise = std::promise<void>();
  std::future future = promise.get_future();
  post_on_bt_main([&promise]() { promise.set_value(); });
  future.wait_for(std::chrono::milliseconds(sync_timeout_in_ms));
};
