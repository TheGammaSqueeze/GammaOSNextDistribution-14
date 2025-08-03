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

#include <sys/mman.h>
#include <unistd.h>  // sysconf(_SC_PAGESIZE)

#include <cstdint>
#include <cstdio>
#include <cstring>

// Make sure compiler doesn't recognize undefined behavior and doesn't optimize out call to nullptr.
volatile void* g_null_addr = nullptr;

namespace {

TEST(HandleNotExecutable, NotExecutable) {
  uint32_t* code = reinterpret_cast<uint32_t*>(mmap(0,
                                                    sysconf(_SC_PAGESIZE),
                                                    PROT_READ | PROT_WRITE,  // No PROT_EXEC!
                                                    MAP_PRIVATE | MAP_ANONYMOUS,
                                                    -1,
                                                    0));
  using Func = void (*)();
  ASSERT_EXIT((reinterpret_cast<Func>(code))(), testing::KilledBySignal(SIGSEGV), "");
  munmap(code, sysconf(_SC_PAGESIZE));
}

TEST(HandleNotExecutable, PcLessThan4096) {
  using Func = void (*)();
  ASSERT_EXIT((reinterpret_cast<Func>(const_cast<void*>(g_null_addr)))(),
              testing::KilledBySignal(SIGSEGV),
              "");
  ASSERT_EXIT((reinterpret_cast<Func>(4095))(), testing::KilledBySignal(SIGSEGV), "");
}

// Add some valid code to the end of the first page and graceful failure rescue at the beginning of
// the second page.
constexpr uint32_t kPageCrossingCode[] = {
    //
    // First page
    //

    // push {lr}
    //
    // We may need lr for graceful return if SIGSEGV doesn't happen.
    0xe52de004,
    // blx r0
    //
    // The only way to check that this was executed (i.e. SIGSEGV didn't happen too early) is to
    // print something to stderr. Call FirstPageExecutionHelper for this.
    0xe12fff30,
    // nop
    //
    // Make sure we cross pages without jumps (i.e. we don't
    // return from blx directly to the second page).
    0xe320f000,

    //
    // Second page
    //

    // pop {pc}
    //
    // If SIGSEGV doesn't happen, make sure we return cleanly.
    0xe49df004,
};

constexpr size_t kFirstPageInsnNum = 3;

void FirstPageExecutionHelper() {
  fprintf(stderr, "First page has executed");
}

TEST(HandleNotExecutable, ExecutableToNotExecutablePageCrossing) {
  const long kPageSize = sysconf(_SC_PAGESIZE);
  // Allocate two executable pages.
  uint32_t* first_page = reinterpret_cast<uint32_t*>(mmap(
      0, kPageSize * 2, PROT_READ | PROT_WRITE | PROT_EXEC, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0));

  uint32_t* second_page = first_page + (kPageSize / sizeof(uint32_t));
  // Make second page nonexecutable.
  mprotect(second_page, kPageSize, PROT_READ | PROT_WRITE);

  uint32_t* start_addr = second_page - kFirstPageInsnNum;
  memcpy(start_addr, kPageCrossingCode, sizeof(kPageCrossingCode));

  using Func = void (*)(void (*)());
  ASSERT_EXIT((reinterpret_cast<Func>(start_addr))(&FirstPageExecutionHelper),
              testing::KilledBySignal(SIGSEGV),
              "First page has executed");

  munmap(first_page, kPageSize * 2);
}

}  // namespace
