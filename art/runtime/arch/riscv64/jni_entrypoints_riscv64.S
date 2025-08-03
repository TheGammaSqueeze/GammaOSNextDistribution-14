/*
 * Copyright (C) 2023 The Android Open Source Project
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

#include "asm_support_riscv64.S"


// 8 argument GPRS: a0 - a7 and 8 argument FPRs: fa0 - fa7
#define ALL_ARGS_SIZE (8 * (8 + 8))


.macro SAVE_ALL_ARGS_INCREASE_FRAME extra_space
    // Reserve space for all argument registers, plus the extra space.
    INCREASE_FRAME (ALL_ARGS_SIZE + \extra_space)

    // Argument GPRs a0 - a7.
    sd    a0, (8*0)(sp)
    sd    a1, (8*1)(sp)
    sd    a2, (8*2)(sp)
    sd    a3, (8*3)(sp)
    sd    a4, (8*4)(sp)
    sd    a5, (8*5)(sp)
    sd    a6, (8*6)(sp)
    sd    a7, (8*7)(sp)

    // Argument FPRs fa0 - fa7.
    fsd   fa0, (8*8)(sp)
    fsd   fa1, (8*9)(sp)
    fsd   fa2, (8*10)(sp)
    fsd   fa3, (8*11)(sp)
    fsd   fa4, (8*12)(sp)
    fsd   fa5, (8*13)(sp)
    fsd   fa6, (8*14)(sp)
    fsd   fa7, (8*15)(sp)
.endm


.macro RESTORE_ALL_ARGS_DECREASE_FRAME extra_space
    // Argument GPRs a0 - a7.
    ld    a0, (8*0)(sp)
    ld    a1, (8*1)(sp)
    ld    a2, (8*2)(sp)
    ld    a3, (8*3)(sp)
    ld    a4, (8*4)(sp)
    ld    a5, (8*5)(sp)
    ld    a6, (8*6)(sp)
    ld    a7, (8*7)(sp)

    // Argument FPRs fa0 - fa7.
    fld   fa0, (8*8)(sp)
    fld   fa1, (8*9)(sp)
    fld   fa2, (8*10)(sp)
    fld   fa3, (8*11)(sp)
    fld   fa4, (8*12)(sp)
    fld   fa5, (8*13)(sp)
    fld   fa6, (8*14)(sp)
    fld   fa7, (8*15)(sp)

    DECREASE_FRAME (ALL_ARGS_SIZE + \extra_space)
.endm


.macro JNI_SAVE_MANAGED_ARGS_TRAMPOLINE name, cxx_name, arg1 = "none"
    .extern \cxx_name
ENTRY \name
    // Save args and RA.
    SAVE_ALL_ARGS_INCREASE_FRAME /*padding*/ 8 + /*RA*/ 8
    SAVE_GPR ra, (ALL_ARGS_SIZE + /*padding*/ 8)
    // Call `cxx_name()`.
    .ifnc \arg1, none
        mv    a0, \arg1
    .endif
    call   \cxx_name
    // Restore RA and args and return.
    RESTORE_GPR ra, (ALL_ARGS_SIZE + /*padding*/ 8)
    RESTORE_ALL_ARGS_DECREASE_FRAME /*padding*/ 8 + /*RA*/ 8
    ret
END \name
.endm


.macro JNI_SAVE_RETURN_VALUE_TRAMPOLINE name, cxx_name, arg1, arg2 = "none"
    .extern \cxx_name
ENTRY \name
    // Save return registers and return address.
    INCREASE_FRAME 32
    sd    a0, 0(sp)
    fsd   fa0, 8(sp)
    SAVE_GPR ra, 24
    // Call `cxx_name()`.
    mv    a0, \arg1
    .ifnc \arg2, none
        mv    a1, \arg2
    .endif
    call  \cxx_name
    // Restore result registers and return.
    ld    a0, 0(sp)
    fld   fa0, 8(sp)
    RESTORE_GPR ra, 24
    DECREASE_FRAME 32
    ret
END \name
.endm


