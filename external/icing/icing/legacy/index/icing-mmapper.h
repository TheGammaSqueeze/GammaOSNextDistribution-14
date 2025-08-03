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
// This class is a helper for mmapping a file.
// Use as a scoped allocator, the memory is mapped
// on construction and released on destruction.

#ifndef ICING_LEGACY_INDEX_ICING_MMAPPER_H_
#define ICING_LEGACY_INDEX_ICING_MMAPPER_H_

#include <unistd.h>

#include <cstddef>
#include <cstdint>

namespace icing {
namespace lib {

class IcingMMapper {
 public:
  // Provide a valid, open file description (with matching permissions
  // for read or write).  The location into the file you wish to map,
  // and the size. "flags" are passed in directly to mmap.
  IcingMMapper(int fd, bool read_only, uint64_t location, size_t size,
               int flags);

  // Set up Mmapper, but delay mapping until Remap is called.
  IcingMMapper(bool read_only, int flags);

  // Will unmap the region on delete.  Does not close the file.
  ~IcingMMapper();

  // Move the location of the mapping to a new location.  Returns
  // true if valid.
  bool Remap(int fd, uint64_t location, size_t size);

  // Close the mapping and become invalid.
  void Unmap();

  // Sync the mapped region to the filesystem.
  bool Sync();

  // Check to see if the file was successfully mapped.
  bool is_valid() const { return (address_ != nullptr); }

  // The address in memory of the mapped file, returns NULL if the
  // mapping of the region was unsuccesful.
  const uint8_t *address() const { return address_; }

  uint8_t *address() { return address_; }

  size_t len() const { return len_; }

  uint64_t location() const { return location_; }

  static size_t __attribute__((const)) system_page_size() {
    static const size_t page_size = sysconf(_SC_PAGE_SIZE);
    return page_size;
  }

  // Rounds `size` up to a multiple of the system page size.
  static size_t page_aligned_size(uint32_t size) {
    return ((size + system_page_size() - 1) / system_page_size()) *
           system_page_size();
  }

 private:
  void DoMapping(int fd, uint64_t location, size_t size);

  uint8_t *address_;
  size_t len_;         // the requested mapping length
  const int flags_;    // flags passed in to mmap
  uint64_t location_;  // the requested mapping file location
  size_t mmap_len_;    // the actual mapping length
  void *mmap_result_;
  const bool read_only_;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_LEGACY_INDEX_ICING_MMAPPER_H_
