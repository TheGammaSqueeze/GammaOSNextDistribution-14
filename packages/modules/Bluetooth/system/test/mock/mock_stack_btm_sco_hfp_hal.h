/*
 * Copyright 2022 The Android Open Source Project
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
 *   Functions generated:10
 *
 *  mockcify.pl ver 0.5.1
 */

#include <cstdint>
#include <functional>

// Original included files, if any

#include "device/include/esco_parameters.h"
#include "stack/btm/btm_sco_hfp_hal.h"

// Original usings

// Mocked compile conditionals, if any

namespace test {
namespace mock {
namespace stack_btm_sco_hfp_hal {

// Shared state between mocked functions and tests
// Name: enable_offload
// Params: bool enable
// Return: bool
struct enable_offload {
  static bool return_value;
  std::function<bool(bool enable)> body{
      [](bool /* enable */) { return return_value; }};
  bool operator()(bool enable) { return body(enable); };
};
extern struct enable_offload enable_offload;

// Name: get_codec_capabilities
// Params: uint64_t codecs
// Return: bt_codecs
struct get_codec_capabilities {
  static hfp_hal_interface::bt_codecs return_value;
  std::function<hfp_hal_interface::bt_codecs(uint64_t codecs)> body{
      [](uint64_t /* codecs */) { return return_value; }};
  hfp_hal_interface::bt_codecs operator()(uint64_t codecs) {
    return body(codecs);
  };
};
extern struct get_codec_capabilities get_codec_capabilities;

// Name: get_offload_enabled
// Params:
// Return: bool
struct get_offload_enabled {
  static bool return_value;
  std::function<bool()> body{[]() { return return_value; }};
  bool operator()() { return body(); };
};
extern struct get_offload_enabled get_offload_enabled;

// Name: get_offload_supported
// Params:
// Return: bool
struct get_offload_supported {
  static bool return_value;
  std::function<bool()> body{[]() { return return_value; }};
  bool operator()() { return body(); };
};
extern struct get_offload_supported get_offload_supported;

// Name: get_packet_size
// Params: int codec
// Return: int
struct get_packet_size {
  static int return_value;
  std::function<int(int /* codec */)> body{
      [](int /* codec */) { return return_value; }};
  int operator()(int codec) { return body(codec); };
};
extern struct get_packet_size get_packet_size;

// Name: get_wbs_supported
// Params:
// Return: bool
struct get_wbs_supported {
  static bool return_value;
  std::function<bool()> body{[]() { return return_value; }};
  bool operator()() { return body(); };
};
extern struct get_wbs_supported get_wbs_supported;

// Name: get_swb_supported
// Params:
// Return: bool
struct get_swb_supported {
  static bool return_value;
  std::function<bool()> body{[]() { return return_value; }};
  bool operator()() { return body(); };
};
extern struct get_swb_supported get_swb_supported;

// Name: is_coding_format_supported
// Params: esco_coding_format_t coding_format
// Return: bool
struct is_coding_format_supported {
  static bool return_value;
  std::function<bool(esco_coding_format_t coding_format)> body{
      [](esco_coding_format_t /* coding_format */) { return return_value; }};
  bool operator()(esco_coding_format_t coding_format) {
    return body(coding_format);
  };
};
extern struct is_coding_format_supported is_coding_format_supported;

// Name: init
// Params:
// Return: void
struct init {
  std::function<void()> body{[]() {}};
  void operator()() { body(); };
};
extern struct init init;

// Name: notify_sco_connection_change
// Params: RawAddress device, bool is_connected, int codec
// Return: void
struct notify_sco_connection_change {
  std::function<void(RawAddress device, bool is_connected, int codec)> body{
      [](RawAddress /* device */, bool /* is_connected */, int /* codec */) {}};
  void operator()(RawAddress device, bool is_connected, int codec) {
    body(device, is_connected, codec);
  };
};
extern struct notify_sco_connection_change notify_sco_connection_change;

// Name: set_codec_datapath
// Params: esco_coding_format_t coding_format
// Return: void
struct set_codec_datapath {
  std::function<void(int coding_format)> body{[](int /* coding_format */) {}};
  void operator()(int coding_format) { body(coding_format); };
};
extern struct set_codec_datapath set_codec_datapath;

// Name: update_esco_parameters
// Params: enh_esco_params_t* p_parms
// Return: void
struct update_esco_parameters {
  std::function<void(enh_esco_params_t* p_parms)> body{
      [](enh_esco_params_t* /* p_parms */) {}};
  void operator()(enh_esco_params_t* p_parms) { body(p_parms); };
};
extern struct update_esco_parameters update_esco_parameters;

}  // namespace stack_btm_sco_hfp_hal
}  // namespace mock
}  // namespace test

// END mockcify generation
