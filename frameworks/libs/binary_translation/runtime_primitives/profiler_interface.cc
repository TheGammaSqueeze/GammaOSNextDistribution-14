/*
 * Copyright (C) 2023 The Android Open Source Project
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

#include "berberis/runtime_primitives/profiler_interface.h"

#include <fcntl.h>

#include "berberis/base/config_globals.h"
#include "berberis/base/format_buffer.h"
#include "berberis/base/gettid.h"
#include "berberis/base/scoped_errno.h"
#include "berberis/base/tracing.h"

namespace berberis {

namespace {

int ProfilerOpenLogFile() {
  auto env = GetProfilingConfig();
  if (!env) {
    TRACE("Profiling: None");
    return -1;
  }

  auto app = GetAppPackageName();

  if (strcmp(env, "1") == 0) {
    // Special case - profile everything.
  } else if (app) {
    // Running an app - must match package name.
    if (strcmp(app, env) != 0) {
      TRACE("Profiling: Skipping: app %s doesn't match filter %s", app, env);
      return -1;
    }
  } else if (auto exe = GetMainExecutableRealPath()) {
    // Running a standalone program - must somehow match main executable path.
    if (!strstr(exe, env)) {
      TRACE("Profiling: Skipping: executable %s doesn't match filter %s", exe, env);
      return -1;
    }
  } else {
    // Running a unit test, or some other non-app, non-executable case.
    return -1;
  }

  ScopedErrno scoped_errno;

  char buf[160];

  int pid = GetpidSyscall();
  if (app) {
    FormatBuffer(buf, sizeof(buf), "/data/data/%s/perf-%u.map", app, pid);
  } else {
    FormatBuffer(buf, sizeof(buf), "/data/local/tmp/perf-%u.map", pid);
  }

  int fd = open(buf, O_WRONLY | O_CREAT | O_CLOEXEC, S_IWUSR);
  if (fd == -1) {
    TRACE("Profiling Error: Failed to open map file %s", buf);
  } else {
    TRACE("Probfiling to %s", buf);
  }
  return fd;
}

}  // namespace

void ProfilerLogGeneratedCode(const void* start,
                              size_t size,
                              GuestAddr guest_start,
                              size_t guest_size,
                              const char* prefix) {
  static int fd = ProfilerOpenLogFile();
  if (fd == -1) {
    return;
  }

  char buf[80];
  // start size name
  // TODO(b232598137): make name useful
  size_t n = FormatBuffer(buf,
                          sizeof(buf),
                          "%p 0x%zx %s_jit_0x%lx+%zu\n",
                          start,
                          size,
                          prefix,
                          guest_start,
                          guest_size);
  UNUSED(write(fd, buf, n));
}

}  // namespace berberis
