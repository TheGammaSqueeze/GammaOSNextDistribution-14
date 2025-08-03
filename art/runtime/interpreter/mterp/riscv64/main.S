%def header():
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

/*
 * This is a #include, not a %include, because we want the C pre-processor
 * to expand the macros into assembler assignment statements.
 */
#include "asm_support.h"
#include "arch/riscv64/asm_support_riscv64.S"

/**
 * RISC-V 64 ABI general notes
 *
 * References
 * - https://github.com/riscv-non-isa/riscv-elf-psabi-doc/blob/master/riscv-cc.adoc
 * - runtime/arch/riscv64/registers_riscv64.h
 *
 * 32 general purposes registers
 * - fixed purpose: zero, ra, sp, gp, tp, s1
 *     gp/scs: shadow call stack - do not clobber!
 *     s1/tr: ART thread register - do not clobber!
 * - temporaries: t0-t6
 * - arguments: a0-a7
 * - callee saved: ra, s0/fp, s2-s11
 *     s0 is flexible, available to use as a frame pointer if needed.
 *
 * 32 floating point registers
 * - temporaries: ft0-ft11
 * - arguments: fa0-fa7
 * - callee saved: fs0-fs11
 */

// Android references
//   Bytecodes: https://source.android.com/docs/core/runtime/dalvik-bytecode
//   Instruction formats: https://source.android.com/docs/core/runtime/instruction-formats
//   Shorty: https://source.android.com/docs/core/runtime/dex-format#shortydescriptor

// Fixed register usages in Nterp.
//    nickname  ABI    reg   purpose
#define xSELF    s1  // x9,   Thread* self pointer
#define xFP      s2  // x18,  interpreted frame pointer: to access locals and args
#define xPC      s3  // x19,  interpreted program counter: to fetch instructions
#define xINST    s4  // x20,  first 16-bit code unit of current instruction
#define xIBASE   s5  // x21,  interpreted instruction base pointer: for computed goto
#define xREFS    s6  // x22,  base of object references of dex registers

// DWARF registers reference
// https://github.com/riscv-non-isa/riscv-elf-psabi-doc/blob/master/riscv-dwarf.adoc
#define CFI_TMP  10  // DWARF register number for       a0/x10
#define CFI_DEX  19  // DWARF register number for xPC  /s3/x19
#define CFI_REFS 22  // DWARF register number for xREFS/s6/x22

// Synchronization
// This code follows the RISC-V atomics ABI specification [1].
//
// Object publication.
// new-instance and new-array operations must first perform a `fence w,w` "constructor fence" to
// ensure their new object references are correctly published with a subsequent SET_VREG_OBJECT.
//
// Volatile load/store.
// A volatile load is implemented as: fence rw,rw ; load ; fence r,rw.
// A 32-bit or 64-bit volatile store is implemented as: amoswap.{w,d}.rl
// A volatile store for a narrower type is implemented as: fence rw,w ; store ; fence rw,rw
//
// [1] https://github.com/riscv-non-isa/riscv-elf-psabi-doc/blob/master/riscv-atomic.adoc

// An assembly entry for nterp.
.macro OAT_ENTRY name
    .type \name, @function
    .hidden \name
    .global \name
    .balign 16
\name:
.endm

.macro SIZE name
    .size \name, .-\name
.endm

// Similar to ENTRY but without the CFI directives.
.macro NAME_START name
    .type \name, @function
    .hidden \name  // Hide this as a global symbol, so we do not incur plt calls.
    .global \name
    /* Cache alignment for function entry */
    .balign 16
\name:
.endm

.macro NAME_END name
  SIZE \name
.endm

// Macro for defining entrypoints into runtime. We don't need to save registers (we're not holding
// references there), but there is no kDontSave runtime method. So just use the kSaveRefsOnly
// runtime method.
.macro NTERP_TRAMPOLINE name, helper
ENTRY \name
    SETUP_SAVE_REFS_ONLY_FRAME
    call \helper
    RESTORE_SAVE_REFS_ONLY_FRAME
    ld t0, THREAD_EXCEPTION_OFFSET(xSELF)
    bnez t0, nterp_deliver_pending_exception
    ret
END \name
.endm

