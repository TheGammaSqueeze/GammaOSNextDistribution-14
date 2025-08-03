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

#include <fcntl.h>
#include <gtest/gtest.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include <fstream>
#include <iostream>

#include "aptXbtenc.h"

#define BYTES_PER_CODEWORD 16

class LibAptxEncTest : public ::testing::Test {
 private:
  void* aptxbtenc = nullptr;

 protected:
  void SetUp() override {
    aptxbtenc = malloc(SizeofAptxbtenc());
    ASSERT_NE(aptxbtenc, nullptr);
    ASSERT_EQ(aptxbtenc_init(aptxbtenc, 0), 0);
  }

  void TearDown() override { free(aptxbtenc); }

  void codeword_cmp(const uint16_t pcm[8], const uint32_t codeword) {
    uint32_t pcmL[4];
    uint32_t pcmR[4];
    for (size_t i = 0; i < 4; i++) {
      pcmL[i] = pcm[0];
      pcmR[i] = pcm[1];
      pcm += 2;
    }
    uint32_t encoded_sample;
    aptxbtenc_encodestereo(aptxbtenc, &pcmL, &pcmR, &encoded_sample);
    ASSERT_EQ(encoded_sample, codeword);
  }
};

TEST_F(LibAptxEncTest, encode_fake_data) {
  const char input[] =
      "012345678901234567890123456789012345678901234567890123456789012345678901"
      "23456789";
  const uint32_t aptx_codeword[] = {1270827967, 134154239, 670640127,
                                    1280265295, 2485752873};

  ASSERT_EQ((sizeof(input) - 1) % BYTES_PER_CODEWORD, 0);
  ASSERT_EQ((sizeof(input) - 1) / BYTES_PER_CODEWORD,
            sizeof(aptx_codeword) / sizeof(uint32_t));

  size_t idx = 0;

  uint16_t pcm[8];

  while (idx * BYTES_PER_CODEWORD < sizeof(input) - 1) {
    memcpy(pcm, input + idx * BYTES_PER_CODEWORD, BYTES_PER_CODEWORD);
    codeword_cmp(pcm, aptx_codeword[idx]);
    ++idx;
  }
}
