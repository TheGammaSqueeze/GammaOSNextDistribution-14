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

#include "icing/file/memory-mapped-file.h"

#include <sys/mman.h>

#include <cerrno>
#include <cinttypes>
#include <memory>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/absl_ports/canonical_errors.h"
#include "icing/absl_ports/str_cat.h"
#include "icing/file/filesystem.h"
#include "icing/legacy/core/icing-string-util.h"
#include "icing/util/math-util.h"
#include "icing/util/status-macros.h"

namespace icing {
namespace lib {

/* static */ libtextclassifier3::StatusOr<MemoryMappedFile>
MemoryMappedFile::Create(const Filesystem& filesystem,
                         std::string_view file_path, Strategy mmap_strategy,
                         int64_t max_file_size) {
  if (max_file_size <= 0 || max_file_size > kMaxFileSize) {
    return absl_ports::OutOfRangeError(IcingStringUtil::StringPrintf(
        "Invalid max file size %" PRId64 " for MemoryMappedFile",
        max_file_size));
  }

  const std::string file_path_str(file_path);
  int64_t file_size = filesystem.FileExists(file_path_str.c_str())
                          ? filesystem.GetFileSize(file_path_str.c_str())
                          : 0;
  if (file_size == Filesystem::kBadFileSize) {
    return absl_ports::InternalError(
        absl_ports::StrCat("Bad file size for file ", file_path));
  }

  return MemoryMappedFile(filesystem, file_path, mmap_strategy, max_file_size,
                          file_size);
}

/* static */ libtextclassifier3::StatusOr<MemoryMappedFile>
MemoryMappedFile::Create(const Filesystem& filesystem,
                         std::string_view file_path, Strategy mmap_strategy,
                         int64_t max_file_size, int64_t pre_mapping_file_offset,
                         int64_t pre_mapping_mmap_size) {
  if (max_file_size <= 0 || max_file_size > kMaxFileSize) {
    return absl_ports::OutOfRangeError(IcingStringUtil::StringPrintf(
        "Invalid max file size %" PRId64 " for MemoryMappedFile",
        max_file_size));
  }

  // We need at least pre_mapping_file_offset + pre_mapping_mmap_size bytes for
  // the underlying file size, so max_file_size should be at least
  // pre_mapping_file_offset + pre_mapping_mmap_size. Safe integer check.
  if (pre_mapping_file_offset < 0 || pre_mapping_mmap_size < 0 ||
      pre_mapping_file_offset > max_file_size - pre_mapping_mmap_size) {
    return absl_ports::OutOfRangeError(IcingStringUtil::StringPrintf(
        "Invalid pre-mapping file offset %" PRId64 " and mmap size %" PRId64
        " with max file size %" PRId64 "for MemoryMappedFile",
        pre_mapping_file_offset, pre_mapping_mmap_size, max_file_size));
  }

  ICING_ASSIGN_OR_RETURN(
      MemoryMappedFile mmapped_file,
      Create(filesystem, file_path, mmap_strategy, max_file_size));

  if (pre_mapping_mmap_size > 0) {
    ICING_RETURN_IF_ERROR(
        mmapped_file.RemapImpl(pre_mapping_file_offset, pre_mapping_mmap_size));
  }

  return std::move(mmapped_file);
}

MemoryMappedFile::MemoryMappedFile(const Filesystem& filesystem,
                                   std::string_view file_path,
                                   Strategy mmap_strategy,
                                   int64_t max_file_size, int64_t file_size)
    : filesystem_(&filesystem),
      file_path_(file_path),
      strategy_(mmap_strategy),
      max_file_size_(max_file_size),
      file_size_(file_size),
      mmap_result_(nullptr),
      file_offset_(0),
      mmap_size_(0),
      alignment_adjustment_(0) {}

MemoryMappedFile::MemoryMappedFile(MemoryMappedFile&& other)
    // Make sure that mmap_result_ is a nullptr before we call Swap. We don't
    // care what values the remaining members hold before we swap into other,
    // but if mmap_result_ holds a non-NULL value before we initialized anything
    // then other will try to free memory at that address when it's destroyed!
    : mmap_result_(nullptr) {
  Swap(&other);
}

MemoryMappedFile& MemoryMappedFile::operator=(MemoryMappedFile&& other) {
  // Swap all of our elements with other. This will ensure that both this now
  // holds other's previous resources and that this's previous resources will be
  // properly freed when other is destructed at the end of this function.
  Swap(&other);
  return *this;
}

MemoryMappedFile::~MemoryMappedFile() { Unmap(); }

void MemoryMappedFile::MemoryMappedFile::Unmap() {
  if (mmap_result_ != nullptr) {
    munmap(mmap_result_, adjusted_mmap_size());
    mmap_result_ = nullptr;
  }

  file_offset_ = 0;
  mmap_size_ = 0;
  alignment_adjustment_ = 0;
}

libtextclassifier3::Status MemoryMappedFile::Remap(int64_t file_offset,
                                                   int64_t mmap_size) {
  return RemapImpl(file_offset, mmap_size);
}

libtextclassifier3::Status MemoryMappedFile::GrowAndRemapIfNecessary(
    int64_t new_file_offset, int64_t new_mmap_size) {
  // We need at least new_file_offset + new_mmap_size bytes for the underlying
  // file size, and it should not exceed max_file_size_. Safe integer check.
  if (new_file_offset < 0 || new_mmap_size < 0 ||
      new_file_offset > max_file_size_ - new_mmap_size) {
    return absl_ports::OutOfRangeError(IcingStringUtil::StringPrintf(
        "Invalid new file offset %" PRId64 " and new mmap size %" PRId64
        " with max file size %" PRId64 "for MemoryMappedFile",
        new_file_offset, new_mmap_size, max_file_size_));
  }

  if (new_mmap_size == 0) {
    // Unmap any previously mmapped region.
    Unmap();
    return libtextclassifier3::Status::OK;
  }

  ICING_RETURN_IF_ERROR(GrowFileSize(new_file_offset + new_mmap_size));

  if (new_file_offset != file_offset_ || new_mmap_size > mmap_size_) {
    ICING_RETURN_IF_ERROR(RemapImpl(new_file_offset, new_mmap_size));
  }

  return libtextclassifier3::Status::OK;
}

libtextclassifier3::Status MemoryMappedFile::PersistToDisk() {
  if (strategy_ == Strategy::READ_ONLY) {
    return absl_ports::FailedPreconditionError(absl_ports::StrCat(
        "Attempting to PersistToDisk on a read-only file: ", file_path_));
  }

  if (mmap_result_ == nullptr) {
    // Nothing mapped to sync.
    return libtextclassifier3::Status::OK;
  }

  // Sync actual file size via system call.
  int64_t actual_file_size = filesystem_->GetFileSize(file_path_.c_str());
  if (actual_file_size == Filesystem::kBadFileSize) {
    return absl_ports::InternalError("Unable to retrieve file size");
  }
  file_size_ = actual_file_size;

  if (strategy_ == Strategy::READ_WRITE_AUTO_SYNC &&
      // adjusted_mmap_size(), which is the mmap size after alignment
      // adjustment, may be larger than the actual underlying file size since we
      // can pre-mmap a large memory region before growing the file. Therefore,
      // we should std::min with file_size_ - adjusted_offset() as the msync
      // size.
      msync(mmap_result_,
            std::min(file_size_ - adjusted_offset(), adjusted_mmap_size()),
            MS_SYNC) != 0) {
    return absl_ports::InternalError(
        absl_ports::StrCat("Unable to sync file using msync(): ", file_path_));
  }

  // In order to prevent automatic syncing of changes, files that use the
  // READ_WRITE_MANUAL_SYNC strategy are mmapped using MAP_PRIVATE. Such files
  // can't be synced using msync(). So, we have to directly write to the
  // underlying file to update it.
  if (strategy_ == Strategy::READ_WRITE_MANUAL_SYNC &&
      // Contents before file_offset_ won't be modified by the caller, so we
      // only need to PWrite contents starting at file_offset_. mmap_size_ may
      // be larger than the actual underlying file size since we can pre-mmap a
      // large memory before growing the file. Therefore, we should std::min
      // with file_size_ - file_offset_ as the PWrite size.
      !filesystem_->PWrite(file_path_.c_str(), file_offset_, region(),
                           std::min(mmap_size_, file_size_ - file_offset_))) {
    return absl_ports::InternalError(
        absl_ports::StrCat("Unable to sync file using PWrite(): ", file_path_));
  }

  return libtextclassifier3::Status::OK;
}

libtextclassifier3::Status MemoryMappedFile::OptimizeFor(
    AccessPattern access_pattern) {
  int madvise_flag = 0;
  if (access_pattern == AccessPattern::ACCESS_ALL) {
    madvise_flag = MADV_WILLNEED;
  } else if (access_pattern == AccessPattern::ACCESS_NONE) {
    madvise_flag = MADV_DONTNEED;
  } else if (access_pattern == AccessPattern::ACCESS_RANDOM) {
    madvise_flag = MADV_RANDOM;
  } else if (access_pattern == AccessPattern::ACCESS_SEQUENTIAL) {
    madvise_flag = MADV_SEQUENTIAL;
  }

  if (madvise(mmap_result_, adjusted_mmap_size(), madvise_flag) != 0) {
    return absl_ports::InternalError(absl_ports::StrCat(
        "Unable to madvise file ", file_path_, "; Error: ", strerror(errno)));
  }
  return libtextclassifier3::Status::OK;
}

libtextclassifier3::Status MemoryMappedFile::GrowFileSize(
    int64_t new_file_size) {
  // Early return if new_file_size doesn't exceed the cached file size
  // (file_size_). It saves a system call for getting the actual file size and
  // reduces latency significantly.
  if (new_file_size <= file_size_) {
    return libtextclassifier3::Status::OK;
  }

  if (new_file_size > max_file_size_) {
    return absl_ports::OutOfRangeError(IcingStringUtil::StringPrintf(
        "new file size %" PRId64 " exceeds maximum file size allowed, %" PRId64
        " bytes",
        new_file_size, max_file_size_));
  }

  // Sync actual file size via system call.
  int64_t actual_file_size = filesystem_->GetFileSize(file_path_.c_str());
  if (actual_file_size == Filesystem::kBadFileSize) {
    return absl_ports::InternalError("Unable to retrieve file size");
  }
  file_size_ = actual_file_size;

  // Early return again if new_file_size doesn't exceed actual_file_size. It
  // saves system calls for opening and closing file descriptor.
  if (new_file_size <= actual_file_size) {
    return libtextclassifier3::Status::OK;
  }

  if (strategy_ == Strategy::READ_ONLY) {
    return absl_ports::FailedPreconditionError(absl_ports::StrCat(
        "Attempting to grow a read-only file: ", file_path_));
  }

  // We use Write here rather than Grow because Grow doesn't actually allocate
  // an underlying disk block. This can lead to problems with mmap because mmap
  // has no effective way to signal that it was impossible to allocate the disk
  // block and ends up crashing instead. Write will force the allocation of
  // these blocks, which will ensure that any failure to grow will surface here.
  int64_t page_size = system_page_size();
  auto buf = std::make_unique<uint8_t[]>(page_size);
  int64_t size_to_write = std::min(page_size - (file_size_ % page_size),
                                   new_file_size - file_size_);
  ScopedFd sfd(filesystem_->OpenForAppend(file_path_.c_str()));
  if (!sfd.is_valid()) {
    return absl_ports::InternalError(
        absl_ports::StrCat("Couldn't open file ", file_path_));
  }
  while (size_to_write > 0 && file_size_ < new_file_size) {
    if (!filesystem_->Write(sfd.get(), buf.get(), size_to_write)) {
      return absl_ports::InternalError(
          absl_ports::StrCat("Couldn't grow file ", file_path_));
    }
    file_size_ += size_to_write;
    size_to_write = std::min(page_size - (file_size_ % page_size),
                             new_file_size - file_size_);
  }

  return libtextclassifier3::Status::OK;
}

libtextclassifier3::Status MemoryMappedFile::RemapImpl(int64_t new_file_offset,
                                                       int64_t new_mmap_size) {
  if (new_file_offset < 0) {
    return absl_ports::OutOfRangeError("Invalid file offset");
  }

  if (new_mmap_size < 0) {
    return absl_ports::OutOfRangeError("Invalid mmap size");
  }

  if (new_mmap_size == 0) {
    // First unmap any previously mmapped region.
    Unmap();
    return libtextclassifier3::Status::OK;
  }

  int64_t new_aligned_offset =
      math_util::RoundDownTo(new_file_offset, system_page_size());
  int64_t new_alignment_adjustment = new_file_offset - new_aligned_offset;
  int64_t new_adjusted_mmap_size = new_alignment_adjustment + new_mmap_size;

  int mmap_flags = 0;
  // Determines if the mapped region should just be readable or also writable.
  int protection_flags = 0;
  ScopedFd fd;
  switch (strategy_) {
    case Strategy::READ_ONLY: {
      mmap_flags = MAP_PRIVATE;
      protection_flags = PROT_READ;
      fd.reset(filesystem_->OpenForRead(file_path_.c_str()));
      break;
    }
    case Strategy::READ_WRITE_AUTO_SYNC: {
      mmap_flags = MAP_SHARED;
      protection_flags = PROT_READ | PROT_WRITE;
      fd.reset(filesystem_->OpenForWrite(file_path_.c_str()));
      break;
    }
    case Strategy::READ_WRITE_MANUAL_SYNC: {
      mmap_flags = MAP_PRIVATE;
      protection_flags = PROT_READ | PROT_WRITE;
      // TODO(cassiewang) MAP_PRIVATE effectively makes it a read-only file.
      // figure out if we can open this file in read-only mode.
      fd.reset(filesystem_->OpenForWrite(file_path_.c_str()));
      break;
    }
    default:
      return absl_ports::UnknownError(IcingStringUtil::StringPrintf(
          "Invalid value in switch statement: %d", strategy_));
  }

  if (!fd.is_valid()) {
    return absl_ports::InternalError(absl_ports::StrCat(
        "Unable to open file meant to be mmapped: ", file_path_));
  }

  void* new_mmap_result =
      mmap(nullptr, new_adjusted_mmap_size, protection_flags, mmap_flags,
           fd.get(), new_aligned_offset);

  if (new_mmap_result == MAP_FAILED) {
    new_mmap_result = nullptr;
    return absl_ports::InternalError(absl_ports::StrCat(
        "Failed to mmap region due to error: ", strerror(errno)));
  }

  // Now we know that we have successfully created a new mapping. We can free
  // the old one and switch to the new one.
  Unmap();

  mmap_result_ = new_mmap_result;
  file_offset_ = new_file_offset;
  mmap_size_ = new_mmap_size;
  alignment_adjustment_ = new_alignment_adjustment;
  return libtextclassifier3::Status::OK;
}

void MemoryMappedFile::Swap(MemoryMappedFile* other) {
  std::swap(filesystem_, other->filesystem_);
  std::swap(file_path_, other->file_path_);
  std::swap(strategy_, other->strategy_);
  std::swap(max_file_size_, other->max_file_size_);
  std::swap(file_size_, other->file_size_);
  std::swap(mmap_result_, other->mmap_result_);
  std::swap(file_offset_, other->file_offset_);
  std::swap(mmap_size_, other->mmap_size_);
  std::swap(alignment_adjustment_, other->alignment_adjustment_);
}

}  // namespace lib
}  // namespace icing
