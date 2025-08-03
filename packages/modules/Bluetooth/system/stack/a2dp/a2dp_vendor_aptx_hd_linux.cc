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

#include "a2dp_vendor_aptx_hd.h"

bool A2DP_IsVendorSourceCodecValidAptxHd(const uint8_t* p_codec_info) {
  return false;
}

bool A2DP_IsVendorPeerSinkCodecValidAptxHd(const uint8_t* p_codec_info) {
  return false;
}

bool A2DP_VendorUsesRtpHeaderAptxHd(bool content_protection_enabled,
                                    const uint8_t* p_codec_info) {
  return false;
}

const char* A2DP_VendorCodecNameAptxHd(const uint8_t* p_codec_info) {
  return "AptxHd";
}

bool A2DP_VendorCodecTypeEqualsAptxHd(const uint8_t* p_codec_info_a,
                                      const uint8_t* p_codec_info_b) {
  return false;
}

bool A2DP_VendorCodecEqualsAptxHd(const uint8_t* p_codec_info_a,
                                  const uint8_t* p_codec_info_b) {
  return false;
}

int A2DP_VendorGetBitRateAptxHd(const uint8_t* p_codec_info) { return -1; }

int A2DP_VendorGetTrackSampleRateAptxHd(const uint8_t* p_codec_info) {
  return -1;
}

int A2DP_VendorGetTrackBitsPerSampleAptxHd(const uint8_t* p_codec_info) {
  return -1;
}

int A2DP_VendorGetTrackChannelCountAptxHd(const uint8_t* p_codec_info) {
  return -1;
}

int A2DP_VendorGetSinkTrackChannelTypeAptxHd(const uint8_t* p_codec_info) {
  return -1;
}

int A2DP_VendorGetChannelModeCodeAptxHd(const uint8_t* p_codec_info) {
  return -1;
}

int A2DP_VendorGetFrameSizeAptxHd(const uint8_t* p_codec_info) { return -1; }

bool A2DP_VendorGetPacketTimestampAptxHd(const uint8_t* p_codec_info,
                                         const uint8_t* p_data,
                                         uint32_t* p_timestamp) {
  return false;
}

bool A2DP_VendorBuildCodecHeaderAptxHd(const uint8_t* p_codec_info,
                                       BT_HDR* p_buf,
                                       uint16_t frames_per_packet) {
  return false;
}

std::string A2DP_VendorCodecInfoStringAptxHd(const uint8_t* p_codec_info) {
  return "Unsupported codec: AptxHd";
}

const tA2DP_ENCODER_INTERFACE* A2DP_VendorGetEncoderInterfaceAptxHd(
    const uint8_t* p_codec_info) {
  return nullptr;
}

const tA2DP_DECODER_INTERFACE* A2DP_VendorGetDecoderInterfaceAptxHd(
    const uint8_t* p_codec_info) {
  return nullptr;
}

bool A2DP_VendorAdjustCodecAptxHd(uint8_t* p_codec_info) { return false; }

btav_a2dp_codec_index_t A2DP_VendorSourceCodecIndexAptxHd(
    const uint8_t* p_codec_info) {
  return BTAV_A2DP_CODEC_INDEX_MAX;
}

btav_a2dp_codec_index_t A2DP_VendorSinkCodecIndexAptxHd(
    const uint8_t* p_codec_info) {
  return BTAV_A2DP_CODEC_INDEX_MAX;
}

const char* A2DP_VendorCodecIndexStrAptxHd(void) { return "AptxHd"; }

bool A2DP_VendorInitCodecConfigAptxHd(AvdtpSepConfig* p_cfg) { return false; }

A2dpCodecConfigAptxHd::A2dpCodecConfigAptxHd(
    btav_a2dp_codec_priority_t codec_priority)
    : A2dpCodecConfig(BTAV_A2DP_CODEC_INDEX_SOURCE_APTX_HD,
                      A2DP_CODEC_ID_APTX_HD, A2DP_VendorCodecIndexStrAptxHd(),
                      codec_priority) {}

A2dpCodecConfigAptxHd::~A2dpCodecConfigAptxHd() {}

bool A2dpCodecConfigAptxHd::init() { return false; }

bool A2dpCodecConfigAptxHd::useRtpHeaderMarkerBit() const { return false; }

void A2dpCodecConfigAptxHd::debug_codec_dump(int fd) {}

bool A2dpCodecConfigAptxHd::setCodecConfig(const uint8_t* p_peer_codec_info,
                                           bool is_capability,
                                           uint8_t* p_result_codec_config) {
  return false;
}

bool A2dpCodecConfigAptxHd::setPeerCodecCapabilities(
    const uint8_t* p_peer_codec_capabilities) {
  return false;
}
