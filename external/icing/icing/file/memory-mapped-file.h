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

// Allows memory-mapping a full file or a specific region within the file.
// It also supports efficiently switching the region being mapped.
//
// Note on Performance:
// It supports different optimized strategies for common patterns on both
// read-only and read-write files. This includes using read-ahead buffers for
// faster reads as well as background-sync vs manual-sync of changes to disk.
// For more details, see comments at MemoryMappedFile::Strategy.
//
// ** Usage 1: pre-mmap large memory and grow the underlying file internally **
//
// // Create MemoryMappedFile instance.
// ICING_ASSIGN_OR_RETURN(
//     std::unique_ptr<MemoryMappedFile> mmapped_file,
//     MemoryMappedFile::Create(filesystem, "/file.pb",
//                              READ_WRITE_AUTO_SYNC,
//                              max_file_size,
//                              /*pre_mapping_file_offset=*/0,
//                              /*pre_mapping_mmap_size=*/1024 * 1024));
//
// // Found that we need 4K bytes for the file and mmapped region.
// mmapped_file->GrowAndRemapIfNecessary(
//     /*new_file_offset=*/0, /*new_mmap_size=*/4 * 1024);
// char read_byte = mmapped_file->region()[4000];
// mmapped_file->mutable_region()[4001] = write_byte;
//
// mmapped_file->PersistToDisk(); // Optional; immediately writes changes to
// disk.
//
// // Found that we need 2048 * 1024 bytes for the file and mmapped region.
// mmapped_file->GrowAndRemapIfNecessary(
//     /*new_file_offset=*/0, /*new_mmap_size=*/2048 * 1024);
// mmapped_file->mutable_region()[2000 * 1024] = write_byte;
// mmapped_file.reset();
//
// ** Usage 2: load by segments **
//
// ICING_ASSIGN_OR_RETURN(
//     std::unique_ptr<MemoryMappedFile> mmapped_file,
//     MemoryMappedFile::Create(filesystem, "/file.pb",
//                              READ_WRITE_AUTO_SYNC,
//                              max_file_size,
//                              /*pre_mapping_file_offset=*/0,
//                              /*pre_mapping_mmap_size=*/16 * 1024));
//
// // load the first 16K.
// mmapped_file->GrowAndRemapIfNecessary(
//     /*new_file_offset=*/0, /*new_mmap_size=*/16 * 1024);
// char read_byte = mmapped_file->region()[100];
// mmapped_file->mutable_region()[10] = write_byte;
//
// mmapped_file->PersistToDisk(); // Optional; immediately writes changes to
// disk.
//
// // load the next 16K.
// mmapped_file->GrowAndRemapIfNecessary(
//     /*new_file_offset=*/16 * 1024, /*new_mmap_size=*/16 * 1024);
// mmapped_file->mutable_region()[10] = write_byte;
// mmapped_file.reset();

#ifndef ICING_FILE_MEMORY_MAPPED_FILE_H_
#define ICING_FILE_MEMORY_MAPPED_FILE_H_

#include <unistd.h>

#include <algorithm>
#include <cstdint>
#include <memory>
#include <string>
#include <string_view>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/file/filesystem.h"

namespace icing {
namespace lib {

class MemoryMappedFile {
 public:
  static int64_t __attribute__((const)) system_page_size() {
    static const int64_t page_size =
        static_cast<int64_t>(sysconf(_SC_PAGE_SIZE));
    return page_size;
  }

  enum Strategy {
    // Memory map a read-only file into a read-only memory region.
    READ_ONLY,

    // Memory map a read-write file into a writable memory region. Any changes
    // made to the region are automatically flushed to the underlying file in
    // the background.
    READ_WRITE_AUTO_SYNC,

    // Memory map a read-write file into a writable memory region. Changes made
    // to this region will never be auto-synced to the underlying file. Unless
    // the caller explicitly calls PersistToDisk(), all changes will be lost
    // when the MemoryMappedFile is destroyed.
    READ_WRITE_MANUAL_SYNC,
  };

  // Absolute max file size, 16 GiB.
  static constexpr int64_t kMaxFileSize = INT64_C(1) << 34;

  // Default max file size, 1 MiB.
  static constexpr int64_t kDefaultMaxFileSize = INT64_C(1) << 20;

  // Creates a new MemoryMappedFile to read/write content to.
  //
  // filesystem    : Object to make system level calls
  // file_path     : Full path of the file that needs to be memory-mapped.
  // mmap_strategy : Strategy/optimizations to access the content.
  // max_file_size : Maximum file size for MemoryMappedFile, default
  //                 kDefaultMaxFileSize.
  //
  // Returns:
  //   A MemoryMappedFile instance on success
  //   OUT_OF_RANGE_ERROR if max_file_size is invalid
  //   INTERNAL_ERROR on I/O error
  static libtextclassifier3::StatusOr<MemoryMappedFile> Create(
      const Filesystem& filesystem, std::string_view file_path,
      Strategy mmap_strategy, int64_t max_file_size = kDefaultMaxFileSize);

