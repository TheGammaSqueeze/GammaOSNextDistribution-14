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

#ifndef BERBERIS_INTRINSICS_INTRINSICS_ATOMICS_IMPL_H_
#define BERBERIS_INTRINSICS_INTRINSICS_ATOMICS_IMPL_H_

#include <cstdint>
#include <type_traits>

#include "berberis/guest_state/guest_addr.h"

namespace berberis::intrinsics {

namespace {

// We are not using std::atomic here because using reinterpret_cast to process normal integers via
// pointer to std::atomic is undefined behavior in C++. There was proposal (N4013) to make that
// behavior defined: https://www.open-std.org/jtc1/sc22/wg21/docs/papers/2014/n4013.html.
// Unfortunately it wasn't accepted and thus we would need to rely on the check for the compiler
// version (both clang and gcc are using layout which makes it safe to use std::atomic in that
// fashion). But the final complication makes even that impractical: while clang builtins support
// fetch_min and fetch_max operations that we need std::atomic don't expose these. This means that
// we would need to mix both styles in that file. At that point it becomes just simpler to go with
// clang/gcc builtins.

inline constexpr int AqRlToMemoryOrder(bool aq, bool rl) {
  if (aq) {
    if (rl) {
      return __ATOMIC_ACQ_REL;
    } else {
      return __ATOMIC_ACQUIRE;
    }
  } else {
    if (rl) {
      return __ATOMIC_RELEASE;
    } else {
      return __ATOMIC_RELAXED;
    }
  }
}

}  // namespace

template <typename IntType, bool aq, bool rl, enum PreferredIntrinsicsImplementation>
std::tuple<IntType> AmoAdd(int64_t arg1, IntType arg2) {
  static_assert(std::is_integral_v<IntType>, "AmoAdd: IntType must be integral");
  static_assert(std::is_signed_v<IntType>, "AmoAdd: IntType must be signed");
  auto ptr = ToHostAddr<IntType>(arg1);
  return {__atomic_fetch_add(ptr, arg2, AqRlToMemoryOrder(aq, rl))};
}

template <typename IntType, bool aq, bool rl, enum PreferredIntrinsicsImplementation>
std::tuple<IntType> AmoAnd(int64_t arg1, IntType arg2) {
  static_assert(std::is_integral_v<IntType>, "AmoAnd: IntType must be integral");
  static_assert(std::is_signed_v<IntType>, "AmoAnd: IntType must be signed");
  auto ptr = ToHostAddr<IntType>(arg1);
  return {__atomic_fetch_and(ptr, arg2, AqRlToMemoryOrder(aq, rl))};
}

template <typename IntType, bool aq, bool rl, enum PreferredIntrinsicsImplementation>
std::tuple<IntType> AmoMax(int64_t arg1, IntType arg2) {
  static_assert(std::is_integral_v<IntType>, "AmoMax: IntType must be integral");
  auto ptr = ToHostAddr<IntType>(arg1);
  return {__atomic_fetch_max(ptr, arg2, AqRlToMemoryOrder(aq, rl))};
}

template <typename IntType, bool aq, bool rl, enum PreferredIntrinsicsImplementation>
std::tuple<IntType> AmoMin(int64_t arg1, IntType arg2) {
  static_assert(std::is_integral_v<IntType>, "AmoMin: IntType must be integral");
  auto ptr = ToHostAddr<IntType>(arg1);
  return {__atomic_fetch_min(ptr, arg2, AqRlToMemoryOrder(aq, rl))};
}

template <typename IntType, bool aq, bool rl, enum PreferredIntrinsicsImplementation>
std::tuple<IntType> AmoOr(int64_t arg1, IntType arg2) {
  static_assert(std::is_integral_v<IntType>, "AmoOr: IntType must be integral");
  static_assert(std::is_signed_v<IntType>, "AmoOr: IntType must be signed");
  auto ptr = ToHostAddr<IntType>(arg1);
  return {__atomic_fetch_or(ptr, arg2, AqRlToMemoryOrder(aq, rl))};
}

template <typename IntType, bool aq, bool rl, enum PreferredIntrinsicsImplementation>
std::tuple<IntType> AmoSwap(int64_t arg1, IntType arg2) {
  static_assert(std::is_integral_v<IntType>, "AmoSwap: IntType must be integral");
  static_assert(std::is_signed_v<IntType>, "AmoSwap: IntType must be signed");
  auto ptr = ToHostAddr<IntType>(arg1);
  return {__atomic_exchange_n(ptr, arg2, AqRlToMemoryOrder(aq, rl))};
}

template <typename IntType, bool aq, bool rl, enum PreferredIntrinsicsImplementation>
std::tuple<IntType> AmoXor(int64_t arg1, IntType arg2) {
  static_assert(std::is_integral_v<IntType>, "AmoXor: IntType must be integral");
  static_assert(std::is_signed_v<IntType>, "AmoXor: IntType must be signed");
  auto ptr = ToHostAddr<IntType>(arg1);
  return {__atomic_fetch_xor(ptr, arg2, AqRlToMemoryOrder(aq, rl))};
}

}  // namespace berberis::intrinsics

#endif  // BERBERIS_INTRINSICS_INTRINSICS_ATOMICS_IMPL_H_
