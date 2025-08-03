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

#ifndef BERBERIS_GUEST_ABI_GUEST_ARGUMENTS_ARCH_H_
#define BERBERIS_GUEST_ABI_GUEST_ARGUMENTS_ARCH_H_

#include <array>
#include <tuple>

#include "berberis/base/dependent_false.h"
#include "berberis/calling_conventions/calling_conventions_riscv64.h"
#include "berberis/guest_abi/guest_abi_arch.h"

namespace berberis {

struct GuestArgumentBuffer {
  int argc;        // in integer registers.
  int resc;        // in integer registers.
  int fp_argc;     // in float registers.
  int fp_resc;     // in float registers.
  int stack_argc;  // in bytes.

  uint64_t argv[8];
  uint64_t fp_argv[8];
  uint64_t stack_argv[1];  // VLA.
};

template <typename, GuestAbi::CallingConventionsVariant = GuestAbi::kDefaultAbi>
class GuestArgumentsAndResult;

// GuestArguments is a typesafe wrapper around GuestArgumentBuffer.
// Usage looks like this:
//   GuestArguments<double(int, double, int, double)> args(*buf);
//   int x = args.Arguments<0>();
//   float y = args.Arguments<1>();
//   args.Result() = x * y;

template <typename ResultType,
          typename... ArgumentType,
          bool kNoexcept,
          GuestAbi::CallingConventionsVariant kCallingConventionsVariant>
class GuestArgumentsAndResult<ResultType(ArgumentType...) noexcept(kNoexcept),
                              kCallingConventionsVariant> : GuestAbi {
 public:
  GuestArgumentsAndResult(GuestArgumentBuffer* buffer) : buffer_(buffer) {}

  template <size_t index>
  auto& GuestArgument() const {
    static_assert(index < sizeof...(ArgumentType));
    using Type = std::tuple_element_t<index, std::tuple<ArgumentType...>>;
    using ArgumentInfo = GuestArgumentInfo<Type, kCallingConventionsVariant>;
    using CastType = typename ArgumentInfo::GuestType;
    return Reference<ArgumentInfo, CastType>(kArgumentsLocations[index]);
  }

  template <size_t index>
  auto& HostArgument() const {
    static_assert(index < sizeof...(ArgumentType));
    using Type = std::tuple_element_t<index, std::tuple<ArgumentType...>>;
    using ArgumentInfo = GuestArgumentInfo<Type, kCallingConventionsVariant>;
    using CastType = typename ArgumentInfo::HostType;
    return Reference<ArgumentInfo, CastType>(kArgumentsLocations[index]);
  }

  auto& GuestResult() const {
    static_assert(!std::is_same_v<ResultType, void>);
    using ArgumentInfo = GuestArgumentInfo<ResultType, kCallingConventionsVariant>;
    using CastType = typename ArgumentInfo::GuestType;
    return Reference<ArgumentInfo, CastType>(kResultLocation);
  }

  auto& HostResult() const {
    static_assert(!std::is_same_v<ResultType, void>);
    using ArgumentInfo = GuestArgumentInfo<ResultType, kCallingConventionsVariant>;
    using CastType = typename ArgumentInfo::HostType;
    return Reference<ArgumentInfo, CastType>(kResultLocation);
  }

 private:
  template <typename ArgumentInfo, typename CastType>
  auto& Reference(riscv64::ArgLocation loc) const {
    if constexpr (ArgumentInfo::kArgumentClass == ArgumentClass::kLargeStruct) {
      return **reinterpret_cast<CastType**>(ArgLocationAddress(loc));
    } else {
      return *reinterpret_cast<CastType*>(ArgLocationAddress(loc));
    }
  }

  constexpr static const std::tuple<riscv64::ArgLocation,
                                    std::array<riscv64::ArgLocation, sizeof...(ArgumentType)>>
  ArgumentsInfoHelper() {
    struct {
      const ArgumentClass kArgumentClass;
      const unsigned kSize;
      const unsigned kAlignment;
    } const kArgumentsInfo[] = {
        {.kArgumentClass =
             GuestArgumentInfo<ArgumentType, kCallingConventionsVariant>::kArgumentClass,
         .kSize = GuestArgumentInfo<ArgumentType, kCallingConventionsVariant>::kSize,
         .kAlignment = GuestArgumentInfo<ArgumentType, kCallingConventionsVariant>::kAlignment}...};

    riscv64::CallingConventions conv;
    // The result location must be allocated before any arguments to ensure that the implicit a0
    // argument for functions with large structure return types is reserved.
    riscv64::ArgLocation result_loc = ResultInfoHelper(conv);
    std::array<riscv64::ArgLocation, sizeof...(ArgumentType)> arg_locs{};
    for (const auto& kArgInfo : kArgumentsInfo) {
      if (kArgInfo.kArgumentClass == ArgumentClass::kInteger ||
          kArgInfo.kArgumentClass == ArgumentClass::kLargeStruct) {
        arg_locs[&kArgInfo - kArgumentsInfo] =
            conv.GetNextIntArgLoc(kArgInfo.kSize, kArgInfo.kAlignment);
      } else if (kArgInfo.kArgumentClass == ArgumentClass::kFp) {
        arg_locs[&kArgInfo - kArgumentsInfo] =
            conv.GetNextFpArgLoc(kArgInfo.kSize, kArgInfo.kAlignment);
      } else {
        LOG_ALWAYS_FATAL("Unsupported ArgumentClass");
      }
    }

    return {result_loc, arg_locs};
  }

  constexpr static riscv64::ArgLocation ResultInfoHelper(riscv64::CallingConventions& conv) {
    using ResultInfo = GuestArgumentInfo<ResultType, kCallingConventionsVariant>;
    if constexpr (std::is_same_v<ResultType, void>) {
      return {riscv64::kArgLocationNone, 0};
    } else if constexpr (ResultInfo::kArgumentClass == ArgumentClass::kInteger) {
      return conv.GetIntResLoc(ResultInfo::kSize);
    } else if constexpr (ResultInfo::kArgumentClass == ArgumentClass::kFp) {
      return conv.GetFpResLoc(ResultInfo::kSize);
    } else if constexpr (ResultInfo::kArgumentClass == ArgumentClass::kLargeStruct) {
      // The caller allocates memory for large structure return values and passes the address in a0
      // as an implicit parameter.  If the return type is a large structure, we must reserve a0 for
      // this implicit parameter.
      return conv.GetNextIntArgLoc(ResultInfo::kSize, ResultInfo::kAlignment);
    } else {
      static_assert(kDependentTypeFalse<ResultType>, "Unsupported ArgumentClass");
    }
  }

  constexpr void* ArgLocationAddress(riscv64::ArgLocation loc) const {
    if (loc.kind == riscv64::kArgLocationStack) {
      return reinterpret_cast<char*>(buffer_->stack_argv) + loc.offset;
    } else if (loc.kind == riscv64::kArgLocationInt) {
      return buffer_->argv + loc.offset;
    } else if (loc.kind == riscv64::kArgLocationFp) {
      return buffer_->fp_argv + loc.offset;
    } else {
      CHECK(false);
    }
  }

  constexpr static riscv64::ArgLocation kResultLocation = std::get<0>(ArgumentsInfoHelper());

  constexpr static std::array<riscv64::ArgLocation, sizeof...(ArgumentType)> kArgumentsLocations =
      std::get<1>(ArgumentsInfoHelper());

  GuestArgumentBuffer* const buffer_;
};

// Partial specialization for GuestArgumentsAndResult<FunctionToPointer> - it acts the same as the
// corresponding GuestArgumentsAndResult<Function>.
template <typename ResultType,
          typename... ArgumentType,
          bool kNoexcept,
          GuestAbi::CallingConventionsVariant kCallingConventionsVariant>
class GuestArgumentsAndResult<ResultType (*)(ArgumentType...) noexcept(kNoexcept),
                              kCallingConventionsVariant>
    : public GuestArgumentsAndResult<ResultType(ArgumentType...) noexcept(kNoexcept),
                                     kCallingConventionsVariant> {
 public:
  GuestArgumentsAndResult(GuestArgumentBuffer* buffer)
      : GuestArgumentsAndResult<ResultType(ArgumentType...) noexcept(kNoexcept),
                                kCallingConventionsVariant>(buffer) {}
};

}  // namespace berberis

#endif  // BERBERIS_GUEST_ABI_GUEST_ARGUMENTS_ARCH_H_
