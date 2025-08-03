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

#include "berberis/backend/x86_64/machine_ir.h"
#include "berberis/base/logging.h"
#include "berberis/guest_state/guest_addr.h"

namespace berberis {

namespace x86_64 {

namespace {

constexpr MachineInsnInfo kCallImmInfo = {
    kMachineOpCallImm,
    26,
    {
        {&kRAX, MachineRegKind::kDef},   {&kRDI, MachineRegKind::kDef},
        {&kRSI, MachineRegKind::kDef},   {&kRDX, MachineRegKind::kDef},
        {&kRCX, MachineRegKind::kDef},   {&kR8, MachineRegKind::kDef},
        {&kR9, MachineRegKind::kDef},    {&kR10, MachineRegKind::kDef},
        {&kR11, MachineRegKind::kDef},   {&kXMM0, MachineRegKind::kDef},
        {&kXMM1, MachineRegKind::kDef},  {&kXMM2, MachineRegKind::kDef},
        {&kXMM3, MachineRegKind::kDef},  {&kXMM4, MachineRegKind::kDef},
        {&kXMM5, MachineRegKind::kDef},  {&kXMM6, MachineRegKind::kDef},
        {&kXMM7, MachineRegKind::kDef},  {&kXMM8, MachineRegKind::kDef},
        {&kXMM9, MachineRegKind::kDef},  {&kXMM10, MachineRegKind::kDef},
        {&kXMM11, MachineRegKind::kDef}, {&kXMM12, MachineRegKind::kDef},
        {&kXMM13, MachineRegKind::kDef}, {&kXMM14, MachineRegKind::kDef},
        {&kXMM15, MachineRegKind::kDef}, {&kFLAGS, MachineRegKind::kDef},
    },
    kMachineInsnSideEffects};

constexpr MachineInsnInfo kCallImmIntArgInfo = {kMachineOpCallImmArg,
                                                1,
                                                {{&kReg64, MachineRegKind::kUse}},
                                                // Is implicitly part of CallImm.
                                                kMachineInsnSideEffects};

constexpr MachineInsnInfo kCallImmXmmArgInfo = {kMachineOpCallImmArg,
                                                1,
                                                {{&kXmmReg, MachineRegKind::kUse}},
                                                // Is implicitly part of CallImm.
                                                kMachineInsnSideEffects};

constexpr MachineRegKind kPseudoCondBranchInfo[] = {{&kFLAGS, MachineRegKind::kUse}};

constexpr MachineRegKind kPseudoIndirectJumpInfo[] = {{&kGeneralReg64, MachineRegKind::kUse}};

constexpr MachineRegKind kPseudoCopyReg32Info[] = {{&kReg32, MachineRegKind::kDef},
                                                   {&kReg32, MachineRegKind::kUse}};

constexpr MachineRegKind kPseudoCopyReg64Info[] = {{&kReg64, MachineRegKind::kDef},
                                                   {&kReg64, MachineRegKind::kUse}};

constexpr MachineRegKind kPseudoCopyXmmInfo[] = {{&kXmmReg, MachineRegKind::kDef},
                                                 {&kXmmReg, MachineRegKind::kUse}};

constexpr MachineRegKind kPseudoDefXmmInfo[] = {{&kXmmReg, MachineRegKind::kDef}};

constexpr MachineRegKind kPseudoDefReg64Info[] = {{&kReg64, MachineRegKind::kDef}};

constexpr MachineRegKind kPseudoReadFlagsInfo[] = {{&kRAX, MachineRegKind::kDef},
                                                   {&kFLAGS, MachineRegKind::kUse}};

constexpr MachineRegKind kPseudoWriteFlagsInfo[] = {{&kRAX, MachineRegKind::kUseDef},
                                                    {&kFLAGS, MachineRegKind::kDef}};

}  // namespace

CallImm::CallImm(uint64_t imm) : MachineInsnX86_64(&kCallImmInfo) {
  set_imm(imm);
}

int CallImm::GetIntArgIndex(int i) {
  constexpr int kIntArgIndex[] = {
      1,  // RDI
      2,  // RSI
      3,  // RDX
      4,  // RCX
      5,  // R8
      6,  // R9
  };

  CHECK_LT(static_cast<unsigned>(i), std::size(kIntArgIndex));
  return kIntArgIndex[i];
}

int CallImm::GetXmmArgIndex(int i) {
  constexpr int kXmmArgIndex[] = {
      9,   // XMM0
      10,  // XMM1
      11,  // XMM2
      12,  // XMM3
      13,  // XMM4
      14,  // XMM5
      15,  // XMM6
      16,  // XMM7
  };

  CHECK_LT(static_cast<unsigned>(i), std::size(kXmmArgIndex));
  return kXmmArgIndex[i];
}

int CallImm::GetFlagsArgIndex() {
  return 25;  // FLAGS
}

MachineReg CallImm::IntResultAt(int i) const {
  constexpr int kIntResultIndex[] = {
      0,  // RAX
      3,  // RDX
  };

  CHECK_LT(static_cast<unsigned>(i), std::size(kIntResultIndex));
  return RegAt(kIntResultIndex[i]);
}

MachineReg CallImm::XmmResultAt(int i) const {
  constexpr int kXmmResultIndex[] = {
      9,   // XMM0
      10,  // XMM1
  };

  CHECK_LT(static_cast<unsigned>(i), std::size(kXmmResultIndex));
  return RegAt(kXmmResultIndex[i]);
}

CallImmArg::CallImmArg(MachineReg arg, CallImm::RegType reg_type)
    : MachineInsnX86_64((reg_type == CallImm::kIntRegType) ? &kCallImmIntArgInfo
                                                           : &kCallImmXmmArgInfo) {
  SetRegAt(0, arg);
}

#include "insn-inl_x86_64.h"  // NOLINT generated file!

}  // namespace x86_64

const MachineOpcode PseudoBranch::kOpcode = kMachineOpPseudoBranch;
using Assembler = x86_64::Assembler;

PseudoBranch::PseudoBranch(const MachineBasicBlock* then_bb)
    : MachineInsn(kMachineOpPseudoBranch, 0, nullptr, nullptr, kMachineInsnSideEffects),
      then_bb_(then_bb) {}

const MachineOpcode PseudoCondBranch::kOpcode = kMachineOpPseudoCondBranch;

PseudoCondBranch::PseudoCondBranch(Assembler::Condition cond,
                                   const MachineBasicBlock* then_bb,
                                   const MachineBasicBlock* else_bb,
                                   MachineReg eflags)
    : MachineInsn(kMachineOpPseudoCondBranch,
                  1,
                  x86_64::kPseudoCondBranchInfo,
                  &eflags_,
                  kMachineInsnSideEffects),
      cond_(cond),
      then_bb_(then_bb),
      else_bb_(else_bb),
      eflags_(eflags) {}

PseudoJump::PseudoJump(GuestAddr target, Kind kind)
    : MachineInsn(kMachineOpPseudoJump, 0, nullptr, nullptr, kMachineInsnSideEffects),
      target_(target),
      kind_(kind) {}

PseudoIndirectJump::PseudoIndirectJump(MachineReg src)
    : MachineInsn(kMachineOpPseudoIndirectJump,
                  1,
                  x86_64::kPseudoIndirectJumpInfo,
                  &src_,
                  kMachineInsnSideEffects),
      src_(src) {}

const MachineOpcode PseudoCopy::kOpcode = kMachineOpPseudoCopy;

// Reg class of correct size is essential for current spill/reload code!!!
PseudoCopy::PseudoCopy(MachineReg dst, MachineReg src, int size)
    : MachineInsn(kMachineOpPseudoCopy,
                  2,
                  size > 8   ? x86_64::kPseudoCopyXmmInfo
                  : size > 4 ? x86_64::kPseudoCopyReg64Info
                             : x86_64::kPseudoCopyReg32Info,
                  regs_,
                  kMachineInsnCopy),
      regs_{dst, src} {}

PseudoDefXReg::PseudoDefXReg(MachineReg reg)
    : MachineInsn(kMachineOpPseudoDefXReg,
                  1,
                  x86_64::kPseudoDefXmmInfo,
                  &reg_,
                  kMachineInsnDefault),
      reg_{reg} {}

PseudoDefReg::PseudoDefReg(MachineReg reg)
    : MachineInsn(kMachineOpPseudoDefReg,
                  1,
                  x86_64::kPseudoDefReg64Info,
                  &reg_,
                  kMachineInsnDefault),
      reg_{reg} {}

const MachineOpcode PseudoReadFlags::kOpcode = kMachineOpPseudoReadFlags;

PseudoReadFlags::PseudoReadFlags(WithOverflowEnum with_overflow, MachineReg dst, MachineReg flags)
    : MachineInsn(kMachineOpPseudoReadFlags,
                  2,
                  x86_64::kPseudoReadFlagsInfo,
                  regs_,
                  kMachineInsnDefault),
      regs_{dst, flags},
      with_overflow_(with_overflow == kWithOverflow) {}

const MachineOpcode PseudoWriteFlags::kOpcode = kMachineOpPseudoWriteFlags;

PseudoWriteFlags::PseudoWriteFlags(MachineReg src, MachineReg flags)
    : MachineInsn(kMachineOpPseudoWriteFlags,
                  2,
                  x86_64::kPseudoWriteFlagsInfo,
                  regs_,
                  kMachineInsnDefault),
      regs_{src, flags} {}

}  // namespace berberis
