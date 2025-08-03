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

#include "entrypoints/entrypoint_utils.h"

#include "art_field-inl.h"
#include "art_method-inl.h"
#include "base/enums.h"
#include "base/mutex.h"
#include "base/sdk_version.h"
#include "class_linker-inl.h"
#include "class_root-inl.h"
#include "dex/dex_file-inl.h"
#include "dex/method_reference.h"
#include "entrypoints/entrypoint_utils-inl.h"
#include "entrypoints/quick/callee_save_frame.h"
#include "entrypoints/runtime_asm_entrypoints.h"
#include "gc/accounting/card_table-inl.h"
#include "jni/java_vm_ext.h"
#include "mirror/class-inl.h"
#include "mirror/method.h"
#include "mirror/object-inl.h"
#include "mirror/object_array-alloc-inl.h"
#include "nth_caller_visitor.h"
#include "oat/index_bss_mapping.h"
#include "oat/oat_file-inl.h"
#include "oat/oat_quick_method_header.h"
#include "reflection.h"
#include "scoped_thread_state_change-inl.h"
#include "well_known_classes-inl.h"

namespace art HIDDEN {

void CheckReferenceResult(Handle<mirror::Object> o, Thread* self) {
  if (o == nullptr) {
    return;
  }
  // Make sure that the result is an instance of the type this method was expected to return.
  ArtMethod* method = self->GetCurrentMethod(nullptr);
  ObjPtr<mirror::Class> return_type = method->ResolveReturnType();

  if (!o->InstanceOf(return_type)) {
    Runtime::Current()->GetJavaVM()->JniAbortF(nullptr,
                                               "attempt to return an instance of %s from %s",
                                               o->PrettyTypeOf().c_str(),
                                               method->PrettyMethod().c_str());
  }
}

JValue InvokeProxyInvocationHandler(ScopedObjectAccessAlreadyRunnable& soa,
                                    const char* shorty,
                                    jobject rcvr_jobj,
                                    jobject interface_method_jobj,
                                    std::vector<jvalue>& args) {
  StackHandleScope<4u> hs(soa.Self());
  DCHECK(rcvr_jobj != nullptr);
  Handle<mirror::Object> h_receiver = hs.NewHandle(soa.Decode<mirror::Object>(rcvr_jobj));
  DCHECK(h_receiver->InstanceOf(GetClassRoot(ClassRoot::kJavaLangReflectProxy)));
  Handle<mirror::Method> h_interface_method =
      hs.NewHandle(soa.Decode<mirror::Method>(interface_method_jobj));

  // Build argument array possibly triggering GC.
  soa.Self()->AssertThreadSuspensionIsAllowable();
  auto h_args = hs.NewHandle<mirror::ObjectArray<mirror::Object>>(nullptr);
  const JValue zero;
  Runtime* runtime = Runtime::Current();
  uint32_t target_sdk_version = runtime->GetTargetSdkVersion();
  // Do not create empty arrays unless needed to maintain Dalvik bug compatibility.
  if (args.size() > 0 || IsSdkVersionSetAndAtMost(target_sdk_version, SdkVersion::kL)) {
    h_args.Assign(mirror::ObjectArray<mirror::Object>::Alloc(
        soa.Self(), GetClassRoot<mirror::ObjectArray<mirror::Object>>(), args.size()));
    if (h_args == nullptr) {
      CHECK(soa.Self()->IsExceptionPending());
      return zero;
    }
    for (size_t i = 0; i < args.size(); ++i) {
      ObjPtr<mirror::Object> value;
      if (shorty[i + 1] == 'L') {
        value = soa.Decode<mirror::Object>(args[i].l);
      } else {
        JValue jv;
        jv.SetJ(args[i].j);
        value = BoxPrimitive(Primitive::GetType(shorty[i + 1]), jv);
        if (value == nullptr) {
          CHECK(soa.Self()->IsExceptionPending());
          return zero;
        }
      }
      // We do not support `Proxy.invoke()` in a transaction.
      h_args->SetWithoutChecks</*kActiveTransaction=*/ false>(i, value);
    }
  }

  // Call Proxy.invoke(Proxy proxy, Method method, Object[] args).
  Handle<mirror::Object> h_result = hs.NewHandle(
      WellKnownClasses::java_lang_reflect_Proxy_invoke->InvokeStatic<'L', 'L', 'L', 'L'>(
          soa.Self(), h_receiver.Get(), h_interface_method.Get(), h_args.Get()));

  // Unbox result and handle error conditions.
  if (LIKELY(!soa.Self()->IsExceptionPending())) {
    if (shorty[0] == 'V' || (shorty[0] == 'L' && h_result == nullptr)) {
      // Do nothing.
      return zero;
    } else {
      ObjPtr<mirror::Class> result_type;
      if (shorty[0] == 'L') {
        // This can cause thread suspension.
        result_type = h_interface_method->GetArtMethod()->ResolveReturnType();
        if (result_type == nullptr) {
          DCHECK(soa.Self()->IsExceptionPending());
          return zero;
        }
      } else {
        result_type = runtime->GetClassLinker()->LookupPrimitiveClass(shorty[0]);
        DCHECK(result_type != nullptr);
      }
      JValue result_unboxed;
      if (!UnboxPrimitiveForResult(h_result.Get(), result_type, &result_unboxed)) {
        DCHECK(soa.Self()->IsExceptionPending());
        return zero;
      }
      return result_unboxed;
    }
  } else {
    // In the case of checked exceptions that aren't declared, the exception must be wrapped by
    // a UndeclaredThrowableException.
    ObjPtr<mirror::Throwable> exception = soa.Self()->GetException();
    if (exception->IsCheckedException()) {
      bool declares_exception = false;
      {
        ScopedAssertNoThreadSuspension ants(__FUNCTION__);
        ObjPtr<mirror::Object> rcvr = soa.Decode<mirror::Object>(rcvr_jobj);
        ObjPtr<mirror::Class> proxy_class = rcvr->GetClass();
        ObjPtr<mirror::Method> interface_method = soa.Decode<mirror::Method>(interface_method_jobj);
        ArtMethod* proxy_method = rcvr->GetClass()->FindVirtualMethodForInterface(
            interface_method->GetArtMethod(), kRuntimePointerSize);
        auto virtual_methods = proxy_class->GetVirtualMethodsSlice(kRuntimePointerSize);
        size_t num_virtuals = proxy_class->NumVirtualMethods();
        size_t method_size = ArtMethod::Size(kRuntimePointerSize);
        // Rely on the fact that the methods are contiguous to determine the index of the method in
        // the slice.
        int throws_index = (reinterpret_cast<uintptr_t>(proxy_method) -
            reinterpret_cast<uintptr_t>(&virtual_methods[0])) / method_size;
        CHECK_LT(throws_index, static_cast<int>(num_virtuals));
        ObjPtr<mirror::ObjectArray<mirror::Class>> declared_exceptions =
            proxy_class->GetProxyThrows()->Get(throws_index);
        ObjPtr<mirror::Class> exception_class = exception->GetClass();
        for (int32_t i = 0; i < declared_exceptions->GetLength() && !declares_exception; i++) {
          ObjPtr<mirror::Class> declared_exception = declared_exceptions->Get(i);
          declares_exception = declared_exception->IsAssignableFrom(exception_class);
        }
      }
      if (!declares_exception) {
        soa.Self()->ThrowNewWrappedException("Ljava/lang/reflect/UndeclaredThrowableException;",
                                             nullptr);
      }
    }
    return zero;
  }
}

bool FillArrayData(ObjPtr<mirror::Object> obj, const Instruction::ArrayDataPayload* payload) {
  DCHECK_EQ(payload->ident, static_cast<uint16_t>(Instruction::kArrayDataSignature));
  if (UNLIKELY(obj == nullptr)) {
    ThrowNullPointerException("null array in FILL_ARRAY_DATA");
    return false;
  }
  ObjPtr<mirror::Array> array = obj->AsArray();
  DCHECK(!array->IsObjectArray());
  if (UNLIKELY(static_cast<int32_t>(payload->element_count) > array->GetLength())) {
    Thread* self = Thread::Current();
    self->ThrowNewExceptionF("Ljava/lang/ArrayIndexOutOfBoundsException;",
                             "failed FILL_ARRAY_DATA; length=%d, index=%d",
                             array->GetLength(), payload->element_count);
    return false;
  }
  // Copy data from dex file to memory assuming both are little endian.
  uint32_t size_in_bytes = payload->element_count * payload->element_width;
  memcpy(array->GetRawData(payload->element_width, 0), payload->data, size_in_bytes);
  return true;
}

static inline std::pair<ArtMethod*, uintptr_t> DoGetCalleeSaveMethodOuterCallerAndPc(
    ArtMethod** sp, CalleeSaveType type) REQUIRES_SHARED(Locks::mutator_lock_) {
  DCHECK_EQ(*sp, Runtime::Current()->GetCalleeSaveMethod(type));

  const size_t callee_frame_size = RuntimeCalleeSaveFrame::GetFrameSize(type);
  auto** caller_sp = reinterpret_cast<ArtMethod**>(
      reinterpret_cast<uintptr_t>(sp) + callee_frame_size);
  const size_t callee_return_pc_offset = RuntimeCalleeSaveFrame::GetReturnPcOffset(type);
  uintptr_t caller_pc = *reinterpret_cast<uintptr_t*>(
      (reinterpret_cast<uint8_t*>(sp) + callee_return_pc_offset));
  ArtMethod* outer_method = *caller_sp;
  return std::make_pair(outer_method, caller_pc);
}

static inline ArtMethod* DoGetCalleeSaveMethodCallerAndDexPc(ArtMethod** sp,
                                                             CalleeSaveType type,
                                                             ArtMethod* outer_method,
                                                             uintptr_t caller_pc,
                                                             uint32_t* dex_pc,
                                                             bool do_caller_check)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  ArtMethod* caller = outer_method;
  if (outer_method != nullptr) {
    const OatQuickMethodHeader* current_code = outer_method->GetOatQuickMethodHeader(caller_pc);
    DCHECK(current_code != nullptr);
    if (current_code->IsOptimized() &&
        CodeInfo::HasInlineInfo(current_code->GetOptimizedCodeInfoPtr())) {
      uintptr_t native_pc_offset = current_code->NativeQuickPcOffset(caller_pc);
      CodeInfo code_info = CodeInfo::DecodeInlineInfoOnly(current_code);
      StackMap stack_map = code_info.GetStackMapForNativePcOffset(native_pc_offset);
      DCHECK(stack_map.IsValid());
      BitTableRange<InlineInfo> inline_infos = code_info.GetInlineInfosOf(stack_map);
      if (!inline_infos.empty()) {
        caller = GetResolvedMethod(outer_method, code_info, inline_infos);
        *dex_pc = inline_infos.back().GetDexPc();
      } else {
        *dex_pc = stack_map.GetDexPc();
      }
    } else {
      size_t callee_frame_size = RuntimeCalleeSaveFrame::GetFrameSize(type);
      ArtMethod** caller_sp = reinterpret_cast<ArtMethod**>(
          reinterpret_cast<uintptr_t>(sp) + callee_frame_size);
      *dex_pc = current_code->ToDexPc(caller_sp, caller_pc);
    }
  }
  if (kIsDebugBuild && do_caller_check) {
    // Note that do_caller_check is optional, as this method can be called by
    // stubs, and tests without a proper call stack.
    NthCallerVisitor visitor(Thread::Current(), 1, true);
    visitor.WalkStack();
    CHECK_EQ(caller, visitor.caller);
  }
  return caller;
}

