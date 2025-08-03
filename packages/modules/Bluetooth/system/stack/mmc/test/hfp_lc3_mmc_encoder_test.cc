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

#include "mmc/codec_server/hfp_lc3_mmc_encoder.h"

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

const int kInputLen = mmc::HFP_LC3_PCM_BYTES;
const int kOutputLen = mmc::HFP_LC3_PKT_FRAME_LEN;
const uint8_t kInputBuf[kInputLen] = {0};
static uint8_t kOutputBuf[kOutputLen] = {0};

class HfpLc3EncoderTest : public Test {
 public:
 protected:
  void SetUp() override {
    reset_mock_function_count_map();
    encoder_ = std::make_unique<mmc::HfpLc3Encoder>();
  }
  void TearDown() override { encoder_.release(); }
  std::unique_ptr<mmc::HfpLc3Encoder> encoder_ = nullptr;
};

class HfpLc3EncoderWithInitTest : public HfpLc3EncoderTest {
 public:
 protected:
  void SetUp() override {
    test::mock::osi_allocator::osi_malloc.body = [&](size_t size) {
      this->lc3_encoder_ = new struct lc3_encoder;
      return (void*)this->lc3_encoder_;
    };
    test::mock::embdrv_lc3::lc3_setup_encoder.body =
        [this](int dt_us, int sr_hz, int sr_pcm_hz, void* mem) {
          return this->lc3_encoder_;
        };
    test::mock::osi_allocator::osi_free_and_reset.body = [&](void** p_ptr) {
      delete this->lc3_encoder_;
      lc3_encoder_ = nullptr;
      *p_ptr = nullptr;
      return;
    };
    std::fill(kOutputBuf, kOutputBuf + kOutputLen, 1);

    HfpLc3EncoderTest::SetUp();
    mmc::ConfigParam config;
    *config.mutable_hfp_lc3_encoder_param() = mmc::Lc3Param();
    ASSERT_EQ(encoder_->init(config), mmc::HFP_LC3_PCM_BYTES);
  }
  void TearDown() override {
    HfpLc3EncoderTest::TearDown();
    test::mock::embdrv_lc3::lc3_setup_encoder = {};
    test::mock::osi_allocator::osi_malloc = {};
    test::mock::osi_allocator::osi_free_and_reset = {};
    std::fill(kOutputBuf, kOutputBuf + kOutputLen, 0);
  }
  struct lc3_encoder* lc3_encoder_ = nullptr;
};

TEST_F(HfpLc3EncoderTest, InitWrongCodec) {
  mmc::ConfigParam config;
  *config.mutable_hfp_lc3_decoder_param() = mmc::Lc3Param();

  int ret = encoder_->init(config);
  EXPECT_EQ(ret, -EINVAL);
  EXPECT_EQ(get_func_call_count("lc3_setup_encoder"), 0);
}

TEST_F(HfpLc3EncoderTest, InitWrongConfig) {
  mmc::ConfigParam config;
  *config.mutable_hfp_lc3_encoder_param() = mmc::Lc3Param();

  // lc3_setup_encoder failed due to wrong parameters (returned nullptr).
  test::mock::embdrv_lc3::lc3_setup_encoder.body =
      [](int dt_us, int sr_hz, int sr_pcm_hz, void* mem) { return nullptr; };

  int ret = encoder_->init(config);
  EXPECT_EQ(ret, -EINVAL);
  EXPECT_EQ(get_func_call_count("lc3_setup_encoder"), 1);

  test::mock::embdrv_lc3::lc3_setup_encoder = {};
}

TEST_F(HfpLc3EncoderTest, InitSuccess) {
  mmc::ConfigParam config;
  *config.mutable_hfp_lc3_encoder_param() = mmc::Lc3Param();

  // lc3_setup_encoder returns encoder instance pointer.
  struct lc3_encoder lc3_encoder;
  test::mock::embdrv_lc3::lc3_setup_encoder.body =
      [&lc3_encoder](int dt_us, int sr_hz, int sr_pcm_hz, void* mem) {
        return &lc3_encoder;
      };

  int ret = encoder_->init(config);
  EXPECT_EQ(ret, mmc::HFP_LC3_PCM_BYTES);
  EXPECT_EQ(get_func_call_count("lc3_setup_encoder"), 1);

  test::mock::embdrv_lc3::lc3_setup_encoder = {};
}

TEST_F(HfpLc3EncoderWithInitTest, CleanUp) {
  encoder_->cleanup();
  EXPECT_EQ(get_func_call_count("osi_free_and_reset"), 1);
}

TEST_F(HfpLc3EncoderTest, TranscodeNullBuffer) {
  // Null input buffer.
  int ret = encoder_->transcode(nullptr, 0, kOutputBuf, kOutputLen);
  EXPECT_EQ(ret, -EINVAL);
  EXPECT_EQ(get_func_call_count("lc3_encode"), 0);

  // Null output buffer.
  ret = encoder_->transcode((uint8_t*)kInputBuf, kInputLen, nullptr, 0);
  EXPECT_EQ(ret, -EINVAL);
  EXPECT_EQ(get_func_call_count("lc3_encode"), 0);
}

TEST_F(HfpLc3EncoderWithInitTest, TranscodeWrongParam) {
  // lc3_encode failed (returned non-zero value).
  test::mock::embdrv_lc3::lc3_encode.return_value = 1;

  int ret = encoder_->transcode((uint8_t*)kInputBuf, kInputLen, kOutputBuf,
                                kOutputLen);
  EXPECT_EQ(ret, mmc::HFP_LC3_PKT_FRAME_LEN);
  EXPECT_THAT(kOutputBuf, Each(0));
  EXPECT_EQ(get_func_call_count("lc3_encode"), 1);

  test::mock::embdrv_lc3::lc3_encode = {};
}

TEST_F(HfpLc3EncoderWithInitTest, TranscodeSuccess) {
  // lc3_encode succeeded (return zero value).
  test::mock::embdrv_lc3::lc3_encode.return_value = 0;

  int ret = encoder_->transcode((uint8_t*)kInputBuf, kInputLen, kOutputBuf,
                                kOutputLen);
  EXPECT_EQ(ret, mmc::HFP_LC3_PKT_FRAME_LEN);
  EXPECT_THAT(kOutputBuf, Contains(Ne(0)));
  EXPECT_EQ(get_func_call_count("lc3_encode"), 1);

  test::mock::embdrv_lc3::lc3_encode = {};
}

}  // namespace
