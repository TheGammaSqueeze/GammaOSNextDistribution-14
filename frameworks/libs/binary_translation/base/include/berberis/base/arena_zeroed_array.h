/*
 * Copyright (C) 2019 The Android Open Source Project
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

#ifndef BERBERIS_BASE_ARENA_ZEROED_ARRAY_H_
#define BERBERIS_BASE_ARENA_ZEROED_ARRAY_H_

#include "berberis/base/arena_alloc.h"
#include "berberis/base/logging.h"

#include <string.h>

namespace berberis {

// A zero-initialized array of fixed size (at construction time).
// TODO(b/117224636): This is a workaround for slow zero-initialized ArenaVector.
// Alternatively, we could zero-initialize memory when Arena allocates memory, eliminating
// the need to zero-initialize memory in every data structure allocated from Arena.
template <typename T>
class ArenaZeroedArray {
 public:
  ArenaZeroedArray(size_t size, Arena* arena)
      : size_(size), array_(NewArrayInArena<T>(arena, size)) {
    memset(array_, 0, sizeof(T) * size);
  }

  const T& operator[](size_t i) const { return array_[i]; }
  T& operator[](size_t i) { return array_[i]; }

  const T& at(size_t i) const {
    CHECK_LT(i, size_);
    return array_[i];
  }

  T& at(size_t i) {
    CHECK_LT(i, size_);
    return array_[i];
  }

 private:
  size_t size_;
  T* array_;
};

}  // namespace berberis

#endif  // BERBERIS_BASE_ARENA_ZEROED_ARRAY_H_
