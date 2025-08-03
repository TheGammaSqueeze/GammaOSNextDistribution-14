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

#include "asm_support_x86.S"

#define MANAGED_ARGS_SAVE_SIZE /*xmm0-xmm3*/ 4 * 8 + /*padding*/ 4 + /* GPR args */ 4 * 4

// Save register args and adds space for outgoing arguments.
// With `call_args_space = 0`, the ESP shall be 8-byte aligned but not 16-byte aligned,
// so either the `call_args_space` should be 8 (or 24, 40, ...) or the user of the macro
// needs to adjust the ESP explicitly afterwards.
MACRO1(SAVE_MANAGED_ARGS_INCREASE_FRAME, call_args_space)
    // Return address is on the stack.
    PUSH_ARG ebx
    PUSH_ARG edx
    PUSH_ARG ecx
    PUSH_ARG eax
    // Make xmm<n> spill slots 8-byte aligned.
    INCREASE_FRAME (\call_args_space + /*FPRs*/ 4 * 8 + /*padding*/ 4)
    movsd %xmm0, \call_args_space + 0(%esp)
    movsd %xmm1, \call_args_space + 8(%esp)
    movsd %xmm2, \call_args_space + 16(%esp)
    movsd %xmm3, \call_args_space + 24(%esp)
END_MACRO

MACRO1(RESTORE_MANAGED_ARGS_DECREASE_FRAME, call_args_space)
    movsd \call_args_space + 0(%esp), %xmm0
    movsd \call_args_space + 8(%esp), %xmm1
    movsd \call_args_space + 16(%esp), %xmm2
    movsd \call_args_space + 24(%esp), %xmm3
    DECREASE_FRAME \call_args_space + /*FPR args*/ 4 * 8 + /*padding*/ 4
    POP_ARG eax
    POP_ARG ecx
    POP_ARG edx
    POP_ARG ebx
END_MACRO

MACRO3(JNI_SAVE_MANAGED_ARGS_TRAMPOLINE, name, cxx_name, arg1)
DEFINE_FUNCTION \name
    // Note: Managed callee-save registers have been saved by the JNI stub.
    // Save register args EAX, ECX, EDX, EBX, mmx0-mmx3, add and padding above `arg1`.
    SAVE_MANAGED_ARGS_INCREASE_FRAME /*padding*/ 4
    // Call `cxx_name()`.
    PUSH_ARG RAW_VAR(arg1)        // Pass arg1.
    call CALLVAR(cxx_name)        // Call cxx_name(...).
    // Restore register args EAX, ECX, EDX, EBX, mmx0-mmx3 and return.
    RESTORE_MANAGED_ARGS_DECREASE_FRAME /*arg1*/ 4 + /*padding*/ 4
    ret
END_FUNCTION \name
END_MACRO

MACRO4(JNI_SAVE_RETURN_VALUE_TRAMPOLINE, name, cxx_name, arg1, arg2)
DEFINE_FUNCTION \name
    // Save return registers.
    PUSH_ARG edx
    PUSH_ARG eax
    .ifnc \arg2, none
        INCREASE_FRAME /*mmx0*/ 8 + /*padding*/ 4
        movsd %xmm0, 0(%esp)
        PUSH_ARG RAW_VAR(arg2)    // Pass arg2.
    .else
        INCREASE_FRAME /*padding*/ 4 + /*mmx0*/ 8 + /*padding*/ 4
        movsd %xmm0, 4(%esp)
    .endif
    // Call `cxx_name()`.
    PUSH_ARG RAW_VAR(arg1)        // Pass arg1.
    call CALLVAR(cxx_name)        // Call cxx_name(...).
    // Restore return registers and return.
    movsd 8(%esp), %xmm0
    DECREASE_FRAME /*call args*/ 8 + /*xmm0*/ 8 + /*padding*/ 4
    POP_ARG eax
    POP_ARG edx
    ret
END_FUNCTION \name
END_MACRO

    /*
     * Jni dlsym lookup stub.
     */
DEFINE_FUNCTION art_jni_dlsym_lookup_stub
    INCREASE_FRAME 8              // Align stack.
    pushl %fs:THREAD_SELF_OFFSET  // Pass Thread::Current().
    CFI_ADJUST_CFA_OFFSET(4)
    // Call artFindNativeMethod() for normal native and artFindNativeMethodRunnable()
    // for @FastNative or @CriticalNative.
    movl (%esp), %eax                                // Thread* self
    movl THREAD_TOP_QUICK_FRAME_OFFSET(%eax), %eax   // uintptr_t tagged_quick_frame
    andl LITERAL(TAGGED_JNI_SP_MASK_TOGGLED32), %eax // ArtMethod** sp
    movl (%eax), %eax                                // ArtMethod* method
    testl LITERAL(ACCESS_FLAGS_METHOD_IS_FAST_NATIVE | ACCESS_FLAGS_METHOD_IS_CRITICAL_NATIVE), \
          ART_METHOD_ACCESS_FLAGS_OFFSET(%eax)
    jne .Llookup_stub_fast_or_critical_native
    call SYMBOL(artFindNativeMethod)  // (Thread*)
    jmp .Llookup_stub_continue
