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

#ifndef BERBERIS_HEAVY_OPTIMIZER_RISCV64_FRONTEND_H_
#define BERBERIS_HEAVY_OPTIMIZER_RISCV64_FRONTEND_H_

#include "berberis/backend/x86_64/machine_ir.h"
#include "berberis/backend/x86_64/machine_ir_builder.h"
#include "berberis/base/arena_map.h"
#include "berberis/base/checks.h"
#include "berberis/base/dependent_false.h"
#include "berberis/decoder/riscv64/decoder.h"
#include "berberis/decoder/riscv64/semantics_player.h"
#include "berberis/guest_state/guest_addr.h"
#include "berberis/guest_state/guest_state_arch.h"
#include "berberis/guest_state/guest_state_opaque.h"
#include "berberis/intrinsics/intrinsics.h"
#include "berberis/intrinsics/macro_assembler.h"
#include "berberis/runtime_primitives/memory_region_reservation.h"
#include "berberis/runtime_primitives/platform.h"

#include "call_intrinsic.h"
#include "inline_intrinsic.h"
#include "simd_register.h"

namespace berberis {

class HeavyOptimizerFrontend {
 public:
  using CsrName = berberis::CsrName;
  using Decoder = Decoder<SemanticsPlayer<HeavyOptimizerFrontend>>;
  using Register = MachineReg;
  using FpRegister = SimdReg;
  using Float32 = intrinsics::Float32;
  using Float64 = intrinsics::Float64;

  struct MemoryOperand {
    Register base{0};
    // We call the following field "index" even though we do not scale it at the
    // moment.  We can add a scale as the need arises.
    Register index{0};
    uint64_t disp = 0;
  };

  explicit HeavyOptimizerFrontend(x86_64::MachineIR* machine_ir, GuestAddr pc)
      : pc_(pc),
        success_(true),
        builder_(machine_ir),
        flag_register_(machine_ir->AllocVReg()),
        is_uncond_branch_(false),
        branch_targets_(machine_ir->arena()) {
    StartRegion();
  }

  void CompareAndBranch(Decoder::BranchOpcode opcode, Register arg1, Register arg2, int16_t offset);
  void Branch(int32_t offset);
  void BranchRegister(Register base, int16_t offset);

  [[nodiscard]] Register GetImm(uint64_t imm);
  [[nodiscard]] Register Copy(Register value) {
    Register result = AllocTempReg();
    Gen<PseudoCopy>(result, value, 8);
    return result;
  }

  [[nodiscard]] Register GetReg(uint8_t reg);
  void SetReg(uint8_t reg, Register value);

  void Unimplemented();
  //
  // Instruction implementations.
  //
  void Nop();
  Register Op(Decoder::OpOpcode opcode, Register arg1, Register arg2);
  Register Op32(Decoder::Op32Opcode opcode, Register arg1, Register arg2);
  Register OpImm(Decoder::OpImmOpcode opcode, Register arg, int16_t imm);
  Register OpImm32(Decoder::OpImm32Opcode opcode, Register arg, int16_t imm);
  Register Slli(Register arg, int8_t imm);
  Register Srli(Register arg, int8_t imm);
  Register Srai(Register arg, int8_t imm);
  Register ShiftImm32(Decoder::ShiftImm32Opcode opcode, Register arg, uint16_t imm);
  Register Rori(Register arg, int8_t shamt);
  Register Roriw(Register arg, int8_t shamt);
  Register Lui(int32_t imm);
  Register Auipc(int32_t imm);

  Register Ecall(Register /* syscall_nr */,
                 Register /* arg0 */,
                 Register /* arg1 */,
                 Register /* arg2 */,
                 Register /* arg3 */,
                 Register /* arg4 */,
                 Register /* arg5 */) {
    Unimplemented();
    return {};
  }

