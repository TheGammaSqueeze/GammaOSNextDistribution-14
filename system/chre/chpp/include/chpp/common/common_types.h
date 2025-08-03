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

#ifndef CHPP_SERVICES_COMMON_TYPES_H_
#define CHPP_SERVICES_COMMON_TYPES_H_

#include <stdint.h>

#include "chpp/macros.h"

/**
 * When CHRE API structures are converted to CHPP wire format structures, this
 * type is used to replace a pointer to an array.
 *
 * For example, in the CHRE API we might have something like this:
 *  struct chreBar {
 *    uint32_t baz;
 *  };
 *  struct chreFooResult {
 *    uint8_t barCount;
 *    const struct chreBar *bars;
 *  };
 *
 * To serialize this object for CHPP, we replace the pointer with a ChppOffset:
 *
 *  struct ChppFooResult { // (packed)
 *   uint8_t barCount;
 *   struct ChppOffset bars;
 *  };
 *
 * Over the wire, this structure would then immediately be followed by barCount
 * instances of chreBar, and bars.offset would be set to sizeof(ChppFooResult)
 * and bars.length set to barCount * sizeof(chreBar).
 *
 * The same applies for things like null-terminated strings (when not defined
 * as a fixed-size nested array) - in that case, "length" includes null
 * termination.
 */
CHPP_PACKED_START
struct ChppOffset {
  //! Offset from the start of payload to the array
  uint16_t offset;

  //! Size of the array, in bytes
  uint16_t length;
} CHPP_PACKED_ATTR;
CHPP_PACKED_END

#endif  // CHPP_SERVICES_COMMON_TYPES_H_
