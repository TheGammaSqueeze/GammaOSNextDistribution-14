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

#define LOG_TAG "bt_headless"

#include "test/headless/headless.h"

#include <dlfcn.h>  //  dlopen

#include <iostream>
#include <map>
#include <memory>

#include "base/logging.h"  // LOG() stdout and android log
#include "gd/os/log.h"
#include "include/check.h"
#include "include/hardware/bluetooth.h"
#include "test/headless/bt_stack_info.h"
#include "test/headless/interface.h"
#include "test/headless/log.h"
#include "test/headless/messenger.h"
#include "types/raw_address.h"

//
// Aggregate disparate variables from callback API into unified single structure
//
extern bt_interface_t bluetoothInterface;

using namespace bluetooth::test::headless;

namespace {

constexpr char kHeadlessIcon[] = "🗣";

std::map<const std::string, std::list<callback_function_t>>
    interface_api_callback_map_;

}  // namespace

void headless_add_callback(const std::string interface_name,
                           callback_function_t function) {
  if (interface_api_callback_map_.find(interface_name) ==
      interface_api_callback_map_.end()) {
    interface_api_callback_map_.emplace(interface_name,
                                        std::list<callback_function_t>());
  }
  interface_api_callback_map_[interface_name].push_back(function);
}

void headless_remove_callback(const std::string interface_name) {
  if (interface_api_callback_map_.find(interface_name) ==
      interface_api_callback_map_.end()) {
    ASSERT_LOG(false, "No callbacks registered for interface:%s",
               interface_name.c_str());
  }
  interface_api_callback_map_.erase(interface_name);
}

std::mutex adapter_state_mutex_;
std::condition_variable adapter_state_cv_;
bt_state_t bt_state_{BT_STATE_OFF};

void adapter_state_changed(bt_state_t state) {
  std::unique_lock<std::mutex> lck(adapter_state_mutex_);
  bt_state_ = state;
  adapter_state_cv_.notify_all();
}
void adapter_properties(bt_status_t status, int num_properties,
                        ::bt_property_t* properties) {
  const size_t num_callbacks = interface_api_callback_map_.size();
  auto callback_list = interface_api_callback_map_.find(__func__);
  if (callback_list != interface_api_callback_map_.end()) {
    for (auto callback : callback_list->second) {
      adapter_properties_params_t params(status, num_properties, properties);
      (callback)(&params);
    }
  }
  LOG_INFO(
      "num_callbacks:%zu status:%s num_properties:%d "
      "properties:%p",
      num_callbacks, bt_status_text(status).c_str(), num_properties,
      properties);
}

void remote_device_properties(bt_status_t status, RawAddress* bd_addr,
                              int num_properties, ::bt_property_t* properties) {
  CHECK(bd_addr != nullptr);
  const size_t num_callbacks = interface_api_callback_map_.size();
  auto callback_list = interface_api_callback_map_.find(__func__);
  if (callback_list != interface_api_callback_map_.end()) {
    RawAddress raw_address =
        (bd_addr != nullptr) ? *bd_addr : RawAddress::kEmpty;
    for (auto callback : callback_list->second) {
      remote_device_properties_params_t params(status, raw_address,
                                               num_properties, properties);
      (callback)(&params);
    }
  }
  LOG_INFO(
      "num_callbacks:%zu status:%s device:%s num_properties:%d "
      "properties:%p",
      num_callbacks, bt_status_text(status).c_str(), STR(*bd_addr),
      num_properties, properties);
}

// Aggregate disparate variables from callback API into unified single structure
void device_found(int num_properties, ::bt_property_t* properties) {
  [[maybe_unused]] const size_t num_callbacks =
      interface_api_callback_map_.size();
  auto callback_list = interface_api_callback_map_.find(__func__);
  if (callback_list != interface_api_callback_map_.end()) {
    for (auto callback : callback_list->second) {
      device_found_params_t params(num_properties, properties);
      (callback)(&params);
    }
  }
  LOG_INFO("Device found callback: num_properties:%d properties:%p",
           num_properties, properties);
}