// Unpack code items from dex format.
// Input: \code_item
// Output:
//   - \regs: register count
//   - \outs: out count
//   - \ins: in count. If set to register "zero" (x0), load is skipped.
//   - \code_item: holds instruction array on exit
.macro FETCH_CODE_ITEM_INFO code_item, regs, outs, ins
    // Check LSB of \code_item. If 1, it's a compact dex file.
    BRANCH_IF_BIT_CLEAR \regs, \code_item, 0, 1f  // Regular dex.
    unimp  // Compact dex: unimplemented.
1:
    // Unpack values from regular dex format.
    lhu \regs, CODE_ITEM_REGISTERS_SIZE_OFFSET(\code_item)
    lhu \outs, CODE_ITEM_OUTS_SIZE_OFFSET(\code_item)
    .ifnc \ins, zero
      lhu \ins, CODE_ITEM_INS_SIZE_OFFSET(\code_item)
    .endif
    addi \code_item, \code_item, CODE_ITEM_INSNS_OFFSET
.endm

.macro EXPORT_PC
    sd xPC, -16(xREFS)
.endm

.macro TEST_IF_MARKING reg, label
    lb \reg, THREAD_IS_GC_MARKING_OFFSET(xSELF)
    bnez \reg, \label
.endm

.macro DO_SUSPEND_CHECK continue
    lwu t0, THREAD_FLAGS_OFFSET(xSELF)
    andi t0, t0, THREAD_SUSPEND_OR_CHECKPOINT_REQUEST
    beqz t0, \continue
    EXPORT_PC
    call art_quick_test_suspend
.endm

// Fetch one or more half-word units from an offset past the current PC.
// The offset is specified in 16-bit code units.
//
// A \width flag allows reading 32 bits (2 units) or 64 bits (4 units) from the offset.
// The RISC-V ISA supports unaligned accesses for these wider loads.
//
// If \width=8, \byte={0,1} indexes into the code unit at the offset.
//
// Default behavior loads one code unit with unsigned zext.
// The \signed flag is for signed sext, for shorter loads.
//
// Does not advance xPC.
.macro FETCH reg, count, signed=0, width=16, byte=0
    .if \width == 8
      .if \signed
        lb  \reg, (\count*2 + \byte)(xPC)
      .else
        lbu \reg, (\count*2 + \byte)(xPC)
      .endif
    .elseif \width == 16
      .if \signed
        lh  \reg, (\count*2)(xPC)
      .else
        lhu \reg, (\count*2)(xPC)
      .endif
    .elseif \width == 32
      .if \signed
        lw  \reg, (\count*2)(xPC)
      .else
        lwu \reg, (\count*2)(xPC)
      .endif
    .elseif \width == 64
      ld  \reg, (\count*2)(xPC)
    .else
      unimp  // impossible
    .endif
.endm

// Fetch the next instruction, from xPC into xINST.
// Does not advance xPC.
.macro FETCH_INST
    lhu xINST, (xPC)  // zero in upper 48 bits
.endm

// Fetch the next instruction, from xPC into xINST. Advance xPC by \count units, each 2 bytes.
//
// Immediates have a 12-bit offset range from xPC. Thus, \count can range from -1024 to 1023.
//
// Note: Must be placed AFTER anything that can throw an exception, or the exception catch may miss.
// Thus, this macro must be placed after EXPORT_PC.
.macro FETCH_ADVANCE_INST count
    lhu xINST, (\count*2)(xPC)  // zero in upper 48 bits
    addi xPC, xPC, (\count*2)
.endm

// Clobbers: \reg
.macro GET_INST_OPCODE reg
    and \reg, xINST, 0xFF
.endm

// Clobbers: \reg
.macro GOTO_OPCODE reg
    slliw \reg, \reg, ${handler_size_bits}
    add \reg, xIBASE, \reg
    jr \reg
.endm

.macro FETCH_FROM_THREAD_CACHE reg, miss_label, z0, z1
    // See art::InterpreterCache::IndexOf() for computing index of key within cache array.
    // Entry address:
    //   xSELF + OFFSET + ((xPC>>2 & xFF) << 4)
    // = xSELF + OFFSET + ((xPC & xFF<<2) << 2)
    // = xSELF + ((OFFSET>>2 + (xPC & xFF<<2)) << 2)
    // => ANDI, ADD, SH2ADD
