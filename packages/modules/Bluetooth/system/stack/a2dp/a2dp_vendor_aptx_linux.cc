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

// Stubbed non-standard codec

#include <cstdint>

#include "a2dp_vendor_aptx.h"

bool A2DP_IsVendorSourceCodecValidAptx(const uint8_t* p_codec_info) {
  return false;
}

bool A2DP_IsVendorPeerSinkCodecValidAptx(const uint8_t* p_codec_info) {
  return false;
}

bool A2DP_VendorUsesRtpHeaderAptx(bool content_protection_enabled,
                                  const uint8_t* p_codec_info) {
  return false;
}

const char* A2DP_VendorCodecNameAptx(const uint8_t* p_codec_info) {
  return "Aptx";
}

bool A2DP_VendorCodecTypeEqualsAptx(const uint8_t* p_codec_info_a,
                                    const uint8_t* p_codec_info_b) {
  return false;
}

bool A2DP_VendorCodecEqualsAptx(const uint8_t* p_codec_info_a,
                                const uint8_t* p_codec_info_b) {
  return false;
}

int A2DP_VendorGetBitRateAptx(const uint8_t* p_codec_info) { return -1; }

int A2DP_VendorGetTrackSampleRateAptx(const uint8_t* p_codec_info) {
  return -1;
}

int A2DP_VendorGetTrackBitsPerSampleAptx(const uint8_t* p_codec_info) {
  return -1;
}

int A2DP_VendorGetTrackChannelCountAptx(const uint8_t* p_codec_info) {
  return -1;
}

int A2DP_VendorGetSinkTrackChannelTypeAptx(const uint8_t* p_codec_info) {
  return -1;
}

int A2DP_VendorGetChannelModeCodeAptx(const uint8_t* p_codec_info) {
  return -1;
}

int A2DP_VendorGetFrameSizeAptx(const uint8_t* p_codec_info) { return -1; }

bool A2DP_VendorGetPacketTimestampAptx(const uint8_t* p_codec_info,
                                       const uint8_t* p_data,
                                       uint32_t* p_timestamp) {
  return false;
}

bool A2DP_VendorBuildCodecHeaderAptx(const uint8_t* p_codec_info, BT_HDR* p_buf,
                                     uint16_t frames_per_packet) {
  return false;
}

std::string A2DP_VendorCodecInfoStringAptx(const uint8_t* p_codec_info) {
  return "Unsupported codec: Aptx";
}

const tA2DP_ENCODER_INTERFACE* A2DP_VendorGetEncoderInterfaceAptx(
    const uint8_t* p_codec_info) {
  return nullptr;
}

const tA2DP_DECODER_INTERFACE* A2DP_VendorGetDecoderInterfaceAptx(
    const uint8_t* p_codec_info) {
  return nullptr;
}

bool A2DP_VendorAdjustCodecAptx(uint8_t* p_codec_info) { return false; }

btav_a2dp_codec_index_t A2DP_VendorSourceCodecIndexAptx(
    const uint8_t* p_codec_info) {
  return BTAV_A2DP_CODEC_INDEX_MAX;
}

btav_a2dp_codec_index_t A2DP_VendorSinkCodecIndexAptx(
    const uint8_t* p_codec_info) {
  return BTAV_A2DP_CODEC_INDEX_MAX;
}

const char* A2DP_VendorCodecIndexStrAptx(void) { return "Aptx"; }

bool A2DP_VendorInitCodecConfigAptx(AvdtpSepConfig* p_cfg) { return false; }

A2dpCodecConfigAptx::A2dpCodecConfigAptx(
    btav_a2dp_codec_priority_t codec_priority)
    : A2dpCodecConfig(BTAV_A2DP_CODEC_INDEX_SOURCE_APTX, A2DP_CODEC_ID_APTX,
                      A2DP_VendorCodecIndexStrAptx(), codec_priority) {}

A2dpCodecConfigAptx::~A2dpCodecConfigAptx() {}

bool A2dpCodecConfigAptx::init() { return false; }

bool A2dpCodecConfigAptx::useRtpHeaderMarkerBit() const { return false; }

void A2dpCodecConfigAptx::debug_codec_dump(int fd) {}

bool A2dpCodecConfigAptx::setCodecConfig(const uint8_t* p_peer_codec_info,
                                         bool is_capability,
                                         uint8_t* p_result_codec_config) {
  return false;
}

bool A2dpCodecConfigAptx::setPeerCodecCapabilities(
    const uint8_t* p_peer_codec_capabilities) {
  return false;
}
