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

#include "asm_support_x86_64.S"

#define MANAGED_ARGS_SAVE_SIZE /*xmm0-xmm7*/ 8 * 8 + /*padding*/ 8 + /* GPR args */ 6 * 8

MACRO0(SAVE_MANAGED_ARGS_INCREASE_FRAME)
    // Return address is on the stack.
    PUSH_ARG r9
    PUSH_ARG r8
    PUSH_ARG rcx
    PUSH_ARG rdx
    PUSH_ARG rsi
    PUSH_ARG rdi
    INCREASE_FRAME (/*FPRs*/ 8 * 8 + /*padding*/ 8)
    movsd %xmm0, 0(%rsp)
    movsd %xmm1, 8(%rsp)
    movsd %xmm2, 16(%rsp)
    movsd %xmm3, 24(%rsp)
    movsd %xmm4, 32(%rsp)
    movsd %xmm5, 40(%rsp)
    movsd %xmm6, 48(%rsp)
    movsd %xmm7, 56(%rsp)
END_MACRO

MACRO0(RESTORE_MANAGED_ARGS_DECREASE_FRAME)
    movsd 0(%rsp), %xmm0
    movsd 8(%rsp), %xmm1
    movsd 16(%rsp), %xmm2
    movsd 24(%rsp), %xmm3
    movsd 32(%rsp), %xmm4
    movsd 40(%rsp), %xmm5
    movsd 48(%rsp), %xmm6
    movsd 56(%rsp), %xmm7
    DECREASE_FRAME (/*FPRs*/ 8 * 8 + /*padding*/ 8)
    POP_ARG rdi
    POP_ARG rsi
    POP_ARG rdx
    POP_ARG rcx
    POP_ARG r8
    POP_ARG r9
END_MACRO

MACRO3(JNI_SAVE_MANAGED_ARGS_TRAMPOLINE, name, cxx_name, arg1)
DEFINE_FUNCTION \name
    // Note: Managed callee-save registers have been saved by the JNI stub.
    // Save register args RDI, RSI, RDX, RCX, R8, R9, mmx0-mmx7 and align stack.
    SAVE_MANAGED_ARGS_INCREASE_FRAME
    // Call `cxx_name()`.
    .ifnc \arg1, none
        mov REG_VAR(arg1), %rdi     // Pass arg1.
    .endif
    call CALLVAR(cxx_name)          // Call cxx_name(...).
    // Restore register args RDI, RSI, RDX, RCX, R8, R9, mmx0-mmx7 and return.
    RESTORE_MANAGED_ARGS_DECREASE_FRAME
    ret
END_FUNCTION \name
END_MACRO

MACRO4(JNI_SAVE_RETURN_VALUE_TRAMPOLINE, name, cxx_name, arg1, arg2)
DEFINE_FUNCTION \name
    // Save return registers and return address.
    PUSH_ARG rax
    INCREASE_FRAME /*mmx0*/ 8 + /*padding*/ 8
    movsd %xmm0, 0(%rsp)
    // Call `cxx_name()`.
    mov REG_VAR(arg1), %rdi         // Pass arg1.
    .ifnc \arg2, none
        mov REG_VAR(arg2), %rsi     // Pass arg2.
    .endif
    call CALLVAR(cxx_name)          // Call cxx_name(...).
    // Restore return registers and return.
    movsd 0(%rsp), %xmm0
    DECREASE_FRAME /*mmx0*/ 8 + /*padding*/ 8
    POP_ARG rax
    ret
END_FUNCTION \name
END_MACRO

    /*
     * Jni dlsym lookup stub.
     */
