/*
 * Copyright (C) 2019 The Android Open Source Project
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

#include <cinttypes>
#include <optional>
#include <tuple>

#include "berberis/base/bit_util.h"

#pragma clang diagnostic push
// Clang does not allow use of C++ types in “extern "C"” functions - but we need to declare one to
// test it.
#pragma clang diagnostic ignored "-Wreturn-type-c-linkage"

extern "C" std::tuple<uint64_t> AsmTupleTest(std::tuple<uint64_t>*);

// This function takes first parameter %rdi and uses it as the address of a tuple.
// If tuple is returned on registers it would contain address of a tuple passed via pointer.
// If tuple is returned on stack this would be address of the returned tuple (hidden parameter).
asm(R"(.p2align 4, 0x90
       .type AsmTupleTest,@function
       AsmTupleTest:
       .cfi_startproc
       movl $42, (%rdi)
       movq %rdi, %rax
       ret
       .size AsmTupleTest, .-AsmTupleTest
       .cfi_endproc)");

#pragma clang diagnostic pop

namespace berberis {

namespace {

std::optional<bool> TupleIsReturnedOnRegisters() {
  std::tuple<uint64_t> result_if_on_regs{};
  std::tuple<uint64_t> result_if_on_stack{};
  result_if_on_stack = AsmTupleTest(&result_if_on_regs);
  if (std::get<uint64_t>(result_if_on_regs) == 42 &&
      std::get<uint64_t>(result_if_on_stack) == bit_cast<uint64_t>(&result_if_on_regs)) {
    return true;
  } else if (std::get<uint64_t>(result_if_on_regs) == 0 &&
             std::get<uint64_t>(result_if_on_stack) == 42) {
    return false;
  } else {
    // Shouldn't happen with proper x86-64 compiler.
    return {};
  }
}

// Note: tuple is returned on registers when libc++ is used and on stack if libstdc++ is used.
TEST(LibCxxAbi, Tuple) {
  auto tuple_is_returned_on_registers = TupleIsReturnedOnRegisters();
  EXPECT_TRUE(tuple_is_returned_on_registers.has_value());
#ifdef _LIBCPP_VERSION
  EXPECT_TRUE(*tuple_is_returned_on_registers);
#else
  EXPECT_FALSE(*tuple_is_returned_on_registers);
#endif
}

}  // namespace

}  // namespace berberis
