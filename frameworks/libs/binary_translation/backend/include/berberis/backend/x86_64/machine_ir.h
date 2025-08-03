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

// x86_64 machine IR interface.

#ifndef BERBERIS_BACKEND_X86_64_MACHINE_IR_H_
#define BERBERIS_BACKEND_X86_64_MACHINE_IR_H_

#include <cstdint>
#include <string>

#include "berberis/assembler/x86_64.h"
#include "berberis/backend/code_emitter.h"
#include "berberis/backend/common/machine_ir.h"
#include "berberis/base/arena_alloc.h"
#include "berberis/guest_state/guest_state_arch.h"

namespace berberis {

enum MachineOpcode : int {
  kMachineOpUndefined = 0,
  kMachineOpCallImm,
  kMachineOpCallImmArg,
  kMachineOpPseudoBranch,
  kMachineOpPseudoCondBranch,
  kMachineOpPseudoCopy,
  kMachineOpPseudoDefReg,
  kMachineOpPseudoDefXReg,
  kMachineOpPseudoIndirectJump,
  kMachineOpPseudoJump,
  kMachineOpPseudoReadFlags,
  kMachineOpPseudoWriteFlags,
#include "berberis/backend/x86_64/machine_opcode_guest-inl.h"
#include "machine_opcode_x86_64-inl.h"  // NOLINT generated file!
};

namespace x86_64 {

constexpr const MachineReg kMachineRegR8{1};
constexpr const MachineReg kMachineRegR9{2};
constexpr const MachineReg kMachineRegR10{3};
constexpr const MachineReg kMachineRegR11{4};
constexpr const MachineReg kMachineRegRSI{5};
constexpr const MachineReg kMachineRegRDI{6};
constexpr const MachineReg kMachineRegRAX{7};
constexpr const MachineReg kMachineRegRBX{8};
constexpr const MachineReg kMachineRegRCX{9};
constexpr const MachineReg kMachineRegRDX{10};
constexpr const MachineReg kMachineRegRBP{11};
constexpr const MachineReg kMachineRegRSP{12};
constexpr const MachineReg kMachineRegR12{13};
constexpr const MachineReg kMachineRegR13{14};
constexpr const MachineReg kMachineRegR14{15};
constexpr const MachineReg kMachineRegR15{16};
constexpr const MachineReg kMachineRegFLAGS{19};
constexpr const MachineReg kMachineRegXMM0{20};
constexpr const MachineReg kMachineRegXMM1{21};
constexpr const MachineReg kMachineRegXMM2{22};
constexpr const MachineReg kMachineRegXMM3{23};
constexpr const MachineReg kMachineRegXMM4{24};
constexpr const MachineReg kMachineRegXMM5{25};
constexpr const MachineReg kMachineRegXMM6{26};
constexpr const MachineReg kMachineRegXMM7{27};
constexpr const MachineReg kMachineRegXMM8{28};
constexpr const MachineReg kMachineRegXMM9{29};
constexpr const MachineReg kMachineRegXMM10{30};
constexpr const MachineReg kMachineRegXMM11{31};
constexpr const MachineReg kMachineRegXMM12{32};
constexpr const MachineReg kMachineRegXMM13{33};
constexpr const MachineReg kMachineRegXMM14{34};
constexpr const MachineReg kMachineRegXMM15{35};

inline bool IsGReg(MachineReg r) {
  return r.reg() >= kMachineRegR8.reg() && r.reg() <= kMachineRegR15.reg();
}

inline bool IsXReg(MachineReg r) {
  return r.reg() >= kMachineRegXMM0.reg() && r.reg() <= kMachineRegXMM15.reg();
}

// rax, rdi, rsi, rdx, rcx, r8-r11, xmm0-xmm15, flags
const int kMaxMachineRegOperands = 26;

// Context loads and stores use rbp as base.
const MachineReg kCPUStatePointer = kMachineRegRBP;

struct MachineInsnInfo {
  MachineOpcode opcode;
  int num_reg_operands;
  MachineRegKind reg_kinds[kMaxMachineRegOperands];
  MachineInsnKind kind;
};

enum class MachineMemOperandScale {
  kOne,
  kTwo,
  kFour,
  kEight,
};

#include "machine_reg_class_x86_64-inl.h"  // NOLINT generated file!

class MachineInsnX86_64 : public MachineInsn {
 public:
  static constexpr const auto kEAX = x86_64::kEAX;
  static constexpr const auto kRAX = x86_64::kRAX;
  static constexpr const auto kCL = x86_64::kCL;
  static constexpr const auto kECX = x86_64::kECX;
  static constexpr const auto kRCX = x86_64::kRCX;
  static constexpr const auto kEDX = x86_64::kEDX;
  static constexpr const auto kRDX = x86_64::kRDX;
  static constexpr const auto kGeneralReg8 = x86_64::kGeneralReg8;
  static constexpr const auto kGeneralReg16 = x86_64::kGeneralReg16;
  static constexpr const auto kGeneralReg32 = x86_64::kGeneralReg32;
  static constexpr const auto kGeneralReg64 = x86_64::kGeneralReg64;
  static constexpr const auto kFpReg32 = x86_64::kFpReg32;
  static constexpr const auto kFpReg64 = x86_64::kFpReg64;
  static constexpr const auto kVecReg128 = x86_64::kVecReg128;
  static constexpr const auto kXmmReg = x86_64::kXmmReg;
  static constexpr const auto kFLAGS = x86_64::kFLAGS;

