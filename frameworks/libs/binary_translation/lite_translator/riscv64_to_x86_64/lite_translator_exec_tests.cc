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
#include "berberis/runtime_primitives/runtime_library.h"
#include "berberis/test_utils/scoped_exec_region.h"

#include "lite_translator.h"
#include "register_maintainer.h"

namespace berberis {

namespace {
class Riscv64LiteTranslatorExecTest : public ::testing::Test {
 public:
  Riscv64LiteTranslatorExecTest()
      : machine_code_(),
        is_translated_(false),
        state_{},
        translator_(&machine_code_, kStartGuestAddr) {}

  void Run() {
    if (!is_translated_) {
      FinalizeTranslation();
    }
    ScopedExecRegion exec(&machine_code_);
    berberis_RunGeneratedCode(&state_, exec.get());
  }

 private:
  void FinalizeTranslation() {
    is_translated_ = true;
    translator_.as()->Finalize();
  }

  // machine_code_ should precede translator_ for the correct construction.
  MachineCode machine_code_;
  bool is_translated_;

 protected:
  // Upper 16-bits must be zero in a valid address.
  constexpr static GuestAddr kStartGuestAddr = 0x0000aaaabbbbccccULL;
  ThreadState state_;
  LiteTranslator translator_;
};

TEST_F(Riscv64LiteTranslatorExecTest, StoreMappedRegs) {
  state_.cpu.x[1] = 0;

  translator_.as()->Movq(x86_64::Assembler::rax, 33);
  translator_.SetReg(1, x86_64::Assembler::rax);
  EXPECT_TRUE(translator_.gp_maintainer()->IsMapped(1));
  EXPECT_TRUE(translator_.gp_maintainer()->IsModified(1));
  translator_.StoreMappedRegs();
  translator_.as()->Jmp(kEntryExitGeneratedCode);

  Run();
  EXPECT_EQ(state_.cpu.x[1], 33ULL);
}

TEST_F(Riscv64LiteTranslatorExecTest, StoreMappedFpRegs) {
  state_.cpu.f[1] = 0;
  LiteTranslator::FpRegister reg = translator_.AllocTempSimdReg();

  translator_.as()->Movq(x86_64::Assembler::rax, 33);
  translator_.as()->Movq(reg, x86_64::Assembler::rax);
  translator_.NanBoxAndSetFpReg<LiteTranslator::Float64>(1, reg);
  EXPECT_TRUE(translator_.simd_maintainer()->IsMapped(1));
  EXPECT_TRUE(translator_.simd_maintainer()->IsModified(1));
  translator_.StoreMappedRegs();
  translator_.as()->Jmp(kEntryExitGeneratedCode);

  Run();
  EXPECT_EQ(state_.cpu.f[1], 33ULL);
}

}  // namespace

}  // namespace berberis