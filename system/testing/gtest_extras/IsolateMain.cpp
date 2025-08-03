/*
 * Copyright (C) 2014 The Android Open Source Project
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
#include <libgen.h>
#include <limits.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>

#include <cstring>
#include <string_view>
#include <vector>

#include <gtest/gtest.h>
#include <gtest_extras/IsolateMain.h>

#include "Color.h"
#include "Isolate.h"

namespace android {
namespace gtest_extras {

static void PrintHelpInfo() {
  printf("Unit Test Options:\n");
  ColoredPrintf(COLOR_GREEN, "  -j ");
  ColoredPrintf(COLOR_YELLOW, "[JOB_COUNT]");
  printf(" or ");
  ColoredPrintf(COLOR_GREEN, "-j");
  ColoredPrintf(COLOR_YELLOW, "[JOB_COUNT]\n");
  printf(
      "      Run up to JOB_COUNT tests in parallel.\n"
      "      Use isolation mode, Run each test in a separate process.\n"
      "      If JOB_COUNT is not given, it is set to the count of available processors.\n");
  ColoredPrintf(COLOR_GREEN, "  --no_isolate\n");
  printf(
      "      Don't use isolation mode, run all tests in a single process.\n"
      "      If the test seems to be running in a debugger (based on the parent's name) this will\n"
      "      be automatically set. If this behavior is not desired use the '--force_isolate' flag\n"
      "      below.\n");
  ColoredPrintf(COLOR_GREEN, "  --force_isolate\n");
  printf(
      "      Force the use of isolation mode, even if it looks like we are running in a\n"
      "      debugger.\n");
  ColoredPrintf(COLOR_GREEN, "  --deadline_threshold_ms=");
  ColoredPrintf(COLOR_YELLOW, "[TIME_IN_MS]\n");
  printf("      Run each test in no longer than ");
  ColoredPrintf(COLOR_YELLOW, "[TIME_IN_MS]");
  printf(
      " time.\n"
      "      Only valid in isolation mode. Default deadline is 90000 ms.\n");
  ColoredPrintf(COLOR_GREEN, "  --slow_threshold_ms=");
  ColoredPrintf(COLOR_YELLOW, "[TIME_IN_MS]\n");
  printf("      Test running longer than ");
  ColoredPrintf(COLOR_YELLOW, "[TIME_IN_MS]");
  printf(
      " will be called slow.\n"
      "      Only valid in isolation mode. Default slow threshold is 2000 ms.\n");
  printf(
      "\nIn isolation mode, you can send SIGQUIT to the parent process to show the\n"
      "current running tests, or send SIGINT to the parent process to stop all\n"
      "running tests.\n"
      "\n");
}

static int GtestRun(std::vector<const char*>* args) {
  int argc = static_cast<int>(args->size());
  args->push_back(nullptr);
  ::testing::InitGoogleTest(&argc, const_cast<char**>(args->data()));
  return RUN_ALL_TESTS();
}

static bool RunInIsolationMode(std::vector<const char*>& args) {
  // Parse arguments that can't be used in isolation mode.
  bool isolation_forced = false;
  for (size_t i = 1; i < args.size(); ++i) {
    if (strcmp(args[i], "--no_isolate") == 0) {
      return false;
    } else if (strcmp(args[i], "--force_isolate") == 0) {
      // We want to make sure we prioritize --no_isolate and --gtest_list_tests.
      isolation_forced = true;
    } else if (strcmp(args[i], "--gtest_list_tests") == 0) {
      return false;
    }
  }
  if (!isolation_forced) {
    // Check if we are running gdb/gdbserver/lldb/lldb-server. No need to be sneaky we are assuming
    // no one is hiding.
    pid_t ppid = getppid();
    std::string exe_path = std::string("/proc/") + std::to_string(ppid) + "/exe";
    char buf[PATH_MAX + 1];
    ssize_t len;
    // NB We can't use things like android::base::* or std::filesystem::* due to linking
    // issues.
    // Since PATH_MAX is the longest a symlink can be in posix we don't need to
    // deal with truncation. Anyway this isn't critical for correctness and is
    // just a QOL thing so it's fine if we are wrong.
    if ((len = TEMP_FAILURE_RETRY(readlink(exe_path.c_str(), buf, sizeof(buf) - 1))) > 0) {
      buf[len] = '\0';
      static std::set<std::string_view> debuggers{"gdb",
                                                  "gdbserver",
                                                  "gdbserver64",
                                                  "lldb",
                                                  "lldb-server",
                                                  "arm-lldb-server",
                                                  "arm64-lldb-server",
                                                  "riscv64-lldb-server",
                                                  "x86-lldb-server",
                                                  "x86_64-lldb-server"};
      return debuggers.find(basename(buf)) == debuggers.end();
    }
    // If we can't figure out what our parent was just assume we are fine to isolate.
  }
  return true;
}

}  // namespace gtest_extras
}  // namespace android

// Tests that override this weak function can add default arguments.
extern "C" bool __attribute__((weak)) GetInitialArgs(const char***, size_t*);

int IsolateMain(int argc, char** argv, char**) {
  std::vector<const char*> args{argv[0]};

  bool print_help = false;
  size_t gtest_color_index = 0;
  for (int i = 1; i < argc; ++i) {
    args.push_back(argv[i]);
    if (strcmp(argv[i], "--help") == 0 || strcmp(argv[i], "-h") == 0) {
      print_help = true;
    } else if (strncmp(argv[i], "--gtest_color=", 14) == 0) {
      gtest_color_index = args.size() - 1;
    }
  }

  if (print_help) {
    std::vector<const char*> help_args{args[0], "--help"};
    if (gtest_color_index != 0) {
      // This is the only option that changes the way the help is printed.
      help_args.push_back(args[gtest_color_index]);
      ::testing::GTEST_FLAG(color) = args[gtest_color_index] + 14;
    }
    android::gtest_extras::PrintHelpInfo();
    return android::gtest_extras::GtestRun(&help_args);
  }

  if (!android::gtest_extras::RunInIsolationMode(args)) {
    return android::gtest_extras::GtestRun(&args);
  }

  const char** start_args;
  size_t num_args;
  if (GetInitialArgs != nullptr && GetInitialArgs(&start_args, &num_args)) {
    std::vector<const char*> initial_args;
    for (size_t i = 0; i < num_args; i++) {
      initial_args.push_back(start_args[i]);
    }
    args.insert(args.begin() + 1, initial_args.begin(), initial_args.end());
  }

  std::vector<char*> child_args;
  android::gtest_extras::Options options;
  if (!options.Process(args, &child_args)) {
    return 1;
  }

  // Add the --no_isolate option to force child processes not to rerun
  // in isolation mode.
  child_args.push_back(strdup("--no_isolate"));

  // Set the flag values.
  ::testing::GTEST_FLAG(color) = options.color();
  ::testing::GTEST_FLAG(print_time) = options.print_time();

  android::gtest_extras::Isolate isolate(options, child_args);
  int return_val = isolate.Run();
  for (auto child_arg : child_args) {
    free(child_arg);
  }
  return return_val;
}
