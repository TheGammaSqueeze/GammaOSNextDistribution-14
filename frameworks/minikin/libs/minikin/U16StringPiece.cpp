/*
 * Copyright (C) 2024 The Android Open Source Project
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

#include "minikin/U16StringPiece.h"

#include <unicode/utf16.h>

#include "minikin/Characters.h"

namespace minikin {

uint32_t U16StringPiece::codePointAt(uint32_t pos) const {
    const uint16_t c = mData[pos];
    if (!U16_IS_SURROGATE(c)) {
        return c;  // Not a surrogate pair.
    }

    if (U16_IS_SURROGATE_TRAIL(c)) {  // isolated surrogate trail.
        return CHAR_REPLACEMENT_CHARACTER;
    }

    if (pos == mLength - 1) {  // isolated surrogate lead.
        return CHAR_REPLACEMENT_CHARACTER;
    }

    const uint16_t c2 = mData[pos + 1];
    if (!(U16_IS_SURROGATE(c2) && U16_IS_SURROGATE_LEAD(c2))) {  // isolated surrogate lead
        return CHAR_REPLACEMENT_CHARACTER;
    }

    return U16_GET_SUPPLEMENTARY(c, c2);
}

}  // namespace minikin
