/*
 * Copyright (C) 2016 The Android Open Source Project
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

#include "jni_macro_assembler_x86_64.h"

#include "base/casts.h"
#include "base/memory_region.h"
#include "entrypoints/quick/quick_entrypoints.h"
#include "indirect_reference_table.h"
#include "lock_word.h"
#include "thread.h"

namespace art HIDDEN {
namespace x86_64 {

static dwarf::Reg DWARFReg(Register reg) {
  return dwarf::Reg::X86_64Core(static_cast<int>(reg));
}
static dwarf::Reg DWARFReg(FloatRegister reg) {
  return dwarf::Reg::X86_64Fp(static_cast<int>(reg));
}

constexpr size_t kFramePointerSize = 8;

static constexpr size_t kNativeStackAlignment = 16;
static_assert(kNativeStackAlignment == kStackAlignment);

static inline CpuRegister GetScratchRegister() {
  return CpuRegister(R11);
}

#define __ asm_.

void X86_64JNIMacroAssembler::BuildFrame(size_t frame_size,
                                         ManagedRegister method_reg,
                                         ArrayRef<const ManagedRegister> spill_regs) {
  DCHECK_EQ(CodeSize(), 0U);  // Nothing emitted yet.
  cfi().SetCurrentCFAOffset(8);  // Return address on stack.
  // Note: @CriticalNative tail call is not used (would have frame_size == kFramePointerSize).
  if (method_reg.IsNoRegister()) {
    CHECK_ALIGNED(frame_size, kNativeStackAlignment);
  } else {
    CHECK_ALIGNED(frame_size, kStackAlignment);
  }
  size_t gpr_count = 0u;
  for (int i = spill_regs.size() - 1; i >= 0; --i) {
    x86_64::X86_64ManagedRegister spill = spill_regs[i].AsX86_64();
    if (spill.IsCpuRegister()) {
      __ pushq(spill.AsCpuRegister());
      gpr_count++;
      cfi().AdjustCFAOffset(kFramePointerSize);
      cfi().RelOffset(DWARFReg(spill.AsCpuRegister().AsRegister()), 0);
    }
  }
  // return address then method on stack.
  int64_t rest_of_frame = static_cast<int64_t>(frame_size)
                          - (gpr_count * kFramePointerSize)
                          - kFramePointerSize /*return address*/;
  if (rest_of_frame != 0) {
    __ subq(CpuRegister(RSP), Immediate(rest_of_frame));
    cfi().AdjustCFAOffset(rest_of_frame);
  }

  // spill xmms
  int64_t offset = rest_of_frame;
  for (int i = spill_regs.size() - 1; i >= 0; --i) {
    x86_64::X86_64ManagedRegister spill = spill_regs[i].AsX86_64();
    if (spill.IsXmmRegister()) {
      offset -= sizeof(double);
      __ movsd(Address(CpuRegister(RSP), offset), spill.AsXmmRegister());
      cfi().RelOffset(DWARFReg(spill.AsXmmRegister().AsFloatRegister()), offset);
    }
  }

  static_assert(static_cast<size_t>(kX86_64PointerSize) == kFramePointerSize,
                "Unexpected frame pointer size.");

  if (method_reg.IsRegister()) {
    __ movq(Address(CpuRegister(RSP), 0), method_reg.AsX86_64().AsCpuRegister());
  }
}

