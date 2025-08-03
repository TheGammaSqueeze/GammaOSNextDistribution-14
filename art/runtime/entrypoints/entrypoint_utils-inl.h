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

#ifndef ART_RUNTIME_ENTRYPOINTS_ENTRYPOINT_UTILS_INL_H_
#define ART_RUNTIME_ENTRYPOINTS_ENTRYPOINT_UTILS_INL_H_

#include "entrypoint_utils.h"

#include <sstream>

#include "art_field-inl.h"
#include "art_method-inl.h"
#include "base/enums.h"
#include "base/sdk_version.h"
#include "class_linker-inl.h"
#include "common_throws.h"
#include "dex/dex_file.h"
#include "dex/invoke_type.h"
#include "entrypoints/quick/callee_save_frame.h"
#include "handle_scope-inl.h"
#include "imt_conflict_table.h"
#include "imtable-inl.h"
#include "indirect_reference_table.h"
#include "mirror/array-alloc-inl.h"
#include "mirror/class-alloc-inl.h"
#include "mirror/class-inl.h"
#include "mirror/object-inl.h"
#include "mirror/throwable.h"
#include "nth_caller_visitor.h"
#include "oat/oat_file.h"
#include "oat/stack_map.h"
#include "reflective_handle_scope-inl.h"
#include "runtime.h"
#include "thread.h"
#include "well_known_classes.h"

namespace art HIDDEN {

inline std::string GetResolvedMethodErrorString(ClassLinker* class_linker,
                                                ArtMethod* inlined_method,
                                                ArtMethod* parent_method,
                                                ArtMethod* outer_method,
                                                ObjPtr<mirror::DexCache> dex_cache,
                                                MethodInfo method_info)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  const uint32_t method_index = method_info.GetMethodIndex();

  std::stringstream error_ss;
  std::string separator = "";
  error_ss << "BCP vector {";
  for (const DexFile* df : class_linker->GetBootClassPath()) {
    error_ss << separator << df << "(" << df->GetLocation() << ")";
    separator = ", ";
  }
  error_ss << "}. oat_dex_files vector: {";
  separator = "";
  for (const OatDexFile* odf_value :
       parent_method->GetDexFile()->GetOatDexFile()->GetOatFile()->GetOatDexFiles()) {
    error_ss << separator << odf_value << "(" << odf_value->GetDexFileLocation() << ")";
    separator = ", ";
  }
  error_ss << "}. ";
  if (inlined_method != nullptr) {
    error_ss << "Inlined method: " << inlined_method->PrettyMethod() << " ("
             << inlined_method->GetDexFile()->GetLocation() << "/"
             << static_cast<const void*>(inlined_method->GetDexFile()) << "). ";
  } else if (dex_cache != nullptr) {
    error_ss << "Could not find an inlined method from an .oat file, using dex_cache to print the "
                "inlined method: "
             << dex_cache->GetDexFile()->PrettyMethod(method_index) << " ("
             << dex_cache->GetDexFile()->GetLocation() << "/"
             << static_cast<const void*>(dex_cache->GetDexFile()) << "). ";
  } else {
    error_ss << "Both inlined_method and dex_cache are null. This means that we had an OOB access "
             << "to either bcp_dex_files or oat_dex_files. ";
  }
  error_ss << "The outer method is: " << parent_method->PrettyMethod() << " ("
           << parent_method->GetDexFile()->GetLocation() << "/"
           << static_cast<const void*>(parent_method->GetDexFile())
           << "). The outermost method in the chain is: " << outer_method->PrettyMethod() << " ("
           << outer_method->GetDexFile()->GetLocation() << "/"
           << static_cast<const void*>(outer_method->GetDexFile())
           << "). MethodInfo: method_index=" << std::dec << method_index
           << ", is_in_bootclasspath=" << std::boolalpha
           << (method_info.GetDexFileIndexKind() == MethodInfo::kKindBCP) << std::noboolalpha
           << ", dex_file_index=" << std::dec << method_info.GetDexFileIndex() << ".";
  return error_ss.str();
}

