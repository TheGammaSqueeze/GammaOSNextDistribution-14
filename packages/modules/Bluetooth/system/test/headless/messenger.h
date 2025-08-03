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

#include <cstddef>
#include <deque>
#include <string>

#include "test/headless/interface.h"
#include "test/headless/stopwatch.h"
#include "test/headless/timeout.h"

namespace bluetooth::test::headless {

using CheckPoint = size_t;

void start_messenger();
void stop_messenger();

namespace messenger {

struct Context {
  Stopwatch stop_watch;
  Timeout timeout;
  CheckPoint check_point;
  bool SetCallbacks(const std::vector<std::string>& callbacks);
  std::vector<Callback> callbacks;
  std::deque<std::shared_ptr<callback_params_t>> callback_ready_q;
};

// Called by client to await any callback for the given callbacks
bool await_callback(Context& context);

namespace sdp {

CheckPoint get_check_point();
bool await_service_discovery(const Timeout& timeout,
                             const CheckPoint& check_point, const size_t count);
std::deque<remote_device_properties_params_t> collect_from(
    CheckPoint& check_point);

}  // namespace sdp
}  // namespace messenger

}  // namespace bluetooth::test::headless