void X86_64JNIMacroAssembler::RemoveFrame(size_t frame_size,
                                          ArrayRef<const ManagedRegister> spill_regs,
                                          [[maybe_unused]] bool may_suspend) {
  CHECK_ALIGNED(frame_size, kNativeStackAlignment);
  cfi().RememberState();
  int gpr_count = 0;
  // unspill xmms
  int64_t offset = static_cast<int64_t>(frame_size)
      - (spill_regs.size() * kFramePointerSize)
      - kFramePointerSize;
  for (size_t i = 0; i < spill_regs.size(); ++i) {
    x86_64::X86_64ManagedRegister spill = spill_regs[i].AsX86_64();
    if (spill.IsXmmRegister()) {
      __ movsd(spill.AsXmmRegister(), Address(CpuRegister(RSP), offset));
      cfi().Restore(DWARFReg(spill.AsXmmRegister().AsFloatRegister()));
      offset += sizeof(double);
    } else {
      gpr_count++;
    }
  }
  DCHECK_EQ(static_cast<size_t>(offset),
            frame_size - (gpr_count * kFramePointerSize) - kFramePointerSize);
  if (offset != 0) {
    __ addq(CpuRegister(RSP), Immediate(offset));
    cfi().AdjustCFAOffset(-offset);
  }
  for (size_t i = 0; i < spill_regs.size(); ++i) {
    x86_64::X86_64ManagedRegister spill = spill_regs[i].AsX86_64();
    if (spill.IsCpuRegister()) {
      __ popq(spill.AsCpuRegister());
      cfi().AdjustCFAOffset(-static_cast<int>(kFramePointerSize));
      cfi().Restore(DWARFReg(spill.AsCpuRegister().AsRegister()));
    }
  }
  __ ret();
  // The CFI should be restored for any code that follows the exit block.
  cfi().RestoreState();
  cfi().DefCFAOffset(frame_size);
}

void X86_64JNIMacroAssembler::IncreaseFrameSize(size_t adjust) {
  if (adjust != 0u) {
    CHECK_ALIGNED(adjust, kNativeStackAlignment);
    __ addq(CpuRegister(RSP), Immediate(-static_cast<int64_t>(adjust)));
    cfi().AdjustCFAOffset(adjust);
  }
}

static void DecreaseFrameSizeImpl(size_t adjust, X86_64Assembler* assembler) {
  if (adjust != 0u) {
    CHECK_ALIGNED(adjust, kNativeStackAlignment);
    assembler->addq(CpuRegister(RSP), Immediate(adjust));
    assembler->cfi().AdjustCFAOffset(-adjust);
  }
}

void X86_64JNIMacroAssembler::DecreaseFrameSize(size_t adjust) {
  DecreaseFrameSizeImpl(adjust, &asm_);
}

ManagedRegister X86_64JNIMacroAssembler::CoreRegisterWithSize(ManagedRegister src, size_t size) {
  DCHECK(src.AsX86_64().IsCpuRegister());
  DCHECK(size == 4u || size == 8u) << size;
  return src;
}

void X86_64JNIMacroAssembler::Store(FrameOffset offs, ManagedRegister msrc, size_t size) {
  Store(X86_64ManagedRegister::FromCpuRegister(RSP), MemberOffset(offs.Int32Value()), msrc, size);
}

void X86_64JNIMacroAssembler::Store(ManagedRegister mbase,
                                    MemberOffset offs,
                                    ManagedRegister msrc,
                                    size_t size) {
  X86_64ManagedRegister base = mbase.AsX86_64();
  X86_64ManagedRegister src = msrc.AsX86_64();
  if (src.IsNoRegister()) {
    CHECK_EQ(0u, size);
  } else if (src.IsCpuRegister()) {
    if (size == 4) {
      CHECK_EQ(4u, size);
      __ movl(Address(base.AsCpuRegister(), offs), src.AsCpuRegister());
    } else {
      CHECK_EQ(8u, size);
      __ movq(Address(base.AsCpuRegister(), offs), src.AsCpuRegister());
    }
  } else if (src.IsX87Register()) {
    if (size == 4) {
      __ fstps(Address(base.AsCpuRegister(), offs));
    } else {
      __ fstpl(Address(base.AsCpuRegister(), offs));
    }
  } else {
    CHECK(src.IsXmmRegister());
    if (size == 4) {
      __ movss(Address(base.AsCpuRegister(), offs), src.AsXmmRegister());
    } else {
      __ movsd(Address(base.AsCpuRegister(), offs), src.AsXmmRegister());
    }
  }
}

void X86_64JNIMacroAssembler::StoreRawPtr(FrameOffset dest, ManagedRegister msrc) {
  X86_64ManagedRegister src = msrc.AsX86_64();
  CHECK(src.IsCpuRegister());
  __ movq(Address(CpuRegister(RSP), dest), src.AsCpuRegister());
}

