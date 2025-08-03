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

#include "berberis/base/mapped_file_fragment.h"

#include <inttypes.h>
#include <stdlib.h>
#include <sys/mman.h>
#include <sys/user.h>
#include <unistd.h>

#include "berberis/base/checks.h"
#include "berberis/base/page_size.h"

namespace {

const off64_t kPageMask = ~static_cast<off64_t>(berberis::kPageSize - 1);

off64_t page_start(off64_t offset) {
  return offset & kPageMask;
}

size_t page_offset(off64_t offset) {
  return static_cast<size_t>(offset & (berberis::kPageSize - 1));
}

}  // namespace

MappedFileFragment::MappedFileFragment()
    : map_start_(nullptr), map_size_(0), data_(nullptr), size_(0) {}

MappedFileFragment::~MappedFileFragment() {
  if (map_start_ != nullptr) {
    munmap(map_start_, map_size_);
  }
}

bool MappedFileFragment::Map(int fd, off64_t base_offset, size_t elf_offset, size_t size) {
  off64_t offset;
  CHECK(!__builtin_add_overflow(base_offset, elf_offset, &offset));

  off64_t page_min = page_start(offset);
  off64_t end_offset;

  CHECK(!__builtin_add_overflow(offset, size, &end_offset));
  CHECK(!__builtin_add_overflow(end_offset, page_offset(offset), &end_offset));

  size_t map_size = static_cast<size_t>(end_offset - page_min);
  CHECK(map_size >= size);

  uint8_t* map_start =
      static_cast<uint8_t*>(mmap64(nullptr, map_size, PROT_READ, MAP_PRIVATE, fd, page_min));

  if (map_start == MAP_FAILED) {
    return false;
  }

  map_start_ = map_start;
  map_size_ = map_size;

  data_ = map_start + page_offset(offset);
  size_ = size;

  return true;
}