DEFINE_FUNCTION art_jni_dlsym_lookup_stub
    // Save callee and GPR args.
    PUSH_ARG r9   // Arg.
    PUSH_ARG r8   // Arg.
    PUSH_ARG rdi  // Arg. (JniEnv for normal and @FastNative)
    PUSH_ARG rsi  // Arg.
    PUSH_ARG rdx  // Arg.
    PUSH_ARG rcx  // Arg.
    // Create space for FPR args, plus padding for alignment
    INCREASE_FRAME 72
    // Save FPRs.
    movq %xmm0, 0(%rsp)
    movq %xmm1, 8(%rsp)
    movq %xmm2, 16(%rsp)
    movq %xmm3, 24(%rsp)
    movq %xmm4, 32(%rsp)
    movq %xmm5, 40(%rsp)
    movq %xmm6, 48(%rsp)
    movq %xmm7, 56(%rsp)
    // prepare call
    movq %gs:THREAD_SELF_OFFSET, %rdi      // RDI := Thread::Current()
    // Call artFindNativeMethod() for normal native and artFindNativeMethodRunnable()
    // for @FastNative or @CriticalNative.
    movq THREAD_TOP_QUICK_FRAME_OFFSET(%rdi), %rax   // uintptr_t tagged_quick_frame
    andq LITERAL(TAGGED_JNI_SP_MASK_TOGGLED64), %rax // ArtMethod** sp
    movq (%rax), %rax                                // ArtMethod* method
    testl LITERAL(ACCESS_FLAGS_METHOD_IS_FAST_NATIVE | ACCESS_FLAGS_METHOD_IS_CRITICAL_NATIVE), \
          ART_METHOD_ACCESS_FLAGS_OFFSET(%rax)
    jne .Llookup_stub_fast_or_critical_native
    call SYMBOL(artFindNativeMethod)  // (Thread*)
    jmp .Llookup_stub_continue
.Llookup_stub_fast_or_critical_native:
    call SYMBOL(artFindNativeMethodRunnable)  // (Thread*)
.Llookup_stub_continue:
    // restore arguments
    movq 0(%rsp), %xmm0
    movq 8(%rsp), %xmm1
    movq 16(%rsp), %xmm2
    movq 24(%rsp), %xmm3
    movq 32(%rsp), %xmm4
    movq 40(%rsp), %xmm5
    movq 48(%rsp), %xmm6
    movq 56(%rsp), %xmm7
    DECREASE_FRAME 72
    POP_ARG rcx  // Arg.
    POP_ARG rdx  // Arg.
    POP_ARG rsi  // Arg.
    POP_ARG rdi  // Arg. (JniEnv for normal and @FastNative)
    POP_ARG r8   // Arg.
    POP_ARG r9   // Arg.
    testq %rax, %rax              // check if returned method code is null
    jz .Lno_native_code_found     // if null, jump to return to handle
    jmp *%rax                     // otherwise, tail call to intended method
.Lno_native_code_found:
    ret
END_FUNCTION art_jni_dlsym_lookup_stub

    /*
     * Jni dlsym lookup stub for @CriticalNative.
     */
DEFINE_FUNCTION art_jni_dlsym_lookup_critical_stub
    // The hidden arg holding the tagged method (bit 0 set means GenericJNI) is RAX.
    // For Generic JNI we already have a managed frame, so we reuse the art_jni_dlsym_lookup_stub.
    testq LITERAL(1), %rax
    jnz art_jni_dlsym_lookup_stub

    // Save GPR args and method.
    PUSH_ARG r9
    PUSH_ARG r8
    PUSH_ARG rdi
    PUSH_ARG rsi
    PUSH_ARG rdx
    PUSH_ARG rcx
    PUSH_ARG rax
    // Create space for FPR args.
    INCREASE_FRAME 8 * 8
    // Save FPRs.
    movq %xmm0, 0(%rsp)
    movq %xmm1, 8(%rsp)
    movq %xmm2, 16(%rsp)
    movq %xmm3, 24(%rsp)
    movq %xmm4, 32(%rsp)
    movq %xmm5, 40(%rsp)
    movq %xmm6, 48(%rsp)
    movq %xmm7, 56(%rsp)
    // Note: It's the caller's responsibility to preserve xmm12-xmm15 as the tail call
    // to native shall always risk clobbering those.

    // Call artCriticalNativeFrameSize(method, caller_pc).
    movq %rax, %rdi       // Pass the method from hidden arg.
    movq 120(%rsp), %rsi  // Pass caller PC.
    call SYMBOL(artCriticalNativeFrameSize)

    // Restore registers.
    movq 0(%rsp), %xmm0
    movq 8(%rsp), %xmm1
    movq 16(%rsp), %xmm2
    movq 24(%rsp), %xmm3
    movq 32(%rsp), %xmm4
    movq 40(%rsp), %xmm5
    movq 48(%rsp), %xmm6
    movq 56(%rsp), %xmm7
    DECREASE_FRAME 8 * 8
    POP_ARG r10  // Restore method to R10.
    POP_ARG rcx
    POP_ARG rdx
    POP_ARG rsi
    POP_ARG rdi
    POP_ARG r8
    POP_ARG r9

    // Load caller PC to R11 and redefine return PC for CFI.
    movq (%rsp), %r11
    CFI_REGISTER(%rip, %r11)

    // Reserve space for a SaveRefsAndArgs managed frame, either for the actual runtime
    // method or for a GenericJNI frame which is similar but has a native method and a tag.
    INCREASE_FRAME FRAME_SIZE_SAVE_REFS_AND_ARGS - __SIZEOF_POINTER__

    // Calculate the number of QWORDs to move.
    shrq LITERAL(3), %rax
    jz .Lcritical_skip_copy_args

    // Save RDI, RSI, RCX so that we can use them for moving stack args.
    PUSH_ARG rdi
    PUSH_ARG rsi
    PUSH_ARG rcx

    // Move the stack args.
    movq %rax, %rcx
    leaq 3 * __SIZEOF_POINTER__(%rsp), %rdi
    leaq FRAME_SIZE_SAVE_REFS_AND_ARGS(%rdi), %rsi
    rep movsq

    // Restore RDI, RSI, RCX.
    POP_ARG rcx
    POP_ARG rsi
    POP_ARG rdi

