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
 *   Functions generated:21
 *
 *  mockcify.pl ver 0.7.0
 */

// Mock include file to share data between tests and mock
#include "test/mock/mock_audio_hal_interface_a2dp_encoding.h"

#include <cstdint>
#include <optional>

#include "test/common/mock_functions.h"

// Original usings

// Mocked internal structures, if any

namespace test {
namespace mock {
namespace audio_hal_interface_a2dp_encoding {

// Function state capture and return values, if needed
struct ack_stream_started ack_stream_started;
struct ack_stream_suspended ack_stream_suspended;
struct cleanup cleanup;
struct codec_index_str codec_index_str;
struct codec_info codec_info;
struct end_session end_session;
struct get_a2dp_configuration get_a2dp_configuration;
struct init init;
struct is_hal_enabled is_hal_enabled;
struct is_hal_offloading is_hal_offloading;
struct is_opus_supported is_opus_supported;
struct parse_a2dp_configuration parse_a2dp_configuration;
struct read read;
struct set_audio_low_latency_mode_allowed set_audio_low_latency_mode_allowed;
struct set_remote_delay set_remote_delay;
struct setup_codec setup_codec;
struct sink_codec_index sink_codec_index;
struct source_codec_index source_codec_index;
struct start_session start_session;
struct supports_codec supports_codec;
struct update_codec_offloading_capabilities
    update_codec_offloading_capabilities;

}  // namespace audio_hal_interface_a2dp_encoding
}  // namespace mock
}  // namespace test

// Mocked function return values, if any
namespace test {
namespace mock {
namespace audio_hal_interface_a2dp_encoding {

std::optional<const char*> codec_index_str::return_value = std::nullopt;
bool codec_info::return_value = false;
std::optional<a2dp_configuration> get_a2dp_configuration::return_value =
    std::nullopt;
bool init::return_value = false;
bool is_hal_enabled::return_value = false;
bool is_hal_offloading::return_value = false;
bool is_opus_supported::return_value = false;
tA2DP_STATUS parse_a2dp_configuration::return_value = 0;
size_t read::return_value = 0;
bool setup_codec::return_value = false;
std::optional<btav_a2dp_codec_index_t> sink_codec_index::return_value =
    std::nullopt;
std::optional<btav_a2dp_codec_index_t> source_codec_index::return_value =
    std::nullopt;
bool supports_codec::return_value = false;
bool update_codec_offloading_capabilities::return_value = false;

}  // namespace audio_hal_interface_a2dp_encoding
}  // namespace mock
}  // namespace test

