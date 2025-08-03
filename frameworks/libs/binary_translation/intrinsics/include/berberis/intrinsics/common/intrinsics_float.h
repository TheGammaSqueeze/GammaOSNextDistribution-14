/*
 * Copyright (C) 2014 The Android Open Source Project
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

#ifndef BERBERIS_INTRINSICS_COMMON_INTRINSICS_FLOAT_H_
#define BERBERIS_INTRINSICS_COMMON_INTRINSICS_FLOAT_H_

// We couldn't safely pass arguments using "raw" float and double on X86 because of peculiarities
// of psABI (sometimes floating point registers are used by guest programs to pass integer value and
// certain integers when converted to fp80 type and back become corrupted).
//
// To make sure that we wouldn't use it to return value by accident we would wrap them and use class
// which makes such mistakes unlikely on x86.
//
// It's safe to pass "raw" values as float and double on modern ABI (RISC-V UABI, x86-64 psABI, etc).
//
// NOTE: That type must be layout-compatible with underlying type thus it must ONLY have one field
// value_ inside.
//
// NOTE: It's perfectly safe to do bit_cast<uint32_t>(Float32) or bit_cast<Float64>(uint64_t).
// Yet it's NOT safe to do bit_cast<float>(Float32) or bit_cast<Float64>(double). This is because
// bit_cast itself is just a regular function and is affected by that psABI issue as well.
//
// If you need to convert between float/double and Float32/Float64 then you have to use memcpy
// and couldn't use any helper function which would receive or return raw float or double value.

#include <stdint.h>

#include <limits>

#include "berberis/base/bit_util.h"

namespace berberis {

namespace intrinsics {

enum class FPInfo { kNaN, kInfinite, kNormal, kSubnormal, kZero };

template <typename BaseType>
class WrappedFloatType {
 public:
  constexpr WrappedFloatType() = default;
  explicit constexpr WrappedFloatType(BaseType value) : value_(value) {}
  constexpr WrappedFloatType(const WrappedFloatType& other) = default;
  constexpr WrappedFloatType(WrappedFloatType&& other) noexcept = default;
  WrappedFloatType& operator=(const WrappedFloatType& other) = default;
  WrappedFloatType& operator=(WrappedFloatType&& other) noexcept = default;
  ~WrappedFloatType() = default;
  template <typename IntType,
            typename = std::enable_if_t<std::is_integral_v<BaseType> &&
                                        sizeof(BaseType) == sizeof(IntType)>>
  [[nodiscard]] constexpr operator Raw<IntType>() const {
    // Can't use bit_cast here because of IA32 ABI!
    Raw<IntType> result;
    memcpy(&result, &value_, sizeof(BaseType));
    return result;
  }
  explicit constexpr operator int16_t() const { return value_; }
  explicit constexpr operator uint16_t() const { return value_; }
  explicit constexpr operator int32_t() const { return value_; }
  explicit constexpr operator uint32_t() const { return value_; }
  explicit constexpr operator int64_t() const { return value_; }
  explicit constexpr operator uint64_t() const { return value_; }
  explicit constexpr operator WrappedFloatType<float>() const {
    return WrappedFloatType<float>(value_);
  }
  explicit constexpr operator WrappedFloatType<double>() const {
    return WrappedFloatType<double>(value_);
  }
#if defined(__i386__) || defined(__x86_64__)
  explicit constexpr operator long double() const { return value_; }
#endif
  // Note: we don't provide unary operator-.  That's done on purpose: with floats -x and 0.-x
  // produce different results which could be surprising.  Use fneg instead of unary operator-.
  friend WrappedFloatType operator+(const WrappedFloatType& v1, const WrappedFloatType& v2);
  friend WrappedFloatType& operator+=(WrappedFloatType& v1, const WrappedFloatType& v2);
  friend WrappedFloatType operator-(const WrappedFloatType& v1, const WrappedFloatType& v2);
  friend WrappedFloatType& operator-=(WrappedFloatType& v1, const WrappedFloatType& v2);
  friend WrappedFloatType operator*(const WrappedFloatType& v1, const WrappedFloatType& v2);
  friend WrappedFloatType& operator*=(WrappedFloatType& v1, const WrappedFloatType& v2);
  friend WrappedFloatType operator/(const WrappedFloatType& v1, const WrappedFloatType& v2);
  friend WrappedFloatType& operator/=(WrappedFloatType& v1, const WrappedFloatType& v2);
  friend bool operator==(const WrappedFloatType& v1, const WrappedFloatType& v2);
  friend bool operator!=(const WrappedFloatType& v1, const WrappedFloatType& v2);
  friend bool operator<(const WrappedFloatType& v1, const WrappedFloatType& v2);
  friend bool operator<=(const WrappedFloatType& v1, const WrappedFloatType& v2);
  friend bool operator>(const WrappedFloatType& v1, const WrappedFloatType& v2);
  friend bool operator>=(const WrappedFloatType& v1, const WrappedFloatType& v2);
  friend inline WrappedFloatType CopySignBit(const WrappedFloatType& v1,
                                             const WrappedFloatType& v2);
  friend inline WrappedFloatType Absolute(const WrappedFloatType& v);
  friend inline WrappedFloatType Negative(const WrappedFloatType& v);
  friend inline FPInfo FPClassify(const WrappedFloatType& v);
  friend inline WrappedFloatType FPRound(const WrappedFloatType& value, uint32_t round_control);
  friend inline int IsNan(const WrappedFloatType& v);
  friend inline int SignBit(const WrappedFloatType& v);
  friend inline WrappedFloatType Sqrt(const WrappedFloatType& v);
  friend inline WrappedFloatType MulAdd(const WrappedFloatType& v1,
                                        const WrappedFloatType& v2,
                                        const WrappedFloatType& v3);

  friend inline WrappedFloatType Max(WrappedFloatType op1, WrappedFloatType op2) {
    if (FPClassify(op1) == FPInfo::kZero && FPClassify(op2) == FPInfo::kZero &&
        SignBit(op1) != SignBit(op2)) {
      return WrappedFloatType(BaseType(+0.f));
    }
    // If either argument is NaN - return default NaN (fmax() may return other).
    if (IsNan(op1) || IsNan(op2)) {
      return std::numeric_limits<WrappedFloatType>::quiet_NaN();
    }
    // Note: fmax is not needed here - it differs from std::max based on operator< only if NANs are
    // involved - and does wrong thing there.  We have no NANs at this point thus could use std::max
    return std::max(op1, op2);
  }

  friend inline WrappedFloatType Min(WrappedFloatType op1, WrappedFloatType op2) {
    if (FPClassify(op1) == FPInfo::kZero && FPClassify(op2) == FPInfo::kZero &&
        SignBit(op1) != SignBit(op2)) {
      return WrappedFloatType(BaseType(-0.f));
    }
    // If either argument is NaN - return default NaN (fmin() may return other).
    if (IsNan(op1) || IsNan(op2)) {
      return std::numeric_limits<WrappedFloatType>::quiet_NaN();
    }
    // Note: fmin is not needed here - it differs from std::min based on operator< only if NANs are
    // involved - and does wrong thing there.  We have no NANs at this point thus could use std::min
    return std::min(op1, op2);
  }

 private:
  static_assert(!std::numeric_limits<BaseType>::is_exact,
                "WrappedFloatType should only be used with float types!");
  BaseType value_;
};

// Note: Float8 is uninhabited and variables of such type couldn't be created. Nonetheless it's
// useful for implementation of certain instructions. For example vfwcvt.f.x.v RISC-V instruction
// converts from Int8 (single-width int) to Float16 (double-width float) if titular element size if
// Float8. Because such instruction never actually accesses elements of Float8 type it's perfectly
// valid and allowed (if CPU supports Float16).
class Float8PhonyType;  // This class doesn't exist but we may use it in template arguments.
using Float8 = WrappedFloatType<Float8PhonyType>;  // Ditto.
using Float16 = WrappedFloatType<_Float16>;
using Float32 = WrappedFloatType<float>;
using Float64 = WrappedFloatType<double>;

}  // namespace intrinsics

}  // namespace berberis

namespace std {

template <typename BaseType>
class numeric_limits<berberis::intrinsics::WrappedFloatType<BaseType>> {
 public:
  static constexpr bool is_specialized = true;
  static constexpr bool is_signed = true;
  static constexpr bool is_integer = false;
  static constexpr bool is_exact = false;
  static constexpr bool has_infinity = true;
  static constexpr bool has_quiet_NaN = std::numeric_limits<BaseType>::has_quiet_NaN;
  static constexpr bool has_signaling_NaN = std::numeric_limits<BaseType>::has_signaling_NaN;
  static constexpr std::float_denorm_style has_denorm = std::numeric_limits<BaseType>::has_denorm;
  static constexpr bool has_denorm_loss = std::numeric_limits<BaseType>::has_denorm_loss;
  static constexpr std::float_round_style round_style = std::numeric_limits<BaseType>::round_style;
  static constexpr bool is_iec559 = std::numeric_limits<BaseType>::is_iec559;
  static constexpr bool is_bounded = true;
  static constexpr bool is_modulo = false;
  static constexpr int digits = std::numeric_limits<BaseType>::digits;
  static constexpr int digits10 = std::numeric_limits<BaseType>::digits10;
  static constexpr int max_digits10 = std::numeric_limits<BaseType>::max_digits10;
  static constexpr int radix = std::numeric_limits<BaseType>::radix;
  static constexpr int min_exponent = std::numeric_limits<BaseType>::min_exponent;
  static constexpr int min_exponent10 = std::numeric_limits<BaseType>::min_exponent10;
  static constexpr int max_exponent = std::numeric_limits<BaseType>::max_exponent;
  static constexpr int max_exponent10 = std::numeric_limits<BaseType>::max_exponent10;
  static constexpr bool traps = std::numeric_limits<BaseType>::traps;
  static constexpr bool tinyness_before = std::numeric_limits<BaseType>::tinyness_before;
  static constexpr berberis::intrinsics::WrappedFloatType<BaseType> min() {
    return berberis::intrinsics::WrappedFloatType<BaseType>(std::numeric_limits<BaseType>::min());
  }
  static constexpr berberis::intrinsics::WrappedFloatType<BaseType> lowest() {
    return berberis::intrinsics::WrappedFloatType<BaseType>(
        std::numeric_limits<BaseType>::lowest());
  }
  static constexpr berberis::intrinsics::WrappedFloatType<BaseType> max() {
    return berberis::intrinsics::WrappedFloatType<BaseType>(std::numeric_limits<BaseType>::max());
  }
  static constexpr berberis::intrinsics::WrappedFloatType<BaseType> epsilon() {
    return berberis::intrinsics::WrappedFloatType<BaseType>(
        std::numeric_limits<BaseType>::epsilon());
  }
  static constexpr berberis::intrinsics::WrappedFloatType<BaseType> round_error() {
    return berberis::intrinsics::WrappedFloatType<BaseType>(
        std::numeric_limits<BaseType>::round_error());
  }
  static constexpr berberis::intrinsics::WrappedFloatType<BaseType> infinity() {
    return berberis::intrinsics::WrappedFloatType<BaseType>(
        std::numeric_limits<BaseType>::infinity());
  }
  static constexpr berberis::intrinsics::WrappedFloatType<BaseType> quiet_NaN() {
    return berberis::intrinsics::WrappedFloatType<BaseType>(
        std::numeric_limits<BaseType>::quiet_NaN());
  }
  static constexpr berberis::intrinsics::WrappedFloatType<BaseType> signaling_NaN() {
    return berberis::intrinsics::WrappedFloatType<BaseType>(
        std::numeric_limits<BaseType>::signaling_NaN());
  }
  static constexpr berberis::intrinsics::WrappedFloatType<BaseType> denorm_min() {
    return berberis::intrinsics::WrappedFloatType<BaseType>(
        std::numeric_limits<BaseType>::denorm_min());
  }
};

}  // namespace std

#endif  // BERBERIS_INTRINSICS_COMMON_INTRINSICS_FLOAT_H_
