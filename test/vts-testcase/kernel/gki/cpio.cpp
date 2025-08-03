/*
 * Copyright (C) 2021 The Android Open Source Project
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

#include "cpio.h"

#include <sys/types.h>
#include <sys/wait.h>

#include <vector>

#include <android-base/logging.h>
#include <android-base/strings.h>

using android::base::ErrnoError;
using android::base::Error;
using android::base::Join;

namespace android {

namespace {
std::vector<char*> ToCStringArray(std::vector<std::string>* args) {
  std::vector<char*> ret;
  ret.reserve(args->size() + 1);
  for (auto& arg : *args) {
    ret.push_back(arg.data());
  }
  ret.push_back(nullptr);
  return ret;
}

// Wrapper of fork() / execvp() / wait() paradigm. Child process chdir() into
// the directory before execv()
android::base::Result<void> Exec(std::vector<std::string>* args,
                                 std::string_view working_directory) {
  pid_t pid = fork();
  if (pid == -1)
    return ErrnoError() << "Unable to execute `" << Join(*args, " ")
                        << "`: failed to fork";
  if (pid == 0) {
    // child process
    CHECK(0 == chdir(working_directory.data())) << strerror(errno);
    // execvp needs char*, so copy the strings to heap.
    auto c_args = ToCStringArray(args);
    (void)close(STDIN_FILENO);
    (void)execvp(c_args[0], c_args.data());
    // execvp only returns on error.
    PLOG(FATAL) << "Unable to execute `" << Join(*args, " ")
                << "`: execvp returns";
  }
  // parent process
  int status;
  if (pid != waitpid(pid, &status, 0))
    return ErrnoError() << "Unable to execute `" << Join(*args, " ")
                        << "`; failed to waitpid(" << pid << ")";
  if (!WIFEXITED(status))
    return Error() << "Unable to execute `" << Join(*args, " ")
                   << "`: not exited normally";
  if (0 != WEXITSTATUS(status))
    return Error() << "Unable to execute `" << Join(*args, " ")
                   << "`: exit status " << WEXITSTATUS(status);
  return {};
}

}  // namespace

// Extract CPIO archive at |archive_path| to a temporary directory.
android::base::Result<std::unique_ptr<TemporaryDir>> CpioExtract(
    std::string_view archive_path) {
  std::vector<std::string> args{"cpio", "-i", "-F", archive_path.data()};

  auto extracted_ramdisk = std::make_unique<TemporaryDir>();
  auto res = Exec(&args, extracted_ramdisk->path);
  if (!res.ok()) return res.error();
  return extracted_ramdisk;
}

}  // namespace android
