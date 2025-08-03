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
// Author: ulas@google.com (Ulas Kirazci)
//
// A disk-backed array.

#ifndef ICING_LEGACY_INDEX_ICING_ARRAY_STORAGE_H_
#define ICING_LEGACY_INDEX_ICING_ARRAY_STORAGE_H_

#include <cstdint>
#include <string>
#include <vector>

#include "icing/legacy/index/icing-filesystem.h"
#include "icing/legacy/index/icing-mmapper.h"

namespace icing {
namespace lib {

class IcingArrayStorage {
 public:
  explicit IcingArrayStorage(const IcingFilesystem &filesystem);
  ~IcingArrayStorage();

  // Mmap a disk-backed array at fd_offset in fd. fd is owned by the
  // caller and must be kept valid.
  //
  // If map_shared is true, changes to GetMutableMem immediately apply
  // to the backing store. Otherwise changes are kept private until an
  // explicit call to Flush.
  //
  // Each element in the array is elt_size bytes and the array is
  // valid up to num_elts. max_num_elts is the max that the array is
  // allowed to grow to.
  //
  // If crc_ptr is not NULL, explicit calls to UpdateCrc keep the crc
  // of the array in *crc_ptr.
  //
  // If init_crc is true, the crc of the array is recomputed and
  // written into crc_ptr. Else, the crc of the array is checked
  // against the current value in crc_ptr and Init fails if the crc
  // does not match.
  //
  // REQUIRES: !is_initialized()
  bool Init(int fd, size_t fd_offset, bool map_shared, uint32_t elt_size,
            uint32_t num_elts, uint32_t max_num_elts, uint32_t *crc_ptr,
            bool init_crc);

  // Undo Init. Make is_initialized() == false.
  void Reset();

  bool is_initialized() const { return mmapper_ != nullptr; }

  // Attempt to swap into RAM.
  void Warm() const;

  // Make array empty again.
  void Clear();

  // Intent to write memory at (elt_idx, elt_idx + elt_len). Returns
  // NULL if file cannot be grown to accommodate that offset.
  template <class T>
  T *GetMutableMem(uint32_t elt_idx, uint32_t elt_len) {
    return static_cast<T *>(GetMutableMemInternal(elt_idx, elt_len));
  }

  // Resizes to first elt_len elements.
  // REQUIRES: elt_len <= num_elts()
  void Truncate(uint32_t len);

  // Push changes to crc into crc_ptr. No effect if crc_ptr is NULL.
  void UpdateCrc();

  // Write and sync dirty pages to fd starting at offset. Returns
  // number of pages synced.
  uint32_t Sync();

  // Accessors.
  const uint8_t *array() const { return mmapper_->address(); }
  template <class T>
  const T *array_cast() const {
    return reinterpret_cast<const T *>(array());
  }
  uint32_t num_elts() const { return cur_num_; }
  uint32_t max_num_elts() const { return max_num_; }
  uint32_t max_size() const { return max_num_elts() * elt_size_; }

  // For stats.
  uint32_t num_dirty_pages() const {
    uint32_t num = 0;
    for (size_t i = 0; i < dirty_pages_.size(); i++) {
      if (dirty_pages_[i]) num++;
    }
    return num;
  }

 private:
  // We track partial updates to the array for CRC updating. This
  // requires extra memory to keep track of original buffers but
  // allows for much faster CRC re-computation. This is the frac limit
  // of byte len after which we will discard recorded changes and
  // recompute the entire CRC instead.
  static const uint32_t kPartialCrcLimitDiv;  // 10 means limit is 1/10

  // Grow file by at least this many elts if array is growable.
  static const size_t kGrowElts;

  // A change record (somebody called GetMutableMem on this
  // region). We only keep changes <= changes_end_.
  struct Change {
    Change(uint32_t o, uint32_t l) : elt_offset(o), elt_len(l) {}

    uint32_t elt_offset;
    uint32_t elt_len;
  };
  static_assert(8 == sizeof(Change), "sizeof(Change) != 8");
  static_assert(4 == alignof(Change), "alignof(Change) != 4");

  void *GetMutableMemInternal(uint32_t elt_idx, uint32_t elt_len);

  bool GrowIfNecessary(uint32_t num_elts);

  int fd_;
  size_t fd_offset_;
  bool map_shared_;
  IcingMMapper *mmapper_;

  // Size of an element in the array.
  uint32_t elt_size_;

  // In bytes.
  uint32_t cur_num_;       // cur boundary of written elts
  uint32_t changes_end_;   // cur_num_ at last call to UpdateCrc
  uint32_t max_num_;       // size of array in elts
  uint32_t capacity_num_;  // num elts that can be accommodated by file size

  uint32_t *crc_ptr_;

  // Changes that have happened since the last update
  // (between [0, changes_end_)).
  std::vector<Change> changes_;
  std::string saved_orig_buf_;

  // Keep track of all pages we touched so we can write them back to
  // disk.
  std::vector<bool> dirty_pages_;

  const IcingFilesystem &filesystem_;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_LEGACY_INDEX_ICING_ARRAY_STORAGE_H_
