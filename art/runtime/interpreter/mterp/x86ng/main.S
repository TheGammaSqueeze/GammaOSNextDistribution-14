%def header():
/*
 * Copyright (C) 2021 The Android Open Source Project
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

/*
 * This is a #include, not a %include, because we want the C pre-processor
 * to expand the macros into assembler assignment statements.
 */
#include "asm_support.h"
#include "arch/x86/asm_support_x86.S"

/**
 * x86 ABI general notes:
 *
 * Caller save set:
 *      eax, ebx, edx, ecx, st(0)-st(7)
 * Callee save set:
 *      esi, edi, ebp
 * Return regs:
 *      32-bit in eax
 *      64-bit in edx:eax (low-order 32 in eax)
 *      fp on top of fp stack st(0)
 *
 * Stack must be 16-byte aligned to support SSE in native code.
 */

#define ARG3        %ebx
#define ARG2        %edx
#define ARG1        %ecx
#define ARG0        %eax

/*
 * single-purpose registers, given names for clarity
 */
#define rSELF    %fs
#define rPC      %esi
#define CFI_DEX  6  // DWARF register number of the register holding dex-pc (esi).
#define CFI_TMP  0  // DWARF register number of the first argument register (eax).
#define rFP      %edi
#define rINST    %ebx
#define rINSTw   %bx
#define rINSTbh  %bh
#define rINSTbl  %bl
#define rIBASE   %edx
#define rREFS    %ebp
#define CFI_REFS 5 // DWARF register number of the reference array (ebp).

// Temporary registers while setting up a frame.
#define rNEW_FP   %ecx
#define rNEW_REFS %eax
#define CFI_NEW_REFS 0

#define LOCAL0 4
#define LOCAL1 8
#define LOCAL2 12

/*
 * Get/set the 32-bit value from a Dalvik register.
 */
#define VREG_ADDRESS(_vreg) (rFP,_vreg,4)
#define VREG_HIGH_ADDRESS(_vreg) 4(rFP,_vreg,4)
#define VREG_REF_ADDRESS(_vreg) (rREFS,_vreg,4)
#define VREG_REF_HIGH_ADDRESS(_vreg) 4(rREFS,_vreg,4)

.macro GET_VREG _reg _vreg
    movl    VREG_ADDRESS(\_vreg), \_reg
.endm

.macro GET_VREG_OBJECT _reg _vreg
    movl    VREG_REF_ADDRESS(\_vreg), \_reg
.endm

/* Read wide value to xmm. */
.macro GET_WIDE_FP_VREG _reg _vreg
    movq    VREG_ADDRESS(\_vreg), \_reg
.endm

.macro SET_VREG _reg _vreg
    movl    \_reg, VREG_ADDRESS(\_vreg)
    movl    MACRO_LITERAL(0), VREG_REF_ADDRESS(\_vreg)
.endm

/* Write wide value from xmm. xmm is clobbered. */
.macro SET_WIDE_FP_VREG _reg _vreg
    movq    \_reg, VREG_ADDRESS(\_vreg)
    pxor    \_reg, \_reg
    movq    \_reg, VREG_REF_ADDRESS(\_vreg)
.endm

.macro SET_VREG_OBJECT _reg _vreg
    movl    \_reg, VREG_ADDRESS(\_vreg)
    movl    \_reg, VREG_REF_ADDRESS(\_vreg)
.endm

.macro GET_VREG_HIGH _reg _vreg
    movl    VREG_HIGH_ADDRESS(\_vreg), \_reg
.endm

.macro SET_VREG_HIGH _reg _vreg
    movl    \_reg, VREG_HIGH_ADDRESS(\_vreg)
    movl    MACRO_LITERAL(0), VREG_REF_HIGH_ADDRESS(\_vreg)
.endm

.macro CLEAR_REF _vreg
    movl    MACRO_LITERAL(0), VREG_REF_ADDRESS(\_vreg)
.endm

.macro CLEAR_WIDE_REF _vreg
    movl    MACRO_LITERAL(0), VREG_REF_ADDRESS(\_vreg)
    movl    MACRO_LITERAL(0), VREG_REF_HIGH_ADDRESS(\_vreg)
.endm

.macro GET_VREG_XMMs _xmmreg _vreg
    movss VREG_ADDRESS(\_vreg), \_xmmreg
.endm
.macro GET_VREG_XMMd _xmmreg _vreg
    movsd VREG_ADDRESS(\_vreg), \_xmmreg
.endm
.macro SET_VREG_XMMs _xmmreg _vreg
    movss \_xmmreg, VREG_ADDRESS(\_vreg)
.endm
.macro SET_VREG_XMMd _xmmreg _vreg
    movsd \_xmmreg, VREG_ADDRESS(\_vreg)
.endm

// Includes the return address implicitly pushed on stack by 'call'.
#define CALLEE_SAVES_SIZE (3 * 4 + 1 * 4)

#define PARAMETERS_SAVES_SIZE (4 * 4)

// +4 for the ArtMethod of the caller.
#define OFFSET_TO_FIRST_ARGUMENT_IN_STACK (CALLEE_SAVES_SIZE + PARAMETERS_SAVES_SIZE + 4)

/*
 * Refresh rINST.
 * At enter to handler rINST does not contain the opcode number.
 * However some utilities require the full value, so this macro
 * restores the opcode number.
 */
.macro REFRESH_INST _opnum
    movb    rINSTbl, rINSTbh
    movb    $$\_opnum, rINSTbl
.endm

/*
 * Fetch the next instruction from rPC into rINSTw.  Does not advance rPC.
 */
.macro FETCH_INST
    movzwl  (rPC), rINST
.endm

.macro FETCH_INST_CLEAR_OPCODE
    movzbl 1(rPC), rINST
.endm

/*
 * Remove opcode from rINST, compute the address of handler and jump to it.
 */
.macro GOTO_NEXT
    movzx   rINSTbl,%ecx
    movzbl  rINSTbh,rINST
    shll    MACRO_LITERAL(${handler_size_bits}), %ecx
    addl    rIBASE, %ecx
    jmp     *%ecx
.endm

/*
 * Advance rPC by instruction count.
 */
.macro ADVANCE_PC _count
    leal    2*\_count(rPC), rPC
.endm

/*
 * Advance rPC by instruction count, fetch instruction and jump to handler.
 */
.macro ADVANCE_PC_FETCH_AND_GOTO_NEXT _count
    ADVANCE_PC \_count
    FETCH_INST
    GOTO_NEXT
.endm

.macro NTERP_DEF_CFA cfi_reg
    CFI_DEF_CFA_BREG_PLUS_UCONST \cfi_reg, -4, CALLEE_SAVES_SIZE + PARAMETERS_SAVES_SIZE
.endm

.macro RESTORE_IBASE
    call 0f
0:
    popl rIBASE
    addl MACRO_LITERAL(SYMBOL(artNterpAsmInstructionStart) - 0b), rIBASE
.endm

.macro RESTORE_IBASE_WITH_CFA
    call 0f
0:
    CFI_ADJUST_CFA_OFFSET(4)
    popl rIBASE
    CFI_ADJUST_CFA_OFFSET(-4)
    addl MACRO_LITERAL(SYMBOL(artNterpAsmInstructionStart) - 0b), rIBASE
.endm

.macro SPILL_ALL_CORE_PARAMETERS
    PUSH_ARG eax
    PUSH_ARG ecx
    PUSH_ARG edx
    PUSH_ARG ebx
.endm

.macro RESTORE_ALL_CORE_PARAMETERS
    POP_ARG ebx
    POP_ARG edx
    POP_ARG ecx
    POP_ARG eax
.endm

.macro DROP_PARAMETERS_SAVES
    addl $$(PARAMETERS_SAVES_SIZE), %esp
.endm

.macro SAVE_WIDE_RETURN
    movl %edx, LOCAL2(%esp)
.endm

.macro LOAD_WIDE_RETURN reg
    movl LOCAL2(%esp), \reg
.endm

// An assembly entry for nterp.
.macro OAT_ENTRY name
    FUNCTION_TYPE(\name)
    ASM_HIDDEN SYMBOL(\name)
    .global SYMBOL(\name)
    .balign 16
SYMBOL(\name):
.endm

.macro ENTRY name
    .text
    ASM_HIDDEN SYMBOL(\name)
    .global SYMBOL(\name)
    FUNCTION_TYPE(\name)
SYMBOL(\name):
.endm

.macro END name
    SIZE(\name)
.endm

// Macro for defining entrypoints into runtime. We don't need to save registers
// (we're not holding references there), but there is no
// kDontSave runtime method. So just use the kSaveRefsOnly runtime method.
.macro NTERP_TRAMPOLINE name, helper
DEFINE_FUNCTION \name
  movd %ebx, %xmm0
  SETUP_SAVE_REFS_ONLY_FRAME ebx
  movd %xmm0, %ebx
  PUSH_ARG ebx
  PUSH_ARG edx
  PUSH_ARG ecx
  PUSH_ARG eax
  call \helper
  DECREASE_FRAME 16
  RESTORE_IBASE_WITH_CFA
  FETCH_INST_CLEAR_OPCODE
  RESTORE_SAVE_REFS_ONLY_FRAME
  cmpl LITERAL(0), %fs:THREAD_EXCEPTION_OFFSET
  jne nterp_deliver_pending_exception
  ret
END_FUNCTION \name
.endm

.macro CLEAR_VOLATILE_MARKER reg
  andl MACRO_LITERAL(-2), \reg
.endm

.macro EXPORT_PC
    movl    rPC, -8(rREFS)
.endm

.macro FETCH_PC
    movl    -8(rREFS), rPC
.endm


.macro BRANCH
    leal    (rPC, rINST, 2), rPC
    // Update method counter and do a suspend check if the branch is negative or zero.
    testl rINST, rINST
    jle 3f
2:
    FETCH_INST
    GOTO_NEXT
3:
    movl (%esp), %eax
    movzwl ART_METHOD_HOTNESS_COUNT_OFFSET(%eax), %ecx
#if (NTERP_HOTNESS_VALUE != 0)
#error Expected 0 for hotness value
#endif
    // If the counter is at zero, handle this in the runtime.
    testw %cx, %cx
    je NterpHandleHotnessOverflow
    // Update counter.
    addl $$-1, %ecx
    movw %cx, ART_METHOD_HOTNESS_COUNT_OFFSET(%eax)
    DO_SUSPEND_CHECK continue_label=2b
.endm

// Expects:
// - edx, and eax to be available.
// Outputs:
// - \registers contains the dex registers size
// - \outs contains the outs size
// - if load_ins is 1, \ins contains the ins
// - \code_item is replaced with a pointer to the instructions
.macro FETCH_CODE_ITEM_INFO code_item, registers, outs, ins, load_ins
    testl MACRO_LITERAL(1), \code_item
    je 5f
    andl $$-2, \code_item  // Remove the extra bit that marks it's a compact dex file.
    movzwl COMPACT_CODE_ITEM_FIELDS_OFFSET(\code_item), %edx
    movl %edx, \registers
    sarl $$COMPACT_CODE_ITEM_REGISTERS_SIZE_SHIFT, \registers
    andl $$0xf, \registers
    movl %edx, \outs
    sarl $$COMPACT_CODE_ITEM_OUTS_SIZE_SHIFT, \outs
    andl $$0xf, \outs
    .if \load_ins
    movl %edx, \ins
    sarl $$COMPACT_CODE_ITEM_INS_SIZE_SHIFT, \ins
    andl $$0xf, \ins
    .else
    movl %edx, %eax
    sarl $$COMPACT_CODE_ITEM_INS_SIZE_SHIFT, %eax
    andl $$0xf, %eax
    addl %eax, \registers
    .endif
    testw $$COMPACT_CODE_ITEM_REGISTERS_INS_OUTS_FLAGS, COMPACT_CODE_ITEM_FLAGS_OFFSET(\code_item)
    je 4f
    movl \code_item, %eax
    testw $$COMPACT_CODE_ITEM_INSNS_FLAG, COMPACT_CODE_ITEM_FLAGS_OFFSET(\code_item)
    je 1f
    subl $$4, %eax
1:
    testw $$COMPACT_CODE_ITEM_REGISTERS_FLAG, COMPACT_CODE_ITEM_FLAGS_OFFSET(\code_item)
    je 2f
    subl $$2, %eax
    movzwl (%eax), %edx
    addl %edx, \registers
2:
    testw $$COMPACT_CODE_ITEM_INS_FLAG, COMPACT_CODE_ITEM_FLAGS_OFFSET(\code_item)
    je 3f
    subl $$2, %eax
    movzwl (%eax), %edx
    .if \load_ins
    addl %edx, \ins
    .else
    addl %edx, \registers
    .endif
3:
    testw $$COMPACT_CODE_ITEM_OUTS_FLAG, COMPACT_CODE_ITEM_FLAGS_OFFSET(\code_item)
    je 4f
    subl $$2, %eax
    movzwl (%eax), %edx
    addl %edx, \outs
4:
    .if \load_ins
    addl \ins, \registers
    .endif
    addl $$COMPACT_CODE_ITEM_INSNS_OFFSET, \code_item
    jmp 6f
