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

#define LOG_TAG "bt_headless_mode"

#include "test/headless/mode/mode.h"

#include <deque>
#include <future>
#include <memory>

#include "base/logging.h"  // LOG() stdout and android log
#include "btm_status.h"
#include "hci_error_code.h"
#include "include/macros.h"
#include "stack/include/acl_api.h"
#include "stack/include/l2cap_acl_interface.h"
#include "test/headless/get_options.h"
#include "test/headless/headless.h"
#include "test/headless/messenger.h"
#include "test/headless/utils/power_mode_client.h"
#include "types/raw_address.h"

using namespace bluetooth::test;
using namespace std::chrono_literals;

namespace {
int do_mode([[maybe_unused]] unsigned int num_loops,
            [[maybe_unused]] const RawAddress& bd_addr,
            [[maybe_unused]] std::list<std::string> options) {
  LOG_CONSOLE("Starting mode change test");
  // Requires a BR_EDR connection to work

  headless::messenger::Context context{
      .stop_watch = Stopwatch("Connect_timeout"),
      .timeout = 3s,
      .check_point = {},
      .callbacks = {Callback::AclStateChanged},
  };

  PowerMode power_mode;

  acl_create_classic_connection(bd_addr, false, false);

  std::shared_ptr<acl_state_changed_params_t> acl{nullptr};

  while (context.stop_watch.LapMs() < 10000) {
    // If we have received callback results within this timeframe...
    if (headless::messenger::await_callback(context)) {
      while (!context.callback_ready_q.empty()) {
        std::shared_ptr<callback_params_t> p = context.callback_ready_q.front();
        context.callback_ready_q.pop_front();
        switch (p->CallbackType()) {
          case Callback::AclStateChanged: {
            acl = Cast<acl_state_changed_params_t>(p);
            LOG_CONSOLE("Acl state changed:%s", acl->ToString().c_str());
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

  if (acl->state == BT_ACL_STATE_DISCONNECTED) {
    LOG_CONSOLE("Connection failed");
    return 1;
  }

  LOG_CONSOLE("Connection completed");
  PowerMode::Client client = power_mode.GetClient(bd_addr);

  {
    pwr_command_t pwr_command;
    pwr_result_t result = client.set_typical_sniff(std::move(pwr_command));
    LOG_CONSOLE("Sniff mode command sent");
    if (result.btm_status == BTM_CMD_STARTED) {
      // This awaits the command status callback
      power_mode_callback_t cmd_status = result.cmd_status_future.get();
      LOG_CONSOLE("Sniff mode command complete:%s",
                  cmd_status.ToString().c_str());
      if (cmd_status.status == BTM_PM_STS_PENDING) {
        LOG_CONSOLE("Sniff mode command accepted; awaiting mode change event");
        power_mode_callback_t mode_event = result.mode_event_future.get();
        LOG_CONSOLE("Sniff mode command complete:%s",
                    mode_event.ToString().c_str());
      } else {
        client.remove_mode_event_promise();
        LOG_CONSOLE("Command failed; no mode change event forthcoming");
      }
    } else {
      LOG_CONSOLE("Smiff mode command failed:%s",
                  btm_status_text(result.btm_status).c_str());
    }
  }

  {
    pwr_command_t pwr_command;
    pwr_result_t result = client.set_active(std::move(pwr_command));
    LOG_CONSOLE("Active mode command sent");
    if (result.btm_status == BTM_CMD_STARTED) {
      power_mode_callback_t cmd_status = result.cmd_status_future.get();
      LOG_CONSOLE("Active mode command complete:%s",
                  cmd_status.ToString().c_str());
      if (cmd_status.status == BTM_PM_STS_PENDING) {
        LOG_CONSOLE("Active mode command accepted; awaiting mode change event");
        power_mode_callback_t mode_event = result.mode_event_future.get();
        LOG_CONSOLE("Active mode command complete:%s",
                    mode_event.ToString().c_str());
      } else {
        client.remove_mode_event_promise();
        LOG_CONSOLE("Command failed; no mode change event forthcoming");
      }
    } else {
      LOG_CONSOLE("Active mode command failed:%s",
                  btm_status_text(result.btm_status).c_str());
    }
  }

  LOG_CONSOLE("Disconnecting");
  acl_disconnect_from_handle(acl->acl_handle, HCI_SUCCESS,
                             "BT headless disconnect");
  LOG_CONSOLE("Waiting to disconnect");

  sleep(3);

  return 0;
}

}  // namespace
   //
int bluetooth::test::headless::Mode::Run() {
  return RunOnHeadlessStack<int>([this]() {
    return do_mode(options_.loop_, options_.device_.front(),
                   options_.non_options_);
  });
}
