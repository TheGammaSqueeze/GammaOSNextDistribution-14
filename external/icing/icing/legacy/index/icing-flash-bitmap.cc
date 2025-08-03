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

#include "icing/legacy/index/icing-flash-bitmap.h"

#include <sys/mman.h>

#include <memory>

#include "icing/legacy/core/icing-string-util.h"
#include "icing/legacy/core/icing-timer.h"
#include "icing/legacy/index/icing-bit-util.h"
#include "icing/legacy/index/icing-filesystem.h"
#include "icing/util/logging.h"

namespace icing {
namespace lib {

// TODO(b/77482303) : Remove version from the IcingFlashBitmap header - magic
// makes it unnecessary.
struct IcingFlashBitmap::Header {
  uint32_t magic;
  uint32_t version;
  uint32_t crc;
  uint32_t dirty;
};

// Helper class used to access the data and the header regions
// of the shared memory.  The header appears first, followed by the
// bitmap memory.
class IcingFlashBitmap::Accessor {
 public:
  explicit Accessor(IcingMMapper *mmapper) : mmapper_(mmapper) {}
  IcingFlashBitmap::Header *header() {
    return reinterpret_cast<IcingFlashBitmap::Header *>(mmapper_->address());
  }
  const IcingFlashBitmap::Header *header() const {
    return reinterpret_cast<const IcingFlashBitmap::Header *>(
        mmapper_->address());
  }
  const char *data() const {
    return reinterpret_cast<const char *>(mmapper_->address() +
                                          sizeof(IcingFlashBitmap::Header));
  }
  size_t data_size() const {
    return mmapper_->len() - sizeof(IcingFlashBitmap::Header);
  }
  size_t num_words() const { return data_size() / sizeof(Word); }
  Word *data32() {
    return reinterpret_cast<Word *>(mmapper_->address() +
                                    sizeof(IcingFlashBitmap::Header));
  }
  const Word *data32() const { return reinterpret_cast<const Word *>(data()); }
  const Word *end32() const {
    return reinterpret_cast<const Word *>(mmapper_->address() +
                                          mmapper_->len());
  }

