/*
 * Copyright 2021 The Android Open Source Project
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

#ifdef __cplusplus
#include <cstdint>
#include <cstring>
#else
#include <stdint.h>
#include <string.h>
#endif

// NOTE: Shared with internal_include/bt_target.h
/* Maximum device name length used in btm database. */
#ifndef BTM_MAX_REM_BD_NAME_LEN
#define BTM_MAX_REM_BD_NAME_LEN 248
#endif

/* Maximum local device name length stored btm database */
#ifndef BTM_MAX_LOC_BD_NAME_LEN
#define BTM_MAX_LOC_BD_NAME_LEN 248
#endif

#define BD_NAME_LEN 248
typedef uint8_t BD_NAME[BD_NAME_LEN + 1]; /* Device name */

/* Device name of peer (may be truncated to save space in BTM database) */
typedef uint8_t tBTM_BD_NAME[BTM_MAX_REM_BD_NAME_LEN + 1];

typedef uint8_t tBTM_LOC_BD_NAME[BTM_MAX_LOC_BD_NAME_LEN + 1];

#ifdef __cplusplus
#include "osi/include/compat.h"  // strlcpy
inline constexpr tBTM_BD_NAME kBtmBdNameEmpty = {};
constexpr size_t kBdNameLength = static_cast<size_t>(BD_NAME_LEN);
constexpr uint8_t kBdNameDelim = (uint8_t)NULL;

inline size_t bd_name_copy(BD_NAME bd_name_dest, const char* src) {
  return strlcpy(reinterpret_cast<char*>(bd_name_dest), const_cast<char*>(src),
                 kBdNameLength + 1);
}
inline size_t bd_name_copy(BD_NAME bd_name_dest, const BD_NAME bd_name_src) {
  return strlcpy(reinterpret_cast<char*>(bd_name_dest),
                 reinterpret_cast<const char*>(bd_name_src), kBdNameLength + 1);
}
inline void bd_name_clear(BD_NAME bd_name) { *bd_name = {0}; }
inline bool bd_name_is_empty(const BD_NAME bd_name) {
  return bd_name[0] == '\0';
}

inline void bd_name_from_char_pointer(BD_NAME bd_name_dest,
                                      const char* bd_name_char) {
  if (bd_name_char != nullptr) {
    strlcpy(reinterpret_cast<char*>(bd_name_dest), bd_name_char,
            kBdNameLength + 1);
  }
}
inline bool bd_name_is_equal(const BD_NAME bd_name1, const BD_NAME bd_name2) {
  return memcmp(reinterpret_cast<void*>(const_cast<uint8_t*>(bd_name1)),
                reinterpret_cast<void*>(const_cast<uint8_t*>(bd_name2)),
                kBdNameLength + 1) == 0;
}
#endif
