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

#include "aptXHDbtenc.h"

#define BYTES_PER_CODEWORD 24

class LibAptxHdEncTest : public ::testing::Test {
 private:
 protected:
  void* aptxhdbtenc = nullptr;
  void SetUp() override {
    aptxhdbtenc = malloc(SizeofAptxhdbtenc());
    ASSERT_NE(aptxhdbtenc, nullptr);
    ASSERT_EQ(aptxhdbtenc_init(aptxhdbtenc, 0), 0);
  }

  void TearDown() override { free(aptxhdbtenc); }

  void codeword_cmp(const uint8_t p[BYTES_PER_CODEWORD],
                    const uint32_t codeword[2]) {
    uint32_t pcmL[4];
    uint32_t pcmR[4];
    for (size_t i = 0; i < 4; i++) {
      pcmL[i] = ((p[0] << 0) | (p[1] << 8) | (((int8_t)p[2]) << 16));
      p += 3;
      pcmR[i] = ((p[0] << 0) | (p[1] << 8) | (((int8_t)p[2]) << 16));
      p += 3;
    }
    uint32_t encoded_sample[2];
    aptxhdbtenc_encodestereo(aptxhdbtenc, &pcmL, &pcmR, (void*)encoded_sample);

    ASSERT_EQ(encoded_sample[0], codeword[0]);
    ASSERT_EQ(encoded_sample[1], codeword[1]);
  }
};

TEST_F(LibAptxHdEncTest, encode_fake_data) {
  const char input[] =
      "012345678901234567890123456789012345678901234567890123456789012345678901"
      "234567890123456789012345678901234567890123456789";
  const uint32_t aptxhd_codeword[] = {7585535, 7585535, 32767,   32767,
                                      557055,  557027,  7586105, 7586109,
                                      9748656, 10764446};

  ASSERT_EQ((sizeof(input) - 1) % BYTES_PER_CODEWORD, 0);
  ASSERT_EQ((sizeof(input) - 1) / BYTES_PER_CODEWORD,
            sizeof(aptxhd_codeword) / sizeof(uint32_t) / 2);

  size_t idx = 0;

  uint8_t pcm[BYTES_PER_CODEWORD];
  while (idx * BYTES_PER_CODEWORD < sizeof(input) - 1) {
    memcpy(pcm, input + idx * BYTES_PER_CODEWORD, BYTES_PER_CODEWORD);
    codeword_cmp(pcm, aptxhd_codeword + idx * 2);
    ++idx;
  }
}