void X86_64JNIMacroAssembler::StoreStackPointerToThread(ThreadOffset64 thr_offs, bool tag_sp) {
  if (tag_sp) {
    CpuRegister reg = GetScratchRegister();
    __ movq(reg, CpuRegister(RSP));
    __ orq(reg, Immediate(0x2));
    __ gs()->movq(Address::Absolute(thr_offs, true), reg);
  } else {
    __ gs()->movq(Address::Absolute(thr_offs, true), CpuRegister(RSP));
  }
}

void X86_64JNIMacroAssembler::Load(ManagedRegister mdest, FrameOffset src, size_t size) {
  Load(mdest, X86_64ManagedRegister::FromCpuRegister(RSP), MemberOffset(src.Int32Value()), size);
}

void X86_64JNIMacroAssembler::Load(ManagedRegister mdest,
                                   ManagedRegister mbase,
                                   MemberOffset offs,
                                   size_t size) {
  X86_64ManagedRegister dest = mdest.AsX86_64();
  X86_64ManagedRegister base = mbase.AsX86_64();
  if (dest.IsNoRegister()) {
    CHECK_EQ(0u, size);
  } else if (dest.IsCpuRegister()) {
    if (size == 4) {
      CHECK_EQ(4u, size);
      __ movl(dest.AsCpuRegister(), Address(base.AsCpuRegister(), offs));
    } else {
      CHECK_EQ(8u, size);
      __ movq(dest.AsCpuRegister(), Address(base.AsCpuRegister(), offs));
    }
  } else if (dest.IsX87Register()) {
    if (size == 4) {
      __ flds(Address(base.AsCpuRegister(), offs));
    } else {
      __ fldl(Address(base.AsCpuRegister(), offs));
    }
  } else {
    CHECK(dest.IsXmmRegister());
    if (size == 4) {
      __ movss(dest.AsXmmRegister(), Address(base.AsCpuRegister(), offs));
    } else {
      __ movsd(dest.AsXmmRegister(), Address(base.AsCpuRegister(), offs));
    }
  }
}

void X86_64JNIMacroAssembler::LoadRawPtrFromThread(ManagedRegister mdest, ThreadOffset64 offs) {
  X86_64ManagedRegister dest = mdest.AsX86_64();
  CHECK(dest.IsCpuRegister());
  __ gs()->movq(dest.AsCpuRegister(), Address::Absolute(offs, true));
}

void X86_64JNIMacroAssembler::SignExtend(ManagedRegister mreg, size_t size) {
  X86_64ManagedRegister reg = mreg.AsX86_64();
  CHECK(size == 1 || size == 2) << size;
  CHECK(reg.IsCpuRegister()) << reg;
  if (size == 1) {
    __ movsxb(reg.AsCpuRegister(), reg.AsCpuRegister());
  } else {
    __ movsxw(reg.AsCpuRegister(), reg.AsCpuRegister());
  }
}

void X86_64JNIMacroAssembler::ZeroExtend(ManagedRegister mreg, size_t size) {
  X86_64ManagedRegister reg = mreg.AsX86_64();
  CHECK(size == 1 || size == 2) << size;
  CHECK(reg.IsCpuRegister()) << reg;
  if (size == 1) {
    __ movzxb(reg.AsCpuRegister(), reg.AsCpuRegister());
  } else {
    __ movzxw(reg.AsCpuRegister(), reg.AsCpuRegister());
  }
}

