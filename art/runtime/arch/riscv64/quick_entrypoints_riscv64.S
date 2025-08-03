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
#include "interpreter/cfi_asm_support.h"

#include "arch/quick_alloc_entrypoints.S"
#include "arch/quick_field_entrypoints.S"


// Wrap ExecuteSwitchImpl in assembly method which specifies DEX PC for unwinding.
//  Argument 0: a0: The context pointer for ExecuteSwitchImpl.
//  Argument 1: a1: Pointer to the templated ExecuteSwitchImpl to call.
//  Argument 2: a2: The value of DEX PC (memory address of the methods bytecode).
ENTRY ExecuteSwitchImplAsm
    INCREASE_FRAME 16
    SAVE_GPR s1, 0
    SAVE_GPR ra, 8

    mv s1, a2   // s1 = DEX PC
    CFI_DEFINE_DEX_PC_WITH_OFFSET(0 /* a0 */, 9 /* s1, a.k.a. x9 */, 0)
    jalr a1     // Call the wrapped method.

    RESTORE_GPR s1, 0
    RESTORE_GPR ra, 8
    DECREASE_FRAME 16
    ret
END ExecuteSwitchImplAsm


.macro INVOKE_STUB_CREATE_FRAME
    // Save RA, FP, xSELF (current thread), A4, A5 (they will be needed in the invoke stub return).
    INCREASE_FRAME 48
    // Slot (8*0) is used for `ArtMethod*` (if no args), args or padding, see below.
    SAVE_GPR xSELF, (8*1)
    SAVE_GPR a4,    (8*2)
    SAVE_GPR a5,    (8*3)
    SAVE_GPR fp,    (8*4)  // Store FP just under the return address.
    SAVE_GPR ra,    (8*5)

    // Make the new FP point to the location where we stored the old FP.
    // Some stack-walking tools may rely on this simply-linked list of saved FPs.
    addi fp, sp, (8*4)  // save frame pointer
    .cfi_def_cfa fp, 48 - (8*4)

    // We already have space for `ArtMethod*` on the stack but we need space for args above
    // the `ArtMethod*`, so add sufficient space now, pushing the `ArtMethod*` slot down.
    addi t0, a2, 0xf    // Reserve space for arguments and
    andi t0, t0, ~0xf   // round up for 16-byte stack alignment.
    sub  sp, sp, t0

    mv xSELF, a3

    // Copy arguments on stack (4 bytes per slot):
    //   A1: source address
    //   A2: arguments length
    //   T0: destination address if there are any args.

    beqz a2, 2f      // loop through 4-byte arguments from the last to the first
    addi t0, sp, 8   // destination address is bottom of the stack + 8 bytes for ArtMethod* (null)
1:
    addi a2, a2, -4
    add  t1, a1, a2  // T1 is the source address of the next copied argument
    lw   t2, (t1)    // T2 is the 4 bytes at address T1
    add  t1, t0, a2  // T1 is the destination address of the next copied argument
    sw   t2, (t1)    // save T2 at the destination address T1
    bnez a2, 1b
2:
    sd zero, (sp)  // Store null into ArtMethod* at bottom of frame.
.endm


.macro INVOKE_STUB_CALL_AND_RETURN
    // Call the method.
    ld   t0, ART_METHOD_QUICK_CODE_OFFSET_64(a0)
    jalr t0

    addi sp, fp, -(8*4)  // restore SP (see `INVOKE_STUB_CREATE_FRAME`)
    .cfi_def_cfa sp, 48

    // Restore ra, fp, xSELF (current thread) a4 (shorty), a5 (result pointer) from stack.
    RESTORE_GPR xSELF, (8*1)
    RESTORE_GPR a4,    (8*2)
    RESTORE_GPR a5,    (8*3)
    RESTORE_GPR fp,    (8*4)
    RESTORE_GPR ra,    (8*5)
    DECREASE_FRAME 48

    // Load result type (1-byte symbol) from a5.
    // Check result type and store the correct register into the jvalue in memory at a4 address.
    lbu t0, (a5)

    li t1, 'V'  // void (do not store result at all)
    beq t1, t0, 1f

    li t1, 'D'  // double
    beq t1, t0, 2f

    li t1, 'F'  // float
    beq t1, t0, 3f

    // Otherwise, result is in a0 (either 8 or 4 bytes, but it is fine to store 8 bytes as the
    // upper bytes in a0 in that case are zero, and jvalue has enough space).
    sd a0, (a4)
1:
    ret

2:  // double: result in fa0 (8 bytes)
    fsd fa0, (a4)
    ret

3:  // float: result in fa0 (4 bytes)
    fsw fa0, (a4)
    ret
.endm


ENTRY art_deliver_pending_exception
    DELIVER_PENDING_EXCEPTION
END art_deliver_pending_exception


// The size of the handler emitted by `INVOKE_STUB_LOAD_REG` below.
#define INVOKE_STUB_LOAD_REG_SIZE 8

// The offset within `INVOKE_STUB_LOAD_REG` for skipping arguments.
#define INVOKE_STUB_LOAD_REG_SKIP_OFFSET 6

// Macro for loading an argument into a register.
//  load - instruction used for loading,
//  reg - the register to load,
//  args - pointer to next argument,
//  size - the size of the register - 4 or 8 bytes, used as an offset for the load,
//  handler_reg - the register with the address of the handler (points to this handler on entry),
//  handler_diff - the difference in bytes from the current to the next handler,
//  cont - the base name of the label for continuing the shorty processing loop,
//  sfx - suffix added to all labels to make labels unique for different users.
.macro INVOKE_STUB_LOAD_REG load, reg, args, size, handler_reg, handler_diff, cont, sfx
.Linvoke_stub_\load\reg\sfx:
    \load  \reg, -\size(\args)
    c.addi \handler_reg, \handler_diff
.org .Linvoke_stub_\load\reg\sfx + INVOKE_STUB_LOAD_REG_SKIP_OFFSET  // Enforce skip offset.
    c.j    \cont\sfx
.org .Linvoke_stub_\load\reg\sfx + INVOKE_STUB_LOAD_REG_SIZE  // Enforce handler size.
.endm


// Fill registers a1 to a7 and fa0 to fa7 with parameters.
// Parse the passed shorty to determine which register to load.
//  a5 - shorty,
//  t0 - points to arguments on the stack if any (undefined for static method without args),
//  sfx - suffix added to all labels to make labels unique for different users.
.macro INVOKE_STUB_LOAD_ALL_ARGS sfx
    addi t1, a5, 1                     // Load shorty address, plus one to skip the return type.

    // Load this (if instance method) and record the number of GPRs to fill.
    .ifc \sfx, _instance
        lwu  a1, (t0)                  // Load "this" parameter,
        addi t0, t0, 4                 // and increment arg pointer.
        .equ NUM_GPRS_TO_FILL, 6
    .else
        .equ NUM_GPRS_TO_FILL, 7
    .endif
    .equ NUM_FPRS_TO_FILL, 8

    // Load addresses for routines that load argument GPRs and FPRs.
    lla  t4, .Lreg_handlers_start\sfx  // First handler for non-FP args.
    addi t5, t4, (3 * NUM_GPRS_TO_FILL * INVOKE_STUB_LOAD_REG_SIZE)  // First handler for FP args.

    // Loop to fill registers.
.Lfill_regs\sfx:
    lb   t2, (t1)                      // Load next character in signature,
    addi t1, t1, 1                     // and increment.
    beqz t2, .Lcall_method\sfx         // Exit at end of signature. Shorty 0 terminated.

    li   t3, 'L'
    beq  t2, t3, .Lload_reference\sfx  // Is this a reference?

    li   t3, 'J'
    beq  t2, t3, .Lload_long\sfx       // Is this a long?

    li   t3, 'F'
    beq  t2, t3, .Lload_float\sfx      // Is this a float?

    li   t3, 'D'
    beq  t2, t3, .Lload_double\sfx     // Is this a double?

    // Everything else uses a 4-byte value sign-extened to a 64 bit GPR.
    addi t0, t0, 4
    jalr x0, 0(t4)

.Lload_reference\sfx:
    addi t0, t0, 4
    jalr x0, (NUM_GPRS_TO_FILL * INVOKE_STUB_LOAD_REG_SIZE)(t4)

.Lload_long\sfx:
    addi t0, t0, 8
    jalr x0, (2 * NUM_GPRS_TO_FILL * INVOKE_STUB_LOAD_REG_SIZE)(t4)

.Lload_float\sfx:
    addi t0, t0, 4
    jalr x0, 0(t5)

.Lload_double\sfx:
    addi t0, t0, 8
    jalr x0, (NUM_FPRS_TO_FILL * INVOKE_STUB_LOAD_REG_SIZE)(t5)

.Lreg_handlers_start\sfx:

// Handlers for loading other args (not reference/long/float/double) into GPRs.
    .ifnc \sfx, _instance
        INVOKE_STUB_LOAD_REG lw, a1, t0, 4, t4, INVOKE_STUB_LOAD_REG_SIZE, .Lfill_regs, \sfx
    .endif
    INVOKE_STUB_LOAD_REG lw, a2, t0, 4, t4, INVOKE_STUB_LOAD_REG_SIZE, .Lfill_regs, \sfx
    INVOKE_STUB_LOAD_REG lw, a3, t0, 4, t4, INVOKE_STUB_LOAD_REG_SIZE, .Lfill_regs, \sfx
    INVOKE_STUB_LOAD_REG lw, a4, t0, 4, t4, INVOKE_STUB_LOAD_REG_SIZE, .Lfill_regs, \sfx
    INVOKE_STUB_LOAD_REG lw, a5, t0, 4, t4, INVOKE_STUB_LOAD_REG_SIZE, .Lfill_regs, \sfx
    INVOKE_STUB_LOAD_REG lw, a6, t0, 4, t4, INVOKE_STUB_LOAD_REG_SIZE, .Lfill_regs, \sfx
    INVOKE_STUB_LOAD_REG lw, a7, t0, 4, t4, INVOKE_STUB_LOAD_REG_SKIP_OFFSET, .Lfill_regs, \sfx

// Handlers for loading reference args into GPRs.
    .ifnc \sfx, _instance
        INVOKE_STUB_LOAD_REG lwu, a1, t0, 4, t4, INVOKE_STUB_LOAD_REG_SIZE, .Lfill_regs, \sfx
    .endif
    INVOKE_STUB_LOAD_REG lwu, a2, t0, 4, t4, INVOKE_STUB_LOAD_REG_SIZE, .Lfill_regs, \sfx
    INVOKE_STUB_LOAD_REG lwu, a3, t0, 4, t4, INVOKE_STUB_LOAD_REG_SIZE, .Lfill_regs, \sfx
    INVOKE_STUB_LOAD_REG lwu, a4, t0, 4, t4, INVOKE_STUB_LOAD_REG_SIZE, .Lfill_regs, \sfx
    INVOKE_STUB_LOAD_REG lwu, a5, t0, 4, t4, INVOKE_STUB_LOAD_REG_SIZE, .Lfill_regs, \sfx
    INVOKE_STUB_LOAD_REG lwu, a6, t0, 4, t4, INVOKE_STUB_LOAD_REG_SIZE, .Lfill_regs, \sfx
    INVOKE_STUB_LOAD_REG lwu, a7, t0, 4, t4, INVOKE_STUB_LOAD_REG_SKIP_OFFSET, .Lfill_regs, \sfx

// Handlers for loading long args into GPRs.
    .ifnc \sfx, _instance
        INVOKE_STUB_LOAD_REG ld, a1, t0, 8, t4, INVOKE_STUB_LOAD_REG_SIZE, .Lfill_regs, \sfx
    .endif
    INVOKE_STUB_LOAD_REG ld, a2, t0, 8, t4, INVOKE_STUB_LOAD_REG_SIZE, .Lfill_regs, \sfx
    INVOKE_STUB_LOAD_REG ld, a3, t0, 8, t4, INVOKE_STUB_LOAD_REG_SIZE, .Lfill_regs, \sfx
    INVOKE_STUB_LOAD_REG ld, a4, t0, 8, t4, INVOKE_STUB_LOAD_REG_SIZE, .Lfill_regs, \sfx
    INVOKE_STUB_LOAD_REG ld, a5, t0, 8, t4, INVOKE_STUB_LOAD_REG_SIZE, .Lfill_regs, \sfx
    INVOKE_STUB_LOAD_REG ld, a6, t0, 8, t4, INVOKE_STUB_LOAD_REG_SIZE, .Lfill_regs, \sfx
    INVOKE_STUB_LOAD_REG ld, a7, t0, 8, t4, INVOKE_STUB_LOAD_REG_SKIP_OFFSET, .Lfill_regs, \sfx

