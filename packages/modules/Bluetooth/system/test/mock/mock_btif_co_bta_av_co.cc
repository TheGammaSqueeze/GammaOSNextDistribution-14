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
 *   Functions generated:25
 *
 *  mockcify.pl ver 0.5.0
 */

// Mock include file to share data between tests and mock
#include "test/mock/mock_btif_co_bta_av_co.h"

#include <cstdint>

#include "test/common/mock_functions.h"

// Original usings

// Mocked internal structures, if any

namespace test {
namespace mock {
namespace btif_co_bta_av_co {

// Function state capture and return values, if needed
struct bta_av_co_audio_close bta_av_co_audio_close;
struct bta_av_co_audio_delay bta_av_co_audio_delay;
struct bta_av_co_audio_disc_res bta_av_co_audio_disc_res;
struct bta_av_co_audio_drop bta_av_co_audio_drop;
struct bta_av_co_audio_getconfig bta_av_co_audio_getconfig;
struct bta_av_co_audio_init bta_av_co_audio_init;
struct bta_av_co_audio_open bta_av_co_audio_open;
struct bta_av_co_audio_setconfig bta_av_co_audio_setconfig;
struct bta_av_co_audio_source_data_path bta_av_co_audio_source_data_path;
struct bta_av_co_audio_start bta_av_co_audio_start;
struct bta_av_co_audio_stop bta_av_co_audio_stop;
struct bta_av_co_audio_update_mtu bta_av_co_audio_update_mtu;
struct bta_av_co_get_encoder_effective_frame_size
    bta_av_co_get_encoder_effective_frame_size;
struct bta_av_co_get_encoder_interface bta_av_co_get_encoder_interface;
struct bta_av_co_get_peer_params bta_av_co_get_peer_params;
struct bta_av_co_get_scmst_info bta_av_co_get_scmst_info;
struct bta_av_co_init bta_av_co_init;
struct bta_av_co_is_supported_codec bta_av_co_is_supported_codec;
struct bta_av_co_set_active_peer bta_av_co_set_active_peer;
struct bta_av_co_set_codec_audio_config bta_av_co_set_codec_audio_config;
struct bta_av_co_set_codec_user_config bta_av_co_set_codec_user_config;
struct bta_av_get_a2dp_current_codec bta_av_get_a2dp_current_codec;
struct bta_av_get_a2dp_peer_current_codec bta_av_get_a2dp_peer_current_codec;
struct btif_a2dp_codec_debug_dump btif_a2dp_codec_debug_dump;

}  // namespace btif_co_bta_av_co
}  // namespace mock
}  // namespace test

// Mocked function return values, if any
namespace test {
namespace mock {
namespace btif_co_bta_av_co {

tA2DP_STATUS bta_av_co_audio_getconfig::return_value = 0;
bool bta_av_co_audio_init::return_value = false;
BT_HDR* bta_av_co_audio_source_data_path::return_value = nullptr;
int bta_av_co_get_encoder_effective_frame_size::return_value = 0;
const tA2DP_ENCODER_INTERFACE* bta_av_co_get_encoder_interface::return_value =
    nullptr;
btav_a2dp_scmst_info_t bta_av_co_get_scmst_info::return_value = {};
bool bta_av_co_is_supported_codec::return_value = false;
bool bta_av_co_set_active_peer::return_value = false;
bool bta_av_co_set_codec_audio_config::return_value = false;
bool bta_av_co_set_codec_user_config::return_value = false;
A2dpCodecConfig* bta_av_get_a2dp_current_codec::return_value = nullptr;
A2dpCodecConfig* bta_av_get_a2dp_peer_current_codec::return_value = nullptr;

}  // namespace btif_co_bta_av_co
}  // namespace mock
}  // namespace test