inline ArtMethod* GetResolvedMethod(ArtMethod* outer_method,
                                    const CodeInfo& code_info,
                                    const BitTableRange<InlineInfo>& inline_infos)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  DCHECK(!outer_method->IsObsolete());

  // This method is being used by artQuickResolutionTrampoline, before it sets up
  // the passed parameters in a GC friendly way. Therefore we must never be
  // suspended while executing it.
  ScopedAssertNoThreadSuspension sants(__FUNCTION__);

  {
    InlineInfo inline_info = inline_infos.back();

    if (inline_info.EncodesArtMethod()) {
      return inline_info.GetArtMethod();
    }

    uint32_t method_index = code_info.GetMethodIndexOf(inline_info);
    if (inline_info.GetDexPc() == static_cast<uint32_t>(-1)) {
      // "charAt" special case. It is the only non-leaf method we inline across dex files.
      ArtMethod* inlined_method = WellKnownClasses::java_lang_String_charAt;
      DCHECK_EQ(inlined_method->GetDexMethodIndex(), method_index);
      return inlined_method;
    }
  }

  // Find which method did the call in the inlining hierarchy.
  ClassLinker* class_linker = Runtime::Current()->GetClassLinker();
  ArtMethod* method = outer_method;
  for (InlineInfo inline_info : inline_infos) {
    DCHECK(!inline_info.EncodesArtMethod());
    DCHECK_NE(inline_info.GetDexPc(), static_cast<uint32_t>(-1));
    MethodInfo method_info = code_info.GetMethodInfoOf(inline_info);
    uint32_t method_index = method_info.GetMethodIndex();
    const uint32_t dex_file_index = method_info.GetDexFileIndex();
    ArtMethod* inlined_method = nullptr;
    ObjPtr<mirror::DexCache> dex_cache = nullptr;
    if (method_info.HasDexFileIndex()) {
      if (method_info.GetDexFileIndexKind() == MethodInfo::kKindBCP) {
        ArrayRef<const DexFile* const> bcp_dex_files(class_linker->GetBootClassPath());
        DCHECK_LT(dex_file_index, bcp_dex_files.size())
            << "OOB access to bcp_dex_files. Dumping info: "
            << GetResolvedMethodErrorString(
                   class_linker, inlined_method, method, outer_method, dex_cache, method_info);
        const DexFile* dex_file = bcp_dex_files[dex_file_index];
        DCHECK_NE(dex_file, nullptr);
        dex_cache = class_linker->FindDexCache(Thread::Current(), *dex_file);
      } else {
        ArrayRef<const OatDexFile* const> oat_dex_files(
            outer_method->GetDexFile()->GetOatDexFile()->GetOatFile()->GetOatDexFiles());
        DCHECK_LT(dex_file_index, oat_dex_files.size())
            << "OOB access to oat_dex_files. Dumping info: "
            << GetResolvedMethodErrorString(
                   class_linker, inlined_method, method, outer_method, dex_cache, method_info);
        const OatDexFile* odf = oat_dex_files[dex_file_index];
        DCHECK_NE(odf, nullptr);
        dex_cache = class_linker->FindDexCache(Thread::Current(), *odf);
      }
    } else {
      dex_cache = outer_method->GetDexCache();
    }
    inlined_method =
        class_linker->LookupResolvedMethod(method_index, dex_cache, dex_cache->GetClassLoader());

    if (UNLIKELY(inlined_method == nullptr)) {
      LOG(FATAL) << GetResolvedMethodErrorString(
          class_linker, inlined_method, method, outer_method, dex_cache, method_info);
      UNREACHABLE();
    }
    DCHECK(!inlined_method->IsRuntimeMethod());
    DCHECK_EQ(inlined_method->GetDexFile() == outer_method->GetDexFile(),
              dex_file_index == MethodInfo::kSameDexFile)
        << GetResolvedMethodErrorString(
               class_linker, inlined_method, method, outer_method, dex_cache, method_info);
    method = inlined_method;
  }

  return method;
}