  void Store(Decoder::MemoryDataOperandType operand_type,
             Register arg,
             int16_t offset,
             Register data);
  Register Load(Decoder::LoadOperandType operand_type, Register arg, int16_t offset);

  template <typename IntType>
  constexpr Decoder::LoadOperandType ToLoadOperandType() {
    if constexpr (std::is_same_v<IntType, int8_t>) {
      return Decoder::LoadOperandType::k8bitSigned;
    } else if constexpr (std::is_same_v<IntType, int16_t>) {
      return Decoder::LoadOperandType::k16bitSigned;
    } else if constexpr (std::is_same_v<IntType, int32_t>) {
      return Decoder::LoadOperandType::k32bitSigned;
    } else if constexpr (std::is_same_v<IntType, int64_t> || std::is_same_v<IntType, uint64_t>) {
      return Decoder::LoadOperandType::k64bit;
    } else if constexpr (std::is_same_v<IntType, uint8_t>) {
      return Decoder::LoadOperandType::k8bitUnsigned;
    } else if constexpr (std::is_same_v<IntType, uint16_t>) {
      return Decoder::LoadOperandType::k16bitUnsigned;
    } else if constexpr (std::is_same_v<IntType, uint32_t>) {
      return Decoder::LoadOperandType::k32bitUnsigned;
    } else {
      static_assert(kDependentTypeFalse<IntType>);
    }
  }

  template <typename IntType>
  constexpr Decoder::MemoryDataOperandType ToMemoryDataOperandType() {
    if constexpr (std::is_same_v<IntType, int8_t> || std::is_same_v<IntType, uint8_t>) {
      return Decoder::MemoryDataOperandType::k8bit;
    } else if constexpr (std::is_same_v<IntType, int16_t> || std::is_same_v<IntType, uint16_t>) {
      return Decoder::MemoryDataOperandType::k16bit;
    } else if constexpr (std::is_same_v<IntType, int32_t> || std::is_same_v<IntType, uint32_t>) {
      return Decoder::MemoryDataOperandType::k32bit;
    } else if constexpr (std::is_same_v<IntType, int64_t> || std::is_same_v<IntType, uint64_t>) {
      return Decoder::MemoryDataOperandType::k64bit;
    } else {
      static_assert(kDependentTypeFalse<IntType>);
    }
  }

  // Versions without recovery can be used to access non-guest memory (e.g. CPUState).
  Register LoadWithoutRecovery(Decoder::LoadOperandType operand_type, Register base, int32_t disp);
  Register LoadWithoutRecovery(Decoder::LoadOperandType operand_type,
                               Register base,
                               Register index,
                               int32_t disp);
  void StoreWithoutRecovery(Decoder::MemoryDataOperandType operand_type,
                            Register base,
                            int32_t disp,
                            Register val);
  void StoreWithoutRecovery(Decoder::MemoryDataOperandType operand_type,
                            Register base,
                            Register index,
                            int32_t disp,
                            Register val);

  //
  // Atomic extensions.
  //

  template <typename IntType, bool aq, bool rl>
  Register Lr(Register addr) {
    Register aligned_addr = AllocTempReg();
    Gen<PseudoCopy>(aligned_addr, addr, 8);
    // The immediate is sign extended to 64-bit.
    Gen<x86_64::AndqRegImm>(aligned_addr, ~int32_t{sizeof(Reservation) - 1}, GetFlagsRegister());

    MemoryRegionReservationLoad(aligned_addr);

    Register addr_offset = AllocTempReg();
    Gen<PseudoCopy>(addr_offset, addr, 8);
    Gen<x86_64::SubqRegReg>(addr_offset, aligned_addr, GetFlagsRegister());

    // Load the requested part from CPUState.
    return LoadWithoutRecovery(ToLoadOperandType<IntType>(),
                               x86_64::kMachineRegRBP,
                               addr_offset,
                               GetThreadStateReservationValueOffset());
  }

