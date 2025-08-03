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

#ifndef BERBERIS_GUEST_ABI_GUEST_PARAMS_ARCH_H_
#define BERBERIS_GUEST_ABI_GUEST_PARAMS_ARCH_H_

#include <array>
#include <cstdint>
#include <tuple>
#include <type_traits>

#include "berberis/base/checks.h"
#include "berberis/base/dependent_false.h"
#include "berberis/calling_conventions/calling_conventions_riscv64.h"
#include "berberis/guest_abi/guest_abi.h"
#include "berberis/guest_state/guest_addr.h"
#include "berberis/guest_state/guest_state.h"

namespace berberis {

class GuestVAListParams;

class GuestParamsAndReturnHelper : protected GuestAbi {
 protected:
  template <typename T, GuestAbi::CallingConventionsVariant kCallingConventionsVariant>
  constexpr static auto* ParamLocationAddress(uint64_t* x,
                                              uint64_t* f,
                                              uint8_t* s,
                                              riscv64::ArgLocation loc) {
    using ArgumentInfo = GuestArgumentInfo<T, kCallingConventionsVariant>;

    void* address = nullptr;
    if (loc.kind == riscv64::kArgLocationStack) {
      address = s + loc.offset;
    } else if (loc.kind == riscv64::kArgLocationInt) {
      address = x + loc.offset + A0;
    } else if (loc.kind == riscv64::kArgLocationFp) {
      address = f + loc.offset + FA0;
    } else {
      FATAL("Unknown ArgumentKind");
    }

    static_assert(!std::is_same_v<T, void>);
    if constexpr (ArgumentInfo::kArgumentClass == ArgumentClass::kLargeStruct) {
      return *reinterpret_cast<typename ArgumentInfo::GuestType**>(address);
    } else {
      return reinterpret_cast<typename ArgumentInfo::GuestType*>(address);
    }
  }
};

template <typename, GuestAbi::CallingConventionsVariant = GuestAbi::kDefaultAbi>
class GuestParamsAndReturn;

// GuestParamsAndReturn is typesafe wrapper around ThreadState.
// Usage looks like this:
//   GuestParamsAndReturn<double(int, double, int, double)> params(state);
//   int x = params.Params<0>();
//   float y = params.Params<1>();
//   params.Return() = x * y;

template <typename ReturnType,
          typename... ParamType,
          bool kNoexcept,
          GuestAbi::CallingConventionsVariant kCallingConventionsVariant>
class GuestParamsAndReturn<ReturnType(ParamType...) noexcept(kNoexcept), kCallingConventionsVariant>
    : GuestParamsAndReturnHelper {
 public:
  GuestParamsAndReturn(ThreadState* state)
      : x_(state->cpu.x), f_(state->cpu.f), s_(ToHostAddr<uint8_t>(GetXReg<SP>(state->cpu))) {}

  template <size_t index>
  [[nodiscard]] auto* Params() const {
    static_assert(index < sizeof...(ParamType));
    return ParamLocationAddress<std::tuple_element_t<index, std::tuple<ParamType...>>,
                                kCallingConventionsVariant>(x_, f_, s_, kParamsLocations[index]);
  }

  [[nodiscard]] auto* Return() const {
    return ParamLocationAddress<ReturnType, kCallingConventionsVariant>(
        x_, f_, s_, kReturnLocation);
  }

 private:
  friend class GuestVAListParams;
  friend class GuestParamsAndReturn<ReturnType(ParamType..., ...) noexcept(kNoexcept),
                                    kCallingConventionsVariant>;

  constexpr static const std::tuple<riscv64::CallingConventions,
                                    riscv64::ArgLocation,
                                    std::array<riscv64::ArgLocation, sizeof...(ParamType)>>
  ParamsInfoHelper() {
    struct {
      const ArgumentClass kArgumentClass;
      const unsigned kSize;
      const unsigned kAlignment;
    } const kArgumentsInfo[] = {
        {.kArgumentClass = GuestArgumentInfo<ParamType, kCallingConventionsVariant>::kArgumentClass,
         .kSize = GuestArgumentInfo<ParamType, kCallingConventionsVariant>::kSize,
         .kAlignment = GuestArgumentInfo<ParamType, kCallingConventionsVariant>::kAlignment}...};

    riscv64::CallingConventions conv;
    // The return location must be allocated before any parameters to ensure that the implicit a0
    // parameter for functions with large structure return types is reserved.
    riscv64::ArgLocation return_loc = ReturnInfoHelper(conv);
    std::array<riscv64::ArgLocation, sizeof...(ParamType)> param_locs{};
    for (const auto& kArgInfo : kArgumentsInfo) {
      if (kArgInfo.kArgumentClass == ArgumentClass::kInteger ||
          kArgInfo.kArgumentClass == ArgumentClass::kLargeStruct) {
        param_locs[&kArgInfo - kArgumentsInfo] =
            conv.GetNextIntArgLoc(kArgInfo.kSize, kArgInfo.kAlignment);
      } else if (kArgInfo.kArgumentClass == ArgumentClass::kFp) {
        param_locs[&kArgInfo - kArgumentsInfo] =
            conv.GetNextFpArgLoc(kArgInfo.kSize, kArgInfo.kAlignment);
      } else {
        FATAL("Unsupported ArgumentClass");
      }
    }

    return {conv, return_loc, param_locs};
  }

  constexpr static riscv64::ArgLocation ReturnInfoHelper() {
    riscv64::CallingConventions conv;
    return ReturnInfoHelper(conv);
  }

  constexpr static riscv64::ArgLocation ReturnInfoHelper(riscv64::CallingConventions& conv) {
    using ReturnInfo = GuestArgumentInfo<ReturnType, kCallingConventionsVariant>;
    if constexpr (std::is_same_v<ReturnType, void>) {
      return {riscv64::kArgLocationNone, 0};
    } else if constexpr (ReturnInfo::kArgumentClass == ArgumentClass::kInteger) {
      return conv.GetIntResLoc(ReturnInfo::kSize);
    } else if constexpr (ReturnInfo::kArgumentClass == ArgumentClass::kFp) {
      return conv.GetFpResLoc(ReturnInfo::kSize);
    } else if constexpr (ReturnInfo::kArgumentClass == ArgumentClass::kLargeStruct) {
      // The caller allocates memory for large structure return values and passes the address in a0
      // as an implicit parameter.  If the return type is a large structure, we must reserve a0 for
      // this implicit parameter.
      return conv.GetNextIntArgLoc(ReturnInfo::kSize, ReturnInfo::kAlignment);
    } else {
      static_assert(kDependentTypeFalse<ReturnType>, "Unsupported ArgumentClass");
    }
  }

  constexpr static riscv64::CallingConventions kVaStartBase = std::get<0>(ParamsInfoHelper());

  constexpr static riscv64::ArgLocation kReturnLocation = ReturnInfoHelper();

  constexpr static std::array<riscv64::ArgLocation, sizeof...(ParamType)> kParamsLocations =
      std::get<2>(ParamsInfoHelper());

  uint64_t* x_;
  uint64_t* f_;
  uint8_t* s_;
};

// Partial specialization for GuestParamsAndReturn<FunctionToPointer> - it acts the same as the
// corresponding GuestParamsAndReturn<Function>.
template <typename ReturnType,
          typename... ParamType,
          bool kNoexcept,
          GuestAbi::CallingConventionsVariant kCallingConventionsVariant>
class GuestParamsAndReturn<ReturnType (*)(ParamType...) noexcept(kNoexcept),
                           kCallingConventionsVariant>
    : public GuestParamsAndReturn<ReturnType(ParamType...) noexcept(kNoexcept),
                                  kCallingConventionsVariant> {
 public:
  GuestParamsAndReturn(ThreadState* state)
      : GuestParamsAndReturn<ReturnType(ParamType...) noexcept(kNoexcept),
                             kCallingConventionsVariant>(state) {}
};

// Partial specialization for GuestParamsAndReturn<Function> for functions with variadic arguments -
// it acts the same as the corresponding one without it.
template <typename ReturnType,
          typename... ParamType,
          bool kNoexcept,
          GuestAbi::CallingConventionsVariant kCallingConventionsVariant>
class GuestParamsAndReturn<ReturnType(ParamType..., ...) noexcept(kNoexcept),
                           kCallingConventionsVariant>
    : public GuestParamsAndReturn<ReturnType(ParamType...) noexcept(kNoexcept),
                                  kCallingConventionsVariant> {
 public:
  GuestParamsAndReturn(ThreadState* state)
      : GuestParamsAndReturn<ReturnType(ParamType...) noexcept(kNoexcept),
                             kCallingConventionsVariant>(state) {}

 private:
  friend class GuestVAListParams;

  constexpr static riscv64::CallingConventions kVaStartBase =
      std::get<0>(GuestParamsAndReturn<ReturnType(ParamType...) noexcept(kNoexcept),
                                       kCallingConventionsVariant>::ParamsInfoHelper());
};

// Partial specialization for GuestParamsAndReturn<FunctionToPointer> for functions with variadic
// arguments - it acts the same as the corresponding one without it.
template <typename ReturnType,
          typename... ParamType,
          bool kNoexcept,
          GuestAbi::CallingConventionsVariant kCallingConventionsVariant>
class GuestParamsAndReturn<ReturnType (*)(ParamType..., ...) noexcept(kNoexcept),
                           kCallingConventionsVariant>
    : public GuestParamsAndReturn<ReturnType(ParamType..., ...) noexcept(kNoexcept),
                                  kCallingConventionsVariant> {
 public:
  GuestParamsAndReturn(ThreadState* state)
      : GuestParamsAndReturn<ReturnType(ParamType..., ...) noexcept(kNoexcept),
                             kCallingConventionsVariant>(state) {}
};

template <typename FunctionType,
          GuestAbi::CallingConventionsVariant kCallingConventionsVariant = GuestAbi::kDefaultAbi>
class GuestParamsValues;

class GuestVAListParams : GuestParamsAndReturnHelper {
 public:
  template <typename Func,
            GuestAbi::CallingConventionsVariant kCallingConventionsVariant = GuestAbi::kDefaultAbi>
  GuestVAListParams(const GuestParamsValues<Func, kCallingConventionsVariant>&& named_parameters)
      : calling_conventions_(named_parameters.kVaStartBase),
        x_(named_parameters.x_),
        s_(named_parameters.s_) {}

