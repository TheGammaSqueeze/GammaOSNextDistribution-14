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

#ifndef BERBERIS_GUEST_ABI_GUEST_ABI_ARCH_H_
#define BERBERIS_GUEST_ABI_GUEST_ABI_ARCH_H_

#include <cstdint>
#include <type_traits>

#include "berberis/base/bit_util.h"
#include "berberis/calling_conventions/calling_conventions_riscv64.h"  // IWYU pragma: export.
#include "berberis/guest_abi/guest_type.h"

namespace berberis {

class GuestAbi {
 public:
  enum CallingConventionsVariant {
    kLp64,   // Soft float.
    kLp64d,  // Hardware float and double.
    kDefaultAbi = kLp64
  };

  template <typename, CallingConventionsVariant = kDefaultAbi, typename = void>
  class GuestArgument;

  template <typename IntegerType, CallingConventionsVariant kCallingConventionsVariant>
  class alignas(sizeof(uint64_t)) GuestArgument<IntegerType,
                                                kCallingConventionsVariant,
                                                std::enable_if_t<std::is_integral_v<IntegerType>>> {
   public:
    using Type = IntegerType;
    GuestArgument(const IntegerType& value) : value_(Box(value)) {}
    GuestArgument(IntegerType&& value) : value_(Box(value)) {}
    GuestArgument() = default;
    GuestArgument(const GuestArgument&) = default;
    GuestArgument(GuestArgument&&) = default;
    GuestArgument& operator=(const GuestArgument&) = default;
    GuestArgument& operator=(GuestArgument&&) = default;
    ~GuestArgument() = default;
    operator IntegerType() const { return Unbox(value_); }
#define ARITHMETIC_ASSIGNMENT_OPERATOR(x) x## =
#define DEFINE_ARITHMETIC_ASSIGNMENT_OPERATOR(x)                                         \
  GuestArgument& operator ARITHMETIC_ASSIGNMENT_OPERATOR(x)(const GuestArgument& data) { \
    value_ = Box(Unbox(value_) x Unbox(data.value_));                                    \
    return *this;                                                                        \
  }                                                                                      \
  GuestArgument& operator ARITHMETIC_ASSIGNMENT_OPERATOR(x)(GuestArgument&& data) {      \
    value_ = Box(Unbox(value_) x Unbox(data.value_));                                    \
    return *this;                                                                        \
  }
    DEFINE_ARITHMETIC_ASSIGNMENT_OPERATOR(+)
    DEFINE_ARITHMETIC_ASSIGNMENT_OPERATOR(-)
    DEFINE_ARITHMETIC_ASSIGNMENT_OPERATOR(*)
    DEFINE_ARITHMETIC_ASSIGNMENT_OPERATOR(/)
    DEFINE_ARITHMETIC_ASSIGNMENT_OPERATOR(%)
    DEFINE_ARITHMETIC_ASSIGNMENT_OPERATOR(^)
    DEFINE_ARITHMETIC_ASSIGNMENT_OPERATOR(&)
    DEFINE_ARITHMETIC_ASSIGNMENT_OPERATOR(|)
#undef DEFINE_ARITHMETIC_ASSIGNMENT_OPERATOR
#undef ARITHMETIC_ASSIGNMENT_OPERATOR

   private:
    static constexpr uint64_t Box(IntegerType value) {
      if constexpr (sizeof(IntegerType) == sizeof(uint64_t)) {
        return static_cast<uint64_t>(value);
      } else if constexpr (std::is_signed_v<IntegerType>) {
        // Signed integers are simply sign-extended to 64 bits.
        return static_cast<uint64_t>(static_cast<int64_t>(value));
      } else {
        // Unsigned integers are first zero-extended to 32 bits then sign-extended to 64 bits.  This
        // generally results in the high bits being set to 0, but the high bits of 32-bit integers
        // with a 1 in the high bit will be set to 1.
        return static_cast<uint64_t>(
            static_cast<int64_t>(static_cast<int32_t>(static_cast<uint32_t>(value))));
      }
    }

    static constexpr IntegerType Unbox(uint64_t value) {
      // Integer narrowing correctly unboxes at any size.
      return static_cast<IntegerType>(value);
    }

    uint64_t value_ = 0;
  };