#if (THREAD_INTERPRETER_CACHE_SIZE_LOG2 != 8)
#error Expected interpreter cache array size = 256 elements
#endif
#if (THREAD_INTERPRETER_CACHE_SIZE_SHIFT != 2)
#error Expected interpreter cache entry size = 16 bytes
#endif
#if ((THREAD_INTERPRETER_CACHE_OFFSET & 0x3) != 0)
#error Expected interpreter cache offset to be 4-byte aligned
#endif
    andi \z0, xPC, 0xFF << 2
    addi \z0, \z0, THREAD_INTERPRETER_CACHE_OFFSET >> 2
    sh2add \z0, \z0, xSELF  // z0 := entry's address
    ld \z1, (\z0)           // z1 := dex PC
    bne xPC, \z1, \miss_label
    ld \reg, 8(\z0)         // value: depends on context; see call site
.endm

// Inputs:
//   - a0
//   - xSELF
// Clobbers: t0
.macro CHECK_AND_UPDATE_SHARED_MEMORY_METHOD if_hot, if_not_hot
    lwu t0, ART_METHOD_ACCESS_FLAGS_OFFSET(a0)
    BRANCH_IF_BIT_CLEAR t0, t0, ART_METHOD_IS_MEMORY_SHARED_FLAG_BIT, \if_hot

    lwu t0, THREAD_SHARED_METHOD_HOTNESS_OFFSET(xSELF)  // t0 := hotness
    beqz t0, \if_hot

    addi t0, t0, -1  // increase hotness
    sw t0,  THREAD_SHARED_METHOD_HOTNESS_OFFSET(xSELF)
    j \if_not_hot
.endm

// Update xPC by \units code units. On back edges, perform hotness and suspend.
.macro BRANCH units
    sh1add xPC, \units, xPC
    blez \units, 2f  // If branch is <= 0, increase hotness and do a suspend check.
1:
    FETCH_INST
    GET_INST_OPCODE t0
    GOTO_OPCODE t0
2:
    ld a0, (sp)
    lhu t0, ART_METHOD_HOTNESS_COUNT_OFFSET(a0)  // t0 := hotness
#if (NTERP_HOTNESS_VALUE != 0)
#error Expected 0 for hotness value
#endif
    // If the counter is at zero (hot), handle it in the runtime.
    beqz t0, 3f
    addi t0, t0, -1  // increase hotness
    sh t0, ART_METHOD_HOTNESS_COUNT_OFFSET(a0)
    DO_SUSPEND_CHECK continue=1b
    j 1b
3:
    tail NterpHandleHotnessOverflow  // arg a0 (ArtMethod*)
.endm

// Increase method hotness before starting the method.
// Hardcoded:
// - a0: ArtMethod*
// Clobbers: t0
.macro START_EXECUTING_INSTRUCTIONS
    ld a0, (sp)
    lhu t0, ART_METHOD_HOTNESS_COUNT_OFFSET(a0)  // t0 := hotness
#if (NTERP_HOTNESS_VALUE != 0)
#error Expected 0 for hotness value
#endif
    // If the counter is at zero (hot), handle it in the runtime.
    beqz t0, 3f
    addi t0, t0, -1  // increase hotness
    sh t0, ART_METHOD_HOTNESS_COUNT_OFFSET(a0)
1:
    DO_SUSPEND_CHECK continue=2f
2:
    FETCH_INST
    GET_INST_OPCODE t0
    GOTO_OPCODE t0
3:
    CHECK_AND_UPDATE_SHARED_MEMORY_METHOD if_hot=4f, if_not_hot=1b
4:
    mv a1, zero  // dex_pc_ptr=nullptr
    mv a2, zero  // vergs=nullptr
    call nterp_hot_method
    j 2b
.endm

// 64 bit read
// Clobbers: \reg
// Safe if \reg == \vreg.
.macro GET_VREG_WIDE reg, vreg
    sh2add \reg, \vreg, xFP  // vreg addr in register array
    ld \reg, (\reg)          // reg := fp[vreg](lo) | fp[vreg+1](hi)
.endm

// 64 bit write
// Clobbers: z0
.macro SET_VREG_WIDE reg, vreg, z0
    sh2add \z0, \vreg, xFP    // vreg addr in register array
    sd \reg, (\z0)            // fp[vreg] := reg(lo) ; fp[vreg+1] := reg(hi)
    sh2add \z0, \vreg, xREFS  // vreg addr in reference array
    sd zero, (\z0)            // refs[vreg] := null ; refs[vreg+1] := null