  ~MachineInsnX86_64() override {
    // No code here - will never be called!
  }

  MachineMemOperandScale scale() const { return scale_; }

  uint32_t disp() const { return disp_; }

  Assembler::Condition cond() const { return cond_; }

  uint64_t imm() const { return imm_; }

  bool IsCPUStateGet() {
    if (opcode() != kMachineOpMovqRegMemBaseDisp && opcode() != kMachineOpMovdqaXRegMemBaseDisp &&
        opcode() != kMachineOpMovwRegMemBaseDisp && opcode() != kMachineOpMovsdXRegMemBaseDisp) {
      return false;
    }

    // Check that it is not for ThreadState fields outside of CPUState.
    if (disp() >= sizeof(CPUState)) {
      return false;
    }

    // reservation_value is loaded in HeavyOptimizerFrontend::AtomicLoad and written
    // in HeavyOptimizerFrontend::AtomicStore partially (for performance
    // reasons), which is not supported by our context optimizer.
    auto reservation_value_offset = offsetof(ThreadState, cpu.reservation_value);
    if (disp() >= reservation_value_offset &&
        disp() < reservation_value_offset + sizeof(Reservation)) {
      return false;
    }

    return RegAt(1) == kCPUStatePointer;
  }

  bool IsCPUStatePut() {
    if (opcode() != kMachineOpMovqMemBaseDispReg && opcode() != kMachineOpMovdqaMemBaseDispXReg &&
        opcode() != kMachineOpMovwMemBaseDispReg && opcode() != kMachineOpMovsdMemBaseDispXReg) {
      return false;
    }

    // Check that it is not for ThreadState fields outside of CPUState.
    if (disp() >= sizeof(CPUState)) {
      return false;
    }

    // reservation_value is loaded in HeavyOptimizerFrontend::AtomicLoad and written
    // in HeavyOptimizerFrontend::AtomicStore partially (for performance
    // reasons), which is not supported by our context optimizer.
    auto reservation_value_offset = offsetof(ThreadState, cpu.reservation_value);
    if (disp() >= reservation_value_offset &&
        disp() < reservation_value_offset + sizeof(Reservation)) {
      return false;
    }

    return RegAt(0) == kCPUStatePointer;
  }

 protected:
  explicit MachineInsnX86_64(const MachineInsnInfo* info)
      : MachineInsn(info->opcode, info->num_reg_operands, info->reg_kinds, regs_, info->kind),
        scale_(MachineMemOperandScale::kOne) {}

  void set_scale(MachineMemOperandScale scale) { scale_ = scale; }

  void set_disp(uint32_t disp) { disp_ = disp; }

  void set_cond(Assembler::Condition cond) { cond_ = cond; }

  void set_imm(uint64_t imm) { imm_ = imm; }

 private:
  MachineReg regs_[kMaxMachineRegOperands];
  MachineMemOperandScale scale_;
  uint32_t disp_;
  uint64_t imm_;
  Assembler::Condition cond_;
};

// Syntax sugar.
inline const MachineInsnX86_64* AsMachineInsnX86_64(const MachineInsn* insn) {
  return static_cast<const MachineInsnX86_64*>(insn);
}

inline MachineInsnX86_64* AsMachineInsnX86_64(MachineInsn* insn) {
  return static_cast<MachineInsnX86_64*>(insn);
}

// Clobbered registers are described as DEF'ed.
// TODO(b/232598137): implement simpler support for clobbered registers?
class CallImm : public MachineInsnX86_64 {
 public:
  enum class RegType {
    kIntType,
    kXmmType,
  };

