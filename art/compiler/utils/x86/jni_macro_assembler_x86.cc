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

#include "jni_macro_assembler_x86.h"

#include "base/casts.h"
#include "entrypoints/quick/quick_entrypoints.h"
#include "indirect_reference_table.h"
#include "lock_word.h"
#include "thread.h"
#include "utils/assembler.h"

namespace art HIDDEN {
namespace x86 {

static Register GetScratchRegister() {
  // ECX is an argument register on entry and gets spilled in BuildFrame().
  // After that, we can use it as a scratch register.
  return ECX;
}

static dwarf::Reg DWARFReg(Register reg) {
  return dwarf::Reg::X86Core(static_cast<int>(reg));
}

constexpr size_t kFramePointerSize = 4;

static constexpr size_t kNativeStackAlignment = 16;
static_assert(kNativeStackAlignment == kStackAlignment);

#define __ asm_.

void X86JNIMacroAssembler::BuildFrame(size_t frame_size,
                                      ManagedRegister method_reg,
                                      ArrayRef<const ManagedRegister> spill_regs) {
  DCHECK_EQ(CodeSize(), 0U);  // Nothing emitted yet.
  cfi().SetCurrentCFAOffset(4);  // Return address on stack.
  if (frame_size == kFramePointerSize) {
    // For @CriticalNative tail call.
    CHECK(method_reg.IsNoRegister());
    CHECK(spill_regs.empty());
  } else if (method_reg.IsNoRegister()) {
    CHECK_ALIGNED(frame_size, kNativeStackAlignment);
  } else {
    CHECK_ALIGNED(frame_size, kStackAlignment);
  }
  int gpr_count = 0;
  for (int i = spill_regs.size() - 1; i >= 0; --i) {
    Register spill = spill_regs[i].AsX86().AsCpuRegister();
    __ pushl(spill);
    gpr_count++;
    cfi().AdjustCFAOffset(kFramePointerSize);
    cfi().RelOffset(DWARFReg(spill), 0);
  }

  // return address then method on stack.
  int32_t adjust = frame_size - gpr_count * kFramePointerSize -
      kFramePointerSize /*return address*/ -
      (method_reg.IsRegister() ? kFramePointerSize /*method*/ : 0u);
  if (adjust != 0) {
    __ addl(ESP, Immediate(-adjust));
    cfi().AdjustCFAOffset(adjust);
  }
  if (method_reg.IsRegister()) {
    __ pushl(method_reg.AsX86().AsCpuRegister());
    cfi().AdjustCFAOffset(kFramePointerSize);
  }
  DCHECK_EQ(static_cast<size_t>(cfi().GetCurrentCFAOffset()), frame_size);
}

void X86JNIMacroAssembler::RemoveFrame(size_t frame_size,
                                       ArrayRef<const ManagedRegister> spill_regs,
                                       [[maybe_unused]] bool may_suspend) {
  CHECK_ALIGNED(frame_size, kNativeStackAlignment);
  cfi().RememberState();
  // -kFramePointerSize for ArtMethod*.
  int adjust = frame_size - spill_regs.size() * kFramePointerSize - kFramePointerSize;
  if (adjust != 0) {
    __ addl(ESP, Immediate(adjust));
    cfi().AdjustCFAOffset(-adjust);
  }
  for (size_t i = 0; i < spill_regs.size(); ++i) {
    Register spill = spill_regs[i].AsX86().AsCpuRegister();
    __ popl(spill);
    cfi().AdjustCFAOffset(-static_cast<int>(kFramePointerSize));
    cfi().Restore(DWARFReg(spill));
  }
  __ ret();
  // The CFI should be restored for any code that follows the exit block.
  cfi().RestoreState();
  cfi().DefCFAOffset(frame_size);
}

void X86JNIMacroAssembler::IncreaseFrameSize(size_t adjust) {
  if (adjust != 0u) {
    CHECK_ALIGNED(adjust, kNativeStackAlignment);
    __ addl(ESP, Immediate(-adjust));
    cfi().AdjustCFAOffset(adjust);
  }
}

static void DecreaseFrameSizeImpl(X86Assembler* assembler, size_t adjust) {
  if (adjust != 0u) {
    CHECK_ALIGNED(adjust, kNativeStackAlignment);
    assembler->addl(ESP, Immediate(adjust));
    assembler->cfi().AdjustCFAOffset(-adjust);
  }
}

ManagedRegister X86JNIMacroAssembler::CoreRegisterWithSize(ManagedRegister src, size_t size) {
  DCHECK(src.AsX86().IsCpuRegister());
  DCHECK_EQ(size, 4u);
  return src;
}

void X86JNIMacroAssembler::DecreaseFrameSize(size_t adjust) {
  DecreaseFrameSizeImpl(&asm_, adjust);
}

void X86JNIMacroAssembler::Store(FrameOffset offs, ManagedRegister msrc, size_t size) {
  Store(X86ManagedRegister::FromCpuRegister(ESP), MemberOffset(offs.Int32Value()), msrc, size);
}

void X86JNIMacroAssembler::Store(ManagedRegister mbase,
                                 MemberOffset offs,
                                 ManagedRegister msrc,
                                 size_t size) {
  X86ManagedRegister base = mbase.AsX86();
  X86ManagedRegister src = msrc.AsX86();
  if (src.IsNoRegister()) {
    CHECK_EQ(0u, size);
  } else if (src.IsCpuRegister()) {
    CHECK_EQ(4u, size);
    __ movl(Address(base.AsCpuRegister(), offs), src.AsCpuRegister());
  } else if (src.IsRegisterPair()) {
    CHECK_EQ(8u, size);
    __ movl(Address(base.AsCpuRegister(), offs), src.AsRegisterPairLow());
    __ movl(Address(base.AsCpuRegister(), FrameOffset(offs.Int32Value()+4)),
            src.AsRegisterPairHigh());
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

void X86JNIMacroAssembler::StoreRawPtr(FrameOffset dest, ManagedRegister msrc) {
  X86ManagedRegister src = msrc.AsX86();
  CHECK(src.IsCpuRegister());
  __ movl(Address(ESP, dest), src.AsCpuRegister());
}

void X86JNIMacroAssembler::StoreStackPointerToThread(ThreadOffset32 thr_offs, bool tag_sp) {
  if (tag_sp) {
    // There is no free register, store contents onto stack and restore back later.
    Register scratch = ECX;
    __ movl(Address(ESP, -32), scratch);
    __ movl(scratch, ESP);
    __ orl(scratch, Immediate(0x2));
    __ fs()->movl(Address::Absolute(thr_offs), scratch);
    __ movl(scratch, Address(ESP, -32));
  } else {
    __ fs()->movl(Address::Absolute(thr_offs), ESP);
  }
}

void X86JNIMacroAssembler::Load(ManagedRegister mdest, FrameOffset src, size_t size) {
  Load(mdest, X86ManagedRegister::FromCpuRegister(ESP), MemberOffset(src.Int32Value()), size);
}

void X86JNIMacroAssembler::Load(ManagedRegister mdest,
                                ManagedRegister mbase,
                                MemberOffset offs,
                                size_t size) {
  X86ManagedRegister dest = mdest.AsX86();
  X86ManagedRegister base = mbase.AsX86();
  if (dest.IsNoRegister()) {
    CHECK_EQ(0u, size);
  } else if (dest.IsCpuRegister()) {
    CHECK_EQ(4u, size);
    __ movl(dest.AsCpuRegister(), Address(base.AsCpuRegister(), offs));
  } else if (dest.IsRegisterPair()) {
    CHECK_EQ(8u, size);
    __ movl(dest.AsRegisterPairLow(), Address(base.AsCpuRegister(), offs));
    __ movl(dest.AsRegisterPairHigh(),
            Address(base.AsCpuRegister(), FrameOffset(offs.Int32Value()+4)));
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

void X86JNIMacroAssembler::LoadRawPtrFromThread(ManagedRegister mdest, ThreadOffset32 offs) {
  X86ManagedRegister dest = mdest.AsX86();
  CHECK(dest.IsCpuRegister());
  __ fs()->movl(dest.AsCpuRegister(), Address::Absolute(offs));
}

void X86JNIMacroAssembler::SignExtend(ManagedRegister mreg, size_t size) {
  X86ManagedRegister reg = mreg.AsX86();
  CHECK(size == 1 || size == 2) << size;
  CHECK(reg.IsCpuRegister()) << reg;
  if (size == 1) {
    __ movsxb(reg.AsCpuRegister(), reg.AsByteRegister());
  } else {
    __ movsxw(reg.AsCpuRegister(), reg.AsCpuRegister());
  }
}

void X86JNIMacroAssembler::ZeroExtend(ManagedRegister mreg, size_t size) {
  X86ManagedRegister reg = mreg.AsX86();
  CHECK(size == 1 || size == 2) << size;
  CHECK(reg.IsCpuRegister()) << reg;
  if (size == 1) {
    __ movzxb(reg.AsCpuRegister(), reg.AsByteRegister());
  } else {
    __ movzxw(reg.AsCpuRegister(), reg.AsCpuRegister());
  }
}

void X86JNIMacroAssembler::MoveArguments(ArrayRef<ArgumentLocation> dests,
                                         ArrayRef<ArgumentLocation> srcs,
                                         ArrayRef<FrameOffset> refs) {
  size_t arg_count = dests.size();
  DCHECK_EQ(arg_count, srcs.size());
  DCHECK_EQ(arg_count, refs.size());

  // Store register args to stack slots. Convert processed references to `jobject`.
  bool found_hidden_arg = false;
  for (size_t i = 0; i != arg_count; ++i) {
    const ArgumentLocation& src = srcs[i];
    const ArgumentLocation& dest = dests[i];
    const FrameOffset ref = refs[i];
    DCHECK_EQ(src.GetSize(), dest.GetSize());  // Even for references.
    if (src.IsRegister()) {
      if (UNLIKELY(dest.IsRegister())) {
        if (dest.GetRegister().Equals(src.GetRegister())) {
          // JNI compiler sometimes adds a no-op move.
          continue;
        }
        // Native ABI has only stack arguments but we may pass one "hidden arg" in register.
        CHECK(!found_hidden_arg);
        found_hidden_arg = true;
        DCHECK_EQ(ref, kInvalidReferenceOffset);
        DCHECK(
            !dest.GetRegister().Equals(X86ManagedRegister::FromCpuRegister(GetScratchRegister())));
        Move(dest.GetRegister(), src.GetRegister(), dest.GetSize());
      } else {
        if (ref != kInvalidReferenceOffset) {
          // Note: We can clobber `src` here as the register cannot hold more than one argument.
          //       This overload of `CreateJObject()` currently does not use the scratch
          //       register ECX, so this shall not clobber another argument.
          CreateJObject(src.GetRegister(), ref, src.GetRegister(), /*null_allowed=*/ i != 0u);
        }
        Store(dest.GetFrameOffset(), src.GetRegister(), dest.GetSize());
      }
    } else {
      // Delay copying until we have spilled all registers, including the scratch register ECX.
    }
  }

  // Copy incoming stack args. Convert processed references to `jobject`.
  for (size_t i = 0; i != arg_count; ++i) {
    const ArgumentLocation& src = srcs[i];
    const ArgumentLocation& dest = dests[i];
    const FrameOffset ref = refs[i];
    DCHECK_EQ(src.GetSize(), dest.GetSize());  // Even for references.
    if (!src.IsRegister()) {
      DCHECK(!dest.IsRegister());
      if (ref != kInvalidReferenceOffset) {
        DCHECK_EQ(srcs[i].GetFrameOffset(), refs[i]);
        CreateJObject(dest.GetFrameOffset(), ref, /*null_allowed=*/ i != 0u);
      } else {
        Copy(dest.GetFrameOffset(), src.GetFrameOffset(), dest.GetSize());
      }
    }
  }
}

void X86JNIMacroAssembler::Move(ManagedRegister mdest, ManagedRegister msrc, size_t size) {
  DCHECK(!mdest.Equals(X86ManagedRegister::FromCpuRegister(GetScratchRegister())));
  X86ManagedRegister dest = mdest.AsX86();
  X86ManagedRegister src = msrc.AsX86();
  if (!dest.Equals(src)) {
    if (dest.IsCpuRegister() && src.IsCpuRegister()) {
      __ movl(dest.AsCpuRegister(), src.AsCpuRegister());
    } else if (src.IsX87Register() && dest.IsXmmRegister()) {
      // Pass via stack and pop X87 register
      IncreaseFrameSize(16);
      if (size == 4) {
        CHECK_EQ(src.AsX87Register(), ST0);
        __ fstps(Address(ESP, 0));
        __ movss(dest.AsXmmRegister(), Address(ESP, 0));
      } else {
        CHECK_EQ(src.AsX87Register(), ST0);
        __ fstpl(Address(ESP, 0));
        __ movsd(dest.AsXmmRegister(), Address(ESP, 0));
      }
      DecreaseFrameSize(16);
    } else {
      // TODO: x87, SSE
      UNIMPLEMENTED(FATAL) << ": Move " << dest << ", " << src;
    }
  }
}

void X86JNIMacroAssembler::Move(ManagedRegister mdest, size_t value) {
  X86ManagedRegister dest = mdest.AsX86();
  __ movl(dest.AsCpuRegister(), Immediate(value));
}

void X86JNIMacroAssembler::Copy(FrameOffset dest, FrameOffset src, size_t size) {
  DCHECK(size == 4 || size == 8) << size;
  Register scratch = GetScratchRegister();
  __ movl(scratch, Address(ESP, src));
  __ movl(Address(ESP, dest), scratch);
  if (size == 8) {
    __ movl(scratch, Address(ESP, FrameOffset(src.Int32Value() + 4)));
    __ movl(Address(ESP, FrameOffset(dest.Int32Value() + 4)), scratch);
  }
}

void X86JNIMacroAssembler::CreateJObject(ManagedRegister mout_reg,
                                         FrameOffset spilled_reference_offset,
                                         ManagedRegister min_reg,
                                         bool null_allowed) {
  X86ManagedRegister out_reg = mout_reg.AsX86();
  X86ManagedRegister in_reg = min_reg.AsX86();
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
    __ leal(out_reg.AsCpuRegister(), Address(ESP, spilled_reference_offset));
    __ Bind(&null_arg);
  } else {
    __ leal(out_reg.AsCpuRegister(), Address(ESP, spilled_reference_offset));
  }
}

void X86JNIMacroAssembler::CreateJObject(FrameOffset out_off,
                                         FrameOffset spilled_reference_offset,
                                         bool null_allowed) {
  Register scratch = GetScratchRegister();
  if (null_allowed) {
    Label null_arg;
    __ movl(scratch, Address(ESP, spilled_reference_offset));
    __ testl(scratch, scratch);
    __ j(kZero, &null_arg);
    __ leal(scratch, Address(ESP, spilled_reference_offset));
    __ Bind(&null_arg);
  } else {
    __ leal(scratch, Address(ESP, spilled_reference_offset));
  }
  __ movl(Address(ESP, out_off), scratch);
}

void X86JNIMacroAssembler::DecodeJNITransitionOrLocalJObject(ManagedRegister reg,
                                                             JNIMacroLabel* slow_path,
                                                             JNIMacroLabel* resume) {
  constexpr uint32_t kGlobalOrWeakGlobalMask =
      dchecked_integral_cast<uint32_t>(IndirectReferenceTable::GetGlobalOrWeakGlobalMask());
  constexpr uint32_t kIndirectRefKindMask =
      dchecked_integral_cast<uint32_t>(IndirectReferenceTable::GetIndirectRefKindMask());
  __ testl(reg.AsX86().AsCpuRegister(), Immediate(kGlobalOrWeakGlobalMask));
  __ j(kNotZero, X86JNIMacroLabel::Cast(slow_path)->AsX86());
  __ andl(reg.AsX86().AsCpuRegister(), Immediate(~kIndirectRefKindMask));
  __ j(kZero, X86JNIMacroLabel::Cast(resume)->AsX86());  // Skip load for null.
  __ movl(reg.AsX86().AsCpuRegister(), Address(reg.AsX86().AsCpuRegister(), /*disp=*/ 0));
}

void X86JNIMacroAssembler::VerifyObject(ManagedRegister /*src*/, bool /*could_be_null*/) {
  // TODO: not validating references
}

void X86JNIMacroAssembler::VerifyObject(FrameOffset /*src*/, bool /*could_be_null*/) {
  // TODO: not validating references
}

void X86JNIMacroAssembler::Jump(ManagedRegister mbase, Offset offset) {
  X86ManagedRegister base = mbase.AsX86();
  CHECK(base.IsCpuRegister());
  __ jmp(Address(base.AsCpuRegister(), offset.Int32Value()));
}

void X86JNIMacroAssembler::Call(ManagedRegister mbase, Offset offset) {
  X86ManagedRegister base = mbase.AsX86();
  CHECK(base.IsCpuRegister());
  __ call(Address(base.AsCpuRegister(), offset.Int32Value()));
  // TODO: place reference map on call
}

void X86JNIMacroAssembler::CallFromThread(ThreadOffset32 offset) {
  __ fs()->call(Address::Absolute(offset));
}

void X86JNIMacroAssembler::GetCurrentThread(ManagedRegister dest) {
  __ fs()->movl(dest.AsX86().AsCpuRegister(),
                Address::Absolute(Thread::SelfOffset<kX86PointerSize>()));
}

void X86JNIMacroAssembler::GetCurrentThread(FrameOffset offset) {
  Register scratch = GetScratchRegister();
  __ fs()->movl(scratch, Address::Absolute(Thread::SelfOffset<kX86PointerSize>()));
  __ movl(Address(ESP, offset), scratch);
}

void X86JNIMacroAssembler::TryToTransitionFromRunnableToNative(
    JNIMacroLabel* label, ArrayRef<const ManagedRegister> scratch_regs) {
  constexpr uint32_t kNativeStateValue = Thread::StoredThreadStateValue(ThreadState::kNative);
  constexpr uint32_t kRunnableStateValue = Thread::StoredThreadStateValue(ThreadState::kRunnable);
  constexpr ThreadOffset32 thread_flags_offset = Thread::ThreadFlagsOffset<kX86PointerSize>();
  constexpr ThreadOffset32 thread_held_mutex_mutator_lock_offset =
      Thread::HeldMutexOffset<kX86PointerSize>(kMutatorLock);

  // We need to preserve managed argument EAX.
  DCHECK_GE(scratch_regs.size(), 2u);
  Register saved_eax = scratch_regs[0].AsX86().AsCpuRegister();
  Register scratch = scratch_regs[1].AsX86().AsCpuRegister();

  // CAS release, old_value = kRunnableStateValue, new_value = kNativeStateValue, no flags.
  __ movl(saved_eax, EAX);  // Save EAX.
  static_assert(kRunnableStateValue == 0u);
  __ xorl(EAX, EAX);
  __ movl(scratch, Immediate(kNativeStateValue));
  __ fs()->LockCmpxchgl(Address::Absolute(thread_flags_offset.Uint32Value()), scratch);
  // LOCK CMPXCHG has full barrier semantics, so we don't need barriers here.
  __ movl(EAX, saved_eax);  // Restore EAX; MOV does not change flags.
  // If any flags are set, go to the slow path.
  __ j(kNotZero, X86JNIMacroLabel::Cast(label)->AsX86());

  // Clear `self->tlsPtr_.held_mutexes[kMutatorLock]`.
  __ fs()->movl(Address::Absolute(thread_held_mutex_mutator_lock_offset.Uint32Value()),
                Immediate(0));
}

void X86JNIMacroAssembler::TryToTransitionFromNativeToRunnable(
    JNIMacroLabel* label,
    ArrayRef<const ManagedRegister> scratch_regs,
    ManagedRegister return_reg) {
  constexpr uint32_t kNativeStateValue = Thread::StoredThreadStateValue(ThreadState::kNative);
  constexpr uint32_t kRunnableStateValue = Thread::StoredThreadStateValue(ThreadState::kRunnable);
  constexpr ThreadOffset32 thread_flags_offset = Thread::ThreadFlagsOffset<kX86PointerSize>();
  constexpr ThreadOffset32 thread_held_mutex_mutator_lock_offset =
      Thread::HeldMutexOffset<kX86PointerSize>(kMutatorLock);
  constexpr ThreadOffset32 thread_mutator_lock_offset =
      Thread::MutatorLockOffset<kX86PointerSize>();

  size_t scratch_index = 0u;
  auto get_scratch_reg = [&]() {
    while (true) {
      DCHECK_LT(scratch_index, scratch_regs.size());
      X86ManagedRegister scratch_reg = scratch_regs[scratch_index].AsX86();
      ++scratch_index;
      DCHECK(!scratch_reg.Overlaps(return_reg.AsX86()));
      if (scratch_reg.AsCpuRegister() != EAX) {
        return scratch_reg.AsCpuRegister();
      }
    }
  };
  Register scratch = get_scratch_reg();
  bool preserve_eax = return_reg.AsX86().Overlaps(X86ManagedRegister::FromCpuRegister(EAX));
  Register saved_eax = preserve_eax ? get_scratch_reg() : kNoRegister;

  // CAS acquire, old_value = kNativeStateValue, new_value = kRunnableStateValue, no flags.
  if (preserve_eax) {
    __ movl(saved_eax, EAX);  // Save EAX.
  }
  __ movl(EAX, Immediate(kNativeStateValue));
  static_assert(kRunnableStateValue == 0u);
  __ xorl(scratch, scratch);
  __ fs()->LockCmpxchgl(Address::Absolute(thread_flags_offset.Uint32Value()), scratch);
  // LOCK CMPXCHG has full barrier semantics, so we don't need barriers here.
  if (preserve_eax) {
    __ movl(EAX, saved_eax);  // Restore EAX; MOV does not change flags.
  }
  // If any flags are set, or the state is not Native, go to the slow path.
  // (While the thread can theoretically transition between different Suspended states,
  // it would be very unexpected to see a state other than Native at this point.)
  __ j(kNotZero, X86JNIMacroLabel::Cast(label)->AsX86());

  // Set `self->tlsPtr_.held_mutexes[kMutatorLock]` to the mutator lock.
  __ fs()->movl(scratch, Address::Absolute(thread_mutator_lock_offset.Uint32Value()));
  __ fs()->movl(Address::Absolute(thread_held_mutex_mutator_lock_offset.Uint32Value()),
                scratch);
}

void X86JNIMacroAssembler::SuspendCheck(JNIMacroLabel* label) {
  __ fs()->testl(Address::Absolute(Thread::ThreadFlagsOffset<kX86PointerSize>()),
                 Immediate(Thread::SuspendOrCheckpointRequestFlags()));
  __ j(kNotZero, X86JNIMacroLabel::Cast(label)->AsX86());
}

void X86JNIMacroAssembler::ExceptionPoll(JNIMacroLabel* label) {
  __ fs()->cmpl(Address::Absolute(Thread::ExceptionOffset<kX86PointerSize>()), Immediate(0));
  __ j(kNotEqual, X86JNIMacroLabel::Cast(label)->AsX86());
}

void X86JNIMacroAssembler::DeliverPendingException() {
  // Pass exception as argument in EAX
  __ fs()->movl(EAX, Address::Absolute(Thread::ExceptionOffset<kX86PointerSize>()));
  __ fs()->call(Address::Absolute(QUICK_ENTRYPOINT_OFFSET(kX86PointerSize, pDeliverException)));
  // this call should never return
  __ int3();
}

std::unique_ptr<JNIMacroLabel> X86JNIMacroAssembler::CreateLabel() {
  return std::unique_ptr<JNIMacroLabel>(new (asm_.GetAllocator()) X86JNIMacroLabel());
}

void X86JNIMacroAssembler::Jump(JNIMacroLabel* label) {
  CHECK(label != nullptr);
  __ jmp(X86JNIMacroLabel::Cast(label)->AsX86());
}

static Condition UnaryConditionToX86Condition(JNIMacroUnaryCondition cond) {
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

void X86JNIMacroAssembler::TestGcMarking(JNIMacroLabel* label, JNIMacroUnaryCondition cond) {
  CHECK(label != nullptr);

  // CMP self->tls32_.is_gc_marking, 0
  // Jcc <Offset>
  DCHECK_EQ(Thread::IsGcMarkingSize(), 4u);
  __ fs()->cmpl(Address::Absolute(Thread::IsGcMarkingOffset<kX86PointerSize>()), Immediate(0));
  __ j(UnaryConditionToX86Condition(cond), X86JNIMacroLabel::Cast(label)->AsX86());
}

void X86JNIMacroAssembler::TestMarkBit(ManagedRegister mref,
                                       JNIMacroLabel* label,
                                       JNIMacroUnaryCondition cond) {
  DCHECK(kUseBakerReadBarrier);
  Register ref = mref.AsX86().AsCpuRegister();
  static_assert(LockWord::kMarkBitStateSize == 1u);
  __ testl(Address(ref, mirror::Object::MonitorOffset().SizeValue()),
           Immediate(LockWord::kMarkBitStateMaskShifted));
  __ j(UnaryConditionToX86Condition(cond), X86JNIMacroLabel::Cast(label)->AsX86());
}


void X86JNIMacroAssembler::TestByteAndJumpIfNotZero(uintptr_t address, JNIMacroLabel* label) {
  __ cmpb(Address::Absolute(address), Immediate(0));
  __ j(kNotZero, X86JNIMacroLabel::Cast(label)->AsX86());
}

void X86JNIMacroAssembler::Bind(JNIMacroLabel* label) {
  CHECK(label != nullptr);
  __ Bind(X86JNIMacroLabel::Cast(label)->AsX86());
}

#undef __

}  // namespace x86
}  // namespace art
