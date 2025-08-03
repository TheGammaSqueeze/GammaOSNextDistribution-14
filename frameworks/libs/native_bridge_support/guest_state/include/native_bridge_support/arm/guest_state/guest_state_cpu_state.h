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

#ifndef NATIVE_BRIDGE_SUPPORT_ARM_GUEST_STATE_GUEST_STATE_CPU_STATE_H_
#define NATIVE_BRIDGE_SUPPORT_ARM_GUEST_STATE_GUEST_STATE_CPU_STATE_H_

namespace berberis {

using GuestAddr = uintptr_t;
using Reservation = uint64_t;

// Guest CPU state.
struct CPUState {
  // General registers, except PC (r15).
  uint32_t r[15];

  // ATTENTION: flag values should only be 0 or 1, for bitwise computations!
  // This is different from 'bool', where 'true' can be any non-zero value!
  struct Flags {
    uint8_t negative;
    uint8_t zero;
    uint8_t carry;
    uint8_t overflow;
    uint32_t saturation;
    // Greater than or equal flags in SIMD-friendly format: 4 bytes, each either 0x00 or 0xff.
    // That's format produced by SIMD instructions (e.g. PCMPGTB/etc on x86 and VCGT/etc on ARM).
    uint32_t ge;
  } flags;

  // Current insn address, +1 if Thumb.
  uint32_t insn_addr;

  // Advanced SIMD and floating-point registers (s, d, q).
  // Have to be aligned (relative to structure start) to allow optimizer
  // determine 128-bit container for 64-bit element.
  alignas(128 / CHAR_BIT) uint64_t d[32];

  // See intrinsics/guest_fp_flags.h for the information about that word.
  // Intrinsics touch separate bits of that word, the rest uses it as opaque 32-bit data structure.
  //
  // Exception: SemanticsDecoder::VMRS accesses four bits directly without intrinsics.
  uint32_t fpflags;

  GuestAddr reservation_address;
  Reservation reservation_value;
};

}
#endif  // NATIVE_BRIDGE_SUPPORT_ARM_GUEST_STATE_GUEST_STATE_CPU_STATE_H_