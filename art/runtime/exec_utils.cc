/*
 * Copyright (C) 2011 The Android Open Source Project
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

#include "exec_utils.h"

#include <poll.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>

#ifdef __BIONIC__
#include <sys/pidfd.h>
#endif

#include <chrono>
#include <climits>
#include <condition_variable>
#include <cstdint>
#include <cstring>
#include <ctime>
#include <mutex>
#include <optional>
#include <string>
#include <string_view>
#include <thread>
#include <vector>

#include "android-base/file.h"
#include "android-base/parseint.h"
#include "android-base/scopeguard.h"
#include "android-base/stringprintf.h"
#include "android-base/strings.h"
#include "android-base/unique_fd.h"
#include "base/macros.h"
#include "base/utils.h"
#include "runtime.h"

namespace art HIDDEN {

namespace {

using ::android::base::ParseInt;
using ::android::base::ReadFileToString;
using ::android::base::StringPrintf;
using ::android::base::unique_fd;

std::string ToCommandLine(const std::vector<std::string>& args) {
  return android::base::Join(args, ' ');
}

// Fork and execute a command specified in a subprocess.
// If there is a runtime (Runtime::Current != nullptr) then the subprocess is created with the
// same environment that existed when the runtime was started.
// Returns the process id of the child process on success, -1 otherwise.
pid_t ExecWithoutWait(const std::vector<std::string>& arg_vector, std::string* error_msg) {
  // Convert the args to char pointers.
  const char* program = arg_vector[0].c_str();
  std::vector<char*> args;
  args.reserve(arg_vector.size() + 1);
  for (const auto& arg : arg_vector) {
    args.push_back(const_cast<char*>(arg.c_str()));
  }
  args.push_back(nullptr);

  // fork and exec
  pid_t pid = fork();
  if (pid == 0) {
    // no allocation allowed between fork and exec

    // change process groups, so we don't get reaped by ProcessManager
    setpgid(0, 0);

    // (b/30160149): protect subprocesses from modifications to LD_LIBRARY_PATH, etc.
    // Use the snapshot of the environment from the time the runtime was created.
    char** envp = (Runtime::Current() == nullptr) ? nullptr : Runtime::Current()->GetEnvSnapshot();
    if (envp == nullptr) {
      execv(program, &args[0]);
    } else {
      execve(program, &args[0], envp);
    }
    // This should be regarded as a crash rather than a normal return.
    PLOG(FATAL) << "Failed to execute (" << ToCommandLine(arg_vector) << ")";
    UNREACHABLE();
  } else if (pid == -1) {
    *error_msg = StringPrintf("Failed to execute (%s) because fork failed: %s",
                              ToCommandLine(arg_vector).c_str(),
                              strerror(errno));
    return -1;
  } else {
    return pid;
  }
}

ExecResult WaitChild(pid_t pid,
                     const std::vector<std::string>& arg_vector,
                     bool no_wait,
                     std::string* error_msg) {
  siginfo_t info;
  // WNOWAIT leaves the child in a waitable state. The call is still blocking.
  int options = WEXITED | (no_wait ? WNOWAIT : 0);
  if (TEMP_FAILURE_RETRY(waitid(P_PID, pid, &info, options)) != 0) {
    *error_msg = StringPrintf("waitid failed for (%s) pid %d: %s",
                              ToCommandLine(arg_vector).c_str(),
                              pid,
                              strerror(errno));
    return {.status = ExecResult::kUnknown};
  }
  if (info.si_pid != pid) {
    *error_msg = StringPrintf("waitid failed for (%s): wanted pid %d, got %d: %s",
                              ToCommandLine(arg_vector).c_str(),
                              pid,
                              info.si_pid,
                              strerror(errno));
    return {.status = ExecResult::kUnknown};
  }
  if (info.si_code != CLD_EXITED) {
    *error_msg =
        StringPrintf("Failed to execute (%s) because the child process is terminated by signal %d",
                     ToCommandLine(arg_vector).c_str(),
                     info.si_status);
    return {.status = ExecResult::kSignaled, .signal = info.si_status};
  }
  return {.status = ExecResult::kExited, .exit_code = info.si_status};
}

// A fallback implementation of `WaitChildWithTimeout` that creates a thread to wait instead of
// relying on `pidfd_open`.
ExecResult WaitChildWithTimeoutFallback(pid_t pid,
                                        const std::vector<std::string>& arg_vector,
                                        int timeout_ms,
                                        std::string* error_msg) {
  bool child_exited = false;
  bool timed_out = false;
  std::condition_variable cv;
  std::mutex m;

  std::thread wait_thread([&]() {
    std::unique_lock<std::mutex> lock(m);
    if (!cv.wait_for(lock, std::chrono::milliseconds(timeout_ms), [&] { return child_exited; })) {
      timed_out = true;
      kill(pid, SIGKILL);
    }
  });

  ExecResult result = WaitChild(pid, arg_vector, /*no_wait=*/true, error_msg);

  {
    std::unique_lock<std::mutex> lock(m);
    child_exited = true;
  }
  cv.notify_all();
  wait_thread.join();

  // The timeout error should have a higher priority than any other error.
  if (timed_out) {
    *error_msg =
        StringPrintf("Failed to execute (%s) because the child process timed out after %dms",
                     ToCommandLine(arg_vector).c_str(),
                     timeout_ms);
    return ExecResult{.status = ExecResult::kTimedOut};
  }

  return result;
}