.Llookup_stub_fast_or_critical_native:
    call SYMBOL(artFindNativeMethodRunnable)  // (Thread*)
.Llookup_stub_continue:
    DECREASE_FRAME 12             // Remove argument & padding.
    testl %eax, %eax              // Check if returned method code is null.
    jz .Lno_native_code_found     // If null, jump to return to handle.
    jmp *%eax                     // Otherwise, tail call to intended method.
.Lno_native_code_found:
    ret
END_FUNCTION art_jni_dlsym_lookup_stub

    /*
     * Jni dlsym lookup stub for @CriticalNative.
     */
DEFINE_FUNCTION art_jni_dlsym_lookup_critical_stub
    // The hidden arg holding the tagged method (bit 0 set means GenericJNI) is eax.
    // For Generic JNI we already have a managed frame, so we reuse the art_jni_dlsym_lookup_stub.
    testl LITERAL(1), %eax
    jnz art_jni_dlsym_lookup_stub

    // Since the native call args are all on the stack, we can use the managed args
    // registers as scratch registers. So, EBX, EDX and ECX are available.

    // Load caller PC.
    movl (%esp), %ecx

    // Save the caller method from the hidden arg.
    PUSH_ARG eax

    // Call artCriticalNativeFrameSize(method, caller_pc).
    PUSH_ARG ecx                  // Pass caller PC.
    PUSH_ARG eax                  // Pass method.
    call SYMBOL(artCriticalNativeFrameSize)  // (method, caller_pc)
    DECREASE_FRAME 8              // Remove args.

    // Restore method register to EBX.
    POP_ARG ebx

    // Load caller PC to EDX and redefine return PC for CFI.
    movl (%esp), %edx
    CFI_REGISTER(%eip, %edx)

    // Reserve space for a SaveRefsAndArgs managed frame, either for the actual runtime
    // method or for a GenericJNI frame which is similar but has a native method and a tag.
    INCREASE_FRAME FRAME_SIZE_SAVE_REFS_AND_ARGS - __SIZEOF_POINTER__

    // Calculate the number of DWORDs to move.
    movl %eax, %ecx
    shrl LITERAL(2), %ecx
    jecxz .Lcritical_skip_copy_args

    // Save EDI, ESI so that we can use them for moving stack args.
    PUSH edi
    PUSH esi

    // Move the stack args.
    leal 2 * __SIZEOF_POINTER__(%esp), %edi
    leal FRAME_SIZE_SAVE_REFS_AND_ARGS(%edi), %esi
    rep movsd

    // Restore EDI, ESI.
    POP esi
    POP edi

.Lcritical_skip_copy_args:
    // Calculate the base address of the managed frame.
    leal (%esp, %eax, 1), %eax

    leal 1(%eax), %ecx            // Prepare managed SP tagged for a GenericJNI frame.
    testl LITERAL(ACCESS_FLAGS_METHOD_IS_NATIVE), ART_METHOD_ACCESS_FLAGS_OFFSET(%ebx)
    jnz .Lcritical_skip_prepare_runtime_method

    // Save the return PC for managed stack walk.
    // (When coming from a compiled stub, the correct return PC is already there.)
    movl %edx, FRAME_SIZE_SAVE_REFS_AND_ARGS - __SIZEOF_POINTER__(%eax)

    // Replace the target method with the SaveRefsAndArgs runtime method.
    LOAD_RUNTIME_INSTANCE ebx
    movl RUNTIME_SAVE_REFS_AND_ARGS_METHOD_OFFSET(%ebx), %ebx

    movl %eax, %ecx               // Prepare untagged managed SP for the runtime method.

