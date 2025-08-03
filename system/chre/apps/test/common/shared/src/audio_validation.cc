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

#include "audio_validation.h"

#ifndef LOG_TAG
#define LOG_TAG "[TestShared]"
#endif

namespace chre {
namespace test_shared {

bool checkAudioSamplesAllZeros(const int16_t *data, const size_t dataLen) {
  for (size_t i = 0; i < dataLen; ++i) {
    if (data[i] != 0) {
      return true;
    }
  }
  return false;
}

bool checkAudioSamplesAllSame(const int16_t *data, const size_t dataLen) {
  if (dataLen > 0) {
    const int16_t controlValue = data[0];
    for (size_t i = 1; i < dataLen; ++i) {
      if (data[i] != controlValue) {
        return true;
      }
    }
  }
  return false;
}

}  // namespace test_shared
}  // namespace chre