  template <typename IntType, bool aq, bool rl>
  Register Sc(Register addr, Register data) {
    // Compute aligned_addr.
    auto aligned_addr = AllocTempReg();
    Gen<PseudoCopy>(aligned_addr, addr, 8);
    // The immediate is sign extended to 64-bit.
    Gen<x86_64::AndqRegImm>(aligned_addr, ~int32_t{sizeof(Reservation) - 1}, GetFlagsRegister());

    // Load current monitor value before we clobber it.
    auto reservation_value = AllocTempReg();
    int32_t value_offset = GetThreadStateReservationValueOffset();
    Gen<x86_64::MovqRegMemBaseDisp>(reservation_value, x86_64::kMachineRegRBP, value_offset);
    Register addr_offset = AllocTempReg();
    Gen<PseudoCopy>(addr_offset, addr, 8);
    Gen<x86_64::SubqRegReg>(addr_offset, aligned_addr, GetFlagsRegister());
    // It's okay to clobber reservation_value since we clear out reservation_address in
    // MemoryRegionReservationExchange anyway.
    StoreWithoutRecovery(ToMemoryDataOperandType<IntType>(),
                         x86_64::kMachineRegRBP,
                         addr_offset,
                         value_offset,
                         data);

    return MemoryRegionReservationExchange(aligned_addr, reservation_value);
  }

  void Fence(Decoder::FenceOpcode /*opcode*/,
             Register /*src*/,
             bool sw,
             bool sr,
             bool /*so*/,
             bool /*si*/,
             bool pw,
             bool pr,
             bool /*po*/,
             bool /*pi*/) {
    UNUSED(sw, sr, pw, pr);
    Unimplemented();
  }

  void FenceI(Register /*arg*/, int16_t /*imm*/) { Unimplemented(); }

  //
  // F and D extensions.
  //
  [[nodiscard]] FpRegister GetFpReg(uint8_t reg);

  template <typename FloatType>
  [[nodiscard]] FpRegister GetFRegAndUnboxNan(uint8_t reg) {
    CHECK_LE(reg, kNumGuestFpRegs);
    FpRegister result = AllocTempSimdReg();
    builder_.GenGetSimd<8>(result.machine_reg(), GetThreadStateFRegOffset(reg));
    FpRegister unboxed_result = AllocTempSimdReg();
    if (host_platform::kHasAVX) {
      builder_.Gen<x86_64::MacroUnboxNanFloat32AVX>(unboxed_result.machine_reg(),
                                                    result.machine_reg());
    } else {
      builder_.Gen<x86_64::MacroUnboxNanFloat32>(unboxed_result.machine_reg(),
                                                 result.machine_reg());
    }
    return unboxed_result;
  }

  template <typename FloatType>
  void NanBoxFpReg(FpRegister value) {
    if (host_platform::kHasAVX) {
      builder_.Gen<x86_64::MacroNanBoxFloat32AVX>(value.machine_reg(), value.machine_reg());
    } else {
      builder_.Gen<x86_64::MacroNanBoxFloat32>(value.machine_reg());
    }
  }

  template <typename FloatType>
  void NanBoxAndSetFpReg(uint8_t reg, FpRegister value) {
    CHECK_LE(reg, kNumGuestFpRegs);
    if (success()) {
      NanBoxFpReg<FloatType>(value);
      builder_.GenSetSimd<8>(GetThreadStateFRegOffset(reg), value.machine_reg());
    }
  }

  template <typename DataType>
  FpRegister LoadFp(Register arg, int16_t offset) {
    auto res = AllocTempSimdReg();
    if constexpr (std::is_same_v<DataType, Float32>) {
      Gen<x86_64::MovssXRegMemBaseDisp>(res.machine_reg(), arg, offset);
    } else if constexpr (std::is_same_v<DataType, Float64>) {
      Gen<x86_64::MovsdXRegMemBaseDisp>(res.machine_reg(), arg, offset);
    } else {
      static_assert(kDependentTypeFalse<DataType>);
    }
    return res;
  }

