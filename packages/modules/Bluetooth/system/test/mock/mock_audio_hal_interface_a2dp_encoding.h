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
 *   Functions generated:21
 *
 *  mockcify.pl ver 0.7.0
 */

#include <cstdint>
#include <functional>

// Original included files, if any
// NOTE: Since this is a mock file with mock definitions some number of
//       include files may not be required.  The include-what-you-use
//       still applies, but crafting proper inclusion is out of scope
//       for this effort.  This compilation unit may compile as-is, or
//       may need attention to prune from (or add to ) the inclusion set.
#include "audio_hal_interface/a2dp_encoding.h"

// Original usings
using bluetooth::audio::a2dp::provider::a2dp_configuration;
using bluetooth::audio::a2dp::provider::a2dp_remote_capabilities;

// Mocked compile conditionals, if any

namespace test {
namespace mock {
namespace audio_hal_interface_a2dp_encoding {

// Shared state between mocked functions and tests
// Name: ack_stream_started
// Params: const tA2DP_CTRL_ACK& status
// Return: void
struct ack_stream_started {
  std::function<void(const tA2DP_CTRL_ACK& status)> body{
      [](const tA2DP_CTRL_ACK& /* status */) {}};
  void operator()(const tA2DP_CTRL_ACK& status) { body(status); };
};
extern struct ack_stream_started ack_stream_started;

// Name: ack_stream_suspended
// Params: const tA2DP_CTRL_ACK& status
// Return: void
struct ack_stream_suspended {
  std::function<void(const tA2DP_CTRL_ACK& status)> body{
      [](const tA2DP_CTRL_ACK& /* status */) {}};
  void operator()(const tA2DP_CTRL_ACK& status) { body(status); };
};
extern struct ack_stream_suspended ack_stream_suspended;

// Name: cleanup
// Params:
// Return: void
struct cleanup {
  std::function<void()> body{[]() {}};
  void operator()() { body(); };
};
extern struct cleanup cleanup;

// Name: codec_index_str
// Params: btav_a2dp_codec_index_t codec_index
// Return: std::optional<const char*>
struct codec_index_str {
  static std::optional<const char*> return_value;
  std::function<std::optional<const char*>(btav_a2dp_codec_index_t codec_index)>
      body{[](btav_a2dp_codec_index_t /* codec_index */) {
        return return_value;
      }};
  std::optional<const char*> operator()(btav_a2dp_codec_index_t codec_index) {
    return body(codec_index);
  };
};
extern struct codec_index_str codec_index_str;

// Name: codec_info
// Params: btav_a2dp_codec_index_t codec_index, uint64_t *codec_id, uint8_t*
// codec_info, btav_a2dp_codec_config_t* codec_config Return: bool
struct codec_info {
  static bool return_value;
  std::function<bool(btav_a2dp_codec_index_t codec_index, uint64_t* codec_id,
                     uint8_t* codec_info,
                     btav_a2dp_codec_config_t* codec_config)>
      body{[](btav_a2dp_codec_index_t /* codec_index */,
              uint64_t* /* codec_id */, uint8_t* /* codec_info */,
              btav_a2dp_codec_config_t* /* codec_config */) {
        return return_value;
      }};
  bool operator()(btav_a2dp_codec_index_t codec_index, uint64_t* codec_id,
                  uint8_t* codec_info, btav_a2dp_codec_config_t* codec_config) {
    return body(codec_index, codec_id, codec_info, codec_config);
  };
};
extern struct codec_info codec_info;

// Name: end_session
// Params:
// Return: void
struct end_session {
  std::function<void()> body{[]() {}};
  void operator()() { body(); };
};
extern struct end_session end_session;

// Name: get_a2dp_configuration
// Params: RawAddress peer_address, std::vector<a2dp_remote_capabilities> const&
// remote_seps, btav_a2dp_codec_config_t const& user_preferences Return:
// std::optional<a2dp_configuration>
struct get_a2dp_configuration {
  static std::optional<a2dp_configuration> return_value;
  std::function<std::optional<a2dp_configuration>(
      RawAddress peer_address,
      std::vector<a2dp_remote_capabilities> const& remote_seps,
      btav_a2dp_codec_config_t const& user_preferences)>
      body{[](RawAddress /* peer_address */,
              std::vector<a2dp_remote_capabilities> const& /* remote_seps */,
              btav_a2dp_codec_config_t const& /* user_preferences */) {
        return return_value;
      }};
  std::optional<a2dp_configuration> operator()(
      RawAddress peer_address,
      std::vector<a2dp_remote_capabilities> const& remote_seps,
      btav_a2dp_codec_config_t const& user_preferences) {
    return body(peer_address, remote_seps, user_preferences);
  };
};
extern struct get_a2dp_configuration get_a2dp_configuration;

// Name: init
// Params: bluetooth::common::MessageLoopThread* message_loop
// Return: bool
struct init {
  static bool return_value;
  std::function<bool(bluetooth::common::MessageLoopThread* message_loop)> body{
      [](bluetooth::common::MessageLoopThread* /* message_loop */) {
        return return_value;
      }};
  bool operator()(bluetooth::common::MessageLoopThread* message_loop) {
    return body(message_loop);
  };
};
extern struct init init;

// Name: is_hal_enabled
// Params:
// Return: bool
struct is_hal_enabled {
  static bool return_value;
  std::function<bool()> body{[]() { return return_value; }};
  bool operator()() { return body(); };
};
extern struct is_hal_enabled is_hal_enabled;

// Name: is_hal_offloading
// Params:
// Return: bool
struct is_hal_offloading {
  static bool return_value;
  std::function<bool()> body{[]() { return return_value; }};
  bool operator()() { return body(); };
};
extern struct is_hal_offloading is_hal_offloading;

// Name: is_opus_supported
// Params:
// Return: bool
struct is_opus_supported {
  static bool return_value;
  std::function<bool()> body{[]() { return return_value; }};
  bool operator()() { return body(); };
};
extern struct is_opus_supported is_opus_supported;

// Name: parse_a2dp_configuration
// Params: btav_a2dp_codec_index_t codec_index, const uint8_t* codec_info,
// btav_a2dp_codec_config_t* codec_parameters, std::vector<uint8_t>*
// vendor_specific_parameters Return: tA2DP_STATUS
struct parse_a2dp_configuration {
  static tA2DP_STATUS return_value;
  std::function<tA2DP_STATUS(btav_a2dp_codec_index_t codec_index,
                             const uint8_t* codec_info,
                             btav_a2dp_codec_config_t* codec_parameters,
                             std::vector<uint8_t>* vendor_specific_parameters)>
      body{[](btav_a2dp_codec_index_t /* codec_index */,
              const uint8_t* /* codec_info */,
              btav_a2dp_codec_config_t* /* codec_parameters */,
              std::vector<uint8_t>* /* vendor_specific_parameters */) {
        return return_value;
      }};
  tA2DP_STATUS operator()(btav_a2dp_codec_index_t codec_index,
                          const uint8_t* codec_info,
                          btav_a2dp_codec_config_t* codec_parameters,
                          std::vector<uint8_t>* vendor_specific_parameters) {
    return body(codec_index, codec_info, codec_parameters,
                vendor_specific_parameters);
  };
};
extern struct parse_a2dp_configuration parse_a2dp_configuration;

// Name: read
// Params: uint8_t* p_buf, uint32_t len
// Return: size_t
struct read {
  static size_t return_value;
  std::function<size_t(uint8_t* p_buf, uint32_t len)> body{
      [](uint8_t* /* p_buf */, uint32_t /* len */) { return return_value; }};
  size_t operator()(uint8_t* p_buf, uint32_t len) { return body(p_buf, len); };
};
extern struct read read;

// Name: set_audio_low_latency_mode_allowed
// Params: bool allowed
// Return: void
struct set_audio_low_latency_mode_allowed {
  std::function<void(bool allowed)> body{[](bool /* allowed */) {}};
  void operator()(bool allowed) { body(allowed); };
};
extern struct set_audio_low_latency_mode_allowed
    set_audio_low_latency_mode_allowed;

// Name: set_remote_delay
// Params: uint16_t delay_report
// Return: void
struct set_remote_delay {
  std::function<void(uint16_t delay_report)> body{
      [](uint16_t /* delay_report */) {}};
  void operator()(uint16_t delay_report) { body(delay_report); };
};
extern struct set_remote_delay set_remote_delay;

// Name: setup_codec
// Params:
// Return: bool
struct setup_codec {
  static bool return_value;
  std::function<bool()> body{[]() { return return_value; }};
  bool operator()() { return body(); };
};
extern struct setup_codec setup_codec;

// Name: sink_codec_index
// Params: const uint8_t* p_codec_info
// Return: std::optional<btav_a2dp_codec_index_t>
struct sink_codec_index {
  static std::optional<btav_a2dp_codec_index_t> return_value;
  std::function<std::optional<btav_a2dp_codec_index_t>(
      const uint8_t* p_codec_info)>
      body{[](const uint8_t* /* p_codec_info */) { return return_value; }};
  std::optional<btav_a2dp_codec_index_t> operator()(
      const uint8_t* p_codec_info) {
    return body(p_codec_info);
  };
};
extern struct sink_codec_index sink_codec_index;

// Name: source_codec_index
// Params: const uint8_t* p_codec_info
// Return: std::optional<btav_a2dp_codec_index_t>
struct source_codec_index {
  static std::optional<btav_a2dp_codec_index_t> return_value;
  std::function<std::optional<btav_a2dp_codec_index_t>(
      const uint8_t* p_codec_info)>
      body{[](const uint8_t* /* p_codec_info */) { return return_value; }};
  std::optional<btav_a2dp_codec_index_t> operator()(
      const uint8_t* p_codec_info) {
    return body(p_codec_info);
  };
};
extern struct source_codec_index source_codec_index;

// Name: start_session
// Params:
// Return: void
struct start_session {
  std::function<void()> body{[]() {}};
  void operator()() { body(); };
};
extern struct start_session start_session;

// Name: supports_codec
// Params: btav_a2dp_codec_index_t codec_index
// Return: bool
struct supports_codec {
  static bool return_value;
  std::function<bool(btav_a2dp_codec_index_t codec_index)> body{
      [](btav_a2dp_codec_index_t /* codec_index */) { return return_value; }};
  bool operator()(btav_a2dp_codec_index_t codec_index) {
    return body(codec_index);
  };
};
extern struct supports_codec supports_codec;

// Name: update_codec_offloading_capabilities
// Params: const std::vector<btav_a2dp_codec_config_t>& framework_preference
// Return: bool
struct update_codec_offloading_capabilities {
  static bool return_value;
  std::function<bool(
      const std::vector<btav_a2dp_codec_config_t>& framework_preference,
      bool supports_a2dp_hw_offload_v2)>
      body{[](const std::vector<
                  btav_a2dp_codec_config_t>& /* framework_preference */,
              bool /*supports_a2dp_hw_offload_v2*/) { return return_value; }};
  bool operator()(
      const std::vector<btav_a2dp_codec_config_t>& framework_preference,
      bool supports_a2dp_hw_offload_v2) {
    return body(framework_preference, supports_a2dp_hw_offload_v2);
  };
};
extern struct update_codec_offloading_capabilities
    update_codec_offloading_capabilities;

}  // namespace audio_hal_interface_a2dp_encoding
}  // namespace mock
}  // namespace test

// END mockcify generation
