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

#ifndef BERBERIS_BACKEND_X86_64_MACHINE_IR_BUILDER_H_
#define BERBERIS_BACKEND_X86_64_MACHINE_IR_BUILDER_H_

#include <array>
#include <iterator>

#include "berberis/backend/common/machine_ir_builder.h"
#include "berberis/backend/x86_64/machine_ir.h"
#include "berberis/base/logging.h"
#include "berberis/guest_state/guest_addr.h"
#include "berberis/guest_state/guest_state_opaque.h"

namespace berberis::x86_64 {

// Syntax sugar for building machine IR.
class MachineIRBuilder : public MachineIRBuilderBase<MachineIR> {
 public:
  explicit MachineIRBuilder(MachineIR* ir) : MachineIRBuilderBase(ir) {}

  void StartBasicBlock(MachineBasicBlock* bb) {
    CHECK(bb->insn_list().empty());
    ir()->bb_list().push_back(bb);
    bb_ = bb;
  }

  template <typename InsnType, typename... Args>
  /*may_discard*/ InsnType* Gen(Args... args) {
    return MachineIRBuilderBase::Gen<InsnType, Args...>(args...);
  }

  void GenGet(MachineReg dst_reg, int32_t offset) {
    Gen<x86_64::MovqRegMemBaseDisp>(dst_reg, x86_64::kMachineRegRBP, offset);
  }

  void GenPut(int32_t offset, MachineReg src_reg) {
    Gen<x86_64::MovqMemBaseDispReg>(x86_64::kMachineRegRBP, offset, src_reg);
  }

  template <size_t kSize>
  void GenGetSimd(MachineReg dst_reg, int32_t offset) {
    if constexpr (kSize == 8) {
      Gen<x86_64::MovsdXRegMemBaseDisp>(dst_reg, x86_64::kMachineRegRBP, offset);
    } else if constexpr (kSize == 16) {
      Gen<x86_64::MovdqaXRegMemBaseDisp>(dst_reg, x86_64::kMachineRegRBP, offset);
    } else {
      static_assert(kDependentValueFalse<kSize>);
    }
  }

  template <size_t kSize>
  void GenSetSimd(int32_t offset, MachineReg src_reg) {
    if constexpr (kSize == 8) {
      Gen<x86_64::MovsdMemBaseDispXReg>(x86_64::kMachineRegRBP, offset, src_reg);
    } else if constexpr (kSize == 16) {
      Gen<x86_64::MovdqaMemBaseDispXReg>(x86_64::kMachineRegRBP, offset, src_reg);
    } else {
      static_assert(kDependentValueFalse<kSize>);
    }
  }

  // Please use GenCallImm instead
  template <typename CallImmType,
            typename IntegralType,
            std::enable_if_t<std::is_same_v<std::decay_t<CallImmType>, CallImm> &&
                                 std::is_integral_v<IntegralType>,
                             bool> = true>
  /*may_discard*/ CallImmType* Gen(IntegralType imm) = delete;

  /*may_discard*/ CallImm* GenCallImm(uint64_t imm, MachineReg flag_register) {
    return GenCallImm(imm, flag_register, std::array<CallImm::Arg, 0>{});
  }

  template <size_t kNumberOfArguments>
  /*may_discard*/ CallImm* GenCallImm(uint64_t imm,
                                      MachineReg flag_register,
                                      const std::array<CallImm::Arg, kNumberOfArguments>& args) {
    auto* call = ir()->NewInsn<CallImm>(imm);
    // Init registers clobbered according to ABI to notify the register allocator.
    for (int i = 0; i < call->NumRegOperands(); ++i) {
      call->SetRegAt(i, ir()->AllocVReg());
    }

    call->SetRegAt(x86_64::CallImm::GetFlagsArgIndex(), flag_register);

    // Now generate CallImmArg instructions for arguments
    GenCallImmArg(call, args);

    InsertInsn(call);
    return call;
  }

  template <typename CallImmArgType,
            typename... Args,
            std::enable_if_t<std::is_same_v<std::decay_t<CallImmArgType>, CallImmArg>, bool> = true>
  /*may_discard*/ CallImmArgType* Gen(Args... args) = delete;

 private:
  template <size_t kNumberOfArgumens>
  void GenCallImmArg(CallImm* call, const std::array<CallImm::Arg, kNumberOfArgumens>& args) {
    int general_register_position = 0;
    int xmm_register_position = 0;
    for (const auto& arg : args) {
      MachineReg arg_reg = arg.reg;
      CallImm::RegType reg_type = arg.reg_type;

      // Rename arg vreg in case it's used in several call operands which have non-intersecting
      // register classes. Reg-alloc will eliminate renaming where possible.
      MachineReg renamed_arg_reg = ir()->AllocVReg();
      auto* copy = ir()->NewInsn<PseudoCopy>(
          renamed_arg_reg, arg_reg, (reg_type == CallImm::kIntRegType) ? 8 : 16);
      auto* call_arg_insn = ir()->NewInsn<CallImmArg>(renamed_arg_reg, reg_type);
      call->SetRegAt((reg_type == CallImm::kIntRegType)
                         ? CallImm::GetIntArgIndex(general_register_position++)
                         : CallImm::GetXmmArgIndex(xmm_register_position++),
                     renamed_arg_reg);

      InsertInsn(copy);
      InsertInsn(call_arg_insn);
    }
  }
};

}  // namespace berberis::x86_64

#endif  // BERBERIS_BACKEND_X86_64_MACHINE_IR_BUILDER_H_
