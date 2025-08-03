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

#ifndef BERBERIS_GUEST_ABI_GUEST_PARAMS_H_
#define BERBERIS_GUEST_ABI_GUEST_PARAMS_H_

#include "berberis/guest_abi/guest_abi.h"
#include "berberis/guest_abi/guest_params_arch.h"

namespace berberis {

// Structured binding rules are designed for access to the insides of a structured type.
// Specifically:
//   auto [x,y] = structured_var;   // Makes copy of structured_var.
//   auto& [x,y] = structured_var;  // Doesn't make copy of a structures var.
// Note that x and y themselves are always references -- either to the copy or the original.
// Access modifiers are applied to the declaration of hidden, invisible variable.
//
// This works well when structured_var has some "insides" which may be copied.
//
// But in our case all these types are "lightweight adapters" used to parse ThreadState.
// There are no difference which copy you use -- all of the calls to Params() or Return() would
// return references to original ThreadState.
//
// However, it's allowed to return regular variable, not reference, from "get<>" function.
// In that case variables x and y (in the example above) would become rvalue references which
// would point to copy of the appropriate value.
//
// This allows us to make accessors XxxValues and XxxReferences which either allow one to access
// copies of Values stored in the ThreadState or access contents of ThreadState directly.

// GuestParamsValues is a syntax sugar for use with structured binding declaration.
// Usage looks like this:
//   auto [length, angle] = GuestParamsValues<double(int, double)>(state);
//   if (length > 100) {
//     length = 100;
//   }
//
// Note: variables are copies here not because "auto [x,y,z] =" construct is used but
// because GuestParamsValues always returns values. See above.

template <typename FunctionType, GuestAbi::CallingConventionsVariant kCallingConventionsVariant>
class GuestParamsValues : public GuestParamsAndReturn<FunctionType, kCallingConventionsVariant> {
 public:
  GuestParamsValues(ThreadState* state)
      : GuestParamsAndReturn<FunctionType, kCallingConventionsVariant>(state) {}

  // Adapter for structural bindings.
  template <std::size_t index>
  auto get() const {
    return *this->template Params<index>();
  }
};

// GuestReturnReference is a syntax sugar for use with structured binding declaration.
// Usage looks like this:
//   auto&& [ret] = GuestReturnReference<double(int, double)>(state);
//   ret = 5.0;
//
// Note: variable is a reference here not because "auto&& [x,y,z] =" construct is used but
// because GuestReturnReference always returns references. See above.

template <typename FunctionType,
          GuestAbi::CallingConventionsVariant kCallingConventionsVariant = GuestAbi::kDefaultAbi>
class GuestReturnReference : public GuestParamsAndReturn<FunctionType, kCallingConventionsVariant> {
 public:
  GuestReturnReference(ThreadState* state)
      : GuestParamsAndReturn<FunctionType, kCallingConventionsVariant>(state) {}

  // Adapter for structural bindings.  Note: we return normal references here, not rvalue references
  // since we are pointing to non-temporary object and don't want to see it moved.
  template <std::size_t index>
  auto& get() const {
    static_assert(index == 0);
    return *this->Return();
  }
};

}  // namespace berberis

