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

#ifndef BERBERIS_LITE_TRANSLATOR_RISCV64_TO_X86_64_CALL_INTRINSIC_H_
#define BERBERIS_LITE_TRANSLATOR_RISCV64_TO_X86_64_CALL_INTRINSIC_H_

#include <array>
#include <cstdint>
#include <type_traits>

#include "berberis/assembler/x86_64.h"
#include "berberis/base/bit_util.h"
#include "berberis/base/dependent_false.h"
#include "berberis/intrinsics/macro_assembler.h"
#include "berberis/runtime_primitives/platform.h"

namespace berberis::call_intrinsic {

constexpr x86_64::Assembler::Register kCallerSavedRegs[] = {
    x86_64::Assembler::rax,
    x86_64::Assembler::rcx,
    x86_64::Assembler::rdx,
    x86_64::Assembler::rdi,
    x86_64::Assembler::rsi,
    x86_64::Assembler::r8,
    x86_64::Assembler::r9,
    x86_64::Assembler::r10,
    x86_64::Assembler::r11,
};

constexpr int8_t kRegIsNotOnStack = -1;

// Map from register number to offset in CallIntrinsic save area. Counted in 8-byte slots.
inline constexpr auto kRegOffsetsOnStack = []() {
  std::array<int8_t, 16> regs_on_stack = {};
  // regs_on_stack.fill(kRegIsNotOnStack); - needs C++20
  for (auto& num : regs_on_stack) {
    num = kRegIsNotOnStack;
  }

  int8_t stack_allocation_size = 0;
  for (auto reg : kCallerSavedRegs) {
    regs_on_stack[reg.num] = stack_allocation_size;
    ++stack_allocation_size;
  }
  return regs_on_stack;
}();

constexpr x86_64::Assembler::XMMRegister kCallerSavedXMMRegs[] = {
    x86_64::Assembler::xmm0,
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
    x86_64::Assembler::xmm15,
};

// Map from register number to offset in CallIntrinsic save area. Counted in 8-byte slots.
inline constexpr auto kSimdRegOffsetsOnStack = []() {
  std::array<int8_t, 16> simd_regs_on_stack = {};
  // simd_regs_on_stack.fill(kRegIsNotOnStack); - needs C++20
  for (auto& num : simd_regs_on_stack) {
    num = kRegIsNotOnStack;
  }

  int8_t stack_allocation_size = AlignUp(std::size(kCallerSavedRegs), 2);
  for (auto reg : kCallerSavedXMMRegs) {
    simd_regs_on_stack[reg.num] = stack_allocation_size;
    stack_allocation_size += 2;
  }
  return simd_regs_on_stack;
}();

// Save area size for CallIntrinsic save area. Counted in 8-byte slots.
inline constexpr int8_t kSaveAreaSize =
    AlignUp(std::size(kCallerSavedRegs), 2) + std::size(kCallerSavedXMMRegs) * 2;

struct StoredRegsInfo {
  std::decay_t<decltype(kRegOffsetsOnStack)> regs_on_stack;
  std::decay_t<decltype(kSimdRegOffsetsOnStack)> simd_regs_on_stack;
};

inline void PushCallerSaved(MacroAssembler<x86_64::Assembler>& as) {
  as.Subq(as.rsp, kSaveAreaSize * 8);

  for (auto reg : kCallerSavedRegs) {
    as.Movq({.base = as.rsp, .disp = kRegOffsetsOnStack[reg.num] * 8}, reg);
  }

  for (auto reg : kCallerSavedXMMRegs) {
    as.Movdqa({.base = as.rsp, .disp = kSimdRegOffsetsOnStack[reg.num] * 8}, reg);
  }
}

// Note: regs_on_stack is usually copy of kRegOffsetsOnStack with some registers marked off as
// kRegIsNotOnStack, simd_regs_on_stack is kSimdRegOffsetsOnStack with some registers marked as
// kRegIsNotOnStack. These registers are skipped during restoration process.
inline void PopCallerSaved(MacroAssembler<x86_64::Assembler>& as, const StoredRegsInfo regs_info) {
  for (auto reg : kCallerSavedRegs) {
    if (regs_info.regs_on_stack[reg.num] != kRegIsNotOnStack) {
      as.Movq(reg, {.base = as.rsp, .disp = regs_info.regs_on_stack[reg.num] * 8});
    }
  }
  for (auto reg : kCallerSavedXMMRegs) {
    if (regs_info.simd_regs_on_stack[reg.num] != kRegIsNotOnStack) {
      as.Movdqa(reg, {.base = as.rsp, .disp = regs_info.simd_regs_on_stack[reg.num] * 8});
    }
  }

  as.Addq(as.rsp, kSaveAreaSize * 8);
}

// Nonfunctional assembler used by static_assert expression. It doesn't do anything but allows us
// to call InitArgs during compilation time with the same argument types as would happen during
// execution.
//
// This turns runtime check into compile time check and thus allows us to catch weird corner cases
// faster.
class ConstExprCheckAssembler {
 public:
  using Operand = MacroAssembler<x86_64::Assembler>::Operand;
  using Register = MacroAssembler<x86_64::Assembler>::Register;
  using XMMRegister = MacroAssembler<x86_64::Assembler>::XMMRegister;
  static constexpr auto rsp = MacroAssembler<x86_64::Assembler>::rsp;