.endm

// Object read
// Clobbers: \reg
// Safe if \reg == \vreg.
.macro GET_VREG_OBJECT reg, vreg
    sh2add \reg, \vreg, xREFS  // vreg addr in reference array
    lwu \reg, (\reg)           // reg := refs[vreg]
.endm

// Object write
// Clobbers: z0
.macro SET_VREG_OBJECT reg, vreg, z0
    sh2add \z0, \vreg, xFP    // vreg addr in register array
    sw \reg, (\z0)            // fp[vreg] := reg
    sh2add \z0, \vreg, xREFS  // vreg addr in reference array
    sw \reg, (\z0)            // refs[vreg] := reg
.endm

// Floating-point 64 bit read
// Clobbers: \reg, \vreg
.macro GET_VREG_DOUBLE reg, vreg
    sh2add \vreg, \vreg, xFP  // vreg addr in register array
    fld \reg, (\vreg)         // reg := fp[vreg](lo) | fp[vreg+1](hi)
.endm

// Floating-point 64 bit write
// Clobbers: \reg, z0
.macro SET_VREG_DOUBLE reg, vreg, z0
    sh2add \z0, \vreg, xFP    // vreg addr in register array
    fsd \reg, (\z0)           // fp[vreg] := reg(lo) ; fp[vreg+1] := reg(hi)
    sh2add \z0, \vreg, xREFS  // vreg addr in reference array
    sd zero, (\z0)            // refs[vreg] := null ; refs[vreg+1] := null
.endm

// Put "%def" definitions after ".macro" definitions for proper expansion. %def is greedy.

// Typed read, defaults to 32-bit read
// Note: An object ref requires LWU, or LW;ZEXT.W.
// Clobbers: \reg
// Safe if \reg == \vreg.
%def get_vreg(reg, vreg, width=32, is_wide=False, is_unsigned=False):
%  if is_wide or width == 64:
     GET_VREG_WIDE $reg, $vreg
%  elif is_unsigned:
     sh2add $reg, $vreg, xFP  // vreg addr in register array
     lwu $reg, ($reg)         // reg := fp[vreg], zext
%  else:
     sh2add $reg, $vreg, xFP  // vreg addr in register array
     lw $reg, ($reg)          // reg := fp[vreg]
%#:

// Typed write, defaults to 32-bit write.
// Note: Incorrect for an object ref; it requires 2nd SW into xREFS.
// Clobbers: z0
%def set_vreg(reg, vreg, z0, width=32, is_wide=False):
%  if is_wide or width == 64:
     SET_VREG_WIDE $reg, $vreg, $z0
%  else:
     sh2add $z0, $vreg, xFP    // vreg addr in register array
     sw $reg, ($z0)            // fp[vreg] := reg
     sh2add $z0, $vreg, xREFS  // vreg addr in reference array
     sw zero, ($z0)            // refs[vreg] := null
%#:

// Floating-point read, defaults to 32-bit read.
// Clobbers: reg, vreg
%def get_vreg_float(reg, vreg, is_double=False):
%  if is_double:
     GET_VREG_DOUBLE $reg, $vreg
%  else:
     sh2add $vreg, $vreg, xFP  // vreg addr in register array
     flw $reg, ($vreg)         // reg := fp[vreg]
%#:

// Floating-point write, defaults to 32-bit write.
// Clobbers: reg, z0
%def set_vreg_float(reg, vreg, z0, is_double=False):
%  if is_double:
     SET_VREG_DOUBLE $reg, $vreg, $z0
%  else:
     sh2add $z0, $vreg, xFP    // vreg addr in register array
     fsw $reg, ($z0)           // fp[vreg] := reg
     sh2add $z0, $vreg, xREFS  // vreg addr in reference array
     sw zero, ($z0)            // refs[vreg] := null
%#:

%def entry():
/*
 * ArtMethod entry point.
 *
 * On entry:
 *  a0     ArtMethod* callee
 *  a1-a7  method parameters
 */
