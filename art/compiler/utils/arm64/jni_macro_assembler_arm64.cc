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

#include "jni_macro_assembler_arm64.h"

#include "entrypoints/quick/quick_entrypoints.h"
#include "indirect_reference_table.h"
#include "jni/jni_env_ext.h"
#include "jni/local_reference_table.h"
#include "lock_word.h"
#include "managed_register_arm64.h"
#include "offsets.h"
#include "thread.h"

using namespace vixl::aarch64;  // NOLINT(build/namespaces)

namespace art HIDDEN {
namespace arm64 {

#ifdef ___
#error "ARM64 Assembler macro already defined."
#else
#define ___   asm_.GetVIXLAssembler()->
#endif

#define reg_x(X) Arm64Assembler::reg_x(X)
#define reg_w(W) Arm64Assembler::reg_w(W)
#define reg_d(D) Arm64Assembler::reg_d(D)
#define reg_s(S) Arm64Assembler::reg_s(S)

// The AAPCS64 requires 16-byte alignment. This is the same as the Managed ABI stack alignment.
static constexpr size_t kAapcs64StackAlignment = 16u;
static_assert(kAapcs64StackAlignment == kStackAlignment);

// STP signed offset for W-register can encode any 4-byte aligned offset smaller than this cutoff.
static constexpr size_t kStpWOffsetCutoff = 256u;

// STP signed offset for X-register can encode any 8-byte aligned offset smaller than this cutoff.
static constexpr size_t kStpXOffsetCutoff = 512u;

// STP signed offset for S-register can encode any 4-byte aligned offset smaller than this cutoff.
static constexpr size_t kStpSOffsetCutoff = 256u;

// STP signed offset for D-register can encode any 8-byte aligned offset smaller than this cutoff.
static constexpr size_t kStpDOffsetCutoff = 512u;

Arm64JNIMacroAssembler::~Arm64JNIMacroAssembler() {
}

void Arm64JNIMacroAssembler::FinalizeCode() {
  ___ FinalizeCode();
}

void Arm64JNIMacroAssembler::GetCurrentThread(ManagedRegister dest) {
  ___ Mov(reg_x(dest.AsArm64().AsXRegister()), reg_x(TR));
}

void Arm64JNIMacroAssembler::GetCurrentThread(FrameOffset offset) {
  StoreToOffset(TR, SP, offset.Int32Value());
}

// See Arm64 PCS Section 5.2.2.1.
void Arm64JNIMacroAssembler::IncreaseFrameSize(size_t adjust) {
  if (adjust != 0u) {
    CHECK_ALIGNED(adjust, kStackAlignment);
    AddConstant(SP, -adjust);
    cfi().AdjustCFAOffset(adjust);
  }
}

// See Arm64 PCS Section 5.2.2.1.
void Arm64JNIMacroAssembler::DecreaseFrameSize(size_t adjust) {
  if (adjust != 0u) {
    CHECK_ALIGNED(adjust, kStackAlignment);
    AddConstant(SP, adjust);
    cfi().AdjustCFAOffset(-adjust);
  }
}

ManagedRegister Arm64JNIMacroAssembler::CoreRegisterWithSize(ManagedRegister m_src, size_t size) {
  DCHECK(size == 4u || size == 8u) << size;
  Arm64ManagedRegister src = m_src.AsArm64();
  // Switch between X and W registers using the `XRegister` and `WRegister` enumerations.
  static_assert(W0 == static_cast<std::underlying_type_t<XRegister>>(X0));
  static_assert(W30 == static_cast<std::underlying_type_t<XRegister>>(X30));
  static_assert(WSP == static_cast<std::underlying_type_t<XRegister>>(SP));
  static_assert(WZR == static_cast<std::underlying_type_t<XRegister>>(XZR));
  if (src.IsXRegister()) {
    if (size == 8u) {
      return m_src;
    }
    auto id = static_cast<std::underlying_type_t<XRegister>>(src.AsXRegister());
    return Arm64ManagedRegister::FromWRegister(enum_cast<WRegister>(id));
  } else {
    CHECK(src.IsWRegister());
    if (size == 4u) {
      return m_src;
    }
    auto id = static_cast<std::underlying_type_t<WRegister>>(src.AsWRegister());
    return Arm64ManagedRegister::FromXRegister(enum_cast<XRegister>(id));
  }
}

void Arm64JNIMacroAssembler::AddConstant(XRegister rd, int32_t value, Condition cond) {
  AddConstant(rd, rd, value, cond);
}

void Arm64JNIMacroAssembler::AddConstant(XRegister rd,
                                         XRegister rn,
                                         int32_t value,
                                         Condition cond) {
  if ((cond == al) || (cond == nv)) {
    // VIXL macro-assembler handles all variants.
    ___ Add(reg_x(rd), reg_x(rn), value);
  } else {
    // temp = rd + value
    // rd = cond ? temp : rn
    UseScratchRegisterScope temps(asm_.GetVIXLAssembler());
    temps.Exclude(reg_x(rd), reg_x(rn));
    Register temp = temps.AcquireX();
    ___ Add(temp, reg_x(rn), value);
    ___ Csel(reg_x(rd), temp, reg_x(rd), cond);
  }
}

void Arm64JNIMacroAssembler::StoreWToOffset(StoreOperandType type,
                                            WRegister source,
                                            XRegister base,
                                            int32_t offset) {
  switch (type) {
    case kStoreByte:
      ___ Strb(reg_w(source), MEM_OP(reg_x(base), offset));
      break;
    case kStoreHalfword:
      ___ Strh(reg_w(source), MEM_OP(reg_x(base), offset));
      break;
    case kStoreWord:
      ___ Str(reg_w(source), MEM_OP(reg_x(base), offset));
      break;
    default:
      LOG(FATAL) << "UNREACHABLE";
  }
}

void Arm64JNIMacroAssembler::StoreToOffset(XRegister source, XRegister base, int32_t offset) {
  CHECK_NE(source, SP);
  ___ Str(reg_x(source), MEM_OP(reg_x(base), offset));
}

void Arm64JNIMacroAssembler::StoreSToOffset(SRegister source, XRegister base, int32_t offset) {
  ___ Str(reg_s(source), MEM_OP(reg_x(base), offset));
}

void Arm64JNIMacroAssembler::StoreDToOffset(DRegister source, XRegister base, int32_t offset) {
  ___ Str(reg_d(source), MEM_OP(reg_x(base), offset));
}

void Arm64JNIMacroAssembler::Store(FrameOffset offs, ManagedRegister m_src, size_t size) {
  Store(Arm64ManagedRegister::FromXRegister(SP), MemberOffset(offs.Int32Value()), m_src, size);
}

void Arm64JNIMacroAssembler::Store(ManagedRegister m_base,
                                   MemberOffset offs,
                                   ManagedRegister m_src,
                                   size_t size) {
  Arm64ManagedRegister base = m_base.AsArm64();
  Arm64ManagedRegister src = m_src.AsArm64();
  if (src.IsNoRegister()) {
    CHECK_EQ(0u, size);
  } else if (src.IsWRegister()) {
    CHECK_EQ(4u, size);
    StoreWToOffset(kStoreWord, src.AsWRegister(), base.AsXRegister(), offs.Int32Value());
  } else if (src.IsXRegister()) {
    CHECK_EQ(8u, size);
    StoreToOffset(src.AsXRegister(), base.AsXRegister(), offs.Int32Value());
  } else if (src.IsSRegister()) {
    StoreSToOffset(src.AsSRegister(), base.AsXRegister(), offs.Int32Value());
  } else {
    CHECK(src.IsDRegister()) << src;
    StoreDToOffset(src.AsDRegister(), base.AsXRegister(), offs.Int32Value());
  }
}

void Arm64JNIMacroAssembler::StoreRawPtr(FrameOffset offs, ManagedRegister m_src) {
  Arm64ManagedRegister src = m_src.AsArm64();
  CHECK(src.IsXRegister()) << src;
  StoreToOffset(src.AsXRegister(), SP, offs.Int32Value());
}

void Arm64JNIMacroAssembler::StoreStackPointerToThread(ThreadOffset64 tr_offs, bool tag_sp) {
  UseScratchRegisterScope temps(asm_.GetVIXLAssembler());
  Register scratch = temps.AcquireX();
  ___ Mov(scratch, reg_x(SP));
  if (tag_sp) {
    ___ Orr(scratch, scratch, 0x2);
  }
  ___ Str(scratch, MEM_OP(reg_x(TR), tr_offs.Int32Value()));
}

// Load routines.
void Arm64JNIMacroAssembler::LoadImmediate(XRegister dest, int32_t value, Condition cond) {
  if ((cond == al) || (cond == nv)) {
    ___ Mov(reg_x(dest), value);
  } else {
    // temp = value
    // rd = cond ? temp : rd
    if (value != 0) {
      UseScratchRegisterScope temps(asm_.GetVIXLAssembler());
      temps.Exclude(reg_x(dest));
      Register temp = temps.AcquireX();
      ___ Mov(temp, value);
      ___ Csel(reg_x(dest), temp, reg_x(dest), cond);
    } else {
      ___ Csel(reg_x(dest), reg_x(XZR), reg_x(dest), cond);
    }
  }
}

void Arm64JNIMacroAssembler::LoadWFromOffset(LoadOperandType type,
                                             WRegister dest,
                                             XRegister base,
                                             int32_t offset) {
  switch (type) {
    case kLoadSignedByte:
      ___ Ldrsb(reg_w(dest), MEM_OP(reg_x(base), offset));
      break;
    case kLoadSignedHalfword:
      ___ Ldrsh(reg_w(dest), MEM_OP(reg_x(base), offset));
      break;
    case kLoadUnsignedByte:
      ___ Ldrb(reg_w(dest), MEM_OP(reg_x(base), offset));
      break;
    case kLoadUnsignedHalfword:
      ___ Ldrh(reg_w(dest), MEM_OP(reg_x(base), offset));
      break;
    case kLoadWord:
      ___ Ldr(reg_w(dest), MEM_OP(reg_x(base), offset));
      break;
    default:
        LOG(FATAL) << "UNREACHABLE";
  }
}

// Note: We can extend this member by adding load type info - see
// sign extended A64 load variants.
void Arm64JNIMacroAssembler::LoadFromOffset(XRegister dest, XRegister base, int32_t offset) {
  CHECK_NE(dest, SP);
  ___ Ldr(reg_x(dest), MEM_OP(reg_x(base), offset));
}

void Arm64JNIMacroAssembler::LoadSFromOffset(SRegister dest, XRegister base, int32_t offset) {
  ___ Ldr(reg_s(dest), MEM_OP(reg_x(base), offset));
}

void Arm64JNIMacroAssembler::LoadDFromOffset(DRegister dest, XRegister base, int32_t offset) {
  ___ Ldr(reg_d(dest), MEM_OP(reg_x(base), offset));
}

void Arm64JNIMacroAssembler::Load(Arm64ManagedRegister dest,
                                  XRegister base,
                                  int32_t offset,
                                  size_t size) {
  if (dest.IsNoRegister()) {
    CHECK_EQ(0u, size) << dest;
  } else if (dest.IsWRegister()) {
    CHECK_EQ(4u, size) << dest;
    ___ Ldr(reg_w(dest.AsWRegister()), MEM_OP(reg_x(base), offset));
  } else if (dest.IsXRegister()) {
    CHECK_NE(dest.AsXRegister(), SP) << dest;

    if (size == 1u) {
      ___ Ldrb(reg_w(dest.AsOverlappingWRegister()), MEM_OP(reg_x(base), offset));
    } else if (size == 4u) {
      ___ Ldr(reg_w(dest.AsOverlappingWRegister()), MEM_OP(reg_x(base), offset));
    }  else {
      CHECK_EQ(8u, size) << dest;
      ___ Ldr(reg_x(dest.AsXRegister()), MEM_OP(reg_x(base), offset));
    }
  } else if (dest.IsSRegister()) {
    ___ Ldr(reg_s(dest.AsSRegister()), MEM_OP(reg_x(base), offset));
  } else {
    CHECK(dest.IsDRegister()) << dest;
    ___ Ldr(reg_d(dest.AsDRegister()), MEM_OP(reg_x(base), offset));
  }
}

void Arm64JNIMacroAssembler::Load(ManagedRegister m_dst, FrameOffset src, size_t size) {
  return Load(m_dst.AsArm64(), SP, src.Int32Value(), size);
}

void Arm64JNIMacroAssembler::Load(ManagedRegister m_dst,
                                  ManagedRegister m_base,
                                  MemberOffset offs,
                                  size_t size) {
  return Load(m_dst.AsArm64(), m_base.AsArm64().AsXRegister(), offs.Int32Value(), size);
}

void Arm64JNIMacroAssembler::LoadRawPtrFromThread(ManagedRegister m_dst, ThreadOffset64 offs) {
  Arm64ManagedRegister dst = m_dst.AsArm64();
  CHECK(dst.IsXRegister()) << dst;
  LoadFromOffset(dst.AsXRegister(), TR, offs.Int32Value());
}

// Copying routines.
void Arm64JNIMacroAssembler::MoveArguments(ArrayRef<ArgumentLocation> dests,
                                           ArrayRef<ArgumentLocation> srcs,
                                           ArrayRef<FrameOffset> refs) {
  size_t arg_count = dests.size();
  DCHECK_EQ(arg_count, srcs.size());
  DCHECK_EQ(arg_count, refs.size());

  auto get_mask = [](ManagedRegister reg) -> uint64_t {
    Arm64ManagedRegister arm64_reg = reg.AsArm64();
    if (arm64_reg.IsXRegister()) {
      size_t core_reg_number = static_cast<size_t>(arm64_reg.AsXRegister());
      DCHECK_LT(core_reg_number, 31u);  // xSP, xZR not allowed.
      return UINT64_C(1) << core_reg_number;
    } else if (arm64_reg.IsWRegister()) {
      size_t core_reg_number = static_cast<size_t>(arm64_reg.AsWRegister());
      DCHECK_LT(core_reg_number, 31u);  // wSP, wZR not allowed.
      return UINT64_C(1) << core_reg_number;
    } else if (arm64_reg.IsDRegister()) {
      size_t fp_reg_number = static_cast<size_t>(arm64_reg.AsDRegister());
      DCHECK_LT(fp_reg_number, 32u);
      return (UINT64_C(1) << 32u) << fp_reg_number;
    } else {
      DCHECK(arm64_reg.IsSRegister());
      size_t fp_reg_number = static_cast<size_t>(arm64_reg.AsSRegister());
      DCHECK_LT(fp_reg_number, 32u);
      return (UINT64_C(1) << 32u) << fp_reg_number;
    }
  };

  // More than 8 core or FP reg args are very rare, so we do not optimize for
  // that case by using LDP/STP, except for situations that arise even with low
  // number of arguments. We use STP for the non-reference spilling which also
  // covers the initial spill for native reference register args as they are
  // spilled as raw 32-bit values. We also optimize loading args to registers
  // with LDP, whether references or not, except for the initial non-null
  // reference which we do not need to load at all.

  // Collect registers to move while storing/copying args to stack slots.
  // Convert processed references to `jobject`.
  uint64_t src_regs = 0u;
  uint64_t dest_regs = 0u;
  for (size_t i = 0; i != arg_count; ++i) {
    const ArgumentLocation& src = srcs[i];
    const ArgumentLocation& dest = dests[i];
    const FrameOffset ref = refs[i];
    if (ref != kInvalidReferenceOffset) {
      DCHECK_EQ(src.GetSize(), kObjectReferenceSize);
      DCHECK_EQ(dest.GetSize(), static_cast<size_t>(kArm64PointerSize));
    } else {
      DCHECK_EQ(src.GetSize(), dest.GetSize());
    }
    if (dest.IsRegister()) {
      // Note: For references, `Equals()` returns `false` for overlapping W and X registers.
      if (ref != kInvalidReferenceOffset &&
          src.IsRegister() &&
          src.GetRegister().AsArm64().AsOverlappingXRegister() ==
              dest.GetRegister().AsArm64().AsXRegister()) {
        // Just convert to `jobject`. No further processing is needed.
        CreateJObject(dest.GetRegister(), ref, src.GetRegister(), /*null_allowed=*/ i != 0u);
      } else if (src.IsRegister() && src.GetRegister().Equals(dest.GetRegister())) {
        // Nothing to do.
      } else {
        if (src.IsRegister()) {
          src_regs |= get_mask(src.GetRegister());
        }
        dest_regs |= get_mask(dest.GetRegister());
      }
    } else if (ref != kInvalidReferenceOffset) {
      if (src.IsRegister()) {
        // Note: We can clobber `src` here as the register cannot hold more than one argument.
        ManagedRegister src_x =
            CoreRegisterWithSize(src.GetRegister(), static_cast<size_t>(kArm64PointerSize));
        CreateJObject(src_x, ref, src.GetRegister(), /*null_allowed=*/ i != 0u);
        Store(dest.GetFrameOffset(), src_x, dest.GetSize());
      } else {
        CreateJObject(dest.GetFrameOffset(), ref, /*null_allowed=*/ i != 0u);
      }
    } else {
      if (src.IsRegister()) {
        static_assert(kStpWOffsetCutoff == kStpSOffsetCutoff);
        static_assert(kStpXOffsetCutoff == kStpDOffsetCutoff);
        if (i + 1u != arg_count &&
            srcs[i + 1u].IsRegister() &&
            srcs[i + 1u].GetSize() == dest.GetSize() &&
            src.GetRegister().AsArm64().IsGPRegister() ==
                srcs[i + 1u].GetRegister().AsArm64().IsGPRegister() &&
            refs[i + 1u] == kInvalidReferenceOffset &&
            !dests[i + 1u].IsRegister() &&
            dests[i + 1u].GetFrameOffset().SizeValue() ==
                dest.GetFrameOffset().SizeValue() + dest.GetSize() &&
            IsAlignedParam(dest.GetFrameOffset().SizeValue(), dest.GetSize()) &&
            dest.GetFrameOffset().SizeValue() <
                (dest.GetSize() == 8u ? kStpXOffsetCutoff : kStpWOffsetCutoff)) {
          DCHECK_EQ(dests[i + 1u].GetSize(), dest.GetSize());
          Arm64ManagedRegister src_reg = src.GetRegister().AsArm64();
          Arm64ManagedRegister src2_reg = srcs[i + 1u].GetRegister().AsArm64();
          DCHECK_EQ(dest.GetSize() == 8u, src_reg.IsXRegister() || src_reg.IsDRegister());
          DCHECK_EQ(dest.GetSize() == 8u, src2_reg.IsXRegister() || src2_reg.IsDRegister());
          if (src_reg.IsWRegister()) {
            ___ Stp(reg_w(src_reg.AsWRegister()),
                    reg_w(src2_reg.AsWRegister()),
                    MEM_OP(sp, dest.GetFrameOffset().SizeValue()));
          } else if (src_reg.IsXRegister()) {
            ___ Stp(reg_x(src_reg.AsXRegister()),
                    reg_x(src2_reg.AsXRegister()),
                    MEM_OP(sp, dest.GetFrameOffset().SizeValue()));
          } else if (src_reg.IsSRegister()) {
            ___ Stp(reg_s(src_reg.AsSRegister()),
                    reg_s(src2_reg.AsSRegister()),
                    MEM_OP(sp, dest.GetFrameOffset().SizeValue()));
          } else {
            DCHECK(src_reg.IsDRegister());
            ___ Stp(reg_d(src_reg.AsDRegister()),
                    reg_d(src2_reg.AsDRegister()),
                    MEM_OP(sp, dest.GetFrameOffset().SizeValue()));
          }
          ++i;
        } else {
          Store(dest.GetFrameOffset(), src.GetRegister(), dest.GetSize());
        }
      } else {
        Copy(dest.GetFrameOffset(), src.GetFrameOffset(), dest.GetSize());
      }
    }
  }
  // Fill destination registers.
  // There should be no cycles, so this simple algorithm should make progress.
  while (dest_regs != 0u) {
    uint64_t old_dest_regs = dest_regs;
    for (size_t i = 0; i != arg_count; ++i) {
      const ArgumentLocation& src = srcs[i];
      const ArgumentLocation& dest = dests[i];
      const FrameOffset ref = refs[i];
      if (!dest.IsRegister()) {
        continue;  // Stored in first loop above.
      }
      auto can_process = [&](ManagedRegister dest_reg) {
        uint64_t dest_reg_mask = get_mask(dest_reg);
        if ((dest_reg_mask & dest_regs) == 0u) {
          return false;  // Equals source, or already filled in one of previous iterations.
        }
        if ((dest_reg_mask & src_regs) != 0u) {
          return false;  // Cannot clobber this register yet.
        }
        return true;
      };
      if (!can_process(dest.GetRegister())) {
        continue;
      }
      if (src.IsRegister()) {
        if (ref != kInvalidReferenceOffset) {
          CreateJObject(dest.GetRegister(), ref, src.GetRegister(), /*null_allowed=*/ i != 0u);
        } else {
          Move(dest.GetRegister(), src.GetRegister(), dest.GetSize());
        }
        src_regs &= ~get_mask(src.GetRegister());  // Allow clobbering source register.
      } else if (i + 1u != arg_count &&
                 (i != 0u || ref == kInvalidReferenceOffset) &&  // Not for non-null reference.
                 dests[i + 1u].IsRegister() &&
                 dest.GetRegister().AsArm64().IsGPRegister() ==
                     dests[i + 1u].GetRegister().AsArm64().IsGPRegister() &&
                 !srcs[i + 1u].IsRegister() &&
                 srcs[i + 1u].GetSize() == src.GetSize() &&
                 srcs[i + 1u].GetFrameOffset().SizeValue() ==
                     src.GetFrameOffset().SizeValue() + src.GetSize() &&
                 IsAlignedParam(src.GetFrameOffset().SizeValue(), src.GetSize()) &&
                 can_process(dests[i + 1u].GetRegister())) {
        Arm64ManagedRegister dest_reg = dest.GetRegister().AsArm64();
        Arm64ManagedRegister dest2_reg = dests[i + 1u].GetRegister().AsArm64();
        DCHECK(ref == kInvalidReferenceOffset || dest_reg.IsXRegister());
        DCHECK(refs[i + 1u] == kInvalidReferenceOffset || dest2_reg.IsXRegister());
        if (dest_reg.IsDRegister()) {
          DCHECK_EQ(dest.GetSize(), 8u);
          DCHECK_EQ(dests[i + 1u].GetSize(), 8u);
          ___ Ldp(reg_d(dest_reg.AsDRegister()),
                  reg_d(dest2_reg.AsDRegister()),
                  MEM_OP(sp, src.GetFrameOffset().SizeValue()));
        } else if (dest_reg.IsSRegister()) {
          DCHECK_EQ(dest.GetSize(), 4u);
          DCHECK_EQ(dests[i + 1u].GetSize(), 4u);
          ___ Ldp(reg_s(dest_reg.AsSRegister()),
                  reg_s(dest2_reg.AsSRegister()),
                  MEM_OP(sp, src.GetFrameOffset().SizeValue()));
        } else if (src.GetSize() == 8u) {
          DCHECK_EQ(dest.GetSize(), 8u);
          DCHECK_EQ(dests[i + 1u].GetSize(), 8u);
          ___ Ldp(reg_x(dest_reg.AsXRegister()),
                  reg_x(dest2_reg.AsXRegister()),
                  MEM_OP(sp, src.GetFrameOffset().SizeValue()));
        } else {
          DCHECK_EQ(dest.GetSize(), ref != kInvalidReferenceOffset ? 8u : 4u);
          DCHECK_EQ(dests[i + 1u].GetSize(), refs[i + 1u] != kInvalidReferenceOffset ? 8u : 4u);
          auto to_w = [](Arm64ManagedRegister reg) {
            return reg_w(reg.IsXRegister() ? reg.AsOverlappingWRegister() : reg.AsWRegister());
          };
          ___ Ldp(to_w(dest_reg), to_w(dest2_reg), MEM_OP(sp, src.GetFrameOffset().SizeValue()));
          auto to_mr_w = [](Arm64ManagedRegister reg) {
            return Arm64ManagedRegister::FromWRegister(reg.AsOverlappingWRegister());
          };
          if (ref != kInvalidReferenceOffset) {
            CreateJObject(dest_reg, ref, to_mr_w(dest_reg), /*null_allowed=*/ true);
          }
          if (refs[i + 1u] != kInvalidReferenceOffset) {
            CreateJObject(dest2_reg, refs[i + 1u], to_mr_w(dest2_reg), /*null_allowed=*/ true);
          }
        }
        dest_regs &= ~get_mask(dest2_reg);  // Destination register was filled.
        ++i;  // Proceed to mark the other destination register as filled.
      } else {
        if (ref != kInvalidReferenceOffset) {
          CreateJObject(
              dest.GetRegister(), ref, ManagedRegister::NoRegister(), /*null_allowed=*/ i != 0u);
        } else {
          Load(dest.GetRegister(), src.GetFrameOffset(), dest.GetSize());
        }
      }
      dest_regs &= ~get_mask(dest.GetRegister());  // Destination register was filled.
    }
    CHECK_NE(old_dest_regs, dest_regs);
    DCHECK_EQ(0u, dest_regs & ~old_dest_regs);
  }
}

void Arm64JNIMacroAssembler::Move(ManagedRegister m_dst, ManagedRegister m_src, size_t size) {
  Arm64ManagedRegister dst = m_dst.AsArm64();
  if (kIsDebugBuild) {
    // Check that the destination is not a scratch register.
    UseScratchRegisterScope temps(asm_.GetVIXLAssembler());
    if (dst.IsXRegister()) {
      CHECK(!temps.IsAvailable(reg_x(dst.AsXRegister())));
    } else if (dst.IsWRegister()) {
      CHECK(!temps.IsAvailable(reg_w(dst.AsWRegister())));
    } else if (dst.IsSRegister()) {
      CHECK(!temps.IsAvailable(reg_s(dst.AsSRegister())));
    } else {
      CHECK(!temps.IsAvailable(reg_d(dst.AsDRegister())));
    }
  }
  Arm64ManagedRegister src = m_src.AsArm64();
  if (!dst.Equals(src)) {
    if (dst.IsXRegister()) {
      if (size == 4) {
        CHECK(src.IsWRegister());
        ___ Mov(reg_w(dst.AsOverlappingWRegister()), reg_w(src.AsWRegister()));
      } else {
        if (src.IsXRegister()) {
          ___ Mov(reg_x(dst.AsXRegister()), reg_x(src.AsXRegister()));
        } else {
          ___ Mov(reg_x(dst.AsXRegister()), reg_x(src.AsOverlappingXRegister()));
        }
      }
    } else if (dst.IsWRegister()) {
      CHECK(src.IsWRegister()) << src;
      ___ Mov(reg_w(dst.AsWRegister()), reg_w(src.AsWRegister()));
    } else if (dst.IsSRegister()) {
      CHECK(src.IsSRegister()) << src;
      ___ Fmov(reg_s(dst.AsSRegister()), reg_s(src.AsSRegister()));
    } else {
      CHECK(dst.IsDRegister()) << dst;
      CHECK(src.IsDRegister()) << src;
      ___ Fmov(reg_d(dst.AsDRegister()), reg_d(src.AsDRegister()));
    }
  }
}

void Arm64JNIMacroAssembler::Move(ManagedRegister m_dst, size_t value) {
  Arm64ManagedRegister dst = m_dst.AsArm64();
  DCHECK(dst.IsXRegister());
  ___ Mov(reg_x(dst.AsXRegister()), value);
}

void Arm64JNIMacroAssembler::Copy(FrameOffset dest, FrameOffset src, size_t size) {
  DCHECK(size == 4 || size == 8) << size;
  UseScratchRegisterScope temps(asm_.GetVIXLAssembler());
  Register scratch = (size == 8) ? temps.AcquireX() : temps.AcquireW();
  ___ Ldr(scratch, MEM_OP(reg_x(SP), src.Int32Value()));
  ___ Str(scratch, MEM_OP(reg_x(SP), dest.Int32Value()));
}

void Arm64JNIMacroAssembler::SignExtend(ManagedRegister mreg, size_t size) {
  Arm64ManagedRegister reg = mreg.AsArm64();
  CHECK(size == 1 || size == 2) << size;
  CHECK(reg.IsWRegister()) << reg;
  if (size == 1) {
    ___ Sxtb(reg_w(reg.AsWRegister()), reg_w(reg.AsWRegister()));
  } else {
    ___ Sxth(reg_w(reg.AsWRegister()), reg_w(reg.AsWRegister()));
  }
}

void Arm64JNIMacroAssembler::ZeroExtend(ManagedRegister mreg, size_t size) {
  Arm64ManagedRegister reg = mreg.AsArm64();
  CHECK(size == 1 || size == 2) << size;
  CHECK(reg.IsWRegister()) << reg;
  if (size == 1) {
    ___ Uxtb(reg_w(reg.AsWRegister()), reg_w(reg.AsWRegister()));
  } else {
    ___ Uxth(reg_w(reg.AsWRegister()), reg_w(reg.AsWRegister()));
  }
}

void Arm64JNIMacroAssembler::VerifyObject(ManagedRegister /*src*/, bool /*could_be_null*/) {
  // TODO: not validating references.
}

void Arm64JNIMacroAssembler::VerifyObject(FrameOffset /*src*/, bool /*could_be_null*/) {
  // TODO: not validating references.
}

void Arm64JNIMacroAssembler::Jump(ManagedRegister m_base, Offset offs) {
  Arm64ManagedRegister base = m_base.AsArm64();
  CHECK(base.IsXRegister()) << base;
  UseScratchRegisterScope temps(asm_.GetVIXLAssembler());
  Register scratch = temps.AcquireX();
  ___ Ldr(scratch, MEM_OP(reg_x(base.AsXRegister()), offs.Int32Value()));
  ___ Br(scratch);
}

void Arm64JNIMacroAssembler::Call(ManagedRegister m_base, Offset offs) {
  Arm64ManagedRegister base = m_base.AsArm64();
  CHECK(base.IsXRegister()) << base;
  ___ Ldr(lr, MEM_OP(reg_x(base.AsXRegister()), offs.Int32Value()));
  ___ Blr(lr);
}

void Arm64JNIMacroAssembler::CallFromThread(ThreadOffset64 offset) {
  // Call *(TR + offset)
  ___ Ldr(lr, MEM_OP(reg_x(TR), offset.Int32Value()));
  ___ Blr(lr);
}

void Arm64JNIMacroAssembler::CreateJObject(ManagedRegister m_out_reg,
                                           FrameOffset spilled_reference_offset,
                                           ManagedRegister m_in_reg,
                                           bool null_allowed) {
  Arm64ManagedRegister out_reg = m_out_reg.AsArm64();
  Arm64ManagedRegister in_reg = m_in_reg.AsArm64();
  CHECK(in_reg.IsNoRegister() || in_reg.IsWRegister()) << in_reg;
  CHECK(out_reg.IsXRegister()) << out_reg;
  if (null_allowed) {
    UseScratchRegisterScope temps(asm_.GetVIXLAssembler());
    Register scratch = temps.AcquireX();

    // Null values get a jobject value null. Otherwise, the jobject is
    // the address of the spilled reference.
    // e.g. out_reg = (in == 0) ? 0 : (SP+spilled_reference_offset)
    if (in_reg.IsNoRegister()) {
      in_reg = Arm64ManagedRegister::FromWRegister(out_reg.AsOverlappingWRegister());
      LoadWFromOffset(kLoadWord, in_reg.AsWRegister(), SP, spilled_reference_offset.Int32Value());
    }
    ___ Add(scratch, reg_x(SP), spilled_reference_offset.Int32Value());
    ___ Cmp(reg_w(in_reg.AsWRegister()), 0);
    ___ Csel(reg_x(out_reg.AsXRegister()), scratch, xzr, ne);
  } else {
    AddConstant(out_reg.AsXRegister(), SP, spilled_reference_offset.Int32Value(), al);
  }
}

void Arm64JNIMacroAssembler::CreateJObject(FrameOffset out_off,
                                           FrameOffset spilled_reference_offset,
                                           bool null_allowed) {
  UseScratchRegisterScope temps(asm_.GetVIXLAssembler());
  Register scratch = temps.AcquireX();
  if (null_allowed) {
    Register scratch2 = temps.AcquireW();
    ___ Ldr(scratch2, MEM_OP(reg_x(SP), spilled_reference_offset.Int32Value()));
    ___ Add(scratch, reg_x(SP), spilled_reference_offset.Int32Value());
    // Null values get a jobject value null. Otherwise, the jobject is
    // the address of the spilled reference.
    // e.g. scratch = (scratch == 0) ? 0 : (SP+spilled_reference_offset)
    ___ Cmp(scratch2, 0);
    ___ Csel(scratch, scratch, xzr, ne);
  } else {
    ___ Add(scratch, reg_x(SP), spilled_reference_offset.Int32Value());
  }
  ___ Str(scratch, MEM_OP(reg_x(SP), out_off.Int32Value()));
}

void Arm64JNIMacroAssembler::DecodeJNITransitionOrLocalJObject(ManagedRegister m_reg,
                                                               JNIMacroLabel* slow_path,
                                                               JNIMacroLabel* resume) {
  constexpr uint64_t kGlobalOrWeakGlobalMask = IndirectReferenceTable::GetGlobalOrWeakGlobalMask();
  constexpr uint64_t kIndirectRefKindMask = IndirectReferenceTable::GetIndirectRefKindMask();
  constexpr size_t kGlobalOrWeakGlobalBit = WhichPowerOf2(kGlobalOrWeakGlobalMask);
  Register reg = reg_w(m_reg.AsArm64().AsWRegister());
  ___ Tbnz(reg.X(), kGlobalOrWeakGlobalBit, Arm64JNIMacroLabel::Cast(slow_path)->AsArm64());
  ___ And(reg.X(), reg.X(), ~kIndirectRefKindMask);
  ___ Cbz(reg.X(), Arm64JNIMacroLabel::Cast(resume)->AsArm64());  // Skip load for null.
  ___ Ldr(reg, MEM_OP(reg.X()));
}

void Arm64JNIMacroAssembler::TryToTransitionFromRunnableToNative(
    JNIMacroLabel* label, [[maybe_unused]] ArrayRef<const ManagedRegister> scratch_regs) {
  constexpr uint32_t kNativeStateValue = Thread::StoredThreadStateValue(ThreadState::kNative);
  constexpr uint32_t kRunnableStateValue = Thread::StoredThreadStateValue(ThreadState::kRunnable);
  constexpr ThreadOffset64 thread_flags_offset = Thread::ThreadFlagsOffset<kArm64PointerSize>();
  constexpr ThreadOffset64 thread_held_mutex_mutator_lock_offset =
      Thread::HeldMutexOffset<kArm64PointerSize>(kMutatorLock);

  UseScratchRegisterScope temps(asm_.GetVIXLAssembler());
  Register scratch = temps.AcquireW();
  Register scratch2 = temps.AcquireW();

  // CAS release, old_value = kRunnableStateValue, new_value = kNativeStateValue, no flags.
  vixl::aarch64::Label retry;
  ___ Bind(&retry);
  static_assert(thread_flags_offset.Int32Value() == 0);  // LDXR/STLXR require exact address.
  ___ Ldxr(scratch, MEM_OP(reg_x(TR)));
  ___ Mov(scratch2, kNativeStateValue);
  // If any flags are set, go to the slow path.
  static_assert(kRunnableStateValue == 0u);
  ___ Cbnz(scratch, Arm64JNIMacroLabel::Cast(label)->AsArm64());
  ___ Stlxr(scratch, scratch2, MEM_OP(reg_x(TR)));
  ___ Cbnz(scratch, &retry);

  // Clear `self->tlsPtr_.held_mutexes[kMutatorLock]`.
  ___ Str(xzr, MEM_OP(reg_x(TR), thread_held_mutex_mutator_lock_offset.Int32Value()));
}

void Arm64JNIMacroAssembler::TryToTransitionFromNativeToRunnable(
    JNIMacroLabel* label,
    [[maybe_unused]] ArrayRef<const ManagedRegister> scratch_regs,
    [[maybe_unused]] ManagedRegister return_reg) {
  constexpr uint32_t kNativeStateValue = Thread::StoredThreadStateValue(ThreadState::kNative);
  constexpr uint32_t kRunnableStateValue = Thread::StoredThreadStateValue(ThreadState::kRunnable);
  constexpr ThreadOffset64 thread_flags_offset = Thread::ThreadFlagsOffset<kArm64PointerSize>();
  constexpr ThreadOffset64 thread_held_mutex_mutator_lock_offset =
      Thread::HeldMutexOffset<kArm64PointerSize>(kMutatorLock);
  constexpr ThreadOffset64 thread_mutator_lock_offset =
      Thread::MutatorLockOffset<kArm64PointerSize>();

  UseScratchRegisterScope temps(asm_.GetVIXLAssembler());
  Register scratch = temps.AcquireW();
  Register scratch2 = temps.AcquireW();

  // CAS acquire, old_value = kNativeStateValue, new_value = kRunnableStateValue, no flags.
  vixl::aarch64::Label retry;
  ___ Bind(&retry);
  static_assert(thread_flags_offset.Int32Value() == 0);  // LDAXR/STXR require exact address.
  ___ Ldaxr(scratch, MEM_OP(reg_x(TR)));
  ___ Mov(scratch2, kNativeStateValue);
  // If any flags are set, or the state is not Native, go to the slow path.
  // (While the thread can theoretically transition between different Suspended states,
  // it would be very unexpected to see a state other than Native at this point.)
  ___ Cmp(scratch, scratch2);
  ___ B(ne, Arm64JNIMacroLabel::Cast(label)->AsArm64());
  static_assert(kRunnableStateValue == 0u);
  ___ Stxr(scratch, wzr, MEM_OP(reg_x(TR)));
  ___ Cbnz(scratch, &retry);

  // Set `self->tlsPtr_.held_mutexes[kMutatorLock]` to the mutator lock.
  ___ Ldr(scratch.X(), MEM_OP(reg_x(TR), thread_mutator_lock_offset.Int32Value()));
  ___ Str(scratch.X(), MEM_OP(reg_x(TR), thread_held_mutex_mutator_lock_offset.Int32Value()));
}

void Arm64JNIMacroAssembler::SuspendCheck(JNIMacroLabel* label) {
  UseScratchRegisterScope temps(asm_.GetVIXLAssembler());
  Register scratch = temps.AcquireW();
  ___ Ldr(scratch, MEM_OP(reg_x(TR), Thread::ThreadFlagsOffset<kArm64PointerSize>().Int32Value()));
  ___ Tst(scratch, Thread::SuspendOrCheckpointRequestFlags());
  ___ B(ne, Arm64JNIMacroLabel::Cast(label)->AsArm64());
}

void Arm64JNIMacroAssembler::ExceptionPoll(JNIMacroLabel* label) {
  UseScratchRegisterScope temps(asm_.GetVIXLAssembler());
  Register scratch = temps.AcquireX();
  ___ Ldr(scratch, MEM_OP(reg_x(TR), Thread::ExceptionOffset<kArm64PointerSize>().Int32Value()));
  ___ Cbnz(scratch, Arm64JNIMacroLabel::Cast(label)->AsArm64());
}

void Arm64JNIMacroAssembler::DeliverPendingException() {
  // Pass exception object as argument.
  // Don't care about preserving X0 as this won't return.
  // Note: The scratch register from `ExceptionPoll()` may have been clobbered.
  ___ Ldr(reg_x(X0), MEM_OP(reg_x(TR), Thread::ExceptionOffset<kArm64PointerSize>().Int32Value()));
  ___ Ldr(lr,
          MEM_OP(reg_x(TR),
                 QUICK_ENTRYPOINT_OFFSET(kArm64PointerSize, pDeliverException).Int32Value()));
  ___ Blr(lr);
  // Call should never return.
  ___ Brk();
}

std::unique_ptr<JNIMacroLabel> Arm64JNIMacroAssembler::CreateLabel() {
  return std::unique_ptr<JNIMacroLabel>(new (asm_.GetAllocator()) Arm64JNIMacroLabel());
}

void Arm64JNIMacroAssembler::Jump(JNIMacroLabel* label) {
  CHECK(label != nullptr);
  ___ B(Arm64JNIMacroLabel::Cast(label)->AsArm64());
}

void Arm64JNIMacroAssembler::TestGcMarking(JNIMacroLabel* label, JNIMacroUnaryCondition cond) {
  CHECK(label != nullptr);

  UseScratchRegisterScope temps(asm_.GetVIXLAssembler());
  Register test_reg;
  DCHECK_EQ(Thread::IsGcMarkingSize(), 4u);
  if (kUseBakerReadBarrier) {
    // TestGcMarking() is used in the JNI stub entry when the marking register is up to date.
    if (kIsDebugBuild && emit_run_time_checks_in_debug_mode_) {
      Register temp = temps.AcquireW();
      asm_.GenerateMarkingRegisterCheck(temp);
    }
    test_reg = reg_w(MR);
  } else {
    test_reg = temps.AcquireW();
    int32_t is_gc_marking_offset = Thread::IsGcMarkingOffset<kArm64PointerSize>().Int32Value();
    ___ Ldr(test_reg, MEM_OP(reg_x(TR), is_gc_marking_offset));
  }
  switch (cond) {
    case JNIMacroUnaryCondition::kZero:
      ___ Cbz(test_reg, Arm64JNIMacroLabel::Cast(label)->AsArm64());
      break;
    case JNIMacroUnaryCondition::kNotZero:
      ___ Cbnz(test_reg, Arm64JNIMacroLabel::Cast(label)->AsArm64());
      break;
    default:
      LOG(FATAL) << "Not implemented unary condition: " << static_cast<int>(cond);
      UNREACHABLE();
  }
}

void Arm64JNIMacroAssembler::TestMarkBit(ManagedRegister m_ref,
                                         JNIMacroLabel* label,
                                         JNIMacroUnaryCondition cond) {
  DCHECK(kUseBakerReadBarrier);
  Register ref = reg_x(m_ref.AsArm64().AsOverlappingXRegister());
  UseScratchRegisterScope temps(asm_.GetVIXLAssembler());
  Register scratch = temps.AcquireW();
  ___ Ldr(scratch, MEM_OP(ref, mirror::Object::MonitorOffset().SizeValue()));
  static_assert(LockWord::kMarkBitStateSize == 1u);
  switch (cond) {
    case JNIMacroUnaryCondition::kZero:
      ___ Tbz(scratch, LockWord::kMarkBitStateShift, Arm64JNIMacroLabel::Cast(label)->AsArm64());
      break;
    case JNIMacroUnaryCondition::kNotZero:
      ___ Tbnz(scratch, LockWord::kMarkBitStateShift, Arm64JNIMacroLabel::Cast(label)->AsArm64());
      break;
    default:
      LOG(FATAL) << "Not implemented unary condition: " << static_cast<int>(cond);
      UNREACHABLE();
  }
}

void Arm64JNIMacroAssembler::TestByteAndJumpIfNotZero(uintptr_t address, JNIMacroLabel* label) {
  UseScratchRegisterScope temps(asm_.GetVIXLAssembler());
  Register scratch = temps.AcquireX();
  ___ Mov(scratch, address);
  ___ Ldrb(scratch.W(), MEM_OP(scratch, 0));
  ___ Cbnz(scratch.W(), Arm64JNIMacroLabel::Cast(label)->AsArm64());
}

void Arm64JNIMacroAssembler::Bind(JNIMacroLabel* label) {
  CHECK(label != nullptr);
  ___ Bind(Arm64JNIMacroLabel::Cast(label)->AsArm64());
}

void Arm64JNIMacroAssembler::BuildFrame(size_t frame_size,
                                        ManagedRegister method_reg,
                                        ArrayRef<const ManagedRegister> callee_save_regs) {
  // Setup VIXL CPURegList for callee-saves.
  CPURegList core_reg_list(CPURegister::kRegister, kXRegSize, 0);
  CPURegList fp_reg_list(CPURegister::kVRegister, kDRegSize, 0);
  for (auto r : callee_save_regs) {
    Arm64ManagedRegister reg = r.AsArm64();
    if (reg.IsXRegister()) {
      core_reg_list.Combine(reg_x(reg.AsXRegister()).GetCode());
    } else {
      DCHECK(reg.IsDRegister());
      fp_reg_list.Combine(reg_d(reg.AsDRegister()).GetCode());
    }
  }
  size_t core_reg_size = core_reg_list.GetTotalSizeInBytes();
  size_t fp_reg_size = fp_reg_list.GetTotalSizeInBytes();

  // Increase frame to required size.
  DCHECK_ALIGNED(frame_size, kStackAlignment);
  // Must at least have space for Method* if we're going to spill it.
  DCHECK_GE(frame_size,
            core_reg_size + fp_reg_size + (method_reg.IsRegister() ? kXRegSizeInBytes : 0u));
  IncreaseFrameSize(frame_size);

  // Save callee-saves.
  asm_.SpillRegisters(core_reg_list, frame_size - core_reg_size);
  asm_.SpillRegisters(fp_reg_list, frame_size - core_reg_size - fp_reg_size);

  if (method_reg.IsRegister()) {
    // Write ArtMethod*
    DCHECK(X0 == method_reg.AsArm64().AsXRegister());
    StoreToOffset(X0, SP, 0);
  }
}

void Arm64JNIMacroAssembler::RemoveFrame(size_t frame_size,
                                         ArrayRef<const ManagedRegister> callee_save_regs,
                                         bool may_suspend) {
  // Setup VIXL CPURegList for callee-saves.
  CPURegList core_reg_list(CPURegister::kRegister, kXRegSize, 0);
  CPURegList fp_reg_list(CPURegister::kVRegister, kDRegSize, 0);
  for (auto r : callee_save_regs) {
    Arm64ManagedRegister reg = r.AsArm64();
    if (reg.IsXRegister()) {
      core_reg_list.Combine(reg_x(reg.AsXRegister()).GetCode());
    } else {
      DCHECK(reg.IsDRegister());
      fp_reg_list.Combine(reg_d(reg.AsDRegister()).GetCode());
    }
  }
  size_t core_reg_size = core_reg_list.GetTotalSizeInBytes();
  size_t fp_reg_size = fp_reg_list.GetTotalSizeInBytes();

  // For now we only check that the size of the frame is large enough to hold spills and method
  // reference.
  DCHECK_GE(frame_size, core_reg_size + fp_reg_size);
  DCHECK_ALIGNED(frame_size, kAapcs64StackAlignment);

  cfi().RememberState();

  // Restore callee-saves.
  asm_.UnspillRegisters(core_reg_list, frame_size - core_reg_size);
  asm_.UnspillRegisters(fp_reg_list, frame_size - core_reg_size - fp_reg_size);

  // Emit marking register refresh even with all GCs as we are still using the
  // register due to nterp's dependency.
  if (kReserveMarkingRegister) {
    vixl::aarch64::Register mr = reg_x(MR);  // Marking Register.
    vixl::aarch64::Register tr = reg_x(TR);  // Thread Register.

    if (may_suspend) {
      // The method may be suspended; refresh the Marking Register.
      ___ Ldr(mr.W(), MemOperand(tr, Thread::IsGcMarkingOffset<kArm64PointerSize>().Int32Value()));
    } else {
      // The method shall not be suspended; no need to refresh the Marking Register.

      // The Marking Register is a callee-save register and thus has been
      // preserved by native code following the AAPCS64 calling convention.

      // The following condition is a compile-time one, so it does not have a run-time cost.
      if (kIsDebugBuild) {
        // The following condition is a run-time one; it is executed after the
        // previous compile-time test, to avoid penalizing non-debug builds.
        if (emit_run_time_checks_in_debug_mode_) {
          // Emit a run-time check verifying that the Marking Register is up-to-date.
          UseScratchRegisterScope temps(asm_.GetVIXLAssembler());
          Register temp = temps.AcquireW();
          // Ensure we are not clobbering a callee-save register that was restored before.
          DCHECK(!core_reg_list.IncludesAliasOf(temp.X()))
              << "core_reg_list should not contain scratch register X" << temp.GetCode();
          asm_.GenerateMarkingRegisterCheck(temp);
        }
      }
    }
  }

  // Decrease frame size to start of callee saved regs.
  DecreaseFrameSize(frame_size);

  // Return to LR.
  ___ Ret();

  // The CFI should be restored for any code that follows the exit block.
  cfi().RestoreState();
  cfi().DefCFAOffset(frame_size);
}

void Arm64JNIMacroAssembler::LoadLocalReferenceTableStates(ManagedRegister jni_env_reg,
                                                           ManagedRegister previous_state_reg,
                                                           ManagedRegister current_state_reg) {
  constexpr size_t kLRTSegmentStateSize = sizeof(jni::LRTSegmentState);
  DCHECK_EQ(kLRTSegmentStateSize, kWRegSizeInBytes);
  const MemberOffset previous_state_offset = JNIEnvExt::LrtPreviousStateOffset(kArm64PointerSize);
  const MemberOffset current_state_offset = JNIEnvExt::LrtSegmentStateOffset(kArm64PointerSize);
  DCHECK_EQ(previous_state_offset.SizeValue() + kLRTSegmentStateSize,
            current_state_offset.SizeValue());

  ___ Ldp(
      reg_w(previous_state_reg.AsArm64().AsWRegister()),
      reg_w(current_state_reg.AsArm64().AsWRegister()),
      MemOperand(reg_x(jni_env_reg.AsArm64().AsXRegister()), previous_state_offset.Int32Value()));
}

void Arm64JNIMacroAssembler::StoreLocalReferenceTableStates(ManagedRegister jni_env_reg,
                                                            ManagedRegister previous_state_reg,
                                                            ManagedRegister current_state_reg) {
  constexpr size_t kLRTSegmentStateSize = sizeof(jni::LRTSegmentState);
  DCHECK_EQ(kLRTSegmentStateSize, kWRegSizeInBytes);
  const MemberOffset previous_state_offset = JNIEnvExt::LrtPreviousStateOffset(kArm64PointerSize);
  const MemberOffset current_state_offset = JNIEnvExt::LrtSegmentStateOffset(kArm64PointerSize);
  DCHECK_EQ(previous_state_offset.SizeValue() + kLRTSegmentStateSize,
            current_state_offset.SizeValue());

  // Set the current segment state together with restoring the cookie.
  ___ Stp(
      reg_w(previous_state_reg.AsArm64().AsWRegister()),
      reg_w(current_state_reg.AsArm64().AsWRegister()),
      MemOperand(reg_x(jni_env_reg.AsArm64().AsXRegister()), previous_state_offset.Int32Value()));
}

#undef ___

}  // namespace arm64
}  // namespace art
