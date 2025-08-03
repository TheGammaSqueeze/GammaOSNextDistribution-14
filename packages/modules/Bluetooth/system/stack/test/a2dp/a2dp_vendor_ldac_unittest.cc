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

#include "stack/include/a2dp_vendor_ldac.h"

#include <gtest/gtest.h>
#include <stdio.h>

#include "common/init_flags.h"
#include "common/testing/log_capture.h"
#include "common/time_util.h"
#include "osi/include/allocator.h"
#include "stack/include/a2dp_vendor_ldac_constants.h"
#include "stack/include/avdt_api.h"
#include "stack/include/bt_hdr.h"
#include "test_util.h"
#include "wav_reader.h"

namespace {
constexpr uint32_t kA2dpTickUs = 23 * 1000;
constexpr char kWavFile[] = "test/a2dp/raw_data/pcm1644s.wav";
constexpr uint8_t kCodecInfoLdacCapability[AVDT_CODEC_SIZE] = {
    A2DP_LDAC_CODEC_LEN,
    AVDT_MEDIA_TYPE_AUDIO,
    A2DP_MEDIA_CT_NON_A2DP,
    0x2D,  // A2DP_LDAC_VENDOR_ID
    0x01,  // A2DP_LDAC_VENDOR_ID
    0x00,  // A2DP_LDAC_VENDOR_ID
    0x00,  // A2DP_LDAC_VENDOR_ID
    0xAA,  // A2DP_LDAC_CODEC_ID
    0x00,  // A2DP_LDAC_CODEC_ID,
    A2DP_LDAC_SAMPLING_FREQ_44100,
    A2DP_LDAC_CHANNEL_MODE_STEREO,
};
uint8_t* Data(BT_HDR* packet) { return packet->data + packet->offset; }
}  // namespace
namespace bluetooth {
namespace testing {

// static BT_HDR* packet = nullptr;
static WavReader wav_reader = WavReader(GetWavFilePath(kWavFile).c_str());

class A2dpLdacTest : public ::testing::Test {
 protected:
  void SetUp() override {
    common::InitFlags::SetAllForTesting();
    SetCodecConfig();
    encoder_iface_ = const_cast<tA2DP_ENCODER_INTERFACE*>(
        A2DP_VendorGetEncoderInterfaceLdac(kCodecInfoLdacCapability));
    ASSERT_NE(encoder_iface_, nullptr);
    decoder_iface_ = const_cast<tA2DP_DECODER_INTERFACE*>(
        A2DP_VendorGetDecoderInterfaceLdac(kCodecInfoLdacCapability));
    ASSERT_NE(decoder_iface_, nullptr);
  }

  void TearDown() override {
    if (a2dp_codecs_ != nullptr) {
      delete a2dp_codecs_;
    }
    if (encoder_iface_ != nullptr) {
      encoder_iface_->encoder_cleanup();
    }
    if (decoder_iface_ != nullptr) {
      decoder_iface_->decoder_cleanup();
    }
  }

// NOTE: Make a super func for all codecs
  void SetCodecConfig() {
    uint8_t source_codec_info_result[AVDT_CODEC_SIZE];
    btav_a2dp_codec_index_t peer_codec_index;
    a2dp_codecs_ = new A2dpCodecs(std::vector<btav_a2dp_codec_config_t>());

    ASSERT_TRUE(a2dp_codecs_->init());

    peer_codec_index = A2DP_SinkCodecIndex(kCodecInfoLdacCapability);
    ASSERT_NE(peer_codec_index, BTAV_A2DP_CODEC_INDEX_MAX);
    ASSERT_EQ(peer_codec_index, BTAV_A2DP_CODEC_INDEX_SINK_LDAC);
    source_codec_config_ =
        a2dp_codecs_->findSourceCodecConfig(kCodecInfoLdacCapability);
    ASSERT_NE(source_codec_config_, nullptr);
    ASSERT_TRUE(a2dp_codecs_->setCodecConfig(kCodecInfoLdacCapability, true,
                                                source_codec_info_result, true));
    ASSERT_EQ(a2dp_codecs_->getCurrentCodecConfig(), source_codec_config_);
    // Compare the result codec with the local test codec info
    for (size_t i = 0; i < kCodecInfoLdacCapability[0] + 1; i++) {
      ASSERT_EQ(source_codec_info_result[i], kCodecInfoLdacCapability[i]);
    }
    ASSERT_NE(source_codec_config_->getAudioBitsPerSample(), 0);
  }

  void InitializeEncoder(a2dp_source_read_callback_t read_cb,
                         a2dp_source_enqueue_callback_t enqueue_cb) {
    tA2DP_ENCODER_INIT_PEER_PARAMS peer_params = {true, true, 1000};
    encoder_iface_->encoder_init(&peer_params, source_codec_config_, read_cb,
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
  A2dpCodecConfig* source_codec_config_;
  A2dpCodecs* a2dp_codecs_;
  tA2DP_ENCODER_INTERFACE* encoder_iface_;
  tA2DP_DECODER_INTERFACE* decoder_iface_;
  std::unique_ptr<LogCapture> log_capture_;
};

TEST_F(A2dpLdacTest, a2dp_source_read_underflow) {
  // log_capture_ = std::make_unique<LogCapture>();
  auto read_cb = +[](uint8_t* p_buf, uint32_t len) -> uint32_t {
    return 0;
  };
  auto enqueue_cb = +[](BT_HDR* p_buf, size_t frames_n, uint32_t len) -> bool {
    return false;
  };
  InitializeEncoder(read_cb, enqueue_cb);
  uint64_t timestamp_us = bluetooth::common::time_gettimeofday_us();
  encoder_iface_->send_frames(timestamp_us);
  usleep(kA2dpTickUs);
  timestamp_us = bluetooth::common::time_gettimeofday_us();
  encoder_iface_->send_frames(timestamp_us);
  // log_capture_->WaitUntilLogContains("a2dp_ldac_encode_frames: underflow");
}

}  // namespace testing
}  //namespace bluetooth
