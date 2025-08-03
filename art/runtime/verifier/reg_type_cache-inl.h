/*
 * Copyright (C) 2012 The Android Open Source Project
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

#ifndef ART_RUNTIME_VERIFIER_REG_TYPE_CACHE_INL_H_
#define ART_RUNTIME_VERIFIER_REG_TYPE_CACHE_INL_H_

#include "base/bit_vector-inl.h"
#include "class_linker.h"
#include "class_root-inl.h"
#include "mirror/class-inl.h"
#include "mirror/method_handle_impl.h"
#include "mirror/method_type.h"
#include "mirror/string.h"
#include "mirror/throwable.h"
#include "reg_type.h"
#include "reg_type_cache.h"

namespace art HIDDEN {
namespace verifier {

inline const art::verifier::RegType& RegTypeCache::GetFromId(uint16_t id) const {
  DCHECK_LT(id, entries_.size());
  const RegType* result = entries_[id];
  DCHECK(result != nullptr);
  return *result;
}

inline const ConstantType& RegTypeCache::FromCat1Const(int32_t value, bool precise) {
  // We only expect 0 to be a precise constant.
  DCHECK_IMPLIES(value == 0, precise);
  if (precise && (value >= kMinSmallConstant) && (value <= kMaxSmallConstant)) {
    return *down_cast<const ConstantType*>(entries_[value - kMinSmallConstant]);
  }
  return FromCat1NonSmallConstant(value, precise);
}

inline const BooleanType& RegTypeCache::Boolean() {
  return *down_cast<const BooleanType*>(entries_[kBooleanCacheId]);
}
inline const ByteType& RegTypeCache::Byte() {
  return *down_cast<const ByteType*>(entries_[kByteCacheId]);
}
inline const CharType& RegTypeCache::Char() {
  return *down_cast<const CharType*>(entries_[kCharCacheId]);
}
inline const ShortType& RegTypeCache::Short() {
  return *down_cast<const ShortType*>(entries_[kShortCacheId]);
}
inline const IntegerType& RegTypeCache::Integer() {
  return *down_cast<const IntegerType*>(entries_[kIntCacheId]);
}
inline const FloatType& RegTypeCache::Float() {
  return *down_cast<const FloatType*>(entries_[kFloatCacheId]);
}
inline const LongLoType& RegTypeCache::LongLo() {
  return *down_cast<const LongLoType*>(entries_[kLongLoCacheId]);
}
inline const LongHiType& RegTypeCache::LongHi() {
  return *down_cast<const LongHiType*>(entries_[kLongHiCacheId]);
}
inline const DoubleLoType& RegTypeCache::DoubleLo() {
  return *down_cast<const DoubleLoType*>(entries_[kDoubleLoCacheId]);
}
inline const DoubleHiType& RegTypeCache::DoubleHi() {
  return *down_cast<const DoubleHiType*>(entries_[kDoubleHiCacheId]);
}
inline const UndefinedType& RegTypeCache::Undefined() {
  return *down_cast<const UndefinedType*>(entries_[kUndefinedCacheId]);
}
inline const ConflictType& RegTypeCache::Conflict() {
  return *down_cast<const ConflictType*>(entries_[kConflictCacheId]);
}
inline const NullType& RegTypeCache::Null() {
  return *down_cast<const NullType*>(entries_[kNullCacheId]);
}

inline const ImpreciseConstType& RegTypeCache::ByteConstant() {
  const ConstantType& result = FromCat1Const(std::numeric_limits<jbyte>::min(), false);
  DCHECK(result.IsImpreciseConstant());
  return *down_cast<const ImpreciseConstType*>(&result);
}

inline const ImpreciseConstType& RegTypeCache::CharConstant() {
  int32_t jchar_max = static_cast<int32_t>(std::numeric_limits<jchar>::max());
  const ConstantType& result =  FromCat1Const(jchar_max, false);
  DCHECK(result.IsImpreciseConstant());
  return *down_cast<const ImpreciseConstType*>(&result);
}

inline const ImpreciseConstType& RegTypeCache::ShortConstant() {
  const ConstantType& result =  FromCat1Const(std::numeric_limits<jshort>::min(), false);
  DCHECK(result.IsImpreciseConstant());
  return *down_cast<const ImpreciseConstType*>(&result);
}

inline const ImpreciseConstType& RegTypeCache::IntConstant() {
  const ConstantType& result = FromCat1Const(std::numeric_limits<jint>::max(), false);
  DCHECK(result.IsImpreciseConstant());
  return *down_cast<const ImpreciseConstType*>(&result);
}

inline const ImpreciseConstType& RegTypeCache::PosByteConstant() {
  const ConstantType& result = FromCat1Const(std::numeric_limits<jbyte>::max(), false);
  DCHECK(result.IsImpreciseConstant());
  return *down_cast<const ImpreciseConstType*>(&result);
}

inline const ImpreciseConstType& RegTypeCache::PosShortConstant() {
  const ConstantType& result =  FromCat1Const(std::numeric_limits<jshort>::max(), false);
  DCHECK(result.IsImpreciseConstant());
  return *down_cast<const ImpreciseConstType*>(&result);
}

inline const PreciseReferenceType& RegTypeCache::JavaLangClass() {
  const RegType* result = &FromClass("Ljava/lang/Class;",
                                     GetClassRoot<mirror::Class>(),
                                     /* precise= */ true);
  DCHECK(result->IsPreciseReference());
  return *down_cast<const PreciseReferenceType*>(result);
}