void X86_64JNIMacroAssembler::MoveArguments(ArrayRef<ArgumentLocation> dests,
                                            ArrayRef<ArgumentLocation> srcs,
                                            ArrayRef<FrameOffset> refs) {
  size_t arg_count = dests.size();
  DCHECK_EQ(arg_count, srcs.size());
  DCHECK_EQ(arg_count, refs.size());

  auto get_mask = [](ManagedRegister reg) -> uint32_t {
    X86_64ManagedRegister x86_64_reg = reg.AsX86_64();
    if (x86_64_reg.IsCpuRegister()) {
      size_t cpu_reg_number = static_cast<size_t>(x86_64_reg.AsCpuRegister().AsRegister());
      DCHECK_LT(cpu_reg_number, 16u);
      return 1u << cpu_reg_number;
    } else {
      DCHECK(x86_64_reg.IsXmmRegister());
      size_t xmm_reg_number = static_cast<size_t>(x86_64_reg.AsXmmRegister().AsFloatRegister());
      DCHECK_LT(xmm_reg_number, 16u);
      return (1u << 16u) << xmm_reg_number;
    }
  };

  // Collect registers to move while storing/copying args to stack slots.
  // Convert all register references and copied stack references to `jobject`.
  uint32_t src_regs = 0u;
  uint32_t dest_regs = 0u;
  for (size_t i = 0; i != arg_count; ++i) {
    const ArgumentLocation& src = srcs[i];
    const ArgumentLocation& dest = dests[i];
    const FrameOffset ref = refs[i];
    if (ref != kInvalidReferenceOffset) {
      DCHECK_EQ(src.GetSize(), kObjectReferenceSize);
      DCHECK_EQ(dest.GetSize(), static_cast<size_t>(kX86_64PointerSize));
    } else {
      DCHECK_EQ(src.GetSize(), dest.GetSize());
    }
    if (src.IsRegister() && ref != kInvalidReferenceOffset) {
      // Note: We can clobber `src` here as the register cannot hold more than one argument.
      //       This overload of `CreateJObject()` is currently implemented as "test and branch";
      //       if it was using a conditional move, it would be better to do this at move time.
      CreateJObject(src.GetRegister(), ref, src.GetRegister(), /*null_allowed=*/ i != 0u);
    }
    if (dest.IsRegister()) {
      // Note: X86_64ManagedRegister makes no distinction between 32-bit and 64-bit core
      // registers, so the following `Equals()` can return `true` for references; the
      // reference has already been converted to `jobject` above.
      if (src.IsRegister() && src.GetRegister().Equals(dest.GetRegister())) {
        // Nothing to do.
      } else {
        if (src.IsRegister()) {
          src_regs |= get_mask(src.GetRegister());
        }
        dest_regs |= get_mask(dest.GetRegister());
      }
    } else {
      if (src.IsRegister()) {
        Store(dest.GetFrameOffset(), src.GetRegister(), dest.GetSize());
      } else if (ref != kInvalidReferenceOffset) {
        CreateJObject(dest.GetFrameOffset(), ref, /*null_allowed=*/ i != 0u);
      } else {
        Copy(dest.GetFrameOffset(), src.GetFrameOffset(), dest.GetSize());
      }
    }
  }

  // Fill destination registers. Convert loaded references to `jobject`.
  // There should be no cycles, so this simple algorithm should make progress.
  while (dest_regs != 0u) {
    uint32_t old_dest_regs = dest_regs;
    for (size_t i = 0; i != arg_count; ++i) {
      const ArgumentLocation& src = srcs[i];
      const ArgumentLocation& dest = dests[i];
      const FrameOffset ref = refs[i];
      if (!dest.IsRegister()) {
        continue;  // Stored in first loop above.
      }
      uint32_t dest_reg_mask = get_mask(dest.GetRegister());
      if ((dest_reg_mask & dest_regs) == 0u) {
        continue;  // Equals source, or already filled in one of previous iterations.
      }
      if ((dest_reg_mask & src_regs) != 0u) {
        continue;  // Cannot clobber this register yet.
      }
      if (src.IsRegister()) {
        Move(dest.GetRegister(), src.GetRegister(), dest.GetSize());
        src_regs &= ~get_mask(src.GetRegister());  // Allow clobbering source register.
      } else if (ref != kInvalidReferenceOffset) {
        CreateJObject(
            dest.GetRegister(), ref, ManagedRegister::NoRegister(), /*null_allowed=*/ i != 0u);
      } else {
        Load(dest.GetRegister(), src.GetFrameOffset(), dest.GetSize());
      }
      dest_regs &= ~get_mask(dest.GetRegister());  // Destination register was filled.
    }
    CHECK_NE(old_dest_regs, dest_regs);
    DCHECK_EQ(0u, dest_regs & ~old_dest_regs);
  }
}

