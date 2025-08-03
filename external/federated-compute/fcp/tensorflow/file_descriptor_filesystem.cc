/*
 * Copyright 2023 Google LLC
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
 
#include "fcp/tensorflow/file_descriptor_filesystem.h"

#include <errno.h>
#include <sys/stat.h>
#include <unistd.h>

#include <algorithm>
#include <memory>
#include <vector>

#include "absl/memory/memory.h"
#include "absl/strings/numbers.h"
#include "absl/strings/str_cat.h"
#include "absl/strings/string_view.h"
#include "absl/strings/strip.h"
#include "fcp/base/monitoring.h"
#include "tensorflow/core/platform/env.h"

namespace tensorflow {

namespace fcp {

using ::tensorflow::Status;

static constexpr char kFdFilesystemPrefix[] = "fd:///";
static constexpr size_t kMaxWriteChunkSize = 64u * 1024;  // 64KB

namespace {
// Copied from base implementation in
// //external/tensorflow/tensorflow/tsl/platform/default/posix_file_system.cc
absl::Status ReadBytesFromFd(int fd, uint64_t offset, size_t n,
                             absl::string_view* result, char* scratch) {
  absl::Status s;
  char* dst = scratch;
  while (n > 0 && s.ok()) {
    ssize_t r = pread(fd, dst, n, static_cast<off_t>(offset));
    if (r > 0) {
      dst += r;
      n -= r;
      offset += r;
    } else if (r == 0) {
      s = absl::OutOfRangeError(absl::StrCat(
          "Read fewer bytes than requested. Total read bytes ", offset));
    } else if (errno == EINTR || errno == EAGAIN) {
      // Retry
    } else {
      s = absl::UnknownError(absl::StrCat("Failed to read: errno ", errno));
    }
  }
  *result = absl::string_view(scratch, dst - scratch);
  return s;
}

class FdRandomAccessFile : public RandomAccessFile {
 public:
  explicit FdRandomAccessFile(int fd) : fd_(fd) {}

  ~FdRandomAccessFile() override { close(fd_); }

  Status Read(uint64 offset, size_t n, StringPiece* result,
              char* scratch) const override {
    absl::string_view sv;
    absl::Status s = ReadBytesFromFd(fd_, offset, n, &sv, scratch);
    if (s.ok()) {
      *result = StringPiece(sv.data(), sv.size());
      return Status();
    } else {
      return Status(static_cast<tensorflow::errors::Code>(s.code()),
                    s.message());
    }
  }

 private:
  int fd_;
};

class FdWritableFile : public WritableFile {
 public:
  explicit FdWritableFile(int fd) : fd_(fd) {}

  ~FdWritableFile() override { Close(); }

  Status Append(StringPiece data) override {
    return Write(data.data(), data.size());
  }

  Status Close() override {
    if (has_closed_) return Status();
    close(fd_);
    has_closed_ = true;
    return Status();
  }
  Status Flush() override { return Status(); }
  Status Sync() override { return Status(); }

 private:
  Status Write(const void* data, size_t data_size) {
    size_t write_len = data_size;
    do {
      size_t chunk_size = std::min<size_t>(write_len, kMaxWriteChunkSize);
      ssize_t wrote = write(fd_, data, chunk_size);
      if (wrote < 0) {
        return Status(tensorflow::error::Code::UNKNOWN,
                      absl::StrCat("Failed to write: ", errno));
      }
      data = static_cast<const uint8_t*>(data) + wrote;
      write_len -= wrote;
    } while (write_len > 0);
    return Status();
  }
  int fd_;
  bool has_closed_ = false;
};

// Gets the file descriptor in the URI.
Status GetFd(absl::string_view fname, int* result) {
  // Consume scheme and empty authority (fd:///)
  if (!absl::ConsumePrefix(&fname, kFdFilesystemPrefix)) {
    return errors::InvalidArgument("Bad uri: ", fname);
  }

  // Try to parse remainder of path as an integer fd
  if (!absl::SimpleAtoi(fname, result)) {
    return errors::InvalidArgument("Bad path: ", fname);
  }

  return OkStatus();
}

}  // anonymous namespace

Status FileDescriptorFileSystem::NewRandomAccessFile(
    const string& filename, std::unique_ptr<RandomAccessFile>* result) {
  int fd;
  TF_RETURN_IF_ERROR(GetFd(filename, &fd));
  FileStatistics stat;
  TF_RETURN_IF_ERROR(Stat(filename, &stat));  // check against directory FD

  int dup_fd = dup(fd);
  if (dup_fd == -1) {
    return errors::Unknown("Failed to dup: errno ", errno);
  }

  *result = std::make_unique<FdRandomAccessFile>(dup_fd);
  return OkStatus();
}

Status FileDescriptorFileSystem::GetMatchingPaths(
    const string& pattern, std::vector<string>* results) {
  results->clear();
  FileStatistics statistics;
  if (Stat(pattern, &statistics).ok()) {
    results->push_back(pattern);
  }
  return OkStatus();
}

Status FileDescriptorFileSystem::Stat(const string& fname,
                                      FileStatistics* stats) {
  if (stats == nullptr) {
    return errors::InvalidArgument("FileStatistics pointer must not be NULL");
  }

  int fd;
  TF_RETURN_IF_ERROR(GetFd(fname, &fd));

  struct stat st;
  if (fstat(fd, &st) == -1) {
    return errors::Unknown("Failed to fstat: errno ", errno);
  }

  if (S_ISDIR(st.st_mode)) {
    return errors::NotFound("File not found: is a directory");
  }
  stats->length = st.st_size;
  stats->mtime_nsec = st.st_mtime * 1e9;
  stats->is_directory = S_ISDIR(st.st_mode);

  return OkStatus();
}

Status FileDescriptorFileSystem::GetFileSize(const string& fname,
                                             uint64* size) {
  FileStatistics stat;
  TF_RETURN_IF_ERROR(Stat(fname, &stat));
  *size = stat.length;
  return OkStatus();
}

Status FileDescriptorFileSystem::NewReadOnlyMemoryRegionFromFile(
    const string& filename, std::unique_ptr<ReadOnlyMemoryRegion>* result) {
  return errors::Unimplemented("Not implemented by the fd filesystem");
}

Status FileDescriptorFileSystem::FileExists(const string& fname) {
  return errors::Unimplemented("Not implemented by the fd filesystem");
}

Status FileDescriptorFileSystem::GetChildren(const string& dir,
                                             std::vector<string>* r) {
  return errors::Unimplemented("fd filesystem is non-hierarchical");
}

Status FileDescriptorFileSystem::NewWritableFile(
    const string& fname, std::unique_ptr<WritableFile>* result) {
  int fd;
  TF_RETURN_IF_ERROR(GetFd(fname, &fd));
  FileStatistics stat;
  TF_RETURN_IF_ERROR(Stat(fname, &stat));  // check against directory FD

  int dup_fd = dup(fd);
  if (dup_fd == -1) {
    return errors::Unknown("Failed to dup: errno ", errno);
  }

  *result = std::make_unique<FdWritableFile>(dup_fd);
  return OkStatus();
}

Status FileDescriptorFileSystem::NewAppendableFile(
    const string& fname, std::unique_ptr<WritableFile>* result) {
  return errors::Unimplemented("Not implemented by the fd filesystem");
}

Status FileDescriptorFileSystem::DeleteFile(const string& f) {
  return errors::Unimplemented("Not implemented by the fd filesystem");
}

Status FileDescriptorFileSystem::CreateDir(const string& d) {
  return errors::Unimplemented("Not implemented by the fd filesystem");
}

Status FileDescriptorFileSystem::DeleteDir(const string& d) {
  return errors::Unimplemented("Not implemented by the fd filesystem");
}

Status FileDescriptorFileSystem::RenameFile(const string& s, const string& t) {
  return errors::Unimplemented("Not implemented by the fd filesystem");
}

Status FileDescriptorFileSystem::CanCreateTempFile(const std::string& fname,
                                                   bool* can_create_temp_file) {
  *can_create_temp_file = false;
  return OkStatus();
}

REGISTER_FILE_SYSTEM("fd", FileDescriptorFileSystem);

}  // namespace fcp
}  // namespace tensorflow
