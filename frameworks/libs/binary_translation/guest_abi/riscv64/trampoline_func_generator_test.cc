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

#include "berberis/base/bit_util.h"
#include "berberis/guest_abi/function_wrappers.h"
#include "berberis/guest_state/guest_state.h"

namespace berberis {

namespace {

TEST(TrampolineFuncGenerator, IntRes) {
  struct Callee {
    static int foo() { return 1; }
  };

  TrampolineFunc func = GetTrampolineFunc<int(void)>();

  ThreadState state{};

  func(reinterpret_cast<void*>(Callee::foo), &state);

  EXPECT_EQ(GetXReg<A0>(state.cpu), 1U);
}

TEST(TrampolineFuncGenerator, FloatArgs_lp64) {
  struct Callee {
    static void foo(void* p, float x, float y) {
      EXPECT_EQ(p, nullptr);
      EXPECT_EQ(x, 0.5f);
      EXPECT_EQ(y, 0.75f);
    }
  };

  TrampolineFunc func = GetTrampolineFunc<void(void*, float, float), GuestAbi::kLp64>();

  ThreadState state{};

  SetXReg<A0>(state.cpu, 0U);
  SetXReg<A1>(state.cpu, bit_cast<uint32_t>(0.5f));
  SetXReg<A2>(state.cpu, bit_cast<uint32_t>(0.75f));

  EXPECT_NO_FATAL_FAILURE(func(reinterpret_cast<void*>(Callee::foo), &state));
}

TEST(TrampolineFuncGenerator, FloatArgs_lp64d) {
  struct Callee {
    static void foo(void* p, float x, float y) {
      EXPECT_EQ(p, nullptr);
      EXPECT_EQ(x, 0.5f);
      EXPECT_EQ(y, 0.75f);
    }
  };

  TrampolineFunc func = GetTrampolineFunc<void(void*, float, float), GuestAbi::kLp64d>();

  ThreadState state{};

  SetXReg<A0>(state.cpu, 0U);
  SetFReg<FA0>(state.cpu, bit_cast<uint32_t>(0.5f));
  SetFReg<FA1>(state.cpu, bit_cast<uint32_t>(0.75f));

  EXPECT_NO_FATAL_FAILURE(func(reinterpret_cast<void*>(Callee::foo), &state));
}

}  // namespace

}  // namespace berberis
