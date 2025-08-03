// Copyright (C) 2021 Google LLC
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

#ifndef ICING_FILE_DESTRUCTIBLE_FILE_H_
#define ICING_FILE_DESTRUCTIBLE_FILE_H_

#include <unistd.h>

#include <string>

#include "icing/file/filesystem.h"
#include "icing/util/logging.h"

namespace icing {
namespace lib {

// A convenient RAII class which will open the specified file path for write and
// delete the underlying file upon destruction.
class DestructibleFile {
 public:
  explicit DestructibleFile(const std::string& filepath,
                            const Filesystem* filesystem)
      : filesystem_(filesystem), filepath_(filepath) {
    fd_ = filesystem_->OpenForWrite(filepath_.c_str());
  }

  DestructibleFile(const DestructibleFile&) = delete;
  DestructibleFile(DestructibleFile&& other) : filesystem_(nullptr), fd_(-1) {
    *this = std::move(other);
  }

  DestructibleFile& operator=(const DestructibleFile&) = delete;
  DestructibleFile& operator=(DestructibleFile&& other) {
    std::swap(fd_, other.fd_);
    std::swap(filesystem_, other.filesystem_);
    std::swap(filepath_, other.filepath_);
    return *this;
  }

  ~DestructibleFile() {
    if (is_valid()) {
      close(fd_);
      if (!filesystem_->DeleteFile(filepath_.c_str())) {
        ICING_VLOG(1) << "Failed to delete file " << filepath_;
      }
    }
  }

  bool is_valid() const { return fd_ >= 0; }
  int get_fd() const { return fd_; }

 private:
  const Filesystem* filesystem_;
  std::string filepath_;
  int fd_;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_FILE_DESTRUCTIBLE_FILE_H_
