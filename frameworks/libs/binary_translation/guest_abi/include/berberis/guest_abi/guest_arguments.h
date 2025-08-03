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

#ifndef BERBERIS_GUEST_ABI_GUEST_ARGUMENTS_H_
#define BERBERIS_GUEST_ABI_GUEST_ARGUMENTS_H_

#include "berberis/guest_abi/guest_abi.h"  // IWYU pragma: export.
#include "berberis/guest_abi/guest_arguments_arch.h"

namespace berberis {

// Structured binding rules are designed for access to the insides of a structured type.
// Specifically:
//   auto [x,y] = structured_var;   // Makes copy of structured_var.
//   auto& [x,y] = structured_var;  // Doesn't make copy of a structures var.
// Note that x and y themselves are always references -- either to the copy or the original.
// Access modifiers are aplied to the declaration of hidden, invisible variable.
//
// This works well when structured_var has some "insides" which may be copied.
//
// But in our case all these types are "lightweight adapters" used to parse GuestArgumentBuffer.
// There are no difference which copy you use -- all of the calls to XxxArgument() or XxxResult()
// would return references to original GuestArgumentBuffer.
//
// However, it's allowed to return regular variable, not reference, from "get<>" function.
// In that case variables x and y (in the example above) would become rvalue references which
// would point to copy of the appropriate value.
//
// This allows us to make accessorts XxxValues and XxxReferences which either allow one to access
// copies of Values stored in the GuestArgumentBuffer or access contents of GuestArgumentBuffer
// directly.

// GuestArgumentsReferences is a syntax sugar for use with structured binding declaration.
// Usage looks like this:
//   auto&& [length, angle] = GuestArgumentsReferences<double(int, double)>(buf);
//   if (length > 100) {
//     length = 100;
//   }
//
// Note: variables are references here not because "auto&& [x,y,z] =" construct is used but
// because GuestArgumentsReferences always returns references. See above.

template <typename FunctionType,
          GuestAbi::CallingConventionsVariant kCallingConventionsVariant = GuestAbi::kDefaultAbi>
class GuestArgumentsReferences : GuestArgumentsAndResult<FunctionType, kCallingConventionsVariant> {
 public:
  GuestArgumentsReferences(GuestArgumentBuffer* buffer)
      : GuestArgumentsAndResult<FunctionType, kCallingConventionsVariant>(buffer) {}

  // Adapter for structural bindings.  Note: we return normal references here, not rvalue references
  // since we are pointing to non-temporary object and don't want to see it moved.
  template <std::size_t index>
  auto& get() const {
    return this->template GuestArgument<index>();
  }
};

// HostArgumentsValue is a syntax sugar for use with structured binding declaration.
// Usage looks like this:
//   auto [length, angle] = HostArgumentsValues<double(int, double)>(buf);
//   if (length > 100) {
//     length = 100;
//   }
//
// Note: variables are copies here not because "auto [x,y,z] =" construct is used but
// because HostArgumentsValues always returns values. See above.

template <typename FunctionType,
          GuestAbi::CallingConventionsVariant kCallingConventionsVariant = GuestAbi::kDefaultAbi>
class HostArgumentsValues : GuestArgumentsAndResult<FunctionType, kCallingConventionsVariant> {
 public:
  HostArgumentsValues(GuestArgumentBuffer* buffer)
      : GuestArgumentsAndResult<FunctionType, kCallingConventionsVariant>(buffer) {}

  // Adapter for structural bindings.
  template <std::size_t index>
  auto get() const {
    return this->template HostArgument<index>();
  }
};

// GuestResultValue is a syntax sugar for use with structured binding declaration.
// Usage looks like this:
//   auto [result] = GuestResultValue<double(int, double)>(buf);
//   if (result == 5.0) {
//     ...
//   }
//
// Note: the variable is a copy here not because "auto [x,y,z] =" construct is used but
// because GuestResultValue always returns values. See above.

template <typename FunctionType,
          GuestAbi::CallingConventionsVariant kCallingConventionsVariant = GuestAbi::kDefaultAbi>
class GuestResultValue : GuestArgumentsAndResult<FunctionType, kCallingConventionsVariant> {
 public:
  GuestResultValue(GuestArgumentBuffer* buffer)
      : GuestArgumentsAndResult<FunctionType, kCallingConventionsVariant>(buffer) {}

  // Adapter for structural bindings.
  template <std::size_t index>
  auto get() const {
    static_assert(index == 0);
    return this->GuestResult();
  }
};

// HostResultReference is a syntax sugar for use with structured binding declaration.
// Usage looks like this:
//   auto&& [result] = HostResultReference<double(int, double)>(buf);
//   result = 5.0;
//
// Note: variable is a reference here not because "auto&& [x,y,z] =" construct is used but
// because GuestArgumentsReferences always returns references. See above.

template <typename FunctionType,
          GuestAbi::CallingConventionsVariant kCallingConventionsVariant = GuestAbi::kDefaultAbi>
class HostResultReference : GuestArgumentsAndResult<FunctionType, kCallingConventionsVariant> {
 public:
  HostResultReference(GuestArgumentBuffer* buffer)
      : GuestArgumentsAndResult<FunctionType, kCallingConventionsVariant>(buffer) {}

  // Adapter for structural bindings.  Note: we return normal references here, not rvalue references
  // since we are pointing to non-temporary object and don't want to see it moved.
  template <std::size_t index>
  auto& get() const {
    static_assert(index == 0);
    return this->HostResult();
  }
};

}  // namespace berberis