5:
    // Fetch dex register size.
    movzwl CODE_ITEM_REGISTERS_SIZE_OFFSET(\code_item), \registers
    // Fetch outs size.
    movzwl CODE_ITEM_OUTS_SIZE_OFFSET(\code_item), \outs
    .if \load_ins
    movzwl CODE_ITEM_INS_SIZE_OFFSET(\code_item), \ins
    .endif
    addl $$CODE_ITEM_INSNS_OFFSET, \code_item
6:
.endm

// Setup the stack to start executing the method. Expects:
// - eax, edx, and ebx to be available.
//
// Inputs
// - code_item: where the code item is
// - refs: register where the pointer to dex references will be
// - fp: register where the pointer to dex values will be
// - cfi_refs: CFI register number of refs
// - load_ins: whether to store the 'ins' value of the code item in esi
//
// Outputs
// - ebx contains the dex registers size
// - edx contains the old stack pointer.
// - \code_item is replace with a pointer to the instructions
// - if load_ins is 1, esi contains the ins
.macro SETUP_STACK_FRAME code_item, refs, fp, cfi_refs, load_ins
    FETCH_CODE_ITEM_INFO \code_item, %ebx, \refs, %esi, \load_ins

    movl $$3, %eax
    cmpl $$2, \refs
    cmovle %eax, \refs

    // Compute required frame size for dex registers: ((2 * ebx) + refs)
    leal (\refs, %ebx, 2), %edx
    sall $$2, %edx

    // Compute new stack pointer in fp: add 12 for saving the previous frame,
    // pc, and method being executed.
    leal -12(%esp), \fp
    subl %edx, \fp
    // Alignment
    andl $$-16, \fp

    // Now setup the stack pointer.
    movl %esp, %edx
    CFI_DEF_CFA_REGISTER(edx)
    movl \fp, %esp

    leal 12(%esp, \refs, 4), \refs
    leal (\refs, %ebx, 4), \fp

    // Save old stack pointer.
    movl %edx, -4(\refs)
    NTERP_DEF_CFA \cfi_refs

    // Save ArtMethod.
    movl 12(%edx), %eax
    movl %eax, (%esp)

    // Put nulls in reference frame.
    testl %ebx, %ebx
    je 2f
    movl \refs, %eax
1:
    movl $$0, (%eax)
    addl $$4, %eax
    cmpl %eax, \fp
    jne 1b
2:
.endm

// Puts the next floating point argument into the expected register,
// fetching values based on a non-range invoke.
// Uses eax as temporary.
//
// TODO: We could simplify a lot of code by loading the G argument into
// the "inst" register. Given that we enter the handler with "1(rPC)" in
// the rINST, we can just add rINST<<16 to the args and we don't even
// need to pass "arg_index" around.
.macro LOOP_OVER_SHORTY_LOADING_XMMS xmm_reg, inst, shorty, arg_index, finished
1: // LOOP
    movb (REG_VAR(shorty)), %al             // al := *shorty
    addl MACRO_LITERAL(1), REG_VAR(shorty)  // shorty++
    cmpb MACRO_LITERAL(0), %al              // if (al == '\0') goto finished
    je VAR(finished)
    cmpb MACRO_LITERAL(68), %al             // if (al == 'D') goto FOUND_DOUBLE
    je 2f
    cmpb MACRO_LITERAL(70), %al             // if (al == 'F') goto FOUND_FLOAT
    je 3f
    shrl MACRO_LITERAL(4), REG_VAR(inst)
    addl MACRO_LITERAL(1), REG_VAR(arg_index)
    //  Handle extra argument in arg array taken by a long.
    cmpb MACRO_LITERAL(74), %al   // if (al != 'J') goto LOOP
    jne 1b
    shrl MACRO_LITERAL(4), REG_VAR(inst)
    addl MACRO_LITERAL(1), REG_VAR(arg_index)
    jmp 1b                        // goto LOOP
2:  // FOUND_DOUBLE
    subl MACRO_LITERAL(8), %esp
    movl REG_VAR(inst), %eax
    andl MACRO_LITERAL(0xf), %eax
    GET_VREG %eax, %eax
    movl %eax, (%esp)
    shrl MACRO_LITERAL(4), REG_VAR(inst)
    addl MACRO_LITERAL(1), REG_VAR(arg_index)
    cmpl MACRO_LITERAL(4), REG_VAR(arg_index)
    je 5f
    movl REG_VAR(inst), %eax
    andl MACRO_LITERAL(0xf), %eax
    shrl MACRO_LITERAL(4), REG_VAR(inst)
    addl MACRO_LITERAL(1), REG_VAR(arg_index)
    jmp 6f
5:
    movzbl 1(rPC), %eax
    andl MACRO_LITERAL(0xf), %eax
6:
    GET_VREG %eax, %eax
    movl %eax, 4(%esp)
    movq (%esp), REG_VAR(xmm_reg)
    addl MACRO_LITERAL(8), %esp
    jmp 4f
3:  // FOUND_FLOAT
    cmpl MACRO_LITERAL(4), REG_VAR(arg_index)
    je 7f
    movl REG_VAR(inst), %eax
    andl MACRO_LITERAL(0xf), %eax
    shrl MACRO_LITERAL(4), REG_VAR(inst)
    addl MACRO_LITERAL(1), REG_VAR(arg_index)
    jmp 8f
7:
    movzbl 1(rPC), %eax
    andl MACRO_LITERAL(0xf), %eax
8:
    GET_VREG_XMMs REG_VAR(xmm_reg), %eax
4:
.endm

// Puts the next int/long/object argument in the expected register,
// fetching values based on a non-range invoke.
// Uses eax as temporary.
.macro LOOP_OVER_SHORTY_LOADING_GPRS gpr_reg, gpr_long_reg, inst, shorty, arg_index, finished, if_long, is_ebx
1: // LOOP
    movb (REG_VAR(shorty)), %al   // al := *shorty
    addl MACRO_LITERAL(1), REG_VAR(shorty)  // shorty++
    cmpb MACRO_LITERAL(0), %al    // if (al == '\0') goto finished
    je  VAR(finished)
    cmpb MACRO_LITERAL(74), %al   // if (al == 'J') goto FOUND_LONG
    je 2f
    cmpb MACRO_LITERAL(70), %al   // if (al == 'F') goto SKIP_FLOAT
    je 3f
    cmpb MACRO_LITERAL(68), %al   // if (al == 'D') goto SKIP_DOUBLE
    je 4f
    cmpl MACRO_LITERAL(4), REG_VAR(arg_index)
    je 7f
    movl REG_VAR(inst), %eax
    andl MACRO_LITERAL(0xf), %eax
    shrl MACRO_LITERAL(4), REG_VAR(inst)
    addl MACRO_LITERAL(1), REG_VAR(arg_index)
    jmp 8f
7:
    // Fetch PC
    movl LOCAL1(%esp), %eax
    movl -8(%eax), %eax
    movzbl 1(%eax), %eax
    andl MACRO_LITERAL(0xf), %eax
8:
    GET_VREG REG_VAR(gpr_reg), %eax
    jmp 5f
2:  // FOUND_LONG
    .if \is_ebx
    // Put back shorty and exit
    subl MACRO_LITERAL(1), REG_VAR(shorty)
    jmp 5f
    .else
    movl REG_VAR(inst), %eax
    andl MACRO_LITERAL(0xf), %eax
    GET_VREG REG_VAR(gpr_reg), %eax
    shrl MACRO_LITERAL(4), REG_VAR(inst)
    addl MACRO_LITERAL(1), REG_VAR(arg_index)
    cmpl MACRO_LITERAL(4), REG_VAR(arg_index)
    je 9f
    movl REG_VAR(inst), %eax
    andl MACRO_LITERAL(0xf), %eax
    shrl MACRO_LITERAL(4), REG_VAR(inst)
    addl MACRO_LITERAL(1), REG_VAR(arg_index)
    jmp 10f
9:
    // Fetch PC
    movl LOCAL1(%esp), %eax
    movl -8(%eax), %eax
    movzbl 1(%eax), %eax
    andl MACRO_LITERAL(0xf), %eax
10:
    GET_VREG REG_VAR(gpr_long_reg), %eax
    jmp \if_long
    .endif
3:  // SKIP_FLOAT
    shrl MACRO_LITERAL(4), REG_VAR(inst)
    addl MACRO_LITERAL(1), REG_VAR(arg_index)
    jmp 1b
4:  // SKIP_DOUBLE
    shrl MACRO_LITERAL(8), REG_VAR(inst)
    addl MACRO_LITERAL(2), REG_VAR(arg_index)
    jmp 1b
5:
.endm

// Puts the next int/long/object argument in the expected stack slot,
// fetching values based on a non-range invoke.
// Uses eax as temporary.
.macro LOOP_OVER_SHORTY_LOADING_INTS stack_offset, shorty, inst, arg_index, finished, is_string_init
1:  // LOOP
    movb (REG_VAR(shorty)), %al   // al := *shorty
    addl MACRO_LITERAL(1), REG_VAR(shorty)  // shorty++
    cmpb MACRO_LITERAL(0), %al    // if (al == '\0') goto finished
    je  VAR(finished)
    cmpb MACRO_LITERAL(74), %al   // if (al == 'J') goto FOUND_LONG
    je 2f
    cmpb MACRO_LITERAL(70), %al   // if (al == 'F') goto SKIP_FLOAT
    je 3f
    cmpb MACRO_LITERAL(68), %al   // if (al == 'D') goto SKIP_DOUBLE
    je 4f
    .if \is_string_init
    cmpl MACRO_LITERAL(3), REG_VAR(arg_index)
    .else
    cmpl MACRO_LITERAL(4), REG_VAR(arg_index)
    .endif
    je 7f
    movl REG_VAR(inst), %eax
    andl MACRO_LITERAL(0xf), %eax
    shrl MACRO_LITERAL(4), REG_VAR(inst)
    jmp 8f
7:
    // Fetch PC.
    movl (LOCAL1 + \stack_offset)(%esp), %eax
    movl -8(%eax), %eax
    movzbl 1(%eax), %eax
    andl MACRO_LITERAL(0xf), %eax
8:
    GET_VREG %eax, %eax
    // Add 4 for the ArtMethod.
    movl %eax, (4 + \stack_offset)(%esp, REG_VAR(arg_index), 4)
    addl MACRO_LITERAL(1), REG_VAR(arg_index)
    jmp 1b
2:  // FOUND_LONG
    movl REG_VAR(inst), %eax
    andl MACRO_LITERAL(0xf), %eax
    GET_VREG %eax, %eax
    // Add 4 for the ArtMethod.
    movl %eax, (4 + \stack_offset)(%esp, REG_VAR(arg_index), 4)
    shrl MACRO_LITERAL(4), REG_VAR(inst)
    addl MACRO_LITERAL(1), REG_VAR(arg_index)
    .if \is_string_init
    cmpl MACRO_LITERAL(3), REG_VAR(arg_index)
    .else
    cmpl MACRO_LITERAL(4), REG_VAR(arg_index)
    .endif
    je 9f
    movl REG_VAR(inst), %eax
    andl MACRO_LITERAL(0xf), %eax
    shrl MACRO_LITERAL(4), REG_VAR(inst)
    jmp 10f
9:
    // Fetch PC.
    movl (LOCAL1 + \stack_offset)(%esp), %eax
    movl -8(%eax), %eax
    movzbl 1(%eax), %eax
    andl MACRO_LITERAL(0xf), %eax
10:
    GET_VREG %eax, %eax
    // +4 for the ArtMethod.
    movl %eax, (4 + \stack_offset)(%esp, REG_VAR(arg_index), 4)
    addl MACRO_LITERAL(1), REG_VAR(arg_index)
    jmp 1b
3:  // SKIP_FLOAT
    shrl MACRO_LITERAL(4), REG_VAR(inst)
    addl MACRO_LITERAL(1), REG_VAR(arg_index)
    jmp 1b
4:  // SKIP_DOUBLE
    shrl MACRO_LITERAL(8), REG_VAR(inst)
    addl MACRO_LITERAL(2), REG_VAR(arg_index)
    jmp 1b
.endm

// Puts the next floating point argument into the expected register,
// fetching values based on a range invoke.
// Uses eax as temporary.
.macro LOOP_RANGE_OVER_SHORTY_LOADING_XMMS xmm_reg, shorty, arg_index, stack_index, finished
1: // LOOP
    movb (REG_VAR(shorty)), %al             // al := *shorty
    addl MACRO_LITERAL(1), REG_VAR(shorty)  // shorty++
    cmpb MACRO_LITERAL(0), %al              // if (al == '\0') goto finished
    je VAR(finished)
    cmpb MACRO_LITERAL(68), %al             // if (al == 'D') goto FOUND_DOUBLE
    je 2f
    cmpb MACRO_LITERAL(70), %al             // if (al == 'F') goto FOUND_FLOAT
    je 3f
    addl MACRO_LITERAL(1), REG_VAR(arg_index)
    addl MACRO_LITERAL(1), REG_VAR(stack_index)
    //  Handle extra argument in arg array taken by a long.
    cmpb MACRO_LITERAL(74), %al   // if (al != 'J') goto LOOP
    jne 1b
    addl MACRO_LITERAL(1), REG_VAR(arg_index)
    addl MACRO_LITERAL(1), REG_VAR(stack_index)
    jmp 1b                        // goto LOOP
