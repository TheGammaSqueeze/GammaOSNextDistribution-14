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

#include <assert.h>
#include <stdint.h>

#include <system/audio.h>

// Ensure that bit mask enum types have 32-bit size.
// There is a lot of code that assumes this.

// Note that audio_devices_t is not a pure mask type since R,
// however there is legacy code which can mix its usage with uint32_t.

#define AUDIO_ENUM_MASKS_LIST(V)                \
    V(audio_channel_mask_t)                     \
    V(audio_channel_representation_t)           \
    V(audio_devices_t)                          \
    V(audio_flags_mask_t)                       \
    V(audio_gain_mode_t)                        \
    V(audio_input_flags_t)                      \
    V(audio_output_flags_t)

#define AUDIO_ASSERT_ENUM_MASK_SIZE(t)                                  \
    static_assert(sizeof(t) == sizeof(uint32_t), "The size of \'" #t "\' type must be 32 bits");
AUDIO_ENUM_MASKS_LIST(AUDIO_ASSERT_ENUM_MASK_SIZE);
#undef AUDIO_ASSERT_ENUM_MASK_SIZE
#undef AUDIO_ENUM_MASKS_LIST
