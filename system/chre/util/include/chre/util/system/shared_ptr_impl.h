/*
 * Copyright (C) 2021 The Android Open Source Project
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

#ifndef CHRE_UTIL_SYSTEM_SHARED_PTR_IMPL_H_
#define CHRE_UTIL_SYSTEM_SHARED_PTR_IMPL_H_

#include "chre/util/system/shared_ptr.h"

#include "chre/util/container_support.h"
#include "chre/util/memory.h"
#include "chre/util/system/ref_base.h"

namespace chre {

template <typename ObjectType>
SharedPtr<ObjectType>::SharedPtr() {
  static_assert(std::is_base_of<RefBase<ObjectType>, ObjectType>::value,
                "Class must inherit from RefBase to use SharedPtr");
}

template <typename ObjectType>
SharedPtr<ObjectType>::SharedPtr(ObjectType *object) : mObject(object) {}

template <typename ObjectType>
SharedPtr<ObjectType>::SharedPtr(SharedPtr<ObjectType> &&other)
    : mObject(other.mObject) {
  other.mObject = nullptr;
}

template <typename ObjectType>
template <typename OtherObjectType>
SharedPtr<ObjectType>::SharedPtr(SharedPtr<OtherObjectType> &&other)
    : mObject(other.mObject) {
  other.mObject = nullptr;
}

template <typename ObjectType>
SharedPtr<ObjectType>::SharedPtr(const SharedPtr &other) {
  reset(other.mObject);
}

template <typename ObjectType>
template <typename OtherObjectType>
SharedPtr<ObjectType>::SharedPtr(const SharedPtr<OtherObjectType> &other) {
  reset(other.mObject);
}

template <typename ObjectType>
SharedPtr<ObjectType>::~SharedPtr() {
  reset();
}

template <typename ObjectType>
bool SharedPtr<ObjectType>::isNull() const {
  return (mObject == nullptr);
}

template <typename ObjectType>
ObjectType *SharedPtr<ObjectType>::get() const {
  return mObject;
}

template <typename ObjectType>
void SharedPtr<ObjectType>::reset(ObjectType *object) {
  CHRE_ASSERT(object == nullptr || mObject != object);

  reset();
  mObject = object;
  if (mObject != nullptr) {
    mObject->incRef();
  }
}

template <typename ObjectType>
void SharedPtr<ObjectType>::reset() {
  if (mObject != nullptr) {
    mObject->decRef();
    mObject = nullptr;
  }
}

template <typename ObjectType>
ObjectType *SharedPtr<ObjectType>::operator->() const {
  return get();
}

template <typename ObjectType>
ObjectType &SharedPtr<ObjectType>::operator*() const {
  return *get();
}

template <typename ObjectType>
ObjectType &SharedPtr<ObjectType>::operator[](size_t index) const {
  return get()[index];
}

template <typename ObjectType>
bool SharedPtr<ObjectType>::operator==(
    const SharedPtr<ObjectType> &other) const {
  return mObject == other.get();
}

template <typename ObjectType>
bool SharedPtr<ObjectType>::operator!=(
    const SharedPtr<ObjectType> &other) const {
  return !(*this == other);
}

template <typename ObjectType>
SharedPtr<ObjectType> &SharedPtr<ObjectType>::operator=(
    const SharedPtr<ObjectType> &other) {
  reset(other.mObject);
  return *this;
}

template <typename ObjectType>
SharedPtr<ObjectType> &SharedPtr<ObjectType>::operator=(
    SharedPtr<ObjectType> &&other) {
  reset();
  mObject = other.mObject;
  other.mObject = nullptr;
  return *this;
}

template <typename ObjectType, typename... Args>
inline SharedPtr<ObjectType> MakeShared(Args &&...args) {
  return SharedPtr<ObjectType>(
      memoryAlloc<ObjectType>(std::forward<Args>(args)...));
}

template <typename ObjectType>
inline SharedPtr<ObjectType> MakeSharedZeroFill() {
  // Due to the need for ObjectType to inherit from RefBase, typical
  // trivial-types won't have a trivial constructor. To match what is provided
  // for UniquePtr, this logic is slightly reworked to allow zero'ing out the
  // memory before constructing the object.
  auto *ptr = memoryAlloc(sizeof(ObjectType));
  if (ptr != nullptr) {
    memset(ptr, 0, sizeof(ObjectType));
  }

  auto *castedPtr = static_cast<ObjectType *>(ptr);
  if (castedPtr != nullptr) {
    new (castedPtr) ObjectType();
  }

  return SharedPtr<ObjectType>(castedPtr);
}

}  // namespace chre

#endif  // CHRE_UTIL_SYSTEM_SHARED_PTR_IMPL_H_