OAT_ENTRY ExecuteNterpWithClinitImpl
#if MIRROR_CLASS_STATUS_SHIFT < 12
#error mirror class status bits cannot use LUI load technique
#endif
    .cfi_startproc
    // For simplicity, we don't do a read barrier here, but instead rely
    // on art_quick_resolution_trampoline to always have a suspend point before
    // calling back here.
    lwu t0, ART_METHOD_DECLARING_CLASS_OFFSET(a0)
    lw t1, MIRROR_CLASS_STATUS_OFFSET(t0)  // t1 := status word, sext
    lui t2, MIRROR_CLASS_STATUS_VISIBLY_INITIALIZED << (MIRROR_CLASS_STATUS_SHIFT - 12)
    // The unsigned comparison works in tandem with the 64-bit sign-extension of
    // the status bits at the top of the 32-bit word. The order of the status
    // constants (sign extended from LUI) is unchanged with unsigned comparison.
    bgeu t1, t2, ExecuteNterpImpl
    lui t2, MIRROR_CLASS_STATUS_INITIALIZED << (MIRROR_CLASS_STATUS_SHIFT - 12)
    bltu t1, t2, .Linitializing_check
    fence w, w
    j ExecuteNterpImpl
.Linitializing_check:
    lui t2, MIRROR_CLASS_STATUS_INITIALIZING << (MIRROR_CLASS_STATUS_SHIFT - 12)
    bltu t1, t2, .Lresolution_trampoline
    lwu t1, MIRROR_CLASS_CLINIT_THREAD_ID_OFFSET(t0)
    lwu t0, THREAD_TID_OFFSET(xSELF)
    beq t0, t1, ExecuteNterpImpl
.Lresolution_trampoline:
    tail art_quick_resolution_trampoline
    .cfi_endproc
    .type EndExecuteNterpWithClinitImpl, @function
    .hidden EndExecuteNterpWithClinitImpl
    .global EndExecuteNterpWithClinitImpl
EndExecuteNterpWithClinitImpl:

OAT_ENTRY ExecuteNterpImpl
   .cfi_startproc
%  setup_nterp_frame(cfi_refs="CFI_REFS", refs="xREFS", fp="xFP", pc="xPC", regs="s7", ins="s8", spills_sp="s9", z0="t0", z1="t1", z2="t2", z3="t3", uniq="entry")
                            // xREFS := callee refs array
                            // xFP   := callee fp array
                            // xPC   := callee dex array
                            // s7    := refs/fp vreg count
                            // s8    := ins count
                            // s9    := post-spills pre-frame sp
                            // sp    := post-frame sp
   CFI_DEFINE_DEX_PC_WITH_OFFSET(/*tmpReg*/CFI_TMP, /*dexReg*/CFI_DEX, /*dexOffset*/0)

   // Fast path: zero args.
   beqz s8, .Lentry_go

   sub s7, s7, s8           // s7 := a1 index in fp/refs
   lwu s10, ART_METHOD_ACCESS_FLAGS_OFFSET(a0)
                            // s10 := method flags

   // Fast path: all reference args.
   sh2add t0, s7, xFP       // t0 := &xFP[a1]
   sh2add t1, s7, xREFS     // t1 := &xREFS[a1]
   BRANCH_IF_BIT_CLEAR t2, s10, ART_METHOD_NTERP_ENTRY_POINT_FAST_PATH_FLAG_BIT, .Lentry_a1
%  setup_ref_args_and_go(fp="t0", refs="t1", refs_end="xFP", spills_sp="s9", z0="t2", z1="t3", done=".Lentry_go")

   // Fast path: instance with zero args.
.Lentry_a1:
   bexti s10, s10, ART_METHOD_IS_STATIC_FLAG_BIT
                            // s10 := 1 if static, 0 if instance
   bnez s10, .Lentry_shorty
   sw a1, (t0)
   sw a1, (t1)
   li t2, 1
   beq s8, t2, .Lentry_go

   // Slow path: runtime call to obtain shorty, full setup from managed ABI.
