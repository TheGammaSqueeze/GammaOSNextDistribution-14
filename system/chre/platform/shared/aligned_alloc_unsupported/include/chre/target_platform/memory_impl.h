/*
 * Copyright (C) 2022 The Android Open Source Project
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

#ifndef CHRE_SHARED_ALIGNED_ALLOC_UNSUPPORTED_MEMORY_IMPL_H_
#define CHRE_SHARED_ALIGNED_ALLOC_UNSUPPORTED_MEMORY_IMPL_H_

#include <cstring>

#include "chre/util/always_false.h"

namespace chre {

template <typename T>
inline T *memoryAlignedAlloc() {
  static_assert(AlwaysFalse<T>::value,
                "memoryAlignedAlloc is unsupported on this platform");
  return nullptr;
}

}  // namespace chre

#endif  // CHRE_SHARED_ALIGNED_ALLOC_UNSUPPORTED_MEMORY_IMPL_H_
