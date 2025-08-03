//
// Copyright (C) 2021 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//

// This file is exactly the same as zucchini_main.cc, except with a few fixes
// that make it compatible with AOSP version of liblog

#include <iostream>

#include "base/command_line.h"
#include "base/logging.h"
#include "base/process/memory.h"
#include "build/build_config.h"
#include "main_utils.h"

#if defined(OS_WIN)
#include "base/win/process_startup_helper.h"
#endif // defined(OS_WIN)

namespace {

void InitLogging() {
  logging::LoggingSettings settings;
  settings.logging_dest = logging::LOG_TO_SYSTEM_DEBUG_LOG;
  // settings.log_file_path = nullptr;
  settings.lock_log = logging::DONT_LOCK_LOG_FILE;
  settings.delete_old = logging::APPEND_TO_OLD_LOG_FILE;
  bool logging_res = logging::InitLogging(settings);
  CHECK(logging_res);
}

void InitErrorHandling(const base::CommandLine &command_line) {
  base::EnableTerminationOnHeapCorruption();
  base::EnableTerminationOnOutOfMemory();
#if defined(OS_WIN)
  base::win::RegisterInvalidParamHandler();
  base::win::SetupCRT(command_line);
#endif // defined(OS_WIN)
}

} // namespace

int main(int argc, const char *argv[]) {
  // Initialize infrastructure from base.
  base::CommandLine::Init(argc, argv);
  const base::CommandLine &command_line =
      *base::CommandLine::ForCurrentProcess();
  InitLogging();
  InitErrorHandling(command_line);
  zucchini::status::Code status =
      RunZucchiniCommand(command_line, std::cout, std::cerr);
  if (!(status == zucchini::status::kStatusSuccess ||
        status == zucchini::status::kStatusInvalidParam)) {
    std::cerr << "Failed with code " << static_cast<int>(status) << std::endl;
  }
  return static_cast<int>(status);
}
