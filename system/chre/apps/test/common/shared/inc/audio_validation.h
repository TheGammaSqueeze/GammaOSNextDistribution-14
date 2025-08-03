/*
 * Copyright (C) 2023 The Android Open Source Project
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

#ifndef CHRE_TEST_SHARED_AUDIO_VALIDATION_H_
#define CHRE_TEST_SHARED_AUDIO_VALIDATION_H_

#include <cinttypes>
#include <cstddef>

namespace chre {
namespace test_shared {

/**
 * Check if the audio samples are all zeros.
 *
 * @return true on check passing.
 */
bool checkAudioSamplesAllZeros(const int16_t *data, const size_t dataLen);

/**
 * Check if adjacent audio samples are unique.
 *
 * @return true on check pass.
 */
bool checkAudioSamplesAllSame(const int16_t *data, const size_t dataLen);

}  // namespace test_shared
}  // namespace chre

#endif  // CHRE_TEST_SHARED_AUDIO_VALIDATION_H_
