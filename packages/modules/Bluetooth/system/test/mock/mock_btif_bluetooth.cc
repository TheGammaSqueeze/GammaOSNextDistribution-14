/*
 * Copyright 2021 The Android Open Source Project
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

/*
 * Generated mock file from original source file
 *   Functions generated:17
 *
 *  mockcify.pl ver 0.2.1
 */
// Mock include file to share data between tests and mock
#include "test/mock/mock_btif_bluetooth.h"

#include <cstdint>

#include "test/common/mock_functions.h"
#include "types/raw_address.h"

// Mocked compile conditionals, if any
// Mocked internal structures, if any

namespace test {
namespace mock {
namespace btif_bluetooth {

// Function state capture and return values, if needed
struct is_atv_device is_atv_device;
struct is_common_criteria_mode is_common_criteria_mode;
struct is_restricted_mode is_restricted_mode;
struct dut_mode_configure dut_mode_configure;
struct dut_mode_send dut_mode_send;
struct get_common_criteria_config_compare_result
    get_common_criteria_config_compare_result;
struct get_remote_device_properties get_remote_device_properties;
struct get_remote_device_property get_remote_device_property;
struct get_remote_services get_remote_services;
struct le_test_mode le_test_mode;
struct set_remote_device_property set_remote_device_property;
struct set_hal_cbacks set_hal_cbacks;
struct invoke_switch_buffer_size_cb invoke_switch_buffer_size_cb;

}  // namespace btif_bluetooth
}  // namespace mock
}  // namespace test

// Mocked functions, if any
bool is_atv_device() {
  inc_func_call_count(__func__);
  return test::mock::btif_bluetooth::is_atv_device();
}
bool is_common_criteria_mode() {
  inc_func_call_count(__func__);
  return test::mock::btif_bluetooth::is_common_criteria_mode();
}
bool is_restricted_mode() {
  inc_func_call_count(__func__);
  return test::mock::btif_bluetooth::is_restricted_mode();
}
int dut_mode_configure(uint8_t enable) {
  inc_func_call_count(__func__);
  return test::mock::btif_bluetooth::dut_mode_configure(enable);
}
int dut_mode_send(uint16_t opcode, uint8_t* buf, uint8_t len) {
  inc_func_call_count(__func__);
  return test::mock::btif_bluetooth::dut_mode_send(opcode, buf, len);
}
int get_common_criteria_config_compare_result() {
  inc_func_call_count(__func__);
  return test::mock::btif_bluetooth::
      get_common_criteria_config_compare_result();
}
int get_remote_device_properties(RawAddress* remote_addr) {
  inc_func_call_count(__func__);
  return test::mock::btif_bluetooth::get_remote_device_properties(remote_addr);
}
int get_remote_device_property(RawAddress* remote_addr,
                               bt_property_type_t type) {
  inc_func_call_count(__func__);
  return test::mock::btif_bluetooth::get_remote_device_property(remote_addr,
                                                                type);
}
int get_remote_services(RawAddress* remote_addr) {
  inc_func_call_count(__func__);
  return test::mock::btif_bluetooth::get_remote_services(remote_addr);
}
int le_test_mode(uint16_t opcode, uint8_t* buf, uint8_t len) {
  inc_func_call_count(__func__);
  return test::mock::btif_bluetooth::le_test_mode(opcode, buf, len);
}
int set_remote_device_property(RawAddress* remote_addr,
                               const bt_property_t* property) {
  inc_func_call_count(__func__);
  return test::mock::btif_bluetooth::set_remote_device_property(remote_addr,
                                                                property);
}
void set_hal_cbacks(bt_callbacks_t* callbacks) {
  inc_func_call_count(__func__);
  test::mock::btif_bluetooth::set_hal_cbacks(callbacks);
}
void invoke_switch_buffer_size_cb(bool invoke_switch_buffer_size_cb) {
  inc_func_call_count(__func__);
  test::mock::btif_bluetooth::invoke_switch_buffer_size_cb(
      invoke_switch_buffer_size_cb);
}

// END mockcify generation