.Lcritical_skip_copy_args:
    // Calculate the base address of the managed frame.
    leaq (%rsp, %rax, 8), %rax

    // Spill registers for the SaveRefsAndArgs frame above the stack args.
    // Note that the runtime shall not examine the args here, otherwise we would have to
    // move them in registers and stack to account for the difference between managed and
    // native ABIs. Do not update CFI while we hold the frame address in RAX and the values
    // in registers are unchanged.
    movq %r15, 192(%rax)
    movq %r14, 184(%rax)
    movq %r13, 176(%rax)
    movq %r12, 168(%rax)
    movq %r9, 160(%rax)
    movq %r8, 152(%rax)
    movq %rsi, 144(%rax)
    movq %rbp, 136(%rax)
    movq %rbx, 128(%rax)
    movq %rdx, 120(%rax)
    movq %rcx, 112(%rax)
    movq %xmm0, 16(%rax)
    movq %xmm1, 24(%rax)
    movq %xmm2, 32(%rax)
    movq %xmm3, 40(%rax)
    movq %xmm4, 48(%rax)
    movq %xmm5, 56(%rax)
    movq %xmm6, 64(%rax)
    movq %xmm7, 72(%rax)
    // Skip managed ABI callee-saves xmm12-xmm15.

    // Move the managed frame address to native callee-save register RBP and update CFI.
    movq %rax, %rbp
    CFI_EXPRESSION_BREG CFI_REG(r15), CFI_REG(rbp), 192
    CFI_EXPRESSION_BREG CFI_REG(r14), CFI_REG(rbp), 184
    CFI_EXPRESSION_BREG CFI_REG(r13), CFI_REG(rbp), 176
    CFI_EXPRESSION_BREG CFI_REG(r12), CFI_REG(rbp), 168
    // Skip args r9, r8, rsi.
    CFI_EXPRESSION_BREG CFI_REG(rbp), CFI_REG(rbp), 136
    CFI_EXPRESSION_BREG CFI_REG(rbx), CFI_REG(rbp), 128
    // Skip args rdx, rcx.
    // Skip args xmm0-xmm7.

    leaq 1(%rbp), %rax            // Prepare managed SP tagged for a GenericJNI frame.
    testl LITERAL(ACCESS_FLAGS_METHOD_IS_NATIVE), ART_METHOD_ACCESS_FLAGS_OFFSET(%r10)
    jnz .Lcritical_skip_prepare_runtime_method

    // Save the return PC for managed stack walk.
    // (When coming from a compiled stub, the correct return PC is already there.)
    movq %r11, FRAME_SIZE_SAVE_REFS_AND_ARGS - __SIZEOF_POINTER__(%rbp)

    // Replace the target method with the SaveRefsAndArgs runtime method.
    LOAD_RUNTIME_INSTANCE r10
    movq RUNTIME_SAVE_REFS_AND_ARGS_METHOD_OFFSET(%r10), %r10

    movq %rbp, %rax               // Prepare untagged managed SP for the runtime method.