  static constexpr RegType kIntRegType = RegType::kIntType;
  static constexpr RegType kXmmRegType = RegType::kXmmType;

  struct Arg {
    MachineReg reg;
    RegType reg_type;
  };

 public:
  explicit CallImm(uint64_t imm);

  [[nodiscard]] static int GetIntArgIndex(int i);
  [[nodiscard]] static int GetXmmArgIndex(int i);
  [[nodiscard]] static int GetFlagsArgIndex();

  [[nodiscard]] MachineReg IntResultAt(int i) const;
  [[nodiscard]] MachineReg XmmResultAt(int i) const;

  [[nodiscard]] std::string GetDebugString() const override;
  void Emit(CodeEmitter* as) const override;
};

// An auxiliary instruction to express data-flow for CallImm arguments.  It uses the same vreg as
// the corresponding operand in CallImm. The specific hard register assigned is defined by the
// register class of CallImm operand. MachineIRBuilder adds an extra PseudoCopy before this insn in
// case the same vreg holds values for several arguments (with non-intersecting register classes).
class CallImmArg : public MachineInsnX86_64 {
 public:
  explicit CallImmArg(MachineReg arg, CallImm::RegType reg_type);

  std::string GetDebugString() const override;
  void Emit(CodeEmitter*) const override{
      // It's an auxiliary instruction. Do not emit.
  };
};

// This template is syntax sugar to group memory instructions with
// different addressing modes.
template <typename Absolute_, typename BaseDisp_, typename IndexDisp_, typename BaseIndexDisp_>
class MemInsns {
 public:
  using Absolute = Absolute_;
  using BaseDisp = BaseDisp_;
  using IndexDisp = IndexDisp_;
  using BaseIndexDisp = BaseIndexDisp_;
};

using MachineInsnForArch = MachineInsnX86_64;

#include "gen_machine_ir_x86_64-inl.h"  // NOLINT generated file!

class MachineInfo {
 public:
#include "machine_info_x86_64-inl.h"  // NOLINT generated file!
};

class MachineIR : public berberis::MachineIR {
 public:
  enum class BasicBlockOrder {
    kUnordered,
    kReversePostOrder,
  };

  explicit MachineIR(Arena* arena, int num_vreg = 0)
      : berberis::MachineIR(arena, num_vreg, 0), bb_order_(BasicBlockOrder::kUnordered) {}

  void AddEdge(MachineBasicBlock* src, MachineBasicBlock* dst) {
    MachineEdge* edge = NewInArena<MachineEdge>(arena(), arena(), src, dst);
    src->out_edges().push_back(edge);
    dst->in_edges().push_back(edge);
    bb_order_ = BasicBlockOrder::kUnordered;
  }

  [[nodiscard]] MachineBasicBlock* NewBasicBlock() {
    return NewInArena<MachineBasicBlock>(arena(), arena(), ReserveBasicBlockId());
  }

  // Instruction iterators are preserved after splitting basic block and moving
  // instructions to the new basic block.
  [[nodiscard]] MachineBasicBlock* SplitBasicBlock(MachineBasicBlock* bb,
                                                   MachineInsnList::iterator insn_it) {
    MachineBasicBlock* new_bb = NewBasicBlock();

    new_bb->insn_list().splice(
        new_bb->insn_list().begin(), bb->insn_list(), insn_it, bb->insn_list().end());
    bb->insn_list().push_back(NewInsn<PseudoBranch>(new_bb));

    // Relink out edges from bb.
    for (auto out_edge : bb->out_edges()) {
      out_edge->set_src(new_bb);
    }
    new_bb->out_edges().swap(bb->out_edges());

    AddEdge(bb, new_bb);
    bb_list().push_back(new_bb);
    return new_bb;
  }

  [[nodiscard]] static bool IsControlTransfer(MachineInsn* insn) {
    return insn->opcode() == kMachineOpPseudoBranch ||
           insn->opcode() == kMachineOpPseudoCondBranch ||
           insn->opcode() == kMachineOpPseudoIndirectJump || insn->opcode() == kMachineOpPseudoJump;
  }

  [[nodiscard]] BasicBlockOrder bb_order() const { return bb_order_; }

  void set_bb_order(BasicBlockOrder order) { bb_order_ = order; }

 private:
  BasicBlockOrder bb_order_;
};

}  // namespace x86_64

}  // namespace berberis

#endif  // BERBERIS_BACKEND_X86_64_MACHINE_IR_H_
