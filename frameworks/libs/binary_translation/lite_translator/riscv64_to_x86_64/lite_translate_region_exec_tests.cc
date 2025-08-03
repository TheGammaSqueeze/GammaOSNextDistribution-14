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

#include <setjmp.h>
#include <cstdint>

#include "berberis/assembler/machine_code.h"
#include "berberis/guest_state/guest_addr.h"
#include "berberis/guest_state/guest_state.h"
#include "berberis/lite_translator/lite_translate_region.h"
#include "berberis/test_utils/scoped_exec_region.h"
#include "berberis/test_utils/testing_run_generated_code.h"

namespace berberis {

namespace {

class Riscv64LiteTranslateRegionTest : public ::testing::Test {
 public:
  template <typename T>
  void Reset(const T code) {
    state_.cpu.insn_addr = ToGuestAddr(code);
  }

  // Attention: it's important to pass code array by reference for sizeof(code) to return the size
  // of the whole array rather than a pointer size when it's passed by value.
  template <typename T>
  bool Run(T& code, GuestAddr expected_stop_addr) {
    Reset(code);
    GuestAddr code_end = ToGuestAddr(bit_cast<char*>(&code[0]) + sizeof(code));
    MachineCode machine_code;
    bool success = LiteTranslateRange(state_.cpu.insn_addr,
                                      code_end,
                                      &machine_code,
                                      LiteTranslateParams{.allow_dispatch = false});

    if (!success) {
      return false;
    }

    ScopedExecRegion exec(&machine_code);

    TestingRunGeneratedCode(&state_, exec.get(), expected_stop_addr);

    // Make sure we print the addresses on mismatch.
    EXPECT_EQ(state_.cpu.insn_addr, expected_stop_addr);
    return true;
  }

 protected:
  ThreadState state_;
};

TEST_F(Riscv64LiteTranslateRegionTest, AddTwice) {
  static const uint32_t code[] = {
      0x003100b3,  // add x1, x2, x3
      0x002081b3,  // add x3, x1, x2
  };
  SetXReg<1>(state_.cpu, 0);
  SetXReg<2>(state_.cpu, 1);
  SetXReg<3>(state_.cpu, 1);
  EXPECT_TRUE(Run(code, ToGuestAddr(bit_cast<char*>(&code[0]) + sizeof(code))));
  EXPECT_EQ(GetXReg<3>(state_.cpu), 3ULL);
}

TEST_F(Riscv64LiteTranslateRegionTest, XorLoop) {
  static const uint16_t code[] = {
      // loop_enter:
      0x161b,  // (4 bytes sllw instruction)
      0x0015,  // sllw    a2,a0,0x1
      0x35fd,  // addw    a1,a1,-1
      0x8d31,  // xor     a0,a0,a2
      0xfde5,  // bnez    a1, loop_enter
  };
  SetXReg<A0>(state_.cpu, 1);
  // The counter will be equal one after decrement, so we expected to branch back.
  SetXReg<A1>(state_.cpu, 2);
  SetXReg<A2>(state_.cpu, 0);
  EXPECT_TRUE(Run(code, ToGuestAddr(&code[0])));
  EXPECT_EQ(GetXReg<A0>(state_.cpu), uint64_t{0b11});
}

TEST_F(Riscv64LiteTranslateRegionTest, RegionEnd) {
  static const uint32_t code[] = {
      0x003100b3,  // add x1, x2, x3
      0x002081b3,  // add x3, x1, x2
      0x008000ef,  // jal x1, 8
      0x003100b3,  // add x1, x2, x3
      0x002081b3,  // add x3, x1, x2
  };
  SetXReg<1>(state_.cpu, 0);
  SetXReg<2>(state_.cpu, 1);
  SetXReg<3>(state_.cpu, 1);
  EXPECT_TRUE(Run(code, ToGuestAddr(code) + 16));
  EXPECT_EQ(GetXReg<3>(state_.cpu), 3ULL);
}

TEST_F(Riscv64LiteTranslateRegionTest, GracefulFailure) {
  static const uint32_t code[] = {
      0x003100b3,  // add x1, x2, x3
      0x00000073,  // ecall #0x0
  };
  MachineCode machine_code;
  EXPECT_FALSE(LiteTranslateRange(ToGuestAddr(code),
                                  ToGuestAddr(code) + 8,
                                  &machine_code,
                                  LiteTranslateParams{.allow_dispatch = false}));
}

jmp_buf g_jmp_buf;

extern "C" __attribute__((used, __visibility__("hidden"))) void
LightTranslateRegionTest_HandleThresholdReached() {
  // We are in generated code, so the easiest way to recover without using
  // runtime library internals is to longjmp.
  longjmp(g_jmp_buf, 1);
}

// The execution jumps here (no call!) from generated code. Thus we
// need this proxy to normal C++ ABI function. Stack in generated code is
// aligned properly for calls.
__attribute__((naked)) void CounterThresholdReached() {
  asm(R"(call LightTranslateRegionTest_HandleThresholdReached)");
}

TEST_F(Riscv64LiteTranslateRegionTest, ProfileCounter) {
  static const uint16_t code[] = {
      0x0505,  //  addi a0,a0,1
  };

  // Volatile to ensure it's correctly restored on longjmp.
  volatile ThreadState state;
  GuestAddr code_end = ToGuestAddr(code + 1);

  MachineCode machine_code;
  uint32_t counter;
  constexpr uint32_t kCounterThreshold = 42;
  bool success = LiteTranslateRange(
      ToGuestAddr(code),
      code_end,
      &machine_code,
      {
          .enable_self_profiling = true,
          .counter_location = &counter,
          .counter_threshold = kCounterThreshold,
          .counter_threshold_callback = reinterpret_cast<const void*>(CounterThresholdReached),
      });
  ASSERT_TRUE(success);

  ScopedExecRegion exec(&machine_code);

  if (setjmp(g_jmp_buf) != 0) {
    // We should trap here after longjmp.
    EXPECT_EQ(state.cpu.x[10], kCounterThreshold);
    return;
  }

  state.cpu.x[10] = 0;
  // We shouldn't ever reach above kCounterThreshold, but keeping this exit condition
  // to handle a potential failure gracefully.
  for (uint64_t i = 0; i <= kCounterThreshold; i++) {
    state.cpu.insn_addr = ToGuestAddr(code);
    // The API accepts non-volatile state.
    TestingRunGeneratedCode(const_cast<ThreadState*>(&state), exec.get(), code_end);
    EXPECT_EQ(state.cpu.insn_addr, code_end);
    EXPECT_EQ(state.cpu.x[10], i + 1);
  }
  FAIL();
}

}  // namespace

}  // namespace berberis
