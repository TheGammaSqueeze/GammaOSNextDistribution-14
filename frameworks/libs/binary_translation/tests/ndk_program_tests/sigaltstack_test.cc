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

#include <signal.h>
#include <string.h>
#include <sys/mman.h>

namespace {

// Verify that nested signal handlers on an alternate stack do not interfere with others' stack
// variables.

constexpr size_t kStackSize = 16 * 4096;  // from bionic/tests/pthread_test.cpp, fails if less.
void* g_ss = nullptr;

constexpr size_t kAccessSize = 4096;
void* g_access_1;
void* g_access_2;

void HandleSignalOnAccess(int, siginfo_t*, void*) {
  char ss_var[32];

  // Check handler runs on alternate stack.
  const char* ss_start = static_cast<const char*>(g_ss);
  EXPECT_GE(ss_var, ss_start);
  EXPECT_LT(ss_var, ss_start + kStackSize);

  if (g_access_2 == nullptr) {
    // First signal.
    // Initialize stack.
    strncpy(ss_var, "firstfirstfirst", sizeof(ss_var));

    // Force second signal.
    // Because of SA_NODEFER, it should be a nested signal handler call.
    g_access_2 = mmap(nullptr, kAccessSize, PROT_NONE, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
    ASSERT_NE(g_access_2, MAP_FAILED);
    *static_cast<int*>(g_access_2) = 2;

    // Check second signal didn't screw the stack up.
    ASSERT_EQ(strncmp(ss_var, "firstfirstfirst", sizeof(ss_var)), 0);

    // Fix first signal.
    ASSERT_EQ(mprotect(g_access_1, kAccessSize, PROT_READ | PROT_WRITE), 0);
  } else {
    // Second signal.
    // Initialize stack with something different.
    strncpy(ss_var, "secondsecondsecond", sizeof(ss_var));

    // Fix second signal.
    ASSERT_EQ(mprotect(g_access_2, kAccessSize, PROT_READ | PROT_WRITE), 0);
  }
}

TEST(Signal, Sigaltstack) {
  // Set signal handler for failed access.
  // Use alternate stack if available.
  // Allow nested handler call.
  struct sigaction sa {};
  sa.sa_sigaction = HandleSignalOnAccess;
  sa.sa_flags = SA_SIGINFO | SA_NODEFER | SA_ONSTACK;
  struct sigaction old_sa {};
  ASSERT_EQ(sigaction(SIGSEGV, &sa, &old_sa), 0);

  // Install alternate signal stack.
  g_ss = mmap(nullptr, kStackSize, PROT_READ | PROT_WRITE, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
  ASSERT_NE(g_ss, MAP_FAILED);
  stack_t ss{};
  ss.ss_sp = g_ss;
  ss.ss_size = kStackSize;
  stack_t old_ss{};
  ASSERT_EQ(sigaltstack(&ss, &old_ss), 0);

  g_access_2 = nullptr;

  // Force first signal.
  g_access_1 = mmap(nullptr, kAccessSize, PROT_NONE, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
  ASSERT_NE(g_access_1, MAP_FAILED);
  *static_cast<int*>(g_access_1) = 1;

  ASSERT_EQ(sigaction(SIGSEGV, &old_sa, nullptr), 0);
  ASSERT_EQ(sigaltstack(&old_ss, nullptr), 0);
  ASSERT_EQ(munmap(g_ss, kStackSize), 0);
  ASSERT_EQ(munmap(g_access_1, kAccessSize), 0);
  ASSERT_EQ(munmap(g_access_2, kAccessSize), 0);
}

}  // namespace
