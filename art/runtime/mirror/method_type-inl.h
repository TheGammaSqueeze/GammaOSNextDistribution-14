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

#ifndef ART_RUNTIME_MIRROR_METHOD_TYPE_INL_H_
#define ART_RUNTIME_MIRROR_METHOD_TYPE_INL_H_

#include "method_type.h"

#include "base/casts.h"
#include "handle_scope-inl.h"
#include "mirror/object-inl.h"

namespace art HIDDEN {
namespace mirror {

inline RawMethodType::RawMethodType(VariableSizedHandleScope* hs)
    : hs_(hs) {
  DCHECK(hs != nullptr);
}

inline bool RawMethodType::IsValid() const {
  return hs_->Size() != 0u;
}

inline void RawMethodType::SetRType(ObjPtr<mirror::Class> rtype) {
  DCHECK(rtype != nullptr);
  DCHECK_EQ(hs_->Size(), 0u);
  hs_->NewHandle(rtype);
  DCHECK_EQ(rtype, GetRType());
}

inline void RawMethodType::AddPType(ObjPtr<mirror::Class> ptype) {
  DCHECK(ptype != nullptr);
  DCHECK_NE(hs_->Size(), 0u);
  hs_->NewHandle(ptype);
  DCHECK_NE(GetNumberOfPTypes(), 0);
  DCHECK_EQ(GetPType(GetNumberOfPTypes() - 1), ptype);
}

inline int32_t RawMethodType::GetNumberOfPTypes() const {
  DCHECK_NE(hs_->Size(), 0u);
  return dchecked_integral_cast<int32_t>(hs_->Size() - 1u);
}

inline ObjPtr<mirror::Class> RawMethodType::GetPType(int32_t i) const {
  DCHECK_LT(i, GetNumberOfPTypes());
  return hs_->GetHandle<mirror::Class>(i + 1).Get();
}

inline ObjPtr<mirror::Class> RawMethodType::GetRType() const {
  return GetRTypeHandle().Get();
}

inline Handle<mirror::Class> RawMethodType::GetRTypeHandle() const {
  DCHECK_NE(hs_->Size(), 0u);
  return hs_->GetHandle<mirror::Class>(0u);
}

inline ObjPtr<ObjectArray<Class>> MethodType::GetPTypes() {
  return GetFieldObject<ObjectArray<Class>>(OFFSET_OF_OBJECT_MEMBER(MethodType, p_types_));
}

inline int MethodType::GetNumberOfPTypes() {
  return GetPTypes()->GetLength();
}

inline ObjPtr<Class> MethodType::GetRType() {
  return GetFieldObject<Class>(OFFSET_OF_OBJECT_MEMBER(MethodType, r_type_));
}

template <typename PTypesType>
inline MethodType::PTypesAccessor<PTypesType>::PTypesAccessor(PTypesType p_types)
    : p_types_(p_types) {}

template <typename PTypesType>
inline int32_t MethodType::PTypesAccessor<PTypesType>::GetLength() const {
  return p_types_->GetLength();
}

template <typename PTypesType>
inline ObjPtr<mirror::Class> MethodType::PTypesAccessor<PTypesType>::Get(int32_t i) const {
  DCHECK_LT(i, GetLength());
  return p_types_->GetWithoutChecks(i);
}

inline MethodType::RawPTypesAccessor::RawPTypesAccessor(RawMethodType method_type)
    : method_type_(method_type) {
  DCHECK(method_type.IsValid());
}

inline int32_t MethodType::RawPTypesAccessor::GetLength() const {
  return method_type_.GetNumberOfPTypes();
}

inline ObjPtr<mirror::Class> MethodType::RawPTypesAccessor::Get(int32_t i) const {
  return method_type_.GetPType(i);
}

template <typename HandleScopeType>
inline MethodType::HandlePTypesAccessor MethodType::NewHandlePTypes(
    Handle<MethodType> method_type, HandleScopeType* hs) {
  Handle<ObjectArray<mirror::Class>> p_types = hs->NewHandle(method_type->GetPTypes());
  return HandlePTypesAccessor(p_types);
}

template <typename HandleScopeType>
inline MethodType::RawPTypesAccessor MethodType::NewHandlePTypes(
    RawMethodType method_type, [[maybe_unused]] HandleScopeType* hs) {
  return RawPTypesAccessor(method_type);
}

inline MethodType::ObjPtrPTypesAccessor MethodType::GetPTypes(ObjPtr<MethodType> method_type) {
  return ObjPtrPTypesAccessor(method_type->GetPTypes());
}

inline MethodType::ObjPtrPTypesAccessor MethodType::GetPTypes(Handle<MethodType> method_type) {
  return GetPTypes(method_type.Get());
}

inline MethodType::RawPTypesAccessor MethodType::GetPTypes(RawMethodType method_type) {
  return RawPTypesAccessor(method_type);
}

inline ObjPtr<mirror::Class> MethodType::GetRType(ObjPtr<MethodType> method_type) {
  return method_type->GetRType();
}

inline ObjPtr<mirror::Class> MethodType::GetRType(Handle<MethodType> method_type) {
  return GetRType(method_type.Get());
}

inline ObjPtr<mirror::Class> MethodType::GetRType(RawMethodType method_type) {
  return method_type.GetRType();
}

}  // namespace mirror
}  // namespace art

#endif  // ART_RUNTIME_MIRROR_METHOD_TYPE_INL_H_
