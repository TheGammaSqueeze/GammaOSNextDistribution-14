/*
 * Copyright (C) 2016 The Android Open Source Project
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

#include <pthread.h>
#include <sched.h>
#include <sys/mman.h>

#include <cstdint>
#include <cstring>

namespace {

// Flush cache and i-cache
void ClearInsnCache(void* start, void* end) {
  // Only use __builtin___clear_cache with Clang or with GCC >= 4.3.0
  __builtin___clear_cache(static_cast<char*>(start), static_cast<char*>(end));
}

}  // namespace

TEST(RuntimeCodePatching, PatchCodeInCurrentThread) {
  // The following function patches loop back branch at L2 with branch to next insn.
  // To avoid messing with immediates, code to write is taken from L3, and helper
  // to flush insn cache from L5.
  const uint32_t code_image[] = {
      0xe92d41f0,  //   push {r4, r5, r6, r7, r8, lr}
                   // <L1>:
      0xe59f3014,  //   ldr r3, L3
      0xe58f300c,  //   str r3, L2
      0xe28f0008,  //   adr r0, L2
      0xe28f1008,  //   adr r1, L3
      0xe59f4010,  //   ldr r4, L5
      0xe12fff34,  //   blx r4
                   // <L2>:
      0xeafffff8,  //   b L1
                   // <L3>:
      0xeaffffff,  //   b L4
                   // <L4>:
      0xe3a0000b,  //   mov r0, #11
      0xe8bd81f0,  //   pop {r4, r5, r6, r7, r8, pc}
                   // <L5>:
      0xe320f000,  //   nop {0}
  };

  uint32_t* code = reinterpret_cast<uint32_t*>(
      mmap(0, 4096, PROT_READ | PROT_WRITE | PROT_EXEC, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0));
  memcpy(code, code_image, sizeof(code_image));
  code[11] = reinterpret_cast<uint32_t>(ClearInsnCache);
  // ATTENTION: flush insn cache! Otherwise just mmaped page might remain cached with wrong prot!
  ClearInsnCache(code, code + 1024);

  using Func = int32_t (*)();
  int32_t result = (reinterpret_cast<Func>(code))();
  EXPECT_EQ(result, 11);

  munmap(code, 4096);
}

TEST(RuntimeCodePatching, PatchCodeInOtherThread) {
  // The following function writes 1 to address in r0 and loops. The write is
  // needed to notify other threads that we entered the loop. We are going to
  // patch the back branch to exit the loop.
  const uint32_t code_image[] = {
      0xe92d41f0,  //   push {r4, r5, r6, r7, r8, lr}
                   // <L1>:
      0xe3a01001,  //   mov r1, #1
      0xe5801000,  //   str r1, [r0]
                   // <L2>:
      0xeafffffc,  //   b 4          // <L1>
                   // <L4>:
      0xe3a0000b,  //   mov r0, #11  // arbitrary return value
      0xe8bd81f0,  //   pop {r4, r5, r6, r7, r8, pc}
  };

  uint32_t* code = reinterpret_cast<uint32_t*>(
      mmap(0, 4096, PROT_READ | PROT_WRITE | PROT_EXEC, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0));
  memcpy(code, code_image, sizeof(code_image));
  // ATTENTION: flush insn cache! Otherwise just mmaped page might remain cached with wrong prot!
  ClearInsnCache(code, code + 1024);

  volatile int func_thread_started = 0;

  using Func = void* (*)(void*);
  pthread_t func_thread;
  ASSERT_EQ(pthread_create(&func_thread,
                           nullptr,
                           reinterpret_cast<Func>(code),
                           const_cast<int*>(&func_thread_started)),
            0);

  while (!func_thread_started) {
    sched_yield();
  }

  code[3] = 0xeaffffff;  // overwrite loop branch with branch to next insn
  ClearInsnCache(code + 3, code + 4);

  void* result;
  ASSERT_EQ(pthread_join(func_thread, &result), 0);
  EXPECT_EQ(reinterpret_cast<int>(result), 11);

  munmap(code, 4096);
}
