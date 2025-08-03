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

#ifndef ART_RUNTIME_NATIVE_STRING_ARRAY_UTILS_H_
#define ART_RUNTIME_NATIVE_STRING_ARRAY_UTILS_H_

#include "base/macros.h"
#include "base/locks.h"
#include "class_root-inl.h"
#include "handle_scope-inl.h"
#include "mirror/object_array-alloc-inl.h"
#include "mirror/string.h"

namespace art HIDDEN {

namespace detail {

inline const char* GetStringCStr(const char* str) { return str; }
inline const char* GetStringCStr(const std::string& str) { return str.c_str(); }

}  // namespace detail

template <typename Container>
ObjPtr<mirror::ObjectArray<mirror::String>> CreateStringArray(
    Thread* self, size_t size, const Container& entries) REQUIRES_SHARED(Locks::mutator_lock_) {
  StackHandleScope<1u> hs(self);
  Handle<mirror::ObjectArray<mirror::String>> array = hs.NewHandle(
      mirror::ObjectArray<mirror::String>::Alloc(
          self, GetClassRoot<mirror::ObjectArray<mirror::String>>(), size));
  if (array == nullptr) {
    DCHECK(self->IsExceptionPending());
    return nullptr;
  }
  // Note: If the container's iterator returns a `std::string` by value, the `auto&&`
  // binds as a const reference and extends the lifetime of the temporary object.
  size_t pos = 0u;
  for (auto&& entry : entries) {
    ObjPtr<mirror::String> oentry =
        mirror::String::AllocFromModifiedUtf8(self, detail::GetStringCStr(entry));
    if (oentry == nullptr) {
      DCHECK(self->IsExceptionPending());
      return nullptr;
    }
    // We're initializing a newly allocated array object, so we do not need to record that under
    // a transaction. If the transaction is aborted, the whole object shall be unreachable.
    DCHECK_LT(pos, size);
    array->SetWithoutChecks</*kTransactionActive=*/ false, /*kCheckTransaction=*/ false>(
        pos, oentry);
    ++pos;
  }
  DCHECK_EQ(pos, size);
  return array.Get();
}

template <typename Container>
ObjPtr<mirror::ObjectArray<mirror::String>> CreateStringArray(
    Thread* self, const Container& entries) REQUIRES_SHARED(Locks::mutator_lock_) {
  return CreateStringArray(self, entries.size(), entries);
}

inline ObjPtr<mirror::ObjectArray<mirror::String>> CreateStringArray(
    Thread* self, std::initializer_list<const char*> entries)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  return CreateStringArray<std::initializer_list<const char*>>(self, entries);
}

}  // namespace art

#endif  // ART_RUNTIME_NATIVE_STRING_ARRAY_UTILS_H_
