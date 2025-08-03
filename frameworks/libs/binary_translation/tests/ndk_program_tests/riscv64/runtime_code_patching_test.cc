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

#include <sys/mman.h>
#include <unistd.h>

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
// By default the Android .text section, including this snippet, is not writeable. We ensure it is
// position independent, so that we can copy it to a writable page, where it'll actually work. The
// only position dependent address of ClearInsnCache callback must be provided in a0.
asm(R"(
.globl PatchCodeInCurrentThreadHelper_begin
PatchCodeInCurrentThreadHelper_begin:
  // Save return address and ClearInsnCache callback.
  addi sp, sp, -16
  sd ra, 0(sp)
  mv t0, a0

  // Facilitate caching of the result setting code.
  li t1, 1000
1:
  jal PatchCodeInCurrentThreadHelper_assign_result
  addi t1, t1, -1
  bnez t1, 1b

  // Overwrite bad-clobber with nop.
  lw t1, PatchCodeInCurrentThreadHelper_nop
  lla a0, PatchCodeInCurrentThreadHelper_bad_clobber
  sw t1, 0(a0)
  // Call ClearInsnCache. a0 is pointing at the overwritten instruction.
  addi a1, a0, 4
  jalr t0

  // Final result assignment.
  jal PatchCodeInCurrentThreadHelper_assign_result

  ld ra, 0(sp)
  addi sp, sp, 16
  ret

.option push
.option norvc  // Prevent instruction compression to ensure that both loads are 4 bytes.
PatchCodeInCurrentThreadHelper_assign_result:
  li a0, 42
PatchCodeInCurrentThreadHelper_bad_clobber:
  li a0, 21
  ret

PatchCodeInCurrentThreadHelper_nop:
  nop
.option pop

.globl PatchCodeInCurrentThreadHelper_end
PatchCodeInCurrentThreadHelper_end:
)");

TEST(RuntimeCodePatching, PatchCodeInCurrentThread) {
  const long kPageSize = sysconf(_SC_PAGESIZE);
  uint32_t* code = reinterpret_cast<uint32_t*>(
      mmap(0, kPageSize, PROT_READ | PROT_WRITE | PROT_EXEC, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0));
  memcpy(code,
         &PatchCodeInCurrentThreadHelper_begin,
         &PatchCodeInCurrentThreadHelper_end - &PatchCodeInCurrentThreadHelper_begin);
  // Flush the instruction cache to ensure that the page is not cached with the wrong protection.
  ClearInsnCache(code, code + 1024);

  auto Func = reinterpret_cast<uint64_t (*)(void*)>(code);
  uint64_t result = Func(reinterpret_cast<void*>(ClearInsnCache));
  EXPECT_EQ(result, 42U);

  munmap(code, kPageSize);
}