ALWAYS_INLINE
inline ObjPtr<mirror::Class> CheckClassInitializedForObjectAlloc(ObjPtr<mirror::Class> klass,
                                                                 Thread* self,
                                                                 bool* slow_path)
    REQUIRES_SHARED(Locks::mutator_lock_)
    REQUIRES(!Roles::uninterruptible_) {
  if (UNLIKELY(!klass->IsVisiblyInitialized())) {
    StackHandleScope<1> hs(self);
    Handle<mirror::Class> h_class(hs.NewHandle(klass));
    // EnsureInitialized (the class initializer) might cause a GC.
    // may cause us to suspend meaning that another thread may try to
    // change the allocator while we are stuck in the entrypoints of
    // an old allocator. Also, the class initialization may fail. To
    // handle these cases we mark the slow path boolean as true so
    // that the caller knows to check the allocator type to see if it
    // has changed and to null-check the return value in case the
    // initialization fails.
    *slow_path = true;
    if (!Runtime::Current()->GetClassLinker()->EnsureInitialized(self, h_class, true, true)) {
      DCHECK(self->IsExceptionPending());
      return nullptr;  // Failure
    } else {
      DCHECK(!self->IsExceptionPending());
    }
    return h_class.Get();
  }
  return klass;
}

ALWAYS_INLINE inline ObjPtr<mirror::Class> CheckObjectAlloc(ObjPtr<mirror::Class> klass,
                                                            Thread* self,
                                                            bool* slow_path)
    REQUIRES_SHARED(Locks::mutator_lock_)
    REQUIRES(!Roles::uninterruptible_) {
  if (UNLIKELY(!klass->IsInstantiable())) {
    self->ThrowNewException("Ljava/lang/InstantiationError;", klass->PrettyDescriptor().c_str());
    *slow_path = true;
    return nullptr;  // Failure
  }
  if (UNLIKELY(klass->IsClassClass())) {
    ThrowIllegalAccessError(nullptr, "Class %s is inaccessible",
                            klass->PrettyDescriptor().c_str());
    *slow_path = true;
    return nullptr;  // Failure
  }
  return CheckClassInitializedForObjectAlloc(klass, self, slow_path);
}

// Allocate an instance of klass. Throws InstantationError if klass is not instantiable,
// or IllegalAccessError if klass is j.l.Class. Performs a clinit check too.
template <bool kInstrumented>
ALWAYS_INLINE
inline ObjPtr<mirror::Object> AllocObjectFromCode(ObjPtr<mirror::Class> klass,
                                                  Thread* self,
                                                  gc::AllocatorType allocator_type) {
  bool slow_path = false;
  klass = CheckObjectAlloc(klass, self, &slow_path);
  if (UNLIKELY(slow_path)) {
    if (klass == nullptr) {
      return nullptr;
    }
    // CheckObjectAlloc can cause thread suspension which means we may now be instrumented.
    return klass->Alloc</*kInstrumented=*/true>(
        self,
        Runtime::Current()->GetHeap()->GetCurrentAllocator());
  }
  DCHECK(klass != nullptr);
  return klass->Alloc<kInstrumented>(self, allocator_type);
}

// Given the context of a calling Method and a resolved class, create an instance.
template <bool kInstrumented>
ALWAYS_INLINE
inline ObjPtr<mirror::Object> AllocObjectFromCodeResolved(ObjPtr<mirror::Class> klass,
                                                          Thread* self,
                                                          gc::AllocatorType allocator_type) {
  DCHECK(klass != nullptr);
  bool slow_path = false;
  klass = CheckClassInitializedForObjectAlloc(klass, self, &slow_path);
  if (UNLIKELY(slow_path)) {
    if (klass == nullptr) {
      return nullptr;
    }
    gc::Heap* heap = Runtime::Current()->GetHeap();
    // Pass in kNoAddFinalizer since the object cannot be finalizable.
    // CheckClassInitializedForObjectAlloc can cause thread suspension which means we may now be
    // instrumented.
    return klass->Alloc</*kInstrumented=*/true, mirror::Class::AddFinalizer::kNoAddFinalizer>(
        self, heap->GetCurrentAllocator());
  }
  // Pass in kNoAddFinalizer since the object cannot be finalizable.
  return klass->Alloc<kInstrumented,
                      mirror::Class::AddFinalizer::kNoAddFinalizer>(self, allocator_type);
}

// Given the context of a calling Method and an initialized class, create an instance.
template <bool kInstrumented>
ALWAYS_INLINE
inline ObjPtr<mirror::Object> AllocObjectFromCodeInitialized(ObjPtr<mirror::Class> klass,
                                                             Thread* self,
                                                             gc::AllocatorType allocator_type) {
  DCHECK(klass != nullptr);
  // Pass in kNoAddFinalizer since the object cannot be finalizable.
  return klass->Alloc<kInstrumented,
                      mirror::Class::AddFinalizer::kNoAddFinalizer>(self, allocator_type);
}


