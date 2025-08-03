/*
 * Copyright 2023, The Android Open Source Project
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

#pragma once

namespace android {

inline constexpr uint32_t kSpdifEncodedChannelCount = 2u;
inline constexpr uint32_t kSpdifDataTypeAc3 = 1u;
inline constexpr uint32_t kSpdifDataTypeEac3 = 21u;
inline constexpr uint32_t kSpdifRateMultiplierEac3 = 4u;

// Burst preamble defined in IEC61937-1
inline constexpr uint16_t kSpdifSync1 = 0xF872;  // Pa
inline constexpr uint16_t kSpdifSync2 = 0x4E1F;  // Pb

// Returns the SPDIF rate multiplier for the audio format, or zero if not supported.
inline int spdif_rate_multiplier(audio_format_t format) {
    switch (format) {
        case AUDIO_FORMAT_E_AC3:
        case AUDIO_FORMAT_E_AC3_JOC:
            return 4;
        case AUDIO_FORMAT_AC3:
        case AUDIO_FORMAT_DTS:
        case AUDIO_FORMAT_DTS_HD:
            return 1;
        default:
            return 0;
    }
}

}  // namespace android