// Mocked functions, if any
void ack_stream_started(const tA2DP_CTRL_ACK& status) {
  inc_func_call_count(__func__);
  test::mock::audio_hal_interface_a2dp_encoding::ack_stream_started(status);
}
void ack_stream_suspended(const tA2DP_CTRL_ACK& status) {
  inc_func_call_count(__func__);
  test::mock::audio_hal_interface_a2dp_encoding::ack_stream_suspended(status);
}
void cleanup() {
  inc_func_call_count(__func__);
  test::mock::audio_hal_interface_a2dp_encoding::cleanup();
}
std::optional<const char*> bluetooth::audio::a2dp::provider::codec_index_str(
    btav_a2dp_codec_index_t codec_index) {
  inc_func_call_count(__func__);
  return test::mock::audio_hal_interface_a2dp_encoding::codec_index_str(
      codec_index);
}
bool bluetooth::audio::a2dp::provider::codec_info(
    btav_a2dp_codec_index_t codec_index, uint64_t* codec_id,
    uint8_t* codec_info, btav_a2dp_codec_config_t* codec_config) {
  inc_func_call_count(__func__);
  return test::mock::audio_hal_interface_a2dp_encoding::codec_info(
      codec_index, codec_id, codec_info, codec_config);
}
void end_session() {
  inc_func_call_count(__func__);
  test::mock::audio_hal_interface_a2dp_encoding::end_session();
}
std::optional<a2dp_configuration>
bluetooth::audio::a2dp::provider::get_a2dp_configuration(
    RawAddress peer_address,
    std::vector<a2dp_remote_capabilities> const& remote_seps,
    btav_a2dp_codec_config_t const& user_preferences) {
  inc_func_call_count(__func__);
  return test::mock::audio_hal_interface_a2dp_encoding::get_a2dp_configuration(
      peer_address, remote_seps, user_preferences);
}
bool init(bluetooth::common::MessageLoopThread* message_loop) {
  inc_func_call_count(__func__);
  return test::mock::audio_hal_interface_a2dp_encoding::init(message_loop);
}
bool is_hal_enabled() {
  inc_func_call_count(__func__);
  return test::mock::audio_hal_interface_a2dp_encoding::is_hal_enabled();
}
bool is_hal_offloading() {
  inc_func_call_count(__func__);
  return test::mock::audio_hal_interface_a2dp_encoding::is_hal_offloading();
}
bool is_opus_supported() {
  inc_func_call_count(__func__);
  return test::mock::audio_hal_interface_a2dp_encoding::is_opus_supported();
}
tA2DP_STATUS bluetooth::audio::a2dp::provider::parse_a2dp_configuration(
    btav_a2dp_codec_index_t codec_index, const uint8_t* codec_info,
    btav_a2dp_codec_config_t* codec_parameters,
    std::vector<uint8_t>* vendor_specific_parameters) {
  inc_func_call_count(__func__);
  return test::mock::audio_hal_interface_a2dp_encoding::
      parse_a2dp_configuration(codec_index, codec_info, codec_parameters,
                               vendor_specific_parameters);
}
size_t read(uint8_t* p_buf, uint32_t len) {
  inc_func_call_count(__func__);
  return test::mock::audio_hal_interface_a2dp_encoding::read(p_buf, len);
}
void set_audio_low_latency_mode_allowed(bool allowed) {
  inc_func_call_count(__func__);
  test::mock::audio_hal_interface_a2dp_encoding::
      set_audio_low_latency_mode_allowed(allowed);
}
void set_remote_delay(uint16_t delay_report) {
  inc_func_call_count(__func__);
  test::mock::audio_hal_interface_a2dp_encoding::set_remote_delay(delay_report);
}
bool setup_codec() {
  inc_func_call_count(__func__);
  return test::mock::audio_hal_interface_a2dp_encoding::setup_codec();
}
std::optional<btav_a2dp_codec_index_t>
bluetooth::audio::a2dp::provider::sink_codec_index(
    const uint8_t* p_codec_info) {
  inc_func_call_count(__func__);
  return test::mock::audio_hal_interface_a2dp_encoding::sink_codec_index(
      p_codec_info);
}
std::optional<btav_a2dp_codec_index_t>
bluetooth::audio::a2dp::provider::source_codec_index(
    const uint8_t* p_codec_info) {
  inc_func_call_count(__func__);
  return test::mock::audio_hal_interface_a2dp_encoding::source_codec_index(
      p_codec_info);
}
void start_session() {
  inc_func_call_count(__func__);
  test::mock::audio_hal_interface_a2dp_encoding::start_session();
}
bool bluetooth::audio::a2dp::provider::supports_codec(
    btav_a2dp_codec_index_t codec_index) {
  inc_func_call_count(__func__);
  return test::mock::audio_hal_interface_a2dp_encoding::supports_codec(
      codec_index);
}
bool update_codec_offloading_capabilities(
    const std::vector<btav_a2dp_codec_config_t>& framework_preference,
    bool supports_a2dp_hw_offload_v2) {
  inc_func_call_count(__func__);
  return test::mock::audio_hal_interface_a2dp_encoding::
      update_codec_offloading_capabilities(framework_preference,
                                           supports_a2dp_hw_offload_v2);
}
// Mocked functions complete
// END mockcify generation
