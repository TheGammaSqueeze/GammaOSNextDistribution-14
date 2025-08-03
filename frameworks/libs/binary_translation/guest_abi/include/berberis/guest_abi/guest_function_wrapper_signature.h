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

#ifndef BERBERIS_GUEST_ABI_GUEST_FUNCTION_WRAPPER_SIGNATURE_H_
#define BERBERIS_GUEST_ABI_GUEST_FUNCTION_WRAPPER_SIGNATURE_H_

#include <cstdint>
#include <type_traits>

namespace berberis {

// Signature string:
//   "<return-type-char><param-type-char><param-type-char>"
//
// Supported types:
//   'v': void (as return type)
//   'i': integer and enum types <= 32bit
//   'l': integer and enum types == 64bit
//   'p': pointers (to objects and functions but not to members)
//   'f': float (floating point 32 bits)
//   'd': double (floating point 64 bits)
//
// Signature char (template constant) for return type or parameter:
//   kGuestFunctionWrapperSignatureChar<Type>
//
// Signature (template constant) for function or function pointer:
//   kGuestFunctionWrapperSignature<int (int)>

class kGuestFunctionWrapperSignatureCharHelper {
 public:
  template <typename Type, std::enable_if_t<std::is_same_v<Type, void>, int> = 0>
  static constexpr char Value() {
    return 'v';
  }

  template <typename Type,
            std::enable_if_t<(std::is_integral_v<Type> || std::is_enum_v<Type>)&&sizeof(Type) <=
                                 sizeof(int32_t),
                             int> = 0>
  static constexpr char Value() {
    return 'i';
  }

  template <typename Type,
            std::enable_if_t<(std::is_integral_v<Type> || std::is_enum_v<Type>)&&sizeof(Type) ==
                                 sizeof(int64_t),
                             int> = 0>
  static constexpr char Value() {
    return 'l';
  }

  template <typename Type, std::enable_if_t<std::is_pointer_v<Type>, int> = 0>
  static constexpr char Value() {
    return 'p';
  }

  template <
      typename Type,
      std::enable_if_t<std::is_same_v<Type, float> && sizeof(Type) == sizeof(int32_t), int> = 0>
  static constexpr char Value() {
    return 'f';
  }

  template <
      typename Type,
      std::enable_if_t<std::is_same_v<Type, double> && sizeof(Type) == sizeof(int64_t), int> = 0>
  static constexpr char Value() {
    return 'd';
  }
};

template <typename Arg>
constexpr char kGuestFunctionWrapperSignatureChar =
    kGuestFunctionWrapperSignatureCharHelper::Value<Arg>();

template <typename Func>
class kGuestFunctionWrapperSignatureHelper;

template <typename Result, typename... Args>
class kGuestFunctionWrapperSignatureHelper<Result (*)(Args...)> {
 public:
  constexpr static const char kValue[] = {kGuestFunctionWrapperSignatureChar<Result>,
                                          kGuestFunctionWrapperSignatureChar<Args>...,
                                          0};
};

template <typename Func>
constexpr static const char (&kGuestFunctionWrapperSignature)[sizeof(
    kGuestFunctionWrapperSignatureHelper<std::decay_t<Func>>::kValue)] =
    kGuestFunctionWrapperSignatureHelper<std::decay_t<Func>>::kValue;

}  // namespace berberis

#endif  // BERBERIS_GUEST_ABI_GUEST_FUNCTION_WRAPPER_SIGNATURE_H_