// JNI dlsym lookup stub.
.extern artFindNativeMethod
.extern artFindNativeMethodRunnable
ENTRY art_jni_dlsym_lookup_stub
    SAVE_ALL_ARGS_INCREASE_FRAME 2*8
    SAVE_GPR fp, (ALL_ARGS_SIZE + 0)
    SAVE_GPR ra, (ALL_ARGS_SIZE + 8)
    add  fp, sp, ALL_ARGS_SIZE

    // Call artFindNativeMethod for normal native.
    // Call artFindNativeMethodRunnable for @FastNative or @CriticalNative.
    // Both functions have a single argument: Thread::Current() in a0.
    mv   a0, xSELF
    ld   t0, THREAD_TOP_QUICK_FRAME_OFFSET(a0)   // uintptr_t tagged_quick_frame
    andi t0, t0, ~TAGGED_JNI_SP_MASK             // ArtMethod** sp
    ld   t0, (t0)                                // ArtMethod* method
    lw   t0, ART_METHOD_ACCESS_FLAGS_OFFSET(t0)  // uint32_t access_flags
    li   t1, (ACCESS_FLAGS_METHOD_IS_FAST_NATIVE | ACCESS_FLAGS_METHOD_IS_CRITICAL_NATIVE)
    and  t0, t0, t1
    bnez t0, .Llookup_stub_fast_or_critical_native
    call artFindNativeMethod
    j    .Llookup_stub_continue

.Llookup_stub_fast_or_critical_native:
    call  artFindNativeMethodRunnable

.Llookup_stub_continue:
    mv    t0, a0  // store result in a temp reg.
    RESTORE_GPR fp, (ALL_ARGS_SIZE + 0)
    RESTORE_GPR ra, (ALL_ARGS_SIZE + 8)
    RESTORE_ALL_ARGS_DECREASE_FRAME 2*8

    beqz  t0, 1f  // is method code null?
    jr    t0      // if non-null, tail call to method code.
1:
    ret           // restore regs and return to caller to handle exception.
END art_jni_dlsym_lookup_stub


// JNI dlsym lookup stub for @CriticalNative.
ENTRY art_jni_dlsym_lookup_critical_stub
    // The hidden arg holding the tagged method is t0 (loaded by compiled JNI stub, compiled
    // managed code, or `art_quick_generic_jni_trampoline`). Bit 0 set means generic JNI.
    // For generic JNI we already have a managed frame, so we reuse the art_jni_dlsym_lookup_stub.
    andi  t6, t0, 1
    bnez  t6, art_jni_dlsym_lookup_stub

    // Save args, the hidden arg and caller PC. No CFI needed for args and the hidden arg.
    SAVE_ALL_ARGS_INCREASE_FRAME 2*8
    SAVE_GPR t0, (ALL_ARGS_SIZE + 0)
    SAVE_GPR ra, (ALL_ARGS_SIZE + 8)

    // Call artCriticalNativeFrameSize(method, caller_pc)
    mv    a0, t0  // a0 := method (from hidden arg)
    mv    a1, ra  // a1 := caller_pc
    call  artCriticalNativeFrameSize

    // Move frame size to T2.
    mv    t2, a0

    // Restore args, the hidden arg and caller PC.
    RESTORE_GPR t0, (ALL_ARGS_SIZE + 0)
    RESTORE_GPR ra, (ALL_ARGS_SIZE + 8)
    RESTORE_ALL_ARGS_DECREASE_FRAME 2*8

    // Reserve space for a SaveRefsAndArgs managed frame, either for the actual runtime
    // method or for a GenericJNI frame which is similar but has a native method and a tag.
    // Add space for RA and padding to keep the stack 16-byte aligned.
    INCREASE_FRAME (FRAME_SIZE_SAVE_REFS_AND_ARGS + 16)

    // Prepare the return address for managed stack walk of the SaveRefsAndArgs frame.
    // If we're coming from JNI stub with tail call, it is RA. If we're coming from
    // JNI stub that saved the return address, it will be the last value we copy below.
    // If we're coming directly from compiled code, it is RA, set further down.
    mv    t4, ra

    // Move the stack args if any. Calculate the base address of the managed frame in the process.
    addi  t1, sp, 16
    beqz  t2, .Lcritical_skip_copy_args
.Lcritical_copy_args_loop:
    ld    t3, FRAME_SIZE_SAVE_REFS_AND_ARGS+0(t1)
    ld    t4, FRAME_SIZE_SAVE_REFS_AND_ARGS+8(t1)
    addi  t2, t2, -16
    sd    t3, 0-16(t1)
    sd    t4, 8-16(t1)
    addi  t1, t1, 16
    bnez  t2, .Lcritical_copy_args_loop
