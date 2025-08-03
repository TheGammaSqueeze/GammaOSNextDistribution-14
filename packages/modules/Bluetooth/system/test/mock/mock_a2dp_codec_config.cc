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

/*
 * Generated mock file from original source file
 *   Functions generated:67
 */

#include <base/logging.h>

#include <string>

#include "a2dp_codec_api.h"
#include "bta/av/bta_av_int.h"
#include "stack/include/bt_hdr.h"
#include "test/common/mock_functions.h"

A2dpCodecConfig* A2dpCodecConfig::createCodec(
    btav_a2dp_codec_index_t codec_index,
    btav_a2dp_codec_priority_t codec_priority) {
  inc_func_call_count(__func__);
  return nullptr;
}
A2dpCodecConfig* A2dpCodecs::findSinkCodecConfig(const uint8_t* p_codec_info) {
  inc_func_call_count(__func__);
  return nullptr;
}
A2dpCodecConfig* A2dpCodecs::findSourceCodecConfig(
    const uint8_t* p_codec_info) {
  inc_func_call_count(__func__);
  return nullptr;
}
A2dpCodecConfig::A2dpCodecConfig(btav_a2dp_codec_index_t codec_index,
                                 const std::string& name,
                                 btav_a2dp_codec_priority_t codec_priority)
    : codec_index_(codec_index),
      name_(name),
      default_codec_priority_(codec_priority) {
  inc_func_call_count(__func__);
}
A2dpCodecConfig::~A2dpCodecConfig() { inc_func_call_count(__func__); }
A2dpCodecs::A2dpCodecs(
    const std::vector<btav_a2dp_codec_config_t>& codec_priorities)
    : current_codec_config_(nullptr) {
  inc_func_call_count(__func__);
}
A2dpCodecs::~A2dpCodecs() { inc_func_call_count(__func__); }
bool A2DP_AdjustCodec(uint8_t* p_codec_info) {
  inc_func_call_count(__func__);
  return false;
}
bool A2DP_BuildCodecHeader(const uint8_t* p_codec_info, BT_HDR* p_buf,
                           uint16_t frames_per_packet) {
  inc_func_call_count(__func__);
  return false;
}
bool A2DP_CodecEquals(const uint8_t* p_codec_info_a,
                      const uint8_t* p_codec_info_b) {
  inc_func_call_count(__func__);
  return false;
}
bool A2DP_CodecTypeEquals(const uint8_t* p_codec_info_a,
                          const uint8_t* p_codec_info_b) {
  inc_func_call_count(__func__);
  return false;
}
bool A2DP_GetPacketTimestamp(const uint8_t* p_codec_info, const uint8_t* p_data,
                             uint32_t* p_timestamp) {
  inc_func_call_count(__func__);
  return false;
}
bool A2DP_InitCodecConfig(btav_a2dp_codec_index_t codec_index,
                          AvdtpSepConfig* p_cfg) {
  inc_func_call_count(__func__);
  return false;
}
int A2DP_GetEecoderEffectiveFrameSize(const uint8_t* p_codec_info) {
  inc_func_call_count(__func__);
  return 0;
}
bool A2DP_IsPeerSinkCodecValid(const uint8_t* p_codec_info) {
  inc_func_call_count(__func__);
  return false;
}
bool A2DP_IsPeerSourceCodecSupported(const uint8_t* p_codec_info) {
  inc_func_call_count(__func__);
  return false;
}
bool A2DP_IsPeerSourceCodecValid(const uint8_t* p_codec_info) {
  inc_func_call_count(__func__);
  return false;
}
bool A2DP_IsSinkCodecSupported(const uint8_t* p_codec_info) {
  inc_func_call_count(__func__);
  return false;
}
bool A2DP_IsSinkCodecValid(const uint8_t* p_codec_info) {
  inc_func_call_count(__func__);
  return false;
}
bool A2DP_IsSourceCodecValid(const uint8_t* p_codec_info) {
  inc_func_call_count(__func__);
  return false;
}
bool A2DP_UsesRtpHeader(bool content_protection_enabled,
                        const uint8_t* p_codec_info) {
  inc_func_call_count(__func__);
  return false;
}
bool A2dpCodecConfig::codecConfigIsValid(
    const btav_a2dp_codec_config_t& codec_config) {
  inc_func_call_count(__func__);
  return false;
}
bool A2dpCodecConfig::copyOutOtaCodecConfig(uint8_t* p_codec_info) {
  inc_func_call_count(__func__);
  return false;
}
bool A2dpCodecConfig::getCodecSpecificConfig(tBT_A2DP_OFFLOAD* p_a2dp_offload) {
  inc_func_call_count(__func__);
  return false;
}
bool A2dpCodecConfig::isCodecConfigEmpty(
    const btav_a2dp_codec_config_t& codec_config) {
  inc_func_call_count(__func__);
  return false;
}
bool A2dpCodecConfig::isValid() const {
  inc_func_call_count(__func__);
  return false;
}
bool A2dpCodecConfig::setCodecUserConfig(
    const btav_a2dp_codec_config_t& codec_user_config,
    const btav_a2dp_codec_config_t& codec_audio_config,
    const tA2DP_ENCODER_INIT_PEER_PARAMS* p_peer_params,
    const uint8_t* p_peer_codec_info, bool is_capability,
    uint8_t* p_result_codec_config, bool* p_restart_input,
    bool* p_restart_output, bool* p_config_updated) {
  inc_func_call_count(__func__);
  return false;
}
bool A2dpCodecs::getCodecConfigAndCapabilities(
    btav_a2dp_codec_config_t* p_codec_config,
    std::vector<btav_a2dp_codec_config_t>* p_codecs_local_capabilities,
    std::vector<btav_a2dp_codec_config_t>* p_codecs_selectable_capabilities) {
  inc_func_call_count(__func__);
  return false;
}
bool A2dpCodecs::init() {
  inc_func_call_count(__func__);
  return false;
}
bool A2dpCodecs::isSupportedCodec(btav_a2dp_codec_index_t codec_index) {
  inc_func_call_count(__func__);
  return false;
}
bool A2dpCodecs::setCodecAudioConfig(
    const btav_a2dp_codec_config_t& codec_audio_config,
    const tA2DP_ENCODER_INIT_PEER_PARAMS* p_peer_params,
    const uint8_t* p_peer_sink_capabilities, uint8_t* p_result_codec_config,
    bool* p_restart_output, bool* p_config_updated) {
  inc_func_call_count(__func__);
  return false;
}
bool A2dpCodecs::setCodecConfig(const uint8_t* p_peer_codec_info,
                                bool is_capability,
                                uint8_t* p_result_codec_config,
                                bool select_current_codec) {
  inc_func_call_count(__func__);
  return false;
}
bool A2dpCodecs::setCodecOtaConfig(
    const uint8_t* p_ota_codec_config,
    const tA2DP_ENCODER_INIT_PEER_PARAMS* p_peer_params,
    uint8_t* p_result_codec_config, bool* p_restart_input,
    bool* p_restart_output, bool* p_config_updated) {
  inc_func_call_count(__func__);
  return false;
}
bool A2dpCodecs::setCodecUserConfig(
    const btav_a2dp_codec_config_t& codec_user_config,
    const tA2DP_ENCODER_INIT_PEER_PARAMS* p_peer_params,
    const uint8_t* p_peer_sink_capabilities, uint8_t* p_result_codec_config,
    bool* p_restart_input, bool* p_restart_output, bool* p_config_updated) {
  inc_func_call_count(__func__);
  return false;
}
bool A2dpCodecs::setPeerSinkCodecCapabilities(
    const uint8_t* p_peer_codec_capabilities) {
  inc_func_call_count(__func__);
  return false;
}
bool A2dpCodecs::setPeerSourceCodecCapabilities(
    const uint8_t* p_peer_codec_capabilities) {
  inc_func_call_count(__func__);
  return false;
}
bool A2dpCodecs::setSinkCodecConfig(const uint8_t* p_peer_codec_info,
                                    bool is_capability,
                                    uint8_t* p_result_codec_config,
                                    bool select_current_codec) {
  inc_func_call_count(__func__);
  return false;
}
btav_a2dp_codec_config_t A2dpCodecConfig::getCodecAudioConfig() {
  inc_func_call_count(__func__);
  btav_a2dp_codec_config_t config;
  return config;
}
btav_a2dp_codec_config_t A2dpCodecConfig::getCodecCapability() {
  inc_func_call_count(__func__);
  btav_a2dp_codec_config_t config;
  return config;
}
btav_a2dp_codec_config_t A2dpCodecConfig::getCodecConfig() {
  inc_func_call_count(__func__);
  btav_a2dp_codec_config_t config;
  return config;
}
btav_a2dp_codec_config_t A2dpCodecConfig::getCodecLocalCapability() {
  inc_func_call_count(__func__);
  btav_a2dp_codec_config_t config;
  return config;
}
btav_a2dp_codec_config_t A2dpCodecConfig::getCodecSelectableCapability() {
  inc_func_call_count(__func__);
  btav_a2dp_codec_config_t config;
  return config;
}
btav_a2dp_codec_config_t A2dpCodecConfig::getCodecUserConfig() {
  inc_func_call_count(__func__);
  btav_a2dp_codec_config_t config;
  return config;
}
btav_a2dp_codec_index_t A2DP_SinkCodecIndex(const uint8_t* p_codec_info) {
  inc_func_call_count(__func__);
  return BTAV_A2DP_CODEC_INDEX_MAX;
}
btav_a2dp_codec_index_t A2DP_SourceCodecIndex(const uint8_t* p_codec_info) {
  inc_func_call_count(__func__);
  return BTAV_A2DP_CODEC_INDEX_MAX;
}
const char* A2DP_CodecIndexStr(btav_a2dp_codec_index_t codec_index) {
  inc_func_call_count(__func__);
  return nullptr;
}
const char* A2DP_CodecName(const uint8_t* p_codec_info) {
  inc_func_call_count(__func__);
  return nullptr;
}
const tA2DP_DECODER_INTERFACE* A2DP_GetDecoderInterface(
    const uint8_t* p_codec_info) {
  inc_func_call_count(__func__);
  return nullptr;
}
const tA2DP_ENCODER_INTERFACE* A2DP_GetEncoderInterface(
    const uint8_t* p_codec_info) {
  inc_func_call_count(__func__);
  return nullptr;
}
int A2DP_GetSinkTrackChannelType(const uint8_t* p_codec_info) {
  inc_func_call_count(__func__);
  return 0;
}
int A2DP_GetTrackBitsPerSample(const uint8_t* p_codec_info) {
  inc_func_call_count(__func__);
  return 0;
}
int A2DP_GetTrackChannelCount(const uint8_t* p_codec_info) {
  inc_func_call_count(__func__);
  return 0;
}
int A2DP_GetTrackSampleRate(const uint8_t* p_codec_info) {
  inc_func_call_count(__func__);
  return 0;
}
int A2dpCodecConfig::getTrackBitRate() const {
  inc_func_call_count(__func__);
  return 0;
}
std::string A2DP_CodecInfoString(const uint8_t* p_codec_info) {
  inc_func_call_count(__func__);
  return 0;
}
std::string A2dpCodecConfig::codecBitsPerSample2Str(
    btav_a2dp_codec_bits_per_sample_t codec_bits_per_sample) {
  inc_func_call_count(__func__);
  return 0;
}
std::string A2dpCodecConfig::codecChannelMode2Str(
    btav_a2dp_codec_channel_mode_t codec_channel_mode) {
  inc_func_call_count(__func__);
  return 0;
}
std::string A2dpCodecConfig::codecConfig2Str(
    const btav_a2dp_codec_config_t& codec_config) {
  inc_func_call_count(__func__);
  return 0;
}
std::string A2dpCodecConfig::codecSampleRate2Str(
    btav_a2dp_codec_sample_rate_t codec_sample_rate) {
  inc_func_call_count(__func__);
  return 0;
}
tA2DP_CODEC_TYPE A2DP_GetCodecType(const uint8_t* p_codec_info) {
  inc_func_call_count(__func__);
  return 0;
}
uint8_t A2DP_GetMediaType(const uint8_t* p_codec_info) {
  inc_func_call_count(__func__);
  return 0;
}
uint8_t A2dpCodecConfig::getAudioBitsPerSample() {
  inc_func_call_count(__func__);
  return 0;
}
void A2DP_InitDefaultCodec(uint8_t* p_codec_info) {
  inc_func_call_count(__func__);
}
void A2dpCodecConfig::debug_codec_dump(int fd) {
  inc_func_call_count(__func__);
}
void A2dpCodecConfig::setCodecPriority(
    btav_a2dp_codec_priority_t codec_priority) {
  inc_func_call_count(__func__);
}
void A2dpCodecConfig::setDefaultCodecPriority() {
  inc_func_call_count(__func__);
}
void A2dpCodecs::debug_codec_dump(int fd) { inc_func_call_count(__func__); }