.Lcritical_skip_prepare_runtime_method:
    // Store the method on the bottom of the managed frame.
    movl %ebx, (%eax)

    // Move the managed frame address to native callee-save register EBX.
    movl %eax, %ebx

    // Spill registers for the SaveRefsAndArgs frame above the stack args.
    movl %edi, 56(%ebx)
    CFI_EXPRESSION_BREG CFI_REG(edi), CFI_REG(ebx), 56
    movl %esi, 52(%ebx)
    CFI_EXPRESSION_BREG CFI_REG(esi), CFI_REG(ebx), 52
    movl %ebp, 48(%ebx)
    CFI_EXPRESSION_BREG CFI_REG(ebp), CFI_REG(ebx), 48
    // Skip managed ABI args EBX, EDX, ECX and FPRs. The runtime shall not examine the
    // args in the managed frame. (We have already clobbered EBX, EDX, ECX anyway.)

    // Place (maybe tagged) managed SP in Thread::Current()->top_quick_frame.
    movl %ecx, %fs:THREAD_TOP_QUICK_FRAME_OFFSET

    // Save our return PC in a slot reserved for first FP arg in managed ABI.
    movl %edx, __SIZEOF_POINTER__(%ebx)
    CFI_EXPRESSION_BREG CFI_REG(eip), CFI_REG(ebx), __SIZEOF_POINTER__

    // Call artFindNativeMethodRunnable()
    INCREASE_FRAME 12             // Align stack.
    pushl %fs:THREAD_SELF_OFFSET  // pass Thread::Current()
    CFI_ADJUST_CFA_OFFSET(4)
    call SYMBOL(artFindNativeMethodRunnable)  // (Thread*)
    addl LITERAL(16), %esp
    CFI_ADJUST_CFA_OFFSET(-16)

    // Check for exception.
    test %eax, %eax
    CFI_REMEMBER_STATE
    jz .Lcritical_deliver_exception

    // Remember our return PC in EDX.
    movl __SIZEOF_POINTER__(%ebx), %edx
    CFI_REGISTER(%eip, %edx)

    // Restore callee-save registers from the frame. We shall not need the method anymore.
    movl 48(%ebx), %ebp
    CFI_RESTORE(%ebp)
    movl 52(%ebx), %esi
    CFI_RESTORE(%esi)
    movl 56(%ebx), %edi
    CFI_RESTORE(%edi)

    // Calculate the number of DWORDs to move.
    movl %ebx, %ecx
    subl %esp, %ecx
    shrl LITERAL(2), %ecx
    jecxz .Lcritical_skip_copy_args_back

    // Save EDI, ESI so that we can use them for moving stack args.
    PUSH edi
    PUSH esi

    // Move stack args to their original place.
    leal -__SIZEOF_POINTER__(%ebx), %esi
    leal FRAME_SIZE_SAVE_REFS_AND_ARGS - __SIZEOF_POINTER__(%ebx), %edi
    std
    rep movsd
    cld

    // Restore EDI, ESI.
    POP esi
    POP edi

.Lcritical_skip_copy_args_back:
    // Remove the frame reservation.
    DECREASE_FRAME FRAME_SIZE_SAVE_REFS_AND_ARGS - __SIZEOF_POINTER__

    // Store our return PC.
    movl %edx, (%esp)
    CFI_REL_OFFSET(%eip, 0)

    // Do the tail call.
    jmp *%eax

.Lcritical_deliver_exception:
    CFI_RESTORE_STATE_AND_DEF_CFA %esp, FRAME_SIZE_SAVE_REFS_AND_ARGS
    DELIVER_PENDING_EXCEPTION_FRAME_READY
END_FUNCTION art_jni_dlsym_lookup_critical_stub

    /*
     * Read barrier for the method's declaring class needed by JNI stub for static methods.
     * (We're using a pointer to the declaring class in `ArtMethod` as `jclass`.)
     */
JNI_SAVE_MANAGED_ARGS_TRAMPOLINE art_jni_read_barrier, artJniReadBarrier, eax

    /*
     * Trampoline to `artJniMethodStart()` that preserves all managed arguments.
     */
JNI_SAVE_MANAGED_ARGS_TRAMPOLINE art_jni_method_start, artJniMethodStart, fs:THREAD_SELF_OFFSET

    /*
     * Trampoline to `artJniMethodEntryHook` that preserves all managed arguments.
     */
JNI_SAVE_MANAGED_ARGS_TRAMPOLINE \
    art_jni_method_entry_hook, artJniMethodEntryHook, fs:THREAD_SELF_OFFSET

    /*
     * Trampoline to `artJniMonitoredMethodStart()` that preserves all managed arguments.
     */
JNI_SAVE_MANAGED_ARGS_TRAMPOLINE \
    art_jni_monitored_method_start, artJniMonitoredMethodStart, fs:THREAD_SELF_OFFSET

    /*
     * Trampoline to `artJniMethodEnd()` that preserves all return registers.
     */
JNI_SAVE_RETURN_VALUE_TRAMPOLINE art_jni_method_end, artJniMethodEnd, fs:THREAD_SELF_OFFSET, none

    /*
     * Trampoline to `artJniMonitoredMethodEnd()` that preserves all return registers.
     */
