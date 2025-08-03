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

#include "art_method-inl.h"
#include "base/callee_save_type.h"
#include "callee_save_frame.h"
#include "class_linker-inl.h"
#include "class_table-inl.h"
#include "dex/dex_file-inl.h"
#include "dex/dex_file_types.h"
#include "entrypoints/entrypoint_utils-inl.h"
#include "gc/heap.h"
#include "jvalue-inl.h"
#include "mirror/class-inl.h"
#include "mirror/class_loader.h"
#include "mirror/object-inl.h"
#include "mirror/object_array-inl.h"
#include "oat/oat_file.h"
#include "oat/oat_file-inl.h"
#include "runtime.h"

namespace art HIDDEN {

static void StoreObjectInBss(ArtMethod* outer_method,
                             const OatFile* oat_file,
                             size_t bss_offset,
                             ObjPtr<mirror::Object> object) REQUIRES_SHARED(Locks::mutator_lock_) {
  // Used for storing Class or String in .bss GC roots.
  static_assert(sizeof(GcRoot<mirror::Class>) == sizeof(GcRoot<mirror::Object>), "Size check.");
  static_assert(sizeof(GcRoot<mirror::String>) == sizeof(GcRoot<mirror::Object>), "Size check.");
  DCHECK_NE(bss_offset, IndexBssMappingLookup::npos);
  DCHECK_ALIGNED(bss_offset, sizeof(GcRoot<mirror::Object>));
  DCHECK_NE(oat_file, nullptr);
  if (UNLIKELY(!oat_file->IsExecutable())) {
    // There are situations where we execute bytecode tied to an oat file opened
    // as non-executable (i.e. the AOT-compiled code cannot be executed) and we
    // can JIT that bytecode and get here without the .bss being mmapped.
    return;
  }
  GcRoot<mirror::Object>* slot = reinterpret_cast<GcRoot<mirror::Object>*>(
      const_cast<uint8_t*>(oat_file->BssBegin() + bss_offset));
  DCHECK_GE(slot, oat_file->GetBssGcRoots().data());
  DCHECK_LT(slot, oat_file->GetBssGcRoots().data() + oat_file->GetBssGcRoots().size());
  if (slot->IsNull()) {
    // This may race with another thread trying to store the very same value but that's OK.
    std::atomic<GcRoot<mirror::Object>>* atomic_slot =
        reinterpret_cast<std::atomic<GcRoot<mirror::Object>>*>(slot);
    static_assert(sizeof(*slot) == sizeof(*atomic_slot), "Size check");
    atomic_slot->store(GcRoot<mirror::Object>(object), std::memory_order_release);
    // We need a write barrier for the class loader that holds the GC roots in the .bss.
    ObjPtr<mirror::ClassLoader> class_loader = outer_method->GetClassLoader();
    Runtime* runtime = Runtime::Current();
    if (kIsDebugBuild) {
      ClassTable* class_table = runtime->GetClassLinker()->ClassTableForClassLoader(class_loader);
      CHECK(class_table != nullptr && !class_table->InsertOatFile(oat_file))
          << "Oat file with .bss GC roots was not registered in class table: "
          << oat_file->GetLocation() << ", " << outer_method->PrettyMethod();
    }
    if (class_loader != nullptr) {
      WriteBarrier::ForEveryFieldWrite(class_loader);
    } else {
      runtime->GetClassLinker()->WriteBarrierForBootOatFileBssRoots(oat_file);
    }
  } else {
    // Each slot serves to store exactly one Class or String.
    DCHECK_EQ(object, slot->Read());
  }
}

static inline void StoreTypeInBss(ArtMethod* caller,
                                  dex::TypeIndex type_idx,
                                  ObjPtr<mirror::Class> resolved_type,
                                  ArtMethod* outer_method) REQUIRES_SHARED(Locks::mutator_lock_) {
  const DexFile* dex_file = caller->GetDexFile();
  DCHECK_NE(dex_file, nullptr);

  if (outer_method->GetDexFile()->GetOatDexFile() == nullptr ||
      outer_method->GetDexFile()->GetOatDexFile()->GetOatFile() == nullptr) {
    // No OatFile to update.
    return;
  }
  const OatFile* outer_oat_file = outer_method->GetDexFile()->GetOatDexFile()->GetOatFile();

  // DexFiles compiled together to an oat file case.
  const OatDexFile* oat_dex_file = dex_file->GetOatDexFile();
  const IndexBssMapping* type_mapping = nullptr;
  const IndexBssMapping* public_type_mapping = nullptr;
  const IndexBssMapping* package_type_mapping = nullptr;
  if (oat_dex_file != nullptr && oat_dex_file->GetOatFile() == outer_oat_file) {
    type_mapping = oat_dex_file->GetTypeBssMapping();
    public_type_mapping = oat_dex_file->GetPublicTypeBssMapping();
    package_type_mapping = oat_dex_file->GetPackageTypeBssMapping();
  } else {
    // Try to find the DexFile in the BCP of the outer_method.
    const OatFile::BssMappingInfo* mapping_info = outer_oat_file->FindBcpMappingInfo(dex_file);
    if (mapping_info != nullptr) {
      type_mapping = mapping_info->type_bss_mapping;
      public_type_mapping = mapping_info->public_type_bss_mapping;
      package_type_mapping = mapping_info->package_type_bss_mapping;
    }
  }

  // Perform the update if we found a mapping.
  auto store = [=](const IndexBssMapping* mapping) REQUIRES_SHARED(Locks::mutator_lock_) {
    if (mapping != nullptr) {
      size_t bss_offset = IndexBssMappingLookup::GetBssOffset(
          mapping, type_idx.index_, dex_file->NumTypeIds(), sizeof(GcRoot<mirror::Class>));
      if (bss_offset != IndexBssMappingLookup::npos) {
        StoreObjectInBss(outer_method, outer_oat_file, bss_offset, resolved_type);
      }
    }
  };
  store(type_mapping);
  if (resolved_type->IsPublic()) {
    store(public_type_mapping);
  }
  if (resolved_type->IsPublic() || resolved_type->GetClassLoader() == caller->GetClassLoader()) {
    store(package_type_mapping);
  }
}

static inline void StoreStringInBss(ArtMethod* caller,
                                    dex::StringIndex string_idx,
                                    ObjPtr<mirror::String> resolved_string,
                                    ArtMethod* outer_method) REQUIRES_SHARED(Locks::mutator_lock_) {
  const DexFile* dex_file = caller->GetDexFile();
  DCHECK_NE(dex_file, nullptr);

  if (outer_method->GetDexFile()->GetOatDexFile() == nullptr ||
      outer_method->GetDexFile()->GetOatDexFile()->GetOatFile() == nullptr) {
    // No OatFile to update.
    return;
  }
  const OatFile* outer_oat_file = outer_method->GetDexFile()->GetOatDexFile()->GetOatFile();

  const OatDexFile* oat_dex_file = dex_file->GetOatDexFile();
  const IndexBssMapping* mapping = nullptr;
  if (oat_dex_file != nullptr && oat_dex_file->GetOatFile() == outer_oat_file) {
    // DexFiles compiled together to an oat file case.
    mapping = oat_dex_file->GetStringBssMapping();
  } else {
    // Try to find the DexFile in the BCP of the outer_method.
    const OatFile::BssMappingInfo* mapping_info = outer_oat_file->FindBcpMappingInfo(dex_file);
    if (mapping_info != nullptr) {
      mapping = mapping_info->string_bss_mapping;
    }
  }

  // Perform the update if we found a mapping.
  if (mapping != nullptr) {
    size_t bss_offset = IndexBssMappingLookup::GetBssOffset(
        mapping, string_idx.index_, dex_file->NumStringIds(), sizeof(GcRoot<mirror::String>));
    if (bss_offset != IndexBssMappingLookup::npos) {
      StoreObjectInBss(outer_method, outer_oat_file, bss_offset, resolved_string);
    }
  }
}

static inline void StoreMethodTypeInBss(ArtMethod* caller,
                                        dex::ProtoIndex proto_idx,
                                        ObjPtr<mirror::MethodType> resolved_method_type,
                                        ArtMethod* outer_method)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  const DexFile* dex_file = caller->GetDexFile();
  DCHECK_NE(dex_file, nullptr);

