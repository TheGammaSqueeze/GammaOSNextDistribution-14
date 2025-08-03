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

#include "asm_support_arm64.S"

#define ALL_ARGS_SIZE (/*x0-x7*/ 8 * 8 + /*d0-d7*/ 8 * 8)

.macro SAVE_ALL_ARGS_INCREASE_FRAME extra_space
    // Save register args x0-x7, d0-d7 and return address.
    stp    x0, x1, [sp, #-(ALL_ARGS_SIZE + \extra_space)]!
    .cfi_adjust_cfa_offset (ALL_ARGS_SIZE + \extra_space)
    stp    x2, x3, [sp, #16]
    stp    x4, x5, [sp, #32]
    stp    x6, x7, [sp, #48]
    stp    d0, d1, [sp, #64]
    stp    d2, d3, [sp, #80]
    stp    d4, d5, [sp, #96]
    stp    d6, d7, [sp, #112]
.endm

.macro RESTORE_ALL_ARGS_DECREASE_FRAME extra_space
    ldp    x2, x3, [sp, #16]
    ldp    x4, x5, [sp, #32]
    ldp    x6, x7, [sp, #48]
    ldp    d0, d1, [sp, #64]
    ldp    d2, d3, [sp, #80]
    ldp    d4, d5, [sp, #96]
    ldp    d6, d7, [sp, #112]
    ldp    x0, x1, [sp], #(ALL_ARGS_SIZE + \extra_space)
    .cfi_adjust_cfa_offset -(ALL_ARGS_SIZE + \extra_space)
.endm

.macro JNI_SAVE_MANAGED_ARGS_TRAMPOLINE name, cxx_name, arg1 = "none"
    .extern \cxx_name
ENTRY \name
    // Save args and LR.
    SAVE_ALL_ARGS_INCREASE_FRAME /*padding*/ 8 + /*LR*/ 8
    str    lr, [sp, #(ALL_ARGS_SIZE + /*padding*/ 8)]
    .cfi_rel_offset lr, ALL_ARGS_SIZE + /*padding*/ 8
    // Call `cxx_name()`.
    .ifnc \arg1, none
        mov x0, \arg1                          // Pass arg1.
    .endif
    bl     \cxx_name                           // Call cxx_name(...).
    // Restore LR and args and return.
    ldr    lr, [sp, #(ALL_ARGS_SIZE + /*padding*/ 8)]
    .cfi_restore lr
    RESTORE_ALL_ARGS_DECREASE_FRAME /*padding*/ 8 + /*LR*/ 8
    ret
END \name
.endm

.macro JNI_SAVE_RETURN_VALUE_TRAMPOLINE name, cxx_name, arg1, arg2 = "none"
    .extern \cxx_name
ENTRY \name
    // Save return registers and return address.
    stp    x0, lr, [sp, #-32]!
    .cfi_adjust_cfa_offset 32
    .cfi_rel_offset lr, 8
    str    d0, [sp, #16]
    // Call `cxx_name()`.
    mov    x0, \arg1                           // Pass arg1.
    .ifnc \arg2, none
        mov x1, \arg2                          // Pass arg2.
    .endif
    bl     \cxx_name                           // Call cxx_name(...).
    // Restore return registers and return.
    ldr    d0, [sp, #16]
    ldp    x0, lr, [sp], #32
    .cfi_adjust_cfa_offset -32
    .cfi_restore lr
    ret
END \name
.endm

    /*
     * Jni dlsym lookup stub.
     */
    .extern artFindNativeMethod
    .extern artFindNativeMethodRunnable
ENTRY art_jni_dlsym_lookup_stub
    // spill regs.
    SAVE_ALL_ARGS_INCREASE_FRAME 2 * 8
    stp   x29, x30, [sp, ALL_ARGS_SIZE]
    .cfi_rel_offset x29, ALL_ARGS_SIZE
    .cfi_rel_offset x30, ALL_ARGS_SIZE + 8
    add   x29, sp, ALL_ARGS_SIZE

    mov x0, xSELF   // pass Thread::Current()
    // Call artFindNativeMethod() for normal native and artFindNativeMethodRunnable()
    // for @FastNative or @CriticalNative.
    ldr   xIP0, [x0, #THREAD_TOP_QUICK_FRAME_OFFSET]      // uintptr_t tagged_quick_frame
    bic   xIP0, xIP0, #TAGGED_JNI_SP_MASK                 // ArtMethod** sp
    ldr   xIP0, [xIP0]                                    // ArtMethod* method
    ldr   xIP0, [xIP0, #ART_METHOD_ACCESS_FLAGS_OFFSET]   // uint32_t access_flags
    mov   xIP1, #(ACCESS_FLAGS_METHOD_IS_FAST_NATIVE | ACCESS_FLAGS_METHOD_IS_CRITICAL_NATIVE)
    tst   xIP0, xIP1
    b.ne  .Llookup_stub_fast_or_critical_native
    bl    artFindNativeMethod
    b     .Llookup_stub_continue
    .Llookup_stub_fast_or_critical_native:
    bl    artFindNativeMethodRunnable
.Llookup_stub_continue:
    mov   x17, x0    // store result in scratch reg.

    // load spill regs.
    ldp   x29, x30, [sp, #ALL_ARGS_SIZE]
    .cfi_restore x29
    .cfi_restore x30
    RESTORE_ALL_ARGS_DECREASE_FRAME 2 * 8

    cbz   x17, 1f   // is method code null ?
    br    x17       // if non-null, tail call to method's code.

1:
    ret             // restore regs and return to caller to handle exception.
END art_jni_dlsym_lookup_stub

    /*
     * Jni dlsym lookup stub for @CriticalNative.
     */
ENTRY art_jni_dlsym_lookup_critical_stub
    // The hidden arg holding the tagged method (bit 0 set means GenericJNI) is x15.
    // For Generic JNI we already have a managed frame, so we reuse the art_jni_dlsym_lookup_stub.
    tbnz  x15, #0, art_jni_dlsym_lookup_stub

    // Save args, the hidden arg and caller PC. No CFI needed for args and the hidden arg.
    SAVE_ALL_ARGS_INCREASE_FRAME 2 * 8
    stp   x15, lr, [sp, #ALL_ARGS_SIZE]
    .cfi_rel_offset lr, ALL_ARGS_SIZE + 8

    // Call artCriticalNativeFrameSize(method, caller_pc)
    mov   x0, x15  // x0 := method (from hidden arg)
    mov   x1, lr   // x1 := caller_pc
    bl    artCriticalNativeFrameSize

    // Move frame size to x14.
    mov   x14, x0

    // Restore args, the hidden arg and caller PC.
    ldp   x15, lr, [sp, #128]
    .cfi_restore lr
    RESTORE_ALL_ARGS_DECREASE_FRAME 2 * 8

    // Reserve space for a SaveRefsAndArgs managed frame, either for the actual runtime
    // method or for a GenericJNI frame which is similar but has a native method and a tag.
    INCREASE_FRAME FRAME_SIZE_SAVE_REFS_AND_ARGS

    // Calculate the base address of the managed frame.
    add   x13, sp, x14

    // Prepare the return address for managed stack walk of the SaveRefsAndArgs frame.
    // If we're coming from JNI stub with tail call, it is LR. If we're coming from
    // JNI stub that saved the return address, it will be the last value we copy below.
    // If we're coming directly from compiled code, it is LR, set further down.
    mov   xIP1, lr

    // Move the stack args if any.
    cbz   x14, .Lcritical_skip_copy_args
    mov   x12, sp
.Lcritical_copy_args_loop:
    ldp   xIP0, xIP1, [x12, #FRAME_SIZE_SAVE_REFS_AND_ARGS]
    subs  x14, x14, #16
    stp   xIP0, xIP1, [x12], #16
    bne   .Lcritical_copy_args_loop
.Lcritical_skip_copy_args:

    // Spill registers for the SaveRefsAndArgs frame above the stack args.
    // Note that the runtime shall not examine the args here, otherwise we would have to
    // move them in registers and stack to account for the difference between managed and
    // native ABIs. Do not update CFI while we hold the frame address in x13 and the values
    // in registers are unchanged.
    stp   d0, d1, [x13, #16]
    stp   d2, d3, [x13, #32]
    stp   d4, d5, [x13, #48]
    stp   d6, d7, [x13, #64]
    stp   x1, x2, [x13, #80]
    stp   x3, x4, [x13, #96]
    stp   x5, x6, [x13, #112]
    stp   x7, x20, [x13, #128]
    stp   x21, x22, [x13, #144]
    stp   x23, x24, [x13, #160]
    stp   x25, x26, [x13, #176]
    stp   x27, x28, [x13, #192]
    stp   x29, xIP1, [x13, #208]  // xIP1: Save return address for tail call from JNI stub.
    // (If there were any stack args, we're storing the value that's already there.
    // For direct calls from compiled managed code, we shall overwrite this below.)

    // Move the managed frame address to native callee-save register x29 and update CFI.
    mov   x29, x13
    // Skip args d0-d7, x1-x7
    CFI_EXPRESSION_BREG 20, 29, 136
    CFI_EXPRESSION_BREG 21, 29, 144
    CFI_EXPRESSION_BREG 22, 29, 152
    CFI_EXPRESSION_BREG 23, 29, 160
    CFI_EXPRESSION_BREG 24, 29, 168
    CFI_EXPRESSION_BREG 25, 29, 176
    CFI_EXPRESSION_BREG 26, 29, 184
    CFI_EXPRESSION_BREG 27, 29, 192
    CFI_EXPRESSION_BREG 28, 29, 200
    CFI_EXPRESSION_BREG 29, 29, 208
    // The saved return PC for managed stack walk is not necessarily our LR.

    // Save our return PC in the padding.
    str   lr, [x29, #__SIZEOF_POINTER__]
    CFI_EXPRESSION_BREG 30, 29, __SIZEOF_POINTER__

    ldr   wIP0, [x15, #ART_METHOD_ACCESS_FLAGS_OFFSET]  // Load access flags.
    add   x14, x29, #1            // Prepare managed SP tagged for a GenericJNI frame.
    tbnz  wIP0, #ACCESS_FLAGS_METHOD_IS_NATIVE_BIT, .Lcritical_skip_prepare_runtime_method

    // When coming from a compiled method, the return PC for managed stack walk is LR.
    // (When coming from a compiled stub, the correct return PC is already stored above.)
    str   lr, [x29, #(FRAME_SIZE_SAVE_REFS_AND_ARGS - __SIZEOF_POINTER__)]

    // Replace the target method with the SaveRefsAndArgs runtime method.
    LOAD_RUNTIME_INSTANCE x15
    ldr   x15, [x15, #RUNTIME_SAVE_REFS_AND_ARGS_METHOD_OFFSET]

    mov   x14, x29                // Prepare untagged managed SP for the runtime method.

.Lcritical_skip_prepare_runtime_method:
    // Store the method on the bottom of the managed frame.
    str   x15, [x29]

    // Place (maybe tagged) managed SP in Thread::Current()->top_quick_frame.
    str   x14, [xSELF, #THREAD_TOP_QUICK_FRAME_OFFSET]

    // Preserve the native arg register x0 in callee-save register x28 which was saved above.
    mov   x28, x0

    // Call artFindNativeMethodRunnable()
    mov   x0, xSELF   // pass Thread::Current()
    bl    artFindNativeMethodRunnable

    // Store result in scratch reg.
    mov   x13, x0

    // Restore the native arg register x0.
    mov   x0, x28

    // Restore our return PC.
    RESTORE_REG_BASE x29, lr, __SIZEOF_POINTER__

    // Remember the stack args size, negated because SP cannot be on the right-hand side in SUB.
    sub   x14, sp, x29

    // Restore the frame. We shall not need the method anymore.
    ldp   d0, d1, [x29, #16]
    ldp   d2, d3, [x29, #32]
    ldp   d4, d5, [x29, #48]
    ldp   d6, d7, [x29, #64]
    ldp   x1, x2, [x29, #80]
    ldp   x3, x4, [x29, #96]
    ldp   x5, x6, [x29, #112]
    ldp   x7, x20, [x29, #128]
    .cfi_restore x20
    RESTORE_TWO_REGS_BASE x29, x21, x22, 144
    RESTORE_TWO_REGS_BASE x29, x23, x24, 160
    RESTORE_TWO_REGS_BASE x29, x25, x26, 176
    RESTORE_TWO_REGS_BASE x29, x27, x28, 192
    RESTORE_REG_BASE x29, x29, 208

    REFRESH_MARKING_REGISTER

    // Check for exception before moving args back to keep the return PC for managed stack walk.
    CFI_REMEMBER_STATE
    cbz   x13, .Lcritical_deliver_exception

    // Move stack args to their original place.
    cbz   x14, .Lcritical_skip_copy_args_back
    sub   x12, sp, x14
.Lcritical_copy_args_back_loop:
    ldp   xIP0, xIP1, [x12, #-16]!
    adds  x14, x14, #16
    stp   xIP0, xIP1, [x12, #FRAME_SIZE_SAVE_REFS_AND_ARGS]
    bne   .Lcritical_copy_args_back_loop
.Lcritical_skip_copy_args_back:

    // Remove the frame reservation.
    DECREASE_FRAME FRAME_SIZE_SAVE_REFS_AND_ARGS

    // Do the tail call.
    br    x13

.Lcritical_deliver_exception:
    CFI_RESTORE_STATE_AND_DEF_CFA sp, FRAME_SIZE_SAVE_REFS_AND_ARGS
    // The exception delivery checks that xSELF was saved but the SaveRefsAndArgs
    // frame does not save it, so we cannot use the existing SaveRefsAndArgs frame.
    // That's why we checked for exception after restoring registers from it.
    // We need to build a SaveAllCalleeSaves frame instead. Args are irrelevant at this
    // point but keep the area allocated for stack args to keep CFA definition simple.
    DECREASE_FRAME FRAME_SIZE_SAVE_REFS_AND_ARGS - FRAME_SIZE_SAVE_ALL_CALLEE_SAVES

    // Calculate the base address of the managed frame.
    sub   x13, sp, x14

    // Spill registers for the SaveAllCalleeSaves frame above the stack args area. Do not update
    // CFI while we hold the frame address in x13 and the values in registers are unchanged.
    stp   d8, d9, [x13, #16]
    stp   d10, d11, [x13, #32]
    stp   d12, d13, [x13, #48]
    stp   d14, d15, [x13, #64]
    stp   x19, x20, [x13, #80]
    stp   x21, x22, [x13, #96]
    stp   x23, x24, [x13, #112]
    stp   x25, x26, [x13, #128]
    stp   x27, x28, [x13, #144]
    str   x29, [x13, #160]
    // Keep the caller PC for managed stack walk.

    // Move the managed frame address to native callee-save register x29 and update CFI.
    mov   x29, x13
    CFI_EXPRESSION_BREG 19, 29, 80
    CFI_EXPRESSION_BREG 20, 29, 88
    CFI_EXPRESSION_BREG 21, 29, 96
    CFI_EXPRESSION_BREG 22, 29, 104
    CFI_EXPRESSION_BREG 23, 29, 112
    CFI_EXPRESSION_BREG 24, 29, 120
    CFI_EXPRESSION_BREG 25, 29, 128
    CFI_EXPRESSION_BREG 26, 29, 136
    CFI_EXPRESSION_BREG 27, 29, 144
    CFI_EXPRESSION_BREG 28, 29, 152
    CFI_EXPRESSION_BREG 29, 29, 160
    // The saved return PC for managed stack walk is not necessarily our LR.

    // Save our return PC in the padding.
    str   lr, [x29, #__SIZEOF_POINTER__]
    CFI_EXPRESSION_BREG 30, 29, __SIZEOF_POINTER__

    // Store ArtMethod* Runtime::callee_save_methods_[kSaveAllCalleeSaves] to the managed frame.
    LOAD_RUNTIME_INSTANCE xIP0
    ldr   xIP0, [xIP0, #RUNTIME_SAVE_ALL_CALLEE_SAVES_METHOD_OFFSET]
    str   xIP0, [x29]

    // Place the managed frame SP in Thread::Current()->top_quick_frame.
    str   x29, [xSELF, #THREAD_TOP_QUICK_FRAME_OFFSET]

    DELIVER_PENDING_EXCEPTION_FRAME_READY
END art_jni_dlsym_lookup_critical_stub

    /*
     * Read barrier for the method's declaring class needed by JNI stub for static methods.
     * (We're using a pointer to the declaring class in `ArtMethod` as `jclass`.)
     */
// The method argument is already in x0 for call to `artJniReadBarrier(ArtMethod*)`.
JNI_SAVE_MANAGED_ARGS_TRAMPOLINE art_jni_read_barrier, artJniReadBarrier

    /*
     * Trampoline to `artJniMethodStart()` that preserves all managed arguments.
     */
JNI_SAVE_MANAGED_ARGS_TRAMPOLINE art_jni_method_start, artJniMethodStart, xSELF

    /*
     * Trampoline to `artJniMethodEntryHook` that preserves all managed arguments.
     */
JNI_SAVE_MANAGED_ARGS_TRAMPOLINE art_jni_method_entry_hook, artJniMethodEntryHook, xSELF

    /*
     * Trampoline to `artJniMonitoredMethodStart()` that preserves all managed arguments.
     */
JNI_SAVE_MANAGED_ARGS_TRAMPOLINE art_jni_monitored_method_start, artJniMonitoredMethodStart, xSELF

    /*
     * Trampoline to `artJniMethodEnd()` that preserves all return registers.
     */
JNI_SAVE_RETURN_VALUE_TRAMPOLINE art_jni_method_end, artJniMethodEnd, xSELF

    /*
     * Trampoline to `artJniMonitoredMethodEnd()` that preserves all return registers.
     */
JNI_SAVE_RETURN_VALUE_TRAMPOLINE art_jni_monitored_method_end, artJniMonitoredMethodEnd, xSELF

    /*
     * Entry from JNI stub that tries to lock the object in a fast path and
     * calls `artLockObjectFromCode()` (the same as for managed code) for the
     * difficult cases, may block for GC.
     * Custom calling convention:
     *     x15 holds the non-null object to lock.
     *     Callee-save registers have been saved and can be used as temporaries.
     *     All argument registers need to be preserved.
     */
ENTRY art_jni_lock_object
    LOCK_OBJECT_FAST_PATH x15, art_jni_lock_object_no_inline, /*can_be_null*/ 0
END art_jni_lock_object

    /*
     * Entry from JNI stub that calls `artLockObjectFromCode()`
     * (the same as for managed code), may block for GC.
     * Custom calling convention:
     *     x15 holds the non-null object to lock.
     *     Callee-save registers have been saved and can be used as temporaries.
     *     All argument registers need to be preserved.
     */
    .extern artLockObjectFromCode
ENTRY art_jni_lock_object_no_inline
    // This is also the slow path for art_jni_lock_object.
    // Save args and LR.
    SAVE_ALL_ARGS_INCREASE_FRAME /*padding*/ 8 + /*LR*/ 8
    str    lr, [sp, #(ALL_ARGS_SIZE + /*padding*/ 8)]
    .cfi_rel_offset lr, ALL_ARGS_SIZE + /*padding*/ 8
    // Call `artLockObjectFromCode()`.
    mov    x0, x15                    // Pass the object to lock.
    mov    x1, xSELF                  // Pass Thread::Current().
    bl     artLockObjectFromCode      // (Object* obj, Thread*)
    // Restore return address.
    ldr    lr, [sp, #(ALL_ARGS_SIZE + /*padding*/ 8)]
    .cfi_restore lr
    // Check result.
    cbnz   x0, 1f
    // Restore register args x0-x7, d0-d7 and return.
    RESTORE_ALL_ARGS_DECREASE_FRAME /*padding*/ 8 + /*LR*/ 8
    ret
    .cfi_adjust_cfa_offset (ALL_ARGS_SIZE + /*padding*/ 8 + /*LR*/ 8)
1:
    // All args are irrelevant when throwing an exception. Remove the spill area.
    DECREASE_FRAME (ALL_ARGS_SIZE + /*padding*/ 8 + /*LR*/ 8)
    // Make a tail call to `artDeliverPendingExceptionFromCode()`.
    // Rely on the JNI transition frame constructed in the JNI stub.
    mov    x0, xSELF                           // Pass Thread::Current().
    b      artDeliverPendingExceptionFromCode  // (Thread*)
END art_jni_lock_object_no_inline

    /*
     * Entry from JNI stub that tries to unlock the object in a fast path and calls
     * `artJniUnlockObject()` for the difficult cases. Note that failure to unlock
     * is fatal, so we do not need to check for exceptions in the slow path.
     * Custom calling convention:
     *     x15 holds the non-null object to unlock.
     *     Callee-save registers have been saved and can be used as temporaries.
     *     Return registers r0 and d0 need to be preserved.
     */
ENTRY art_jni_unlock_object
    UNLOCK_OBJECT_FAST_PATH x15, art_jni_unlock_object_no_inline, /*can_be_null*/ 0
END art_jni_unlock_object

    /*
     * Entry from JNI stub that calls `artJniUnlockObject()`. Note that failure to
     * unlock is fatal, so we do not need to check for exceptions.
     * Custom calling convention:
     *     x15 holds the non-null object to unlock.
     *     Callee-save registers have been saved and can be used as temporaries.
     *     Return registers r0 and d0 need to be preserved.
     */
    // This is also the slow path for art_jni_unlock_object.
JNI_SAVE_RETURN_VALUE_TRAMPOLINE art_jni_unlock_object_no_inline, artJniUnlockObject, x15, xSELF
