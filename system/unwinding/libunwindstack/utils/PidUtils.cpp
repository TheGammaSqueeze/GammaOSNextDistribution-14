/*
 * Copyright (C) 2022 The Android Open Source Project
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

#include <errno.h>
#include <stdint.h>
#include <stdio.h>
#include <sys/ptrace.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <time.h>
#include <unistd.h>

#include "PidUtils.h"

namespace unwindstack {

static bool Exited(pid_t pid) {
  int status;
  pid_t wait_pid = waitpid(pid, &status, WNOHANG);
  if (wait_pid != pid) {
    return false;
  }

  if (WIFEXITED(status)) {
    fprintf(stderr, "%d died: Process exited with code %d\n", pid, WEXITSTATUS(status));
  } else if (WIFSIGNALED(status)) {
    fprintf(stderr, "%d died: Process exited due to signal %d\n", pid, WTERMSIG(status));
  } else {
    fprintf(stderr, "%d died: Process finished for unknown reason\n", pid);
  }
  return true;
}

bool Quiesce(pid_t pid) {
  siginfo_t si;
  // Wait for up to 10 seconds.
  for (time_t start_time = time(nullptr); time(nullptr) - start_time < 10;) {
    if (ptrace(PTRACE_GETSIGINFO, pid, 0, &si) == 0) {
      return true;
    }
    if (errno != ESRCH) {
      if (errno == EINVAL) {
        // The process is in group-stop state, so try and kick the
        // process out of that state.
        if (ptrace(PTRACE_LISTEN, pid, 0, 0) == -1) {
          // Cannot recover from this, so just pretend it worked and see
          // if we can unwind.
          return true;
        }
      } else {
        perror("ptrace getsiginfo failed");
        return false;
      }
    }
    usleep(5000);
  }
  fprintf(stderr, "%d: Did not quiesce in 10 seconds\n", pid);
  return false;
}

bool Attach(pid_t pid) {
  // Wait up to 45 seconds to attach.
  for (time_t start_time = time(nullptr); time(nullptr) - start_time < 45;) {
    if (ptrace(PTRACE_ATTACH, pid, 0, 0) == 0) {
      break;
    }
    if (errno != ESRCH) {
      perror("Failed to attach");
      return false;
    }
    usleep(5000);
  }

  if (Quiesce(pid)) {
    return true;
  }

  if (ptrace(PTRACE_DETACH, pid, 0, 0) == -1) {
    perror("Failed to detach");
  }
  return false;
}

bool Detach(pid_t pid) {
  if (ptrace(PTRACE_DETACH, pid, 0, 0) == -1) {
    perror("ptrace detach failed");
    return false;
  }
  return true;
}

static constexpr time_t kMaxWaitTimeSeconds = 30;

bool WaitForPidState(pid_t pid, const std::function<PidRunEnum()>& state_check_func) {
  PidRunEnum status = PID_RUN_KEEP_GOING;
  for (time_t start_time = time(nullptr);
       time(nullptr) - start_time < kMaxWaitTimeSeconds && status == PID_RUN_KEEP_GOING;) {
    if (Attach(pid)) {
      status = state_check_func();
      if (status == PID_RUN_PASS) {
        return true;
      }

      if (!Detach(pid)) {
        return false;
      }
    } else if (Exited(pid)) {
      return false;
    }
    usleep(5000);
  }
  if (status == PID_RUN_KEEP_GOING) {
    fprintf(stderr, "Timed out waiting for pid %d to be ready\n", pid);
  }
  return status == PID_RUN_PASS;
}

bool WaitForPidStateAfterAttach(pid_t pid, const std::function<PidRunEnum()>& state_check_func) {
  PidRunEnum status;
  time_t start_time = time(nullptr);
  do {
    status = state_check_func();
    if (status == PID_RUN_PASS) {
      return true;
    }
    if (!Detach(pid)) {
      return false;
    }
    usleep(5000);
  } while (time(nullptr) - start_time < kMaxWaitTimeSeconds && status == PID_RUN_KEEP_GOING &&
           Attach(pid));
  if (status == PID_RUN_KEEP_GOING) {
    fprintf(stderr, "Timed out waiting for pid %d to be ready\n", pid);
  }
  return status == PID_RUN_PASS;
}

}  // namespace unwindstack
