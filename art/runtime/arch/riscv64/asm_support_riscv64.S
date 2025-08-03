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

#ifndef ART_RUNTIME_ARCH_RISCV64_ASM_SUPPORT_RISCV64_S_
#define ART_RUNTIME_ARCH_RISCV64_ASM_SUPPORT_RISCV64_S_

#include "asm_support_riscv64.h"
#include "interpreter/cfi_asm_support.h"

// Define special registers.

// Register holding Thread::Current().
#define xSELF s1


.macro ENTRY name
    .hidden \name  // Hide this as a global symbol, so we do not incur plt calls.
    .global \name
    .balign 16
\name:
    .cfi_startproc
.endm


.macro END name
    .cfi_endproc
    .size \name, .-\name
.endm


.macro UNDEFINED name
    ENTRY \name
        unimp
    END \name
.endm


.macro CFI_REMEMBER_STATE
    .cfi_remember_state
.endm


// The spec is not clear whether the CFA is part of the saved state and tools differ in the
// behaviour, so explicitly set the CFA to avoid any ambiguity.
// The restored CFA state should match the CFA state during CFI_REMEMBER_STATE.
.macro CFI_RESTORE_STATE_AND_DEF_CFA reg, offset
    .cfi_restore_state
    .cfi_def_cfa \reg, \offset
.endm


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
    .if (((\offset) < -0x40) || ((\offset) >= 0x40))
        .error "Unsupported offset"
    .endif

    .if ((\size) < 0)
        .error "Unsupported size, negative"
    .elseif ((\size) < 0x80)
        CFI_DEF_CFA_BREG_PLUS_UCONST_1_1(\reg, \offset, \size)
    .elseif ((\size) < 0x4000)
        CFI_DEF_CFA_BREG_PLUS_UCONST_1_2(\reg, \offset, \size)
    .else
        .error "Unsupported size, too large"
    .endif
.endm


// Macro to poison (negate) the reference for heap poisoning.
.macro POISON_HEAP_REF ref
#ifdef USE_HEAP_POISONING
    neg \ref, \ref
    zext.w \ref, \ref
#endif  // USE_HEAP_POISONING
.endm


// Macro to unpoison (negate) the reference for heap poisoning.
.macro UNPOISON_HEAP_REF ref
#ifdef USE_HEAP_POISONING
    neg \ref, \ref
    zext.w \ref, \ref
#endif  // USE_HEAP_POISONING
.endm


.macro INCREASE_FRAME frame_adjustment
    addi sp, sp, -(\frame_adjustment)
    .cfi_adjust_cfa_offset (\frame_adjustment)
.endm


.macro DECREASE_FRAME frame_adjustment
    addi sp, sp, (\frame_adjustment)
    .cfi_adjust_cfa_offset -(\frame_adjustment)
.endm


.macro SAVE_GPR_BASE base, reg, offset
    sd \reg, (\offset)(\base)
    .cfi_rel_offset \reg, (\offset)
.endm


.macro SAVE_GPR reg, offset
    SAVE_GPR_BASE sp, \reg, \offset
.endm


.macro RESTORE_GPR_BASE base, reg, offset
    ld \reg, (\offset)(\base)
    .cfi_restore \reg
.endm


.macro RESTORE_GPR reg, offset
    RESTORE_GPR_BASE sp, \reg, \offset
.endm


.macro RESTORE_GPR_NE skip, reg, offset
    .ifnc \skip, \reg
    RESTORE_GPR_BASE sp, \reg, \offset
    .endif
.endm


.macro SAVE_FPR reg, offset
    fsd \reg, (\offset)(sp)
    .cfi_rel_offset \reg, (\offset)
.endm


.macro RESTORE_FPR reg, offset
    fld \reg, (\offset)(sp)
    .cfi_restore \reg
.endm


.macro LOAD_RUNTIME_INSTANCE reg
#if __has_feature(hwaddress_sanitizer)
#error "ART does not support HWASAN on RISC-V yet"
#else
    la \reg, _ZN3art7Runtime9instance_E
#endif
    ld \reg, 0(\reg)
.endm


