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
 *   Functions generated:43
 *
 *  mockcify.pl ver 0.6.0
 */

#include <cstdint>
#include <functional>

// Original included files, if any

#include "bta/include/bta_av_api.h"
#include "include/hardware/bt_av.h"
#include "types/raw_address.h"

// Original usings
typedef enum {
  /* Reuse BTA_AV_XXX_EVT - No need to redefine them here */
  BTIF_AV_CONNECT_REQ_EVT = BTA_AV_MAX_EVT,
  BTIF_AV_DISCONNECT_REQ_EVT,
  BTIF_AV_START_STREAM_REQ_EVT,
  BTIF_AV_STOP_STREAM_REQ_EVT,
  BTIF_AV_SUSPEND_STREAM_REQ_EVT,
  BTIF_AV_SINK_CONFIG_REQ_EVT,
  BTIF_AV_ACL_DISCONNECTED,
  BTIF_AV_OFFLOAD_START_REQ_EVT,
  BTIF_AV_AVRCP_OPEN_EVT,
  BTIF_AV_AVRCP_CLOSE_EVT,
  BTIF_AV_AVRCP_REMOTE_PLAY_EVT,
  BTIF_AV_SET_LATENCY_REQ_EVT,
} btif_av_sm_event_t;

// Mocked compile conditionals, if any

