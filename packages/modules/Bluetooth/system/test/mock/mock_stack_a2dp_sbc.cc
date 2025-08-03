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
 *   Functions generated:34
 *
 *  mockcify.pl ver 0.5.0
 */
// Mock include file to share data between tests and mock
#include "test/mock/mock_stack_a2dp_sbc.h"

#include <cstdint>
#include <string>

#include "test/common/mock_functions.h"

// Original usings

// Mocked internal structures, if any

namespace test {
namespace mock {
namespace stack_a2dp_sbc {

// Function state capture and return values, if needed
struct A2DP_AdjustCodecSbc A2DP_AdjustCodecSbc;
struct A2DP_BuildCodecHeaderSbc A2DP_BuildCodecHeaderSbc;
struct A2DP_CodecEqualsSbc A2DP_CodecEqualsSbc;
struct A2DP_CodecIndexStrSbc A2DP_CodecIndexStrSbc;
struct A2DP_CodecIndexStrSbcSink A2DP_CodecIndexStrSbcSink;
struct A2DP_CodecInfoStringSbc A2DP_CodecInfoStringSbc;
struct A2DP_CodecNameSbc A2DP_CodecNameSbc;
struct A2DP_CodecTypeEqualsSbc A2DP_CodecTypeEqualsSbc;
struct A2DP_GetAllocationMethodCodeSbc A2DP_GetAllocationMethodCodeSbc;
struct A2DP_GetBitrateSbc A2DP_GetBitrateSbc;
struct A2DP_GetChannelModeCodeSbc A2DP_GetChannelModeCodeSbc;
struct A2DP_GetDecoderInterfaceSbc A2DP_GetDecoderInterfaceSbc;
struct A2DP_GetEncoderInterfaceSbc A2DP_GetEncoderInterfaceSbc;
struct A2DP_GetMaxBitpoolSbc A2DP_GetMaxBitpoolSbc;
struct A2DP_GetMinBitpoolSbc A2DP_GetMinBitpoolSbc;
struct A2DP_GetNumberOfBlocksSbc A2DP_GetNumberOfBlocksSbc;
struct A2DP_GetNumberOfSubbandsSbc A2DP_GetNumberOfSubbandsSbc;
struct A2DP_GetPacketTimestampSbc A2DP_GetPacketTimestampSbc;
struct A2DP_GetSamplingFrequencyCodeSbc A2DP_GetSamplingFrequencyCodeSbc;
struct A2DP_GetSinkTrackChannelTypeSbc A2DP_GetSinkTrackChannelTypeSbc;
struct A2DP_GetTrackBitsPerSampleSbc A2DP_GetTrackBitsPerSampleSbc;
struct A2DP_GetTrackChannelCountSbc A2DP_GetTrackChannelCountSbc;
struct A2DP_GetTrackSampleRateSbc A2DP_GetTrackSampleRateSbc;
struct A2DP_InitCodecConfigSbc A2DP_InitCodecConfigSbc;
struct A2DP_InitCodecConfigSbcSink A2DP_InitCodecConfigSbcSink;
struct A2DP_InitDefaultCodecSbc A2DP_InitDefaultCodecSbc;
struct A2DP_IsPeerSinkCodecValidSbc A2DP_IsPeerSinkCodecValidSbc;
struct A2DP_IsPeerSourceCodecSupportedSbc A2DP_IsPeerSourceCodecSupportedSbc;
struct A2DP_IsPeerSourceCodecValidSbc A2DP_IsPeerSourceCodecValidSbc;
struct A2DP_IsSinkCodecSupportedSbc A2DP_IsSinkCodecSupportedSbc;
struct A2DP_IsSinkCodecValidSbc A2DP_IsSinkCodecValidSbc;
struct A2DP_IsSourceCodecValidSbc A2DP_IsSourceCodecValidSbc;
struct A2DP_SinkCodecIndexSbc A2DP_SinkCodecIndexSbc;
struct A2DP_SourceCodecIndexSbc A2DP_SourceCodecIndexSbc;

}  // namespace stack_a2dp_sbc
}  // namespace mock
}  // namespace test

