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

#include "stack/include/a2dp_aac.h"

#include <base/logging.h>
#include <bluetooth/log.h>
#include <gtest/gtest.h>
#include <stdio.h>

#include <cstdint>
#include <string>

#include "common/init_flags.h"
#include "common/testing/log_capture.h"
#include "common/time_util.h"
#include "os/log.h"
#include "osi/include/allocator.h"
#include "stack/include/a2dp_aac_decoder.h"
#include "stack/include/a2dp_aac_encoder.h"
#include "stack/include/avdt_api.h"
#include "stack/include/bt_hdr.h"
#include "test_util.h"
#include "wav_reader.h"

namespace {
constexpr uint32_t kAacReadSize = 1024 * 2 * 2;
constexpr uint32_t kA2dpTickUs = 23 * 1000;
constexpr char kDecodedDataCallbackIsInvoked[] =
    "A2DP decoded data callback is invoked.";
constexpr char kEnqueueCallbackIsInvoked[] =
    "A2DP source enqueue callback is invoked.";
constexpr uint16_t kPeerMtu = 1000;
constexpr char kWavFile[] = "test/a2dp/raw_data/pcm1644s.wav";
constexpr uint8_t kCodecInfoAacCapability[AVDT_CODEC_SIZE] = {
    8,           // Length (A2DP_AAC_INFO_LEN)
    0,           // Media Type: AVDT_MEDIA_TYPE_AUDIO
    2,           // Media Codec Type: A2DP_MEDIA_CT_AAC
    0x80,        // Object Type: A2DP_AAC_OBJECT_TYPE_MPEG2_LC
    0x01,        // Sampling Frequency: A2DP_AAC_SAMPLING_FREQ_44100
    0x04,        // Channels: A2DP_AAC_CHANNEL_MODE_STEREO
    0x00 | 0x4,  // Variable Bit Rate:
                 // A2DP_AAC_VARIABLE_BIT_RATE_DISABLED
                 // Bit Rate: 320000 = 0x4e200
    0xe2,        // Bit Rate: 320000 = 0x4e200
    0x00,        // Bit Rate: 320000 = 0x4e200
    7,           // Unused
    8,           // Unused
    9            // Unused
};
uint8_t* Data(BT_HDR* packet) { return packet->data + packet->offset; }
}  // namespace

namespace bluetooth {
namespace testing {

static BT_HDR* packet = nullptr;
static WavReader wav_reader = WavReader(GetWavFilePath(kWavFile).c_str());

class A2dpAacTest : public ::testing::Test {
 protected:
  void SetUp() override {
    common::InitFlags::SetAllForTesting();
    SetCodecConfig();
    encoder_iface_ = const_cast<tA2DP_ENCODER_INTERFACE*>(
        A2DP_GetEncoderInterfaceAac(kCodecInfoAacCapability));
    ASSERT_NE(encoder_iface_, nullptr);
    decoder_iface_ = const_cast<tA2DP_DECODER_INTERFACE*>(
        A2DP_GetDecoderInterfaceAac(kCodecInfoAacCapability));
    ASSERT_NE(decoder_iface_, nullptr);
  }

  void TearDown() override {
    if (a2dp_codecs_ != nullptr) {
      delete a2dp_codecs_;
    }
    if (encoder_iface_ != nullptr) {
      encoder_iface_->encoder_cleanup();
    }
    A2DP_UnloadEncoderAac();
    if (decoder_iface_ != nullptr) {
      decoder_iface_->decoder_cleanup();
    }
    A2DP_UnloadDecoderAac();
  }

  void SetCodecConfig() {
    uint8_t codec_info_result[AVDT_CODEC_SIZE];
    btav_a2dp_codec_index_t peer_codec_index;
    a2dp_codecs_ = new A2dpCodecs(std::vector<btav_a2dp_codec_config_t>());

    ASSERT_TRUE(a2dp_codecs_->init());

    // Create the codec capability - AAC Sink
    memset(codec_info_result, 0, sizeof(codec_info_result));
    ASSERT_TRUE(A2DP_IsSinkCodecSupportedAac(kCodecInfoAacCapability));
    peer_codec_index = A2DP_SinkCodecIndex(kCodecInfoAacCapability);
    ASSERT_NE(peer_codec_index, BTAV_A2DP_CODEC_INDEX_MAX);
    sink_codec_config_ = a2dp_codecs_->findSinkCodecConfig(kCodecInfoAacCapability);
    ASSERT_NE(sink_codec_config_, nullptr);
    ASSERT_TRUE(a2dp_codecs_->setSinkCodecConfig(kCodecInfoAacCapability, true,
                                                 codec_info_result, true));
    ASSERT_TRUE(a2dp_codecs_->setPeerSinkCodecCapabilities(kCodecInfoAacCapability));
    // Compare the result codec with the local test codec info
    for (size_t i = 0; i < kCodecInfoAacCapability[0] + 1; i++) {
      ASSERT_EQ(codec_info_result[i], kCodecInfoAacCapability[i]);
    }
    ASSERT_TRUE(a2dp_codecs_->setCodecConfig(kCodecInfoAacCapability, true, codec_info_result, true));
    source_codec_config_ = a2dp_codecs_->getCurrentCodecConfig();
  }

