/*
 * Copyright (C) 2016 The Android Open Source Project
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

#ifndef ART_RUNTIME_ENTRYPOINTS_QUICK_QUICK_DEFAULT_INIT_ENTRYPOINTS_H_
#define ART_RUNTIME_ENTRYPOINTS_QUICK_QUICK_DEFAULT_INIT_ENTRYPOINTS_H_

#include "base/logging.h"  // FOR VLOG_IS_ON.
#include "entrypoints/jni/jni_entrypoints.h"
#include "entrypoints/runtime_asm_entrypoints.h"
#include "quick_alloc_entrypoints.h"
#include "quick_default_externs.h"
#include "quick_entrypoints.h"

namespace art HIDDEN {

static void DefaultInitEntryPoints(JniEntryPoints* jpoints,
                                   QuickEntryPoints* qpoints,
                                   bool monitor_jni_entry_exit) {
  // JNI
  jpoints->pDlsymLookup = reinterpret_cast<void*>(art_jni_dlsym_lookup_stub);
  jpoints->pDlsymLookupCritical = reinterpret_cast<void*>(art_jni_dlsym_lookup_critical_stub);

  // Alloc
  ResetQuickAllocEntryPoints(qpoints);

  // Resolution and initialization
  qpoints->SetInitializeStaticStorage(art_quick_initialize_static_storage);
  qpoints->SetResolveTypeAndVerifyAccess(art_quick_resolve_type_and_verify_access);
  qpoints->SetResolveType(art_quick_resolve_type);
  qpoints->SetResolveMethodHandle(art_quick_resolve_method_handle);
  qpoints->SetResolveMethodType(art_quick_resolve_method_type);
  qpoints->SetResolveString(art_quick_resolve_string);

  // Field
  qpoints->SetSet8Instance(art_quick_set8_instance);
  qpoints->SetSet8Static(art_quick_set8_static);
  qpoints->SetSet16Instance(art_quick_set16_instance);
  qpoints->SetSet16Static(art_quick_set16_static);
  qpoints->SetSet32Instance(art_quick_set32_instance);
  qpoints->SetSet32Static(art_quick_set32_static);
  qpoints->SetSet64Instance(art_quick_set64_instance);
  qpoints->SetSet64Static(art_quick_set64_static);
  qpoints->SetSetObjInstance(art_quick_set_obj_instance);
  qpoints->SetSetObjStatic(art_quick_set_obj_static);
  qpoints->SetGetByteInstance(art_quick_get_byte_instance);
  qpoints->SetGetBooleanInstance(art_quick_get_boolean_instance);
  qpoints->SetGetShortInstance(art_quick_get_short_instance);
  qpoints->SetGetCharInstance(art_quick_get_char_instance);
  qpoints->SetGet32Instance(art_quick_get32_instance);
  qpoints->SetGet64Instance(art_quick_get64_instance);
  qpoints->SetGetObjInstance(art_quick_get_obj_instance);
  qpoints->SetGetByteStatic(art_quick_get_byte_static);
  qpoints->SetGetBooleanStatic(art_quick_get_boolean_static);
  qpoints->SetGetShortStatic(art_quick_get_short_static);
  qpoints->SetGetCharStatic(art_quick_get_char_static);
  qpoints->SetGet32Static(art_quick_get32_static);
  qpoints->SetGet64Static(art_quick_get64_static);
  qpoints->SetGetObjStatic(art_quick_get_obj_static);

  // Array
  qpoints->SetAputObject(art_quick_aput_obj);

  // JNI
  qpoints->SetJniMethodStart(art_jni_method_start);
  qpoints->SetJniMethodEnd(art_jni_method_end);
  qpoints->SetQuickGenericJniTrampoline(art_quick_generic_jni_trampoline);
  qpoints->SetJniDecodeReferenceResult(JniDecodeReferenceResult);
  qpoints->SetJniReadBarrier(art_jni_read_barrier);
  qpoints->SetJniMethodEntryHook(art_jni_method_entry_hook);

  // Locks
  if (UNLIKELY(VLOG_IS_ON(systrace_lock_logging))) {
    qpoints->SetJniLockObject(art_jni_lock_object_no_inline);
    qpoints->SetJniUnlockObject(art_jni_unlock_object_no_inline);
    qpoints->SetLockObject(art_quick_lock_object_no_inline);
    qpoints->SetUnlockObject(art_quick_unlock_object_no_inline);
  } else {
    qpoints->SetJniLockObject(art_jni_lock_object);
    qpoints->SetJniUnlockObject(art_jni_unlock_object);
    qpoints->SetLockObject(art_quick_lock_object);
    qpoints->SetUnlockObject(art_quick_unlock_object);
  }

  // Invocation
  qpoints->SetQuickImtConflictTrampoline(art_quick_imt_conflict_trampoline);
  qpoints->SetQuickResolutionTrampoline(art_quick_resolution_trampoline);
  qpoints->SetQuickToInterpreterBridge(art_quick_to_interpreter_bridge);
  qpoints->SetInvokeDirectTrampolineWithAccessCheck(
      art_quick_invoke_direct_trampoline_with_access_check);
  qpoints->SetInvokeInterfaceTrampolineWithAccessCheck(
      art_quick_invoke_interface_trampoline_with_access_check);
  qpoints->SetInvokeStaticTrampolineWithAccessCheck(
      art_quick_invoke_static_trampoline_with_access_check);
  qpoints->SetInvokeSuperTrampolineWithAccessCheck(
      art_quick_invoke_super_trampoline_with_access_check);
  qpoints->SetInvokeVirtualTrampolineWithAccessCheck(
      art_quick_invoke_virtual_trampoline_with_access_check);
  qpoints->SetInvokePolymorphic(art_quick_invoke_polymorphic);
  qpoints->SetInvokeCustom(art_quick_invoke_custom);

  // Thread
  qpoints->SetTestSuspend(art_quick_test_suspend);

  // Throws
  qpoints->SetDeliverException(art_quick_deliver_exception);
  qpoints->SetThrowArrayBounds(art_quick_throw_array_bounds);
  qpoints->SetThrowDivZero(art_quick_throw_div_zero);
  qpoints->SetThrowNullPointer(art_quick_throw_null_pointer_exception);
  qpoints->SetThrowStackOverflow(art_quick_throw_stack_overflow);
  qpoints->SetThrowStringBounds(art_quick_throw_string_bounds);

  // Deoptimize
  qpoints->SetDeoptimize(art_quick_deoptimize_from_compiled_code);

  // StringBuilder append
  qpoints->SetStringBuilderAppend(art_quick_string_builder_append);

  // Tiered JIT support
  qpoints->SetUpdateInlineCache(art_quick_update_inline_cache);
  qpoints->SetCompileOptimized(art_quick_compile_optimized);

  // Tracing hooks
  qpoints->SetMethodEntryHook(art_quick_method_entry_hook);
  qpoints->SetMethodExitHook(art_quick_method_exit_hook);

  if (monitor_jni_entry_exit) {
    qpoints->SetJniMethodStart(art_jni_monitored_method_start);
    qpoints->SetJniMethodEnd(art_jni_monitored_method_end);
  }
}

}  // namespace art

#endif  // ART_RUNTIME_ENTRYPOINTS_QUICK_QUICK_DEFAULT_INIT_ENTRYPOINTS_H_
