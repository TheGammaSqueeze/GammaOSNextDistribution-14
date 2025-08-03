/*
 * Copyright (C) 2022 The Android Open Source Project
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
#ifndef ANDROID_HARDWARE_VIBRATOR_TEST_UTILS_H
#define ANDROID_HARDWARE_VIBRATOR_TEST_UTILS_H

#include <cmath>

#include "types.h"

static inline EffectScale toScale(float amplitude, float maximum) {
    float ratio = 100; /* Unit: % */
    if (maximum != 0)
        ratio = amplitude / maximum * 100;

    if (maximum == 0 || ratio > 100)
        ratio = 100;

    return std::round(ratio);
}

static inline EffectScale levelToScale(EffectLevel level) {
    return toScale(level, 100);
}

static inline EffectScale amplitudeToScale(EffectAmplitude amplitude) {
    return toScale(amplitude, 1.0f);
}

static inline uint32_t msToCycles(EffectDuration ms) {
    return ms * 48;
}

#endif  // ANDROID_HARDWARE_VIBRATOR_TEST_UTILS_H