  template <typename EnumType, CallingConventionsVariant kCallingConventionsVariant>
  class alignas(sizeof(uint64_t)) GuestArgument<EnumType,
                                                kCallingConventionsVariant,
                                                std::enable_if_t<std::is_enum_v<EnumType>>> {
   public:
    using Type = EnumType;
    GuestArgument(const EnumType& value) : value_(Box(value)) {}
    GuestArgument(EnumType&& value) : value_(Box(value)) {}
    GuestArgument() = default;
    GuestArgument(const GuestArgument&) = default;
    GuestArgument(GuestArgument&&) = default;
    GuestArgument& operator=(const GuestArgument&) = default;
    GuestArgument& operator=(GuestArgument&&) = default;
    ~GuestArgument() = default;
    operator EnumType() const { return Unbox(value_); }

   private:
    using UnderlyingType = std::underlying_type_t<EnumType>;

    static constexpr UnderlyingType Box(EnumType value) {
      return static_cast<UnderlyingType>(value);
    }

    static constexpr EnumType Unbox(UnderlyingType value) { return static_cast<EnumType>(value); }

    GuestArgument<UnderlyingType, kCallingConventionsVariant> value_;
  };

  template <typename FloatingPointType>
  class alignas(sizeof(uint64_t))
      GuestArgument<FloatingPointType,
                    kLp64,
                    std::enable_if_t<std::is_floating_point_v<FloatingPointType>>> {
   public:
    using Type = FloatingPointType;
    GuestArgument(const FloatingPointType& value) : value_(Box(value)) {}
    GuestArgument(FloatingPointType&& value) : value_(Box(value)) {}
    GuestArgument() = default;
    GuestArgument(const GuestArgument&) = default;
    GuestArgument(GuestArgument&&) = default;
    GuestArgument& operator=(const GuestArgument&) = default;
    GuestArgument& operator=(GuestArgument&&) = default;
    ~GuestArgument() = default;
    operator FloatingPointType() const { return Unbox(value_); }

   private:
    // Floating-point arguments in integer registers do not require NaN boxing.  They are stored in
    // the lower bits of the 64-bit integer register with the high bits undefined.  Bit casting and
    // unsigned narrowing/widening conversions are sufficient.

    static constexpr uint64_t Box(FloatingPointType value) {
      if constexpr (sizeof(FloatingPointType) == sizeof(uint64_t)) {
        return bit_cast<uint64_t>(value);
      } else if constexpr (sizeof(FloatingPointType) == sizeof(uint32_t)) {
        return static_cast<uint64_t>(bit_cast<uint32_t>(value));
      } else {
        FATAL("Unsupported floating-point argument width");
      }
    }

    static constexpr FloatingPointType Unbox(uint64_t value) {
      if constexpr (sizeof(FloatingPointType) == sizeof(uint64_t)) {
        return bit_cast<FloatingPointType>(value);
      } else if constexpr (sizeof(FloatingPointType) == sizeof(uint32_t)) {
        return bit_cast<FloatingPointType>(static_cast<uint32_t>(value));
      } else {
        FATAL("Unsupported floating-point argument width");
      }
    }

    uint64_t value_ = 0;
  };

  template <typename FloatingPointType>
  class alignas(sizeof(uint64_t))
      GuestArgument<FloatingPointType,
                    kLp64d,
                    std::enable_if_t<std::is_floating_point_v<FloatingPointType>>> {
   public:
    using Type = FloatingPointType;
    GuestArgument(const FloatingPointType& value) : value_(Box(value)) {}
    GuestArgument(FloatingPointType&& value) : value_(Box(value)) {}
    GuestArgument() = default;
    GuestArgument(const GuestArgument&) = default;
    GuestArgument(GuestArgument&&) = default;
    GuestArgument& operator=(const GuestArgument&) = default;
    GuestArgument& operator=(GuestArgument&&) = default;
    ~GuestArgument() = default;
    operator FloatingPointType() const { return Unbox(value_); }

   private:
    // Floating-point arguments passed in floating-point registers require NaN boxing when they are
    // narrower than 64 bits.  The argument is stored in the lower bits of the 64-bit floating-point
    // register with the high bits set to 1.

    static constexpr uint64_t Box(FloatingPointType value) {
      if constexpr (sizeof(FloatingPointType) == sizeof(uint64_t)) {
        return bit_cast<uint64_t>(value);
      } else if constexpr (sizeof(FloatingPointType) == sizeof(uint32_t)) {
        return bit_cast<uint32_t>(value) | kNanBoxFloat32;
      } else {
        FATAL("Unsupported floating-point argument width");
      }
    }

    static constexpr FloatingPointType Unbox(uint64_t value) {
      if constexpr (sizeof(FloatingPointType) == sizeof(uint64_t)) {
        return bit_cast<Type>(value);
      } else if constexpr (sizeof(FloatingPointType) == sizeof(uint32_t)) {
        // Integer narrowing removes the NaN box.
        return bit_cast<Type>(static_cast<uint32_t>(value));
      } else {
        FATAL("Unsupported floating-point argument width");
      }
    }

    static constexpr uint64_t kNanBoxFloat32 = 0xffff'ffff'0000'0000ULL;

    uint64_t value_ = 0;
  };

 protected:
  enum class ArgumentClass { kInteger, kFp, kLargeStruct };

