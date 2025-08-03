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

#ifndef CHRE_UTIL_RAW_STORAGE_H_
#define CHRE_UTIL_RAW_STORAGE_H_

#include <new>
#include <type_traits>

#include "chre/util/non_copyable.h"

namespace chre {

/**
 * A simple wrapper around std::aligned_storage that provides a region of
 * uninitialized memory suitable for storing an array of objects, with some
 * convenience wrappers for constructing and accessing elements.
 *
 * This wrapper does not keep track of which indices contain active elements and
 * therefore does not handle invoking the destructor - this is the
 * responsibility of the code using it. Therefore this class is geared towards
 * usage within another data structure, e.g. chre::ArrayQueue.
 */
template <typename ElementType, size_t kCapacity>
class RawStorage : public NonCopyable {
 public:
  size_t capacity() const {
    return kCapacity;
  }

  ElementType *data() {
    return std::launder(reinterpret_cast<ElementType *>(mStorage));
  }
  const ElementType *data() const {
    return std::launder(reinterpret_cast<const ElementType *>(mStorage));
  }

  ElementType &operator[](size_t index) {
    return data()[index];
  }
  const ElementType &operator[](size_t index) const {
    return data()[index];
  }

 private:
  //! To avoid static initialization of members, std::aligned_storage is used.
  std::aligned_storage_t<sizeof(ElementType), alignof(ElementType)>
      mStorage[kCapacity];
};

}  // namespace chre

#endif  // CHRE_UTIL_RAW_STORAGE_H_