2:  // FOUND_DOUBLE
    GET_VREG_XMMd REG_VAR(xmm_reg), REG_VAR(arg_index)
    addl MACRO_LITERAL(2), REG_VAR(arg_index)
    addl MACRO_LITERAL(2), REG_VAR(stack_index)
    jmp 4f
3:  // FOUND_FLOAT
    GET_VREG_XMMs REG_VAR(xmm_reg), REG_VAR(arg_index)
    addl MACRO_LITERAL(1), REG_VAR(arg_index)
    add MACRO_LITERAL(1), REG_VAR(stack_index)
4:
.endm

// Puts the next floating point argument into the expected stack slot,
// fetching values based on a range invoke.
// Uses eax as temporary.
//
// TODO: We could just copy all the vregs to the stack slots in a simple loop
// (or REP MOVSD) without looking at the shorty at all. (We could also drop
// the "stack_index" from the macros for loading registers.) We could also do
// that conditionally if argument word count > 3; otherwise we know that all
// args fit into registers.
.macro LOOP_RANGE_OVER_FPs shorty, arg_index, stack_index, finished
1: // LOOP
    movb (REG_VAR(shorty)), %al             // bl := *shorty
    addl MACRO_LITERAL(1), REG_VAR(shorty)  // shorty++
    cmpb MACRO_LITERAL(0), %al              // if (al == '\0') goto finished
    je VAR(finished)
    cmpb MACRO_LITERAL(68), %al             // if (al == 'D') goto FOUND_DOUBLE
    je 2f
    cmpb MACRO_LITERAL(70), %al             // if (al == 'F') goto FOUND_FLOAT
    je 3f
    addl MACRO_LITERAL(1), REG_VAR(arg_index)
    addl MACRO_LITERAL(1), REG_VAR(stack_index)
    //  Handle extra argument in arg array taken by a long.
    cmpb MACRO_LITERAL(74), %al   // if (al != 'J') goto LOOP
    jne 1b
    addl MACRO_LITERAL(1), REG_VAR(arg_index)
    addl MACRO_LITERAL(1), REG_VAR(stack_index)
    jmp 1b                        // goto LOOP
2:  // FOUND_DOUBLE
    movq (rFP, REG_VAR(arg_index), 4), %xmm4
    movq %xmm4, 4(%esp, REG_VAR(stack_index), 4)
    addl MACRO_LITERAL(2), REG_VAR(arg_index)
    addl MACRO_LITERAL(2), REG_VAR(stack_index)
    jmp 1b
3:  // FOUND_FLOAT
    movl (rFP, REG_VAR(arg_index), 4), %eax
    movl %eax, 4(%esp, REG_VAR(stack_index), 4)
    addl MACRO_LITERAL(1), REG_VAR(arg_index)
    addl MACRO_LITERAL(1), REG_VAR(stack_index)
    jmp 1b
.endm

// Puts the next int/long/object argument in the expected register,
// fetching values based on a range invoke.
// Uses eax as temporary.
.macro LOOP_RANGE_OVER_SHORTY_LOADING_GPRS gpr_reg, gpr_long_reg, shorty, arg_index, stack_index, finished, if_long, is_ebx
1: // LOOP
    movb (REG_VAR(shorty)), %al             // al := *shorty
    addl MACRO_LITERAL(1), REG_VAR(shorty)  // shorty++
    cmpb MACRO_LITERAL(0), %al    // if (al == '\0') goto finished
    je VAR(finished)
    cmpb MACRO_LITERAL(74), %al   // if (al == 'J') goto FOUND_LONG
    je 2f
    cmpb MACRO_LITERAL(70), %al   // if (al == 'F') goto SKIP_FLOAT
    je 3f
    cmpb MACRO_LITERAL(68), %al   // if (al == 'D') goto SKIP_DOUBLE
    je 4f
    movl       (rFP, REG_VAR(arg_index), 4), REG_VAR(gpr_reg)
    addl MACRO_LITERAL(1), REG_VAR(arg_index)
    addl MACRO_LITERAL(1), REG_VAR(stack_index)
    jmp 5f
2:  // FOUND_LONG
    .if \is_ebx
    // Put back shorty and exit
    subl MACRO_LITERAL(1), REG_VAR(shorty)
    .else
    movl (rFP, REG_VAR(arg_index), 4), REG_VAR(gpr_reg)
    movl 4(rFP, REG_VAR(arg_index), 4), REG_VAR(gpr_long_reg)
    addl MACRO_LITERAL(2), REG_VAR(arg_index)
    addl MACRO_LITERAL(2), REG_VAR(stack_index)
    .endif
    jmp \if_long
3:  // SKIP_FLOAT
    addl MACRO_LITERAL(1), REG_VAR(arg_index)
    addl MACRO_LITERAL(1), REG_VAR(stack_index)
    jmp 1b
4:  // SKIP_DOUBLE
    addl MACRO_LITERAL(2), REG_VAR(arg_index)
    addl MACRO_LITERAL(2), REG_VAR(stack_index)
    jmp 1b
5:
.endm

// Puts the next int/long/object argument in the expected stack slot,
// fetching values based on a range invoke.
// Uses eax as temporary.
.macro LOOP_RANGE_OVER_INTs offset, shorty, arg_index, stack_index, finished
1: // LOOP
    movb (REG_VAR(shorty)), %al             // al := *shorty
    addl MACRO_LITERAL(1), REG_VAR(shorty)  // shorty++
    cmpb MACRO_LITERAL(0), %al    // if (al == '\0') goto finished
    je  VAR(finished)
    cmpb MACRO_LITERAL(74), %al   // if (al == 'J') goto FOUND_LONG
    je 2f
    cmpb MACRO_LITERAL(70), %al   // if (al == 'F') goto SKIP_FLOAT
    je 3f
    cmpb MACRO_LITERAL(68), %al   // if (al == 'D') goto SKIP_DOUBLE
    je 4f
    movl (rFP, REG_VAR(arg_index), 4), %eax
    // Add 4 for the ArtMethod.
    movl %eax, (4 + \offset)(%esp, REG_VAR(stack_index), 4)
3:  // SKIP_FLOAT
    addl MACRO_LITERAL(1), REG_VAR(arg_index)
    addl MACRO_LITERAL(1), REG_VAR(stack_index)
    jmp 1b
2:  // FOUND_LONG
    movl (rFP, REG_VAR(arg_index), 4), %eax
    // Add 4 for the ArtMethod.
    movl %eax, (4 + \offset)(%esp, REG_VAR(stack_index), 4)
    movl 4(rFP, REG_VAR(arg_index), 4), %eax
    // Add 4 for the ArtMethod and 4 for other half.
    movl %eax, (4 + 4 + \offset)(%esp, REG_VAR(stack_index), 4)
4:  // SKIP_DOUBLE
    addl MACRO_LITERAL(2), REG_VAR(arg_index)
    addl MACRO_LITERAL(2), REG_VAR(stack_index)
    jmp 1b
.endm

// Puts the next floating point parameter passed in physical register
// in the expected dex register array entry.
// Uses eax as temporary.
.macro LOOP_OVER_SHORTY_STORING_XMMS xmm_reg, shorty, arg_index, fp, finished
1: // LOOP
    movb (REG_VAR(shorty)), %al             // al := *shorty
    addl MACRO_LITERAL(1), REG_VAR(shorty)  // shorty++
    cmpb MACRO_LITERAL(0), %al              // if (al == '\0') goto finished
    je VAR(finished)
    cmpb MACRO_LITERAL(68), %al             // if (al == 'D') goto FOUND_DOUBLE
    je 2f
    cmpb MACRO_LITERAL(70), %al             // if (al == 'F') goto FOUND_FLOAT
    je 3f
    addl MACRO_LITERAL(1), REG_VAR(arg_index)
    //  Handle extra argument in arg array taken by a long.
    cmpb MACRO_LITERAL(74), %al   // if (al != 'J') goto LOOP
    jne 1b
    addl MACRO_LITERAL(1), REG_VAR(arg_index)
    jmp 1b                        // goto LOOP
2:  // FOUND_DOUBLE
    movq REG_VAR(xmm_reg),(REG_VAR(fp), REG_VAR(arg_index), 4)
    addl MACRO_LITERAL(2), REG_VAR(arg_index)
    jmp 4f
3:  // FOUND_FLOAT
    movss REG_VAR(xmm_reg), (REG_VAR(fp), REG_VAR(arg_index), 4)
    addl MACRO_LITERAL(1), REG_VAR(arg_index)
4:
.endm

// Puts the next int/long/object parameter passed in physical register
// in the expected dex register array entry, and in case of object in the
// expected reference array entry.
// Uses eax as temporary.
.macro LOOP_OVER_SHORTY_STORING_GPRS offset, offset_long, stack_ptr, shorty, arg_index, regs, refs, finished, if_long, is_ebx
1: // LOOP
    movb (REG_VAR(shorty)), %al             // al := *shorty
    addl MACRO_LITERAL(1), REG_VAR(shorty)  // shorty++
    cmpb MACRO_LITERAL(0), %al    // if (al == '\0') goto finished
    je  VAR(finished)
    cmpb MACRO_LITERAL(74), %al   // if (al == 'J') goto FOUND_LONG
    je 2f
    cmpb MACRO_LITERAL(70), %al   // if (al == 'F') goto SKIP_FLOAT
    je 3f
    cmpb MACRO_LITERAL(68), %al   // if (al == 'D') goto SKIP_DOUBLE
    je 4f
    cmpb MACRO_LITERAL(76), %al   // if (al != 'L') goto NOT_REFERENCE
    jne 6f
    movl \offset(REG_VAR(stack_ptr)), %eax
    movl %eax, (REG_VAR(regs), REG_VAR(arg_index), 4)
    movl %eax, (REG_VAR(refs), REG_VAR(arg_index), 4)
    addl MACRO_LITERAL(1), REG_VAR(arg_index)
    jmp 5f
2:  // FOUND_LONG
    .if \is_ebx
    // Put back shorty and jump to \if_long
    subl MACRO_LITERAL(1), REG_VAR(shorty)
    .else
    movl \offset(REG_VAR(stack_ptr)), %eax
    movl %eax, (REG_VAR(regs), REG_VAR(arg_index), 4)
    movl \offset_long(REG_VAR(stack_ptr)), %eax
    movl %eax, 4(REG_VAR(regs), REG_VAR(arg_index), 4)
    addl MACRO_LITERAL(2), REG_VAR(arg_index)
    .endif
    jmp \if_long
3:  // SKIP_FLOAT
    addl MACRO_LITERAL(1), REG_VAR(arg_index)
    jmp 1b
4:  // SKIP_DOUBLE
    addl MACRO_LITERAL(2), REG_VAR(arg_index)
    jmp 1b
6:  // NOT_REFERENCE
    movl \offset(REG_VAR(stack_ptr)), %eax
    movl %eax, (REG_VAR(regs), REG_VAR(arg_index), 4)
    addl MACRO_LITERAL(1), REG_VAR(arg_index)
5:
.endm

// Puts the next floating point parameter passed in stack
// in the expected dex register array entry.
// Uses eax as temporary.
//
// TODO: Or we could just spill regs to the reserved slots in the caller's
// frame and copy all regs in a simple loop. This time, however, we would
// need to look at the shorty anyway to look for the references.
// (The trade-off is different for passing arguments and receiving them.)
.macro LOOP_OVER_FPs shorty, arg_index, regs, stack_ptr, finished
1: // LOOP
    movb (REG_VAR(shorty)), %al             // al := *shorty
    addl MACRO_LITERAL(1), REG_VAR(shorty)  // shorty++
    cmpb MACRO_LITERAL(0), %al              // if (al == '\0') goto finished
    je VAR(finished)
    cmpb MACRO_LITERAL(68), %al             // if (al == 'D') goto FOUND_DOUBLE
    je 2f
    cmpb MACRO_LITERAL(70), %al             // if (al == 'F') goto FOUND_FLOAT
    je 3f
    addl MACRO_LITERAL(1), REG_VAR(arg_index)
    //  Handle extra argument in arg array taken by a long.
    cmpb MACRO_LITERAL(74), %al   // if (al != 'J') goto LOOP
    jne 1b
    addl MACRO_LITERAL(1), REG_VAR(arg_index)
    jmp 1b                        // goto LOOP
2:  // FOUND_DOUBLE
    movq OFFSET_TO_FIRST_ARGUMENT_IN_STACK(REG_VAR(stack_ptr), REG_VAR(arg_index), 4), %xmm4
    movq %xmm4, (REG_VAR(regs), REG_VAR(arg_index), 4)
    addl MACRO_LITERAL(2), REG_VAR(arg_index)
    jmp 1b
3:  // FOUND_FLOAT
    movl OFFSET_TO_FIRST_ARGUMENT_IN_STACK(REG_VAR(stack_ptr), REG_VAR(arg_index), 4), %eax
    movl %eax, (REG_VAR(regs), REG_VAR(arg_index), 4)
    addl MACRO_LITERAL(1), REG_VAR(arg_index)
    jmp 1b
.endm

