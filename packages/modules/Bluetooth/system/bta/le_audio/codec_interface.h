/******************************************************************************
 *
 * Copyright (c) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ******************************************************************************/

#pragma once

#include <stdint.h>

#include <vector>

#include "audio_hal_client/audio_hal_client.h"
#include "le_audio_types.h"

namespace le_audio {

/* CodecInterface provides a thin abstraction layer above the codec instance. It
 * manages the output buffers internally and resizes them automatically when
 * needed.
 * Multi-channel stream encoding requires multiple CodecInterface instances, but
 * even then it is still possible to encode the stream data into a single output
 * buffer. Thanks to the optional parameters to the encode() method, the
 * internal buffer of the first instance can be used as an output buffer by the
 * second instance, as long as equal life time of both instances is guaranteed.
 *
 */
class CodecInterface {
 public:
  enum class Status {
    STATUS_ERR_CODEC_NOT_READY = -128,
    STATUS_ERR_INVALID_CODEC_ID = -127,
    STATUS_ERR_CODING_ERROR = -1,
    STATUS_OK = 0,
  };

  CodecInterface(const types::LeAudioCodecId& codec_id);
  virtual ~CodecInterface();
  static std::unique_ptr<CodecInterface> CreateInstance(
      const types::LeAudioCodecId& codec_id) {
    return std::make_unique<CodecInterface>(codec_id);
  }
  virtual CodecInterface::Status InitEncoder(
      const LeAudioCodecConfiguration& pcm_config,
      const LeAudioCodecConfiguration& codec_config);
  virtual CodecInterface::Status InitDecoder(
      const LeAudioCodecConfiguration& codec_config,
      const LeAudioCodecConfiguration& pcm_config);
  virtual CodecInterface::Status Encode(
      const uint8_t* data, int stride, uint16_t out_size,
      std::vector<int16_t>* out_buffer = nullptr, uint16_t out_offset = 0);
  virtual CodecInterface::Status Decode(uint8_t* data, uint16_t size);
  virtual void Cleanup();
  virtual bool IsReady();
  virtual uint16_t GetNumOfSamplesPerChannel();
  virtual uint8_t GetNumOfBytesPerSample();
  virtual std::vector<int16_t>& GetDecodedSamples();

 private:
  struct Impl;
  Impl* impl;
};
}  // namespace le_audio
