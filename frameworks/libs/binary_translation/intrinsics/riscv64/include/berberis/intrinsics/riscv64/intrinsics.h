/*
 * Copyright (C) 2015 The Android Open Source Project
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

#ifndef BERBERIS_INTRINSICS_RISCV64_INTRINSICS_H_
#define BERBERIS_INTRINSICS_RISCV64_INTRINSICS_H_

#include <limits>
#include <tuple>
#include <type_traits>

#include "berberis/base/bit_util.h"
#include "berberis/intrinsics/common/intrinsics.h"
#include "berberis/intrinsics/intrinsics_float.h"  // Float32/Float64/ProcessNans
#include "berberis/intrinsics/type_traits.h"

namespace berberis::intrinsics {

#include "berberis/intrinsics/intrinsics-inl.h"  // NOLINT: generated file!

}  // namespace berberis::intrinsics

#include "berberis/intrinsics/intrinsics_atomics_impl.h"
#include "berberis/intrinsics/intrinsics_bitmanip_impl.h"
#include "berberis/intrinsics/intrinsics_floating_point_impl.h"

#endif  // BERBERIS_INTRINSICS_RISCV64_INTRINSICS_H_
