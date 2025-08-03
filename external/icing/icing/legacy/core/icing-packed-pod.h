// Copyright (C) 2019 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#ifndef ICING_LEGACY_CORE_ICING_PACKED_POD_H_
#define ICING_LEGACY_CORE_ICING_PACKED_POD_H_

#include <type_traits>

namespace icing {
namespace lib {

// Any struct whose size is not a multiple of its alignment will lead to a
// padding of sizeof(T) % alignof(T) bytes per element when creating an array of
// that type.
template <typename T>
struct no_array_padding
    : std::integral_constant<bool, sizeof(T) % alignof(T) == 0> {};

// See go/icing-ubsan for details on requirements of packed objects.
template <typename T>
struct icing_is_packed_pod
    : std::conjunction<std::integral_constant<bool, alignof(T) == 1>,
                        std::is_standard_layout<T>,
                        std::is_trivially_copyable<T>> {};

}  // namespace lib
}  // namespace icing

#endif  // ICING_LEGACY_CORE_ICING_PACKED_POD_H_
