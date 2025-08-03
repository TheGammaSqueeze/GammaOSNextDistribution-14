/*
 * Copyright (C) 2013 The Android Open Source Project
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

#ifndef ART_RUNTIME_MIRROR_DEX_CACHE_INL_H_
#define ART_RUNTIME_MIRROR_DEX_CACHE_INL_H_

#include "dex_cache.h"

#include <android-base/logging.h>

#include "art_field.h"
#include "art_method.h"
#include "base/atomic_pair.h"
#include "base/casts.h"
#include "base/enums.h"
#include "class_linker.h"
#include "dex/dex_file.h"
#include "gc_root-inl.h"
#include "linear_alloc-inl.h"
#include "mirror/call_site.h"
#include "mirror/class.h"
#include "mirror/method_type.h"
#include "obj_ptr.h"
#include "object-inl.h"
#include "runtime.h"
#include "write_barrier-inl.h"

#include <atomic>

namespace art HIDDEN {
namespace mirror {

template<typename DexCachePair>
static void InitializeArray(std::atomic<DexCachePair>* array) {
  DexCachePair::Initialize(array);
}

template<typename T>
static void InitializeArray(T*) {
  // Nothing to do.
}

template<typename T>
T* DexCache::AllocArray(MemberOffset obj_offset, size_t num, LinearAllocKind kind, bool startup) {
  Thread* self = Thread::Current();
  mirror::DexCache* dex_cache = this;
  if (gUseReadBarrier && self->GetIsGcMarking()) {
    // Several code paths use DexCache without read-barrier for performance.
    // We have to check the "to-space" object here to avoid allocating twice.
    dex_cache = reinterpret_cast<DexCache*>(ReadBarrier::Mark(this));
  }
  // DON'T USE 'this' from now on.
  Runtime* runtime = Runtime::Current();
  // Note: in the 1002-notify-startup test, the startup linear alloc can become null
  // concurrently, even if the runtime is marked at startup. Therefore we should only
  // fetch it once here.
  LinearAlloc* startup_linear_alloc = runtime->GetStartupLinearAlloc();
  LinearAlloc* alloc = (startup && startup_linear_alloc != nullptr)
      ? startup_linear_alloc
      : runtime->GetClassLinker()->GetOrCreateAllocatorForClassLoader(GetClassLoader());
  MutexLock mu(self, *Locks::dex_cache_lock_);  // Avoid allocation by multiple threads.
  T* array = dex_cache->GetFieldPtr64<T*>(obj_offset);
  if (array != nullptr) {
    DCHECK(alloc->Contains(array));
    return array;  // Other thread just allocated the array.
  }
  array = reinterpret_cast<T*>(alloc->AllocAlign16(self, RoundUp(num * sizeof(T), 16), kind));
  InitializeArray(array);  // Ensure other threads see the array initialized.
  dex_cache->SetField64Volatile<false, false>(obj_offset, reinterpret_cast64<uint64_t>(array));
  return array;
}

template <typename T>
inline DexCachePair<T>::DexCachePair(ObjPtr<T> object, uint32_t index)
    : object(object), index(index) {}

template <typename T>
inline T* DexCachePair<T>::GetObjectForIndex(uint32_t idx) {
  if (idx != index) {
    return nullptr;
  }
  DCHECK(!object.IsNull());
  return object.Read();
}

template <typename T>
inline void DexCachePair<T>::Initialize(std::atomic<DexCachePair<T>>* dex_cache) {
  DexCachePair<T> first_elem;
  first_elem.object = GcRoot<T>(nullptr);
  first_elem.index = InvalidIndexForSlot(0);
  dex_cache[0].store(first_elem, std::memory_order_relaxed);
}

template <typename T>
inline void NativeDexCachePair<T>::Initialize(std::atomic<NativeDexCachePair<T>>* dex_cache) {
  NativeDexCachePair<T> first_elem;
  first_elem.object = nullptr;
  first_elem.index = InvalidIndexForSlot(0);

  auto* array = reinterpret_cast<AtomicPair<uintptr_t>*>(dex_cache);
  AtomicPair<uintptr_t> v(first_elem.index, reinterpret_cast<size_t>(first_elem.object));
  AtomicPairStoreRelease(&array[0], v);
}

template <typename T>
inline void GcRootArray<T>::Set(uint32_t index, T* value) {
  GcRoot<T> root(value);
  entries_[index].store(root, std::memory_order_relaxed);
}

template <typename T>
inline T* GcRootArray<T>::Get(uint32_t index) {
  return entries_[index].load(std::memory_order_relaxed).Read();
}

inline uint32_t DexCache::ClassSize(PointerSize pointer_size) {
  const uint32_t vtable_entries = Object::kVTableLength;
  return Class::ComputeClassSize(true, vtable_entries, 0, 0, 0, 0, 0, pointer_size);
}

inline String* DexCache::GetResolvedString(dex::StringIndex string_idx) {
  return GetStringsEntry(string_idx.index_);
}

inline void DexCache::SetResolvedString(dex::StringIndex string_idx, ObjPtr<String> resolved) {
  DCHECK(resolved != nullptr);
  SetStringsEntry(string_idx.index_, resolved.Ptr());
  Runtime* const runtime = Runtime::Current();
  if (UNLIKELY(runtime->IsActiveTransaction())) {
    DCHECK(runtime->IsAotCompiler());
    runtime->RecordResolveString(this, string_idx);
  }
  // TODO: Fine-grained marking, so that we don't need to go through all arrays in full.
  WriteBarrier::ForEveryFieldWrite(this);
}

inline void DexCache::ClearString(dex::StringIndex string_idx) {
  DCHECK(Runtime::Current()->IsAotCompiler());
  auto* array = GetStringsArray();
  if (array != nullptr) {
    array->Set(string_idx.index_, nullptr);
  }
  auto* strings = GetStrings();
  if (UNLIKELY(strings == nullptr)) {
    return;
  }
  strings->Clear(string_idx.index_);
}

inline Class* DexCache::GetResolvedType(dex::TypeIndex type_idx) {
  return GetResolvedTypesEntry(type_idx.index_);
}

inline void DexCache::ClearResolvedType(dex::TypeIndex type_idx) {
  DCHECK(Runtime::Current()->IsAotCompiler());
  auto* array = GetResolvedTypesArray();
  if (array != nullptr) {
    array->Set(type_idx.index_, nullptr);
  }
  auto* resolved_types = GetResolvedTypes();
  if (UNLIKELY(resolved_types == nullptr)) {
    return;
  }
  resolved_types->Clear(type_idx.index_);
}

inline MethodType* DexCache::GetResolvedMethodType(dex::ProtoIndex proto_idx) {
  return GetResolvedMethodTypesEntry(proto_idx.index_);
}

inline void DexCache::SetResolvedMethodType(dex::ProtoIndex proto_idx, MethodType* resolved) {
  DCHECK(resolved != nullptr);
  SetResolvedMethodTypesEntry(proto_idx.index_, resolved);

  Runtime* const runtime = Runtime::Current();
  if (UNLIKELY(runtime->IsActiveTransaction())) {
    DCHECK(runtime->IsAotCompiler());
    runtime->RecordResolveMethodType(this, proto_idx);
  }
  // TODO: Fine-grained marking, so that we don't need to go through all arrays in full.
  WriteBarrier::ForEveryFieldWrite(this);
}

inline void DexCache::ClearMethodType(dex::ProtoIndex proto_idx) {
  DCHECK(Runtime::Current()->IsAotCompiler());
  auto* array = GetResolvedMethodTypesArray();
  if (array != nullptr) {
    array->Set(proto_idx.index_, nullptr);
  }
  auto* methods = GetResolvedMethodTypes();
  if (methods == nullptr) {
    return;
  }
  methods->Clear(proto_idx.index_);
}

inline CallSite* DexCache::GetResolvedCallSite(uint32_t call_site_idx) {
  DCHECK(Runtime::Current()->IsMethodHandlesEnabled());
  DCHECK_LT(call_site_idx, GetDexFile()->NumCallSiteIds());
  GcRootArray<CallSite>* call_sites = GetResolvedCallSites();
  if (UNLIKELY(call_sites == nullptr)) {
    return nullptr;
  }
  Atomic<GcRoot<mirror::CallSite>>* target = call_sites->GetGcRoot(call_site_idx);
  return target->load(std::memory_order_seq_cst).Read();
}

inline ObjPtr<CallSite> DexCache::SetResolvedCallSite(uint32_t call_site_idx,
                                                      ObjPtr<CallSite> call_site) {
  DCHECK(Runtime::Current()->IsMethodHandlesEnabled());
  DCHECK_LT(call_site_idx, GetDexFile()->NumCallSiteIds());

  GcRoot<mirror::CallSite> null_call_site(nullptr);
  GcRoot<mirror::CallSite> candidate(call_site);
  GcRootArray<CallSite>* call_sites = GetResolvedCallSites();
  if (UNLIKELY(call_sites == nullptr)) {
    call_sites = AllocateResolvedCallSites();
  }
  Atomic<GcRoot<mirror::CallSite>>* target = call_sites->GetGcRoot(call_site_idx);

  // The first assignment for a given call site wins.
  if (target->CompareAndSetStrongSequentiallyConsistent(null_call_site, candidate)) {
    // TODO: Fine-grained marking, so that we don't need to go through all arrays in full.
    WriteBarrier::ForEveryFieldWrite(this);
    return call_site;
  } else {
    return target->load(std::memory_order_relaxed).Read();
  }
}

inline ArtField* DexCache::GetResolvedField(uint32_t field_idx) {
  return GetResolvedFieldsEntry(field_idx);
}

inline void DexCache::SetResolvedField(uint32_t field_idx, ArtField* field) {
  SetResolvedFieldsEntry(field_idx, field);
}

inline ArtMethod* DexCache::GetResolvedMethod(uint32_t method_idx) {
  return GetResolvedMethodsEntry(method_idx);
}

inline void DexCache::SetResolvedMethod(uint32_t method_idx, ArtMethod* method) {
  SetResolvedMethodsEntry(method_idx, method);
}

template <ReadBarrierOption kReadBarrierOption,
          typename Visitor,
          typename T>
inline void VisitDexCachePairs(T* array,
                               size_t num_pairs,
                               const Visitor& visitor)
    REQUIRES_SHARED(Locks::mutator_lock_) REQUIRES(Locks::heap_bitmap_lock_) {
  // Check both the data pointer and count since the array might be initialized
  // concurrently on other thread, and we might observe just one of the values.
  for (size_t i = 0; array != nullptr && i < num_pairs; ++i) {
    auto source = array->GetPair(i);
    // NOTE: We need the "template" keyword here to avoid a compilation
    // failure. GcRoot<T> is a template argument-dependent type and we need to
    // tell the compiler to treat "Read" as a template rather than a field or
    // function. Otherwise, on encountering the "<" token, the compiler would
    // treat "Read" as a field.
    auto const before = source.object.template Read<kReadBarrierOption>();
    visitor.VisitRootIfNonNull(source.object.AddressWithoutBarrier());
    if (source.object.template Read<kReadBarrierOption>() != before) {
      array->SetPair(i, source);
    }
  }
}

template <typename Visitor>
void DexCache::VisitDexCachePairRoots(Visitor& visitor,
                                      DexCachePair<Object>* pairs_begin,
                                      DexCachePair<Object>* pairs_end) {
  for (; pairs_begin < pairs_end; pairs_begin++) {
    visitor.VisitRootIfNonNull(pairs_begin->object.AddressWithoutBarrier());
  }
}

template <bool kVisitNativeRoots,
          VerifyObjectFlags kVerifyFlags,
          ReadBarrierOption kReadBarrierOption,
          typename Visitor>
inline void DexCache::VisitReferences(ObjPtr<Class> klass, const Visitor& visitor) {
  // Visit instance fields first.
  VisitInstanceFieldsReferences<kVerifyFlags, kReadBarrierOption>(klass, visitor);
  // Visit arrays after.
  if (kVisitNativeRoots) {
    VisitNativeRoots<kVerifyFlags, kReadBarrierOption>(visitor);
  }
}

template <VerifyObjectFlags kVerifyFlags,
          ReadBarrierOption kReadBarrierOption,
          typename Visitor>
inline void DexCache::VisitNativeRoots(const Visitor& visitor) {
  VisitDexCachePairs<kReadBarrierOption, Visitor>(
      GetStrings<kVerifyFlags>(), NumStrings<kVerifyFlags>(), visitor);

  VisitDexCachePairs<kReadBarrierOption, Visitor>(
      GetResolvedTypes<kVerifyFlags>(), NumResolvedTypes<kVerifyFlags>(), visitor);

  VisitDexCachePairs<kReadBarrierOption, Visitor>(
      GetResolvedMethodTypes<kVerifyFlags>(), NumResolvedMethodTypes<kVerifyFlags>(), visitor);

  GcRootArray<mirror::CallSite>* resolved_call_sites = GetResolvedCallSites<kVerifyFlags>();
  size_t num_call_sites = NumResolvedCallSites<kVerifyFlags>();
  for (size_t i = 0; resolved_call_sites != nullptr && i != num_call_sites; ++i) {
    visitor.VisitRootIfNonNull(resolved_call_sites->GetGcRootAddress(i)->AddressWithoutBarrier());
  }

  // Dex cache arrays can be reset and cleared during app startup. Assert we do not get
  // suspended to ensure the arrays are not deallocated.
  ScopedAssertNoThreadSuspension soants("dex caches");
  GcRootArray<mirror::Class>* resolved_types = GetResolvedTypesArray<kVerifyFlags>();
  size_t num_resolved_types = NumResolvedTypesArray<kVerifyFlags>();
  for (size_t i = 0; resolved_types != nullptr && i != num_resolved_types; ++i) {
    visitor.VisitRootIfNonNull(resolved_types->GetGcRootAddress(i)->AddressWithoutBarrier());
  }

  GcRootArray<mirror::String>* resolved_strings = GetStringsArray<kVerifyFlags>();
  size_t num_resolved_strings = NumStringsArray<kVerifyFlags>();
  for (size_t i = 0; resolved_strings != nullptr && i != num_resolved_strings; ++i) {
    visitor.VisitRootIfNonNull(resolved_strings->GetGcRootAddress(i)->AddressWithoutBarrier());
  }

  GcRootArray<mirror::MethodType>* resolved_method_types =
      GetResolvedMethodTypesArray<kVerifyFlags>();
  size_t num_resolved_method_types = NumResolvedMethodTypesArray<kVerifyFlags>();
  for (size_t i = 0; resolved_method_types != nullptr && i != num_resolved_method_types; ++i) {
    visitor.VisitRootIfNonNull(resolved_method_types->GetGcRootAddress(i)->AddressWithoutBarrier());
  }
}

template <VerifyObjectFlags kVerifyFlags, ReadBarrierOption kReadBarrierOption>
inline ObjPtr<String> DexCache::GetLocation() {
  return GetFieldObject<String, kVerifyFlags, kReadBarrierOption>(
      OFFSET_OF_OBJECT_MEMBER(DexCache, location_));
}

}  // namespace mirror
}  // namespace art

#endif  // ART_RUNTIME_MIRROR_DEX_CACHE_INL_H_
