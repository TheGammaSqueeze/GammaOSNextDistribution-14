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

#define LOG_TAG "Minikin"

#include "ScriptUtils.h"

#include <unicode/ubidi.h>
#include <unicode/uscript.h>
#include <unicode/utf16.h>
#include <unicode/utypes.h>

#include <algorithm>

#include "MinikinInternal.h"
#include "minikin/Emoji.h"

namespace minikin {

static hb_codepoint_t decodeUtf16(U16StringPiece text, Range range, uint32_t pos) {
    uint32_t result;
    U16_NEXT(text.data(), pos, range.getEnd(), result);
    if (U_IS_SURROGATE(result)) {  // isolated surrogate
        result = CHAR_REPLACEMENT_CHARACTER;
    }
    return static_cast<hb_codepoint_t>(result);
}

static UScriptCode getICUScript(uint32_t cp) {
    UErrorCode status = U_ZERO_ERROR;
    UScriptCode scriptCode = uscript_getScript(cp, &status);
    if (U_FAILURE(status)) [[unlikely]] {
        return USCRIPT_INVALID_CODE;
    }
    return scriptCode;
}

static hb_script_t getHbScript(uint32_t cp) {
    hb_unicode_funcs_t* unicode_func = hb_unicode_funcs_get_default();
    return hb_unicode_script(unicode_func, cp);
}

// static
std::pair<uint32_t, hb_script_t> ScriptText::getScriptRun(U16StringPiece text, Range range,
                                                          uint32_t pos) {
    if (!range.contains(pos)) {
        return std::make_pair(range.getEnd(), HB_SCRIPT_UNKNOWN);
    }

    uint32_t cp = decodeUtf16(text, range, pos);
    UScriptCode current_script = getICUScript(cp);
    hb_script_t current_hb_script = getHbScript(cp);
    uint32_t i;
    for (i = pos + U16_LENGTH(cp); i < range.getEnd(); i += U16_LENGTH(cp)) {
        cp = decodeUtf16(text, range, i);
        UScriptCode next_script = getICUScript(cp);
        if (current_script != next_script) {
            if (current_script == USCRIPT_INHERITED || current_script == USCRIPT_COMMON) {
                current_script = next_script;
                current_hb_script = getHbScript(cp);
            } else if (next_script == USCRIPT_INHERITED || next_script == USCRIPT_COMMON) {
                continue;
            } else {
                break;
            }
        }
    }
    if (current_script == USCRIPT_INHERITED) {
        return std::make_pair(i, HB_SCRIPT_COMMON);
    } else {
        return std::make_pair(i, current_hb_script);
    }
}

}  // namespace minikin
