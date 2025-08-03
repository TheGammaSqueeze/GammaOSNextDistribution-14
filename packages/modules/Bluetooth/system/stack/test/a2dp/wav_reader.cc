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

#include <iostream>
#include <iterator>

#include "os/files.h"
#include "os/log.h"

namespace bluetooth {
namespace testing {

WavReader::WavReader(const char* filename) {
  if (os::FileExists(filename)) {
    wavFile_.open(filename, std::ios::in | std::ios::binary);
    wavFile_.read((char*)&header_, kWavHeaderSize);
    ReadSamples();
  } else {
    ASSERT_LOG(false, "File %s does not exist!", filename);
  }
}

WavReader::~WavReader() {
  if (wavFile_.is_open()) {
    wavFile_.close();
  }
}

WavHeader WavReader::GetHeader() const { return header_; }

void WavReader::ReadSamples() {
  std::istreambuf_iterator<char> start{wavFile_}, end;
  samples_ = std::vector<uint8_t>(start, end);
}

uint8_t* WavReader::GetSamples() { return &samples_[0]; }

size_t WavReader::GetSampleCount() { return samples_.size(); }

}  // namespace testing
}  // namespace bluetooth