inline const PreciseReferenceType& RegTypeCache::JavaLangString() {
  // String is final and therefore always precise.
  const RegType* result = &FromClass("Ljava/lang/String;",
                                     GetClassRoot<mirror::String>(),
                                     /* precise= */ true);
  DCHECK(result->IsPreciseReference());
  return *down_cast<const PreciseReferenceType*>(result);
}

inline const PreciseReferenceType& RegTypeCache::JavaLangInvokeMethodHandle() {
  const RegType* result = &FromClass("Ljava/lang/invoke/MethodHandle;",
                                     GetClassRoot<mirror::MethodHandle>(),
                                     /* precise= */ true);
  DCHECK(result->IsPreciseReference());
  return *down_cast<const PreciseReferenceType*>(result);
}

inline const PreciseReferenceType& RegTypeCache::JavaLangInvokeMethodType() {
  const RegType* result = &FromClass("Ljava/lang/invoke/MethodType;",
                                     GetClassRoot<mirror::MethodType>(),
                                     /* precise= */ true);
  DCHECK(result->IsPreciseReference());
  return *down_cast<const PreciseReferenceType*>(result);
}

inline const RegType&  RegTypeCache::JavaLangThrowable(bool precise) {
  const RegType* result = &FromClass("Ljava/lang/Throwable;",
                                     GetClassRoot<mirror::Throwable>(),
                                     precise);
  if (precise) {
    DCHECK(result->IsPreciseReference());
    return *down_cast<const PreciseReferenceType*>(result);
  } else {
    DCHECK(result->IsReference());
    return *down_cast<const ReferenceType*>(result);
  }
}

inline const RegType& RegTypeCache::JavaLangObject(bool precise) {
  const RegType* result = &FromClass("Ljava/lang/Object;", GetClassRoot<mirror::Object>(), precise);
  if (precise) {
    DCHECK(result->IsPreciseReference());
    return *down_cast<const PreciseReferenceType*>(result);
  } else {
    DCHECK(result->IsReference());
    return *down_cast<const ReferenceType*>(result);
  }
}

template <class RegTypeType>
inline RegTypeType& RegTypeCache::AddEntry(RegTypeType* new_entry) {
  DCHECK(new_entry != nullptr);
  entries_.push_back(new_entry);
  if (new_entry->HasClass()) {
    Handle<mirror::Class> klass = new_entry->GetClassHandle();
    DCHECK(!klass->IsPrimitive());
    klass_entries_.push_back(std::make_pair(klass, new_entry));
  }
  return *new_entry;
}

}  // namespace verifier
}  // namespace art
#endif  // ART_RUNTIME_VERIFIER_REG_TYPE_CACHE_INL_H_