ArtMethod* GetCalleeSaveMethodCallerAndDexPc(ArtMethod** sp,
                                             CalleeSaveType type,
                                             uint32_t* dex_pc,
                                             bool do_caller_check)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  ScopedAssertNoThreadSuspension ants(__FUNCTION__);
  auto outer_caller_and_pc = DoGetCalleeSaveMethodOuterCallerAndPc(sp, type);
  ArtMethod* outer_method = outer_caller_and_pc.first;
  uintptr_t caller_pc = outer_caller_and_pc.second;
  ArtMethod* caller = DoGetCalleeSaveMethodCallerAndDexPc(sp,
                                                          type,
                                                          outer_method,
                                                          caller_pc,
                                                          dex_pc,
                                                          do_caller_check);
  return caller;
}

CallerAndOuterMethod GetCalleeSaveMethodCallerAndOuterMethod(Thread* self, CalleeSaveType type) {
  CallerAndOuterMethod result;
  ScopedAssertNoThreadSuspension ants(__FUNCTION__);
  ArtMethod** sp = self->GetManagedStack()->GetTopQuickFrameKnownNotTagged();
  auto outer_caller_and_pc = DoGetCalleeSaveMethodOuterCallerAndPc(sp, type);
  result.outer_method = outer_caller_and_pc.first;
  uintptr_t caller_pc = outer_caller_and_pc.second;
  uint32_t dex_pc;
  result.caller = DoGetCalleeSaveMethodCallerAndDexPc(sp,
                                                      type,
                                                      result.outer_method,
                                                      caller_pc,
                                                      &dex_pc,
                                                      /* do_caller_check= */ true);
  return result;
}

