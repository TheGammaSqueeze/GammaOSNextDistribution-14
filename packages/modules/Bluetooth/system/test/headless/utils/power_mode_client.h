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

#define LOG_TAG "bt_headless_mode"

#include <base/strings/stringprintf.h>

#include <future>
#include <mutex>

#include "base/logging.h"  // LOG() stdout and android log
#include "bta/dm/bta_dm_int.h"
#include "stack/include/btm_client_interface.h"
#include "stack/include/btm_status.h"
#include "stack/include/hci_error_code.h"
#include "types/raw_address.h"

using namespace std::chrono_literals;

namespace {
const tBTM_PM_PWR_MD default_mandatory_sniff_mode = {
    .max = 0x0006,
    .min = 0x0006,
    .attempt = 0x0020,
    .timeout = 0x7fff,
    .mode = BTM_PM_MD_SNIFF,
};

const tBTM_PM_PWR_MD typical_sniff_mode = {
    .max = 800,  // 5 seconds
    .min = 400,  // 2.5 seconds
    .attempt = 4,
    .timeout = 1,
    .mode = BTM_PM_MD_SNIFF,
};

const tBTM_PM_PWR_MD default_active_mode = {
    .max = 0,      // Unused
    .min = 0,      // Unused
    .attempt = 0,  // Unused
    .timeout = 0,  // Unused
    .mode = BTM_PM_MD_ACTIVE,
};
}  // namespace

// tBTM_PM_STATUS_CBACK
struct power_mode_callback_t {
  const RawAddress bd_addr;
  tBTM_PM_STATUS status;
  uint16_t value;
  tHCI_STATUS hci_status;

  std::string ToString() const {
    return base::StringPrintf("bd_addr:%s pm_status:%s value:%hu hci_status:%s",
                              bd_addr.ToString().c_str(),
                              power_mode_status_text(status).c_str(), value,
                              hci_status_code_text(hci_status).c_str());
  }
};

struct pwr_command_t {
  std::promise<power_mode_callback_t> cmd_status_promise;
  std::promise<power_mode_callback_t> mode_event_promise;
};

struct pwr_result_t {
  tBTM_STATUS btm_status;
  std::future<power_mode_callback_t> cmd_status_future;
  std::future<power_mode_callback_t> mode_event_future;
};

namespace {

class Queue {
 public:
  void CallbackReceived(const power_mode_callback_t& data) {
    LOG_INFO("Power mode callback cnt:%zu data:%s", cnt++,
             data.ToString().c_str());
    std::unique_lock<std::mutex> lk(mutex);
    if (promises_map_[data.bd_addr].empty()) {
      LOG_INFO("Received unsolicited power mode callback: %s",
               data.ToString().c_str());
      return;
    }
    promises_map_[data.bd_addr].front().set_value(data);
    promises_map_[data.bd_addr].pop_front();
  }

  void CommandSent(const RawAddress& bd_addr, pwr_command_t&& pwr_command) {
    std::unique_lock<std::mutex> lk(mutex);
    promises_map_[bd_addr].push_back(std::move(pwr_command.cmd_status_promise));
    promises_map_[bd_addr].push_back(std::move(pwr_command.mode_event_promise));
  }

  void PopFront(const RawAddress& bd_addr) {
    std::unique_lock<std::mutex> lk(mutex);
    ASSERT_LOG(!promises_map_[bd_addr].empty(),
               "Unable to remove promise from empty bag of promises");
    promises_map_[bd_addr].pop_front();
  }

 private:
  mutable std::mutex mutex;
  std::unordered_map<RawAddress,
                     std::deque<std::promise<power_mode_callback_t>>>
      promises_map_;
  size_t cnt = 0;

} queue_;

}  // namespace

class PowerMode {
 public:
  class Client {
   public:
    Client(const uint8_t pm_id, const RawAddress& bd_addr)
        : pm_id_(pm_id), bd_addr_(bd_addr) {}

    // Used when the power mode command status is unsuccessful
    // to prevent waiting for a mode event that will never arrive.
    // Exposed to allow testing of these conditions.
    void remove_mode_event_promise() { queue_.PopFront(bd_addr_); }

    pwr_result_t set_sniff(pwr_command_t&& pwr_command) {
      return send_power_mode_command(
          std::move(pwr_command),
          get_btm_client_interface().link_policy.BTM_SetPowerMode(
              pm_id_, bd_addr_, &default_mandatory_sniff_mode));
    }
    pwr_result_t set_typical_sniff(pwr_command_t&& pwr_command) {
      return send_power_mode_command(
          std::move(pwr_command),
          get_btm_client_interface().link_policy.BTM_SetPowerMode(
              pm_id_, bd_addr_, &typical_sniff_mode));
    }

    pwr_result_t set_active(pwr_command_t&& pwr_command) {
      return send_power_mode_command(
          std::move(pwr_command),
          get_btm_client_interface().link_policy.BTM_SetPowerMode(
              pm_id_, bd_addr_, &default_active_mode));
    }

   private:
    pwr_result_t send_power_mode_command(pwr_command_t&& pwr_command,
                                         const tBTM_STATUS btm_status) {
      pwr_result_t result = {
          .btm_status = btm_status,
          .cmd_status_future = pwr_command.cmd_status_promise.get_future(),
          .mode_event_future = pwr_command.mode_event_promise.get_future(),
      };
      queue_.CommandSent(bd_addr_, std::move(pwr_command));
      return result;
    }

    const uint8_t pm_id_;
    const RawAddress bd_addr_;
  };

  PowerMode() {
    BTM_PmRegister(BTM_PM_DEREG, &bta_dm_cb.pm_id,
                   []([[maybe_unused]] const RawAddress& bd_addr,
                      [[maybe_unused]] tBTM_PM_STATUS status,
                      [[maybe_unused]] uint16_t value,
                      [[maybe_unused]] tHCI_STATUS hci_status) {});

    tBTM_STATUS btm_status =
        get_btm_client_interface().lifecycle.BTM_PmRegister(
            BTM_PM_REG_SET, &pm_id_,
            [](const RawAddress& bd_addr, tBTM_PM_STATUS status, uint16_t value,
               tHCI_STATUS hci_status) {
              queue_.CallbackReceived(power_mode_callback_t{
                  .bd_addr = bd_addr,
                  .status = status,
                  .value = value,
                  .hci_status = hci_status,
              });
            });

    ASSERT_LOG(BTM_SUCCESS == btm_status, "Failed to register power mode:%s",
               btm_status_text(btm_status).c_str());
  }

  ~PowerMode() {
    ASSERT(BTM_SUCCESS == get_btm_client_interface().lifecycle.BTM_PmRegister(
                              BTM_PM_DEREG, &pm_id_,
                              []([[maybe_unused]] const RawAddress& bd_addr,
                                 [[maybe_unused]] tBTM_PM_STATUS status,
                                 [[maybe_unused]] uint16_t value,
                                 [[maybe_unused]] tHCI_STATUS hci_status) {}));
  }

  Client GetClient(const RawAddress bd_addr) { return Client(pm_id_, bd_addr); }

 private:
  uint8_t pm_id_;
};