ALWAYS_INLINE
inline ObjPtr<mirror::Class> CheckArrayAlloc(dex::TypeIndex type_idx,
                                             int32_t component_count,
                                             ArtMethod* method,
                                             bool* slow_path) {
  if (UNLIKELY(component_count < 0)) {
    ThrowNegativeArraySizeException(component_count);
    *slow_path = true;
    return nullptr;  // Failure
  }
  ObjPtr<mirror::Class> klass = method->GetDexCache()->GetResolvedType(type_idx);
  if (UNLIKELY(klass == nullptr)) {  // Not in dex cache so try to resolve
    ClassLinker* class_linker = Runtime::Current()->GetClassLinker();
    klass = class_linker->ResolveType(type_idx, method);
    *slow_path = true;
    if (klass == nullptr) {  // Error
      DCHECK(Thread::Current()->IsExceptionPending());
      return nullptr;  // Failure
    }
    CHECK(klass->IsArrayClass()) << klass->PrettyClass();
  }
  if (!method->SkipAccessChecks()) {
    ObjPtr<mirror::Class> referrer = method->GetDeclaringClass();
    if (UNLIKELY(!referrer->CanAccess(klass))) {
      ThrowIllegalAccessErrorClass(referrer, klass);
      *slow_path = true;
      return nullptr;  // Failure
    }
  }
  return klass;
}

// Given the context of a calling Method, use its DexCache to resolve a type to an array Class. If
// it cannot be resolved, throw an error. If it can, use it to create an array.
// When verification/compiler hasn't been able to verify access, optionally perform an access
// check.
template <bool kInstrumented>
ALWAYS_INLINE
inline ObjPtr<mirror::Array> AllocArrayFromCode(dex::TypeIndex type_idx,
                                                int32_t component_count,
                                                ArtMethod* method,
                                                Thread* self,
                                                gc::AllocatorType allocator_type) {
  bool slow_path = false;
  ObjPtr<mirror::Class> klass = CheckArrayAlloc(type_idx, component_count, method, &slow_path);
  if (UNLIKELY(slow_path)) {
    if (klass == nullptr) {
      return nullptr;
    }
    gc::Heap* heap = Runtime::Current()->GetHeap();
    // CheckArrayAlloc can cause thread suspension which means we may now be instrumented.
    return mirror::Array::Alloc</*kInstrumented=*/true>(self,
                                                        klass,
                                                        component_count,
                                                        klass->GetComponentSizeShift(),
                                                        heap->GetCurrentAllocator());
  }
  return mirror::Array::Alloc<kInstrumented>(self,
                                             klass,
                                             component_count,
                                             klass->GetComponentSizeShift(),
                                             allocator_type);
}

template <bool kInstrumented>
ALWAYS_INLINE
inline ObjPtr<mirror::Array> AllocArrayFromCodeResolved(ObjPtr<mirror::Class> klass,
                                                        int32_t component_count,
                                                        Thread* self,
                                                        gc::AllocatorType allocator_type) {
  DCHECK(klass != nullptr);
  if (UNLIKELY(component_count < 0)) {
    ThrowNegativeArraySizeException(component_count);
    return nullptr;  // Failure
  }
  // No need to retry a slow-path allocation as the above code won't cause a GC or thread
  // suspension.
  return mirror::Array::Alloc<kInstrumented>(self,
                                             klass,
                                             component_count,
                                             klass->GetComponentSizeShift(),
                                             allocator_type);
}