void X86_64JNIMacroAssembler::Move(ManagedRegister mdest, ManagedRegister msrc, size_t size) {
  DCHECK(!mdest.Equals(X86_64ManagedRegister::FromCpuRegister(GetScratchRegister().AsRegister())));
  X86_64ManagedRegister dest = mdest.AsX86_64();
  X86_64ManagedRegister src = msrc.AsX86_64();
  if (!dest.Equals(src)) {
    if (dest.IsCpuRegister() && src.IsCpuRegister()) {
      __ movq(dest.AsCpuRegister(), src.AsCpuRegister());
    } else if (src.IsX87Register() && dest.IsXmmRegister()) {
      // Pass via stack and pop X87 register
      __ subl(CpuRegister(RSP), Immediate(16));
      if (size == 4) {
        CHECK_EQ(src.AsX87Register(), ST0);
        __ fstps(Address(CpuRegister(RSP), 0));
        __ movss(dest.AsXmmRegister(), Address(CpuRegister(RSP), 0));
      } else {
        CHECK_EQ(src.AsX87Register(), ST0);
        __ fstpl(Address(CpuRegister(RSP), 0));
        __ movsd(dest.AsXmmRegister(), Address(CpuRegister(RSP), 0));
      }
      __ addq(CpuRegister(RSP), Immediate(16));
    } else {
      // TODO: x87, SSE
      UNIMPLEMENTED(FATAL) << ": Move " << dest << ", " << src;
    }
  }
}


void X86_64JNIMacroAssembler::Move(ManagedRegister mdest, size_t value) {
  X86_64ManagedRegister dest = mdest.AsX86_64();
  __ movq(dest.AsCpuRegister(), Immediate(value));
}

void X86_64JNIMacroAssembler::Copy(FrameOffset dest, FrameOffset src, size_t size) {
  DCHECK(size == 4 || size == 8) << size;
  CpuRegister scratch = GetScratchRegister();
  if (size == 8) {
    __ movq(scratch, Address(CpuRegister(RSP), src));
    __ movq(Address(CpuRegister(RSP), dest), scratch);
  } else {
    __ movl(scratch, Address(CpuRegister(RSP), src));
    __ movl(Address(CpuRegister(RSP), dest), scratch);
  }
}

void X86_64JNIMacroAssembler::CreateJObject(ManagedRegister mout_reg,
                                            FrameOffset spilled_reference_offset,
                                            ManagedRegister min_reg,
                                            bool null_allowed) {
  X86_64ManagedRegister out_reg = mout_reg.AsX86_64();
  X86_64ManagedRegister in_reg = min_reg.AsX86_64();
  if (in_reg.IsNoRegister()) {  // TODO(64): && null_allowed
    // Use out_reg as indicator of null.
    in_reg = out_reg;
    // TODO: movzwl
    __ movl(in_reg.AsCpuRegister(), Address(CpuRegister(RSP), spilled_reference_offset));
  }
  CHECK(in_reg.IsCpuRegister());
  CHECK(out_reg.IsCpuRegister());
  VerifyObject(in_reg, null_allowed);
  if (null_allowed) {
    Label null_arg;
    if (!out_reg.Equals(in_reg)) {
      __ xorl(out_reg.AsCpuRegister(), out_reg.AsCpuRegister());
    }
    __ testl(in_reg.AsCpuRegister(), in_reg.AsCpuRegister());
    __ j(kZero, &null_arg);
    __ leaq(out_reg.AsCpuRegister(), Address(CpuRegister(RSP), spilled_reference_offset));
    __ Bind(&null_arg);
  } else {
    __ leaq(out_reg.AsCpuRegister(), Address(CpuRegister(RSP), spilled_reference_offset));
  }
}