// Handlers for loading floats into FPRs.
    INVOKE_STUB_LOAD_REG flw, fa0, t0, 4, t5, INVOKE_STUB_LOAD_REG_SIZE, .Lfill_regs, \sfx
    INVOKE_STUB_LOAD_REG flw, fa1, t0, 4, t5, INVOKE_STUB_LOAD_REG_SIZE, .Lfill_regs, \sfx
    INVOKE_STUB_LOAD_REG flw, fa2, t0, 4, t5, INVOKE_STUB_LOAD_REG_SIZE, .Lfill_regs, \sfx
    INVOKE_STUB_LOAD_REG flw, fa3, t0, 4, t5, INVOKE_STUB_LOAD_REG_SIZE, .Lfill_regs, \sfx
    INVOKE_STUB_LOAD_REG flw, fa4, t0, 4, t5, INVOKE_STUB_LOAD_REG_SIZE, .Lfill_regs, \sfx
    INVOKE_STUB_LOAD_REG flw, fa5, t0, 4, t5, INVOKE_STUB_LOAD_REG_SIZE, .Lfill_regs, \sfx
    INVOKE_STUB_LOAD_REG flw, fa6, t0, 4, t5, INVOKE_STUB_LOAD_REG_SIZE, .Lfill_regs, \sfx
    INVOKE_STUB_LOAD_REG flw, fa7, t0, 4, t5, INVOKE_STUB_LOAD_REG_SKIP_OFFSET, .Lfill_regs, \sfx

// Handlers for loading doubles into FPRs.
    INVOKE_STUB_LOAD_REG fld, fa0, t0, 8, t5, INVOKE_STUB_LOAD_REG_SIZE, .Lfill_regs, \sfx
    INVOKE_STUB_LOAD_REG fld, fa1, t0, 8, t5, INVOKE_STUB_LOAD_REG_SIZE, .Lfill_regs, \sfx
    INVOKE_STUB_LOAD_REG fld, fa2, t0, 8, t5, INVOKE_STUB_LOAD_REG_SIZE, .Lfill_regs, \sfx
    INVOKE_STUB_LOAD_REG fld, fa3, t0, 8, t5, INVOKE_STUB_LOAD_REG_SIZE, .Lfill_regs, \sfx
    INVOKE_STUB_LOAD_REG fld, fa4, t0, 8, t5, INVOKE_STUB_LOAD_REG_SIZE, .Lfill_regs, \sfx
    INVOKE_STUB_LOAD_REG fld, fa5, t0, 8, t5, INVOKE_STUB_LOAD_REG_SIZE, .Lfill_regs, \sfx
    INVOKE_STUB_LOAD_REG fld, fa6, t0, 8, t5, INVOKE_STUB_LOAD_REG_SIZE, .Lfill_regs, \sfx
    INVOKE_STUB_LOAD_REG fld, fa7, t0, 8, t5, INVOKE_STUB_LOAD_REG_SKIP_OFFSET, .Lfill_regs, \sfx

.Lcall_method\sfx:
.endm


// void art_quick_invoke_stub(ArtMethod* method,   // a0
//                            uint32_t*  args,     // a1
//                            uint32_t   argsize,  // a2
//                            Thread*    self,     // a3
//                            JValue*    result,   // a4
//                            char*      shorty)   // a5
ENTRY art_quick_invoke_stub
    INVOKE_STUB_CREATE_FRAME

    // Load args into registers.
    INVOKE_STUB_LOAD_ALL_ARGS _instance

    // Call the method and return.
    INVOKE_STUB_CALL_AND_RETURN
END art_quick_invoke_stub


// void art_quick_invoke_static_stub(ArtMethod* method,   // a0
//                                   uint32_t*  args,     // a1
//                                   uint32_t   argsize,  // a2
//                                   Thread*    self,     // a3
//                                   JValue*    result,   // a4
//                                   char*      shorty)   // a5
ENTRY art_quick_invoke_static_stub
    INVOKE_STUB_CREATE_FRAME

    // Load args into registers.
    INVOKE_STUB_LOAD_ALL_ARGS _static

    // Call the method and return.
    INVOKE_STUB_CALL_AND_RETURN
END art_quick_invoke_static_stub


ENTRY art_quick_generic_jni_trampoline
    SETUP_SAVE_REFS_AND_ARGS_FRAME_WITH_METHOD_IN_A0

    // Save sp, so we can have static CFI info.
    mv   fp, sp
    .cfi_def_cfa_register fp

    li   t0, GENERIC_JNI_TRAMPOLINE_RESERVED_AREA
    sub  sp, sp, t0

    mv   a0, xSELF    // Thread*
    mv   a1, fp       // SP for the managed frame.
    mv   a2, sp       // reserved area for arguments and other saved data (up to managed frame)
    call artQuickGenericJniTrampoline

    // Check for error (class init check or locking for synchronized native method can throw).
    beqz a0, .Lexception_in_native

    mv   t2, a0       // save pointer to native method code into temporary

    // Load argument GPRs from stack (saved there by artQuickGenericJniTrampoline).
    ld  a0, 8*0(sp)   // JniEnv* for the native method
    ld  a1, 8*1(sp)
    ld  a2, 8*2(sp)
    ld  a3, 8*3(sp)
    ld  a4, 8*4(sp)
    ld  a5, 8*5(sp)
    ld  a6, 8*6(sp)
    ld  a7, 8*7(sp)

    // Load argument FPRs from stack (saved there by artQuickGenericJniTrampoline).
    fld  fa0, 8*8(sp)
    fld  fa1, 8*9(sp)
    fld  fa2, 8*10(sp)
    fld  fa3, 8*11(sp)
    fld  fa4, 8*12(sp)
    fld  fa5, 8*13(sp)
    fld  fa6, 8*14(sp)
    fld  fa7, 8*15(sp)

    ld  t0, 8*16(sp)  // @CriticalNative arg, used by art_jni_dlsym_lookup_critical_stub

    ld  t1, 8*17(sp)  // restore stack
    mv  sp, t1

    jalr  t2  // call native method

    // result sign extension is handled in C code, prepare for artQuickGenericJniEndTrampoline call:
    // uint64_t artQuickGenericJniEndTrampoline(Thread* self,       // a0
    //                                          jvalue result,      // a1 (need to move from a0)
    //                                          uint64_t result_f)  // a2 (need to move from fa0)
    mv  a1, a0
    mv  a0, xSELF
    fmv.x.d  a2, fa0
    call artQuickGenericJniEndTrampoline

    // Pending exceptions possible.
    ld   t0, THREAD_EXCEPTION_OFFSET(xSELF)
    bnez t0, .Lexception_in_native

    // Tear down the alloca.
    mv   sp, fp
    CFI_REMEMBER_STATE
    .cfi_def_cfa_register sp

    LOAD_RUNTIME_INSTANCE a1
    lb   a1, RUN_EXIT_HOOKS_OFFSET_FROM_RUNTIME_INSTANCE(a1)
    bnez a1, .Lcall_method_exit_hook

.Lcall_method_exit_hook_done:
    // This does not clobber the result register a0. a1 is not used for result as the managed code
    // does not have a 128-bit type. Alternatively we could restore a subset of these registers.
    RESTORE_SAVE_REFS_AND_ARGS_FRAME
    fmv.d.x  fa0, a0
    ret
    CFI_RESTORE_STATE_AND_DEF_CFA sp, FRAME_SIZE_SAVE_REFS_AND_ARGS

.Lcall_method_exit_hook:
    fmv.d.x  fa0, a0
    li   a4, FRAME_SIZE_SAVE_REFS_AND_ARGS
    call  art_quick_method_exit_hook
    j    .Lcall_method_exit_hook_done

.Lexception_in_native:
    // Move to a1 then sp to please assembler.
    ld   a1, THREAD_TOP_QUICK_FRAME_OFFSET(xSELF)
    addi sp, a1, -1  // Remove the GenericJNI tag.
    call art_deliver_pending_exception
END art_quick_generic_jni_trampoline


ENTRY art_quick_to_interpreter_bridge
    SETUP_SAVE_REFS_AND_ARGS_FRAME

    // uint64_t artQuickToInterpreterBridge(ArtMethod* method, Thread* self, ArtMethod** sp)
    // a0 will contain ArtMethod*
    mv   a1, xSELF
    mv   a2, sp
    call artQuickToInterpreterBridge

    // TODO: no need to restore arguments in this case.
    RESTORE_SAVE_REFS_AND_ARGS_FRAME

    fmv.d.x  fa0, a0  // copy the result to FP result register

    RETURN_OR_DELIVER_PENDING_EXCEPTION_REG t0
END art_quick_to_interpreter_bridge


    .extern artMethodEntryHook
ENTRY art_quick_method_entry_hook
    SETUP_SAVE_EVERYTHING_FRAME

    ld   a0, FRAME_SIZE_SAVE_EVERYTHING(sp)   // Pass ArtMethod*.
    mv   a1, xSELF                            // Pass Thread::Current().
    mv   a2, sp                               // pass SP
    call artMethodEntryHook                   // (ArtMethod*, Thread*, SP)

    RESTORE_SAVE_EVERYTHING_FRAME
    ret
END art_quick_method_entry_hook


    .extern artMethodExitHook
ENTRY art_quick_method_exit_hook
    SETUP_SAVE_EVERYTHING_FRAME

    // frame_size is passed in A4 from JITed code and `art_quick_generic_jni_trampoline`.
    addi a3, sp, SAVE_EVERYTHING_FRAME_OFFSET_FA0  // FP result ptr in kSaveEverything frame
    addi a2, sp, SAVE_EVERYTHING_FRAME_OFFSET_A0   // integer result ptr in kSaveEverything frame
    addi a1, sp, FRAME_SIZE_SAVE_EVERYTHING        // ArtMethod**
    mv   a0, xSELF                                 // Thread::Current
    call artMethodExitHook                         // (Thread*, ArtMethod**, gpr_res*, fpr_res*,
                                                   // frame_size)

    // Normal return.
    RESTORE_SAVE_EVERYTHING_FRAME
    ret
END art_quick_method_exit_hook


// On entry a0 is uintptr_t* gprs_ and a1 is uint64_t* fprs_.
// Both must reside on the stack, between current sp and target sp.
ENTRY art_quick_do_long_jump
    // Load FPRs
    fld  ft0,  8*0(a1)   // f0
    fld  ft1,  8*1(a1)   // f1
    fld  ft2,  8*2(a1)   // f2
    fld  ft3,  8*3(a1)   // f3
    fld  ft4,  8*4(a1)   // f4
    fld  ft5,  8*5(a1)   // f5
    fld  ft6,  8*6(a1)   // f6
    fld  ft7,  8*7(a1)   // f7
    fld  fs0,  8*8(a1)   // f8
    fld  fs1,  8*9(a1)   // f9
    fld  fa0,  8*10(a1)  // f10
    fld  fa1,  8*11(a1)  // f11
    fld  fa2,  8*12(a1)  // f12
    fld  fa3,  8*13(a1)  // f13
    fld  fa4,  8*14(a1)  // f14
    fld  fa5,  8*15(a1)  // f15
    fld  fa6,  8*16(a1)  // f16
    fld  fa7,  8*17(a1)  // f17
    fld  fs2,  8*18(a1)  // f18
    fld  fs3,  8*19(a1)  // f19
    fld  fs4,  8*20(a1)  // f20
    fld  fs5,  8*21(a1)  // f21
    fld  fs6,  8*22(a1)  // f22
    fld  fs7,  8*23(a1)  // f23
    fld  fs8,  8*24(a1)  // f24
    fld  fs9,  8*25(a1)  // f25
    fld  fs10, 8*26(a1)  // f26
    fld  fs11, 8*27(a1)  // f27
    fld  ft8,  8*28(a1)  // f28
    fld  ft9,  8*29(a1)  // f29
    fld  ft10, 8*30(a1)  // f30
    fld  ft11, 8*31(a1)  // f31

    // Load GPRs.
    // Skip slot 8*0(a0) for zero/x0 as it is hard-wired zero.
    ld  ra,   8*1(a0)   // x1
    // Skip slot 8*2(a0) for sp/x2 as it is set below.
    // Skip slot 8*3(a0) for platform-specific thread pointer gp/x3.
    // Skip slot 8*4(a0) for platform-specific global pointer tp/x4.
    // Skip slot 8*5(a0) for t0/x5 as it is clobbered below.
    // Skip slot 8*6(a0) for t1/x6 as it is clobbered below.
    ld  t2,   8*7(a0)   // x7
    ld  s0,   8*8(a0)   // x8
    ld  s1,   8*9(a0)   // x9
    // Delay loading a0 as the base is in a0.
    ld  a1,   8*11(a0)  // x11
    ld  a2,   8*12(a0)  // x12
    ld  a3,   8*13(a0)  // x13
    ld  a4,   8*14(a0)  // x14
    ld  a5,   8*15(a0)  // x15
    ld  a6,   8*16(a0)  // x16
    ld  a7,   8*17(a0)  // x17
    ld  s2,   8*18(a0)  // x18
    ld  s3,   8*19(a0)  // x19
    ld  s4,   8*20(a0)  // x20
    ld  s5,   8*21(a0)  // x21
    ld  s6,   8*22(a0)  // x22
    ld  s7,   8*23(a0)  // x23
    ld  s8,   8*24(a0)  // x24
    ld  s9,   8*25(a0)  // x25
    ld  s10,  8*26(a0)  // x26
    ld  s11,  8*27(a0)  // x27
    ld  t3,   8*28(a0)  // x28
    ld  t4,   8*29(a0)  // x29
    ld  t5,   8*30(a0)  // x30
    ld  t6,   8*31(a0)  // x31

    // Load sp to t0.
    ld  t0, 8*2(a0)

    // Load PC to t1, it is in the last stack slot.
    ld  t1, 8*32(a0)

    // Now load a0.
    ld  a0, 8*10(a0)  // x10

    // Set sp. Do not access fprs_ and gprs_ from now, they are below sp.
    mv sp, t0

    jr  t1