FLATTEN
inline ArtField* ResolveFieldWithAccessChecks(Thread* self,
                                              ClassLinker* class_linker,
                                              uint16_t field_index,
                                              ArtMethod* caller,
                                              bool is_static,
                                              bool is_put,
                                              size_t resolve_field_type)  // Resolve if not zero
    REQUIRES_SHARED(Locks::mutator_lock_) {
  if (caller->SkipAccessChecks()) {
    return class_linker->ResolveField(field_index, caller, is_static);
  }

  caller = caller->GetInterfaceMethodIfProxy(class_linker->GetImagePointerSize());

  StackHandleScope<2> hs(self);
  Handle<mirror::DexCache> h_dex_cache(hs.NewHandle(caller->GetDexCache()));
  Handle<mirror::ClassLoader> h_class_loader(hs.NewHandle(caller->GetClassLoader()));

  ArtField* resolved_field = class_linker->ResolveFieldJLS(field_index,
                                                           h_dex_cache,
                                                           h_class_loader);
  if (resolved_field == nullptr) {
    return nullptr;
  }

  ObjPtr<mirror::Class> fields_class = resolved_field->GetDeclaringClass();
  if (UNLIKELY(resolved_field->IsStatic() != is_static)) {
    ThrowIncompatibleClassChangeErrorField(resolved_field, is_static, caller);
    return nullptr;
  }
  ObjPtr<mirror::Class> referring_class = caller->GetDeclaringClass();
  if (UNLIKELY(!referring_class->CheckResolvedFieldAccess(fields_class,
                                                          resolved_field,
                                                          caller->GetDexCache(),
                                                          field_index))) {
    DCHECK(self->IsExceptionPending());
    return nullptr;
  }
  if (UNLIKELY(is_put && !resolved_field->CanBeChangedBy(caller))) {
    ThrowIllegalAccessErrorFinalField(caller, resolved_field);
    return nullptr;
  }

  if (resolve_field_type != 0u) {
    StackArtFieldHandleScope<1> rhs(self);
    ReflectiveHandle<ArtField> field_handle(rhs.NewHandle(resolved_field));
    if (resolved_field->ResolveType().IsNull()) {
      DCHECK(self->IsExceptionPending());
      return nullptr;
    }
    resolved_field = field_handle.Get();
  }
  return resolved_field;
}

template<FindFieldType type>
inline ArtField* FindFieldFromCode(uint32_t field_idx,
                                   ArtMethod* referrer,
                                   Thread* self,
                                   bool should_resolve_type = false) {
  constexpr bool is_set = (type & FindFieldFlags::WriteBit) != 0;
  constexpr bool is_static = (type & FindFieldFlags::StaticBit) != 0;
  ClassLinker* class_linker = Runtime::Current()->GetClassLinker();
  ArtField* resolved_field = ResolveFieldWithAccessChecks(
      self, class_linker, field_idx, referrer, is_static, is_set, should_resolve_type ? 1u : 0u);
  if (!is_static || resolved_field == nullptr) {
    // instance fields must be being accessed on an initialized class
    return resolved_field;
  } else {
    ObjPtr<mirror::Class> fields_class = resolved_field->GetDeclaringClass();
    // If the class is initialized we're done.
    if (LIKELY(fields_class->IsVisiblyInitialized())) {
      return resolved_field;
    } else {
      StackHandleScope<1> hs(self);
      StackArtFieldHandleScope<1> rhs(self);
      ReflectiveHandle<ArtField> resolved_field_handle(rhs.NewHandle(resolved_field));
      if (LIKELY(class_linker->EnsureInitialized(self, hs.NewHandle(fields_class), true, true))) {
        // Otherwise let's ensure the class is initialized before resolving the field.
        return resolved_field_handle.Get();
      }
      DCHECK(self->IsExceptionPending());  // Throw exception and unwind
      return nullptr;  // Failure.
    }
  }
}

// NOLINTBEGIN(bugprone-macro-parentheses)
// Explicit template declarations of FindFieldFromCode for all field access types.
#define EXPLICIT_FIND_FIELD_FROM_CODE_TEMPLATE_DECL(_type) \
template REQUIRES_SHARED(Locks::mutator_lock_) ALWAYS_INLINE \
ArtField* FindFieldFromCode<_type>(uint32_t field_idx, \
                                   ArtMethod* referrer, \
                                   Thread* self, \
                                   bool should_resolve_type = false) \