.Lentry_shorty:
   SPILL_ALL_ARGUMENTS
   // TODO: Better way to get shorty
   call NterpGetShorty      // arg a0
   mv s11, a0               // s11 := shorty
   RESTORE_ALL_ARGUMENTS

   // temporaries are trashed, recompute some values
   sh2add t0, s7, xFP       // t0 := &xFP[a1]
   sh2add t1, s7, xREFS     // t1 := &xREFS[a1]
   addi t2, s11, 1          // t2 := shorty arg (skip return type)
   xori s10, s10, 1         // s10 := 0 if static, 1 if instance
   slliw t3, s10, 2         // t3 := (static) 0, (instance) 4: fp/refs/outs byte offset
   // constant setup for gpr/fpr shorty comparisons
   li s0, 'D'               // s0 := double char (unused fp)
   li s4, 'F'               // s4 := float char (unused xINST)
   li s5, 'J'               // s5 := long char (unused xIBASE)
   li s8, 'L'               // s8 := ref char (unused ins count)
   bnez s10, .Lentry_args   // instance a1 already stored into callee's xFP and xREFS

%  store_gpr_to_vreg(gpr="a1", offset="t3", shorty="t2", fp="t0", refs="t1", z0="t4", z1="t5", D="s0", F="s4", J="s5", L="s8", next=".Lentry_fargs")

.Lentry_args:
   // linear scan through shorty: extract non-float args
%  store_gpr_to_vreg(gpr="a2", offset="t3", shorty="t2", fp="t0", refs="t1", z0="t4", z1="t5", D="s0", F="s4", J="s5", L="s8", next=".Lentry_fargs")
%  store_gpr_to_vreg(gpr="a3", offset="t3", shorty="t2", fp="t0", refs="t1", z0="t4", z1="t5", D="s0", F="s4", J="s5", L="s8", next=".Lentry_fargs")
%  store_gpr_to_vreg(gpr="a4", offset="t3", shorty="t2", fp="t0", refs="t1", z0="t4", z1="t5", D="s0", F="s4", J="s5", L="s8", next=".Lentry_fargs")
%  store_gpr_to_vreg(gpr="a5", offset="t3", shorty="t2", fp="t0", refs="t1", z0="t4", z1="t5", D="s0", F="s4", J="s5", L="s8", next=".Lentry_fargs")
%  store_gpr_to_vreg(gpr="a6", offset="t3", shorty="t2", fp="t0", refs="t1", z0="t4", z1="t5", D="s0", F="s4", J="s5", L="s8", next=".Lentry_fargs")
%  store_gpr_to_vreg(gpr="a7", offset="t3", shorty="t2", fp="t0", refs="t1", z0="t4", z1="t5", D="s0", F="s4", J="s5", L="s8", next=".Lentry_fargs")
   // We drained arg registers, so continue from caller stack's out array. Unlike the reference-only
   // fast-path, the continuation offset in the out array can vary, depending on the presence of
   // 64-bit values in the arg registers. \offset tracks this value as a byte offset.
   addi t5, s9, (NTERP_SIZE_SAVE_CALLEE_SAVES + 8)
                            // t5 := (caller) outs array base address
   add t4, t3, t0           // t4 := (callee) &FP[next]
   add t1, t3, t1           // t1 := (callee) &REFS[next]
   add t3, t3, t5           // t3 := (caller) &OUTS[next]
%  store_outs_to_vregs(outs="t3", shorty="t2", fp="t4", refs="t1", z0="t5", z1="t6", D="s0", F="s4", J="s5", L="s8", next=".Lentry_fargs")
                            // t0 = &xFP[a1], unclobbered
.Lentry_fargs:
   addi t1, s11, 1          // t1 := shorty arg (skip return type)
   slliw t2, s10, 2         // t2 := starting byte offset for fp/outs, static and instance
   // linear scan through shorty: extract float args