  template <typename DataType>
  void StoreFp(Register arg, int16_t offset, FpRegister data) {
    if constexpr (std::is_same_v<DataType, Float32>) {
      Gen<x86_64::MovssMemBaseDispXReg>(arg, offset, data.machine_reg());
    } else if constexpr (std::is_same_v<DataType, Float64>) {
      Gen<x86_64::MovsdMemBaseDispXReg>(arg, offset, data.machine_reg());
    } else {
      static_assert(kDependentTypeFalse<DataType>);
    }
  }

  FpRegister Fmv(FpRegister arg) {
    auto res = AllocTempSimdReg();
    Gen<PseudoCopy>(res.machine_reg(), arg.machine_reg(), 16);
    return res;
  }

  //
  // V extension.
  //

  template <typename VOpArgs, typename... ExtraAegs>
  void OpVector(const VOpArgs& /*args*/, ExtraAegs... /*extra_args*/) {
    // TODO(b/300690740): develop and implement strategy which would allow us to support vector
    // intrinsics not just in the interpreter.
    Unimplemented();
  }

  //
  // Csr
  //

  Register UpdateCsr(Decoder::CsrOpcode opcode, Register arg, Register csr);
  Register UpdateCsr(Decoder::CsrImmOpcode opcode, uint8_t imm, Register csr);

  [[nodiscard]] bool success() const { return success_; }

  //
  // Intrinsic proxy methods.
  //

#include "berberis/intrinsics/translator_intrinsics_hooks-inl.h"

  //
  // Guest state getters/setters.
  //

  [[nodiscard]] GuestAddr GetInsnAddr() const { return pc_; }
  void IncrementInsnAddr(uint8_t insn_size) { pc_ += insn_size; }

  [[nodiscard]] bool IsRegionEndReached() const;
  void StartInsn();
  void Finalize(GuestAddr stop_pc);

  // These methods are exported only for testing.
  [[nodiscard]] const ArenaMap<GuestAddr, MachineInsnPosition>& branch_targets() const {
    return branch_targets_;
  }

  template <CsrName kName>
  [[nodiscard]] Register GetCsr() {
    auto csr_reg = AllocTempReg();
    if constexpr (std::is_same_v<CsrFieldType<kName>, uint8_t>) {
      Gen<x86_64::MovzxblRegMemBaseDisp>(csr_reg, x86_64::kMachineRegRBP, kCsrFieldOffset<kName>);
    } else if constexpr (std::is_same_v<CsrFieldType<kName>, uint64_t>) {
      Gen<x86_64::MovqRegMemBaseDisp>(csr_reg, x86_64::kMachineRegRBP, kCsrFieldOffset<kName>);
    } else {
      static_assert(kDependentTypeFalse<CsrFieldType<kName>>);
    }
    return csr_reg;
  }

  template <CsrName kName>
  void SetCsr(uint8_t imm) {
    // Note: csr immediate only have 5 bits in RISC-V encoding which guarantess us that
    // “imm & kCsrMask<kName>”can be used as 8-bit immediate.
    if constexpr (std::is_same_v<CsrFieldType<kName>, uint8_t>) {
      Gen<x86_64::MovbMemBaseDispImm>(x86_64::kMachineRegRBP,
                                      kCsrFieldOffset<kName>,
                                      static_cast<int8_t>(imm & kCsrMask<kName>));
    } else if constexpr (std::is_same_v<CsrFieldType<kName>, uint64_t>) {
      Gen<x86_64::MovbMemBaseDispImm>(x86_64::kMachineRegRBP,
                                      kCsrFieldOffset<kName>,
                                      static_cast<int8_t>(imm & kCsrMask<kName>));
    } else {
      static_assert(kDependentTypeFalse<CsrFieldType<kName>>);
    }
  }

