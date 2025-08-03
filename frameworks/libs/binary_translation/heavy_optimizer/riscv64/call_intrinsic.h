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

#ifndef BERBERIS_HEAVY_OPTIMIZER_RISCV64_CALL_INTRINSIC_H_
#define BERBERIS_HEAVY_OPTIMIZER_RISCV64_CALL_INTRINSIC_H_

#include <type_traits>

#include "berberis/backend/code_emitter.h"
#include "berberis/backend/common/machine_ir.h"
#include "berberis/backend/x86_64/machine_ir.h"
#include "berberis/backend/x86_64/machine_ir_builder.h"
#include "berberis/base/bit_util.h"
#include "berberis/base/dependent_false.h"

#include "simd_register.h"

namespace berberis {

namespace call_intrinsic_internal {

// TODO(b/308951522) Create Expand node in backend and use that instead so it
// can be optimized when possible.
template <typename IntrinsicType>
void SignExtend64(x86_64::MachineIRBuilder* builder, MachineReg dst, MachineReg src) {
  // Note, RISCV64 ABI mandates type-extension up to 32-bit and then sign
  // extension to 64-bit. This meants U8 and U16 are effectively zero-extended
  // to 64-bit.
  if constexpr (sizeof(IntrinsicType) == 1) {
    if constexpr (std::is_signed_v<IntrinsicType>) {
      builder->Gen<x86_64::MovsxbqRegReg>(dst, src);
    } else {
      builder->Gen<x86_64::MovzxbqRegReg>(dst, src);
    }
  } else if constexpr (sizeof(IntrinsicType) == 2) {
    if constexpr (std::is_signed_v<IntrinsicType>) {
      builder->Gen<x86_64::MovsxwqRegReg>(dst, src);
    } else {
      builder->Gen<x86_64::MovzxwqRegReg>(dst, src);
    }
  } else if constexpr (sizeof(IntrinsicType) == 4) {
    builder->Gen<x86_64::MovsxlqRegReg>(dst, src);
  } else {
    static_assert(kDependentTypeFalse<IntrinsicType>,
                  "Unsupported type, only integrals with size 4, 2 and 1 are supported.");
  }
}

template <typename IntrinsicType>
void SignExtend64Result(x86_64::MachineIRBuilder* builder, MachineReg dst, MachineReg src) {
  if constexpr (sizeof(IntrinsicType) == 8) {
    builder->Gen<PseudoCopy>(dst, src, 8);
  } else {
    static_assert(sizeof(IntrinsicType) == 4, "8- and 16-bit return values are not yet supported");
    call_intrinsic_internal::SignExtend64<IntrinsicType>(builder, dst, src);
  }
}

template <
    typename IntrinsicType,
    typename AssemblerType,
    typename std::enable_if_t<!std::is_same_v<AssemblerType, MachineReg> &&
                                  std::is_integral_v<IntrinsicType> && (sizeof(IntrinsicType) <= 4),
                              bool> = true>
x86_64::CallImm::Arg GenPrepareCallImmArg(x86_64::MachineIRBuilder* builder, AssemblerType val) {
  static_assert(std::is_same_v<AssemblerType, IntrinsicType>);
  MachineReg reg = builder->ir()->AllocVReg();
  MachineReg temp_reg = builder->ir()->AllocVReg();
  // SignExtend64 works with registers, we need to copy integral value to a register before calling
  // it.
  builder->Gen<x86_64::MovlRegImm>(temp_reg, static_cast<uint32_t>(val));
  SignExtend64<IntrinsicType>(builder, reg, temp_reg);
  return {reg, x86_64::CallImm::kIntRegType};
}

template <
    typename IntrinsicType,
    typename AssemblerType,
    typename std::enable_if_t<std::is_same_v<AssemblerType, MachineReg> &&
                                  (std::is_integral_v<IntrinsicType> ||
                                   std::is_pointer_v<IntrinsicType>)&&(sizeof(IntrinsicType) == 8),
                              bool> = true>
x86_64::CallImm::Arg GenPrepareCallImmArg(x86_64::MachineIRBuilder* /*builder*/,
                                          AssemblerType reg) {
  return {reg, x86_64::CallImm::kIntRegType};
}

template <
    typename IntrinsicType,
    typename AssemblerType,
    typename std::enable_if_t<std::is_same_v<AssemblerType, MachineReg> &&
                                  std::is_integral_v<IntrinsicType> && (sizeof(IntrinsicType) <= 4),
                              bool> = true>
x86_64::CallImm::Arg GenPrepareCallImmArg(x86_64::MachineIRBuilder* builder, AssemblerType reg) {
  MachineReg new_reg = builder->ir()->AllocVReg();
  SignExtend64<IntrinsicType>(builder, new_reg, reg);
  return {new_reg, x86_64::CallImm::kIntRegType};
}

template <typename IntrinsicType,
          typename AssemblerType,
          typename std::enable_if_t<std::is_same_v<AssemblerType, SimdReg>, bool> = true>
x86_64::CallImm::Arg GenPrepareCallImmArg(x86_64::MachineIRBuilder* /*builder*/,
                                          AssemblerType reg) {
  return {reg.machine_reg(), x86_64::CallImm::kXmmRegType};
}

template <typename IntrinsicResType, typename AssemblerResType>
void LoadCallIntrinsicResult(x86_64::MachineIRBuilder* builder,
                             MachineReg result_ptr,
                             AssemblerResType result) {
  static_assert(std::tuple_size_v<IntrinsicResType> == std::tuple_size_v<AssemblerResType>);
  constexpr const uint32_t kResultTupleSize = std::tuple_size_v<IntrinsicResType>;
  static_assert(kResultTupleSize > 1, "Result tuple size is expected to be at least 2");

  if constexpr (kResultTupleSize == 2) {
    using FirstElementType = std::tuple_element_t<0, IntrinsicResType>;
    using SecondElementType = std::tuple_element_t<1, IntrinsicResType>;

    auto first_reg = std::get<0>(result);
    auto second_reg = std::get<1>(result);

    if constexpr (std::is_same_v<FirstElementType, SIMD128Register>) {
      builder->Gen<x86_64::MovdquXRegMemBaseDisp>(first_reg.machine_reg(), result_ptr, 0);
      if constexpr (std::is_same_v<SecondElementType, SIMD128Register>) {
        builder->Gen<x86_64::MovdquXRegMemBaseDisp>(second_reg.machine_reg(), result_ptr, 16);
      } else if constexpr (std::is_integral_v<SecondElementType>) {
        builder->Gen<x86_64::MovqRegMemBaseDisp>(second_reg, result_ptr, 16);
      } else {
        static_assert(kDependentTypeFalse<IntrinsicResType>, "Unsupported intrinsic return type.");
      }
    } else {
      static_assert(kDependentTypeFalse<IntrinsicResType>, "Unsupported intrinsic return type.");
    }
  } else if constexpr (kResultTupleSize == 3) {
    using FirstElementType = std::tuple_element_t<0, IntrinsicResType>;
    using SecondElementType = std::tuple_element_t<1, IntrinsicResType>;
    using ThirdElementType = std::tuple_element_t<2, IntrinsicResType>;

    if constexpr (std::is_same_v<FirstElementType, SIMD128Register> &&
                  std::is_same_v<SecondElementType, SIMD128Register> &&
                  std::is_same_v<ThirdElementType, SIMD128Register>) {
      builder->Gen<x86_64::MovdquXRegMemBaseDisp>(
          std::get<0>(result).machine_reg(), result_ptr, 0 * 16);
      builder->Gen<x86_64::MovdquXRegMemBaseDisp>(
          std::get<1>(result).machine_reg(), result_ptr, 1 * 16);
      builder->Gen<x86_64::MovdquXRegMemBaseDisp>(
          std::get<2>(result).machine_reg(), result_ptr, 2 * 16);
    } else {
      static_assert(kDependentTypeFalse<IntrinsicResType>, "Unsupported intrinsic return type.");
    }
  } else if constexpr (kResultTupleSize == 4) {
    using FirstElementType = std::tuple_element_t<0, IntrinsicResType>;
    using SecondElementType = std::tuple_element_t<1, IntrinsicResType>;
    using ThirdElementType = std::tuple_element_t<2, IntrinsicResType>;
    using FourthElementType = std::tuple_element_t<3, IntrinsicResType>;

    if constexpr (std::is_same_v<FirstElementType, SIMD128Register> &&
                  std::is_same_v<SecondElementType, SIMD128Register> &&
                  std::is_same_v<ThirdElementType, SIMD128Register> &&
                  std::is_same_v<FourthElementType, SIMD128Register>) {
      builder->Gen<x86_64::MovdquXRegMemBaseDisp>(
          std::get<0>(result).machine_reg(), result_ptr, 0 * 16);
      builder->Gen<x86_64::MovdquXRegMemBaseDisp>(
          std::get<1>(result).machine_reg(), result_ptr, 1 * 16);
      builder->Gen<x86_64::MovdquXRegMemBaseDisp>(
          std::get<2>(result).machine_reg(), result_ptr, 2 * 16);
      builder->Gen<x86_64::MovdquXRegMemBaseDisp>(
          std::get<3>(result).machine_reg(), result_ptr, 3 * 16);
    } else {
      static_assert(kDependentTypeFalse<IntrinsicResType>, "Unsupported intrinsic return type.");
    }
  } else {
    static_assert(kDependentTypeFalse<IntrinsicResType>, "Unsupported intrinsic return type.");
  }
}

}  // namespace call_intrinsic_internal

// Specialization for IntrinsicResType=void
template <typename IntrinsicResType,
          typename... IntrinsicArgType,
          typename... AssemblerArgType,
          std::enable_if_t<std::is_same_v<IntrinsicResType, void>, bool> = true>
void CallIntrinsicImpl(x86_64::MachineIRBuilder* builder,
                       IntrinsicResType (*function)(IntrinsicArgType...),
                       MachineReg flag_register,
                       AssemblerArgType... args) {
  // Store fixed parameters into registers and prepare list of input parameters for
  // GenPrepareCallImmArg.
  constexpr const size_t kArgumentArraySize = sizeof...(IntrinsicArgType);
  std::array<x86_64::CallImm::Arg, kArgumentArraySize> args_for_call_imm;
  size_t index = 0;

  ((args_for_call_imm[index++] =
        call_intrinsic_internal::GenPrepareCallImmArg<IntrinsicArgType, AssemblerArgType>(builder,
                                                                                          args)),
   ...);

  builder->GenCallImm(bit_cast<uintptr_t>(function), flag_register, args_for_call_imm);
}

template <typename AssemblerResType,
          typename IntrinsicResType,
          typename... IntrinsicArgType,
          typename... AssemblerArgType,
          std::enable_if_t<!std::is_same_v<IntrinsicResType, void>, bool> = true>
void CallIntrinsicImpl(x86_64::MachineIRBuilder* builder,
                       IntrinsicResType (*function)(IntrinsicArgType...),
                       AssemblerResType result,
                       MachineReg flag_register,
                       AssemblerArgType... args) {
  constexpr const bool kIsResultOnStack = sizeof(IntrinsicResType) > 16;

  // Store fixed parameters into registers and prepare list of input parameters for
  // GenPrepareCallImmArg.
  constexpr const size_t kArgumentArraySize =
      kIsResultOnStack ? sizeof...(IntrinsicArgType) + 1 : sizeof...(IntrinsicArgType);

  std::array<x86_64::CallImm::Arg, kArgumentArraySize> args_for_call_imm;

  size_t index = 0;
  if constexpr (kIsResultOnStack) {
    builder->ir()->ReserveArgs(sizeof(IntrinsicResType));
    args_for_call_imm[index++] = {x86_64::kMachineRegRSP, x86_64::CallImm::kIntRegType};
  }

  ((args_for_call_imm[index++] =
        call_intrinsic_internal::GenPrepareCallImmArg<IntrinsicArgType, AssemblerArgType>(builder,
                                                                                          args)),
   ...);

  auto* call = builder->GenCallImm(bit_cast<uintptr_t>(function), flag_register, args_for_call_imm);

  if constexpr (kIsResultOnStack) {
    call_intrinsic_internal::LoadCallIntrinsicResult<IntrinsicResType>(
        builder, call->IntResultAt(0), result);
  } else if constexpr (std::tuple_size_v<IntrinsicResType> == 1) {
    using ResultType = std::tuple_element_t<0, IntrinsicResType>;
    if constexpr (std::is_integral_v<ResultType>) {
      call_intrinsic_internal::SignExtend64Result<ResultType>(
          builder, result, call->IntResultAt(0));
    } else {
      builder->Gen<PseudoCopy>(result.machine_reg(), call->XmmResultAt(0), 16);
    }
  } else if constexpr (std::tuple_size_v<IntrinsicResType> == 2) {
    using ResultType1 = std::tuple_element_t<0, IntrinsicResType>;
    using ResultType2 = std::tuple_element_t<1, IntrinsicResType>;
    // The only case where it is not on stack is two integral types
    static_assert(std::is_integral_v<ResultType1> && std::is_integral_v<ResultType2>);

    call_intrinsic_internal::SignExtend64Result<ResultType1>(
        builder, std::get<0>(result), call->IntResultAt(0));
    call_intrinsic_internal::SignExtend64Result<ResultType2>(
        builder, std::get<1>(result), call->IntResultAt(1));
  } else {
    static_assert(kDependentTypeFalse<IntrinsicResType>, "Unsupported result type");
  }
}

}  // namespace berberis
#endif  // BERBERIS_HEAVY_OPTIMIZER_RISCV64_CALL_INTRINSIC_H_
