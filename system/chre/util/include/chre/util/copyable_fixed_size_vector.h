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

#ifndef CHRE_UTIL_COPYABLE_FIXED_SIZE_VECTOR_H_
#define CHRE_UTIL_COPYABLE_FIXED_SIZE_VECTOR_H_

#include <cstring>
#include <type_traits>

#include "chre/util/fixed_size_vector.h"

namespace chre {

/**
 * Equivalent to FixedSizeVector, but for situations where there's an explicit
 * need for it to be copyable. This implies that we've weighed alternatives and
 * are OK with the potential overhead involved, for example if we know this is
 * a small size collection that we'd otherwise use a plain array for.
 */
template <typename ElementType, size_t kCapacity>
class CopyableFixedSizeVector : public FixedSizeVector<ElementType, kCapacity> {
 public:
  CopyableFixedSizeVector() = default;

  CopyableFixedSizeVector(const CopyableFixedSizeVector &other) {
    copyFrom(other);
  }

  CopyableFixedSizeVector &operator=(const CopyableFixedSizeVector &other) {
    copyFrom(other);
    return *this;
  }

 private:
  void copyFrom(const CopyableFixedSizeVector &other) {
    this->mSize = other.mSize;
    if (std::is_trivially_copy_constructible<ElementType>::value) {
      std::memcpy(this->data(), other.data(),
                  sizeof(ElementType) * this->mSize);
    } else {
      for (size_t i = 0; i < this->mSize; i++) {
        new (&this->data()[i]) ElementType(other[i]);
      }
    }
  }
};

}  // namespace chre

#endif  // CHRE_UTIL_COPYABLE_FIXED_SIZE_VECTOR_H_