  template <CsrName kName>
  void SetCsr(Register arg) {
    auto tmp = AllocTempReg();
    Gen<PseudoCopy>(tmp, arg, sizeof(CsrFieldType<kName>));
    if constexpr (sizeof(CsrFieldType<kName>) == 1) {
      Gen<x86_64::AndbRegImm>(tmp, kCsrMask<kName>, GetFlagsRegister());
      Gen<x86_64::MovbMemBaseDispReg>(x86_64::kMachineRegRBP, kCsrFieldOffset<kName>, tmp);
    } else if constexpr (sizeof(CsrFieldType<kName>) == 8) {
      Gen<x86_64::AndqRegImm>(
          tmp, constants_pool::kConst<uint64_t{kCsrMask<kName>}>, GetFlagsRegister());
      Gen<x86_64::MovqMemBaseDispReg>(x86_64::kMachineRegRBP, kCsrFieldOffset<kName>, tmp);
    } else {
      static_assert(kDependentTypeFalse<CsrFieldType<kName>>);
    }
  }

 private:
  // Specialization for AssemblerResType=void
  template <auto kFunction,
            typename AssemblerResType,
            typename... AssemblerArgType,
            std::enable_if_t<std::is_same_v<std::decay_t<AssemblerResType>, void>, bool> = true>
  void CallIntrinsic(AssemblerArgType... args) {
    if (TryInlineIntrinsicForHeavyOptimizerVoid<kFunction>(
            &builder_, GetFlagsRegister(), args...)) {
      return;
    }

    CallIntrinsicImpl(&builder_, kFunction, GetFlagsRegister(), args...);
  }

  template <auto kFunction,
            typename AssemblerResType,
            typename... AssemblerArgType,
            std::enable_if_t<!std::is_same_v<std::decay_t<AssemblerResType>, void>, bool> = true>
  AssemblerResType CallIntrinsic(AssemblerArgType... args) {
    AssemblerResType result;

    if constexpr (std::is_same_v<AssemblerResType, Register>) {
      result = AllocTempReg();
    } else if constexpr (std::is_same_v<AssemblerResType, SimdReg>) {
      result = AllocTempSimdReg();
    } else if constexpr (std::is_same_v<AssemblerResType, std::tuple<Register, Register>>) {
      result = {AllocTempReg(), AllocTempReg()};
    } else if constexpr (std::is_same_v<AssemblerResType, std::tuple<SimdReg, Register>>) {
      result = {AllocTempSimdReg(), AllocTempReg()};
    } else if constexpr (std::is_same_v<AssemblerResType, std::tuple<SimdReg, SimdReg>>) {
      result = {AllocTempSimdReg(), AllocTempSimdReg()};
    } else if constexpr (std::is_same_v<AssemblerResType, std::tuple<SimdReg, SimdReg, SimdReg>>) {
      result = {AllocTempSimdReg(), AllocTempSimdReg(), AllocTempSimdReg()};
    } else if constexpr (std::is_same_v<AssemblerResType,
                                        std::tuple<SimdReg, SimdReg, SimdReg, SimdReg>>) {
      result = {AllocTempSimdReg(), AllocTempSimdReg(), AllocTempSimdReg(), AllocTempSimdReg()};
    } else {
      // This should not be reached by the compiler. If it is - there is a new result type that
      // needs to be supported.
      static_assert(kDependentTypeFalse<AssemblerResType>, "Unsupported result type");
    }

    if (TryInlineIntrinsicForHeavyOptimizer<kFunction>(
            &builder_, result, GetFlagsRegister(), args...)) {
      return result;
    }

    CallIntrinsicImpl(&builder_, kFunction, result, GetFlagsRegister(), args...);
    return result;
  }

  void MemoryRegionReservationLoad(Register aligned_addr);
  Register MemoryRegionReservationExchange(Register aligned_addr, Register curr_reservation_value);
  void MemoryRegionReservationSwapWithLockedOwner(Register aligned_addr,
                                                  Register curr_reservation_value,
                                                  Register new_reservation_value,
                                                  MachineBasicBlock* failure_bb);

