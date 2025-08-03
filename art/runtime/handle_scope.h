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

#ifndef ART_RUNTIME_HANDLE_SCOPE_H_
#define ART_RUNTIME_HANDLE_SCOPE_H_

#include <stack>

#include <android-base/logging.h>

#include "base/enums.h"
#include "base/locks.h"
#include "base/macros.h"
#include "stack_reference.h"

namespace art HIDDEN {

template<class T> class Handle;
class HandleScope;
template<class T> class HandleWrapper;
template<class T> class HandleWrapperObjPtr;
template<class T> class MutableHandle;
template<class MirrorType> class ObjPtr;
class Thread;
class VariableSizedHandleScope;

namespace mirror {
class Object;
}  // namespace mirror

// Basic handle scope, tracked by a list. May be variable sized.
class PACKED(4) BaseHandleScope {
 public:
  bool IsVariableSized() const {
    return capacity_ == kNumReferencesVariableSized;
  }

  // The current size of this handle scope.
  ALWAYS_INLINE uint32_t Size() const;

  // The current capacity of this handle scope.
  // It can change (increase) only for a `VariableSizedHandleScope`.
  ALWAYS_INLINE uint32_t Capacity() const;

  ALWAYS_INLINE bool Contains(StackReference<mirror::Object>* handle_scope_entry) const;

  template <typename Visitor>
  ALWAYS_INLINE void VisitRoots(Visitor& visitor) REQUIRES_SHARED(Locks::mutator_lock_);

  template <typename Visitor>
  ALWAYS_INLINE void VisitHandles(Visitor& visitor) REQUIRES_SHARED(Locks::mutator_lock_);

  // Link to previous BaseHandleScope or null.
  BaseHandleScope* GetLink() const {
    return link_;
  }

  ALWAYS_INLINE VariableSizedHandleScope* AsVariableSized();
  ALWAYS_INLINE HandleScope* AsHandleScope();
  ALWAYS_INLINE const VariableSizedHandleScope* AsVariableSized() const;
  ALWAYS_INLINE const HandleScope* AsHandleScope() const;

 protected:
  BaseHandleScope(BaseHandleScope* link, uint32_t capacity)
      : link_(link),
        capacity_(capacity) {}

  // Variable sized constructor.
  explicit BaseHandleScope(BaseHandleScope* link)
      : link_(link),
        capacity_(kNumReferencesVariableSized) {}

  static constexpr int32_t kNumReferencesVariableSized = -1;

  // Link-list of handle scopes. The root is held by a Thread.
  BaseHandleScope* const link_;

  // Number of handlerized references. -1 for variable sized handle scopes.
  const int32_t capacity_;

 private:
  DISALLOW_COPY_AND_ASSIGN(BaseHandleScope);
};

// HandleScopes are scoped objects containing a number of Handles. They are used to allocate
// handles, for these handles (and the objects contained within them) to be visible/roots for the
// GC. It is most common to stack allocate HandleScopes using StackHandleScope.
class PACKED(4) HandleScope : public BaseHandleScope {
 public:
  ~HandleScope() {}

  ALWAYS_INLINE ObjPtr<mirror::Object> GetReference(size_t i) const
      REQUIRES_SHARED(Locks::mutator_lock_);

  template<class T>
  ALWAYS_INLINE Handle<T> GetHandle(size_t i) REQUIRES_SHARED(Locks::mutator_lock_);

  template<class T>
  ALWAYS_INLINE MutableHandle<T> GetMutableHandle(size_t i) REQUIRES_SHARED(Locks::mutator_lock_);

  ALWAYS_INLINE void SetReference(size_t i, ObjPtr<mirror::Object> object)
      REQUIRES_SHARED(Locks::mutator_lock_);

  template<class T>
  ALWAYS_INLINE MutableHandle<T> NewHandle(T* object) REQUIRES_SHARED(Locks::mutator_lock_);

  template<class T>
  ALWAYS_INLINE HandleWrapper<T> NewHandleWrapper(T** object)
      REQUIRES_SHARED(Locks::mutator_lock_);

  template<class T>
  ALWAYS_INLINE HandleWrapperObjPtr<T> NewHandleWrapper(ObjPtr<T>* object)
      REQUIRES_SHARED(Locks::mutator_lock_);

  template<class MirrorType>
  ALWAYS_INLINE MutableHandle<MirrorType> NewHandle(ObjPtr<MirrorType> object)
    REQUIRES_SHARED(Locks::mutator_lock_);

  ALWAYS_INLINE bool Contains(StackReference<mirror::Object>* handle_scope_entry) const;

  // Offset of link within HandleScope, used by generated code.
  static constexpr size_t LinkOffset([[maybe_unused]] PointerSize pointer_size) { return 0; }

  // Offset of length within handle scope, used by generated code.
  static constexpr size_t CapacityOffset(PointerSize pointer_size) {
    return static_cast<size_t>(pointer_size);
  }

  // Offset of link within handle scope, used by generated code.
  static constexpr size_t ReferencesOffset(PointerSize pointer_size) {
    return CapacityOffset(pointer_size) + sizeof(capacity_) + sizeof(size_);
  }

  // The current size of this handle scope.
  ALWAYS_INLINE uint32_t Size() const {
    return size_;
  }

  // The capacity of this handle scope, immutable.
  ALWAYS_INLINE uint32_t Capacity() const {
    DCHECK_GT(capacity_, 0);
    return static_cast<uint32_t>(capacity_);
  }

