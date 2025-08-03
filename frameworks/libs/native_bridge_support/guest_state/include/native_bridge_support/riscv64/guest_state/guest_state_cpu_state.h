/*
 * Copyright (C) 2024 The Android Open Source Project
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

#ifndef NATIVE_BRIDGE_SUPPORT_RISCV64_GUEST_STATE_GUEST_STATE_CPU_STATE_H_
#define NATIVE_BRIDGE_SUPPORT_RISCV64_GUEST_STATE_GUEST_STATE_CPU_STATE_H_

namespace berberis {

using GuestAddr = uintptr_t;
using Reservation = uint64_t;

struct CPUState {
  // x0 to x31.
  uint64_t x[32];
  // f0 to f31. We are using uint64_t because C++ may change values of NaN when they are passed from
  // or to function and RISC-V uses NaN-boxing which would make things problematic.
  uint64_t f[32];
  // v0 to v32. We only support 128bit vectors for now.
  alignas(16) __uint128_t v[32];

  GuestAddr insn_addr;

  GuestAddr reservation_address;
  Reservation reservation_value;

  // Technically only 9 bits are defined: sign bit and 8 low bits.
  // But for performance reason it's easier to keep full 64bits in this variable.
  uint64_t vtype;
  // This register usually contains zero and each vector instruction would reset it to zero.
  // But it's allowed to change it and if that happens we are supposed to support it.
  uint8_t vstart;
  // This register is usually set to process full 128 bits set of SIMD data.
  // But it's allowed to change it and if that happens we are supposed to support it.
  uint8_t vl;
  // Only 3 bits are defined but we allocate full byte to simplify implementation.
  uint8_t vcsr;
  // RISC-V has five rounding modes, while x86-64 has only four.
  //
  // Extra rounding mode (RMM in RISC-V documentation) is emulated but requires the use of
  // FE_TOWARDZERO mode for correct work.
  //
  // Additionally RISC-V implementation is supposed to support three “illegal” rounding modes and
  // when they are selected all instructions which use rounding mode trigger “undefined instruction”
  // exception.
  //
  // For simplicity we always keep full rounding mode (3 bits) in the frm field and set host
  // rounding mode to appropriate one.
  //
  // Exceptions, on the other hand, couldn't be stored here efficiently, instead we rely on the fact
  // that x86-64 implements all five exceptions that RISC-V needs (and more).
  uint8_t frm;
};

}
#endif  // NATIVE_BRIDGE_SUPPORT_RISCV64_GUEST_STATE_GUEST_STATE_CPU_STATE_H_