END art_quick_do_long_jump


.macro DEOPT_OR_RETURN temp, is_ref = 0
    lwu   \temp, THREAD_DEOPT_CHECK_REQUIRED_OFFSET(xSELF)
    bnez  \temp, 2f
    ret
2:
    SETUP_SAVE_EVERYTHING_FRAME
    li    a2, \is_ref                 // pass if result is a reference
    mv    a1, a0                      // pass the result
    mv    a0, xSELF                   // pass Thread::Current
    call  artDeoptimizeIfNeeded       // (Thread*, uintptr_t, bool)
    RESTORE_SAVE_EVERYTHING_FRAME
    ret
.endm


.macro RETURN_OR_DEOPT_IF_RESULT_IS_NON_NULL_OR_DELIVER
    beqz  a0, 1f
    DEOPT_OR_RETURN a1, /*is_ref=*/ 1
1:
    DELIVER_PENDING_EXCEPTION
.endm


.macro RETURN_OR_DEOPT_IF_INT_RESULT_IS_ZERO_OR_DELIVER
    bnez  a0, 1f
    DEOPT_OR_RETURN a1
1:
    DELIVER_PENDING_EXCEPTION
.endm


.macro RETURN_OR_DEOPT_OR_DELIVER_PENDING_EXCEPTION is_ref = 0
    lwu   a1, THREAD_EXCEPTION_OFFSET(xSELF)   // Get exception field.
    bnez  a1, 1f
    DEOPT_OR_RETURN a1, \is_ref                // Check if deopt is required.
1:
    DELIVER_PENDING_EXCEPTION                  // Deliver exception on current thread.
.endm


.macro RETURN_REF_OR_DEOPT_OR_DELIVER_PENDING_EXCEPTION
    RETURN_OR_DEOPT_OR_DELIVER_PENDING_EXCEPTION /* is_ref= */ 1
.endm


.macro DEOPT_OR_RESTORE_SAVE_EVERYTHING_FRAME_AND_RETURN_A0 temp, is_ref
  ld      \temp, THREAD_DEOPT_CHECK_REQUIRED_OFFSET(xSELF)
  CFI_REMEMBER_STATE
  bnez    \temp, 2f
  RESTORE_SAVE_EVERYTHING_FRAME /* load_a0= */ 0
  ret
2:
  CFI_RESTORE_STATE_AND_DEF_CFA sp, FRAME_SIZE_SAVE_EVERYTHING
  sd      a0, SAVE_EVERYTHING_FRAME_OFFSET_A0(sp)    // update result in the frame
  li      a2, \is_ref                                // pass if result is a reference
  mv      a1, a0                                     // pass the result
  mv      a0, xSELF                                  // Thread::Current
  call    artDeoptimizeIfNeeded
  CFI_REMEMBER_STATE
  RESTORE_SAVE_EVERYTHING_FRAME
  ret
  CFI_RESTORE_STATE_AND_DEF_CFA sp, FRAME_SIZE_SAVE_EVERYTHING
.endm


// Entry from managed code that tries to lock the object in a fast path and
// calls `artLockObjectFromCode()` for the difficult cases, may block for GC.
// A0 holds the possibly null object to lock.
ENTRY art_quick_lock_object
    LOCK_OBJECT_FAST_PATH a0, art_quick_lock_object_no_inline, /*can_be_null*/ 1
END art_quick_lock_object


// Entry from managed code that calls `artLockObjectFromCode()`, may block for GC.
// A0 holds the possibly null object to lock.
    .extern artLockObjectFromCode
ENTRY art_quick_lock_object_no_inline
    // This is also the slow path for `art_quick_lock_object`.
    SETUP_SAVE_REFS_ONLY_FRAME        // save callee saves in case we block
    mv    a1, xSELF                   // pass Thread::Current
    call  artLockObjectFromCode       // (Object*, Thread*)
    RESTORE_SAVE_REFS_ONLY_FRAME
    RETURN_OR_DEOPT_IF_INT_RESULT_IS_ZERO_OR_DELIVER
END art_quick_lock_object_no_inline


// Entry from managed code that tries to unlock the object in a fast path and calls
// `artUnlockObjectFromCode()` for the difficult cases and delivers exception on failure.
// A0 holds the possibly null object to unlock.
ENTRY art_quick_unlock_object
    UNLOCK_OBJECT_FAST_PATH a0, art_quick_unlock_object_no_inline, /*can_be_null*/ 1
END art_quick_unlock_object


// Entry from managed code that calls `artUnlockObjectFromCode()`
// and delivers exception on failure.
// A0 holds the possibly null object to unlock.
    .extern artUnlockObjectFromCode
ENTRY art_quick_unlock_object_no_inline
    // This is also the slow path for `art_quick_unlock_object`.
    SETUP_SAVE_REFS_ONLY_FRAME        // save callee saves in case exception allocation triggers GC
    mv    a1, xSELF                   // pass Thread::Current
    call  artUnlockObjectFromCode     // (Object*, Thread*)
    RESTORE_SAVE_REFS_ONLY_FRAME
    RETURN_OR_DEOPT_IF_INT_RESULT_IS_ZERO_OR_DELIVER
END art_quick_unlock_object_no_inline


// Called by managed code that is attempting to call a method on a proxy class. On entry a0 holds
// the proxy method and a1 holds the receiver. The frame size of the invoked proxy method agrees
// with kSaveRefsAndArgs frame.
.extern artQuickProxyInvokeHandler
ENTRY art_quick_proxy_invoke_handler
    SETUP_SAVE_REFS_AND_ARGS_FRAME_WITH_METHOD_IN_A0

    // uint64_t artQuickProxyInvokeHandler(ArtMethod* proxy_method,   // a0
    //                                     mirror::Object* receiver,  // a1
    //                                     Thread* self,              // a2
    //                                     ArtMethod** sp)            // a3
    mv    a2, xSELF                   // pass Thread::Current
    mv    a3, sp                      // pass sp
    call  artQuickProxyInvokeHandler  // (Method* proxy method, receiver, Thread*, sp)

    ld    a2, THREAD_EXCEPTION_OFFSET(xSELF)
    bnez  a2, .Lexception_in_proxy    // success if no exception is pending
    CFI_REMEMBER_STATE
    RESTORE_SAVE_REFS_AND_ARGS_FRAME  // Restore frame
    fmv.d.x  fa0, a0                  // Store result in fa0 in case it was float or double
    ret                               // return on success

.Lexception_in_proxy:
    CFI_RESTORE_STATE_AND_DEF_CFA sp, FRAME_SIZE_SAVE_REFS_AND_ARGS
    RESTORE_SAVE_REFS_AND_ARGS_FRAME
    DELIVER_PENDING_EXCEPTION
END art_quick_proxy_invoke_handler


// Compiled code has requested that we deoptimize into the interpreter. The deoptimization
// will long jump to the upcall with a special exception of -1.
    .extern artDeoptimizeFromCompiledCode
ENTRY art_quick_deoptimize_from_compiled_code
    SETUP_SAVE_EVERYTHING_FRAME
    mv    a1, xSELF                           // Pass Thread::Current().
    call  artDeoptimizeFromCompiledCode       // (DeoptimizationKind, Thread*)
    unimp
END art_quick_deoptimize_from_compiled_code


    .extern artStringBuilderAppend
ENTRY art_quick_string_builder_append
    SETUP_SAVE_REFS_ONLY_FRAME                // Save callee saves in case of GC.
    addi  a1, sp, (FRAME_SIZE_SAVE_REFS_ONLY + __SIZEOF_POINTER__)  // Pass args.
    mv    a2, xSELF                           // Pass Thread::Current().
    call  artStringBuilderAppend              // (uint32_t, const unit32_t*, Thread*)
    RESTORE_SAVE_REFS_ONLY_FRAME
    RETURN_OR_DEOPT_IF_RESULT_IS_NON_NULL_OR_DELIVER
END art_quick_string_builder_append


// Entry from managed code that calls artInstanceOfFromCode and on failure calls
// artThrowClassCastExceptionForObject.
    .extern artInstanceOfFromCode
    .extern artThrowClassCastExceptionForObject
ENTRY art_quick_check_instance_of
    // Type check using the bit string passes null as the target class. In that case just throw.
    beqz  a1, .Lthrow_class_cast_exception_for_bitstring_check

    // Store arguments and return address register.
    // Stack needs to be 16B aligned on calls.
    INCREASE_FRAME 32
    sd    a0, 0*8(sp)
    sd    a1, 1*8(sp)
    SAVE_GPR ra, 3*8

    // Call runtime code.
    call  artInstanceOfFromCode

    // Restore RA.
    RESTORE_GPR ra, 3*8

    // Check for exception.
    CFI_REMEMBER_STATE
    beqz a0, .Lthrow_class_cast_exception

    // Remove spill area and return (no need to restore A0 and A1).
    DECREASE_FRAME 32
    ret

.Lthrow_class_cast_exception:
    CFI_RESTORE_STATE_AND_DEF_CFA sp, 32
    // Restore A0 and remove spill area.
    ld    a0, 0*8(sp)
    ld    a1, 1*8(sp)
    DECREASE_FRAME 32

.Lthrow_class_cast_exception_for_bitstring_check:
    SETUP_SAVE_ALL_CALLEE_SAVES_FRAME         // Save all registers as basis for long jump context.
    mv    a2, xSELF                           // Pass Thread::Current().
    call  artThrowClassCastExceptionForObject // (Object*, Class*, Thread*)
    unimp                                     // We should not return here...
END art_quick_check_instance_of


.macro N_ARG_RUNTIME_EXCEPTION_SAVE_EVERYTHING n, c_name, cxx_name
.extern \cxx_name
ENTRY \c_name
    SETUP_SAVE_EVERYTHING_FRAME       // save all registers as basis for long jump context.
    mv    a\n, xSELF                  // pass Thread::Current.
    call  \cxx_name                   // \cxx_name(args..., Thread*).
    unimp
END \c_name
.endm


.macro NO_ARG_RUNTIME_EXCEPTION_SAVE_EVERYTHING c_name, cxx_name
    N_ARG_RUNTIME_EXCEPTION_SAVE_EVERYTHING 0, \c_name, \cxx_name
.endm


.macro TWO_ARG_RUNTIME_EXCEPTION_SAVE_EVERYTHING c_name, cxx_name
    N_ARG_RUNTIME_EXCEPTION_SAVE_EVERYTHING 2, \c_name, \cxx_name
.endm


.macro N_ARG_RUNTIME_EXCEPTION n, c_name, cxx_name
.extern \cxx_name
ENTRY \c_name
    SETUP_SAVE_ALL_CALLEE_SAVES_FRAME // save all registers as basis for long jump context.
    mv    a\n, xSELF                  // pass Thread::Current.
    call  \cxx_name                   // \cxx_name(args..., Thread*).
    unimp
END \c_name
.endm

.macro NO_ARG_RUNTIME_EXCEPTION c_name, cxx_name
    N_ARG_RUNTIME_EXCEPTION 0, \c_name, \cxx_name
.endm


.macro ONE_ARG_RUNTIME_EXCEPTION c_name, cxx_name
    N_ARG_RUNTIME_EXCEPTION 1, \c_name, \cxx_name
.endm


