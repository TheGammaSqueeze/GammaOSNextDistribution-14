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

#include <fstream>
#include <vector>

namespace bluetooth {
namespace testing {

struct WavHeader {
  // RIFF chunk descriptor
  uint8_t chunk_id[4];
  uint32_t chunk_size;
  uint8_t chunk_format[4];
  // "fmt" sub-chunk
  uint8_t subchunk1_id[4];
  uint32_t subchunk1_size;
  uint16_t audio_format;
  uint16_t num_channels;
  uint32_t sample_rate;
  uint32_t byte_rate;
  uint16_t block_align;
  uint16_t bits_per_sample;
  // "data" sub-chunk
  uint8_t subchunk2_id[4];
  uint32_t subchunk2_size;
};

namespace {
constexpr size_t kWavHeaderSize = sizeof(WavHeader);
}

class WavReader {
 public:
  WavReader(const char* filename);
  ~WavReader();
  WavHeader GetHeader() const;
  uint8_t* GetSamples();
  size_t GetSampleCount();

 private:
  std::ifstream wavFile_;
  WavHeader header_;
  std::vector<uint8_t> samples_;

  void ReadSamples();
};

}  // namespace testing
}  // namespace bluetooth
