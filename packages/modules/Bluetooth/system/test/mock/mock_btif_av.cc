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
 *   Functions generated:43
 *
 *  mockcify.pl ver 0.6.0
 */
// Mock include file to share data between tests and mock
#include "test/mock/mock_btif_av.h"

#include <cstdint>

#include "test/common/mock_functions.h"

// Original usings

// Mocked internal structures, if any

namespace test {
namespace mock {
namespace btif_av {

// Function state capture and return values, if needed
struct btif_av_acl_disconnected btif_av_acl_disconnected;
struct btif_av_clear_remote_suspend_flag btif_av_clear_remote_suspend_flag;
struct btif_av_find_by_handle btif_av_find_by_handle;
struct btif_av_get_audio_delay btif_av_get_audio_delay;
struct btif_av_get_peer_sep btif_av_get_peer_sep;
struct btif_av_get_sink_interface btif_av_get_sink_interface;
struct btif_av_get_src_interface btif_av_get_src_interface;
struct btif_av_is_a2dp_offload_enabled btif_av_is_a2dp_offload_enabled;
struct btif_av_is_a2dp_offload_running btif_av_is_a2dp_offload_running;
struct btif_av_is_connected btif_av_is_connected;
struct btif_av_is_connected_addr btif_av_is_connected_addr;
struct btif_av_is_peer_edr btif_av_is_peer_edr;
struct btif_av_is_peer_silenced btif_av_is_peer_silenced;
struct btif_av_is_sink_enabled btif_av_is_sink_enabled;
struct btif_av_is_source_enabled btif_av_is_source_enabled;
struct btif_av_peer_is_connected_sink btif_av_peer_is_connected_sink;
struct btif_av_peer_is_connected_source btif_av_peer_is_connected_source;
struct btif_av_peer_is_sink btif_av_peer_is_sink;
struct btif_av_peer_is_source btif_av_peer_is_source;
struct btif_av_peer_prefers_mandatory_codec
    btif_av_peer_prefers_mandatory_codec;
struct btif_av_peer_supports_3mbps btif_av_peer_supports_3mbps;
struct btif_av_report_source_codec_state btif_av_report_source_codec_state;
struct btif_av_reset_audio_delay btif_av_reset_audio_delay;
struct btif_av_set_audio_delay btif_av_set_audio_delay;
struct btif_av_set_dynamic_audio_buffer_size
    btif_av_set_dynamic_audio_buffer_size;
struct btif_av_set_low_latency btif_av_set_low_latency;
struct btif_av_sink_active_peer btif_av_sink_active_peer;
struct btif_av_sink_execute_service btif_av_sink_execute_service;
struct btif_av_source_active_peer btif_av_source_active_peer;
struct btif_av_source_execute_service btif_av_source_execute_service;
struct btif_av_src_disconnect_sink btif_av_src_disconnect_sink;
struct btif_av_src_sink_coexist_enabled btif_av_src_sink_coexist_enabled;
struct btif_av_stream_ready btif_av_stream_ready;
struct btif_av_stream_start btif_av_stream_start;
struct btif_av_stream_start_offload btif_av_stream_start_offload;
struct btif_av_stream_start_with_latency btif_av_stream_start_with_latency;
struct btif_av_stream_started_ready btif_av_stream_started_ready;
struct btif_av_stream_stop btif_av_stream_stop;
struct btif_av_stream_suspend btif_av_stream_suspend;
struct btif_debug_av_dump btif_debug_av_dump;
struct dump_av_sm_event_name dump_av_sm_event_name;
struct src_do_suspend_in_main_thread src_do_suspend_in_main_thread;

}  // namespace btif_av
}  // namespace mock
}  // namespace test