 private:
  IcingMMapper *const mmapper_;
};

bool IcingFlashBitmap::Verify() const {
  if (!is_initialized()) {
    ICING_LOG(ERROR) << "Can't verify unopened flash bitmap " << filename_;
    return false;
  }
  if (mmapper_ == nullptr) {
    // Opened for read and file doesn't exist.
    return true;
  }
  Accessor accessor(mmapper_.get());
  if (accessor.header()->magic != kMagic) {
    ICING_LOG(ERROR) << "Flash bitmap " << filename_ << " has incorrect magic header";
    return false;
  }
  if (accessor.header()->version != kCurVersion) {
    ICING_LOG(ERROR) << "Flash bitmap " << filename_ << " has incorrect version";
    return false;
  }
  if (accessor.header()->dirty) {
    ICING_LOG(ERROR) << "Flash bitmap " << filename_ << " is dirty";
    return false;
  }
  uint32_t crc =
      IcingStringUtil::UpdateCrc32(0, accessor.data(), accessor.data_size());
  if (accessor.header()->crc != crc) {
    ICING_LOG(ERROR) << "Flash bitmap " << filename_ << " has incorrect CRC32 " << accessor.header()->crc << " " << crc;
    return false;
  }
  return true;
}

bool IcingFlashBitmap::Init() {
  Close();

  // Ensure the storage directory exists
  std::string storage_dir = filesystem_->GetDirname(filename_.c_str());
  if (!filesystem_->CreateDirectoryRecursively(storage_dir.c_str())) {
    return false;
  }

  IcingScopedFd fd(filesystem_->OpenForWrite(filename_.c_str()));
  if (!fd.is_valid()) {
    return false;
  }

  // Figure out our file size for mmap.
  uint64_t orig_file_size = filesystem_->GetFileSize(fd.get());
  uint64_t file_size = orig_file_size;
  if (orig_file_size == IcingFilesystem::kBadFileSize) {
    goto error;
  }

  // Make sure we have something to mmap.
  if (orig_file_size < kGrowSize) {
    if (!filesystem_->Grow(fd.get(), kGrowSize)) {
      goto error;
    }
    file_size = kGrowSize;
  }

  // Mmap for write.
  mmapper_ =
      std::make_unique<IcingMMapper>(fd.get(), false, 0, file_size, MAP_SHARED);
  if (!mmapper_->is_valid()) {
    goto error;
  }

  // Set open_type_ before the possible flush on create.
  open_type_ = READ_WRITE;

  if (orig_file_size == 0) {
    Accessor accessor(mmapper_.get());
    // Original file didn't yet exist, create header.
    accessor.header()->magic = kMagic;
    accessor.header()->version = kCurVersion;
    accessor.header()->dirty = true;  // Forces crc update at sync.
    // Sync file so we know it's supposed to exist.
    if (!Sync()) {
      goto error;
    }
  }
  return true;

error:
  open_type_ = UNOPENED;
  mmapper_.reset();
  return false;
}

bool IcingFlashBitmap::InitForRead() {
  IcingTimer open_timer;
  Close();

  // Cannot mmap non-existing or zero-size files.
  // It's not an error in this case, it just means the
  // bitmap is empty, so proceed without mapping it.
  if (!filesystem_->FileExists(filename_.c_str()) ||
      filesystem_->GetFileSize(filename_.c_str()) == 0) {
    open_type_ = READ_ONLY;
    return true;
  }

  IcingScopedFd fd(filesystem_->OpenForRead(filename_.c_str()));
  if (!fd.is_valid()) {
    return false;
  }

#ifdef __APPLE__
  // No MAP_POPULATE in iOS (so no pre-page-faulting.  See man mmap)
  // On Apple we need MAP_SHARED even for sharing the state within the same
  // process (which gets optimized in the linux-implementation).
  // Usages of flash-bitmap are expected to flush the content (delayed for
  // performance reasons). That implies that the copy-on-write behavior of
  // MAP_PRIVATE is a performance optimization, and MAP_SHARED as alternative
  // behavior is acceptable.
  int flags = MAP_SHARED;
#else
  int flags = MAP_PRIVATE | MAP_POPULATE;
#endif

  // Figure out our file size for mmap.
  uint64_t file_size = filesystem_->GetFileSize(fd.get());
  if (file_size == IcingFilesystem::kBadFileSize) {
    goto error;
  }

  // Slurp the bitmap in one go with MAP_POPULATE
  mmapper_ =
      std::make_unique<IcingMMapper>(fd.get(), true, 0, file_size, flags);
  if (!mmapper_->is_valid()) {
    goto error;
  }

  open_type_ = READ_ONLY;
  return true;

error:
  open_type_ = UNOPENED;
  mmapper_.reset();
  return false;
}

void IcingFlashBitmap::Close() {
  if (is_initialized()) {
    UpdateCrc();
    mmapper_.reset();
    open_type_ = UNOPENED;
  }
}

bool IcingFlashBitmap::Delete() {
  Close();
  return filesystem_->DeleteFile(filename_.c_str());
}

bool IcingFlashBitmap::Sync() const {
  if (!is_initialized()) {
    ICING_LOG(FATAL) << "Bitmap not initialized";
  }

  UpdateCrc();
  return (mmapper_ == nullptr) ? true : mmapper_->Sync();
}

uint64_t IcingFlashBitmap::GetDiskUsage() const {
  // For non-existing files, size is 0.
  if (mmapper_ == nullptr) {
    return 0;
  }
  return filesystem_->GetFileDiskUsage(filename_.c_str());
}

uint32_t IcingFlashBitmap::UpdateCrc() const {
  Accessor accessor(mmapper_.get());
  if (open_type_ == READ_WRITE && accessor.header()->dirty) {
    accessor.header()->crc = IcingStringUtil::UpdateCrc32(
        kEmptyCrc, accessor.data(), accessor.data_size());
    accessor.header()->dirty = false;
  }

  // Non-existent mmapper means file does not exist. An empty file has
  // a crc of kEmptyCrc, so just return that.
  return mmapper_.get() ? accessor.header()->crc : kEmptyCrc;
}

bool IcingFlashBitmap::Grow(size_t new_file_size) {
  IcingScopedFd fd(filesystem_->OpenForWrite(filename_.c_str()));
  if (!filesystem_->Grow(fd.get(), new_file_size)) {
    ICING_LOG(ERROR) << "Grow " << filename_ << " to new size " << new_file_size << " failed";
    return false;
  }
  if (!mmapper_->Remap(fd.get(), 0, new_file_size)) {
    ICING_LOG(ERROR) << "Remap of " << filename_ << " after grow failed";
    return false;
  }
  ICING_VLOG(1) << "Grew " << filename_ << " new size " << new_file_size;
  Accessor accessor(mmapper_.get());
  accessor.header()->dirty = true;
  return true;
}

bool IcingFlashBitmap::SetBit(uint64_t idx, bool value) {
  if (open_type_ != READ_WRITE) {
    ICING_LOG(FATAL) << "Bitmap not opened with type READ_WRITE";
  }

  Accessor accessor(mmapper_.get());

  // Figure out which word needs to be modified.
  uint64_t word_offset = idx / kWordBits;

  // Grow file (and mmap) if word_offset >= file size / sizeof(Word).
  if (word_offset >= accessor.num_words()) {
    if (!value) {
      // Values beyond the end of file are false by default, don't write it.
      return true;
    }
    // Grow enough to fit word_offset (including the header).
    size_t file_size = sizeof(Header) + (word_offset + 1) * sizeof(Word);
    // Align to kGrowSize.
    file_size = ALIGN_UP(file_size, kGrowSize);
    if (!Grow(file_size)) {
      return false;
    }
  }

  // Set the word in the mmapped region.
  Word *words = accessor.data32();
  Word mask = GetWordBitmask(idx);
  Word old_word = words[word_offset];
  Word new_word = value ? old_word | mask : old_word & ~mask;
  if (new_word != old_word) {
    words[word_offset] = new_word;
    accessor.header()->dirty = true;
  }
  return true;
}

bool IcingFlashBitmap::GetBit(uint64_t idx) const {
  return GetWord(idx / kWordBits) & GetWordBitmask(idx);
}

IcingFlashBitmap::Word IcingFlashBitmap::GetWord(uint64_t idx) const {
  if (!is_initialized()) {
    ICING_LOG(FATAL) << "Bitmap not initialized";
  }

  // For non-existing files, always return false.
  if (mmapper_ == nullptr) {
    return 0;
  }

  Accessor accessor(mmapper_.get());
  // Check that we are within limits.
  if (idx >= accessor.num_words()) {
    return 0;
  }
  return accessor.data32()[idx];
}

size_t IcingFlashBitmap::NumWords() const {
  if (!is_initialized()) {
    ICING_LOG(FATAL) << "Bitmap not initialized";
  }

  // For non-existing files, always return false.
  if (mmapper_ == nullptr) {
    return 0;
  }

  return Accessor(mmapper_.get()).num_words();
}

IcingFlashBitmap::Word IcingFlashBitmap::GetWordBitmask(uint64_t idx) {
  return 1u << (idx % kWordBits);
}

void IcingFlashBitmap::Truncate(uint64_t idx) {
  if (!is_initialized()) {
    ICING_LOG(FATAL) << "Bitmap not initialized";
  }

  Accessor accessor(mmapper_.get());
  size_t num_words = accessor.num_words();

  uint64_t word_offset = idx / kWordBits;
  if (word_offset >= num_words) {
    // Truncation offset beyond actual file. We're done.
    return;
  }

  Word *words = accessor.data32();

  // Keep only bits < idx in the last word.
  words[word_offset] &= (GetWordBitmask(idx) - 1);

  // Clear everything starting at word_offset + 1
  uint64_t last_word_offset = word_offset + 1;
  if (last_word_offset < num_words) {
    memset(words + last_word_offset, 0,
           (num_words - last_word_offset) * sizeof(Word));
  }
  accessor.header()->dirty = true;
  UpdateCrc();
}

bool IcingFlashBitmap::OrBitmap(const IcingFlashBitmap &bitmap) {
  if (!is_initialized()) {
    ICING_LOG(FATAL) << "Bitmap not initialized";
  }

  if (mmapper_ == nullptr || bitmap.mmapper_ == nullptr) {
    // TODO(b/32125196): Figure out how we can get into this state.
    return false;
  }

  // If this bitmap is smaller than the source, then grow the
  // size to match.
  if (mmapper_->len() < bitmap.mmapper_->len()) {
    if (!Grow(bitmap.mmapper_->len())) {
      return false;
    }
  }
  Accessor src_accessor(bitmap.mmapper_.get());
  const Word *src = src_accessor.data32();
  const Word *end = src_accessor.end32();

  Accessor dst_accessor(mmapper_.get());
  Word *dst = dst_accessor.data32();
  while (src < end) {
    *dst++ |= *src++;
  }
  dst_accessor.header()->dirty = true;
  UpdateCrc();
  return true;
}

}  // namespace lib
}  // namespace icing