void X86_64JNIMacroAssembler::CreateJObject(FrameOffset out_off,
                                            FrameOffset spilled_reference_offset,
                                            bool null_allowed) {
  CpuRegister scratch = GetScratchRegister();
  if (null_allowed) {
    Label null_arg;
    __ movl(scratch, Address(CpuRegister(RSP), spilled_reference_offset));
    __ testl(scratch, scratch);
    __ j(kZero, &null_arg);
    __ leaq(scratch, Address(CpuRegister(RSP), spilled_reference_offset));
    __ Bind(&null_arg);
  } else {
    __ leaq(scratch, Address(CpuRegister(RSP), spilled_reference_offset));
  }
  __ movq(Address(CpuRegister(RSP), out_off), scratch);
}

void X86_64JNIMacroAssembler::DecodeJNITransitionOrLocalJObject(ManagedRegister reg,
                                                                JNIMacroLabel* slow_path,
                                                                JNIMacroLabel* resume) {
  constexpr uint64_t kGlobalOrWeakGlobalMask = IndirectReferenceTable::GetGlobalOrWeakGlobalMask();
  constexpr uint64_t kIndirectRefKindMask = IndirectReferenceTable::GetIndirectRefKindMask();
  // TODO: Add `testq()` with `imm32` to assembler to avoid using 64-bit pointer as 32-bit value.
  __ testl(reg.AsX86_64().AsCpuRegister(), Immediate(kGlobalOrWeakGlobalMask));
  __ j(kNotZero, X86_64JNIMacroLabel::Cast(slow_path)->AsX86_64());
  __ andq(reg.AsX86_64().AsCpuRegister(), Immediate(~kIndirectRefKindMask));
  __ j(kZero, X86_64JNIMacroLabel::Cast(resume)->AsX86_64());  // Skip load for null.
  __ movl(reg.AsX86_64().AsCpuRegister(), Address(reg.AsX86_64().AsCpuRegister(), /*disp=*/ 0));
}

void X86_64JNIMacroAssembler::VerifyObject(ManagedRegister /*src*/, bool /*could_be_null*/) {
  // TODO: not validating references
}

void X86_64JNIMacroAssembler::VerifyObject(FrameOffset /*src*/, bool /*could_be_null*/) {
  // TODO: not validating references
}

void X86_64JNIMacroAssembler::Jump(ManagedRegister mbase, Offset offset) {
  X86_64ManagedRegister base = mbase.AsX86_64();
  CHECK(base.IsCpuRegister());
  __ jmp(Address(base.AsCpuRegister(), offset.Int32Value()));
}

void X86_64JNIMacroAssembler::Call(ManagedRegister mbase, Offset offset) {
  X86_64ManagedRegister base = mbase.AsX86_64();
  CHECK(base.IsCpuRegister());
  __ call(Address(base.AsCpuRegister(), offset.Int32Value()));
  // TODO: place reference map on call
}

void X86_64JNIMacroAssembler::CallFromThread(ThreadOffset64 offset) {
  __ gs()->call(Address::Absolute(offset, true));
}

void X86_64JNIMacroAssembler::GetCurrentThread(ManagedRegister dest) {
  __ gs()->movq(dest.AsX86_64().AsCpuRegister(),
                Address::Absolute(Thread::SelfOffset<kX86_64PointerSize>(), true));
}

void X86_64JNIMacroAssembler::GetCurrentThread(FrameOffset offset) {
  CpuRegister scratch = GetScratchRegister();
  __ gs()->movq(scratch, Address::Absolute(Thread::SelfOffset<kX86_64PointerSize>(), true));
  __ movq(Address(CpuRegister(RSP), offset), scratch);
}

