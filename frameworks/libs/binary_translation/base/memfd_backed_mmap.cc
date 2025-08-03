/*
 * Copyright (C) 2019 The Android Open Source Project
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

#include "berberis/base/memfd_backed_mmap.h"

#include <sys/mman.h>
#include <unistd.h>

#include "berberis/base/large_mmap.h"
#include "berberis/base/logging.h"
#include "berberis/base/mmap.h"

#include "fd.h"

namespace berberis {

// Creates memfd region of memfd_file_size bytes filled with value.
int CreateAndFillMemfd(const char* name, size_t memfd_file_size, uintptr_t value) {
  const size_t kPageSize = sysconf(_SC_PAGE_SIZE);
  CHECK_EQ(memfd_file_size % sizeof(value), 0);
  CHECK_EQ(memfd_file_size % kPageSize, 0);

  // Use intermediate map to fully initialize file content. It lets compiler
  // optimize the loop below and limits WriteFully to fd to one call. Running
  // the Memfd.uintptr_t test on this showed 4x performance improvement.
  uintptr_t* memfd_file_content = static_cast<uintptr_t*>(MmapOrDie(memfd_file_size));

  for (size_t i = 0; i < memfd_file_size / sizeof(value); ++i) {
    memfd_file_content[i] = value;
  }

  int memfd = CreateMemfdOrDie(name);

  WriteFullyOrDie(memfd, memfd_file_content, memfd_file_size);

  MunmapOrDie(memfd_file_content, memfd_file_size);

  return memfd;
}

// Allocates a region of map_size bytes and backs it in chunks with memfd region
// of memfd_file_size bytes.
void* CreateMemfdBackedMapOrDie(int memfd, size_t map_size, size_t memfd_file_size) {
  const size_t kPageSize = sysconf(_SC_PAGE_SIZE);
  CHECK_EQ(map_size % memfd_file_size, 0);
  CHECK_EQ(memfd_file_size % kPageSize, 0);

  // Reserving memory for the map. Instruct kernel to not commit any RAM to
  // the map by using MAP_NORESERVE. It would be a waste anyways since
  // this region is used to create memfd backed mmaps.
  uint8_t* ptr = static_cast<uint8_t*>(
      LargeMmapImplOrDie({.size = map_size, .flags = MAP_PRIVATE | MAP_ANONYMOUS | MAP_NORESERVE}));

  // map memfd
  for (size_t i = 0; i < map_size / memfd_file_size; ++i) {
    MmapImplOrDie({.addr = ptr + (i * memfd_file_size),
                   .size = memfd_file_size,
                   .flags = MAP_PRIVATE | MAP_FIXED,
                   .fd = memfd});
  }

  return ptr;
}

}  // namespace berberis