  void InitializeEncoder(bool peer_supports_3mbps, a2dp_source_read_callback_t read_cb,
                         a2dp_source_enqueue_callback_t enqueue_cb) {
    tA2DP_ENCODER_INIT_PEER_PARAMS peer_params = {true, peer_supports_3mbps, kPeerMtu};
    encoder_iface_->encoder_init(&peer_params, sink_codec_config_, read_cb,
                                 enqueue_cb);
  }

  void InitializeDecoder(decoded_data_callback_t data_cb) {
    decoder_iface_->decoder_init(data_cb);
  }

  BT_HDR* AllocateL2capPacket(const std::vector<uint8_t> data) const {
    auto packet = AllocatePacket(data.size());
    std::copy(data.cbegin(), data.cend(), Data(packet));
    return packet;
  }

  BT_HDR* AllocatePacket(size_t packet_length) const {
    BT_HDR* packet =
        static_cast<BT_HDR*>(osi_calloc(sizeof(BT_HDR) + packet_length));
    packet->len = packet_length;
    return packet;
  }
  A2dpCodecConfig* sink_codec_config_;
  A2dpCodecConfig* source_codec_config_;
  A2dpCodecs* a2dp_codecs_;
  tA2DP_ENCODER_INTERFACE* encoder_iface_;
  tA2DP_DECODER_INTERFACE* decoder_iface_;
  std::unique_ptr<LogCapture> log_capture_;
};

TEST_F(A2dpAacTest, a2dp_source_read_underflow) {
  log_capture_ = std::make_unique<LogCapture>();
  auto read_cb = +[](uint8_t* p_buf, uint32_t len) -> uint32_t {
    // underflow
    return 0;
  };
  auto enqueue_cb = +[](BT_HDR* p_buf, size_t frames_n, uint32_t len) -> bool {
    return false;
  };
  InitializeEncoder(true, read_cb, enqueue_cb);
  uint64_t timestamp_us = bluetooth::common::time_gettimeofday_us();
  encoder_iface_->send_frames(timestamp_us);
  usleep(kA2dpTickUs);
  timestamp_us = bluetooth::common::time_gettimeofday_us();
  encoder_iface_->send_frames(timestamp_us);
  log_capture_->WaitUntilLogContains("a2dp_aac_encode_frames: underflow");
}

TEST_F(A2dpAacTest, a2dp_enqueue_cb_is_invoked) {
  log_capture_ = std::make_unique<LogCapture>();
  auto read_cb = +[](uint8_t* p_buf, uint32_t len) -> uint32_t {
    ASSERT(kAacReadSize == len);
    return len;
  };
  auto enqueue_cb = +[](BT_HDR* p_buf, size_t frames_n, uint32_t len) -> bool {
    log::info("{}", kEnqueueCallbackIsInvoked);
    osi_free(p_buf);
    return false;
  };
  InitializeEncoder(true, read_cb, enqueue_cb);
  uint64_t timestamp_us = bluetooth::common::time_gettimeofday_us();
  encoder_iface_->send_frames(timestamp_us);
  usleep(kA2dpTickUs);
  timestamp_us = bluetooth::common::time_gettimeofday_us();
  encoder_iface_->send_frames(timestamp_us);
  log_capture_->WaitUntilLogContains(kEnqueueCallbackIsInvoked);
}

TEST_F(A2dpAacTest, decoded_data_cb_not_invoked_when_empty_packet) {
  auto data_cb = +[](uint8_t* p_buf, uint32_t len) { FAIL(); };
  InitializeDecoder(data_cb);
  std::vector<uint8_t> data;
  BT_HDR* packet = AllocateL2capPacket(data);
  decoder_iface_->decode_packet(packet);
  osi_free(packet);
}

TEST_F(A2dpAacTest, decoded_data_cb_invoked) {
  log_capture_ = std::make_unique<LogCapture>();
  auto data_cb = +[](uint8_t* p_buf, uint32_t len) {
    log::info("{}", kDecodedDataCallbackIsInvoked);
  };
  InitializeDecoder(data_cb);

  auto read_cb = +[](uint8_t* p_buf, uint32_t len) -> uint32_t {
    static uint32_t counter = 0;
    memcpy(p_buf, wav_reader.GetSamples() + counter, len);
    counter += len;
    return len;
  };
  auto enqueue_cb = +[](BT_HDR* p_buf, size_t frames_n, uint32_t len) -> bool {
    packet = p_buf;
    log::info("{}", kEnqueueCallbackIsInvoked);
    return false;
  };
  InitializeEncoder(true, read_cb, enqueue_cb);

  uint64_t timestamp_us = bluetooth::common::time_gettimeofday_us();
  encoder_iface_->send_frames(timestamp_us);
  usleep(kA2dpTickUs);
  timestamp_us = bluetooth::common::time_gettimeofday_us();
  encoder_iface_->send_frames(timestamp_us);

  log_capture_->WaitUntilLogContains(kEnqueueCallbackIsInvoked);
  decoder_iface_->decode_packet(packet);
  osi_free(packet);
  ASSERT_TRUE(log_capture_->Find(kDecodedDataCallbackIsInvoked));
}

TEST_F(A2dpAacTest, set_source_codec_config_works) {
  uint8_t codec_info_result[AVDT_CODEC_SIZE];
  ASSERT_TRUE(a2dp_codecs_->setCodecConfig(kCodecInfoAacCapability, true, codec_info_result, true));
  ASSERT_TRUE(A2DP_CodecTypeEqualsAac(codec_info_result, kCodecInfoAacCapability));
  ASSERT_TRUE(A2DP_CodecEqualsAac(codec_info_result, kCodecInfoAacCapability));
  auto* codec_config = a2dp_codecs_->findSourceCodecConfig(kCodecInfoAacCapability);
  ASSERT_EQ(codec_config->name(), source_codec_config_->name());
  ASSERT_EQ(codec_config->getAudioBitsPerSample(), source_codec_config_->getAudioBitsPerSample());
}

TEST_F(A2dpAacTest, sink_supports_aac) {
  ASSERT_TRUE(A2DP_IsSinkCodecSupportedAac(kCodecInfoAacCapability));
}

TEST_F(A2dpAacTest, effective_mtu_when_peer_supports_3mbps) {
  auto read_cb = +[](uint8_t* p_buf, uint32_t len) -> uint32_t {
    ASSERT(kAacReadSize == len);
    return len;
  };
  auto enqueue_cb = +[](BT_HDR* p_buf, size_t frames_n, uint32_t len) -> bool {
    osi_free(p_buf);
    return false;
  };
  InitializeEncoder(true, read_cb, enqueue_cb);
  ASSERT_EQ(a2dp_aac_get_effective_frame_size(), kPeerMtu);
}

TEST_F(A2dpAacTest, effective_mtu_when_peer_does_not_support_3mbps) {
  auto read_cb = +[](uint8_t* p_buf, uint32_t len) -> uint32_t {
    ASSERT(kAacReadSize == len);
    return len;
  };
  auto enqueue_cb = +[](BT_HDR* p_buf, size_t frames_n, uint32_t len) -> bool {
    osi_free(p_buf);
    return false;
  };
  InitializeEncoder(false, read_cb, enqueue_cb);
  ASSERT_EQ(a2dp_aac_get_effective_frame_size(), 663 /* MAX_2MBPS_AVDTP_MTU */);
}

TEST_F(A2dpAacTest, debug_codec_dump) {
  log_capture_ = std::make_unique<LogCapture>();
  a2dp_codecs_->debug_codec_dump(2);
  log_capture_->WaitUntilLogContains("Current Codec: AAC");
}

TEST_F(A2dpAacTest, codec_info_string) {
  auto codec_info = A2DP_CodecInfoString(kCodecInfoAacCapability);
  ASSERT_NE(codec_info.find("samp_freq: 44100"), std::string::npos);
  ASSERT_NE(codec_info.find("ch_mode: Stereo"), std::string::npos);
}

TEST_F(A2dpAacTest, get_track_bits_per_sample) {
  ASSERT_EQ(A2DP_GetTrackBitsPerSampleAac(kCodecInfoAacCapability), 16);
}

}  // namespace testing
}  // namespace bluetooth
