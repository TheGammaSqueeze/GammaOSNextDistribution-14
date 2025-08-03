/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <stddef.h>
#include <stdint.h>

#include "elf/relocation.h"

// Following are naive implementations of functions in <string.h> that are not
// provided by the toolchain for RISC-V targets. They are simply for getting
// compilation passed and not necessarily optimzied.
extern "C" {

int memcmp(const void* s1, const void* s2, size_t count) {
  const uint8_t* left = static_cast<const uint8_t*>(s1);
  const uint8_t* right = static_cast<const uint8_t*>(s2);
  for (size_t i = 0; i < count; i++) {
    if (left[i] == right[i]) {
      continue;
    }
    return left[i] < right[i] ? -1 : 1;
  }
  return 0;
}

void* memmove(void* dest, void const* src, size_t count) {
  uint8_t* _dest = static_cast<uint8_t*>(dest);
  const uint8_t* _src = static_cast<const uint8_t*>(src);
  if (dest < src) {
    for (size_t i = 0; i < count; i++) {
      _dest[i] = _src[i];
    }
  } else {
    for (size_t i = count; i > 0; i--) {
      _dest[i - 1] = _src[i - 1];
    }
  }
  return dest;
}

size_t strlen(const char* str) {
  size_t i = 0;
  for (; str[i] != 0; i++) {
  }
  return i;
}

void* memset(void* ptr, int value, size_t num) {
  uint8_t* start = static_cast<uint8_t*>(ptr);
  for (size_t i = 0; i < num; i++) {
    start[i] = value;
  }
  return ptr;
}

void* memcpy(void* dest, const void* src, size_t num) {
  return memmove(dest, src, num);
}

void ApplyRelocationHangIfFail(uintptr_t program_base, uintptr_t dynamic_section) {
  if (!ApplyRelocation(program_base, dynamic_section)) {
    while (true) {
    };
  }
}

}
