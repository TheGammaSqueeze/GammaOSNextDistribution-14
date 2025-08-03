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
#include <sys/ptrace.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>

namespace {

class ScopedSigKill {
 public:
  ScopedSigKill(pid_t p) : pid_(p) {}
  ~ScopedSigKill() { kill(pid_, SIGKILL); }

 private:
  pid_t pid_;
};

pid_t PrepareTracee() {
  pid_t child_pid = fork();
  if (child_pid == 0) {
    // Child.
    ptrace(PTRACE_TRACEME, 0, 0, 0);
    raise(SIGSTOP);
    exit(0);
  }
  // Parent waits for child to stop.
  int status;
  EXPECT_GE(waitpid(child_pid, &status, 0), 0);
  EXPECT_TRUE(WIFSTOPPED(status));
  EXPECT_EQ(WSTOPSIG(status), SIGSTOP);
  if (testing::Test::HasFatalFailure()) {
    kill(child_pid, SIGKILL);
  }
  return child_pid;
}

}  // namespace

TEST(Ptrace, PeekPokeData) {
  long data = 0xfeed;
  pid_t child_pid;
  ASSERT_NO_FATAL_FAILURE({ child_pid = PrepareTracee(); });
  // Asserts may exit early, make sure we always kill the child.
  ScopedSigKill scoped_sig_kill(child_pid);

  // Clobber data in parent.
  data = 0xdead;
  // Child still has the original value.
  ASSERT_EQ(ptrace(PTRACE_PEEKDATA, child_pid, &data, 0), 0xfeed);
  // Update the value.
  ASSERT_EQ(ptrace(PTRACE_POKEDATA, child_pid, &data, 0xabcd), 0);
  // Observe the updated value.
  ASSERT_EQ(ptrace(PTRACE_PEEKDATA, child_pid, &data, 0), 0xabcd);
}

TEST(Ptrace, SetOptions) {
  pid_t child_pid;
  ASSERT_NO_FATAL_FAILURE({ child_pid = PrepareTracee(); });
  // Asserts may exit early, make sure we always kill the child.
  ScopedSigKill scoped_sig_kill(child_pid);

  ASSERT_EQ(ptrace(PTRACE_SETOPTIONS, child_pid, nullptr, 0), 0);
}
