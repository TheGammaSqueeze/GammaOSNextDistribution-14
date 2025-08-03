/*
 * Copyright (C) 2019 The Android Open Source Project
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

#include "arch/instruction_set.h"
#include "art_method-inl.h"
#include "dex/code_item_accessors.h"
#include "entrypoints/quick/callee_save_frame.h"
#include "interpreter/mterp/nterp.h"
#include "nterp_helpers.h"
#include "oat/oat_quick_method_header.h"
#include "quick/quick_method_frame_info.h"

namespace art HIDDEN {

/**
 * An nterp frame follows the optimizing compiler's ABI conventions, with
 * int/long/reference parameters being passed in core registers / stack and
 * float/double parameters being passed in floating point registers / stack.
 *
 * There are no ManagedStack transitions between compiler and nterp frames.
 *
 * On entry, nterp will copy its parameters to a dex register array allocated on
 * the stack. There is a fast path when calling from nterp to nterp to not
 * follow the ABI but just copy the parameters from the caller's dex registers
 * to the callee's dex registers.
 *
 * The stack layout of an nterp frame is:
 *    ----------------
 *    |              |      All callee save registers of the platform
 *    | callee-save  |      (core and floating point).
 *    | registers    |      On x86 and x64 this includes the return address,
 *    |              |      already spilled on entry.
 *    ----------------
 *    |   x86 args   |      x86 only: registers used for argument passing.
 *    ----------------
 *    |  alignment   |      Stack aligment of kStackAlignment.
 *    ----------------
 *    |              |      Contains `registers_size` entries (of size 4) from
 *    |    dex       |      the code item information of the method.
 *    |  registers   |
 *    |              |
 *    ----------------
 *    |              |      A copy of the dex registers above, but only
 *    |  reference   |      containing references, used for GC.
 *    |  registers   |
 *    |              |
 *    ----------------
 *    |  caller fp   |      Frame pointer of caller. Stored below the reference
 *    ----------------      registers array for easy access from nterp when returning.
 *    |  dex_pc_ptr  |      Pointer to the dex instruction being executed.
 *    ----------------      Stored whenever nterp goes into the runtime.
 *    |  alignment   |      Pointer aligment for dex_pc_ptr and caller_fp.
 *    ----------------
 *    |              |      In case nterp calls compiled code, we reserve space
 *    |     out      |      for out registers. This space will be used for
 *    |   registers  |      arguments passed on stack.
 *    |              |
 *    ----------------
 *    |  ArtMethod*  |      The method being currently executed.
 *    ----------------
 *
 *    Exception handling:
 *    Nterp follows the same convention than the compiler,
 *    with the addition of:
 *    - All catch handlers have the same landing pad.
 *    - Before doing the longjmp for exception delivery, the register containing the
 *      dex PC pointer must be updated.
 *
 *    Stack walking:
 *    An nterp frame is walked like a compiled code frame. We add an
 *    OatQuickMethodHeader prefix to the nterp entry point, which contains:
 *    - vmap_table_offset=0 (nterp doesn't need one).
 *    - code_size=NterpEnd-NterpStart
 */

static constexpr size_t kPointerSize = static_cast<size_t>(kRuntimePointerSize);

static constexpr size_t NterpGetFrameEntrySize(InstructionSet isa) {
  uint32_t core_spills = 0;
  uint32_t fp_spills = 0;
  // Note: the return address is considered part of the callee saves.
  switch (isa) {
    case InstructionSet::kX86:
      core_spills = x86::X86CalleeSaveFrame::GetCoreSpills(CalleeSaveType::kSaveAllCalleeSaves);
      fp_spills = x86::X86CalleeSaveFrame::GetFpSpills(CalleeSaveType::kSaveAllCalleeSaves);
      // x86 also saves registers used for argument passing.
      core_spills |= x86::kX86CalleeSaveEverythingSpills;
      break;
    case InstructionSet::kX86_64:
      core_spills =
          x86_64::X86_64CalleeSaveFrame::GetCoreSpills(CalleeSaveType::kSaveAllCalleeSaves);
      fp_spills = x86_64::X86_64CalleeSaveFrame::GetFpSpills(CalleeSaveType::kSaveAllCalleeSaves);
      break;
    case InstructionSet::kArm:
    case InstructionSet::kThumb2:
      core_spills = arm::ArmCalleeSaveFrame::GetCoreSpills(CalleeSaveType::kSaveAllCalleeSaves);
      fp_spills = arm::ArmCalleeSaveFrame::GetFpSpills(CalleeSaveType::kSaveAllCalleeSaves);
      break;
    case InstructionSet::kArm64:
      core_spills = arm64::Arm64CalleeSaveFrame::GetCoreSpills(CalleeSaveType::kSaveAllCalleeSaves);
      fp_spills = arm64::Arm64CalleeSaveFrame::GetFpSpills(CalleeSaveType::kSaveAllCalleeSaves);
      break;
    case InstructionSet::kRiscv64:
      core_spills =
          riscv64::Riscv64CalleeSaveFrame::GetCoreSpills(CalleeSaveType::kSaveAllCalleeSaves);
      fp_spills = riscv64::Riscv64CalleeSaveFrame::GetFpSpills(CalleeSaveType::kSaveAllCalleeSaves);
      break;
    default:
      InstructionSetAbort(isa);
  }
  // Note: the return address is considered part of the callee saves.
  return (POPCOUNT(core_spills) + POPCOUNT(fp_spills)) *
      static_cast<size_t>(InstructionSetPointerSize(isa));
}

