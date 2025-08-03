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

#ifndef ART_RUNTIME_ARCH_ARM64_ASM_SUPPORT_ARM64_S_
#define ART_RUNTIME_ARCH_ARM64_ASM_SUPPORT_ARM64_S_

#include "asm_support_arm64.h"
#include "interpreter/cfi_asm_support.h"

// Define special registers.

// Register holding Thread::Current().
#define xSELF x19
// Frame Pointer
#define xFP   x29
// Link Register
#define xLR   x30
// Define the intraprocedural linkage temporary registers.
#define xIP0 x16
#define wIP0 w16
#define xIP1 x17
#define wIP1 w17

#ifdef RESERVE_MARKING_REGISTER
// Marking Register, holding Thread::Current()->GetIsGcMarking().
#define wMR w20
#endif

// Implicit suspend check register.
#define xSUSPEND x21

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

.macro ENTRY_ALIGNED name, alignment
    .type \name, #function
    .hidden \name  // Hide this as a global symbol, so we do not incur plt calls.
    .global \name
    .balign \alignment
\name:
    .cfi_startproc
.endm

.macro ENTRY name
    ENTRY_ALIGNED \name, 16
.endm

.macro END name
    .cfi_endproc
    .size \name, .-\name
.endm

.macro UNIMPLEMENTED name
    ENTRY \name
    brk 0
    END \name
.endm

// Macro to poison (negate) the reference for heap poisoning.
.macro POISON_HEAP_REF rRef
#ifdef USE_HEAP_POISONING
    neg \rRef, \rRef
#endif  // USE_HEAP_POISONING
.endm

// Macro to unpoison (negate) the reference for heap poisoning.
.macro UNPOISON_HEAP_REF rRef
#ifdef USE_HEAP_POISONING
    neg \rRef, \rRef
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

