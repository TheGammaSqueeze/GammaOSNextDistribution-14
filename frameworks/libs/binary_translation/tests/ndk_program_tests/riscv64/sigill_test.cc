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
#include <signal.h>
#include <sys/ucontext.h>

#include "berberis/ndk_program_tests/scoped_sigaction.h"

namespace {

sigjmp_buf g_recover_riscv64;

extern "C" char g_illegal_instruction_riscv64;

void SigillSignalHandlerRiscv64(int /* sig */, siginfo_t* /* info */, void* ctx) {
  fprintf(stderr, "SIGILL caught\n");
  // Warning: Do not use ASSERT, so that we recover with longjmp unconditionally.
  // Otherwise we'll be looping executing illegal instruction.
  EXPECT_EQ(static_cast<ucontext*>(ctx)->uc_mcontext.__gregs[REG_PC],
            reinterpret_cast<greg_t>(&g_illegal_instruction_riscv64));
  longjmp(g_recover_riscv64, 1);
}

TEST(Signal, SigillRiscv64) {
  struct sigaction sa;
  sa.sa_flags = SA_SIGINFO;
  sigemptyset(&sa.sa_mask);
  sa.sa_sigaction = SigillSignalHandlerRiscv64;
  ScopedSigaction scoped_sa(SIGILL, &sa);

  if (setjmp(g_recover_riscv64) == 0) {
    fprintf(stderr, "Executing invalid RISC-V instruction\n");
    asm volatile(
        ".align 8\n"
        ".globl g_illegal_instruction_riscv64\n"
        "g_illegal_instruction_riscv64:\n"
        ".4byte 0x0\n");
    fprintf(stderr, "Bug, recover from SIGILL shall come as longjmp()\n");
    FAIL();
  } else {
    fprintf(stderr, "Recovered, test passed\n");
  }
}

}  // namespace
