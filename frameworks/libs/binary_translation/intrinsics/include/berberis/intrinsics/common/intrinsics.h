/*
 * Copyright (C) 2015 The Android Open Source Project
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

#ifndef BERBERIS_INTRINSICS_COMMON_INTRINSICS_H_
#define BERBERIS_INTRINSICS_COMMON_INTRINSICS_H_

#include <cstdint>

#include "berberis/base/dependent_false.h"
#include "berberis/intrinsics/intrinsics_float.h"  // Float32/Float64/ProcessNans

namespace berberis {

class SIMD128Register;

namespace intrinsics {

enum EnumFromTemplateType {
  kInt8T,
  kUInt8T,
  kInt16T,
  kUInt16T,
  kInt32T,
  kUInt32T,
  kInt64T,
  kUInt64T,
  kFloat32,
  kFloat64,
  kSIMD128Register,
};

template <typename Type>
constexpr EnumFromTemplateType TypeToEnumFromTemplateType() {
  if constexpr (std::is_same_v<int8_t, std::decay_t<Type>>) {
    return EnumFromTemplateType::kInt8T;
  } else if constexpr (std::is_same_v<uint8_t, std::decay_t<Type>>) {
    return EnumFromTemplateType::kUInt8T;
  } else if constexpr (std::is_same_v<int16_t, std::decay_t<Type>>) {
    return EnumFromTemplateType::kUInt16T;
  } else if constexpr (std::is_same_v<uint16_t, std::decay_t<Type>>) {
    return EnumFromTemplateType::kUInt16T;
  } else if constexpr (std::is_same_v<int32_t, std::decay_t<Type>>) {
    return EnumFromTemplateType::kUInt32T;
  } else if constexpr (std::is_same_v<uint32_t, std::decay_t<Type>>) {
    return EnumFromTemplateType::kUInt32T;
  } else if constexpr (std::is_same_v<int64_t, std::decay_t<Type>>) {
    return EnumFromTemplateType::kUInt64T;
  } else if constexpr (std::is_same_v<uint64_t, std::decay_t<Type>>) {
    return EnumFromTemplateType::kUInt64T;
  } else if constexpr (std::is_same_v<Float32, std::decay_t<Type>>) {
    return EnumFromTemplateType::kFloat32;
  } else if constexpr (std::is_same_v<Float64, std::decay_t<Type>>) {
    return EnumFromTemplateType::kFloat64;
  } else if constexpr (std::is_same_v<Float64, std::decay_t<Type>>) {
    return EnumFromTemplateType::kSIMD128Register;
  } else {
    static_assert(kDependentTypeFalse<Type>);
  }
}

template <typename Type>
constexpr EnumFromTemplateType kEnumFromTemplateType = TypeToEnumFromTemplateType<Type>();

// A solution for the inability to call generic implementation from specialization.
// Declaration:
//   template <typename Type,
//             int size,
//             enum PreferredIntrinsicsImplementation = kUseAssemblerImplementationIfPossible>
//   inline std::tuple<SIMD128Register> VectorMultiplyByScalarInt(SIMD128Register op1,
//                                                                SIMD128Register op2);
// Normal use only specifies two arguments, e.g. VectorMultiplyByScalarInt<uint32_t, 2>,
// but assembler implementation can (if SSE 4.1 is not available) do the following call:
//   return VectorMultiplyByScalarInt<uint32_t, 2, kUseCppImplementation>(in0, in1);
//
// Because PreferredIntrinsicsImplementation argument has non-default value we have call to the
// generic C-based implementation here.

enum PreferredIntrinsicsImplementation {
  kUseAssemblerImplementationIfPossible,
  kUseCppImplementation
};

}  // namespace intrinsics

}  // namespace berberis

#endif  // BERBERIS_INTRINSICS_COMMON_INTRINSICS_H_