// We need to save callee-save GPRs on the stack as they may contain references, and must be
// visible to GC (unless the called method holds mutator lock and prevents GC from happening).
// FP callee-saves shall be preserved by whatever runtime function we call, so they do not need
// to be saved.
.macro SETUP_SAVE_REFS_AND_ARGS_FRAME_INTERNAL
#if (FRAME_SIZE_SAVE_REFS_AND_ARGS != 8*(1 + 8 + 7 + 11 + 1))
#error "FRAME_SIZE_SAVE_REFS_AND_ARGS(RISCV64) size not as expected."
#endif
    // stack slot (0*8)(sp) is for ArtMethod*

    SAVE_FPR fa0, (1*8)
    SAVE_FPR fa1, (2*8)
    SAVE_FPR fa2, (3*8)
    SAVE_FPR fa3, (4*8)
    SAVE_FPR fa4, (5*8)
    SAVE_FPR fa5, (6*8)
    SAVE_FPR fa6, (7*8)
    SAVE_FPR fa7, (8*8)

    SAVE_GPR fp,  (9*8)  // x8, frame pointer
    // s1 (x9) is the ART thread register

    // a0 (x10) is the method pointer
    SAVE_GPR a1,  (10*8)  // x11
    SAVE_GPR a2,  (11*8)  // x12
    SAVE_GPR a3,  (12*8)  // x13
    SAVE_GPR a4,  (13*8)  // x14
    SAVE_GPR a5,  (14*8)  // x15
    SAVE_GPR a6,  (15*8)  // x16
    SAVE_GPR a7,  (16*8)  // x17

    SAVE_GPR s2,  (17*8)  // x18
    SAVE_GPR s3,  (18*8)  // x19
    SAVE_GPR s4,  (19*8)  // x20
    SAVE_GPR s5,  (20*8)  // x21
    SAVE_GPR s6,  (21*8)  // x22
    SAVE_GPR s7,  (22*8)  // x23
    SAVE_GPR s8,  (23*8)  // x24
    SAVE_GPR s9,  (24*8)  // x25
    SAVE_GPR s10, (25*8)  // x26
    SAVE_GPR s11, (26*8)  // x27

    SAVE_GPR ra,  (27*8)  // x1, return address
.endm


.macro RESTORE_SAVE_REFS_AND_ARGS_FRAME_INTERNAL
    // stack slot (0*8)(sp) is for ArtMethod*

    RESTORE_FPR fa0, (1*8)
    RESTORE_FPR fa1, (2*8)
    RESTORE_FPR fa2, (3*8)
    RESTORE_FPR fa3, (4*8)
    RESTORE_FPR fa4, (5*8)
    RESTORE_FPR fa5, (6*8)
    RESTORE_FPR fa6, (7*8)
    RESTORE_FPR fa7, (8*8)

    RESTORE_GPR fp,  (9*8)  // x8, frame pointer

    // a0 is the method pointer
    RESTORE_GPR a1,  (10*8)  // x11
    RESTORE_GPR a2,  (11*8)  // x12
    RESTORE_GPR a3,  (12*8)  // x13
    RESTORE_GPR a4,  (13*8)  // x14
    RESTORE_GPR a5,  (14*8)  // x15
    RESTORE_GPR a6,  (15*8)  // x16
    RESTORE_GPR a7,  (16*8)  // x17

    // s1 is the ART thread register
    RESTORE_GPR s2,  (17*8)  // x18
    RESTORE_GPR s3,  (18*8)  // x19
    RESTORE_GPR s4,  (19*8)  // x20
    RESTORE_GPR s5,  (20*8)  // x21
    RESTORE_GPR s6,  (21*8)  // x22
    RESTORE_GPR s7,  (22*8)  // x23
    RESTORE_GPR s8,  (23*8)  // x24
    RESTORE_GPR s9,  (24*8)  // x25
    RESTORE_GPR s10, (25*8)  // x26
    RESTORE_GPR s11, (26*8)  // x27

    RESTORE_GPR ra,  (27*8)  // x1, return address
.endm


.macro SETUP_CALLEE_SAVE_FRAME_COMMON_INTERNAL reg
    // ArtMethod* is in reg, store it at the bottom of the stack.
    sd \reg, (sp)

    // Place sp in Thread::Current()->top_quick_frame.
    sd sp, THREAD_TOP_QUICK_FRAME_OFFSET(xSELF)
.endm


.macro SETUP_CALLEE_SAVE_FRAME_COMMON tmpreg, runtime_method_offset
    // art::Runtime* tmpreg = art::Runtime::instance_;
    LOAD_RUNTIME_INSTANCE \tmpreg

    // ArtMethod* tmpreg = Runtime::instance_->callee_save_methods_[<callee-save-frame-type>];
    ld  \tmpreg, \runtime_method_offset(\tmpreg)

    SETUP_CALLEE_SAVE_FRAME_COMMON_INTERNAL \tmpreg
.endm


.macro SETUP_SAVE_REFS_AND_ARGS_FRAME
    INCREASE_FRAME FRAME_SIZE_SAVE_REFS_AND_ARGS
    SETUP_SAVE_REFS_AND_ARGS_FRAME_INTERNAL
    SETUP_CALLEE_SAVE_FRAME_COMMON t0, RUNTIME_SAVE_REFS_AND_ARGS_METHOD_OFFSET
.endm


.macro SETUP_SAVE_REFS_AND_ARGS_FRAME_WITH_METHOD_IN_A0
    INCREASE_FRAME FRAME_SIZE_SAVE_REFS_AND_ARGS
    SETUP_SAVE_REFS_AND_ARGS_FRAME_INTERNAL
    SETUP_CALLEE_SAVE_FRAME_COMMON_INTERNAL a0
