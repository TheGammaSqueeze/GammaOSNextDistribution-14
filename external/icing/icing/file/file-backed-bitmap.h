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

// A file-backed bitmap with fast & efficient reads/writes of bits.
// The bitmap will automatically grow in size as more bits are added, with a
// max-capacity of 2M bits.
//
// Note on Performance:
// This class internally uses mmap() without a readahead buffer. This keeps the
// memory-usage low while also having low (amortized) read/write latency.
// However, some reads/writes will pay the cost of page-faults.
// In order to keep memory-mapping efficient, the bitmap always grows in
// 4KiB sized blocks so that it is aligned with system page-size.
//
// This class doesn't aggressively flush/sync changes to disk and relies on the
// system to buffer and flush changes in the background. This greatly reduces
// disk-churn and performance of writes. However, an unexpected crash or an
// abrupt reboot of the system could lead to data-loss. This can be mitigated
// by manually calling PersistToDisk() when needed.
//
// Usage:
// auto bitmap = RETURN_OR_ASSIGN(FileBackedBitmap::Create(...));
//
// bitmap.Set(100, false);
// bitmap.Set(10, true);
//
// bitmap.Get(0);  // Default default of 'false'.
// bitmap.Get(10);
//
// bitmap.PersistToDisk();  // Optional. Immediately syncs all changes to disk.
// bitmap.reset();

#ifndef ICING_FILE_FILE_BACKED_BITMAP_H_
#define ICING_FILE_FILE_BACKED_BITMAP_H_
#include <cstdint>
#include <memory>
#include <string>
#include <string_view>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/file/filesystem.h"
#include "icing/file/memory-mapped-file.h"

namespace icing {
namespace lib {

class FileBackedBitmap {
 public:
  // Growth of FileBackedBitmap is in blocks of a fixed size. This helper
  // returns the number of bits that can be fitted in the specified number of
  // blocks.
  //
  // NOTE: This is meant for tests and clients shouldn't care about this.
  static int GetBlockCapacity(int num_blocks);

  // Returns an initialized instance of the bitmap that can immediately handle
  // read/write operations.
  //
  // file_path : Specifies the file to persist the bitmap to; must be a path
  //             within a directory that already exists. If the file itself
  //             doesn't exist, a new bitmap will be created.
  //
  // mmap_strategy : Mmap strategy for the underlying file, see
  //                 MemoryMappedFile::Strategy for more details.
  //
  // Returns an error if the file was corrupted or if any IO error was
  // encountered. An error here implies that the old data has been lost and
  // the file has to be deleted and re-initialized again.
  static libtextclassifier3::StatusOr<std::unique_ptr<FileBackedBitmap>> Create(
      const Filesystem* filesystem, std::string_view file_path,
      MemoryMappedFile::Strategy mmap_strategy);

  // If the bitmap was created with
  // MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC, then changes will be
  // synced by the system and the checksum will be updated.
  ~FileBackedBitmap();

  // Set the bit at the specified position. The bitmap is automatically resized
  // to at least fit 'index' number of bits. bit_index should not be larger than
  // 2M, which is the max-capacity of FileBackedBitmap.
  //
  // Returns any encountered IO error.
  //
  // NOTE: While changes take place immediately, they may not be fully persisted
  // to disk till PersistToDisk() is called.
  //
  // NOTE: The Bitmap grows in blocks of 4KiB. So, setting a specific bit
  // beyond current capacity can lead to pre-allocating up to ~32K extra bits.
  libtextclassifier3::Status Set(int bit_index, bool bit_value);

  // Get the bit at the specified index. Unset bits default to 'false'.
  //
  // Returns OUT_OF_RANGE error if bit_index > NumBits().
  libtextclassifier3::StatusOr<bool> Get(int bit_index) const;

  // Count of bits currently being stored in this bitmap.
  //
  // NOTE: BitMap growth happens in blocks of 4KiB. So, the smallest bitmap will
  // automatically have ~32K bits pre-allocated. Subsequently, future
  // growths/truncations of the bitmap will change NumBits() in multiples of
  // 32K.
  int NumBits() const;

