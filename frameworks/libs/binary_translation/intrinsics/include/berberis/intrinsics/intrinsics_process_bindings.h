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

#ifndef BERBERIS_INTRINSICS_INTRINSICS_PROCESS_BINDINGS_H_
#define BERBERIS_INTRINSICS_INTRINSICS_PROCESS_BINDINGS_H_

#include <xmmintrin.h>

#include <cstdint>

#include "berberis/intrinsics/intrinsics_args.h"
#include "berberis/intrinsics/intrinsics_bindings.h"
#include "berberis/intrinsics/intrinsics_float.h"
#include "berberis/intrinsics/type_traits.h"

namespace berberis::intrinsics::bindings {

// Comparison of pointers which point to different functions is generally not a
// constexpr since such functions can be merged in object code (comparing
// pointers to the same function is constexpr). This helper compares them using
// templates explicitly telling that we are not worried about such subtleties here.
template <auto kFunction>
class FunctionCompareTag;

#include "berberis/intrinsics/intrinsics_process_bindings-inl.h"

}  // namespace berberis::intrinsics::bindings

#endif  // BERBERIS_INTRINSICS_INTRINSICS_BINDINGS_H_