// Puts the next int/long/object parameter passed in stack
// in the expected dex register array entry, and in case of object in the
// expected reference array entry.
// Uses eax as temporary.
.macro LOOP_OVER_INTs shorty, arg_index, regs, refs, stack_ptr, finished
1: // LOOP
    movb (REG_VAR(shorty)), %al             // al := *shorty
    addl MACRO_LITERAL(1), REG_VAR(shorty)  // shorty++
    cmpb MACRO_LITERAL(0), %al    // if (al == '\0') goto finished
    je  VAR(finished)
    cmpb MACRO_LITERAL(74), %al   // if (al == 'J') goto FOUND_LONG
    je 2f
    cmpb MACRO_LITERAL(76), %al   // if (al == 'L') goto FOUND_REFERENCE
    je 6f
    cmpb MACRO_LITERAL(70), %al   // if (al == 'F') goto SKIP_FLOAT
    je 3f
    cmpb MACRO_LITERAL(68), %al   // if (al == 'D') goto SKIP_DOUBLE
    je 4f
    movl OFFSET_TO_FIRST_ARGUMENT_IN_STACK(REG_VAR(stack_ptr), REG_VAR(arg_index), 4), %eax
    movl %eax, (REG_VAR(regs), REG_VAR(arg_index), 4)
    addl MACRO_LITERAL(1), REG_VAR(arg_index)
    jmp 1b
6:  // FOUND_REFERENCE
    movl OFFSET_TO_FIRST_ARGUMENT_IN_STACK(REG_VAR(stack_ptr), REG_VAR(arg_index), 4), %eax
    movl %eax, (REG_VAR(regs), REG_VAR(arg_index), 4)
    movl %eax, (REG_VAR(refs), REG_VAR(arg_index), 4)
3:  // SKIP_FLOAT
    addl MACRO_LITERAL(1), REG_VAR(arg_index)
    jmp 1b
2:  // FOUND_LONG
    movl OFFSET_TO_FIRST_ARGUMENT_IN_STACK(REG_VAR(stack_ptr), REG_VAR(arg_index), 4), %eax
    movl %eax, (REG_VAR(regs), REG_VAR(arg_index), 4)
    movl (OFFSET_TO_FIRST_ARGUMENT_IN_STACK+4)(REG_VAR(stack_ptr), REG_VAR(arg_index), 4), %eax
    movl %eax, 4(REG_VAR(regs), REG_VAR(arg_index), 4)
4:  // SKIP_DOUBLE
    addl MACRO_LITERAL(2), REG_VAR(arg_index)
    jmp 1b
.endm

// Increase method hotness and do suspend check before starting executing the method.
.macro START_EXECUTING_INSTRUCTIONS
   movl (%esp), %eax
   movzwl ART_METHOD_HOTNESS_COUNT_OFFSET(%eax), %ecx
#if (NTERP_HOTNESS_VALUE != 0)
#error Expected 0 for hotness value
#endif
   // If the counter is at zero, handle this in the runtime.
   testl %ecx, %ecx
   je 3f
   // Update counter.
   addl $$-1, %ecx
   movw %cx, ART_METHOD_HOTNESS_COUNT_OFFSET(%eax)
1:
   testl $$(THREAD_SUSPEND_OR_CHECKPOINT_REQUEST), rSELF:THREAD_FLAGS_OFFSET
   jz 2f
   EXPORT_PC
   call SYMBOL(art_quick_test_suspend)
   RESTORE_IBASE
2:
   FETCH_INST
   GOTO_NEXT
3:
   CHECK_AND_UPDATE_SHARED_MEMORY_METHOD if_hot=4f, if_not_hot=1b
4:
   movl $$0, ARG1
   movl rFP, ARG2
   call nterp_hot_method
   jmp 2b
.endm

.macro SPILL_ALL_CALLEE_SAVES
    PUSH edi
    PUSH esi
    PUSH ebp
.endm

.macro RESTORE_ALL_CALLEE_SAVES
    POP ebp
    POP esi
    POP edi
.endm

.macro GET_SHORTY dest, is_interface, is_polymorphic, is_custom
   // Save eax (ArtMethod), ecx (potential this).
   push %eax
   push %ecx
   .if \is_polymorphic
   push rPC
   push 12(%esp)
   call SYMBOL(NterpGetShortyFromInvokePolymorphic)
   addl MACRO_LITERAL(8), %esp
   .elseif \is_custom
   push rPC
   push 12(%esp)
   call SYMBOL(NterpGetShortyFromInvokeCustom)
   addl MACRO_LITERAL(8), %esp
   .elseif \is_interface
   subl MACRO_LITERAL(16), %esp
   // Save interface method.
   movss %xmm7, (%esp)
   movzwl 2(rPC), %eax
   pushl %eax
   // Caller is at 8 (saved ArtMethod + ecx) + 16 + 4 (second argument)
   pushl 28(%esp)
   call SYMBOL(NterpGetShortyFromMethodId)
   // Restore interface method.
   movss 8(%esp), %xmm7
   addl MACRO_LITERAL(24), %esp
   .else
   subl MACRO_LITERAL(4), %esp  // Alignment
   push %eax
   call SYMBOL(NterpGetShorty)
   addl MACRO_LITERAL(8), %esp
   .endif
   movl %eax, \dest
   pop %ecx
   pop %eax
.endm

.macro GET_SHORTY_SLOW_PATH dest, is_interface
   // Save all registers that can hold arguments in the fast path.
   pushl %eax
   pushl %ecx
   pushl %edx
   subl MACRO_LITERAL(4), %esp
   movss %xmm0, (%esp)
   .if \is_interface
   // Alignment.
   subl MACRO_LITERAL(8), %esp
   movzwl 2(rPC), %eax
   pushl %eax
   // Caller is at 16 (parameters) + 8 (alignment) + 4 (second argument).
   pushl 28(%esp)
   call SYMBOL(NterpGetShortyFromMethodId)
   movl %eax, \dest
   movss 16(%esp), %xmm0
   addl MACRO_LITERAL(20), %esp
   .else
   // Alignment.
   subl MACRO_LITERAL(12), %esp
   pushl %eax
   call SYMBOL(NterpGetShorty)
   movl %eax, \dest
   movss 16(%esp), %xmm0
   addl MACRO_LITERAL(20), %esp
   .endif
   popl %edx
   popl %ecx
   popl %eax
.endm

// Uses ecx and edx as temporary
.macro UPDATE_REGISTERS_FOR_STRING_INIT old_value, new_value
   movl rREFS, %edx
   movl rFP, %ecx
1:
   cmpl (%edx), \old_value
   jne 2f
   movl \new_value, (%edx)
   movl \new_value, (%ecx)
2:
   addl $$4, %edx
   addl $$4, %ecx
   cmpl %edx, rFP
   jne 1b
.endm

.macro DO_CALL is_polymorphic, is_custom
   .if \is_polymorphic
   call SYMBOL(art_quick_invoke_polymorphic)
   .elseif \is_custom
   call SYMBOL(art_quick_invoke_custom)
   .else
   call *ART_METHOD_QUICK_CODE_OFFSET_32(%eax)
   .endif
.endm

.macro COMMON_INVOKE_NON_RANGE is_static=0, is_interface=0, suffix="", is_string_init=0, is_polymorphic=0, is_custom=0
   .if \is_polymorphic
   // No fast path for polymorphic calls.
   .elseif \is_custom
   // No fast path for custom calls.
   .elseif \is_string_init
   // No fast path for string.init.
   .else
     testl $$ART_METHOD_NTERP_INVOKE_FAST_PATH_FLAG, ART_METHOD_ACCESS_FLAGS_OFFSET(%eax)
     je .Lfast_path_with_few_args_\suffix
     movzbl 1(rPC), %edx
     movl %edx, %ebx
     shrl MACRO_LITERAL(4), %ebx # Number of arguments
     .if \is_static
     jz .Linvoke_fast_path_\suffix  # shl sets the Z flag
     .else
     cmpl MACRO_LITERAL(1), %ebx
     je .Linvoke_fast_path_\suffix
     .endif
     movzwl 4(rPC), %esi
     cmpl MACRO_LITERAL(2), %ebx
     .if \is_static
     jl .Lone_arg_fast_path_\suffix
     .endif
     je .Ltwo_args_fast_path_\suffix
     cmpl MACRO_LITERAL(4), %ebx
     jl .Lthree_args_fast_path_\suffix
     je .Lfour_args_fast_path_\suffix

     andl        MACRO_LITERAL(0xf), %edx
     GET_VREG    %edx, %edx
     movl        %edx, (4 + 4 * 4)(%esp)
.Lfour_args_fast_path_\suffix:
     movl        %esi, %edx
     shrl        MACRO_LITERAL(12), %edx
     GET_VREG    %edx, %edx
     movl        %edx, (4 + 3 * 4)(%esp)
.Lthree_args_fast_path_\suffix:
     movl        %esi, %ebx
     shrl        MACRO_LITERAL(8), %ebx
     andl        MACRO_LITERAL(0xf), %ebx
     GET_VREG    %ebx, %ebx
.Ltwo_args_fast_path_\suffix:
     movl        %esi, %edx
     shrl        MACRO_LITERAL(4), %edx
     andl        MACRO_LITERAL(0xf), %edx
     GET_VREG    %edx, %edx
.Lone_arg_fast_path_\suffix:
     .if \is_static
     andl        MACRO_LITERAL(0xf), %esi
     GET_VREG    %ecx, %esi
     .else
     // First argument already in %ecx.
     .endif
.Linvoke_fast_path_\suffix:
     // Fetch PC before calling for proper stack unwinding.
     FETCH_PC
     call *ART_METHOD_QUICK_CODE_OFFSET_32(%eax) // Call the method.
     // In case of a long return, save the high half into LOCAL0
     SAVE_WIDE_RETURN
     RESTORE_IBASE
     ADVANCE_PC_FETCH_AND_GOTO_NEXT 3

.Lfast_path_with_few_args_\suffix:
     // Fast path when we have zero or one argument (modulo 'this'). If there
     // is one argument, we can put it in both floating point and core register.
     movzbl 1(rPC), %edx
     shrl MACRO_LITERAL(4), %edx # Number of arguments
     .if \is_static
     cmpl MACRO_LITERAL(1), %edx
     jl .Linvoke_with_few_args_\suffix
     jne .Lget_shorty_\suffix
     movzwl 4(rPC), %ecx
     andl MACRO_LITERAL(0xf), %ecx  // dex register of first argument
     GET_VREG %ecx, %ecx
     movd %ecx, %xmm0
     .else
     cmpl MACRO_LITERAL(2), %edx
     jl .Linvoke_with_few_args_\suffix
     jne .Lget_shorty_\suffix
     movzwl 4(rPC), %edx
     shrl MACRO_LITERAL(4), %edx
     andl MACRO_LITERAL(0xf), %edx  // dex register of second argument
     GET_VREG %edx, %edx
     movd %edx, %xmm0
     .endif
.Linvoke_with_few_args_\suffix:
     // Check if the next instruction is move-result or move-result-wide.
     // If it is, we fetch the shorty and jump to the regular invocation.
     movzwl  6(rPC), %ebx
     andl MACRO_LITERAL(0xfe), %ebx
     cmpl MACRO_LITERAL(0x0a), %ebx
     je .Lget_shorty_and_invoke_\suffix
     call *ART_METHOD_QUICK_CODE_OFFSET_32(%eax) // Call the method.
     RESTORE_IBASE
     ADVANCE_PC_FETCH_AND_GOTO_NEXT 3
.Lget_shorty_and_invoke_\suffix:
     GET_SHORTY_SLOW_PATH %esi, \is_interface
     jmp .Lgpr_setup_finished_\suffix
   .endif

.Lget_shorty_\suffix:
   GET_SHORTY %ebx, \is_interface, \is_polymorphic, \is_custom
   movl %eax, LOCAL0(%esp)
   movl %ebp, LOCAL1(%esp)
   movl %ebx, LOCAL2(%esp)
   // From this point:
   // - ebx contains shorty (in callee-save to switch over return value after call).
   // - eax, edx, and ebp are available
   // - ecx contains 'this' pointer for instance method.
   // TODO: ebp/rREFS is used for stack unwinding, can we find a way to preserve it?
   leal 1(%ebx), %edx  // shorty + 1  ; ie skip return arg character
   movzwl 4(rPC), %ebx // arguments
   .if \is_string_init
   shrl MACRO_LITERAL(4), %ebx
   movl $$1, %ebp       // ignore first argument
   .elseif \is_static
   movl $$0, %ebp       // arg_index
   .else
   shrl MACRO_LITERAL(4), %ebx
   movl $$1, %ebp       // arg_index
   .endif
   LOOP_OVER_SHORTY_LOADING_XMMS xmm0, ebx, edx, ebp, .Lxmm_setup_finished_\suffix
   LOOP_OVER_SHORTY_LOADING_XMMS xmm1, ebx, edx, ebp, .Lxmm_setup_finished_\suffix
   LOOP_OVER_SHORTY_LOADING_XMMS xmm2, ebx, edx, ebp, .Lxmm_setup_finished_\suffix
   LOOP_OVER_SHORTY_LOADING_XMMS xmm3, ebx, edx, ebp, .Lxmm_setup_finished_\suffix
   // We know this can only be a float.
   movb (%edx), %al                        // al := *shorty
   cmpb MACRO_LITERAL(70), %al             // if (al != 'F') goto finished
   jne .Lxmm_setup_finished_\suffix
   movzbl 1(rPC), %eax
   andl MACRO_LITERAL(0xf), %eax
   GET_VREG %eax, %eax
   // Add four for the ArtMethod.
   movl %eax, 4(%esp, %ebp, 4)
   // We know there is no more argument, jump to the call.
   jmp .Lrestore_saved_values_\suffix
