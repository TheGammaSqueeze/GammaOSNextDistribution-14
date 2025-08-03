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

#ifndef ART_RUNTIME_ART_METHOD_ALLOC_INL_H_
#define ART_RUNTIME_ART_METHOD_ALLOC_INL_H_

#include "art_method-inl.h"

#include "handle.h"
#include "handle_scope.h"
#include "mirror/class-alloc-inl.h"

namespace art HIDDEN {

namespace detail {

template <char Shorty>
struct HandleShortyTraits {
  using Type = typename ShortyTraits<Shorty>::Type;
  static Type Extract(Type value) ALWAYS_INLINE { return value; }
};

template <> struct HandleShortyTraits<'L'> {
  using Type = Handle<mirror::Object>;
  static typename ShortyTraits<'L'>::Type Extract(Type value)
      REQUIRES_SHARED(Locks::mutator_lock_) ALWAYS_INLINE {
    return value.Get();
  }
};

}  // namespace detail

template <char... ArgType, typename HandleScopeType>
inline Handle<mirror::Object> ArtMethod::NewObject(
    HandleScopeType& hs,
    Thread* self,
    typename detail::HandleShortyTraits<ArgType>::Type... args) {
  DCHECK(!GetDeclaringClass()->IsInterface());
  DCHECK(GetDeclaringClass()->IsInitialized());
  DCHECK(IsConstructor());
  DCHECK(!IsStatic());
  MutableHandle<mirror::Object> new_object = hs.NewHandle(GetDeclaringClass()->AllocObject(self));
  DCHECK_EQ(new_object == nullptr, self->IsExceptionPending());
  if (LIKELY(new_object != nullptr)) {
    InvokeInstance<'V', ArgType...>(
        self, new_object.Get(), detail::HandleShortyTraits<ArgType>::Extract(args)...);
    if (UNLIKELY(self->IsExceptionPending())) {
      new_object.Assign(nullptr);
    }
  }
  return new_object;
}

template <char... ArgType>
inline ObjPtr<mirror::Object> ArtMethod::NewObject(
    Thread* self, typename detail::HandleShortyTraits<ArgType>::Type... args) {
  StackHandleScope<1u> hs(self);
  return NewObject<ArgType...>(hs, self, args...).Get();
}

}  // namespace art

#endif  // ART_RUNTIME_ART_METHOD_ALLOC_INL_H_
