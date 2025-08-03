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
 *   Functions generated:34
 *
 *  mockcify.pl ver 0.5.0
 */

#include <cstdint>
#include <functional>
#include <string>

// Original included files, if any
#include "stack/include/a2dp_codec_api.h"
#include "stack/include/bt_hdr.h"

// Original usings

// Mocked compile conditionals, if any

namespace test {
namespace mock {
namespace stack_a2dp_sbc {

// Shared state between mocked functions and tests
// Name: A2DP_AdjustCodecSbc
// Params: uint8_t* p_codec_info
// Return: bool
struct A2DP_AdjustCodecSbc {
  static bool return_value;
  std::function<bool(uint8_t* p_codec_info)> body{
      [](uint8_t* /* p_codec_info */) { return return_value; }};
  bool operator()(uint8_t* p_codec_info) { return body(p_codec_info); };
};
extern struct A2DP_AdjustCodecSbc A2DP_AdjustCodecSbc;

// Name: A2DP_BuildCodecHeaderSbc
// Params:  const uint8_t* p_codec_info, BT_HDR* p_buf, uint16_t
// frames_per_packet Return: bool
struct A2DP_BuildCodecHeaderSbc {
  static bool return_value;
  std::function<bool(const uint8_t* p_codec_info, BT_HDR* p_buf,
                     uint16_t frames_per_packet)>
      body{[](const uint8_t* /* p_codec_info */, BT_HDR* /* p_buf */,
              uint16_t /* frames_per_packet */) { return return_value; }};
  bool operator()(const uint8_t* p_codec_info, BT_HDR* p_buf,
                  uint16_t frames_per_packet) {
    return body(p_codec_info, p_buf, frames_per_packet);
  };
};
extern struct A2DP_BuildCodecHeaderSbc A2DP_BuildCodecHeaderSbc;

// Name: A2DP_CodecEqualsSbc
// Params: const uint8_t* p_codec_info_a, const uint8_t* p_codec_info_b
// Return: bool
struct A2DP_CodecEqualsSbc {
  static bool return_value;
  std::function<bool(const uint8_t* p_codec_info_a,
                     const uint8_t* p_codec_info_b)>
      body{[](const uint8_t* /* p_codec_info_a */,
              const uint8_t* /* p_codec_info_b */) { return return_value; }};
  bool operator()(const uint8_t* p_codec_info_a,
                  const uint8_t* p_codec_info_b) {
    return body(p_codec_info_a, p_codec_info_b);
  };
};
extern struct A2DP_CodecEqualsSbc A2DP_CodecEqualsSbc;

// Name: A2DP_CodecIndexStrSbc
// Params: void
// Return: const char*
struct A2DP_CodecIndexStrSbc {
  static const char* return_value;
  std::function<const char*(void)> body{[](void) { return return_value; }};
  const char* operator()(void) { return body(); };
};
extern struct A2DP_CodecIndexStrSbc A2DP_CodecIndexStrSbc;

// Name: A2DP_CodecIndexStrSbcSink
// Params: void
// Return: const char*
struct A2DP_CodecIndexStrSbcSink {
  static const char* return_value;
  std::function<const char*(void)> body{[](void) { return return_value; }};
  const char* operator()(void) { return body(); };
};
extern struct A2DP_CodecIndexStrSbcSink A2DP_CodecIndexStrSbcSink;

// Name: A2DP_CodecInfoStringSbc
// Params: const uint8_t* p_codec_info
// Return: std::string
struct A2DP_CodecInfoStringSbc {
  static std::string return_value;
  std::function<std::string(const uint8_t* p_codec_info)> body{
      [](const uint8_t* /* p_codec_info */) { return return_value; }};
  std::string operator()(const uint8_t* p_codec_info) {
    return body(p_codec_info);
  };
};
extern struct A2DP_CodecInfoStringSbc A2DP_CodecInfoStringSbc;

// Name: A2DP_CodecNameSbc
// Params:  const uint8_t* p_codec_info
// Return: const char*
struct A2DP_CodecNameSbc {
  static const char* return_value;
  std::function<const char*(const uint8_t* p_codec_info)> body{
      [](const uint8_t* /* p_codec_info */) { return return_value; }};
  const char* operator()(const uint8_t* p_codec_info) {
    return body(p_codec_info);
  };
};
extern struct A2DP_CodecNameSbc A2DP_CodecNameSbc;

// Name: A2DP_CodecTypeEqualsSbc
// Params: const uint8_t* p_codec_info_a, const uint8_t* p_codec_info_b
// Return: bool
struct A2DP_CodecTypeEqualsSbc {
  static bool return_value;
  std::function<bool(const uint8_t* p_codec_info_a,
                     const uint8_t* p_codec_info_b)>
      body{[](const uint8_t* /* p_codec_info_a */,
              const uint8_t* /* p_codec_info_b */) { return return_value; }};
  bool operator()(const uint8_t* p_codec_info_a,
                  const uint8_t* p_codec_info_b) {
    return body(p_codec_info_a, p_codec_info_b);
  };
};
extern struct A2DP_CodecTypeEqualsSbc A2DP_CodecTypeEqualsSbc;

// Name: A2DP_GetAllocationMethodCodeSbc
// Params: const uint8_t* p_codec_info
// Return: int
struct A2DP_GetAllocationMethodCodeSbc {
  static int return_value;
  std::function<int(const uint8_t* p_codec_info)> body{
      [](const uint8_t* /* p_codec_info */) { return return_value; }};
  int operator()(const uint8_t* p_codec_info) { return body(p_codec_info); };
};
extern struct A2DP_GetAllocationMethodCodeSbc A2DP_GetAllocationMethodCodeSbc;

// Name: A2DP_GetBitrateSbc
// Params:
// Return: uint32_t
struct A2DP_GetBitrateSbc {
  static uint32_t return_value;
  std::function<uint32_t()> body{[]() { return return_value; }};
  uint32_t operator()() { return body(); };
};
extern struct A2DP_GetBitrateSbc A2DP_GetBitrateSbc;

// Name: A2DP_GetChannelModeCodeSbc
// Params: const uint8_t* p_codec_info
// Return: int
struct A2DP_GetChannelModeCodeSbc {
  static int return_value;
  std::function<int(const uint8_t* p_codec_info)> body{
      [](const uint8_t* /* p_codec_info */) { return return_value; }};
  int operator()(const uint8_t* p_codec_info) { return body(p_codec_info); };
};
extern struct A2DP_GetChannelModeCodeSbc A2DP_GetChannelModeCodeSbc;

// Name: A2DP_GetDecoderInterfaceSbc
// Params: const uint8_t* p_codec_info
// Return: const tA2DP_DECODER_INTERFACE*
struct A2DP_GetDecoderInterfaceSbc {
  static const tA2DP_DECODER_INTERFACE* return_value;
  std::function<const tA2DP_DECODER_INTERFACE*(const uint8_t* p_codec_info)>
      body{[](const uint8_t* /* p_codec_info */) { return return_value; }};
  const tA2DP_DECODER_INTERFACE* operator()(const uint8_t* p_codec_info) {
    return body(p_codec_info);
  };
};
extern struct A2DP_GetDecoderInterfaceSbc A2DP_GetDecoderInterfaceSbc;

// Name: A2DP_GetEncoderInterfaceSbc
// Params: const uint8_t* p_codec_info
// Return: const tA2DP_ENCODER_INTERFACE*
struct A2DP_GetEncoderInterfaceSbc {
  static const tA2DP_ENCODER_INTERFACE* return_value;
  std::function<const tA2DP_ENCODER_INTERFACE*(const uint8_t* p_codec_info)>
      body{[](const uint8_t* /* p_codec_info */) { return return_value; }};
  const tA2DP_ENCODER_INTERFACE* operator()(const uint8_t* p_codec_info) {
    return body(p_codec_info);
  };
};
extern struct A2DP_GetEncoderInterfaceSbc A2DP_GetEncoderInterfaceSbc;

// Name: A2DP_GetMaxBitpoolSbc
// Params: const uint8_t* p_codec_info
// Return: int
struct A2DP_GetMaxBitpoolSbc {
  static int return_value;
  std::function<int(const uint8_t* p_codec_info)> body{
      [](const uint8_t* /* p_codec_info */) { return return_value; }};
  int operator()(const uint8_t* p_codec_info) { return body(p_codec_info); };
};
extern struct A2DP_GetMaxBitpoolSbc A2DP_GetMaxBitpoolSbc;

// Name: A2DP_GetMinBitpoolSbc
// Params: const uint8_t* p_codec_info
// Return: int
struct A2DP_GetMinBitpoolSbc {
  static int return_value;
  std::function<int(const uint8_t* p_codec_info)> body{
      [](const uint8_t* /* p_codec_info */) { return return_value; }};
  int operator()(const uint8_t* p_codec_info) { return body(p_codec_info); };
};
extern struct A2DP_GetMinBitpoolSbc A2DP_GetMinBitpoolSbc;

// Name: A2DP_GetNumberOfBlocksSbc
// Params: const uint8_t* p_codec_info
// Return: int
struct A2DP_GetNumberOfBlocksSbc {
  static int return_value;
  std::function<int(const uint8_t* p_codec_info)> body{
      [](const uint8_t* /* p_codec_info */) { return return_value; }};
  int operator()(const uint8_t* p_codec_info) { return body(p_codec_info); };
};
extern struct A2DP_GetNumberOfBlocksSbc A2DP_GetNumberOfBlocksSbc;

// Name: A2DP_GetNumberOfSubbandsSbc
// Params: const uint8_t* p_codec_info
// Return: int
struct A2DP_GetNumberOfSubbandsSbc {
  static int return_value;
  std::function<int(const uint8_t* p_codec_info)> body{
      [](const uint8_t* /* p_codec_info */) { return return_value; }};
  int operator()(const uint8_t* p_codec_info) { return body(p_codec_info); };
};
extern struct A2DP_GetNumberOfSubbandsSbc A2DP_GetNumberOfSubbandsSbc;

// Name: A2DP_GetPacketTimestampSbc
// Params:  const uint8_t* p_codec_info, const uint8_t* p_data, uint32_t*
// p_timestamp Return: bool
struct A2DP_GetPacketTimestampSbc {
  static bool return_value;
  std::function<bool(const uint8_t* p_codec_info, const uint8_t* p_data,
                     uint32_t* p_timestamp)>
      body{[](const uint8_t* /* p_codec_info */, const uint8_t* /* p_data */,
              uint32_t* /* p_timestamp */) { return return_value; }};
  bool operator()(const uint8_t* p_codec_info, const uint8_t* p_data,
                  uint32_t* p_timestamp) {
    return body(p_codec_info, p_data, p_timestamp);
  };
};
extern struct A2DP_GetPacketTimestampSbc A2DP_GetPacketTimestampSbc;

// Name: A2DP_GetSamplingFrequencyCodeSbc
// Params: const uint8_t* p_codec_info
// Return: int
struct A2DP_GetSamplingFrequencyCodeSbc {
  static int return_value;
  std::function<int(const uint8_t* p_codec_info)> body{
      [](const uint8_t* /* p_codec_info */) { return return_value; }};
  int operator()(const uint8_t* p_codec_info) { return body(p_codec_info); };
};
extern struct A2DP_GetSamplingFrequencyCodeSbc A2DP_GetSamplingFrequencyCodeSbc;

// Name: A2DP_GetSinkTrackChannelTypeSbc
// Params: const uint8_t* p_codec_info
// Return: int
struct A2DP_GetSinkTrackChannelTypeSbc {
  static int return_value;
  std::function<int(const uint8_t* p_codec_info)> body{
      [](const uint8_t* /* p_codec_info */) { return return_value; }};
  int operator()(const uint8_t* p_codec_info) { return body(p_codec_info); };
};
extern struct A2DP_GetSinkTrackChannelTypeSbc A2DP_GetSinkTrackChannelTypeSbc;

// Name: A2DP_GetTrackBitsPerSampleSbc
// Params: const uint8_t* p_codec_info
// Return: int
struct A2DP_GetTrackBitsPerSampleSbc {
  static int return_value;
  std::function<int(const uint8_t* p_codec_info)> body{
      [](const uint8_t* /* p_codec_info */) { return return_value; }};
  int operator()(const uint8_t* p_codec_info) { return body(p_codec_info); };
};
extern struct A2DP_GetTrackBitsPerSampleSbc A2DP_GetTrackBitsPerSampleSbc;

// Name: A2DP_GetTrackChannelCountSbc
// Params: const uint8_t* p_codec_info
// Return: int
struct A2DP_GetTrackChannelCountSbc {
  static int return_value;
  std::function<int(const uint8_t* p_codec_info)> body{
      [](const uint8_t* /* p_codec_info */) { return return_value; }};
  int operator()(const uint8_t* p_codec_info) { return body(p_codec_info); };
};
extern struct A2DP_GetTrackChannelCountSbc A2DP_GetTrackChannelCountSbc;

// Name: A2DP_GetTrackSampleRateSbc
// Params: const uint8_t* p_codec_info
// Return: int
struct A2DP_GetTrackSampleRateSbc {
  static int return_value;
  std::function<int(const uint8_t* p_codec_info)> body{
      [](const uint8_t* /* p_codec_info */) { return return_value; }};
  int operator()(const uint8_t* p_codec_info) { return body(p_codec_info); };
};
extern struct A2DP_GetTrackSampleRateSbc A2DP_GetTrackSampleRateSbc;

// Name: A2DP_InitCodecConfigSbc
// Params: AvdtpSepConfig* p_cfg
// Return: bool
struct A2DP_InitCodecConfigSbc {
  static bool return_value;
  std::function<bool(AvdtpSepConfig* p_cfg)> body{
      [](AvdtpSepConfig* /* p_cfg */) { return return_value; }};
  bool operator()(AvdtpSepConfig* p_cfg) { return body(p_cfg); };
};
extern struct A2DP_InitCodecConfigSbc A2DP_InitCodecConfigSbc;

// Name: A2DP_InitCodecConfigSbcSink
// Params: AvdtpSepConfig* p_cfg
// Return: bool
struct A2DP_InitCodecConfigSbcSink {
  static bool return_value;
  std::function<bool(AvdtpSepConfig* p_cfg)> body{
      [](AvdtpSepConfig* /* p_cfg */) { return return_value; }};
  bool operator()(AvdtpSepConfig* p_cfg) { return body(p_cfg); };
};
extern struct A2DP_InitCodecConfigSbcSink A2DP_InitCodecConfigSbcSink;

// Name: A2DP_InitDefaultCodecSbc
// Params: uint8_t* p_codec_info
// Return: void
struct A2DP_InitDefaultCodecSbc {
  std::function<void(uint8_t* p_codec_info)> body{
      [](uint8_t* /* p_codec_info */) {}};
  void operator()(uint8_t* p_codec_info) { body(p_codec_info); };
};
extern struct A2DP_InitDefaultCodecSbc A2DP_InitDefaultCodecSbc;

// Name: A2DP_IsPeerSinkCodecValidSbc
// Params: const uint8_t* p_codec_info
// Return: bool
struct A2DP_IsPeerSinkCodecValidSbc {
  static bool return_value;
  std::function<bool(const uint8_t* p_codec_info)> body{
      [](const uint8_t* /* p_codec_info */) { return return_value; }};
  bool operator()(const uint8_t* p_codec_info) { return body(p_codec_info); };
};
extern struct A2DP_IsPeerSinkCodecValidSbc A2DP_IsPeerSinkCodecValidSbc;

// Name: A2DP_IsPeerSourceCodecSupportedSbc
// Params: const uint8_t* p_codec_info
// Return: bool
struct A2DP_IsPeerSourceCodecSupportedSbc {
  static bool return_value;
  std::function<bool(const uint8_t* p_codec_info)> body{
      [](const uint8_t* /* p_codec_info */) { return return_value; }};
  bool operator()(const uint8_t* p_codec_info) { return body(p_codec_info); };
};
extern struct A2DP_IsPeerSourceCodecSupportedSbc
    A2DP_IsPeerSourceCodecSupportedSbc;

// Name: A2DP_IsPeerSourceCodecValidSbc
// Params: const uint8_t* p_codec_info
// Return: bool
struct A2DP_IsPeerSourceCodecValidSbc {
  static bool return_value;
  std::function<bool(const uint8_t* p_codec_info)> body{
      [](const uint8_t* /* p_codec_info */) { return return_value; }};
  bool operator()(const uint8_t* p_codec_info) { return body(p_codec_info); };
};
extern struct A2DP_IsPeerSourceCodecValidSbc A2DP_IsPeerSourceCodecValidSbc;

// Name: A2DP_IsSinkCodecSupportedSbc
// Params: const uint8_t* p_codec_info
// Return: bool
struct A2DP_IsSinkCodecSupportedSbc {
  static bool return_value;
  std::function<bool(const uint8_t* p_codec_info)> body{
      [](const uint8_t* /* p_codec_info */) { return return_value; }};
  bool operator()(const uint8_t* p_codec_info) { return body(p_codec_info); };
};
extern struct A2DP_IsSinkCodecSupportedSbc A2DP_IsSinkCodecSupportedSbc;

// Name: A2DP_IsSinkCodecValidSbc
// Params: const uint8_t* p_codec_info
// Return: bool
struct A2DP_IsSinkCodecValidSbc {
  static bool return_value;
  std::function<bool(const uint8_t* p_codec_info)> body{
      [](const uint8_t* /* p_codec_info */) { return return_value; }};
  bool operator()(const uint8_t* p_codec_info) { return body(p_codec_info); };
};
extern struct A2DP_IsSinkCodecValidSbc A2DP_IsSinkCodecValidSbc;

// Name: A2DP_IsSourceCodecValidSbc
// Params: const uint8_t* p_codec_info
// Return: bool
struct A2DP_IsSourceCodecValidSbc {
  static bool return_value;
  std::function<bool(const uint8_t* p_codec_info)> body{
      [](const uint8_t* /* p_codec_info */) { return return_value; }};
  bool operator()(const uint8_t* p_codec_info) { return body(p_codec_info); };
};
extern struct A2DP_IsSourceCodecValidSbc A2DP_IsSourceCodecValidSbc;

// Name: A2DP_SinkCodecIndexSbc
// Params:  const uint8_t* p_codec_info
// Return: btav_a2dp_codec_index_t
struct A2DP_SinkCodecIndexSbc {
  static btav_a2dp_codec_index_t return_value;
  std::function<btav_a2dp_codec_index_t(const uint8_t* p_codec_info)> body{
      [](const uint8_t* /* p_codec_info */) { return return_value; }};
  btav_a2dp_codec_index_t operator()(const uint8_t* p_codec_info) {
    return body(p_codec_info);
  };
};
extern struct A2DP_SinkCodecIndexSbc A2DP_SinkCodecIndexSbc;

// Name: A2DP_SourceCodecIndexSbc
// Params:  const uint8_t* p_codec_info
// Return: btav_a2dp_codec_index_t
struct A2DP_SourceCodecIndexSbc {
  static btav_a2dp_codec_index_t return_value;
  std::function<btav_a2dp_codec_index_t(const uint8_t* p_codec_info)> body{
      [](const uint8_t* /* p_codec_info */) { return return_value; }};
  btav_a2dp_codec_index_t operator()(const uint8_t* p_codec_info) {
    return body(p_codec_info);
  };
};
extern struct A2DP_SourceCodecIndexSbc A2DP_SourceCodecIndexSbc;

}  // namespace stack_a2dp_sbc
}  // namespace mock
}  // namespace test

// END mockcify generation