.Lxmm_setup_finished_\suffix:
   // Reload rREFS for fetching the PC.
   movl LOCAL1(%esp), %ebp
   // Reload shorty
   movl LOCAL2(%esp), %ebx
   FETCH_PC
   leal 1(%ebx), %ebx  // shorty + 1  ; ie skip return arg character
   movzwl 4(rPC), %esi // arguments
   .if \is_string_init
   movl $$0, %ebp       // arg_index
   shrl MACRO_LITERAL(4), %esi
   LOOP_OVER_SHORTY_LOADING_GPRS ecx, edx, esi, ebx, ebp, .Lrestore_saved_values_\suffix, .Lif_long_ebx_\suffix, is_ebx=0
   .elseif \is_static
   movl $$0, %ebp       // arg_index
   LOOP_OVER_SHORTY_LOADING_GPRS ecx, edx, esi, ebx, ebp, .Lrestore_saved_values_\suffix, .Lif_long_ebx_\suffix, is_ebx=0
   .else
   shrl MACRO_LITERAL(4), %esi
   movl $$1, %ebp       // arg_index
   .endif
   // For long argument, store second half in eax to not overwrite the shorty.
   LOOP_OVER_SHORTY_LOADING_GPRS edx, eax, esi, ebx, ebp, .Lrestore_saved_values_\suffix, .Lif_long_\suffix, is_ebx=0
.Lif_long_ebx_\suffix:
   // Store in eax to not overwrite the shorty.
   LOOP_OVER_SHORTY_LOADING_GPRS eax, eax, esi, ebx, ebp, .Lrestore_saved_values_\suffix, .Lif_long_\suffix, is_ebx=1
.Lif_long_\suffix:
   // Save shorty, as LOOP_OVER_SHORTY_LOADING_INTS might overwrite the LOCAL2 slot for a long argument.
   pushl LOCAL2(%esp)
   pushl %eax
   LOOP_OVER_SHORTY_LOADING_INTS 8, ebx, esi, ebp, .Lrestore_ebx_\suffix, \is_string_init
.Lrestore_ebx_\suffix:
   popl %ebx
   popl %esi
   movl LOCAL0(%esp), %eax
   movl LOCAL1(%esp), %ebp
   jmp .Lgpr_setup_finished_\suffix
.Lrestore_saved_values_\suffix:
   movl LOCAL0(%esp), %eax
   movl LOCAL1(%esp), %ebp
   movl LOCAL2(%esp), %esi
.Lgpr_setup_finished_\suffix:
   // Look at the shorty now, as we'll want %esi to have the PC for proper stack unwinding
   // and we're running out of callee-save registers.
   cmpb LITERAL(68), (%esi)       // Test if result type char == 'D'.
   je .Linvoke_double_\suffix
   cmpb LITERAL(70), (%esi)       // Test if result type char == 'F'.
   je .Linvoke_float_\suffix
   FETCH_PC
   DO_CALL \is_polymorphic, \is_custom
   SAVE_WIDE_RETURN
.Ldone_return_\suffix:
   /* resume execution of caller */
   .if \is_string_init
   movzwl 4(rPC), %ecx // arguments
   andl $$0xf, %ecx
   GET_VREG rINST, %ecx
   UPDATE_REGISTERS_FOR_STRING_INIT rINST, %eax
   .endif
   RESTORE_IBASE

   .if \is_polymorphic
   ADVANCE_PC_FETCH_AND_GOTO_NEXT 4
   .else
   ADVANCE_PC_FETCH_AND_GOTO_NEXT 3
   .endif

.Linvoke_double_\suffix:
   FETCH_PC
   DO_CALL \is_polymorphic, \is_custom
   movq %xmm0, LOCAL1(%esp)
   movl LOCAL1(%esp), %eax
   jmp .Ldone_return_\suffix
.Linvoke_float_\suffix:
   FETCH_PC
   DO_CALL \is_polymorphic, \is_custom
   movd %xmm0, %eax
   jmp .Ldone_return_\suffix
.endm

.macro COMMON_INVOKE_RANGE is_static=0, is_interface=0, suffix="", is_string_init=0, is_polymorphic=0, is_custom=0
   .if \is_polymorphic
   // No fast path for polymorphic calls.
   .elseif \is_custom
   // No fast path for custom calls.
   .elseif \is_string_init
   // No fast path for string.init.
   .else
     testl $$ART_METHOD_NTERP_INVOKE_FAST_PATH_FLAG, ART_METHOD_ACCESS_FLAGS_OFFSET(%eax)
     je .Lfast_path_with_few_args_range_\suffix
     movzbl 1(rPC), %edx  // number of arguments
     .if \is_static
     testl %edx, %edx
     je .Linvoke_fast_path_range_\suffix
     .else
     cmpl MACRO_LITERAL(1), %edx
     je .Linvoke_fast_path_range_\suffix
     .endif
     movzwl 4(rPC), %ebx  // dex register of first argument
     leal (rFP, %ebx, 4), %esi  // location of first dex register value
     cmpl MACRO_LITERAL(2), %edx
     .if \is_static
     jl .Lone_arg_fast_path_range_\suffix
     .endif
     je .Ltwo_args_fast_path_range_\suffix
     cmp MACRO_LITERAL(4), %edx
     jl .Lthree_args_fast_path_range_\suffix

.Lloop_over_fast_path_range_\suffix:
     subl MACRO_LITERAL(1), %edx
     movl (%esi, %edx, 4), %ebx
     movl %ebx, 4(%esp, %edx, 4)  // Add 4 for the ArtMethod
     cmpl MACRO_LITERAL(3), %edx
     jne .Lloop_over_fast_path_range_\suffix

.Lthree_args_fast_path_range_\suffix:
     movl 8(%esi), %ebx
.Ltwo_args_fast_path_range_\suffix:
     movl 4(%esi), %edx
.Lone_arg_fast_path_range_\suffix:
     .if \is_static
     movl 0(%esi), %ecx
     .else
     // First argument already in %ecx.
     .endif
.Linvoke_fast_path_range_\suffix:
     FETCH_PC
     call *ART_METHOD_QUICK_CODE_OFFSET_32(%eax) // Call the method.
     SAVE_WIDE_RETURN
     RESTORE_IBASE
     ADVANCE_PC_FETCH_AND_GOTO_NEXT 3

.Lfast_path_with_few_args_range_\suffix:
     // Fast path when we have zero or one argument (modulo 'this'). If there
     // is one argument, we can put it in both floating point and core register.
     movzbl 1(rPC), %ebx # Number of arguments
     .if \is_static
     cmpl MACRO_LITERAL(1), %ebx
     jl .Linvoke_with_few_args_range_\suffix
     jne .Lget_shorty_range_\suffix
     movzwl 4(rPC), %ebx  // Dex register of first argument
     GET_VREG %ecx, %ebx
     movd %ecx, %xmm0
     .else
     cmpl MACRO_LITERAL(2), %ebx
     jl .Linvoke_with_few_args_range_\suffix
     jne .Lget_shorty_range_\suffix
     movzwl 4(rPC), %ebx
     addl MACRO_LITERAL(1), %ebx  // dex register of second argument
     GET_VREG %edx, %ebx
     movd %edx, %xmm0
     .endif
.Linvoke_with_few_args_range_\suffix:
     // Check if the next instruction is move-result or move-result-wide.
     // If it is, we fetch the shorty and jump to the regular invocation.
     movzwl  6(rPC), %ebx
     and MACRO_LITERAL(0xfe), %ebx
     cmpl MACRO_LITERAL(0x0a), %ebx
     je .Lget_shorty_and_invoke_range_\suffix
     call *ART_METHOD_QUICK_CODE_OFFSET_32(%eax) // Call the method.
     RESTORE_IBASE
     ADVANCE_PC_FETCH_AND_GOTO_NEXT 3
.Lget_shorty_and_invoke_range_\suffix:
     GET_SHORTY_SLOW_PATH %esi, \is_interface
     jmp .Lgpr_setup_finished_range_\suffix
   .endif

.Lget_shorty_range_\suffix:
   GET_SHORTY %ebx, \is_interface, \is_polymorphic, \is_custom
   movl %eax, LOCAL0(%esp)
   movl %ebp, LOCAL1(%esp)
   movl %ebx, LOCAL2(%esp)
   // From this point:
   // - ebx contains shorty (in callee-save to switch over return value after call).
   // - eax, edx, ebx, and ebp are available.
   // - ecx contains 'this' pointer for instance method.
   // TODO: ebp/rREFS is used for stack unwinding, can we find a way to preserve it?
   leal 1(%ebx), %edx  // shorty + 1  ; ie skip return arg character
   movzwl 4(rPC), %ebx // arg start index
   .if \is_string_init
   addl $$1, %ebx       // arg start index
   movl $$0, %ebp       // index in stack
   .elseif \is_static
   movl $$0, %ebp       // index in stack
   .else
   addl $$1, %ebx       // arg start index
   movl $$1, %ebp       // index in stack
   .endif
   LOOP_RANGE_OVER_SHORTY_LOADING_XMMS xmm0, edx, ebx, ebp, .Lxmm_setup_finished_range_\suffix
   LOOP_RANGE_OVER_SHORTY_LOADING_XMMS xmm1, edx, ebx, ebp, .Lxmm_setup_finished_range_\suffix
   LOOP_RANGE_OVER_SHORTY_LOADING_XMMS xmm2, edx, ebx, ebp, .Lxmm_setup_finished_range_\suffix
   LOOP_RANGE_OVER_SHORTY_LOADING_XMMS xmm3, edx, ebx, ebp, .Lxmm_setup_finished_range_\suffix
   LOOP_RANGE_OVER_FPs edx, ebx, ebp, .Lxmm_setup_finished_range_\suffix
.Lxmm_setup_finished_range_\suffix:
   // Reload rREFS for fetching the PC.
   movl LOCAL1(%esp), %ebp
   // Reload shorty
   movl LOCAL2(%esp), %ebx
   FETCH_PC
   leal 1(%ebx), %ebx  // shorty + 1  ; ie skip return arg character
   // From this point:
   // - ebx contains shorty
   // - eax and ebp are available.
   // - ecx contains 'this' pointer for instance method.
   movzwl 4(rPC), %ebp // arg start index
   // rPC (esi) is now available
   .if \is_string_init
   addl $$1, %ebp       // arg start index
   movl $$0, %esi       // index in stack
   LOOP_RANGE_OVER_SHORTY_LOADING_GPRS ecx, edx, ebx, ebp, esi, .Lrestore_saved_values_range_\suffix, .Lif_long_ebx_range_\suffix, is_ebx=0
   .elseif \is_static
   movl $$0, %esi // index in stack
   LOOP_RANGE_OVER_SHORTY_LOADING_GPRS ecx, edx, ebx, ebp, esi, .Lrestore_saved_values_range_\suffix, .Lif_long_ebx_range_\suffix, is_ebx=0
   .else
   addl $$1, %ebp // arg start index
   movl $$1, %esi // index in stack
   .endif
   // For long argument, store second half in eax to not overwrite the shorty.
   LOOP_RANGE_OVER_SHORTY_LOADING_GPRS edx, eax, ebx, ebp, esi, .Lrestore_saved_values_range_\suffix, .Lif_long_range_\suffix, is_ebx=0
.Lif_long_ebx_range_\suffix:
   // Store in eax to not overwrite the shorty.
   LOOP_RANGE_OVER_SHORTY_LOADING_GPRS eax, eax, ebx, ebp, esi, .Lrestore_saved_values_range_\suffix, .Lif_long_range_\suffix, is_ebx=1
.Lif_long_range_\suffix:
   // Save shorty, as LOOP_RANGE_OVER_SHORTY_LOADING_INTS might overwrite the LOCAL2 slot for a long argument.
   pushl LOCAL2(%esp)
   pushl %eax
   LOOP_RANGE_OVER_INTs 8, ebx, ebp, esi, .Lrestore_ebx_range_\suffix
.Lrestore_ebx_range_\suffix:
   popl %ebx
   popl %esi
   movl LOCAL0(%esp), %eax
   movl LOCAL1(%esp), %ebp
   jmp .Lgpr_setup_finished_range_\suffix

.Lrestore_saved_values_range_\suffix:
   movl LOCAL0(%esp), %eax
   movl LOCAL1(%esp), %ebp
   // Save shorty in callee-save register
   movl LOCAL2(%esp), %esi

.Lgpr_setup_finished_range_\suffix:
   cmpb LITERAL(68), (%esi)       // Test if result type char == 'D'.
   je .Lreturn_range_double_\suffix
   cmpb LITERAL(70), (%esi)       // Test if result type char == 'F'.
   je .Lreturn_range_float_\suffix

   FETCH_PC
   DO_CALL \is_polymorphic, \is_custom
   SAVE_WIDE_RETURN