void X86_64JNIMacroAssembler::TryToTransitionFromRunnableToNative(
    JNIMacroLabel* label, [[maybe_unused]] ArrayRef<const ManagedRegister> scratch_regs) {
  constexpr uint32_t kNativeStateValue = Thread::StoredThreadStateValue(ThreadState::kNative);
  constexpr uint32_t kRunnableStateValue = Thread::StoredThreadStateValue(ThreadState::kRunnable);
  constexpr ThreadOffset64 thread_flags_offset = Thread::ThreadFlagsOffset<kX86_64PointerSize>();
  constexpr ThreadOffset64 thread_held_mutex_mutator_lock_offset =
      Thread::HeldMutexOffset<kX86_64PointerSize>(kMutatorLock);

  CpuRegister rax(RAX);  // RAX can be freely clobbered. It does not hold any argument.
  CpuRegister scratch = GetScratchRegister();

  // CAS release, old_value = kRunnableStateValue, new_value = kNativeStateValue, no flags.
  static_assert(kRunnableStateValue == 0u);
  __ xorl(rax, rax);
  __ movl(scratch, Immediate(kNativeStateValue));
  __ gs()->LockCmpxchgl(Address::Absolute(thread_flags_offset.Uint32Value(), /*no_rip=*/ true),
                        scratch);
  // LOCK CMPXCHG has full barrier semantics, so we don't need barriers here.
  // If any flags are set, go to the slow path.
  __ j(kNotZero, X86_64JNIMacroLabel::Cast(label)->AsX86_64());

  // Clear `self->tlsPtr_.held_mutexes[kMutatorLock]`.
  __ gs()->movq(
      Address::Absolute(thread_held_mutex_mutator_lock_offset.Uint32Value(), /*no_rip=*/ true),
      Immediate(0));
}

void X86_64JNIMacroAssembler::TryToTransitionFromNativeToRunnable(
    JNIMacroLabel* label,
    ArrayRef<const ManagedRegister> scratch_regs,
    ManagedRegister return_reg) {
  constexpr uint32_t kNativeStateValue = Thread::StoredThreadStateValue(ThreadState::kNative);
  constexpr uint32_t kRunnableStateValue = Thread::StoredThreadStateValue(ThreadState::kRunnable);
  constexpr ThreadOffset64 thread_flags_offset = Thread::ThreadFlagsOffset<kX86_64PointerSize>();
  constexpr ThreadOffset64 thread_held_mutex_mutator_lock_offset =
      Thread::HeldMutexOffset<kX86_64PointerSize>(kMutatorLock);
  constexpr ThreadOffset64 thread_mutator_lock_offset =
      Thread::MutatorLockOffset<kX86_64PointerSize>();

  DCHECK_GE(scratch_regs.size(), 2u);
  DCHECK(!scratch_regs[0].AsX86_64().Overlaps(return_reg.AsX86_64()));
  CpuRegister scratch = scratch_regs[0].AsX86_64().AsCpuRegister();
  DCHECK(!scratch_regs[1].AsX86_64().Overlaps(return_reg.AsX86_64()));
  CpuRegister saved_rax = scratch_regs[1].AsX86_64().AsCpuRegister();
  CpuRegister rax(RAX);
  bool preserve_rax = return_reg.AsX86_64().Overlaps(X86_64ManagedRegister::FromCpuRegister(RAX));

  // CAS acquire, old_value = kNativeStateValue, new_value = kRunnableStateValue, no flags.
  if (preserve_rax) {
    __ movq(saved_rax, rax);  // Save RAX.
  }
  __ movl(rax, Immediate(kNativeStateValue));
  static_assert(kRunnableStateValue == 0u);
  __ xorl(scratch, scratch);
  __ gs()->LockCmpxchgl(Address::Absolute(thread_flags_offset.Uint32Value(), /*no_rip=*/ true),
                        scratch);
  // LOCK CMPXCHG has full barrier semantics, so we don't need barriers here.
  if (preserve_rax) {
    __ movq(rax, saved_rax);  // Restore RAX; MOV does not change flags.
  }
  // If any flags are set, or the state is not Native, go to the slow path.
  // (While the thread can theoretically transition between different Suspended states,
  // it would be very unexpected to see a state other than Native at this point.)
  __ j(kNotZero, X86_64JNIMacroLabel::Cast(label)->AsX86_64());

  // Set `self->tlsPtr_.held_mutexes[kMutatorLock]` to the mutator lock.
  __ gs()->movq(scratch,
                Address::Absolute(thread_mutator_lock_offset.Uint32Value(), /*no_rip=*/ true));
  __ gs()->movq(
      Address::Absolute(thread_held_mutex_mutator_lock_offset.Uint32Value(), /*no_rip=*/ true),
      scratch);
}

