/*
 * Copyright (C) 2014 The Android Open Source Project
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

#ifndef ART_RUNTIME_HANDLE_SCOPE_INL_H_
#define ART_RUNTIME_HANDLE_SCOPE_INL_H_

#include "handle_scope.h"

#include "base/casts.h"
#include "base/mutex.h"
#include "handle.h"
#include "handle_wrapper.h"
#include "mirror/object_reference-inl.h"
#include "obj_ptr-inl.h"
#include "thread-current-inl.h"
#include "verify_object.h"

namespace art HIDDEN {

template<size_t kNumReferences>
inline FixedSizeHandleScope<kNumReferences>::FixedSizeHandleScope(BaseHandleScope* link)
    : HandleScope(link, kNumReferences) {
  if (kDebugLocking) {
    Locks::mutator_lock_->AssertSharedHeld(Thread::Current());
  }
  static_assert(kNumReferences >= 1, "FixedSizeHandleScope must contain at least 1 reference");
  DCHECK_EQ(&storage_[0], GetReferences());  // TODO: Figure out how to use a compile assert.
  if (kIsDebugBuild) {
    // Fill storage with "DEAD HAndleSCope", mapping H->"4" and S->"5".
    for (size_t i = 0; i < kNumReferences; ++i) {
      GetReferences()[i].Assign(reinterpret_cast32<mirror::Object*>(0xdead4a5c));
    }
  }
}

template<size_t kNumReferences>
inline StackHandleScope<kNumReferences>::StackHandleScope(Thread* self)
    : FixedSizeHandleScope<kNumReferences>(self->GetTopHandleScope()),
      self_(self) {
  DCHECK_EQ(self, Thread::Current());
  if (kDebugLocking) {
    Locks::mutator_lock_->AssertSharedHeld(self_);
  }
  self_->PushHandleScope(this);
}

template<size_t kNumReferences>
inline StackHandleScope<kNumReferences>::~StackHandleScope() {
  if (kDebugLocking) {
    Locks::mutator_lock_->AssertSharedHeld(self_);
  }
  BaseHandleScope* top_handle_scope = self_->PopHandleScope();
  DCHECK_EQ(top_handle_scope, this);
}

inline ObjPtr<mirror::Object> HandleScope::GetReference(size_t i) const {
  DCHECK_LT(i, Size());
  if (kDebugLocking) {
    Locks::mutator_lock_->AssertSharedHeld(Thread::Current());
  }
  return GetReferences()[i].AsMirrorPtr();
}

template<class T>
inline Handle<T> HandleScope::GetHandle(size_t i) {
  DCHECK_LT(i, Size());
  return Handle<T>(&GetReferences()[i]);
}

template<class T>
inline MutableHandle<T> HandleScope::GetMutableHandle(size_t i) {
  DCHECK_LT(i, Size());
  return MutableHandle<T>(&GetReferences()[i]);
}

inline void HandleScope::SetReference(size_t i, ObjPtr<mirror::Object> object) {
  if (kDebugLocking) {
    Locks::mutator_lock_->AssertSharedHeld(Thread::Current());
  }
  DCHECK_LT(i, Size());
  VerifyObject(object);
  GetReferences()[i].Assign(object);
}

template<class T>
inline MutableHandle<T> HandleScope::NewHandle(T* object) {
  return NewHandle(ObjPtr<T>(object));
}

template<class MirrorType>
inline MutableHandle<MirrorType> HandleScope::NewHandle(
    ObjPtr<MirrorType> object) {
  DCHECK_LT(Size(), Capacity());
  size_t pos = size_;
  ++size_;
  SetReference(pos, object);
  MutableHandle<MirrorType> h(GetMutableHandle<MirrorType>(pos));
  return h;
}

template<class T>
inline HandleWrapper<T> HandleScope::NewHandleWrapper(T** object) {
  return HandleWrapper<T>(object, NewHandle(*object));
}

template<class T>
inline HandleWrapperObjPtr<T> HandleScope::NewHandleWrapper(
    ObjPtr<T>* object) {
  return HandleWrapperObjPtr<T>(object, NewHandle(*object));
}

inline bool HandleScope::Contains(StackReference<mirror::Object>* handle_scope_entry) const {
  return GetReferences() <= handle_scope_entry && handle_scope_entry < GetReferences() + size_;
}

template <typename Visitor>
inline void HandleScope::VisitRoots(Visitor& visitor) {
  for (size_t i = 0, size = Size(); i < size; ++i) {
    // GetReference returns a pointer to the stack reference within the handle scope. If this
    // needs to be updated, it will be done by the root visitor.
    visitor.VisitRootIfNonNull(GetHandle<mirror::Object>(i).GetReference());
  }
}

template <typename Visitor>
inline void HandleScope::VisitHandles(Visitor& visitor) {
  for (size_t i = 0, size = Size(); i < size; ++i) {
    if (GetHandle<mirror::Object>(i) != nullptr) {
      visitor.Visit(GetHandle<mirror::Object>(i));
    }
  }
}

// The current size of this handle scope.
inline uint32_t BaseHandleScope::Size() const {
  return LIKELY(!IsVariableSized())
      ? AsHandleScope()->Size()
      : AsVariableSized()->Size();
}

// The current capacity of this handle scope.
inline uint32_t BaseHandleScope::Capacity() const {
  return LIKELY(!IsVariableSized())
      ? AsHandleScope()->Capacity()
      : AsVariableSized()->Capacity();
}

inline bool BaseHandleScope::Contains(StackReference<mirror::Object>* handle_scope_entry) const {
  return LIKELY(!IsVariableSized())
      ? AsHandleScope()->Contains(handle_scope_entry)
      : AsVariableSized()->Contains(handle_scope_entry);
}

template <typename Visitor>
inline void BaseHandleScope::VisitRoots(Visitor& visitor) {
  if (LIKELY(!IsVariableSized())) {
    AsHandleScope()->VisitRoots(visitor);
  } else {
    AsVariableSized()->VisitRoots(visitor);
  }
}

template <typename Visitor>
inline void BaseHandleScope::VisitHandles(Visitor& visitor) {
  if (LIKELY(!IsVariableSized())) {
    AsHandleScope()->VisitHandles(visitor);
  } else {
    AsVariableSized()->VisitHandles(visitor);
  }
}

inline VariableSizedHandleScope* BaseHandleScope::AsVariableSized() {
  DCHECK(IsVariableSized());
  return down_cast<VariableSizedHandleScope*>(this);
}

inline HandleScope* BaseHandleScope::AsHandleScope() {
  DCHECK(!IsVariableSized());
  return down_cast<HandleScope*>(this);
}

inline const VariableSizedHandleScope* BaseHandleScope::AsVariableSized() const {
  DCHECK(IsVariableSized());
  return down_cast<const VariableSizedHandleScope*>(this);
}

inline const HandleScope* BaseHandleScope::AsHandleScope() const {
  DCHECK(!IsVariableSized());
  return down_cast<const HandleScope*>(this);
}

template<class T>
inline MutableHandle<T> VariableSizedHandleScope::NewHandle(T* object) {
  return NewHandle(ObjPtr<T>(object));
}

template<class MirrorType>
inline MutableHandle<MirrorType> VariableSizedHandleScope::NewHandle(ObjPtr<MirrorType> ptr) {
  DCHECK_EQ(current_scope_->Capacity(), kNumReferencesPerScope);
  if (current_scope_->Size() == kNumReferencesPerScope) {
    current_scope_ = new LocalScopeType(current_scope_);
  }
  return current_scope_->NewHandle(ptr);
}

inline VariableSizedHandleScope::VariableSizedHandleScope(Thread* const self)
    : BaseHandleScope(self->GetTopHandleScope()),
      self_(self),
      current_scope_(&first_scope_),
      first_scope_(/*link=*/ nullptr) {
  DCHECK_EQ(self, Thread::Current());
  if (kDebugLocking) {
    Locks::mutator_lock_->AssertSharedHeld(self_);
  }
  self_->PushHandleScope(this);
}