  template <typename Visitor>
  ALWAYS_INLINE void VisitRoots(Visitor& visitor) REQUIRES_SHARED(Locks::mutator_lock_);

  template <typename Visitor>
  ALWAYS_INLINE void VisitHandles(Visitor& visitor) REQUIRES_SHARED(Locks::mutator_lock_);

 protected:
  // Return backing storage used for references.
  ALWAYS_INLINE StackReference<mirror::Object>* GetReferences() const {
    uintptr_t address = reinterpret_cast<uintptr_t>(this) + ReferencesOffset(kRuntimePointerSize);
    return reinterpret_cast<StackReference<mirror::Object>*>(address);
  }

  explicit HandleScope(size_t capacity) : HandleScope(nullptr, capacity) {}

  HandleScope(BaseHandleScope* link, uint32_t capacity)
      : BaseHandleScope(link, capacity) {
    // Handle scope should be created only if we have a code path that stores something in it.
    // We may not take that code path and the handle scope may remain empty.
    DCHECK_NE(capacity, 0u);
  }

  // Position new handles will be created.
  uint32_t size_ = 0;

  // Storage for references is in derived classes.
  // StackReference<mirror::Object> references_[capacity_]

 private:
  DISALLOW_COPY_AND_ASSIGN(HandleScope);
};

// Fixed size handle scope that is not necessarily linked in the thread.
template<size_t kNumReferences>
class PACKED(4) FixedSizeHandleScope : public HandleScope {
 private:
  explicit ALWAYS_INLINE FixedSizeHandleScope(BaseHandleScope* link)
      REQUIRES_SHARED(Locks::mutator_lock_);
  ALWAYS_INLINE ~FixedSizeHandleScope() REQUIRES_SHARED(Locks::mutator_lock_) {}

  // Reference storage.
  StackReference<mirror::Object> storage_[kNumReferences];

  template<size_t kNumRefs> friend class StackHandleScope;
  friend class VariableSizedHandleScope;
};

// Scoped handle storage of a fixed size that is stack allocated.
template<size_t kNumReferences>
class PACKED(4) StackHandleScope final : public FixedSizeHandleScope<kNumReferences> {
 public:
  explicit ALWAYS_INLINE StackHandleScope(Thread* self)
      REQUIRES_SHARED(Locks::mutator_lock_);

  ALWAYS_INLINE ~StackHandleScope() REQUIRES_SHARED(Locks::mutator_lock_);

  Thread* Self() const {
    return self_;
  }

 private:
  // The thread that the stack handle scope is a linked list upon. The stack handle scope will
  // push and pop itself from this thread.
  Thread* const self_;
};

// Utility class to manage a variable sized handle scope by having a list of fixed size handle
// scopes.
// Calls to NewHandle will create a new handle inside the current FixedSizeHandleScope.
// When the current handle scope becomes full a new one is created and put at the front of the
// list.
class VariableSizedHandleScope : public BaseHandleScope {
 public:
  explicit VariableSizedHandleScope(Thread* const self) REQUIRES_SHARED(Locks::mutator_lock_);
  ~VariableSizedHandleScope() REQUIRES_SHARED(Locks::mutator_lock_);

  template<class T>
  MutableHandle<T> NewHandle(T* object) REQUIRES_SHARED(Locks::mutator_lock_);

  template<class MirrorType>
  MutableHandle<MirrorType> NewHandle(ObjPtr<MirrorType> ptr)
      REQUIRES_SHARED(Locks::mutator_lock_);

  // The current size of this handle scope.
  ALWAYS_INLINE uint32_t Size() const;

  // The current capacity of this handle scope.
  ALWAYS_INLINE uint32_t Capacity() const;

  // Retrieve a `Handle<>` based on the slot index (in handle creation order).
  // Note: This is linear in the size of the scope, so it should be used carefully.
  template<class T>
  ALWAYS_INLINE Handle<T> GetHandle(size_t i) REQUIRES_SHARED(Locks::mutator_lock_);

  ALWAYS_INLINE bool Contains(StackReference<mirror::Object>* handle_scope_entry) const;

  template <typename Visitor>
  void VisitRoots(Visitor& visitor) REQUIRES_SHARED(Locks::mutator_lock_);

  template <typename Visitor>
  ALWAYS_INLINE void VisitHandles(Visitor& visitor) REQUIRES_SHARED(Locks::mutator_lock_);

 private:
  static constexpr size_t kLocalScopeSize = 64u;
  static constexpr size_t kSizeOfReferencesPerScope =
      kLocalScopeSize
          - /* BaseHandleScope::link_ */ sizeof(BaseHandleScope*)
          - /* BaseHandleScope::capacity_ */ sizeof(int32_t)
          - /* HandleScope<>::size_ */ sizeof(uint32_t);
  static constexpr size_t kNumReferencesPerScope =
      kSizeOfReferencesPerScope / sizeof(StackReference<mirror::Object>);

  Thread* const self_;

  // Linked list of fixed size handle scopes.
  using LocalScopeType = FixedSizeHandleScope<kNumReferencesPerScope>;
  static_assert(sizeof(LocalScopeType) == kLocalScopeSize, "Unexpected size of LocalScopeType");
  LocalScopeType* current_scope_;
  LocalScopeType first_scope_;

  DISALLOW_COPY_AND_ASSIGN(VariableSizedHandleScope);
};

}  // namespace art

#endif  // ART_RUNTIME_HANDLE_SCOPE_H_