namespace std {

// Partial specializations to support structural bindings for templates
// GuestParamsValues/GuestReturnReference.

// tuple_size for GuestParamsValues<Function>
template <typename ReturnType,
          typename... ParamType,
          bool kNoexcept,
          berberis::GuestAbi::CallingConventionsVariant kCallingConventionsVariant>
class tuple_size<berberis::GuestParamsValues<ReturnType(ParamType...) noexcept(kNoexcept),
                                             kCallingConventionsVariant>>
    : public std::integral_constant<std::size_t, sizeof...(ParamType)> {};

// tuple_size for GuestParamsValues<PointerToFunction>
template <typename ReturnType,
          typename... ParamType,
          bool kNoexcept,
          berberis::GuestAbi::CallingConventionsVariant kCallingConventionsVariant>
class tuple_size<berberis::GuestParamsValues<ReturnType (*)(ParamType...) noexcept(kNoexcept),
                                             kCallingConventionsVariant>>
    : public std::integral_constant<std::size_t, sizeof...(ParamType)> {};

template <typename ReturnType,
          typename... ParamType,
          bool kNoexcept,
          berberis::GuestAbi::CallingConventionsVariant kCallingConventionsVariant>
class tuple_size<berberis::GuestParamsValues<ReturnType(ParamType..., ...) noexcept(kNoexcept),
                                             kCallingConventionsVariant>>
    : public std::integral_constant<std::size_t, sizeof...(ParamType)> {};

template <typename ReturnType,
          typename... ParamType,
          bool kNoexcept,
          berberis::GuestAbi::CallingConventionsVariant kCallingConventionsVariant>
class tuple_size<berberis::GuestParamsValues<ReturnType (*)(ParamType..., ...) noexcept(kNoexcept),
                                             kCallingConventionsVariant>>
    : public std::integral_constant<std::size_t, sizeof...(ParamType)> {};

template <std::size_t index,
          typename FunctionType,
          berberis::GuestAbi::CallingConventionsVariant kCallingConventionsVariant>
class tuple_element<index, berberis::GuestParamsValues<FunctionType, kCallingConventionsVariant>> {
 public:
  using type = std::invoke_result_t<
      decltype(&berberis::GuestParamsValues<FunctionType,
                                            kCallingConventionsVariant>::template get<index>),
      berberis::GuestParamsValues<FunctionType, kCallingConventionsVariant>>;
};

// tuple_size for GuestReturnReference<Function>
template <typename ReturnType,
          typename... ParamType,
          bool kNoexcept,
          berberis::GuestAbi::CallingConventionsVariant kCallingConventionsVariant>
class tuple_size<berberis::GuestReturnReference<ReturnType(ParamType...) noexcept(kNoexcept),
                                                kCallingConventionsVariant>>
    : public std::integral_constant<std::size_t, std::is_same_v<ReturnType, void> ? 0 : 1> {};

// tuple_size for GuestReturnReference<PointerToFunction>
template <typename ReturnType,
          typename... ParamType,
          bool kNoexcept,
          berberis::GuestAbi::CallingConventionsVariant kCallingConventionsVariant>
class tuple_size<berberis::GuestReturnReference<ReturnType (*)(ParamType...) noexcept(kNoexcept),
                                                kCallingConventionsVariant>>
    : public std::integral_constant<std::size_t, std::is_same_v<ReturnType, void> ? 0 : 1> {};

template <typename ReturnType,
          typename... ParamType,
          bool kNoexcept,
          berberis::GuestAbi::CallingConventionsVariant kCallingConventionsVariant>
class tuple_size<berberis::GuestReturnReference<ReturnType(ParamType..., ...) noexcept(kNoexcept),
                                                kCallingConventionsVariant>>
    : public std::integral_constant<std::size_t, std::is_same_v<ReturnType, void> ? 0 : 1> {};

template <typename ReturnType,
          typename... ParamType,
          bool kNoexcept,
          berberis::GuestAbi::CallingConventionsVariant kCallingConventionsVariant>
class tuple_size<
    berberis::GuestReturnReference<ReturnType (*)(ParamType..., ...) noexcept(kNoexcept),
                                   kCallingConventionsVariant>>
    : public std::integral_constant<std::size_t, std::is_same_v<ReturnType, void> ? 0 : 1> {};

template <typename FunctionType,
          berberis::GuestAbi::CallingConventionsVariant kCallingConventionsVariant>
class tuple_element<0, berberis::GuestReturnReference<FunctionType, kCallingConventionsVariant>> {
 public:
  using type = std::invoke_result_t<
      decltype(&berberis::GuestReturnReference<FunctionType,
                                               kCallingConventionsVariant>::template get<0>),
      berberis::GuestReturnReference<FunctionType, kCallingConventionsVariant>>;
};

}  // namespace std

#endif  // BERBERIS_GUEST_ABI_GUEST_PARAMS_H_
