/*
 * Copyright (C) 2020 The Android Open Source Project
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

#ifndef ANDROID_XSDC_SUPPORT_H
#define ANDROID_XSDC_SUPPORT_H

#include <iterator>
#include <type_traits>

namespace android {

namespace details {
// Never instantiated. Used as a placeholder for template variables.
template <typename T>
struct xsdc_invalid_type;

// XSDC generates specializations of this for enums. See xsdc_enum_range.
template <typename T, typename = std::enable_if_t<std::is_enum<T>::value>>
constexpr xsdc_invalid_type<T> xsdc_enum_values;
}  // namespace details

/**
 * Every XSDC generated enum supports this function.
 * E.x.: for (const auto v : xsdc_enum_range<Enum>()) { ... }
 */
template <typename T, typename = std::enable_if_t<std::is_enum<T>::value>>
struct xsdc_enum_range {
    // Container-like associated type.
    using value_type = T;

    constexpr auto begin() const { return std::begin(details::xsdc_enum_values<T>); }
    constexpr auto cbegin() const { return begin(); }
    constexpr auto rbegin() const { return std::rbegin(details::xsdc_enum_values<T>); }
    constexpr auto crbegin() const { return rbegin(); }
    constexpr auto end() const { return std::end(details::xsdc_enum_values<T>); }
    constexpr auto cend() const { return end(); }
    constexpr auto rend() const { return std::rend(details::xsdc_enum_values<T>); }
    constexpr auto crend() const { return rend(); }
};

}  // namespace android

#endif  // ANDROID_XSDC_SUPPORT_H