.Lcritical_skip_prepare_runtime_method:
    // Store the method on the bottom of the managed frame.
    movq %r10, (%rbp)

    // Place (maybe tagged) managed SP in Thread::Current()->top_quick_frame.
    movq %rax, %gs:THREAD_TOP_QUICK_FRAME_OFFSET

    // Save our return PC in the padding.
    movq %r11, __SIZEOF_POINTER__(%rbp)
    CFI_EXPRESSION_BREG CFI_REG(rip), CFI_REG(rbp), __SIZEOF_POINTER__

    // Preserve the native arg register RDI in callee-save register RBX which was saved above.
    movq %rdi, %rbx

    // Call artFindNativeMethodRunnable()
    movq %gs:THREAD_SELF_OFFSET, %rdi  // pass Thread::Current()
    call SYMBOL(artFindNativeMethodRunnable)  // (Thread*)

    // Check for exception.
    test %rax, %rax
    CFI_REMEMBER_STATE
    jz .Lcritical_deliver_exception

    // Restore the native arg register RDI.
    movq %rbx, %rdi

    // Remember our return PC in R11.
    movq __SIZEOF_POINTER__(%rbp), %r11
    CFI_REGISTER(%rip, %r11)

    // Remember the frame base address in r10 but do not redefine CFI.
    movq %rbp, %r10

    // Restore the frame. We shall not need the method anymore.
    movq 16(%rbp), %xmm0
    movq 24(%rbp), %xmm1
    movq 32(%rbp), %xmm2
    movq 40(%rbp), %xmm3
    movq 48(%rbp), %xmm4
    movq 56(%rbp), %xmm5
    movq 64(%rbp), %xmm6
    movq 72(%rbp), %xmm7
    // Skip managed callee-saves xmm12-xmm15.
    movq 112(%rbp), %rcx
    movq 120(%rbp), %rdx
    RESTORE_REG_BASE rbp, rbx, 128
    // Delay restoring RBP as it's the managed frame base.
    movq 144(%rbp), %rsi
    movq 152(%rbp), %r8
    movq 160(%rbp), %r9
    RESTORE_REG_BASE rbp, r12, 168
    RESTORE_REG_BASE rbp, r13, 176
    RESTORE_REG_BASE rbp, r14, 184
    RESTORE_REG_BASE rbp, r15, 192
    // Restore RBP last.
    RESTORE_REG_BASE rbp, rbp, 136

    cmp %r10, %rsp
    je .Lcritical_skip_copy_args_back

    // Save RDI, RSI, RCX so that we can use them for moving stack args.
    PUSH_ARG rdi
    PUSH_ARG rsi
    PUSH_ARG rcx

    // Calculate the number of QWORDs to move.
    leaq -3 * __SIZEOF_POINTER__(%r10), %rcx
    subq %rsp, %rcx
    shrq LITERAL(3), %rcx

    // Move the stack args.
    leaq -__SIZEOF_POINTER__(%r10), %rsi
    leaq FRAME_SIZE_SAVE_REFS_AND_ARGS - __SIZEOF_POINTER__(%r10), %rdi
    std
    rep movsq
    cld

    // Restore RDI, RSI, RCX.
    POP_ARG rcx
    POP_ARG rsi
    POP_ARG rdi

.Lcritical_skip_copy_args_back:
    // Remove the frame reservation.
    DECREASE_FRAME FRAME_SIZE_SAVE_REFS_AND_ARGS - __SIZEOF_POINTER__

    // Store our return PC.
    movq %r11, (%rsp)
    CFI_REL_OFFSET(%rip, 0)

    // Do the tail call.
    jmp *%rax

.Lcritical_deliver_exception:
    CFI_RESTORE_STATE_AND_DEF_CFA %rbp, FRAME_SIZE_SAVE_REFS_AND_ARGS
    DELIVER_PENDING_EXCEPTION_FRAME_READY
END_FUNCTION art_jni_dlsym_lookup_critical_stub

    /*
     * Read barrier for the method's declaring class needed by JNI stub for static methods.
     * (We're using a pointer to the declaring class in `ArtMethod` as `jclass`.)
     */
JNI_SAVE_MANAGED_ARGS_TRAMPOLINE art_jni_read_barrier, artJniReadBarrier, none

    /*
     * Trampoline to `artJniMethodStart()` that preserves all managed arguments.
     */
JNI_SAVE_MANAGED_ARGS_TRAMPOLINE art_jni_method_start, artJniMethodStart, gs:THREAD_SELF_OFFSET

    /*
     * Trampoline to `artJniMethodEntryHook` that preserves all managed arguments.
     */
JNI_SAVE_MANAGED_ARGS_TRAMPOLINE \
    art_jni_method_entry_hook, artJniMethodEntryHook, gs:THREAD_SELF_OFFSET

    /*
     * Trampoline to `artJniMonitoredMethodStart()` that preserves all managed arguments.
     */
JNI_SAVE_MANAGED_ARGS_TRAMPOLINE \
    art_jni_monitored_method_start, artJniMonitoredMethodStart, gs:THREAD_SELF_OFFSET

    /*
     * Trampoline to `artJniMethodEnd()` that preserves all return registers.
     */
