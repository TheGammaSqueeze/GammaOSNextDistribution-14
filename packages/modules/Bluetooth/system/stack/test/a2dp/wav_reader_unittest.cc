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

#include "wav_reader.h"

#include <gtest/gtest.h>

#include <memory>
#include <string>

#include "test_util.h"

namespace {
constexpr uint32_t kSampleRate = 44100;
constexpr char kWavFile[] = "test/a2dp/raw_data/pcm0844s.wav";
}  // namespace

namespace bluetooth {
namespace testing {

class WavReaderTest : public ::testing::Test {
 protected:
  void SetUp() override {}

  void TearDown() override {}
};

TEST_F(WavReaderTest, read_wav_header) {
  std::unique_ptr<WavReader> wav_file = std::make_unique<WavReader>(GetWavFilePath(kWavFile).c_str());
  ASSERT_EQ(wav_file->GetHeader().sample_rate, kSampleRate);
}

TEST_F(WavReaderTest, check_wav_sample_count) {
  std::unique_ptr<WavReader> wav_file = std::make_unique<WavReader>(GetWavFilePath(kWavFile).c_str());
  ASSERT_EQ(wav_file->GetHeader().subchunk2_size, wav_file->GetSampleCount());
}
}  // namespace testing
}  // namespace bluetooth