  if (outer_method->GetDexFile()->GetOatDexFile() == nullptr ||
      outer_method->GetDexFile()->GetOatDexFile()->GetOatFile() == nullptr) {
    // No OatFile to update.
    return;
  }
  const OatFile* outer_oat_file = outer_method->GetDexFile()->GetOatDexFile()->GetOatFile();

  const OatDexFile* oat_dex_file = dex_file->GetOatDexFile();
  const IndexBssMapping* mapping = nullptr;
  if (oat_dex_file != nullptr && oat_dex_file->GetOatFile() == outer_oat_file) {
    // DexFiles compiled together to an oat file case.
    mapping = oat_dex_file->GetMethodTypeBssMapping();
  } else {
    // Try to find the DexFile in the BCP of the outer_method.
    const OatFile::BssMappingInfo* mapping_info = outer_oat_file->FindBcpMappingInfo(dex_file);
    if (mapping_info != nullptr) {
      mapping = mapping_info->method_type_bss_mapping;
    }
  }

  // Perform the update if we found a mapping.
  if (mapping != nullptr) {
    size_t bss_offset = IndexBssMappingLookup::GetBssOffset(
        mapping, proto_idx.index_, dex_file->NumProtoIds(), sizeof(GcRoot<mirror::MethodType>));
    if (bss_offset != IndexBssMappingLookup::npos) {
      StoreObjectInBss(outer_method, outer_oat_file, bss_offset, resolved_method_type);
    }
  }
}