.endm


.macro RESTORE_SAVE_REFS_AND_ARGS_FRAME
    RESTORE_SAVE_REFS_AND_ARGS_FRAME_INTERNAL
    DECREASE_FRAME FRAME_SIZE_SAVE_REFS_AND_ARGS
.endm


.macro SAVE_ALL_CALLEE_SAVES
#if (FRAME_SIZE_SAVE_ALL_CALLEE_SAVES != 8*(12 + 11 + 1 + 1 + 1))
#error "FRAME_SIZE_SAVE_ALL_CALLEE_SAVES(RISCV64) size not as expected."
#endif
    // stack slot (0*8)(sp) is for ArtMethod*
    // stack slot (1*8)(sp) is for padding

    // FP callee-saves.
    SAVE_FPR fs0,  (8*2)   // f8
    SAVE_FPR fs1,  (8*3)   // f9
    SAVE_FPR fs2,  (8*4)   // f18
    SAVE_FPR fs3,  (8*5)   // f19
    SAVE_FPR fs4,  (8*6)   // f20
    SAVE_FPR fs5,  (8*7)   // f21
    SAVE_FPR fs6,  (8*8)   // f22
    SAVE_FPR fs7,  (8*9)   // f23
    SAVE_FPR fs8,  (8*10)  // f24
    SAVE_FPR fs9,  (8*11)  // f25
    SAVE_FPR fs10, (8*12)  // f26
    SAVE_FPR fs11, (8*13)  // f27

    // GP callee-saves
    SAVE_GPR s0,  (8*14)  // x8/fp, frame pointer
    // s1 (x9) is the ART thread register
    SAVE_GPR s2,  (8*15)  // x18
    SAVE_GPR s3,  (8*16)  // x19
    SAVE_GPR s4,  (8*17)  // x20
    SAVE_GPR s5,  (8*18)  // x21
    SAVE_GPR s6,  (8*19)  // x22
    SAVE_GPR s7,  (8*20)  // x23
    SAVE_GPR s8,  (8*21)  // x24
    SAVE_GPR s9,  (8*22)  // x25
    SAVE_GPR s10, (8*23)  // x26
    SAVE_GPR s11, (8*24)  // x27

    SAVE_GPR ra,  (8*25)  // x1, return address
.endm


.macro RESTORE_ALL_CALLEE_SAVES
#if (FRAME_SIZE_SAVE_ALL_CALLEE_SAVES != 8*(12 + 11 + 1 + 1 + 1))
#error "FRAME_SIZE_SAVE_ALL_CALLEE_SAVES(RISCV64) size not as expected."
#endif
    // stack slot (8*0)(sp) is for ArtMethod*
    // stack slot (8*1)(sp) is for padding

    // FP callee-saves.
    RESTORE_FPR fs0,  (8*2)   // f8
    RESTORE_FPR fs1,  (8*3)   // f9
    RESTORE_FPR fs2,  (8*4)   // f18
    RESTORE_FPR fs3,  (8*5)   // f19
    RESTORE_FPR fs4,  (8*6)   // f20
    RESTORE_FPR fs5,  (8*7)   // f21
    RESTORE_FPR fs6,  (8*8)   // f22
    RESTORE_FPR fs7,  (8*9)   // f23
    RESTORE_FPR fs8,  (8*10)  // f24
    RESTORE_FPR fs9,  (8*11)  // f25
    RESTORE_FPR fs10, (8*12)  // f26
    RESTORE_FPR fs11, (8*13)  // f27

    // GP callee-saves
    RESTORE_GPR s0,  (8*14)  // x8/fp, frame pointer
    // s1 is the ART thread register
    RESTORE_GPR s2,  (8*15)  // x18
    RESTORE_GPR s3,  (8*16)  // x19
    RESTORE_GPR s4,  (8*17)  // x20
    RESTORE_GPR s5,  (8*18)  // x21
    RESTORE_GPR s6,  (8*19)  // x22
    RESTORE_GPR s7,  (8*20)  // x23
    RESTORE_GPR s8,  (8*21)  // x24
    RESTORE_GPR s9,  (8*22)  // x25
    RESTORE_GPR s10, (8*23)  // x26
    RESTORE_GPR s11, (8*24)  // x27

    RESTORE_GPR ra,  (8*25)  // x1, return address
.endm


.macro SETUP_SAVE_ALL_CALLEE_SAVES_FRAME
    INCREASE_FRAME FRAME_SIZE_SAVE_ALL_CALLEE_SAVES
    SAVE_ALL_CALLEE_SAVES
    SETUP_CALLEE_SAVE_FRAME_COMMON t0, RUNTIME_SAVE_ALL_CALLEE_SAVES_METHOD_OFFSET
