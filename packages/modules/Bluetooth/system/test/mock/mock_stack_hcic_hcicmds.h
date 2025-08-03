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

#pragma once

/*
 * Generated mock file from original source file
 *   Functions generated:75
 *
 *  mockcify.pl ver 0.3.0
 */

#include <cstdint>
#include <functional>

// Original included files, if any
#include "base/callback.h"
#include "device/include/esco_parameters.h"
#include "hcimsgs.h"
#include "stack/include/bt_octets.h"
#include "types/raw_address.h"

// Mocked compile conditionals, if any

namespace test {
namespace mock {
namespace stack_hcic_hcicmds {

// Name: btsnd_hcic_accept_conn
// Params: const RawAddress& dest, uint8_t role
// Return: void
struct btsnd_hcic_accept_conn {
  std::function<void(const RawAddress& dest, uint8_t role)> body{
      [](const RawAddress& /* dest */, uint8_t /* role */) {}};
  void operator()(const RawAddress& dest, uint8_t role) { body(dest, role); };
};
extern struct btsnd_hcic_accept_conn btsnd_hcic_accept_conn;

// Name: btsnd_hcic_accept_esco_conn
// Params: const RawAddress& bd_addr, uint32_t transmit_bandwidth, uint32_t
// receive_bandwidth, uint16_t max_latency, uint16_t content_fmt, uint8_t
// retrans_effort, uint16_t packet_types Return: void
struct btsnd_hcic_accept_esco_conn {
  std::function<void(const RawAddress& bd_addr, uint32_t transmit_bandwidth,
                     uint32_t receive_bandwidth, uint16_t max_latency,
                     uint16_t content_fmt, uint8_t retrans_effort,
                     uint16_t packet_types)>
      body{[](const RawAddress& /* bd_addr */,
              uint32_t /* transmit_bandwidth */,
              uint32_t /* receive_bandwidth */, uint16_t /* max_latency */,
              uint16_t /* content_fmt */, uint8_t /* retrans_effort */,
              uint16_t /* packet_types */) {}};
  void operator()(const RawAddress& bd_addr, uint32_t transmit_bandwidth,
                  uint32_t receive_bandwidth, uint16_t max_latency,
                  uint16_t content_fmt, uint8_t retrans_effort,
                  uint16_t packet_types) {
    body(bd_addr, transmit_bandwidth, receive_bandwidth, max_latency,
         content_fmt, retrans_effort, packet_types);
  };
};
extern struct btsnd_hcic_accept_esco_conn btsnd_hcic_accept_esco_conn;

// Name: btsnd_hcic_add_SCO_conn
// Params: uint16_t handle, uint16_t packet_types
// Return: void
struct btsnd_hcic_add_SCO_conn {
  std::function<void(uint16_t handle, uint16_t packet_types)> body{
      [](uint16_t /* handle */, uint16_t /* packet_types */) {}};
  void operator()(uint16_t handle, uint16_t packet_types) {
    body(handle, packet_types);
  };
};
extern struct btsnd_hcic_add_SCO_conn btsnd_hcic_add_SCO_conn;

// Name: btsnd_hcic_auth_request
// Params: uint16_t handle
// Return: void
struct btsnd_hcic_auth_request {
  std::function<void(uint16_t handle)> body{[](uint16_t /* handle */) {}};
  void operator()(uint16_t handle) { body(handle); };
};
extern struct btsnd_hcic_auth_request btsnd_hcic_auth_request;

// Name: btsnd_hcic_change_name
// Params: BD_NAME name
// Return: void
struct btsnd_hcic_change_name {
  std::function<void(BD_NAME name)> body{[](BD_NAME /* name */) {}};
  void operator()(BD_NAME name) { body(name); };
};
extern struct btsnd_hcic_change_name btsnd_hcic_change_name;

// Name: btsnd_hcic_create_conn_cancel
// Params: const RawAddress& dest
// Return: void
struct btsnd_hcic_create_conn_cancel {
  std::function<void(const RawAddress& dest)> body{
      [](const RawAddress& /* dest */) {}};
  void operator()(const RawAddress& dest) { body(dest); };
};
extern struct btsnd_hcic_create_conn_cancel btsnd_hcic_create_conn_cancel;

// Name: btsnd_hcic_delete_stored_key
// Params: const RawAddress& bd_addr, bool delete_all_flag
// Return: void
struct btsnd_hcic_delete_stored_key {
  std::function<void(const RawAddress& bd_addr, bool delete_all_flag)> body{
      [](const RawAddress& /* bd_addr */, bool /* delete_all_flag */) {}};
  void operator()(const RawAddress& bd_addr, bool delete_all_flag) {
    body(bd_addr, delete_all_flag);
  };
};
extern struct btsnd_hcic_delete_stored_key btsnd_hcic_delete_stored_key;

// Name: btsnd_hcic_enable_test_mode
// Params: void
// Return: void
struct btsnd_hcic_enable_test_mode {
  std::function<void(void)> body{[](void) {}};
  void operator()(void) { body(); };
};
extern struct btsnd_hcic_enable_test_mode btsnd_hcic_enable_test_mode;

// Name: btsnd_hcic_enhanced_accept_synchronous_connection
// Params: const RawAddress& bd_addr, enh_esco_params_t* p_params
// Return: void
struct btsnd_hcic_enhanced_accept_synchronous_connection {
  std::function<void(const RawAddress& bd_addr, enh_esco_params_t* p_params)>
      body{[](const RawAddress& /* bd_addr */,
              enh_esco_params_t* /* p_params */) {}};
  void operator()(const RawAddress& bd_addr, enh_esco_params_t* p_params) {
    body(bd_addr, p_params);
  };
};
extern struct btsnd_hcic_enhanced_accept_synchronous_connection
    btsnd_hcic_enhanced_accept_synchronous_connection;

// Name: btsnd_hcic_enhanced_flush
// Params: uint16_t handle, uint8_t packet_type
// Return: void
struct btsnd_hcic_enhanced_flush {
  std::function<void(uint16_t handle, uint8_t packet_type)> body{
      [](uint16_t /* handle */, uint8_t /* packet_type */) {}};
  void operator()(uint16_t handle, uint8_t packet_type) {
    body(handle, packet_type);
  };
};
extern struct btsnd_hcic_enhanced_flush btsnd_hcic_enhanced_flush;

// Name: btsnd_hcic_enhanced_set_up_synchronous_connection
// Params: uint16_t conn_handle, enh_esco_params_t* p_params
// Return: void
struct btsnd_hcic_enhanced_set_up_synchronous_connection {
  std::function<void(uint16_t conn_handle, enh_esco_params_t* p_params)> body{
      [](uint16_t /* conn_handle */, enh_esco_params_t* /* p_params */) {}};
  void operator()(uint16_t conn_handle, enh_esco_params_t* p_params) {
    body(conn_handle, p_params);
  };
};
extern struct btsnd_hcic_enhanced_set_up_synchronous_connection
    btsnd_hcic_enhanced_set_up_synchronous_connection;

// Name: btsnd_hcic_exit_park_mode
// Params: uint16_t handle
// Return: void
struct btsnd_hcic_exit_park_mode {
  std::function<void(uint16_t handle)> body{[](uint16_t /* handle */) {}};
  void operator()(uint16_t handle) { body(handle); };
};
extern struct btsnd_hcic_exit_park_mode btsnd_hcic_exit_park_mode;

// Name: btsnd_hcic_exit_sniff_mode
// Params: uint16_t handle
// Return: void
struct btsnd_hcic_exit_sniff_mode {
  std::function<void(uint16_t handle)> body{[](uint16_t /* handle */) {}};
  void operator()(uint16_t handle) { body(handle); };
};
extern struct btsnd_hcic_exit_sniff_mode btsnd_hcic_exit_sniff_mode;

// Name: btsnd_hcic_hold_mode
// Params: uint16_t handle, uint16_t max_hold_period, uint16_t min_hold_period
// Return: void
struct btsnd_hcic_hold_mode {
  std::function<void(uint16_t handle, uint16_t max_hold_period,
                     uint16_t min_hold_period)>
      body{[](uint16_t /* handle */, uint16_t /* max_hold_period */,
              uint16_t /* min_hold_period */) {}};
  void operator()(uint16_t handle, uint16_t max_hold_period,
                  uint16_t min_hold_period) {
    body(handle, max_hold_period, min_hold_period);
  };
};
extern struct btsnd_hcic_hold_mode btsnd_hcic_hold_mode;

// Name: btsnd_hcic_io_cap_req_neg_reply
// Params: const RawAddress& bd_addr, uint8_t err_code
// Return: void
struct btsnd_hcic_io_cap_req_neg_reply {
  std::function<void(const RawAddress& bd_addr, uint8_t err_code)> body{
      [](const RawAddress& /* bd_addr */, uint8_t /* err_code */) {}};
  void operator()(const RawAddress& bd_addr, uint8_t err_code) {
    body(bd_addr, err_code);
  };
};
extern struct btsnd_hcic_io_cap_req_neg_reply btsnd_hcic_io_cap_req_neg_reply;

// Name: btsnd_hcic_io_cap_req_reply
// Params: const RawAddress& bd_addr, uint8_t capability, uint8_t oob_present,
// uint8_t auth_req Return: void
struct btsnd_hcic_io_cap_req_reply {
  std::function<void(const RawAddress& bd_addr, uint8_t capability,
                     uint8_t oob_present, uint8_t auth_req)>
      body{[](const RawAddress& /* bd_addr */, uint8_t /* capability */,
              uint8_t /* oob_present */, uint8_t /* auth_req */) {}};
  void operator()(const RawAddress& bd_addr, uint8_t capability,
                  uint8_t oob_present, uint8_t auth_req) {
    body(bd_addr, capability, oob_present, auth_req);
  };
};
extern struct btsnd_hcic_io_cap_req_reply btsnd_hcic_io_cap_req_reply;

// Name: btsnd_hcic_link_key_neg_reply
// Params: const RawAddress& bd_addr
// Return: void
struct btsnd_hcic_link_key_neg_reply {
  std::function<void(const RawAddress& bd_addr)> body{
      [](const RawAddress& /* bd_addr */) {}};
  void operator()(const RawAddress& bd_addr) { body(bd_addr); };
};
extern struct btsnd_hcic_link_key_neg_reply btsnd_hcic_link_key_neg_reply;

// Name: btsnd_hcic_link_key_req_reply
// Params: const RawAddress& bd_addr, const LinkKey& link_key
// Return: void
struct btsnd_hcic_link_key_req_reply {
  std::function<void(const RawAddress& bd_addr, const LinkKey& link_key)> body{
      [](const RawAddress& /* bd_addr */, const LinkKey& /* link_key */) {}};
  void operator()(const RawAddress& bd_addr, const LinkKey& link_key) {
    body(bd_addr, link_key);
  };
};
extern struct btsnd_hcic_link_key_req_reply btsnd_hcic_link_key_req_reply;

// Name: btsnd_hcic_park_mode
// Params: uint16_t handle, uint16_t beacon_max_interval, uint16_t
// beacon_min_interval Return: void
struct btsnd_hcic_park_mode {
  std::function<void(uint16_t handle, uint16_t beacon_max_interval,
                     uint16_t beacon_min_interval)>
      body{[](uint16_t /* handle */, uint16_t /* beacon_max_interval */,
              uint16_t /* beacon_min_interval */) {}};
  void operator()(uint16_t handle, uint16_t beacon_max_interval,
                  uint16_t beacon_min_interval) {
    body(handle, beacon_max_interval, beacon_min_interval);
  };
};
extern struct btsnd_hcic_park_mode btsnd_hcic_park_mode;

// Name: btsnd_hcic_pin_code_neg_reply
// Params: const RawAddress& bd_addr
// Return: void
struct btsnd_hcic_pin_code_neg_reply {
  std::function<void(const RawAddress& bd_addr)> body{
      [](const RawAddress& /* bd_addr */) {}};
  void operator()(const RawAddress& bd_addr) { body(bd_addr); };
};
extern struct btsnd_hcic_pin_code_neg_reply btsnd_hcic_pin_code_neg_reply;

// Name: btsnd_hcic_pin_code_req_reply
// Params: const RawAddress& bd_addr, uint8_t pin_code_len, PIN_CODE pin_code
// Return: void
struct btsnd_hcic_pin_code_req_reply {
  std::function<void(const RawAddress& bd_addr, uint8_t pin_code_len,
                     PIN_CODE pin_code)>
      body{[](const RawAddress& /* bd_addr */, uint8_t /* pin_code_len */,
              PIN_CODE /* pin_code */) {}};
  void operator()(const RawAddress& bd_addr, uint8_t pin_code_len,
                  PIN_CODE pin_code) {
    body(bd_addr, pin_code_len, pin_code);
  };
};
extern struct btsnd_hcic_pin_code_req_reply btsnd_hcic_pin_code_req_reply;

// Name: btsnd_hcic_read_encryption_key_size
// Params: uint16_t handle, ReadEncKeySizeCb cb
// Return: void
struct btsnd_hcic_read_encryption_key_size {
  std::function<void(uint16_t handle, ReadEncKeySizeCb cb)> body{
      [](uint16_t /* handle */, ReadEncKeySizeCb /* cb */) {}};
  void operator()(uint16_t handle, ReadEncKeySizeCb cb) {
    body(handle, std::move(cb));
  };
};
extern struct btsnd_hcic_read_encryption_key_size
    btsnd_hcic_read_encryption_key_size;

// Name: btsnd_hcic_read_failed_contact_counter
// Params: uint16_t handle
// Return: void
struct btsnd_hcic_read_failed_contact_counter {
  std::function<void(uint16_t handle)> body{[](uint16_t /* handle */) {}};
  void operator()(uint16_t handle) { body(handle); };
};
extern struct btsnd_hcic_read_failed_contact_counter
    btsnd_hcic_read_failed_contact_counter;

// Name: btsnd_hcic_read_local_oob_data
// Params: void
// Return: void
struct btsnd_hcic_read_local_oob_data {
  std::function<void(void)> body{[](void) {}};
  void operator()(void) { body(); };
};
extern struct btsnd_hcic_read_local_oob_data btsnd_hcic_read_local_oob_data;

// Name: btsnd_hcic_read_name
// Params: void
// Return: void
struct btsnd_hcic_read_name {
  std::function<void(void)> body{[](void) {}};
  void operator()(void) { body(); };
};
extern struct btsnd_hcic_read_name btsnd_hcic_read_name;

// Name: btsnd_hcic_read_rmt_clk_offset
// Params: uint16_t handle
// Return: void
struct btsnd_hcic_read_rmt_clk_offset {
  std::function<void(uint16_t handle)> body{[](uint16_t /* handle */) {}};
  void operator()(uint16_t handle) { body(handle); };
};
extern struct btsnd_hcic_read_rmt_clk_offset btsnd_hcic_read_rmt_clk_offset;

// Name: btsnd_hcic_read_rssi
// Params: uint16_t handle
// Return: void
struct btsnd_hcic_read_rssi {
  std::function<void(uint16_t handle)> body{[](uint16_t /* handle */) {}};
  void operator()(uint16_t handle) { body(handle); };
};
extern struct btsnd_hcic_read_rssi btsnd_hcic_read_rssi;

// Name: btsnd_hcic_read_tx_power
// Params: uint16_t handle, uint8_t type
// Return: void
struct btsnd_hcic_read_tx_power {
  std::function<void(uint16_t handle, uint8_t type)> body{
      [](uint16_t /* handle */, uint8_t /* type */) {}};
  void operator()(uint16_t handle, uint8_t type) { body(handle, type); };
};
extern struct btsnd_hcic_read_tx_power btsnd_hcic_read_tx_power;

// Name: btsnd_hcic_reject_conn
// Params: const RawAddress& dest, uint8_t reason
// Return: void
struct btsnd_hcic_reject_conn {
  std::function<void(const RawAddress& dest, uint8_t reason)> body{
      [](const RawAddress& /* dest */, uint8_t /* reason */) {}};
  void operator()(const RawAddress& dest, uint8_t reason) {
    body(dest, reason);
  };
};
extern struct btsnd_hcic_reject_conn btsnd_hcic_reject_conn;

// Name: btsnd_hcic_reject_esco_conn
// Params: const RawAddress& bd_addr, uint8_t reason
// Return: void
struct btsnd_hcic_reject_esco_conn {
  std::function<void(const RawAddress& bd_addr, uint8_t reason)> body{
      [](const RawAddress& /* bd_addr */, uint8_t /* reason */) {}};
  void operator()(const RawAddress& bd_addr, uint8_t reason) {
    body(bd_addr, reason);
  };
};
extern struct btsnd_hcic_reject_esco_conn btsnd_hcic_reject_esco_conn;

// Name: btsnd_hcic_rem_oob_neg_reply
// Params: const RawAddress& bd_addr
// Return: void
struct btsnd_hcic_rem_oob_neg_reply {
  std::function<void(const RawAddress& bd_addr)> body{
      [](const RawAddress& /* bd_addr */) {}};
  void operator()(const RawAddress& bd_addr) { body(bd_addr); };
};
extern struct btsnd_hcic_rem_oob_neg_reply btsnd_hcic_rem_oob_neg_reply;

// Name: btsnd_hcic_rem_oob_reply
// Params: const RawAddress& bd_addr, const Octet16& c, const Octet16& r
// Return: void
struct btsnd_hcic_rem_oob_reply {
  std::function<void(const RawAddress& bd_addr, const Octet16& c,
                     const Octet16& r)>
      body{[](const RawAddress& /* bd_addr */, const Octet16& /* c */,
              const Octet16& /* r */) {}};
  void operator()(const RawAddress& bd_addr, const Octet16& c,
                  const Octet16& r) {
    body(bd_addr, c, r);
  };
};
extern struct btsnd_hcic_rem_oob_reply btsnd_hcic_rem_oob_reply;

// Name: btsnd_hcic_rmt_ext_features
// Params: uint16_t handle, uint8_t page_num
// Return: void
struct btsnd_hcic_rmt_ext_features {
  std::function<void(uint16_t handle, uint8_t page_num)> body{
      [](uint16_t /* handle */, uint8_t /* page_num */) {}};
  void operator()(uint16_t handle, uint8_t page_num) {
    body(handle, page_num);
  };
};
extern struct btsnd_hcic_rmt_ext_features btsnd_hcic_rmt_ext_features;

// Name: btsnd_hcic_rmt_name_req
// Params: const RawAddress& bd_addr, uint8_t page_scan_rep_mode, uint8_t
// page_scan_mode, uint16_t clock_offset Return: void
struct btsnd_hcic_rmt_name_req {
  std::function<void(const RawAddress& bd_addr, uint8_t page_scan_rep_mode,
                     uint8_t page_scan_mode, uint16_t clock_offset)>
      body{[](const RawAddress& /* bd_addr */, uint8_t /* page_scan_rep_mode */,
              uint8_t /* page_scan_mode */, uint16_t /* clock_offset */) {}};
  void operator()(const RawAddress& bd_addr, uint8_t page_scan_rep_mode,
                  uint8_t page_scan_mode, uint16_t clock_offset) {
    body(bd_addr, page_scan_rep_mode, page_scan_mode, clock_offset);
  };
};
extern struct btsnd_hcic_rmt_name_req btsnd_hcic_rmt_name_req;

// Name: btsnd_hcic_rmt_name_req_cancel
// Params: const RawAddress& bd_addr
// Return: void
struct btsnd_hcic_rmt_name_req_cancel {
  std::function<void(const RawAddress& bd_addr)> body{
      [](const RawAddress& /* bd_addr */) {}};
  void operator()(const RawAddress& bd_addr) { body(bd_addr); };
};
extern struct btsnd_hcic_rmt_name_req_cancel btsnd_hcic_rmt_name_req_cancel;

// Name: btsnd_hcic_rmt_ver_req
// Params: uint16_t handle
// Return: void
struct btsnd_hcic_rmt_ver_req {
  std::function<void(uint16_t handle)> body{[](uint16_t /* handle */) {}};
  void operator()(uint16_t handle) { body(handle); };
};
extern struct btsnd_hcic_rmt_ver_req btsnd_hcic_rmt_ver_req;

// Name: btsnd_hcic_set_conn_encrypt
// Params: uint16_t handle, bool enable
// Return: void
struct btsnd_hcic_set_conn_encrypt {
  std::function<void(uint16_t handle, bool enable)> body{
      [](uint16_t /* handle */, bool /* enable */) {}};
  void operator()(uint16_t handle, bool enable) { body(handle, enable); };
};
extern struct btsnd_hcic_set_conn_encrypt btsnd_hcic_set_conn_encrypt;

// Name: btsnd_hcic_set_event_filter
// Params: uint8_t filt_type, uint8_t filt_cond_type, uint8_t* filt_cond,
// uint8_t filt_cond_len Return: void
struct btsnd_hcic_set_event_filter {
  std::function<void(uint8_t filt_type, uint8_t filt_cond_type,
                     uint8_t* filt_cond, uint8_t filt_cond_len)>
      body{[](uint8_t /* filt_type */, uint8_t /* filt_cond_type */,
              uint8_t* /* filt_cond */, uint8_t /* filt_cond_len */) {}};
  void operator()(uint8_t filt_type, uint8_t filt_cond_type, uint8_t* filt_cond,
                  uint8_t filt_cond_len) {
    body(filt_type, filt_cond_type, filt_cond, filt_cond_len);
  };
};
extern struct btsnd_hcic_set_event_filter btsnd_hcic_set_event_filter;

// Name: btsnd_hcic_setup_esco_conn
// Params: uint16_t handle, uint32_t transmit_bandwidth, uint32_t
// receive_bandwidth, uint16_t max_latency, uint16_t voice, uint8_t
// retrans_effort, uint16_t packet_types Return: void
struct btsnd_hcic_setup_esco_conn {
  std::function<void(uint16_t handle, uint32_t transmit_bandwidth,
                     uint32_t receive_bandwidth, uint16_t max_latency,
                     uint16_t voice, uint8_t retrans_effort,
                     uint16_t packet_types)>
      body{[](uint16_t /* handle */, uint32_t /* transmit_bandwidth */,
              uint32_t /* receive_bandwidth */, uint16_t /* max_latency */,
              uint16_t /* voice */, uint8_t /* retrans_effort */,
              uint16_t /* packet_types */) {}};
  void operator()(uint16_t handle, uint32_t transmit_bandwidth,
                  uint32_t receive_bandwidth, uint16_t max_latency,
                  uint16_t voice, uint8_t retrans_effort,
                  uint16_t packet_types) {
    body(handle, transmit_bandwidth, receive_bandwidth, max_latency, voice,
         retrans_effort, packet_types);
  };
};
extern struct btsnd_hcic_setup_esco_conn btsnd_hcic_setup_esco_conn;

// Name: btsnd_hcic_sniff_mode
// Params: uint16_t handle, uint16_t max_sniff_period, uint16_t
// min_sniff_period, uint16_t sniff_attempt, uint16_t sniff_timeout Return: void
struct btsnd_hcic_sniff_mode {
  std::function<void(uint16_t handle, uint16_t max_sniff_period,
                     uint16_t min_sniff_period, uint16_t sniff_attempt,
                     uint16_t sniff_timeout)>
      body{[](uint16_t /* handle */, uint16_t /* max_sniff_period */,
              uint16_t /* min_sniff_period */, uint16_t /* sniff_attempt */,
              uint16_t /* sniff_timeout */) {}};
  void operator()(uint16_t handle, uint16_t max_sniff_period,
                  uint16_t min_sniff_period, uint16_t sniff_attempt,
                  uint16_t sniff_timeout) {
    body(handle, max_sniff_period, min_sniff_period, sniff_attempt,
         sniff_timeout);
  };
};
extern struct btsnd_hcic_sniff_mode btsnd_hcic_sniff_mode;

// Name: btsnd_hcic_sniff_sub_rate
// Params: uint16_t handle, uint16_t max_lat, uint16_t min_remote_lat, uint16_t
// min_local_lat Return: void
struct btsnd_hcic_sniff_sub_rate {
  std::function<void(uint16_t handle, uint16_t max_lat, uint16_t min_remote_lat,
                     uint16_t min_local_lat)>
      body{[](uint16_t /* handle */, uint16_t /* max_lat */,
              uint16_t /* min_remote_lat */, uint16_t /* min_local_lat */) {}};
  void operator()(uint16_t handle, uint16_t max_lat, uint16_t min_remote_lat,
                  uint16_t min_local_lat) {
    body(handle, max_lat, min_remote_lat, min_local_lat);
  };
};
extern struct btsnd_hcic_sniff_sub_rate btsnd_hcic_sniff_sub_rate;

// Name: btsnd_hcic_user_conf_reply
// Params: const RawAddress& bd_addr, bool is_yes
// Return: void
struct btsnd_hcic_user_conf_reply {
  std::function<void(const RawAddress& bd_addr, bool is_yes)> body{
      [](const RawAddress& /* bd_addr */, bool /* is_yes */) {}};
  void operator()(const RawAddress& bd_addr, bool is_yes) {
    body(bd_addr, is_yes);
  };
};
extern struct btsnd_hcic_user_conf_reply btsnd_hcic_user_conf_reply;

// Name: btsnd_hcic_user_passkey_neg_reply
// Params: const RawAddress& bd_addr
// Return: void
struct btsnd_hcic_user_passkey_neg_reply {
  std::function<void(const RawAddress& bd_addr)> body{
      [](const RawAddress& /* bd_addr */) {}};
  void operator()(const RawAddress& bd_addr) { body(bd_addr); };
};
extern struct btsnd_hcic_user_passkey_neg_reply
    btsnd_hcic_user_passkey_neg_reply;

// Name: btsnd_hcic_user_passkey_reply
// Params: const RawAddress& bd_addr, uint32_t value
// Return: void
struct btsnd_hcic_user_passkey_reply {
  std::function<void(const RawAddress& bd_addr, uint32_t value)> body{
      [](const RawAddress& /* bd_addr */, uint32_t /* value */) {}};
  void operator()(const RawAddress& bd_addr, uint32_t value) {
    body(bd_addr, value);
  };
};
extern struct btsnd_hcic_user_passkey_reply btsnd_hcic_user_passkey_reply;

// Name: btsnd_hcic_vendor_spec_cmd
// Params: uint16_t opcode, uint8_t len, uint8_t* p_data, void*
// p_cmd_cplt_cback Return: void
struct btsnd_hcic_vendor_spec_cmd {
  std::function<void(uint16_t opcode, uint8_t len, uint8_t* p_data,
                     tBTM_VSC_CMPL_CB* p_cmd_cplt_cback)>
      body{[](uint16_t /* opcode */, uint8_t /* len */, uint8_t* /* p_data */,
              tBTM_VSC_CMPL_CB* /* p_cmd_cplt_cback */) {}};
  void operator()(uint16_t opcode, uint8_t len, uint8_t* p_data,
                  tBTM_VSC_CMPL_CB* p_cmd_cplt_cback) {
    body(opcode, len, p_data, p_cmd_cplt_cback);
  };
};
extern struct btsnd_hcic_vendor_spec_cmd btsnd_hcic_vendor_spec_cmd;

// Name: btsnd_hcic_write_auth_enable
// Params: uint8_t flag
// Return: void
struct btsnd_hcic_write_auth_enable {
  std::function<void(uint8_t flag)> body{[](uint8_t /* flag */) {}};
  void operator()(uint8_t flag) { body(flag); };
};
extern struct btsnd_hcic_write_auth_enable btsnd_hcic_write_auth_enable;

// Name: btsnd_hcic_write_auto_flush_tout
// Params: uint16_t handle, uint16_t tout
// Return: void
struct btsnd_hcic_write_auto_flush_tout {
  std::function<void(uint16_t handle, uint16_t tout)> body{
      [](uint16_t /* handle */, uint16_t /* tout */) {}};
  void operator()(uint16_t handle, uint16_t tout) { body(handle, tout); };
};
extern struct btsnd_hcic_write_auto_flush_tout btsnd_hcic_write_auto_flush_tout;

// Name: btsnd_hcic_write_cur_iac_lap
// Params: uint8_t num_cur_iac, LAP* const iac_lap
// Return: void
struct btsnd_hcic_write_cur_iac_lap {
  std::function<void(uint8_t num_cur_iac, LAP* const iac_lap)> body{
      [](uint8_t /* num_cur_iac */, LAP* const /* iac_lap */) {}};
  void operator()(uint8_t num_cur_iac, LAP* const iac_lap) {
    body(num_cur_iac, iac_lap);
  };
};
extern struct btsnd_hcic_write_cur_iac_lap btsnd_hcic_write_cur_iac_lap;

// Name: btsnd_hcic_write_def_policy_set
// Params: uint16_t settings
// Return: void
struct btsnd_hcic_write_def_policy_set {
  std::function<void(uint16_t settings)> body{[](uint16_t /* settings */) {}};
  void operator()(uint16_t settings) { body(settings); };
};
extern struct btsnd_hcic_write_def_policy_set btsnd_hcic_write_def_policy_set;

// Name: btsnd_hcic_write_dev_class
// Params: DEV_CLASS dev_class
// Return: void
struct btsnd_hcic_write_dev_class {
  std::function<void(DEV_CLASS dev_class)> body{
      [](DEV_CLASS /* dev_class */) {}};
  void operator()(DEV_CLASS dev_class) { body(dev_class); };
};
extern struct btsnd_hcic_write_dev_class btsnd_hcic_write_dev_class;

// Name: btsnd_hcic_write_ext_inquiry_response
// Params: void* buffer, uint8_t fec_req
// Return: void
struct btsnd_hcic_write_ext_inquiry_response {
  std::function<void(void* buffer, uint8_t fec_req)> body{
      [](void* /* buffer */, uint8_t /* fec_req */) {}};
  void operator()(void* buffer, uint8_t fec_req) { body(buffer, fec_req); };
};
extern struct btsnd_hcic_write_ext_inquiry_response
    btsnd_hcic_write_ext_inquiry_response;

// Name: btsnd_hcic_write_inqscan_cfg
// Params: uint16_t interval, uint16_t window
// Return: void
struct btsnd_hcic_write_inqscan_cfg {
  std::function<void(uint16_t interval, uint16_t window)> body{
      [](uint16_t /* interval */, uint16_t /* window */) {}};
  void operator()(uint16_t interval, uint16_t window) {
    body(interval, window);
  };
};
extern struct btsnd_hcic_write_inqscan_cfg btsnd_hcic_write_inqscan_cfg;

// Name: btsnd_hcic_write_inqscan_type
// Params: uint8_t type
// Return: void
struct btsnd_hcic_write_inqscan_type {
  std::function<void(uint8_t type)> body{[](uint8_t /* type */) {}};
  void operator()(uint8_t type) { body(type); };
};
extern struct btsnd_hcic_write_inqscan_type btsnd_hcic_write_inqscan_type;

// Name: btsnd_hcic_write_inquiry_mode
// Params: uint8_t mode
// Return: void
struct btsnd_hcic_write_inquiry_mode {
  std::function<void(uint8_t mode)> body{[](uint8_t /* mode */) {}};
  void operator()(uint8_t mode) { body(mode); };
};
extern struct btsnd_hcic_write_inquiry_mode btsnd_hcic_write_inquiry_mode;

// Name: btsnd_hcic_write_link_super_tout
// Params: uint16_t handle, uint16_t timeout
// Return: void
struct btsnd_hcic_write_link_super_tout {
  std::function<void(uint16_t handle, uint16_t timeout)> body{
      [](uint16_t /* handle */, uint16_t /* timeout */) {}};
  void operator()(uint16_t handle, uint16_t timeout) { body(handle, timeout); };
};
extern struct btsnd_hcic_write_link_super_tout btsnd_hcic_write_link_super_tout;

// Name: btsnd_hcic_write_page_tout
// Params: uint16_t timeout
// Return: void
struct btsnd_hcic_write_page_tout {
  std::function<void(uint16_t timeout)> body{[](uint16_t /* timeout */) {}};
  void operator()(uint16_t timeout) { body(timeout); };
};
extern struct btsnd_hcic_write_page_tout btsnd_hcic_write_page_tout;

// Name: btsnd_hcic_write_pagescan_cfg
// Params: uint16_t interval, uint16_t window
// Return: void
struct btsnd_hcic_write_pagescan_cfg {
  std::function<void(uint16_t interval, uint16_t window)> body{
      [](uint16_t /* interval */, uint16_t /* window */) {}};
  void operator()(uint16_t interval, uint16_t window) {
    body(interval, window);
  };
};
extern struct btsnd_hcic_write_pagescan_cfg btsnd_hcic_write_pagescan_cfg;

// Name: btsnd_hcic_write_pagescan_type
// Params: uint8_t type
// Return: void
struct btsnd_hcic_write_pagescan_type {
  std::function<void(uint8_t type)> body{[](uint8_t /* type */) {}};
  void operator()(uint8_t type) { body(type); };
};
extern struct btsnd_hcic_write_pagescan_type btsnd_hcic_write_pagescan_type;

// Name: btsnd_hcic_write_pin_type
// Params: uint8_t type
// Return: void
struct btsnd_hcic_write_pin_type {
  std::function<void(uint8_t type)> body{[](uint8_t /* type */) {}};
  void operator()(uint8_t type) { body(type); };
};
extern struct btsnd_hcic_write_pin_type btsnd_hcic_write_pin_type;

// Name: btsnd_hcic_write_policy_set
// Params: uint16_t handle, uint16_t settings
// Return: void
struct btsnd_hcic_write_policy_set {
  std::function<void(uint16_t handle, uint16_t settings)> body{
      [](uint16_t /* handle */, uint16_t /* settings */) {}};
  void operator()(uint16_t handle, uint16_t settings) {
    body(handle, settings);
  };
};
extern struct btsnd_hcic_write_policy_set btsnd_hcic_write_policy_set;

// Name: btsnd_hcic_write_scan_enable
// Params: uint8_t flag
// Return: void
struct btsnd_hcic_write_scan_enable {
  std::function<void(uint8_t flag)> body{[](uint8_t /* flag */) {}};
  void operator()(uint8_t flag) { body(flag); };
};
extern struct btsnd_hcic_write_scan_enable btsnd_hcic_write_scan_enable;

// Name: btsnd_hcic_write_voice_settings
// Params: uint16_t flags
// Return: void
struct btsnd_hcic_write_voice_settings {
  std::function<void(uint16_t flags)> body{[](uint16_t /* flags */) {}};
  void operator()(uint16_t flags) { body(flags); };
};
extern struct btsnd_hcic_write_voice_settings btsnd_hcic_write_voice_settings;

}  // namespace stack_hcic_hcicmds
}  // namespace mock
}  // namespace test

// END mockcify generation