EXPLICIT_FIND_FIELD_FROM_CODE_TEMPLATE_DECL(InstanceObjectRead);
EXPLICIT_FIND_FIELD_FROM_CODE_TEMPLATE_DECL(InstanceObjectWrite);
EXPLICIT_FIND_FIELD_FROM_CODE_TEMPLATE_DECL(InstancePrimitiveRead);
EXPLICIT_FIND_FIELD_FROM_CODE_TEMPLATE_DECL(InstancePrimitiveWrite);
EXPLICIT_FIND_FIELD_FROM_CODE_TEMPLATE_DECL(StaticObjectRead);
EXPLICIT_FIND_FIELD_FROM_CODE_TEMPLATE_DECL(StaticObjectWrite);
EXPLICIT_FIND_FIELD_FROM_CODE_TEMPLATE_DECL(StaticPrimitiveRead);
EXPLICIT_FIND_FIELD_FROM_CODE_TEMPLATE_DECL(StaticPrimitiveWrite);

#undef EXPLICIT_FIND_FIELD_FROM_CODE_TEMPLATE_DECL
// NOLINTEND(bugprone-macro-parentheses)

static inline bool IsStringInit(const DexFile* dex_file, uint32_t method_idx)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  const dex::MethodId& method_id = dex_file->GetMethodId(method_idx);
  const char* class_name = dex_file->StringByTypeIdx(method_id.class_idx_);
  const char* method_name = dex_file->GetMethodName(method_id);
  // Instead of calling ResolveMethod() which has suspend point and can trigger
  // GC, look up the method symbolically.
  // Compare method's class name and method name against string init.
  // It's ok since it's not allowed to create your own java/lang/String.
  // TODO: verify that assumption.
  if ((strcmp(class_name, "Ljava/lang/String;") == 0) &&
      (strcmp(method_name, "<init>") == 0)) {
    return true;
  }
  return false;
}

static inline bool IsStringInit(const Instruction& instr, ArtMethod* caller)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  if (instr.Opcode() == Instruction::INVOKE_DIRECT ||
      instr.Opcode() == Instruction::INVOKE_DIRECT_RANGE) {
    uint16_t callee_method_idx = (instr.Opcode() == Instruction::INVOKE_DIRECT_RANGE) ?
        instr.VRegB_3rc() : instr.VRegB_35c();
    return IsStringInit(caller->GetDexFile(), callee_method_idx);
  }
  return false;
}

extern "C" size_t NterpGetMethod(Thread* self, ArtMethod* caller, const uint16_t* dex_pc_ptr);

template <InvokeType type>
ArtMethod* FindMethodToCall(Thread* self,
                            ArtMethod* caller,
                            ObjPtr<mirror::Object>* this_object,
                            const Instruction& inst,
                            bool only_lookup_tls_cache,
                            /*out*/ bool* string_init)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  PointerSize pointer_size = Runtime::Current()->GetClassLinker()->GetImagePointerSize();

  // Try to find the method in thread-local cache.
  size_t tls_value = 0u;
  if (!self->GetInterpreterCache()->Get(self, &inst, &tls_value)) {
    if (only_lookup_tls_cache) {
      return nullptr;
    }
    DCHECK(!self->IsExceptionPending());
    // NterpGetMethod can suspend, so save this_object.
    StackHandleScope<1> hs(self);
    HandleWrapperObjPtr<mirror::Object> h_this(hs.NewHandleWrapper(this_object));
    tls_value = NterpGetMethod(self, caller, reinterpret_cast<const uint16_t*>(&inst));
    if (self->IsExceptionPending()) {
      return nullptr;
    }
  }

  if (type != kStatic && UNLIKELY((*this_object) == nullptr)) {
    if (UNLIKELY(IsStringInit(inst, caller))) {
      // Hack for String init:
      //
      // We assume that the input of String.<init> in verified code is always
      // an uninitialized reference. If it is a null constant, it must have been
      // optimized out by the compiler and we arrive here after deoptimization.
      // Do not throw NullPointerException.
    } else {
      // Maintain interpreter-like semantics where NullPointerException is thrown
      // after potential NoSuchMethodError from class linker.
      const uint32_t method_idx = inst.VRegB();
      ThrowNullPointerExceptionForMethodAccess(method_idx, type);
      return nullptr;
    }
  }

  static constexpr size_t kStringInitMethodFlag = 0b1;
  static constexpr size_t kInvokeInterfaceOnObjectMethodFlag = 0b1;
  static constexpr size_t kMethodMask = ~0b11;

  ArtMethod* called_method = nullptr;
  switch (type) {
    case kDirect:
    case kSuper:
    case kStatic:
      // Note: for the interpreter, the String.<init> special casing for invocation is handled
      // in DoCallCommon.
      *string_init = ((tls_value & kStringInitMethodFlag) != 0);
      DCHECK_EQ(*string_init, IsStringInit(inst, caller));
      called_method = reinterpret_cast<ArtMethod*>(tls_value & kMethodMask);
      break;
    case kInterface:
      if ((tls_value & kInvokeInterfaceOnObjectMethodFlag) != 0) {
        // invokeinterface on a j.l.Object method.
        uint16_t method_index = tls_value >> 16;
        called_method = (*this_object)->GetClass()->GetVTableEntry(method_index, pointer_size);
      } else {
        ArtMethod* interface_method = reinterpret_cast<ArtMethod*>(tls_value & kMethodMask);
        called_method = (*this_object)->GetClass()->GetImt(pointer_size)->Get(
            interface_method->GetImtIndex(), pointer_size);
        if (called_method->IsRuntimeMethod()) {
          called_method = (*this_object)->GetClass()->FindVirtualMethodForInterface(
              interface_method, pointer_size);
          if (UNLIKELY(called_method == nullptr)) {
            ThrowIncompatibleClassChangeErrorClassForInterfaceDispatch(
                interface_method, *this_object, caller);
            return nullptr;
          }
        }
      }
      break;
    case kVirtual:
      called_method = (*this_object)->GetClass()->GetVTableEntry(tls_value, pointer_size);
      break;
  }

  if (UNLIKELY(!called_method->IsInvokable())) {
    called_method->ThrowInvocationTimeError((type == kStatic) ? nullptr : *this_object);
    return nullptr;
  }
  DCHECK(!called_method->IsRuntimeMethod()) << called_method->PrettyMethod();
  return called_method;
}