ArtMethod* GetCalleeSaveOuterMethod(Thread* self, CalleeSaveType type) {
  ScopedAssertNoThreadSuspension ants(__FUNCTION__);
  ArtMethod** sp = self->GetManagedStack()->GetTopQuickFrameKnownNotTagged();
  return DoGetCalleeSaveMethodOuterCallerAndPc(sp, type).first;
}

ObjPtr<mirror::MethodHandle> ResolveMethodHandleFromCode(ArtMethod* referrer,
                                                         uint32_t method_handle_idx) {
  Thread::PoisonObjectPointersIfDebug();
  ClassLinker* class_linker = Runtime::Current()->GetClassLinker();
  return class_linker->ResolveMethodHandle(Thread::Current(), method_handle_idx, referrer);
}

ObjPtr<mirror::MethodType> ResolveMethodTypeFromCode(ArtMethod* referrer,
                                                     dex::ProtoIndex proto_idx) {
  Thread::PoisonObjectPointersIfDebug();
  ObjPtr<mirror::MethodType> method_type =
      referrer->GetDexCache()->GetResolvedMethodType(proto_idx);
  if (UNLIKELY(method_type == nullptr)) {
    StackHandleScope<2> hs(Thread::Current());
    Handle<mirror::DexCache> dex_cache(hs.NewHandle(referrer->GetDexCache()));
    Handle<mirror::ClassLoader> class_loader(hs.NewHandle(referrer->GetClassLoader()));
    ClassLinker* class_linker = Runtime::Current()->GetClassLinker();
    method_type = class_linker->ResolveMethodType(hs.Self(), proto_idx, dex_cache, class_loader);
  }
  return method_type;
}