.endm


.macro SETUP_SAVE_EVERYTHING_FRAME_DECREMENTED_SP_SKIP_RA \
        runtime_method_offset = RUNTIME_SAVE_EVERYTHING_METHOD_OFFSET
#if (FRAME_SIZE_SAVE_EVERYTHING != 8*(1 + 32 + 27))
#error "FRAME_SIZE_SAVE_EVERYTHING(RISCV64) size not as expected."
#endif
    // stack slot (8*0)(sp) is for ArtMethod*

    // 32 slots for FPRs
    SAVE_FPR ft0,  8*1   // f0
    SAVE_FPR ft1,  8*2   // f1
    SAVE_FPR ft2,  8*3   // f2
    SAVE_FPR ft3,  8*4   // f3
    SAVE_FPR ft4,  8*5   // f4
    SAVE_FPR ft5,  8*6   // f5
    SAVE_FPR ft6,  8*7   // f6
    SAVE_FPR ft7,  8*8   // f7
    SAVE_FPR fs0,  8*9   // f8
    SAVE_FPR fs1,  8*10  // f9
#define SAVE_EVERYTHING_FRAME_OFFSET_FA0 (8*11)
    SAVE_FPR fa0,  8*11  // f10, its offset must equal SAVE_EVERYTHING_FRAME_OFFSET_FA0
    SAVE_FPR fa1,  8*12  // f11
    SAVE_FPR fa2,  8*13  // f12
    SAVE_FPR fa3,  8*14  // f13
    SAVE_FPR fa4,  8*15  // f14
    SAVE_FPR fa5,  8*16  // f15
    SAVE_FPR fa6,  8*17  // f16
    SAVE_FPR fa7,  8*18  // f17
    SAVE_FPR fs2,  8*19  // f18
    SAVE_FPR fs3,  8*20  // f19
    SAVE_FPR fs4,  8*21  // f20
    SAVE_FPR fs5,  8*22  // f21
    SAVE_FPR fs6,  8*23  // f22
    SAVE_FPR fs7,  8*24  // f23
    SAVE_FPR fs8,  8*25  // f24
    SAVE_FPR fs9,  8*26  // f25
    SAVE_FPR fs10, 8*27  // f26
    SAVE_FPR fs11, 8*28  // f27
    SAVE_FPR ft8,  8*29  // f28
    SAVE_FPR ft9,  8*30  // f29
    SAVE_FPR ft10, 8*31  // f30
    SAVE_FPR ft11, 8*32  // f31

    // 27 slots for GPRs (excluded: zero/x0, sp/x2, gp/x3, tp/x4, s1/x9 -- the ART thread register)
    SAVE_GPR t0,  8*33  // x5
    SAVE_GPR t1,  8*34  // x6
    SAVE_GPR t2,  8*35  // x7
    SAVE_GPR s0,  8*36  // x8
#define SAVE_EVERYTHING_FRAME_OFFSET_A0 (8*37)
    SAVE_GPR a0,  8*37  // x10, its offset must equal SAVE_EVERYTHING_FRAME_OFFSET_A0
    SAVE_GPR a1,  8*38  // x11
    SAVE_GPR a2,  8*39  // x12
    SAVE_GPR a3,  8*40  // x13
    SAVE_GPR a4,  8*41  // x14
    SAVE_GPR a5,  8*42  // x15
    SAVE_GPR a6,  8*43  // x16
    SAVE_GPR a7,  8*44  // x17
    SAVE_GPR s2,  8*45  // x18
    SAVE_GPR s3,  8*46  // x19
    SAVE_GPR s4,  8*47  // x20
    SAVE_GPR s5,  8*48  // x21
    SAVE_GPR s6,  8*49  // x22
    SAVE_GPR s7,  8*50  // x23
    SAVE_GPR s8,  8*51  // x24
    SAVE_GPR s9,  8*52  // x25
    SAVE_GPR s10, 8*53  // x26
    SAVE_GPR s11, 8*54  // x27
    SAVE_GPR t3,  8*55  // x28
    SAVE_GPR t4,  8*56  // x29
    SAVE_GPR t5,  8*57  // x30
    SAVE_GPR t6,  8*58  // x31

    // RA already saved by the user of this macro.

    SETUP_CALLEE_SAVE_FRAME_COMMON t0, \runtime_method_offset
.endm


.macro SETUP_SAVE_EVERYTHING_FRAME runtime_method_offset = RUNTIME_SAVE_EVERYTHING_METHOD_OFFSET
#if (FRAME_SIZE_SAVE_EVERYTHING != 8*(1 + 32 + 27))
#error "FRAME_SIZE_SAVE_EVERYTHING(RISCV64) size not as expected."
#endif
    INCREASE_FRAME FRAME_SIZE_SAVE_EVERYTHING
    SAVE_GPR ra,  8*59  // x1, return address
    SETUP_SAVE_EVERYTHING_FRAME_DECREMENTED_SP_SKIP_RA \runtime_method_offset