// Called by managed code to create and deliver a NullPointerException.
NO_ARG_RUNTIME_EXCEPTION_SAVE_EVERYTHING \
        art_quick_throw_null_pointer_exception, artThrowNullPointerExceptionFromCode


// Call installed by a signal handler to create and deliver a NullPointerException.
.extern artThrowNullPointerExceptionFromSignal
ENTRY art_quick_throw_null_pointer_exception_from_signal
    // The fault handler pushes the gc map address, i.e. "return address", to stack
    // and passes the fault address in RA. So we need to set up the CFI info accordingly.
    .cfi_def_cfa_offset __SIZEOF_POINTER__
    .cfi_rel_offset ra, 0
    // Save all registers as basis for long jump context.
    INCREASE_FRAME (FRAME_SIZE_SAVE_EVERYTHING - __SIZEOF_POINTER__)
    SETUP_SAVE_EVERYTHING_FRAME_DECREMENTED_SP_SKIP_RA
    mv    a0, ra                      // pass the fault address stored in RA by the fault handler.
    mv    a1, xSELF                   // pass Thread::Current.
    call  artThrowNullPointerExceptionFromSignal  // (arg, Thread*).
    unimp
END art_quick_throw_null_pointer_exception_from_signal


// Called by managed code to deliver an ArithmeticException.
NO_ARG_RUNTIME_EXCEPTION_SAVE_EVERYTHING art_quick_throw_div_zero, artThrowDivZeroFromCode


// Called by managed code to create and deliver an ArrayIndexOutOfBoundsException.
// Arg0 holds index, arg1 holds limit.
TWO_ARG_RUNTIME_EXCEPTION_SAVE_EVERYTHING art_quick_throw_array_bounds, artThrowArrayBoundsFromCode


// Called by managed code to create and deliver a StringIndexOutOfBoundsException
// as if thrown from a call to String.charAt(). Arg0 holds index, arg1 holds limit.
TWO_ARG_RUNTIME_EXCEPTION_SAVE_EVERYTHING \
        art_quick_throw_string_bounds, artThrowStringBoundsFromCode

// Called by managed code to create and deliver a StackOverflowError.
NO_ARG_RUNTIME_EXCEPTION art_quick_throw_stack_overflow, artThrowStackOverflowFromCode

// Called by managed code to deliver an exception.
ONE_ARG_RUNTIME_EXCEPTION art_quick_deliver_exception, artDeliverExceptionFromCode


// Called to attempt to execute an obsolete method.
ONE_ARG_RUNTIME_EXCEPTION art_invoke_obsolete_method_stub, artInvokeObsoleteMethod


ENTRY art_quick_resolution_trampoline
    SETUP_SAVE_REFS_AND_ARGS_FRAME

    // const void* artQuickResolutionTrampoline(ArtMethod* called,         // a0
    //                                          mirror::Object* receiver,  // a1
    //                                          Thread* self,              // a2
    //                                          ArtMethod** sp)            // a3
    mv   a2, xSELF
    mv   a3, sp
    call artQuickResolutionTrampoline
    CFI_REMEMBER_STATE
    beqz a0, 1f
    mv   t0, a0    // Remember returned code pointer in t0.
    ld   a0, (sp)  // artQuickResolutionTrampoline puts called method in *sp.

    RESTORE_SAVE_REFS_AND_ARGS_FRAME
    jr   t0
1:
    CFI_RESTORE_STATE_AND_DEF_CFA sp, FRAME_SIZE_SAVE_REFS_AND_ARGS
    RESTORE_SAVE_REFS_AND_ARGS_FRAME
    DELIVER_PENDING_EXCEPTION
END art_quick_resolution_trampoline


ENTRY art_quick_test_suspend
    SETUP_SAVE_EVERYTHING_FRAME \
        RUNTIME_SAVE_EVERYTHING_FOR_SUSPEND_CHECK_METHOD_OFFSET
    mv   a0, xSELF
    call artTestSuspendFromCode
    RESTORE_SAVE_EVERYTHING_FRAME
    ret
END art_quick_test_suspend


ENTRY art_quick_compile_optimized
    SETUP_SAVE_EVERYTHING_FRAME
    ld   a0, FRAME_SIZE_SAVE_EVERYTHING(sp)   // pass ArtMethod
    mv   a1, xSELF                            // pass Thread::Current
    call artCompileOptimized                  // (ArtMethod*, Thread*)
    RESTORE_SAVE_EVERYTHING_FRAME
    // Note: If we implement implicit suspend checks or a marking register for GC, we don't need
    // to restore such registers here, as artCompileOptimized doesn't allow thread suspension.
    ret
END art_quick_compile_optimized


/*  extern"C" void art_quick_osr_stub(void*          stack,                 A0
 *                                    size_t         stack_size_in_bytes,   A1
 *                                    const uint8_t* native_pc,             A2
 *                                    JValue*        result,                A3
 *                                    char*          shorty,                A4
 *                                    Thread*        self)                  A5
 */
ENTRY art_quick_osr_stub
    // Save all callee-save registers (we do not fill the spill area in the OSR frame, so we
    // need to preserve them here) and A3 (it will be needed after the OSR method returns).
    // Also add space for the `ArtMethod*` slot (null to indicate transition) and padding.
    SAVE_SIZE=(12 + 12 + /* RA */ 1 + /* A3 */ 1 + /* ArtMethod* */ 1 + /* padding */ 1) * 8
    INCREASE_FRAME SAVE_SIZE
    sd zero, 0*8(sp)          // Store null to the `ArtMethod*` slot to indicate transition.
                              // Skip padding.
    SAVE_GPR a3, 2*8          // Save `result`.
    SAVE_FPR fs0, 3*8
    SAVE_FPR fs1, 4*8
    SAVE_FPR fs2, 5*8
    SAVE_FPR fs3, 6*8
    SAVE_FPR fs4, 7*8
    SAVE_FPR fs5, 8*8
    SAVE_FPR fs6, 9*8
    SAVE_FPR fs7, 10*8
    SAVE_FPR fs8, 11*8
    SAVE_FPR fs9, 12*8
    SAVE_FPR fs10, 13*8
    SAVE_FPR fs11, 14*8
    SAVE_GPR s2, 15*8
    SAVE_GPR s3, 16*8
    SAVE_GPR s4, 17*8
    SAVE_GPR s5, 18*8
    SAVE_GPR s6, 19*8
    SAVE_GPR s7, 20*8
    SAVE_GPR s8, 21*8
    SAVE_GPR s9, 22*8
    SAVE_GPR s10, 23*8
    SAVE_GPR s11, 24*8
    SAVE_GPR xSELF, 25*8       // Save xSELF/S1.
    SAVE_GPR fp, 26*8          // Save FP/S0.
    SAVE_GPR ra, 27*8          // Save return address.

    // Make the new FP point to the location where we stored the old FP.
    // Some stack-walking tools may rely on this simply-linked list of saved FPs.
    addi fp, sp, (26*8)  // save frame pointer
    .cfi_def_cfa fp, SAVE_SIZE - (26*8)

    mv   xSELF, a5

    CFI_REMEMBER_STATE
    jal .Losr_entry

    // The called method removes the stack frame created in `.Losr_entry`.
    // The SP is already correctly restored, we do not need to restore it from FP.
    .cfi_def_cfa sp, SAVE_SIZE

    // Restore saved registers including the result address.
    RESTORE_GPR a3, 2*8       // Restore `result`.
    RESTORE_FPR fs0, 3*8
    RESTORE_FPR fs1, 4*8
    RESTORE_FPR fs2, 5*8
    RESTORE_FPR fs3, 6*8
    RESTORE_FPR fs4, 7*8
    RESTORE_FPR fs5, 8*8
    RESTORE_FPR fs6, 9*8
    RESTORE_FPR fs7, 10*8
    RESTORE_FPR fs8, 11*8
    RESTORE_FPR fs9, 12*8
    RESTORE_FPR fs10, 13*8
    RESTORE_FPR fs11, 14*8
    RESTORE_GPR s2, 15*8
    RESTORE_GPR s3, 16*8
    RESTORE_GPR s4, 17*8
    RESTORE_GPR s5, 18*8
    RESTORE_GPR s6, 19*8
    RESTORE_GPR s7, 20*8
    RESTORE_GPR s8, 21*8
    RESTORE_GPR s9, 22*8
    RESTORE_GPR s10, 23*8
    RESTORE_GPR s11, 24*8
    RESTORE_GPR xSELF, 25*8   // Restore xSELF/S1.
    RESTORE_GPR fp, 26*8      // Restore FP/S0.
    RESTORE_GPR ra, 27*8      // Restore return address.
    DECREASE_FRAME SAVE_SIZE

    // The compiler put the result in A0. Doesn't matter if it is 64 or 32 bits.
    sd   a0, (a3)
    ret

.Losr_entry:
    CFI_RESTORE_STATE_AND_DEF_CFA fp, SAVE_SIZE - (26*8)

    // Prepare the destination register for backward copy of arguments.
    addi t1, sp, -8

    // Update stack pointer for the callee frame.
    sub  sp, sp, a1

    // Subtract the return address slot size from args size.
    addi a1, a1, -8

    // Update return address slot expected by the callee.
    sd   ra, (t1)

    // Prepare the source register for backward copy of arguments.
    add  t0, a0, a1

    // Copy arguments into stack frame. Use simple backward-copy routine for now.
    // There is always at least the `ArtMethod*` to to copy.
    // A0 - source address
    // A1 - args length
    // SP - destination address.
    // T0 - loop variable initialized to A0 + A1 for backward copy
    // T1 - loop variable initialized to SP + A1 for backward copy
    // T2 - temporary for holding the copied value
.Losr_loop:
    addi t0, t0, -8
    ld   t2, (t0)
    addi t1, t1, -8
    sd   t2, (t1)
    bne  t1, sp, .Losr_loop

    // Branch to the OSR entry point.
    jr   a2

END art_quick_osr_stub


    /*
     * All generated callsites for interface invokes and invocation slow paths will load arguments
     * as usual - except instead of loading arg0/A0 with the target Method*, arg0/A0 will contain
     * the method_idx. This wrapper will call the appropriate C++ helper while preserving arguments
     * and allowing a moving GC to update references in callee-save registers.
     * NOTE: "this" is the first visible argument of the target, and so can be found in arg1/A1.
     *
     * The helper will attempt to locate the target and return a 128-bit result consisting of the
     * target `ArtMethod*` in A0 and its `entry_point_from_quick_compiled_code_` in A1.
     *
     * If unsuccessful, the helper will return null/null. There will be a pending exception
     * to deliver in the thread.
     *
     * On success this wrapper will restore arguments and *jump* to the target, leaving the RA
     * pointing back to the original caller.
     */
.macro INVOKE_TRAMPOLINE_BODY cxx_name
    .extern \cxx_name
    SETUP_SAVE_REFS_AND_ARGS_FRAME
    mv    a2, xSELF                           // Pass Thread::Current().
    mv    a3, sp                              // Pass pointer to the saved frame context.
    call   \cxx_name                          // (method_idx, this, Thread*, $sp)
    mv    t0, a1                              // Save method's code pointer in T0.
    RESTORE_SAVE_REFS_AND_ARGS_FRAME
    beq   a0, zero, 1f
    jr    t0
1:
    DELIVER_PENDING_EXCEPTION
.endm

.macro INVOKE_TRAMPOLINE c_name, cxx_name
ENTRY \c_name
    INVOKE_TRAMPOLINE_BODY \cxx_name
END \c_name
.endm

INVOKE_TRAMPOLINE art_quick_invoke_interface_trampoline_with_access_check, \
                  artInvokeInterfaceTrampolineWithAccessCheck
INVOKE_TRAMPOLINE art_quick_invoke_static_trampoline_with_access_check, \
                  artInvokeStaticTrampolineWithAccessCheck
INVOKE_TRAMPOLINE art_quick_invoke_direct_trampoline_with_access_check, \
                  artInvokeDirectTrampolineWithAccessCheck
INVOKE_TRAMPOLINE art_quick_invoke_super_trampoline_with_access_check, \
                  artInvokeSuperTrampolineWithAccessCheck
INVOKE_TRAMPOLINE art_quick_invoke_virtual_trampoline_with_access_check, \
                  artInvokeVirtualTrampolineWithAccessCheck

    /*
     * Polymorphic method invocation.
     * On entry:
     *   A0 = unused
     *   A1 = receiver
     */
