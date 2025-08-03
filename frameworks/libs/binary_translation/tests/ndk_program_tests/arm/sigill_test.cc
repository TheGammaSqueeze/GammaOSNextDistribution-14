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

sigjmp_buf g_recover_arm;

void SigillSignalHandlerArm(int /* sig */, siginfo_t* /* info */, void* ctx) {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wundefined-internal"
  extern const uint8_t illegal_instruction_arm[] asm(".L.arm_code.illegal_instruction_arm")
      __attribute__((visibility("hidden")));
#pragma clang diagnostic pop
  fprintf(stderr, "SIGILL caught\n");
  ASSERT_EQ(static_cast<ucontext*>(ctx)->uc_mcontext.arm_pc,
            reinterpret_cast<unsigned long>(illegal_instruction_arm));
  longjmp(g_recover_arm, 1);
}

TEST(Signal, SigillArm) {
  struct sigaction sa;
  sa.sa_flags = SA_SIGINFO;
  sigemptyset(&sa.sa_mask);
  sa.sa_sigaction = SigillSignalHandlerArm;
  ScopedSigaction scoped_sa(SIGILL, &sa);

  if (setjmp(g_recover_arm) == 0) {
    fprintf(stderr, "Executing invalid ARM instruction\n");
#ifdef __THUMBEL__
    extern const uint8_t illegal_instruction_arm[] asm(".L.arm_code.illegal_instruction_arm")
        __attribute__((visibility("hidden")));
    asm volatile(
        "bx %[illegal_instruction_addr]\n"
        ".p2align 2\n"
        ".code 32\n"
        ".L.arm_code.illegal_instruction_arm:\n"
        ".4byte 0xe7fedeff\n"
        ".code 16\n"
        :
        : [illegal_instruction_addr] "r"(illegal_instruction_arm));
#else
    asm volatile(
        ".L.arm_code.illegal_instruction_arm:\n"
        ".4byte 0xe7fedeff");
#endif
    fprintf(stderr, "Bug, recover from SIGILL shall come as longjump()\n");
    EXPECT_TRUE(0);
  } else {
    fprintf(stderr, "Recovered, test passed\n");
  }
}

sigjmp_buf g_recover_thumb;

void SigillSignalHandlerThumb(int /* sig */, siginfo_t* /* info */, void* ctx) {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wundefined-internal"
  extern const uint8_t illegal_instruction_thumb[] asm(".L.arm_code.illegal_instruction_thumb")
      __attribute__((visibility("hidden")));
#pragma clang diagnostic pop
  fprintf(stderr, "SIGILL caught\n");
  ASSERT_EQ(static_cast<ucontext*>(ctx)->uc_mcontext.arm_pc,
            reinterpret_cast<unsigned long>(illegal_instruction_thumb));
  longjmp(g_recover_thumb, 1);
}

TEST(Signal, SigillThumb) {
  struct sigaction sa;
  sa.sa_flags = SA_SIGINFO;
  sigemptyset(&sa.sa_mask);
  sa.sa_sigaction = SigillSignalHandlerThumb;
  ScopedSigaction scoped_sa(SIGILL, &sa);

  if (setjmp(g_recover_thumb) == 0) {
    fprintf(stderr, "Executing invalid Thumb instruction\n");
#ifdef __THUMBEL__
    asm volatile(
        ".L.arm_code.illegal_instruction_thumb:\n"
        ".2byte 0xdeef");
#else
    extern const uint8_t illegal_instruction_thumb[] asm(".L.arm_code.illegal_instruction_thumb")
        __attribute__((visibility("hidden")));
    asm volatile(
        "bx %[illegal_instruction_addr]\n"
        ".code 16\n"
        ".L.arm_code.illegal_instruction_thumb:\n"
        ".2byte 0xdeef\n"
        ".p2align 2\n"
        ".code 32\n"
        :
        : [illegal_instruction_addr] "r"(illegal_instruction_thumb + 1));
#endif
    fprintf(stderr, "Bug, recover from SIGILL shall come as longjump()\n");
    EXPECT_TRUE(0);
  } else {
    fprintf(stderr, "Recovered, test passed\n");
  }
}

}  // namespace
