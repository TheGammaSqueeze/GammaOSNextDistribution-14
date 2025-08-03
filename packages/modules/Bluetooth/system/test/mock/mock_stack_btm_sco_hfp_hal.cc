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
/*
 * Generated mock file from original source file
 *   Functions generated:10
 *
 *  mockcify.pl ver 0.5.1
 */
// Mock include file to share data between tests and mock
#include "test/mock/mock_stack_btm_sco_hfp_hal.h"

#ifndef __clang_analyzer__
#include <cstdint>

#include "test/common/mock_functions.h"

// Original usings

// Mocked internal structures, if any

namespace test {
namespace mock {
namespace stack_btm_sco_hfp_hal {

// Function state capture and return values, if needed
struct enable_offload enable_offload;
struct get_codec_capabilities get_codec_capabilities;
struct get_offload_enabled get_offload_enabled;
struct get_offload_supported get_offload_supported;
struct get_packet_size get_packet_size;
struct get_wbs_supported get_wbs_supported;
struct get_swb_supported get_swb_supported;
struct init init;
struct notify_sco_connection_change notify_sco_connection_change;
struct set_codec_datapath set_codec_datapath;
struct update_esco_parameters update_esco_parameters;
struct is_coding_format_supported is_coding_format_supported;

}  // namespace stack_btm_sco_hfp_hal
}  // namespace mock
}  // namespace test

// Mocked function return values, if any
namespace test {
namespace mock {
namespace stack_btm_sco_hfp_hal {

bool enable_offload::return_value = false;
hfp_hal_interface::bt_codecs get_codec_capabilities::return_value = {};
bool get_offload_enabled::return_value = false;
bool get_offload_supported::return_value = false;
int get_packet_size::return_value = 0;
bool get_wbs_supported::return_value = false;
bool get_swb_supported::return_value = false;
bool is_coding_format_supported::return_value = false;

}  // namespace stack_btm_sco_hfp_hal
}  // namespace mock
}  // namespace test

namespace hfp_hal_interface {

// Mocked functions, if any
bool enable_offload(bool enable) {
  inc_func_call_count(__func__);
  return test::mock::stack_btm_sco_hfp_hal::enable_offload(enable);
}
bt_codecs get_codec_capabilities(uint64_t codecs) {
  inc_func_call_count(__func__);
  return test::mock::stack_btm_sco_hfp_hal::get_codec_capabilities(codecs);
}
bool get_offload_enabled() {
  inc_func_call_count(__func__);
  return test::mock::stack_btm_sco_hfp_hal::get_offload_enabled();
}
bool get_offload_supported() {
  inc_func_call_count(__func__);
  return test::mock::stack_btm_sco_hfp_hal::get_offload_supported();
}
int get_packet_size(int codec) {
  inc_func_call_count(__func__);
  return test::mock::stack_btm_sco_hfp_hal::get_packet_size(codec);
}
bool get_wbs_supported() {
  inc_func_call_count(__func__);
  return test::mock::stack_btm_sco_hfp_hal::get_wbs_supported();
}
bool get_swb_supported() {
  inc_func_call_count(__func__);
  return test::mock::stack_btm_sco_hfp_hal::get_swb_supported();
}
bool is_coding_format_supported(esco_coding_format_t coding_format) {
  inc_func_call_count(__func__);
  return test::mock::stack_btm_sco_hfp_hal::is_coding_format_supported(
      coding_format);
}
void init() {
  inc_func_call_count(__func__);
  test::mock::stack_btm_sco_hfp_hal::init();
}
void notify_sco_connection_change(RawAddress device, bool is_connected,
                                  int codec) {
  inc_func_call_count(__func__);
  test::mock::stack_btm_sco_hfp_hal::notify_sco_connection_change(
      device, is_connected, codec);
}
void set_codec_datapath(int coding_format) {
  inc_func_call_count(__func__);
  test::mock::stack_btm_sco_hfp_hal::set_codec_datapath(coding_format);
}
void update_esco_parameters(enh_esco_params_t* p_parms) {
  inc_func_call_count(__func__);
  test::mock::stack_btm_sco_hfp_hal::update_esco_parameters(p_parms);
}

}  // namespace hfp_hal_interface

// Mocked functions complete
#endif  //  __clang_analyzer__
// END mockcify generation