// Mocked function return values, if any
namespace test {
namespace mock {
namespace stack_a2dp_sbc {

bool A2DP_AdjustCodecSbc::return_value = false;
bool A2DP_BuildCodecHeaderSbc::return_value = false;
bool A2DP_CodecEqualsSbc::return_value = false;
const char* A2DP_CodecIndexStrSbc::return_value = nullptr;
const char* A2DP_CodecIndexStrSbcSink::return_value = nullptr;
std::string A2DP_CodecInfoStringSbc::return_value = std::string();
const char* A2DP_CodecNameSbc::return_value = nullptr;
bool A2DP_CodecTypeEqualsSbc::return_value = false;
int A2DP_GetAllocationMethodCodeSbc::return_value = 0;
uint32_t A2DP_GetBitrateSbc::return_value = 0;
int A2DP_GetChannelModeCodeSbc::return_value = 0;
const tA2DP_DECODER_INTERFACE* A2DP_GetDecoderInterfaceSbc::return_value =
    nullptr;
const tA2DP_ENCODER_INTERFACE* A2DP_GetEncoderInterfaceSbc::return_value =
    nullptr;
int A2DP_GetMaxBitpoolSbc::return_value = 0;
int A2DP_GetMinBitpoolSbc::return_value = 0;
int A2DP_GetNumberOfBlocksSbc::return_value = 0;
int A2DP_GetNumberOfSubbandsSbc::return_value = 0;
bool A2DP_GetPacketTimestampSbc::return_value = false;
int A2DP_GetSamplingFrequencyCodeSbc::return_value = 0;
int A2DP_GetSinkTrackChannelTypeSbc::return_value = 0;
int A2DP_GetTrackBitsPerSampleSbc::return_value = 0;
int A2DP_GetTrackChannelCountSbc::return_value = 0;
int A2DP_GetTrackSampleRateSbc::return_value = 0;
bool A2DP_InitCodecConfigSbc::return_value = false;
bool A2DP_InitCodecConfigSbcSink::return_value = false;
bool A2DP_IsPeerSinkCodecValidSbc::return_value = false;
bool A2DP_IsPeerSourceCodecSupportedSbc::return_value = false;
bool A2DP_IsPeerSourceCodecValidSbc::return_value = false;
bool A2DP_IsSinkCodecSupportedSbc::return_value = false;
bool A2DP_IsSinkCodecValidSbc::return_value = false;
bool A2DP_IsSourceCodecValidSbc::return_value = false;
btav_a2dp_codec_index_t A2DP_SinkCodecIndexSbc::return_value =
    BTAV_A2DP_CODEC_INDEX_SOURCE_MIN;
btav_a2dp_codec_index_t A2DP_SourceCodecIndexSbc::return_value =
    BTAV_A2DP_CODEC_INDEX_SOURCE_MIN;

}  // namespace stack_a2dp_sbc
}  // namespace mock
}  // namespace test

