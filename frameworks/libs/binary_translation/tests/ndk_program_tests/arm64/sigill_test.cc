/*
 * Copyright (C) 2014 The Android Open Source Project
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

#include <cstdio>

#include "scoped_sigaction.h"

namespace {

sigjmp_buf g_recover_arm64;

extern "C" char illegal_instruction_arm64;

void SigillSignalHandlerArm64(int /* sig */, siginfo_t* /* info */, void* ctx) {
  fprintf(stderr, "SIGILL caught\n");
  // Warning: do not use ASSERT, so that we recover with longjump unconditionally.
  // Otherwise we'll be looping executing illegal instruction.
  EXPECT_EQ(static_cast<ucontext*>(ctx)->uc_mcontext.pc,
            reinterpret_cast<unsigned long>(&illegal_instruction_arm64));
  longjmp(g_recover_arm64, 1);
}

TEST(Signal, SigillArm64) {
  struct sigaction sa;
  sa.sa_flags = SA_SIGINFO;
  sigemptyset(&sa.sa_mask);
  sa.sa_sigaction = SigillSignalHandlerArm64;
  ScopedSigaction scoped_sa(SIGILL, &sa);

  if (setjmp(g_recover_arm64) == 0) {
    fprintf(stderr, "Executing invalid ARM instruction\n");
    asm volatile(
        ".align 8\n"
        ".globl illegal_instruction_arm64\n"
        "illegal_instruction_arm64:\n"
        ".4byte 0x0\n");
    fprintf(stderr, "Bug, recover from SIGILL shall come as longjump()\n");
    EXPECT_TRUE(0);
  } else {
    fprintf(stderr, "Recovered, test passed\n");
  }
}

}  // namespace
