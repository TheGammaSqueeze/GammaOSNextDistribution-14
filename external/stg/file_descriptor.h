// SPDX-License-Identifier: Apache-2.0 WITH LLVM-exception
// -*- mode: C++ -*-
//
// Copyright 2022 Google LLC
//
// Licensed under the Apache License v2.0 with LLVM Exceptions (the
// "License"); you may not use this file except in compliance with the
// License.  You may obtain a copy of the License at
//
//     https://llvm.org/LICENSE.txt
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
// Author: Aleksei Vetrov
// Author: Matthias Maennich

#ifndef STG_FILE_DESCRIPTOR_H_
#define STG_FILE_DESCRIPTOR_H_

#include <sys/stat.h>  // for mode_t

#include <utility>

namespace stg {

// RAII wrapper over file descriptor
class FileDescriptor {
 public:
  FileDescriptor() = default;
  FileDescriptor(const char* filename, int flags, mode_t mode = 0);
  FileDescriptor(const FileDescriptor&) = delete;
  FileDescriptor& operator=(const FileDescriptor&) = delete;
  FileDescriptor(FileDescriptor&& other) noexcept {
    std::swap(fd_, other.fd_);
  }
  FileDescriptor& operator=(FileDescriptor&& other) = delete;
  ~FileDescriptor() noexcept(false);

  int Value() const;

 private:
  int fd_ = -1;
};

}  // namespace stg

#endif  // STG_FILE_DESCRIPTOR_H_
