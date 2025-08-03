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

#ifndef ART_RUNTIME_MIRROR_METHOD_HANDLE_IMPL_INL_H_
#define ART_RUNTIME_MIRROR_METHOD_HANDLE_IMPL_INL_H_

#include "method_handle_impl.h"

#include "art_method-inl.h"
#include "object-inl.h"

namespace art HIDDEN {
namespace mirror {

inline MethodHandle::Kind MethodHandle::GetHandleKind() REQUIRES_SHARED(Locks::mutator_lock_) {
  const int32_t handle_kind = GetField32(OFFSET_OF_OBJECT_MEMBER(MethodHandle, handle_kind_));
  DCHECK(handle_kind >= 0 && handle_kind <= static_cast<int32_t>(Kind::kLastValidKind));
  return static_cast<Kind>(handle_kind);
}

inline ObjPtr<mirror::MethodType> MethodHandle::GetMethodType()
    REQUIRES_SHARED(Locks::mutator_lock_) {
  return GetFieldObject<mirror::MethodType>(OFFSET_OF_OBJECT_MEMBER(MethodHandle, method_type_));
}

inline ObjPtr<mirror::MethodHandle> MethodHandle::GetAsTypeCache()
    REQUIRES_SHARED(Locks::mutator_lock_) {
  return GetFieldObject<mirror::MethodHandle>(
      OFFSET_OF_OBJECT_MEMBER(MethodHandle, as_type_cache_));
}

inline ArtField* MethodHandle::GetTargetField() REQUIRES_SHARED(Locks::mutator_lock_) {
  return reinterpret_cast<ArtField*>(
      GetField64(OFFSET_OF_OBJECT_MEMBER(MethodHandle, art_field_or_method_)));
}

inline ArtMethod* MethodHandle::GetTargetMethod() REQUIRES_SHARED(Locks::mutator_lock_) {
  return reinterpret_cast<ArtMethod*>(
      GetField64(OFFSET_OF_OBJECT_MEMBER(MethodHandle, art_field_or_method_)));
}

}  // namespace mirror
}  // namespace art

#endif  // ART_RUNTIME_MIRROR_METHOD_HANDLE_IMPL_INL_H_
