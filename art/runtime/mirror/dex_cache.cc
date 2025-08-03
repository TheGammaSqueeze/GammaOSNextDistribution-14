/*
 * Copyright (C) 2011 The Android Open Source Project
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

#include "dex_cache-inl.h"

#include "art_method-inl.h"
#include "class_linker.h"
#include "gc/accounting/card_table-inl.h"
#include "gc/heap.h"
#include "jit/profile_saver.h"
#include "linear_alloc.h"
#include "oat/oat_file.h"
#include "object-inl.h"
#include "object.h"
#include "object_array-inl.h"
#include "reflective_value_visitor.h"
#include "runtime.h"
#include "runtime_globals.h"
#include "string.h"
#include "thread.h"
#include "write_barrier.h"

namespace art HIDDEN {
namespace mirror {

// Whether to allocate full dex cache arrays during startup. Currently disabled
// while debugging b/283632504.
static constexpr bool kEnableFullArraysAtStartup = false;

void DexCache::Initialize(const DexFile* dex_file, ObjPtr<ClassLoader> class_loader) {
  DCHECK(GetDexFile() == nullptr);
  DCHECK(GetStrings() == nullptr);
  DCHECK(GetResolvedTypes() == nullptr);
  DCHECK(GetResolvedMethods() == nullptr);
  DCHECK(GetResolvedFields() == nullptr);
  DCHECK(GetResolvedMethodTypes() == nullptr);
  DCHECK(GetResolvedCallSites() == nullptr);

  ScopedAssertNoThreadSuspension sants(__FUNCTION__);

  SetDexFile(dex_file);
  SetClassLoader(class_loader);
}

void DexCache::VisitReflectiveTargets(ReflectiveValueVisitor* visitor) {
  bool wrote = false;
  auto* fields = GetResolvedFields();
  size_t num_fields = NumResolvedFields();
  // Check both the data pointer and count since the array might be initialized
  // concurrently on other thread, and we might observe just one of the values.
  for (size_t i = 0; fields != nullptr && i < num_fields; i++) {
    auto pair(fields->GetNativePair(i));
    if (pair.index == NativeDexCachePair<ArtField>::InvalidIndexForSlot(i)) {
      continue;
    }
    ArtField* new_val = visitor->VisitField(
        pair.object, DexCacheSourceInfo(kSourceDexCacheResolvedField, pair.index, this));
    if (UNLIKELY(new_val != pair.object)) {
      if (new_val == nullptr) {
        pair = NativeDexCachePair<ArtField>(
            nullptr, NativeDexCachePair<ArtField>::InvalidIndexForSlot(i));
      } else {
        pair.object = new_val;
      }
      fields->SetNativePair(i, pair);
      wrote = true;
    }
  }
  auto* methods = GetResolvedMethods();
  size_t num_methods = NumResolvedMethods();
  // Check both the data pointer and count since the array might be initialized
  // concurrently on other thread, and we might observe just one of the values.
  for (size_t i = 0; methods != nullptr && i < num_methods; i++) {
    auto pair(methods->GetNativePair(i));
    if (pair.index == NativeDexCachePair<ArtMethod>::InvalidIndexForSlot(i)) {
      continue;
    }
    ArtMethod* new_val = visitor->VisitMethod(
        pair.object, DexCacheSourceInfo(kSourceDexCacheResolvedMethod, pair.index, this));
    if (UNLIKELY(new_val != pair.object)) {
      if (new_val == nullptr) {
        pair = NativeDexCachePair<ArtMethod>(
            nullptr, NativeDexCachePair<ArtMethod>::InvalidIndexForSlot(i));
      } else {
        pair.object = new_val;
      }
      methods->SetNativePair(i, pair);
      wrote = true;
    }
  }

  auto* fields_array = GetResolvedFieldsArray();
  num_fields = NumResolvedFieldsArray();
  for (size_t i = 0; fields_array != nullptr && i < num_fields; i++) {
    ArtField* old_val = fields_array->Get(i);
    if (old_val == nullptr) {
      continue;
    }
    ArtField* new_val = visitor->VisitField(
        old_val, DexCacheSourceInfo(kSourceDexCacheResolvedField, i, this));
    if (new_val != old_val) {
      fields_array->Set(i, new_val);
      wrote = true;
    }
  }

  auto* methods_array = GetResolvedMethodsArray();
  num_methods = NumResolvedMethodsArray();
  for (size_t i = 0; methods_array != nullptr && i < num_methods; i++) {
    ArtMethod* old_val = methods_array->Get(i);
    if (old_val == nullptr) {
      continue;
    }
    ArtMethod* new_val = visitor->VisitMethod(
        old_val, DexCacheSourceInfo(kSourceDexCacheResolvedMethod, i, this));
    if (new_val != old_val) {
      methods_array->Set(i, new_val);
      wrote = true;
    }
  }

  if (wrote) {
    WriteBarrier::ForEveryFieldWrite(this);
  }
}

void DexCache::ResetNativeArrays() {
  SetStrings(nullptr);
  SetResolvedTypes(nullptr);
  SetResolvedMethods(nullptr);
  SetResolvedFields(nullptr);
  SetResolvedMethodTypes(nullptr);
  SetResolvedCallSites(nullptr);

  SetStringsArray(nullptr);
  SetResolvedTypesArray(nullptr);
  SetResolvedMethodsArray(nullptr);
  SetResolvedFieldsArray(nullptr);
  SetResolvedMethodTypesArray(nullptr);
}

void DexCache::SetLocation(ObjPtr<mirror::String> location) {
  SetFieldObject<false>(OFFSET_OF_OBJECT_MEMBER(DexCache, location_), location);
}

void DexCache::SetClassLoader(ObjPtr<ClassLoader> class_loader) {
  SetFieldObject<false>(OFFSET_OF_OBJECT_MEMBER(DexCache, class_loader_), class_loader);
}

ObjPtr<ClassLoader> DexCache::GetClassLoader() {
  return GetFieldObject<ClassLoader>(OFFSET_OF_OBJECT_MEMBER(DexCache, class_loader_));
}

bool DexCache::ShouldAllocateFullArrayAtStartup() {
  if (!kEnableFullArraysAtStartup) {
    return false;
  }
  Runtime* runtime = Runtime::Current();
  if (runtime->IsAotCompiler()) {
    // To save on memory in dex2oat, we don't allocate full arrays by default.
    return false;
  }

  if (runtime->IsZygote()) {
    // Zygote doesn't have a notion of startup.
    return false;
  }

  if (runtime->GetStartupCompleted()) {
    // We only allocate full arrays during app startup.
    return false;
  }

  if (GetClassLoader() == nullptr) {
    // Only allocate full array for app dex files (also note that for
    // multi-image, the `GetCompilerFilter` call below does not work for
    // non-primary oat files).
    return false;
  }

  const OatDexFile* oat_dex_file = GetDexFile()->GetOatDexFile();
  if (oat_dex_file != nullptr &&
      CompilerFilter::IsAotCompilationEnabled(oat_dex_file->GetOatFile()->GetCompilerFilter())) {
    // We only allocate full arrays for dex files where we do not have
    // compilation.
    return false;
  }

  return true;
}

void DexCache::UnlinkStartupCaches() {
  if (GetDexFile() == nullptr) {
    // Unused dex cache.
    return;
  }
  UnlinkStringsArrayIfStartup();
  UnlinkResolvedFieldsArrayIfStartup();
  UnlinkResolvedMethodsArrayIfStartup();
  UnlinkResolvedTypesArrayIfStartup();
  UnlinkResolvedMethodTypesArrayIfStartup();
}

void DexCache::SetResolvedType(dex::TypeIndex type_idx, ObjPtr<Class> resolved) {
  DCHECK(resolved != nullptr);
  DCHECK(resolved->IsResolved()) << resolved->GetStatus();
  // TODO default transaction support.
  // Use a release store for SetResolvedType. This is done to prevent other threads from seeing a
  // class but not necessarily seeing the loaded members like the static fields array.
  // See b/32075261.
  SetResolvedTypesEntry(type_idx.index_, resolved.Ptr());
  // TODO: Fine-grained marking, so that we don't need to go through all arrays in full.
  WriteBarrier::ForEveryFieldWrite(this);

  if (this == resolved->GetDexCache()) {
    // If we're updating the dex cache of the class, optimistically update the cache for methods and
    // fields if the caches are full arrays.
    auto* resolved_methods = GetResolvedMethodsArray();
    if (resolved_methods != nullptr) {
      PointerSize pointer_size = Runtime::Current()->GetClassLinker()->GetImagePointerSize();
      // Because there could be duplicate method entries, we make sure we only
      // update the cache with the first one found to be consistent with method
      // resolution.
      uint32_t previous_method_index = dex::kDexNoIndex;
      for (ArtMethod& current_method : resolved->GetDeclaredMethods(pointer_size)) {
        uint32_t new_index = current_method.GetDexMethodIndex();
        if (new_index != previous_method_index) {
          resolved_methods->Set(new_index, &current_method);
          previous_method_index = new_index;
        }
      }
    }
    auto* resolved_fields = GetResolvedFieldsArray();
    if (resolved_fields != nullptr) {
      for (ArtField& current_field : resolved->GetSFields()) {
        resolved_fields->Set(current_field.GetDexFieldIndex(), &current_field);
      }
      for (ArtField& current_field : resolved->GetIFields()) {
        resolved_fields->Set(current_field.GetDexFieldIndex(), &current_field);
      }
    }
  }
}

}  // namespace mirror
}  // namespace art