template<bool access_check>
ALWAYS_INLINE ArtMethod* FindSuperMethodToCall(uint32_t method_idx,
                                              ArtMethod* resolved_method,
                                              ArtMethod* referrer,
                                              Thread* self)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  // TODO This lookup is quite slow.
  // NB This is actually quite tricky to do any other way. We cannot use GetDeclaringClass since
  //    that will actually not be what we want in some cases where there are miranda methods or
  //    defaults. What we actually need is a GetContainingClass that says which classes virtuals
  //    this method is coming from.
  ClassLinker* linker = Runtime::Current()->GetClassLinker();
  dex::TypeIndex type_idx = referrer->GetDexFile()->GetMethodId(method_idx).class_idx_;
  ObjPtr<mirror::Class> referenced_class = linker->ResolveType(type_idx, referrer);
  if (UNLIKELY(referenced_class == nullptr)) {
    DCHECK(self->IsExceptionPending());
    return nullptr;
  }

  if (access_check) {
    if (!referenced_class->IsAssignableFrom(referrer->GetDeclaringClass())) {
      ThrowNoSuchMethodError(kSuper,
                             resolved_method->GetDeclaringClass(),
                             resolved_method->GetName(),
                             resolved_method->GetSignature());
      return nullptr;
    }
  }

  if (referenced_class->IsInterface()) {
    // TODO We can do better than this for a (compiled) fastpath.
    ArtMethod* found_method = referenced_class->FindVirtualMethodForInterfaceSuper(
        resolved_method, linker->GetImagePointerSize());
    DCHECK(found_method != nullptr);
    return found_method;
  }

  DCHECK(resolved_method->IsCopied() ||
         !resolved_method->GetDeclaringClass()->IsInterface());

  uint16_t vtable_index = resolved_method->GetMethodIndex();
  ObjPtr<mirror::Class> super_class = referrer->GetDeclaringClass()->GetSuperClass();
  if (access_check) {
    DCHECK(super_class == nullptr || super_class->HasVTable());
    // Check existence of super class.
    if (super_class == nullptr ||
        vtable_index >= static_cast<uint32_t>(super_class->GetVTableLength())) {
      // Behavior to agree with that of the verifier.
      ThrowNoSuchMethodError(kSuper,
                             resolved_method->GetDeclaringClass(),
                             resolved_method->GetName(),
                             resolved_method->GetSignature());
      return nullptr;  // Failure.
    }
  }
  DCHECK(super_class != nullptr);
  DCHECK(super_class->HasVTable());
  return super_class->GetVTableEntry(vtable_index, linker->GetImagePointerSize());
}

