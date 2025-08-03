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
#include "oat/oat_quick_method_header.h"
#include "runtime_globals.h"
#include "thread-current-inl.h"

#if defined(__APPLE__)
#define ucontext __darwin_ucontext

#if defined(__x86_64__)
// 64 bit mac build.
#define CTX_ESP uc_mcontext->__ss.__rsp
#define CTX_EIP uc_mcontext->__ss.__rip
#define CTX_EAX uc_mcontext->__ss.__rax
#define CTX_METHOD uc_mcontext->__ss.__rdi
#define CTX_RDI uc_mcontext->__ss.__rdi
#define CTX_JMP_BUF uc_mcontext->__ss.__rdi
#else
// 32 bit mac build.
#define CTX_ESP uc_mcontext->__ss.__esp
#define CTX_EIP uc_mcontext->__ss.__eip
#define CTX_EAX uc_mcontext->__ss.__eax
#define CTX_METHOD uc_mcontext->__ss.__eax
#define CTX_JMP_BUF uc_mcontext->__ss.__eax
#endif

#elif defined(__x86_64__)
// 64 bit linux build.
#define CTX_ESP uc_mcontext.gregs[REG_RSP]
#define CTX_EIP uc_mcontext.gregs[REG_RIP]
#define CTX_EAX uc_mcontext.gregs[REG_RAX]
#define CTX_METHOD uc_mcontext.gregs[REG_RDI]
#define CTX_RDI uc_mcontext.gregs[REG_RDI]
#define CTX_JMP_BUF uc_mcontext.gregs[REG_RDI]
#else
// 32 bit linux build.
#define CTX_ESP uc_mcontext.gregs[REG_ESP]
#define CTX_EIP uc_mcontext.gregs[REG_EIP]
#define CTX_EAX uc_mcontext.gregs[REG_EAX]
#define CTX_METHOD uc_mcontext.gregs[REG_EAX]
#define CTX_JMP_BUF uc_mcontext.gregs[REG_EAX]
#endif

//
// X86 (and X86_64) specific fault handler functions.
//