inline VariableSizedHandleScope::~VariableSizedHandleScope() {
  if (kDebugLocking) {
    Locks::mutator_lock_->AssertSharedHeld(self_);
  }
  BaseHandleScope* top_handle_scope = self_->PopHandleScope();
  DCHECK_EQ(top_handle_scope, this);
  // Don't delete first_scope_ since it is not heap allocated.
  while (current_scope_ != &first_scope_) {
    LocalScopeType* next = down_cast<LocalScopeType*>(current_scope_->GetLink());
    delete current_scope_;
    current_scope_ = next;
  }
}

inline uint32_t VariableSizedHandleScope::Size() const {
  const LocalScopeType* cur = current_scope_;
  DCHECK(cur != nullptr);
  // The linked list of local scopes starts from the latest which may not be fully filled.
  uint32_t sum = cur->Size();
  cur = down_cast<const LocalScopeType*>(cur->GetLink());
  while (cur != nullptr) {
    // All other local scopes are fully filled.
    DCHECK_EQ(cur->Size(), kNumReferencesPerScope);
    sum += kNumReferencesPerScope;
    cur = down_cast<const LocalScopeType*>(cur->GetLink());
  }
  return sum;
}

inline uint32_t VariableSizedHandleScope::Capacity() const {
  uint32_t sum = 0;
  const LocalScopeType* cur = current_scope_;
  while (cur != nullptr) {
    DCHECK_EQ(cur->Capacity(), kNumReferencesPerScope);
    sum += kNumReferencesPerScope;
    cur = down_cast<const LocalScopeType*>(cur->GetLink());
  }
  return sum;
}