  // Initialize from a va_list, which is a pointer to parameters which are located as if they were
  // passed on the stack.
  GuestVAListParams(GuestAddr va_ptr)
      : calling_conventions_(riscv64::CallingConventions::kStackOnly),
        s_(ToHostAddr<uint8_t>(va_ptr)) {}

  template <typename T,
            GuestAbi::CallingConventionsVariant kCallingConventionsVariant = GuestAbi::kDefaultAbi>
  typename GuestArgumentInfo<T, kCallingConventionsVariant>::GuestType& GetParam() {
    using ArgumentInfo = GuestArgumentInfo<T, kCallingConventionsVariant>;
    // All argument types (integer, floating point, and aggregate) are passed in integer registers
    // and/or on the stack regardless of the calling convention.
    if constexpr (ArgumentInfo::kArgumentClass == ArgumentClass::kInteger ||
                  ArgumentInfo::kArgumentClass == ArgumentClass::kFp ||
                  ArgumentInfo::kArgumentClass == ArgumentClass::kLargeStruct) {
      return *ParamLocationAddress<T, kCallingConventionsVariant>(
          x_,
          nullptr,
          s_,
          calling_conventions_.GetNextIntArgLoc(ArgumentInfo::kSize, ArgumentInfo::kAlignment));
    } else {
      static_assert(kDependentTypeFalse<T>, "Unsupported ArgumentClass");
    }
  }

  template <typename T>
  T* GetPointerParam() {
    return ToHostAddr<T>(GetParam<GuestAddr>());
  }

 private:
  riscv64::CallingConventions calling_conventions_;

  uint64_t* x_ = nullptr;
  uint8_t* s_ = nullptr;
};

}  // namespace berberis

#endif  // BERBERIS_GUEST_ABI_GUEST_PARAMS_ARCH_H_
