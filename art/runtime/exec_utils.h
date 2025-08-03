/*
 * Copyright (C) 2017 The Android Open Source Project
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

#ifndef ART_RUNTIME_EXEC_UTILS_H_
#define ART_RUNTIME_EXEC_UTILS_H_

#include <time.h>

#include <cstdint>
#include <functional>
#include <optional>
#include <string>
#include <vector>

#include "android-base/unique_fd.h"
#include "base/macros.h"

namespace art HIDDEN {

struct ProcessStat {
  // The total wall time, in milliseconds, that the process spent, or 0 if failed to get the value.
  int64_t wall_time_ms = 0;
  // The total CPU time, in milliseconds, that the process and any waited-for children spent, or 0
  // if failed to get the value.
  int64_t cpu_time_ms = 0;
};

struct ExecCallbacks {
  // Called in the parent process as soon as the child process is forked.
  std::function<void(pid_t pid)> on_start = [](pid_t) {};
  // Called in the parent process after the child process exits while still in a waitable state, no
  // matter the child process succeeds or not.
  std::function<void(pid_t pid)> on_end = [](pid_t) {};
};

struct ExecResult {
  // This struct needs to be in sync with the ExecResultStatus enum contained within the
  // OdrefreshReported atom in frameworks/proto_logging/atoms/art/odrefresh_extension_atoms.proto.
  enum Status {
    // Unable to get the status.
    kUnknown = 0,
    // Process exited normally with an exit code.
    kExited = 1,
    // Process terminated by a signal.
    kSignaled = 2,
    // Process timed out and killed.
    kTimedOut = 3,
    // Failed to start the process.
    kStartFailed = 4,
    kLast = kStartFailed
  };

  Status status = kUnknown;

  // The process exit code, if `status` is `kExited`, or -1.
  int exit_code = -1;

  // The signal that terminated the process, if `status` is `kSignaled`, or 0.
  int signal = 0;
};

// Wrapper on fork/execv to run a command in a subprocess.
// These spawn child processes using the environment as it was set when the single instance
// of the runtime (Runtime::Current()) was started.  If no instance of the runtime was started, it
// will use the current environment settings.
class EXPORT ExecUtils {
 public:
  virtual ~ExecUtils() = default;

  virtual bool Exec(const std::vector<std::string>& arg_vector,
                    /*out*/ std::string* error_msg) const;

  virtual int ExecAndReturnCode(const std::vector<std::string>& arg_vector,
                                /*out*/ std::string* error_msg) const;

  // Executes the command specified in `arg_vector` in a subprocess with a timeout.
  // If `timeout_sec` is negative, blocks until the subprocess exits.
  // Returns a structured result. If the status is not `kExited`, also returns a non-empty
  // `error_msg`.
  virtual ExecResult ExecAndReturnResult(const std::vector<std::string>& arg_vector,
                                         int timeout_sec,
                                         /*out*/ std::string* error_msg) const;

  // Same as above, but also collects stat of the process and calls callbacks. The stat is collected
  // no matter the child process succeeds or not.
  virtual ExecResult ExecAndReturnResult(const std::vector<std::string>& arg_vector,
                                         int timeout_sec,
                                         const ExecCallbacks& callbacks,
                                         /*out*/ ProcessStat* stat,
                                         /*out*/ std::string* error_msg) const;

 protected:
  virtual android::base::unique_fd PidfdOpen(pid_t pid) const;

  // Returns the content of `/proc/<pid>/stat`, or an empty string if failed.
  virtual std::string GetProcStat(pid_t pid) const;

  virtual std::optional<int64_t> GetUptimeMs(std::string* error_msg) const;

  virtual int64_t GetTicksPerSec() const;

 private:
  bool GetStat(pid_t pid, /*out*/ ProcessStat* stat, /*out*/ std::string* error_msg) const;
};

inline bool Exec(const std::vector<std::string>& arg_vector, /*out*/ std::string* error_msg) {
  return ExecUtils().Exec(arg_vector, error_msg);
}

inline int ExecAndReturnCode(const std::vector<std::string>& arg_vector,
                             /*out*/ std::string* error_msg) {
  return ExecUtils().ExecAndReturnCode(arg_vector, error_msg);
}

}  // namespace art

#endif  // ART_RUNTIME_EXEC_UTILS_H_