%  store_fpr_to_vreg(fpr="fa0", offset="t2", shorty="t1", fp="t0", z0="t3", z1="t4", D="s0", F="s4", J="s5", next=".Lentry_go")
%  store_fpr_to_vreg(fpr="fa1", offset="t2", shorty="t1", fp="t0", z0="t3", z1="t4", D="s0", F="s4", J="s5", next=".Lentry_go")
%  store_fpr_to_vreg(fpr="fa2", offset="t2", shorty="t1", fp="t0", z0="t3", z1="t4", D="s0", F="s4", J="s5", next=".Lentry_go")
%  store_fpr_to_vreg(fpr="fa3", offset="t2", shorty="t1", fp="t0", z0="t3", z1="t4", D="s0", F="s4", J="s5", next=".Lentry_go")
%  store_fpr_to_vreg(fpr="fa4", offset="t2", shorty="t1", fp="t0", z0="t3", z1="t4", D="s0", F="s4", J="s5", next=".Lentry_go")
%  store_fpr_to_vreg(fpr="fa5", offset="t2", shorty="t1", fp="t0", z0="t3", z1="t4", D="s0", F="s4", J="s5", next=".Lentry_go")
%  store_fpr_to_vreg(fpr="fa6", offset="t2", shorty="t1", fp="t0", z0="t3", z1="t4", D="s0", F="s4", J="s5", next=".Lentry_go")
%  store_fpr_to_vreg(fpr="fa7", offset="t2", shorty="t1", fp="t0", z0="t3", z1="t4", D="s0", F="s4", J="s5", next=".Lentry_go")
   addi t3, s9, (NTERP_SIZE_SAVE_CALLEE_SAVES + 8)
                            // t3 := (caller) outs array base address
   add t0, t2, t0           // t0 := (callee) &FP[next]
   add t2, t2, t3           // t2 := (caller) &OUTS[next]
%  store_float_outs_to_vregs(outs="t2", shorty="t1", fp="t0", z0="t3", D="s0", F="s4", J="s5", next=".Lentry_go")

.Lentry_go:
    la xIBASE, artNterpAsmInstructionStart
    START_EXECUTING_INSTRUCTIONS
    // NOTE: no fallthrough
    // cfi info continues, and covers the whole nterp implementation.
    SIZE ExecuteNterpImpl

%def footer():
/*
 * ===========================================================================
 *  Common subroutines and data
 * ===========================================================================
 */

    .text
    .align  2


// Enclose all code below in a symbol (which gets printed in backtraces).
NAME_START nterp_helper

common_errArrayIndex:
    EXPORT_PC
    // CALL preserves RA for stack walking.
    call art_quick_throw_array_bounds  // args a0 (index), a1 (length)

common_errDivideByZero:
    EXPORT_PC
    // CALL preserves RA for stack walking.
    call art_quick_throw_div_zero

common_errNullObject:
    EXPORT_PC
    // CALL preserves RA for stack walking.
    call art_quick_throw_null_pointer_exception

NterpInvokeVirtual:
%  nterp_invoke_virtual()
NterpInvokeSuper:
%  nterp_invoke_super()
NterpInvokeDirect:
%  nterp_invoke_direct()
NterpInvokeStringInit:
%  nterp_invoke_string_init()
NterpInvokeStatic:
%  nterp_invoke_static()
NterpInvokeInterface:
%  nterp_invoke_interface()
NterpInvokePolymorphic:
%  nterp_invoke_polymorphic()
NterpInvokeCustom:
%  nterp_invoke_custom()
NterpInvokeVirtualRange:
%  nterp_invoke_virtual_range()
NterpInvokeSuperRange:
%  nterp_invoke_super_range()
NterpInvokeDirectRange:
%  nterp_invoke_direct_range()
NterpInvokeStringInitRange:
%  nterp_invoke_string_init_range()
NterpInvokeStaticRange:
%  nterp_invoke_static_range()
NterpInvokeInterfaceRange:
%  nterp_invoke_interface_range()
NterpInvokePolymorphicRange:
%  nterp_invoke_polymorphic_range()
NterpInvokeCustomRange:
%  nterp_invoke_custom_range()

// Arg a0: ArtMethod*
NterpHandleHotnessOverflow:
   CHECK_AND_UPDATE_SHARED_MEMORY_METHOD if_hot=.Lhotspill_hot, if_not_hot=.Lhotspill_suspend
.Lhotspill_hot:
   mv a1, xPC
   mv a2, xFP
   call nterp_hot_method  // args a0, a1, a2
   bnez a0, .Lhotspill_osr
.Lhotspill_advance:
   FETCH_INST
   GET_INST_OPCODE t0
   GOTO_OPCODE t0
