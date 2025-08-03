/*
 * Copyright 2020 The Android Open Source Project
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

#define LOG_TAG "bt_headless_mode"

#include "test/headless/connect/connect.h"

#include <inttypes.h>

#include <chrono>
#include <cstdint>
#include <cstdio>
#include <future>
#include <string>

#include "base/logging.h"  // LOG() stdout and android log
#include "btif/include/stack_manager_t.h"
#include "os/log.h"  // android log only
#include "stack/include/acl_api.h"
#include "stack/include/btm_api.h"
#include "stack/include/btm_api_types.h"
#include "stack/include/hci_error_code.h"
#include "stack/include/l2cap_acl_interface.h"
#include "test/headless/get_options.h"
#include "test/headless/headless.h"
#include "test/headless/interface.h"
#include "test/headless/messenger.h"
#include "types/raw_address.h"

using namespace bluetooth::test;
using namespace std::chrono_literals;

const stack_manager_t* stack_manager_get_interface();

namespace {

bool f_simulate_stack_crash = false;

int do_connect([[maybe_unused]] unsigned int num_loops,
               [[maybe_unused]] const RawAddress& bd_addr,
               [[maybe_unused]] std::list<std::string> options) {
  int disconnect_wait_time{0};

  if (options.size() != 0) {
    std::string opt = options.front();
    options.pop_front();
    auto v = bluetooth::test::headless::GetOpt::Split(opt);
    if (v.size() == 2) {
      if (v[0] == "wait") disconnect_wait_time = std::stoi(v[1]);
    }
  }
  ASSERT_LOG(disconnect_wait_time >= 0, "Time cannot go backwards");

  headless::messenger::Context context{
      .stop_watch = Stopwatch("Connect_timeout"),
      .timeout = 3s,
      .check_point = {},
      .callbacks = {Callback::AclStateChanged},
  };

  LOG_CONSOLE("Creating connection to:%s", bd_addr.ToString().c_str());
  LOG(INFO) << "Creating classic connection to " << bd_addr.ToString();
  acl_create_classic_connection(bd_addr, false, false);

  std::shared_ptr<callback_params_t> acl{nullptr};
  while (context.stop_watch.LapMs() < 10000) {
    // If we have received callback results within this timeframe...
    if (headless::messenger::await_callback(context)) {
      while (!context.callback_ready_q.empty()) {
        std::shared_ptr<callback_params_t> p = context.callback_ready_q.front();
        context.callback_ready_q.pop_front();
        switch (p->CallbackType()) {
          case Callback::AclStateChanged: {
            acl = p;
          } break;
          default:
            LOG_CONSOLE("WARN Received callback for unasked:%s",
                        p->Name().c_str());
            break;
        }
      }
    }
    if (acl != nullptr) break;
  }

  if (acl != nullptr) {
    LOG_CONSOLE("Acl state changed:%s", acl->ToString().c_str());
  }

  uint64_t connect = std::chrono::duration_cast<std::chrono::milliseconds>(
                         std::chrono::system_clock::now().time_since_epoch())
                         .count();

  if (f_simulate_stack_crash) {
    LOG_CONSOLE("Just crushing stack");
    LOG(INFO) << "Just crushing stack";
    bluetoothInterface.disable();
  }
  std::shared_ptr<callback_params_t> acl2{nullptr};

  if (disconnect_wait_time == 0) {
    LOG_CONSOLE("Waiting to disconnect from supervision timeout\n");
    while (context.stop_watch.LapMs() < 10000) {
      // If we have received callback results within this timeframe...
      if (headless::messenger::await_callback(context)) {
        while (!context.callback_ready_q.empty()) {
          std::shared_ptr<callback_params_t> p =
              context.callback_ready_q.front();
          context.callback_ready_q.pop_front();
          switch (p->CallbackType()) {
            case Callback::AclStateChanged: {
              acl2 = p;
            } break;
            default:
              LOG_CONSOLE("WARN Received callback for unasked:%s",
                          p->Name().c_str());
              break;
          }
        }
      }
      if (acl2 != nullptr) break;
    }
    uint64_t disconnect =
        std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::system_clock::now().time_since_epoch())
            .count();

    LOG_CONSOLE("Disconnected after:%" PRId64 "ms from:%s acl:%s",
                disconnect - connect, bd_addr.ToString().c_str(),
                acl->ToString().c_str());
  }

  acl_disconnect_from_handle(
      ((acl_state_changed_params_t*)(acl2.get()))->acl_handle, HCI_SUCCESS,
      "BT headless disconnect");

  sleep(3);

  return 0;
}

}  // namespace

int bluetooth::test::headless::Connect::Run() {
  return RunOnHeadlessStack<int>([this]() {
    return do_connect(options_.loop_, options_.device_.front(),
                      options_.non_options_);
  });
}
