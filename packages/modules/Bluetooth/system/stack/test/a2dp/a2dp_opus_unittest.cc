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

#include <base/logging.h>
#include <gtest/gtest.h>
#include <stdio.h>

#include <chrono>
#include <cstdint>
#include <future>
#include <string>

#include "common/init_flags.h"
#include "common/time_util.h"
#include "os/log.h"
#include "osi/include/allocator.h"
#include "stack/include/a2dp_vendor_opus.h"
#include "stack/include/a2dp_vendor_opus_constants.h"
#include "stack/include/bt_hdr.h"
#include "test_util.h"
#include "wav_reader.h"

namespace {
constexpr uint32_t kA2dpTickUs = 23 * 1000;
constexpr char kWavFile[] = "test/a2dp/raw_data/pcm1644s.wav";
const uint8_t kCodecInfoOpusCapability[AVDT_CODEC_SIZE] = {
  A2DP_OPUS_CODEC_LEN,         // Length
  AVDT_MEDIA_TYPE_AUDIO << 4,  // Media Type
  A2DP_MEDIA_CT_NON_A2DP,      // Media Codec Type Vendor
  (A2DP_OPUS_VENDOR_ID & 0x000000FF),
  (A2DP_OPUS_VENDOR_ID & 0x0000FF00) >> 8,
  (A2DP_OPUS_VENDOR_ID & 0x00FF0000) >> 16,
  (A2DP_OPUS_VENDOR_ID & 0xFF000000) >> 24,
  (A2DP_OPUS_CODEC_ID & 0x00FF),
  (A2DP_OPUS_CODEC_ID & 0xFF00) >> 8,
  A2DP_OPUS_CHANNEL_MODE_STEREO | A2DP_OPUS_20MS_FRAMESIZE |
      A2DP_OPUS_SAMPLING_FREQ_48000
};
uint8_t* Data(BT_HDR* packet) { return packet->data + packet->offset; }

uint32_t GetReadSize() {
  return A2DP_VendorGetFrameSizeOpus(kCodecInfoOpusCapability) * A2DP_VendorGetTrackChannelCountOpus(kCodecInfoOpusCapability) * (A2DP_VendorGetTrackBitsPerSampleOpus(kCodecInfoOpusCapability) / 8);
}
}  // namespace

namespace bluetooth {
namespace testing {

static BT_HDR* packet = nullptr;
static WavReader wav_reader = WavReader(GetWavFilePath(kWavFile).c_str());
static std::promise<void> promise;

class A2dpOpusTest : public ::testing::Test {
 protected:
  void SetUp() override {
    common::InitFlags::SetAllForTesting();
    SetCodecConfig();
    encoder_iface_ = const_cast<tA2DP_ENCODER_INTERFACE*>(
        A2DP_VendorGetEncoderInterfaceOpus(kCodecInfoOpusCapability));
    ASSERT_NE(encoder_iface_, nullptr);
    decoder_iface_ = const_cast<tA2DP_DECODER_INTERFACE*>(
        A2DP_VendorGetDecoderInterfaceOpus(kCodecInfoOpusCapability));
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

  void SetCodecConfig() {
    uint8_t codec_info_result[AVDT_CODEC_SIZE];
    btav_a2dp_codec_index_t peer_codec_index;
    a2dp_codecs_ = new A2dpCodecs(std::vector<btav_a2dp_codec_config_t>());

    ASSERT_TRUE(a2dp_codecs_->init());

    // Create the codec capability - SBC Sink
    memset(codec_info_result, 0, sizeof(codec_info_result));
    peer_codec_index = A2DP_SinkCodecIndex(kCodecInfoOpusCapability);
    ASSERT_NE(peer_codec_index, BTAV_A2DP_CODEC_INDEX_MAX);
    codec_config_ = a2dp_codecs_->findSinkCodecConfig(kCodecInfoOpusCapability);
    ASSERT_NE(codec_config_, nullptr);
    ASSERT_TRUE(a2dp_codecs_->setSinkCodecConfig(kCodecInfoOpusCapability, true,
                                                 codec_info_result, true));
    ASSERT_EQ(a2dp_codecs_->getCurrentCodecConfig(), codec_config_);
    // Compare the result codec with the local test codec info
    for (size_t i = 0; i < kCodecInfoOpusCapability[0] + 1; i++) {
      ASSERT_EQ(codec_info_result[i], kCodecInfoOpusCapability[i]);
    }
    ASSERT_EQ(codec_config_->getAudioBitsPerSample(), 16);
  }

  void InitializeEncoder(a2dp_source_read_callback_t read_cb,
                         a2dp_source_enqueue_callback_t enqueue_cb) {
    tA2DP_ENCODER_INIT_PEER_PARAMS peer_params = {true, true, 1000};
    encoder_iface_->encoder_init(&peer_params, codec_config_, read_cb,
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
  A2dpCodecConfig* codec_config_;
  A2dpCodecs* a2dp_codecs_;
  tA2DP_ENCODER_INTERFACE* encoder_iface_;
  tA2DP_DECODER_INTERFACE* decoder_iface_;
};

TEST_F(A2dpOpusTest, a2dp_source_read_underflow) {
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
  InitializeEncoder(read_cb, enqueue_cb);
  uint64_t timestamp_us = bluetooth::common::time_gettimeofday_us();
  encoder_iface_->send_frames(timestamp_us);
  usleep(kA2dpTickUs);
  timestamp_us = bluetooth::common::time_gettimeofday_us();
  encoder_iface_->send_frames(timestamp_us);
  ASSERT_EQ(promise.get_future().wait_for(std::chrono::milliseconds(10)),
            std::future_status::timeout);
}

TEST_F(A2dpOpusTest, a2dp_enqueue_cb_is_invoked) {
  promise = {};
  auto read_cb = +[](uint8_t* p_buf, uint32_t len) -> uint32_t {
    ASSERT(GetReadSize() == len);
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
  InitializeEncoder(read_cb, enqueue_cb);
  uint64_t timestamp_us = bluetooth::common::time_gettimeofday_us();
  encoder_iface_->send_frames(timestamp_us);
  usleep(kA2dpTickUs);
  timestamp_us = bluetooth::common::time_gettimeofday_us();
  encoder_iface_->send_frames(timestamp_us);
  promise.get_future().wait();
}

TEST_F(A2dpOpusTest, decoded_data_cb_not_invoked_when_empty_packet) {
  auto data_cb = +[](uint8_t* p_buf, uint32_t len) { FAIL(); };
  InitializeDecoder(data_cb);
  std::vector<uint8_t> data;
  BT_HDR* packet = AllocateL2capPacket(data);
  decoder_iface_->decode_packet(packet);
  osi_free(packet);
}

TEST_F(A2dpOpusTest, decoded_data_cb_invoked) {
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
  InitializeEncoder(read_cb, enqueue_cb);

  uint64_t timestamp_us = bluetooth::common::time_gettimeofday_us();
  encoder_iface_->send_frames(timestamp_us);

  promise.get_future().wait();
  decoder_iface_->decode_packet(packet);
  osi_free(packet);
}

}  // namespace testing
}  // namespace bluetooth