  // Creates a new MemoryMappedFile to read/write content to. It remaps when
  // creating the instance, but doesn't check or grow the actual file size, so
  // the caller should call GrowAndRemapIfNecessary before accessing region.
  //
  // filesystem    : Object to make system level calls
  // file_path     : Full path of the file that needs to be memory-mapped.
  // mmap_strategy : Strategy/optimizations to access the content.
  // max_file_size : Maximum file size for MemoryMappedFile.
  // pre_mapping_file_offset : The offset of the file to be memory mapped.
  // pre_mapping_mmap_size   : mmap size for pre-mapping.
  //
  // Returns:
  //   A MemoryMappedFile instance on success
  //   OUT_OF_RANGE_ERROR if max_file_size, file_offset, or mmap_size is invalid
  //   INTERNAL_ERROR on I/O error
  static libtextclassifier3::StatusOr<MemoryMappedFile> Create(
      const Filesystem& filesystem, std::string_view file_path,
      Strategy mmap_strategy, int64_t max_file_size,
      int64_t pre_mapping_file_offset, int64_t pre_mapping_mmap_size);

  // Delete copy constructor and assignment operator.
  MemoryMappedFile(const MemoryMappedFile& other) = delete;
  MemoryMappedFile& operator=(const MemoryMappedFile& other) = delete;

  MemoryMappedFile(MemoryMappedFile&& other);
  MemoryMappedFile& operator=(MemoryMappedFile&& other);

  // Frees any region that is still memory-mapped region.
  ~MemoryMappedFile();

  // TODO(b/247671531): migrate all callers to use GrowAndRemapIfNecessary and
  // deprecate this API.
  //
  // Memory-map the newly specified region within the file specified by
  // file_offset and mmap_size. Unmaps any previously mmapped region.
  // It doesn't handle the underlying file growth.
  //
  // Returns any encountered IO error.
  libtextclassifier3::Status Remap(int64_t file_offset, int64_t mmap_size);

  // Attempt to memory-map the newly specified region within the file specified
  // by new_file_offset and new_mmap_size. It handles mmap and file growth
  // intelligently.
  // - Compute least file size needed according to new_file_offset and
  //   new_mmap_size, and compare with the current file size. If requiring file
  //   growth, then grow the underlying file (Write) or return error if
  //   strategy_ is READ_ONLY.
  // - If new_file_offset is different from the current file_offset_ or
  //   new_mmap_size is greater than the current mmap_size_, then memory-map
  //   the newly specified region and unmap any previously mmapped region.
  //
  // This API is useful for file growth since it grows the underlying file
  // internally and handles remapping intelligently. By pre-mmapping a large
  // memory, we only need to grow the underlying file (Write) without remapping
  // in each round of growth, which significantly reduces the cost of system
  // call and memory paging after remap.
  //
  // Returns:
  //   OK on success
  //   OUT_OF_RANGE_ERROR if new_file_offset and new_mmap_size is invalid
  //   Any error from GrowFileSize() and RemapImpl()
  libtextclassifier3::Status GrowAndRemapIfNecessary(int64_t new_file_offset,
                                                     int64_t new_mmap_size);

  // unmap and free-up the region that has currently been memory mapped.
  void Unmap();

  // Explicitly persist any changes made to the currently mapped region to disk.
  //
  // NOTE: This is only valid if Strategy=READ_WRITE was used.
  //
  // Returns:
  //   OK on success
  //   INTERNAL on I/O error
  //   FAILED_PRECONDITION if Strategy is not implemented
  libtextclassifier3::Status PersistToDisk();

  // Advise the system to help it optimize the memory-mapped region for
  // upcoming read/write operations.
  //
  // NOTE: See linux documentation of madvise() for additional details.
  enum AccessPattern {
    // Future memory access are expected to be in random order. So, readhead
    // will have limited impact on latency.
    ACCESS_RANDOM,

    // Future memory access are expected to be sequential. So, some readahead
    // can greatly improve latency.
    ACCESS_SEQUENTIAL,

    // Future memory access is expected to be high-volume and all over the file.
    // So, preloading the whole region into memory would greatly improve
    // latency.
    ACCESS_ALL,

    // Future memory access is expected to be rare. So, it is best to free up
    // as much of preloaded memory as possible.
    ACCESS_NONE,
  };
  libtextclassifier3::Status OptimizeFor(AccessPattern access_pattern);

  Strategy strategy() const { return strategy_; }

  int64_t max_file_size() const { return max_file_size_; }

  // Accessors to the memory-mapped region. Returns null if nothing is mapped.
  const char* region() const {
    return reinterpret_cast<const char*>(mmap_result_) + alignment_adjustment_;
  }
  char* mutable_region() {
    return reinterpret_cast<char*>(mmap_result_) + alignment_adjustment_;
  }

  int64_t file_offset() const { return file_offset_; }

  // TODO(b/247671531): remove this API after migrating all callers to use
  //                    GrowAndRemapIfNecessary.
  int64_t region_size() const { return mmap_size_; }