// Mocked function return values, if any
namespace test {
namespace mock {
namespace btif_av {

const RawAddress& btif_av_find_by_handle::return_value = RawAddress::kEmpty;
uint16_t btif_av_get_audio_delay::return_value = 0;
uint8_t btif_av_get_peer_sep::return_value = 0;
const btav_sink_interface_t* btif_av_get_sink_interface::return_value = nullptr;
const btav_source_interface_t* btif_av_get_src_interface::return_value =
    nullptr;
bool btif_av_is_a2dp_offload_enabled::return_value = false;
bool btif_av_is_a2dp_offload_running::return_value = false;
bool btif_av_is_connected::return_value = false;
bool btif_av_is_connected_addr::return_value = false;
bool btif_av_is_peer_edr::return_value = false;
bool btif_av_is_peer_silenced::return_value = false;
bool btif_av_is_sink_enabled::return_value = false;
bool btif_av_is_source_enabled::return_value = false;
bool btif_av_peer_is_connected_sink::return_value = false;
bool btif_av_peer_is_connected_source::return_value = false;
bool btif_av_peer_is_sink::return_value = false;
bool btif_av_peer_is_source::return_value = false;
bool btif_av_peer_prefers_mandatory_codec::return_value = false;
bool btif_av_peer_supports_3mbps::return_value = false;
RawAddress btif_av_sink_active_peer::return_value;
bt_status_t btif_av_sink_execute_service::return_value = BT_STATUS_SUCCESS;
RawAddress btif_av_source_active_peer::return_value;
bt_status_t btif_av_source_execute_service::return_value = BT_STATUS_SUCCESS;
bool btif_av_src_sink_coexist_enabled::return_value = false;
bool btif_av_stream_ready::return_value = false;
bool btif_av_stream_started_ready::return_value = false;
const char* dump_av_sm_event_name::return_value = nullptr;

}  // namespace btif_av
}  // namespace mock
}  // namespace test