.Ldone_return_range_\suffix:
   /* resume execution of caller */
   .if \is_string_init
   movzwl 4(rPC), %ecx // arguments
   GET_VREG rINST, %ecx
   UPDATE_REGISTERS_FOR_STRING_INIT rINST, %eax
   .endif
   RESTORE_IBASE
   .if \is_polymorphic
   ADVANCE_PC_FETCH_AND_GOTO_NEXT 4
   .else
   ADVANCE_PC_FETCH_AND_GOTO_NEXT 3
   .endif
.Lreturn_range_double_\suffix:
   FETCH_PC
   DO_CALL \is_polymorphic, \is_custom
   movq %xmm0, LOCAL1(%esp)
   movl LOCAL1(%esp), %eax
   jmp .Ldone_return_range_\suffix
.Lreturn_range_float_\suffix:
   FETCH_PC
   DO_CALL \is_polymorphic, \is_custom
   movd %xmm0, %eax
   jmp .Ldone_return_range_\suffix
.endm

// Helper for static field get.
.macro OP_SGET load="movl", wide="0"
   // Fast-path which gets the field from thread-local cache.
%  fetch_from_thread_cache("%eax", miss_label="2f")
1:
   movl ART_FIELD_OFFSET_OFFSET(%eax), %ecx
   movl ART_FIELD_DECLARING_CLASS_OFFSET(%eax), %eax
   cmpl $$0, rSELF:THREAD_READ_BARRIER_MARK_REG00_OFFSET
   jne 3f
4:
   .if \wide
   addl %ecx, %eax
   \load (%eax), %ecx
   SET_VREG %ecx, rINST            # fp[A] <- value
   \load 4(%eax), %ecx
   SET_VREG_HIGH %ecx, rINST
   .else
   \load (%eax, %ecx, 1), %eax
   SET_VREG %eax, rINST            # fp[A] <- value
   .endif
   ADVANCE_PC_FETCH_AND_GOTO_NEXT 2
2:
   EXPORT_PC
   movl rSELF:THREAD_SELF_OFFSET, ARG0
   movl 0(%esp), ARG1
   movl rPC, ARG2
   movl $$0, ARG3
   call nterp_get_static_field
   .if !\wide
   CLEAR_VOLATILE_MARKER %eax
   jmp 1b
   .else
   testl MACRO_LITERAL(1), %eax
   je 1b
   CLEAR_VOLATILE_MARKER %eax
   movl ART_FIELD_OFFSET_OFFSET(%eax), %ecx
   movl ART_FIELD_DECLARING_CLASS_OFFSET(%eax), %eax
   cmpl $$0, rSELF:THREAD_READ_BARRIER_MARK_REG00_OFFSET
   jne 5f
6:
   movsd (%eax, %ecx, 1), %xmm0
   SET_WIDE_FP_VREG %xmm0, rINST
   ADVANCE_PC_FETCH_AND_GOTO_NEXT 2
5:
   call art_quick_read_barrier_mark_reg00
   jmp 6b
   .endif
3:
   call art_quick_read_barrier_mark_reg00
   jmp 4b
.endm

// Helper for static field put.
.macro OP_SPUT rINST_reg="rINST", store="movl", wide="0":
   // Fast-path which gets the field from thread-local cache.
%  fetch_from_thread_cache("%eax", miss_label="2f")
1:
   movl ART_FIELD_OFFSET_OFFSET(%eax), %ecx
   movl ART_FIELD_DECLARING_CLASS_OFFSET(%eax), %eax
   cmpl $$0, rSELF:THREAD_READ_BARRIER_MARK_REG00_OFFSET
   jne 3f
4:
   .if \wide
   addl %ecx, %eax
   GET_VREG %ecx, rINST                  # rINST <- v[A]
   movl %ecx, (%eax)
   GET_VREG_HIGH %ecx, rINST
   movl %ecx, 4(%eax)
   .else
   GET_VREG rINST, rINST                  # rINST <- v[A]
   \store    \rINST_reg, (%eax,%ecx,1)
   .endif
   ADVANCE_PC_FETCH_AND_GOTO_NEXT 2
2:
   EXPORT_PC
   movl rSELF:THREAD_SELF_OFFSET, ARG0
   movl 0(%esp), ARG1
   movl rPC, ARG2
   movl $$0, ARG3
   call nterp_get_static_field
   testl MACRO_LITERAL(1), %eax
   je 1b
   // Clear the marker that we put for volatile fields.
   CLEAR_VOLATILE_MARKER %eax
   movl ART_FIELD_OFFSET_OFFSET(%eax), %ecx
   movl ART_FIELD_DECLARING_CLASS_OFFSET(%eax), %eax
   cmpl $$0, rSELF:THREAD_READ_BARRIER_MARK_REG00_OFFSET
   jne 6f
5:
   .if \wide
   addl %ecx, %eax
   GET_WIDE_FP_VREG %xmm0, rINST
   movsd %xmm0, (%eax)
   .else
   GET_VREG rINST, rINST                  # rINST <- v[A]
   \store    \rINST_reg, (%eax,%ecx,1)
   .endif
   lock addl $$0, (%esp)
   ADVANCE_PC_FETCH_AND_GOTO_NEXT 2
3:
   call art_quick_read_barrier_mark_reg00
   jmp 4b
6:
   call art_quick_read_barrier_mark_reg00
   jmp 5b
.endm


.macro OP_IPUT_INTERNAL rINST_reg="rINST", store="movl", wide="0", volatile="0":
   movzbl  rINSTbl, %ecx                   # ecx <- BA
   sarl    $$4, %ecx                       # ecx <- B
   GET_VREG %ecx, %ecx                     # vB (object we're operating on)
   testl   %ecx, %ecx                      # is object null?
   je      common_errNullObject
   andb    $$0xf, rINSTbl                  # rINST <- A
   .if \wide
   addl %ecx, %eax
   GET_WIDE_FP_VREG %xmm0, rINST
   movsd %xmm0, (%eax)
   .else
   GET_VREG rINST, rINST                  # rINST <- v[A]
   \store \rINST_reg, (%ecx,%eax,1)
   .endif
.endm

// Helper for instance field put.
.macro OP_IPUT rINST_reg="rINST", store="movl", wide="0":
   // Fast-path which gets the field from thread-local cache.
%  fetch_from_thread_cache("%eax", miss_label="2f")
1:
   OP_IPUT_INTERNAL \rINST_reg, \store, \wide, volatile=0
   ADVANCE_PC_FETCH_AND_GOTO_NEXT 2
2:
   EXPORT_PC
   movl rSELF:THREAD_SELF_OFFSET, ARG0
   movl 0(%esp), ARG1
   movl rPC, ARG2
   movl $$0, ARG3
   call nterp_get_instance_field_offset
   testl %eax, %eax
   jns 1b
   negl %eax
   OP_IPUT_INTERNAL \rINST_reg, \store, \wide, volatile=1
   lock addl $$0, (%esp)
   ADVANCE_PC_FETCH_AND_GOTO_NEXT 2
.endm

// Helper for instance field get.
.macro OP_IGET load="movl", wide="0"
   // Fast-path which gets the field from thread-local cache.
%  fetch_from_thread_cache("%eax", miss_label="2f")
1:
   movl    rINST, %ecx                     # ecx <- BA
   sarl    $$4, %ecx                       # ecx <- B
   GET_VREG %ecx, %ecx                     # vB (object we're operating on)
   testl   %ecx, %ecx                      # is object null?
   je      common_errNullObject
   andb    $$0xf,rINSTbl                   # rINST <- A
   .if \wide
   addl %ecx, %eax
   \load (%eax), %ecx
   SET_VREG %ecx, rINST
   \load 4(%eax), %ecx
   SET_VREG_HIGH %ecx, rINST
   .else
   \load (%ecx,%eax,1), %eax
   SET_VREG %eax, rINST                    # fp[A] <- value
   .endif
   ADVANCE_PC_FETCH_AND_GOTO_NEXT 2
2:
   EXPORT_PC
   movl rSELF:THREAD_SELF_OFFSET, ARG0
   movl 0(%esp), ARG1
   movl rPC, ARG2
   movl $$0, ARG3
   call nterp_get_instance_field_offset
   testl %eax, %eax
   jns 1b
   negl %eax
   .if !\wide
   jmp 1b
   .else
   movl    rINST, %ecx                     # ecx <- BA
   sarl    $$4, %ecx                       # ecx <- B
   GET_VREG %ecx, %ecx                     # vB (object we're operating on)
   testl   %ecx, %ecx                      # is object null?
   je      common_errNullObject
   andb    $$0xf,rINSTbl                   # rINST <- A
   movsd (%eax, %ecx, 1), %xmm0
   SET_WIDE_FP_VREG %xmm0, rINST
   ADVANCE_PC_FETCH_AND_GOTO_NEXT 2
   .endif
.endm

// Store a reference parameter into our dex register frame.
// Uses xmm4 as temporary.
.macro SETUP_REFERENCE_PARAMETER_IN_GPR offset, stack_ptr, regs, refs, ins, arg_offset, finished
    movss \offset(REG_VAR(stack_ptr)), %xmm4
    movss %xmm4, (REG_VAR(regs), REG_VAR(arg_offset))
    movss %xmm4, (REG_VAR(refs), REG_VAR(arg_offset))
    addl MACRO_LITERAL(4), REG_VAR(arg_offset)
    subl MACRO_LITERAL(1), REG_VAR(ins)
    je \finished
.endm

// Store a reference parameter into our dex register frame.
// Uses xmm4 as temporary.
.macro SETUP_REFERENCE_PARAMETERS_IN_STACK stack_ptr, regs, refs, ins, arg_offset
1:
    movss OFFSET_TO_FIRST_ARGUMENT_IN_STACK(REG_VAR(stack_ptr), REG_VAR(arg_offset)), %xmm4
    movss %xmm4, (REG_VAR(regs), REG_VAR(arg_offset))
    movss %xmm4, (REG_VAR(refs), REG_VAR(arg_offset))
    addl MACRO_LITERAL(4), REG_VAR(arg_offset)
    subl MACRO_LITERAL(1), REG_VAR(ins)
    jne 1b
.endm

.macro DO_SUSPEND_CHECK continue_label
    testl   $$(THREAD_SUSPEND_OR_CHECKPOINT_REQUEST), rSELF:THREAD_FLAGS_OFFSET
    jz      \continue_label
    jmp     NterpCallSuspendAndGotoNext
.endm

.macro CHECK_AND_UPDATE_SHARED_MEMORY_METHOD if_hot, if_not_hot
    testl $$ART_METHOD_IS_MEMORY_SHARED_FLAG, ART_METHOD_ACCESS_FLAGS_OFFSET(%eax)
    jz \if_hot
    movzwl rSELF:THREAD_SHARED_METHOD_HOTNESS_OFFSET, %ecx
    testl %ecx, %ecx
    je \if_hot
    addl $$-1, %ecx
    movw %cx, rSELF:THREAD_SHARED_METHOD_HOTNESS_OFFSET
    jmp \if_not_hot
.endm


%def entry():
/*
 * ArtMethod entry point.
 *
 * On entry:
 *  eax   ArtMethod* callee
 *  rest  method parameters
 */

OAT_ENTRY ExecuteNterpWithClinitImpl
    .cfi_startproc
    PUSH_ARG esi
    // For simplicity, we don't do a read barrier here, but instead rely
    // on art_quick_resolution_trampoline to always have a suspend point before
    // calling back here.
    movl ART_METHOD_DECLARING_CLASS_OFFSET(%eax), %esi
    cmpl $$(MIRROR_CLASS_STATUS_VISIBLY_INITIALIZED_SHIFTED), MIRROR_CLASS_STATUS_OFFSET(%esi)
    jae .Lcontinue_execute_nterp
    cmpl $$(MIRROR_CLASS_STATUS_INITIALIZING_SHIFTED), MIRROR_CLASS_STATUS_OFFSET(%esi)
    jb .Linvoke_trampoline
    movl MIRROR_CLASS_CLINIT_THREAD_ID_OFFSET(%esi), %esi
    cmpl %esi, rSELF:THREAD_TID_OFFSET
    CFI_REMEMBER_STATE
    je .Lcontinue_execute_nterp
.Linvoke_trampoline:
    POP_ARG esi
    jmp art_quick_resolution_trampoline
.Lcontinue_execute_nterp:
    CFI_RESTORE_STATE_AND_DEF_CFA esp, 8
    POP_ARG esi
    jmp ExecuteNterpImpl
    .cfi_endproc
    .global SYMBOL(EndExecuteNterpWithClinitImpl)
SYMBOL(EndExecuteNterpWithClinitImpl):

