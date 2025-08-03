/******************************************************************************
 *
 *  Copyright 2018 The Android Open Source Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at:
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/

#include <base/command_line.h>
#include <base/logging.h>
#include <base/strings/string_piece.h>
#include <base/strings/string_util.h>
#include <bluetooth/log.h>
#include <gtest/gtest.h>

#include <string>

using namespace bluetooth;

int main(int argc, char** argv) {
  ::testing::InitGoogleTest(&argc, argv);

  if (base::CommandLine::InitializedForCurrentProcess()) {
    log::fatal("base::CommandLine::Init should not be called twice");
    return 1;
  }

  const char* log_level_arg = nullptr;
  for (int i = 0; i < argc; ++i) {
    if (base::StartsWith(base::StringPiece(argv[i]), base::StringPiece("--v="),
                         base::CompareCase::INSENSITIVE_ASCII)) {
      log_level_arg = argv[i];
    }
  }
  if (log_level_arg == nullptr) {
    log_level_arg = "--v=0";
  }
  const char* logging_argv[] = {"bt_stack", log_level_arg};
  // Init command line object with logging switches
  if (!base::CommandLine::Init(2, logging_argv)) {
    log::fatal("base::CommandLine::Init failed, arg0={}, arg1={}",
               logging_argv[0], logging_argv[1]);
    return 1;
  }

  logging::LoggingSettings log_settings;
  if (!logging::InitLogging(log_settings)) {
    log::error("Failed to set up logging");
  }

  // Android already logs thread_id, proc_id, timestamp, so disable those.
  logging::SetLogItems(false, false, false, false);

  return RUN_ALL_TESTS();
}
