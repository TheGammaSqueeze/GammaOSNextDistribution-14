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

// Copyright 2018 The Fuchsia Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#pragma once

#include <assert.h>

#include <optional>
#include <type_traits>
#include <utility>

namespace gfxstream::guest {
namespace fit {

// Determines whether a type can be compared with nullptr.
template <typename T, typename Comparable = bool>
struct IsComparableWithNull : public std::false_type {};
template <typename T>
struct IsComparableWithNull<T, decltype(std::declval<const T&>() == nullptr)>
    : public std::true_type {};

// Suppress the warning when the compiler can see that a nullable value is
// never equal to nullptr.
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Waddress"
template <typename T, std::enable_if_t<IsComparableWithNull<T>::value, bool> = true>
constexpr inline bool isNull(T&& value) {
    return std::forward<T>(value) == nullptr;
}
#pragma GCC diagnostic pop

template <typename T, std::enable_if_t<!IsComparableWithNull<T>::value, bool> = false>
constexpr inline bool isNull(T&&) {
    return false;
}

// Determines whether a type can be initialized, assigned, and compared
// with nullptr.
template <typename T>
struct IsNullable
    : public std::integral_constant<bool,
                                    std::is_constructible<T, decltype(nullptr)>::value &&
                                        std::is_assignable<T&, decltype(nullptr)>::value &&
                                        IsComparableWithNull<T>::value> {};
template <>
struct IsNullable<void> : public std::false_type {};

// Holds a value or nullptr.
//
// This class is similar to |std::optional<T>| except that it uses less
// storage when the value type can be initialized, assigned, and compared
// with nullptr.
//
// For example:
// - sizeof(fit::nullable<void*>) == sizeof(void*)
// - sizeof(std::optional<void*>) == sizeof(struct { bool; void*; })
// - sizeof(fit::nullable<int>) == sizeof(struct { bool; int; })
// - sizeof(std::optional<int>) == sizeof(struct { bool; int; })
//
// TODO(fxbug.dev/42123486): fit::nullable does not precisely mirror
// std::optional. This should be corrected to avoid surprises when switching
// between the types.
template <typename T,
          bool = (IsNullable<T>::value && std::is_constructible<T, T&&>::value &&
                  std::is_assignable<T&, T&&>::value)>
class Nullable final {
public:
    using value_type = T;

    ~Nullable() = default;
    constexpr Nullable() = default;

    explicit constexpr Nullable(decltype(nullptr)) {}
    explicit constexpr Nullable(T value) : mOpt(std::move(value)) {}

    constexpr Nullable(const Nullable& other) = default;
    constexpr Nullable& operator=(const Nullable& other) = default;

    constexpr Nullable(Nullable&& other) = default;
    constexpr Nullable& operator=(Nullable&& other) = default;

    constexpr T& value() & { return mOpt.value(); }
    constexpr const T& value() const& { return mOpt.value(); }
    constexpr T&& value() && { return std::move(mOpt.value()); }
    constexpr const T&& value() const&& { return std::move(mOpt.value()); }

    template <typename U = T>
    constexpr T valueOr(U&& default_value) const {
        return mOpt.value_or(std::forward<U>(default_value));
    }

    constexpr T* operator->() { return &*mOpt; }
    constexpr const T* operator->() const { return &*mOpt; }
    constexpr T& operator*() { return *mOpt; }
    constexpr const T& operator*() const { return *mOpt; }

    constexpr bool hasValue() const { return mOpt.has_value(); }
    explicit constexpr operator bool() const { return hasValue(); }

    constexpr Nullable& operator=(decltype(nullptr)) {
        reset();
        return *this;
    }

    constexpr Nullable& operator=(T value) {
        mOpt = std::move(value);
        return *this;
    }

    constexpr void reset() { mOpt.reset(); }

    constexpr void swap(Nullable& other) { mOpt.swap(other.mOpt); }

private:
    std::optional<T> mOpt;
};

template <typename T>
class Nullable<T, true> final {
public:
    using value_type = T;

