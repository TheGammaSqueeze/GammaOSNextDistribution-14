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

#include "call_intrinsic.h"

#include <cstdint>
#include <tuple>

#include "berberis/assembler/machine_code.h"
#include "berberis/backend/code_emitter.h"
#include "berberis/backend/x86_64/code_gen.h"
#include "berberis/backend/x86_64/machine_ir.h"
#include "berberis/backend/x86_64/machine_ir_builder.h"
#include "berberis/backend/x86_64/machine_ir_check.h"
#include "berberis/base/arena_alloc.h"
#include "berberis/base/bit_util.h"
#include "berberis/code_gen_lib/code_gen_lib.h"
#include "berberis/test_utils/scoped_exec_region.h"

namespace berberis {

namespace {

class ExecTest {
 public:
  ExecTest() = default;

  void Init(x86_64::MachineIR* machine_ir) {
    auto* jump = machine_ir->template NewInsn<PseudoJump>(0);
    machine_ir->bb_list().back()->insn_list().push_back(jump);

    EXPECT_EQ(x86_64::CheckMachineIR(*machine_ir), x86_64::kMachineIRCheckSuccess);

    MachineCode machine_code;
    CodeEmitter as(
        &machine_code, machine_ir->FrameSize(), machine_ir->NumBasicBlocks(), machine_ir->arena());

    // We need to set exit_label_for_testing before Emit, which checks it.
    auto* exit_label = as.MakeLabel();
    as.set_exit_label_for_testing(exit_label);

    // Save callee saved regs.
    as.Push(as.rbp);
    as.Push(as.rbx);
    as.Push(as.r12);
    as.Push(as.r13);
    as.Push(as.r14);
    as.Push(as.r15);
    // Align stack for calls.
    as.Subq(as.rsp, 8);

    x86_64::GenCode(machine_ir, &machine_code, x86_64::GenCodeParams{.skip_emit = true});
    machine_ir->Emit(&as);

    as.Bind(exit_label);

    as.Addq(as.rsp, 8);
    // Restore callee saved regs.
    as.Pop(as.r15);
    as.Pop(as.r14);
    as.Pop(as.r13);
    as.Pop(as.r12);
    as.Pop(as.rbx);
    as.Pop(as.rbp);

    as.Ret();

    as.Finalize();

    exec_.Init(&machine_code);
  }

  void Exec() const { exec_.get<void()>()(); }