// Waits for the child process to finish and leaves the child in a waitable state.
ExecResult WaitChildWithTimeout(pid_t pid,
                                unique_fd pidfd,
                                const std::vector<std::string>& arg_vector,
                                int timeout_ms,
                                std::string* error_msg) {
  auto cleanup = android::base::make_scope_guard([&]() {
    kill(pid, SIGKILL);
    std::string ignored_error_msg;
    WaitChild(pid, arg_vector, /*no_wait=*/true, &ignored_error_msg);
  });

  struct pollfd pfd;
  pfd.fd = pidfd.get();
  pfd.events = POLLIN;
  int poll_ret = TEMP_FAILURE_RETRY(poll(&pfd, /*nfds=*/1, timeout_ms));

  pidfd.reset();

  if (poll_ret < 0) {
    *error_msg = StringPrintf("poll failed for pid %d: %s", pid, strerror(errno));
    return {.status = ExecResult::kUnknown};
  }
  if (poll_ret == 0) {
    *error_msg =
        StringPrintf("Failed to execute (%s) because the child process timed out after %dms",
                     ToCommandLine(arg_vector).c_str(),
                     timeout_ms);
    return {.status = ExecResult::kTimedOut};
  }

  cleanup.Disable();
  return WaitChild(pid, arg_vector, /*no_wait=*/true, error_msg);
}

bool ParseProcStat(const std::string& stat_content,
                   int64_t uptime_ms,
                   int64_t ticks_per_sec,
                   /*out*/ ProcessStat* stat) {
  size_t pos = stat_content.rfind(") ");
  if (pos == std::string::npos) {
    return false;
  }
  std::vector<std::string> stat_fields;
  // Skip the first two fields. The second field is the parenthesized process filename, which can
  // contain anything, including spaces.
  Split(std::string_view(stat_content).substr(pos + 2), ' ', &stat_fields);
  constexpr int kSkippedFields = 2;
  int64_t utime, stime, cutime, cstime, starttime;
  if (stat_fields.size() < 22 - kSkippedFields ||
      !ParseInt(stat_fields[13 - kSkippedFields], &utime) ||
      !ParseInt(stat_fields[14 - kSkippedFields], &stime) ||
      !ParseInt(stat_fields[15 - kSkippedFields], &cutime) ||
      !ParseInt(stat_fields[16 - kSkippedFields], &cstime) ||
      !ParseInt(stat_fields[21 - kSkippedFields], &starttime)) {
    return false;
  }
  if (starttime == 0) {
    // The start time is the time the process started after system boot, so it's not supposed to be
    // zero unless the process is `init`.
    return false;
  }
  stat->cpu_time_ms = (utime + stime + cutime + cstime) * 1000 / ticks_per_sec;
  stat->wall_time_ms = uptime_ms - starttime * 1000 / ticks_per_sec;
  return true;
}

}  // namespace

int ExecUtils::ExecAndReturnCode(const std::vector<std::string>& arg_vector,
                                 std::string* error_msg) const {
  return ExecAndReturnResult(arg_vector, /*timeout_sec=*/-1, error_msg).exit_code;
}

ExecResult ExecUtils::ExecAndReturnResult(const std::vector<std::string>& arg_vector,
                                          int timeout_sec,
                                          std::string* error_msg) const {
  return ExecAndReturnResult(arg_vector, timeout_sec, ExecCallbacks(), /*stat=*/nullptr, error_msg);
}

