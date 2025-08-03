/*
 * Copyright (C) 2008 The Android Open Source Project
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
#include "runtime_globals.h"
#include "thread-current-inl.h"

//
// ARM specific fault handler functions.
//

namespace art HIDDEN {

extern "C" void art_quick_throw_null_pointer_exception_from_signal();
extern "C" void art_quick_throw_stack_overflow();
extern "C" void art_quick_implicit_suspend();

// Get the size of a thumb2 instruction in bytes.
static uint32_t GetInstructionSize(uint8_t* pc) {
  uint16_t instr = pc[0] | pc[1] << 8;
  bool is_32bit = ((instr & 0xF000) == 0xF000) || ((instr & 0xF800) == 0xE800);
  uint32_t instr_size = is_32bit ? 4 : 2;
  return instr_size;
}

uintptr_t FaultManager::GetFaultPc([[maybe_unused]] siginfo_t* siginfo, void* context) {
  ucontext_t* uc = reinterpret_cast<ucontext_t*>(context);
  mcontext_t* mc = reinterpret_cast<mcontext_t*>(&uc->uc_mcontext);
  if (mc->arm_sp == 0) {
    VLOG(signals) << "Missing SP";
    return 0u;
  }
  return mc->arm_pc;
}

uintptr_t FaultManager::GetFaultSp(void* context) {
  ucontext_t* uc = reinterpret_cast<ucontext_t*>(context);
  mcontext_t* mc = reinterpret_cast<mcontext_t*>(&uc->uc_mcontext);
  return mc->arm_sp;
}

bool NullPointerHandler::Action([[maybe_unused]] int sig, siginfo_t* info, void* context) {
  uintptr_t fault_address = reinterpret_cast<uintptr_t>(info->si_addr);
  if (!IsValidFaultAddress(fault_address)) {
    return false;
  }

  ucontext_t* uc = reinterpret_cast<ucontext_t*>(context);
  mcontext_t* mc = reinterpret_cast<mcontext_t*>(&uc->uc_mcontext);
  ArtMethod** sp = reinterpret_cast<ArtMethod**>(mc->arm_sp);
  if (!IsValidMethod(*sp)) {
    return false;
  }

  // For null checks in compiled code we insert a stack map that is immediately
  // after the load/store instruction that might cause the fault and we need to
  // pass the return PC to the handler. For null checks in Nterp, we similarly
  // need the return PC to recognize that this was a null check in Nterp, so
  // that the handler can get the needed data from the Nterp frame.

  // Note: Currently, Nterp is compiled to the A32 instruction set and managed
  // code is compiled to the T32 instruction set.
  // To find the stack map for compiled code, we need to set the bottom bit in
  // the return PC indicating T32 just like we would if we were going to return
  // to that PC (though we're going to jump to the exception handler instead).

  // Need to work out the size of the instruction that caused the exception.
  uint8_t* ptr = reinterpret_cast<uint8_t*>(mc->arm_pc);
  bool in_thumb_mode = mc->arm_cpsr & (1 << 5);
  uint32_t instr_size = in_thumb_mode ? GetInstructionSize(ptr) : 4;
  uintptr_t return_pc = (mc->arm_pc + instr_size) | (in_thumb_mode ? 1 : 0);

  // Push the return PC to the stack and pass the fault address in LR.
  mc->arm_sp -= sizeof(uintptr_t);
  *reinterpret_cast<uintptr_t*>(mc->arm_sp) = return_pc;
  mc->arm_lr = fault_address;

  // Arrange for the signal handler to return to the NPE entrypoint.
  mc->arm_pc = reinterpret_cast<uintptr_t>(art_quick_throw_null_pointer_exception_from_signal);
  // Make sure the thumb bit is set as the handler is in thumb mode.
  mc->arm_cpsr = mc->arm_cpsr | (1 << 5);
  // Pass the faulting address as the first argument of
  // art_quick_throw_null_pointer_exception_from_signal.
  VLOG(signals) << "Generating null pointer exception";
  return true;
}

// A suspend check is done using the following instruction sequence:
// 0xf723c0b2: f8d902c0  ldr.w   r0, [r9, #704]  ; suspend_trigger_
// .. some intervening instruction
// 0xf723c0b6: 6800      ldr     r0, [r0, #0]

// The offset from r9 is Thread::ThreadSuspendTriggerOffset().
// To check for a suspend check, we examine the instructions that caused
// the fault (at PC-4 and PC).
bool SuspensionHandler::Action([[maybe_unused]] int sig,
                               [[maybe_unused]] siginfo_t* info,
                               void* context) {
  // These are the instructions to check for.  The first one is the ldr r0,[r9,#xxx]
  // where xxx is the offset of the suspend trigger.
  uint32_t checkinst1 = 0xf8d90000
      + Thread::ThreadSuspendTriggerOffset<PointerSize::k32>().Int32Value();
  uint16_t checkinst2 = 0x6800;

  ucontext_t* uc = reinterpret_cast<ucontext_t*>(context);
  mcontext_t* mc = reinterpret_cast<mcontext_t*>(&uc->uc_mcontext);
  uint8_t* ptr2 = reinterpret_cast<uint8_t*>(mc->arm_pc);
  uint8_t* ptr1 = ptr2 - 4;
  VLOG(signals) << "checking suspend";

  uint16_t inst2 = ptr2[0] | ptr2[1] << 8;
  VLOG(signals) << "inst2: " << std::hex << inst2 << " checkinst2: " << checkinst2;
  if (inst2 != checkinst2) {
    // Second instruction is not good, not ours.
    return false;
  }

  // The first instruction can a little bit up the stream due to load hoisting
  // in the compiler.
  uint8_t* limit = ptr1 - 40;   // Compiler will hoist to a max of 20 instructions.
  bool found = false;
  while (ptr1 > limit) {
    uint32_t inst1 = ((ptr1[0] | ptr1[1] << 8) << 16) | (ptr1[2] | ptr1[3] << 8);
    VLOG(signals) << "inst1: " << std::hex << inst1 << " checkinst1: " << checkinst1;
    if (inst1 == checkinst1) {
      found = true;
      break;
    }
    ptr1 -= 2;      // Min instruction size is 2 bytes.
  }
  if (found) {
    VLOG(signals) << "suspend check match";
    // This is a suspend check.  Arrange for the signal handler to return to
    // art_quick_implicit_suspend.  Also set LR so that after the suspend check it
    // will resume the instruction (current PC + 2).  PC points to the
    // ldr r0,[r0,#0] instruction (r0 will be 0, set by the trigger).

    // NB: remember that we need to set the bottom bit of the LR register
    // to switch to thumb mode.
    VLOG(signals) << "arm lr: " << std::hex << mc->arm_lr;
    VLOG(signals) << "arm pc: " << std::hex << mc->arm_pc;
    mc->arm_lr = mc->arm_pc + 3;  // +2 + 1 (for thumb)
    mc->arm_pc = reinterpret_cast<uintptr_t>(art_quick_implicit_suspend);

    // Now remove the suspend trigger that caused this fault.
    Thread::Current()->RemoveSuspendTrigger();
    VLOG(signals) << "removed suspend trigger invoking test suspend";
    return true;
  }
  return false;
}

// Stack overflow fault handler.
//
// This checks that the fault address is equal to the current stack pointer
// minus the overflow region size (16K typically).  The instruction sequence
// that generates this signal is:
//
// sub r12,sp,#16384
// ldr.w r12,[r12,#0]
//
// The second instruction will fault if r12 is inside the protected region
// on the stack.
//
// If we determine this is a stack overflow we need to move the stack pointer
// to the overflow region below the protected region.

bool StackOverflowHandler::Action([[maybe_unused]] int sig,
                                  [[maybe_unused]] siginfo_t* info,
                                  void* context) {
  ucontext_t* uc = reinterpret_cast<ucontext_t*>(context);
  mcontext_t* mc = reinterpret_cast<mcontext_t*>(&uc->uc_mcontext);
  VLOG(signals) << "stack overflow handler with sp at " << std::hex << &uc;
  VLOG(signals) << "sigcontext: " << std::hex << mc;

  uintptr_t sp = mc->arm_sp;
  VLOG(signals) << "sp: " << std::hex << sp;

  uintptr_t fault_addr = mc->fault_address;
  VLOG(signals) << "fault_addr: " << std::hex << fault_addr;
  VLOG(signals) << "checking for stack overflow, sp: " << std::hex << sp <<
    ", fault_addr: " << fault_addr;

  uintptr_t overflow_addr = sp - GetStackOverflowReservedBytes(InstructionSet::kArm);

  // Check that the fault address is the value expected for a stack overflow.
  if (fault_addr != overflow_addr) {
    VLOG(signals) << "Not a stack overflow";
    return false;
  }

  VLOG(signals) << "Stack overflow found";

  // Now arrange for the signal handler to return to art_quick_throw_stack_overflow_from.
  // The value of LR must be the same as it was when we entered the code that
  // caused this fault.  This will be inserted into a callee save frame by
  // the function to which this handler returns (art_quick_throw_stack_overflow).
  mc->arm_pc = reinterpret_cast<uintptr_t>(art_quick_throw_stack_overflow);

  // Make sure the thumb bit is set as the handler is in thumb mode.
  mc->arm_cpsr = mc->arm_cpsr | (1 << 5);

  // The kernel will now return to the address in sc->arm_pc.
  return true;
}
}       // namespace art
