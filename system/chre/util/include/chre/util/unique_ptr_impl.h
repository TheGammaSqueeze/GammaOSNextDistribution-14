/*
 * Copyright (C) 2016 The Android Open Source Project
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

#ifndef CHRE_UTIL_UNIQUE_PTR_IMPL_H_
#define CHRE_UTIL_UNIQUE_PTR_IMPL_H_

#include "chre/util/unique_ptr.h"

#include <string.h>
#include <type_traits>
#include <utility>

#include "chre/util/container_support.h"
#include "chre/util/memory.h"

namespace chre {

template <typename ObjectOrArrayType>
UniquePtr<ObjectOrArrayType>::UniquePtr() : mObject(nullptr) {}

template <typename ObjectOrArrayType>
UniquePtr<ObjectOrArrayType>::UniquePtr(
    typename UniquePtr<ObjectOrArrayType>::ObjectType *object)
    : mObject(object) {}

template <typename ObjectOrArrayType>
UniquePtr<ObjectOrArrayType>::UniquePtr(UniquePtr<ObjectOrArrayType> &&other) {
  mObject = other.mObject;
  other.mObject = nullptr;
}

template <typename ObjectOrArrayType>
template <typename OtherObjectOrArrayType>
UniquePtr<ObjectOrArrayType>::UniquePtr(
    UniquePtr<OtherObjectOrArrayType> &&other) {
  static_assert(std::is_array_v<ObjectOrArrayType> ==
                    std::is_array_v<OtherObjectOrArrayType>,
                "UniquePtr conversion not supported across array and non-array "
                "types");
  mObject = other.mObject;
  other.mObject = nullptr;
}

template <typename ObjectOrArrayType>
UniquePtr<ObjectOrArrayType>::~UniquePtr() {
  reset();
}

template <typename ObjectOrArrayType>
bool UniquePtr<ObjectOrArrayType>::isNull() const {
  return (mObject == nullptr);
}

template <typename ObjectOrArrayType>
typename UniquePtr<ObjectOrArrayType>::ObjectType *
UniquePtr<ObjectOrArrayType>::get() const {
  return mObject;
}

template <typename ObjectOrArrayType>
typename UniquePtr<ObjectOrArrayType>::ObjectType *
UniquePtr<ObjectOrArrayType>::release() {
  ObjectType *obj = mObject;
  mObject = nullptr;
  return obj;
}

template <typename ObjectOrArrayType>
void UniquePtr<ObjectOrArrayType>::reset(ObjectType *object) {
  CHRE_ASSERT(object == nullptr || mObject != object);

  reset();
  mObject = object;
}

template <typename ObjectOrArrayType>
void UniquePtr<ObjectOrArrayType>::reset() {
  if (mObject != nullptr) {
    if constexpr (!std::is_trivially_destructible_v<ObjectType>) {
      mObject->~ObjectType();
    }
    memoryFree(mObject);
    mObject = nullptr;
  }
}

template <typename ObjectOrArrayType>
typename UniquePtr<ObjectOrArrayType>::ObjectType *
UniquePtr<ObjectOrArrayType>::operator->() const {
  static_assert(!std::is_array_v<ObjectOrArrayType>,
                "UniquePtr<T>::operator-> is not supported for array types");
  return get();
}

template <typename ObjectOrArrayType>
typename UniquePtr<ObjectOrArrayType>::ObjectType &
UniquePtr<ObjectOrArrayType>::operator*() const {
  static_assert(!std::is_array_v<ObjectOrArrayType>,
                "UniquePtr<T>::operator* is not supported for array types");
  return *get();
}

template <typename ObjectOrArrayType>
typename UniquePtr<ObjectOrArrayType>::ObjectType &
UniquePtr<ObjectOrArrayType>::operator[](size_t index) const {
  static_assert(std::is_array_v<ObjectOrArrayType>,
                "UniquePtr<T>::operator[] is only allowed when T is an array");
  return get()[index];
}

template <typename ObjectOrArrayType>
bool UniquePtr<ObjectOrArrayType>::operator==(
    const UniquePtr<ObjectOrArrayType> &other) const {
  return mObject == other.get();
}

template <typename ObjectOrArrayType>
bool UniquePtr<ObjectOrArrayType>::operator!=(
    const UniquePtr<ObjectOrArrayType> &other) const {
  return !(*this == other);
}

template <typename ObjectOrArrayType>
UniquePtr<ObjectOrArrayType> &UniquePtr<ObjectOrArrayType>::operator=(
    UniquePtr<ObjectOrArrayType> &&other) {
  reset();
  mObject = other.mObject;
  other.mObject = nullptr;
  return *this;
}

template <typename ObjectType, typename... Args>
inline UniquePtr<ObjectType> MakeUnique(Args &&... args) {
  return UniquePtr<ObjectType>(
      memoryAlloc<ObjectType>(std::forward<Args>(args)...));
}

template <typename ObjectArrayType>
UniquePtr<ObjectArrayType> MakeUniqueArray(size_t count) {
  return UniquePtr<ObjectArrayType>(memoryAllocArray<ObjectArrayType>(count));
}

template <typename ObjectType>
inline UniquePtr<ObjectType> MakeUniqueZeroFill() {
  // For simplicity, we call memset *after* memoryAlloc<ObjectType>() - this is
  // only valid for types that have a trivial constructor. This utility function
  // is really meant to be used with trivial types only - if there's a desire to
  // zero things out in a non-trivial type, the right place for that is in its
  // constructor.
  static_assert(std::is_trivial<ObjectType>::value,
                "MakeUniqueZeroFill is only supported for trivial types");
  auto ptr = UniquePtr<ObjectType>(memoryAlloc<ObjectType>());
  if (!ptr.isNull()) {
    memset(ptr.get(), 0, sizeof(ObjectType));
  }
  return ptr;
}

}  // namespace chre

#endif  // CHRE_UTIL_UNIQUE_PTR_IMPL_H_
