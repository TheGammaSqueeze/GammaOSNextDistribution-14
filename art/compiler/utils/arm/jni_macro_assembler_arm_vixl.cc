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

#include "jni_macro_assembler_arm_vixl.h"

#include <iostream>
#include <type_traits>

#include "entrypoints/quick/quick_entrypoints.h"
#include "indirect_reference_table.h"
#include "jni/jni_env_ext.h"
#include "jni/local_reference_table.h"
#include "lock_word.h"
#include "thread.h"

using namespace vixl::aarch32;  // NOLINT(build/namespaces)
namespace vixl32 = vixl::aarch32;

using vixl::ExactAssemblyScope;

namespace art HIDDEN {
namespace arm {

#ifdef ___
#error "ARM Assembler macro already defined."
#else
#define ___   asm_.GetVIXLAssembler()->
#endif

// The AAPCS requires 8-byte alignment. This is not as strict as the Managed ABI stack alignment.
static constexpr size_t kAapcsStackAlignment = 8u;
static_assert(kAapcsStackAlignment < kStackAlignment);

// STRD immediate can encode any 4-byte aligned offset smaller than this cutoff.
static constexpr size_t kStrdOffsetCutoff = 1024u;

// ADD sp, imm can encode 4-byte aligned immediate smaller than this cutoff.
static constexpr size_t kAddSpImmCutoff = 1024u;

vixl::aarch32::Register AsVIXLRegister(ArmManagedRegister reg) {
  CHECK(reg.IsCoreRegister());
  return vixl::aarch32::Register(reg.RegId());
}

static inline vixl::aarch32::SRegister AsVIXLSRegister(ArmManagedRegister reg) {
  CHECK(reg.IsSRegister());
  return vixl::aarch32::SRegister(reg.RegId() - kNumberOfCoreRegIds);
}

static inline vixl::aarch32::DRegister AsVIXLDRegister(ArmManagedRegister reg) {
  CHECK(reg.IsDRegister());
  return vixl::aarch32::DRegister(reg.RegId() - kNumberOfCoreRegIds - kNumberOfSRegIds);
}

static inline vixl::aarch32::Register AsVIXLRegisterPairLow(ArmManagedRegister reg) {
  return vixl::aarch32::Register(reg.AsRegisterPairLow());
}

static inline vixl::aarch32::Register AsVIXLRegisterPairHigh(ArmManagedRegister reg) {
  return vixl::aarch32::Register(reg.AsRegisterPairHigh());
}

void ArmVIXLJNIMacroAssembler::FinalizeCode() {
  asm_.FinalizeCode();
}

static constexpr size_t kFramePointerSize = static_cast<size_t>(kArmPointerSize);

void ArmVIXLJNIMacroAssembler::BuildFrame(size_t frame_size,
                                          ManagedRegister method_reg,
                                          ArrayRef<const ManagedRegister> callee_save_regs) {
  // If we're creating an actual frame with the method, enforce managed stack alignment,
  // otherwise only the native stack alignment.
  if (method_reg.IsNoRegister()) {
    CHECK_ALIGNED_PARAM(frame_size, kAapcsStackAlignment);
  } else {
    CHECK_ALIGNED_PARAM(frame_size, kStackAlignment);
  }

  // Push callee saves and link register.
  RegList core_spill_mask = 0;
  uint32_t fp_spill_mask = 0;
  for (const ManagedRegister& reg : callee_save_regs) {
    if (reg.AsArm().IsCoreRegister()) {
      core_spill_mask |= 1 << reg.AsArm().AsCoreRegister();
    } else {
      fp_spill_mask |= 1 << reg.AsArm().AsSRegister();
    }
  }
  if (core_spill_mask == (1u << lr.GetCode()) &&
      fp_spill_mask == 0u &&
      frame_size == 2 * kFramePointerSize &&
      !method_reg.IsRegister()) {
    // Special case: Only LR to push and one word to skip. Do this with a single
    // 16-bit PUSH instruction by arbitrarily pushing r3 (without CFI for r3).
    core_spill_mask |= 1u << r3.GetCode();
    ___ Push(RegisterList(core_spill_mask));
    cfi().AdjustCFAOffset(2 * kFramePointerSize);
    cfi().RelOffset(DWARFReg(lr), kFramePointerSize);
  } else if (core_spill_mask != 0u) {
    ___ Push(RegisterList(core_spill_mask));
    cfi().AdjustCFAOffset(POPCOUNT(core_spill_mask) * kFramePointerSize);
    cfi().RelOffsetForMany(DWARFReg(r0), 0, core_spill_mask, kFramePointerSize);
  }
  if (fp_spill_mask != 0) {
    uint32_t first = CTZ(fp_spill_mask);

    // Check that list is contiguous.
    DCHECK_EQ(fp_spill_mask >> CTZ(fp_spill_mask), ~0u >> (32 - POPCOUNT(fp_spill_mask)));

    ___ Vpush(SRegisterList(vixl32::SRegister(first), POPCOUNT(fp_spill_mask)));
    cfi().AdjustCFAOffset(POPCOUNT(fp_spill_mask) * kFramePointerSize);
    cfi().RelOffsetForMany(DWARFReg(s0), 0, fp_spill_mask, kFramePointerSize);
  }

  // Increase frame to required size.
  int pushed_values = POPCOUNT(core_spill_mask) + POPCOUNT(fp_spill_mask);
  // Must at least have space for Method* if we're going to spill it.
  CHECK_GE(frame_size, (pushed_values + (method_reg.IsRegister() ? 1u : 0u)) * kFramePointerSize);
  IncreaseFrameSize(frame_size - pushed_values * kFramePointerSize);  // handles CFI as well.

  if (method_reg.IsRegister()) {
    // Write out Method*.
    CHECK(r0.Is(AsVIXLRegister(method_reg.AsArm())));
    asm_.StoreToOffset(kStoreWord, r0, sp, 0);
  }
}

void ArmVIXLJNIMacroAssembler::RemoveFrame(size_t frame_size,
                                           ArrayRef<const ManagedRegister> callee_save_regs,
                                           bool may_suspend) {
  CHECK_ALIGNED(frame_size, kAapcsStackAlignment);

  // Compute callee saves to pop.
  RegList core_spill_mask = 0u;
  uint32_t fp_spill_mask = 0u;
  for (const ManagedRegister& reg : callee_save_regs) {
    if (reg.AsArm().IsCoreRegister()) {
      core_spill_mask |= 1u << reg.AsArm().AsCoreRegister();
    } else {
      fp_spill_mask |= 1u << reg.AsArm().AsSRegister();
    }
  }

  // Pop LR to PC unless we need to emit some read barrier code just before returning.
  bool emit_code_before_return =
      kReserveMarkingRegister &&
      (may_suspend || (kIsDebugBuild && emit_run_time_checks_in_debug_mode_));
  if ((core_spill_mask & (1u << lr.GetCode())) != 0u && !emit_code_before_return) {
    DCHECK_EQ(core_spill_mask & (1u << pc.GetCode()), 0u);
    core_spill_mask ^= (1u << lr.GetCode()) | (1u << pc.GetCode());
  }

  // If there are no FP registers to pop and we pop PC, we can avoid emitting any CFI.
  if (fp_spill_mask == 0u && (core_spill_mask & (1u << pc.GetCode())) != 0u) {
    if (frame_size == POPCOUNT(core_spill_mask) * kFramePointerSize) {
      // Just pop all registers and avoid CFI.
      ___ Pop(RegisterList(core_spill_mask));
      return;
    } else if (frame_size == 8u && core_spill_mask == (1u << pc.GetCode())) {
      // Special case: One word to ignore and one to pop to PC. We are free to clobber the
      // caller-save register r3 on return, so use a 16-bit POP instruction and avoid CFI.
      ___ Pop(RegisterList((1u << r3.GetCode()) | (1u << pc.GetCode())));
      return;
    }
  }

  // We shall need to adjust CFI and restore it after the frame exit sequence.
  cfi().RememberState();

  // Decrease frame to start of callee saves.
  size_t pop_values = POPCOUNT(core_spill_mask) + POPCOUNT(fp_spill_mask);
  CHECK_GE(frame_size, pop_values * kFramePointerSize);
  DecreaseFrameSize(frame_size - (pop_values * kFramePointerSize));  // handles CFI as well.

  // Pop FP callee saves.
  if (fp_spill_mask != 0u) {
    uint32_t first = CTZ(fp_spill_mask);
    // Check that list is contiguous.
     DCHECK_EQ(fp_spill_mask >> CTZ(fp_spill_mask), ~0u >> (32 - POPCOUNT(fp_spill_mask)));

    ___ Vpop(SRegisterList(vixl32::SRegister(first), POPCOUNT(fp_spill_mask)));
    cfi().AdjustCFAOffset(-kFramePointerSize * POPCOUNT(fp_spill_mask));
    cfi().RestoreMany(DWARFReg(s0), fp_spill_mask);
  }

  // Pop core callee saves.
  if (core_spill_mask != 0u) {
    ___ Pop(RegisterList(core_spill_mask));
    if ((core_spill_mask & (1u << pc.GetCode())) == 0u) {
      cfi().AdjustCFAOffset(-kFramePointerSize * POPCOUNT(core_spill_mask));
      cfi().RestoreMany(DWARFReg(r0), core_spill_mask);
    }
  }

  // Emit marking register refresh even with all GCs as we are still using the
  // register due to nterp's dependency.
  if (kReserveMarkingRegister) {
    if (may_suspend) {
      // The method may be suspended; refresh the Marking Register.
      ___ Ldr(mr, MemOperand(tr, Thread::IsGcMarkingOffset<kArmPointerSize>().Int32Value()));
    } else {
      // The method shall not be suspended; no need to refresh the Marking Register.

      // The Marking Register is a callee-save register, and thus has been
      // preserved by native code following the AAPCS calling convention.

      // The following condition is a compile-time one, so it does not have a run-time cost.
      if (kIsDebugBuild) {
        // The following condition is a run-time one; it is executed after the
        // previous compile-time test, to avoid penalizing non-debug builds.
        if (emit_run_time_checks_in_debug_mode_) {
          // Emit a run-time check verifying that the Marking Register is up-to-date.
          UseScratchRegisterScope temps(asm_.GetVIXLAssembler());
          vixl32::Register temp = temps.Acquire();
          // Ensure we are not clobbering a callee-save register that was restored before.
          DCHECK_EQ(core_spill_mask & (1 << temp.GetCode()), 0)
              << "core_spill_mask hould not contain scratch register R" << temp.GetCode();
          asm_.GenerateMarkingRegisterCheck(temp);
        }
      }
    }
  }

  // Return to LR.
  if ((core_spill_mask & (1u << pc.GetCode())) == 0u) {
    ___ Bx(vixl32::lr);
  }

  // The CFI should be restored for any code that follows the exit block.
  cfi().RestoreState();
  cfi().DefCFAOffset(frame_size);
}


void ArmVIXLJNIMacroAssembler::IncreaseFrameSize(size_t adjust) {
  if (adjust != 0u) {
    asm_.AddConstant(sp, -adjust);
    cfi().AdjustCFAOffset(adjust);
  }
}

void ArmVIXLJNIMacroAssembler::DecreaseFrameSize(size_t adjust) {
  if (adjust != 0u) {
    asm_.AddConstant(sp, adjust);
    cfi().AdjustCFAOffset(-adjust);
  }
}

ManagedRegister ArmVIXLJNIMacroAssembler::CoreRegisterWithSize(ManagedRegister src, size_t size) {
  DCHECK(src.AsArm().IsCoreRegister());
  DCHECK_EQ(size, 4u);
  return src;
}

void ArmVIXLJNIMacroAssembler::Store(FrameOffset dest, ManagedRegister m_src, size_t size) {
  Store(ArmManagedRegister::FromCoreRegister(SP), MemberOffset(dest.Int32Value()), m_src, size);
}

void ArmVIXLJNIMacroAssembler::Store(ManagedRegister m_base,
                                     MemberOffset offs,
                                     ManagedRegister m_src,
                                     size_t size) {
  ArmManagedRegister base = m_base.AsArm();
  ArmManagedRegister src = m_src.AsArm();
  if (src.IsNoRegister()) {
    CHECK_EQ(0u, size);
  } else if (src.IsCoreRegister()) {
    CHECK_EQ(4u, size);
    UseScratchRegisterScope temps(asm_.GetVIXLAssembler());
    temps.Exclude(AsVIXLRegister(src));
    asm_.StoreToOffset(kStoreWord, AsVIXLRegister(src), AsVIXLRegister(base), offs.Int32Value());
  } else if (src.IsRegisterPair()) {
    CHECK_EQ(8u, size);
    ___ Strd(AsVIXLRegisterPairLow(src),
             AsVIXLRegisterPairHigh(src),
             MemOperand(AsVIXLRegister(base), offs.Int32Value()));
  } else if (src.IsSRegister()) {
    CHECK_EQ(4u, size);
    asm_.StoreSToOffset(AsVIXLSRegister(src), AsVIXLRegister(base), offs.Int32Value());
  } else {
    CHECK_EQ(8u, size);
    CHECK(src.IsDRegister()) << src;
    asm_.StoreDToOffset(AsVIXLDRegister(src), AsVIXLRegister(base), offs.Int32Value());
  }
}

void ArmVIXLJNIMacroAssembler::StoreRawPtr(FrameOffset dest, ManagedRegister msrc) {
  vixl::aarch32::Register src = AsVIXLRegister(msrc.AsArm());
  UseScratchRegisterScope temps(asm_.GetVIXLAssembler());
  temps.Exclude(src);
  asm_.StoreToOffset(kStoreWord, src, sp, dest.Int32Value());
}

void ArmVIXLJNIMacroAssembler::Load(ManagedRegister m_dst, FrameOffset src, size_t size) {
  return Load(m_dst.AsArm(), sp, src.Int32Value(), size);
}

void ArmVIXLJNIMacroAssembler::Load(ManagedRegister m_dst,
                                    ManagedRegister m_base,
                                    MemberOffset offs,
                                    size_t size) {
  return Load(m_dst.AsArm(), AsVIXLRegister(m_base.AsArm()), offs.Int32Value(), size);
}


void ArmVIXLJNIMacroAssembler::LoadRawPtrFromThread(ManagedRegister mdest, ThreadOffset32 offs) {
  vixl::aarch32::Register dest = AsVIXLRegister(mdest.AsArm());
  UseScratchRegisterScope temps(asm_.GetVIXLAssembler());
  temps.Exclude(dest);
  asm_.LoadFromOffset(kLoadWord, dest, tr, offs.Int32Value());
}

void ArmVIXLJNIMacroAssembler::StoreStackPointerToThread(ThreadOffset32 thr_offs, bool tag_sp) {
  if (tag_sp) {
    UseScratchRegisterScope temps(asm_.GetVIXLAssembler());
    vixl32::Register reg = temps.Acquire();
    ___ Orr(reg, sp, 0x2);
    asm_.StoreToOffset(kStoreWord, reg, tr, thr_offs.Int32Value());
  } else {
    asm_.StoreToOffset(kStoreWord, sp, tr, thr_offs.Int32Value());
  }
}

void ArmVIXLJNIMacroAssembler::SignExtend([[maybe_unused]] ManagedRegister mreg,
                                          [[maybe_unused]] size_t size) {
  UNIMPLEMENTED(FATAL) << "no sign extension necessary for arm";
}

void ArmVIXLJNIMacroAssembler::ZeroExtend([[maybe_unused]] ManagedRegister mreg,
                                          [[maybe_unused]] size_t size) {
  UNIMPLEMENTED(FATAL) << "no zero extension necessary for arm";
}

static inline bool IsCoreRegisterOrPair(ArmManagedRegister reg) {
  return reg.IsCoreRegister() || reg.IsRegisterPair();
}

static inline bool NoSpillGap(const ArgumentLocation& loc1, const ArgumentLocation& loc2) {
  DCHECK(!loc1.IsRegister());
  DCHECK(!loc2.IsRegister());
  uint32_t loc1_offset = loc1.GetFrameOffset().Uint32Value();
  uint32_t loc2_offset = loc2.GetFrameOffset().Uint32Value();
  return loc1_offset + loc1.GetSize() == loc2_offset;
}

static inline uint32_t GetSRegisterNumber(ArmManagedRegister reg) {
  if (reg.IsSRegister()) {
    return static_cast<uint32_t>(reg.AsSRegister());
  } else {
    DCHECK(reg.IsDRegister());
    return 2u * static_cast<uint32_t>(reg.AsDRegister());
  }
}

// Get the number of locations to spill together.
static inline size_t GetSpillChunkSize(ArrayRef<ArgumentLocation> dests,
                                       ArrayRef<ArgumentLocation> srcs,
                                       size_t start) {
  DCHECK_LT(start, dests.size());
  DCHECK_ALIGNED(dests[start].GetFrameOffset().Uint32Value(), 4u);
  const ArgumentLocation& first_src = srcs[start];
  DCHECK(first_src.IsRegister());
  ArmManagedRegister first_src_reg = first_src.GetRegister().AsArm();
  size_t end = start + 1u;
  if (IsCoreRegisterOrPair(first_src_reg)) {
    while (end != dests.size() &&
           NoSpillGap(dests[end - 1u], dests[end]) &&
           srcs[end].IsRegister() &&
           IsCoreRegisterOrPair(srcs[end].GetRegister().AsArm())) {
      ++end;
    }
  } else {
    DCHECK(first_src_reg.IsSRegister() || first_src_reg.IsDRegister());
    uint32_t next_sreg = GetSRegisterNumber(first_src_reg) + first_src.GetSize() / kSRegSizeInBytes;
    while (end != dests.size() &&
           NoSpillGap(dests[end - 1u], dests[end]) &&
           srcs[end].IsRegister() &&
           !IsCoreRegisterOrPair(srcs[end].GetRegister().AsArm()) &&
           GetSRegisterNumber(srcs[end].GetRegister().AsArm()) == next_sreg) {
      next_sreg += srcs[end].GetSize() / kSRegSizeInBytes;
      ++end;
    }
  }
  return end - start;
}

static inline uint32_t GetCoreRegisterMask(ArmManagedRegister reg) {
  if (reg.IsCoreRegister()) {
    return 1u << static_cast<size_t>(reg.AsCoreRegister());
  } else {
    DCHECK(reg.IsRegisterPair());
    DCHECK_LT(reg.AsRegisterPairLow(), reg.AsRegisterPairHigh());
    return (1u << static_cast<size_t>(reg.AsRegisterPairLow())) |
           (1u << static_cast<size_t>(reg.AsRegisterPairHigh()));
  }
}

static inline uint32_t GetCoreRegisterMask(ArrayRef<ArgumentLocation> srcs) {
  uint32_t mask = 0u;
  for (const ArgumentLocation& loc : srcs) {
    DCHECK(loc.IsRegister());
    mask |= GetCoreRegisterMask(loc.GetRegister().AsArm());
  }
  return mask;
}

static inline bool UseStrdForChunk(ArrayRef<ArgumentLocation> srcs, size_t start, size_t length) {
  DCHECK_GE(length, 2u);
  DCHECK(srcs[start].IsRegister());
  DCHECK(srcs[start + 1u].IsRegister());
  // The destination may not be 8B aligned (but it is 4B aligned).
  // Allow arbitrary destination offset, macro assembler will use a temp if needed.
  // Note: T32 allows unrelated registers in STRD. (A32 does not.)
  return length == 2u &&
         srcs[start].GetRegister().AsArm().IsCoreRegister() &&
         srcs[start + 1u].GetRegister().AsArm().IsCoreRegister();
}

static inline bool UseVstrForChunk(ArrayRef<ArgumentLocation> srcs, size_t start, size_t length) {
  DCHECK_GE(length, 2u);
  DCHECK(srcs[start].IsRegister());
  DCHECK(srcs[start + 1u].IsRegister());
  // The destination may not be 8B aligned (but it is 4B aligned).
  // Allow arbitrary destination offset, macro assembler will use a temp if needed.
  return length == 2u &&
         srcs[start].GetRegister().AsArm().IsSRegister() &&
         srcs[start + 1u].GetRegister().AsArm().IsSRegister() &&
         IsAligned<2u>(static_cast<size_t>(srcs[start].GetRegister().AsArm().AsSRegister()));
}

void ArmVIXLJNIMacroAssembler::MoveArguments(ArrayRef<ArgumentLocation> dests,
                                             ArrayRef<ArgumentLocation> srcs,
                                             ArrayRef<FrameOffset> refs) {
  size_t arg_count = dests.size();
  DCHECK_EQ(arg_count, srcs.size());
  DCHECK_EQ(arg_count, refs.size());

  // Convert reference registers to `jobject` values.
  // TODO: Delay this for references that are copied to another register.
  for (size_t i = 0; i != arg_count; ++i) {
    if (refs[i] != kInvalidReferenceOffset && srcs[i].IsRegister()) {
      // Note: We can clobber `srcs[i]` here as the register cannot hold more than one argument.
      ManagedRegister src_i_reg = srcs[i].GetRegister();
      CreateJObject(src_i_reg, refs[i], src_i_reg, /*null_allowed=*/ i != 0u);
    }
  }

  // Native ABI is soft-float, so all destinations should be core registers or stack offsets.
  // And register locations should be first, followed by stack locations.
  auto is_register = [](const ArgumentLocation& loc) { return loc.IsRegister(); };
  DCHECK(std::is_partitioned(dests.begin(), dests.end(), is_register));
  size_t num_reg_dests =
      std::distance(dests.begin(), std::partition_point(dests.begin(), dests.end(), is_register));

  // Collect registers to move. No need to record FP regs as destinations are only core regs.
  uint32_t src_regs = 0u;
  uint32_t dest_regs = 0u;
  uint32_t same_regs = 0u;
  for (size_t i = 0; i != num_reg_dests; ++i) {
    const ArgumentLocation& src = srcs[i];
    const ArgumentLocation& dest = dests[i];
    DCHECK(dest.IsRegister() && IsCoreRegisterOrPair(dest.GetRegister().AsArm()));
    if (src.IsRegister() && IsCoreRegisterOrPair(src.GetRegister().AsArm())) {
      if (src.GetRegister().Equals(dest.GetRegister())) {
        same_regs |= GetCoreRegisterMask(src.GetRegister().AsArm());
        continue;
      }
      src_regs |= GetCoreRegisterMask(src.GetRegister().AsArm());
    }
    dest_regs |= GetCoreRegisterMask(dest.GetRegister().AsArm());
  }

  // Spill register arguments to stack slots.
  for (size_t i = num_reg_dests; i != arg_count; ) {
    const ArgumentLocation& src = srcs[i];
    if (!src.IsRegister()) {
      ++i;
      continue;
    }
    const ArgumentLocation& dest = dests[i];
    DCHECK_EQ(src.GetSize(), dest.GetSize());  // Even for references.
    DCHECK(!dest.IsRegister());
    uint32_t frame_offset = dest.GetFrameOffset().Uint32Value();
    size_t chunk_size = GetSpillChunkSize(dests, srcs, i);
    DCHECK_NE(chunk_size, 0u);
    if (chunk_size == 1u) {
      Store(dest.GetFrameOffset(), src.GetRegister(), dest.GetSize());
    } else if (UseStrdForChunk(srcs, i, chunk_size)) {
      ___ Strd(AsVIXLRegister(srcs[i].GetRegister().AsArm()),
               AsVIXLRegister(srcs[i + 1u].GetRegister().AsArm()),
               MemOperand(sp, frame_offset));
    } else if (UseVstrForChunk(srcs, i, chunk_size)) {
      size_t sreg = GetSRegisterNumber(src.GetRegister().AsArm());
      DCHECK_ALIGNED(sreg, 2u);
      ___ Vstr(vixl32::DRegister(sreg / 2u), MemOperand(sp, frame_offset));
    } else {
      UseScratchRegisterScope temps2(asm_.GetVIXLAssembler());
      vixl32::Register base_reg;
      if (frame_offset == 0u) {
        base_reg = sp;
      } else {
        base_reg = temps2.Acquire();
        ___ Add(base_reg, sp, frame_offset);
      }

      ArmManagedRegister src_reg = src.GetRegister().AsArm();
      if (IsCoreRegisterOrPair(src_reg)) {
        uint32_t core_reg_mask = GetCoreRegisterMask(srcs.SubArray(i, chunk_size));
        ___ Stm(base_reg, NO_WRITE_BACK, RegisterList(core_reg_mask));
      } else {
        uint32_t start_sreg = GetSRegisterNumber(src_reg);
        const ArgumentLocation& last_dest = dests[i + chunk_size - 1u];
        uint32_t total_size =
            last_dest.GetFrameOffset().Uint32Value() + last_dest.GetSize() - frame_offset;
        if (IsAligned<2u>(start_sreg) &&
            IsAligned<kDRegSizeInBytes>(frame_offset) &&
            IsAligned<kDRegSizeInBytes>(total_size)) {
          uint32_t dreg_count = total_size / kDRegSizeInBytes;
          DRegisterList dreg_list(vixl32::DRegister(start_sreg / 2u), dreg_count);
          ___ Vstm(F64, base_reg, NO_WRITE_BACK, dreg_list);
        } else {
          uint32_t sreg_count = total_size / kSRegSizeInBytes;
          SRegisterList sreg_list(vixl32::SRegister(start_sreg), sreg_count);
          ___ Vstm(F32, base_reg, NO_WRITE_BACK, sreg_list);
        }
      }
    }
    i += chunk_size;
  }

  // Copy incoming stack arguments to outgoing stack arguments.
  // Registers r0-r3 are argument registers for both managed and native ABI and r4
  // is a scratch register in managed ABI but also a hidden argument register for
  // @CriticalNative call. We can use these registers as temporaries for copying
  // stack arguments as long as they do not currently hold live values.
  // TODO: Use the callee-save scratch registers instead to avoid using calling
  // convention knowledge in the assembler. This would require reordering the
  // argument move with pushing the IRT frame where those registers are used.
  uint32_t copy_temp_regs = ((1u << 5) - 1u) & ~(same_regs | src_regs);
  if ((dest_regs & (1u << R4)) != 0) {
    // For @CriticalNative, R4 shall hold the hidden argument but it is available
    // for use as a temporary at this point. However, it may be the only available
    // register, so we shall use IP as the second temporary if needed.
    // We do not need to worry about `CreateJObject` for @CriticalNative.
    DCHECK_NE(copy_temp_regs, 0u);
    DCHECK(std::all_of(refs.begin(),
                       refs.end(),
                       [](FrameOffset r) { return r == kInvalidReferenceOffset; }));
  } else {
    // For normal native and @FastNative, R4 and at least one of R0-R3 should be
    // available because there are only 3 destination registers R1-R3 where the
    // source registers can be moved. The R0 shall be filled by the `JNIEnv*`
    // argument later. We need to keep IP available for `CreateJObject()`.
    DCHECK_GE(POPCOUNT(copy_temp_regs), 2);
  }
  vixl32::Register copy_temp1 = vixl32::Register(LeastSignificantBit(copy_temp_regs));
  copy_temp_regs ^= 1u << copy_temp1.GetCode();
  vixl32::Register copy_xtemp = (copy_temp_regs != 0u)
      ? vixl32::Register(LeastSignificantBit(copy_temp_regs))
      : vixl32::Register();
  for (size_t i = num_reg_dests; i != arg_count; ++i) {
    if (srcs[i].IsRegister()) {
      continue;
    }
    FrameOffset src_offset = srcs[i].GetFrameOffset();
    DCHECK_ALIGNED(src_offset.Uint32Value(), 4u);
    FrameOffset dest_offset = dests[i].GetFrameOffset();
    DCHECK_ALIGNED(dest_offset.Uint32Value(), 4u);
    // Look for opportunities to move 2 words at a time with LDRD/STRD
    // when the source types are word-sized.
    if (srcs[i].GetSize() == 4u &&
        i + 1u != arg_count &&
        !srcs[i + 1u].IsRegister() &&
        srcs[i + 1u].GetSize() == 4u &&
        NoSpillGap(srcs[i], srcs[i + 1u]) &&
        NoSpillGap(dests[i], dests[i + 1u]) &&
        dest_offset.Uint32Value() < kStrdOffsetCutoff) {
      UseScratchRegisterScope temps(asm_.GetVIXLAssembler());
      vixl32::Register copy_temp2 = copy_xtemp.IsValid() ? copy_xtemp : temps.Acquire();
      ___ Ldrd(copy_temp1, copy_temp2, MemOperand(sp, src_offset.Uint32Value()));
      if (refs[i] != kInvalidReferenceOffset) {
        ArmManagedRegister m_copy_temp1 = ArmManagedRegister::FromCoreRegister(
            enum_cast<Register>(copy_temp1.GetCode()));
        CreateJObject(m_copy_temp1, refs[i], m_copy_temp1, /*null_allowed=*/ i != 0u);
      }
      if (refs[i + 1u] != kInvalidReferenceOffset) {
        ArmManagedRegister m_copy_temp2 = ArmManagedRegister::FromCoreRegister(
            enum_cast<Register>(copy_temp2.GetCode()));
        CreateJObject(m_copy_temp2, refs[i + 1u], m_copy_temp2, /*null_allowed=*/ true);
      }
      ___ Strd(copy_temp1, copy_temp2, MemOperand(sp, dest_offset.Uint32Value()));
      ++i;
    } else if (dests[i].GetSize() == 8u && dest_offset.Uint32Value() < kStrdOffsetCutoff) {
      UseScratchRegisterScope temps(asm_.GetVIXLAssembler());
      vixl32::Register copy_temp2 = copy_xtemp.IsValid() ? copy_xtemp : temps.Acquire();
      ___ Ldrd(copy_temp1, copy_temp2, MemOperand(sp, src_offset.Uint32Value()));
      ___ Strd(copy_temp1, copy_temp2, MemOperand(sp, dest_offset.Uint32Value()));
    } else if (refs[i] != kInvalidReferenceOffset) {
      // Do not use the `CreateJObject()` overload for stack target as it generates
      // worse code than explicitly using a low register temporary.
      ___ Ldr(copy_temp1, MemOperand(sp, src_offset.Uint32Value()));
      ArmManagedRegister m_copy_temp1 = ArmManagedRegister::FromCoreRegister(
          enum_cast<Register>(copy_temp1.GetCode()));
      CreateJObject(m_copy_temp1, refs[i], m_copy_temp1, /*null_allowed=*/ i != 0u);
      ___ Str(copy_temp1, MemOperand(sp, dest_offset.Uint32Value()));
    } else {
      Copy(dest_offset, src_offset, dests[i].GetSize());
    }
  }

  // Fill destination registers from source core registers.
  // There should be no cycles, so this algorithm should make progress.
  while (src_regs != 0u) {
    uint32_t old_src_regs = src_regs;
    for (size_t i = 0; i != num_reg_dests; ++i) {
      DCHECK(dests[i].IsRegister() && IsCoreRegisterOrPair(dests[i].GetRegister().AsArm()));
      if (!srcs[i].IsRegister() || !IsCoreRegisterOrPair(srcs[i].GetRegister().AsArm())) {
        continue;
      }
      uint32_t dest_reg_mask = GetCoreRegisterMask(dests[i].GetRegister().AsArm());
      if ((dest_reg_mask & dest_regs) == 0u) {
        continue;  // Equals source, or already filled in one of previous iterations.
      }
      // There are no partial overlaps of 8-byte arguments, otherwise we would have to
      // tweak this check; Move() can deal with partial overlap for historical reasons.
      if ((dest_reg_mask & src_regs) != 0u) {
        continue;  // Cannot clobber this register yet.
      }
      Move(dests[i].GetRegister(), srcs[i].GetRegister(), dests[i].GetSize());
      uint32_t src_reg_mask = GetCoreRegisterMask(srcs[i].GetRegister().AsArm());
      DCHECK_EQ(src_regs & src_reg_mask, src_reg_mask);
      src_regs &= ~src_reg_mask;  // Allow clobbering the source register or pair.
      dest_regs &= ~dest_reg_mask;  // Destination register or pair was filled.
    }
    CHECK_NE(old_src_regs, src_regs);
    DCHECK_EQ(0u, src_regs & ~old_src_regs);
  }

  // Now fill destination registers from FP registers or stack slots, looking for
  // opportunities to use LDRD/VMOV to fill 2 registers with one instruction.
  for (size_t i = 0, j; i != num_reg_dests; i = j) {
    j = i + 1u;
    DCHECK(dests[i].IsRegister());
    ArmManagedRegister dest_reg = dests[i].GetRegister().AsArm();
    DCHECK(IsCoreRegisterOrPair(dest_reg));
    if (srcs[i].IsRegister() && IsCoreRegisterOrPair(srcs[i].GetRegister().AsArm())) {
      DCHECK_EQ(GetCoreRegisterMask(dests[i].GetRegister().AsArm()) & dest_regs, 0u);
      continue;  // Equals destination or moved above.
    }
    DCHECK_NE(GetCoreRegisterMask(dest_reg) & dest_regs, 0u);
    if (dests[i].GetSize() == 4u) {
      // Find next register to load.
      while (j != num_reg_dests &&
             (srcs[j].IsRegister() && IsCoreRegisterOrPair(srcs[j].GetRegister().AsArm()))) {
        DCHECK_EQ(GetCoreRegisterMask(dests[j].GetRegister().AsArm()) & dest_regs, 0u);
        ++j;  // Equals destination or moved above.
      }
      if (j != num_reg_dests && dests[j].GetSize() == 4u) {
        if (!srcs[i].IsRegister() && !srcs[j].IsRegister() && NoSpillGap(srcs[i], srcs[j])) {
          ___ Ldrd(AsVIXLRegister(dests[i].GetRegister().AsArm()),
                   AsVIXLRegister(dests[j].GetRegister().AsArm()),
                   MemOperand(sp, srcs[i].GetFrameOffset().Uint32Value()));
          if (refs[i] != kInvalidReferenceOffset) {
            DCHECK_EQ(refs[i], srcs[i].GetFrameOffset());
            CreateJObject(dest_reg, refs[i], dest_reg, /*null_allowed=*/ i != 0u);
          }
          if (refs[j] != kInvalidReferenceOffset) {
            DCHECK_EQ(refs[j], srcs[j].GetFrameOffset());
            ManagedRegister dest_j_reg = dests[j].GetRegister();
            CreateJObject(dest_j_reg, refs[j], dest_j_reg, /*null_allowed=*/ true);
          }
          ++j;
          continue;
        }
        if (srcs[i].IsRegister() && srcs[j].IsRegister()) {
          uint32_t first_sreg = GetSRegisterNumber(srcs[i].GetRegister().AsArm());
          if (IsAligned<2u>(first_sreg) &&
              first_sreg + 1u == GetSRegisterNumber(srcs[j].GetRegister().AsArm())) {
            ___ Vmov(AsVIXLRegister(dest_reg),
                     AsVIXLRegister(dests[j].GetRegister().AsArm()),
                     vixl32::DRegister(first_sreg / 2u));
            ++j;
            continue;
          }
        }
      }
    }
    if (srcs[i].IsRegister()) {
      Move(dests[i].GetRegister(), srcs[i].GetRegister(), dests[i].GetSize());
    } else if (refs[i] != kInvalidReferenceOffset) {
      CreateJObject(dest_reg, refs[i], ManagedRegister::NoRegister(), /*null_allowed=*/ i != 0u);
    } else {
      Load(dest_reg, srcs[i].GetFrameOffset(), dests[i].GetSize());
    }
  }
}

void ArmVIXLJNIMacroAssembler::Move(ManagedRegister mdst,
                                    ManagedRegister msrc,
                                    [[maybe_unused]] size_t size) {
  ArmManagedRegister dst = mdst.AsArm();
  if (kIsDebugBuild) {
    // Check that the destination is not a scratch register.
    UseScratchRegisterScope temps(asm_.GetVIXLAssembler());
    if (dst.IsCoreRegister()) {
      CHECK(!temps.IsAvailable(AsVIXLRegister(dst)));
    } else if (dst.IsDRegister()) {
      CHECK(!temps.IsAvailable(AsVIXLDRegister(dst)));
    } else if (dst.IsSRegister()) {
      CHECK(!temps.IsAvailable(AsVIXLSRegister(dst)));
    } else {
      CHECK(dst.IsRegisterPair()) << dst;
      CHECK(!temps.IsAvailable(AsVIXLRegisterPairLow(dst)));
      CHECK(!temps.IsAvailable(AsVIXLRegisterPairHigh(dst)));
    }
  }
  ArmManagedRegister src = msrc.AsArm();
  if (!dst.Equals(src)) {
    if (dst.IsCoreRegister()) {
      if (src.IsCoreRegister()) {
        ___ Mov(AsVIXLRegister(dst), AsVIXLRegister(src));
      } else {
        CHECK(src.IsSRegister()) << src;
        ___ Vmov(AsVIXLRegister(dst), AsVIXLSRegister(src));
      }
    } else if (dst.IsDRegister()) {
      if (src.IsDRegister()) {
        ___ Vmov(F64, AsVIXLDRegister(dst), AsVIXLDRegister(src));
      } else {
        // VMOV Dn, Rlo, Rhi (Dn = {Rlo, Rhi})
        CHECK(src.IsRegisterPair()) << src;
        ___ Vmov(AsVIXLDRegister(dst), AsVIXLRegisterPairLow(src), AsVIXLRegisterPairHigh(src));
      }
    } else if (dst.IsSRegister()) {
      if (src.IsSRegister()) {
        ___ Vmov(F32, AsVIXLSRegister(dst), AsVIXLSRegister(src));
      } else {
        // VMOV Sn, Rn  (Sn = Rn)
        CHECK(src.IsCoreRegister()) << src;
        ___ Vmov(AsVIXLSRegister(dst), AsVIXLRegister(src));
      }
    } else {
      CHECK(dst.IsRegisterPair()) << dst;
      if (src.IsRegisterPair()) {
        // Ensure that the first move doesn't clobber the input of the second.
        if (src.AsRegisterPairHigh() != dst.AsRegisterPairLow()) {
          ___ Mov(AsVIXLRegisterPairLow(dst),  AsVIXLRegisterPairLow(src));
          ___ Mov(AsVIXLRegisterPairHigh(dst), AsVIXLRegisterPairHigh(src));
        } else {
          ___ Mov(AsVIXLRegisterPairHigh(dst), AsVIXLRegisterPairHigh(src));
          ___ Mov(AsVIXLRegisterPairLow(dst),  AsVIXLRegisterPairLow(src));
        }
      } else {
        CHECK(src.IsDRegister()) << src;
        ___ Vmov(AsVIXLRegisterPairLow(dst), AsVIXLRegisterPairHigh(dst), AsVIXLDRegister(src));
      }
    }
  }
}

void ArmVIXLJNIMacroAssembler::Move(ManagedRegister mdst, size_t value) {
  ArmManagedRegister dst = mdst.AsArm();
  ___ Mov(AsVIXLRegister(dst), static_cast<uint32_t>(value));
}

void ArmVIXLJNIMacroAssembler::Copy(FrameOffset dest, FrameOffset src, size_t size) {
  DCHECK(size == 4 || size == 8) << size;
  UseScratchRegisterScope temps(asm_.GetVIXLAssembler());
  vixl32::Register scratch = temps.Acquire();
  if (size == 4) {
    asm_.LoadFromOffset(kLoadWord, scratch, sp, src.Int32Value());
    asm_.StoreToOffset(kStoreWord, scratch, sp, dest.Int32Value());
  } else if (size == 8) {
    asm_.LoadFromOffset(kLoadWord, scratch, sp, src.Int32Value());
    asm_.StoreToOffset(kStoreWord, scratch, sp, dest.Int32Value());
    asm_.LoadFromOffset(kLoadWord, scratch, sp, src.Int32Value() + 4);
    asm_.StoreToOffset(kStoreWord, scratch, sp, dest.Int32Value() + 4);
  }
}

void ArmVIXLJNIMacroAssembler::CreateJObject(ManagedRegister mout_reg,
                                             FrameOffset spilled_reference_offset,
                                             ManagedRegister min_reg,
                                             bool null_allowed) {
  vixl::aarch32::Register out_reg = AsVIXLRegister(mout_reg.AsArm());
  vixl::aarch32::Register in_reg =
      min_reg.AsArm().IsNoRegister() ? vixl::aarch32::Register() : AsVIXLRegister(min_reg.AsArm());
  UseScratchRegisterScope temps(asm_.GetVIXLAssembler());
  temps.Exclude(out_reg);
  if (null_allowed) {
    // Null values get a jobject value null. Otherwise, the jobject is
    // the address of the spilled reference.
    // e.g. out_reg = (handle == 0) ? 0 : (SP+spilled_reference_offset)
    if (!in_reg.IsValid()) {
      asm_.LoadFromOffset(kLoadWord, out_reg, sp, spilled_reference_offset.Int32Value());
      in_reg = out_reg;
    }

    if (out_reg.IsLow() && spilled_reference_offset.Uint32Value() < kAddSpImmCutoff) {
      // There is a 16-bit "ADD Rd, SP, <imm>" instruction we can use in IT-block.
      if (out_reg.Is(in_reg)) {
        ___ Cmp(in_reg, 0);
      } else {
        ___ Movs(out_reg, in_reg);
      }
      ExactAssemblyScope guard(asm_.GetVIXLAssembler(),
                               2 * vixl32::k16BitT32InstructionSizeInBytes);
      ___ it(ne);
      ___ add(ne, Narrow, out_reg, sp, spilled_reference_offset.Int32Value());
    } else {
      vixl32::Register addr_reg = out_reg.Is(in_reg) ? temps.Acquire() : out_reg;
      vixl32::Register cond_mov_src_reg = out_reg.Is(in_reg) ? addr_reg : in_reg;
      vixl32::Condition cond = out_reg.Is(in_reg) ? ne : eq;
      ___ Add(addr_reg, sp, spilled_reference_offset.Int32Value());
      ___ Cmp(in_reg, 0);
      ExactAssemblyScope guard(asm_.GetVIXLAssembler(),
                               2 * vixl32::k16BitT32InstructionSizeInBytes);
      ___ it(cond);
      ___ mov(cond, Narrow, out_reg, cond_mov_src_reg);
    }
  } else {
    asm_.AddConstant(out_reg, sp, spilled_reference_offset.Int32Value());
  }
}

void ArmVIXLJNIMacroAssembler::DecodeJNITransitionOrLocalJObject(ManagedRegister mreg,
                                                                 JNIMacroLabel* slow_path,
                                                                 JNIMacroLabel* resume) {
  constexpr uint32_t kGlobalOrWeakGlobalMask =
      dchecked_integral_cast<uint32_t>(IndirectReferenceTable::GetGlobalOrWeakGlobalMask());
  constexpr uint32_t kIndirectRefKindMask =
      dchecked_integral_cast<uint32_t>(IndirectReferenceTable::GetIndirectRefKindMask());
  vixl32::Register reg = AsVIXLRegister(mreg.AsArm());
  ___ Tst(reg, kGlobalOrWeakGlobalMask);
  ___ B(ne, ArmVIXLJNIMacroLabel::Cast(slow_path)->AsArm());
  ___ Bics(reg, reg, kIndirectRefKindMask);
  ___ B(eq, ArmVIXLJNIMacroLabel::Cast(resume)->AsArm());  // Skip load for null.
  ___ Ldr(reg, MemOperand(reg));
}

void ArmVIXLJNIMacroAssembler::VerifyObject([[maybe_unused]] ManagedRegister src,
                                            [[maybe_unused]] bool could_be_null) {
  // TODO: not validating references.
}

void ArmVIXLJNIMacroAssembler::VerifyObject([[maybe_unused]] FrameOffset src,
                                            [[maybe_unused]] bool could_be_null) {
  // TODO: not validating references.
}

void ArmVIXLJNIMacroAssembler::Jump(ManagedRegister mbase, Offset offset) {
  vixl::aarch32::Register base = AsVIXLRegister(mbase.AsArm());
  UseScratchRegisterScope temps(asm_.GetVIXLAssembler());
  vixl32::Register scratch = temps.Acquire();
  asm_.LoadFromOffset(kLoadWord, scratch, base, offset.Int32Value());
  ___ Bx(scratch);
}

void ArmVIXLJNIMacroAssembler::Call(ManagedRegister mbase, Offset offset) {
  vixl::aarch32::Register base = AsVIXLRegister(mbase.AsArm());
  asm_.LoadFromOffset(kLoadWord, lr, base, offset.Int32Value());
  ___ Blx(lr);
  // TODO: place reference map on call.
}

void ArmVIXLJNIMacroAssembler::CallFromThread(ThreadOffset32 offset) {
  // Call *(TR + offset)
  asm_.LoadFromOffset(kLoadWord, lr, tr, offset.Int32Value());
  ___ Blx(lr);
  // TODO: place reference map on call
}

void ArmVIXLJNIMacroAssembler::GetCurrentThread(ManagedRegister dest) {
  UseScratchRegisterScope temps(asm_.GetVIXLAssembler());
  temps.Exclude(AsVIXLRegister(dest.AsArm()));
  ___ Mov(AsVIXLRegister(dest.AsArm()), tr);
}

void ArmVIXLJNIMacroAssembler::GetCurrentThread(FrameOffset dest_offset) {
  asm_.StoreToOffset(kStoreWord, tr, sp, dest_offset.Int32Value());
}

void ArmVIXLJNIMacroAssembler::TryToTransitionFromRunnableToNative(
    JNIMacroLabel* label, ArrayRef<const ManagedRegister> scratch_regs) {
  constexpr uint32_t kNativeStateValue = Thread::StoredThreadStateValue(ThreadState::kNative);
  constexpr uint32_t kRunnableStateValue = Thread::StoredThreadStateValue(ThreadState::kRunnable);
  constexpr ThreadOffset32 thread_flags_offset = Thread::ThreadFlagsOffset<kArmPointerSize>();
  constexpr ThreadOffset32 thread_held_mutex_mutator_lock_offset =
      Thread::HeldMutexOffset<kArmPointerSize>(kMutatorLock);

  DCHECK_GE(scratch_regs.size(), 2u);
  vixl32::Register scratch = AsVIXLRegister(scratch_regs[0].AsArm());
  vixl32::Register scratch2 = AsVIXLRegister(scratch_regs[1].AsArm());

  // CAS release, old_value = kRunnableStateValue, new_value = kNativeStateValue, no flags.
  vixl32::Label retry;
  ___ Bind(&retry);
  ___ Ldrex(scratch, MemOperand(tr, thread_flags_offset.Int32Value()));
  ___ Mov(scratch2, kNativeStateValue);
  // If any flags are set, go to the slow path.
  ___ Cmp(scratch, kRunnableStateValue);
  ___ B(ne, ArmVIXLJNIMacroLabel::Cast(label)->AsArm());
  ___ Dmb(DmbOptions::ISH);  // Memory barrier "any-store" for the "release" operation.
  ___ Strex(scratch, scratch2, MemOperand(tr, thread_flags_offset.Int32Value()));
  ___ Cmp(scratch, 0);
  ___ B(ne, &retry);

  // Clear `self->tlsPtr_.held_mutexes[kMutatorLock]`; `scratch` holds 0 at this point.
  ___ Str(scratch, MemOperand(tr, thread_held_mutex_mutator_lock_offset.Int32Value()));
}

void ArmVIXLJNIMacroAssembler::TryToTransitionFromNativeToRunnable(
    JNIMacroLabel* label,
    ArrayRef<const ManagedRegister> scratch_regs,
    ManagedRegister return_reg) {
  constexpr uint32_t kNativeStateValue = Thread::StoredThreadStateValue(ThreadState::kNative);
  constexpr uint32_t kRunnableStateValue = Thread::StoredThreadStateValue(ThreadState::kRunnable);
  constexpr ThreadOffset32 thread_flags_offset = Thread::ThreadFlagsOffset<kArmPointerSize>();
  constexpr ThreadOffset32 thread_held_mutex_mutator_lock_offset =
      Thread::HeldMutexOffset<kArmPointerSize>(kMutatorLock);
  constexpr ThreadOffset32 thread_mutator_lock_offset =
      Thread::MutatorLockOffset<kArmPointerSize>();

  // There must be at least two scratch registers.
  DCHECK_GE(scratch_regs.size(), 2u);
  DCHECK(!scratch_regs[0].AsArm().Overlaps(return_reg.AsArm()));
  vixl32::Register scratch = AsVIXLRegister(scratch_regs[0].AsArm());
  DCHECK(!scratch_regs[1].AsArm().Overlaps(return_reg.AsArm()));
  vixl32::Register scratch2 = AsVIXLRegister(scratch_regs[1].AsArm());

  // CAS acquire, old_value = kNativeStateValue, new_value = kRunnableStateValue, no flags.
  vixl32::Label retry;
  ___ Bind(&retry);
  ___ Ldrex(scratch, MemOperand(tr, thread_flags_offset.Int32Value()));
  // If any flags are set, or the state is not Native, go to the slow path.
  // (While the thread can theoretically transition between different Suspended states,
  // it would be very unexpected to see a state other than Native at this point.)
  ___ Eors(scratch2, scratch, kNativeStateValue);
  ___ B(ne, ArmVIXLJNIMacroLabel::Cast(label)->AsArm());
  static_assert(kRunnableStateValue == 0u);
  ___ Strex(scratch, scratch2, MemOperand(tr, thread_flags_offset.Int32Value()));
  ___ Cmp(scratch, 0);
  ___ B(ne, &retry);
  ___ Dmb(DmbOptions::ISH);  // Memory barrier "load-any" for the "acquire" operation.

  // Set `self->tlsPtr_.held_mutexes[kMutatorLock]` to the mutator lock.
  ___ Ldr(scratch, MemOperand(tr, thread_mutator_lock_offset.Int32Value()));
  ___ Str(scratch, MemOperand(tr, thread_held_mutex_mutator_lock_offset.Int32Value()));
}

void ArmVIXLJNIMacroAssembler::SuspendCheck(JNIMacroLabel* label) {
  UseScratchRegisterScope temps(asm_.GetVIXLAssembler());
  vixl32::Register scratch = temps.Acquire();
  asm_.LoadFromOffset(kLoadWord,
                      scratch,
                      tr,
                      Thread::ThreadFlagsOffset<kArmPointerSize>().Int32Value());

  ___ Tst(scratch, Thread::SuspendOrCheckpointRequestFlags());
  ___ BPreferNear(ne, ArmVIXLJNIMacroLabel::Cast(label)->AsArm());
  // TODO: think about using CBNZ here.
}

void ArmVIXLJNIMacroAssembler::ExceptionPoll(JNIMacroLabel* label) {
  UseScratchRegisterScope temps(asm_.GetVIXLAssembler());
  vixl32::Register scratch = temps.Acquire();
  asm_.LoadFromOffset(kLoadWord,
                      scratch,
                      tr,
                      Thread::ExceptionOffset<kArmPointerSize>().Int32Value());

  ___ Cmp(scratch, 0);
  ___ BPreferNear(ne, ArmVIXLJNIMacroLabel::Cast(label)->AsArm());
  // TODO: think about using CBNZ here.
}

void ArmVIXLJNIMacroAssembler::DeliverPendingException() {
  // Pass exception object as argument.
  // Don't care about preserving r0 as this won't return.
  // Note: The scratch register from `ExceptionPoll()` may have been clobbered.
  asm_.LoadFromOffset(kLoadWord,
                      r0,
                      tr,
                      Thread::ExceptionOffset<kArmPointerSize>().Int32Value());
  ___ Ldr(lr,
          MemOperand(tr,
              QUICK_ENTRYPOINT_OFFSET(kArmPointerSize, pDeliverException).Int32Value()));
  ___ Blx(lr);
}

std::unique_ptr<JNIMacroLabel> ArmVIXLJNIMacroAssembler::CreateLabel() {
  return std::unique_ptr<JNIMacroLabel>(new (asm_.GetAllocator()) ArmVIXLJNIMacroLabel());
}

void ArmVIXLJNIMacroAssembler::Jump(JNIMacroLabel* label) {
  CHECK(label != nullptr);
  ___ B(ArmVIXLJNIMacroLabel::Cast(label)->AsArm());
}

void ArmVIXLJNIMacroAssembler::TestGcMarking(JNIMacroLabel* label, JNIMacroUnaryCondition cond) {
  CHECK(label != nullptr);

  UseScratchRegisterScope temps(asm_.GetVIXLAssembler());
  vixl32::Register test_reg;
  DCHECK_EQ(Thread::IsGcMarkingSize(), 4u);
  if (kUseBakerReadBarrier) {
    // TestGcMarking() is used in the JNI stub entry when the marking register is up to date.
    if (kIsDebugBuild && emit_run_time_checks_in_debug_mode_) {
      vixl32::Register temp = temps.Acquire();
      asm_.GenerateMarkingRegisterCheck(temp);
    }
    test_reg = mr;
  } else {
    test_reg = temps.Acquire();
    ___ Ldr(test_reg, MemOperand(tr, Thread::IsGcMarkingOffset<kArmPointerSize>().Int32Value()));
  }
  switch (cond) {
    case JNIMacroUnaryCondition::kZero:
      ___ CompareAndBranchIfZero(test_reg, ArmVIXLJNIMacroLabel::Cast(label)->AsArm());
      break;
    case JNIMacroUnaryCondition::kNotZero:
      ___ CompareAndBranchIfNonZero(test_reg, ArmVIXLJNIMacroLabel::Cast(label)->AsArm());
      break;
    default:
      LOG(FATAL) << "Not implemented unary condition: " << static_cast<int>(cond);
      UNREACHABLE();
  }
}

void ArmVIXLJNIMacroAssembler::TestMarkBit(ManagedRegister mref,
                                           JNIMacroLabel* label,
                                           JNIMacroUnaryCondition cond) {
  DCHECK(kUseBakerReadBarrier);
  vixl32::Register ref = AsVIXLRegister(mref.AsArm());
  UseScratchRegisterScope temps(asm_.GetVIXLAssembler());
  vixl32::Register scratch = temps.Acquire();
  ___ Ldr(scratch, MemOperand(ref, mirror::Object::MonitorOffset().SizeValue()));
  static_assert(LockWord::kMarkBitStateSize == 1u);
  ___ Tst(scratch, LockWord::kMarkBitStateMaskShifted);
  switch (cond) {
    case JNIMacroUnaryCondition::kZero:
      ___ B(eq, ArmVIXLJNIMacroLabel::Cast(label)->AsArm());
      break;
    case JNIMacroUnaryCondition::kNotZero:
      ___ B(ne, ArmVIXLJNIMacroLabel::Cast(label)->AsArm());
      break;
    default:
      LOG(FATAL) << "Not implemented unary condition: " << static_cast<int>(cond);
      UNREACHABLE();
  }
}

void ArmVIXLJNIMacroAssembler::TestByteAndJumpIfNotZero(uintptr_t address, JNIMacroLabel* label) {
  UseScratchRegisterScope temps(asm_.GetVIXLAssembler());
  vixl32::Register scratch = temps.Acquire();
  ___ Mov(scratch, static_cast<uint32_t>(address));
  ___ Ldrb(scratch, MemOperand(scratch, 0));
  ___ CompareAndBranchIfNonZero(scratch, ArmVIXLJNIMacroLabel::Cast(label)->AsArm());
}

void ArmVIXLJNIMacroAssembler::Bind(JNIMacroLabel* label) {
  CHECK(label != nullptr);
  ___ Bind(ArmVIXLJNIMacroLabel::Cast(label)->AsArm());
}

void ArmVIXLJNIMacroAssembler::Load(ArmManagedRegister dest,
                                    vixl32::Register base,
                                    int32_t offset,
                                    size_t size) {
  if (dest.IsNoRegister()) {
    CHECK_EQ(0u, size) << dest;
  } else if (dest.IsCoreRegister()) {
    vixl::aarch32::Register dst = AsVIXLRegister(dest);
    CHECK(!dst.Is(sp)) << dest;

    UseScratchRegisterScope temps(asm_.GetVIXLAssembler());
    temps.Exclude(dst);

    if (size == 1u) {
      ___ Ldrb(dst, MemOperand(base, offset));
    } else {
      CHECK_EQ(4u, size) << dest;
      ___ Ldr(dst, MemOperand(base, offset));
    }
  } else if (dest.IsRegisterPair()) {
    CHECK_EQ(8u, size) << dest;
    // TODO: Use LDRD to improve stubs for @CriticalNative methods with parameters
    // (long, long, ...). A single 32-bit LDRD is presumably faster than two 16-bit LDRs.
    ___ Ldr(AsVIXLRegisterPairLow(dest),  MemOperand(base, offset));
    ___ Ldr(AsVIXLRegisterPairHigh(dest), MemOperand(base, offset + 4));
  } else if (dest.IsSRegister()) {
    ___ Vldr(AsVIXLSRegister(dest), MemOperand(base, offset));
  } else {
    CHECK(dest.IsDRegister()) << dest;
    ___ Vldr(AsVIXLDRegister(dest), MemOperand(base, offset));
  }
}

void ArmVIXLJNIMacroAssembler::LoadLocalReferenceTableStates(ManagedRegister jni_env_reg,
                                                             ManagedRegister previous_state_reg,
                                                             ManagedRegister current_state_reg) {
  constexpr size_t kLRTSegmentStateSize = sizeof(jni::LRTSegmentState);
  DCHECK_EQ(kLRTSegmentStateSize, kRegSizeInBytes);
  const MemberOffset previous_state_offset = JNIEnvExt::LrtPreviousStateOffset(kArmPointerSize);
  const MemberOffset current_state_offset = JNIEnvExt::LrtSegmentStateOffset(kArmPointerSize);
  DCHECK_EQ(previous_state_offset.SizeValue() + kLRTSegmentStateSize,
            current_state_offset.SizeValue());

  ___ Ldrd(AsVIXLRegister(previous_state_reg.AsArm()),
           AsVIXLRegister(current_state_reg.AsArm()),
           MemOperand(AsVIXLRegister(jni_env_reg.AsArm()), previous_state_offset.Int32Value()));
}

void ArmVIXLJNIMacroAssembler::StoreLocalReferenceTableStates(ManagedRegister jni_env_reg,
                                                              ManagedRegister previous_state_reg,
                                                              ManagedRegister current_state_reg) {
  constexpr size_t kLRTSegmentStateSize = sizeof(jni::LRTSegmentState);
  DCHECK_EQ(kLRTSegmentStateSize, kRegSizeInBytes);
  const MemberOffset previous_state_offset = JNIEnvExt::LrtPreviousStateOffset(kArmPointerSize);
  const MemberOffset current_state_offset = JNIEnvExt::LrtSegmentStateOffset(kArmPointerSize);
  DCHECK_EQ(previous_state_offset.SizeValue() + kLRTSegmentStateSize,
            current_state_offset.SizeValue());

  ___ Strd(AsVIXLRegister(previous_state_reg.AsArm()),
           AsVIXLRegister(current_state_reg.AsArm()),
           MemOperand(AsVIXLRegister(jni_env_reg.AsArm()), previous_state_offset.Int32Value()));
}

}  // namespace arm
}  // namespace art
