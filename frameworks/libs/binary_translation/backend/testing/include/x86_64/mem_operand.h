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

#ifndef BERBERIS_BACKEND_X86_64_MEM_OPERAND_H_
#define BERBERIS_BACKEND_X86_64_MEM_OPERAND_H_

#include <cstdint>

#include "berberis/backend/x86_64/machine_ir.h"
#include "berberis/backend/x86_64/machine_ir_builder.h"
#include "berberis/base/logging.h"

namespace berberis {

namespace x86_64 {

class MemOperand {
 public:
  enum AddrMode { kAddrModeInvalid, kAddrModeBaseDisp, kAddrModeIndexDisp, kAddrModeBaseIndexDisp };

  MemOperand() : addr_mode_(kAddrModeInvalid), scale_(MachineMemOperandScale::kOne), disp_(0) {}

  static MemOperand MakeBaseDisp(MachineReg base, int32_t disp) {
    return MemOperand(
        kAddrModeBaseDisp, base, kInvalidMachineReg, MachineMemOperandScale::kOne, disp);
  }

  template <MachineMemOperandScale scale>
  static MemOperand MakeIndexDisp(MachineReg index, int32_t disp) {
    // We do not accept kOne here.  BaseDisp has
    // better encoding than IndexDisp with kOne.
    // Also, we do not want to have two ways to express reg + disp.
    static_assert(scale != MachineMemOperandScale::kOne, "ScaleOne not allowed");
    return MemOperand(kAddrModeIndexDisp, kInvalidMachineReg, index, scale, disp);
  }

  template <MachineMemOperandScale scale>
  static MemOperand MakeBaseIndexDisp(MachineReg base, MachineReg index, int32_t disp) {
    return MemOperand(kAddrModeBaseIndexDisp, base, index, scale, disp);
  }

  AddrMode addr_mode() const { return addr_mode_; }

  MachineReg base() const {
    CHECK(addr_mode_ == kAddrModeBaseDisp || addr_mode_ == kAddrModeBaseIndexDisp);
    return base_;
  }

  MachineReg index() const {
    CHECK(addr_mode_ == kAddrModeIndexDisp || addr_mode_ == kAddrModeBaseIndexDisp);
    return index_;
  }

  MachineMemOperandScale scale() const {
    CHECK(addr_mode_ == kAddrModeIndexDisp || addr_mode_ == kAddrModeBaseIndexDisp);
    return scale_;
  }

  int32_t disp() const {
    CHECK_NE(addr_mode_, kAddrModeInvalid);
    return disp_;
  }

  bool IsValid() const { return addr_mode_ != kAddrModeInvalid; }

 private:
  // We keep this general constructor private. Users must call
  // MakeBaseDisp, MakeIndexDisp etc. This way, it's obvious to callers
  // what addressing mode is being requested because the method names
  // contain addressing modes.
  MemOperand(AddrMode addr_mode,
             MachineReg base,
             MachineReg index,
             MachineMemOperandScale scale,
             int32_t disp)
      : addr_mode_(addr_mode), base_(base), index_(index), scale_(scale), disp_(disp) {}

  const AddrMode addr_mode_;
  const MachineReg base_;
  const MachineReg index_;
  const MachineMemOperandScale scale_;
  // The hardware sign-extends disp to 64-bit.
  const int32_t disp_;
};

template <typename MachineInsnMemInsns, typename... Args>
void GenArgsMem(MachineIRBuilder* builder, const MemOperand& mem_operand, Args... args) {
  switch (mem_operand.addr_mode()) {
    case MemOperand::kAddrModeBaseDisp:
      builder->Gen<typename MachineInsnMemInsns::BaseDisp>(
          args..., mem_operand.base(), mem_operand.disp());
      break;
    case MemOperand::kAddrModeIndexDisp:
      builder->Gen<typename MachineInsnMemInsns::IndexDisp>(
          args..., mem_operand.index(), mem_operand.scale(), mem_operand.disp());
      break;
    case MemOperand::kAddrModeBaseIndexDisp:
      builder->Gen<typename MachineInsnMemInsns::BaseIndexDisp>(args...,
                                                                mem_operand.base(),
                                                                mem_operand.index(),
                                                                mem_operand.scale(),
                                                                mem_operand.disp());
      break;
    default:
      FATAL("Impossible addressing mode");
  }
}

template <typename MachineInsnMemInsns, typename... Args>
void GenMemArgs(MachineIRBuilder* builder, const MemOperand& mem_operand, Args... args) {
  switch (mem_operand.addr_mode()) {
    case MemOperand::kAddrModeBaseDisp:
      builder->Gen<typename MachineInsnMemInsns::BaseDisp>(
          mem_operand.base(), mem_operand.disp(), args...);
      break;
    case MemOperand::kAddrModeIndexDisp:
      builder->Gen<typename MachineInsnMemInsns::IndexDisp>(
          mem_operand.index(), mem_operand.scale(), mem_operand.disp(), args...);
      break;
    case MemOperand::kAddrModeBaseIndexDisp:
      builder->Gen<typename MachineInsnMemInsns::BaseIndexDisp>(mem_operand.base(),
                                                                mem_operand.index(),
                                                                mem_operand.scale(),
                                                                mem_operand.disp(),
                                                                args...);
      break;
    default:
      FATAL("Impossible addressing mode");
  }
}

}  // namespace x86_64

}  // namespace berberis

#endif  // BERBERIS_BACKEND_X86_64_MEM_OPERAND_H_
