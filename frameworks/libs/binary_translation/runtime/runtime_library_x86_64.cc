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

#include "berberis/runtime_primitives/runtime_library.h"

#include "berberis/base/checks.h"
#include "berberis/base/config.h"
#include "berberis/guest_state/guest_state.h"

// Perform all the steps needed to exit generated code except return, which is
// up to the users of this macro. The users of this macro may choose to perform
// a sibling call as necessary.
// clang-format off
#define END_GENERATED_CODE(EXIT_INSN)                                   \
  asm(                                                                  \
      /* Sync insn_addr. */                                             \
      "mov %%rax, %[InsnAddr](%%rbp)\n"                                 \
      /* Set kOutsideGeneratedCode residence. */                        \
      "movb %[OutsideGeneratedCode], %[Residence](%%rbp)\n"             \
                                                                        \
      /* Set %rdi to the pointer to the guest state so that             \
       * we can perform a sibling call to functions like                \
       * berberis_HandleNotTranslated.                                  \
       */                                                               \
      "mov %%rbp, %%rdi\n"                                              \
                                                                        \
      /* Restore stack */                                               \
      "add %[FrameSizeAtTranslatedCode], %%rsp\n"                       \
                                                                        \
      /* Epilogue */                                                    \
      "pop %%r15\n"                                                     \
      "pop %%r14\n"                                                     \
      "pop %%r13\n"                                                     \
      "pop %%r12\n"                                                     \
      "pop %%rbx\n"                                                     \
      "pop %%rbp\n"                                                     \
      EXIT_INSN                                                         \
      ::[InsnAddr] "p"(offsetof(berberis::ThreadState, cpu.insn_addr)), \
      [Residence] "p"(offsetof(berberis::ThreadState, residence)),      \
      [OutsideGeneratedCode] "J"(berberis::kOutsideGeneratedCode),      \
      [FrameSizeAtTranslatedCode] "J"(berberis::config::kFrameSizeAtTranslatedCode))
// clang-format on

