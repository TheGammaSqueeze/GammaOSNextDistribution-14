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

#ifndef BERBERIS_LITE_TRANSLATOR_RISCV64_ALLOCATOR_H_
#define BERBERIS_LITE_TRANSLATOR_RISCV64_ALLOCATOR_H_

#include <algorithm>  // std::max
#include <optional>

#include "berberis/assembler/x86_64.h"
#include "berberis/base/dependent_false.h"

namespace berberis {

template <typename RegType>
inline constexpr auto kAllocatableRegisters = []() {
  static_assert(kDependentTypeFalse<RegType>,
                "kAllocatableRegisters is only usable with x86_64::Assembler::Register or "
                "x86_64::Assembler::XMMRegister");
  return true;
};

// TODO(286261771): Add rdx to registers, push it on stack in all instances that are clobbering it.
template <>
inline constexpr x86_64::Assembler::Register kAllocatableRegisters<x86_64::Assembler::Register>[] =
    {x86_64::Assembler::rbx,
     x86_64::Assembler::rsi,
     x86_64::Assembler::rdi,
     x86_64::Assembler::r8,
     x86_64::Assembler::r9,
     x86_64::Assembler::r10,
     x86_64::Assembler::r11,
     x86_64::Assembler::r12,
     x86_64::Assembler::r13,
     x86_64::Assembler::r14,
     x86_64::Assembler::r15};

template <>
inline constexpr x86_64::Assembler::XMMRegister
    kAllocatableRegisters<x86_64::Assembler::XMMRegister>[] = {x86_64::Assembler::xmm0,
                                                               x86_64::Assembler::xmm1,
                                                               x86_64::Assembler::xmm2,
                                                               x86_64::Assembler::xmm3,
                                                               x86_64::Assembler::xmm4,
                                                               x86_64::Assembler::xmm5,
                                                               x86_64::Assembler::xmm6,
                                                               x86_64::Assembler::xmm7,
                                                               x86_64::Assembler::xmm8,
                                                               x86_64::Assembler::xmm9,
                                                               x86_64::Assembler::xmm10,
                                                               x86_64::Assembler::xmm11,
                                                               x86_64::Assembler::xmm12,
                                                               x86_64::Assembler::xmm13,
                                                               x86_64::Assembler::xmm14,
                                                               x86_64::Assembler::xmm15};

template <typename RegType>
class Allocator {
 public:
  std::optional<RegType> Alloc() {
    if (regs_allocated_ + max_temp_regs_allocated >= kNumRegister) {
      return std::nullopt;
    }
    return std::optional<RegType>(kAllocatableRegisters<RegType>[regs_allocated_++]);
  }

  std::optional<RegType> AllocTemp() {
    if (regs_allocated_ + temp_regs_allocated >= kNumRegister) {
      return std::nullopt;
    }
    auto res = std::optional<RegType>(
        kAllocatableRegisters<RegType>[kNumRegister - 1 - temp_regs_allocated]);
    temp_regs_allocated++;
    max_temp_regs_allocated = std::max(max_temp_regs_allocated, temp_regs_allocated);
    return res;
  }

  void FreeTemps() { temp_regs_allocated = 0; }

 private:
  inline static const uint32_t kNumRegister = std::size(kAllocatableRegisters<RegType>);

  uint32_t regs_allocated_ = 0;
  uint32_t temp_regs_allocated = 0;
  uint32_t max_temp_regs_allocated = 0;
};

}  // namespace berberis

#endif  // BERBERIS_LITE_TRANSLATOR_RISCV64_ALLOCATOR_H_