 private:
  ScopedExecRegion exec_;
};

__attribute__((naked)) std::tuple<uint64_t> CopyU64(uint64_t) {
  asm(R"(
    movq %rdi, %rax
    ret
  )");
}

template <typename IntrinsicFunc,
          typename T,
          typename std::enable_if_t<std::is_integral_v<T>, bool> = true>
void CallOneArgumentIntrinsicUseIntegral(IntrinsicFunc func, T argument, uint64_t* result) {
  Arena arena;
  x86_64::MachineIR machine_ir(&arena);

  x86_64::MachineIRBuilder builder(&machine_ir);
  builder.StartBasicBlock(machine_ir.NewBasicBlock());
  MachineReg flag_register = builder.ir()->AllocVReg();
  MachineReg result_register = builder.ir()->AllocVReg();
  MachineReg result_value_addr_reg = builder.ir()->AllocVReg();

  CallIntrinsicImpl(&builder, func, result_register, flag_register, argument);

  builder.Gen<x86_64::MovqRegImm>(result_value_addr_reg, bit_cast<uintptr_t>(result));
  builder.Gen<x86_64::MovqMemBaseDispReg>(result_value_addr_reg, 0, result_register);

  ExecTest test;
  test.Init(&machine_ir);
  test.Exec();
}

template <typename IntrinsicFunc>
void CallOneArgumentIntrinsicUseRegister(IntrinsicFunc func, uint64_t argument, uint64_t* result) {
  Arena arena;
  x86_64::MachineIR machine_ir(&arena);

  x86_64::MachineIRBuilder builder(&machine_ir);
  builder.StartBasicBlock(machine_ir.NewBasicBlock());
  MachineReg flag_register = builder.ir()->AllocVReg();
  MachineReg argument_register = builder.ir()->AllocVReg();
  MachineReg result_register = builder.ir()->AllocVReg();
  MachineReg result_value_addr_reg = builder.ir()->AllocVReg();

  builder.Gen<x86_64::MovqRegImm>(argument_register, argument);

  CallIntrinsicImpl(&builder, func, result_register, flag_register, argument_register);

  builder.Gen<x86_64::MovqRegImm>(result_value_addr_reg, bit_cast<uintptr_t>(result));
  builder.Gen<x86_64::MovqMemBaseDispReg>(result_value_addr_reg, 0, result_register);

  ExecTest test;
  test.Init(&machine_ir);
  test.Exec();
}

TEST(HeavyOptimizerCallIntrinsicTest, U32Result) {
  uint64_t result = 0;
  CallOneArgumentIntrinsicUseRegister(reinterpret_cast<std::tuple<uint32_t> (*)(uint64_t)>(CopyU64),
                                      0xaaaa'bbbb'cccc'eeffULL,
                                      &result);
  EXPECT_EQ(result, 0xffff'ffff'cccc'eeffULL);

  result = 0;
  CallOneArgumentIntrinsicUseRegister(reinterpret_cast<std::tuple<uint32_t> (*)(uint64_t)>(CopyU64),
                                      0xaaaa'bbbb'5ccc'eeffULL,
                                      &result);
  EXPECT_EQ(result, 0x5ccc'eeffULL);
}

TEST(HeavyOptimizerCallIntrinsicTest, I32Result) {
  uint64_t result = 0;
  CallOneArgumentIntrinsicUseRegister(reinterpret_cast<std::tuple<int32_t> (*)(uint64_t)>(CopyU64),
                                      0xaaaa'bbbb'cccc'eeffULL,
                                      &result);
  EXPECT_EQ(result, 0xffff'ffff'cccc'eeffULL);

  result = 0;
  CallOneArgumentIntrinsicUseRegister(
      reinterpret_cast<std::tuple<int32_t> (*)(uint64_t)>(CopyU64), 0xcccc'eeffULL, &result);
  EXPECT_EQ(result, 0xffff'ffff'cccc'eeffULL);
}

TEST(HeavyOptimizerCallIntrinsicTest, ZeroExtendU8Arg) {
  uint64_t result = 0;
  CallOneArgumentIntrinsicUseRegister(reinterpret_cast<std::tuple<uint64_t> (*)(uint8_t)>(CopyU64),
                                      0xaaaa'bbbb'cccc'eeffULL,
                                      &result);
  EXPECT_EQ(result, 0xffULL);

  result = 0;
  CallOneArgumentIntrinsicUseIntegral(reinterpret_cast<std::tuple<uint64_t> (*)(uint8_t)>(CopyU64),
                                      static_cast<uint8_t>(0xff),
                                      &result);
  EXPECT_EQ(result, 0xffULL);
}

TEST(HeavyOptimizerCallIntrinsicTest, ZeroExtendU16Arg) {
  uint64_t result = 0;
  CallOneArgumentIntrinsicUseRegister(reinterpret_cast<std::tuple<uint64_t> (*)(uint16_t)>(CopyU64),
                                      0xaaaa'bbbb'cccc'eeffULL,
                                      &result);
  EXPECT_EQ(result, 0xeeffULL);

  result = 0;
  CallOneArgumentIntrinsicUseRegister(
      reinterpret_cast<std::tuple<uint64_t> (*)(uint16_t)>(CopyU64), 0xeeffULL, &result);
  EXPECT_EQ(result, 0xeeffULL);

  result = 0;
  CallOneArgumentIntrinsicUseIntegral(reinterpret_cast<std::tuple<uint64_t> (*)(uint16_t)>(CopyU64),
                                      static_cast<uint16_t>(0xaaaa'bbbb'cccc'eeffULL),
                                      &result);
  EXPECT_EQ(result, 0xeeffULL);

  result = 0;
  CallOneArgumentIntrinsicUseIntegral(reinterpret_cast<std::tuple<uint64_t> (*)(uint16_t)>(CopyU64),
                                      static_cast<uint16_t>(0xeeff),
                                      &result);
  EXPECT_EQ(result, 0xeeffULL);
}

TEST(HeavyOptimizerCallIntrinsicTest, SignExtendU32Arg) {
  uint64_t result = 0;
  CallOneArgumentIntrinsicUseRegister(reinterpret_cast<std::tuple<uint64_t> (*)(uint32_t)>(CopyU64),
                                      0xaaaa'bbbb'cccc'eeffULL,
                                      &result);
  EXPECT_EQ(result, 0xffff'ffff'cccc'eeffULL);

  result = 0;
  CallOneArgumentIntrinsicUseIntegral(reinterpret_cast<std::tuple<uint64_t> (*)(uint32_t)>(CopyU64),
                                      static_cast<uint32_t>(0xcccc'eeff),
                                      &result);
  EXPECT_EQ(result, 0xffff'ffff'cccc'eeffULL);
}

TEST(HeavyOptimizerCallIntrinsicTest, SignExtendI8Arg) {
  uint64_t result = 0;
  CallOneArgumentIntrinsicUseRegister(reinterpret_cast<std::tuple<uint64_t> (*)(int8_t)>(CopyU64),
                                      0xaaaa'bbbb'cccc'eeffULL,
                                      &result);
  EXPECT_EQ(result, 0xffff'ffff'ffff'ffffULL);

  result = 0;
  CallOneArgumentIntrinsicUseIntegral(reinterpret_cast<std::tuple<uint64_t> (*)(int8_t)>(CopyU64),
                                      static_cast<int8_t>(0xff),
                                      &result);
  EXPECT_EQ(result, 0xffff'ffff'ffff'ffffULL);
}

TEST(HeavyOptimizerCallIntrinsicTest, SignExtendI16Arg) {
  uint64_t result = 0;
  CallOneArgumentIntrinsicUseRegister(reinterpret_cast<std::tuple<uint64_t> (*)(int16_t)>(CopyU64),
                                      0xaaaa'bbbb'cccc'eeffULL,
                                      &result);
  EXPECT_EQ(result, 0xffff'ffff'ffff'eeffULL);

  result = 0;
  CallOneArgumentIntrinsicUseIntegral(reinterpret_cast<std::tuple<uint64_t> (*)(int16_t)>(CopyU64),
                                      static_cast<int16_t>(0xeeffULL),
                                      &result);
  EXPECT_EQ(result, 0xffff'ffff'ffff'eeffULL);
}

TEST(HeavyOptimizerCallIntrinsicTest, SignExtendI32Arg) {
  uint64_t result = 0;
  CallOneArgumentIntrinsicUseRegister(reinterpret_cast<std::tuple<uint64_t> (*)(int32_t)>(CopyU64),
                                      0xaaaa'bbbb'cccc'eeffULL,
                                      &result);
  EXPECT_EQ(result, 0xffff'ffff'cccc'eeffULL);

  result = 0;
  CallOneArgumentIntrinsicUseIntegral(reinterpret_cast<std::tuple<uint64_t> (*)(int32_t)>(CopyU64),
                                      static_cast<int32_t>(0xcccc'eeff),
                                      &result);
  EXPECT_EQ(result, 0xffff'ffff'cccc'eeffULL);
}

}  // namespace

}  // namespace berberis