namespace berberis {

// "Calling conventions" among generated code and trampolines
// ==========================================================
//
// Introduction
// ------------
//
// To ensure the high performance of our generated code, we employ a couple of
// techniques:
//
// - We allow generated regions to jump among them without transferring control
//   back to Berberis runtime.
//
// - We use custom "calling conventions" that are different from the standard
//   x86_64 calling conventions, with some items passed in registers.
//
// Entry and exits
// ---------------
//
// Upon entry into generated code and trampoline adapters, we must have:
//
// - %rbp pointing to CPUState,
//
// - every field in CPUState up to date, except insn_addr, and
//
// - %rax containing up-to-date value for potentially stale CPUState::insn_addr.
//
// Since we jump among generated code and trampolines, each region must adhere
// to the "calling conventions" above as it exits.
//
// Each region is allowed to use the stack pointed to by %rsp. However, it must
// restore %rsp before exiting.
//
// %rbx, %rbp, and %r12-%r15 are callee saved, all other registers are
// "caller saved". That is, regions are allowed to use them without restoring
// their original values.
//
// Berberis -> generated code
// ---------------------------------
//
// If we are transferring control to generated code and trampolines from the
// Berberis runtime, such as ExecuteGuest, then we must do so via
// berberis_RunGeneratedCode, which is responsible for setting up registers for
// the "calling conventions".
//
// Generated code -> Berberis
// ---------------------------------
//
// When we are exiting generate code, we must do so via END_GENERATED_CODE macro
// defined in this file. The macro ensures that CPUState is fully up to date,
// including insn_addr, before transferring control back to the Berberis
// runtime.

extern "C" {

[[gnu::naked]] [[gnu::noinline]] void berberis_RunGeneratedCode(ThreadState* state, HostCode code) {
  // Parameters are in %rdi - state and %rsi - code
  //
  // On x86_64 Linux, stack should be aligned on 16 at every call insn.
  // That means stack is 8 mod 16 on function entry.
  // See https://software.intel.com/sites/default/files/article/402129/mpx-linux64-abi.pdf (3.2.2)
  //
  // Stack:
  //  0:               <- stack after prologue, aligned for next call
  //  8: saved r15     <- stack after prologue
  // 16: saved r14
  // 24: saved r13
  // 32: saved r12
  // 40: saved rbx
  // 48: saved rbp
  // 56: return addr
  // 00: <- stack at call insn - aligned on 16

  // clang-format off
  asm(
    // Prologue
    "push %%rbp\n"
    "push %%rbx\n"
    "push %%r12\n"
    "push %%r13\n"
    "push %%r14\n"
    "push %%r15\n"

    // Align stack for next call
    "sub %[FrameSizeAtTranslatedCode], %%rsp\n"  // kStackAlignAtCall, kFrameSizeAtTranslatedCode

    // Set state pointer
    "mov %%rdi, %%rbp\n"  // kStateRegister, kOmitFramePointer

    // Set insn_addr.
    "mov %[InsnAddr](%%rbp), %%rax\n"
    // Set kInsideGeneratedCode residence.
    "movb %[InsideGeneratedCode], %[Residence](%%rbp)\n"

    // Jump to entry
    "jmp *%%rsi"
    ::[InsnAddr] "p"(offsetof(ThreadState, cpu.insn_addr)),
    [Residence] "p"(offsetof(ThreadState, residence)),
    [InsideGeneratedCode] "J"(kInsideGeneratedCode),
    [FrameSizeAtTranslatedCode] "J"(config::kFrameSizeAtTranslatedCode));
  // clang-format on
}

extern "C" [[gnu::naked]] [[gnu::noinline]] void berberis_entry_Interpret() {
  // clang-format off
  asm(
    // Sync insn_addr.
    "mov %%rax, %[InsnAddr](%%rbp)\n"
    // Set kOutsideGeneratedCode residence.
    "movb %[OutsideGeneratedCode], %[Residence](%%rbp)\n"

    // rbp holds the pointer to state which is the argument to the call.
    "mov %%rbp, %%rdi\n"
    "call berberis_HandleInterpret\n"

    // rdi may be clobbered by the call above, so init it again.
    "mov %%rbp, %%rdi\n"
    "call berberis_GetDispatchAddress\n"
    "mov %%rax, %%rcx\n"

    // Set insn_addr.
    "mov %[InsnAddr](%%rbp), %%rax\n"
    // Set kInsideGeneratedCode residence.
    "movb %[InsideGeneratedCode], %[Residence](%%rbp)\n"

    "jmp *%%rcx\n"
    ::[InsnAddr] "p"(offsetof(berberis::ThreadState, cpu.insn_addr)),
    [Residence] "p"(offsetof(berberis::ThreadState, residence)),
    [OutsideGeneratedCode] "J"(berberis::kOutsideGeneratedCode),
    [InsideGeneratedCode] "J"(berberis::kInsideGeneratedCode),
    [FrameSizeAtTranslatedCode] "J"(berberis::config::kFrameSizeAtTranslatedCode));
  // clang-format on
}

[[gnu::naked]] [[gnu::noinline]] void berberis_entry_ExitGeneratedCode() {
  END_GENERATED_CODE("ret");
}

[[gnu::naked]] [[gnu::noinline]] void berberis_entry_Stop() {
  END_GENERATED_CODE("ret");
}

[[gnu::naked]] [[gnu::noinline]] void berberis_entry_NoExec() {
  END_GENERATED_CODE("jmp berberis_HandleNoExec");
  // void berberis_HandleNoExec(ThreadState*);
  // Perform a sibling call to berberis_HandleNoExec. The only parameter
  // is state which is saved in %rdi by END_GENERATED_CODE. We could call the
  // function here instead of jumping to it, but it would be more work to do
  // so because we would have to align the stack and issue the "ret"
  // instruction after the call.
  // TODO(b/232598137): Remove state from HandleNoExec parameters. Get it from
  // the guest thread instead.
}

[[gnu::naked]] [[gnu::noinline]] void berberis_entry_NotTranslated() {
  END_GENERATED_CODE("jmp berberis_HandleNotTranslated");
  // void berberis_HandleNotTranslated(ThreadState*);
  // See the comment above about the sibling call.
}

[[gnu::naked]] [[gnu::noinline]] void berberis_entry_Translating() {
  // TODO(b/232598137): Run interpreter while translation is in progress.
  END_GENERATED_CODE("ret");
}

[[gnu::naked]] [[gnu::noinline]] void berberis_entry_Invalidating() {
  // TODO(b/232598137): maybe call sched_yield() here.
  END_GENERATED_CODE("ret");
}

[[gnu::naked]] [[gnu::noinline]] void berberis_entry_Wrapping() {
  // TODO(b/232598137): maybe call sched_yield() here.
  END_GENERATED_CODE("ret");
}

[[gnu::naked]] [[gnu::noinline]] void berberis_entry_HandleLightCounterThresholdReached() {
  // void berberis_HandleLightCounterThresholdReached(ProcessState*);
  // Perform a sibling call to berberis_HandleLightCounterThresholdReached. The
  // only parameter is state which is saved in %rdi by END_GENERATED_CODE. We
  // could call the function here instead of jumping to it, but it would be more
  // work to do so because we would have to align the stack and issue the "ret"
  // instruction after the call.
  // TODO(b/232598137): Remove state from HandleLightCounterThresholdReached
  // parameters. Get it from the guest thread instead.
  END_GENERATED_CODE("jmp berberis_HandleLightCounterThresholdReached");
}

}  // extern "C"

}  // namespace berberis
