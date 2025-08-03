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

#ifndef ANDROID_EFFECT_HAPTICGENERATOR_CORE_H_
#define ANDROID_EFFECT_HAPTICGENERATOR_CORE_H_

#include <system/audio_effect.h>

#if __cplusplus
extern "C" {
#endif

// The HapticGenerator type UUID is not defined by OpenSL ES and has been
// generated from http://www.itu.int/ITU-T/asn1/uuid.html
static const effect_uuid_t FX_IID_HAPTICGENERATOR_ = { 0x1411e6d6, 0xaecd, 0x4021, 0xa1cf,
        { 0xa6, 0xac, 0xeb, 0x0d, 0x71, 0xe5 } };
const effect_uuid_t * const FX_IID_HAPTICGENERATOR = &FX_IID_HAPTICGENERATOR_;

/* enumerated parameter settings for haptic generator effect */
typedef enum
{
    HG_PARAM_HAPTIC_INTENSITY, // haptic intensity
    HG_PARAM_VIBRATOR_INFO, // vibrator information, e.g. resonant frequency, Q factor
} t_hapticgenerator_params;

#if __cplusplus
}  // extern "C"
#endif


#endif /*ANDROID_EFFECT_HAPTICGENERATOR_CORE_H_*/