  constexpr ConstExprCheckAssembler() = default;

  template <typename U, typename V>
  constexpr void Expand(Register, Operand) const {}
  template <typename U, typename V>
  constexpr void Expand(Register, Register) const {}

  template <typename U>
  constexpr void Mov(Operand, Register) const {}
  template <typename U>
  constexpr void Mov(Register, Operand) const {}
  template <typename U>
  constexpr void Mov(Register, Register) const {}

  constexpr void Movl(Register, int32_t) const {}

  template <typename U>
  constexpr void Movs(Operand, XMMRegister) const {}
  template <typename U>
  constexpr void Movs(XMMRegister, Operand) const {}
  template <typename U>
  constexpr void Movs(XMMRegister, XMMRegister) const {}

  template <typename U>
  constexpr void Vmovs(Operand, XMMRegister) const {}
  template <typename U>
  constexpr void Vmovs(XMMRegister, Operand) const {}
  template <typename U>
  constexpr void Vmovs(XMMRegister, XMMRegister, XMMRegister) const {}
};

// Helper wrapper to pass the intrinsic type down the generic lambda.
template <typename T, typename U>
struct ArgWrap {
  using AssemblerType = T;
  using IntrinsicType = U;
  AssemblerType value;
};

static constexpr x86_64::Assembler::Register kAbiArgs[] = {
    x86_64::Assembler::rdi,
    x86_64::Assembler::rsi,
    x86_64::Assembler::rdx,
    x86_64::Assembler::rcx,
    x86_64::Assembler::r8,
    x86_64::Assembler::r9,
};

static constexpr x86_64::Assembler::XMMRegister kAbiSimdArgs[] = {
    x86_64::Assembler::xmm0,
    x86_64::Assembler::xmm1,
    x86_64::Assembler::xmm2,
    x86_64::Assembler::xmm3,
    x86_64::Assembler::xmm4,
    x86_64::Assembler::xmm5,
    x86_64::Assembler::xmm6,
    x86_64::Assembler::xmm7,
};

// Assumes RSP points to preallocated stack args area.
template <typename IntrinsicResType,
          typename... IntrinsicArgType,
          typename MacroAssembler,
          typename... AssemblerArgType>
constexpr bool InitArgs(MacroAssembler&& as, bool has_avx, AssemblerArgType... args) {
  using Assembler = std::decay_t<MacroAssembler>;
  using Register = typename Assembler::Register;
  using XMMRegister = typename Assembler::XMMRegister;
  using Float32 = intrinsics::Float32;
  using Float64 = intrinsics::Float64;

  // All ABI argument registers are saved among caller-saved registers, so we can safely initialize
  // them now. When intrinsic receives its argument from such register we'll read it from stack, so
  // there is no early-clobbering problem. Callee-saved regs are never ABI arguments, so we can move
  // them to ABI reg directly.

  size_t gp_index = 0;
  size_t simd_index = 0;
  bool success = ([&as, &gp_index, &simd_index, has_avx](auto arg) -> bool {
    using AssemblerType = typename decltype(arg)::AssemblerType;
    using IntrinsicType = typename decltype(arg)::IntrinsicType;

    if (std::is_integral_v<IntrinsicType>) {
      if (gp_index == std::size(kAbiArgs)) {
        return false;
      }
    } else if constexpr (std::is_same_v<IntrinsicType, Float32> ||
                         std::is_same_v<IntrinsicType, Float64>) {
      if (simd_index == std::size(kAbiSimdArgs)) {
        return false;
      }
    } else {
      return false;
    }

    // Note, ABI mandates extension up to 32-bit and zero-filling the upper half.
    if constexpr (std::is_integral_v<IntrinsicType> && sizeof(IntrinsicType) <= sizeof(int32_t) &&
                  std::is_integral_v<AssemblerType> && sizeof(AssemblerType) <= sizeof(int32_t)) {
      as.Movl(kAbiArgs[gp_index++], static_cast<int32_t>(arg.value));
    } else if constexpr (std::is_integral_v<IntrinsicType> &&
                         sizeof(IntrinsicType) == sizeof(int64_t) &&
                         std::is_integral_v<AssemblerType> &&
                         sizeof(AssemblerType) == sizeof(int64_t)) {
      as.template Expand<int64_t, IntrinsicType>(kAbiArgs[gp_index++],
                                                 static_cast<int64_t>(arg.value));
    } else if constexpr (std::is_integral_v<IntrinsicType> &&
                         sizeof(IntrinsicType) <= sizeof(int32_t) &&
                         std::is_same_v<AssemblerType, Register>) {
      if (kRegOffsetsOnStack[arg.value.num] == kRegIsNotOnStack) {
        as.template Expand<int32_t, IntrinsicType>(kAbiArgs[gp_index++], arg.value);
      } else {
        as.template Expand<int32_t, IntrinsicType>(
            kAbiArgs[gp_index++],
            {.base = Assembler::rsp, .disp = kRegOffsetsOnStack[arg.value.num] * 8});
      }
    } else if constexpr (std::is_integral_v<IntrinsicType> &&
                         sizeof(IntrinsicType) == sizeof(int64_t) &&
                         std::is_same_v<AssemblerType, Register>) {
      if (kRegOffsetsOnStack[arg.value.num] == kRegIsNotOnStack) {
        as.template Expand<int64_t, IntrinsicType>(kAbiArgs[gp_index++], arg.value);
      } else {
        as.template Expand<int64_t, IntrinsicType>(
            kAbiArgs[gp_index++],
            {.base = Assembler::rsp, .disp = kRegOffsetsOnStack[arg.value.num] * 8});
      }
    } else if constexpr ((std::is_same_v<IntrinsicType, Float32> ||
                          std::is_same_v<IntrinsicType, Float64>)&&std::is_same_v<AssemblerType,
                                                                                  XMMRegister>) {
      if (kSimdRegOffsetsOnStack[arg.value.num] == kRegIsNotOnStack) {
        if (has_avx) {
          as.template Vmovs<IntrinsicType>(
              kAbiSimdArgs[simd_index], kAbiSimdArgs[simd_index], arg.value);
          simd_index++;
        } else {
          as.template Movs<IntrinsicType>(kAbiSimdArgs[simd_index++], arg.value);
        }
      } else {
        if (has_avx) {
          as.template Vmovs<IntrinsicType>(
              kAbiSimdArgs[simd_index++],
              {.base = as.rsp, .disp = kSimdRegOffsetsOnStack[arg.value.num] * 8});
        } else {
          as.template Movs<IntrinsicType>(
              kAbiSimdArgs[simd_index++],
              {.base = as.rsp, .disp = kSimdRegOffsetsOnStack[arg.value.num] * 8});
        }
      }
    } else {
      static_assert(kDependentTypeFalse<std::tuple<IntrinsicType, AssemblerType>>,
                    "Unknown parameter type, please add support to CallIntrinsic");
    }
    return true;
  }(ArgWrap<AssemblerArgType, IntrinsicArgType>{.value = args}) && ...);
  return success;
}

// Forward results from ABI registers to result-specified registers and mark registers in the
// returned StoredRegsInfo with kRegIsNotOnStack to prevent restoration from stack.
template <typename IntrinsicResType, typename AssemblerResType>
StoredRegsInfo ForwardResults(MacroAssembler<x86_64::Assembler>& as, AssemblerResType result) {
  using Assembler = MacroAssembler<x86_64::Assembler>;
  using Register = Assembler::Register;
  using XMMRegister = Assembler::XMMRegister;
  using Float32 = intrinsics::Float32;
  using Float64 = intrinsics::Float64;

  StoredRegsInfo regs_info = {.regs_on_stack = kRegOffsetsOnStack,
                              .simd_regs_on_stack = kSimdRegOffsetsOnStack};

  if constexpr (Assembler::kFormatIs<IntrinsicResType, std::tuple<int32_t>, std::tuple<uint32_t>> &&
                std::is_same_v<AssemblerResType, Register>) {
    // Note: even unsigned 32-bit results are sign-extended to 64bit register on RV64.
    regs_info.regs_on_stack[result.num] = kRegIsNotOnStack;
    as.Expand<int64_t, int32_t>(result, Assembler::rax);
  } else if constexpr (Assembler::
                           kFormatIs<IntrinsicResType, std::tuple<int64_t>, std::tuple<uint64_t>> &&
                       std::is_same_v<AssemblerResType, Register>) {
    regs_info.regs_on_stack[result.num] = kRegIsNotOnStack;
    as.Mov<int64_t>(result, Assembler::rax);
  } else if constexpr (Assembler::
                           kFormatIs<IntrinsicResType, std::tuple<Float32>, std::tuple<Float64>> &&
                       std::is_same_v<AssemblerResType, XMMRegister>) {
    using ResType0 = std::tuple_element_t<0, IntrinsicResType>;
    regs_info.simd_regs_on_stack[result.num] = kRegIsNotOnStack;
    if (host_platform::kHasAVX) {
      as.Vmovs<ResType0>(result, result, Assembler::xmm0);
    } else {
      as.Movs<ResType0>(result, Assembler::xmm0);
    }
  } else if constexpr (std::tuple_size_v<IntrinsicResType> == 2) {
    using ResType0 = std::tuple_element_t<0, IntrinsicResType>;
    using ResType1 = std::tuple_element_t<1, IntrinsicResType>;
    auto [result0, result1] = result;
    if constexpr (Assembler::kFormatIs<ResType0, int32_t, uint32_t> &&
                  std::is_same_v<std::tuple_element_t<0, AssemblerResType>, Register>) {
      regs_info.regs_on_stack[result0.num] = kRegIsNotOnStack;
      as.Expand<int64_t, int32_t>(result0, Assembler::rax);
    } else if constexpr (Assembler::kFormatIs<ResType0, int64_t, uint64_t> &&
                         std::is_same_v<std::tuple_element_t<0, AssemblerResType>, Register>) {
      regs_info.regs_on_stack[result0.num] = kRegIsNotOnStack;
      as.Mov<int64_t>(result0, Assembler::rax);
    } else {
      static_assert(kDependentTypeFalse<std::tuple<IntrinsicResType, AssemblerResType>>,
                    "Unknown result type, please add support to CallIntrinsic");
    }
    if constexpr (Assembler::kFormatIs<ResType1, int32_t, uint32_t> &&
                  std::is_same_v<std::tuple_element_t<1, AssemblerResType>, Register>) {
      regs_info.regs_on_stack[result1.num] = kRegIsNotOnStack;
      as.Expand<int64_t, int32_t>(result1, Assembler::rdx);
    } else if constexpr (Assembler::kFormatIs<ResType1, int64_t, uint64_t> &&
                         std::is_same_v<std::tuple_element_t<1, AssemblerResType>, Register>) {
      regs_info.regs_on_stack[result1.num] = kRegIsNotOnStack;
      as.Mov<int64_t>(result1, Assembler::rdx);
    } else {
      static_assert(kDependentTypeFalse<std::tuple<IntrinsicResType, AssemblerResType>>,
                    "Unknown result type, please add support to CallIntrinsic");
    }
  } else {
    static_assert(kDependentTypeFalse<std::tuple<IntrinsicResType, AssemblerResType>>,
                  "Unknown result type, please add support to CallIntrinsic");
  }
  return regs_info;
}

// Note: we can ignore status in the actual InitArgs call because we know that InitArgs would
// succeed if the call in static_assert succeeded.
//
// AVX flag shouldn't change the outcome, but better safe than sorry.

template <typename IntrinsicResType, typename... IntrinsicArgType, typename... AssemblerArgType>
void InitArgsVerify(AssemblerArgType...) {
  static_assert(InitArgs<IntrinsicResType, IntrinsicArgType...>(
      ConstExprCheckAssembler(), true, AssemblerArgType{0}...));
  static_assert(InitArgs<IntrinsicResType, IntrinsicArgType...>(
      ConstExprCheckAssembler(), false, AssemblerArgType{0}...));
}

template <typename AssemblerResType,
          typename IntrinsicResType,
          typename... IntrinsicArgType,
          typename... AssemblerArgType>
void CallIntrinsic(MacroAssembler<x86_64::Assembler>& as,
                   IntrinsicResType (*function)(IntrinsicArgType...),
                   AssemblerResType result,
                   AssemblerArgType... args) {
  PushCallerSaved(as);

  InitArgsVerify<IntrinsicResType, IntrinsicArgType...>(args...);
  InitArgs<IntrinsicResType, IntrinsicArgType...>(as, host_platform::kHasAVX, args...);

  as.Call(reinterpret_cast<void*>(function));

  auto regs_info = ForwardResults<IntrinsicResType>(as, result);

  PopCallerSaved(as, regs_info);
}

template <typename AssemblerResType, typename... IntrinsicArgType, typename... AssemblerArgType>
void CallIntrinsic(MacroAssembler<x86_64::Assembler>& as,
                   void (*function)(IntrinsicArgType...),
                   AssemblerArgType... args) {
  PushCallerSaved(as);

  InitArgsVerify<void, IntrinsicArgType...>(args...);
  InitArgs<void, IntrinsicArgType...>(as, host_platform::kHasAVX, args...);

  as.Call(reinterpret_cast<void*>(function));

  PopCallerSaved(
      as, {.regs_on_stack = kRegOffsetsOnStack, .simd_regs_on_stack = kSimdRegOffsetsOnStack});
}

}  // namespace berberis::call_intrinsic

#endif  // BERBERIS_LITE_TRANSLATOR_RISCV64_TO_X86_64_CALL_INTRINSIC_H_