// Mocked functions, if any
bool A2DP_AdjustCodecSbc(uint8_t* p_codec_info) {
  inc_func_call_count(__func__);
  return test::mock::stack_a2dp_sbc::A2DP_AdjustCodecSbc(p_codec_info);
}
bool A2DP_BuildCodecHeaderSbc(const uint8_t* p_codec_info, BT_HDR* p_buf,
                              uint16_t frames_per_packet) {
  inc_func_call_count(__func__);
  return test::mock::stack_a2dp_sbc::A2DP_BuildCodecHeaderSbc(
      p_codec_info, p_buf, frames_per_packet);
}
bool A2DP_CodecEqualsSbc(const uint8_t* p_codec_info_a,
                         const uint8_t* p_codec_info_b) {
  inc_func_call_count(__func__);
  return test::mock::stack_a2dp_sbc::A2DP_CodecEqualsSbc(p_codec_info_a,
                                                         p_codec_info_b);
}
const char* A2DP_CodecIndexStrSbc(void) {
  inc_func_call_count(__func__);
  return test::mock::stack_a2dp_sbc::A2DP_CodecIndexStrSbc();
}
const char* A2DP_CodecIndexStrSbcSink(void) {
  inc_func_call_count(__func__);
  return test::mock::stack_a2dp_sbc::A2DP_CodecIndexStrSbcSink();
}
std::string A2DP_CodecInfoStringSbc(const uint8_t* p_codec_info) {
  inc_func_call_count(__func__);
  return test::mock::stack_a2dp_sbc::A2DP_CodecInfoStringSbc(p_codec_info);
}
const char* A2DP_CodecNameSbc(const uint8_t* p_codec_info) {
  inc_func_call_count(__func__);
  return test::mock::stack_a2dp_sbc::A2DP_CodecNameSbc(p_codec_info);
}
bool A2DP_CodecTypeEqualsSbc(const uint8_t* p_codec_info_a,
                             const uint8_t* p_codec_info_b) {
  inc_func_call_count(__func__);
  return test::mock::stack_a2dp_sbc::A2DP_CodecTypeEqualsSbc(p_codec_info_a,
                                                             p_codec_info_b);
}
int A2DP_GetAllocationMethodCodeSbc(const uint8_t* p_codec_info) {
  inc_func_call_count(__func__);
  return test::mock::stack_a2dp_sbc::A2DP_GetAllocationMethodCodeSbc(
      p_codec_info);
}
uint32_t A2DP_GetBitrateSbc() {
  inc_func_call_count(__func__);
  return test::mock::stack_a2dp_sbc::A2DP_GetBitrateSbc();
}
int A2DP_GetChannelModeCodeSbc(const uint8_t* p_codec_info) {
  inc_func_call_count(__func__);
  return test::mock::stack_a2dp_sbc::A2DP_GetChannelModeCodeSbc(p_codec_info);
}
const tA2DP_DECODER_INTERFACE* A2DP_GetDecoderInterfaceSbc(
    const uint8_t* p_codec_info) {
  inc_func_call_count(__func__);
  return test::mock::stack_a2dp_sbc::A2DP_GetDecoderInterfaceSbc(p_codec_info);
}
const tA2DP_ENCODER_INTERFACE* A2DP_GetEncoderInterfaceSbc(
    const uint8_t* p_codec_info) {
  inc_func_call_count(__func__);
  return test::mock::stack_a2dp_sbc::A2DP_GetEncoderInterfaceSbc(p_codec_info);
}
int A2DP_GetMaxBitpoolSbc(const uint8_t* p_codec_info) {
  inc_func_call_count(__func__);
  return test::mock::stack_a2dp_sbc::A2DP_GetMaxBitpoolSbc(p_codec_info);
}
int A2DP_GetMinBitpoolSbc(const uint8_t* p_codec_info) {
  inc_func_call_count(__func__);
  return test::mock::stack_a2dp_sbc::A2DP_GetMinBitpoolSbc(p_codec_info);
}
int A2DP_GetNumberOfBlocksSbc(const uint8_t* p_codec_info) {
  inc_func_call_count(__func__);
  return test::mock::stack_a2dp_sbc::A2DP_GetNumberOfBlocksSbc(p_codec_info);
}
int A2DP_GetNumberOfSubbandsSbc(const uint8_t* p_codec_info) {
  inc_func_call_count(__func__);
  return test::mock::stack_a2dp_sbc::A2DP_GetNumberOfSubbandsSbc(p_codec_info);
}
bool A2DP_GetPacketTimestampSbc(const uint8_t* p_codec_info,
                                const uint8_t* p_data, uint32_t* p_timestamp) {
  inc_func_call_count(__func__);
  return test::mock::stack_a2dp_sbc::A2DP_GetPacketTimestampSbc(
      p_codec_info, p_data, p_timestamp);
}
int A2DP_GetSamplingFrequencyCodeSbc(const uint8_t* p_codec_info) {
  inc_func_call_count(__func__);
  return test::mock::stack_a2dp_sbc::A2DP_GetSamplingFrequencyCodeSbc(
      p_codec_info);
}
int A2DP_GetSinkTrackChannelTypeSbc(const uint8_t* p_codec_info) {
  inc_func_call_count(__func__);
  return test::mock::stack_a2dp_sbc::A2DP_GetSinkTrackChannelTypeSbc(
      p_codec_info);
}
int A2DP_GetTrackBitsPerSampleSbc(const uint8_t* p_codec_info) {
  inc_func_call_count(__func__);
  return test::mock::stack_a2dp_sbc::A2DP_GetTrackBitsPerSampleSbc(
      p_codec_info);
}
int A2DP_GetTrackChannelCountSbc(const uint8_t* p_codec_info) {
  inc_func_call_count(__func__);
  return test::mock::stack_a2dp_sbc::A2DP_GetTrackChannelCountSbc(p_codec_info);
}
int A2DP_GetTrackSampleRateSbc(const uint8_t* p_codec_info) {
  inc_func_call_count(__func__);
  return test::mock::stack_a2dp_sbc::A2DP_GetTrackSampleRateSbc(p_codec_info);
}
bool A2DP_InitCodecConfigSbc(AvdtpSepConfig* p_cfg) {
  inc_func_call_count(__func__);
  return test::mock::stack_a2dp_sbc::A2DP_InitCodecConfigSbc(p_cfg);
}
bool A2DP_InitCodecConfigSbcSink(AvdtpSepConfig* p_cfg) {
  inc_func_call_count(__func__);
  return test::mock::stack_a2dp_sbc::A2DP_InitCodecConfigSbcSink(p_cfg);
}
void A2DP_InitDefaultCodecSbc(uint8_t* p_codec_info) {
  inc_func_call_count(__func__);
  test::mock::stack_a2dp_sbc::A2DP_InitDefaultCodecSbc(p_codec_info);
}
bool A2DP_IsPeerSinkCodecValidSbc(const uint8_t* p_codec_info) {
  inc_func_call_count(__func__);
  return test::mock::stack_a2dp_sbc::A2DP_IsPeerSinkCodecValidSbc(p_codec_info);
}
bool A2DP_IsPeerSourceCodecSupportedSbc(const uint8_t* p_codec_info) {
  inc_func_call_count(__func__);
  return test::mock::stack_a2dp_sbc::A2DP_IsPeerSourceCodecSupportedSbc(
      p_codec_info);
}
bool A2DP_IsPeerSourceCodecValidSbc(const uint8_t* p_codec_info) {
  inc_func_call_count(__func__);
  return test::mock::stack_a2dp_sbc::A2DP_IsPeerSourceCodecValidSbc(
      p_codec_info);
}
bool A2DP_IsSinkCodecSupportedSbc(const uint8_t* p_codec_info) {
  inc_func_call_count(__func__);
  return test::mock::stack_a2dp_sbc::A2DP_IsSinkCodecSupportedSbc(p_codec_info);
}
bool A2DP_IsSinkCodecValidSbc(const uint8_t* p_codec_info) {
  inc_func_call_count(__func__);
  return test::mock::stack_a2dp_sbc::A2DP_IsSinkCodecValidSbc(p_codec_info);
}
bool A2DP_IsSourceCodecValidSbc(const uint8_t* p_codec_info) {
  inc_func_call_count(__func__);
  return test::mock::stack_a2dp_sbc::A2DP_IsSourceCodecValidSbc(p_codec_info);
}
btav_a2dp_codec_index_t A2DP_SinkCodecIndexSbc(const uint8_t* p_codec_info) {
  inc_func_call_count(__func__);
  return test::mock::stack_a2dp_sbc::A2DP_SinkCodecIndexSbc(p_codec_info);
}
btav_a2dp_codec_index_t A2DP_SourceCodecIndexSbc(const uint8_t* p_codec_info) {
  inc_func_call_count(__func__);
  return test::mock::stack_a2dp_sbc::A2DP_SourceCodecIndexSbc(p_codec_info);
}
// Mocked functions complete
// END mockcify generation