.Lcritical_skip_copy_args:

    // Spill registers for the SaveRefsAndArgs frame above the stack args.
    // Note that the runtime shall not examine the args here, otherwise we would have to
    // move them in registers and stack to account for the difference between managed and
    // native ABIs. Do not update CFI while we hold the frame address in T1 and the values
    // in registers are unchanged.
    // stack slot (0*8)(t1) is for ArtMethod*
    fsd   fa0, (1*8)(t1)
    fsd   fa1, (2*8)(t1)
    fsd   fa2, (3*8)(t1)
    fsd   fa3, (4*8)(t1)
    fsd   fa4, (5*8)(t1)
    fsd   fa5, (6*8)(t1)
    fsd   fa6, (7*8)(t1)
    fsd   fa7, (8*8)(t1)
    sd    fp,  (9*8)(t1)   // x8, frame pointer
    // s1 (x9) is the ART thread register
    // a0 (x10) is the method pointer
    sd    a1,  (10*8)(t1)  // x11
    sd    a2,  (11*8)(t1)  // x12
    sd    a3,  (12*8)(t1)  // x13
    sd    a4,  (13*8)(t1)  // x14
    sd    a5,  (14*8)(t1)  // x15
    sd    a6,  (15*8)(t1)  // x16
    sd    a7,  (16*8)(t1)  // x17
    sd    s2,  (17*8)(t1)  // x18
    sd    s3,  (18*8)(t1)  // x19
    sd    s4,  (19*8)(t1)  // x20
    sd    s5,  (20*8)(t1)  // x21
    sd    s6,  (21*8)(t1)  // x22
    sd    s7,  (22*8)(t1)  // x23
    sd    s8,  (23*8)(t1)  // x24
    sd    s9,  (24*8)(t1)  // x25
    sd    s10, (25*8)(t1)  // x26
    sd    s11, (26*8)(t1)  // x27
    sd    t4,  (27*8)(t1)  // t4: Save return address for tail call from JNI stub.
    // (If there were any stack args, we're storing the value that's already there.
    // For direct calls from compiled managed code, we shall overwrite this below.)

    // Move the managed frame address to native callee-save register fp (x8) and update CFI.
    mv    fp, t1
    // Skip args FA0-FA7, A1-A7
    CFI_EXPRESSION_BREG  8, 8, (9*8)
    CFI_EXPRESSION_BREG 18, 8, (17*8)
    CFI_EXPRESSION_BREG 19, 8, (18*8)
    CFI_EXPRESSION_BREG 20, 8, (19*8)
    CFI_EXPRESSION_BREG 21, 8, (20*8)
    CFI_EXPRESSION_BREG 22, 8, (21*8)
    CFI_EXPRESSION_BREG 23, 8, (22*8)
    CFI_EXPRESSION_BREG 24, 8, (23*8)
    CFI_EXPRESSION_BREG 25, 8, (24*8)
    CFI_EXPRESSION_BREG 26, 8, (25*8)
    CFI_EXPRESSION_BREG 27, 8, (26*8)
    // The saved return PC for managed stack walk is not necessarily our RA.

    // Save our return PC below the managed frame.
    sd    ra, -__SIZEOF_POINTER__(fp)
    CFI_EXPRESSION_BREG 1, 8, -__SIZEOF_POINTER__

    lw    t2, ART_METHOD_ACCESS_FLAGS_OFFSET(t0)  // Load access flags.
    addi  t1, fp, 1        // Prepare managed SP tagged for a GenericJNI frame.
    slliw t2, t2, 31 - ACCESS_FLAGS_METHOD_IS_NATIVE_BIT
    bltz  t2, .Lcritical_skip_prepare_runtime_method

    // When coming from a compiled method, the return PC for managed stack walk is RA.
    // (When coming from a compiled stub, the correct return PC is already stored above.)
    sd    ra, (FRAME_SIZE_SAVE_REFS_AND_ARGS - __SIZEOF_POINTER__)(fp)

    // Replace the target method with the SaveRefsAndArgs runtime method.
    LOAD_RUNTIME_INSTANCE t0
    ld    t0, RUNTIME_SAVE_REFS_AND_ARGS_METHOD_OFFSET(t0)

    mv    t1, fp           // Prepare untagged managed SP for the runtime method.

