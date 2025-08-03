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
#include <stdlib.h>
#include <string.h>

#include <opus.h>

/* 4 bytes: packet length, 4 bytes: encoder final range */
constexpr int kSetupByteOffset = 8;
constexpr int kMaxFrameSample = 5760;
const int kSamplingRates[] = {8000, 12000, 16000, 24000, 48000};
constexpr int kNumberSamplingRates = sizeof(kSamplingRates) / sizeof(kSamplingRates[0]);

#ifdef MULTISTREAM
#include "opus_multistream.h"
#define OPUS_DEC_DATA_TYPE OpusMSDecoder
#define OPUS_DEC_DECODE_API opus_multistream_decode
#define OPUS_DEC_CREATE_API ms_opus_decoder_create
#define OPUS_DEC_DESTROY_API opus_multistream_decoder_destroy
static OpusMSDecoder *ms_opus_decoder_create(opus_int32 Fs, int channels, int *error) {
  int streams = 1;
  int coupledStreams = channels == 2;
  unsigned char mapping[256] = {0, 1};
  return opus_multistream_decoder_create(Fs, channels, streams, coupledStreams, mapping, error);
}
#else
#define OPUS_DEC_DATA_TYPE OpusDecoder
#define OPUS_DEC_DECODE_API opus_decode
#define OPUS_DEC_CREATE_API opus_decoder_create
#define OPUS_DEC_DESTROY_API opus_decoder_destroy
#endif

class Codec {
 public:
  Codec() = default;
  ~Codec() { deInitDecoder(); }
  bool initDecoder(const uint8_t *data);
  void decodeFrames(const uint8_t *data, size_t size);
  void deInitDecoder();

 private:
  int mSamplingRate;
  int mNoOfChannels;
  OPUS_DEC_DATA_TYPE *mDec = nullptr;
  opus_int16 *mPcm = nullptr;
};

bool Codec::initDecoder(const uint8_t *data) {
  const uint8_t *tocPtr = &data[kSetupByteOffset];
  const int bandwidth = opus_packet_get_bandwidth(tocPtr);
  int samplingRateIndex = bandwidth - OPUS_BANDWIDTH_NARROWBAND;

  /*bounds check on samplingRateIndex*/
  if ((samplingRateIndex >= 0) && (samplingRateIndex < kNumberSamplingRates)) {
    mSamplingRate = kSamplingRates[samplingRateIndex];
  } else {
    mSamplingRate = 8000;  // set to a default value
  }

  mNoOfChannels = opus_packet_get_nb_channels(tocPtr);
  if ((mNoOfChannels != 1) && (mNoOfChannels != 2)) {
    mNoOfChannels = 1;
  }

  int err;
  mDec = OPUS_DEC_CREATE_API(mSamplingRate, mNoOfChannels, &err);
  if (!mDec || err != OPUS_OK) {
    return false;
  }
  size_t sizePcm = sizeof(*mPcm) * kMaxFrameSample * mNoOfChannels;
  mPcm = static_cast<opus_int16 *>(malloc(sizePcm));
  if (!mPcm) {
    return false;
  }
  memset(mPcm, 0x0, sizePcm);
  return true;
}

void Codec::deInitDecoder() {
  OPUS_DEC_DESTROY_API(mDec);
  mDec = nullptr;
  if (mPcm) {
    free(mPcm);
  }
  mPcm = nullptr;
}

void Codec::decodeFrames(const uint8_t *data, size_t size) {
  (void)OPUS_DEC_DECODE_API(mDec, data, size, mPcm, kMaxFrameSample, 0 /*fec*/);
}

extern "C" int LLVMFuzzerTestOneInput(const uint8_t *data, size_t size) {
  if (size < kSetupByteOffset + 1) {
    return 0;
  }
  Codec *codec = new Codec();
  if (!codec) {
    return 0;
  }
  if (codec->initDecoder(data)) {
    codec->decodeFrames(data, size);
  }
  delete codec;
  return 0;
}