namespace test {
namespace mock {
namespace btif_av {

// Shared state between mocked functions and tests
// Name: btif_av_acl_disconnected
// Params: const RawAddress& peer_address
// Return: void
struct btif_av_acl_disconnected {
  std::function<void(const RawAddress& peer_address)> body{
      [](const RawAddress& /* peer_address */) {}};
  void operator()(const RawAddress& peer_address) { body(peer_address); };
};
extern struct btif_av_acl_disconnected btif_av_acl_disconnected;

// Name: btif_av_clear_remote_suspend_flag
// Params: void
// Return: void
struct btif_av_clear_remote_suspend_flag {
  std::function<void(void)> body{[](void) {}};
  void operator()(void) { body(); };
};
extern struct btif_av_clear_remote_suspend_flag
    btif_av_clear_remote_suspend_flag;

// Name: btif_av_find_by_handle
// Params: tBTA_AV_HNDL bta_handle
// Return: const RawAddress&
struct btif_av_find_by_handle {
  static const RawAddress& return_value;
  std::function<const RawAddress&(tBTA_AV_HNDL bta_handle)> body{
      [](tBTA_AV_HNDL /* bta_handle */) { return return_value; }};
  const RawAddress& operator()(tBTA_AV_HNDL bta_handle) {
    return body(bta_handle);
  };
};
extern struct btif_av_find_by_handle btif_av_find_by_handle;

// Name: btif_av_get_audio_delay
// Params:
// Return: uint16_t
struct btif_av_get_audio_delay {
  static uint16_t return_value;
  std::function<uint16_t()> body{[]() { return return_value; }};
  uint16_t operator()() { return body(); };
};
extern struct btif_av_get_audio_delay btif_av_get_audio_delay;

// Name: btif_av_get_peer_sep
// Params: void
// Return: uint8_t
struct btif_av_get_peer_sep {
  static uint8_t return_value;
  std::function<uint8_t(void)> body{[](void) { return return_value; }};
  uint8_t operator()(void) { return body(); };
};
extern struct btif_av_get_peer_sep btif_av_get_peer_sep;

// Name: btif_av_get_sink_interface
// Params: void
// Return: const btav_sink_interface_t*
struct btif_av_get_sink_interface {
  static const btav_sink_interface_t* return_value;
  std::function<const btav_sink_interface_t*(void)> body{
      [](void) { return return_value; }};
  const btav_sink_interface_t* operator()(void) { return body(); };
};
extern struct btif_av_get_sink_interface btif_av_get_sink_interface;

// Name: btif_av_get_src_interface
// Params: void
// Return: const btav_source_interface_t*
struct btif_av_get_src_interface {
  static const btav_source_interface_t* return_value;
  std::function<const btav_source_interface_t*(void)> body{
      [](void) { return return_value; }};
  const btav_source_interface_t* operator()(void) { return body(); };
};
extern struct btif_av_get_src_interface btif_av_get_src_interface;

// Name: btif_av_is_a2dp_offload_enabled
// Params:
// Return: bool
struct btif_av_is_a2dp_offload_enabled {
  static bool return_value;
  std::function<bool()> body{[]() { return return_value; }};
  bool operator()() { return body(); };
};
extern struct btif_av_is_a2dp_offload_enabled btif_av_is_a2dp_offload_enabled;

// Name: btif_av_is_a2dp_offload_running
// Params:
// Return: bool
struct btif_av_is_a2dp_offload_running {
  static bool return_value;
  std::function<bool()> body{[]() { return return_value; }};
  bool operator()() { return body(); };
};
extern struct btif_av_is_a2dp_offload_running btif_av_is_a2dp_offload_running;

// Name: btif_av_is_connected
// Params: void
// Return: bool
struct btif_av_is_connected {
  static bool return_value;
  std::function<bool(void)> body{[](void) { return return_value; }};
  bool operator()(void) { return body(); };
};
extern struct btif_av_is_connected btif_av_is_connected;

// Name: btif_av_is_connected_addr
// Params: const RawAddress& peer_address
// Return: bool
struct btif_av_is_connected_addr {
  static bool return_value;
  std::function<bool(const RawAddress& peer_address)> body{
      [](const RawAddress& /* peer_address */) { return return_value; }};
  bool operator()(const RawAddress& peer_address) {
    return body(peer_address);
  };
};
extern struct btif_av_is_connected_addr btif_av_is_connected_addr;

// Name: btif_av_is_peer_edr
// Params: const RawAddress& peer_address
// Return: bool
struct btif_av_is_peer_edr {
  static bool return_value;
  std::function<bool(const RawAddress& peer_address)> body{
      [](const RawAddress& /* peer_address */) { return return_value; }};
  bool operator()(const RawAddress& peer_address) {
    return body(peer_address);
  };
};
extern struct btif_av_is_peer_edr btif_av_is_peer_edr;

// Name: btif_av_is_peer_silenced
// Params: const RawAddress& peer_address
// Return: bool
struct btif_av_is_peer_silenced {
  static bool return_value;
  std::function<bool(const RawAddress& peer_address)> body{
      [](const RawAddress& /* peer_address */) { return return_value; }};
  bool operator()(const RawAddress& peer_address) {
    return body(peer_address);
  };
};
extern struct btif_av_is_peer_silenced btif_av_is_peer_silenced;

// Name: btif_av_is_sink_enabled
// Params: void
// Return: bool
struct btif_av_is_sink_enabled {
  static bool return_value;
  std::function<bool(void)> body{[](void) { return return_value; }};
  bool operator()(void) { return body(); };
};
extern struct btif_av_is_sink_enabled btif_av_is_sink_enabled;

// Name: btif_av_is_source_enabled
// Params: void
// Return: bool
struct btif_av_is_source_enabled {
  static bool return_value;
  std::function<bool(void)> body{[](void) { return return_value; }};
  bool operator()(void) { return body(); };
};
extern struct btif_av_is_source_enabled btif_av_is_source_enabled;

// Name: btif_av_peer_is_connected_sink
// Params: const RawAddress& peer_address
// Return: bool
struct btif_av_peer_is_connected_sink {
  static bool return_value;
  std::function<bool(const RawAddress& peer_address)> body{
      [](const RawAddress& /* peer_address */) { return return_value; }};
  bool operator()(const RawAddress& peer_address) {
    return body(peer_address);
  };
};
extern struct btif_av_peer_is_connected_sink btif_av_peer_is_connected_sink;

// Name: btif_av_peer_is_connected_source
// Params: const RawAddress& peer_address
// Return: bool
struct btif_av_peer_is_connected_source {
  static bool return_value;
  std::function<bool(const RawAddress& peer_address)> body{
      [](const RawAddress& /* peer_address */) { return return_value; }};
  bool operator()(const RawAddress& peer_address) {
    return body(peer_address);
  };
};
extern struct btif_av_peer_is_connected_source btif_av_peer_is_connected_source;

// Name: btif_av_peer_is_sink
// Params: const RawAddress& peer_address
// Return: bool
struct btif_av_peer_is_sink {
  static bool return_value;
  std::function<bool(const RawAddress& peer_address)> body{
      [](const RawAddress& /* peer_address */) { return return_value; }};
  bool operator()(const RawAddress& peer_address) {
    return body(peer_address);
  };
};
extern struct btif_av_peer_is_sink btif_av_peer_is_sink;

// Name: btif_av_peer_is_source
// Params: const RawAddress& peer_address
// Return: bool
struct btif_av_peer_is_source {
  static bool return_value;
  std::function<bool(const RawAddress& peer_address)> body{
      [](const RawAddress& /* peer_address */) { return return_value; }};
  bool operator()(const RawAddress& peer_address) {
    return body(peer_address);
  };
};
extern struct btif_av_peer_is_source btif_av_peer_is_source;

// Name: btif_av_peer_prefers_mandatory_codec
// Params: const RawAddress& peer_address
// Return: bool
struct btif_av_peer_prefers_mandatory_codec {
  static bool return_value;
  std::function<bool(const RawAddress& peer_address)> body{
      [](const RawAddress& /* peer_address */) { return return_value; }};
  bool operator()(const RawAddress& peer_address) {
    return body(peer_address);
  };
};
extern struct btif_av_peer_prefers_mandatory_codec
    btif_av_peer_prefers_mandatory_codec;

// Name: btif_av_peer_supports_3mbps
// Params: const RawAddress& peer_address
// Return: bool
struct btif_av_peer_supports_3mbps {
  static bool return_value;
  std::function<bool(const RawAddress& peer_address)> body{
      [](const RawAddress& /* peer_address */) { return return_value; }};
  bool operator()(const RawAddress& peer_address) {
    return body(peer_address);
  };
};
extern struct btif_av_peer_supports_3mbps btif_av_peer_supports_3mbps;

// Name: btif_av_report_source_codec_state
// Params: const RawAddress& peer_address, const btav_a2dp_codec_config_t&
// codec_config, const std::vector<btav_a2dp_codec_config_t>&
// codecs_local_capabilities, const std::vector<btav_a2dp_codec_config_t>&
// codecs_selectable_capabilities Return: void
struct btif_av_report_source_codec_state {
  std::function<void(
      const RawAddress& peer_address,
      const btav_a2dp_codec_config_t& codec_config,
      const std::vector<btav_a2dp_codec_config_t>& codecs_local_capabilities,
      const std::vector<btav_a2dp_codec_config_t>&
          codecs_selectable_capabilities)>
      body{[](const RawAddress& /* peer_address */,
              const btav_a2dp_codec_config_t& /* codec_config */,
              const std::vector<btav_a2dp_codec_config_t>&
              /* codecs_local_capabilities */,
              const std::vector<btav_a2dp_codec_config_t>&
              /* codecs_selectable_capabilities */) {}};
  void operator()(
      const RawAddress& peer_address,
      const btav_a2dp_codec_config_t& codec_config,
      const std::vector<btav_a2dp_codec_config_t>& codecs_local_capabilities,
      const std::vector<btav_a2dp_codec_config_t>&
          codecs_selectable_capabilities) {
    body(peer_address, codec_config, codecs_local_capabilities,
         codecs_selectable_capabilities);
  };
};
extern struct btif_av_report_source_codec_state
    btif_av_report_source_codec_state;

// Name: btif_av_reset_audio_delay
// Params: void
// Return: void
struct btif_av_reset_audio_delay {
  std::function<void(void)> body{[](void) {}};
  void operator()(void) { body(); };
};
extern struct btif_av_reset_audio_delay btif_av_reset_audio_delay;

// Name: btif_av_set_audio_delay
// Params: const RawAddress& peer_address, uint16_t delay
// Return: void
struct btif_av_set_audio_delay {
  std::function<void(const RawAddress& peer_address, uint16_t delay)> body{
      [](const RawAddress& /* peer_address */, uint16_t /* delay */) {}};
  void operator()(const RawAddress& peer_address, uint16_t delay) {
    body(peer_address, delay);
  };
};
extern struct btif_av_set_audio_delay btif_av_set_audio_delay;

// Name: btif_av_set_dynamic_audio_buffer_size
// Params: uint8_t dynamic_audio_buffer_size
// Return: void
struct btif_av_set_dynamic_audio_buffer_size {
  std::function<void(uint8_t dynamic_audio_buffer_size)> body{
      [](uint8_t /* dynamic_audio_buffer_size */) {}};
  void operator()(uint8_t dynamic_audio_buffer_size) {
    body(dynamic_audio_buffer_size);
  };
};
extern struct btif_av_set_dynamic_audio_buffer_size
    btif_av_set_dynamic_audio_buffer_size;

// Name: btif_av_set_low_latency
// Params: bool is_low_latency
// Return: void
struct btif_av_set_low_latency {
  std::function<void(bool is_low_latency)> body{
      [](bool /* is_low_latency */) {}};
  void operator()(bool is_low_latency) { body(is_low_latency); };
};
extern struct btif_av_set_low_latency btif_av_set_low_latency;

// Name: btif_av_sink_active_peer
// Params: void
// Return: RawAddress
struct btif_av_sink_active_peer {
  static RawAddress return_value;
  std::function<RawAddress(void)> body{[](void) { return return_value; }};
  RawAddress operator()(void) { return body(); };
};
extern struct btif_av_sink_active_peer btif_av_sink_active_peer;

// Name: btif_av_sink_execute_service
// Params: bool enable
// Return: bt_status_t
struct btif_av_sink_execute_service {
  static bt_status_t return_value;
  std::function<bt_status_t(bool enable)> body{
      [](bool /* enable */) { return return_value; }};
  bt_status_t operator()(bool enable) { return body(enable); };
};
extern struct btif_av_sink_execute_service btif_av_sink_execute_service;

// Name: btif_av_source_active_peer
// Params: void
// Return: RawAddress
struct btif_av_source_active_peer {
  static RawAddress return_value;
  std::function<RawAddress(void)> body{[](void) { return return_value; }};
  RawAddress operator()(void) { return body(); };
};
extern struct btif_av_source_active_peer btif_av_source_active_peer;

// Name: btif_av_source_execute_service
// Params: bool enable
// Return: bt_status_t
struct btif_av_source_execute_service {
  static bt_status_t return_value;
  std::function<bt_status_t(bool enable)> body{
      [](bool /* enable */) { return return_value; }};
  bt_status_t operator()(bool enable) { return body(enable); };
};
extern struct btif_av_source_execute_service btif_av_source_execute_service;

// Name: btif_av_src_disconnect_sink
// Params: const RawAddress& peer_address
// Return: void
struct btif_av_src_disconnect_sink {
  std::function<void(const RawAddress& peer_address)> body{
      [](const RawAddress& /* peer_address */) {}};
  void operator()(const RawAddress& peer_address) { body(peer_address); };
};
extern struct btif_av_src_disconnect_sink btif_av_src_disconnect_sink;

// Name: btif_av_src_sink_coexist_enabled
// Params: void
// Return: bool
struct btif_av_src_sink_coexist_enabled {
  static bool return_value;
  std::function<bool(void)> body{[](void) { return return_value; }};
  bool operator()(void) { return body(); };
};
extern struct btif_av_src_sink_coexist_enabled btif_av_src_sink_coexist_enabled;

// Name: btif_av_stream_ready
// Params: void
// Return: bool
struct btif_av_stream_ready {
  static bool return_value;
  std::function<bool(void)> body{[](void) { return return_value; }};
  bool operator()(void) { return body(); };
};
extern struct btif_av_stream_ready btif_av_stream_ready;

// Name: btif_av_stream_start
// Params: void
// Return: void
struct btif_av_stream_start {
  std::function<void(void)> body{[](void) {}};
  void operator()(void) { body(); };
};
extern struct btif_av_stream_start btif_av_stream_start;

// Name: btif_av_stream_start_offload
// Params: void
// Return: void
struct btif_av_stream_start_offload {
  std::function<void(void)> body{[](void) {}};
  void operator()(void) { body(); };
};
extern struct btif_av_stream_start_offload btif_av_stream_start_offload;

// Name: btif_av_stream_start_with_latency
// Params: bool use_latency_mode
// Return: void
struct btif_av_stream_start_with_latency {
  std::function<void(bool use_latency_mode)> body{
      [](bool /* use_latency_mode */) {}};
  void operator()(bool use_latency_mode) { body(use_latency_mode); };
};
extern struct btif_av_stream_start_with_latency
    btif_av_stream_start_with_latency;

// Name: btif_av_stream_started_ready
// Params: void
// Return: bool
struct btif_av_stream_started_ready {
  static bool return_value;
  std::function<bool(void)> body{[](void) { return return_value; }};
  bool operator()(void) { return body(); };
};
extern struct btif_av_stream_started_ready btif_av_stream_started_ready;

// Name: btif_av_stream_stop
// Params: const RawAddress& peer_address
// Return: void
struct btif_av_stream_stop {
  std::function<void(const RawAddress& peer_address)> body{
      [](const RawAddress& /* peer_address */) {}};
  void operator()(const RawAddress& peer_address) { body(peer_address); };
};
extern struct btif_av_stream_stop btif_av_stream_stop;

// Name: btif_av_stream_suspend
// Params: void
// Return: void
struct btif_av_stream_suspend {
  std::function<void(void)> body{[](void) {}};
  void operator()(void) { body(); };
};
extern struct btif_av_stream_suspend btif_av_stream_suspend;

// Name: btif_debug_av_dump
// Params: int fd
// Return: void
struct btif_debug_av_dump {
  std::function<void(int fd)> body{[](int /* fd */) {}};
  void operator()(int fd) { body(fd); };
};
extern struct btif_debug_av_dump btif_debug_av_dump;

// Name: dump_av_sm_event_name
// Params: int event
// Return: const char*
struct dump_av_sm_event_name {
  static const char* return_value;
  std::function<const char*(int event)> body{
      [](int /* event */) { return return_value; }};
  const char* operator()(int event) { return body(event); };
};
extern struct dump_av_sm_event_name dump_av_sm_event_name;

// Name: src_do_suspend_in_main_thread
// Params: btif_av_sm_event_t event
// Return: void
struct src_do_suspend_in_main_thread {
  std::function<void(btif_av_sm_event_t event)> body{
      [](btif_av_sm_event_t /* event */) {}};
  void operator()(btif_av_sm_event_t event) { body(event); };
};
extern struct src_do_suspend_in_main_thread src_do_suspend_in_main_thread;

}  // namespace btif_av
}  // namespace mock
}  // namespace test

// END mockcify generation
