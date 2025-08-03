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

#include "stack/include/a2dp_sbc.h"

#include <base/logging.h>
#include <gtest/gtest.h>
#include <stdio.h>

#include <chrono>
#include <cstdint>
#include <future>
#include <string>

#include "common/init_flags.h"
#include "common/testing/log_capture.h"
#include "common/time_util.h"
#include "os/log.h"
#include "osi/include/allocator.h"
#include "stack/include/a2dp_sbc_decoder.h"
#include "stack/include/a2dp_sbc_encoder.h"
#include "stack/include/avdt_api.h"
#include "stack/include/bt_hdr.h"
#include "test_util.h"
#include "wav_reader.h"

namespace {
constexpr uint32_t kSbcReadSize = 512;
constexpr uint32_t kA2dpTickUs = 23 * 1000;
constexpr char kWavFile[] = "test/a2dp/raw_data/pcm1644s.wav";
constexpr uint16_t kPeerMtu = 1000;
const uint8_t kCodecInfoSbcCapability[AVDT_CODEC_SIZE] = {
    6,                   // Length (A2DP_SBC_INFO_LEN)
    0,                   // Media Type: AVDT_MEDIA_TYPE_AUDIO
    0,                   // Media Codec Type: A2DP_MEDIA_CT_SBC
    0x20 | 0x01,         // Sample Frequency: A2DP_SBC_IE_SAMP_FREQ_44 |
                         // Channel Mode: A2DP_SBC_IE_CH_MD_JOINT
    0x10 | 0x04 | 0x01,  // Block Length: A2DP_SBC_IE_BLOCKS_16 |
                         // Subbands: A2DP_SBC_IE_SUBBAND_8 |
                         // Allocation Method: A2DP_SBC_IE_ALLOC_MD_L
    2,                   // MinimumBitpool Value: A2DP_SBC_IE_MIN_BITPOOL
    53,                  // Maximum Bitpool Value: A2DP_SBC_MAX_BITPOOL
    7,                   // Fake
    8,                   // Fake
    9                    // Fake
};
uint8_t* Data(BT_HDR* packet) { return packet->data + packet->offset; }
}  // namespace

namespace bluetooth {
namespace testing {

static BT_HDR* packet = nullptr;
static WavReader wav_reader = WavReader(GetWavFilePath(kWavFile).c_str());
static std::promise<void> promise;

class A2dpSbcTest : public ::testing::Test {
 protected:
  void SetUp() override {
    common::InitFlags::SetAllForTesting();
    SetCodecConfig();
    encoder_iface_ = const_cast<tA2DP_ENCODER_INTERFACE*>(
        A2DP_GetEncoderInterfaceSbc(kCodecInfoSbcCapability));
    ASSERT_NE(encoder_iface_, nullptr);
    decoder_iface_ = const_cast<tA2DP_DECODER_INTERFACE*>(
        A2DP_GetDecoderInterfaceSbc(kCodecInfoSbcCapability));
    ASSERT_NE(decoder_iface_, nullptr);
  }

  void TearDown() override {
    if (a2dp_codecs_ != nullptr) {
      delete a2dp_codecs_;
    }
    if (encoder_iface_ != nullptr) {
      encoder_iface_->encoder_cleanup();
    }
    A2DP_UnloadEncoderSbc();
    if (decoder_iface_ != nullptr) {
      decoder_iface_->decoder_cleanup();
    }
    A2DP_UnloadDecoderSbc();
  }

