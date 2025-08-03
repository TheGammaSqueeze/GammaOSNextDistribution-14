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
#include <sstream>
#include <string>

#include "os/log.h"

namespace {
constexpr char kTempFilename[] = "/tmp/bt_gtest_log_capture-XXXXXX";
constexpr size_t kTempFilenameMaxSize = 64;
constexpr size_t kBufferSize = 4096;
constexpr int kStandardErrorFd = STDERR_FILENO;
}  // namespace

namespace bluetooth {
namespace testing {

LogCapture::LogCapture() {
  std::tie(dup_fd_, fd_) = create_backing_store();
  if (dup_fd_ == -1 || fd_ == -1) {
    LOG_ERROR("Unable to create backing storage : %s", strerror(errno));
    return;
  }
  if (!set_non_blocking(dup_fd_)) {
    LOG_ERROR("Unable to set socket non-blocking : %s", strerror(errno));
    return;
  }
  original_stderr_fd_ = fcntl(kStandardErrorFd, F_DUPFD_CLOEXEC);
  if (original_stderr_fd_ == -1) {
    LOG_ERROR("Unable to save original fd : %s", strerror(errno));
    return;
  }
  if (dup3(dup_fd_, kStandardErrorFd, O_CLOEXEC) == -1) {
    LOG_ERROR("Unable to duplicate stderr fd : %s", strerror(errno));
    return;
  }
}

LogCapture::~LogCapture() {
  Rewind()->Flush();
  clean_up();
}

LogCapture* LogCapture::Rewind() {
  if (fd_ != -1) {
    if (lseek(fd_, 0, SEEK_SET) != 0) {
      LOG_ERROR("Unable to rewind log capture : %s", strerror(errno));
    }
  }
  return this;
}

bool LogCapture::Find(const std::string& to_find) {
  std::string str = this->Read();
  return str.find(to_find) != std::string::npos;
}

void LogCapture::Flush() {
  if (fd_ != -1 && original_stderr_fd_ != -1) {
    ssize_t sz{-1};
    do {
      char buf[kBufferSize];
      sz = read(fd_, buf, sizeof(buf));
      if (sz > 0) {
        write(original_stderr_fd_, buf, sz);
      }
    } while (sz == kBufferSize);
  }
}

void LogCapture::Sync() {
  if (fd_ != -1) {
    fsync(fd_);
  }
}

void LogCapture::Reset() {
  if (fd_ != -1) {
    if (ftruncate(fd_, 0UL) == -1) {
      LOG_ERROR("Unable to truncate backing storage : %s", strerror(errno));
    }
    this->Rewind();
    // The only time we rewind the dup()'ed fd is during Reset()
    if (dup_fd_ != -1) {
      if (lseek(dup_fd_, 0, SEEK_SET) != 0) {
        LOG_ERROR("Unable to rewind log capture : %s", strerror(errno));
      }
    }
  }
}

std::string LogCapture::Read() {
  if (fd_ == -1) {
    return std::string();
  }
  std::ostringstream oss;
  ssize_t sz{-1};
  do {
    char buf[kBufferSize];
    sz = read(fd_, buf, sizeof(buf));
    if (sz > 0) {
      oss << buf;
    }
  } while (sz == kBufferSize);
  return oss.str();
}

size_t LogCapture::Size() const {
  size_t size{0UL};
  struct stat statbuf;
  if (fd_ != -1 && fstat(fd_, &statbuf) != -1) {
    size = statbuf.st_size;
  }
  return size;
}

void LogCapture::WaitUntilLogContains(const std::string& text) {
  bool found = false;
  do {
    found = this->Rewind()->Find(text);
  } while (!found);
}

std::pair<int, int> LogCapture::create_backing_store() const {
  char backing_store_filename[kTempFilenameMaxSize];
  strncpy(backing_store_filename, kTempFilename, kTempFilenameMaxSize);
  int dup_fd = mkstemp(backing_store_filename);
  int fd = open(backing_store_filename, O_RDWR);
  if (dup_fd != -1) {
    unlink(backing_store_filename);
  }
  return std::make_pair(dup_fd, fd);
}

bool LogCapture::set_non_blocking(int fd) const {
  int flags = fcntl(fd, F_GETFL, 0);
  if (flags == -1) {
    LOG_ERROR("Unable to get file descriptor flags : %s", strerror(errno));
    return false;
  }
  if (fcntl(fd, F_SETFL, flags | O_NONBLOCK) == -1) {
    LOG_ERROR("Unable to set file descriptor flags : %s", strerror(errno));
    return false;
  }
  return true;
}

void LogCapture::clean_up() {
  if (original_stderr_fd_ != -1) {
    if (dup3(original_stderr_fd_, kStandardErrorFd, O_CLOEXEC) != kStandardErrorFd) {
      LOG_ERROR("Unable to restore original fd : %s", strerror(errno));
    }
  }
  if (dup_fd_ != -1) {
    close(dup_fd_);
    dup_fd_ = -1;
  }
  if (fd_ != -1) {
    close(fd_);
    fd_ = -1;
  }
}

}  // namespace testing
}  // namespace bluetooth
