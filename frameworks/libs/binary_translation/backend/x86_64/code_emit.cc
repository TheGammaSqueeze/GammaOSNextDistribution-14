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

#include "berberis/backend/x86_64/code_emit.h"

#include <iterator>  // std::next
#include <utility>

#include "berberis/assembler/x86_64.h"
#include "berberis/backend/code_emitter.h"
#include "berberis/backend/x86_64/machine_ir.h"
#include "berberis/base/arena_vector.h"
#include "berberis/base/logging.h"
#include "berberis/code_gen_lib/code_gen_lib.h"
#include "berberis/guest_state/guest_addr.h"
#include "berberis/runtime_primitives/host_code.h"  // AsHostCode

namespace berberis {

using Assembler = x86_64::Assembler;

namespace x86_64 {

namespace {

void EmitMovGRegGReg(CodeEmitter* as, MachineReg dst, MachineReg src, int /* size */) {
  as->Movq(GetGReg(dst), GetGReg(src));
}

void EmitMovGRegXReg(CodeEmitter* as, MachineReg dst, MachineReg src, int /* size */) {
  as->Movq(GetGReg(dst), GetXReg(src));
}

void EmitMovGRegMem(CodeEmitter* as, MachineReg dst, MachineReg src, int /* size */) {
  // TODO(b/207399902): Make this cast safe
  int offset = static_cast<int>(src.GetSpilledRegIndex());
  as->Movq(GetGReg(dst), {.base = Assembler::rsp, .disp = offset});
}

void EmitMovXRegGReg(CodeEmitter* as, MachineReg dst, MachineReg src, int /* size */) {
  as->Movq(GetXReg(dst), GetGReg(src));
}

void EmitMovXRegXReg(CodeEmitter* as, MachineReg dst, MachineReg src, int /* size */) {
  as->Pmov(GetXReg(dst), GetXReg(src));
}

void EmitMovXRegMem(CodeEmitter* as, MachineReg dst, MachineReg src, int size) {
  // TODO(b/207399902): Make this cast safe
  int offset = static_cast<int>(src.GetSpilledRegIndex());
  if (size > 8) {
    as->MovdquXRegMemBaseDisp(GetXReg(dst), Assembler::rsp, offset);
  } else if (size > 4) {
    as->MovsdXRegMemBaseDisp(GetXReg(dst), Assembler::rsp, offset);
  } else {
    as->Movss(GetXReg(dst), {.base = Assembler::rsp, .disp = offset});
  }
}

void EmitMovMemGReg(CodeEmitter* as, MachineReg dst, MachineReg src, int /* size */) {
  // TODO(b/207399902): Make this cast safe
  int offset = static_cast<int>(dst.GetSpilledRegIndex());
  as->Movq({.base = Assembler::rsp, .disp = offset}, GetGReg(src));
}

void EmitMovMemXReg(CodeEmitter* as, MachineReg dst, MachineReg src, int size) {
  // TODO(b/207399902): Make this cast safe
  int offset = static_cast<int>(dst.GetSpilledRegIndex());
  if (size > 8) {
    as->MovdquMemBaseDispXReg(Assembler::rsp, offset, GetXReg(src));
  } else if (size > 4) {
    as->MovsdMemBaseDispXReg(Assembler::rsp, offset, GetXReg(src));
  } else {
    as->Movss({.base = Assembler::rsp, .disp = offset}, GetXReg(src));
  }
}

void EmitMovMemMem(CodeEmitter* as, MachineReg dst, MachineReg src, int size) {
  // ATTENTION: memory to memory copy, very inefficient!
  // TODO(b/207399902): Make this cast safe
  int dst_offset = static_cast<int>(dst.GetSpilledRegIndex());
  int src_offset = static_cast<int>(src.GetSpilledRegIndex());
  for (int part = 0; part < size; part += 8) {
    // offset BEFORE rsp decr!
    as->Pushq({.base = Assembler::rsp, .disp = src_offset + part});
    // offset AFTER rsp incr!
    as->Popq({.base = Assembler::rsp, .disp = dst_offset + part});
  }
}

void EmitCopy(CodeEmitter* as, MachineReg dst, MachineReg src, int size) {
  if (dst.IsSpilledReg()) {
    if (src.IsSpilledReg()) {
      EmitMovMemMem(as, dst, src, size);
    } else if (IsXReg(src)) {
      EmitMovMemXReg(as, dst, src, size);
    } else {
      EmitMovMemGReg(as, dst, src, size);
    }
  } else if (IsXReg(dst)) {
    if (src.IsSpilledReg()) {
      EmitMovXRegMem(as, dst, src, size);
    } else if (IsXReg(src)) {
      EmitMovXRegXReg(as, dst, src, size);
    } else {
      EmitMovXRegGReg(as, dst, src, size);
    }
  } else {
    if (src.IsSpilledReg()) {
      EmitMovGRegMem(as, dst, src, size);
    } else if (IsXReg(src)) {
      EmitMovGRegXReg(as, dst, src, size);
    } else {
      EmitMovGRegGReg(as, dst, src, size);
    }
  }
}

using RecoveryLabels = ArenaVector<std::pair<CodeEmitter::Label*, GuestAddr>>;

void EmitRecoveryLabels(CodeEmitter* as, const RecoveryLabels& labels) {
  if (labels.empty()) {
    return;
  }

  auto* exit_label = as->MakeLabel();

  for (auto pair : labels) {
    as->Bind(pair.first);
    // EmitExitGeneratedCode is more efficient if receives target in rax.
    as->Movq(as->rax, pair.second);
    // Exit uses Jmp to full 64-bit address and is 14 bytes long, which is expensive.
    // Thus we generate local relative jump to the common exit label here.
    // It's up to 5 bytes, but likely 2-bytes since distance is expected to be short.
    as->Jmp(*exit_label);
  }

  as->Bind(exit_label);

  if (as->exit_label_for_testing()) {
    as->Jmp(*as->exit_label_for_testing());
    return;
  }

  EmitExitGeneratedCode(as, as->rax);
}

}  // namespace

Assembler::Register GetGReg(MachineReg r) {
  static constexpr Assembler::Register kHardRegs[] = {Assembler::no_register,
                                                      Assembler::r8,
                                                      Assembler::r9,
                                                      Assembler::r10,
                                                      Assembler::r11,
                                                      Assembler::rsi,
                                                      Assembler::rdi,
                                                      Assembler::rax,
                                                      Assembler::rbx,
                                                      Assembler::rcx,
                                                      Assembler::rdx,
                                                      Assembler::rbp,
                                                      Assembler::rsp,
                                                      Assembler::r12,
                                                      Assembler::r13,
                                                      Assembler::r14,
                                                      Assembler::r15};
  CHECK_LT(static_cast<unsigned>(r.reg()), std::size(kHardRegs));
  return kHardRegs[r.reg()];
}

Assembler::XMMRegister GetXReg(MachineReg r) {
  static constexpr Assembler::XMMRegister kHardRegs[] = {
      Assembler::xmm0,
      Assembler::xmm1,
      Assembler::xmm2,
      Assembler::xmm3,
      Assembler::xmm4,
      Assembler::xmm5,
      Assembler::xmm6,
      Assembler::xmm7,
      Assembler::xmm8,
      Assembler::xmm9,
      Assembler::xmm10,
      Assembler::xmm11,
      Assembler::xmm12,
      Assembler::xmm13,
      Assembler::xmm14,
      Assembler::xmm15,
  };
  CHECK_GE(r.reg(), kMachineRegXMM0.reg());
  CHECK_LT(static_cast<unsigned>(r.reg() - kMachineRegXMM0.reg()), std::size(kHardRegs));
  return kHardRegs[r.reg() - kMachineRegXMM0.reg()];
}

Assembler::ScaleFactor ToScaleFactor(MachineMemOperandScale scale) {
  switch (scale) {
    case MachineMemOperandScale::kOne:
      return Assembler::kTimesOne;
    case MachineMemOperandScale::kTwo:
      return Assembler::kTimesTwo;
    case MachineMemOperandScale::kFour:
      return Assembler::kTimesFour;
    case MachineMemOperandScale::kEight:
      return Assembler::kTimesEight;
  }
}

void CallImm::Emit(CodeEmitter* as) const {
  as->Call(AsHostCode(imm()));
}

}  // namespace x86_64

void PseudoBranch::Emit(CodeEmitter* as) const {
  const Assembler::Label* then_label = as->GetLabelAt(then_bb()->id());

  if (as->next_label() == then_label) {
    // We do not need to emit any instruction as we fall through to
    // the next basic block.
    return;
  }

  as->Jmp(*then_label);
}

void PseudoCondBranch::Emit(CodeEmitter* as) const {
  const Assembler::Label* then_label = as->GetLabelAt(then_bb()->id());
  const Assembler::Label* else_label = as->GetLabelAt(else_bb()->id());

  if (as->next_label() == else_label) {
    // We do not need to emit JMP as our "else" arm falls through to
    // the next basic block.
    as->Jcc(cond_, *then_label);
  } else if (as->next_label() == then_label) {
    // Reverse the condition and emit Jcc to else_label().  We do not
    // need to emit JMP as our original (that is, before reversing)
    // "then" arm falls through to the next basic block.
    as->Jcc(ToReverseCond(cond()), *else_label);
  } else {
    // Neither our "then" nor "else" arm falls through to the next
    // basic block.  We need to emit both Jcc and Jmp.
    as->Jcc(cond(), *then_label);
    as->Jmp(*else_label);
  }
}

void PseudoJump::Emit(CodeEmitter* as) const {
  EmitFreeStackFrame(as, as->frame_size());

  if (as->exit_label_for_testing()) {
    as->Movq(as->rax, target_);
    as->Jmp(*as->exit_label_for_testing());
    return;
  }

  switch (kind_) {
    case Kind::kJumpWithPendingSignalsCheck:
      EmitDirectDispatch(as, target_, true);
      break;
    case Kind::kJumpWithoutPendingSignalsCheck:
      EmitDirectDispatch(as, target_, false);
      break;
    case Kind::kSyscall:
      EmitSyscall(as, target_);
      break;
    case Kind::kExitGeneratedCode:
      as->Movq(as->rax, target_);
      EmitExitGeneratedCode(as, as->rax);
      break;
  }
}

void PseudoIndirectJump::Emit(CodeEmitter* as) const {
  EmitFreeStackFrame(as, as->frame_size());
  if (as->exit_label_for_testing()) {
    as->Movq(as->rax, x86_64::GetGReg(RegAt(0)));
    as->Jmp(*as->exit_label_for_testing());
    return;
  }
  EmitIndirectDispatch(as, x86_64::GetGReg(RegAt(0)));
}

void PseudoCopy::Emit(CodeEmitter* as) const {
  MachineReg dst = RegAt(0);
  MachineReg src = RegAt(1);
  if (src == dst) {
    return;
  }
  // Operands should have equal register classes!
  CHECK_EQ(RegKindAt(0).RegClass(), RegKindAt(1).RegClass());
  // TODO(b/232598137): Why get size by class then pick insn by size instead of pick insn by class?
  int size = RegKindAt(0).RegClass()->RegSize();
  x86_64::EmitCopy(as, dst, src, size);
}

void PseudoReadFlags::Emit(CodeEmitter* as) const {
  as->Lahf();
  if (with_overflow()) {
    as->Setcc(CodeEmitter::Condition::kOverflow, as->rax);
  } else {
    // Still need to fill overflow with zero.
    as->Movb(as->rax, int8_t{0});
  }
}

void PseudoWriteFlags::Emit(CodeEmitter* as) const {
  as->Addb(as->rax, int8_t{0x7f});
  as->Sahf();
}

void MachineIR::Emit(CodeEmitter* as) const {
  EmitAllocStackFrame(as, as->frame_size());
  ArenaVector<std::pair<CodeEmitter::Label*, GuestAddr>> recovery_labels(arena());

  for (auto bb_it = bb_list().begin(); bb_it != bb_list().end(); ++bb_it) {
    const MachineBasicBlock* bb = *bb_it;
    as->Bind(as->GetLabelAt(bb->id()));

    // Let CodeEmitter know the label of the next basic block, if any.
    // This label can be used e.g. used by PseudoBranch and
    // PseudoCondBranch to avoid generating jumps to the next basic
    // block.
    auto next_bb_it = std::next(bb_it);
    if (next_bb_it == bb_list().end()) {
      as->set_next_label(nullptr);
    } else {
      as->set_next_label(as->GetLabelAt((*next_bb_it)->id()));
    }

    for (const auto* insn : bb->insn_list()) {
      if (insn->recovery_bb()) {
        as->SetRecoveryPoint(as->GetLabelAt(insn->recovery_bb()->id()));
      } else if (insn->recovery_pc() != kNullGuestAddr) {
        auto* label = as->MakeLabel();
        as->SetRecoveryPoint(label);
        recovery_labels.push_back(std::make_pair(label, insn->recovery_pc()));
      }
      insn->Emit(as);
    }
  }

  x86_64::EmitRecoveryLabels(as, recovery_labels);
}

}  // namespace berberis