.Lcritical_skip_prepare_runtime_method:
    // Store the method on the bottom of the managed frame.
    sd    t0, (fp)

    // Place (maybe tagged) managed SP in Thread::Current()->top_quick_frame.
    sd    t1, THREAD_TOP_QUICK_FRAME_OFFSET(xSELF)

    // Preserve the native arg register A0 in callee-save register S2 (x18) which was saved above.
    mv    s2, a0

    // Call artFindNativeMethodRunnable()
    mv    a0, xSELF   // pass Thread::Current()
    call  artFindNativeMethodRunnable

    // Store result in scratch reg.
    mv    t0, a0

    // Restore the native arg register A0.
    mv    a0, s2

    // Restore our return PC.
    RESTORE_GPR_BASE fp, ra, -__SIZEOF_POINTER__

    // Remember the end of out args before restoring FP.
    addi  t1, fp, -16

    // Restore arg registers.
    fld   fa0, (1*8)(fp)
    fld   fa1, (2*8)(fp)
    fld   fa2, (3*8)(fp)
    fld   fa3, (4*8)(fp)
    fld   fa4, (5*8)(fp)
    fld   fa5, (6*8)(fp)
    fld   fa6, (7*8)(fp)
    fld   fa7, (8*8)(fp)
    // fp (x8) is restored last to keep CFI data valid until then.
    // s1 (x9) is the ART thread register
    // a0 (x10) is the method pointer
    ld    a1,  (10*8)(fp)  // x11
    ld    a2,  (11*8)(fp)  // x12
    ld    a3,  (12*8)(fp)  // x13
    ld    a4,  (13*8)(fp)  // x14
    ld    a5,  (14*8)(fp)  // x15
    ld    a6,  (15*8)(fp)  // x16
    ld    a7,  (16*8)(fp)  // x17
    RESTORE_GPR_BASE fp, s2,  (17*8)  // x18
    RESTORE_GPR_BASE fp, s3,  (18*8)  // x19
    RESTORE_GPR_BASE fp, s4,  (19*8)  // x20
    RESTORE_GPR_BASE fp, s5,  (20*8)  // x21
    RESTORE_GPR_BASE fp, s6,  (21*8)  // x22
    RESTORE_GPR_BASE fp, s7,  (22*8)  // x23
    RESTORE_GPR_BASE fp, s8,  (23*8)  // x24
    RESTORE_GPR_BASE fp, s9,  (24*8)  // x25
    RESTORE_GPR_BASE fp, s10, (25*8)  // x26
    RESTORE_GPR_BASE fp, s11, (26*8)  // x27
    RESTORE_GPR_BASE fp, fp,  (9*8)   // fp (x8) is restored last

    // Check for exception before moving args back to keep the return PC for managed stack walk.
    CFI_REMEMBER_STATE
    beqz  t0, .Lcritical_deliver_exception

    // Move stack args to their original place.
    beq   t1, sp, .Lcritical_skip_copy_args_back
    sub   t2, t1, sp
.Lcritical_copy_args_back_loop:
    ld    t3, 0-16(t1)
    ld    t4, 8-16(t1)
    addi  t2, t2, -16
    sd    t3, FRAME_SIZE_SAVE_REFS_AND_ARGS+0(t1)
    sd    t4, FRAME_SIZE_SAVE_REFS_AND_ARGS+8(t1)
    addi  t1, t1, -16
    bnez  t2, .Lcritical_copy_args_back_loop
.Lcritical_skip_copy_args_back:

    // Remove the frame reservation.
    DECREASE_FRAME (FRAME_SIZE_SAVE_REFS_AND_ARGS + 16)

    // Do the tail call.
    jr    t0

.Lcritical_deliver_exception:
    CFI_RESTORE_STATE_AND_DEF_CFA sp, FRAME_SIZE_SAVE_REFS_AND_ARGS + 16
    // If this is called from a method that catches the exception, all callee-save registers need
    // to be saved, so that the exception handling code can read them in case they contain live
    // values later used by that method. This includes callee-save FP registers which are not
    // saved in a SaveRefsAndArgs frame, so we cannot reuse the managed frame we have built above.
    // That's why we checked for exception after restoring registers from that frame.
    // We need to build a SaveAllCalleeSaves frame instead. Args are irrelevant at this
    // point but keep the area allocated for stack args to keep CFA definition simple.