void X86_64JNIMacroAssembler::SuspendCheck(JNIMacroLabel* label) {
  __ gs()->testl(Address::Absolute(Thread::ThreadFlagsOffset<kX86_64PointerSize>(), true),
                 Immediate(Thread::SuspendOrCheckpointRequestFlags()));
  __ j(kNotZero, X86_64JNIMacroLabel::Cast(label)->AsX86_64());
}

void X86_64JNIMacroAssembler::ExceptionPoll(JNIMacroLabel* label) {
  __ gs()->cmpl(Address::Absolute(Thread::ExceptionOffset<kX86_64PointerSize>(), true),
                Immediate(0));
  __ j(kNotEqual, X86_64JNIMacroLabel::Cast(label)->AsX86_64());
}

void X86_64JNIMacroAssembler::DeliverPendingException() {
  // Pass exception as argument in RDI
  __ gs()->movq(CpuRegister(RDI),
                Address::Absolute(Thread::ExceptionOffset<kX86_64PointerSize>(), true));
  __ gs()->call(
      Address::Absolute(QUICK_ENTRYPOINT_OFFSET(kX86_64PointerSize, pDeliverException), true));
  // this call should never return
  __ int3();
}

std::unique_ptr<JNIMacroLabel> X86_64JNIMacroAssembler::CreateLabel() {
  return std::unique_ptr<JNIMacroLabel>(new (asm_.GetAllocator()) X86_64JNIMacroLabel());
}

void X86_64JNIMacroAssembler::Jump(JNIMacroLabel* label) {
  CHECK(label != nullptr);
  __ jmp(X86_64JNIMacroLabel::Cast(label)->AsX86_64());
}

static Condition UnaryConditionToX86_64Condition(JNIMacroUnaryCondition cond) {
  switch (cond) {
    case JNIMacroUnaryCondition::kZero:
      return kZero;
    case JNIMacroUnaryCondition::kNotZero:
      return kNotZero;
    default:
      LOG(FATAL) << "Not implemented condition: " << static_cast<int>(cond);
      UNREACHABLE();
  }
}

void X86_64JNIMacroAssembler::TestGcMarking(JNIMacroLabel* label, JNIMacroUnaryCondition cond) {
  CHECK(label != nullptr);

  // CMP self->tls32_.is_gc_marking, 0
  // Jcc <Offset>
  DCHECK_EQ(Thread::IsGcMarkingSize(), 4u);
  __ gs()->cmpl(Address::Absolute(Thread::IsGcMarkingOffset<kX86_64PointerSize>(), true),
                Immediate(0));
  __ j(UnaryConditionToX86_64Condition(cond), X86_64JNIMacroLabel::Cast(label)->AsX86_64());
}

void X86_64JNIMacroAssembler::TestMarkBit(ManagedRegister mref,
                                          JNIMacroLabel* label,
                                          JNIMacroUnaryCondition cond) {
  DCHECK(kUseBakerReadBarrier);
  CpuRegister ref = mref.AsX86_64().AsCpuRegister();
  static_assert(LockWord::kMarkBitStateSize == 1u);
  __ testl(Address(ref, mirror::Object::MonitorOffset().SizeValue()),
           Immediate(LockWord::kMarkBitStateMaskShifted));
  __ j(UnaryConditionToX86_64Condition(cond), X86_64JNIMacroLabel::Cast(label)->AsX86_64());
}

void X86_64JNIMacroAssembler::TestByteAndJumpIfNotZero(uintptr_t address, JNIMacroLabel* label) {
  CpuRegister scratch = GetScratchRegister();
  __ movq(scratch, Immediate(address));
  __ cmpb(Address(scratch, 0), Immediate(0));
  __ j(kNotZero, X86_64JNIMacroLabel::Cast(label)->AsX86_64());
}

void X86_64JNIMacroAssembler::Bind(JNIMacroLabel* label) {
  CHECK(label != nullptr);
  __ Bind(X86_64JNIMacroLabel::Cast(label)->AsX86_64());
}

#undef __

}  // namespace x86_64
}  // namespace art
