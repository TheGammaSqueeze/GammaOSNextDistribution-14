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

#pragma once

#include <signal.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>

#include <functional>

#include <gtest/gtest.h>

#include "PidUtils.h"
#include "TestUtils.h"

namespace unwindstack {

class ForkTest : public ::testing::Test {
 protected:
  void SetForkFunc(std::function<void()> fork_func) { fork_func_ = fork_func; }

  void Fork(std::function<void()> fork_func) {
    SetForkFunc(fork_func);
    Fork();
  }

  void Fork() {
    for (size_t i = 0; i < kMaxRetries; i++) {
      if ((pid_ = fork()) == 0) {
        fork_func_();
        _exit(1);
      }
      ASSERT_NE(-1, pid_);
      if (Attach(pid_)) {
        return;
      }
      kill(pid_, SIGKILL);
      waitpid(pid_, nullptr, 0);
      pid_ = -1;
    }
    FAIL() << "Unable to fork and attach to process.";
  }

  void ForkAndWaitForPidState(const std::function<PidRunEnum()>& state_check_func) {
    for (size_t i = 0; i < kMaxRetries; i++) {
      ASSERT_NO_FATAL_FAILURE(Fork());

      if (WaitForPidStateAfterAttach(pid_, state_check_func)) {
        return;
      }
      kill(pid_, SIGKILL);
      waitpid(pid_, nullptr, 0);
      pid_ = -1;
    }
    FAIL() << "Process never got to expected state.";
  }

  void TearDown() override {
    if (pid_ == -1) {
      return;
    }
    Detach(pid_);
    kill(pid_, SIGKILL);
    waitpid(pid_, nullptr, 0);
  }

  pid_t pid_ = -1;
  bool should_detach_ = true;
  // Default to a run forever function.
  std::function<void()> fork_func_ = []() {
    while (true)
      ;
  };

  static constexpr size_t kMaxRetries = 3;
};

}  // namespace unwindstack
