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

#include "berberis/backend/x86_64/insn_folding.h"

#include <cstdint>
#include <tuple>

#include "berberis/backend/common/machine_ir.h"
#include "berberis/backend/x86_64/machine_ir.h"

#include "berberis/backend/code_emitter.h"  // for CodeEmitter::Condition
#include "berberis/base/algorithm.h"
#include "berberis/base/bit_util.h"
#include "berberis/base/logging.h"

namespace berberis::x86_64 {

void DefMap::MapDefRegs(const MachineInsn* insn) {
  for (int op = 0; op < insn->NumRegOperands(); ++op) {
    MachineReg reg = insn->RegAt(op);
    if (insn->RegKindAt(op).RegClass()->IsSubsetOf(&x86_64::kFLAGS)) {
      if (flags_reg_ == kInvalidMachineReg) {
        flags_reg_ = reg;
      }
      // Some optimizations assume flags is the same virtual register everywhere.
      CHECK(reg == flags_reg_);
    }
    if (insn->RegKindAt(op).IsDef()) {
      Set(reg, insn);
    }
  }
}

void DefMap::ProcessInsn(const MachineInsn* insn) {
  MapDefRegs(insn);
  ++index_;
}

void DefMap::Initialize() {
  std::fill(def_map_.begin(), def_map_.end(), std::pair(nullptr, 0));
  flags_reg_ = kInvalidMachineReg;
  index_ = 0;
}

bool InsnFolding::IsRegImm(MachineReg reg, uint64_t* imm) const {
  auto [general_insn, _] = def_map_.Get(reg);
  if (!general_insn) {
    return false;
  }
  const auto* insn = AsMachineInsnX86_64(general_insn);
  if (insn->opcode() == kMachineOpMovqRegImm) {
    *imm = insn->imm();
    return true;
  } else if (insn->opcode() == kMachineOpMovlRegImm) {
    // Take into account zero-extension by MOVL.
    *imm = static_cast<uint64_t>(static_cast<uint32_t>(insn->imm()));
    return true;
  }
  return false;
}

MachineInsn* InsnFolding::NewImmInsnFromRegInsn(const MachineInsn* insn, int32_t imm32) {
  MachineInsn* folded_insn;
  switch (insn->opcode()) {
    case kMachineOpAddqRegReg:
      folded_insn = machine_ir_->NewInsn<AddqRegImm>(insn->RegAt(0), imm32, insn->RegAt(2));
      break;
    case kMachineOpSubqRegReg:
      folded_insn = machine_ir_->NewInsn<SubqRegImm>(insn->RegAt(0), imm32, insn->RegAt(2));
      break;
    case kMachineOpCmpqRegReg:
      folded_insn = machine_ir_->NewInsn<CmpqRegImm>(insn->RegAt(0), imm32, insn->RegAt(2));
      break;
    case kMachineOpOrqRegReg:
      folded_insn = machine_ir_->NewInsn<OrqRegImm>(insn->RegAt(0), imm32, insn->RegAt(2));
      break;
    case kMachineOpXorqRegReg:
      folded_insn = machine_ir_->NewInsn<XorqRegImm>(insn->RegAt(0), imm32, insn->RegAt(2));
      break;
    case kMachineOpAndqRegReg:
      folded_insn = machine_ir_->NewInsn<AndqRegImm>(insn->RegAt(0), imm32, insn->RegAt(2));
      break;
    case kMachineOpTestqRegReg:
      folded_insn = machine_ir_->NewInsn<TestqRegImm>(insn->RegAt(0), imm32, insn->RegAt(2));
      break;
    case kMachineOpMovlRegReg:
      folded_insn = machine_ir_->NewInsn<MovlRegImm>(insn->RegAt(0), imm32);
      break;
    case kMachineOpAddlRegReg:
      folded_insn = machine_ir_->NewInsn<AddlRegImm>(insn->RegAt(0), imm32, insn->RegAt(2));
      break;
    case kMachineOpSublRegReg:
      folded_insn = machine_ir_->NewInsn<SublRegImm>(insn->RegAt(0), imm32, insn->RegAt(2));
      break;
    case kMachineOpCmplRegReg:
      folded_insn = machine_ir_->NewInsn<CmplRegImm>(insn->RegAt(0), imm32, insn->RegAt(2));
      break;
    case kMachineOpOrlRegReg:
      folded_insn = machine_ir_->NewInsn<OrlRegImm>(insn->RegAt(0), imm32, insn->RegAt(2));
      break;
    case kMachineOpXorlRegReg:
      folded_insn = machine_ir_->NewInsn<XorlRegImm>(insn->RegAt(0), imm32, insn->RegAt(2));
      break;
    case kMachineOpAndlRegReg:
      folded_insn = machine_ir_->NewInsn<AndlRegImm>(insn->RegAt(0), imm32, insn->RegAt(2));
      break;
    case kMachineOpTestlRegReg:
      folded_insn = machine_ir_->NewInsn<TestlRegImm>(insn->RegAt(0), imm32, insn->RegAt(2));
      break;
    case kMachineOpMovlMemBaseDispReg:
      folded_insn = machine_ir_->NewInsn<MovlMemBaseDispImm>(
          insn->RegAt(0), AsMachineInsnX86_64(insn)->disp(), imm32);
      break;
    case kMachineOpMovqMemBaseDispReg:
      folded_insn = machine_ir_->NewInsn<MovqMemBaseDispImm>(
          insn->RegAt(0), AsMachineInsnX86_64(insn)->disp(), imm32);
      break;
    default:
      LOG_ALWAYS_FATAL("unexpected opcode");
  }
  // Inherit the additional attributes.
  folded_insn->set_recovery_bb(insn->recovery_bb());
  folded_insn->set_recovery_pc(insn->recovery_pc());
  return folded_insn;
}

bool InsnFolding::IsWritingSameFlagsValue(const MachineInsn* write_flags_insn) const {
  CHECK(write_flags_insn && write_flags_insn->opcode() == kMachineOpPseudoWriteFlags);
  MachineReg src_reg = write_flags_insn->RegAt(0);
  auto [def_insn, def_insn_pos] = def_map_.Get(src_reg);
  // Warning: We are assuming that all flags writes in IR happen to the same virtual register.
  while (true) {
    if (!def_insn) {
      return false;
    }

    int opcode = def_insn->opcode();
    if (opcode == kMachineOpPseudoCopy) {
      src_reg = def_insn->RegAt(1);
      std::tie(def_insn, def_insn_pos) = def_map_.Get(src_reg, def_insn_pos);
      continue;
    } else if (opcode == kMachineOpPseudoReadFlags) {
      break;
    }
    return false;
  }

  // Instruction is PseudoReadFlags.
  if (write_flags_insn->RegAt(1) != def_insn->RegAt(1)) {
    return false;
  }
  auto [flag_def_insn, _] = def_map_.Get(write_flags_insn->RegAt(1), def_insn_pos);
  return flag_def_insn != nullptr;
}

template <bool is_input_64bit>
std::tuple<bool, MachineInsn*> InsnFolding::TryFoldImmediateInput(const MachineInsn* insn) {
  auto src = insn->RegAt(1);
  uint64_t imm64;
  if (!IsRegImm(src, &imm64)) {
    return {false, nullptr};
  }

  // MovqRegReg is the only instruction that can encode full 64-bit immediate.
  if (insn->opcode() == kMachineOpMovqRegReg) {
    return {true, machine_ir_->NewInsn<MovqRegImm>(insn->RegAt(0), imm64)};
  }

  int64_t signed_imm = bit_cast<int64_t>(imm64);
  int32_t signed_imm32 = static_cast<int32_t>(signed_imm);
  if (!is_input_64bit) {
    // Use the lower half of the register as the immediate operand.
    return {true, NewImmInsnFromRegInsn(insn, signed_imm32)};
  }

  // Except for MOVQ x86 doesn't allow to encode 64-bit immediates. That said,
  // we can encode 32-bit immediates that are sign-extended by hardware to
  // 64-bit during instruction execution.
  if (signed_imm == static_cast<int64_t>(signed_imm32)) {
    return {true, NewImmInsnFromRegInsn(insn, signed_imm32)};
  }

  return {false, nullptr};
}

std::tuple<bool, MachineInsn*> InsnFolding::TryFoldRedundantMovl(const MachineInsn* insn) {
  CHECK_EQ(insn->opcode(), kMachineOpMovlRegReg);
  auto src = insn->RegAt(1);
  auto [def_insn, _] = def_map_.Get(src);

  if (!def_insn) {
    return {false, nullptr};
  }

  // If the definition of src clears its upper half, then we can replace MOVL with PseudoCopy.
  switch (def_insn->opcode()) {
    case kMachineOpMovlRegReg:
    case kMachineOpAndlRegReg:
    case kMachineOpXorlRegReg:
    case kMachineOpOrlRegReg:
    case kMachineOpSublRegReg:
    case kMachineOpAddlRegReg:
      return {true, machine_ir_->NewInsn<PseudoCopy>(insn->RegAt(0), src, 4)};
    default:
      return {false, nullptr};
  }
}

std::tuple<bool, MachineInsn*> InsnFolding::TryFoldInsn(const MachineInsn* insn) {
  switch (insn->opcode()) {
    case kMachineOpMovqMemBaseDispReg:
    case kMachineOpMovqRegReg:
    case kMachineOpAndqRegReg:
    case kMachineOpTestqRegReg:
    case kMachineOpXorqRegReg:
    case kMachineOpOrqRegReg:
    case kMachineOpSubqRegReg:
    case kMachineOpCmpqRegReg:
    case kMachineOpAddqRegReg:
      return TryFoldImmediateInput<true>(insn);
    case kMachineOpMovlRegReg: {
      auto [is_folded, folded_insn] = TryFoldImmediateInput<false>(insn);
      if (is_folded) {
        return {is_folded, folded_insn};
      }

      return TryFoldRedundantMovl(insn);
    }
    case kMachineOpMovlMemBaseDispReg:
    case kMachineOpAndlRegReg:
    case kMachineOpTestlRegReg:
    case kMachineOpXorlRegReg:
    case kMachineOpOrlRegReg:
    case kMachineOpSublRegReg:
    case kMachineOpCmplRegReg:
    case kMachineOpAddlRegReg:
      return TryFoldImmediateInput<false>(insn);
    case kMachineOpPseudoWriteFlags: {
      if (IsWritingSameFlagsValue(insn)) {
        return {true, nullptr};
      }
      break;
    }
    default:
      return {false, nullptr};
  }
  return {false, nullptr};
}

void FoldInsns(MachineIR* machine_ir) {
  DefMap def_map(machine_ir->NumVReg(), machine_ir->arena());
  for (auto* bb : machine_ir->bb_list()) {
    def_map.Initialize();
    InsnFolding insn_folding(def_map, machine_ir);
    MachineInsnList& insn_list = bb->insn_list();

    for (auto insn_it = insn_list.begin(); insn_it != insn_list.end();) {
      auto [is_folded, new_insn] = insn_folding.TryFoldInsn(*insn_it);

      if (is_folded) {
        insn_it = insn_list.erase(insn_it);
        if (new_insn) {
          insn_list.insert(insn_it, new_insn);
          def_map.ProcessInsn(new_insn);
        }
      } else {
        def_map.ProcessInsn(*insn_it);
        ++insn_it;
      }
    }
  }
}

// TODO(b/179708579): Maybe combine with FoldInsns.
void FoldWriteFlags(MachineIR* machine_ir) {
  for (auto* bb : machine_ir->bb_list()) {
    CHECK(!bb->insn_list().empty());
    auto insn_it = std::prev(bb->insn_list().end());
    if ((*insn_it)->opcode() != kMachineOpPseudoCondBranch) {
      continue;
    }

    auto* branch = static_cast<PseudoCondBranch*>(*insn_it);
    const auto* write_flags = *(--insn_it);
    if (write_flags->opcode() != kMachineOpPseudoWriteFlags) {
      continue;
    }
    // There is only one flags register, so CondBranch must read flags from WriteFlags.
    MachineReg flags = write_flags->RegAt(1);
    CHECK_EQ(flags.reg(), branch->RegAt(0).reg());

    const auto& live_out = bb->live_out();
    if (Contains(live_out, flags)) {
      // Flags are living-out. Cannot remove.
      // TODO(b/179708579): This shouldn't happen. Consider conversion to an assert.
      continue;
    }

    using Cond = CodeEmitter::Condition;
    Cond new_cond = Cond::kInvalidCondition;
    PseudoWriteFlags::Flags flags_mask;

    switch (branch->cond()) {
      // Verify that the flags are within the bottom 16 bits, so we can use Testw.
      static_assert(sizeof(PseudoWriteFlags::Flags) == 2);
      case Cond::kZero:
        new_cond = Cond::kNotZero;
        flags_mask = PseudoWriteFlags::Flags::kZero;
        break;
      case Cond::kNotZero:
        new_cond = Cond::kZero;
        flags_mask = PseudoWriteFlags::Flags::kZero;
        break;
      case Cond::kCarry:
        new_cond = Cond::kNotZero;
        flags_mask = PseudoWriteFlags::Flags::kCarry;
        break;
      case Cond::kNotCarry:
        new_cond = Cond::kZero;
        flags_mask = PseudoWriteFlags::Flags::kCarry;
        break;
      case Cond::kNegative:
        new_cond = Cond::kNotZero;
        flags_mask = PseudoWriteFlags::Flags::kNegative;
        break;
      case Cond::kNotSign:
        new_cond = Cond::kZero;
        flags_mask = PseudoWriteFlags::Flags::kNegative;
        break;
      case Cond::kOverflow:
        new_cond = Cond::kNotZero;
        flags_mask = PseudoWriteFlags::Flags::kOverflow;
        break;
      case Cond::kNoOverflow:
        new_cond = Cond::kZero;
        flags_mask = PseudoWriteFlags::Flags::kOverflow;
        break;
      default:
        continue;
    }

    MachineReg flags_src = write_flags->RegAt(0);
    MachineInsn* new_write_flags =
        machine_ir->NewInsn<x86_64::TestwRegImm>(flags_src, flags_mask, flags);
    insn_it = bb->insn_list().erase(insn_it);
    bb->insn_list().insert(insn_it, new_write_flags);
    branch->set_cond(new_cond);
  }
}

}  // namespace berberis::x86_64
