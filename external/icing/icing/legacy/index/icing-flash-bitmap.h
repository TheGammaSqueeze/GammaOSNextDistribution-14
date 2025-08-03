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
//
// A disk-backed bitmap.
//
// For writing:
//
// Init();
// SetBit(bit_idx, <true|false>)  // Automatically grows with SetBit.
// ...
// Sync();  // SetBit takes effect immediately but Sync persists to disk.
//
// For reading:
//
// InitForRead();
// GetBit(bit_idx);
// ...
// Close();
//
// InitForRead uses mmap MAP_POPULATE to fault the entire file to
// memory. Subsequent random GetBits are very fast (nanoseconds).
//
// It's ok to call Init after InitForRead. The last "Init" call takes
// effect.

#ifndef ICING_LEGACY_INDEX_ICING_FLASH_BITMAP_H_
#define ICING_LEGACY_INDEX_ICING_FLASH_BITMAP_H_

#include <cstdint>
#include <memory>
#include <string>

#include "icing/legacy/index/icing-filesystem.h"
#include "icing/legacy/index/icing-mmapper.h"

namespace icing {
namespace lib {

class IcingFlashBitmap {
 public:
  using Word = uint32_t;

  static constexpr uint32_t kEmptyCrc = 0;
  static constexpr size_t kGrowSize = (1u << 12);  // 4KB;
  static constexpr size_t kWordBits = 8 * sizeof(Word);

  IcingFlashBitmap(const std::string &filename,
                   const IcingFilesystem *filesystem)
      : filesystem_(filesystem), filename_(filename), open_type_(UNOPENED) {}
  ~IcingFlashBitmap() { Close(); }

  // Init for read and write. Returns true on success.  Does not verify
  // the data checksum.  To do so call Verify explicitly.
  bool Init();

  // Init for read only. Also faults the entire file into memory with
  // MAP_POPULATE.  Does not verify  the data checksum.  To do so call Verify
  // explicitly.
  bool InitForRead();

  // Verifies the integrity of the data by checking the header values
  // and data checksum. Returns true if opened with InitForRead() and
  // file does not exist.
  bool Verify() const;

  // If either of the init functions was called successfully.
  bool is_initialized() const { return open_type_ != UNOPENED; }

  // Close file and release resources. Leaves the bitmap in uninitialized state.
  void Close();

  // The following functions require is_initialized() with Init()
  // EXCEPT GetBit() which requires Init() or InitForRead().

  // Close and delete the underlying file. Leaves the bitmap in uninitialized
  // state (even if deletion failed).
  bool Delete();

  // Delete the underlying file, and reinitialize it. If successful, the bitmap
  // is initialized.
  bool Clear() { return Delete() && Init(); }

  // Sync the changes to disk.
  bool Sync() const;

  uint64_t GetDiskUsage() const;

  // Set or clear a bit at idx. Automatically resizes the file to fit
  // idx.  Returns true on success.
  bool SetBit(uint64_t idx, bool value);

  // Get the value of bit at idx. If idx is out of range, returns false.
  // Can be called with InitForRead().
  bool GetBit(uint64_t idx) const;

  // Get the idx'th word in the bitmap. If idx is out of range, returns zero.
  // Can be called with InitForRead().
  Word GetWord(uint64_t idx) const;
  size_t NumWords() const;

  // Clear all bits starting at idx.
  void Truncate(uint64_t idx);

  // Ors all the set bits from a given bitmap into this bitmap.
  bool OrBitmap(const IcingFlashBitmap &bitmap);

  const std::string &filename() const { return filename_; }

  // If the bitmap is dirty, update the crc and mark it clean.
  uint32_t UpdateCrc() const;

 private:
  class Accessor;
  struct Header;

  static const uint32_t kMagic = 0x394b0698;
  static const uint32_t kCurVersion = 18;

  enum OpenType { UNOPENED, READ_ONLY, READ_WRITE };

  static Word GetWordBitmask(uint64_t idx);

  // Increase the size of the bitmap file to the new size.  Return true
  // on success.
  bool Grow(size_t new_file_size);

  // Upgrade for version 18.
  bool UpgradeTo18();

  // Legacy file system. Switch to use the new Filesystem class instead.
  const IcingFilesystem *const filesystem_;
  std::string filename_;
  OpenType open_type_;
  std::unique_ptr<IcingMMapper> mmapper_;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_LEGACY_INDEX_ICING_FLASH_BITMAP_H_