.endm


.macro RESTORE_SAVE_EVERYTHING_FRAME load_a0 = 1
    // stack slot (8*0)(sp) is for ArtMethod*

    // 32 slots for FPRs
    RESTORE_FPR ft0,  (8*1)   // f0
    RESTORE_FPR ft1,  (8*2)   // f1
    RESTORE_FPR ft2,  (8*3)   // f2
    RESTORE_FPR ft3,  (8*4)   // f3
    RESTORE_FPR ft4,  (8*5)   // f4
    RESTORE_FPR ft5,  (8*6)   // f5
    RESTORE_FPR ft6,  (8*7)   // f6
    RESTORE_FPR ft7,  (8*8)   // f7
    RESTORE_FPR fs0,  (8*9)   // f8
    RESTORE_FPR fs1,  (8*10)  // f9
#if SAVE_EVERYTHING_FRAME_OFFSET_FA0 != (8*11)
#error "unexpected SAVE_EVERYTHING_FRAME_OFFSET_FA0"
#endif
    RESTORE_FPR fa0,  (8*11)  // f10, offset must equal SAVE_EVERYTHING_FRAME_OFFSET_FA0
    RESTORE_FPR fa1,  (8*12)  // f11
    RESTORE_FPR fa2,  (8*13)  // f12
    RESTORE_FPR fa3,  (8*14)  // f13
    RESTORE_FPR fa4,  (8*15)  // f14
    RESTORE_FPR fa5,  (8*16)  // f15
    RESTORE_FPR fa6,  (8*17)  // f16
    RESTORE_FPR fa7,  (8*18)  // f17
    RESTORE_FPR fs2,  (8*19)  // f18
    RESTORE_FPR fs3,  (8*20)  // f19
    RESTORE_FPR fs4,  (8*21)  // f20
    RESTORE_FPR fs5,  (8*22)  // f21
    RESTORE_FPR fs6,  (8*23)  // f22
    RESTORE_FPR fs7,  (8*24)  // f23
    RESTORE_FPR fs8,  (8*25)  // f24
    RESTORE_FPR fs9,  (8*26)  // f25
    RESTORE_FPR fs10, (8*27)  // f26
    RESTORE_FPR fs11, (8*28)  // f27
    RESTORE_FPR ft8,  (8*29)  // f28
    RESTORE_FPR ft9,  (8*30)  // f29
    RESTORE_FPR ft10, (8*31)  // f30
    RESTORE_FPR ft11, (8*32)  // f31

    // 26 slots for GPRs (excluded: zero/x0, sp/x2, gp/x3, tp/x4, s1/x9 -- the ART thread register)
    RESTORE_GPR t0,  (8*33)  // x5
    RESTORE_GPR t1,  (8*34)  // x6
    RESTORE_GPR t2,  (8*35)  // x7
    RESTORE_GPR s0,  (8*36)  // x8
#if SAVE_EVERYTHING_FRAME_OFFSET_A0 != (8*37)
#error "unexpected SAVE_EVERYTHING_FRAME_OFFSET_A0"
#endif
    .if \load_a0
    RESTORE_GPR a0,  (8*37)  // x10, offset must equal SAVE_EVERYTHING_FRAME_OFFSET_A0
    .endif
    RESTORE_GPR a1,  (8*38)  // x11
    RESTORE_GPR a2,  (8*39)  // x12
    RESTORE_GPR a3,  (8*40)  // x13
    RESTORE_GPR a4,  (8*41)  // x14
    RESTORE_GPR a5,  (8*42)  // x15
    RESTORE_GPR a6,  (8*43)  // x16
    RESTORE_GPR a7,  (8*44)  // x17
    RESTORE_GPR s2,  (8*45)  // x18
    RESTORE_GPR s3,  (8*46)  // x19
    RESTORE_GPR s4,  (8*47)  // x20
    RESTORE_GPR s5,  (8*48)  // x21
    RESTORE_GPR s6,  (8*49)  // x22
    RESTORE_GPR s7,  (8*50)  // x23
    RESTORE_GPR s8,  (8*51)  // x24
    RESTORE_GPR s9,  (8*52)  // x25
    RESTORE_GPR s10, (8*53)  // x26
    RESTORE_GPR s11, (8*54)  // x27
    RESTORE_GPR t3,  (8*55)  // x28
    RESTORE_GPR t4,  (8*56)  // x29
    RESTORE_GPR t5,  (8*57)  // x30
    RESTORE_GPR t6,  (8*58)  // x31

    RESTORE_GPR ra,  (8*59)  // x1, return address

    DECREASE_FRAME FRAME_SIZE_SAVE_EVERYTHING
.endm