inline ObjPtr<mirror::Class> ResolveVerifyAndClinit(dex::TypeIndex type_idx,
                                                    ArtMethod* referrer,
                                                    Thread* self,
                                                    bool can_run_clinit,
                                                    bool verify_access) {
  ClassLinker* class_linker = Runtime::Current()->GetClassLinker();
  ObjPtr<mirror::Class> klass = class_linker->ResolveType(type_idx, referrer);
  if (UNLIKELY(klass == nullptr)) {
    CHECK(self->IsExceptionPending());
    return nullptr;  // Failure - Indicate to caller to deliver exception
  }
  // Perform access check if necessary.
  ObjPtr<mirror::Class> referring_class = referrer->GetDeclaringClass();
  if (verify_access && UNLIKELY(!referring_class->CanAccess(klass))) {
    ThrowIllegalAccessErrorClass(referring_class, klass);
    return nullptr;  // Failure - Indicate to caller to deliver exception
  }
  // If we're just implementing const-class, we shouldn't call <clinit>.
  if (!can_run_clinit) {
    return klass;
  }
  // If we are the <clinit> of this class, just return our storage.
  //
  // Do not set the DexCache InitializedStaticStorage, since that implies <clinit> has finished
  // running.
  if (klass == referring_class && referrer->IsConstructor() && referrer->IsStatic()) {
    return klass;
  }
  StackHandleScope<1> hs(self);
  Handle<mirror::Class> h_class(hs.NewHandle(klass));
  if (!class_linker->EnsureInitialized(self, h_class, true, true)) {
    CHECK(self->IsExceptionPending());
    return nullptr;  // Failure - Indicate to caller to deliver exception
  }
  return h_class.Get();
}

template <typename INT_TYPE, typename FLOAT_TYPE>
inline INT_TYPE art_float_to_integral(FLOAT_TYPE f) {
  const INT_TYPE kMaxInt = static_cast<INT_TYPE>(std::numeric_limits<INT_TYPE>::max());
  const INT_TYPE kMinInt = static_cast<INT_TYPE>(std::numeric_limits<INT_TYPE>::min());
  const FLOAT_TYPE kMaxIntAsFloat = static_cast<FLOAT_TYPE>(kMaxInt);
  const FLOAT_TYPE kMinIntAsFloat = static_cast<FLOAT_TYPE>(kMinInt);
  if (LIKELY(f > kMinIntAsFloat)) {
     if (LIKELY(f < kMaxIntAsFloat)) {
       return static_cast<INT_TYPE>(f);
     } else {
       return kMaxInt;
     }
  } else {
    return (f != f) ? 0 : kMinInt;  // f != f implies NaN
  }
}

inline ObjPtr<mirror::Object> GetGenericJniSynchronizationObject(Thread* self, ArtMethod* called)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  DCHECK(!called->IsCriticalNative());
  DCHECK(!called->IsFastNative());
  DCHECK(self->GetManagedStack()->GetTopQuickFrame() != nullptr);
  DCHECK_EQ(*self->GetManagedStack()->GetTopQuickFrame(), called);
  // We do not need read barriers here.
  // On method entry, all reference arguments are to-space references and we mark the
  // declaring class of a static native method if needed. When visiting thread roots at
  // the start of a GC, we visit all these references to ensure they point to the to-space.
  if (called->IsStatic()) {
    // Static methods synchronize on the declaring class object.
    return called->GetDeclaringClass<kWithoutReadBarrier>();
  } else {
    // Instance methods synchronize on the `this` object.
    // The `this` reference is stored in the first out vreg in the caller's frame.
    uint8_t* sp = reinterpret_cast<uint8_t*>(self->GetManagedStack()->GetTopQuickFrame());
    size_t frame_size = RuntimeCalleeSaveFrame::GetFrameSize(CalleeSaveType::kSaveRefsAndArgs);
    StackReference<mirror::Object>* this_ref = reinterpret_cast<StackReference<mirror::Object>*>(
        sp + frame_size + static_cast<size_t>(kRuntimePointerSize));
    return this_ref->AsMirrorPtr();
  }
}

}  // namespace art

#endif  // ART_RUNTIME_ENTRYPOINTS_ENTRYPOINT_UTILS_INL_H_