  // Syntax sugar.
  template <typename InsnType, typename... Args>
  /*may_discard*/ InsnType* Gen(Args... args) {
    return builder_.Gen<InsnType, Args...>(args...);
  }

  static x86_64::Assembler::Condition ToAssemblerCond(Decoder::BranchOpcode opcode);

  [[nodiscard]] Register AllocTempReg();
  [[nodiscard]] SimdReg AllocTempSimdReg();
  [[nodiscard]] Register GetFlagsRegister() const { return flag_register_; };

  void GenJump(GuestAddr target);
  void ExitGeneratedCode(GuestAddr target);
  void ExitRegionIndirect(Register target);

  void GenRecoveryBlockForLastInsn();

  void ResolveJumps();
  void ReplaceJumpWithBranch(MachineBasicBlock* bb, MachineBasicBlock* target_bb);
  void UpdateBranchTargetsAfterSplit(GuestAddr addr,
                                     const MachineBasicBlock* old_bb,
                                     MachineBasicBlock* new_bb);

  void StartRegion() {
    auto* region_entry_bb = builder_.ir()->NewBasicBlock();
    auto* cont_bb = builder_.ir()->NewBasicBlock();
    builder_.ir()->AddEdge(region_entry_bb, cont_bb);
    builder_.StartBasicBlock(region_entry_bb);
    Gen<PseudoBranch>(cont_bb);
    builder_.StartBasicBlock(cont_bb);
  }

