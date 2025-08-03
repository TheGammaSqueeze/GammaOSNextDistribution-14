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

#include <string.h>
#include <math.h>
#include "audio_ops.h"

namespace android {
namespace hardware {
namespace audio {
namespace CPP_VERSION {
namespace implementation {
namespace aops {

void multiplyByVolume(const float volume, int16_t *a, const size_t n) {
    constexpr int_fast32_t kDenominator = 32768;
    const int_fast32_t numerator =
        static_cast<int_fast32_t>(round(volume * kDenominator));

    if (numerator >= kDenominator) {
        return;  // (numerator > kDenominator) is not expected
    } else if (numerator <= 0) {
        memset(a, 0, n * sizeof(*a));
        return;  // (numerator < 0) is not expected
    }

    int16_t *end = a + n;

    // The unroll code below is to save on CPU branch instructions.
    // 8 is arbitrary chosen.

#define STEP \
        *a = (*a * numerator + kDenominator / 2) / kDenominator; \
        ++a

    switch (n % 8) {
    case 7:  goto l7;
    case 6:  goto l6;
    case 5:  goto l5;
    case 4:  goto l4;
    case 3:  goto l3;
    case 2:  goto l2;
    case 1:  goto l1;
    default: break;
    }

    while (a < end) {
        STEP;
l7:     STEP;
l6:     STEP;
l5:     STEP;
l4:     STEP;
l3:     STEP;
l2:     STEP;
l1:     STEP;
    }

#undef STEP
}

}  // namespace aops
}  // namespace implementation
}  // namespace CPP_VERSION
}  // namespace audio
}  // namespace hardware
}  // namespace android
