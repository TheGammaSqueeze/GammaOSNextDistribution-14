/*
 * Copyright 2020 The Android Open Source Project
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

#ifndef A2DP_CODEC_HELPERFUNCTIONS_H_
#define A2DP_CODEC_HELPERFUNCTIONS_H_

#include <fuzzer/FuzzedDataProvider.h>

#include <vector>

#include "stack/include/bt_hdr.h"

static const std::vector<const btav_a2dp_codec_index_t> CODEC_INDEX_ENUM_VALS =
    {BTAV_A2DP_CODEC_INDEX_SOURCE_MIN,
     BTAV_A2DP_CODEC_INDEX_SOURCE_SBC,
     BTAV_A2DP_CODEC_INDEX_SOURCE_AAC,
     BTAV_A2DP_CODEC_INDEX_SOURCE_APTX,
     BTAV_A2DP_CODEC_INDEX_SOURCE_APTX_HD,
     BTAV_A2DP_CODEC_INDEX_SOURCE_LDAC,
     BTAV_A2DP_CODEC_INDEX_SOURCE_MAX,
     BTAV_A2DP_CODEC_INDEX_SINK_MIN,
     BTAV_A2DP_CODEC_INDEX_SINK_SBC,
     BTAV_A2DP_CODEC_INDEX_SINK_AAC,
     BTAV_A2DP_CODEC_INDEX_SINK_LDAC,
     BTAV_A2DP_CODEC_INDEX_SINK_MAX,
     BTAV_A2DP_CODEC_INDEX_MIN,
     BTAV_A2DP_CODEC_INDEX_MAX};

static const std::vector<const btav_a2dp_codec_priority_t>
    CODEC_PRIORITY_ENUM_VALS = {BTAV_A2DP_CODEC_PRIORITY_DISABLED,
                                BTAV_A2DP_CODEC_PRIORITY_DEFAULT,
                                BTAV_A2DP_CODEC_PRIORITY_HIGHEST};

static const std::vector<const btav_a2dp_codec_sample_rate_t>
    CODEC_SAMPLERATE_ENUM_VALS = {
        BTAV_A2DP_CODEC_SAMPLE_RATE_NONE,   BTAV_A2DP_CODEC_SAMPLE_RATE_44100,
        BTAV_A2DP_CODEC_SAMPLE_RATE_48000,  BTAV_A2DP_CODEC_SAMPLE_RATE_88200,
        BTAV_A2DP_CODEC_SAMPLE_RATE_96000,  BTAV_A2DP_CODEC_SAMPLE_RATE_176400,
        BTAV_A2DP_CODEC_SAMPLE_RATE_192000, BTAV_A2DP_CODEC_SAMPLE_RATE_16000,
        BTAV_A2DP_CODEC_SAMPLE_RATE_24000};

static const std::vector<const btav_a2dp_codec_bits_per_sample_t>
    CODEC_BPS_ENUM_VALS = {BTAV_A2DP_CODEC_BITS_PER_SAMPLE_NONE,
                           BTAV_A2DP_CODEC_BITS_PER_SAMPLE_16,
                           BTAV_A2DP_CODEC_BITS_PER_SAMPLE_24,
                           BTAV_A2DP_CODEC_BITS_PER_SAMPLE_32};

static const std::vector<const btav_a2dp_codec_channel_mode_t>
    CODEC_CHANNELMODE_ENUM_VALS = {BTAV_A2DP_CODEC_CHANNEL_MODE_NONE,
                                   BTAV_A2DP_CODEC_CHANNEL_MODE_MONO,
                                   BTAV_A2DP_CODEC_CHANNEL_MODE_STEREO};

// Construct a btav_a2dp_codec_index_t object
btav_a2dp_codec_index_t getArbitraryBtavCodecIndex(FuzzedDataProvider* fdp) {
  return CODEC_INDEX_ENUM_VALS.at(
      fdp->ConsumeIntegralInRange<size_t>(0, CODEC_INDEX_ENUM_VALS.size() - 1));
}

// Construct a btav_a2dp_codec_priority_t object
btav_a2dp_codec_priority_t getArbitraryBtavCodecPriority(
    FuzzedDataProvider* fdp) {
  return CODEC_PRIORITY_ENUM_VALS.at(fdp->ConsumeIntegralInRange<size_t>(
      0, CODEC_PRIORITY_ENUM_VALS.size() - 1));
}
// Construct a btav_a2dp_codec_sample_rate_t object
btav_a2dp_codec_sample_rate_t getArbitraryBtavCodecSampleRate(
    FuzzedDataProvider* fdp) {
  return CODEC_SAMPLERATE_ENUM_VALS.at(fdp->ConsumeIntegralInRange<size_t>(
      0, CODEC_SAMPLERATE_ENUM_VALS.size() - 1));
}
// Construct a btav_a2dp_codec_bits_per_sample_t object
btav_a2dp_codec_bits_per_sample_t getArbitraryBtavCodecBitsPerSample(
    FuzzedDataProvider* fdp) {
  return CODEC_BPS_ENUM_VALS.at(
      fdp->ConsumeIntegralInRange<size_t>(0, CODEC_BPS_ENUM_VALS.size() - 1));
}
// Construct a btav_a2dp_codec_channel_mode_t object
btav_a2dp_codec_channel_mode_t getArbitraryBtavCodecChannelMode(
    FuzzedDataProvider* fdp) {
  return CODEC_CHANNELMODE_ENUM_VALS.at(fdp->ConsumeIntegralInRange<size_t>(
      0, CODEC_CHANNELMODE_ENUM_VALS.size() - 1));
}
// Construct a btav_a2dp_codec_config_t object
btav_a2dp_codec_config_t getArbitraryBtavCodecConfig(FuzzedDataProvider* fdp) {
  btav_a2dp_codec_config_t config;

  config.codec_type = getArbitraryBtavCodecIndex(fdp);
  config.codec_priority = getArbitraryBtavCodecPriority(fdp);
  config.sample_rate = getArbitraryBtavCodecSampleRate(fdp);
  config.bits_per_sample = getArbitraryBtavCodecBitsPerSample(fdp);
  config.channel_mode = getArbitraryBtavCodecChannelMode(fdp);
  config.codec_specific_1 = fdp->ConsumeIntegral<int64_t>();
  config.codec_specific_2 = fdp->ConsumeIntegral<int64_t>();
  config.codec_specific_3 = fdp->ConsumeIntegral<int64_t>();
  config.codec_specific_4 = fdp->ConsumeIntegral<int64_t>();

  return config;
}

tA2DP_ENCODER_INIT_PEER_PARAMS getArbitraryA2dpEncoderInitPeerParams(
    FuzzedDataProvider* fdp) {
  tA2DP_ENCODER_INIT_PEER_PARAMS params;

  params.is_peer_edr = fdp->ConsumeBool();
  params.peer_supports_3mbps = fdp->ConsumeBool();
  params.peer_mtu = fdp->ConsumeIntegral<uint16_t>();

  return params;
}

#define MAX_BTHDR_SIZE 1024
std::shared_ptr<BT_HDR> getArbitraryBtHdr(FuzzedDataProvider* fdp) {
  // Build a data buffer
  size_t buf_size = fdp->ConsumeIntegralInRange<size_t>(0, MAX_BTHDR_SIZE);
  std::vector<uint8_t> bytes = fdp->ConsumeBytes<uint8_t>(buf_size);

  if (bytes.empty()) {
    return nullptr;
  }

  uint16_t hdr_size = bytes.size() + sizeof(BT_HDR);
  std::shared_ptr<BT_HDR> bt_hdr(reinterpret_cast<BT_HDR*>(calloc(1, hdr_size)),
                                 free);

  bt_hdr->event = fdp->ConsumeIntegral<uint16_t>();
  bt_hdr->len = bytes.size();
  bt_hdr->offset =
      fdp->ConsumeIntegralInRange<uint16_t>(0, hdr_size - sizeof(BT_HDR));
  bt_hdr->layer_specific = fdp->ConsumeIntegral<uint16_t>();
  std::copy(bytes.begin(), bytes.end(), bt_hdr->data);

  return bt_hdr;
}

#include "bta/av/bta_av_int.h"
tBT_A2DP_OFFLOAD generateArbitrarytA2dpOffload(FuzzedDataProvider* fdp) {
  tBT_A2DP_OFFLOAD retval;

  retval.codec_type = fdp->ConsumeIntegral<uint32_t>();
  retval.max_latency = fdp->ConsumeIntegral<uint16_t>();
  std::vector<uint8_t> scms_t_enable = fdp->ConsumeBytes<uint8_t>(2);
  memcpy(&retval.scms_t_enable[0], scms_t_enable.data(), scms_t_enable.size());
  retval.sample_rate = fdp->ConsumeIntegral<uint32_t>();
  retval.bits_per_sample = fdp->ConsumeIntegral<uint8_t>();
  retval.ch_mode = fdp->ConsumeIntegral<uint8_t>();
  retval.encoded_audio_bitrate = fdp->ConsumeIntegral<uint32_t>();
  retval.acl_hdl = fdp->ConsumeIntegral<uint16_t>();
  retval.l2c_rcid = fdp->ConsumeIntegral<uint16_t>();
  retval.mtu = fdp->ConsumeIntegral<uint16_t>();

  std::vector<uint8_t> codec_info_bytes = fdp->ConsumeBytes<uint8_t>(32);
  memcpy(&retval.codec_info[0], codec_info_bytes.data(),
         codec_info_bytes.size());

  return retval;
}

#endif  // A2DP_CODEC_HELPERFUNCTIONS_H_