.extern artInvokePolymorphic
ENTRY art_quick_invoke_polymorphic
    SETUP_SAVE_REFS_AND_ARGS_FRAME
    mv      a0, a1                            // Pass the receiver.
    mv      a1, xSELF                         // Pass Thread::Current().
    mv      a2, sp                            // Pass pointer to the saved frame context.
    call    artInvokePolymorphic              // artInvokePolymorphic(receiver, Thread*, context)
    RESTORE_SAVE_REFS_AND_ARGS_FRAME
    fmv.d.x fa0, a0                           // Copy the result also to the FP return register.
    RETURN_OR_DELIVER_PENDING_EXCEPTION_REG  t0
END art_quick_invoke_polymorphic

/*
     * InvokeCustom invocation.
     * On entry:
     *   A0 = call_site_idx
     */
.extern artInvokeCustom
ENTRY art_quick_invoke_custom
    SETUP_SAVE_REFS_AND_ARGS_FRAME
    mv      a1, xSELF                         // Pass Thread::Current().
    mv      a2, sp                            // Pass pointer to the saved frame context.
    call    artInvokeCustom                   // artInvokeCustom(call_site_idx, Thread*, context)
    RESTORE_SAVE_REFS_AND_ARGS_FRAME
    fmv.d.x fa0, a0                           // Copy the result also to the FP return register.
    RETURN_OR_DELIVER_PENDING_EXCEPTION_REG t0
END art_quick_invoke_custom

    /*
     * Called to resolve an imt conflict.
     * On entry:
     *   A0 is the conflict ArtMethod.
     *   T0 is a hidden argument that holds the target interface method's dex method index.
     */
ENTRY art_quick_imt_conflict_trampoline
    ld      t1, ART_METHOD_JNI_OFFSET_64(a0)  // Load ImtConflictTable
    ld      a0, 0(t1)                         // Load first entry in ImtConflictTable.
.Limt_table_iterate:
    // Branch if found.
    beq     a0, t0, .Limt_table_found

    // If the entry is null, the interface method is not in the ImtConflictTable.
    beqz    a0, .Lconflict_trampoline
    // Iterate over the entries of the ImtConflictTable.
    addi    t1, t1, (2 * __SIZEOF_POINTER__)
    ld      a0, 0(t1)
    j       .Limt_table_iterate
.Limt_table_found:
    // We successfully hit an entry in the table. Load the target method and jump to it.
    ld      a0, __SIZEOF_POINTER__(t1)
    ld      t1, ART_METHOD_QUICK_CODE_OFFSET_64(a0)
    jr      t1
.Lconflict_trampoline:
    // Call the runtime stub to populate the ImtConflictTable and jump to the
    // resolved method.
    move a0, t0  // Load interface method
    INVOKE_TRAMPOLINE_BODY artInvokeInterfaceTrampoline
END art_quick_imt_conflict_trampoline


.macro UPDATE_INLINE_CACHE_ENTRY class, entry, temp, loop_label, done_label, next_label
\loop_label:
    lwu     \temp, (\entry)
    beq     \class, \temp, \done_label
    bnez    \temp, \next_label
    lr.w    \temp, (\entry)
    bnez    \temp, \loop_label
    sc.w    \temp, \class, (\entry)
    beqz    \temp, \done_label
    j       \loop_label
.endm

// A0 contains the class, T5 contains the inline cache. T6 can be used, T5 can be clobbered.
ENTRY art_quick_update_inline_cache
#if (INLINE_CACHE_SIZE != 5)
#error "INLINE_CACHE_SIZE not as expected."
#endif
#if defined(USE_READ_BARRIER) && defined(USE_BAKER_READ_BARRIER)
    // Don't update the cache if we are marking.
    lwu     t6, THREAD_IS_GC_MARKING_OFFSET(xSELF)
    bnez    t6, .Ldone
#endif
    addi    t5, t5, INLINE_CACHE_CLASSES_OFFSET
    UPDATE_INLINE_CACHE_ENTRY a0, t5, t6, .Lentry1_loop, .Ldone, .Lentry2
.Lentry2:
    addi    t5, t5, 4
    UPDATE_INLINE_CACHE_ENTRY a0, t5, t6, .Lentry2_loop, .Ldone, .Lentry3
.Lentry3:
    addi    t5, t5, 4
    UPDATE_INLINE_CACHE_ENTRY a0, t5, t6, .Lentry3_loop, .Ldone, .Lentry4
.Lentry4:
    addi    t5, t5, 4
    UPDATE_INLINE_CACHE_ENTRY a0, t5, t6, .Lentry4_loop, .Ldone, .Lentry5
.Lentry5:
    // Unconditionally store, the inline cache is megamorphic.
    sw      a0, 4(t5)
.Ldone:
    ret
END art_quick_update_inline_cache


.macro ONE_ARG_SAVE_EVERYTHING_DOWNCALL \
        name, entrypoint, runtime_method_offset = RUNTIME_SAVE_EVERYTHING_METHOD_OFFSET
    .extern \entrypoint
ENTRY \name
    SETUP_SAVE_EVERYTHING_FRAME \runtime_method_offset  // Save everything for stack crawl.
    mv    a1, xSELF                   // Pass Thread::Current().
    call  \entrypoint                 // (uint32_t/Class* index/klass, Thread* self)
    beqz  a0, 1f                      // If result is null, deliver the exception.
    DEOPT_OR_RESTORE_SAVE_EVERYTHING_FRAME_AND_RETURN_A0 /* temp= */ a1, /* is_ref= */ 1
1:
    DELIVER_PENDING_EXCEPTION_FRAME_READY
END \name
.endm


.macro ONE_ARG_SAVE_EVERYTHING_DOWNCALL_FOR_CLINIT name, entrypoint
    ONE_ARG_SAVE_EVERYTHING_DOWNCALL \
            \name, \entrypoint, RUNTIME_SAVE_EVERYTHING_FOR_CLINIT_METHOD_OFFSET
.endm


ONE_ARG_SAVE_EVERYTHING_DOWNCALL_FOR_CLINIT \
        art_quick_initialize_static_storage, artInitializeStaticStorageFromCode
ONE_ARG_SAVE_EVERYTHING_DOWNCALL_FOR_CLINIT art_quick_resolve_type, artResolveTypeFromCode
ONE_ARG_SAVE_EVERYTHING_DOWNCALL \
        art_quick_resolve_type_and_verify_access, artResolveTypeAndVerifyAccessFromCode
ONE_ARG_SAVE_EVERYTHING_DOWNCALL art_quick_resolve_method_handle, artResolveMethodHandleFromCode
ONE_ARG_SAVE_EVERYTHING_DOWNCALL art_quick_resolve_method_type, artResolveMethodTypeFromCode
ONE_ARG_SAVE_EVERYTHING_DOWNCALL art_quick_resolve_string, artResolveStringFromCode


// Helper macros for `art_quick_aput_obj`.
#ifdef USE_READ_BARRIER
#ifdef USE_BAKER_READ_BARRIER
.macro BAKER_RB_CHECK_GRAY_BIT_AND_LOAD dest, obj, offset, gray_slow_path_label
    lw    t6, MIRROR_OBJECT_LOCK_WORD_OFFSET(\obj)
    slliw t6, t6, 31 - LOCK_WORD_READ_BARRIER_STATE_SHIFT  // Shift the state bit to sign bit.
    bltz  t6, \gray_slow_path_label
    // False dependency to avoid needing load/load fence.
    xor   t6, t6, t6
    add   \obj, \obj, t6
    lwu   \dest, \offset(\obj)                     // Heap reference = 32b; zero-extends to `dest`.
    UNPOISON_HEAP_REF \dest
.endm

.macro BAKER_RB_LOAD_AND_MARK dest, obj, offset, mark_function
    lwu   \dest, \offset(\obj)                     // Heap reference = 32b; zero-extends to `dest`.
    UNPOISON_HEAP_REF \dest
    // Save RA in a register preserved by `art_quick_read_barrier_mark_regNN`
    // and unused by the `art_quick_aput_obj`.
    mv    t2, ra
    call  \mark_function
    mv    ra, t2                                   // Restore RA.
.endm
#else  // USE_BAKER_READ_BARRIER
    .extern artReadBarrierSlow
.macro READ_BARRIER_SLOW dest, obj, offset
    // Store registers used in art_quick_aput_obj (a0-a4, RA), stack is 16B aligned.
    INCREASE_FRAME 48
    SAVE_GPR a0, 0*8
    SAVE_GPR a1, 1*8
    SAVE_GPR a2, 2*8
    SAVE_GPR a3, 3*8
    SAVE_GPR a4, 4*8
    SAVE_GPR ra, 5*8

    // mv a0, \ref                  // Pass ref in A0 (no-op for now since parameter ref is unused).
    .ifnc \obj, a1
        mv a1, \obj                 // Pass `obj`.
    .endif
    li    a2, \offset               // Pass offset.
    call  artReadBarrierSlow        // artReadBarrierSlow(ref, obj, offset)
    // No need to unpoison return value in A0, `artReadBarrierSlow()` would do the unpoisoning.
    .ifnc \dest, a0
        mv \dest, a0                // save return value in dest
    .endif

    // Conditionally restore saved registers
    RESTORE_GPR_NE a0, 0*8, \dest
    RESTORE_GPR_NE a1, 1*8, \dest
    RESTORE_GPR_NE a2, 2*8, \dest
    RESTORE_GPR_NE a3, 3*8, \dest
    RESTORE_GPR_NE a4, 4*8, \dest
    RESTORE_GPR ra, 5*8
    DECREASE_FRAME 48
.endm
#endif  // USE_BAKER_READ_BARRIER
#endif  // USE_READ_BARRIER

ENTRY art_quick_aput_obj
    beqz  a2, .Laput_obj_null
#if defined(USE_READ_BARRIER) && !defined(USE_BAKER_READ_BARRIER)
    READ_BARRIER_SLOW a3, a0, MIRROR_OBJECT_CLASS_OFFSET
    READ_BARRIER_SLOW a3, a3, MIRROR_CLASS_COMPONENT_TYPE_OFFSET
    READ_BARRIER_SLOW a4, a2, MIRROR_OBJECT_CLASS_OFFSET
#else  // !defined(USE_READ_BARRIER) || defined(USE_BAKER_READ_BARRIER)
#ifdef USE_READ_BARRIER
    // TODO(riscv64): Define marking register to avoid this load.
    lw    t6, THREAD_IS_GC_MARKING_OFFSET(xSELF)
    bnez  t6, .Laput_obj_gc_marking
#endif  // USE_READ_BARRIER
    lwu   a3, MIRROR_OBJECT_CLASS_OFFSET(a0)           // Heap reference = 32b; zero-extends to a3.
    UNPOISON_HEAP_REF a3
    lwu   a3, MIRROR_CLASS_COMPONENT_TYPE_OFFSET(a3)   // Heap reference = 32b; zero-extends to a3.
    UNPOISON_HEAP_REF a3
    lwu   a4, MIRROR_OBJECT_CLASS_OFFSET(a2)           // Heap reference = 32b; zero-extends to a4.
    UNPOISON_HEAP_REF a4
#endif  // !defined(USE_READ_BARRIER) || defined(USE_BAKER_READ_BARRIER)
    // value's type == array's component type - trivial assignability
    bne   a3, a4, .Laput_obj_check_assignability
.Laput_obj_store:
    sh2add a3, a1, a0
    POISON_HEAP_REF a2
    sw    a2, MIRROR_OBJECT_ARRAY_DATA_OFFSET(a3)      // Heap reference = 32b.
    ld    a3, THREAD_CARD_TABLE_OFFSET(xSELF)
    srli  a0, a0, CARD_TABLE_CARD_SHIFT
    add   a0, a0, a3
    sb    a3, (a0)
    ret

.Laput_obj_null:
    sh2add a3, a1, a0
    sw    a2, MIRROR_OBJECT_ARRAY_DATA_OFFSET(a3)      // Heap reference = 32b.
    ret

.Laput_obj_check_assignability:
    // Store arguments and return register
    INCREASE_FRAME 32
    SAVE_GPR a0, 0*8
    SAVE_GPR a1, 1*8
    SAVE_GPR a2, 2*8
    SAVE_GPR ra, 3*8

    // Call runtime code
    mv    a0, a3          // Heap reference, 32b, "uncompress" = do nothing, already zero-extended.
    mv    a1, a4          // Heap reference, 32b, "uncompress" = do nothing, already zero-extended.
    call  artIsAssignableFromCode

    // Check for exception
    CFI_REMEMBER_STATE
    beqz a0, .Laput_obj_throw_array_store_exception

    // Restore
    RESTORE_GPR a0, 0*8
    RESTORE_GPR a1, 1*8
    RESTORE_GPR a2, 2*8
    RESTORE_GPR ra, 3*8
    DECREASE_FRAME 32

    sh2add a3, a1, a0
    POISON_HEAP_REF a2
    sw    a2, MIRROR_OBJECT_ARRAY_DATA_OFFSET(a3)      // Heap reference = 32b.
    ld    a3, THREAD_CARD_TABLE_OFFSET(xSELF)
    srli  a0, a0, CARD_TABLE_CARD_SHIFT
    add   a0, a0, a3
    sb    a3, (a0)
    ret