OAT_ENTRY ExecuteNterpImpl
    .cfi_startproc
    .cfi_def_cfa esp, 4
    testl %eax, -STACK_OVERFLOW_RESERVED_BYTES(%esp)
    // Spill callee save regs
    SPILL_ALL_CALLEE_SAVES

    // Make argument registers available.
    SPILL_ALL_CORE_PARAMETERS

    // Fetch code item.
    movl ART_METHOD_DATA_OFFSET_32(%eax), %ecx

    // Setup the stack for executing the method.
    SETUP_STACK_FRAME %ecx, rREFS, rFP, CFI_REFS, load_ins=1

    // Save the PC
    movl %ecx, -8(rREFS)

    // Setup the parameters
    testl %esi, %esi
    je .Lxmm_setup_finished

    subl %esi, %ebx
    sall $$2, %ebx // ebx is now the offset for inputs into the registers array.

    // Reload ArtMethod.
    movl (%esp), %eax
    testl $$ART_METHOD_NTERP_ENTRY_POINT_FAST_PATH_FLAG, ART_METHOD_ACCESS_FLAGS_OFFSET(%eax)
    je .Lsetup_slow_path
    leal (rREFS, %ebx, 1), %ecx
    leal (rFP, %ebx, 1), %ebx
    movl $$0, %eax

    // edx is the old stack pointer
    SETUP_REFERENCE_PARAMETER_IN_GPR 8, edx, ebx, ecx, esi, eax, .Lxmm_setup_finished
    SETUP_REFERENCE_PARAMETER_IN_GPR 4, edx, ebx, ecx, esi, eax, .Lxmm_setup_finished
    SETUP_REFERENCE_PARAMETER_IN_GPR 0, edx, ebx, ecx, esi, eax, .Lxmm_setup_finished
    SETUP_REFERENCE_PARAMETERS_IN_STACK edx, ebx, ecx, esi, eax
    jmp .Lxmm_setup_finished

.Lsetup_slow_path:
    // If the method is not static and there is one argument ('this'), we don't need to fetch the
    // shorty.
    testl $$ART_METHOD_IS_STATIC_FLAG, ART_METHOD_ACCESS_FLAGS_OFFSET(%eax)
    jne .Lsetup_with_shorty

    // Record 'this'.
    movl 8(%edx), %eax
    movl %eax, (rFP, %ebx)
    movl %eax, (rREFS, %ebx)

    cmpl $$1, %esi
    je .Lxmm_setup_finished

.Lsetup_with_shorty:
    // Save xmm registers. Core registers have already been saved.
    subl MACRO_LITERAL(4 * 8), %esp
    movq %xmm0, 0(%esp)
    movq %xmm1, 8(%esp)
    movq %xmm2, 16(%esp)
    movq %xmm3, 24(%esp)
    subl MACRO_LITERAL(12), %esp
    pushl (4 * 8 + 12)(%esp)
    call SYMBOL(NterpGetShorty)
    addl MACRO_LITERAL(16), %esp

    // Restore xmm registers
    movq 0(%esp), %xmm0
    movq 8(%esp), %xmm1
    movq 16(%esp), %xmm2
    movq 24(%esp), %xmm3
    addl MACRO_LITERAL(4 * 8), %esp

    // Reload the old stack pointer.
    movl -4(rREFS), %edx
    // TODO: Get shorty in a better way and remove above

    movl $$0, %esi
    movl (%esp), %ecx
    testl $$ART_METHOD_IS_STATIC_FLAG, ART_METHOD_ACCESS_FLAGS_OFFSET(%ecx)

    // Note the leal and movl below don't change the flags.
    leal (rFP, %ebx, 1), %ecx
    leal (rREFS, %ebx, 1), %ebx
    // Save rFP (%edi), we're using it as temporary below.
    movl rFP, LOCAL1(%esp)
    leal 1(%eax), %edi  // shorty + 1  ; ie skip return arg character
    // Save shorty + 1
    movl %edi, LOCAL2(%esp)
    jne .Lhandle_static_method
    addl $$4, %ecx
    addl $$4, %ebx
    addl $$4, %edx
    LOOP_OVER_SHORTY_STORING_GPRS 0, -4, edx, edi, esi, ecx, ebx, .Lgpr_setup_finished, .Lif_long, is_ebx=0
    LOOP_OVER_SHORTY_STORING_GPRS -4, 0, edx, edi, esi, ecx, ebx, .Lgpr_setup_finished, .Lif_long, is_ebx=1
    jmp .Lif_long
.Lhandle_static_method:
    LOOP_OVER_SHORTY_STORING_GPRS 8, 4, edx, edi, esi, ecx, ebx, .Lgpr_setup_finished, .Lif_long_ebx, is_ebx=0
    LOOP_OVER_SHORTY_STORING_GPRS 4, 0, edx, edi, esi, ecx, ebx, .Lgpr_setup_finished, .Lif_long, is_ebx=0
.Lif_long_ebx:
    LOOP_OVER_SHORTY_STORING_GPRS 0, 0, edx, edi, esi, ecx, ebx, .Lgpr_setup_finished, .Lif_long, is_ebx=1
.Lif_long:
    LOOP_OVER_INTs edi, esi, ecx, ebx, edx, .Lgpr_setup_finished
.Lgpr_setup_finished:
    // Restore shorty + 1
    movl LOCAL2(%esp), %edi
    movl $$0, %esi // reset counter
    LOOP_OVER_SHORTY_STORING_XMMS xmm0, edi, esi, ecx, .Lrestore_fp
    LOOP_OVER_SHORTY_STORING_XMMS xmm1, edi, esi, ecx, .Lrestore_fp
    LOOP_OVER_SHORTY_STORING_XMMS xmm2, edi, esi, ecx, .Lrestore_fp
    LOOP_OVER_SHORTY_STORING_XMMS xmm3, edi, esi, ecx, .Lrestore_fp
    LOOP_OVER_FPs edi, esi, ecx, edx, .Lrestore_fp
.Lrestore_fp:
    movl LOCAL1(%esp), rFP
.Lxmm_setup_finished:
    FETCH_PC
    CFI_DEFINE_DEX_PC_WITH_OFFSET(CFI_TMP, CFI_DEX, 0)
    // Set rIBASE
    RESTORE_IBASE
    /* start executing the instruction at rPC */
    START_EXECUTING_INSTRUCTIONS
    /* NOTE: no fallthrough */
    // cfi info continues, and covers the whole nterp implementation.
    END ExecuteNterpImpl

%def opcode_pre():

%def fetch_from_thread_cache(dest_reg, miss_label):
   // Fetch some information from the thread cache.
   // Uses eax, and ecx as temporaries.
   movl rSELF:THREAD_SELF_OFFSET, %eax
   movl rPC, %ecx
   sall MACRO_LITERAL(THREAD_INTERPRETER_CACHE_SIZE_SHIFT), %ecx
   andl MACRO_LITERAL(THREAD_INTERPRETER_CACHE_SIZE_MASK), %ecx
   cmpl THREAD_INTERPRETER_CACHE_OFFSET(%eax, %ecx, 1), rPC
   jne  ${miss_label}
   movl __SIZEOF_POINTER__+THREAD_INTERPRETER_CACHE_OFFSET(%eax, %ecx, 1), ${dest_reg}

%def footer():
/*
 * ===========================================================================
 *  Common subroutines and data
 * ===========================================================================
 */

    .text
    .align  2

// Enclose all code below in a symbol (which gets printed in backtraces).
ENTRY nterp_helper

// Note: mterp also uses the common_* names below for helpers, but that's OK
// as the assembler compiled each interpreter separately.
common_errDivideByZero:
    EXPORT_PC
    call art_quick_throw_div_zero

// Expect array in eax, index in ecx.
common_errArrayIndex:
    EXPORT_PC
    movl MIRROR_ARRAY_LENGTH_OFFSET(%eax), %edx
    movl %ecx, %eax
    movl %edx, %ecx
    call art_quick_throw_array_bounds

common_errNullObject:
    EXPORT_PC
    call art_quick_throw_null_pointer_exception

NterpCommonInvokeStatic:
    COMMON_INVOKE_NON_RANGE is_static=1, is_interface=0, suffix="invokeStatic"

NterpCommonInvokeStaticRange:
    COMMON_INVOKE_RANGE is_static=1, is_interface=0, suffix="invokeStatic"

NterpCommonInvokeInstance:
    COMMON_INVOKE_NON_RANGE is_static=0, is_interface=0, suffix="invokeInstance"

NterpCommonInvokeInstanceRange:
    COMMON_INVOKE_RANGE is_static=0, is_interface=0, suffix="invokeInstance"

NterpCommonInvokeInterface:
    COMMON_INVOKE_NON_RANGE is_static=0, is_interface=1, suffix="invokeInterface"

NterpCommonInvokeInterfaceRange:
    COMMON_INVOKE_RANGE is_static=0, is_interface=1, suffix="invokeInterface"

NterpCommonInvokePolymorphic:
    COMMON_INVOKE_NON_RANGE is_static=0, is_interface=0, is_polymorphic=1, suffix="invokePolymorphic"

NterpCommonInvokePolymorphicRange:
    COMMON_INVOKE_RANGE is_static=0, is_interface=0, is_polymorphic=1, suffix="invokePolymorphic"

NterpCommonInvokeCustom:
    COMMON_INVOKE_NON_RANGE is_static=1, is_interface=0, is_polymorphic=0, is_custom=1, suffix="invokeCustom"

NterpCommonInvokeCustomRange:
    COMMON_INVOKE_RANGE is_static=1, is_interface=0, is_polymorphic=0, is_custom=1, suffix="invokeCustom"

NterpHandleStringInit:
   COMMON_INVOKE_NON_RANGE is_static=0, is_interface=0, is_string_init=1, suffix="stringInit"

NterpHandleStringInitRange:
   COMMON_INVOKE_RANGE is_static=0, is_interface=0, is_string_init=1, suffix="stringInit"

NterpNewInstance:
   EXPORT_PC
   // Fast-path which gets the class from thread-local cache.
%  fetch_from_thread_cache("%eax", miss_label="2f")
   cmpl $$0, rSELF:THREAD_READ_BARRIER_MARK_REG00_OFFSET
   jne 3f
4:
   call *rSELF:THREAD_ALLOC_OBJECT_ENTRYPOINT_OFFSET
   RESTORE_IBASE
   FETCH_INST_CLEAR_OPCODE
1:
   SET_VREG_OBJECT %eax, rINST             # fp[A] <- value
   ADVANCE_PC_FETCH_AND_GOTO_NEXT 2
2:
   movl rSELF:THREAD_SELF_OFFSET, ARG0
   movl 0(%esp), ARG1
   movl rPC, ARG2
   call nterp_allocate_object
   jmp 1b
3:
   // 00 is %eax
   call art_quick_read_barrier_mark_reg00
   jmp 4b

NterpNewArray:
   /* new-array vA, vB, class@CCCC */
   EXPORT_PC
   // Fast-path which gets the class from thread-local cache.
%  fetch_from_thread_cache("%eax", miss_label="2f")
   cmpl $$0, rSELF:THREAD_READ_BARRIER_MARK_REG00_OFFSET
   jne 3f
1:
   movzbl  rINSTbl, %ecx
   sarl    $$4, %ecx                         # ecx<- B
   GET_VREG %ecx %ecx                        # ecx<- vB (array length)
   call *rSELF:THREAD_ALLOC_ARRAY_ENTRYPOINT_OFFSET
   RESTORE_IBASE
   FETCH_INST_CLEAR_OPCODE
   andb    $$0xf, rINSTbl                   # rINST<- A
   SET_VREG_OBJECT %eax, rINST              # fp[A] <- value
   ADVANCE_PC_FETCH_AND_GOTO_NEXT 2
2:
   movl rSELF:THREAD_SELF_OFFSET, ARG0
   movl 0(%esp), ARG1
   movl rPC, ARG2
   call nterp_get_class
   jmp 1b
3:
   // 00 is %eax
   call art_quick_read_barrier_mark_reg00
   jmp 1b

NterpPutObjectInstanceField:
   // Fast-path which gets the field from thread-local cache.
%  fetch_from_thread_cache("%eax", miss_label="2f")
1:
   movl    rINST, %ecx                     # ecx <- BA
   andl    $$0xf, %ecx                     # ecx <- A
   GET_VREG %ecx, %ecx                     # ecx <- v[A]
   sarl    $$4, rINST
   GET_VREG rINST, rINST                   # vB (object we're operating on)
   testl   rINST, rINST                    # is object null?
   je      common_errNullObject
   POISON_HEAP_REF ecx
   movl %ecx, (rINST, %eax, 1)
   testl %ecx, %ecx
   je 4f
   movl rSELF:THREAD_CARD_TABLE_OFFSET, %eax
   shrl $$CARD_TABLE_CARD_SHIFT, rINST
   movb %al, (%eax, rINST, 1)
4:
   ADVANCE_PC_FETCH_AND_GOTO_NEXT 2
2:
   EXPORT_PC
   // Fetch the value, needed by nterp_get_instance_field_offset.
   movl    rINST, %ecx                     # ecx <- BA
   andl    $$0xf, %ecx                     # ecx <- A
   GET_VREG ARG3, %ecx                     # ecx <- v[A]
   movl rSELF:THREAD_SELF_OFFSET, ARG0
   movl 0(%esp), ARG1
   movl rPC, ARG2
   call nterp_get_instance_field_offset
   testl %eax, %eax
   jns 1b
   negl %eax
   // Reload the value as it may have moved.
   movl    rINST, %ecx                     # ecx <- BA
   andl    $$0xf, %ecx                     # ecx <- A
   GET_VREG %ecx, %ecx                     # ecx <- v[A]
   sarl    $$4, rINST
   GET_VREG rINST, rINST                   # vB (object we're operating on)
   testl   rINST, rINST                    # is object null?
   je      common_errNullObject
   POISON_HEAP_REF ecx
   movl %ecx, (rINST, %eax, 1)
   testl %ecx, %ecx
   je 5f
   movl rSELF:THREAD_CARD_TABLE_OFFSET, %eax
   shrl $$CARD_TABLE_CARD_SHIFT, rINST
   movb %al, (%eax, rINST, 1)
