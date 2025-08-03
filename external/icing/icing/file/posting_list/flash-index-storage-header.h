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

#ifndef ICING_FILE_POSTING_LIST_FLASH_INDEX_STORAGE_HEADER_H_
#define ICING_FILE_POSTING_LIST_FLASH_INDEX_STORAGE_HEADER_H_

#include <cstdint>
#include <memory>

#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/absl_ports/canonical_errors.h"
#include "icing/file/filesystem.h"

namespace icing {
namespace lib {

// The class used to manage the flash block that contains the header for
// FlashIndexStorage. This contains information about the index blocks that
// store the posting lists.
class HeaderBlock {
 public:
  // The class used to access the actual header.
  struct Header {
    // A magic used to mark the beginning of a valid header.
    static constexpr int kMagic = 0xb0780cf4;
    int magic;
    int block_size;
    int last_indexed_docid;
    // The size of the index_block_infos array.
    int num_index_block_infos;

    struct IndexBlockInfo {
      // The size of the posting lists that fit on all the index blocks in this
      // chain. Each block on this posting list will have posting lists of size
      // posting_list_bytes.
      int posting_list_bytes;
      // The block index of the first block in the free list chain.
      int free_list_block_index;
    };
    // Variable-size array, num_index_block_infos long. Can have a max length
    // of log(block_size). This array is used to maintain a free list for the
    // available blocks.
    IndexBlockInfo index_block_infos[0];
  };

  // Read HeaderBlock from the specified fd.
  //
  // RETURNS:
  //  - HeaderBlock, on success
  //  - INTERNAL if unable to read block_size bytes from fd.
  static libtextclassifier3::StatusOr<HeaderBlock> Read(
      const Filesystem* filesystem, int fd, int block_size) {
    std::unique_ptr<uint8_t[]> buffer = std::make_unique<uint8_t[]>(block_size);
    if (!filesystem->PRead(fd, buffer.get(), block_size, 0)) {
      return absl_ports::InternalError("Unable to reader header block!");
    }
    return HeaderBlock(filesystem, std::move(buffer), block_size);
  }

  // Make a new HeaderBlock with the specified size.
  explicit HeaderBlock(const Filesystem* filesystem, int block_size)
      : HeaderBlock(filesystem, std::make_unique<uint8_t[]>(block_size),
                    block_size) {
    std::memset(header_buffer_.get(), 0, block_size);
  }

  Header* header() const {
    return reinterpret_cast<Header*>(header_buffer_.get());
  }

  // Add another entry to the index_block_infos array and return a pointer to
  // that entry. Returns a nullptr if the index_block_infos array is already
  // at a max size.
  Header::IndexBlockInfo* AddIndexBlockInfo() {
    if (size() + sizeof(Header::IndexBlockInfo) > block_size_) {
      return nullptr;
    }
    ++header()->num_index_block_infos;
    return header()->index_block_infos + (header()->num_index_block_infos - 1);
  }

  // Returns the size of the header block currently in use.
  int size() const {
    return sizeof(Header) +
           header()->num_index_block_infos * sizeof(Header::IndexBlockInfo);
  }

  // Writes the header to fd. Returns true on success.
  bool Write(int fd) {
    return filesystem_->PWrite(fd, 0, header_buffer_.get(), block_size_);
  }

 private:
  explicit HeaderBlock(const Filesystem* filesystem,
                       std::unique_ptr<uint8_t[]> buffer, int block_size)
      : filesystem_(filesystem),
        header_buffer_(std::move(buffer)),
        block_size_(block_size) {}

  const Filesystem* filesystem_;  // does NOT own!
  std::unique_ptr<uint8_t[]> header_buffer_;
  int block_size_;
};
static_assert(16 == sizeof(HeaderBlock::Header),
              "Header has changed size. Consider how this change might affect "
              "pre-existing indices.");

}  // namespace lib
}  // namespace icing

#endif  // ICING_FILE_POSTING_LIST_FLASH_INDEX_STORAGE_HEADER_H_
