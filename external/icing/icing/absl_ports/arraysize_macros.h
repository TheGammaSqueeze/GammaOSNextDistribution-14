// Copyright (C) 2022 Google LLC
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

#ifndef ICING_ABSL_PORTS_ARRAYSIZE_MACROS_H_
#define ICING_ABSL_PORTS_ARRAYSIZE_MACROS_H_

#include <cstddef>

namespace icing {
namespace lib {
namespace absl_ports {

// ABSL_PORT_ARRAYSIZE()
//
// Returns the number of elements in an array as a compile-time constant, which
// can be used in defining new arrays. If you use this macro on a pointer by
// mistake, you will get a compile-time error.
#define ABSL_PORT_ARRAYSIZE(array) (sizeof(absl_ports::ArraySizeHelper(array)))

// Note: this internal template function declaration is used by ABSL_PORT_ARRAYSIZE.
// The function doesn't need a definition, as we only use its type.
template <typename T, size_t N>
auto ArraySizeHelper(const T (&array)[N]) -> char (&)[N];

}  // namespace absl_ports
}  // namespace lib
}  // namespace icing

#endif  // ICING_ARRAYSIZE_MACROS_H_
