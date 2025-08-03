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

#ifndef MINIKIN_CONST_H
#define MINIKIN_CONST_H

#include <cstdint>
#include <memory>
#include <vector>

#include "minikin/FontVariation.h"

namespace minikin {

constexpr uint32_t CHAR_LIMIT_FOR_CACHE = 128;

constexpr uint32_t MakeTag(char c1, char c2, char c3, char c4) {
    return ((uint32_t)c1 << 24) | ((uint32_t)c2 << 16) | ((uint32_t)c3 << 8) | (uint32_t)c4;
}

// Axis tags
const uint32_t TAG_wght = MakeTag('w', 'g', 'h', 't');
const uint32_t TAG_ital = MakeTag('i', 't', 'a', 'l');

}  // namespace minikin

#endif  // MINIKIN_CONST_H
