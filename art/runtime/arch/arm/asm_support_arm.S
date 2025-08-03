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

#ifndef ART_RUNTIME_ARCH_ARM_ASM_SUPPORT_ARM_S_
#define ART_RUNTIME_ARCH_ARM_ASM_SUPPORT_ARM_S_

#include "asm_support_arm.h"
#include "interpreter/cfi_asm_support.h"

// Define special registers.

// Register holding suspend check count down.
#define rSUSPEND r4
// Register holding Thread::Current().
#define rSELF r9

#ifdef RESERVE_MARKING_REGISTER
// Marking Register, holding Thread::Current()->GetIsGcMarking().
#define rMR r8
#endif

.syntax unified
.arch armv7-a
.arch_extension idiv
.thumb

.macro CFI_EXPRESSION_BREG n, b, offset
    .if (-0x40 <= (\offset)) && ((\offset) < 0x40)
        CFI_EXPRESSION_BREG_1(\n, \b, \offset)
    .elseif (-0x2000 <= (\offset)) && ((\offset) < 0x2000)
        CFI_EXPRESSION_BREG_2(\n, \b, \offset)
    .else
        .error "Unsupported offset"
    .endif
.endm

.macro CFI_DEF_CFA_BREG_PLUS_UCONST reg, offset, size
    .if ((\size) < 0)
        .error "Size should be positive"
    .endif
    .if (((\offset) < -0x40) || ((\offset) >= 0x40))
        .error "Unsupported offset"
    .endif
    .if ((\size) < 0x80)
        CFI_DEF_CFA_BREG_PLUS_UCONST_1_1(\reg, \offset, \size)
    .elseif ((\size) < 0x4000)
        CFI_DEF_CFA_BREG_PLUS_UCONST_1_2(\reg, \offset, \size)
    .else
        .error "Unsupported size"
    .endif
.endm

.macro CFI_REMEMBER_STATE
    .cfi_remember_state
.endm

// The spec is not clear whether the CFA is part of the saved state and tools
// differ in the behaviour, so explicitly set the CFA to avoid any ambiguity.
// The restored CFA state should match the CFA state during CFI_REMEMBER_STATE.
.macro CFI_RESTORE_STATE_AND_DEF_CFA reg, offset
    .cfi_restore_state
    .cfi_def_cfa \reg, \offset
.endm

// Common ENTRY declaration code for ARM and thumb, an ENTRY should always be paired with an END.
.macro DEF_ENTRY thumb_or_arm, name, alignment
    \thumb_or_arm
// Clang ignores .thumb_func and requires an explicit .thumb. Investigate whether we should still
// carry around the .thumb_func.
    .ifc \thumb_or_arm, .thumb_func
        .thumb
    .endif
    .type \name, #function
    .hidden \name  // Hide this as a global symbol, so we do not incur plt calls.
    .global \name
    .balign \alignment
\name:
    .cfi_startproc
    .fnstart
.endm

// A thumb2 style ENTRY.
.macro ENTRY name
    DEF_ENTRY .thumb_func, \name, 16
.endm
.macro ENTRY_ALIGNED name, alignment
    DEF_ENTRY .thumb_func, \name, \alignment
.endm

// A ARM style ENTRY.
.macro ARM_ENTRY name
    DEF_ENTRY .arm, \name, 16
.endm

// Terminate an ENTRY.
.macro END name
    .fnend
    .cfi_endproc
    .size \name, .-\name
.endm

// Declare an unimplemented ENTRY that will halt a debugger.
.macro UNIMPLEMENTED name
    ENTRY \name
    bkpt
    bkpt
    END \name
.endm

// Macro to poison (negate) the reference for heap poisoning.
.macro POISON_HEAP_REF rRef
#ifdef USE_HEAP_POISONING
    rsb \rRef, \rRef, #0
#endif  // USE_HEAP_POISONING
.endm

// Macro to unpoison (negate) the reference for heap poisoning.
.macro UNPOISON_HEAP_REF rRef
#ifdef USE_HEAP_POISONING
    rsb \rRef, \rRef, #0
#endif  // USE_HEAP_POISONING
.endm

.macro INCREASE_FRAME frame_adjustment
    sub sp, sp, #(\frame_adjustment)
    .cfi_adjust_cfa_offset (\frame_adjustment)
