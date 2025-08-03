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

#include "gtest/gtest.h"

#include "berberis/backend/x86_64/machine_insn_intrinsics.h"
#include "berberis/intrinsics/common_to_x86/intrinsics_bindings.h"
#include "berberis/intrinsics/intrinsics_args.h"

namespace berberis {

namespace {

// TEST(MachineInsnIntrinsicsTest, HasNMem)
static_assert(x86_64::has_n_mem_v<
              1,
              TmpArg<intrinsics::bindings::Mem32, intrinsics::bindings::DefEarlyClobber>>);
static_assert(!x86_64::has_n_mem_v<1>);
static_assert(!x86_64::has_n_mem_v<
              1,
              TmpArg<intrinsics::bindings::GeneralReg32, intrinsics::bindings::DefEarlyClobber>>);
static_assert(x86_64::has_n_mem_v<2,
                                  TmpArg<intrinsics::bindings::Mem32, intrinsics::bindings::Use>,
                                  TmpArg<intrinsics::bindings::Mem32, intrinsics::bindings::Def>>);
static_assert(!x86_64::has_n_mem_v<
              2,
              TmpArg<intrinsics::bindings::Mem32, intrinsics::bindings::DefEarlyClobber>>);

// TEST(MachineInsnIntrinsicsTest, ConstructorArgs)
static_assert(
    std::is_same_v<x86_64::constructor_args_t<
                       TmpArg<intrinsics::bindings::Mem64, intrinsics::bindings::DefEarlyClobber>>,
                   std::tuple<MachineReg, int32_t>>);
static_assert(
    std::is_same_v<x86_64::constructor_args_t<TmpArg<intrinsics::bindings::GeneralReg64,
                                                     intrinsics::bindings::DefEarlyClobber>>,
                   std::tuple<MachineReg>>);
static_assert(std::is_same_v<x86_64::constructor_args_t<
                                 InArg<0, intrinsics::bindings::Imm32, intrinsics::bindings::Use>>,
                             std::tuple<int32_t>>);
static_assert(
    std::is_same_v<
        x86_64::constructor_args_t<
            InArg<0, intrinsics::bindings::Imm16, intrinsics::bindings::Use>,
            TmpArg<intrinsics::bindings::Mem64, intrinsics::bindings::DefEarlyClobber>,
            TmpArg<intrinsics::bindings::GeneralReg64, intrinsics::bindings::DefEarlyClobber>>,
        std::tuple<int16_t, MachineReg, int32_t, MachineReg>>);

}  // namespace

}  // namespace berberis
