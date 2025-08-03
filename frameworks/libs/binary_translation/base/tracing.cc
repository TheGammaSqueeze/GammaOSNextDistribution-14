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

#include "berberis/base/tracing.h"

#include <fcntl.h>
#include <netdb.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/types.h>

#include <string>

#include "berberis/base/checks.h"
#include "berberis/base/config_globals.h"
#include "berberis/base/file.h"
#include "berberis/base/logging.h"
#include "berberis/base/scoped_errno.h"

namespace berberis {

namespace {

int TraceToFile(std::string trace_filename) {
  if (trace_filename == "1") {
    ALOGD("tracing to stdout");
    return STDOUT_FILENO;
  }

  if (trace_filename == "2") {
    ALOGD("tracing to stderr");
    return STDERR_FILENO;
  }

  // If the provided path is relative set it up under app's private directory.
  if (trace_filename.at(0) != '/') {
    const char* app_private_dir = GetAppPrivateDir();
    if (!app_private_dir) {
      ALOGE("not tracing - app's private directory is undefined");
      return -1;
    }
    trace_filename = std::string(app_private_dir) + "/" + trace_filename;
  }

  if (uid_t uid = getuid()) {
    // If not running as root, should be output file directory owner.
    // To trace an app, use output file in app's data directory.
    std::string dir = Dirname(trace_filename);
    struct stat dir_stat;
    if (stat(dir.c_str(), &dir_stat)) {
      ALOGE("not tracing - failed to stat \"%s\"", dir.c_str());
      return -1;
    }
    if (uid != dir_stat.st_uid) {
      ALOGE("not tracing - uid mismatch of \"%s\"", dir.c_str());
      return -1;
    }
  }

  int fd = open(trace_filename.c_str(), O_WRONLY | O_CREAT | O_APPEND | O_CLOEXEC, S_IWUSR);
  if (fd == -1) {
    ALOGE("not tracing - failed to open output file \"%s\"", trace_filename.c_str());
    return -1;
  }

  ALOGD("tracing to \"%s\"", trace_filename.c_str());
  return fd;
}

// At the moment, only accept ":<port>", assume localhost:<port>
int TraceToSocket(const char* env) {
  CHECK_EQ(':', env[0]);

  struct addrinfo hints {};
  hints.ai_family = AF_UNSPEC;
  hints.ai_socktype = SOCK_STREAM;

  struct addrinfo* results;
  if (getaddrinfo("localhost", env + 1, &hints, &results) != 0) {
    ALOGE("not tracing - failed to get info for \"%s\"", env);
    return -1;
  }

  for (auto info = results; info; info = info->ai_next) {
    int fd = socket(info->ai_family, info->ai_socktype, info->ai_protocol);
    if (fd != -1) {
      if (connect(fd, info->ai_addr, info->ai_addrlen) != -1) {
        freeaddrinfo(results);
        ALOGD("tracing to \"localhost%s\"", env);
        return fd;
      }

      close(fd);
    }
  }

  freeaddrinfo(results);
  ALOGE("not tracing - failed to connect to \"%s\"", env);
  return -1;
}

}  // namespace

int Tracing::fd_ = -1;

void Tracing::InitImpl() {
  ScopedErrno scoped_errno;

  auto env = GetTracingConfig();
  if (!env || !env[0]) {
    return;
  }

  if (const char* filter_end = strchr(env, '=')) {
    const char* app = GetAppPackageName();
    if (!app || strncmp(app, env, filter_end - env) != 0) {
      ALOGD("not tracing - filtered out");
      return;
    }
    env = filter_end + 1;
  }

  if (env[0] == ':') {
    fd_ = TraceToSocket(env);
  } else {
    fd_ = TraceToFile(env);
  }
}

}  // namespace berberis
