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

#ifndef BERBERIS_INTRINSICS_GUEST_RISCV64_FPSTATE_H_
#define BERBERIS_INTRINSICS_GUEST_RISCV64_FPSTATE_H_

#include <cfenv>
#include <cstdint>

#include "berberis/base/checks.h"
#include "berberis/intrinsics/guest_rounding_modes.h"

namespace berberis {

namespace FPFlags {

inline constexpr uint64_t NV = 1 << 4;
inline constexpr uint64_t DZ = 1 << 3;
inline constexpr uint64_t OF = 1 << 2;
inline constexpr uint64_t UF = 1 << 1;
inline constexpr uint64_t NX = 1 << 0;
inline constexpr uint64_t RM_POS = 5;
inline constexpr uint64_t RM_MASK = 0b111;
inline constexpr uint64_t RM_MAX = 0b100;
inline constexpr uint64_t RNE = 0b000;
inline constexpr uint64_t RTZ = 0b001;
inline constexpr uint64_t RDN = 0b010;
inline constexpr uint64_t RUP = 0b011;
inline constexpr uint64_t RMM = 0b100;
inline constexpr uint64_t DYN = 0b111;

}  // namespace FPFlags

namespace intrinsics {

// Note that not all RISC-V rounding modes are supported on popular architectures.
// FE_TIESAWAY is emulated, but proper emulation needs FE_TOWARDZERO mode.
inline int ToHostRoundingMode(int8_t rm) {
  if (rm == FPFlags::DYN) {
    return FE_HOSTROUND;
  }
  CHECK_GE(rm, 0);
  CHECK_LE(rm, int8_t{FPFlags::RM_MAX});
  static constexpr int kRounding[FPFlags::RM_MAX + 1] = {
      FE_TONEAREST, FE_TOWARDZERO, FE_DOWNWARD, FE_UPWARD, FE_TOWARDZERO};
  return kRounding[rm];
}

// Same as ToHostRoundingMode, but returns pseudo FE_TIESAWAY mode for RMM.
inline int ToIntrinsicRoundingMode(int8_t rm) {
  if (rm == FPFlags::RMM) {
    return FE_TIESAWAY;
  }
  return ToHostRoundingMode(rm);
}

inline uint8_t GuestModeFromHostRounding() {
  switch (fegetround()) {
    case FE_TONEAREST:
      return FPFlags::RNE;
    case FE_DOWNWARD:
      return FPFlags::RDN;
    case FE_UPWARD:
      return FPFlags::RUP;
    case FE_TOWARDZERO:
      return FPFlags::RTZ;
    default:
      CHECK(false);
  }
}

}  // namespace intrinsics

}  // namespace berberis

#endif  // BERBERIS_INTRINSICS_GUEST_RISCV64_FPSTATE_H_
