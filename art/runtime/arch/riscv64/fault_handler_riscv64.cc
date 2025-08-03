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

#include "fault_handler.h"

#include <sys/ucontext.h>

#include "arch/instruction_set.h"
#include "base/logging.h"  // For VLOG.

extern "C" void art_quick_throw_stack_overflow();
extern "C" void art_quick_throw_null_pointer_exception_from_signal();
extern "C" void art_quick_implicit_suspend();

// RISCV64 specific fault handler functions (or stubs if unimplemented yet).

namespace art HIDDEN {

uintptr_t FaultManager::GetFaultPc(siginfo_t*, void* context) {
  ucontext_t* uc = reinterpret_cast<ucontext_t*>(context);
  mcontext_t* mc = reinterpret_cast<mcontext_t*>(&uc->uc_mcontext);
  if (mc->__gregs[REG_SP] == 0) {
    VLOG(signals) << "Missing SP";
    return 0u;
  }
  return mc->__gregs[REG_PC];
}

uintptr_t FaultManager::GetFaultSp(void* context) {
  ucontext_t* uc = reinterpret_cast<ucontext_t*>(context);
  mcontext_t* mc = reinterpret_cast<mcontext_t*>(&uc->uc_mcontext);
  return mc->__gregs[REG_SP];
}

bool NullPointerHandler::Action([[maybe_unused]] int sig, siginfo_t* info, void* context) {
  uintptr_t fault_address = reinterpret_cast<uintptr_t>(info->si_addr);
  if (!IsValidFaultAddress(fault_address)) {
    return false;
  }

  ucontext_t* uc = reinterpret_cast<ucontext_t*>(context);
  mcontext_t* mc = reinterpret_cast<mcontext_t*>(&uc->uc_mcontext);
  ArtMethod** sp = reinterpret_cast<ArtMethod**>(mc->__gregs[REG_SP]);
  if (!IsValidMethod(*sp)) {
    return false;
  }

  // For null checks in compiled code we insert a stack map that is immediately
  // after the load/store instruction that might cause the fault and we need to
  // pass the return PC to the handler. For null checks in Nterp, we similarly
  // need the return PC to recognize that this was a null check in Nterp, so
  // that the handler can get the needed data from the Nterp frame.

  // Need to work out the size of the instruction that caused the exception.
  uintptr_t old_pc = mc->__gregs[REG_PC];
  uintptr_t instr_size = (reinterpret_cast<uint16_t*>(old_pc)[0] & 3u) == 3u ? 4u : 2u;
  uintptr_t return_pc = old_pc + instr_size;
  if (!IsValidReturnPc(sp, return_pc)) {
    return false;
  }

  // Push the return PC to the stack and pass the fault address in RA.
  mc->__gregs[REG_SP] -= sizeof(uintptr_t);
  *reinterpret_cast<uintptr_t*>(mc->__gregs[REG_SP]) = return_pc;
  mc->__gregs[REG_RA] = fault_address;

  // Arrange for the signal handler to return to the NPE entrypoint.
  mc->__gregs[REG_PC] =
      reinterpret_cast<uintptr_t>(art_quick_throw_null_pointer_exception_from_signal);
  VLOG(signals) << "Generating null pointer exception";
  return true;
}

bool SuspensionHandler::Action(int, siginfo_t*, void*) {
  LOG(FATAL) << "SuspensionHandler::Action is not implemented for RISC-V";
  return false;
}

bool StackOverflowHandler::Action([[maybe_unused]] int sig,
                                  siginfo_t* info,
                                  void* context) {
  ucontext_t* uc = reinterpret_cast<ucontext_t*>(context);
  mcontext_t* mc = reinterpret_cast<mcontext_t*>(&uc->uc_mcontext);
  VLOG(signals) << "stack overflow handler with sp at " << std::hex << &uc;
  VLOG(signals) << "sigcontext: " << std::hex << mc;

  uintptr_t sp = mc->__gregs[REG_SP];
  VLOG(signals) << "sp: " << std::hex << sp;

  uintptr_t fault_addr = reinterpret_cast<uintptr_t>(info->si_addr);
  VLOG(signals) << "fault_addr: " << std::hex << fault_addr;
  VLOG(signals) << "checking for stack overflow, sp: " << std::hex << sp <<
      ", fault_addr: " << fault_addr;

  uintptr_t overflow_addr = sp - GetStackOverflowReservedBytes(InstructionSet::kRiscv64);

  // Check that the fault address is the value expected for a stack overflow.
  if (fault_addr != overflow_addr) {
    VLOG(signals) << "Not a stack overflow";
    return false;
  }

  VLOG(signals) << "Stack overflow found";

  // Now arrange for the signal handler to return to art_quick_throw_stack_overflow.
  // The value of RA must be the same as it was when we entered the code that
  // caused this fault.  This will be inserted into a callee save frame by
  // the function to which this handler returns (art_quick_throw_stack_overflow).
  mc->__gregs[REG_PC] = reinterpret_cast<uintptr_t>(art_quick_throw_stack_overflow);

  // The kernel will now return to the address in `mc->__gregs[REG_PC]`.
  return true;
}

}  // namespace art