namespace art HIDDEN {

extern "C" void art_quick_throw_null_pointer_exception_from_signal();
extern "C" void art_quick_throw_stack_overflow();
extern "C" void art_quick_test_suspend();

// Get the size of an instruction in bytes.
// Return 0 if the instruction is not handled.
static uint32_t GetInstructionSize(const uint8_t* pc, size_t bytes) {
#define FETCH_OR_SKIP_BYTE(assignment)  \
  do {                                  \
    if (bytes == 0u) {                  \
      return 0u;                        \
    }                                   \
    (assignment);                       \
    ++pc;                               \
    --bytes;                            \
  } while (0)
#define FETCH_BYTE(var) FETCH_OR_SKIP_BYTE((var) = *pc)
#define SKIP_BYTE() FETCH_OR_SKIP_BYTE((void)0)

#if defined(__x86_64)
  const bool x86_64 = true;
#else
  const bool x86_64 = false;
#endif

  const uint8_t* startpc = pc;

  uint8_t opcode;
  FETCH_BYTE(opcode);
  uint8_t modrm;
  bool has_modrm = false;
  bool two_byte = false;
  uint32_t displacement_size = 0;
  uint32_t immediate_size = 0;
  bool operand_size_prefix = false;

  // Prefixes.
  while (true) {
    bool prefix_present = false;
    switch (opcode) {
      // Group 3
      case 0x66:
        operand_size_prefix = true;
        FALLTHROUGH_INTENDED;

      // Group 1
      case 0xf0:
      case 0xf2:
      case 0xf3:

      // Group 2
      case 0x2e:
      case 0x36:
      case 0x3e:
      case 0x26:
      case 0x64:
      case 0x65:

      // Group 4
      case 0x67:
        FETCH_BYTE(opcode);
        prefix_present = true;
        break;
    }
    if (!prefix_present) {
      break;
    }
  }

  if (x86_64 && opcode >= 0x40 && opcode <= 0x4f) {
    FETCH_BYTE(opcode);
  }

  if (opcode == 0x0f) {
    // Two byte opcode
    two_byte = true;
    FETCH_BYTE(opcode);
  }

  bool unhandled_instruction = false;

  if (two_byte) {
    switch (opcode) {
      case 0x10:        // vmovsd/ss
      case 0x11:        // vmovsd/ss
      case 0xb6:        // movzx
      case 0xb7:
      case 0xbe:        // movsx
      case 0xbf:
        FETCH_BYTE(modrm);
        has_modrm = true;
        break;
      default:
        unhandled_instruction = true;
        break;
    }
  } else {
    switch (opcode) {
      case 0x88:        // mov byte
      case 0x89:        // mov
      case 0x8b:
      case 0x38:        // cmp with memory.
      case 0x39:
      case 0x3a:
      case 0x3b:
      case 0x3c:
      case 0x3d:
      case 0x85:        // test.
        FETCH_BYTE(modrm);
        has_modrm = true;
        break;

      case 0x80:        // group 1, byte immediate.
      case 0x83:
      case 0xc6:
        FETCH_BYTE(modrm);
        has_modrm = true;
        immediate_size = 1;
        break;

      case 0x81:        // group 1, word immediate.
      case 0xc7:        // mov
        FETCH_BYTE(modrm);
        has_modrm = true;
        immediate_size = operand_size_prefix ? 2 : 4;
        break;

      case 0xf6:
      case 0xf7:
        FETCH_BYTE(modrm);
        has_modrm = true;
        switch ((modrm >> 3) & 7) {  // Extract "reg/opcode" from "modr/m".
          case 0:  // test
            immediate_size = (opcode == 0xf6) ? 1 : (operand_size_prefix ? 2 : 4);
            break;
          case 2:  // not
          case 3:  // neg
          case 4:  // mul
          case 5:  // imul
          case 6:  // div
          case 7:  // idiv
            break;
          default:
            unhandled_instruction = true;
            break;
        }
        break;

      default:
        unhandled_instruction = true;
        break;
    }
  }

  if (unhandled_instruction) {
    VLOG(signals) << "Unhandled x86 instruction with opcode " << static_cast<int>(opcode);
    return 0;
  }

  if (has_modrm) {
    uint8_t mod = (modrm >> 6) & 3U /* 0b11 */;

    // Check for SIB.
    if (mod != 3U /* 0b11 */ && (modrm & 7U /* 0b111 */) == 4) {
      SKIP_BYTE();  // SIB
    }

    switch (mod) {
      case 0U /* 0b00 */: break;
      case 1U /* 0b01 */: displacement_size = 1; break;
      case 2U /* 0b10 */: displacement_size = 4; break;
      case 3U /* 0b11 */:
        break;
    }
  }

  // Skip displacement and immediate.
  pc += displacement_size + immediate_size;

  VLOG(signals) << "x86 instruction length calculated as " << (pc - startpc);
  return pc - startpc;

#undef SKIP_BYTE
#undef FETCH_BYTE
#undef FETCH_OR_SKIP_BYTE
}

uintptr_t FaultManager::GetFaultPc([[maybe_unused]] siginfo_t* siginfo, void* context) {
  ucontext_t* uc = reinterpret_cast<ucontext_t*>(context);
  if (uc->CTX_ESP == 0) {
    VLOG(signals) << "Missing SP";
    return 0u;
  }
  return uc->CTX_EIP;
}

uintptr_t FaultManager::GetFaultSp(void* context) {
  ucontext_t* uc = reinterpret_cast<ucontext_t*>(context);
  return uc->CTX_ESP;
}

bool NullPointerHandler::Action(int, siginfo_t* sig, void* context) {
  uintptr_t fault_address = reinterpret_cast<uintptr_t>(sig->si_addr);
  if (!IsValidFaultAddress(fault_address)) {
    return false;
  }

  ucontext_t* uc = reinterpret_cast<ucontext_t*>(context);
  ArtMethod** sp = reinterpret_cast<ArtMethod**>(uc->CTX_ESP);
  ArtMethod* method = *sp;
  if (!IsValidMethod(method)) {
    return false;
  }

  // For null checks in compiled code we insert a stack map that is immediately
  // after the load/store instruction that might cause the fault and we need to
  // pass the return PC to the handler. For null checks in Nterp, we similarly
  // need the return PC to recognize that this was a null check in Nterp, so
  // that the handler can get the needed data from the Nterp frame.

  // Note: Allowing nested faults if `IsValidMethod()` returned a false positive.
  // Note: The `ArtMethod::GetOatQuickMethodHeader()` can acquire locks, which is
  // essentially unsafe in a signal handler, but we allow that here just like in
  // `NullPointerHandler::IsValidReturnPc()`. For more details see comments there.
  uintptr_t pc = uc->CTX_EIP;
  const OatQuickMethodHeader* method_header = method->GetOatQuickMethodHeader(pc);
  if (method_header == nullptr) {
    VLOG(signals) << "No method header.";
    return false;
  }
  const uint8_t* pc_ptr = reinterpret_cast<const uint8_t*>(pc);
  size_t offset = pc_ptr - method_header->GetCode();
  size_t code_size = method_header->GetCodeSize();
  CHECK_LT(offset, code_size);
  size_t max_instr_size = code_size - offset;
  uint32_t instr_size = GetInstructionSize(pc_ptr, max_instr_size);
  if (instr_size == 0u) {
    // Unknown instruction (can't really happen) or not enough bytes until end of method code.
    return false;
  }

  uintptr_t return_pc = reinterpret_cast<uintptr_t>(pc + instr_size);
  if (!IsValidReturnPc(sp, return_pc)) {
    return false;
  }

  // Push the return PC and fault address onto the stack.
  uintptr_t* next_sp = reinterpret_cast<uintptr_t*>(sp) - 2;
  next_sp[1] = return_pc;
  next_sp[0] = fault_address;
  uc->CTX_ESP = reinterpret_cast<uintptr_t>(next_sp);

  // Arrange for the signal handler to return to the NPE entrypoint.
  uc->CTX_EIP = reinterpret_cast<uintptr_t>(
      art_quick_throw_null_pointer_exception_from_signal);
  VLOG(signals) << "Generating null pointer exception";
  return true;
}

// A suspend check is done using the following instruction sequence:
// (x86)
// 0xf720f1df:         648B058C000000      mov     eax, fs:[0x8c]  ; suspend_trigger
// .. some intervening instructions.
// 0xf720f1e6:                   8500      test    eax, [eax]
// (x86_64)
// 0x7f579de45d9e: 65488B0425A8000000      movq    rax, gs:[0xa8]  ; suspend_trigger
// .. some intervening instructions.
// 0x7f579de45da7:               8500      test    eax, [eax]

// The offset from fs is Thread::ThreadSuspendTriggerOffset().
// To check for a suspend check, we examine the instructions that caused
// the fault.
bool SuspensionHandler::Action(int, siginfo_t*, void* context) {
  // These are the instructions to check for.  The first one is the mov eax, fs:[xxx]
  // where xxx is the offset of the suspend trigger.
  uint32_t trigger = Thread::ThreadSuspendTriggerOffset<kRuntimePointerSize>().Int32Value();

  VLOG(signals) << "Checking for suspension point";
#if defined(__x86_64__)
  uint8_t checkinst1[] = {0x65, 0x48, 0x8b, 0x04, 0x25, static_cast<uint8_t>(trigger & 0xff),
      static_cast<uint8_t>((trigger >> 8) & 0xff), 0, 0};
#else
  uint8_t checkinst1[] = {0x64, 0x8b, 0x05, static_cast<uint8_t>(trigger & 0xff),
      static_cast<uint8_t>((trigger >> 8) & 0xff), 0, 0};
#endif
  uint8_t checkinst2[] = {0x85, 0x00};

  ucontext_t* uc = reinterpret_cast<ucontext_t*>(context);
  uint8_t* pc = reinterpret_cast<uint8_t*>(uc->CTX_EIP);
  uint8_t* sp = reinterpret_cast<uint8_t*>(uc->CTX_ESP);

  if (pc[0] != checkinst2[0] || pc[1] != checkinst2[1]) {
    // Second instruction is not correct (test eax,[eax]).
    VLOG(signals) << "Not a suspension point";
    return false;
  }

  // The first instruction can a little bit up the stream due to load hoisting
  // in the compiler.
  uint8_t* limit = pc - 100;   // Compiler will hoist to a max of 20 instructions.
  uint8_t* ptr = pc - sizeof(checkinst1);
  bool found = false;
  while (ptr > limit) {
    if (memcmp(ptr, checkinst1, sizeof(checkinst1)) == 0) {
      found = true;
      break;
    }
    ptr -= 1;
  }

  if (found) {
    VLOG(signals) << "suspend check match";

    // We need to arrange for the signal handler to return to the null pointer
    // exception generator.  The return address must be the address of the
    // next instruction (this instruction + 2).  The return address
    // is on the stack at the top address of the current frame.

    // Push the return address onto the stack.
    uintptr_t retaddr = reinterpret_cast<uintptr_t>(pc + 2);
    uintptr_t* next_sp = reinterpret_cast<uintptr_t*>(sp - sizeof(uintptr_t));
    *next_sp = retaddr;
    uc->CTX_ESP = reinterpret_cast<uintptr_t>(next_sp);

    uc->CTX_EIP = reinterpret_cast<uintptr_t>(art_quick_test_suspend);

    // Now remove the suspend trigger that caused this fault.
    Thread::Current()->RemoveSuspendTrigger();
    VLOG(signals) << "removed suspend trigger invoking test suspend";
    return true;
  }
  VLOG(signals) << "Not a suspend check match, first instruction mismatch";
  return false;
}

// The stack overflow check is done using the following instruction:
// test eax, [esp+ -xxx]
// where 'xxx' is the size of the overflow area.
//
// This is done before any frame is established in the method.  The return
// address for the previous method is on the stack at ESP.

bool StackOverflowHandler::Action(int, siginfo_t* info, void* context) {
  ucontext_t* uc = reinterpret_cast<ucontext_t*>(context);
  uintptr_t sp = static_cast<uintptr_t>(uc->CTX_ESP);

  uintptr_t fault_addr = reinterpret_cast<uintptr_t>(info->si_addr);
  VLOG(signals) << "fault_addr: " << std::hex << fault_addr;
  VLOG(signals) << "checking for stack overflow, sp: " << std::hex << sp <<
    ", fault_addr: " << fault_addr;

#if defined(__x86_64__)
  uintptr_t overflow_addr = sp - GetStackOverflowReservedBytes(InstructionSet::kX86_64);
#else
  uintptr_t overflow_addr = sp - GetStackOverflowReservedBytes(InstructionSet::kX86);
#endif

  // Check that the fault address is the value expected for a stack overflow.
  if (fault_addr != overflow_addr) {
    VLOG(signals) << "Not a stack overflow";
    return false;
  }

  VLOG(signals) << "Stack overflow found";

  // Since the compiler puts the implicit overflow
  // check before the callee save instructions, the SP is already pointing to
  // the previous frame.

  // Now arrange for the signal handler to return to art_quick_throw_stack_overflow.
  uc->CTX_EIP = reinterpret_cast<uintptr_t>(art_quick_throw_stack_overflow);

  return true;
}
}       // namespace art