JNI_SAVE_RETURN_VALUE_TRAMPOLINE \
    art_jni_monitored_method_end, artJniMonitoredMethodEnd, fs:THREAD_SELF_OFFSET, none

    /*
     * Entry from JNI stub that tries to lock the object in a fast path and
     * calls `artLockObjectFromCode()` (the same as for managed code) for the
     * difficult cases, may block for GC.
     * Custom calling convention:
     *     EBP holds the non-null object to lock.
     *     Callee-save registers have been saved and can be used as temporaries (except EBP).
     *     All argument registers need to be preserved.
     */
DEFINE_FUNCTION art_jni_lock_object
    movl %eax, %edi                       // Preserve EAX in a callee-save register.
    LOCK_OBJECT_FAST_PATH ebp, esi, /*saved_eax*/ edi .Llock_object_jni_slow

.Llock_object_jni_slow:
    movl %edi, %eax                       // Restore EAX.
    jmp  SYMBOL(art_jni_lock_object_no_inline)
END_FUNCTION art_jni_lock_object

    /*
     * Entry from JNI stub that calls `artLockObjectFromCode()`
     * (the same as for managed code), may block for GC.
     * Custom calling convention:
     *     EBP holds the non-null object to lock.
     *     Callee-save registers have been saved and can be used as temporaries (except EBP).
     *     All argument registers need to be preserved.
     */
DEFINE_FUNCTION art_jni_lock_object_no_inline
    // This is also the slow path for art_jni_lock_object.
    // Save register args EAX, ECX, EDX, EBX, mmx0-mmx3; original value of EAX is in EDI.
    SAVE_MANAGED_ARGS_INCREASE_FRAME /*call_args_space*/ 0
    // Note: The stack is not 16-byte aligned here but it shall be after pushing args for the call.
    // Call `artLockObjectFromCode()`
    pushl %fs:THREAD_SELF_OFFSET          // Pass Thread::Current().
    CFI_ADJUST_CFA_OFFSET(4)
    PUSH_ARG ebp                          // Pass the object to lock.
    call SYMBOL(artLockObjectFromCode)    // (object, Thread*)
    // Check result.
    testl %eax, %eax
    jnz   1f
    // Restore register args EAX, ECX, EDX, EBX, mmx0-mmx3 and return.
    RESTORE_MANAGED_ARGS_DECREASE_FRAME /*call_args_space*/ 8
    ret
    .cfi_adjust_cfa_offset (/*call args*/ 8 + MANAGED_ARGS_SAVE_SIZE)
1:
    // All args are irrelevant when throwing an exception.
    // Remove the spill area except for new padding to align stack.
    DECREASE_FRAME (/*call args*/ 8 + MANAGED_ARGS_SAVE_SIZE - /*new padding*/ 8)
    // Rely on the JNI transition frame constructed in the JNI stub.
    pushl %fs:THREAD_SELF_OFFSET          // pass Thread::Current()
    CFI_ADJUST_CFA_OFFSET(4)
    call SYMBOL(artDeliverPendingExceptionFromCode)  // (Thread*)
    UNREACHABLE
END_FUNCTION art_jni_lock_object_no_inline

    /*
     * Entry from JNI stub that tries to unlock the object in a fast path and calls
     * `artJniUnlockObject()` for the difficult cases. Note that failure to unlock
     * is fatal, so we do not need to check for exceptions in the slow path.
     * Custom calling convention:
     *     EBP holds the non-null object to unlock.
     *     Callee-save registers have been saved and can be used as temporaries (except EBP).
     *     Return registers EAX, EDX and mmx0 need to be preserved.
     */
DEFINE_FUNCTION art_jni_unlock_object
    movl %eax, %edi                       // Preserve EAX in a different register.
    UNLOCK_OBJECT_FAST_PATH ebp, esi, /*saved_eax*/ edi, .Lunlock_object_jni_slow

 .Lunlock_object_jni_slow:
    movl %edi, %eax                       // Restore EAX.
    jmp  SYMBOL(art_jni_unlock_object_no_inline)
END_FUNCTION art_jni_unlock_object

    /*
     * Entry from JNI stub that calls `artJniUnlockObject()`. Note that failure to
     * unlock is fatal, so we do not need to check for exceptions.
     * Custom calling convention:
     *     EBP holds the non-null object to unlock.
     *     Callee-save registers have been saved and can be used as temporaries (except EBP).
     *     Return registers EAX, EDX and mmx0 need to be preserved.
     */
    // This is also the slow path for art_jni_unlock_object.
JNI_SAVE_RETURN_VALUE_TRAMPOLINE \
    art_jni_unlock_object_no_inline, artJniUnlockObject, ebp, fs:THREAD_SELF_OFFSET