// For compatibility with Runtime::CreateCalleeSaveMethod(kSaveRefsOnly).
.macro SETUP_SAVE_REFS_ONLY_FRAME
    INCREASE_FRAME FRAME_SIZE_SAVE_REFS_ONLY

    // stack slot (8*0)(sp) is for ArtMethod*
    // stack slot (8*1)(sp) is for padding
    SAVE_GPR s0,  (8*2)   // x8
    SAVE_GPR s2,  (8*3)   // x18
    SAVE_GPR s3,  (8*4)   // x19
    SAVE_GPR s4,  (8*5)   // x20
    SAVE_GPR s5,  (8*6)   // x21
    SAVE_GPR s6,  (8*7)   // x22
    SAVE_GPR s7,  (8*8)   // x23
    SAVE_GPR s8,  (8*9)   // x24
    SAVE_GPR s9,  (8*10)  // x25
    SAVE_GPR s10, (8*11)  // x26
    SAVE_GPR s11, (8*12)  // x27
    SAVE_GPR ra,  (8*13)  // x1

    SETUP_CALLEE_SAVE_FRAME_COMMON t0, RUNTIME_SAVE_REFS_ONLY_METHOD_OFFSET
.endm


.macro RESTORE_SAVE_REFS_ONLY_FRAME
    // stack slot (8*0)(sp) is for ArtMethod*
    // stack slot (8*1)(sp) is for padding
    RESTORE_GPR s0,  (8*2)   // x8
    RESTORE_GPR s2,  (8*3)   // x18
    RESTORE_GPR s3,  (8*4)   // x19
    RESTORE_GPR s4,  (8*5)   // x20
    RESTORE_GPR s5,  (8*6)   // x21
    RESTORE_GPR s6,  (8*7)   // x22
    RESTORE_GPR s7,  (8*8)   // x23
    RESTORE_GPR s8,  (8*9)   // x24
    RESTORE_GPR s9,  (8*10)  // x25
    RESTORE_GPR s10, (8*11)  // x26
    RESTORE_GPR s11, (8*12)  // x27
    RESTORE_GPR ra,  (8*13)  // x1

    DECREASE_FRAME FRAME_SIZE_SAVE_REFS_ONLY
.endm


// CFI note. This macro is used where the CFA rule is a dwarf expression, so adjustment of SP does
// not affect CFA computation. We also elide CFI descriptors for the argument registers, because
// they can be recovered from the stack in a debugging scenario.
.macro SPILL_ALL_ARGUMENTS
#if (FRAME_SIZE_SAVE_ARGS_ONLY != 128)
#error "FRAME_SIZE_SAVE_ARGS_ONLY(riscv64) not as expected."
#endif
    addi sp, sp, -FRAME_SIZE_SAVE_ARGS_ONLY
    sd a0,   (8*0)(sp)
    sd a1,   (8*1)(sp)
    sd a2,   (8*2)(sp)
    sd a3,   (8*3)(sp)
    sd a4,   (8*4)(sp)
    sd a5,   (8*5)(sp)
    sd a6,   (8*6)(sp)
    sd a7,   (8*7)(sp)
    fsd fa0, (8*8)(sp)
    fsd fa1, (8*9)(sp)
    fsd fa2, (8*10)(sp)
    fsd fa3, (8*11)(sp)
    fsd fa4, (8*12)(sp)
    fsd fa5, (8*13)(sp)
    fsd fa6, (8*14)(sp)
    fsd fa7, (8*15)(sp)
.endm


.macro RESTORE_ALL_ARGUMENTS
    ld a0,   (8*0)(sp)
    ld a1,   (8*1)(sp)
    ld a2,   (8*2)(sp)
    ld a3,   (8*3)(sp)
    ld a4,   (8*4)(sp)
    ld a5,   (8*5)(sp)
    ld a6,   (8*6)(sp)
    ld a7,   (8*7)(sp)
    fld fa0, (8*8)(sp)
    fld fa1, (8*9)(sp)
    fld fa2, (8*10)(sp)
    fld fa3, (8*11)(sp)
    fld fa4, (8*12)(sp)
    fld fa5, (8*13)(sp)
    fld fa6, (8*14)(sp)
    fld fa7, (8*15)(sp)
    addi sp, sp, FRAME_SIZE_SAVE_ARGS_ONLY
.endm


