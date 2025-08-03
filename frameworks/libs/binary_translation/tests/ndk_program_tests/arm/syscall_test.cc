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

#include <sys/mman.h>
#include <sys/syscall.h>

TEST(Syscall, SchedSetAffinity) {
  int mask = 1;
  ASSERT_EQ(syscall(SYS_sched_setaffinity, 0, 4, &mask), 0);
}

TEST(Syscall, CacheFlush) {
  // This test work as simple JIT engine with code patching.
  // Workflow is like this:
  // - create code
  // - flush cache
  // - runs code
  // - modify code
  // - flush cache
  // - run code
  static const int kCacheFlushSyscall = 0xf0002;
  static const uint32_t code_template[] = {
      0xe3000001,  // movw r0, #0x1
      0xe12fff1e,  // bx lr
  };
  uint8_t* start = reinterpret_cast<uint8_t*>(mmap(0,
                                                   sizeof(code_template),
                                                   PROT_READ | PROT_WRITE | PROT_EXEC,
                                                   MAP_PRIVATE | MAP_ANONYMOUS,
                                                   -1,
                                                   0));
  ASSERT_NE(start, MAP_FAILED);
  memcpy(start, code_template, sizeof(code_template));
  uint8_t* end = start + sizeof(code_template);
  ASSERT_EQ(syscall(kCacheFlushSyscall, start, end, 0), 0);
  using TestFunc = int (*)(void);
  TestFunc func = reinterpret_cast<TestFunc>(start);
  ASSERT_EQ(func(), 0x1);
  *reinterpret_cast<uint32_t*>(start) = 0xe3000011;  // movw r0, #0x11
  ASSERT_EQ(syscall(kCacheFlushSyscall, start, end, 0), 0);
  ASSERT_EQ(func(), 0x11);
  munmap(start, sizeof(code_template));
}

TEST(Syscall, OAbiDisabled) {
  int pipefd[2];
  ASSERT_EQ(pipe(pipefd), 0);
  char buf[4] = "Tst";
  register uint32_t r0 asm("r0") = pipefd[1];
  register uint32_t r1 asm("r1") = reinterpret_cast<uint32_t>(buf);
  register uint32_t r2 asm("r2") = sizeof(buf);
  register uint32_t r7 asm("r7") = SYS_write;
  // Call "write" syscall using EABI, but instrument it to be interpreted as "read" if executed
  // on system with OABI syscall calling convention or CONFIG_OABI_COMPAT mode enabled.
  //
  // On kernels with CONFIG_OABI_COMPAT immediate from "swi" instruction would be used, attempt to
  // use "read" syscall (based on .imm value of "swi"instruction) would happen - and test would
  // fail.
  //
  // On kernels without CONFIG_OABI_COMPAT value from r7 would be used and test would succeed.
  asm("swi %1" : "=r"(r0) : "i"(SYS_read), "r"(r0), "r"(r1), "r"(r2), "r"(r7));
  uint32_t result = r0;
  ASSERT_EQ(result, sizeof(buf));
  close(pipefd[0]);
  close(pipefd[1]);
}
