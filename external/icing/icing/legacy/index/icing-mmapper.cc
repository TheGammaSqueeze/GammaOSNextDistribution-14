// Copyright (C) 2019 Google LLC
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

// Copyright 2012 Google Inc. All Rights Reserved.
// Author: sbanacho@google.com (Scott Banachowski)
//
#include "icing/legacy/index/icing-mmapper.h"

#include <sys/mman.h>

#include <cerrno>
#include <cstring>

#include "icing/legacy/core/icing-string-util.h"
#include "icing/legacy/index/icing-filesystem.h"
#include "icing/util/logging.h"

namespace icing {
namespace lib {

IcingMMapper::IcingMMapper(bool read_only, int flags)
    : address_(nullptr),
      len_(0),
      flags_(flags),
      location_(0),
      mmap_len_(0),
      mmap_result_(nullptr),
      read_only_(read_only) {}

IcingMMapper::IcingMMapper(int fd, bool read_only, uint64_t location,
                           size_t size, int flags)
    : address_(nullptr),
      len_(0),
      flags_(flags),
      location_(0),
      mmap_len_(0),
      mmap_result_(nullptr),
      read_only_(read_only) {
  DoMapping(fd, location, size);
}

void IcingMMapper::DoMapping(int fd, uint64_t location, size_t size) {
  uint64_t aligned_offset =
      (location / system_page_size()) * system_page_size();
  size_t alignment_adjustment = location - aligned_offset;
  size_t mmap_len = alignment_adjustment + size;

  int prot = read_only_ ? (PROT_READ) : (PROT_READ | PROT_WRITE);

  mmap_result_ = mmap(nullptr, mmap_len, prot, flags_, fd, aligned_offset);

  if (mmap_result_ != MAP_FAILED) {
    len_ = size;
    location_ = location;
    mmap_len_ = mmap_len;
    address_ = reinterpret_cast<uint8_t *>(mmap_result_) + alignment_adjustment;
  } else {
    const char *errstr = strerror(errno);
    ICING_LOG(ERROR) << "Could not mmap file for reading: " << errstr;
    mmap_result_ = nullptr;
  }
}

bool IcingMMapper::Remap(int fd, uint64_t location, size_t size) {
  Unmap();
  DoMapping(fd, location, size);
  return is_valid();
}

void IcingMMapper::Unmap() {
  if (mmap_result_ != nullptr) {
    munmap(mmap_result_, mmap_len_);
  }
  address_ = nullptr;
  len_ = 0;
  location_ = 0;
  mmap_len_ = 0;
  mmap_result_ = nullptr;
}

IcingMMapper::~IcingMMapper() { Unmap(); }

bool IcingMMapper::Sync() {
  if (is_valid() && !read_only_) {
    if (msync(mmap_result_, mmap_len_, MS_SYNC) != 0) {
      ICING_LOG(ERROR) << "msync failed: " << strerror(errno);
      return false;
    }
  }
  return true;
}

}  // namespace lib
}  // namespace icing
