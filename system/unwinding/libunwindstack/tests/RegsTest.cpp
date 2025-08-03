/*
 * Copyright (C) 2017 The Android Open Source Project
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

#include <stdint.h>

#include <memory>

#include <gtest/gtest.h>

#include <unwindstack/Elf.h>
#include <unwindstack/ElfInterface.h>
#include <unwindstack/MachineRiscv64.h>
#include <unwindstack/MapInfo.h>
#include <unwindstack/RegsArm.h>
#include <unwindstack/RegsArm64.h>
#include <unwindstack/RegsRiscv64.h>
#include <unwindstack/RegsX86.h>
#include <unwindstack/RegsX86_64.h>

#include "ElfFake.h"
#include "RegsFake.h"
#include "utils/MemoryFake.h"

namespace unwindstack {

class RegsTest : public ::testing::Test {
 protected:
  void SetUp() override {
    fake_memory_ = new MemoryFake;
    std::shared_ptr<Memory> memory(fake_memory_);
    elf_.reset(new ElfFake(memory));
    elf_interface_ = new ElfInterfaceFake(memory);
    elf_->FakeSetInterface(elf_interface_);
  }

  ElfInterfaceFake* elf_interface_;
  MemoryFake* fake_memory_;
  std::unique_ptr<ElfFake> elf_;
};

TEST_F(RegsTest, regs32) {
  RegsImplFake<uint32_t> regs32(50);
  ASSERT_EQ(50U, regs32.total_regs());

  uint32_t* raw = reinterpret_cast<uint32_t*>(regs32.RawData());
  for (size_t i = 0; i < 50; i++) {
    raw[i] = 0xf0000000 + i;
  }
  regs32.set_pc(0xf0120340);
  regs32.set_sp(0xa0ab0cd0);

  for (size_t i = 0; i < 50; i++) {
    ASSERT_EQ(0xf0000000U + i, regs32[i]) << "Failed comparing register " << i;
  }

  ASSERT_EQ(0xf0120340U, regs32.pc());
  ASSERT_EQ(0xa0ab0cd0U, regs32.sp());

  regs32[32] = 10;
  ASSERT_EQ(10U, regs32[32]);
}

TEST_F(RegsTest, regs64) {
  RegsImplFake<uint64_t> regs64(30);
  ASSERT_EQ(30U, regs64.total_regs());

  uint64_t* raw = reinterpret_cast<uint64_t*>(regs64.RawData());
  for (size_t i = 0; i < 30; i++) {
    raw[i] = 0xf123456780000000UL + i;
  }
  regs64.set_pc(0xf123456780102030UL);
  regs64.set_sp(0xa123456780a0b0c0UL);

  for (size_t i = 0; i < 30; i++) {
    ASSERT_EQ(0xf123456780000000U + i, regs64[i]) << "Failed reading register " << i;
  }

  ASSERT_EQ(0xf123456780102030UL, regs64.pc());
  ASSERT_EQ(0xa123456780a0b0c0UL, regs64.sp());

  regs64[8] = 10;
  ASSERT_EQ(10U, regs64[8]);
}

TEST_F(RegsTest, rel_pc) {
  EXPECT_EQ(4U, GetPcAdjustment(0x10, elf_.get(), ARCH_ARM64));
  EXPECT_EQ(4U, GetPcAdjustment(0x4, elf_.get(), ARCH_ARM64));
  EXPECT_EQ(0U, GetPcAdjustment(0x3, elf_.get(), ARCH_ARM64));
  EXPECT_EQ(0U, GetPcAdjustment(0x2, elf_.get(), ARCH_ARM64));
  EXPECT_EQ(0U, GetPcAdjustment(0x1, elf_.get(), ARCH_ARM64));
  EXPECT_EQ(0U, GetPcAdjustment(0x0, elf_.get(), ARCH_ARM64));

  EXPECT_EQ(4U, GetPcAdjustment(0x10, elf_.get(), ARCH_RISCV64));
  EXPECT_EQ(4U, GetPcAdjustment(0x4, elf_.get(), ARCH_RISCV64));
  EXPECT_EQ(0U, GetPcAdjustment(0x3, elf_.get(), ARCH_RISCV64));
  EXPECT_EQ(0U, GetPcAdjustment(0x2, elf_.get(), ARCH_RISCV64));
  EXPECT_EQ(0U, GetPcAdjustment(0x1, elf_.get(), ARCH_RISCV64));
  EXPECT_EQ(0U, GetPcAdjustment(0x0, elf_.get(), ARCH_RISCV64));

  EXPECT_EQ(1U, GetPcAdjustment(0x100, elf_.get(), ARCH_X86));
  EXPECT_EQ(1U, GetPcAdjustment(0x2, elf_.get(), ARCH_X86));
  EXPECT_EQ(1U, GetPcAdjustment(0x1, elf_.get(), ARCH_X86));
  EXPECT_EQ(0U, GetPcAdjustment(0x0, elf_.get(), ARCH_X86));

  EXPECT_EQ(1U, GetPcAdjustment(0x100, elf_.get(), ARCH_X86_64));
  EXPECT_EQ(1U, GetPcAdjustment(0x2, elf_.get(), ARCH_X86_64));
  EXPECT_EQ(1U, GetPcAdjustment(0x1, elf_.get(), ARCH_X86_64));
  EXPECT_EQ(0U, GetPcAdjustment(0x0, elf_.get(), ARCH_X86_64));
}

TEST_F(RegsTest, rel_pc_arm) {
  // Check fence posts.
  elf_->FakeSetLoadBias(0);
  EXPECT_EQ(2U, GetPcAdjustment(0x5, elf_.get(), ARCH_ARM));
  EXPECT_EQ(2U, GetPcAdjustment(0x4, elf_.get(), ARCH_ARM));
  EXPECT_EQ(2U, GetPcAdjustment(0x3, elf_.get(), ARCH_ARM));
  EXPECT_EQ(2U, GetPcAdjustment(0x2, elf_.get(), ARCH_ARM));
  EXPECT_EQ(0U, GetPcAdjustment(0x1, elf_.get(), ARCH_ARM));
  EXPECT_EQ(0U, GetPcAdjustment(0x0, elf_.get(), ARCH_ARM));

  elf_->FakeSetLoadBias(0x100);
  EXPECT_EQ(0U, GetPcAdjustment(0x1, elf_.get(), ARCH_ARM));
  EXPECT_EQ(2U, GetPcAdjustment(0x2, elf_.get(), ARCH_ARM));
  EXPECT_EQ(2U, GetPcAdjustment(0xff, elf_.get(), ARCH_ARM));
  EXPECT_EQ(2U, GetPcAdjustment(0x105, elf_.get(), ARCH_ARM));
  EXPECT_EQ(2U, GetPcAdjustment(0x104, elf_.get(), ARCH_ARM));
  EXPECT_EQ(2U, GetPcAdjustment(0x103, elf_.get(), ARCH_ARM));
  EXPECT_EQ(2U, GetPcAdjustment(0x102, elf_.get(), ARCH_ARM));
  EXPECT_EQ(0U, GetPcAdjustment(0x101, elf_.get(), ARCH_ARM));
  EXPECT_EQ(0U, GetPcAdjustment(0x100, elf_.get(), ARCH_ARM));

  // Check thumb instructions handling.
  elf_->FakeSetLoadBias(0);
  fake_memory_->SetData32(0x2000, 0);
  EXPECT_EQ(2U, GetPcAdjustment(0x2005, elf_.get(), ARCH_ARM));
  fake_memory_->SetData32(0x2000, 0xe000f000);
  EXPECT_EQ(4U, GetPcAdjustment(0x2005, elf_.get(), ARCH_ARM));

  elf_->FakeSetLoadBias(0x400);
  fake_memory_->SetData32(0x2100, 0);
  EXPECT_EQ(2U, GetPcAdjustment(0x2505, elf_.get(), ARCH_ARM));
  fake_memory_->SetData32(0x2100, 0xf111f111);
  EXPECT_EQ(4U, GetPcAdjustment(0x2505, elf_.get(), ARCH_ARM));
}

TEST_F(RegsTest, elf_invalid) {
  auto map_info = MapInfo::Create(0x1000, 0x2000, 0, 0, "");
  std::shared_ptr<Memory> empty;
  Elf* invalid_elf = new Elf(empty);
  map_info->set_elf(invalid_elf);

  EXPECT_EQ(0x500U, invalid_elf->GetRelPc(0x1500, map_info.get()));
  EXPECT_EQ(2U, GetPcAdjustment(0x500U, invalid_elf, ARCH_ARM));
  EXPECT_EQ(2U, GetPcAdjustment(0x511U, invalid_elf, ARCH_ARM));

  EXPECT_EQ(0x600U, invalid_elf->GetRelPc(0x1600, map_info.get()));
  EXPECT_EQ(4U, GetPcAdjustment(0x600U, invalid_elf, ARCH_ARM64));

  EXPECT_EQ(0x600U, invalid_elf->GetRelPc(0x1600, map_info.get()));
  EXPECT_EQ(4U, GetPcAdjustment(0x600U, invalid_elf, ARCH_RISCV64));

  EXPECT_EQ(0x700U, invalid_elf->GetRelPc(0x1700, map_info.get()));
  EXPECT_EQ(1U, GetPcAdjustment(0x700U, invalid_elf, ARCH_X86));

  EXPECT_EQ(0x800U, invalid_elf->GetRelPc(0x1800, map_info.get()));
  EXPECT_EQ(1U, GetPcAdjustment(0x800U, invalid_elf, ARCH_X86_64));
}

TEST_F(RegsTest, regs_convert) {
  RegsArm arm;
  EXPECT_EQ(0, arm.Convert(0));
  EXPECT_EQ(0x1c22, arm.Convert(0x1c22));
  RegsArm64 arm64;
  EXPECT_EQ(0, arm64.Convert(0));
  EXPECT_EQ(0x1c22, arm64.Convert(0x1c22));
  RegsX86 x86;
  EXPECT_EQ(0, x86.Convert(0));
  EXPECT_EQ(0x1c22, x86.Convert(0x1c22));
  RegsX86_64 x86_64;
  EXPECT_EQ(0, x86_64.Convert(0));
  EXPECT_EQ(0x1c22, x86_64.Convert(0x1c22));
}

TEST_F(RegsTest, arm_verify_sp_pc) {
  RegsArm arm;
  uint32_t* regs = reinterpret_cast<uint32_t*>(arm.RawData());
  regs[13] = 0x100;
  regs[15] = 0x200;
  EXPECT_EQ(0x100U, arm.sp());
  EXPECT_EQ(0x200U, arm.pc());
}

TEST_F(RegsTest, arm64_verify_sp_pc) {
  RegsArm64 arm64;
  uint64_t* regs = reinterpret_cast<uint64_t*>(arm64.RawData());
  regs[31] = 0xb100000000ULL;
  regs[32] = 0xc200000000ULL;
  EXPECT_EQ(0xb100000000U, arm64.sp());
  EXPECT_EQ(0xc200000000U, arm64.pc());
}

TEST_F(RegsTest, riscv64_verify_sp_pc) {
  RegsRiscv64 riscv64;
  uint64_t* regs = reinterpret_cast<uint64_t*>(riscv64.RawData());
  regs[2] = 0x212340000ULL;
  regs[0] = 0x1abcd0000ULL;
  EXPECT_EQ(0x212340000U, riscv64.sp());
  EXPECT_EQ(0x1abcd0000U, riscv64.pc());
}

TEST_F(RegsTest, riscv_convert) {
  RegsRiscv64 regs;
  EXPECT_EQ(0, regs.Convert(0));
  EXPECT_EQ(RISCV64_REG_REAL_COUNT - 1, regs.Convert(RISCV64_REG_REAL_COUNT - 1));
  EXPECT_EQ(RISCV64_REG_VLENB, regs.Convert(0x1c22));
  EXPECT_EQ(RISCV64_REG_COUNT, regs.Convert(RISCV64_REG_VLENB));
}

TEST_F(RegsTest, x86_verify_sp_pc) {
  RegsX86 x86;
  uint32_t* regs = reinterpret_cast<uint32_t*>(x86.RawData());
  regs[4] = 0x23450000;
  regs[8] = 0xabcd0000;
  EXPECT_EQ(0x23450000U, x86.sp());
  EXPECT_EQ(0xabcd0000U, x86.pc());
}

TEST_F(RegsTest, x86_64_verify_sp_pc) {
  RegsX86_64 x86_64;
  uint64_t* regs = reinterpret_cast<uint64_t*>(x86_64.RawData());
  regs[7] = 0x1200000000ULL;
  regs[16] = 0x4900000000ULL;
  EXPECT_EQ(0x1200000000U, x86_64.sp());
  EXPECT_EQ(0x4900000000U, x86_64.pc());
}

TEST_F(RegsTest, arm64_strip_pac_mask) {
  RegsArm64 arm64;
  arm64.SetPseudoRegister(Arm64Reg::ARM64_PREG_RA_SIGN_STATE, 1);
  arm64.SetPACMask(0x007fff8000000000ULL);
  arm64.set_pc(0x0020007214bb3a04ULL);
  EXPECT_EQ(0x0000007214bb3a04ULL, arm64.pc());
}

TEST_F(RegsTest, arm64_fallback_pc) {
  RegsArm64 arm64;
  arm64.SetPACMask(0x007fff8000000000ULL);
  arm64.set_pc(0x0020007214bb3a04ULL);
  arm64.fallback_pc();
  EXPECT_EQ(0x0000007214bb3a04ULL, arm64.pc());
}

TEST_F(RegsTest, machine_type) {
  RegsArm arm_regs;
  EXPECT_EQ(ARCH_ARM, arm_regs.Arch());

  RegsArm64 arm64_regs;
  EXPECT_EQ(ARCH_ARM64, arm64_regs.Arch());

  RegsRiscv64 riscv64_regs;
  EXPECT_EQ(ARCH_RISCV64, riscv64_regs.Arch());

  RegsX86 x86_regs;
  EXPECT_EQ(ARCH_X86, x86_regs.Arch());

  RegsX86_64 x86_64_regs;
  EXPECT_EQ(ARCH_X86_64, x86_64_regs.Arch());
}

template <typename RegisterType>
void clone_test(Regs* regs) {
  RegisterType* register_values = reinterpret_cast<RegisterType*>(regs->RawData());
  int num_regs = regs->total_regs();
  for (int i = 0; i < num_regs; ++i) {
    register_values[i] = i;
  }

  std::unique_ptr<Regs> clone(regs->Clone());
  ASSERT_EQ(regs->total_regs(), clone->total_regs());
  RegisterType* clone_values = reinterpret_cast<RegisterType*>(clone->RawData());
  for (int i = 0; i < num_regs; ++i) {
    EXPECT_EQ(register_values[i], clone_values[i]);
    EXPECT_NE(&register_values[i], &clone_values[i]);
  }
}

TEST_F(RegsTest, clone) {
  std::vector<std::unique_ptr<Regs>> regs;
  regs.emplace_back(new RegsArm());
  regs.emplace_back(new RegsArm64());
  regs.emplace_back(new RegsRiscv64());
  regs.emplace_back(new RegsX86());
  regs.emplace_back(new RegsX86_64());

  for (auto& r : regs) {
    if (r->Is32Bit()) {
      clone_test<uint32_t>(r.get());
    } else {
      clone_test<uint64_t>(r.get());
    }
  }
}

}  // namespace unwindstack
