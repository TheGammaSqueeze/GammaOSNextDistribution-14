/******************************************************************************
 *
 * Copyright (C) 2020 The Android Open Source Project
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
 *****************************************************************************
 * Originally developed and contributed by Ittiam Systems Pvt. Ltd, Bangalore
 */
#include <stdint.h>
#include <string.h>
#include "gsm.h"

constexpr int16_t kGsmInFrameSize = 33;
constexpr int16_t kGsmInFrameSizeMinus1 = kGsmInFrameSize - 1;
constexpr int16_t kGsmOutFrameSize = 160;
constexpr int16_t kGsmTotalInFrameSize = (kGsmInFrameSize + kGsmInFrameSizeMinus1);
constexpr int16_t kGsmTotalOutFrameSize = (kGsmOutFrameSize * 2);

class Codec {
 public:
  Codec() = default;
  ~Codec() { deInitDecoder(); }
  bool initDecoder();
  void decodeFrames(const uint8_t *data, size_t size);
  void deInitDecoder();

 private:
  gsm mGsm = nullptr;
};

bool Codec::initDecoder() {
  mGsm = gsm_create();
  if (mGsm) {
    int msopt = 1;
    gsm_option(mGsm, GSM_OPT_WAV49, &msopt);
    return true;
  } else {
    return false;
  }
}

void Codec::decodeFrames(const uint8_t *data, size_t size) {
  uint8_t firstByte = *data;
  int32_t verbosity = firstByte & 0x01;
  int32_t fast = (firstByte >> 1) & 0x01;
  gsm_option(mGsm, GSM_OPT_VERBOSE, &verbosity);
  gsm_option(mGsm, GSM_OPT_FAST, &fast);
  gsm_byte *readPointer = const_cast<uint8_t *>(data);
  uint8_t tmpData[kGsmTotalInFrameSize];
  gsm_signal out[kGsmTotalOutFrameSize];

  while (size > 0) {
    if (size < kGsmTotalInFrameSize) {
      memset(tmpData, 0, sizeof(tmpData));
      memcpy(tmpData, data, size);
      size = kGsmTotalInFrameSize;
      readPointer = tmpData;
    }
    gsm_decode(mGsm, readPointer, out);
    readPointer += kGsmInFrameSize;
    size -= kGsmInFrameSize;

    gsm_decode(mGsm, readPointer, out);
    readPointer += kGsmInFrameSizeMinus1;
    size -= kGsmInFrameSizeMinus1;
  }
}

void Codec::deInitDecoder() {
  gsm_destroy(mGsm);
  mGsm = nullptr;
}

extern "C" int LLVMFuzzerTestOneInput(const uint8_t *data, size_t size) {
  if (size < 1) {
    return 0;
  }
  Codec *codec = new Codec();
  if (!codec) {
    return 0;
  }
  if (codec->initDecoder()) {
    codec->decodeFrames(data, size);
  }
  delete codec;
  return 0;
}
