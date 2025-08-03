/*
 * Copyright (C) 2017 The Android Open Source Project
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

#ifndef CHRE_UTIL_MEMORY_IMPL_H_
#define CHRE_UTIL_MEMORY_IMPL_H_

#include <cstring>
#include <new>
#include <type_traits>
#include <utility>

#include "chre/util/container_support.h"

namespace chre {

namespace util::internal {

// Backport of C++20's std::is_unbounded_array_v
template <typename>
constexpr bool is_unbounded_array_v = false;
template <typename T>
constexpr bool is_unbounded_array_v<T[]> = true;

}  // namespace util::internal

template <typename ElementType>
inline void destroy(ElementType *first, size_t count) {
  for (size_t i = 0; i < count; i++) {
    first[i].~ElementType();
  }
}

//! Overload used when the type is move assignable
template <typename ElementType>
inline void moveOrCopyAssign(ElementType &dest, ElementType &source,
                             std::true_type) {
  dest = std::move(source);
}

//! Overload used when the type is not move assignable
template <typename ElementType>
inline void moveOrCopyAssign(ElementType &dest, ElementType &source,
                             std::false_type) {
  dest = source;
}

template <typename ElementType>
inline void moveOrCopyAssign(ElementType &dest, ElementType &source) {
  moveOrCopyAssign(dest, source,
                   typename std::is_move_assignable<ElementType>::type());
}

//! Overload used when type is trivially copy constructible
template <typename ElementType>
inline void uninitializedMoveOrCopy(ElementType *source, size_t count,
                                    ElementType *dest, std::true_type) {
  std::memcpy(dest, source, count * sizeof(ElementType));
}

//! Overload used when type is not trivially copy constructible, but is move
//! constructible
template <typename ElementType>
inline void uninitializedMoveOrCopy(ElementType *source, size_t count,
                                    ElementType *dest, std::false_type,
                                    std::true_type) {
  for (size_t i = 0; i < count; i++) {
    new (&dest[i]) ElementType(std::move(source[i]));
  }
}

//! Overload used when type is not trivially copy constructible or move
//! constructible
template <typename ElementType>
inline void uninitializedMoveOrCopy(ElementType *source, size_t count,
                                    ElementType *dest, std::false_type,
                                    std::false_type) {
  for (size_t i = 0; i < count; i++) {
    new (&dest[i]) ElementType(source[i]);
  }
}

//! Overload used when type is not trivially copy constructible
template <typename ElementType>
inline void uninitializedMoveOrCopy(ElementType *source, size_t count,
                                    ElementType *dest, std::false_type) {
  // Check the assumption that if is_move_constructible is false, then
  // is_copy_constructible is true
  static_assert(std::is_move_constructible<ElementType>() ||
                    std::is_copy_constructible<ElementType>(),
                "Object must be copy- or move- constructible to use "
                "uninitializedMoveOrCopy");
  uninitializedMoveOrCopy(
      source, count, dest, std::false_type(),
      typename std::is_move_constructible<ElementType>::type());
}

template <typename ElementType>
inline void uninitializedMoveOrCopy(ElementType *source, size_t count,
                                    ElementType *dest) {
  // TODO: we should be able to use std::is_trivially_copy_constructible here,
  // but it's not found in the linux x86 build, because our build uses GCC 4.8's
  // C++ standard library, which doesn't support it. Works in the SLPI build,
  // though...
  uninitializedMoveOrCopy(source, count, dest,
                          typename std::is_trivial<ElementType>::type());
  // typename std::is_trivially_copy_constructible<ElementType>::type());
}

template <typename T, typename... Args>
inline T *memoryAlloc(Args &&... args) {
  T *storage = nullptr;
  if constexpr (alignof(T) > alignof(std::max_align_t)) {
    storage = memoryAlignedAlloc<T>();
  } else {
    storage = static_cast<T *>(memoryAlloc(sizeof(T)));
  }

  if (storage != nullptr) {
    new (storage) T(std::forward<Args>(args)...);
  }

  return storage;
}

template <typename T>
typename std::remove_extent<T>::type *memoryAllocArray(size_t count) {
  static_assert(util::internal::is_unbounded_array_v<T>,
                "memoryAllocArray is only supported for unbounded array types, "
                "e.g. int[]");
  // If this limitation becomes an issue, the solution is just to create a
  // version of memoryAlignedAlloc() that accepts size_t as a parameter
  static_assert(alignof(T) <= alignof(std::max_align_t),
                "Additional alignment in memoryAllocArray isn't supported");
  using BaseType = typename std::remove_extent<T>::type;
  auto *ptr = static_cast<BaseType *>(memoryAlloc(count * sizeof(BaseType)));
  if (ptr != nullptr) {
    new (ptr) BaseType[count];
  }
  return ptr;
}

template <typename T>
void memoryFreeAndDestroy(T *element) {
  if (element != nullptr) {
    element->~T();
    memoryFree(element);
  }
}

}  // namespace chre

#endif  // CHRE_UTIL_MEMORY_IMPL_H_
