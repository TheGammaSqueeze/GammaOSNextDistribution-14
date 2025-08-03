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

#ifndef BERBERIS_HEAVY_OPTIMIZER_RISCV64_SIMD_REGISTER_H_
#define BERBERIS_HEAVY_OPTIMIZER_RISCV64_SIMD_REGISTER_H_

#include "berberis/backend/common/machine_ir.h"

namespace berberis {

// Simple wrapper around MachineReg
class SimdReg {
 public:
  constexpr SimdReg() = default;
  constexpr SimdReg(const SimdReg&) = default;
  constexpr SimdReg& operator=(const SimdReg&) = default;
  constexpr SimdReg(SimdReg&&) = default;
  constexpr SimdReg& operator=(SimdReg&&) = default;
  explicit constexpr SimdReg(MachineReg reg) : machine_reg_{reg} {}

  [[nodiscard]] MachineReg constexpr machine_reg() const { return machine_reg_; }

 private:
  MachineReg machine_reg_;
};

}  // namespace berberis

#endif  // BERBERIS_HEAVY_OPTIMIZER_RISCV64_SIMD_REGISTER_H_