static uint16_t GetNumberOfOutRegs(const CodeItemDataAccessor& accessor, InstructionSet isa) {
  uint16_t out_regs = accessor.OutsSize();
  switch (isa) {
    case InstructionSet::kX86: {
      // On x86, we use three slots for temporaries.
      out_regs = std::max(out_regs, static_cast<uint16_t>(3u));
      break;
    }
    default:
      break;
  }
  return out_regs;
}

static uint16_t GetNumberOfOutRegs(ArtMethod* method, InstructionSet isa)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  CodeItemDataAccessor accessor(method->DexInstructionData());
  return GetNumberOfOutRegs(accessor, isa);
}

// Note: There may be two pieces of alignment but there is no need to align
// out args to `kPointerSize` separately before aligning to kStackAlignment.
// This allows using the size without padding for the maximum frame size check
// in `CanMethodUseNterp()`.
static size_t NterpGetFrameSizeWithoutPadding(ArtMethod* method, InstructionSet isa)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  CodeItemDataAccessor accessor(method->DexInstructionData());
  const uint16_t num_regs = accessor.RegistersSize();
  const uint16_t out_regs = GetNumberOfOutRegs(accessor, isa);
  size_t pointer_size = static_cast<size_t>(InstructionSetPointerSize(isa));

  DCHECK(IsAlignedParam(kStackAlignment, pointer_size));
  DCHECK(IsAlignedParam(NterpGetFrameEntrySize(isa), pointer_size));
  DCHECK(IsAlignedParam(kVRegSize * 2, pointer_size));
  size_t frame_size =
      NterpGetFrameEntrySize(isa) +
      (num_regs * kVRegSize) * 2 +  // dex registers and reference registers
      pointer_size +  // previous frame
      pointer_size +  // saved dex pc
      (out_regs * kVRegSize) +  // out arguments
      pointer_size;  // method
  return frame_size;
}

// The frame size nterp will use for the given method.
static inline size_t NterpGetFrameSize(ArtMethod* method, InstructionSet isa)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  return RoundUp(NterpGetFrameSizeWithoutPadding(method, isa), kStackAlignment);
}

QuickMethodFrameInfo NterpFrameInfo(ArtMethod** frame) {
  uint32_t core_spills =
      RuntimeCalleeSaveFrame::GetCoreSpills(CalleeSaveType::kSaveAllCalleeSaves);
  uint32_t fp_spills =
      RuntimeCalleeSaveFrame::GetFpSpills(CalleeSaveType::kSaveAllCalleeSaves);
  return QuickMethodFrameInfo(NterpGetFrameSize(*frame, kRuntimeISA), core_spills, fp_spills);
}

uintptr_t NterpGetRegistersArray(ArtMethod** frame) {
  CodeItemDataAccessor accessor((*frame)->DexInstructionData());
  const uint16_t num_regs = accessor.RegistersSize();
  // The registers array is just above the reference array.
  return NterpGetReferenceArray(frame) + (num_regs * kVRegSize);
}

uintptr_t NterpGetReferenceArray(ArtMethod** frame) {
  const uint16_t out_regs = GetNumberOfOutRegs(*frame, kRuntimeISA);
  // The references array is just above the saved frame pointer.
  return reinterpret_cast<uintptr_t>(frame) +
      kPointerSize +  // method
      RoundUp(out_regs * kVRegSize, kPointerSize) +  // out arguments and pointer alignment
      kPointerSize +  // saved dex pc
      kPointerSize;  // previous frame.
}

uint32_t NterpGetDexPC(ArtMethod** frame) {
  const uint16_t out_regs = GetNumberOfOutRegs(*frame, kRuntimeISA);
  uintptr_t dex_pc_ptr = reinterpret_cast<uintptr_t>(frame) +
      kPointerSize +  // method
      RoundUp(out_regs * kVRegSize, kPointerSize);  // out arguments and pointer alignment
  CodeItemInstructionAccessor instructions((*frame)->DexInstructions());
  return *reinterpret_cast<const uint16_t**>(dex_pc_ptr) - instructions.Insns();
}

uint32_t NterpGetVReg(ArtMethod** frame, uint16_t vreg) {
  return reinterpret_cast<uint32_t*>(NterpGetRegistersArray(frame))[vreg];
}

uint32_t NterpGetVRegReference(ArtMethod** frame, uint16_t vreg) {
  return reinterpret_cast<uint32_t*>(NterpGetReferenceArray(frame))[vreg];
}

uintptr_t NterpGetCatchHandler() {
  // Nterp uses the same landing pad for all exceptions. The dex_pc_ptr set before
  // longjmp will actually be used to jmp to the catch handler.
  return reinterpret_cast<uintptr_t>(artNterpAsmInstructionEnd);
}

bool CanMethodUseNterp(ArtMethod* method, InstructionSet isa) {
  uint32_t access_flags = method->GetAccessFlags();
  if (ArtMethod::IsNative(access_flags) ||
      !ArtMethod::IsInvokable(access_flags) ||
      ArtMethod::MustCountLocks(access_flags) ||
      // Proxy methods do not go through the JIT like other methods, so we don't
      // run them with nterp.
      method->IsProxyMethod()) {
    return false;
  }
  if (isa == InstructionSet::kRiscv64 && method->GetDexFile()->IsCompactDexFile()) {
    return false;  // Riscv64 nterp does not support compact dex yet.
  }
  // There is no need to add the alignment padding size for comparison with aligned limit.
  size_t frame_size_without_padding = NterpGetFrameSizeWithoutPadding(method, isa);
  DCHECK_EQ(NterpGetFrameSize(method, isa), RoundUp(frame_size_without_padding, kStackAlignment));
  static_assert(IsAligned<kStackAlignment>(interpreter::kNterpMaxFrame));
  return frame_size_without_padding <= interpreter::kNterpMaxFrame;
}

}  // namespace art
