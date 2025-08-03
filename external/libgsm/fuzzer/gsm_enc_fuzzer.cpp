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
#include <algorithm>

#include "gsm.h"

constexpr size_t kGsmInNumSamples = 160;
constexpr size_t kGsmInBufferSize = kGsmInNumSamples * sizeof(gsm_signal);

enum { IDX_VERBOSITY, IDX_FAST, IDX_LTP_CUT, IDX_WAV49, IDX_LAST };

class Codec {
   public:
    Codec() = default;
    ~Codec() { deInitEncoder(); }
    bool initEncoder();
    void encodeFrames(uint8_t *data, size_t size);
    void deInitEncoder();

   private:
    gsm mGsm = nullptr;
};

bool Codec::initEncoder() {
    mGsm = gsm_create();
    return mGsm ? true : false;
}

void Codec::encodeFrames(uint8_t *data, size_t size) {
    int32_t verbosity = data[IDX_VERBOSITY] & 0x01;
    int32_t fast = data[IDX_FAST] & 0x01;
    int32_t ltp_cut = data[IDX_LTP_CUT] & 0x01;
    int32_t wav49 = data[IDX_WAV49] & 0x01;
    gsm_option(mGsm, GSM_OPT_VERBOSE, &verbosity);
    gsm_option(mGsm, GSM_OPT_FAST, &fast);
    gsm_option(mGsm, GSM_OPT_LTP_CUT, &ltp_cut);
    gsm_option(mGsm, GSM_OPT_WAV49, &wav49);
    data += IDX_LAST;
    size -= IDX_LAST;

    while (size > 0) {
        gsm_signal tmpData[kGsmInNumSamples];
        gsm_frame out;
        size_t frameSize = std::min(size, kGsmInBufferSize);

        memcpy(tmpData, data, frameSize);
        if (frameSize < kGsmInBufferSize) {
            memset(reinterpret_cast<uint8_t *>(tmpData) + frameSize, data[0],
                   kGsmInBufferSize - frameSize);
        }
        gsm_encode(mGsm, tmpData, out);
        data += frameSize;
        size -= frameSize;
    }
}

void Codec::deInitEncoder() {
    if (mGsm) {
        gsm_destroy(mGsm);
        mGsm = nullptr;
    }
}

extern "C" int LLVMFuzzerTestOneInput(const uint8_t *data, size_t size) {
    if (size < IDX_LAST) {
        return 0;
    }
    Codec *codec = new Codec();
    if (!codec) {
        return 0;
    }
    if (codec->initEncoder()) {
        codec->encodeFrames(const_cast<uint8_t *>(data), size);
    }
    delete codec;
    return 0;
}