  // The size that is safe for the client to read/write. This is only valid for
  // callers that use GrowAndRemapIfNecessary.
  int64_t available_size() const {
    return std::min(mmap_size_,
                    std::max(INT64_C(0), file_size_ - file_offset_));
  }

 private:
  explicit MemoryMappedFile(const Filesystem& filesystem,
                            std::string_view file_path, Strategy mmap_strategy,
                            int64_t max_file_size, int64_t file_size);

  // Grow the underlying file to new_file_size.
  // Note: it is possible that Write() (implemented in the file system call
  // library) grows the underlying file partially and returns error due to
  // failures, so the cached file_size_ may contain out-of-date value, but it is
  // still guaranteed that file_size_ is always smaller or equal to the actual
  // file size. In the next round of growing:
  // - If new_file_size is not greater than file_size_, then we're still
  //   confident that the actual file size is large enough and therefore skip
  //   the grow process.
  // - If new_file_size is greater than file_size_, then we will invoke the
  //   system call to sync the actual file size. At this moment, file_size_ is
  //   the actual file size and therefore we can grow the underlying file size
  //   correctly.
  //
  // Returns:
  //   OK on success
  //   FAILED_PRECONDITION_ERROR if requiring file growth and strategy_ is
  //                             READ_ONLY
  //   OUT_OF_RANGE_ERROR if new_mmap_size exceeds max_file_size_
  //   INTERNAL_ERROR on I/O error
  libtextclassifier3::Status GrowFileSize(int64_t new_file_size);

  // Memory-map the newly specified region within the file specified by
  // new_file_offset and new_mmap_size. Unmaps any previously mmapped region.
  // It doesn't handle the underlying file growth.
  //
  // Returns:
  //   OK on success
  //   OUT_OF_RANGE_ERROR if new_file_offset and new_mmap_size is invalid
  //   INTERNAL_ERROR on I/O error
  libtextclassifier3::Status RemapImpl(int64_t new_file_offset,
                                       int64_t new_mmap_size);

  // Swaps the contents of this with other.
  void Swap(MemoryMappedFile* other);

  int64_t adjusted_offset() const {
    return file_offset_ - alignment_adjustment_;
  }

  int64_t adjusted_mmap_size() const {
    return alignment_adjustment_ + mmap_size_;
  }

  // Cached constructor params.
  const Filesystem* filesystem_;
  std::string file_path_;
  Strategy strategy_;

  // Raw file related fields:
  // - max_file_size_
  // - file_size_

  // Max file size for MemoryMappedFile. It should not exceed the absolute max
  // size of memory mapped file (kMaxFileSize). It is only used in
  // GrowAndRemapIfNecessary(), the new API that handles underlying file growth
  // internally and remaps intelligently.
  //
  // Note: max_file_size_ will be specified in runtime and the caller should
  // make sure its value is correct and reasonable.
  int64_t max_file_size_;

  // Cached file size to avoid calling system call too frequently. It is only
  // used in GrowAndRemapIfNecessary(), the new API that handles underlying file
  // growth internally and remaps intelligently.
  //
  // Note: it is guaranteed that file_size_ is smaller or equal to the actual
  // file size as long as the underlying file hasn't been truncated or deleted
  // externally. See GrowFileSize() for more details.
  int64_t file_size_;

  // Memory mapped related fields:
  // - mmap_result_
  // - file_offset_
  // - alignment_adjustment_
  // - mmap_size_

  // Raw pointer (or error) returned by calls to mmap().
  void* mmap_result_;

  // Offset within the file at which the current memory-mapped region starts.
  int64_t file_offset_;

  // Size that is currently memory-mapped.
  // Note that the mmapped size can be larger than the underlying file size. We
  // can reduce remapping by pre-mmapping a large memory and grow the file size
  // later. See GrowAndRemapIfNecessary().
  int64_t mmap_size_;

  // The difference between file_offset_ and the actual adjusted (aligned)
  // offset.
  // Since mmap requires the offset to be a multiple of system page size, we
  // have to align file_offset_ to the last multiple of system page size.
  int64_t alignment_adjustment_;

  // E.g. system_page_size = 5, RemapImpl(/*new_file_offset=*/8, mmap_size)
  //
  // File layout:               xxxxx xxxxx xxxxx xxxxx xxxxx xx
  // file_offset_:                       8
  // adjusted_offset():               5
  // region()/mutable_region():          |
  // mmap_result_:                    |
  //
  // alignment_adjustment_: file_offset_ - adjusted_offset()
  // mmap_size_:            mmap_size
  // region_size():         mmap_size_
  // available_size():      std::min(mmap_size_,
  //                                 std::max(0, file_size_ - file_offset_))
  // region_range:          [file_offset_, file_offset + mmap_size)
  // adjusted_mmap_size():  alignment_adjustment_ + mmap_size_
  // adjusted_mmap_range:   [alignment_offset, file_offset + mmap_size)
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_FILE_MEMORY_MAPPED_FILE_H_
