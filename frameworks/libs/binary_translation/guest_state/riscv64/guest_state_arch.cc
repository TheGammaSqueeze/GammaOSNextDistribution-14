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

#include "berberis/guest_state/guest_state.h"

#include "berberis/base/checks.h"
#include "berberis/guest_state/guest_state_arch.h"
#include "berberis/guest_state/guest_state_opaque.h"

namespace berberis {

void SetReturnValueRegister(CPUState& cpu, GuestAddr val) {
  SetXReg<A0>(cpu, val);
}

GuestAddr GetReturnValueRegister(const CPUState& cpu) {
  return GetXReg<A0>(cpu);
}

void SetStackRegister(CPUState& cpu, GuestAddr val) {
  SetXReg<SP>(cpu, val);
}

GuestAddr GetStackRegister(const CPUState& cpu) {
  return GetXReg<SP>(cpu);
}

void SetLinkRegister(CPUState& cpu, GuestAddr val) {
  SetXReg<RA>(cpu, val);
}

GuestAddr GetLinkRegister(const CPUState& cpu) {
  return GetXReg<RA>(cpu);
}

void SetTlsAddr(ThreadState& state, GuestAddr addr) {
  SetXReg<TP>(state.cpu, addr);
}

GuestAddr GetTlsAddr(const ThreadState& state) {
  return GetXReg<TP>(state.cpu);
}

void SetShadowCallStackPointer(CPUState& cpu, GuestAddr scs_sp) {
  SetXReg<GP>(cpu, scs_sp);
}

void InitFloatingPointState() {
  // TODO(b/276787675): Initialize host MXCSR register once riscv64 intrinsics are supported.
}

void AdvanceInsnAddrBeyondSyscall(CPUState& cpu) {
  // RV64I uses the same 4-byte ECALL instruction as RV32I.
  // See ratified RISC-V unprivileged spec v2.1.
  cpu.insn_addr += 4;
}

std::size_t GetThreadStateRegOffset(int reg) {
  return offsetof(ThreadState, cpu.x[reg]);
}

std::size_t GetThreadStateFRegOffset(int freg) {
  return offsetof(ThreadState, cpu.f[freg]);
}

std::size_t GetThreadStateVRegOffset(int vreg) {
  return offsetof(ThreadState, cpu.v[vreg]);
}

std::size_t GetThreadStateSimdRegOffset(int /* simd_reg */) {
  // RISCV64 does not have simd registers.
  UNREACHABLE();
}

std::size_t GetThreadStateReservationAddressOffset() {
  return offsetof(ThreadState, cpu.reservation_address);
}

std::size_t GetThreadStateReservationValueOffset() {
  return offsetof(ThreadState, cpu.reservation_value);
}

bool IsSimdOffset(size_t offset) {
  size_t v0_offset = offsetof(ThreadState, cpu.v);
  return (offset >= v0_offset) && ((offset - v0_offset) < sizeof(ThreadState::cpu.v));
}

bool DoesCpuStateHaveFlags() {
  return false;
}

bool DoesCpuStateHaveDedicatedFpRegs() {
  return true;
}

bool DoesCpuStateHaveDedicatedVecRegs() {
  return true;
}

bool DoesCpuStateHaveDedicatedSimdRegs() {
  return false;
}

std::size_t GetThreadStateFlagOffset() {
  // RISCV64 Does not have flags in its CPUState
  CHECK(false);
}

}  // namespace berberis
