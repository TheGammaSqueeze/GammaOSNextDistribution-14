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

#ifndef CHRE_UTIL_ALWAYS_FALSE_H_
#define CHRE_UTIL_ALWAYS_FALSE_H_

#include <type_traits>

namespace chre {

/**
 * Simple helper which can be used when a type-dependent expression is needed
 * that evaluates to false. Useful with static_assert in templates, for example:
 *
 * <code>
 * template<typename T>
 * class MyClass {
 *   static_assert(AlwaysFalse<T>::value,
 *                 "Use one of the supported template specializations");
 * };
 *
 * template<>
 * class MyClass<int> {
 *   // (valid implementation)
 * };
 * </code>
 */
template <typename... T>
struct AlwaysFalse : std::false_type {};

}  // namespace chre

#endif  // CHRE_UTIL_ALWAYS_FALSE_H_
