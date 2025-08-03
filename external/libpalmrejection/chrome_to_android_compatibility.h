// Copyright 2022 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#pragma once

#include "base/time/time.h"

// Android's external/libchrome directory is out of date.
// Add missing templates here as a temporary solution
namespace base {

/**
 * Workaround for the error in unit tests: ISO C++20 considers use of overloaded
 * operator '==' (with operand types 'const base::TimeTicks'
 * and 'const base::TimeTicks') to be ambiguous despite there being a unique
 * best viable function [-Werror,-Wambiguous-reversed-operator]
 */
bool operator==(const TimeTicks& t1, const TimeTicks& t2);

namespace time_internal {

// clang-format off
template <typename T>
using EnableIfIntegral = typename std::
    enable_if<std::is_integral<T>::value || std::is_enum<T>::value, int>::type;
template <typename T>
using EnableIfFloat =
    typename std::enable_if<std::is_floating_point<T>::value, int>::type;

}  // namespace time_internal


template <typename T, time_internal::EnableIfIntegral<T> = 0>
constexpr TimeDelta Seconds(T n) {
  return TimeDelta::FromInternalValue(
      ClampMul(static_cast<int64_t>(n), Time::kMicrosecondsPerSecond));
}
template <typename T, time_internal::EnableIfIntegral<T> = 0>
constexpr TimeDelta Milliseconds(T n) {
  return TimeDelta::FromInternalValue(
      ClampMul(static_cast<int64_t>(n), Time::kMicrosecondsPerMillisecond));
}
template <typename T, time_internal::EnableIfFloat<T> = 0>
constexpr TimeDelta Seconds(T n) {
  return TimeDelta::FromInternalValue(
      saturated_cast<int64_t>(n * Time::kMicrosecondsPerSecond));
}
template <typename T, time_internal::EnableIfFloat<T> = 0>
constexpr TimeDelta Milliseconds(T n) {
  return TimeDelta::FromInternalValue(
      saturated_cast<int64_t>(n * Time::kMicrosecondsPerMillisecond));
}

} // namespace base