  void SetCodecConfig() {
    uint8_t codec_info_result[AVDT_CODEC_SIZE];
    btav_a2dp_codec_index_t peer_codec_index;
    a2dp_codecs_ = new A2dpCodecs(std::vector<btav_a2dp_codec_config_t>());

    ASSERT_TRUE(a2dp_codecs_->init());

    // Create the codec capability - SBC Sink
    memset(codec_info_result, 0, sizeof(codec_info_result));
    ASSERT_TRUE(A2DP_IsSinkCodecSupportedSbc(kCodecInfoSbcCapability));
    peer_codec_index = A2DP_SinkCodecIndex(kCodecInfoSbcCapability);
    ASSERT_NE(peer_codec_index, BTAV_A2DP_CODEC_INDEX_MAX);
    sink_codec_config_ = a2dp_codecs_->findSinkCodecConfig(kCodecInfoSbcCapability);
    ASSERT_NE(sink_codec_config_, nullptr);
    ASSERT_TRUE(a2dp_codecs_->setSinkCodecConfig(kCodecInfoSbcCapability, true,
                                                 codec_info_result, true));
    ASSERT_TRUE(a2dp_codecs_->setPeerSinkCodecCapabilities(kCodecInfoSbcCapability));
    // Compare the result codec with the local test codec info
    for (size_t i = 0; i < kCodecInfoSbcCapability[0] + 1; i++) {
      ASSERT_EQ(codec_info_result[i], kCodecInfoSbcCapability[i]);
    }
    ASSERT_TRUE(a2dp_codecs_->setCodecConfig(kCodecInfoSbcCapability, true, codec_info_result, true));
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

TEST_F(A2dpSbcTest, a2dp_source_read_underflow) {
  promise = {};
  auto read_cb = +[](uint8_t* p_buf, uint32_t len) -> uint32_t {
    // underflow
    return 0;
  };
  auto enqueue_cb = +[](BT_HDR* p_buf, size_t frames_n, uint32_t len) -> bool {
    promise.set_value();
    osi_free(p_buf);
    return false;
  };
  InitializeEncoder(true, read_cb, enqueue_cb);
  uint64_t timestamp_us = bluetooth::common::time_gettimeofday_us();
  encoder_iface_->send_frames(timestamp_us);
  usleep(kA2dpTickUs);
  timestamp_us = bluetooth::common::time_gettimeofday_us();
  encoder_iface_->send_frames(timestamp_us);
  ASSERT_EQ(promise.get_future().wait_for(std::chrono::milliseconds(10)),
            std::future_status::timeout);
}

TEST_F(A2dpSbcTest, a2dp_enqueue_cb_is_invoked) {
  promise = {};
  auto read_cb = +[](uint8_t* p_buf, uint32_t len) -> uint32_t {
    ASSERT(kSbcReadSize == len);
    return len;
  };
  auto enqueue_cb = +[](BT_HDR* p_buf, size_t frames_n, uint32_t len) -> bool {
    static bool first_invocation = true;
    if (first_invocation) {
      promise.set_value();
    }
    first_invocation = false;
    osi_free(p_buf);
    return false;
  };
  InitializeEncoder(true, read_cb, enqueue_cb);
  uint64_t timestamp_us = bluetooth::common::time_gettimeofday_us();
  encoder_iface_->send_frames(timestamp_us);
  usleep(kA2dpTickUs);
  timestamp_us = bluetooth::common::time_gettimeofday_us();
  encoder_iface_->send_frames(timestamp_us);
  promise.get_future().wait();
}

TEST_F(A2dpSbcTest, decoded_data_cb_not_invoked_when_empty_packet) {
  auto data_cb = +[](uint8_t* p_buf, uint32_t len) { FAIL(); };
  InitializeDecoder(data_cb);
  std::vector<uint8_t> data;
  BT_HDR* packet = AllocateL2capPacket(data);
  decoder_iface_->decode_packet(packet);
  osi_free(packet);
}

TEST_F(A2dpSbcTest, decoded_data_cb_invoked) {
  promise = {};
  auto data_cb = +[](uint8_t* p_buf, uint32_t len) {};
  InitializeDecoder(data_cb);

  auto read_cb = +[](uint8_t* p_buf, uint32_t len) -> uint32_t {
    static uint32_t counter = 0;
    memcpy(p_buf, wav_reader.GetSamples() + counter, len);
    counter += len;
    return len;
  };
  auto enqueue_cb = +[](BT_HDR* p_buf, size_t frames_n, uint32_t len) -> bool {
    static bool first_invocation = true;
    if (first_invocation) {
      packet = reinterpret_cast<BT_HDR*>(
          osi_malloc(sizeof(*p_buf) + p_buf->len + 1));
      memcpy(packet, p_buf, sizeof(*p_buf));
      packet->offset = 0;
      memcpy(packet->data + 1, p_buf->data + p_buf->offset, p_buf->len);
      packet->data[0] = frames_n;
      p_buf->len += 1;
      promise.set_value();
    }
    first_invocation = false;
    osi_free(p_buf);
    return false;
  };
  InitializeEncoder(true, read_cb, enqueue_cb);

  uint64_t timestamp_us = bluetooth::common::time_gettimeofday_us();
  encoder_iface_->send_frames(timestamp_us);

  promise.get_future().wait();
  decoder_iface_->decode_packet(packet);
  osi_free(packet);
}

TEST_F(A2dpSbcTest, set_source_codec_config_works) {
  uint8_t codec_info_result[AVDT_CODEC_SIZE];
  ASSERT_TRUE(a2dp_codecs_->setCodecConfig(kCodecInfoSbcCapability, true, codec_info_result, true));
  ASSERT_TRUE(A2DP_CodecTypeEqualsSbc(codec_info_result, kCodecInfoSbcCapability));
  ASSERT_TRUE(A2DP_CodecEqualsSbc(codec_info_result, kCodecInfoSbcCapability));
  auto* codec_config = a2dp_codecs_->findSourceCodecConfig(kCodecInfoSbcCapability);
  ASSERT_EQ(codec_config->name(), source_codec_config_->name());
  ASSERT_EQ(codec_config->getAudioBitsPerSample(), source_codec_config_->getAudioBitsPerSample());
}

TEST_F(A2dpSbcTest, sink_supports_sbc) {
  ASSERT_TRUE(A2DP_IsSinkCodecSupportedSbc(kCodecInfoSbcCapability));
}

TEST_F(A2dpSbcTest, effective_mtu_when_peer_supports_3mbps) {
  auto read_cb = +[](uint8_t* p_buf, uint32_t len) -> uint32_t {
    ASSERT(kSbcReadSize == len);
    return len;
  };
  auto enqueue_cb = +[](BT_HDR* p_buf, size_t frames_n, uint32_t len) -> bool {
    osi_free(p_buf);
    return false;
  };
  InitializeEncoder(true, read_cb, enqueue_cb);
  ASSERT_EQ(a2dp_sbc_get_effective_frame_size(), kPeerMtu);
}

TEST_F(A2dpSbcTest, effective_mtu_when_peer_does_not_support_3mbps) {
  auto read_cb = +[](uint8_t* p_buf, uint32_t len) -> uint32_t {
    ASSERT(kSbcReadSize == len);
    return len;
  };
  auto enqueue_cb = +[](BT_HDR* p_buf, size_t frames_n, uint32_t len) -> bool {
    osi_free(p_buf);
    return false;
  };
  InitializeEncoder(false, read_cb, enqueue_cb);
  ASSERT_EQ(a2dp_sbc_get_effective_frame_size(), 663 /* MAX_2MBPS_AVDTP_MTU */);
}

TEST_F(A2dpSbcTest, debug_codec_dump) {
  log_capture_ = std::make_unique<LogCapture>();
  a2dp_codecs_->debug_codec_dump(2);
  log_capture_->WaitUntilLogContains("Current Codec: SBC");
}

TEST_F(A2dpSbcTest, codec_info_string) {
  auto codec_info = A2DP_CodecInfoString(kCodecInfoSbcCapability);
  ASSERT_NE(codec_info.find("samp_freq: 44100"), std::string::npos);
  ASSERT_NE(codec_info.find("ch_mode: Joint"), std::string::npos);
}

TEST_F(A2dpSbcTest, get_track_bits_per_sample) {
  ASSERT_EQ(A2DP_GetTrackBitsPerSampleSbc(kCodecInfoSbcCapability), 16);
}

}  // namespace testing
}  // namespace bluetooth
