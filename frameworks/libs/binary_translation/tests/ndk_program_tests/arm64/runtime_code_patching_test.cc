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

#include <sys/mman.h>

#include <cstdint>
#include <cstring>

namespace {

// Flush cache and i-cache
extern "C" void ClearInsnCache(void* start, void* end) {
  // Only use __builtin___clear_cache with Clang or with GCC >= 4.3.0
  __builtin___clear_cache(static_cast<char*>(start), static_cast<char*>(end));
}

}  // namespace

extern "C" char PatchCodeInCurrentThreadHelper_begin;
extern "C" char PatchCodeInCurrentThreadHelper_end;
// By default android .text section including this snippet is not executable. We
// ensure it is position independent, so that we can copy it to a writable page,
// where it'll actually work. The only position dependent address of
// ClearInsnCache callback must be provided in r0.
asm(R"(
.globl PatchCodeInCurrentThreadHelper_begin
PatchCodeInCurrentThreadHelper_begin:
  // Save link register and ClearInsnCache callback.
  str x30, [sp, -16]!
  mov x3, x0

  // Facilitate caching of the result setting code.
  mov x1, #1000
PatchCodeInCurrentThreadHelper_warmup_loop:
  bl PatchCodeInCurrentThreadHelper_assign_result
  subs x1, x1, #1
  bne PatchCodeInCurrentThreadHelper_warmup_loop

  // Overwrite bad-clobber with nop.
  ldr w1, PatchCodeInCurrentThreadHelper_nop
  adr x0, PatchCodeInCurrentThreadHelper_bad_clobber
  str w1, [x0]
  // Call ClearInsnCache. x0 is pointing at the overwritten instruction.
  add x1, x0, 4
  blr x3

  // Final result assignment.
  bl PatchCodeInCurrentThreadHelper_assign_result

  ldr x30, [sp], 16
  ret

PatchCodeInCurrentThreadHelper_assign_result:
  mov x0, 42
PatchCodeInCurrentThreadHelper_bad_clobber:
  mov x0, 21
  ret

PatchCodeInCurrentThreadHelper_nop:
  nop

.globl PatchCodeInCurrentThreadHelper_end
PatchCodeInCurrentThreadHelper_end:
)");

TEST(RuntimeCodePatching, PatchCodeInCurrentThread) {
  uint32_t* code = reinterpret_cast<uint32_t*>(
      mmap(0, 4096, PROT_READ | PROT_WRITE | PROT_EXEC, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0));
  memcpy(code,
         &PatchCodeInCurrentThreadHelper_begin,
         &PatchCodeInCurrentThreadHelper_end - &PatchCodeInCurrentThreadHelper_begin);
  // ATTENTION: flush insn cache! Otherwise just mmaped page might remain cached with wrong prot!
  ClearInsnCache(code, code + 1024);

  auto Func = reinterpret_cast<uint64_t (*)(void*)>(code);
  uint64_t result = Func(reinterpret_cast<void*>(ClearInsnCache));
  EXPECT_EQ(result, 42U);

  munmap(code, 4096);
}
