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

// Stubbed non-standard codec.

#include "a2dp_vendor.h"
#include "a2dp_vendor_opus.h"

bool A2DP_IsVendorSourceCodecValidOpus(const uint8_t* p_codec_info) {
  return false;
}

bool A2DP_IsVendorSinkCodecValidOpus(const uint8_t* p_codec_info) {
  return false;
}

bool A2DP_IsVendorPeerSourceCodecValidOpus(const uint8_t* p_codec_info) {
  return false;
}

bool A2DP_IsVendorPeerSinkCodecValidOpus(const uint8_t* p_codec_info) {
  return false;
}

bool A2DP_IsVendorSinkCodecSupportedOpus(const uint8_t* p_codec_info) {
  return false;
}
bool A2DP_IsPeerSourceCodecSupportedOpus(const uint8_t* p_codec_info) {
  return false;
}

bool A2DP_VendorUsesRtpHeaderOpus(bool content_protection_enabled,
                                  const uint8_t* p_codec_info) {
  return false;
}

const char* A2DP_VendorCodecNameOpus(const uint8_t* p_codec_info) {
  return "Opus";
}

bool A2DP_VendorCodecTypeEqualsOpus(const uint8_t* p_codec_info_a,
                                    const uint8_t* p_codec_info_b) {
  return false;
}

bool A2DP_VendorCodecEqualsOpus(const uint8_t* p_codec_info_a,
                                const uint8_t* p_codec_info_b) {
  return false;
}

int A2DP_VendorGetBitRateOpus(const uint8_t* p_codec_info) { return -1; }

int A2DP_VendorGetTrackSampleRateOpus(const uint8_t* p_codec_info) {
  return -1;
}

int A2DP_VendorGetTrackBitsPerSampleOpus(const uint8_t* p_codec_info) {
  return -1;
}

int A2DP_VendorGetTrackChannelCountOpus(const uint8_t* p_codec_info) {
  return -1;
}

int A2DP_VendorGetSinkTrackChannelTypeOpus(const uint8_t* p_codec_info) {
  return -1;
}

int A2DP_VendorGetChannelModeCodeOpus(const uint8_t* p_codec_info) {
  return -1;
}

int A2DP_VendorGetFrameSizeOpus(const uint8_t* p_codec_info) { return -1; }

bool A2DP_VendorGetPacketTimestampOpus(const uint8_t* p_codec_info,
                                       const uint8_t* p_data,
                                       uint32_t* p_timestamp) {
  return false;
}

bool A2DP_VendorBuildCodecHeaderOpus(const uint8_t* p_codec_info, BT_HDR* p_buf,
                                     uint16_t frames_per_packet) {
  return false;
}

std::string A2DP_VendorCodecInfoStringOpus(const uint8_t* p_codec_info) {
  return "Unsupported codec: Opus";
}

const tA2DP_ENCODER_INTERFACE* A2DP_VendorGetEncoderInterfaceOpus(
    const uint8_t* p_codec_info) {
  return nullptr;
}

const tA2DP_DECODER_INTERFACE* A2DP_VendorGetDecoderInterfaceOpus(
    const uint8_t* p_codec_info) {
  return nullptr;
}

bool A2DP_VendorAdjustCodecOpus(uint8_t* p_codec_info) { return false; }

btav_a2dp_codec_index_t A2DP_VendorSourceCodecIndexOpus(
    const uint8_t* p_codec_info) {
  return BTAV_A2DP_CODEC_INDEX_MAX;
}

btav_a2dp_codec_index_t A2DP_VendorSinkCodecIndexOpus(
    const uint8_t* p_codec_info) {
  return BTAV_A2DP_CODEC_INDEX_MAX;
}

const char* A2DP_VendorCodecIndexStrOpus(void) { return "Opus"; }

const char* A2DP_VendorCodecIndexStrOpusSink(void) { return "Opus SINK"; }

bool A2DP_VendorInitCodecConfigOpus(AvdtpSepConfig* p_cfg) { return false; }

bool A2DP_VendorInitCodecConfigOpusSink(AvdtpSepConfig* p_cfg) { return false; }

A2dpCodecConfigOpusSource::A2dpCodecConfigOpusSource(
    btav_a2dp_codec_priority_t codec_priority)
    : A2dpCodecConfigOpusBase(BTAV_A2DP_CODEC_INDEX_SOURCE_OPUS,
                              A2DP_VendorCodecIndexStrOpus(), codec_priority,
                              true) {}

A2dpCodecConfigOpusSource::~A2dpCodecConfigOpusSource() {}

bool A2dpCodecConfigOpusSource::init() { return false; }

bool A2dpCodecConfigOpusSource::useRtpHeaderMarkerBit() const { return false; }

void A2dpCodecConfigOpusSource::debug_codec_dump(int fd) {}

bool A2dpCodecConfigOpusBase::setCodecConfig(const uint8_t* p_peer_codec_info,
                                             bool is_capability,
                                             uint8_t* p_result_codec_config) {
  return false;
}

bool A2dpCodecConfigOpusBase::setPeerCodecCapabilities(
    const uint8_t* p_peer_codec_capabilities) {
  return false;
}

A2dpCodecConfigOpusSink::A2dpCodecConfigOpusSink(
    btav_a2dp_codec_priority_t codec_priority)
    : A2dpCodecConfigOpusBase(BTAV_A2DP_CODEC_INDEX_SINK_OPUS,
                              A2DP_VendorCodecIndexStrOpusSink(),
                              codec_priority, false) {}

A2dpCodecConfigOpusSink::~A2dpCodecConfigOpusSink() {}

bool A2dpCodecConfigOpusSink::init() { return false; }

bool A2dpCodecConfigOpusSink::useRtpHeaderMarkerBit() const { return false; }

bool A2dpCodecConfigOpusSink::updateEncoderUserConfig(
    const tA2DP_ENCODER_INIT_PEER_PARAMS* p_peer_params, bool* p_restart_input,
    bool* p_restart_output, bool* p_config_updated) {
  return false;
}
