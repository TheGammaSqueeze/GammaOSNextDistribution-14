/*
 * Copyright 2021 The Android Open Source Project
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

#include "phash_fingerprinter.h"

#include "array2d.h"
#include "fast_dct-inl.h"
#include "fast_dct.h"
#include "phash_config.h"

namespace android {

constexpr int kResizedLength = 8;

int64_t PhashFingerprinter::GenerateFingerprint(const uint8_t* frame) {
    // 1. verify input
    if (frame == nullptr) {
        return 0;
    }

    // 2. Compute the DCT
    Array2D<const uint8_t> dct_in(frame);
    Array2D<int16_t> dct_out(dct_buf_);

    FastDCT<const uint8_t, int16_t> dct(kImageLength);
    dct.ForwardTransform2D(dct_in, &dct_out);

    // 3. Simplify the DCT by only using the coefficients in the top-left 8x8
    // window (i.e. those for the lowest frequencies).
    double total = 0;
    for (int x = 0; x < kResizedLength; x++) {
        for (int y = 0; y < kResizedLength; y++) {
            total += dct_out(x, y);
        }
    }
    // Remove the DC component at (0, 0) from total.
    total -= dct_out(0, 0);

    // 4. Compute the average
    double average = total / ((kResizedLength * kResizedLength) - 1);

    // 5. Further simplify the DCT by setting each value to 0 or 1 based on
    // whether the DCT value is larger than the average, then store each value as
    // a bit in an int64.
    int64_t fingerprint = 0;
    for (int x = 0; x < kResizedLength; x++) {
        for (int y = 0; y < kResizedLength; y++) {
            fingerprint <<= 1;
            fingerprint |= (dct_out(x, y) > average ? 1 : 0);
        }
    }
    // Remove the DC component at (0, 0)
    fingerprint &= ~(1ULL << 63);

    return fingerprint;
}

} // namespace android