JNI_SAVE_RETURN_VALUE_TRAMPOLINE art_jni_method_end, artJniMethodEnd, gs:THREAD_SELF_OFFSET, none

    /*
     * Trampoline to `artJniMonitoredMethodEnd()` that preserves all return registers.
     */
JNI_SAVE_RETURN_VALUE_TRAMPOLINE \
    art_jni_monitored_method_end, artJniMonitoredMethodEnd, gs:THREAD_SELF_OFFSET, none

    /*
     * Entry from JNI stub that tries to lock the object in a fast path and
     * calls `artLockObjectFromCode()` (the same as for managed code) for the
     * difficult cases, may block for GC.
     * Custom calling convention:
     *     RBX holds the non-null object to lock.
     *     Callee-save registers have been saved and can be used as temporaries (except RBX).
     *     All argument registers need to be preserved.
     */
DEFINE_FUNCTION art_jni_lock_object
    LOCK_OBJECT_FAST_PATH rbx, ebp, art_jni_lock_object_no_inline
END_FUNCTION art_jni_lock_object

    /*
     * Entry from JNI stub that calls `artLockObjectFromCode()`
     * (the same as for managed code), may block for GC.
     * Custom calling convention:
     *     RBX holds the non-null object to lock.
     *     Callee-save registers have been saved and can be used as temporaries (except RBX).
     *     All argument registers need to be preserved.
     */
DEFINE_FUNCTION art_jni_lock_object_no_inline
    // This is also the slow path for art_jni_lock_object.
    // Save register args RDI, RSI, RDX, RCX, R8, R9, mmx0-mmx7 and align stack.
    SAVE_MANAGED_ARGS_INCREASE_FRAME
    // Call `artLockObjectFromCode()`
    movq %rbx, %rdi                       // Pass the object to lock.
    movq %gs:THREAD_SELF_OFFSET, %rsi     // Pass Thread::Current().
    call SYMBOL(artLockObjectFromCode)    // (object, Thread*)
    // Check result.
    testl %eax, %eax
    jnz   1f
    // Restore register args RDI, RSI, RDX, RCX, R8, R9, mmx0-mmx7 and return.
    RESTORE_MANAGED_ARGS_DECREASE_FRAME
    ret
    .cfi_adjust_cfa_offset MANAGED_ARGS_SAVE_SIZE
1:
    // All args are irrelevant when throwing an exception. Remove the spill area.
    DECREASE_FRAME MANAGED_ARGS_SAVE_SIZE
    // Rely on the JNI transition frame constructed in the JNI stub.
    movq %gs:THREAD_SELF_OFFSET, %rdi     // Pass Thread::Current().
    jmp  SYMBOL(artDeliverPendingExceptionFromCode)  // (Thread*); tail call.
END_FUNCTION art_jni_lock_object_no_inline

    /*
     * Entry from JNI stub that tries to unlock the object in a fast path and calls
     * `artJniUnlockObject()` for the difficult cases. Note that failure to unlock
     * is fatal, so we do not need to check for exceptions in the slow path.
     * Custom calling convention:
     *     RBX holds the non-null object to unlock.
     *     Callee-save registers have been saved and can be used as temporaries (except RBX).
     *     Return registers RAX and mmx0 need to be preserved.
     */
DEFINE_FUNCTION art_jni_unlock_object
    movq %rax, %r12                       // Preserve RAX in a different register.
    UNLOCK_OBJECT_FAST_PATH rbx, ebp, /*saved_rax*/ r12, .Lunlock_object_jni_slow

 .Lunlock_object_jni_slow:
    movq %r12, %rax                       // Restore RAX.
    jmp  SYMBOL(art_jni_unlock_object_no_inline)
END_FUNCTION art_jni_unlock_object

    /*
     * Entry from JNI stub that calls `artJniUnlockObject()`. Note that failure to
     * unlock is fatal, so we do not need to check for exceptions.
     * Custom calling convention:
     *     RBX holds the non-null object to unlock.
     *     Callee-save registers have been saved and can be used as temporaries (except RBX).
     *     Return registers RAX and mmx0 need to be preserved.
     */
    // This is also the slow path for art_jni_unlock_object.
JNI_SAVE_RETURN_VALUE_TRAMPOLINE \
    art_jni_unlock_object_no_inline, artJniUnlockObject, rbx, gs:THREAD_SELF_OFFSET
