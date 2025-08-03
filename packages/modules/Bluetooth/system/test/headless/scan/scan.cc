/*
 * Copyright 2022 The Android Open Source Project
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

#include "test/headless/scan/scan.h"

#include "base/logging.h"  // LOG() stdout and android log
#include "os/log.h"
#include "test/headless/get_options.h"
#include "test/headless/headless.h"
#include "test/headless/interface.h"
#include "test/headless/log.h"
#include "test/headless/messenger.h"
#include "test/headless/property.h"
#include "test/headless/stopwatch.h"

using namespace bluetooth::test;
using namespace std::chrono_literals;

namespace {

int start_scan([[maybe_unused]] unsigned int num_loops) {
  LOG(INFO) << "Started Device Scan";

  ASSERT(bluetoothInterface.start_discovery() == BT_STATUS_SUCCESS);
  LOG_CONSOLE("Started inquiry - device discovery");

  headless::messenger::Context context{
      .stop_watch = Stopwatch("Inquiry_timeout"),
      .timeout = 1s,
      .check_point = {},
      .callbacks = {Callback::RemoteDeviceProperties, Callback::DeviceFound},
  };

  while (context.stop_watch.LapMs() < 10000) {
    // If we have received callback results within this timeframe...
    if (headless::messenger::await_callback(context)) {
      while (!context.callback_ready_q.empty()) {
        std::shared_ptr<callback_params_t> p = context.callback_ready_q.front();
        context.callback_ready_q.pop_front();
        switch (p->CallbackType()) {
          case Callback::RemoteDeviceProperties: {
            remote_device_properties_params_t* q =
                static_cast<remote_device_properties_params_t*>(p.get());
            for (const auto& p2 : q->properties()) {
              LOG_CONSOLE("  %s prop:%s", p->Name().c_str(),
                          p2->ToString().c_str());
            }
          } break;
          case Callback::DeviceFound: {
            device_found_params_t* q =
                static_cast<device_found_params_t*>(p.get());
            for (const auto& p2 : q->properties()) {
              LOG_CONSOLE("  %s prop:%s", p->Name().c_str(),
                          p2->ToString().c_str());
            }
          } break;
          default:
            LOG_CONSOLE("WARN Received callback for unasked:%s",
                        p->Name().c_str());
            break;
        }
      }
    }
  }

  LOG_CONSOLE("Stopped inquiry - device discovery");
  return 0;
}

}  // namespace

int bluetooth::test::headless::Scan::Run() {
  if (options_.loop_ < 1) {
    LOG_CONSOLE("This test requires at least a single loop");
    options_.Usage();
    return -1;
  }
  return RunOnHeadlessStack<int>([this]() {
    return start_scan(options_.loop_);
  });
}