.Lhotspill_osr:
   // a0 = OsrData*
   // Drop most of the current nterp frame, but keep the callee-saves.
   // The nterp callee-saves (count and layout) match the OSR frame's callee-saves.
   ld sp, -8(xREFS)  // caller's interpreted frame pointer
   .cfi_def_cfa sp, NTERP_SIZE_SAVE_CALLEE_SAVES
   lwu t0, OSR_DATA_FRAME_SIZE(a0)
   addi t0, t0, -NTERP_SIZE_SAVE_CALLEE_SAVES  // t0 := osr frame - callee saves, in bytes
   mv s7, sp         // Remember CFA in a callee-save register.
   .cfi_def_cfa_register s7
   sub sp, sp, t0    // OSR size guaranteed to be stack aligned (16 bytes).

   addi t1, a0, OSR_DATA_MEMORY  // t1 := read start
   add t1, t1, t0                // t1 := read end (exclusive)
   mv t2, s7                     // t2 := write end (exclusive)
   // t0 >= 8 (OSR places ArtMethod* at bottom of frame), so loop will terminate.
.Lhotspill_osr_copy_loop:
   addi t1, t1, -8
   ld t3, (t1)
   addi t2, t2, -8
   sd t3, (t2)
   bne t2, sp, .Lhotspill_osr_copy_loop

   ld s8, OSR_DATA_NATIVE_PC(a0)  // s8 := native PC; jump after free
   call free  // arg a0; release OsrData*
   jr s8      // Jump to the compiled code.
.Lhotspill_suspend:
   DO_SUSPEND_CHECK continue=.Lhotspill_advance
   j .Lhotspill_advance

// This is the logical end of ExecuteNterpImpl, where the frame info applies.
.cfi_endproc

NterpToNterpInstance:
%  nterp_to_nterp_instance()
NterpToNterpStringInit:
%  nterp_to_nterp_string_init()
NterpToNterpStatic:
%  nterp_to_nterp_static()
NterpToNterpInstanceRange:
%  nterp_to_nterp_instance_range()
NterpToNterpStringInitRange:
%  nterp_to_nterp_string_init_range()
NterpToNterpStaticRange:
%  nterp_to_nterp_static_range()

NAME_END nterp_helper

// EndExecuteNterpImpl includes the methods after .cfi_endproc, as we want the runtime to see them
// as part of the Nterp PCs. This label marks the end of PCs contained by the OatQuickMethodHeader
// created for the interpreter entry point.
    .type EndExecuteNterpImpl, @function
    .hidden EndExecuteNterpImpl
    .global EndExecuteNterpImpl
EndExecuteNterpImpl:

// Entrypoints into runtime.
NTERP_TRAMPOLINE nterp_allocate_object, NterpAllocateObject
NTERP_TRAMPOLINE nterp_filled_new_array, NterpFilledNewArray
NTERP_TRAMPOLINE nterp_filled_new_array_range, NterpFilledNewArrayRange
NTERP_TRAMPOLINE nterp_get_class, NterpGetClass
NTERP_TRAMPOLINE nterp_get_instance_field_offset, NterpGetInstanceFieldOffset
NTERP_TRAMPOLINE nterp_get_method, NterpGetMethod
NTERP_TRAMPOLINE nterp_get_static_field, NterpGetStaticField
NTERP_TRAMPOLINE nterp_hot_method, NterpHotMethod
NTERP_TRAMPOLINE nterp_load_object, NterpLoadObject

ENTRY nterp_deliver_pending_exception
    DELIVER_PENDING_EXCEPTION
END nterp_deliver_pending_exception

// gen_mterp.py will inline the following definitions
// within [ExecuteNterpImpl, EndExecuteNterpImpl).
%def instruction_start():
    .type artNterpAsmInstructionStart, @function
    .hidden artNterpAsmInstructionStart
    .global artNterpAsmInstructionStart
artNterpAsmInstructionStart = .L_op_nop
    .text

%def instruction_end():
    .type artNterpAsmInstructionEnd, @function
    .hidden artNterpAsmInstructionEnd
    .global artNterpAsmInstructionEnd
artNterpAsmInstructionEnd:
    // artNterpAsmInstructionEnd is used as landing pad for exception handling.
    // xPC (S3) for the exception handler was set just prior to the long jump coming here.
    FETCH_INST
    GET_INST_OPCODE t0
    GOTO_OPCODE t0

%def opcode_pre():
%   pass
%def opcode_name_prefix():
%   return "nterp_"
%def opcode_start():
    NAME_START nterp_${opcode}
%def opcode_end():
    NAME_END nterp_${opcode}
%def opcode_slow_path_start(name):
    NAME_START ${name}
%def opcode_slow_path_end(name):
    NAME_END ${name}