.Laput_obj_throw_array_store_exception:
    CFI_RESTORE_STATE_AND_DEF_CFA sp, 32
    RESTORE_GPR a0, 0*8
    RESTORE_GPR a1, 1*8
    RESTORE_GPR a2, 2*8
    RESTORE_GPR ra, 3*8
    DECREASE_FRAME 32

#if defined(USE_READ_BARRIER) && defined(USE_BAKER_READ_BARRIER)
    CFI_REMEMBER_STATE
#endif  // defined(USE_READ_BARRIER) && defined(USE_BAKER_READ_BARRIER)
    SETUP_SAVE_ALL_CALLEE_SAVES_FRAME
    mv    a1, a2                        // Pass value.
    mv    a2, xSELF                     // Pass Thread::Current().
    call  artThrowArrayStoreException   // (Object*, Object*, Thread*).
    unimp                               // Unreachable.

#if defined(USE_READ_BARRIER) && defined(USE_BAKER_READ_BARRIER)
    CFI_RESTORE_STATE_AND_DEF_CFA sp, 0
.Laput_obj_gc_marking:
    BAKER_RB_CHECK_GRAY_BIT_AND_LOAD \
        a3, a0, MIRROR_OBJECT_CLASS_OFFSET, .Laput_obj_mark_array_class
.Laput_obj_mark_array_class_continue:
    BAKER_RB_CHECK_GRAY_BIT_AND_LOAD \
        a3, a3, MIRROR_CLASS_COMPONENT_TYPE_OFFSET, .Laput_obj_mark_array_element
.Laput_obj_mark_array_element_continue:
    BAKER_RB_CHECK_GRAY_BIT_AND_LOAD \
        a4, a2, MIRROR_OBJECT_CLASS_OFFSET, .Laput_obj_mark_object_class
.Laput_obj_mark_object_class_continue:
    // value's type == array's component type - trivial assignability
    bne   a3, a4, .Laput_obj_check_assignability
    j     .Laput_obj_store

.Laput_obj_mark_array_class:
    BAKER_RB_LOAD_AND_MARK a3, a0, MIRROR_OBJECT_CLASS_OFFSET, art_quick_read_barrier_mark_reg13
    j     .Laput_obj_mark_array_class_continue

.Laput_obj_mark_array_element:
    BAKER_RB_LOAD_AND_MARK \
        a3, a3, MIRROR_CLASS_COMPONENT_TYPE_OFFSET, art_quick_read_barrier_mark_reg13
    j     .Laput_obj_mark_array_element_continue

.Laput_obj_mark_object_class:
    BAKER_RB_LOAD_AND_MARK a4, a2, MIRROR_OBJECT_CLASS_OFFSET, art_quick_read_barrier_mark_reg14
    j     .Laput_obj_mark_object_class_continue
#endif  // defined(USE_READ_BARRIER) && defined(USE_BAKER_READ_BARRIER)
END art_quick_aput_obj


// Create a function `name` calling the art::ReadBarrier::Mark routine, getting its argument and
// returning its result through \reg, saving and restoring all caller-save registers.
//
// The generated function follows a non-standard calling convention:
// - register `reg` is used to pass the singleton argument,
// - register `reg` is used to return the result,
// - all other registers are callee-save (the values they hold are preserved).
.macro READ_BARRIER_MARK_REG name, reg
ENTRY \name
    beqz \reg, .Lrb_return_\name  // early return if null

    // Save t5 and t6 onto stack to honor caller-save calling convention.
    INCREASE_FRAME 16
    SAVE_GPR t5, (8*0)
    SAVE_GPR t6, (8*1)

    lw t5, MIRROR_OBJECT_LOCK_WORD_OFFSET(\reg)  // t5 := lock word
    slliw t6, t5, 31-LOCK_WORD_MARK_BIT_SHIFT    // mark bit into MSB
    bltz t6, .Lrb_tmp_restore_\name
    // Check if the top two bits are set. If so, it is a forwarding address.
    slliw t6, t5, 1
    and t6, t6, t5
    CFI_REMEMBER_STATE
    bgez t6, .Lrb_full_\name
    // Extract and zero-extend the forwarding address.
    slli \reg, t5, (LOCK_WORD_STATE_FORWARDING_ADDRESS_SHIFT + 32)
    srli \reg, \reg, 32
    .ifc \reg, t5
      sd t5, (8*0)(sp)
    .endif
    .ifc \reg, t6
      sd t6, (8*1)(sp)
    .endif
.Lrb_tmp_restore_\name:
    RESTORE_GPR t5, (8*0)
    RESTORE_GPR t6, (8*1)
    DECREASE_FRAME 16
.Lrb_return_\name:
    ret
.Lrb_full_\name:
    CFI_RESTORE_STATE_AND_DEF_CFA sp, 16
    // Save remaining caller-save registers on stack. t5 and t6 already saved.
    // fa0-fa7, ft0-ft11, a0-a7, t0-t4, ra: 8 * (8 + 12 + 8 + 5 + 1) = 8 * 34 = 272 bytes
    INCREASE_FRAME 272
    SAVE_FPR fa0,  (8*0)
    SAVE_FPR fa1,  (8*1)
    SAVE_FPR fa2,  (8*2)
    SAVE_FPR fa3,  (8*3)
    SAVE_FPR fa4,  (8*4)
    SAVE_FPR fa5,  (8*5)
    SAVE_FPR fa6,  (8*6)
    SAVE_FPR fa7,  (8*7)
    SAVE_FPR ft0,  (8*8)
    SAVE_FPR ft1,  (8*9)
    SAVE_FPR ft2,  (8*10)
    SAVE_FPR ft3,  (8*11)
    SAVE_FPR ft4,  (8*12)
    SAVE_FPR ft5,  (8*13)
    SAVE_FPR ft6,  (8*14)
    SAVE_FPR ft7,  (8*15)
    SAVE_FPR ft8,  (8*16)
    SAVE_FPR ft9,  (8*17)
    SAVE_FPR ft10, (8*18)
    SAVE_FPR ft11, (8*19)

    SAVE_GPR a0,   (8*20)
    SAVE_GPR a1,   (8*21)
    SAVE_GPR a2,   (8*22)
    SAVE_GPR a3,   (8*23)
    SAVE_GPR a4,   (8*24)
    SAVE_GPR a5,   (8*25)
    SAVE_GPR a6,   (8*26)
    SAVE_GPR a7,   (8*27)
    SAVE_GPR t0,   (8*28)
    SAVE_GPR t1,   (8*29)
    SAVE_GPR t2,   (8*30)
    SAVE_GPR t3,   (8*31)
    SAVE_GPR t4,   (8*32)
    SAVE_GPR ra,   (8*33)

    .ifc \reg, t5
      ld a0, (8*34)(sp)
    .else
      .ifc \reg, t6
        ld a0, (8*35)(sp)
      .else
        .ifnc \reg, a0
          mv a0, \reg
        .endif
      .endif
    .endif
    call artReadBarrierMark
    .ifnc \reg, a0
      mv \reg, a0
    .endif

    // Restore all caller-save registers from stack, including t5 and t6.
    // fa0-fa7, ft0-ft11, ra, a0-a7, t0-t6: 8 * (8 + 12 + 1 + 8 + 7) = 8 * 36 = 288 bytes
    RESTORE_FPR fa0,  (8*0)
    RESTORE_FPR fa1,  (8*1)
    RESTORE_FPR fa2,  (8*2)
    RESTORE_FPR fa3,  (8*3)
    RESTORE_FPR fa4,  (8*4)
    RESTORE_FPR fa5,  (8*5)
    RESTORE_FPR fa6,  (8*6)
    RESTORE_FPR fa7,  (8*7)
    RESTORE_FPR ft0,  (8*8)
    RESTORE_FPR ft1,  (8*9)
    RESTORE_FPR ft2,  (8*10)
    RESTORE_FPR ft3,  (8*11)
    RESTORE_FPR ft4,  (8*12)
    RESTORE_FPR ft5,  (8*13)
    RESTORE_FPR ft6,  (8*14)
    RESTORE_FPR ft7,  (8*15)
    RESTORE_FPR ft8,  (8*16)
    RESTORE_FPR ft9,  (8*17)
    RESTORE_FPR ft10, (8*18)
    RESTORE_FPR ft11, (8*19)
    RESTORE_GPR_NE \reg, a0, (8*20)
    RESTORE_GPR_NE \reg, a1, (8*21)
    RESTORE_GPR_NE \reg, a2, (8*22)
    RESTORE_GPR_NE \reg, a3, (8*23)
    RESTORE_GPR_NE \reg, a4, (8*24)
    RESTORE_GPR_NE \reg, a5, (8*25)
    RESTORE_GPR_NE \reg, a6, (8*26)
    RESTORE_GPR_NE \reg, a7, (8*27)
    RESTORE_GPR_NE \reg, t0, (8*28)
    RESTORE_GPR_NE \reg, t1, (8*29)
    RESTORE_GPR_NE \reg, t2, (8*30)
    RESTORE_GPR_NE \reg, t3, (8*31)
    RESTORE_GPR_NE \reg, t4, (8*32)
    RESTORE_GPR_NE \reg, ra, (8*33)
    RESTORE_GPR_NE \reg, t5, (8*34)
    RESTORE_GPR_NE \reg, t6, (8*35)
    DECREASE_FRAME 288
    ret
END \name
.endm


// No read barrier for X0 (Zero), X1 (RA), X2 (SP), X3 (GP) and X4 (TP).
READ_BARRIER_MARK_REG art_quick_read_barrier_mark_reg05, t0
READ_BARRIER_MARK_REG art_quick_read_barrier_mark_reg06, t1
READ_BARRIER_MARK_REG art_quick_read_barrier_mark_reg07, t2
READ_BARRIER_MARK_REG art_quick_read_barrier_mark_reg08, s0
// No read barrier for X9 (S1/xSELF).
READ_BARRIER_MARK_REG art_quick_read_barrier_mark_reg10, a0
READ_BARRIER_MARK_REG art_quick_read_barrier_mark_reg11, a1
READ_BARRIER_MARK_REG art_quick_read_barrier_mark_reg12, a2
READ_BARRIER_MARK_REG art_quick_read_barrier_mark_reg13, a3
READ_BARRIER_MARK_REG art_quick_read_barrier_mark_reg14, a4
READ_BARRIER_MARK_REG art_quick_read_barrier_mark_reg15, a5
READ_BARRIER_MARK_REG art_quick_read_barrier_mark_reg16, a6
READ_BARRIER_MARK_REG art_quick_read_barrier_mark_reg17, a7
READ_BARRIER_MARK_REG art_quick_read_barrier_mark_reg18, s2
READ_BARRIER_MARK_REG art_quick_read_barrier_mark_reg19, s3
READ_BARRIER_MARK_REG art_quick_read_barrier_mark_reg20, s4
READ_BARRIER_MARK_REG art_quick_read_barrier_mark_reg21, s5
READ_BARRIER_MARK_REG art_quick_read_barrier_mark_reg22, s6
READ_BARRIER_MARK_REG art_quick_read_barrier_mark_reg23, s7
READ_BARRIER_MARK_REG art_quick_read_barrier_mark_reg24, s8
READ_BARRIER_MARK_REG art_quick_read_barrier_mark_reg25, s9
READ_BARRIER_MARK_REG art_quick_read_barrier_mark_reg26, s10
READ_BARRIER_MARK_REG art_quick_read_barrier_mark_reg27, s11
READ_BARRIER_MARK_REG art_quick_read_barrier_mark_reg28, t3
READ_BARRIER_MARK_REG art_quick_read_barrier_mark_reg29, t4
READ_BARRIER_MARK_REG art_quick_read_barrier_mark_reg30, t5
READ_BARRIER_MARK_REG art_quick_read_barrier_mark_reg31, t6


.macro N_ARG_DOWNCALL n, name, entrypoint, return
    .extern \entrypoint
ENTRY \name
    SETUP_SAVE_REFS_ONLY_FRAME        // Save callee saves in case of GC.
    mv    a\n, xSELF                  // Pass Thread::Current().
    call  \entrypoint                 // (<n args>, Thread*)
    RESTORE_SAVE_REFS_ONLY_FRAME
    \return
END \name
.endm


.macro ONE_ARG_DOWNCALL name, entrypoint, return
    N_ARG_DOWNCALL 1, \name, \entrypoint, \return
