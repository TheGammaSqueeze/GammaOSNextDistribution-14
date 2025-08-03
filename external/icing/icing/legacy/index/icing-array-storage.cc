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

#include "icing/legacy/index/icing-array-storage.h"

#include <sys/mman.h>

#include <algorithm>
#include <cinttypes>

#include "icing/legacy/core/icing-string-util.h"
#include "icing/legacy/core/icing-timer.h"
#include "icing/legacy/index/icing-bit-util.h"
#include "icing/legacy/index/icing-filesystem.h"
#include "icing/legacy/index/icing-mmapper.h"
#include "icing/util/logging.h"

using std::max;
using std::min;
using std::vector;

namespace icing {
namespace lib {

namespace {

// Do the cast and const dance.
void *MakeVoidPtr(const void *ptr) { return const_cast<void *>(ptr); }

}  // namespace

const uint32_t IcingArrayStorage::kPartialCrcLimitDiv = 8;  // limit is 1/8th
const size_t IcingArrayStorage::kGrowElts = 1u << 14;       // 16KB

IcingArrayStorage::IcingArrayStorage(const IcingFilesystem &filesystem)
    : mmapper_(nullptr), filesystem_(filesystem) {
  Reset();
}

IcingArrayStorage::~IcingArrayStorage() { delete mmapper_; }

bool IcingArrayStorage::Init(int fd, size_t fd_offset, bool map_shared,
                             uint32_t elt_size, uint32_t num_elts,
                             uint32_t max_num_elts, uint32_t *crc_ptr,
                             bool init_crc) {
  if (is_initialized()) {
    return true;
  }

  // Compute capacity_num_.
  uint64_t file_size = filesystem_.GetFileSize(fd);
  if (file_size == IcingFilesystem::kBadFileSize) {
    ICING_LOG(ERROR) << "Array storage could not get file size";
    return false;
  }
  if (file_size < fd_offset) {
    ICING_LOG(ERROR) << "Array storage file size " << file_size << " less than offset " << fd_offset;
    return false;
  }

  uint32_t capacity_num_elts = (file_size - fd_offset) / elt_size;
  if (capacity_num_elts < num_elts) {
    ICING_LOG(ERROR) << "Array storage num elts " << num_elts << " > capacity num elts " << capacity_num_elts;
    return false;
  }

  // Map beyond the capacity. We will grow underlying file to avoid
  // SIGBUS.
  mmapper_ = new IcingMMapper(fd, false, fd_offset, max_num_elts * elt_size,
                              map_shared ? MAP_SHARED : MAP_PRIVATE);
  if (!mmapper_->is_valid()) {
    ICING_LOG(ERROR) << "Array storage map failed";
    delete mmapper_;
    mmapper_ = nullptr;
    return false;
  }

  fd_ = fd;
  fd_offset_ = fd_offset;
  map_shared_ = map_shared;
  elt_size_ = elt_size;
  // changes_end_ refers to the last element that was included in the
  // current crc. If we change it, we must also update *crc_ptr_ to
  // 0. Otherwise UpdateCrc will fail.
  cur_num_ = changes_end_ = num_elts;
  max_num_ = max_num_elts;
  capacity_num_ = capacity_num_elts;
  crc_ptr_ = crc_ptr;

  if (crc_ptr_) {
    uint32_t crc = IcingStringUtil::UpdateCrc32(0, array_cast<char>(),
                                                cur_num_ * elt_size_);
    if (init_crc) {
      *crc_ptr_ = crc;
    } else if (crc != *crc_ptr_) {
      ICING_LOG(ERROR) << "Array storage bad crc " << crc << " vs " << *crc_ptr_;
      goto failed;
    }
  }
  return true;

failed:
  Reset();
  return false;
}

void IcingArrayStorage::Reset() {
  fd_ = -1;
  fd_offset_ = 0;
  map_shared_ = false;
  delete mmapper_;
  mmapper_ = nullptr;
  elt_size_ = 0;
  cur_num_ = 0;
  changes_end_ = 0;
  max_num_ = 0;
  capacity_num_ = 0;
  crc_ptr_ = nullptr;
  changes_.clear();
  saved_orig_buf_.clear();
  dirty_pages_.clear();
}

void IcingArrayStorage::Truncate(uint32_t len) {
  if (len > cur_num_) {
    ICING_LOG(FATAL) << "Length exceeds current size";
  }

  cur_num_ = len;
}

void *IcingArrayStorage::GetMutableMemInternal(uint32_t elt_idx,
                                               uint32_t elt_len) {
  uint32_t start_byte = elt_idx * elt_size_;
  uint32_t len_bytes = elt_len * elt_size_;

  if (!GrowIfNecessary(elt_idx + elt_len)) {
    return nullptr;
  }

  cur_num_ = max(cur_num_, elt_idx + elt_len);

  if (crc_ptr_) {
    // Cache original value to update crcs.
    if (elt_idx < changes_end_) {
      uint32_t change_len = min(changes_end_, elt_idx + elt_len) - elt_idx;

      // If we exceed kPartialCrcLimitDiv, clear changes_end_ to
      // revert to full CRC.
      if ((saved_orig_buf_.size() + change_len * elt_size_) *
              kPartialCrcLimitDiv >
          changes_end_ * elt_size_) {
        ICING_VLOG(2) << "Array storage change tracking limit exceeded";
        changes_.clear();
        saved_orig_buf_.clear();
        changes_end_ = 0;
        *crc_ptr_ = 0;
      } else {
        changes_.push_back(Change(elt_idx, change_len));
        saved_orig_buf_.append(array_cast<char>() + start_byte,
                               change_len * elt_size_);
      }
    }
  }

  if (!map_shared_) {
    // Mark dirty pages.
    int start_page = start_byte / IcingMMapper::system_page_size();
    int end_page =
        (start_byte + len_bytes - 1) / IcingMMapper::system_page_size();

    for (int i = start_page; i <= end_page; i++) {
      if (static_cast<size_t>(i) >= dirty_pages_.size()) {
        dirty_pages_.resize(i + 1);
      }
      dirty_pages_[i] = true;
    }
  }

  return MakeVoidPtr(&(array())[start_byte]);
}

bool IcingArrayStorage::GrowIfNecessary(uint32_t num_elts) {
  if (num_elts <= capacity_num_) return true;
  if (num_elts > max_num_) return false;

  // Need to grow.
  uint64_t new_file_size = fd_offset_ + uint64_t{num_elts} * elt_size_;
  // Grow to kGrowElts boundary.
  new_file_size = AlignUp(new_file_size, kGrowElts * elt_size_);
  if (!filesystem_.Grow(fd_, new_file_size)) {
    return false;
  }
  capacity_num_ = (new_file_size - fd_offset_) / elt_size_;
  return true;
}

void IcingArrayStorage::UpdateCrc() {
  if (!crc_ptr_) return;

  // First apply the modified area. Keep a bitmap of already updated
  // regions so we don't double-update.
  vector<bool> updated(changes_end_);
  uint32_t cur_offset = 0;
  uint32_t cur_crc = *crc_ptr_;
  int num_partial_crcs = 0;
  int num_truncated = 0;
  int num_overlapped = 0;
  int num_duplicate = 0;
  for (size_t i = 0; i < changes_.size(); i++) {
    const Change &change = changes_[i];
    if (change.elt_offset + change.elt_len > changes_end_) {
      ICING_LOG(FATAL) << "Off " << change.elt_offset << " len "
                       << change.elt_len << " end " << changes_end_;
    }

    // Skip truncated tracked changes.
    if (change.elt_offset >= cur_num_) {
      ++num_truncated;
      continue;
    }

    // Turn change buf into change^orig.
    const char *buf_end =
        &saved_orig_buf_[cur_offset + change.elt_len * elt_size_];
    const char *cur_array = array_cast<char>() + change.elt_offset * elt_size_;
    // Now xor in. SSE acceleration please?
    for (char *cur = &saved_orig_buf_[cur_offset]; cur < buf_end;
         cur++, cur_array++) {
      *cur ^= *cur_array;
    }

    // Skip over already updated bytes by setting update to 0.
    bool new_update = false;
    bool overlap = false;
    uint32_t cur_elt = change.elt_offset;
    for (char *cur = &saved_orig_buf_[cur_offset]; cur < buf_end;
         cur_elt++, cur += elt_size_) {
      if (updated[cur_elt]) {
        memset(cur, 0, elt_size_);
        overlap = true;
      } else {
        updated[cur_elt] = true;
        new_update = true;
      }
    }

    // Apply update to crc.
    if (new_update) {
      cur_crc = IcingStringUtil::UpdateAtPositionCrc32(
          cur_crc, changes_end_ * elt_size_, change.elt_offset * elt_size_,
          buf_end - change.elt_len * elt_size_, change.elt_len * elt_size_);
      num_partial_crcs++;
      if (overlap) {
        num_overlapped++;
      }
    } else {
      num_duplicate++;
    }
    cur_offset += change.elt_len * elt_size_;
  }
  if (!changes_.empty()) {
    ICING_VLOG(2) << "Array update partial crcs " << num_partial_crcs
        << " truncated " << num_truncated << " overlapped " << num_overlapped
        << " duplicate " << num_duplicate;
  }

  // Now update with grown area.
  if (changes_end_ < cur_num_) {
    cur_crc = IcingStringUtil::UpdateCrc32(
        cur_crc, array_cast<char>() + changes_end_ * elt_size_,
        (cur_num_ - changes_end_) * elt_size_);
    ICING_VLOG(2) << "Array update tail crc offset " << changes_end_ << " -> " << cur_num_;
  }

  // Clear, now that we've applied changes.
  changes_.clear();
  saved_orig_buf_.clear();
  changes_end_ = cur_num_;

  // Commit new crc.
  *crc_ptr_ = cur_crc;
}

void IcingArrayStorage::Warm() const {
  if (madvise(MakeVoidPtr(array()),
              IcingMMapper::page_aligned_size(cur_num_ * elt_size_),
              MADV_WILLNEED) != 0) {
    ICING_LOG(FATAL) << "Failed to madvise()";
  }
}

void IcingArrayStorage::Clear() {
  cur_num_ = 0;
  changes_end_ = 0;
  changes_.clear();
  saved_orig_buf_.clear();
  dirty_pages_.clear();
  if (crc_ptr_) *crc_ptr_ = 0;
}

// TODO(b/69383247): investigate strange behavior here
// If map_shared_ is false (i.e. we are using MAP_PRIVATE), dirty pages are
// flushed to the underlying file, but strangely a sync isn't done.
// If map_shared_ is true, then we call sync.
uint32_t IcingArrayStorage::Sync() {
  if (!map_shared_) {
    IcingTimer timer;
    uint32_t num_flushed = 0;     // pages flushed
    uint32_t num_contiguous = 0;  // contiguous series of pages flushed
    uint32_t dirty_pages_size = dirty_pages_.size();

    bool in_dirty = false;
    uint32_t dirty_start = 0;
    for (size_t i = 0; i < dirty_pages_size; i++) {
      bool is_dirty = dirty_pages_[i];
      if (in_dirty && !is_dirty) {
        // Flush pages between dirty_start and this.
        uint32_t dirty_end = i * IcingMMapper::system_page_size();
        num_contiguous++;
        num_flushed +=
            (dirty_end - dirty_start) / IcingMMapper::system_page_size();

        if (pwrite(fd_, array() + dirty_start, dirty_end - dirty_start,
                   fd_offset_ + dirty_start) !=
            static_cast<ssize_t>(dirty_end - dirty_start)) {
          ICING_LOG(ERROR) << "Flushing pages failed (" << dirty_start << ", " << dirty_end << ")";
        }
        in_dirty = false;
      } else if (!in_dirty && is_dirty) {
        dirty_start = i * IcingMMapper::system_page_size();
        in_dirty = true;
      }
    }

    // Flush remaining.
    if (in_dirty) {
      uint32_t dirty_end = dirty_pages_size * IcingMMapper::system_page_size();
      num_contiguous++;
      num_flushed +=
          (dirty_end - dirty_start) / IcingMMapper::system_page_size();

      if (pwrite(fd_, array() + dirty_start, dirty_end - dirty_start,
                 fd_offset_ + dirty_start) !=
          static_cast<ssize_t>(dirty_end - dirty_start)) {
        ICING_LOG(ERROR) << "Flushing pages failed (" << dirty_start << ", " << dirty_end << ")";
      }
    }

    // Clear in one shot.
    dirty_pages_.clear();

    // Invalidate region so that we are rid of dirty private pages.
    if (madvise(MakeVoidPtr(array()),
                IcingMMapper::page_aligned_size(cur_num_ * elt_size_),
                MADV_DONTNEED) != 0) {
      ICING_LOG(FATAL) << "Failed to madvise()";
    }

    if (num_flushed > 0) {
      ICING_VLOG(1) << "Flushing " << num_flushed << "/" << dirty_pages_size << " " << num_contiguous << " contiguous pages in " << timer.Elapsed() * 1000 << "ms.";
    }

    return num_flushed;
  } else {
    // Changes have been applied. msync() to ensure they are written out.
    // Don't sync 0-length, which is an error in iOS and a no-op on Android
    const size_t sync_length =
        IcingMMapper::page_aligned_size(cur_num_ * elt_size_);
    if (sync_length > 0) {
      if (msync(MakeVoidPtr(array()), sync_length, MS_SYNC) != 0) {
        ICING_LOG(FATAL) << "Failed to msync()";
      }
    }

    return 0;
  }
}

}  // namespace lib
}  // namespace icing
