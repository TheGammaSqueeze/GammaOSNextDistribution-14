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

#define LOG_TAG "bt_headless_scan"

#include "test/headless/adapter/adapter.h"

#include "base/logging.h"  // LOG() stdout and android log
#include "gd/os/log.h"
#include "test/headless/headless.h"
#include "test/headless/interface.h"
#include "test/headless/log.h"
#include "test/headless/messenger.h"
#include "test/headless/stopwatch.h"

using namespace bluetooth::test;
using namespace std::chrono_literals;

namespace {

unsigned kTimeoutMs = 5000;

int get_adapter_info([[maybe_unused]] unsigned int num_loops) {
  LOG(INFO) << "Started Device Adapter Properties";

  ASSERT(bluetoothInterface.get_adapter_properties() == BT_STATUS_SUCCESS);
  LOG_CONSOLE("Started get adapter properties");

  headless::messenger::Context context{
      .stop_watch = Stopwatch(__func__),
      .timeout = 1s,  // Poll time
      .check_point = {},
      .callbacks = {Callback::AdapterProperties},
  };

  bool adapter_properties_found = false;
  while (context.stop_watch.LapMs() < kTimeoutMs) {
    // If we have received callback results within this timeframe...
    if (headless::messenger::await_callback(context)) {
      while (!context.callback_ready_q.empty()) {
        std::shared_ptr<callback_params_t> p = context.callback_ready_q.front();
        context.callback_ready_q.pop_front();
        switch (p->CallbackType()) {
          case Callback::AdapterProperties: {
            adapter_properties_params_t* q =
                static_cast<adapter_properties_params_t*>(p.get());
            for (const auto& p2 : q->properties()) {
              LOG_CONSOLE("  %s prop:%s", p->Name().c_str(),
                          p2->ToString().c_str());
            }
            adapter_properties_found = true;
          } break;
          default:
            LOG_CONSOLE("WARN Received callback for unasked:%s",
                        p->Name().c_str());
            break;
        }
      }
    }
    if (adapter_properties_found) break;
  }

  LOG_CONSOLE("Retrieved adapter properties");
  return 0;
}

}  // namespace

int bluetooth::test::headless::Adapter::Run() {
  if (options_.loop_ < 1) {
    LOG_CONSOLE("This test requires at least a single loop");
    options_.Usage();
    return -1;
  }
  return RunOnHeadlessStack<int>(
      [this]() { return get_adapter_info(options_.loop_); });
}