// Mocked functions, if any
void btif_av_acl_disconnected(const RawAddress& peer_address) {
  inc_func_call_count(__func__);
  test::mock::btif_av::btif_av_acl_disconnected(peer_address);
}
void btif_av_clear_remote_suspend_flag(void) {
  inc_func_call_count(__func__);
  test::mock::btif_av::btif_av_clear_remote_suspend_flag();
}
const RawAddress& btif_av_find_by_handle(tBTA_AV_HNDL bta_handle) {
  inc_func_call_count(__func__);
  return test::mock::btif_av::btif_av_find_by_handle(bta_handle);
}
uint16_t btif_av_get_audio_delay() {
  inc_func_call_count(__func__);
  return test::mock::btif_av::btif_av_get_audio_delay();
}
uint8_t btif_av_get_peer_sep(void) {
  inc_func_call_count(__func__);
  return test::mock::btif_av::btif_av_get_peer_sep();
}
const btav_sink_interface_t* btif_av_get_sink_interface(void) {
  inc_func_call_count(__func__);
  return test::mock::btif_av::btif_av_get_sink_interface();
}
const btav_source_interface_t* btif_av_get_src_interface(void) {
  inc_func_call_count(__func__);
  return test::mock::btif_av::btif_av_get_src_interface();
}
bool btif_av_is_a2dp_offload_enabled() {
  inc_func_call_count(__func__);
  return test::mock::btif_av::btif_av_is_a2dp_offload_enabled();
}
bool btif_av_is_a2dp_offload_running() {
  inc_func_call_count(__func__);
  return test::mock::btif_av::btif_av_is_a2dp_offload_running();
}
bool btif_av_is_connected(void) {
  inc_func_call_count(__func__);
  return test::mock::btif_av::btif_av_is_connected();
}
bool btif_av_is_connected_addr(const RawAddress& peer_address) {
  inc_func_call_count(__func__);
  return test::mock::btif_av::btif_av_is_connected_addr(peer_address);
}
bool btif_av_is_peer_edr(const RawAddress& peer_address) {
  inc_func_call_count(__func__);
  return test::mock::btif_av::btif_av_is_peer_edr(peer_address);
}
bool btif_av_is_peer_silenced(const RawAddress& peer_address) {
  inc_func_call_count(__func__);
  return test::mock::btif_av::btif_av_is_peer_silenced(peer_address);
}
bool btif_av_is_sink_enabled(void) {
  inc_func_call_count(__func__);
  return test::mock::btif_av::btif_av_is_sink_enabled();
}
bool btif_av_is_source_enabled(void) {
  inc_func_call_count(__func__);
  return test::mock::btif_av::btif_av_is_source_enabled();
}
bool btif_av_peer_is_connected_sink(const RawAddress& peer_address) {
  inc_func_call_count(__func__);
  return test::mock::btif_av::btif_av_peer_is_connected_sink(peer_address);
}
bool btif_av_peer_is_connected_source(const RawAddress& peer_address) {
  inc_func_call_count(__func__);
  return test::mock::btif_av::btif_av_peer_is_connected_source(peer_address);
}
bool btif_av_peer_is_sink(const RawAddress& peer_address) {
  inc_func_call_count(__func__);
  return test::mock::btif_av::btif_av_peer_is_sink(peer_address);
}
bool btif_av_peer_is_source(const RawAddress& peer_address) {
  inc_func_call_count(__func__);
  return test::mock::btif_av::btif_av_peer_is_source(peer_address);
}
bool btif_av_peer_prefers_mandatory_codec(const RawAddress& peer_address) {
  inc_func_call_count(__func__);
  return test::mock::btif_av::btif_av_peer_prefers_mandatory_codec(
      peer_address);
}
bool btif_av_peer_supports_3mbps(const RawAddress& peer_address) {
  inc_func_call_count(__func__);
  return test::mock::btif_av::btif_av_peer_supports_3mbps(peer_address);
}
void btif_av_report_source_codec_state(
    const RawAddress& peer_address,
    const btav_a2dp_codec_config_t& codec_config,
    const std::vector<btav_a2dp_codec_config_t>& codecs_local_capabilities,
    const std::vector<btav_a2dp_codec_config_t>&
        codecs_selectable_capabilities) {
  inc_func_call_count(__func__);
  test::mock::btif_av::btif_av_report_source_codec_state(
      peer_address, codec_config, codecs_local_capabilities,
      codecs_selectable_capabilities);
}
void btif_av_reset_audio_delay(void) {
  inc_func_call_count(__func__);
  test::mock::btif_av::btif_av_reset_audio_delay();
}
void btif_av_set_audio_delay(const RawAddress& peer_address, uint16_t delay) {
  inc_func_call_count(__func__);
  test::mock::btif_av::btif_av_set_audio_delay(peer_address, delay);
}
void btif_av_set_dynamic_audio_buffer_size(uint8_t dynamic_audio_buffer_size) {
  inc_func_call_count(__func__);
  test::mock::btif_av::btif_av_set_dynamic_audio_buffer_size(
      dynamic_audio_buffer_size);
}
void btif_av_set_low_latency(bool is_low_latency) {
  inc_func_call_count(__func__);
  test::mock::btif_av::btif_av_set_low_latency(is_low_latency);
}
RawAddress btif_av_sink_active_peer(void) {
  inc_func_call_count(__func__);
  return test::mock::btif_av::btif_av_sink_active_peer();
}
bt_status_t btif_av_sink_execute_service(bool enable) {
  inc_func_call_count(__func__);
  return test::mock::btif_av::btif_av_sink_execute_service(enable);
}
RawAddress btif_av_source_active_peer(void) {
  inc_func_call_count(__func__);
  return test::mock::btif_av::btif_av_source_active_peer();
}
bt_status_t btif_av_source_execute_service(bool enable) {
  inc_func_call_count(__func__);
  return test::mock::btif_av::btif_av_source_execute_service(enable);
}
void btif_av_src_disconnect_sink(const RawAddress& peer_address) {
  inc_func_call_count(__func__);
  test::mock::btif_av::btif_av_src_disconnect_sink(peer_address);
}
bool btif_av_src_sink_coexist_enabled(void) {
  inc_func_call_count(__func__);
  return test::mock::btif_av::btif_av_src_sink_coexist_enabled();
}
bool btif_av_stream_ready(void) {
  inc_func_call_count(__func__);
  return test::mock::btif_av::btif_av_stream_ready();
}
void btif_av_stream_start(void) {
  inc_func_call_count(__func__);
  test::mock::btif_av::btif_av_stream_start();
}
void btif_av_stream_start_offload(void) {
  inc_func_call_count(__func__);
  test::mock::btif_av::btif_av_stream_start_offload();
}
void btif_av_stream_start_with_latency(bool use_latency_mode) {
  inc_func_call_count(__func__);
  test::mock::btif_av::btif_av_stream_start_with_latency(use_latency_mode);
}
bool btif_av_stream_started_ready(void) {
  inc_func_call_count(__func__);
  return test::mock::btif_av::btif_av_stream_started_ready();
}
void btif_av_stream_stop(const RawAddress& peer_address) {
  inc_func_call_count(__func__);
  test::mock::btif_av::btif_av_stream_stop(peer_address);
}
void btif_av_stream_suspend(void) {
  inc_func_call_count(__func__);
  test::mock::btif_av::btif_av_stream_suspend();
}
void btif_debug_av_dump(int fd) {
  inc_func_call_count(__func__);
  test::mock::btif_av::btif_debug_av_dump(fd);
}
const char* dump_av_sm_event_name(int event) {
  inc_func_call_count(__func__);
  return test::mock::btif_av::dump_av_sm_event_name(event);
}
void src_do_suspend_in_main_thread(btif_av_sm_event_t event) {
  inc_func_call_count(__func__);
  test::mock::btif_av::src_do_suspend_in_main_thread(event);
}
// Mocked functions complete
// END mockcify generation