extern "C" mirror::Class* artInitializeStaticStorageFromCode(mirror::Class* klass, Thread* self)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  // Called to ensure static storage base is initialized for direct static field reads and writes.
  // A class may be accessing another class' fields when it doesn't have access, as access has been
  // given by inheritance.
  ScopedQuickEntrypointChecks sqec(self);
  DCHECK(klass != nullptr);
  ClassLinker* class_linker = Runtime::Current()->GetClassLinker();
  StackHandleScope<1> hs(self);
  Handle<mirror::Class> h_klass = hs.NewHandle(klass);
  bool success = class_linker->EnsureInitialized(
      self, h_klass, /* can_init_fields= */ true, /* can_init_parents= */ true);
  if (UNLIKELY(!success)) {
    return nullptr;
  }
  return h_klass.Get();
}

extern "C" mirror::Class* artResolveTypeFromCode(uint32_t type_idx, Thread* self)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  // Called when the .bss slot was empty or for main-path runtime call.
  ScopedQuickEntrypointChecks sqec(self);
  auto caller_and_outer = GetCalleeSaveMethodCallerAndOuterMethod(
      self, CalleeSaveType::kSaveEverythingForClinit);
  ArtMethod* caller = caller_and_outer.caller;
  ObjPtr<mirror::Class> result = ResolveVerifyAndClinit(dex::TypeIndex(type_idx),
                                                        caller,
                                                        self,
                                                        /* can_run_clinit= */ false,
                                                        /* verify_access= */ false);
  ArtMethod* outer_method = caller_and_outer.outer_method;
  if (LIKELY(result != nullptr)) {
    StoreTypeInBss(caller, dex::TypeIndex(type_idx), result, outer_method);
  }
  return result.Ptr();
}

extern "C" mirror::Class* artResolveTypeAndVerifyAccessFromCode(uint32_t type_idx, Thread* self)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  // Called when caller isn't guaranteed to have access to a type.
  ScopedQuickEntrypointChecks sqec(self);
  auto caller_and_outer = GetCalleeSaveMethodCallerAndOuterMethod(self,
                                                                  CalleeSaveType::kSaveEverything);
  ArtMethod* caller = caller_and_outer.caller;
  ObjPtr<mirror::Class> result = ResolveVerifyAndClinit(dex::TypeIndex(type_idx),
                                                        caller,
                                                        self,
                                                        /* can_run_clinit= */ false,
                                                        /* verify_access= */ true);
  ArtMethod* outer_method = caller_and_outer.outer_method;
  if (LIKELY(result != nullptr)) {
    StoreTypeInBss(caller, dex::TypeIndex(type_idx), result, outer_method);
  }
  return result.Ptr();
}

extern "C" mirror::MethodHandle* artResolveMethodHandleFromCode(uint32_t method_handle_idx,
                                                                Thread* self)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  ScopedQuickEntrypointChecks sqec(self);
  auto caller_and_outer =
      GetCalleeSaveMethodCallerAndOuterMethod(self, CalleeSaveType::kSaveEverything);
  ArtMethod* caller = caller_and_outer.caller;
  ObjPtr<mirror::MethodHandle> result = ResolveMethodHandleFromCode(caller, method_handle_idx);
  return result.Ptr();
}

extern "C" mirror::MethodType* artResolveMethodTypeFromCode(uint32_t proto_idx, Thread* self)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  ScopedQuickEntrypointChecks sqec(self);
  auto caller_and_outer = GetCalleeSaveMethodCallerAndOuterMethod(self,
                                                                  CalleeSaveType::kSaveEverything);
  ArtMethod* caller = caller_and_outer.caller;
  ObjPtr<mirror::MethodType> result = ResolveMethodTypeFromCode(caller, dex::ProtoIndex(proto_idx));
  ArtMethod* outer_method = caller_and_outer.outer_method;
  if (LIKELY(result != nullptr)) {
    StoreMethodTypeInBss(caller, dex::ProtoIndex(proto_idx), result, outer_method);
  }
  return result.Ptr();
}

extern "C" mirror::String* artResolveStringFromCode(int32_t string_idx, Thread* self)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  ScopedQuickEntrypointChecks sqec(self);
  auto caller_and_outer = GetCalleeSaveMethodCallerAndOuterMethod(self,
                                                                  CalleeSaveType::kSaveEverything);
  ArtMethod* caller = caller_and_outer.caller;
  ObjPtr<mirror::String> result =
      Runtime::Current()->GetClassLinker()->ResolveString(dex::StringIndex(string_idx), caller);
  ArtMethod* outer_method = caller_and_outer.outer_method;
  if (LIKELY(result != nullptr)) {
    StoreStringInBss(caller, dex::StringIndex(string_idx), result, outer_method);
  }
  return result.Ptr();
}

}  // namespace art
