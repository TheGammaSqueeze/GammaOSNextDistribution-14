/*
 * Copyright 2021 The Android Open Source Project
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

#include "os/syslog.h"

#include <syslog.h>

#include <cstdarg>
#include <memory>

#include "os/log_tags.h"

// TODO(b/305066880) - This implementation will replace this syslog
// implementation. Remove this file once everything is moved over to the new
// logging macros.
#include "bluetooth/log.h"

namespace bluetooth::log_internal {
extern Level GetLogLevelForTag(char const* tag);
}

namespace {
#define SYSLOG_IDENT "btadapterd"

const char kSyslogIdent[] = SYSLOG_IDENT;

// Map LOG_TAG_* to syslog mappings
const int kLevelMap[] = {
    /*LOG_TAG_VERBOSE=*/LOG_DEBUG,
    /*LOG_TAG_DEBUG=*/LOG_DEBUG,
    /*LOG_TAG_INFO=*/LOG_INFO,
    /*LOG_TAG_WARN=*/LOG_WARNING,
    /*LOG_TAG_ERROR=*/LOG_ERR,
    /*LOG_TAG_FATAL=*/LOG_CRIT,
};

static_assert(sizeof(kLevelMap) / sizeof(kLevelMap[0]) == (LOG_TAG_FATAL - LOG_TAG_VERBOSE) + 1);

class SyslogWrapper {
 public:
  SyslogWrapper() {
    openlog(kSyslogIdent, LOG_CONS | LOG_NDELAY | LOG_PID | LOG_PERROR, LOG_DAEMON);
  }

  ~SyslogWrapper() {
    closelog();
  }
};

std::unique_ptr<SyslogWrapper> gSyslog;
}  // namespace

void write_syslog(int level, const char* tag, const char* format, ...) {
  if (!gSyslog) {
    gSyslog = std::make_unique<SyslogWrapper>();
  }

  // Filter out logs that don't meet level requirement.
  bluetooth::log_internal::Level current_level = bluetooth::log_internal::GetLogLevelForTag(tag);
  if (static_cast<bluetooth::log_internal::Level>(level) < current_level) {
    return;
  }

  // I don't expect to see incorrect levels but making the check anyway so we
  // don't go out of bounds in the array above.
  if (level > LOG_TAG_FATAL) {
    level = LOG_TAG_ERROR;
  } else if (level < LOG_TAG_VERBOSE) {
    level = LOG_TAG_VERBOSE;
  }
  int syslog_level = kLevelMap[level - LOG_TAG_VERBOSE];

  va_list args;
  va_start(args, format);
  vsyslog(syslog_level, format, args);
  va_end(args);
}