5:
   lock addl $$0, (%esp)
   ADVANCE_PC_FETCH_AND_GOTO_NEXT 2

NterpGetObjectInstanceField:
   // Fast-path which gets the field from thread-local cache.
%  fetch_from_thread_cache("%eax", miss_label="2f")
1:
   movl    rINST, %ecx                     # ecx <- BA
   sarl    $$4, %ecx                       # ecx <- B
   GET_VREG %ecx, %ecx                     # vB (object we're operating on)
   testl   %ecx, %ecx                      # is object null?
   je      common_errNullObject
   testb $$READ_BARRIER_TEST_VALUE, GRAY_BYTE_OFFSET(%ecx)
   movl (%ecx,%eax,1), %eax
   jnz 3f
   UNPOISON_HEAP_REF eax  // Affects flags, so we cannot unpoison before the jnz.
4:
   andb    $$0xf,rINSTbl                   # rINST <- A
   SET_VREG_OBJECT %eax, rINST             # fp[A] <- value
   ADVANCE_PC_FETCH_AND_GOTO_NEXT 2
2:
   EXPORT_PC
   movl rSELF:THREAD_SELF_OFFSET, ARG0
   movl 0(%esp), ARG1
   movl rPC, ARG2
   movl $$0, ARG3
   call nterp_get_instance_field_offset
   testl %eax, %eax
   jns 1b
   // For volatile fields, we return a negative offset. Remove the sign
   // and no need for any barrier thanks to the memory model.
   negl %eax
   jmp 1b
3:
   UNPOISON_HEAP_REF eax
   // reg00 is eax
   call art_quick_read_barrier_mark_reg00
   jmp 4b

NterpPutObjectStaticField:
   GET_VREG rINST, rINST
   // Fast-path which gets the field from thread-local cache.
%  fetch_from_thread_cache("%eax", miss_label="2f")
1:
   movl ART_FIELD_OFFSET_OFFSET(%eax), %ecx
   movl ART_FIELD_DECLARING_CLASS_OFFSET(%eax), %eax
   cmpl $$0, rSELF:THREAD_READ_BARRIER_MARK_REG00_OFFSET
   jne 3f
5:
   POISON_HEAP_REF ebx  // `rINST` is `%ebx` but we need to pass `ebx`.
   movl rINST, (%eax, %ecx, 1)
   testl rINST, rINST
   je 4f
   movl rSELF:THREAD_CARD_TABLE_OFFSET, %ecx
   shrl $$CARD_TABLE_CARD_SHIFT, %eax
   movb %cl, (%ecx, %eax, 1)
4:
   ADVANCE_PC_FETCH_AND_GOTO_NEXT 2
2:
   EXPORT_PC
   movl rSELF:THREAD_SELF_OFFSET, ARG0
   movl 0(%esp), ARG1
   movl rPC, ARG2
   movl rINST, ARG3
   call nterp_get_static_field
   // Reload the value as it may have moved.
   GET_VREG rINST, rINST
   testl MACRO_LITERAL(1), %eax
   je 1b
   CLEAR_VOLATILE_MARKER %eax
   movl ART_FIELD_OFFSET_OFFSET(%eax), %ecx
   movl ART_FIELD_DECLARING_CLASS_OFFSET(%eax), %eax
   cmpl $$0, rSELF:THREAD_READ_BARRIER_MARK_REG00_OFFSET
   jne 7f
6:
   POISON_HEAP_REF ebx  // `rINST` is `%ebx` but we need to pass `ebx`.
   movl rINST, (%eax, %ecx, 1)
   testl rINST, rINST
   je 8f
   movl rSELF:THREAD_CARD_TABLE_OFFSET, %ecx
   shrl $$CARD_TABLE_CARD_SHIFT, %eax
   movb %cl, (%ecx, %eax, 1)
8:
   lock addl $$0, (%esp)
   ADVANCE_PC_FETCH_AND_GOTO_NEXT 2
3:
   call art_quick_read_barrier_mark_reg00
   jmp 5b
7:
   call art_quick_read_barrier_mark_reg00
   jmp 6b

NterpGetObjectStaticField:
   // Fast-path which gets the field from thread-local cache.
%  fetch_from_thread_cache("%eax", miss_label="2f")
1:
   movl ART_FIELD_OFFSET_OFFSET(%eax), %ecx
   movl ART_FIELD_DECLARING_CLASS_OFFSET(%eax), %eax
   cmpl $$0, rSELF:THREAD_READ_BARRIER_MARK_REG00_OFFSET
   jne 5f
6:
   testb $$READ_BARRIER_TEST_VALUE, GRAY_BYTE_OFFSET(%eax)
   movl (%eax, %ecx, 1), %eax
   jnz 3f
   UNPOISON_HEAP_REF eax  // Affects flags, so we cannot unpoison before the jnz.
4:
   SET_VREG_OBJECT %eax, rINST             # fp[A] <- value
   ADVANCE_PC_FETCH_AND_GOTO_NEXT 2
2:
   EXPORT_PC
   movl rSELF:THREAD_SELF_OFFSET, ARG0
   movl 0(%esp), ARG1
   movl rPC, ARG2
   movl $$0, ARG3
   call nterp_get_static_field
   CLEAR_VOLATILE_MARKER %eax
   jmp 1b
3:
   UNPOISON_HEAP_REF eax
   call art_quick_read_barrier_mark_reg00
   jmp 4b
5:
   call art_quick_read_barrier_mark_reg00
   jmp 6b

NterpGetBooleanStaticField:
  OP_SGET load="movzbl", wide=0

NterpGetByteStaticField:
  OP_SGET load="movsbl", wide=0

NterpGetCharStaticField:
  OP_SGET load="movzwl", wide=0

NterpGetShortStaticField:
  OP_SGET load="movswl", wide=0

NterpGetWideStaticField:
  OP_SGET load="movl", wide=1

NterpGetIntStaticField:
  OP_SGET load="movl", wide=0

NterpPutStaticField:
  OP_SPUT rINST_reg=rINST, store="movl", wide=0

NterpPutBooleanStaticField:
NterpPutByteStaticField:
  OP_SPUT rINST_reg=rINSTbl, store="movb", wide=0

NterpPutCharStaticField:
NterpPutShortStaticField:
  OP_SPUT rINST_reg=rINSTw, store="movw", wide=0

NterpPutWideStaticField:
  OP_SPUT rINST_reg=rINST, store="movl", wide=1

NterpPutInstanceField:
  OP_IPUT rINST_reg=rINST, store="movl", wide=0

NterpPutBooleanInstanceField:
NterpPutByteInstanceField:
  OP_IPUT rINST_reg=rINSTbl, store="movb", wide=0

NterpPutCharInstanceField:
NterpPutShortInstanceField:
  OP_IPUT rINST_reg=rINSTw, store="movw", wide=0

NterpPutWideInstanceField:
  OP_IPUT rINST_reg=rINST, store="movl", wide=1

NterpGetBooleanInstanceField:
  OP_IGET load="movzbl", wide=0

NterpGetByteInstanceField:
  OP_IGET load="movsbl", wide=0

NterpGetCharInstanceField:
  OP_IGET load="movzwl", wide=0

NterpGetShortInstanceField:
  OP_IGET load="movswl", wide=0

NterpGetWideInstanceField:
  OP_IGET load="movl", wide=1

NterpGetInstanceField:
  OP_IGET load="movl", wide=0

NterpCallSuspendAndGotoNext:
    EXPORT_PC
    // Save branch offset.
    movl rINST, LOCAL0(%esp)
    call SYMBOL(art_quick_test_suspend)
    RESTORE_IBASE
    movl LOCAL0(%esp), rINST
    FETCH_INST
    GOTO_NEXT

NterpHandleHotnessOverflow:
    CHECK_AND_UPDATE_SHARED_MEMORY_METHOD if_hot=1f, if_not_hot=4f
1:
    movl rPC, %ecx
    movl rFP, ARG2
    // Save next PC.
    movl %ecx, LOCAL0(%esp)
    call nterp_hot_method
    testl %eax, %eax
    jne 3f
    // Fetch next PC.
    mov LOCAL0(%esp), rPC
2:
    FETCH_INST
    GOTO_NEXT
3:
    // Drop the current frame.
    movl -4(rREFS), %esp
    CFI_DEF_CFA(esp, PARAMETERS_SAVES_SIZE+CALLEE_SAVES_SIZE)
    DROP_PARAMETERS_SAVES
    CFI_DEF_CFA(esp, CALLEE_SAVES_SIZE)

    // Setup the new frame
    movl OSR_DATA_FRAME_SIZE(%eax), %ecx
    // Given stack size contains all callee saved registers, remove them.
    subl $$CALLEE_SAVES_SIZE, %ecx

    // Remember CFA.
    movl %esp, %ebp
    CFI_DEF_CFA_REGISTER(ebp)

    subl %ecx, %esp
    movl %esp, %edi               // edi := beginning of stack
    leal OSR_DATA_MEMORY(%eax), %esi  // esi := memory to copy
    rep movsb                     // while (ecx--) { *edi++ = *esi++ }

    // Fetch the native PC to jump to and save it in stack.
    pushl OSR_DATA_NATIVE_PC(%eax)
    CFI_ADJUST_CFA_OFFSET(4)

    subl MACRO_LITERAL(8), %esp
    CFI_ADJUST_CFA_OFFSET(8)
    pushl %eax
    CFI_ADJUST_CFA_OFFSET(4)
    // Free the memory holding OSR Data.
    call SYMBOL(NterpFree)
    addl MACRO_LITERAL(12), %esp
    CFI_ADJUST_CFA_OFFSET(-12)

    // Jump to the compiled code.
    ret
4:
    DO_SUSPEND_CHECK continue_label=2b


NterpHandleInvokeInterfaceOnObjectMethodRange:
   shrl $$16, %eax
   movl MIRROR_CLASS_VTABLE_OFFSET_32(%edx, %eax, 4), %eax
   jmp NterpCommonInvokeInstanceRange

NterpHandleInvokeInterfaceOnObjectMethod:
   shrl $$16, %eax
   movl MIRROR_CLASS_VTABLE_OFFSET_32(%edx, %eax, 4), %eax
   jmp NterpCommonInvokeInstance

// This is the logical end of ExecuteNterpImpl, where the frame info applies.
// EndExecuteNterpImpl includes the methods below as we want the runtime to
// see them as part of the Nterp PCs.
.cfi_endproc

END nterp_helper

// This is the end of PCs contained by the OatQuickMethodHeader created for the interpreter
// entry point.
    FUNCTION_TYPE(EndExecuteNterpImpl)
    ASM_HIDDEN SYMBOL(EndExecuteNterpImpl)
    .global SYMBOL(EndExecuteNterpImpl)
SYMBOL(EndExecuteNterpImpl):

// Entrypoints into runtime.
NTERP_TRAMPOLINE nterp_get_static_field, NterpGetStaticField
NTERP_TRAMPOLINE nterp_get_instance_field_offset, NterpGetInstanceFieldOffset
NTERP_TRAMPOLINE nterp_filled_new_array, NterpFilledNewArray
NTERP_TRAMPOLINE nterp_filled_new_array_range, NterpFilledNewArrayRange
NTERP_TRAMPOLINE nterp_get_class, NterpGetClass
NTERP_TRAMPOLINE nterp_allocate_object, NterpAllocateObject
NTERP_TRAMPOLINE nterp_get_method, NterpGetMethod
NTERP_TRAMPOLINE nterp_hot_method, NterpHotMethod
NTERP_TRAMPOLINE nterp_load_object, NterpLoadObject

DEFINE_FUNCTION nterp_deliver_pending_exception
    DELIVER_PENDING_EXCEPTION
END_FUNCTION nterp_deliver_pending_exception

// gen_mterp.py will inline the following definitions
// within [ExecuteNterpImpl, EndExecuteNterpImpl).
%def instruction_end():

    FUNCTION_TYPE(artNterpAsmInstructionEnd)
    ASM_HIDDEN SYMBOL(artNterpAsmInstructionEnd)
    .global SYMBOL(artNterpAsmInstructionEnd)
SYMBOL(artNterpAsmInstructionEnd):
    // artNterpAsmInstructionEnd is used as landing pad for exception handling.
    RESTORE_IBASE
    FETCH_INST
    GOTO_NEXT

%def instruction_start():

    FUNCTION_TYPE(artNterpAsmInstructionStart)
    ASM_HIDDEN SYMBOL(artNterpAsmInstructionStart)
    .global SYMBOL(artNterpAsmInstructionStart)
SYMBOL(artNterpAsmInstructionStart) = .L_op_nop
    .text

%def opcode_name_prefix():
%   return "nterp_"
%def opcode_start():
    ENTRY nterp_${opcode}
%def opcode_end():
    END nterp_${opcode}
    // Advance to the end of this handler. Causes error if we are past that point.
    .org nterp_${opcode} + NTERP_HANDLER_SIZE  // ${opcode} handler is too big!
%def opcode_slow_path_start(name):
    ENTRY ${name}
%def opcode_slow_path_end(name):
    END ${name}