.endm


.macro TWO_ARG_DOWNCALL name, entrypoint, return
    N_ARG_DOWNCALL 2, \name, \entrypoint, \return
.endm


.macro THREE_ARG_DOWNCALL name, entrypoint, return
    N_ARG_DOWNCALL 3, \name, \entrypoint, \return
.endm


.macro FOUR_ARG_DOWNCALL name, entrypoint, return
    N_ARG_DOWNCALL 4, \name, \entrypoint, \return
.endm


// Entry from managed code that calls artHandleFillArrayDataFromCode and
// delivers exception on failure.
TWO_ARG_DOWNCALL art_quick_handle_fill_data, \
                 artHandleFillArrayDataFromCode, \
                 RETURN_OR_DEOPT_IF_INT_RESULT_IS_ZERO_OR_DELIVER


// Generate the allocation entrypoints for each allocator.
GENERATE_ALLOC_ENTRYPOINTS_FOR_NON_TLAB_ALLOCATORS
// Comment out allocators that have riscv64 specific asm.
// GENERATE_ALLOC_ENTRYPOINTS_ALLOC_OBJECT_RESOLVED(_region_tlab, RegionTLAB)
// GENERATE_ALLOC_ENTRYPOINTS_ALLOC_OBJECT_INITIALIZED(_region_tlab, RegionTLAB)
GENERATE_ALLOC_ENTRYPOINTS_ALLOC_OBJECT_WITH_ACCESS_CHECK(_region_tlab, RegionTLAB)
GENERATE_ALLOC_ENTRYPOINTS_ALLOC_STRING_OBJECT(_region_tlab, RegionTLAB)
// GENERATE_ALLOC_ENTRYPOINTS_ALLOC_ARRAY_RESOLVED(_region_tlab, RegionTLAB)
// GENERATE_ALLOC_ENTRYPOINTS_ALLOC_ARRAY_RESOLVED8(_region_tlab, RegionTLAB)
// GENERATE_ALLOC_ENTRYPOINTS_ALLOC_ARRAY_RESOLVED16(_region_tlab, RegionTLAB)
// GENERATE_ALLOC_ENTRYPOINTS_ALLOC_ARRAY_RESOLVED32(_region_tlab, RegionTLAB)
// GENERATE_ALLOC_ENTRYPOINTS_ALLOC_ARRAY_RESOLVED64(_region_tlab, RegionTLAB)
GENERATE_ALLOC_ENTRYPOINTS_ALLOC_STRING_FROM_BYTES(_region_tlab, RegionTLAB)
GENERATE_ALLOC_ENTRYPOINTS_ALLOC_STRING_FROM_CHARS(_region_tlab, RegionTLAB)
GENERATE_ALLOC_ENTRYPOINTS_ALLOC_STRING_FROM_STRING(_region_tlab, RegionTLAB)

// GENERATE_ALLOC_ENTRYPOINTS_ALLOC_OBJECT_RESOLVED(_tlab, TLAB)
// GENERATE_ALLOC_ENTRYPOINTS_ALLOC_OBJECT_INITIALIZED(_tlab, TLAB)
GENERATE_ALLOC_ENTRYPOINTS_ALLOC_OBJECT_WITH_ACCESS_CHECK(_tlab, TLAB)
GENERATE_ALLOC_ENTRYPOINTS_ALLOC_STRING_OBJECT(_tlab, TLAB)
// GENERATE_ALLOC_ENTRYPOINTS_ALLOC_ARRAY_RESOLVED(_tlab, TLAB)
// GENERATE_ALLOC_ENTRYPOINTS_ALLOC_ARRAY_RESOLVED8(_tlab, TLAB)
// GENERATE_ALLOC_ENTRYPOINTS_ALLOC_ARRAY_RESOLVED16(_tlab, TLAB)
// GENERATE_ALLOC_ENTRYPOINTS_ALLOC_ARRAY_RESOLVED32(_tlab, TLAB)
// GENERATE_ALLOC_ENTRYPOINTS_ALLOC_ARRAY_RESOLVED64(_tlab, TLAB)
GENERATE_ALLOC_ENTRYPOINTS_ALLOC_STRING_FROM_BYTES(_tlab, TLAB)
GENERATE_ALLOC_ENTRYPOINTS_ALLOC_STRING_FROM_CHARS(_tlab, TLAB)
GENERATE_ALLOC_ENTRYPOINTS_ALLOC_STRING_FROM_STRING(_tlab, TLAB)

// If isInitialized=1 then the compiler assumes the object's class has already been initialized.
// If isInitialized=0 the compiler can only assume it's been at least resolved.
.macro ART_QUICK_ALLOC_OBJECT_ROSALLOC c_name, cxx_name, isInitialized
ENTRY \c_name
    // Fast path rosalloc allocation.
    // a0: type, xSELF(s1): Thread::Current
    // a1-a7: free.
    ld    a3, THREAD_LOCAL_ALLOC_STACK_TOP_OFFSET(xSELF)    // Check if the thread local
                                                            // allocation stack has room.
                                                            // ldp won't work due to large offset.
    ld    a4, THREAD_LOCAL_ALLOC_STACK_END_OFFSET(xSELF)
    bgeu  a3, a4, .Lslow_path\c_name
    lwu   a3, MIRROR_CLASS_OBJECT_SIZE_ALLOC_FAST_PATH_OFFSET(a0)  // Load the object size (a3)
    li    a5, ROSALLOC_MAX_THREAD_LOCAL_BRACKET_SIZE        // Check if the size is for a thread
                                                            // local allocation.
    // If the class is not yet visibly initialized, or it is finalizable,
    // the object size will be very large to force the branch below to be taken.
    //
    // See Class::SetStatus() in class.cc for more details.
    bgeu  a3, a5, .Lslow_path\c_name
                                                            // Compute the rosalloc bracket index
                                                            // from the size. Since the size is
                                                            // already aligned we can combine the
                                                            // two shifts together.
#if ROSALLOC_BRACKET_QUANTUM_SIZE_SHIFT != POINTER_SIZE_SHIFT
#error "Unexpected ROSALLOC_BRACKET_QUANTUM_SIZE_SHIFT != POINTER_SIZE_SHIFT"
#endif
    // No-op: srli  a3, a3, (ROSALLOC_BRACKET_QUANTUM_SIZE_SHIFT - POINTER_SIZE_SHIFT)
    add   a4, xSELF, a3
                                                            // Subtract pointer size since there
                                                            // are no runs for 0 byte allocations
                                                            // and the size is already aligned.
    ld    a4, (THREAD_ROSALLOC_RUNS_OFFSET - __SIZEOF_POINTER__)(a4)
                                                            // Load the free list head (a3). This
                                                            // will be the return val.
    ld    a3, (ROSALLOC_RUN_FREE_LIST_OFFSET + ROSALLOC_RUN_FREE_LIST_HEAD_OFFSET)(a4)
    beqz  a3, .Lslow_path\c_name
    // "Point of no slow path". Won't go to the slow path from here on. OK to clobber a0 and a1.
    ld    a1, ROSALLOC_SLOT_NEXT_OFFSET(a3)                 // Load the next pointer of the head
                                                            // and update the list head with the
                                                            // next pointer.
    sd    a1, (ROSALLOC_RUN_FREE_LIST_OFFSET + ROSALLOC_RUN_FREE_LIST_HEAD_OFFSET)(a4)
                                                            // Store the class pointer in the
                                                            // header. This also overwrites the
                                                            // next pointer. The offsets are
                                                            // asserted to match.

#if ROSALLOC_SLOT_NEXT_OFFSET != MIRROR_OBJECT_CLASS_OFFSET
#error "Class pointer needs to overwrite next pointer."
#endif
    POISON_HEAP_REF a0
    sw    a0, MIRROR_OBJECT_CLASS_OFFSET(a3)
                                                            // Push the new object onto the thread
                                                            // local allocation stack and
                                                            // increment the thread local
                                                            // allocation stack top.
    ld    a1, THREAD_LOCAL_ALLOC_STACK_TOP_OFFSET(xSELF)
    sw    a3, (a1)
    addi  a1, a1, COMPRESSED_REFERENCE_SIZE                 // Increment A1 to point to next slot.
    sd    a1, THREAD_LOCAL_ALLOC_STACK_TOP_OFFSET(xSELF)    // Decrement the size of the free list.

    // After this "SD" the object is published to the thread local allocation stack,
    // and it will be observable from a runtime internal (eg. Heap::VisitObjects) point of view.
    // It is not yet visible to the running (user) compiled code until after the return.
    //
    // To avoid the memory barrier prior to the "SD", a trick is employed, by differentiating
    // the state of the allocation stack slot. It can be a pointer to one of:
    // 0) Null entry, because the stack was bumped but the new pointer wasn't written yet.
    //       (The stack initial state is "null" pointers).
    // 1) A partially valid object, with an invalid class pointer to the next free rosalloc slot.
    // 2) A fully valid object, with a valid class pointer pointing to a real class.
    // Other states are not allowed.
    //
    // An object that is invalid only temporarily, and will eventually become valid.
    // The internal runtime code simply checks if the object is not null or is partial and then
    // ignores it.
    //
    // (Note: The actual check is done by seeing if a non-null object has a class pointer pointing
    // to ClassClass, and that the ClassClass's class pointer is self-cyclic. A rosalloc free slot
    // "next" pointer is not-cyclic.)
    //
    // See also b/28790624 for a listing of CLs dealing with this race.
    lwu   a1, (ROSALLOC_RUN_FREE_LIST_OFFSET + ROSALLOC_RUN_FREE_LIST_SIZE_OFFSET)(a4)
    addi  a1, a1, -1
    sw    a1, (ROSALLOC_RUN_FREE_LIST_OFFSET + ROSALLOC_RUN_FREE_LIST_SIZE_OFFSET)(a4)

    mv    a0, a3                                            // Set the return value and return.
    // No barrier. The class is already observably initialized (otherwise the fast
    // path size check above would fail) and new-instance allocations are protected
    // from publishing by the compiler which inserts its own StoreStore barrier.
    ret
.Lslow_path\c_name:
    SETUP_SAVE_REFS_ONLY_FRAME                              // Save callee saves in case of GC.
    mv    a1, xSELF                                         // Pass Thread::Current().
    call  \cxx_name
    RESTORE_SAVE_REFS_ONLY_FRAME
    RETURN_OR_DEOPT_IF_RESULT_IS_NON_NULL_OR_DELIVER
END \c_name
.endm

ART_QUICK_ALLOC_OBJECT_ROSALLOC art_quick_alloc_object_resolved_rosalloc, \
                                artAllocObjectFromCodeResolvedRosAlloc, /* isInitialized */ 0
ART_QUICK_ALLOC_OBJECT_ROSALLOC art_quick_alloc_object_initialized_rosalloc, \
                                artAllocObjectFromCodeInitializedRosAlloc, /* isInitialized */ 1

// If isInitialized=1 then the compiler assumes the object's class has already been initialized.
// If isInitialized=0 the compiler can only assume it's been at least resolved.
.macro ALLOC_OBJECT_TLAB_FAST_PATH_RESOLVED slowPathLabel isInitialized
    ld    a4, THREAD_LOCAL_POS_OFFSET(xSELF)
    ld    a5, THREAD_LOCAL_END_OFFSET(xSELF)
    lwu   a7, MIRROR_CLASS_OBJECT_SIZE_ALLOC_FAST_PATH_OFFSET(a0)  // Load the object size (a7).
    add   a6, a4, a7                                        // Add object size to tlab pos.
                                                            // Check if it fits, overflow works
                                                            // since the tlab pos and end are 32
                                                            // bit values.

    // If the class is not yet visibly initialized, or it is finalizable,
    // the object size will be very large to force the branch below to be taken.
    //
    // See Class::SetStatus() in class.cc for more details.
    bgtu  a6, a5, \slowPathLabel
    sd    a6, THREAD_LOCAL_POS_OFFSET(xSELF)                // Store new thread_local_pos.
    POISON_HEAP_REF a0
    sw    a0, MIRROR_OBJECT_CLASS_OFFSET(a4)                // Store the class pointer.
    mv    a0, a4
    // No barrier. The class is already observably initialized (otherwise the fast
    // path size check above would fail) and new-instance allocations are protected
    // from publishing by the compiler which inserts its own StoreStore barrier.
    ret
.endm

// The common code for art_quick_alloc_object_*region_tlab
// Currently the implementation ignores isInitialized. TODO(b/172087402): clean this up.
// Caller must execute a constructor fence after this.
.macro GENERATE_ALLOC_OBJECT_RESOLVED_TLAB name, entrypoint, isInitialized
ENTRY \name
    // Fast path region tlab allocation.
    // a0: type, xSELF(s1): Thread::Current
    // a1-a7: free.
    ALLOC_OBJECT_TLAB_FAST_PATH_RESOLVED .Lslow_path\name, \isInitialized
