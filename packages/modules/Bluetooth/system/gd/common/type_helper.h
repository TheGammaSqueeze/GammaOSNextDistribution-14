/*
 * Copyright 2020 The Android Open Source Project
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

#pragma once

#include <type_traits>

namespace bluetooth {
namespace common {

// Check whether T is a specialization of TemplateType
template <typename T, template <typename...> class TemplateType>
struct is_specialization_of : std::false_type {};
template <template <typename...> class TemplateType, typename... Args>
struct is_specialization_of<TemplateType<Args...>, TemplateType> : std::true_type {};

}  // namespace common
}  // namespace bluetooth