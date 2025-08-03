/*
 * Copyright 2022 The Android Open Source Project
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

#include "common/testing/log_capture.h"

#include <fcntl.h>
#include <sys/stat.h>

#include <cstddef>
#include <string>

#include "os/log.h"

namespace bluetooth {
namespace testing {

LogCapture::LogCapture() {
  LOG_INFO(
      "Log capture disabled for android build dup_fd:%d fd:%d original_stderr_fd:%d",
      dup_fd_,
      fd_,
      original_stderr_fd_);
}

LogCapture::~LogCapture() {}

LogCapture* LogCapture::Rewind() {
  return this;
}

bool LogCapture::Find(const std::string& /* to_find */) {
  // For |atest| assume all log captures succeed
  return true;
}

void LogCapture::Flush() {}

void LogCapture::Sync() {}

void LogCapture::Reset() {}

std::string LogCapture::Read() {
  return std::string();
}

size_t LogCapture::Size() const {
  size_t size{0UL};
  return size;
}

void LogCapture::WaitUntilLogContains(const std::string& /* text */) {}

std::pair<int, int> LogCapture::create_backing_store() const {
  int dup_fd = -1;
  int fd = -1;
  return std::make_pair(dup_fd, fd);
}

bool LogCapture::set_non_blocking(int /* fd */) const {
  return true;
}

void LogCapture::clean_up() {}

}  // namespace testing
}  // namespace bluetooth