.macro SAVE_REG reg, offset
    str \reg, [sp, #(\offset)]
    .cfi_rel_offset \reg, (\offset)
.endm

.macro RESTORE_REG_BASE base, reg, offset
    ldr \reg, [\base, #(\offset)]
    .cfi_restore \reg
.endm

.macro RESTORE_REG reg, offset
    RESTORE_REG_BASE sp, \reg, \offset
.endm

.macro SAVE_TWO_REGS_BASE base, reg1, reg2, offset
    stp \reg1, \reg2, [\base, #(\offset)]
    .cfi_rel_offset \reg1, (\offset)
    .cfi_rel_offset \reg2, (\offset) + 8
.endm

.macro SAVE_TWO_REGS reg1, reg2, offset
    SAVE_TWO_REGS_BASE sp, \reg1, \reg2, \offset
.endm

.macro RESTORE_TWO_REGS_BASE base, reg1, reg2, offset
    ldp \reg1, \reg2, [\base, #(\offset)]
    .cfi_restore \reg1
    .cfi_restore \reg2
.endm

.macro RESTORE_TWO_REGS reg1, reg2, offset
    RESTORE_TWO_REGS_BASE sp, \reg1, \reg2, \offset
.endm

.macro LOAD_RUNTIME_INSTANCE reg
#if __has_feature(hwaddress_sanitizer)
    adrp \reg, :pg_hi21_nc:_ZN3art7Runtime9instance_E
#else
    adrp \reg, _ZN3art7Runtime9instance_E
#endif
    ldr \reg, [\reg, #:lo12:_ZN3art7Runtime9instance_E]
.endm

// Macro to refresh the Marking Register (W20).
//
// This macro must be called at the end of functions implementing
// entrypoints that possibly (directly or indirectly) perform a
// suspend check (before they return).
.macro REFRESH_MARKING_REGISTER
#ifdef RESERVE_MARKING_REGISTER
    ldr wMR, [xSELF, #THREAD_IS_GC_MARKING_OFFSET]
#endif
.endm

// Macro to refresh the suspend check register.
//
// We do not refresh `xSUSPEND` after every transition to Runnable, so there is
// a chance that an implicit suspend check loads null to xSUSPEND but before
// causing a SIGSEGV at the next implicit suspend check we make a runtime call
// that performs the suspend check explicitly. This can cause a spurious fault
// without a pending suspend check request but it should be rare and the fault
// overhead was already expected when we triggered the suspend check, we just
// pay the price later than expected.
.macro REFRESH_SUSPEND_CHECK_REGISTER
    ldr xSUSPEND, [xSELF, #THREAD_SUSPEND_TRIGGER_OFFSET]
.endm

    /*
     * Macro that sets up the callee save frame to conform with
     * Runtime::CreateCalleeSaveMethod(kSaveRefsOnly).
     */
.macro SETUP_SAVE_REFS_ONLY_FRAME
    // art::Runtime* xIP0 = art::Runtime::instance_;
    // Our registers aren't intermixed - just spill in order.
    LOAD_RUNTIME_INSTANCE xIP0

    // ArtMethod* xIP0 = Runtime::instance_->callee_save_methods_[kSaveRefOnly];
    ldr xIP0, [xIP0, RUNTIME_SAVE_REFS_ONLY_METHOD_OFFSET]

    INCREASE_FRAME 96

    // Ugly compile-time check, but we only have the preprocessor.
#if (FRAME_SIZE_SAVE_REFS_ONLY != 96)
#error "FRAME_SIZE_SAVE_REFS_ONLY(ARM64) size not as expected."
#endif

    // GP callee-saves.
    // x20 paired with ArtMethod* - see below.
    SAVE_TWO_REGS x21, x22, 16
    SAVE_TWO_REGS x23, x24, 32
    SAVE_TWO_REGS x25, x26, 48
    SAVE_TWO_REGS x27, x28, 64
    SAVE_TWO_REGS x29, xLR, 80

    // Store ArtMethod* Runtime::callee_save_methods_[kSaveRefsOnly].
    // Note: We could avoid saving X20 in the case of Baker read
    // barriers, as it is overwritten by REFRESH_MARKING_REGISTER
    // later; but it's not worth handling this special case.
    stp xIP0, x20, [sp]
    .cfi_rel_offset x20, 8

    // Place sp in Thread::Current()->top_quick_frame.
    mov xIP0, sp
    str xIP0, [xSELF, # THREAD_TOP_QUICK_FRAME_OFFSET]
.endm

// TODO: Probably no need to restore registers preserved by aapcs64.
.macro RESTORE_SAVE_REFS_ONLY_FRAME
    // Callee-saves.
    // Note: Likewise, we could avoid restoring X20 in the case of Baker
    // read barriers, as it is overwritten by REFRESH_MARKING_REGISTER
    // later; but it's not worth handling this special case.
    RESTORE_REG x20, 8
    RESTORE_TWO_REGS x21, x22, 16
    RESTORE_TWO_REGS x23, x24, 32
    RESTORE_TWO_REGS x25, x26, 48
    RESTORE_TWO_REGS x27, x28, 64
    RESTORE_TWO_REGS x29, xLR, 80

    DECREASE_FRAME 96
.endm

.macro SETUP_SAVE_REFS_AND_ARGS_FRAME_INTERNAL base
    // Ugly compile-time check, but we only have the preprocessor.
#if (FRAME_SIZE_SAVE_REFS_AND_ARGS != 224)
#error "FRAME_SIZE_SAVE_REFS_AND_ARGS(ARM64) size not as expected."
#endif

    // Stack alignment filler [\base, #8].
    // FP args.
    stp d0, d1, [\base, #16]
    stp d2, d3, [\base, #32]
    stp d4, d5, [\base, #48]
    stp d6, d7, [\base, #64]

    // Core args.
    stp x1, x2, [\base, #80]
    stp x3, x4, [\base, #96]
    stp x5, x6, [\base, #112]

    // x7, Callee-saves.
    // Note: We could avoid saving X20 in the case of Baker read
    // barriers, as it is overwritten by REFRESH_MARKING_REGISTER
    // later; but it's not worth handling this special case.
    stp x7, x20, [\base, #128]
    .cfi_rel_offset x20, 136
    SAVE_TWO_REGS_BASE \base, x21, x22, 144
    SAVE_TWO_REGS_BASE \base, x23, x24, 160
    SAVE_TWO_REGS_BASE \base, x25, x26, 176
    SAVE_TWO_REGS_BASE \base, x27, x28, 192

    // x29(callee-save) and LR.
    SAVE_TWO_REGS_BASE \base, x29, xLR, 208
.endm

// TODO: Probably no need to restore registers preserved by aapcs64. (That would require
// auditing all users to make sure they restore aapcs64 callee-save registers they clobber.)
.macro RESTORE_SAVE_REFS_AND_ARGS_FRAME_INTERNAL base
    // FP args.
    ldp d0, d1, [\base, #16]
    ldp d2, d3, [\base, #32]
    ldp d4, d5, [\base, #48]
    ldp d6, d7, [\base, #64]

    // Core args.
    ldp x1, x2, [\base, #80]
    ldp x3, x4, [\base, #96]
    ldp x5, x6, [\base, #112]

    // x7, callee-saves and LR.
    // Note: Likewise, we could avoid restoring X20 in the case of Baker
    // read barriers, as it is overwritten by REFRESH_MARKING_REGISTER
    // later; but it's not worth handling this special case.
    ldp x7, x20, [\base, #128]
    .cfi_restore x20
    RESTORE_TWO_REGS_BASE \base, x21, x22, 144
    RESTORE_TWO_REGS_BASE \base, x23, x24, 160
    RESTORE_TWO_REGS_BASE \base, x25, x26, 176
    RESTORE_TWO_REGS_BASE \base, x27, x28, 192
    RESTORE_TWO_REGS_BASE \base, x29, xLR, 208
.endm

.macro RESTORE_SAVE_REFS_AND_ARGS_FRAME
    RESTORE_SAVE_REFS_AND_ARGS_FRAME_INTERNAL sp
    DECREASE_FRAME FRAME_SIZE_SAVE_REFS_AND_ARGS
.endm

.macro SAVE_ALL_CALLEE_SAVES offset
    // FP callee-saves.
    stp d8, d9,   [sp, #(0 + \offset)]
    stp d10, d11, [sp, #(16 + \offset)]
    stp d12, d13, [sp, #(32 + \offset)]
    stp d14, d15, [sp, #(48 + \offset)]

    // GP callee-saves
    SAVE_TWO_REGS x19, x20, (64 + \offset)
    SAVE_TWO_REGS x21, x22, (80 + \offset)
    SAVE_TWO_REGS x23, x24, (96 + \offset)
    SAVE_TWO_REGS x25, x26, (112 + \offset)
    SAVE_TWO_REGS x27, x28, (128 + \offset)
    SAVE_TWO_REGS x29, xLR, (144 + \offset)
.endm

    /*
     * Macro that sets up the callee save frame to conform with
     * Runtime::CreateCalleeSaveMethod(kSaveAllCalleeSaves)
     */
.macro SETUP_SAVE_ALL_CALLEE_SAVES_FRAME
    // art::Runtime* xIP0 = art::Runtime::instance_;
    // Our registers aren't intermixed - just spill in order.
    LOAD_RUNTIME_INSTANCE xIP0

    // ArtMethod* xIP0 = Runtime::instance_->callee_save_methods_[kSaveAllCalleeSaves];
    ldr xIP0, [xIP0, RUNTIME_SAVE_ALL_CALLEE_SAVES_METHOD_OFFSET]

    INCREASE_FRAME 176

    // Ugly compile-time check, but we only have the preprocessor.
#if (FRAME_SIZE_SAVE_ALL_CALLEE_SAVES != 176)
#error "FRAME_SIZE_SAVE_ALL_CALLEE_SAVES(ARM64) size not as expected."
#endif

    // Stack alignment filler [sp, #8].
    SAVE_ALL_CALLEE_SAVES 16

    // Store ArtMethod* Runtime::callee_save_methods_[kSaveAllCalleeSaves].
    str xIP0, [sp]
    // Place sp in Thread::Current()->top_quick_frame.
    mov xIP0, sp
    str xIP0, [xSELF, # THREAD_TOP_QUICK_FRAME_OFFSET]
.endm

    /*
     * Macro that calls through to artDeliverPendingExceptionFromCode, where the pending
     * exception is Thread::Current()->exception_ when the runtime method frame is ready.
     */
.macro DELIVER_PENDING_EXCEPTION_FRAME_READY
    mov x0, xSELF

    // Point of no return.
    bl artDeliverPendingExceptionFromCode  // artDeliverPendingExceptionFromCode(Thread*)
    brk 0  // Unreached
.endm

    /*
     * Macro that calls through to artDeliverPendingExceptionFromCode, where the pending
     * exception is Thread::Current()->exception_.
     */
.macro DELIVER_PENDING_EXCEPTION
    SETUP_SAVE_ALL_CALLEE_SAVES_FRAME
    DELIVER_PENDING_EXCEPTION_FRAME_READY
.endm

.macro RETURN_OR_DELIVER_PENDING_EXCEPTION_REG reg
    ldr \reg, [xSELF, # THREAD_EXCEPTION_OFFSET]   // Get exception field.
    cbnz \reg, 1f
    ret
1:
    DELIVER_PENDING_EXCEPTION
.endm

.macro RETURN_OR_DELIVER_PENDING_EXCEPTION
    RETURN_OR_DELIVER_PENDING_EXCEPTION_REG xIP0
.endm

// Locking is needed for both managed code and JNI stubs.
.macro LOCK_OBJECT_FAST_PATH obj, slow_lock, can_be_null
    // Use scratch registers x8-x11 as temporaries.
    ldr    w9, [xSELF, #THREAD_ID_OFFSET]
    .if \can_be_null
        cbz    \obj, \slow_lock
    .endif
                                      // Exclusive load/store has no immediate anymore.
    add    x8, \obj, #MIRROR_OBJECT_LOCK_WORD_OFFSET
1:
    ldaxr  w10, [x8]                  // Acquire needed only in most common case.
    eor    w11, w10, w9               // Prepare the value to store if unlocked
                                      //   (thread id, count of 0 and preserved read barrier bits),
                                      // or prepare to compare thread id for recursive lock check
                                      //   (lock_word.ThreadId() ^ self->ThreadId()).
    tst    w10, #LOCK_WORD_GC_STATE_MASK_SHIFTED_TOGGLED  // Test the non-gc bits.
    b.ne   2f                         // Check if unlocked.
    // Unlocked case - store w11: original lock word plus thread id, preserved read barrier bits.
    stxr   w10, w11, [x8]
    cbnz   w10, 1b                    // If the store failed, retry.
    ret
2:  // w10: original lock word, w9: thread id, w11: w10 ^ w9
                                      // Check lock word state and thread id together,
    tst    w11, #(LOCK_WORD_STATE_MASK_SHIFTED | LOCK_WORD_THIN_LOCK_OWNER_MASK_SHIFTED)
    b.ne   \slow_lock
    add    w11, w10, #LOCK_WORD_THIN_LOCK_COUNT_ONE  // Increment the recursive lock count.
    tst    w11, #LOCK_WORD_THIN_LOCK_COUNT_MASK_SHIFTED  // Test the new thin lock count.
    b.eq   \slow_lock                 // Zero as the new count indicates overflow, go slow path.
    stxr   w10, w11, [x8]
    cbnz   w10, 1b                    // If the store failed, retry.
    ret
.endm

// Unlocking is needed for both managed code and JNI stubs.
.macro UNLOCK_OBJECT_FAST_PATH obj, slow_unlock, can_be_null
    // Use scratch registers x8-x11 as temporaries.
    ldr    w9, [xSELF, #THREAD_ID_OFFSET]
    .if \can_be_null
        cbz    \obj, \slow_unlock
    .endif
                                      // Exclusive load/store has no immediate anymore.
    add    x8, \obj, #MIRROR_OBJECT_LOCK_WORD_OFFSET
1:
#ifndef USE_READ_BARRIER
    ldr    w10, [x8]
#else
    ldxr   w10, [x8]                  // Need to use atomic instructions for read barrier.
#endif
    eor    w11, w10, w9               // Prepare the value to store if simply locked
                                      //   (mostly 0s, and preserved read barrier bits),
                                      // or prepare to compare thread id for recursive lock check
                                      //   (lock_word.ThreadId() ^ self->ThreadId()).
    tst    w11, #LOCK_WORD_GC_STATE_MASK_SHIFTED_TOGGLED  // Test the non-gc bits.
    b.ne   2f                         // Locked recursively or by other thread?
    // Transition to unlocked.
#ifndef USE_READ_BARRIER
    stlr   w11, [x8]
#else
    stlxr  w10, w11, [x8]             // Need to use atomic instructions for read barrier.
    cbnz   w10, 1b                    // If the store failed, retry.
#endif
    ret
2:
                                      // Check lock word state and thread id together.
    tst    w11, #(LOCK_WORD_STATE_MASK_SHIFTED | LOCK_WORD_THIN_LOCK_OWNER_MASK_SHIFTED)
    b.ne   \slow_unlock
    sub    w11, w10, #LOCK_WORD_THIN_LOCK_COUNT_ONE  // decrement count
#ifndef USE_READ_BARRIER
    str    w11, [x8]
#else
    stxr   w10, w11, [x8]             // Need to use atomic instructions for read barrier.
    cbnz   w10, 1b                    // If the store failed, retry.
#endif
    ret
.endm

#endif  // ART_RUNTIME_ARCH_ARM64_ASM_SUPPORT_ARM64_S_