void discovery_state_changed(bt_discovery_state_t state) {
  auto callback_list = interface_api_callback_map_.find(__func__);
  if (callback_list != interface_api_callback_map_.end()) {
    for (auto callback : callback_list->second) {
      discovery_state_changed_params_t params(state);
      (callback)(&params);
    }
  }
}

/** Bluetooth Legacy PinKey Request callback */
void pin_request([[maybe_unused]] RawAddress* remote_bd_addr,
                 [[maybe_unused]] bt_bdname_t* bd_name,
                 [[maybe_unused]] uint32_t cod,
                 [[maybe_unused]] bool min_16_digit) {
  LOG_INFO("%s", __func__);
}

void ssp_request([[maybe_unused]] RawAddress* remote_bd_addr,
                 [[maybe_unused]] bt_bdname_t* bd_name,
                 [[maybe_unused]] uint32_t cod,
                 [[maybe_unused]] bt_ssp_variant_t pairing_variant,
                 [[maybe_unused]] uint32_t pass_key) {
  LOG_INFO("%s", __func__);
}

/** Bluetooth Bond state changed callback */
/* Invoked in response to create_bond, cancel_bond or remove_bond */
void bond_state_changed([[maybe_unused]] bt_status_t status,
                        [[maybe_unused]] RawAddress* remote_bd_addr,
                        [[maybe_unused]] bt_bond_state_t state,
                        [[maybe_unused]] int fail_reason) {
  LOG_INFO("%s", __func__);
}

void address_consolidate([[maybe_unused]] RawAddress* main_bd_addr,
                         [[maybe_unused]] RawAddress* secondary_bd_addr) {
  LOG_INFO("%s", __func__);
}

void le_address_associate([[maybe_unused]] RawAddress* main_bd_addr,
                          [[maybe_unused]] RawAddress* secondary_bd_addr) {
  LOG_INFO("%s", __func__);
}

/** Bluetooth ACL connection state changed callback */
void acl_state_changed(bt_status_t status, RawAddress* remote_bd_addr,
                       bt_acl_state_t state, int transport_link_type,
                       bt_hci_error_code_t hci_reason,
                       bt_conn_direction_t direction, uint16_t acl_handle) {
  CHECK(remote_bd_addr != nullptr);
  const size_t num_callbacks = interface_api_callback_map_.size();
  auto callback_list = interface_api_callback_map_.find(__func__);
  if (callback_list != interface_api_callback_map_.end()) {
    RawAddress raw_address(*remote_bd_addr);
    for (auto callback : callback_list->second) {
      acl_state_changed_params_t params(status, raw_address, state,
                                        transport_link_type, hci_reason,
                                        direction, acl_handle);
      (callback)(&params);
    }
  }
  LOG_INFO("%s num_callbacks:%zu status:%s device:%s state:%s", __func__,
           num_callbacks, bt_status_text(status).c_str(),
           remote_bd_addr->ToString().c_str(),
           (state) ? "disconnected" : "connected");
}

/** Bluetooth Link Quality Report callback */
void link_quality_report([[maybe_unused]] uint64_t timestamp,
                         [[maybe_unused]] int report_id,
                         [[maybe_unused]] int rssi, [[maybe_unused]] int snr,
                         [[maybe_unused]] int retransmission_count,
                         [[maybe_unused]] int packets_not_receive_count,
                         [[maybe_unused]] int negative_acknowledgement_count) {
  LOG_INFO("%s", __func__);
}

/** Switch buffer size callback */
void switch_buffer_size([[maybe_unused]] bool is_low_latency_buffer_size) {
  LOG_INFO("%s", __func__);
}

/** Switch codec callback */
void switch_codec([[maybe_unused]] bool is_low_latency_buffer_size) {
  LOG_INFO("%s", __func__);
}

void thread_event([[maybe_unused]] bt_cb_thread_evt evt) {
  LOG_INFO("%s", __func__);
}

void dut_mode_recv([[maybe_unused]] uint16_t opcode,
                   [[maybe_unused]] uint8_t* buf,
                   [[maybe_unused]] uint8_t len) {
  LOG_INFO("%s", __func__);
}

void le_test_mode([[maybe_unused]] bt_status_t status,
                  [[maybe_unused]] uint16_t num_packets) {
  LOG_INFO("%s", __func__);
}