inline bool VariableSizedHandleScope::Contains(StackReference<mirror::Object>* handle_scope_entry)
    const {
  const LocalScopeType* cur = current_scope_;
  while (cur != nullptr) {
    if (cur->Contains(handle_scope_entry)) {
      return true;
    }
    cur = down_cast<const LocalScopeType*>(cur->GetLink());
  }
  return false;
}

template<class T>
Handle<T> VariableSizedHandleScope::GetHandle(size_t i) {
  // Handle the most common path efficiently.
  if (i < kNumReferencesPerScope) {
    return first_scope_.GetHandle<T>(i);
  }

  uint32_t size = Size();
  DCHECK_GT(size, kNumReferencesPerScope);
  DCHECK_LT(i, size);
  LocalScopeType* cur = current_scope_;
  DCHECK(cur != &first_scope_);
  // The linked list of local scopes starts from the latest which may not be fully filled.
  uint32_t cur_start = size - cur->Size();
  DCHECK_EQ(cur_start % kNumReferencesPerScope, 0u);  // All other local scopes are fully filled.
  while (i < cur_start) {
    cur = down_cast<LocalScopeType*>(cur->GetLink());
    DCHECK(cur != nullptr);
    DCHECK_EQ(cur->Size(), kNumReferencesPerScope);
    cur_start -= kNumReferencesPerScope;
  }
  return cur->GetHandle<T>(i - cur_start);
}

template <typename Visitor>
inline void VariableSizedHandleScope::VisitRoots(Visitor& visitor) {
  LocalScopeType* cur = current_scope_;
  while (cur != nullptr) {
    cur->VisitRoots(visitor);
    cur = down_cast<LocalScopeType*>(cur->GetLink());
  }
}

template <typename Visitor>
inline void VariableSizedHandleScope::VisitHandles(Visitor& visitor) {
  LocalScopeType* cur = current_scope_;
  while (cur != nullptr) {
    cur->VisitHandles(visitor);
    cur = down_cast<LocalScopeType*>(cur->GetLink());
  }
}

}  // namespace art

#endif  // ART_RUNTIME_HANDLE_SCOPE_INL_H_