.endm

.macro DECREASE_FRAME frame_adjustment
    add sp, sp, #(\frame_adjustment)
    .cfi_adjust_cfa_offset -(\frame_adjustment)
.endm

.macro LOAD_RUNTIME_INSTANCE rDest
    movw \rDest, #:lower16:(_ZN3art7Runtime9instance_E - (. + 12))
    movt \rDest, #:upper16:(_ZN3art7Runtime9instance_E - (. + 8))
    add \rDest, pc
    ldr \rDest, [\rDest]
.endm

// Macro to refresh the Marking Register (R8).
//
// This macro must be called at the end of functions implementing
// entrypoints that possibly (directly or indirectly) perform a
// suspend check (before they return).
.macro REFRESH_MARKING_REGISTER
#ifdef RESERVE_MARKING_REGISTER
    ldr rMR, [rSELF, #THREAD_IS_GC_MARKING_OFFSET]
#endif
.endm

.macro CONDITIONAL_CBZ reg, reg_if, dest
.ifc \reg, \reg_if
    cbz \reg, \dest
.endif
.endm

.macro CONDITIONAL_CMPBZ reg, reg_if, dest
.ifc \reg, \reg_if
    cmp \reg, #0
    beq \dest
.endif
.endm

// Use CBZ if the register is in {r0, r7} otherwise compare and branch.
.macro SMART_CBZ reg, dest
    CONDITIONAL_CBZ \reg, r0, \dest
    CONDITIONAL_CBZ \reg, r1, \dest
    CONDITIONAL_CBZ \reg, r2, \dest
    CONDITIONAL_CBZ \reg, r3, \dest
    CONDITIONAL_CBZ \reg, r4, \dest
    CONDITIONAL_CBZ \reg, r5, \dest
    CONDITIONAL_CBZ \reg, r6, \dest
    CONDITIONAL_CBZ \reg, r7, \dest
    CONDITIONAL_CMPBZ \reg, r8, \dest
    CONDITIONAL_CMPBZ \reg, r9, \dest
    CONDITIONAL_CMPBZ \reg, r10, \dest
    CONDITIONAL_CMPBZ \reg, r11, \dest
    CONDITIONAL_CMPBZ \reg, r12, \dest
    CONDITIONAL_CMPBZ \reg, r13, \dest
    CONDITIONAL_CMPBZ \reg, r14, \dest
    CONDITIONAL_CMPBZ \reg, r15, \dest
.endm

    /*
     * Macro that sets up the callee save frame to conform with
     * Runtime::CreateCalleeSaveMethod(kSaveRefsAndArgs), except for storing the method.
     */
.macro SETUP_SAVE_REFS_AND_ARGS_FRAME_REGISTERS_ONLY
    // Note: We could avoid saving R8 in the case of Baker read
    // barriers, as it is overwritten by REFRESH_MARKING_REGISTER
    // later; but it's not worth handling this special case.
    push {r1-r3, r5-r8, r10-r11, lr}   @ 10 words of callee saves and args.
    .cfi_adjust_cfa_offset 40
    .cfi_rel_offset r5, 12
    .cfi_rel_offset r6, 16
    .cfi_rel_offset r7, 20
    .cfi_rel_offset r8, 24
    .cfi_rel_offset r10, 28
    .cfi_rel_offset r11, 32
    .cfi_rel_offset lr, 36
    vpush {s0-s15}                     @ 16 words of float args.
    .cfi_adjust_cfa_offset 64
    sub sp, #8                         @ 2 words of space, alignment padding and Method*
    .cfi_adjust_cfa_offset 8
    // Ugly compile-time check, but we only have the preprocessor.
#if (FRAME_SIZE_SAVE_REFS_AND_ARGS != 40 + 64 + 8)
#error "FRAME_SIZE_SAVE_REFS_AND_ARGS(ARM) size not as expected."
#endif
.endm

.macro RESTORE_SAVE_REFS_AND_ARGS_FRAME
    add  sp, #8                      @ rewind sp
    .cfi_adjust_cfa_offset -8
    vpop {s0-s15}
    .cfi_adjust_cfa_offset -64
    // Note: Likewise, we could avoid restoring R8 in the case of Baker
    // read barriers, as it is overwritten by REFRESH_MARKING_REGISTER
    // later; but it's not worth handling this special case.
    pop {r1-r3, r5-r8, r10-r11, lr}  @ 10 words of callee saves and args.
    .cfi_restore r5
    .cfi_restore r6
    .cfi_restore r7
    .cfi_restore r8
    .cfi_restore r10
    .cfi_restore r11
    .cfi_restore lr
    .cfi_adjust_cfa_offset -40
.endm

    /*
     * Macro to spill the GPRs.
     */
.macro SPILL_ALL_CALLEE_SAVE_GPRS
    push {r4-r11, lr}                             @ 9 words (36 bytes) of callee saves.
    .cfi_adjust_cfa_offset 36
    .cfi_rel_offset r4, 0
    .cfi_rel_offset r5, 4
    .cfi_rel_offset r6, 8
    .cfi_rel_offset r7, 12
    .cfi_rel_offset r8, 16
    .cfi_rel_offset r9, 20
    .cfi_rel_offset r10, 24
    .cfi_rel_offset r11, 28
    .cfi_rel_offset lr, 32
.endm

    /*
     * Macro that sets up the callee save frame to conform with
     * Runtime::CreateCalleeSaveMethod(kSaveAllCalleeSaves)
     */
.macro SETUP_SAVE_ALL_CALLEE_SAVES_FRAME rTemp
    SPILL_ALL_CALLEE_SAVE_GPRS                    @ 9 words (36 bytes) of callee saves.
    vpush {s16-s31}                               @ 16 words (64 bytes) of floats.
    .cfi_adjust_cfa_offset 64
    sub sp, #12                                   @ 3 words of space, bottom word will hold Method*
    .cfi_adjust_cfa_offset 12
    LOAD_RUNTIME_INSTANCE \rTemp                  @ Load Runtime::Current into rTemp.
    @ Load kSaveAllCalleeSaves Method* into rTemp.
    ldr \rTemp, [\rTemp, #RUNTIME_SAVE_ALL_CALLEE_SAVES_METHOD_OFFSET]
    str \rTemp, [sp, #0]                          @ Place Method* at bottom of stack.
    str sp, [rSELF, #THREAD_TOP_QUICK_FRAME_OFFSET]  @ Place sp in Thread::Current()->top_quick_frame.

     // Ugly compile-time check, but we only have the preprocessor.
#if (FRAME_SIZE_SAVE_ALL_CALLEE_SAVES != 36 + 64 + 12)
#error "FRAME_SIZE_SAVE_ALL_CALLEE_SAVES(ARM) size not as expected."
#endif
.endm

    /*
     * Macro that calls through to artDeliverPendingExceptionFromCode, where the pending
     * exception is Thread::Current()->exception_ when the runtime method frame is ready.
     */
.macro DELIVER_PENDING_EXCEPTION_FRAME_READY
    mov    r0, rSELF                           @ pass Thread::Current
    bl     artDeliverPendingExceptionFromCode  @ artDeliverPendingExceptionFromCode(Thread*)
.endm

    /*
     * Macro that calls through to artDeliverPendingExceptionFromCode, where the pending
     * exception is Thread::Current()->exception_.
     */
.macro DELIVER_PENDING_EXCEPTION
    SETUP_SAVE_ALL_CALLEE_SAVES_FRAME r0       @ save callee saves for throw
    DELIVER_PENDING_EXCEPTION_FRAME_READY
.endm

.macro  RETURN_OR_DELIVER_PENDING_EXCEPTION_REG reg
    ldr \reg, [rSELF, #THREAD_EXCEPTION_OFFSET]  @ Get exception field.
    cbnz \reg, 1f
    bx lr
1:
    DELIVER_PENDING_EXCEPTION
.endm

.macro  RETURN_OR_DELIVER_PENDING_EXCEPTION
    ldr ip, [rSELF, #THREAD_EXCEPTION_OFFSET]  @ Get exception field.
    cmp ip, #0
    bne 1f
    bx lr
1:
    DELIVER_PENDING_EXCEPTION
.endm

    /*
     * Macro that sets up the callee save frame to conform with
     * Runtime::CreateCalleeSaveMethod(kSaveRefsOnly).
     */
.macro SETUP_SAVE_REFS_ONLY_FRAME rTemp
    // Note: We could avoid saving R8 in the case of Baker read
    // barriers, as it is overwritten by REFRESH_MARKING_REGISTER
    // later; but it's not worth handling this special case.
    push {r5-r8, r10-r11, lr}                     @ 7 words of callee saves
    .cfi_adjust_cfa_offset 28
    .cfi_rel_offset r5, 0
    .cfi_rel_offset r6, 4
    .cfi_rel_offset r7, 8
    .cfi_rel_offset r8, 12
    .cfi_rel_offset r10, 16
    .cfi_rel_offset r11, 20
    .cfi_rel_offset lr, 24
    sub sp, #4                                    @ bottom word will hold Method*
    .cfi_adjust_cfa_offset 4
    LOAD_RUNTIME_INSTANCE \rTemp                  @ Load Runtime::Current into rTemp.
    @ Load kSaveRefsOnly Method* into rTemp.
    ldr \rTemp, [\rTemp, #RUNTIME_SAVE_REFS_ONLY_METHOD_OFFSET]
    str \rTemp, [sp, #0]                          @ Place Method* at bottom of stack.
    str sp, [rSELF, #THREAD_TOP_QUICK_FRAME_OFFSET]  @ Place sp in Thread::Current()->top_quick_frame.

    // Ugly compile-time check, but we only have the preprocessor.
#if (FRAME_SIZE_SAVE_REFS_ONLY != 28 + 4)
#error "FRAME_SIZE_SAVE_REFS_ONLY(ARM) size not as expected."
#endif
.endm

.macro RESTORE_SAVE_REFS_ONLY_FRAME
    add sp, #4               @ bottom word holds Method*
    .cfi_adjust_cfa_offset -4
    // Note: Likewise, we could avoid restoring R8 in the case of Baker
    // read barriers, as it is overwritten by REFRESH_MARKING_REGISTER
    // later; but it's not worth handling this special case.
    pop {r5-r8, r10-r11, lr} @ 7 words of callee saves
    .cfi_restore r5
    .cfi_restore r6
    .cfi_restore r7
    .cfi_restore r8
    .cfi_restore r10
    .cfi_restore r11
    .cfi_restore lr
    .cfi_adjust_cfa_offset -28
.endm

// Locking is needed for both managed code and JNI stubs.
.macro LOCK_OBJECT_FAST_PATH obj, tmp1, tmp2, tmp3, slow_lock, can_be_null
    ldr    \tmp1, [rSELF, #THREAD_ID_OFFSET]
    .if \can_be_null
        cbz \obj, \slow_lock
    .endif
1:
    ldrex  \tmp2, [\obj, #MIRROR_OBJECT_LOCK_WORD_OFFSET]
    eor    \tmp3, \tmp2, \tmp1        @ Prepare the value to store if unlocked
                                      @   (thread id, count of 0 and preserved read barrier bits),
                                      @ or prepare to compare thread id for recursive lock check
                                      @   (lock_word.ThreadId() ^ self->ThreadId()).
    ands   ip, \tmp2, #LOCK_WORD_GC_STATE_MASK_SHIFTED_TOGGLED  @ Test the non-gc bits.
    bne    2f                         @ Check if unlocked.
    @ unlocked case - store tmp3: original lock word plus thread id, preserved read barrier bits.
    strex  \tmp2, \tmp3, [\obj, #MIRROR_OBJECT_LOCK_WORD_OFFSET]
    cbnz   \tmp2, 3f                   @ If store failed, retry.
    dmb    ish                        @ Full (LoadLoad|LoadStore) memory barrier.
    bx lr
2:  @ tmp2: original lock word, tmp1: thread_id, tmp3: tmp2 ^ tmp1
#if LOCK_WORD_THIN_LOCK_COUNT_SHIFT + LOCK_WORD_THIN_LOCK_COUNT_SIZE != LOCK_WORD_GC_STATE_SHIFT
#error "Expecting thin lock count and gc state in consecutive bits."
#endif
                                      @ Check lock word state and thread id together.
    bfc    \tmp3, \
           #LOCK_WORD_THIN_LOCK_COUNT_SHIFT, \
           #(LOCK_WORD_THIN_LOCK_COUNT_SIZE + LOCK_WORD_GC_STATE_SIZE)
    cbnz   \tmp3, \slow_lock          @ if either of the top two bits are set, or the lock word's
                                      @ thread id did not match, go slow path.
    add    \tmp3, \tmp2, #LOCK_WORD_THIN_LOCK_COUNT_ONE  @ Increment the recursive lock count.
                                      @ Extract the new thin lock count for overflow check.
    ubfx   \tmp2, \tmp3, #LOCK_WORD_THIN_LOCK_COUNT_SHIFT, #LOCK_WORD_THIN_LOCK_COUNT_SIZE
    cbz    \tmp2, \slow_lock          @ Zero as the new count indicates overflow, go slow path.
                                      @ strex necessary for read barrier bits.
    strex  \tmp2, \tmp3, [\obj, #MIRROR_OBJECT_LOCK_WORD_OFFSET]
    cbnz   \tmp2, 3f                  @ If strex failed, retry.
    bx lr
3:
    b      1b                         @ retry
.endm

// Unlocking is needed for both managed code and JNI stubs.
.macro UNLOCK_OBJECT_FAST_PATH obj, tmp1, tmp2, tmp3, slow_unlock, can_be_null
    ldr    \tmp1, [rSELF, #THREAD_ID_OFFSET]
    .if \can_be_null
        cbz    \obj, \slow_unlock
    .endif
1:
#ifndef USE_READ_BARRIER
    ldr    \tmp2, [\obj, #MIRROR_OBJECT_LOCK_WORD_OFFSET]
#else
                                      @ Need to use atomic instructions for read barrier.
    ldrex  \tmp2, [\obj, #MIRROR_OBJECT_LOCK_WORD_OFFSET]
#endif
    eor    \tmp3, \tmp2, \tmp1        @ Prepare the value to store if simply locked
                                      @   (mostly 0s, and preserved read barrier bits),
                                      @ or prepare to compare thread id for recursive lock check
                                      @   (lock_word.ThreadId() ^ self->ThreadId()).
    ands   ip, \tmp3, #LOCK_WORD_GC_STATE_MASK_SHIFTED_TOGGLED  @ Test the non-gc bits.
    bne    2f                         @ Locked recursively or by other thread?
    @ Transition to unlocked.
    dmb    ish                        @ Full (LoadStore|StoreStore) memory barrier.
#ifndef USE_READ_BARRIER
    str    \tmp3, [\obj, #MIRROR_OBJECT_LOCK_WORD_OFFSET]
#else
                                      @ strex necessary for read barrier bits
    strex  \tmp2, \tmp3, [\obj, #MIRROR_OBJECT_LOCK_WORD_OFFSET]
    cbnz   \tmp2, 3f                  @ If the store failed, retry.
#endif
    bx     lr
2:  @ tmp2: original lock word, tmp1: thread_id, tmp3: tmp2 ^ tmp1
#if LOCK_WORD_THIN_LOCK_COUNT_SHIFT + LOCK_WORD_THIN_LOCK_COUNT_SIZE != LOCK_WORD_GC_STATE_SHIFT
#error "Expecting thin lock count and gc state in consecutive bits."
#endif
                                      @ Check lock word state and thread id together,
    bfc    \tmp3, \
           #LOCK_WORD_THIN_LOCK_COUNT_SHIFT, \
           #(LOCK_WORD_THIN_LOCK_COUNT_SIZE + LOCK_WORD_GC_STATE_SIZE)
    cbnz   \tmp3, \slow_unlock        @ if either of the top two bits are set, or the lock word's
                                      @ thread id did not match, go slow path.
    sub    \tmp3, \tmp2, #LOCK_WORD_THIN_LOCK_COUNT_ONE  @ Decrement recursive lock count.
#ifndef USE_READ_BARRIER
    str    \tmp3, [\obj, #MIRROR_OBJECT_LOCK_WORD_OFFSET]
#else
                                      @ strex necessary for read barrier bits.
    strex  \tmp2, \tmp3, [\obj, #MIRROR_OBJECT_LOCK_WORD_OFFSET]
    cbnz   \tmp2, 3f                  @ If the store failed, retry.
#endif
    bx     lr
3:
    b      1b                         @ retry
.endm

#endif  // ART_RUNTIME_ARCH_X86_ASM_SUPPORT_X86_S_
