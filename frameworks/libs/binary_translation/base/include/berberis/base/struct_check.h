/*
 * Copyright (C) 2015 The Android Open Source Project
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

#ifndef BERBERIS_BASE_STRUCT_CHECK_H_
#define BERBERIS_BASE_STRUCT_CHECK_H_

#include <climits>  // CHAR_BIT
#include <cstddef>

namespace berberis {

// Helper macroses for verification purposes.  When we declare a data structure
// with forced align we want to verify that size and offset of the resulting
// structure is now the same as for structure which we want to mimic.
#define CHECK_STRUCT_LAYOUT(type, size, align)                                                  \
  static_assert(sizeof(type) * CHAR_BIT == size,                                                \
                "size of " #type " must be " #size " bit because it's " #size " bit on guest"); \
  static_assert(alignof(type) * CHAR_BIT == align,                                              \
                "align of " #type " must be " #align " bit because it's " #align " bit on guest")

// If structure is allocated on guest and used by host then we could declare them "partially
// compatible" if alignment requirements on host are less strict than on guest.
#define CHECK_STRUCT_LAYOUT_GUEST_TO_HOST(type, size, align)                                      \
  static_assert(sizeof(type) * CHAR_BIT == size,                                                  \
                "size of " #type " must be " #size " bit because it's " #size " bit on guest");   \
  static_assert(alignof(type) * CHAR_BIT <= align && align % (alignof(type) * CHAR_BIT) == 0,     \
                "align of " #type " must be less or equal to " #align " bit because it's " #align \
                " bit on guest")

// If structure is allocated on host and used by guest then we could declare them "partially
// compatible" if alignment requirements on guest are less strict than on host.
#define CHECK_STRUCT_LAYOUT_HOST_TO_GUEST(type, size, align)                                      \
  static_assert(sizeof(type) * CHAR_BIT == size,                                                  \
                "size of " #type " must be " #size " bit because it's " #size " bit on guest");   \
  static_assert(alignof(type) * CHAR_BIT >= align && (alignof(type) * CHAR_BIT) % align == 0,     \
                "align of " #type " must be more or equal to " #align " bit because it's " #align \
                " bit on guest")

#define CHECK_FIELD_LAYOUT(type, field, offset, size)                          \
  static_assert(offsetof(type, field) * CHAR_BIT == offset,                    \
                "offset of `" #field "' field in " #type " must be " #offset   \
                " because it's " #offset " on guest");                         \
  static_assert(sizeof(static_cast<type*>(nullptr)->field) * CHAR_BIT == size, \
                "size of `" #field "' field in " #type " must be " #size       \
                " bit because it's " #size " bit on guest")

}  // namespace berberis

#endif  // BERBERIS_BASE_STRUCT_CHECK_H_
