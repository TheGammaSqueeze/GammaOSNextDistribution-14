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

#ifndef ANDROID_EFFECT_AGC2_CORE_H_
#define ANDROID_EFFECT_AGC2_CORE_H_

#include <system/audio_effect.h>

#if __cplusplus
extern "C" {
#endif

// The AGC type UUID is not defined by OpenSL ES and has been generated from
// http://www.itu.int/ITU-T/asn1/uuid.html
static const effect_uuid_t FX_IID_AGC2_ =
    { 0xae3c653b, 0xbe18, 0x4ab8, 0x8938, { 0x41, 0x8f, 0x0a, 0x7f, 0x06, 0xac } };
const effect_uuid_t * const FX_IID_AGC2 = &FX_IID_AGC2_;

typedef enum
{
    AGC2_PARAM_FIXED_DIGITAL_GAIN,                 // fixed digital gain in millibel
    AGC2_PARAM_ADAPT_DIGI_LEVEL_ESTIMATOR,         // adaptive digital level estimator
    AGC2_PARAM_ADAPT_DIGI_EXTRA_SATURATION_MARGIN, // saturation margin in millibel
    AGC2_PARAM_PROPERTIES
} agc2_params_t;


//t_agc2_settings groups all current agc2 settings for backup and restore.
typedef struct {
    float          fixedDigitalGain;
    uint32_t       level_estimator;
    float          extraSaturationMargin;
} agc2_settings_t;

#if __cplusplus
}  // extern "C"
#endif

#endif /*ANDROID_EFFECT_AGC2_CORE_H_*/
