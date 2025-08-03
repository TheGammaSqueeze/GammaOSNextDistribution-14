//
// Copyright (C) 2017 The Android Open Source Project
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

#ifndef UPDATE_ENGINE_PAYLOAD_CONSUMER_CACHED_FILE_DESCRIPTOR_H_
#define UPDATE_ENGINE_PAYLOAD_CONSUMER_CACHED_FILE_DESCRIPTOR_H_

#include <errno.h>
#include <sys/types.h>

#include <memory>
#include <vector>

#include <brillo/secure_blob.h>

#include "update_engine/payload_consumer/file_descriptor.h"

namespace chromeos_update_engine {

class CachedFileDescriptorBase : public FileDescriptor {
 public:
  explicit CachedFileDescriptorBase(size_t cache_size) : cache_(cache_size) {}
  ~CachedFileDescriptorBase() override = default;

  bool Open(const char* path, int flags, mode_t mode) override {
    return GetFd()->Open(path, flags, mode);
  }
  bool Open(const char* path, int flags) override {
    return GetFd()->Open(path, flags);
  }
  ssize_t Read(void* buf, size_t count) override {
    return GetFd()->Read(buf, count);
  }
  ssize_t Write(const void* buf, size_t count) override;
  off64_t Seek(off64_t offset, int whence) override;
  uint64_t BlockDevSize() override { return GetFd()->BlockDevSize(); }
  bool BlkIoctl(int request,
                uint64_t start,
                uint64_t length,
                int* result) override {
    return GetFd()->BlkIoctl(request, start, length, result);
  }
  bool Flush() override;
  bool Close() override;
  bool IsSettingErrno() override { return GetFd()->IsSettingErrno(); }
  bool IsOpen() override { return GetFd()->IsOpen(); }

 protected:
  virtual FileDescriptor* GetFd() = 0;

 private:
  // Internal flush without the need to call |fd_->Flush()|.
  bool FlushCache();

  brillo::Blob cache_;
  size_t bytes_cached_{0};
  off64_t offset_{0};

  DISALLOW_COPY_AND_ASSIGN(CachedFileDescriptorBase);
};

class CachedFileDescriptor final : public CachedFileDescriptorBase {
 public:
  CachedFileDescriptor(FileDescriptorPtr fd, size_t cache_size)
      : CachedFileDescriptorBase(cache_size), fd_(fd) {}

 protected:
  virtual FileDescriptor* GetFd() { return fd_.get(); }
  FileDescriptorPtr fd_;
};

class UnownedCachedFileDescriptor final : public CachedFileDescriptorBase {
 public:
  UnownedCachedFileDescriptor(FileDescriptor* fd, size_t cache_size)
      : CachedFileDescriptorBase(cache_size), fd_(fd) {}
  // used for EnocdeFEC
  void SetFD(FileDescriptor* fd);

 protected:
  virtual FileDescriptor* GetFd() { return fd_; }
  FileDescriptor* fd_;
};

}  // namespace chromeos_update_engine

#endif  // UPDATE_ENGINE_PAYLOAD_CONSUMER_CACHED_FILE_DESCRIPTOR_H_