  template <typename Type, CallingConventionsVariant = kDefaultAbi, typename = void>
  struct GuestArgumentInfo;

  template <typename IntegerType, CallingConventionsVariant kCallingConventionsVariant>
  struct GuestArgumentInfo<IntegerType,
                           kCallingConventionsVariant,
                           std::enable_if_t<std::is_integral_v<IntegerType>>> {
    // Integers wider than 16 bytes are not supported.  They do not appear in the public Android
    // API.
    static_assert(sizeof(IntegerType) <= 16);
    constexpr static ArgumentClass kArgumentClass = ArgumentClass::kInteger;
    constexpr static unsigned kSize = sizeof(IntegerType);
    // Use sizeof, not alignof for kAlignment because all integer types are naturally aligned on
    // RISC-V, which is not guaranteed to be true for the host.
    constexpr static unsigned kAlignment = sizeof(IntegerType);
    using GuestType = GuestArgument<IntegerType, kCallingConventionsVariant>;
    using HostType = GuestType;
  };

  template <typename EnumType, CallingConventionsVariant kCallingConventionsVariant>
  struct GuestArgumentInfo<EnumType,
                           kCallingConventionsVariant,
                           std::enable_if_t<std::is_enum_v<EnumType>>>
      : GuestArgumentInfo<std::underlying_type_t<EnumType>> {
    using GuestType = GuestArgument<EnumType, kCallingConventionsVariant>;
    using HostType = GuestType;
  };

  template <typename PointeeType, CallingConventionsVariant kCallingConventionsVariant>
  struct GuestArgumentInfo<PointeeType*, kCallingConventionsVariant> {
    constexpr static ArgumentClass kArgumentClass = ArgumentClass::kInteger;
    constexpr static unsigned kSize = 8;
    constexpr static unsigned kAlignment = 8;
    using GuestType = GuestType<PointeeType*>;
    using HostType = PointeeType*;
  };

  template <typename ResultType,
            typename... ArgumentType,
            CallingConventionsVariant kCallingConventionsVariant>
  struct GuestArgumentInfo<ResultType (*)(ArgumentType...), kCallingConventionsVariant> {
    constexpr static ArgumentClass kArgumentClass = ArgumentClass::kInteger;
    constexpr static unsigned kSize = 8;
    constexpr static unsigned kAlignment = 8;
    using GuestType = GuestType<ResultType (*)(ArgumentType...)>;
    using HostType = ResultType (*)(ArgumentType...);
  };

  template <>
  struct GuestArgumentInfo<float, kLp64> {
    constexpr static ArgumentClass kArgumentClass = ArgumentClass::kInteger;
    constexpr static unsigned kSize = 4;
    constexpr static unsigned kAlignment = 4;
    using GuestType = GuestArgument<float, kLp64>;
    using HostType = GuestType;
  };

  template <>
  struct GuestArgumentInfo<float, kLp64d> {
    constexpr static ArgumentClass kArgumentClass = ArgumentClass::kFp;
    constexpr static unsigned kSize = 4;
    constexpr static unsigned kAlignment = 4;
    using GuestType = GuestArgument<float, kLp64d>;
    using HostType = GuestType;
  };

  template <>
  struct GuestArgumentInfo<double, kLp64> {
    constexpr static ArgumentClass kArgumentClass = ArgumentClass::kInteger;
    constexpr static unsigned kSize = 8;
    constexpr static unsigned kAlignment = 8;
    using GuestType = GuestArgument<double, kLp64>;
    using HostType = GuestType;
  };

  template <>
  struct GuestArgumentInfo<double, kLp64d> {
    constexpr static ArgumentClass kArgumentClass = ArgumentClass::kFp;
    constexpr static unsigned kSize = 8;
    constexpr static unsigned kAlignment = 8;
    using GuestType = GuestArgument<double, kLp64d>;
    using HostType = GuestType;
  };

  // Structures larger than 16 bytes are passed by reference.
  template <typename LargeStructType, CallingConventionsVariant kCallingConventionsVariant>
  struct GuestArgumentInfo<
      LargeStructType,
      kCallingConventionsVariant,
      std::enable_if_t<std::is_class_v<LargeStructType> && (sizeof(LargeStructType) > 16)>> {
    constexpr static ArgumentClass kArgumentClass = ArgumentClass::kLargeStruct;
    constexpr static unsigned kSize = 8;
    constexpr static unsigned kAlignment = 8;
    // Although the structure is passed by reference, keep the type of the underlying structure
    // here.  This is for passing the structure as a function argument.  It is easier to pass
    // constant structures using this declaration than adding const to the pointee type of a
    // pointer.
    using GuestType = GuestType<LargeStructType>;
    using HostType = LargeStructType;
  };
};

}  // namespace berberis

#endif  // BERBERIS_GUEST_ABI_GUEST_ABI_ARCH_H_
