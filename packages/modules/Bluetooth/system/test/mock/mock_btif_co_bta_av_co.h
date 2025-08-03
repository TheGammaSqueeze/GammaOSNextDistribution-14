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
 *   Functions generated:25
 *
 *  mockcify.pl ver 0.5.0
 */

#include <cstdint>
#include <functional>

// Original included files, if any
#include <vector>

#include "bta/include/bta_av_api.h"
#include "include/hardware/bt_av.h"
#include "stack/include/a2dp_codec_api.h"
#include "stack/include/a2dp_error_codes.h"
#include "stack/include/avdt_api.h"
#include "stack/include/bt_hdr.h"
#include "types/raw_address.h"

// Original usings

// Mocked compile conditionals, if any

namespace test {
namespace mock {
namespace btif_co_bta_av_co {

// Shared state between mocked functions and tests
// Name: bta_av_co_audio_close
// Params: tBTA_AV_HNDL bta_av_handle, const RawAddress& peer_address
// Return: void
struct bta_av_co_audio_close {
  std::function<void(tBTA_AV_HNDL bta_av_handle,
                     const RawAddress& peer_address)>
      body{[](tBTA_AV_HNDL /* bta_av_handle */,
              const RawAddress& /* peer_address */) {}};
  void operator()(tBTA_AV_HNDL bta_av_handle, const RawAddress& peer_address) {
    body(bta_av_handle, peer_address);
  };
};
extern struct bta_av_co_audio_close bta_av_co_audio_close;

// Name: bta_av_co_audio_delay
// Params: tBTA_AV_HNDL bta_av_handle, const RawAddress& peer_address, uint16_t
// delay Return: void
struct bta_av_co_audio_delay {
  std::function<void(tBTA_AV_HNDL bta_av_handle, const RawAddress& peer_address,
                     uint16_t delay)>
      body{[](tBTA_AV_HNDL /* bta_av_handle */,
              const RawAddress& /* peer_address */, uint16_t /* delay */) {}};
  void operator()(tBTA_AV_HNDL bta_av_handle, const RawAddress& peer_address,
                  uint16_t delay) {
    body(bta_av_handle, peer_address, delay);
  };
};
extern struct bta_av_co_audio_delay bta_av_co_audio_delay;

// Name: bta_av_co_audio_disc_res
// Params: tBTA_AV_HNDL bta_av_handle, const RawAddress& peer_address, uint8_t
// num_seps, uint8_t num_sinks, uint8_t num_sources, uint16_t uuid_local Return:
// void
struct bta_av_co_audio_disc_res {
  std::function<void(tBTA_AV_HNDL bta_av_handle, const RawAddress& peer_address,
                     uint8_t num_seps, uint8_t num_sinks, uint8_t num_sources,
                     uint16_t uuid_local)>
      body{[](tBTA_AV_HNDL /* bta_av_handle */,
              const RawAddress& /* peer_address */, uint8_t /* num_seps */,
              uint8_t /* num_sinks */, uint8_t /* num_sources */,
              uint16_t /* uuid_local */) {}};
  void operator()(tBTA_AV_HNDL bta_av_handle, const RawAddress& peer_address,
                  uint8_t num_seps, uint8_t num_sinks, uint8_t num_sources,
                  uint16_t uuid_local) {
    body(bta_av_handle, peer_address, num_seps, num_sinks, num_sources,
         uuid_local);
  };
};
extern struct bta_av_co_audio_disc_res bta_av_co_audio_disc_res;

// Name: bta_av_co_audio_drop
// Params: tBTA_AV_HNDL bta_av_handle, const RawAddress& peer_address
// Return: void
struct bta_av_co_audio_drop {
  std::function<void(tBTA_AV_HNDL bta_av_handle,
                     const RawAddress& peer_address)>
      body{[](tBTA_AV_HNDL /* bta_av_handle */,
              const RawAddress& /* peer_address */) {}};
  void operator()(tBTA_AV_HNDL bta_av_handle, const RawAddress& peer_address) {
    body(bta_av_handle, peer_address);
  };
};
extern struct bta_av_co_audio_drop bta_av_co_audio_drop;

// Name: bta_av_co_audio_getconfig
// Params: tBTA_AV_HNDL bta_av_handle, const RawAddress& peer_address, uint8_t*
// p_codec_info, uint8_t* p_sep_info_idx, uint8_t seid, uint8_t* p_num_protect,
// uint8_t* p_protect_info Return: tA2DP_STATUS
struct bta_av_co_audio_getconfig {
  static tA2DP_STATUS return_value;
  std::function<tA2DP_STATUS(
      tBTA_AV_HNDL bta_av_handle, const RawAddress& peer_address,
      uint8_t* p_codec_info, uint8_t* p_sep_info_idx, uint8_t seid,
      uint8_t* p_num_protect, uint8_t* p_protect_info)>
      body{[](tBTA_AV_HNDL /* bta_av_handle */,
              const RawAddress& /* peer_address */, uint8_t* /* p_codec_info */,
              uint8_t* /* p_sep_info_idx */, uint8_t /* seid */,
              uint8_t* /* p_num_protect */,
              uint8_t* /* p_protect_info */) { return return_value; }};
  tA2DP_STATUS operator()(tBTA_AV_HNDL bta_av_handle,
                          const RawAddress& peer_address, uint8_t* p_codec_info,
                          uint8_t* p_sep_info_idx, uint8_t seid,
                          uint8_t* p_num_protect, uint8_t* p_protect_info) {
    return body(bta_av_handle, peer_address, p_codec_info, p_sep_info_idx, seid,
                p_num_protect, p_protect_info);
  };
};
extern struct bta_av_co_audio_getconfig bta_av_co_audio_getconfig;

// Name: bta_av_co_audio_init
// Params: btav_a2dp_codec_index_t codec_index, AvdtpSepConfig* p_cfg
// Return: bool
struct bta_av_co_audio_init {
  static bool return_value;
  std::function<bool(btav_a2dp_codec_index_t codec_index,
                     AvdtpSepConfig* p_cfg)>
      body{[](btav_a2dp_codec_index_t /* codec_index */,
              AvdtpSepConfig* /* p_cfg */) { return return_value; }};
  bool operator()(btav_a2dp_codec_index_t codec_index, AvdtpSepConfig* p_cfg) {
    return body(codec_index, p_cfg);
  };
};
extern struct bta_av_co_audio_init bta_av_co_audio_init;

// Name: bta_av_co_audio_open
// Params: tBTA_AV_HNDL bta_av_handle, const RawAddress& peer_address, uint16_t
// mtu Return: void
struct bta_av_co_audio_open {
  std::function<void(tBTA_AV_HNDL bta_av_handle, const RawAddress& peer_address,
                     uint16_t mtu)>
      body{[](tBTA_AV_HNDL /* bta_av_handle */,
              const RawAddress& /* peer_address */, uint16_t /* mtu */) {}};
  void operator()(tBTA_AV_HNDL bta_av_handle, const RawAddress& peer_address,
                  uint16_t mtu) {
    body(bta_av_handle, peer_address, mtu);
  };
};
extern struct bta_av_co_audio_open bta_av_co_audio_open;

// Name: bta_av_co_audio_setconfig
// Params: tBTA_AV_HNDL bta_av_handle, const RawAddress& peer_address, const
// uint8_t* p_codec_info, uint8_t seid, uint8_t num_protect, const uint8_t*
// p_protect_info, uint8_t t_local_sep, uint8_t avdt_handle Return: void
struct bta_av_co_audio_setconfig {
  std::function<void(tBTA_AV_HNDL bta_av_handle, const RawAddress& peer_address,
                     const uint8_t* p_codec_info, uint8_t seid,
                     uint8_t num_protect, const uint8_t* p_protect_info,
                     uint8_t t_local_sep, uint8_t avdt_handle)>
      body{[](tBTA_AV_HNDL /* bta_av_handle */,
              const RawAddress& /* peer_address */,
              const uint8_t* /* p_codec_info */, uint8_t /* seid */,
              uint8_t /* num_protect */, const uint8_t* /* p_protect_info */,
              uint8_t /* t_local_sep */, uint8_t /* avdt_handle */) {}};
  void operator()(tBTA_AV_HNDL bta_av_handle, const RawAddress& peer_address,
                  const uint8_t* p_codec_info, uint8_t seid,
                  uint8_t num_protect, const uint8_t* p_protect_info,
                  uint8_t t_local_sep, uint8_t avdt_handle) {
    body(bta_av_handle, peer_address, p_codec_info, seid, num_protect,
         p_protect_info, t_local_sep, avdt_handle);
  };
};
extern struct bta_av_co_audio_setconfig bta_av_co_audio_setconfig;

// Name: bta_av_co_audio_source_data_path
// Params: const uint8_t* p_codec_info, uint32_t* p_timestamp
// Return: BT_HDR*
struct bta_av_co_audio_source_data_path {
  static BT_HDR* return_value;
  std::function<BT_HDR*(const uint8_t* p_codec_info, uint32_t* p_timestamp)>
      body{[](const uint8_t* /* p_codec_info */, uint32_t* /* p_timestamp */) {
        return return_value;
      }};
  BT_HDR* operator()(const uint8_t* p_codec_info, uint32_t* p_timestamp) {
    return body(p_codec_info, p_timestamp);
  };
};
extern struct bta_av_co_audio_source_data_path bta_av_co_audio_source_data_path;

// Name: bta_av_co_audio_start
// Params: tBTA_AV_HNDL bta_av_handle, const RawAddress& peer_address, const
// uint8_t* p_codec_info, bool* p_no_rtp_header Return: void
struct bta_av_co_audio_start {
  std::function<void(tBTA_AV_HNDL bta_av_handle, const RawAddress& peer_address,
                     const uint8_t* p_codec_info, bool* p_no_rtp_header)>
      body{[](tBTA_AV_HNDL /* bta_av_handle */,
              const RawAddress& /* peer_address */,
              const uint8_t* /* p_codec_info */,
              bool* /* p_no_rtp_header */) {}};
  void operator()(tBTA_AV_HNDL bta_av_handle, const RawAddress& peer_address,
                  const uint8_t* p_codec_info, bool* p_no_rtp_header) {
    body(bta_av_handle, peer_address, p_codec_info, p_no_rtp_header);
  };
};
extern struct bta_av_co_audio_start bta_av_co_audio_start;

// Name: bta_av_co_audio_stop
// Params: tBTA_AV_HNDL bta_av_handle, const RawAddress& peer_address
// Return: void
struct bta_av_co_audio_stop {
  std::function<void(tBTA_AV_HNDL bta_av_handle,
                     const RawAddress& peer_address)>
      body{[](tBTA_AV_HNDL /* bta_av_handle */,
              const RawAddress& /* peer_address */) {}};
  void operator()(tBTA_AV_HNDL bta_av_handle, const RawAddress& peer_address) {
    body(bta_av_handle, peer_address);
  };
};
extern struct bta_av_co_audio_stop bta_av_co_audio_stop;

// Name: bta_av_co_audio_update_mtu
// Params: tBTA_AV_HNDL bta_av_handle, const RawAddress& peer_address, uint16_t
// mtu Return: void
struct bta_av_co_audio_update_mtu {
  std::function<void(tBTA_AV_HNDL bta_av_handle, const RawAddress& peer_address,
                     uint16_t mtu)>
      body{[](tBTA_AV_HNDL /* bta_av_handle */,
              const RawAddress& /* peer_address */, uint16_t /* mtu */) {}};
  void operator()(tBTA_AV_HNDL bta_av_handle, const RawAddress& peer_address,
                  uint16_t mtu) {
    body(bta_av_handle, peer_address, mtu);
  };
};
extern struct bta_av_co_audio_update_mtu bta_av_co_audio_update_mtu;

// Name: bta_av_co_get_encoder_effective_frame_size
// Params:
// Return: int
struct bta_av_co_get_encoder_effective_frame_size {
  static int return_value;
  std::function<int()> body{[]() { return return_value; }};
  int operator()() { return body(); };
};
extern struct bta_av_co_get_encoder_effective_frame_size
    bta_av_co_get_encoder_effective_frame_size;

// Name: bta_av_co_get_encoder_interface
// Params: void
// Return: const tA2DP_ENCODER_INTERFACE*
struct bta_av_co_get_encoder_interface {
  static const tA2DP_ENCODER_INTERFACE* return_value;
  std::function<const tA2DP_ENCODER_INTERFACE*(void)> body{
      [](void) { return return_value; }};
  const tA2DP_ENCODER_INTERFACE* operator()(void) { return body(); };
};
extern struct bta_av_co_get_encoder_interface bta_av_co_get_encoder_interface;

// Name: bta_av_co_get_peer_params
// Params: const RawAddress& peer_address, tA2DP_ENCODER_INIT_PEER_PARAMS*
// p_peer_params Return: void
struct bta_av_co_get_peer_params {
  std::function<void(const RawAddress& peer_address,
                     tA2DP_ENCODER_INIT_PEER_PARAMS* p_peer_params)>
      body{[](const RawAddress& /* peer_address */,
              tA2DP_ENCODER_INIT_PEER_PARAMS* /* p_peer_params */) {}};
  void operator()(const RawAddress& peer_address,
                  tA2DP_ENCODER_INIT_PEER_PARAMS* p_peer_params) {
    body(peer_address, p_peer_params);
  };
};
extern struct bta_av_co_get_peer_params bta_av_co_get_peer_params;

// Name: bta_av_co_get_scmst_info
// Params: const RawAddress& peer_address
// Return: btav_a2dp_scmst_info_t
struct bta_av_co_get_scmst_info {
  static btav_a2dp_scmst_info_t return_value;
  std::function<btav_a2dp_scmst_info_t(const RawAddress& peer_address)> body{
      [](const RawAddress& /* peer_address */) { return return_value; }};
  btav_a2dp_scmst_info_t operator()(const RawAddress& peer_address) {
    return body(peer_address);
  };
};
extern struct bta_av_co_get_scmst_info bta_av_co_get_scmst_info;

// Name: bta_av_co_init
// Params: const std::vector<btav_a2dp_codec_config_t>& codec_priorities
// Return: void
struct bta_av_co_init {
  std::function<void(
      const std::vector<btav_a2dp_codec_config_t>& codec_priorities,
      std::vector<btav_a2dp_codec_info_t>* supported_codecs)>
      body{[](const std::vector<
                  btav_a2dp_codec_config_t>& /* codec_priorities */,
              std::vector<btav_a2dp_codec_info_t>* /* supported_codecs */) {}};
  void operator()(const std::vector<btav_a2dp_codec_config_t>& codec_priorities,
                  std::vector<btav_a2dp_codec_info_t>* supported_codecs) {
    body(codec_priorities, supported_codecs);
  };
};
extern struct bta_av_co_init bta_av_co_init;

// Name: bta_av_co_is_supported_codec
// Params: btav_a2dp_codec_index_t codec_index
// Return: bool
struct bta_av_co_is_supported_codec {
  static bool return_value;
  std::function<bool(btav_a2dp_codec_index_t codec_index)> body{
      [](btav_a2dp_codec_index_t /* codec_index */) { return return_value; }};
  bool operator()(btav_a2dp_codec_index_t codec_index) {
    return body(codec_index);
  };
};
extern struct bta_av_co_is_supported_codec bta_av_co_is_supported_codec;

// Name: bta_av_co_set_active_peer
// Params: const RawAddress& peer_address
// Return: bool
struct bta_av_co_set_active_peer {
  static bool return_value;
  std::function<bool(const RawAddress& peer_address)> body{
      [](const RawAddress& /* peer_address */) { return return_value; }};
  bool operator()(const RawAddress& peer_address) {
    return body(peer_address);
  };
};
extern struct bta_av_co_set_active_peer bta_av_co_set_active_peer;

// Name: bta_av_co_set_codec_audio_config
// Params: const btav_a2dp_codec_config_t& codec_audio_config
// Return: bool
struct bta_av_co_set_codec_audio_config {
  static bool return_value;
  std::function<bool(const btav_a2dp_codec_config_t& codec_audio_config)> body{
      [](const btav_a2dp_codec_config_t& /* codec_audio_config */) {
        return return_value;
      }};
  bool operator()(const btav_a2dp_codec_config_t& codec_audio_config) {
    return body(codec_audio_config);
  };
};
extern struct bta_av_co_set_codec_audio_config bta_av_co_set_codec_audio_config;

// Name: bta_av_co_set_codec_user_config
// Params: const RawAddress& peer_address, const btav_a2dp_codec_config_t&
// codec_user_config, bool* p_restart_output Return: bool
struct bta_av_co_set_codec_user_config {
  static bool return_value;
  std::function<bool(const RawAddress& peer_address,
                     const btav_a2dp_codec_config_t& codec_user_config,
                     bool* p_restart_output)>
      body{[](const RawAddress& /* peer_address */,
              const btav_a2dp_codec_config_t& /* codec_user_config */,
              bool* /* p_restart_output */) { return return_value; }};
  bool operator()(const RawAddress& peer_address,
                  const btav_a2dp_codec_config_t& codec_user_config,
                  bool* p_restart_output) {
    return body(peer_address, codec_user_config, p_restart_output);
  };
};
extern struct bta_av_co_set_codec_user_config bta_av_co_set_codec_user_config;

// Name: bta_av_get_a2dp_current_codec
// Params: void
// Return: A2dpCodecConfig*
struct bta_av_get_a2dp_current_codec {
  static A2dpCodecConfig* return_value;
  std::function<A2dpCodecConfig*(void)> body{[](void) { return return_value; }};
  A2dpCodecConfig* operator()(void) { return body(); };
};
extern struct bta_av_get_a2dp_current_codec bta_av_get_a2dp_current_codec;

// Name: bta_av_get_a2dp_peer_current_codec
// Params: const RawAddress& peer_address
// Return: A2dpCodecConfig*
struct bta_av_get_a2dp_peer_current_codec {
  static A2dpCodecConfig* return_value;
  std::function<A2dpCodecConfig*(const RawAddress& peer_address)> body{
      [](const RawAddress& /* peer_address */) { return return_value; }};
  A2dpCodecConfig* operator()(const RawAddress& peer_address) {
    return body(peer_address);
  };
};
extern struct bta_av_get_a2dp_peer_current_codec
    bta_av_get_a2dp_peer_current_codec;

// Name: btif_a2dp_codec_debug_dump
// Params: int fd
// Return: void
struct btif_a2dp_codec_debug_dump {
  std::function<void(int fd)> body{[](int /* fd */) {}};
  void operator()(int fd) { body(fd); };
};
extern struct btif_a2dp_codec_debug_dump btif_a2dp_codec_debug_dump;

}  // namespace btif_co_bta_av_co
}  // namespace mock
}  // namespace test

// END mockcify generation
