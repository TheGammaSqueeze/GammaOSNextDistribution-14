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

#ifndef ART_RUNTIME_WELL_KNOWN_CLASSES_INL_H_
#define ART_RUNTIME_WELL_KNOWN_CLASSES_INL_H_

#include "well_known_classes.h"

#include "art_field-inl.h"
#include "art_method-inl.h"

namespace art HIDDEN {
namespace detail {

template <typename MemberType, MemberType** kMember>
template <ReadBarrierOption kReadBarrierOption>
ObjPtr<mirror::Class> ClassFromMember<MemberType, kMember>::Get() {
  return (*kMember)->template GetDeclaringClass<kReadBarrierOption>();
}

template <typename MemberType, MemberType** kMember>
mirror::Class* ClassFromMember<MemberType, kMember>::operator->() const {
  return Get().Ptr();
}

template <typename MemberType, MemberType** kMember>
inline bool operator==(const ClassFromMember<MemberType, kMember> lhs, ObjPtr<mirror::Class> rhs) {
  return lhs.Get() == rhs;
}

template <typename MemberType, MemberType** kMember>
inline bool operator==(ObjPtr<mirror::Class> lhs, const ClassFromMember<MemberType, kMember> rhs) {
  return rhs == lhs;
}

template <typename MemberType, MemberType** kMember>
bool operator!=(const ClassFromMember<MemberType, kMember> lhs, ObjPtr<mirror::Class> rhs) {
  return !(lhs == rhs);
}

template <typename MemberType, MemberType** kMember>
bool operator!=(ObjPtr<mirror::Class> lhs, const ClassFromMember<MemberType, kMember> rhs) {
  return !(rhs == lhs);
}

}  // namespace detail
}  // namespace art

#endif  // ART_RUNTIME_WELL_KNOWN_CLASSES_INL_H_