    constexpr Nullable() : mValue(nullptr) {}
    explicit constexpr Nullable(decltype(nullptr)) : mValue(nullptr) {}
    explicit constexpr Nullable(T value) : mValue(std::move(value)) {}
    constexpr Nullable(const Nullable& other) = default;
    constexpr Nullable(Nullable&& other) : mValue(std::move(other.value_)) {}
    ~Nullable() = default;

    constexpr T& value() & {
        if (hasValue()) {
            return mValue;
        } else {
            __builtin_abort();
        }
    }
    constexpr const T& value() const& {
        if (hasValue()) {
            return mValue;
        } else {
            __builtin_abort();
        }
    }
    constexpr T&& value() && {
        if (hasValue()) {
            return std::move(mValue);
        } else {
            __builtin_abort();
        }
    }
    constexpr const T&& value() const&& {
        if (hasValue()) {
            return std::move(mValue);
        } else {
            __builtin_abort();
        }
    }

    template <typename U = T>
    constexpr T valueOr(U&& default_value) const {
        return hasValue() ? mValue : static_cast<T>(std::forward<U>(default_value));
    }

    constexpr T* operator->() { return &mValue; }
    constexpr const T* operator->() const { return &mValue; }
    constexpr T& operator*() { return mValue; }
    constexpr const T& operator*() const { return mValue; }

    constexpr bool hasValue() const { return !(mValue == nullptr); }
    explicit constexpr operator bool() const { return hasValue(); }

    constexpr Nullable& operator=(const Nullable& other) = default;
    constexpr Nullable& operator=(Nullable&& other) {
        mValue = std::move(other.value_);
        return *this;
    }

    constexpr Nullable& operator=(decltype(nullptr)) {
        reset();
        return *this;
    }

    constexpr Nullable& operator=(T value) {
        mValue = std::move(value);
        return *this;
    }

    constexpr void reset() { mValue = nullptr; }

    constexpr void swap(Nullable& other) {
        using std::swap;
        swap(mValue, other.value_);
    }

private:
    T mValue;
};

template <typename T>
void swap(Nullable<T>& a, Nullable<T>& b) {
    a.swap(b);
}

template <typename T>
constexpr bool operator==(const Nullable<T>& lhs, decltype(nullptr)) {
    return !lhs.hasValue();
}
template <typename T>
constexpr bool operator!=(const Nullable<T>& lhs, decltype(nullptr)) {
    return lhs.hasValue();
}

template <typename T>
constexpr bool operator==(decltype(nullptr), const Nullable<T>& rhs) {
    return !rhs.hasValue();
}
template <typename T>
constexpr bool operator!=(decltype(nullptr), const Nullable<T>& rhs) {
    return rhs.hasValue();
}

template <typename T, typename U>
constexpr bool operator==(const Nullable<T>& lhs, const Nullable<U>& rhs) {
    return (lhs.hasValue() == rhs.hasValue()) && (!lhs.hasValue() || *lhs == *rhs);
}
template <typename T, typename U>
constexpr bool operator!=(const Nullable<T>& lhs, const Nullable<U>& rhs) {
    return (lhs.hasValue() != rhs.hasValue()) || (lhs.hasValue() && *lhs != *rhs);
}

template <typename T, typename U>
constexpr bool operator==(const Nullable<T>& lhs, const U& rhs) {
    return (lhs.hasValue() != isNull(rhs)) && (!lhs.hasValue() || *lhs == rhs);
}
template <typename T, typename U>
constexpr bool operator!=(const Nullable<T>& lhs, const U& rhs) {
    return (lhs.hasValue() == isNull(rhs)) || (lhs.hasValue() && *lhs != rhs);
}

template <typename T, typename U>
constexpr bool operator==(const T& lhs, const Nullable<U>& rhs) {
    return (isNull(lhs) != rhs.hasValue()) && (!rhs.hasValue() || lhs == *rhs);
}
template <typename T, typename U>
constexpr bool operator!=(const T& lhs, const Nullable<U>& rhs) {
    return (isNull(lhs) == rhs.hasValue()) || (rhs.hasValue() && lhs != *rhs);
}

}  // namespace fit
}  // namespace gfxstream::guest
