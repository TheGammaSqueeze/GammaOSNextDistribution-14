// Copyright 2021 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

// Copyright 2019 The Fuchsia Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#pragma once

#include <type_traits>
#include <utility>

namespace gfxstream::guest {
namespace fit {
namespace internal {

// Utility to return the first type in a parameter pack.
template <typename... Ts>
struct First;
template <typename FirstType, typename... Rest>
struct First<FirstType, Rest...> {
    using Type = FirstType;
};

template <typename... Ts>
using First_t = typename First<Ts...>::Type;

// Utility to count the occurences of type T in the parameter pack Ts.
template <typename T, typename... Ts>
struct OccurencesOf : std::integral_constant<size_t, 0> {};
template <typename T, typename U>
struct OccurencesOf<T, U> : std::integral_constant<size_t, std::is_same<T, U>::value> {};
template <typename T, typename First, typename... Rest>
struct OccurencesOf<T, First, Rest...>
    : std::integral_constant<size_t,
                             OccurencesOf<T, First>::value + OccurencesOf<T, Rest...>::value> {};

template <typename T, typename... Ts>
constexpr size_t occurencesOf = OccurencesOf<T, Ts...>::value;

// Utility to remove const, volatile, and reference qualifiers.
template <typename T>
using RemoveCvref_t = std::remove_cv_t<std::remove_reference_t<T>>;

// Evaluates to truth-like when type T matches type U with cv-reference removed.
template <typename T, typename U>
using NotSameType = std::negation<std::is_same<T, RemoveCvref_t<U>>>;

// Concept helper for constructors.
template <typename... Conditions>
using RequiresConditions = std::enable_if_t<std::conjunction_v<Conditions...>, bool>;

// Concept helper for assignment operators.
template <typename Return, typename... Conditions>
using AssignmentRequiresConditions =
    std::enable_if_t<std::conjunction_v<Conditions...>, std::add_lvalue_reference_t<Return>>;

// Evaluates to true when every element type of Ts is trivially destructible.
template <typename... Ts>
constexpr bool isTriviallyDestructible = std::conjunction_v<std::is_trivially_destructible<Ts>...>;

// Evaluates to true when every element type of Ts is trivially copyable.
template <typename... Ts>
constexpr bool isTriviallyCopyable =
    (std::conjunction_v<std::is_trivially_copy_assignable<Ts>...> &&
     std::conjunction_v<std::is_trivially_copy_constructible<Ts>...>);

// Evaluates to true when every element type of Ts is trivially movable.
template <typename... Ts>
constexpr bool isTriviallyMovable =
    (std::conjunction_v<std::is_trivially_move_assignable<Ts>...> &&
     std::conjunction_v<std::is_trivially_move_constructible<Ts>...>);

// Enable if relational operator is convertible to bool and the optional
// conditions are true.
template <typename Op, typename... Conditions>
using enable_relop_t =
    std::enable_if_t<(std::is_convertible<Op, bool>::value && std::conjunction_v<Conditions...>),
                     bool>;

template <typename T>
struct Identity {
    using Type = T;
};

// Evaluates to true when T is an unbounded array.
template <typename T>
struct IsUnboundedArray : std::conjunction<std::is_array<T>, std::negation<std::extent<T>>> {};

// Returns true when T is a complete type or an unbounded array.
template <typename T, size_t = sizeof(T)>
constexpr bool isCompleteOrUnboundedArray(Identity<T>) {
    return true;
}
template <typename Identity, typename T = typename Identity::Type>
constexpr bool isCompleteOrUnboundedArray(Identity) {
    return std::disjunction<std::is_reference<T>, std::is_function<T>, std::is_void<T>,
                            IsUnboundedArray<T>>::value;
}

// Using swap for ADL. This directive is contained within the fit::internal
// namespace, which prevents leaking std::swap into user namespaces. Doing this
// at namespace scope is necessary to lookup swap via ADL while preserving the
// noexcept() specification of the resulting lookup.
using std::swap;

// Evaluates to true when T is swappable.
template <typename T, typename = void>
struct IsSwappable : std::false_type {
    static_assert(isCompleteOrUnboundedArray(Identity<T>{}),
                  "T must be a complete type or an unbounded array!");
};
template <typename T>
struct IsSwappable<T, std::void_t<decltype(swap(std::declval<T&>(), std::declval<T&>()))>>
    : std::true_type {
    static_assert(isCompleteOrUnboundedArray(Identity<T>{}),
                  "T must be a complete type or an unbounded array!");
};

// Evaluates to true when T is nothrow swappable.
template <typename T, typename = void>
struct IsNothrowSwappable : std::false_type {
    static_assert(isCompleteOrUnboundedArray(Identity<T>{}),
                  "T must be a complete type or an unbounded array!");
};
template <typename T>
struct IsNothrowSwappable<T, std::void_t<decltype(swap(std::declval<T&>(), std::declval<T&>()))>>
    : std::integral_constant<bool, noexcept(swap(std::declval<T&>(), std::declval<T&>()))> {
    static_assert(isCompleteOrUnboundedArray(Identity<T>{}),
                  "T must be a complete type or an unbounded array!");
};

}  // namespace internal
}  // namespace fit
}  // namespace gfxstream::guest