.macro SETUP_NTERP_SAVE_CALLEE_SAVES
#if (NTERP_SIZE_SAVE_CALLEE_SAVES != 8*(12 + 1 + 10 + 1))
#error "NTERP_SIZE_SAVE_CALLEE_SAVES(RISCV64) size not as expected."
#endif
    // FP callee-saves.
    SAVE_FPR fs0,  (8*0)   // f8
    SAVE_FPR fs1,  (8*1)   // f9
    SAVE_FPR fs2,  (8*2)   // f18
    SAVE_FPR fs3,  (8*3)   // f19
    SAVE_FPR fs4,  (8*4)   // f20
    SAVE_FPR fs5,  (8*5)   // f21
    SAVE_FPR fs6,  (8*6)   // f22
    SAVE_FPR fs7,  (8*7)   // f23
    SAVE_FPR fs8,  (8*8)   // f24
    SAVE_FPR fs9,  (8*9)   // f25
    SAVE_FPR fs10, (8*10)  // f26
    SAVE_FPR fs11, (8*11)  // f27

    // GP callee-saves
    SAVE_GPR s0,  (8*12)  // x8/fp, frame pointer
    // s1 (x9) is the ART thread register
    SAVE_GPR s2,  (8*13)  // x18
    SAVE_GPR s3,  (8*14)  // x19
    SAVE_GPR s4,  (8*15)  // x20
    SAVE_GPR s5,  (8*16)  // x21
    SAVE_GPR s6,  (8*17)  // x22
    SAVE_GPR s7,  (8*18)  // x23
    SAVE_GPR s8,  (8*19)  // x24
    SAVE_GPR s9,  (8*20)  // x25
    SAVE_GPR s10, (8*21)  // x26
    SAVE_GPR s11, (8*22)  // x27

    SAVE_GPR ra,  (8*23)  // x1, return address
.endm


.macro RESTORE_NTERP_SAVE_CALLEE_SAVES
#if (NTERP_SIZE_SAVE_CALLEE_SAVES != 8*(12 + 1 + 10 + 1))
#error "NTERP_SIZE_SAVE_CALLEE_SAVES(RISCV64) size not as expected."
#endif
    // FP callee-saves.
    RESTORE_FPR fs0,  (8*0)   // f8
    RESTORE_FPR fs1,  (8*1)   // f9
    RESTORE_FPR fs2,  (8*2)   // f18
    RESTORE_FPR fs3,  (8*3)   // f19
    RESTORE_FPR fs4,  (8*4)   // f20
    RESTORE_FPR fs5,  (8*5)   // f21
    RESTORE_FPR fs6,  (8*6)   // f22
    RESTORE_FPR fs7,  (8*7)   // f23
    RESTORE_FPR fs8,  (8*8)   // f24
    RESTORE_FPR fs9,  (8*9)   // f25
    RESTORE_FPR fs10, (8*10)  // f26
    RESTORE_FPR fs11, (8*11)  // f27

    // GP callee-saves
    RESTORE_GPR s0,  (8*12)  // x8/fp, frame pointer
    // s1 is the ART thread register
    RESTORE_GPR s2,  (8*13)  // x18
    RESTORE_GPR s3,  (8*14)  // x19
    RESTORE_GPR s4,  (8*15)  // x20
    RESTORE_GPR s5,  (8*16)  // x21
    RESTORE_GPR s6,  (8*17)  // x22
    RESTORE_GPR s7,  (8*18)  // x23
    RESTORE_GPR s8,  (8*19)  // x24
    RESTORE_GPR s9,  (8*20)  // x25
    RESTORE_GPR s10, (8*21)  // x26
    RESTORE_GPR s11, (8*22)  // x27

    RESTORE_GPR ra,  (8*23)  // x1, return address
.endm


// Macro that calls through to artDeliverPendingExceptionFromCode, where the pending exception is
// Thread::Current()->exception_ when the runtime method frame is ready.
.macro DELIVER_PENDING_EXCEPTION_FRAME_READY
    mv a0, xSELF
    call artDeliverPendingExceptionFromCode  // Point of no return.
    unimp                                    // Unreachable.
.endm


// Macro that calls through to artDeliverPendingExceptionFromCode, where the pending exception is
// Thread::Current()->exception_.
.macro DELIVER_PENDING_EXCEPTION
    SETUP_SAVE_ALL_CALLEE_SAVES_FRAME
    DELIVER_PENDING_EXCEPTION_FRAME_READY
.endm


.macro RETURN_OR_DELIVER_PENDING_EXCEPTION_REG reg
    ld \reg, THREAD_EXCEPTION_OFFSET(xSELF)
    bnez \reg, 1f
    ret
1:
    DELIVER_PENDING_EXCEPTION
.endm

// Macro to emit a single LUI to load the given value while checking that the low 12 bits are zero.
.macro LUI_VALUE reg, value
    .if (\value & 0xfff) != 0
    .error "Cannot use LUI to materialize a value with some of the low 12 bits set."
    .endif
    lui \reg, (\value) >> 12
.endm


// Locking is needed for both managed code and JNI stubs.
.macro LOCK_OBJECT_FAST_PATH obj, slow_lock, can_be_null
    // Use scratch registers T1-T6 as temporaries.
    // Note: T0 is used as the argument register for `art_jni_lock_object` and passed as `obj`.
    lw      t2, THREAD_ID_OFFSET(xSELF)
    .if \can_be_null
        beqz    \obj, \slow_lock
    .endif
    addi    t1, \obj, MIRROR_OBJECT_LOCK_WORD_OFFSET  // Exclusive load/store has no offset.
