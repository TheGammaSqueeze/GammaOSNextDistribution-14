/*
 * Copyright 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "mock_codec_interface.h"

namespace le_audio {

struct CodecInterface::Impl : public MockCodecInterface {
 public:
  Impl(const types::LeAudioCodecId& codec_id) {
    output_channel_data_.resize(1);
  };
  ~Impl() = default;

  std::vector<int16_t>& GetDecodedSamples() { return output_channel_data_; }
  std::vector<int16_t> output_channel_data_;
};

CodecInterface::CodecInterface(const types::LeAudioCodecId& codec_id) {
  impl = new Impl(codec_id);
}
CodecInterface::~CodecInterface() { delete impl; }
bool CodecInterface::IsReady() { return impl->IsReady(); };
CodecInterface::Status CodecInterface::InitEncoder(
    const LeAudioCodecConfiguration& pcm_config,
    const LeAudioCodecConfiguration& codec_config) {
  return impl->InitEncoder(pcm_config, codec_config);
}
CodecInterface::Status CodecInterface::InitDecoder(
    const LeAudioCodecConfiguration& codec_config,
    const LeAudioCodecConfiguration& pcm_config) {
  return impl->InitDecoder(codec_config, pcm_config);
}
std::vector<int16_t>& CodecInterface::GetDecodedSamples() {
  return impl->GetDecodedSamples();
}
CodecInterface::Status CodecInterface::Decode(uint8_t* data, uint16_t size) {
  return impl->Decode(data, size);
}
CodecInterface::Status CodecInterface::Encode(const uint8_t* data, int stride,
                                              uint16_t out_size,
                                              std::vector<int16_t>* out_buffer,
                                              uint16_t out_offset) {
  return impl->Encode(data, stride, out_size, out_buffer, out_offset);
}
void CodecInterface::Cleanup() { return impl->Cleanup(); }

uint16_t CodecInterface::GetNumOfSamplesPerChannel() {
  return impl->GetNumOfSamplesPerChannel();
};
uint8_t CodecInterface::GetNumOfBytesPerSample() {
  return impl->GetNumOfBytesPerSample();
};
}  // namespace le_audio
