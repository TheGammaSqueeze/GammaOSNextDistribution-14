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

#ifndef BERBERIS_GUEST_STATE_GUEST_STATE_OPAQUE_H_
#define BERBERIS_GUEST_STATE_GUEST_STATE_OPAQUE_H_

#include <atomic>
#include <cstddef>
#include <cstdint>

#include "berberis/guest_state/guest_addr.h"

namespace berberis {

struct CPUState;
struct ThreadState;

// TODO(b/294958305): remove this once android_api is migrated to ThreadState
using ProcessState = ThreadState;

ThreadState* CreateThreadState();
void DestroyThreadState(ThreadState* state);

class GuestThread;
void SetGuestThread(ThreadState& state, GuestThread* thread);
GuestThread* GetGuestThread(const ThreadState& state);

// Track whether we are in generated code or not.
enum GuestThreadResidence : uint8_t {
  kOutsideGeneratedCode = 0,
  kInsideGeneratedCode = 1,
};

GuestThreadResidence GetResidence(const ThreadState& state);
void SetResidence(ThreadState& state, GuestThreadResidence residence);

// TODO(b/28058920): Refactor into GuestThread.
// Pending signals status state machine:
//   disabled <-> enabled <-> enabled and pending signals present
enum PendingSignalsStatus : uint_least8_t {
  kPendingSignalsDisabled = 0,  // initial value, must be 0
  kPendingSignalsEnabled,
  kPendingSignalsPresent,  // implies enabled
};

// Values are interpreted as PendingSignalsStatus.
std::atomic<uint_least8_t>& GetPendingSignalsStatusAtomic(ThreadState& state);
void SetPendingSignalsStatusAtomic(ThreadState& state, PendingSignalsStatus status);

const CPUState& GetCPUState(const ThreadState& state);
CPUState& GetCPUState(ThreadState& state);
void SetCPUState(ThreadState& state, const CPUState& cpu);

GuestAddr GetReturnValueRegister(const CPUState& cpu);
void SetReturnValueRegister(CPUState& cpu, GuestAddr val);

void SetStackRegister(CPUState& cpu, GuestAddr val);
GuestAddr GetStackRegister(const CPUState& cpu);

void SetLinkRegister(CPUState& cpu, GuestAddr val);
GuestAddr GetLinkRegister(const CPUState& cpu);

void SetInsnAddr(CPUState& cpu, GuestAddr addr);
GuestAddr GetInsnAddr(const CPUState& cpu);

// Assuming PC currently points to a supervisor call instruction, advance PC to the next
// instruction. Must be implemented according to the guest architecture.
void AdvanceInsnAddrBeyondSyscall(CPUState& cpu);

// TODO(b/28058920): Refactor into GuestThread.
bool ArePendingSignalsPresent(const ThreadState& state);

void SetTlsAddr(ThreadState& state, GuestAddr addr);
GuestAddr GetTlsAddr(const ThreadState& cpu);

// Set the appropriate stack pointer register, if it exists for a given guest architecture.
void SetShadowCallStackPointer(CPUState& cpu, GuestAddr scs_sp);

void InitFloatingPointState();

std::size_t GetThreadStateRegOffset(int reg);

bool DoesCpuStateHaveDedicatedFpRegs();
std::size_t GetThreadStateFRegOffset(int freg);

bool DoesCpuStateHaveDedicatedVecRegs();
std::size_t GetThreadStateVRegOffset(int vreg);

bool DoesCpuStateHaveDedicatedSimdRegs();
std::size_t GetThreadStateSimdRegOffset(int simd_reg);
bool IsSimdOffset(size_t offset);

std::size_t GetThreadStateReservationAddressOffset();
std::size_t GetThreadStateReservationValueOffset();

bool DoesCpuStateHaveFlags();
std::size_t GetThreadStateFlagOffset();

}  // namespace berberis

#endif  // BERBERIS_GUEST_STATE_GUEST_STATE_OPAQUE_H_
