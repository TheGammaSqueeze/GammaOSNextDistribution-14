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

#include <setjmp.h>
#include <signal.h>

#include "berberis/ndk_program_tests/scoped_sigaction.h"

namespace {

void FuncWithLongJump(jmp_buf buf) {
  longjmp(buf, 1);
}

TEST(SetJmp, Jmp) {
  jmp_buf buf;
  int value = setjmp(buf);
  if (value == 0) {
    FuncWithLongJump(buf);
    FAIL();
  }
  ASSERT_EQ(value, 1);
}

jmp_buf g_jmp_buf;
bool g_wrapper_called;

void LongjmpHandler(int) {
  longjmp(g_jmp_buf, 1);
}

void WrapperHandler(int) {
  g_wrapper_called = true;
  struct sigaction sa {};
  sigemptyset(&sa.sa_mask);
  sa.sa_handler = LongjmpHandler;
  ScopedSigaction scoped_sa(SIGXCPU, &sa);

  int value = setjmp(g_jmp_buf);
  if (value == 0) {
    raise(SIGXCPU);
    FAIL();
  }
  ASSERT_EQ(value, 1);
}

TEST(SetJmp, JmpFromSignalHandler) {
  g_wrapper_called = false;
  // Before we do setjmp/longjmp we create a nested execution by invoking a wrapper handler. This
  // way we ensure that nested executions are handled correctly.
  struct sigaction sa {};
  sigemptyset(&sa.sa_mask);
  sa.sa_handler = WrapperHandler;
  ScopedSigaction scoped_sa(SIGPWR, &sa);
  raise(SIGPWR);
  ASSERT_TRUE(g_wrapper_called);
}

}  // namespace