1:
    // Note: The LR/SC sequence must be at most 16 instructions, so we cannot have the
    // recursive locking in a slow-path as on other architectures.
    lr.w.aq t3, (t1)                  // Acquire needed only in most common case.
    LUI_VALUE t5, LOCK_WORD_GC_STATE_MASK_SHIFTED  // Prepare mask for testing non-gc bits.
    xor     t4, t3, t2                // Prepare the value to store if unlocked
                                      //   (thread id, count of 0 and preserved read barrier bits),
                                      // or prepare to compare thread id for recursive lock check
                                      //   (lock_word.ThreadId() ^ self->ThreadId()).
    or      t6, t5, t3                // Test the non-gc bits.
    beq     t6, t5, 2f                // Check if unlocked.
                                      // Check lock word state and thread id together,
    LUI_VALUE \
        t5, 0xffffffff ^ (LOCK_WORD_STATE_MASK_SHIFTED | LOCK_WORD_THIN_LOCK_OWNER_MASK_SHIFTED)
    or      t6, t5, t4
    bne     t6, t5, \slow_lock
    LUI_VALUE t4, LOCK_WORD_THIN_LOCK_COUNT_ONE  // Increment the recursive lock count.
    addw    t4, t3, t4
    LUI_VALUE t5, LOCK_WORD_THIN_LOCK_COUNT_MASK_SHIFTED  // Test the new thin lock count.
    and     t5, t4, t5
    beqz    t5, \slow_lock            // Zero as the new count indicates overflow, go slow path.
2:
    // Store the prepared value:
    //   - if unlocked, original lock word plus thread id,
    //   - if already locked, original lock word plus incremented lock count.
    sc.w    t3, t4, (t1)
    bnez    t3, 1b                    // If the store failed, retry.
    ret
.endm

// Unlocking is needed for both managed code and JNI stubs.
.macro UNLOCK_OBJECT_FAST_PATH obj, slow_unlock, can_be_null
    // Use scratch registers T1-T6 as temporaries.
    // Note: T0 is used as the argument register for `art_jni_unlock_object` and passed as `obj`.
    lw      t2, THREAD_ID_OFFSET(xSELF)
    .if \can_be_null
        beqz    \obj, \slow_unlock
    .endif
    addi    t1, \obj, MIRROR_OBJECT_LOCK_WORD_OFFSET  // Exclusive load/store has no offset.
1:
    // Note: Without read barriers, we could do plain LW here but there is no store-release
    // other than SC on riscv64, so we do this with LR/SC for all cofigurations.
    // Note: The LR/SC sequence must be at most 16 instructions, so we cannot have the
    // recursive unlocking in a slow-path as on other architectures.
    lr.w    t3, (t1)
    LUI_VALUE t5, LOCK_WORD_GC_STATE_MASK_SHIFTED  // Prepare mask for testing non-gc bits.
    xor     t4, t3, t2                // Prepare the value to store if simply locked
                                      //   (mostly 0s, and preserved read barrier bits),
                                      // or prepare to compare thread id for recursive lock check
                                      //   (lock_word.ThreadId() ^ self->ThreadId()).
    or      t6, t5, t4                // Test the non-gc bits.
    beq     t6, t5, 2f                // Simply locked by this thread?
                                      // Check lock word state and thread id together.
    LUI_VALUE \
        t5, 0xffffffff ^ (LOCK_WORD_STATE_MASK_SHIFTED | LOCK_WORD_THIN_LOCK_OWNER_MASK_SHIFTED)
    or      t6, t5, t4
    bne     t6, t5, \slow_unlock
    LUI_VALUE t4, LOCK_WORD_THIN_LOCK_COUNT_ONE  // Decrement the recursive lock count.
    subw    t4, t3, t4
2:
    // Store the prepared value:
    //   - if simply locked, original lock word with removed thread id,
    //   - if recursively locked, original lock word plus decremented lock count.
    sc.w.rl t3, t4, (t1)              // Need to use atomic instructions for read barrier.
    bnez    t3, 1b                    // If the store failed, retry.
    ret
.endm


// Macros to branch based on the value of a specific bit.
.macro BRANCH_IF_BIT_CLEAR tmp, reg, bit, dest
    slli    \tmp, \reg, (63 - \bit) // tested bit => sign bit
    bgez    \tmp, \dest
.endm


.macro BRANCH_IF_BIT_SET tmp, reg, bit, dest
    slli    \tmp, \reg, (63 - \bit) // tested bit => sign bit
    bltz    \tmp, \dest
.endm


#endif  // ART_RUNTIME_ARCH_RISCV64_ASM_SUPPORT_RISCV64_S_