ExecResult ExecUtils::ExecAndReturnResult(const std::vector<std::string>& arg_vector,
                                          int timeout_sec,
                                          const ExecCallbacks& callbacks,
                                          /*out*/ ProcessStat* stat,
                                          /*out*/ std::string* error_msg) const {
  if (timeout_sec > INT_MAX / 1000) {
    *error_msg = "Timeout too large";
    return {.status = ExecResult::kStartFailed};
  }

  // Start subprocess.
  pid_t pid = ExecWithoutWait(arg_vector, error_msg);
  if (pid == -1) {
    return {.status = ExecResult::kStartFailed};
  }

  callbacks.on_start(pid);

  // Wait for subprocess to finish.
  ExecResult result;
  if (timeout_sec >= 0) {
    unique_fd pidfd = PidfdOpen(pid);
    if (pidfd.get() >= 0) {
      result =
          WaitChildWithTimeout(pid, std::move(pidfd), arg_vector, timeout_sec * 1000, error_msg);
    } else {
      LOG(DEBUG) << StringPrintf(
          "pidfd_open failed for pid %d: %s, falling back", pid, strerror(errno));
      result = WaitChildWithTimeoutFallback(pid, arg_vector, timeout_sec * 1000, error_msg);
    }
  } else {
    result = WaitChild(pid, arg_vector, /*no_wait=*/true, error_msg);
  }

  if (stat != nullptr) {
    std::string local_error_msg;
    if (!GetStat(pid, stat, &local_error_msg)) {
      LOG(ERROR) << "Failed to get process stat: " << local_error_msg;
    }
  }

  callbacks.on_end(pid);

  std::string local_error_msg;
  // TODO(jiakaiz): Use better logic to detect waitid failure.
  if (WaitChild(pid, arg_vector, /*no_wait=*/false, &local_error_msg).status ==
      ExecResult::kUnknown) {
    LOG(ERROR) << "Failed to clean up child process '" << arg_vector[0] << "': " << local_error_msg;
  }

  return result;
}

bool ExecUtils::Exec(const std::vector<std::string>& arg_vector, std::string* error_msg) const {
  int status = ExecAndReturnCode(arg_vector, error_msg);
  if (status < 0) {
    // Internal error. The error message is already set.
    return false;
  }
  if (status > 0) {
    *error_msg =
        StringPrintf("Failed to execute (%s) because the child process returns non-zero exit code",
                     ToCommandLine(arg_vector).c_str());
    return false;
  }
  return true;
}

unique_fd ExecUtils::PidfdOpen(pid_t pid) const {
#ifdef __BIONIC__
  return unique_fd(pidfd_open(pid, /*flags=*/0));
#else
  // There is no glibc wrapper for pidfd_open.
#ifndef SYS_pidfd_open
  constexpr int SYS_pidfd_open = 434;
#endif
  return unique_fd(syscall(SYS_pidfd_open, pid, /*flags=*/0));
#endif
}

std::string ExecUtils::GetProcStat(pid_t pid) const {
  std::string stat_content;
  if (!ReadFileToString(StringPrintf("/proc/%d/stat", pid), &stat_content)) {
    stat_content = "";
  }
  return stat_content;
}

std::optional<int64_t> ExecUtils::GetUptimeMs(std::string* error_msg) const {
  timespec t;
  if (clock_gettime(CLOCK_MONOTONIC, &t) != 0) {
    *error_msg = ART_FORMAT("Failed to get uptime: {}", strerror(errno));
    return std::nullopt;
  }
  return t.tv_sec * 1000 + t.tv_nsec / 1000000;
}

int64_t ExecUtils::GetTicksPerSec() const { return sysconf(_SC_CLK_TCK); }

bool ExecUtils::GetStat(pid_t pid,
                        /*out*/ ProcessStat* stat,
                        /*out*/ std::string* error_msg) const {
  std::optional<int64_t> uptime_ms = GetUptimeMs(error_msg);
  if (!uptime_ms.has_value()) {
    return false;
  }
  std::string stat_content = GetProcStat(pid);
  if (stat_content.empty()) {
    *error_msg = StringPrintf("Failed to read /proc/%d/stat: %s", pid, strerror(errno));
    return false;
  }
  int64_t ticks_per_sec = GetTicksPerSec();
  if (!ParseProcStat(stat_content, *uptime_ms, ticks_per_sec, stat)) {
    *error_msg = StringPrintf("Failed to parse /proc/%d/stat '%s'", pid, stat_content.c_str());
    return false;
  }
  return true;
}

}  // namespace art