.Lslow_path\name:
    SETUP_SAVE_REFS_ONLY_FRAME                              // Save callee saves in case of GC.
    mv    a1, xSELF                                         // Pass Thread::Current().
    call  \entrypoint                                       // (mirror::Class*, Thread*)
    RESTORE_SAVE_REFS_ONLY_FRAME
    RETURN_OR_DEOPT_IF_RESULT_IS_NON_NULL_OR_DELIVER
END \name
.endm

GENERATE_ALLOC_OBJECT_RESOLVED_TLAB \
    art_quick_alloc_object_resolved_region_tlab, \
    artAllocObjectFromCodeResolvedRegionTLAB, /* isInitialized */ 0
GENERATE_ALLOC_OBJECT_RESOLVED_TLAB \
    art_quick_alloc_object_initialized_region_tlab, \
    artAllocObjectFromCodeInitializedRegionTLAB, /* isInitialized */ 1
GENERATE_ALLOC_OBJECT_RESOLVED_TLAB \
    art_quick_alloc_object_resolved_tlab, \
    artAllocObjectFromCodeResolvedTLAB, /* isInitialized */ 0
GENERATE_ALLOC_OBJECT_RESOLVED_TLAB \
    art_quick_alloc_object_initialized_tlab, \
    artAllocObjectFromCodeInitializedTLAB, /* isInitialized */ 1

.macro ALLOC_ARRAY_TLAB_FAST_PATH_RESOLVED_WITH_SIZE \
    slowPathLabel, class, count, temp0, temp1, temp2
    andi  \temp1, \temp1, OBJECT_ALIGNMENT_MASK_TOGGLED64   // Apply alignment mask
                                                            // (addr + 7) & ~7. The mask must
                                                            // be 64 bits to keep high bits in
                                                            // case of overflow.
    // Negative sized arrays are handled here since xCount holds a zero extended 32 bit value.
    // Negative ints become large 64 bit unsigned ints which will always be larger than max signed
    // 32 bit int. Since the max shift for arrays is 3, it can not become a negative 64 bit int.
    li    \temp2, MIN_LARGE_OBJECT_THRESHOLD                // Possibly a large object, go slow
    bgeu  \temp1, \temp2, \slowPathLabel                    // path.

    ld    \temp0, THREAD_LOCAL_POS_OFFSET(xSELF)            // Check tlab for space, note that
                                                            // we use (end - begin) to handle
                                                            // negative size arrays. It is
                                                            // assumed that a negative size will
                                                            // always be greater unsigned than
                                                            // region size.
    ld    \temp2, THREAD_LOCAL_END_OFFSET(xSELF)
    sub   \temp2, \temp2, \temp0

    // The array class is always initialized here. Unlike new-instance,
    // this does not act as a double test.
    bgtu  \temp1, \temp2, \slowPathLabel
    // "Point of no slow path". Won't go to the slow path from here on. OK to clobber x0 and x1.
                                                            // Move old thread_local_pos to x0
                                                            // for the return value.
    mv    a0, \temp0
    add   \temp0, \temp0, \temp1
    sd    \temp0, THREAD_LOCAL_POS_OFFSET(xSELF)            // Store new thread_local_pos.
    POISON_HEAP_REF \class
    sw    \class, MIRROR_OBJECT_CLASS_OFFSET(a0)            // Store the class pointer.
    sw    \count, MIRROR_ARRAY_LENGTH_OFFSET(a0)            // Store the array length.
// new-array is special. The class is loaded and immediately goes to the Initialized state
// before it is published. Therefore the only fence needed is for the publication of the object.
// See ClassLinker::CreateArrayClass() for more details.

// For publication of the new array, we don't need a 'fence w, w' here.
// The compiler generates 'fence w, w' for all new-array insts.
    ret
.endm

// Caller must execute a constructor fence after this.
.macro GENERATE_ALLOC_ARRAY_TLAB name, entrypoint, size_setup
ENTRY \name
    // Fast path array allocation for region tlab allocation.
    // a0: mirror::Class* type
    // a1: int32_t component_count
    // a2-a7: free.
    mv    a3, a0
    \size_setup a3, a1, a4, a5, a6
    ALLOC_ARRAY_TLAB_FAST_PATH_RESOLVED_WITH_SIZE .Lslow_path\name, a3, a1, a4, a5, a6
.Lslow_path\name:
    // a0: mirror::Class* klass
    // a1: int32_t component_count
    // a2: Thread* self
    SETUP_SAVE_REFS_ONLY_FRAME                              // Save callee saves in case of GC.
    mv    a2, xSELF                                         // Pass Thread::Current().
    call  \entrypoint
    RESTORE_SAVE_REFS_ONLY_FRAME
    RETURN_OR_DEOPT_IF_RESULT_IS_NON_NULL_OR_DELIVER
END \name
.endm

.macro COMPUTE_ARRAY_SIZE_UNKNOWN class, count, temp0, temp1, temp2
    // Array classes are never finalizable or uninitialized, no need to check.
    lwu   \temp0, MIRROR_CLASS_COMPONENT_TYPE_OFFSET(\class) // Load component type
    UNPOISON_HEAP_REF \temp0
    lwu   \temp0, MIRROR_CLASS_OBJECT_PRIMITIVE_TYPE_OFFSET(\temp0)
    srli  \temp0, \temp0, PRIMITIVE_TYPE_SIZE_SHIFT_SHIFT   // Component size shift is in high 16
                                                            // bits.
    zext.w \temp1, \count                                   // From \count we use a 32 bit value,
                                                            // it can not overflow.
    sll   \temp1, \temp1, \temp0                            // Calculate data size
    // Add array data offset and alignment.
    addi   \temp1, \temp1, (MIRROR_INT_ARRAY_DATA_OFFSET + OBJECT_ALIGNMENT_MASK)
#if MIRROR_LONG_ARRAY_DATA_OFFSET != MIRROR_INT_ARRAY_DATA_OFFSET + 4
#error Long array data offset must be 4 greater than int array data offset.
#endif

    addi  \temp0, \temp0, 1                                 // Add 4 to the length only if the
                                                            // component size shift is 3
                                                            // (for 64 bit alignment).
    andi  \temp0, \temp0, 4
    add   \temp1, \temp1, \temp0
.endm

.macro COMPUTE_ARRAY_SIZE_8 class, count, temp0, temp1, temp2
    // Add array data offset and alignment adjustment to the `\count`.
    li     \temp1, (MIRROR_INT_ARRAY_DATA_OFFSET + OBJECT_ALIGNMENT_MASK)
    add.uw \temp1, \count, \temp1
.endm

.macro COMPUTE_ARRAY_SIZE_16 class, count, temp0, temp1, temp2
    // Add array data offset and alignment adjustment to the shifted `\count`.
    li     \temp1, (MIRROR_INT_ARRAY_DATA_OFFSET + OBJECT_ALIGNMENT_MASK)
    sh1add.uw \temp1, \count, \temp1
.endm

.macro COMPUTE_ARRAY_SIZE_32 class, count, temp0, temp1, temp2
    // Add array data offset and alignment adjustment to the shifted `\count`.
    li     \temp1, (MIRROR_INT_ARRAY_DATA_OFFSET + OBJECT_ALIGNMENT_MASK)
    sh2add.uw \temp1, \count, \temp1
.endm

.macro COMPUTE_ARRAY_SIZE_64 class, count, temp0, temp1, temp2
    // Add array data offset and alignment adjustment to the shifted `\count`.
    li     \temp1, (MIRROR_WIDE_ARRAY_DATA_OFFSET + OBJECT_ALIGNMENT_MASK)
    sh3add.uw \temp1, \count, \temp1
.endm

// TODO(ngeoffray): art_quick_alloc_array_resolved_region_tlab is not used for arm64, remove
// the entrypoint once all backends have been updated to use the size variants.
GENERATE_ALLOC_ARRAY_TLAB art_quick_alloc_array_resolved_region_tlab, \
                          artAllocArrayFromCodeResolvedRegionTLAB, \
                          COMPUTE_ARRAY_SIZE_UNKNOWN
GENERATE_ALLOC_ARRAY_TLAB art_quick_alloc_array_resolved8_region_tlab, \
                          artAllocArrayFromCodeResolvedRegionTLAB, \
                          COMPUTE_ARRAY_SIZE_8
GENERATE_ALLOC_ARRAY_TLAB art_quick_alloc_array_resolved16_region_tlab, \
                          artAllocArrayFromCodeResolvedRegionTLAB, \
                          COMPUTE_ARRAY_SIZE_16
GENERATE_ALLOC_ARRAY_TLAB art_quick_alloc_array_resolved32_region_tlab, \
                          artAllocArrayFromCodeResolvedRegionTLAB, \
                          COMPUTE_ARRAY_SIZE_32
GENERATE_ALLOC_ARRAY_TLAB art_quick_alloc_array_resolved64_region_tlab, \
                          artAllocArrayFromCodeResolvedRegionTLAB, \
                          COMPUTE_ARRAY_SIZE_64
GENERATE_ALLOC_ARRAY_TLAB art_quick_alloc_array_resolved_tlab, \
                          artAllocArrayFromCodeResolvedTLAB, \
                          COMPUTE_ARRAY_SIZE_UNKNOWN
GENERATE_ALLOC_ARRAY_TLAB art_quick_alloc_array_resolved8_tlab, \
                          artAllocArrayFromCodeResolvedTLAB, \
                          COMPUTE_ARRAY_SIZE_8
GENERATE_ALLOC_ARRAY_TLAB art_quick_alloc_array_resolved16_tlab, \
                          artAllocArrayFromCodeResolvedTLAB, \
                          COMPUTE_ARRAY_SIZE_16
GENERATE_ALLOC_ARRAY_TLAB art_quick_alloc_array_resolved32_tlab, \
                          artAllocArrayFromCodeResolvedTLAB, \
                          COMPUTE_ARRAY_SIZE_32
GENERATE_ALLOC_ARRAY_TLAB art_quick_alloc_array_resolved64_tlab, \
                          artAllocArrayFromCodeResolvedTLAB, \
                          COMPUTE_ARRAY_SIZE_64


GENERATE_FIELD_ENTRYPOINTS


// String's indexOf.
//
// TODO: Not very optimized. We should use the vector extension.
// On entry:
//    a0:   string object (known non-null)
//    a1:   char to match (known <= 0xFFFF)
//    a2:   Starting offset in string data
ENTRY art_quick_indexof
#if (STRING_COMPRESSION_FEATURE)
    lwu   a4, MIRROR_STRING_COUNT_OFFSET(a0)
#else
    lwu   a3, MIRROR_STRING_COUNT_OFFSET(a0)
#endif
    addi  a0, a0, MIRROR_STRING_VALUE_OFFSET
#if (STRING_COMPRESSION_FEATURE)
    /* Split the count into length (a3) and compression flag (a4) */
    srliw a3, a4, 1
    andi  a4, a4, 1
#endif
    /* Clamp start to [0..count) */
    sraiw a5, a2, 31
    andn  a2, a2, a5
    bge   a2, a3, .Lstring_indexof_nomatch

#if (STRING_COMPRESSION_FEATURE)
    beqz  a4, .Lstring_indexof_compressed
#endif
    /* Build pointers to start and end of the data to compare */
    sh1add a2, a2, a0
    sh1add a3, a3, a0

    /*
     * At this point we have:
     *  a0: original start of string data
     *  a1: char to compare
     *  a2: start of the data to test
     *  a3: end of the data to test
     */

.Lstring_indexof_loop:
    lhu   a4, 0(a2)
    beq   a4, a1, .Lstring_indexof_match
    addi  a2, a2, 2
    bne   a2, a3, .Lstring_indexof_loop
.Lstring_indexof_nomatch:
    li    a0, -1
    ret
.Lstring_indexof_match:
    sub   a0, a2, a0
    srli  a0, a0, 1
    ret

#if (STRING_COMPRESSION_FEATURE)
   // Comparing compressed string one character at a time with the input character.
.Lstring_indexof_compressed:
    add   a2, a2, a0
    add   a3, a3, a0
.Lstring_indexof_compressed_loop:
    lbu   a4, (a2)
    beq   a4, a1, .Lstring_indexof_compressed_match
    addi  a2, a2, 1
    bne   a2, a3, .Lstring_indexof_compressed_loop
    li    a0, -1
    ret
.Lstring_indexof_compressed_match:
    sub   a0, a2, a0
    ret
#endif
END art_quick_indexof
