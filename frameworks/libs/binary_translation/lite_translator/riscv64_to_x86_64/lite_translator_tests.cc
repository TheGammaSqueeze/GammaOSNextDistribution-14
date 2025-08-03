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

#include <cstdint>

#include "berberis/assembler/machine_code.h"
#include "berberis/guest_state/guest_addr.h"

#include "lite_translator.h"
#include "register_maintainer.h"

namespace berberis {

namespace {

TEST(Riscv64LiteTranslatorTest, GetMappedRegs) {
  MachineCode machine_code;
  LiteTranslator translator(&machine_code, 0);
  auto [mapped_reg, is_new] = translator.GetMappedRegisterOrMap(1);
  EXPECT_TRUE(is_new);
  auto [mapped_reg2, is_new2] = translator.GetMappedRegisterOrMap(1);
  EXPECT_FALSE(is_new2);
  EXPECT_EQ(mapped_reg, mapped_reg2);
  auto [mapped_reg3, is_new3] = translator.GetMappedRegisterOrMap(2);
  EXPECT_TRUE(is_new3);
  EXPECT_NE(mapped_reg, mapped_reg3);
}

TEST(Riscv64LiteTranslatorTest, GetRegs) {
  // if size of machine_code does not change load is skipped.
  MachineCode machine_code;
  LiteTranslator translator(&machine_code, 0);
  translator.GetReg(1);
  EXPECT_TRUE(translator.gp_maintainer()->IsMapped(1));
  int size1 = machine_code.install_size();
  translator.GetReg(1);
  int size2 = machine_code.install_size();
  EXPECT_EQ(size1, size2);
  translator.GetReg(2);
  EXPECT_TRUE(translator.gp_maintainer()->IsMapped(2));
  int size3 = machine_code.install_size();
  EXPECT_LT(size2, size3);
  translator.GetReg(3);
  EXPECT_TRUE(translator.gp_maintainer()->IsMapped(3));
  int size4 = machine_code.install_size();
  EXPECT_LT(size3, size4);
}

TEST(Riscv64LiteTranslatorTest, GetFpReg) {
  // if size of machine_code does not change load is skipped.
  MachineCode machine_code;
  LiteTranslator translator(&machine_code, 0);
  translator.GetFpReg(1);
  EXPECT_TRUE(translator.simd_maintainer()->IsMapped(1));
  int size1 = machine_code.install_size();
  translator.GetFpReg(1);
  int size2 = machine_code.install_size();
  EXPECT_EQ(size1, size2);
  translator.GetFpReg(2);
  EXPECT_TRUE(translator.simd_maintainer()->IsMapped(2));
  int size3 = machine_code.install_size();
  EXPECT_LT(size2, size3);
  translator.GetFpReg(3);
  EXPECT_TRUE(translator.simd_maintainer()->IsMapped(3));
  int size4 = machine_code.install_size();
  EXPECT_LT(size3, size4);
}

TEST(Riscv64LiteTranslatorTest, NanBoxAndSetFpReg) {
  MachineCode machine_code;
  LiteTranslator translator(&machine_code, 0);
  LiteTranslator::FpRegister reg;
  int32_t offset = offsetof(ThreadState, cpu.f) + 1 * sizeof(LiteTranslator::Float64);
  size_t store_insn_base = machine_code.install_size();
  translator.StoreFpReg(reg, offset);
  size_t store_insn_size = machine_code.install_size() - store_insn_base;

  size_t mov_insn_base = machine_code.install_size();
  translator.MoveFpReg(reg, reg);
  size_t mov_insn_size = machine_code.install_size() - mov_insn_base;

  size_t nan_box_insn_base = machine_code.install_size();
  translator.NanBoxFpReg<LiteTranslator::Float32>(reg);
  size_t nan_box_insn_size = machine_code.install_size() - nan_box_insn_base;

  ASSERT_NE(store_insn_size, mov_insn_size);

  size_t nan_box_and_set_base = machine_code.install_size();
  translator.NanBoxAndSetFpReg<LiteTranslator::Float32>(1, reg);
  EXPECT_EQ(nan_box_insn_size + mov_insn_size, machine_code.install_size() - nan_box_and_set_base);

  nan_box_and_set_base = machine_code.install_size();
  translator.NanBoxAndSetFpReg<LiteTranslator::Float32>(1, reg);
  EXPECT_EQ(nan_box_insn_size + mov_insn_size, machine_code.install_size() - nan_box_and_set_base);
}

TEST(Riscv64LiteTranslatorTest, SetReg) {
  MachineCode machine_code;
  LiteTranslator translator(&machine_code, 0);
  LiteTranslator::Register reg;
  int32_t offset = offsetof(ThreadState, cpu.x[0]) + 1 * 8;
  size_t store_insn_base = machine_code.install_size();
  translator.as()->Movq({.base = translator.as()->rbp, .disp = offset}, reg);
  size_t store_insn_size = machine_code.install_size() - store_insn_base;

  size_t mov_insn_base = machine_code.install_size();
  translator.as()->Movq(reg, reg);
  size_t mov_insn_size = machine_code.install_size() - mov_insn_base;

  ASSERT_NE(store_insn_size, mov_insn_size);

  size_t set_base = machine_code.install_size();
  translator.SetReg(1, reg);
  EXPECT_EQ(mov_insn_size, machine_code.install_size() - set_base);

  set_base = machine_code.install_size();
  translator.SetReg(1, reg);
  EXPECT_EQ(mov_insn_size, machine_code.install_size() - set_base);
}

}  // namespace

}  // namespace berberis
