/*
 * Copyright 2023 The Android Open Source Project
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

#include <base/at_exit.h>
#include <base/check.h>
#include <base/files/file_descriptor_watcher_posix.h>
#include <base/files/file_util.h>
#include <base/run_loop.h>
#include <base/strings/stringprintf.h>
#include <base/task/single_thread_task_executor.h>
#include <sys/syslog.h>

#include "mmc/daemon/service.h"

// syslog.h and base/logging.h both try to #define LOG_INFO and LOG_WARNING.
// We need to #undef at least these two before including base/logging.h.  The
// others are included to be consistent.
namespace {
const int kSyslogDebug = LOG_DEBUG;
const int kSyslogInfo = LOG_INFO;
const int kSyslogWarning = LOG_WARNING;
const int kSyslogError = LOG_ERR;
const int kSyslogCritical = LOG_CRIT;

#undef LOG_INFO
#undef LOG_WARNING
#undef LOG_ERR
#undef LOG_CRIT
}  // namespace

#include <base/logging.h>

static bool MessageHandler(int severity, const char* file, int line,
                           size_t message_start, const std::string& message) {
  const auto str = base::StringPrintf("%s:%d - %s", file, line,
                                      message.substr(message_start).c_str());

  switch (severity) {
    case logging::LOGGING_INFO:
      severity = kSyslogInfo;
      break;

    case logging::LOGGING_WARNING:
      severity = kSyslogWarning;
      break;

    case logging::LOGGING_ERROR:
      severity = kSyslogError;
      break;

    case logging::LOGGING_FATAL:
      severity = kSyslogCritical;
      break;

    default:
      severity = kSyslogDebug;
      break;
  }

  syslog(severity, "%s", str.c_str());

  if (severity == kSyslogCritical) abort();

  return true;
}

int main(int argc, char* argv[]) {
  // Set up syslog to stderr.
  logging::LoggingSettings settings;
  settings.logging_dest =
      logging::LOG_TO_SYSTEM_DEBUG_LOG | logging::LOG_TO_STDERR;
  logging::SetLogItems(false, false, false, false);
  logging::InitLogging(settings);
  logging::SetLogMessageHandler(MessageHandler);

  LOG(INFO) << "Start MMC daemon";

  // These are needed to send D-Bus signals and receive messages.
  // Even though they are not used directly, they set up some global state
  // needed by the D-Bus library.
  base::SingleThreadTaskExecutor task_executor(base::MessagePumpType::IO);
  base::FileDescriptorWatcher watcher(task_executor.task_runner());
  base::AtExitManager at_exit_manager;

  base::RunLoop run_loop;

  auto service = std::make_unique<mmc::Service>(run_loop.QuitClosure());
  CHECK(service->Init());

  run_loop.Run();

  return 0;
}
