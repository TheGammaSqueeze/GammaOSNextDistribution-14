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
/*
 * Generated mock file from original source file
 *   Functions generated:18
 *
 *  mockcify.pl ver 0.6.0
 */
// Mock include file to share data between tests and mock
#include "test/mock/mock_btif_util.h"

#include <cstdint>

#include "test/common/mock_functions.h"

// Original usings

// Mocked internal structures, if any

namespace test {
namespace mock {
namespace btif_util {

// Function state capture and return values, if needed
struct ascii_2_hex ascii_2_hex;
struct devclass2uint devclass2uint;
struct dump_adapter_scan_mode dump_adapter_scan_mode;
struct dump_av_audio_state dump_av_audio_state;
struct dump_av_conn_state dump_av_conn_state;
struct dump_bt_status dump_bt_status;
struct dump_dm_event dump_dm_event;
struct dump_dm_search_event dump_dm_search_event;
struct dump_hd_event dump_hd_event;
struct dump_hf_client_event dump_hf_client_event;
struct dump_hf_event dump_hf_event;
struct dump_hh_event dump_hh_event;
struct dump_property_type dump_property_type;
struct dump_rc_event dump_rc_event;
struct dump_rc_notification_event_id dump_rc_notification_event_id;
struct dump_rc_pdu dump_rc_pdu;
struct dump_thread_evt dump_thread_evt;
struct uint2devclass uint2devclass;

}  // namespace btif_util
}  // namespace mock
}  // namespace test

// Mocked function return values, if any
namespace test {
namespace mock {
namespace btif_util {

int ascii_2_hex::return_value = 0;
uint32_t devclass2uint::return_value = 0;
const char* dump_adapter_scan_mode::return_value = nullptr;
const char* dump_av_audio_state::return_value = nullptr;
const char* dump_av_conn_state::return_value = nullptr;
const char* dump_bt_status::return_value = nullptr;
const char* dump_dm_event::return_value = nullptr;
const char* dump_dm_search_event::return_value = nullptr;
const char* dump_hd_event::return_value = nullptr;
const char* dump_hf_client_event::return_value = nullptr;
const char* dump_hf_event::return_value = nullptr;
const char* dump_hh_event::return_value = nullptr;
const char* dump_property_type::return_value = nullptr;
const char* dump_rc_event::return_value = nullptr;
const char* dump_rc_notification_event_id::return_value = nullptr;
const char* dump_rc_pdu::return_value = nullptr;
const char* dump_thread_evt::return_value = nullptr;

}  // namespace btif_util
}  // namespace mock
}  // namespace test

// Mocked functions, if any
int ascii_2_hex(const char* p_ascii, int len, uint8_t* p_hex) {
  inc_func_call_count(__func__);
  return test::mock::btif_util::ascii_2_hex(p_ascii, len, p_hex);
}
uint32_t devclass2uint(const DEV_CLASS dev_class) {
  inc_func_call_count(__func__);
  return test::mock::btif_util::devclass2uint(dev_class);
}
const char* dump_adapter_scan_mode(bt_scan_mode_t mode) {
  inc_func_call_count(__func__);
  return test::mock::btif_util::dump_adapter_scan_mode(mode);
}
const char* dump_av_audio_state(uint16_t event) {
  inc_func_call_count(__func__);
  return test::mock::btif_util::dump_av_audio_state(event);
}
const char* dump_av_conn_state(uint16_t event) {
  inc_func_call_count(__func__);
  return test::mock::btif_util::dump_av_conn_state(event);
}
const char* dump_bt_status(bt_status_t status) {
  inc_func_call_count(__func__);
  return test::mock::btif_util::dump_bt_status(status);
}
const char* dump_dm_event(uint16_t event) {
  inc_func_call_count(__func__);
  return test::mock::btif_util::dump_dm_event(event);
}
const char* dump_dm_search_event(uint16_t event) {
  inc_func_call_count(__func__);
  return test::mock::btif_util::dump_dm_search_event(event);
}
const char* dump_hd_event(uint16_t event) {
  inc_func_call_count(__func__);
  return test::mock::btif_util::dump_hd_event(event);
}
const char* dump_hf_client_event(uint16_t event) {
  inc_func_call_count(__func__);
  return test::mock::btif_util::dump_hf_client_event(event);
}
const char* dump_hf_event(uint16_t event) {
  inc_func_call_count(__func__);
  return test::mock::btif_util::dump_hf_event(event);
}
const char* dump_hh_event(uint16_t event) {
  inc_func_call_count(__func__);
  return test::mock::btif_util::dump_hh_event(event);
}
const char* dump_property_type(bt_property_type_t type) {
  inc_func_call_count(__func__);
  return test::mock::btif_util::dump_property_type(type);
}
const char* dump_rc_event(uint8_t event) {
  inc_func_call_count(__func__);
  return test::mock::btif_util::dump_rc_event(event);
}
const char* dump_rc_notification_event_id(uint8_t event_id) {
  inc_func_call_count(__func__);
  return test::mock::btif_util::dump_rc_notification_event_id(event_id);
}
const char* dump_rc_pdu(uint8_t pdu) {
  inc_func_call_count(__func__);
  return test::mock::btif_util::dump_rc_pdu(pdu);
}
const char* dump_thread_evt(bt_cb_thread_evt evt) {
  inc_func_call_count(__func__);
  return test::mock::btif_util::dump_thread_evt(evt);
}
DEV_CLASS uint2devclass(uint32_t cod) {
  inc_func_call_count(__func__);
  return test::mock::btif_util::uint2devclass(cod);
}
// Mocked functions complete
// END mockcify generation