#if FRAME_SIZE_SAVE_ALL_CALLEE_SAVES > FRAME_SIZE_SAVE_REFS_AND_ARGS
#error "Expanding stack frame from kSaveRefsAndArgs to kSaveAllCalleeSaves is not implemented."
#endif
    DECREASE_FRAME FRAME_SIZE_SAVE_REFS_AND_ARGS - FRAME_SIZE_SAVE_ALL_CALLEE_SAVES

    // Calculate the base address of the managed frame.
    addi  t1, t1, 16 + FRAME_SIZE_SAVE_REFS_AND_ARGS - FRAME_SIZE_SAVE_ALL_CALLEE_SAVES

    // Spill registers for the SaveAllCalleeSaves frame above the stack args area. Do not update
    // CFI while we hold the frame address in T1 and the values in registers are unchanged.
    // stack slot (0*8)(t1) is for ArtMethod*
    // stack slot (1*8)(t1) is for padding
    // FP callee-saves.
    fsd   fs0,  (8*2)(t1)   // f8
    fsd   fs1,  (8*3)(t1)   // f9
    fsd   fs2,  (8*4)(t1)   // f18
    fsd   fs3,  (8*5)(t1)   // f19
    fsd   fs4,  (8*6)(t1)   // f20
    fsd   fs5,  (8*7)(t1)   // f21
    fsd   fs6,  (8*8)(t1)   // f22
    fsd   fs7,  (8*9)(t1)   // f23
    fsd   fs8,  (8*10)(t1)  // f24
    fsd   fs9,  (8*11)(t1)  // f25
    fsd   fs10, (8*12)(t1)  // f26
    fsd   fs11, (8*13)(t1)  // f27
    // GP callee-saves
    sd    s0,  (8*14)(t1)  // x8/fp, frame pointer
    // s1 (x9) is the ART thread register
    sd    s2,  (8*15)(t1)  // x18
    sd    s3,  (8*16)(t1)  // x19
    sd    s4,  (8*17)(t1)  // x20
    sd    s5,  (8*18)(t1)  // x21
    sd    s6,  (8*19)(t1)  // x22
    sd    s7,  (8*20)(t1)  // x23
    sd    s8,  (8*21)(t1)  // x24
    sd    s9,  (8*22)(t1)  // x25
    sd    s10, (8*23)(t1)  // x26
    sd    s11, (8*24)(t1)  // x27
    // Keep the caller PC for managed stack walk.

    // Move the managed frame address to native callee-save register fp (x8) and update CFI.
    mv    fp, t1
    CFI_EXPRESSION_BREG  8, 8, (14*8)  // fp/x8: The base register for these CFI expressions.
    CFI_EXPRESSION_BREG  /*FP reg*/ 32 + 8, 8, (8*2)    // fs0/f8
    CFI_EXPRESSION_BREG  /*FP reg*/ 32 + 9, 8, (8*3)    // fs1/f9
    CFI_EXPRESSION_BREG  /*FP reg*/ 32 + 18, 8, (8*4)   // fs2/f18
    CFI_EXPRESSION_BREG  /*FP reg*/ 32 + 19, 8, (8*5)   // fs3/f19
    CFI_EXPRESSION_BREG  /*FP reg*/ 32 + 20, 8, (8*6)   // fs4/f20
    CFI_EXPRESSION_BREG  /*FP reg*/ 32 + 21, 8, (8*7)   // fs5/f21
    CFI_EXPRESSION_BREG  /*FP reg*/ 32 + 22, 8, (8*8)   // fs6/f22
    CFI_EXPRESSION_BREG  /*FP reg*/ 32 + 23, 8, (8*9)   // fs7/f23
    CFI_EXPRESSION_BREG  /*FP reg*/ 32 + 24, 8, (8*10)  // fs8/f24
    CFI_EXPRESSION_BREG  /*FP reg*/ 32 + 25, 8, (8*11)  // fs9/f25
    CFI_EXPRESSION_BREG  /*FP reg*/ 32 + 26, 8, (8*12)  // fs10/f26
    // CFI expression for fp (x8) already emitted above.
    CFI_EXPRESSION_BREG 18, 8, (15*8)  // s2/x18
    CFI_EXPRESSION_BREG 19, 8, (16*8)  // s3/x19
    CFI_EXPRESSION_BREG 20, 8, (17*8)  // s4/x20
    CFI_EXPRESSION_BREG 21, 8, (18*8)  // s5/x21
    CFI_EXPRESSION_BREG 22, 8, (19*8)  // s6/x22
    CFI_EXPRESSION_BREG 23, 8, (20*8)  // s7/x23
    CFI_EXPRESSION_BREG 24, 8, (21*8)  // s8/x24
    CFI_EXPRESSION_BREG 25, 8, (22*8)  // s9/x25
    CFI_EXPRESSION_BREG 26, 8, (23*8)  // s10/x26
    CFI_EXPRESSION_BREG 27, 8, (24*8)  // s11/x27
    // The saved return PC for managed stack walk is not necessarily our RA.

    // Save our return PC below the managed frame.
    sd    ra, -__SIZEOF_POINTER__(fp)
    CFI_EXPRESSION_BREG 1, 8, -__SIZEOF_POINTER__

    // Store ArtMethod* Runtime::callee_save_methods_[kSaveAllCalleeSaves] to the managed frame.
    LOAD_RUNTIME_INSTANCE t0
    ld    t0, RUNTIME_SAVE_ALL_CALLEE_SAVES_METHOD_OFFSET(t0)
    sd    t0, (fp)

    // Place the managed frame SP in Thread::Current()->top_quick_frame.
    sd    fp, THREAD_TOP_QUICK_FRAME_OFFSET(xSELF)

    DELIVER_PENDING_EXCEPTION_FRAME_READY
