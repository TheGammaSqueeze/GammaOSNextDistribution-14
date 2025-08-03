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

/*
 * Generated mock file from original source file
 *   Functions generated:18
 *
 *  mockcify.pl ver 0.6.0
 */
#include <cstdint>
#include <functional>

// Original included files, if any
#include <cstdint>
#include <functional>

#include "include/hardware/bluetooth.h"
#include "stack/include/bt_dev_class.h"

// Original usings

// Mocked compile conditionals, if any

namespace test {
namespace mock {
namespace btif_util {

// Shared state between mocked functions and tests
// Name: ascii_2_hex
// Params: const char* p_ascii, int len, uint8_t* p_hex
// Return: int
struct ascii_2_hex {
  static int return_value;
  std::function<int(const char* p_ascii, int len, uint8_t* p_hex)> body{
      [](const char* /* p_ascii */, int /* len */, uint8_t* /* p_hex */) {
        return return_value;
      }};
  int operator()(const char* p_ascii, int len, uint8_t* p_hex) {
    return body(p_ascii, len, p_hex);
  };
};
extern struct ascii_2_hex ascii_2_hex;

// Name: devclass2uint
// Params: DEV_CLASS dev_class
// Return: uint32_t
struct devclass2uint {
  static uint32_t return_value;
  std::function<uint32_t(DEV_CLASS dev_class)> body{
      [](DEV_CLASS /* dev_class */) { return return_value; }};
  uint32_t operator()(DEV_CLASS dev_class) { return body(dev_class); };
};
extern struct devclass2uint devclass2uint;

// Name: dump_adapter_scan_mode
// Params: bt_scan_mode_t mode
// Return: const char*
struct dump_adapter_scan_mode {
  static const char* return_value;
  std::function<const char*(bt_scan_mode_t mode)> body{
      [](bt_scan_mode_t /* mode */) { return return_value; }};
  const char* operator()(bt_scan_mode_t mode) { return body(mode); };
};
extern struct dump_adapter_scan_mode dump_adapter_scan_mode;

// Name: dump_av_audio_state
// Params: uint16_t event
// Return: const char*
struct dump_av_audio_state {
  static const char* return_value;
  std::function<const char*(uint16_t event)> body{
      [](uint16_t /* event */) { return return_value; }};
  const char* operator()(uint16_t event) { return body(event); };
};
extern struct dump_av_audio_state dump_av_audio_state;

// Name: dump_av_conn_state
// Params: uint16_t event
// Return: const char*
struct dump_av_conn_state {
  static const char* return_value;
  std::function<const char*(uint16_t event)> body{
      [](uint16_t /* event */) { return return_value; }};
  const char* operator()(uint16_t event) { return body(event); };
};
extern struct dump_av_conn_state dump_av_conn_state;

// Name: dump_bt_status
// Params: bt_status_t status
// Return: const char*
struct dump_bt_status {
  static const char* return_value;
  std::function<const char*(bt_status_t status)> body{
      [](bt_status_t /* status */) { return return_value; }};
  const char* operator()(bt_status_t status) { return body(status); };
};
extern struct dump_bt_status dump_bt_status;

// Name: dump_dm_event
// Params: uint16_t event
// Return: const char*
struct dump_dm_event {
  static const char* return_value;
  std::function<const char*(uint16_t event)> body{
      [](uint16_t /* event */) { return return_value; }};
  const char* operator()(uint16_t event) { return body(event); };
};
extern struct dump_dm_event dump_dm_event;

// Name: dump_dm_search_event
// Params: uint16_t event
// Return: const char*
struct dump_dm_search_event {
  static const char* return_value;
  std::function<const char*(uint16_t event)> body{
      [](uint16_t /* event */) { return return_value; }};
  const char* operator()(uint16_t event) { return body(event); };
};
extern struct dump_dm_search_event dump_dm_search_event;

// Name: dump_hd_event
// Params: uint16_t event
// Return: const char*
struct dump_hd_event {
  static const char* return_value;
  std::function<const char*(uint16_t event)> body{
      [](uint16_t /* event */) { return return_value; }};
  const char* operator()(uint16_t event) { return body(event); };
};
extern struct dump_hd_event dump_hd_event;

// Name: dump_hf_client_event
// Params: uint16_t event
// Return: const char*
struct dump_hf_client_event {
  static const char* return_value;
  std::function<const char*(uint16_t event)> body{
      [](uint16_t /* event */) { return return_value; }};
  const char* operator()(uint16_t event) { return body(event); };
};
extern struct dump_hf_client_event dump_hf_client_event;

// Name: dump_hf_event
// Params: uint16_t event
// Return: const char*
struct dump_hf_event {
  static const char* return_value;
  std::function<const char*(uint16_t event)> body{
      [](uint16_t /* event */) { return return_value; }};
  const char* operator()(uint16_t event) { return body(event); };
};
extern struct dump_hf_event dump_hf_event;

// Name: dump_hh_event
// Params: uint16_t event
// Return: const char*
struct dump_hh_event {
  static const char* return_value;
  std::function<const char*(uint16_t event)> body{
      [](uint16_t /* event */) { return return_value; }};
  const char* operator()(uint16_t event) { return body(event); };
};
extern struct dump_hh_event dump_hh_event;

// Name: dump_property_type
// Params: bt_property_type_t type
// Return: const char*
struct dump_property_type {
  static const char* return_value;
  std::function<const char*(bt_property_type_t type)> body{
      [](bt_property_type_t /* type */) { return return_value; }};
  const char* operator()(bt_property_type_t type) { return body(type); };
};
extern struct dump_property_type dump_property_type;

// Name: dump_rc_event
// Params: uint8_t event
// Return: const char*
struct dump_rc_event {
  static const char* return_value;
  std::function<const char*(uint8_t event)> body{
      [](uint8_t /* event */) { return return_value; }};
  const char* operator()(uint8_t event) { return body(event); };
};
extern struct dump_rc_event dump_rc_event;

// Name: dump_rc_notification_event_id
// Params: uint8_t event_id
// Return: const char*
struct dump_rc_notification_event_id {
  static const char* return_value;
  std::function<const char*(uint8_t event_id)> body{
      [](uint8_t /* event_id */) { return return_value; }};
  const char* operator()(uint8_t event_id) { return body(event_id); };
};
extern struct dump_rc_notification_event_id dump_rc_notification_event_id;

// Name: dump_rc_pdu
// Params: uint8_t pdu
// Return: const char*
struct dump_rc_pdu {
  static const char* return_value;
  std::function<const char*(uint8_t pdu)> body{
      [](uint8_t /* pdu */) { return return_value; }};
  const char* operator()(uint8_t pdu) { return body(pdu); };
};
extern struct dump_rc_pdu dump_rc_pdu;

// Name: dump_thread_evt
// Params: bt_cb_thread_evt evt
// Return: const char*
struct dump_thread_evt {
  static const char* return_value;
  std::function<const char*(bt_cb_thread_evt evt)> body{
      [](bt_cb_thread_evt /* evt */) { return return_value; }};
  const char* operator()(bt_cb_thread_evt evt) { return body(evt); };
};
extern struct dump_thread_evt dump_thread_evt;

// Name: uint2devclass
// Params: uint32_t cod, DEV_CLASS dev_class
// Return: void
struct uint2devclass {
  static constexpr DEV_CLASS return_value{};
  std::function<DEV_CLASS(uint32_t cod)> body{
      [](uint32_t /* cod */) { return return_value; }};
  DEV_CLASS operator()(uint32_t cod) { return body(cod); };
};
extern struct uint2devclass uint2devclass;

}  // namespace btif_util
}  // namespace mock
}  // namespace test

// END mockcify generation
