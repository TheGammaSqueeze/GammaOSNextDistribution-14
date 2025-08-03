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

#include "mmc/codec_server/hfp_lc3_mmc_decoder.h"

#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include <cerrno>
#include <cstdint>

#include "mmc/codec_server/lc3_utils.h"
#include "mmc/proto/mmc_config.pb.h"
#include "mmc/test/mock/mock_embdrv_lc3.h"
#include "test/common/mock_functions.h"
#include "test/mock/mock_osi_allocator.h"

namespace {

using ::testing::Contains;
using ::testing::Each;
using ::testing::Ne;
using ::testing::Test;

const int kInputLen = mmc::HFP_LC3_PKT_FRAME_LEN;
const int kOutputLen = mmc::HFP_LC3_PCM_BYTES + 1;
const uint8_t kInputBuf[kInputLen] = {0};
static uint8_t kOutputBuf[kOutputLen] = {0};

class HfpLc3DecoderTest : public Test {
 public:
 protected:
  void SetUp() override {
    reset_mock_function_count_map();
    decoder_ = std::make_unique<mmc::HfpLc3Decoder>();
  }
  void TearDown() override { decoder_.release(); }
  std::unique_ptr<mmc::HfpLc3Decoder> decoder_ = nullptr;
};

class HfpLc3DecoderWithInitTest : public HfpLc3DecoderTest {
 public:
 protected:
  void SetUp() override {
    test::mock::osi_allocator::osi_malloc.body = [&](size_t size) {
      this->lc3_decoder_ = new struct lc3_decoder;
      return (void*)this->lc3_decoder_;
    };
    test::mock::embdrv_lc3::lc3_setup_decoder.body =
        [this](int dt_us, int sr_hz, int sr_pcm_hz, void* mem) {
          return this->lc3_decoder_;
        };
    test::mock::osi_allocator::osi_free_and_reset.body = [&](void** p_ptr) {
      delete this->lc3_decoder_;
      lc3_decoder_ = nullptr;
      *p_ptr = nullptr;
      return;
    };
    std::fill(kOutputBuf, kOutputBuf + kOutputLen, 1);

    HfpLc3DecoderTest::SetUp();
    mmc::ConfigParam config;
    *config.mutable_hfp_lc3_decoder_param() = mmc::Lc3Param();
    ASSERT_EQ(decoder_->init(config), mmc::HFP_LC3_PKT_FRAME_LEN);
  }
  void TearDown() override {
    HfpLc3DecoderTest::TearDown();
    test::mock::embdrv_lc3::lc3_setup_decoder = {};
    test::mock::osi_allocator::osi_malloc = {};
    test::mock::osi_allocator::osi_free_and_reset = {};
    std::fill(kOutputBuf, kOutputBuf + kOutputLen, 0);
  }
  struct lc3_decoder* lc3_decoder_ = nullptr;
};

TEST_F(HfpLc3DecoderTest, InitWrongCodec) {
  mmc::ConfigParam config;
  *config.mutable_hfp_lc3_encoder_param() = mmc::Lc3Param();

  int ret = decoder_->init(config);
  EXPECT_EQ(ret, -EINVAL);
  EXPECT_EQ(get_func_call_count("lc3_setup_decoder"), 0);
}

TEST_F(HfpLc3DecoderTest, InitWrongConfig) {
  mmc::ConfigParam config;
  *config.mutable_hfp_lc3_decoder_param() = mmc::Lc3Param();

  // lc3_setup_decoder failed due to wrong parameters (returned nullptr).
  test::mock::embdrv_lc3::lc3_setup_decoder.body =
      [](int dt_us, int sr_hz, int sr_pcm_hz, void* mem) { return nullptr; };

  int ret = decoder_->init(config);
  EXPECT_EQ(ret, -EINVAL);
  EXPECT_EQ(get_func_call_count("lc3_setup_decoder"), 1);

  test::mock::embdrv_lc3::lc3_setup_decoder = {};
}

TEST_F(HfpLc3DecoderTest, InitSuccess) {
  mmc::ConfigParam config;
  *config.mutable_hfp_lc3_decoder_param() = mmc::Lc3Param();

  // lc3_setup_decoder returns decoder instance pointer.
  struct lc3_decoder lc3_decoder;
  test::mock::embdrv_lc3::lc3_setup_decoder.body =
      [&lc3_decoder](int dt_us, int sr_hz, int sr_pcm_hz, void* mem) {
        return &lc3_decoder;
      };

  int ret = decoder_->init(config);
  EXPECT_EQ(ret, mmc::HFP_LC3_PKT_FRAME_LEN);
  EXPECT_EQ(get_func_call_count("lc3_setup_decoder"), 1);

  test::mock::embdrv_lc3::lc3_setup_decoder = {};
}

TEST_F(HfpLc3DecoderWithInitTest, CleanUp) {
  decoder_->cleanup();
  EXPECT_EQ(get_func_call_count("osi_free_and_reset"), 1);
}

TEST_F(HfpLc3DecoderTest, TranscodeNullBuffer) {
  // Null output buffer.
  int ret = decoder_->transcode((uint8_t*)kInputBuf, kInputLen, nullptr, 0);
  EXPECT_EQ(ret, -EINVAL);
  EXPECT_EQ(get_func_call_count("lc3_decode"), 0);
}

TEST_F(HfpLc3DecoderWithInitTest, TranscodeWrongParam) {
  // lc3_decode failed (returned value neither zero nor one).
  test::mock::embdrv_lc3::lc3_decode.return_value = -1;

  int ret = decoder_->transcode((uint8_t*)kInputBuf, kInputLen, kOutputBuf,
                                kOutputLen);
  EXPECT_EQ(ret, mmc::HFP_LC3_PCM_BYTES + 1);
  EXPECT_THAT(kOutputBuf, Each(0));
  EXPECT_EQ(get_func_call_count("lc3_decode"), 1);

  test::mock::embdrv_lc3::lc3_decode = {};
}

TEST_F(HfpLc3DecoderWithInitTest, TranscodePLC) {
  // lc3_decode conducted PLC (return one).
  test::mock::embdrv_lc3::lc3_decode.return_value = 1;

  int ret = decoder_->transcode((uint8_t*)kInputBuf, kInputLen, kOutputBuf,
                                kOutputLen);
  EXPECT_EQ(ret, mmc::HFP_LC3_PCM_BYTES + 1);
  EXPECT_EQ(kOutputBuf[0], 1);
  EXPECT_EQ(get_func_call_count("lc3_decode"), 1);

  test::mock::embdrv_lc3::lc3_decode = {};
}

TEST_F(HfpLc3DecoderWithInitTest, TranscodeSuccess) {
  // lc3_decode succeeded (return zero value).
  test::mock::embdrv_lc3::lc3_decode.return_value = 0;

  int ret = decoder_->transcode((uint8_t*)kInputBuf, kInputLen, kOutputBuf,
                                kOutputLen);
  EXPECT_EQ(ret, mmc::HFP_LC3_PCM_BYTES + 1);
  EXPECT_EQ(kOutputBuf[0], 0);
  EXPECT_THAT(kOutputBuf, Contains(Ne(0)));
  EXPECT_EQ(get_func_call_count("lc3_decode"), 1);

  test::mock::embdrv_lc3::lc3_decode = {};
}

}  // namespace
