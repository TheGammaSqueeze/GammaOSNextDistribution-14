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

#ifndef BERBERIS_BACKEND_COMMON_MACHINE_IR_BUILDER_H_
#define BERBERIS_BACKEND_COMMON_MACHINE_IR_BUILDER_H_

#include <optional>
#include <utility>

#include "berberis/backend/common/machine_ir.h"
#include "berberis/guest_state/guest_addr.h"

namespace berberis {

using MachineInsnPosition = std::pair<MachineBasicBlock*, std::optional<MachineInsnList::iterator>>;

// Syntax sugar for building machine IR.
template <typename MachineIRType>
class MachineIRBuilderBase {
 public:
  explicit MachineIRBuilderBase(MachineIRType* ir) : ir_(ir), bb_(nullptr) {}

  [[nodiscard]] MachineIRType* ir() { return ir_; }
  [[nodiscard]] const MachineIRType* ir() const { return ir_; }

  template <typename InsnType, typename... Args>
  /*may_discard*/ InsnType* Gen(Args... args) {
    InsnType* insn = ir_->template NewInsn<InsnType>(args...);
    InsertInsn(insn);
    return insn;
  }

  void SetRecoveryPointAtLastInsn(MachineBasicBlock* recovery_bb) {
    bb_->insn_list().back()->set_recovery_bb(recovery_bb);
    recovery_bb->MarkAsRecovery();
  }

  void SetRecoveryWithGuestPCAtLastInsn(GuestAddr pc) {
    bb_->insn_list().back()->set_recovery_pc(pc);
  }

  [[nodiscard]] MachineInsnPosition GetMachineInsnPosition() {
    if (bb_->insn_list().empty()) {
      return std::make_pair(bb_, std::nullopt);
    }

    return std::make_pair(
        bb_, std::optional<MachineInsnList::iterator>(std::prev(bb_->insn_list().end())));
  }

  [[nodiscard]] MachineBasicBlock* bb() const { return bb_; }

 private:
  MachineIRType* ir_;

 protected:
  void InsertInsn(MachineInsn* insn) { bb_->insn_list().push_back(insn); }

  MachineBasicBlock* bb_;
};

}  // namespace berberis

#endif  // BERBERIS_BACKEND_COMMON_MACHINE_IR_BUILDER_H_