  GuestAddr pc_;
  bool success_;
  x86_64::MachineIRBuilder builder_;
  MachineReg flag_register_;
  bool is_uncond_branch_;
  // Contains IR positions of all guest instructions of the current region.
  // Also contains all branch targets which the current region jumps to.
  // If the target is outside of the current region the position is uninitialized,
  // i.e. it's basic block (position.first) is nullptr.
  ArenaMap<GuestAddr, MachineInsnPosition> branch_targets_;
};

template <>
[[nodiscard]] inline HeavyOptimizerFrontend::FpRegister
HeavyOptimizerFrontend::GetFRegAndUnboxNan<intrinsics::Float64>(uint8_t reg) {
  return GetFpReg(reg);
}

template <>
inline void HeavyOptimizerFrontend::NanBoxFpReg<intrinsics::Float64>(FpRegister) {}

template <>
[[nodiscard]] inline HeavyOptimizerFrontend::Register
HeavyOptimizerFrontend::GetCsr<CsrName::kFCsr>() {
  auto csr_reg = AllocTempReg();
  auto tmp = AllocTempReg();
  InlineIntrinsicForHeavyOptimizer<&intrinsics::FeGetExceptions>(
      &builder_, tmp, GetFlagsRegister());
  Gen<x86_64::MovzxbqRegMemBaseDisp>(
      csr_reg, x86_64::kMachineRegRBP, kCsrFieldOffset<CsrName::kFrm>);
  Gen<x86_64::ShlbRegImm>(csr_reg, 5, GetFlagsRegister());
  Gen<x86_64::OrbRegReg>(csr_reg, tmp, GetFlagsRegister());
  return csr_reg;
}

template <>
[[nodiscard]] inline HeavyOptimizerFrontend::Register
HeavyOptimizerFrontend::GetCsr<CsrName::kFFlags>() {
  return FeGetExceptions();
}

template <>
[[nodiscard]] inline HeavyOptimizerFrontend::Register
HeavyOptimizerFrontend::GetCsr<CsrName::kVlenb>() {
  return GetImm(16);
}

template <>
[[nodiscard]] inline HeavyOptimizerFrontend::Register
HeavyOptimizerFrontend::GetCsr<CsrName::kVxrm>() {
  auto reg = AllocTempReg();
  Gen<x86_64::MovzxbqRegMemBaseDisp>(reg, x86_64::kMachineRegRBP, kCsrFieldOffset<CsrName::kVcsr>);
  Gen<x86_64::AndbRegImm>(reg, 0b11, GetFlagsRegister());
  return reg;
}

template <>
[[nodiscard]] inline HeavyOptimizerFrontend::Register
HeavyOptimizerFrontend::GetCsr<CsrName::kVxsat>() {
  auto reg = AllocTempReg();
  Gen<x86_64::MovzxbqRegMemBaseDisp>(reg, x86_64::kMachineRegRBP, kCsrFieldOffset<CsrName::kVcsr>);
  Gen<x86_64::ShrbRegImm>(reg, 2, GetFlagsRegister());
  return reg;
}

template <>
inline void HeavyOptimizerFrontend::SetCsr<CsrName::kFCsr>(uint8_t imm) {
  // Note: instructions Csrrci or Csrrsi couldn't affect Frm because immediate only has five bits.
  // But these instruction don't pass their immediate-specified argument into `SetCsr`, they combine
  // it with register first. Fixing that can only be done by changing code in the semantics player.
  //
  // But Csrrwi may clear it.  And we actually may only arrive here from Csrrwi.
  // Thus, technically, we know that imm >> 5 is always zero, but it doesn't look like a good idea
  // to rely on that: it's very subtle and it only affects code generation speed.
  Gen<x86_64::MovbMemBaseDispImm>(
      x86_64::kMachineRegRBP, kCsrFieldOffset<CsrName::kFrm>, static_cast<int8_t>(imm >> 5));
  InlineIntrinsicForHeavyOptimizerVoid<&intrinsics::FeSetExceptionsAndRoundImm>(
      &builder_, GetFlagsRegister(), imm);
}

template <>
inline void HeavyOptimizerFrontend::SetCsr<CsrName::kFCsr>(Register arg) {
  // Check size to be sure we can use Andb and Movb below.
  static_assert(sizeof(kCsrMask<CsrName::kFrm>) == 1);

  auto exceptions = AllocTempReg();
  auto rounding_mode = AllocTempReg();
  Gen<PseudoCopy>(exceptions, arg, 1);
  Gen<x86_64::AndlRegImm>(exceptions, 0b1'1111, GetFlagsRegister());
  // We don't care about the data in rounding_mode because we will shift in the
  // data we need.
  Gen<PseudoDefReg>(rounding_mode);
  Gen<x86_64::ShldlRegRegImm>(rounding_mode, arg, int8_t{32 - 5}, GetFlagsRegister());
  Gen<x86_64::AndbRegImm>(rounding_mode, kCsrMask<CsrName::kFrm>, GetFlagsRegister());
  Gen<x86_64::MovbMemBaseDispReg>(
      x86_64::kMachineRegRBP, kCsrFieldOffset<CsrName::kFrm>, rounding_mode);
  InlineIntrinsicForHeavyOptimizerVoid<&intrinsics::FeSetExceptionsAndRound>(
      &builder_, GetFlagsRegister(), exceptions, rounding_mode);
}

template <>
inline void HeavyOptimizerFrontend::SetCsr<CsrName::kFFlags>(uint8_t imm) {
  FeSetExceptionsImm(static_cast<int8_t>(imm & 0b1'1111));
}

template <>
inline void HeavyOptimizerFrontend::SetCsr<CsrName::kFFlags>(Register arg) {
  auto tmp = AllocTempReg();
  Gen<PseudoCopy>(tmp, arg, 1);
  Gen<x86_64::AndlRegImm>(tmp, 0b1'1111, GetFlagsRegister());
  FeSetExceptions(tmp);
}

template <>
inline void HeavyOptimizerFrontend::SetCsr<CsrName::kFrm>(uint8_t imm) {
  Gen<x86_64::MovbMemBaseDispImm>(x86_64::kMachineRegRBP,
                                  kCsrFieldOffset<CsrName::kFrm>,
                                  static_cast<int8_t>(imm & kCsrMask<CsrName::kFrm>));
  FeSetRoundImm(static_cast<int8_t>(imm & kCsrMask<CsrName::kFrm>));
}

template <>
inline void HeavyOptimizerFrontend::SetCsr<CsrName::kFrm>(Register arg) {
  // Use RCX as temporary register. We know it would be used by FeSetRound, too.
  auto tmp = AllocTempReg();
  Gen<PseudoCopy>(tmp, arg, 1);
  Gen<x86_64::AndbRegImm>(tmp, kCsrMask<CsrName::kFrm>, GetFlagsRegister());
  Gen<x86_64::MovbMemBaseDispReg>(x86_64::kMachineRegRBP, kCsrFieldOffset<CsrName::kFrm>, tmp);
  FeSetRound(tmp);
}

template <>
inline void HeavyOptimizerFrontend::SetCsr<CsrName::kVxrm>(uint8_t imm) {
  imm &= 0b11;
  if (imm != 0b11) {
    Gen<x86_64::AndbMemBaseDispImm>(
        x86_64::kMachineRegRBP, kCsrFieldOffset<CsrName::kVcsr>, 0b100, GetFlagsRegister());
  }
  if (imm != 0b00) {
    Gen<x86_64::OrbMemBaseDispImm>(
        x86_64::kMachineRegRBP, kCsrFieldOffset<CsrName::kVcsr>, imm, GetFlagsRegister());
  }
}

template <>
inline void HeavyOptimizerFrontend::SetCsr<CsrName::kVxrm>(Register arg) {
  Gen<x86_64::AndbMemBaseDispImm>(
      x86_64::kMachineRegRBP, kCsrFieldOffset<CsrName::kVcsr>, 0b100, GetFlagsRegister());
  Gen<x86_64::AndbRegImm>(arg, 0b11, GetFlagsRegister());
  Gen<x86_64::OrbMemBaseDispReg>(
      x86_64::kMachineRegRBP, kCsrFieldOffset<CsrName::kVcsr>, arg, GetFlagsRegister());
}

template <>
inline void HeavyOptimizerFrontend::SetCsr<CsrName::kVxsat>(uint8_t imm) {
  if (imm & 0b1) {
    Gen<x86_64::OrbMemBaseDispImm>(
        x86_64::kMachineRegRBP, kCsrFieldOffset<CsrName::kVcsr>, 0b100, GetFlagsRegister());
  } else {
    Gen<x86_64::AndbMemBaseDispImm>(
        x86_64::kMachineRegRBP, kCsrFieldOffset<CsrName::kVcsr>, 0b11, GetFlagsRegister());
  }
}

template <>
inline void HeavyOptimizerFrontend::SetCsr<CsrName::kVxsat>(Register arg) {
  using Condition = x86_64::Assembler::Condition;
  Gen<x86_64::AndbMemBaseDispImm>(
      x86_64::kMachineRegRBP, kCsrFieldOffset<CsrName::kVcsr>, 0b11, GetFlagsRegister());
  Gen<x86_64::TestbRegImm>(arg, 1, GetFlagsRegister());
  auto tmp = AllocTempReg();
  Gen<x86_64::SetccReg>(Condition::kNotZero, tmp, GetFlagsRegister());
  Gen<x86_64::MovzxbqRegReg>(tmp, tmp);
  Gen<x86_64::ShlbRegImm>(tmp, int8_t{2}, GetFlagsRegister());
  Gen<x86_64::OrbMemBaseDispReg>(
      x86_64::kMachineRegRBP, kCsrFieldOffset<CsrName::kVcsr>, tmp, GetFlagsRegister());
}

}  // namespace berberis

#endif /* BERBERIS_HEAVY_OPTIMIZER_RISCV64_FRONTEND_H_ */
