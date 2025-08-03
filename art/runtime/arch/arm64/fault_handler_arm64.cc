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

#include "fault_handler.h"

#include <sys/ucontext.h>

#include "arch/instruction_set.h"
#include "art_method.h"
#include "base/enums.h"
#include "base/hex_dump.h"
#include "base/logging.h"  // For VLOG.
#include "base/macros.h"
#include "registers_arm64.h"
#include "runtime_globals.h"
#include "thread-current-inl.h"

extern "C" void art_quick_throw_stack_overflow();
extern "C" void art_quick_throw_null_pointer_exception_from_signal();
extern "C" void art_quick_implicit_suspend();

//
// ARM64 specific fault handler functions.
//

namespace art HIDDEN {

uintptr_t FaultManager::GetFaultPc(siginfo_t* siginfo, void* context) {
  // SEGV_MTEAERR (Async MTE fault) is delivered at an arbitrary point after the actual fault.
  // Register contents, including PC and SP, are unrelated to the fault and can only confuse ART
  // signal handlers.
  if (siginfo->si_signo == SIGSEGV && siginfo->si_code == SEGV_MTEAERR) {
    VLOG(signals) << "Async MTE fault";
    return 0u;
  }

  ucontext_t* uc = reinterpret_cast<ucontext_t*>(context);
  mcontext_t* mc = reinterpret_cast<mcontext_t*>(&uc->uc_mcontext);
  if (mc->sp == 0) {
    VLOG(signals) << "Missing SP";
    return 0u;
  }
  return mc->pc;
}

uintptr_t FaultManager::GetFaultSp(void* context) {
  ucontext_t* uc = reinterpret_cast<ucontext_t*>(context);
  mcontext_t* mc = reinterpret_cast<mcontext_t*>(&uc->uc_mcontext);
  return mc->sp;
}

bool NullPointerHandler::Action([[maybe_unused]] int sig, siginfo_t* info, void* context) {
  uintptr_t fault_address = reinterpret_cast<uintptr_t>(info->si_addr);
  if (!IsValidFaultAddress(fault_address)) {
    return false;
  }

  // For null checks in compiled code we insert a stack map that is immediately
  // after the load/store instruction that might cause the fault and we need to
  // pass the return PC to the handler. For null checks in Nterp, we similarly
  // need the return PC to recognize that this was a null check in Nterp, so
  // that the handler can get the needed data from the Nterp frame.

  ucontext_t* uc = reinterpret_cast<ucontext_t*>(context);
  mcontext_t* mc = reinterpret_cast<mcontext_t*>(&uc->uc_mcontext);
  ArtMethod** sp = reinterpret_cast<ArtMethod**>(mc->sp);
  uintptr_t return_pc = mc->pc + 4u;
  if (!IsValidMethod(*sp) || !IsValidReturnPc(sp, return_pc)) {
    return false;
  }

  // Push the return PC to the stack and pass the fault address in LR.
  mc->sp -= sizeof(uintptr_t);
  *reinterpret_cast<uintptr_t*>(mc->sp) = return_pc;
  mc->regs[30] = fault_address;

  // Arrange for the signal handler to return to the NPE entrypoint.
  mc->pc = reinterpret_cast<uintptr_t>(art_quick_throw_null_pointer_exception_from_signal);
  VLOG(signals) << "Generating null pointer exception";
  return true;
}

// A suspend check is done using the following instruction:
//      0x...: f94002b5  ldr x21, [x21, #0]
// To check for a suspend check, we examine the instruction that caused the fault (at PC).
bool SuspensionHandler::Action([[maybe_unused]] int sig,
                               [[maybe_unused]] siginfo_t* info,
                               void* context) {
  constexpr uint32_t kSuspendCheckRegister = 21;
  constexpr uint32_t checkinst =
      0xf9400000 | (kSuspendCheckRegister << 5) | (kSuspendCheckRegister << 0);

  ucontext_t* uc = reinterpret_cast<ucontext_t*>(context);
  mcontext_t* mc = reinterpret_cast<mcontext_t*>(&uc->uc_mcontext);

  uint32_t inst = *reinterpret_cast<uint32_t*>(mc->pc);
  VLOG(signals) << "checking suspend; inst: " << std::hex << inst << " checkinst: " << checkinst;
  if (inst != checkinst) {
    // The instruction is not good, not ours.
    return false;
  }

  // This is a suspend check.
  VLOG(signals) << "suspend check match";

  // Set LR so that after the suspend check it will resume after the
  // `ldr x21, [x21,#0]` instruction that triggered the suspend check.
  mc->regs[30] = mc->pc + 4;
  // Arrange for the signal handler to return to `art_quick_implicit_suspend()`.
  mc->pc = reinterpret_cast<uintptr_t>(art_quick_implicit_suspend);

  // Now remove the suspend trigger that caused this fault.
  Thread::Current()->RemoveSuspendTrigger();
  VLOG(signals) << "removed suspend trigger invoking test suspend";

  return true;
}

bool StackOverflowHandler::Action([[maybe_unused]] int sig,
                                  [[maybe_unused]] siginfo_t* info,
                                  void* context) {
  ucontext_t* uc = reinterpret_cast<ucontext_t*>(context);
  mcontext_t* mc = reinterpret_cast<mcontext_t*>(&uc->uc_mcontext);
  VLOG(signals) << "stack overflow handler with sp at " << std::hex << &uc;
  VLOG(signals) << "sigcontext: " << std::hex << mc;

  uintptr_t sp = mc->sp;
  VLOG(signals) << "sp: " << std::hex << sp;

  uintptr_t fault_addr = mc->fault_address;
  VLOG(signals) << "fault_addr: " << std::hex << fault_addr;
  VLOG(signals) << "checking for stack overflow, sp: " << std::hex << sp <<
      ", fault_addr: " << fault_addr;

  uintptr_t overflow_addr = sp - GetStackOverflowReservedBytes(InstructionSet::kArm64);

  // Check that the fault address is the value expected for a stack overflow.
  if (fault_addr != overflow_addr) {
    VLOG(signals) << "Not a stack overflow";
    return false;
  }

  VLOG(signals) << "Stack overflow found";

  // Now arrange for the signal handler to return to art_quick_throw_stack_overflow.
  // The value of LR must be the same as it was when we entered the code that
  // caused this fault.  This will be inserted into a callee save frame by
  // the function to which this handler returns (art_quick_throw_stack_overflow).
  mc->pc = reinterpret_cast<uintptr_t>(art_quick_throw_stack_overflow);

  // The kernel will now return to the address in sc->pc.
  return true;
}
}       // namespace art