void energy_info([[maybe_unused]] bt_activity_energy_info* energy_info,
                 [[maybe_unused]] bt_uid_traffic_t* uid_data) {
  LOG_INFO("%s", __func__);
}

bt_callbacks_t bt_callbacks{
    /** set to sizeof(bt_callbacks_t) */
    .size = sizeof(bt_callbacks_t),
    .adapter_state_changed_cb = adapter_state_changed,
    .adapter_properties_cb = adapter_properties,
    .remote_device_properties_cb = remote_device_properties,
    .device_found_cb = device_found,
    .discovery_state_changed_cb = discovery_state_changed,
    .pin_request_cb = pin_request,
    .ssp_request_cb = ssp_request,
    .bond_state_changed_cb = bond_state_changed,
    .address_consolidate_cb = address_consolidate,
    .le_address_associate_cb = le_address_associate,
    .acl_state_changed_cb = acl_state_changed,
    .thread_evt_cb = thread_event,
    .dut_mode_recv_cb = dut_mode_recv,
    .le_test_mode_cb = le_test_mode,
    .energy_info_cb = energy_info,
    .link_quality_report_cb = link_quality_report,
    .switch_buffer_size_cb = switch_buffer_size,
    .switch_codec_cb = switch_codec,
};
// HAL HARDWARE CALLBACKS

// OS CALLOUTS
int acquire_wake_lock_co([[maybe_unused]] const char* lock_name) {
  LOG_INFO("%s", __func__);
  return 1;
}

int release_wake_lock_co([[maybe_unused]] const char* lock_name) {
  LOG_INFO("%s", __func__);
  return 0;
}

bt_os_callouts_t bt_os_callouts{
    .size = sizeof(bt_os_callouts_t),
    .acquire_wake_lock = acquire_wake_lock_co,
    .release_wake_lock = release_wake_lock_co,
};

void HeadlessStack::SetUp() {
  LOG(INFO) << __func__ << " Entry";

  const bool start_restricted = false;
  const bool is_common_criteria_mode = false;
  const int config_compare_result = 0;
  const bool is_atv = false;

  int status = bluetoothInterface.init(
      &bt_callbacks, start_restricted, is_common_criteria_mode,
      config_compare_result, StackInitFlags(), is_atv, nullptr);

  (status == BT_STATUS_SUCCESS)
      ? LOG(INFO) << __func__ << " Initialized bluetooth callbacks"
      : LOG(FATAL) << "Failed to initialize Bluetooth stack";

  status = bluetoothInterface.set_os_callouts(&bt_os_callouts);
  (status == BT_STATUS_SUCCESS)
      ? LOG(INFO) << __func__ << " Initialized os callouts"
      : LOG(ERROR) << "Failed to set up Bluetooth OS callouts";

  bluetoothInterface.enable();
  LOG_INFO("%s HeadlessStack stack has enabled", __func__);

  std::unique_lock<std::mutex> lck(adapter_state_mutex_);
  while (bt_state_ != BT_STATE_ON) adapter_state_cv_.wait(lck);
  LOG_INFO("%s HeadlessStack stack is operational", __func__);

  bt_stack_info_ = std::make_unique<BtStackInfo>();

  bluetooth::test::headless::start_messenger();

  LOG_CONSOLE("%s Headless stack has started up successfully", kHeadlessIcon);
}

void HeadlessStack::TearDown() {
  bluetooth::test::headless::stop_messenger();

  LOG_INFO("Stack has disabled");
  int status = bluetoothInterface.disable();

  LOG(INFO) << __func__ << " Interface has been disabled status:" << status;

  bluetoothInterface.cleanup();
  LOG(INFO) << __func__ << " Cleaned up hal bluetooth library";

  std::unique_lock<std::mutex> lck(adapter_state_mutex_);
  while (bt_state_ != BT_STATE_OFF) adapter_state_cv_.wait(lck);
  LOG_INFO("%s HeadlessStack stack has exited", __func__);
  LOG_CONSOLE("%s Headless stack has shutdown successfully", kHeadlessIcon);
}