// Mocked functions, if any
void bta_av_co_audio_close(tBTA_AV_HNDL bta_av_handle,
                           const RawAddress& peer_address) {
  inc_func_call_count(__func__);
  test::mock::btif_co_bta_av_co::bta_av_co_audio_close(bta_av_handle,
                                                       peer_address);
}
void bta_av_co_audio_delay(tBTA_AV_HNDL bta_av_handle,
                           const RawAddress& peer_address, uint16_t delay) {
  inc_func_call_count(__func__);
  test::mock::btif_co_bta_av_co::bta_av_co_audio_delay(bta_av_handle,
                                                       peer_address, delay);
}
void bta_av_co_audio_disc_res(tBTA_AV_HNDL bta_av_handle,
                              const RawAddress& peer_address, uint8_t num_seps,
                              uint8_t num_sinks, uint8_t num_sources,
                              uint16_t uuid_local) {
  inc_func_call_count(__func__);
  test::mock::btif_co_bta_av_co::bta_av_co_audio_disc_res(
      bta_av_handle, peer_address, num_seps, num_sinks, num_sources,
      uuid_local);
}
void bta_av_co_audio_drop(tBTA_AV_HNDL bta_av_handle,
                          const RawAddress& peer_address) {
  inc_func_call_count(__func__);
  test::mock::btif_co_bta_av_co::bta_av_co_audio_drop(bta_av_handle,
                                                      peer_address);
}
tA2DP_STATUS bta_av_co_audio_getconfig(tBTA_AV_HNDL bta_av_handle,
                                       const RawAddress& peer_address,
                                       uint8_t* p_codec_info,
                                       uint8_t* p_sep_info_idx, uint8_t seid,
                                       uint8_t* p_num_protect,
                                       uint8_t* p_protect_info) {
  inc_func_call_count(__func__);
  return test::mock::btif_co_bta_av_co::bta_av_co_audio_getconfig(
      bta_av_handle, peer_address, p_codec_info, p_sep_info_idx, seid,
      p_num_protect, p_protect_info);
}
bool bta_av_co_audio_init(btav_a2dp_codec_index_t codec_index,
                          AvdtpSepConfig* p_cfg) {
  inc_func_call_count(__func__);
  return test::mock::btif_co_bta_av_co::bta_av_co_audio_init(codec_index,
                                                             p_cfg);
}
void bta_av_co_audio_open(tBTA_AV_HNDL bta_av_handle,
                          const RawAddress& peer_address, uint16_t mtu) {
  inc_func_call_count(__func__);
  test::mock::btif_co_bta_av_co::bta_av_co_audio_open(bta_av_handle,
                                                      peer_address, mtu);
}
void bta_av_co_audio_setconfig(tBTA_AV_HNDL bta_av_handle,
                               const RawAddress& peer_address,
                               const uint8_t* p_codec_info, uint8_t seid,
                               uint8_t num_protect,
                               const uint8_t* p_protect_info,
                               uint8_t t_local_sep, uint8_t avdt_handle) {
  inc_func_call_count(__func__);
  test::mock::btif_co_bta_av_co::bta_av_co_audio_setconfig(
      bta_av_handle, peer_address, p_codec_info, seid, num_protect,
      p_protect_info, t_local_sep, avdt_handle);
}
BT_HDR* bta_av_co_audio_source_data_path(const uint8_t* p_codec_info,
                                         uint32_t* p_timestamp) {
  inc_func_call_count(__func__);
  return test::mock::btif_co_bta_av_co::bta_av_co_audio_source_data_path(
      p_codec_info, p_timestamp);
}
void bta_av_co_audio_start(tBTA_AV_HNDL bta_av_handle,
                           const RawAddress& peer_address,
                           const uint8_t* p_codec_info, bool* p_no_rtp_header) {
  inc_func_call_count(__func__);
  test::mock::btif_co_bta_av_co::bta_av_co_audio_start(
      bta_av_handle, peer_address, p_codec_info, p_no_rtp_header);
}
void bta_av_co_audio_stop(tBTA_AV_HNDL bta_av_handle,
                          const RawAddress& peer_address) {
  inc_func_call_count(__func__);
  test::mock::btif_co_bta_av_co::bta_av_co_audio_stop(bta_av_handle,
                                                      peer_address);
}
void bta_av_co_audio_update_mtu(tBTA_AV_HNDL bta_av_handle,
                                const RawAddress& peer_address, uint16_t mtu) {
  inc_func_call_count(__func__);
  test::mock::btif_co_bta_av_co::bta_av_co_audio_update_mtu(bta_av_handle,
                                                            peer_address, mtu);
}
int bta_av_co_get_encoder_effective_frame_size() {
  inc_func_call_count(__func__);
  return test::mock::btif_co_bta_av_co::
      bta_av_co_get_encoder_effective_frame_size();
}
const tA2DP_ENCODER_INTERFACE* bta_av_co_get_encoder_interface(void) {
  inc_func_call_count(__func__);
  return test::mock::btif_co_bta_av_co::bta_av_co_get_encoder_interface();
}
void bta_av_co_get_peer_params(const RawAddress& peer_address,
                               tA2DP_ENCODER_INIT_PEER_PARAMS* p_peer_params) {
  inc_func_call_count(__func__);
  test::mock::btif_co_bta_av_co::bta_av_co_get_peer_params(peer_address,
                                                           p_peer_params);
}
btav_a2dp_scmst_info_t bta_av_co_get_scmst_info(
    const RawAddress& peer_address) {
  inc_func_call_count(__func__);
  return test::mock::btif_co_bta_av_co::bta_av_co_get_scmst_info(peer_address);
}
void bta_av_co_init(
    const std::vector<btav_a2dp_codec_config_t>& codec_priorities,
    std::vector<btav_a2dp_codec_info_t>* supported_codecs) {
  inc_func_call_count(__func__);
  test::mock::btif_co_bta_av_co::bta_av_co_init(codec_priorities,
                                                supported_codecs);
}
bool bta_av_co_is_supported_codec(btav_a2dp_codec_index_t codec_index) {
  inc_func_call_count(__func__);
  return test::mock::btif_co_bta_av_co::bta_av_co_is_supported_codec(
      codec_index);
}
bool bta_av_co_set_active_peer(const RawAddress& peer_address) {
  inc_func_call_count(__func__);
  return test::mock::btif_co_bta_av_co::bta_av_co_set_active_peer(peer_address);
}
bool bta_av_co_set_codec_audio_config(
    const btav_a2dp_codec_config_t& codec_audio_config) {
  inc_func_call_count(__func__);
  return test::mock::btif_co_bta_av_co::bta_av_co_set_codec_audio_config(
      codec_audio_config);
}
bool bta_av_co_set_codec_user_config(
    const RawAddress& peer_address,
    const btav_a2dp_codec_config_t& codec_user_config, bool* p_restart_output) {
  inc_func_call_count(__func__);
  return test::mock::btif_co_bta_av_co::bta_av_co_set_codec_user_config(
      peer_address, codec_user_config, p_restart_output);
}
A2dpCodecConfig* bta_av_get_a2dp_current_codec(void) {
  inc_func_call_count(__func__);
  return test::mock::btif_co_bta_av_co::bta_av_get_a2dp_current_codec();
}
A2dpCodecConfig* bta_av_get_a2dp_peer_current_codec(
    const RawAddress& peer_address) {
  inc_func_call_count(__func__);
  return test::mock::btif_co_bta_av_co::bta_av_get_a2dp_peer_current_codec(
      peer_address);
}
void btif_a2dp_codec_debug_dump(int fd) {
  inc_func_call_count(__func__);
  test::mock::btif_co_bta_av_co::btif_a2dp_codec_debug_dump(fd);
}
// Mocked functions complete
// END mockcify generation