void MaybeUpdateBssMethodEntry(ArtMethod* callee,
                               MethodReference callee_reference,
                               ArtMethod* outer_method) {
  DCHECK_NE(callee, nullptr);
  if (outer_method->GetDexFile()->GetOatDexFile() == nullptr ||
      outer_method->GetDexFile()->GetOatDexFile()->GetOatFile() == nullptr) {
    // No OatFile to update.
    return;
  }
  const OatFile* outer_oat_file = outer_method->GetDexFile()->GetOatDexFile()->GetOatFile();

  const DexFile* dex_file = callee_reference.dex_file;
  const OatDexFile* oat_dex_file = dex_file->GetOatDexFile();
  const IndexBssMapping* mapping = nullptr;
  if (oat_dex_file != nullptr && oat_dex_file->GetOatFile() == outer_oat_file) {
    // DexFiles compiled together to an oat file case.
    mapping = oat_dex_file->GetMethodBssMapping();
  } else {
    // Try to find the DexFile in the BCP of the outer_method.
    const OatFile::BssMappingInfo* mapping_info = outer_oat_file->FindBcpMappingInfo(dex_file);
    if (mapping_info != nullptr) {
      mapping = mapping_info->method_bss_mapping;
    }
  }

  // Perform the update if we found a mapping.
  if (mapping != nullptr) {
    size_t bss_offset =
        IndexBssMappingLookup::GetBssOffset(mapping,
                                            callee_reference.index,
                                            dex_file->NumMethodIds(),
                                            static_cast<size_t>(kRuntimePointerSize));
    if (bss_offset != IndexBssMappingLookup::npos) {
      DCHECK_ALIGNED(bss_offset, static_cast<size_t>(kRuntimePointerSize));
      DCHECK_NE(outer_oat_file, nullptr);
      ArtMethod** method_entry = reinterpret_cast<ArtMethod**>(
          const_cast<uint8_t*>(outer_oat_file->BssBegin() + bss_offset));
      DCHECK_GE(method_entry, outer_oat_file->GetBssMethods().data());
      DCHECK_LT(method_entry,
                outer_oat_file->GetBssMethods().data() + outer_oat_file->GetBssMethods().size());
      std::atomic<ArtMethod*>* atomic_entry =
          reinterpret_cast<std::atomic<ArtMethod*>*>(method_entry);
      if (kIsDebugBuild) {
        ArtMethod* existing = atomic_entry->load(std::memory_order_acquire);
        CHECK(existing->IsRuntimeMethod() || existing == callee);
      }
      static_assert(sizeof(*method_entry) == sizeof(*atomic_entry), "Size check.");
      atomic_entry->store(callee, std::memory_order_release);
    }
  }
}

}  // namespace art
