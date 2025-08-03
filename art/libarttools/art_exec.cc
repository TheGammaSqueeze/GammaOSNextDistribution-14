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

#include <stdlib.h>
#include <sys/capability.h>
#include <sys/resource.h>
#include <unistd.h>

#include <filesystem>
#include <iostream>
#include <iterator>
#include <optional>
#include <string>
#include <string_view>
#include <system_error>
#include <unordered_map>
#include <unordered_set>
#include <vector>

#include "android-base/logging.h"
#include "android-base/parseint.h"
#include "android-base/result.h"
#include "android-base/strings.h"
#include "base/macros.h"
#include "base/scoped_cap.h"
#include "palette/palette.h"
#include "system/thread_defs.h"

namespace {

using ::android::base::ConsumePrefix;
using ::android::base::Join;
using ::android::base::ParseInt;
using ::android::base::Result;
using ::android::base::Split;

constexpr const char* kUsage =
    R"(A wrapper binary that configures the process and executes a command.

By default, it closes all open file descriptors except stdin, stdout, and stderr. `--keep-fds` can
be passed to keep some more file descriptors open.

Usage: art_exec [OPTIONS]... -- [COMMAND]...

Supported options:
  --help: Print this text.
  --set-task-profile=PROFILES: Apply a set of task profiles (see
      https://source.android.com/devices/tech/perf/cgroups). Requires root access. PROFILES can be a
      comma-separated list of task profile names.
  --set-priority=PRIORITY: Apply the process priority. Currently, the only supported value of
      PRIORITY is "background".
  --drop-capabilities: Drop all root capabilities. Note that this has effect only if `art_exec` runs
      with some root capabilities but not as the root user.
  --keep-fds=FILE_DESCRIPTORS: A semicolon-separated list of file descriptors to keep open.
  --env=KEY=VALUE: Set an environment variable. This flag can be passed multiple times to set
      multiple environment variables.
)";

constexpr int kErrorUsage = 100;
constexpr int kErrorOther = 101;

struct Options {
  int command_pos = -1;
  std::vector<std::string> task_profiles;
  std::optional<int> priority = std::nullopt;
  bool drop_capabilities = false;
  std::unordered_set<int> keep_fds{fileno(stdin), fileno(stdout), fileno(stderr)};
  std::unordered_map<std::string, std::string> envs;
  std::string chroot;
};

[[noreturn]] void Usage(const std::string& error_msg) {
  LOG(ERROR) << error_msg;
  std::cerr << error_msg << "\n" << kUsage << "\n";
  exit(kErrorUsage);
}

Options ParseOptions(int argc, char** argv) {
  Options options;
  for (int i = 1; i < argc; i++) {
    std::string_view arg = argv[i];
    if (arg == "--help") {
      std::cerr << kUsage << "\n";
      exit(0);
    } else if (ConsumePrefix(&arg, "--set-task-profile=")) {
      options.task_profiles = Split(std::string(arg), ",");
      if (options.task_profiles.empty()) {
        Usage("Empty task profile list");
      }
    } else if (ConsumePrefix(&arg, "--set-priority=")) {
      if (arg == "background") {
        options.priority = ANDROID_PRIORITY_BACKGROUND;
      } else {
        Usage("Unknown priority " + std::string(arg));
      }
    } else if (arg == "--drop-capabilities") {
      options.drop_capabilities = true;
    } else if (ConsumePrefix(&arg, "--keep-fds=")) {
      for (const std::string& fd_str : Split(std::string(arg), ":")) {
        int fd;
        if (!ParseInt(fd_str, &fd)) {
          Usage("Invalid fd " + fd_str);
        }
        options.keep_fds.insert(fd);
      }
    } else if (ConsumePrefix(&arg, "--env=")) {
      size_t pos = arg.find('=');
      if (pos == std::string_view::npos) {
        Usage("Malformed environment variable. Must contain '='");
      }
      options.envs[std::string(arg.substr(/*pos=*/0, /*n=*/pos))] =
          std::string(arg.substr(pos + 1));
    } else if (ConsumePrefix(&arg, "--chroot=")) {
      options.chroot = arg;
    } else if (arg == "--") {
      if (i + 1 >= argc) {
        Usage("Missing command after '--'");
      }
      options.command_pos = i + 1;
      return options;
    } else {
      Usage("Unknown option " + std::string(arg));
    }
  }
  Usage("Missing '--'");
}

Result<void> DropInheritableCaps() {
  art::ScopedCap cap(cap_get_proc());
  if (cap.Get() == nullptr) {
    return ErrnoErrorf("Failed to call cap_get_proc");
  }
  if (cap_clear_flag(cap.Get(), CAP_INHERITABLE) != 0) {
    return ErrnoErrorf("Failed to call cap_clear_flag");
  }
  if (cap_set_proc(cap.Get()) != 0) {
    return ErrnoErrorf("Failed to call cap_set_proc");
  }
  return {};
}

Result<void> CloseFds(const std::unordered_set<int>& keep_fds) {
  std::vector<int> open_fds;
  std::error_code ec;
  for (const std::filesystem::directory_entry& dir_entry :
       std::filesystem::directory_iterator("/proc/self/fd", ec)) {
    int fd;
    if (!ParseInt(dir_entry.path().filename(), &fd)) {
      return Errorf("Invalid entry in /proc/self/fd {}", dir_entry.path().filename());
    }
    open_fds.push_back(fd);
  }
  if (ec) {
    return Errorf("Failed to list open FDs: {}", ec.message());
  }
  for (int fd : open_fds) {
    if (keep_fds.find(fd) == keep_fds.end()) {
      if (close(fd) != 0) {
        Result<void> error = ErrnoErrorf("Failed to close FD {}", fd);
        if (std::filesystem::exists(ART_FORMAT("/proc/self/fd/{}", fd))) {
          return error;
        }
      }
    }
  }
  return {};
}

}  // namespace

int main(int argc, char** argv) {
  android::base::InitLogging(argv);

  Options options = ParseOptions(argc, argv);

  if (auto result = CloseFds(options.keep_fds); !result.ok()) {
    LOG(ERROR) << "Failed to close open FDs: " << result.error();
    return kErrorOther;
  }

  if (!options.task_profiles.empty()) {
    if (int ret = PaletteSetTaskProfiles(/*tid=*/0, options.task_profiles);
        ret != PALETTE_STATUS_OK) {
      LOG(ERROR) << "Failed to set task profile: " << ret;
      return kErrorOther;
    }
  }

  if (options.priority.has_value()) {
    if (setpriority(PRIO_PROCESS, /*who=*/0, options.priority.value()) != 0) {
      PLOG(ERROR) << "Failed to setpriority";
      return kErrorOther;
    }
  }

  if (options.drop_capabilities) {
    if (auto result = DropInheritableCaps(); !result.ok()) {
      LOG(ERROR) << "Failed to drop inheritable capabilities: " << result.error();
      return kErrorOther;
    }
  }

  for (const auto& [key, value] : options.envs) {
    setenv(key.c_str(), value.c_str(), /*overwrite=*/1);
  }

  if (!options.chroot.empty()) {
    if (chroot(options.chroot.c_str()) != 0) {
      PLOG(ERROR) << ART_FORMAT("Failed to chroot to '{}'", options.chroot);
      return kErrorOther;
    }
  }

  execv(argv[options.command_pos], argv + options.command_pos);

  std::vector<const char*> command_args(argv + options.command_pos, argv + argc);
  PLOG(FATAL) << "Failed to execute (" << Join(command_args, ' ') << ")";
  UNREACHABLE();
}