  // Truncates the size of the bitmap to 'new_num_bits'. Any data beyond this
  // will be lost.
  libtextclassifier3::Status TruncateTo(int new_num_bits);

  // Syncs all the changes made to the bitmap to disk and updates the checksum.
  //
  // Returns any encountered IO error.
  //
  // NOTE: Neither Set() nor the ~FileBackedBitmap() guarantee syncing all
  // changes to disk. This method should be explicitly called to protect the
  // data from an abrupt system reboot.
  libtextclassifier3::Status PersistToDisk();

 private:
  // Limit the max-size of the bitmap. Someone wanting to store more bits will
  // likely benefit from a custom solution.
  static constexpr int kMaxNumBits = 2 * 1024 * 1024;

  // Growth of FileBackedBitmap will be in blocks of this size. This size
  // should align with the page-size of the system so that mmapping can be
  // most efficient.
  static constexpr int kBlockByteSize = 4 * 1024;

  // Version of the file-format used by the class. Every time the format is
  // modified in a backwards-incompatible way, this needs to be incremented
  static constexpr int32_t kCurrentVersion = 1;

  struct Header {
    // Version of the file-format used by this class. This allows us to change
    // the format and upgrade old data to the new format without losing it.
    int32_t version;

    // Checksum of the entire file when it was last persisted to disk.
    // This is used on init to make sure that the file has not been corrupted.
    //
    // NOTE: The checksum is not expected to match when ChecksumState=kStale.
    uint32_t checksum;

    // As an optimization, FileBackedBitmap delays recomputation of the checksum
    // even when some bits in the Bitmap are modified. While this improves
    // performance, it increases the risk of losing data due to a crash.
    // ChecksumState tracks if the changes to the bitmap have been fully
    // reflected in the checksum stored above.
    //
    // NOTE: We use int32_t to store a bool info here to keep the Header
    // aligned.
    enum ChecksumState : int32_t { kFresh, kStale };
    ChecksumState state;
  };

  // The size of the backing file to store the specified number of bits. This
  // size is aligned to the page-size of the system so that it can be
  // efficiently memory mapped.
  static size_t FileSizeForBits(int num_bits);

  static constexpr int kHeaderByteSize = sizeof(Header);

  // Helpers to read/modify the header of the bitmap file.
  const Header& header() const;
  Header* mutable_header();

  // Use FileBackedBitmap::Create() to instantiate.
  explicit FileBackedBitmap(const Filesystem* filesystem,
                            std::string_view file_path,
                            MemoryMappedFile&& mmapper);

  // Verify the contents of the bitmap and get ready for read/write operations.
  //
  // Returns an error if the file was corrupted or if any IO error was
  // encountered. An error here implies that the old data has been lost and
  // the file has to be deleted and re-initialized again.
  libtextclassifier3::Status Initialize();

  // Makes sure that the data on disk is upgraded to match the file-format
  // represented by kCurrentVersion.
  libtextclassifier3::Status UpgradeToCurrentVersion();

  // Grows the size of the bitmap to match 'new_num_bits'. Any newly added bit
  // will default to 'false'.
  //
  // The upper-bound for new_num_bits is kMaxNumBits. Requests to further
  // increase the size will fail with an INVALID_ARGUMENT error.
  libtextclassifier3::Status GrowTo(int new_num_bits);

  using Word = uint32_t;
  static constexpr int kNumWordBits = sizeof(Word) * 8;

  // Helpers to perform 32bit read/write operations on the raw bitmap data.
  // This makes it easy to use 32bit bitwise operations to modify the bitmap.
  libtextclassifier3::StatusOr<Word> GetWord(int word_index) const;
  libtextclassifier3::Status SetWord(int word_index, Word word);

  // CRC32 based checksum of all the bits stored in the bitmap. This checksum
  // only uses the data and not the contents of the header.
  uint32_t ComputeChecksum() const;

  const Filesystem* const filesystem_;
  const std::string file_path_;
  std::unique_ptr<MemoryMappedFile> mmapper_;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_FILE_FILE_BACKED_BITMAP_H_
