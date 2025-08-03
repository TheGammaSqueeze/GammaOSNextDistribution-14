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

#ifndef MINIKIN_LETTER_SPACING_UTILS_H
#define MINIKIN_LETTER_SPACING_UTILS_H

#define LOG_TAG "Minikin"

#include <hb.h>

namespace minikin {

/**
 * Disable certain scripts (mostly those with cursive connection) from having letterspacing
 * applied. See https://github.com/behdad/harfbuzz/issues/64 for more details.
 */
inline bool isLetterSpacingCapableScript(hb_script_t script) {
    return !(script == HB_SCRIPT_ARABIC || script == HB_SCRIPT_NKO ||
             script == HB_SCRIPT_PSALTER_PAHLAVI || script == HB_SCRIPT_MANDAIC ||
             script == HB_SCRIPT_MONGOLIAN || script == HB_SCRIPT_PHAGS_PA ||
             script == HB_SCRIPT_DEVANAGARI || script == HB_SCRIPT_BENGALI ||
             script == HB_SCRIPT_GURMUKHI || script == HB_SCRIPT_MODI ||
             script == HB_SCRIPT_SHARADA || script == HB_SCRIPT_SYLOTI_NAGRI ||
             script == HB_SCRIPT_TIRHUTA || script == HB_SCRIPT_OGHAM);
}

inline bool isLetterSpacingCapableCodePoint(uint32_t cp) {
    hb_script_t script = hb_unicode_script(hb_unicode_funcs_get_default(), cp);
    return isLetterSpacingCapableScript(script);
}

}  // namespace minikin

#endif  // MINIKIN_LETTER_SPACING_UTILS_H