namespace std {

// Partial specializations to support structural bindings for templates
// GuestArgumentsReferences/GuestResultValue.

// tuple_size for GuestArgumentsReferences<Function>
template <typename ResultType,
          typename... ArgumentType,
          bool kNoexcept,
          berberis::GuestAbi::CallingConventionsVariant kCallingConventionsVariant>
class tuple_size<berberis::GuestArgumentsReferences<ResultType(ArgumentType...) noexcept(kNoexcept),
                                                    kCallingConventionsVariant>>
    : public std::integral_constant<std::size_t, sizeof...(ArgumentType)> {};

template <typename ResultType,
          typename... ArgumentType,
          bool kNoexcept,
          berberis::GuestAbi::CallingConventionsVariant kCallingConventionsVariant>
class tuple_size<berberis::HostArgumentsValues<ResultType(ArgumentType...) noexcept(kNoexcept),
                                               kCallingConventionsVariant>>
    : public std::integral_constant<std::size_t, sizeof...(ArgumentType)> {};

// tuple_size for GuestArgumentsReferences<PointerToFunction>
template <typename ResultType,
          typename... ArgumentType,
          bool kNoexcept,
          berberis::GuestAbi::CallingConventionsVariant kCallingConventionsVariant>
class tuple_size<
    berberis::GuestArgumentsReferences<ResultType (*)(ArgumentType...) noexcept(kNoexcept),
                                       kCallingConventionsVariant>>
    : public std::integral_constant<std::size_t, sizeof...(ArgumentType)> {};

template <typename ResultType,
          typename... ArgumentType,
          bool kNoexcept,
          berberis::GuestAbi::CallingConventionsVariant kCallingConventionsVariant>
class tuple_size<berberis::HostArgumentsValues<ResultType (*)(ArgumentType...) noexcept(kNoexcept),
                                               kCallingConventionsVariant>>
    : public std::integral_constant<std::size_t, sizeof...(ArgumentType)> {};

template <std::size_t index,
          typename FunctionType,
          berberis::GuestAbi::CallingConventionsVariant kCallingConventionsVariant>
class tuple_element<index,
                    berberis::GuestArgumentsReferences<FunctionType, kCallingConventionsVariant>> {
 public:
  using type = std::invoke_result_t<
      decltype(&berberis::GuestArgumentsReferences<FunctionType, kCallingConventionsVariant>::
                   template get<index>),
      berberis::GuestArgumentsReferences<FunctionType, kCallingConventionsVariant>>;
};

template <std::size_t index,
          typename FunctionType,
          berberis::GuestAbi::CallingConventionsVariant kCallingConventionsVariant>
class tuple_element<index,
                    berberis::HostArgumentsValues<FunctionType, kCallingConventionsVariant>> {
 public:
  using type = std::invoke_result_t<
      decltype(&berberis::HostArgumentsValues<FunctionType,
                                              kCallingConventionsVariant>::template get<index>),
      berberis::HostArgumentsValues<FunctionType, kCallingConventionsVariant>>;
};

// tuple_size for GuestResultValue<Function>
template <typename ResultType,
          typename... ArgumentType,
          bool kNoexcept,
          berberis::GuestAbi::CallingConventionsVariant kCallingConventionsVariant>
class tuple_size<berberis::GuestResultValue<ResultType(ArgumentType...) noexcept(kNoexcept),
                                            kCallingConventionsVariant>>
    : public std::integral_constant<std::size_t, std::is_same_v<ResultType, void> ? 0 : 1> {};

template <typename ResultType,
          typename... ArgumentType,
          bool kNoexcept,
          berberis::GuestAbi::CallingConventionsVariant kCallingConventionsVariant>
class tuple_size<berberis::HostResultReference<ResultType(ArgumentType...) noexcept(kNoexcept),
                                               kCallingConventionsVariant>>
    : public std::integral_constant<std::size_t, std::is_same_v<ResultType, void> ? 0 : 1> {};

// tuple_size for GuestResultValue<PointerToFunction>
template <typename ResultType,
          typename... ArgumentType,
          bool kNoexcept,
          berberis::GuestAbi::CallingConventionsVariant kCallingConventionsVariant>
class tuple_size<berberis::GuestResultValue<ResultType (*)(ArgumentType...) noexcept(kNoexcept),
                                            kCallingConventionsVariant>>
    : public std::integral_constant<std::size_t, std::is_same_v<ResultType, void> ? 0 : 1> {};

template <typename ResultType,
          typename... ArgumentType,
          bool kNoexcept,
          berberis::GuestAbi::CallingConventionsVariant kCallingConventionsVariant>
class tuple_size<berberis::HostResultReference<ResultType (*)(ArgumentType...) noexcept(kNoexcept),
                                               kCallingConventionsVariant>>
    : public std::integral_constant<std::size_t, std::is_same_v<ResultType, void> ? 0 : 1> {};

template <typename FunctionType,
          berberis::GuestAbi::CallingConventionsVariant kCallingConventionsVariant>
class tuple_element<0, berberis::GuestResultValue<FunctionType, kCallingConventionsVariant>> {
 public:
  using type = std::invoke_result_t<
      decltype(&berberis::GuestResultValue<FunctionType,
                                           kCallingConventionsVariant>::template get<0>),
      berberis::GuestResultValue<FunctionType, kCallingConventionsVariant>>;
};

template <typename FunctionType,
          berberis::GuestAbi::CallingConventionsVariant kCallingConventionsVariant>
class tuple_element<0, berberis::HostResultReference<FunctionType, kCallingConventionsVariant>> {
 public:
  using type = std::invoke_result_t<
      decltype(&berberis::HostResultReference<FunctionType,
                                              kCallingConventionsVariant>::template get<0>),
      berberis::HostResultReference<FunctionType, kCallingConventionsVariant>>;
};

}  // namespace std

#endif  // BERBERIS_GUEST_ABI_GUEST_ARGUMENTS_H_
