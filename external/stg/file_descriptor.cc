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

#include "file_descriptor.h"

#include <fcntl.h>
#include <unistd.h>

#include <cerrno>
#include <exception>

#include "error.h"

namespace stg {

FileDescriptor::FileDescriptor(const char* filename, int flags, mode_t mode)
    : fd_(open(filename, flags, mode)) {
  if (fd_ < 0) {
    Die() << "open failed: " << Error(errno);
  }
}

FileDescriptor::~FileDescriptor() noexcept(false) {
  // If we're unwinding, ignore any close failure.
  if (fd_ >= 0 && close(fd_) != 0 && std::uncaught_exceptions() == 0) {
    Die() << "close failed: " << Error(errno);
  }
  fd_ = -1;
}


int FileDescriptor::Value() const {
  Check(fd_ >= 0) << "FileDescriptor was not initialized";
  return fd_;
}

}  // namespace stg