END art_jni_dlsym_lookup_critical_stub

    /*
     * Read barrier for the method's declaring class needed by JNI stub for static methods.
     * (We're using a pointer to the declaring class in `ArtMethod` as `jclass`.)
     */
// The method argument is already in a0 for call to `artJniReadBarrier(ArtMethod*)`.
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
     *     T0 holds the non-null object to lock.
     *     Callee-save registers have been saved and can be used as temporaries.
     *     All argument registers need to be preserved.
     */
ENTRY art_jni_lock_object
    LOCK_OBJECT_FAST_PATH t0, art_jni_lock_object_no_inline, /*can_be_null*/ 0
END art_jni_lock_object

    /*
     * Entry from JNI stub that calls `artLockObjectFromCode()`
     * (the same as for managed code), may block for GC.
     * Custom calling convention:
     *     T0 holds the non-null object to lock.
     *     Callee-save registers have been saved and can be used as temporaries.
     *     All argument registers need to be preserved.
     */
    .extern artLockObjectFromCode
ENTRY art_jni_lock_object_no_inline
    // This is also the slow path for art_jni_lock_object.
    // Save args and RA.
    SAVE_ALL_ARGS_INCREASE_FRAME /*padding*/ 8 + /*RA*/ 8
    SAVE_GPR ra, (ALL_ARGS_SIZE + /*padding*/ 8)
    // Call `artLockObjectFromCode()`.
    mv    a0, t0                     // Pass the object to lock.
    mv    a1, xSELF                  // Pass Thread::Current().
    call  artLockObjectFromCode      // (Object* obj, Thread*)
    // Restore return address.
    RESTORE_GPR ra, (ALL_ARGS_SIZE + /*padding*/ 8)
    // Check result.
    bnez   a0, 1f
    // Restore register args a0-a7, fa0-fa7 and return.
    RESTORE_ALL_ARGS_DECREASE_FRAME /*padding*/ 8 + /*RA*/ 8
    ret
    .cfi_adjust_cfa_offset (ALL_ARGS_SIZE + /*padding*/ 8 + /*RA*/ 8)
1:
    // All args are irrelevant when throwing an exception. Remove the spill area.
    DECREASE_FRAME (ALL_ARGS_SIZE + /*padding*/ 8 + /*RA*/ 8)
    // Make a tail call to `artDeliverPendingExceptionFromCode()`.
    // Rely on the JNI transition frame constructed in the JNI stub.
    mv     a0, xSELF                           // Pass Thread::Current().
    tail   artDeliverPendingExceptionFromCode  // (Thread*)
END art_jni_lock_object_no_inline

    /*
     * Entry from JNI stub that tries to unlock the object in a fast path and calls
     * `artJniUnlockObject()` for the difficult cases. Note that failure to unlock
     * is fatal, so we do not need to check for exceptions in the slow path.
     * Custom calling convention:
     *     T0 holds the non-null object to unlock.
     *     Callee-save registers have been saved and can be used as temporaries.
     *     Return registers a0 and fa0 need to be preserved.
     */
ENTRY art_jni_unlock_object
    UNLOCK_OBJECT_FAST_PATH t0, art_jni_unlock_object_no_inline, /*can_be_null*/ 0
END art_jni_unlock_object

    /*
     * Entry from JNI stub that calls `artJniUnlockObject()`. Note that failure to
     * unlock is fatal, so we do not need to check for exceptions.
     * Custom calling convention:
     *     T0 holds the non-null object to unlock.
     *     Callee-save registers have been saved and can be used as temporaries.
     *     Return registers a0 and fa0 need to be preserved.
     */
    // This is also the slow path for art_jni_unlock_object.
JNI_SAVE_RETURN_VALUE_TRAMPOLINE art_jni_unlock_object_no_inline, artJniUnlockObject, t0, xSELF
