// Copyright (C) 2022 Google LLC
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

#ifndef ICING_FILE_DESTRUCTIBLE_DIRECTORY_H_
#define ICING_FILE_DESTRUCTIBLE_DIRECTORY_H_

#include "icing/file/filesystem.h"
#include "icing/util/logging.h"

namespace icing {
namespace lib {

// A convenient RAII class which will recursively create the directory at the
// specified file path and delete it upon destruction.
class DestructibleDirectory {
 public:
  explicit DestructibleDirectory(const Filesystem* filesystem, std::string dir)
      : filesystem_(filesystem), dir_(std::move(dir)) {
    is_valid_ = filesystem_->CreateDirectoryRecursively(dir_.c_str());
  }

  DestructibleDirectory(const DestructibleDirectory&) = delete;
  DestructibleDirectory& operator=(const DestructibleDirectory&) = delete;

  DestructibleDirectory(DestructibleDirectory&& rhs)
      : filesystem_(nullptr), is_valid_(false) {
    Swap(rhs);
  }

  DestructibleDirectory& operator=(DestructibleDirectory&& rhs) {
    Swap(rhs);
    return *this;
  }

  ~DestructibleDirectory() {
    if (filesystem_ != nullptr &&
        !filesystem_->DeleteDirectoryRecursively(dir_.c_str())) {
      // Swallow deletion failures as there's nothing actionable to do about
      // them.
      ICING_LOG(WARNING) << "Unable to delete temporary directory: " << dir_;
    }
  }

  const std::string& dir() const { return dir_; }

  bool is_valid() const { return is_valid_; }

 private:
  void Swap(DestructibleDirectory& other) {
    std::swap(filesystem_, other.filesystem_);
    std::swap(dir_, other.dir_);
    std::swap(is_valid_, other.is_valid_);
  }

  const Filesystem* filesystem_;
  std::string dir_;
  bool is_valid_;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_FILE_DESTRUCTIBLE_DIRECTORY_H_
