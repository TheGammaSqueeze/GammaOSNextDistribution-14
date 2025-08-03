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

#ifndef MINIKIN_FEATURE_FLAGS_H
#define MINIKIN_FEATURE_FLAGS_H

#ifdef __ANDROID__
#include <com_android_text_flags.h>
#endif  // __ANDROID__

namespace features {

#ifdef __ANDROID__
#define DEFINE_FEATURE_FLAG_ACCESSOROR(feature_name)                \
    inline bool feature_name() {                                    \
        static bool flag = com_android_text_flags_##feature_name(); \
        return flag;                                                \
    }
#else  //  __ANDROID__
#define DEFINE_FEATURE_FLAG_ACCESSOROR(feature_name) \
    inline bool feature_name() {                     \
        return true;                                 \
    }
#endif  //  __ANDROID__

DEFINE_FEATURE_FLAG_ACCESSOROR(phrase_strict_fallback)
DEFINE_FEATURE_FLAG_ACCESSOROR(word_style_auto)
DEFINE_FEATURE_FLAG_ACCESSOROR(letter_spacing_justification)
DEFINE_FEATURE_FLAG_ACCESSOROR(lazy_variation_instance)

}  // namespace features

#endif  // FEATURE_FLAGS
