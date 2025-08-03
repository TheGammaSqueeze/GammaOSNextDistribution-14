/*
 * Copyright (C) 2020 The Android Open Source Project
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

#include <fuzzer/FuzzedDataProvider.h>

#include "../g722_enc_dec.h"

namespace {

uint32_t get_rate_from_fdp(FuzzedDataProvider* fdp) {
  uint32_t rate = fdp->ConsumeIntegralInRange<uint32_t>(
      0, 3);  // Currently 3 different bit rates are available in G.722 codec
  switch (rate) {
    case 0:
      return 48000;
    case 1:
      return 56000;
    default:
      return 64000;
  }
}

void fuzz_encode(FuzzedDataProvider* fdp) {
  uint32_t rate = get_rate_from_fdp(fdp);
  std::vector<uint8_t> buff = fdp->ConsumeRemainingBytes<uint8_t>();

  int num_samples =
      buff.size() / (2 /*bytes_per_sample*/ * 2 /*number of channels*/);

  // The G.722 codec accept only even number of samples for encoding
  if (num_samples % 2 != 0) {
    num_samples--;
  }

  // Making channel data from buffer
  std::vector<uint16_t> channel_data;

  for (int i = 0; i < num_samples; i++) {
    const uint8_t* sample = buff.data() + i * 2;
    int16_t left = (int16_t)((*(sample + 1) << 8) + *sample) >> 1;

    sample += 2;
    int16_t right = (int16_t)((*(sample + 1) << 8) + *sample) >> 1;

    uint16_t mono_data = (int16_t)(((uint32_t)left + (uint32_t)right) >> 1);
    channel_data.push_back(mono_data);
  }

  // Encoder Initialization
  g722_encode_state_t* encoder_state = nullptr;
  encoder_state = g722_encode_init(nullptr, rate, G722_PACKED);

  // Encode
  std::vector<uint8_t> encoded_data;
  // Magic number is used in api, It should basically fit the number generated
  // by this formula : num_channels * sample_rate * data_interval_ms
  // * (bit_rate / 8)) / 1000 as mentioned in hearing_aid.cc And if we fit all
  // the values in the above formula, the max value we can get is 1920. And I
  // used "size" of the input that libfuzzer generates as the initial
  // parameter to resize
  encoded_data.resize(buff.size());
  int encoded_size =
      g722_encode(encoder_state, encoded_data.data(),
                  (const int16_t*)channel_data.data(), channel_data.size());
  encoded_data.resize(encoded_size);

  // Encoder release
  if (encoder_state != nullptr) {
    g722_encode_release(encoder_state);
    encoder_state = nullptr;
  }
}

void fuzz_decode(FuzzedDataProvider* fdp) {
  // Get values for primitive types from fdp
  uint32_t rate = get_rate_from_fdp(fdp);
  int options = fdp->ConsumeIntegral<int>();
  uint16_t gain = fdp->ConsumeIntegral<uint16_t>();

  // Decoder Initialization
  g722_decode_state_t* decoder_state = nullptr;
  decoder_state = g722_decode_init(decoder_state, rate, options);

  std::vector<uint8_t> encoded_input = fdp->ConsumeRemainingBytes<uint8_t>();
  int out_len =
      encoded_input.size() * 2 /*bytes_per_sample*/ * 2 /*number of channels*/;

  // Decode
  std::vector<int16_t> decoded_output;
  decoded_output.resize(out_len);
  int decoded_size = g722_decode(decoder_state, decoded_output.data(),
                                 (const uint8_t*)encoded_input.data(),
                                 encoded_input.size(), gain);
  if (decoded_size > decoded_output.size()) {
    abort();
  }

  // Encoder release
  if (decoder_state != nullptr) {
    g722_decode_release(decoder_state);
    decoder_state = nullptr;
  }
}
}  // namespace

extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data, size_t size) {
  FuzzedDataProvider fdp(data, size);
  fdp.ConsumeBool() ? fuzz_encode(&fdp) : fuzz_decode(&fdp);
  return 0;
}
