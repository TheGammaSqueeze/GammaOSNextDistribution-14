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

#ifndef ICING_FILE_POSTING_LIST_INDEX_BLOCK_H_
#define ICING_FILE_POSTING_LIST_INDEX_BLOCK_H_

#include <sys/types.h>

#include <cstdint>
#include <memory>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/file/filesystem.h"
#include "icing/file/posting_list/posting-list-common.h"
#include "icing/file/posting_list/posting-list-used.h"
#include "icing/legacy/index/icing-bit-util.h"

namespace icing {
namespace lib {

// This class is used to manage I/O to a single flash block and to manage the
// division of that flash block into PostingLists. It provides an interface to
// allocate, free and read posting lists. Note that IndexBlock is stateless:
// - Any changes to block header will be synced to disk before the method
//   returns.
// - Any posting list allocation/freeing will be synced to disk before the
//   method returns.
// - When getting an allocated posting list, it PReads the contents from disk to
//   a buffer and transfer the ownership to PostingListUsed. Any changes to
//   PostingListUsed will not be visible to other instances until calling
//   WritePostingListToDisk.
//
// An IndexBlock contains a small header and an array of fixed-size posting list
// buffers. Initially, all posting lists are chained in a singly-linked free
// list.
//
// When we want to get a new PostingList from an IndexBlock, we just pull one
// off the free list. When the user wants to return the PostingList to the free
// pool, we prepend it to the free list.
//
// Read-write the same block is NOT thread safe. If we try to read-write the
// same block at the same time (either by the same or different IndexBlock
// instances), then it causes race condition and the behavior is undefined.
class IndexBlock {
 public:
  // What is the maximum posting list size in bytes that can be stored in this
  // block.
  static uint32_t CalculateMaxPostingListBytes(uint32_t block_size_in_bytes,
                                               uint32_t data_type_bytes) {
    return (block_size_in_bytes - sizeof(BlockHeader)) / data_type_bytes *
           data_type_bytes;
  }

  // Creates an IndexBlock to reference the previously used region of the file
  // descriptor starting at block_file_offset with size block_size.
  //
  // - serializer: for reading/writing posting list. Also some additional
  //               information (e.g. data size) should be provided by the
  //               serializer.
  // - fd: a valid file descriptor opened for write by the caller.
  // - block_file_offset: absolute offset of the file (fd).
  // - block_size: byte size of this block.
  //
  // Unlike CreateFromUninitializedRegion, a pre-existing index block has
  // already determined and written posting list bytes into block header, so it
  // will be read from block header and the caller doesn't have to provide.
  //
  // RETURNS:
  //   - A valid IndexBlock instance on success
  //   - INVALID_ARGUMENT_ERROR
  //     - If block_size is too small for even just the BlockHeader
  //     - If the posting list size stored in the region is not a valid posting
  //       list size (e.g. exceeds max_posting_list_bytes(size))
  //   - INTERNAL_ERROR on I/O error
  static libtextclassifier3::StatusOr<IndexBlock>
  CreateFromPreexistingIndexBlockRegion(const Filesystem* filesystem,
                                        PostingListSerializer* serializer,
                                        int fd, off_t block_file_offset,
                                        uint32_t block_size);

  // Creates an IndexBlock to reference an uninitialized region of the file
  // descriptor starting at block_file_offset with size block_size. The
  // IndexBlock will initialize the region to be an empty IndexBlock with
  // posting lists of size posting_list_bytes.
  //
  // - serializer: for reading/writing posting list. Also some additional
  //               information (e.g. data size) should be provided by the
  //               serializer.
  // - fd: a valid file descriptor opened for write by the caller.
  // - block_file_offset: absolute offset of the file (fd).
  // - block_size: byte size of this block.
  // - posting_list_bytes: byte size of all posting lists in this block. This
  //   information will be written into block header.
  //
  // RETURNS:
  //   - A valid IndexBlock instance on success
  //   - INVALID_ARGUMENT_ERROR
  //     - If block_size is too small for even just the BlockHeader
  //     - If the posting list size stored in the region is not a valid posting
  //       list size (e.g. exceeds max_posting_list_bytes(size))
  //   - INTERNAL_ERROR on I/O error
  static libtextclassifier3::StatusOr<IndexBlock> CreateFromUninitializedRegion(
      const Filesystem* filesystem, PostingListSerializer* serializer, int fd,
      off_t block_file_offset, uint32_t block_size,
      uint32_t posting_list_bytes);

  IndexBlock(const IndexBlock&) = delete;
  IndexBlock& operator=(const IndexBlock&) = delete;
  IndexBlock(IndexBlock&&) = default;
  IndexBlock& operator=(IndexBlock&&) = default;

  ~IndexBlock() = default;

  struct PostingListAndBlockInfo {
    PostingListUsed posting_list_used;
    PostingListIndex posting_list_index;

    uint32_t next_block_index;

    // Flag indicating if there are any free posting lists available after this
    // allocation request.
    bool has_free_posting_lists;

    explicit PostingListAndBlockInfo(PostingListUsed&& posting_list_used_in,
                                     PostingListIndex posting_list_index_in,
                                     uint32_t next_block_index_in,
                                     bool has_free_posting_lists_in)
        : posting_list_used(std::move(posting_list_used_in)),
          posting_list_index(posting_list_index_in),
          next_block_index(next_block_index_in),
          has_free_posting_lists(has_free_posting_lists_in) {}
  };

  // PReads existing posting list content at posting_list_index, instantiates a
  // PostingListUsed, and returns it with some additional index block info.
  //
  // RETURNS:
  //   - A valid PostingListAndBlockInfo on success
  //   - INVALID_ARGUMENT_ERROR if posting_list_index < 0 or posting_list_index
  //     >= max_num_posting_lists()
  //   - INTERNAL_ERROR on I/O error
  libtextclassifier3::StatusOr<PostingListAndBlockInfo> GetAllocatedPostingList(
      PostingListIndex posting_list_index);

  // Allocates a PostingListUsed in the IndexBlock, initializes the content
  // (by serializer), and returns the initialized PostingListUsed instance,
  // PostingListIndex, and some additional index block info.
  //
  // RETURNS:
  //   - A valid PostingListAndBlockInfo instance on success
  //   - RESOURCE_EXHAUSTED_ERROR if there is already no free posting list
  //     available, i.e. !HasFreePostingLists()
  //   - INTERNAL_ERROR on I/O error
  libtextclassifier3::StatusOr<PostingListAndBlockInfo> AllocatePostingList();

  // Frees a posting list at posting_list_index, adds it into the free list
  // chain and updates block header. Both changes on posting list free and
  // header will be synced to disk.
  //
  // It is considered an error to "double-free" a posting list. You should never
  // call FreePostingList(index) with the same index twice, unless that index
  // was returned by an intervening AllocatePostingList() call.
  //
  // Ex.
  //   PostingListIndex index = block.AllocatePostingList();
  //   DoSomething(block.GetAllocatedPostingList(index));
  //   block.FreePostingList(index);
  //   block.FreePostingList(index);  // Argh! What are you doing?!
  //   ...
  //   PostingListIndex index = block.AllocatePostingList();
  //   DoSomething(block.GetAllocatedPostingList(index));
  //   block.FreePostingList(index);
  //   index = block.AllocatePostingList();
  //   DoSomethingElse(block.GetAllocatedPostingList(index));
  //   // A-Ok! We called AllocatePostingList() since the last FreePostingList()
  //   // call.
  //   block.FreePostingList(index);
  //
  // RETURNS:
  //   - OK on success
  //   - INVALID_ARGUMENT_ERROR if posting_list_index < 0 or posting_list_index
  //     >= max_num_posting_lists()
  //   - INTERNAL_ERROR on I/O error
  libtextclassifier3::Status FreePostingList(
      PostingListIndex posting_list_index);

  // Writes back an allocated posting list (PostingListUsed) at
  // posting_list_index to disk.
  //
  // RETURNS:
  //   - OK on success
  //   - INVALID_ARGUMENT_ERROR
  //     - If posting_list_index < 0 or posting_list_index >=
  //       max_num_posting_lists()
  //     - If posting_list_used.size_in_bytes() != posting_list_bytes_
  //   - INTERNAL_ERROR on I/O error
  libtextclassifier3::Status WritePostingListToDisk(
      const PostingListUsed& posting_list_used,
      PostingListIndex posting_list_index);

  // PReads to get the index of next block from block header. Blocks can be
  // chained, and the interpretation of the chaining is up to the caller.
  //
  // RETURNS:
  //   - Next block index on success
  //   - INTERNAL_ERROR on I/O error
  libtextclassifier3::StatusOr<uint32_t> GetNextBlockIndex() const;

  // PWrites block header to set the index of next block.
  //
  // RETURNS:
  //   - OK on success
  //   - INTERNAL_ERROR on I/O error
  libtextclassifier3::Status SetNextBlockIndex(uint32_t next_block_index);

  // PReads to get whether or not there are available posting lists in the free
  // list.
  //
  // RETURNS:
  //   - A bool value on success
  //   - INTERNAL_ERROR on I/O error
  libtextclassifier3::StatusOr<bool> HasFreePostingLists() const;

  // Retrieves the size (in bytes) of the posting lists in this IndexBlock.
  uint32_t posting_list_bytes() const { return posting_list_bytes_; }

  // Retrieves maximum number of posting lists in the block.
  uint32_t max_num_posting_lists() const {
    return total_posting_lists_bytes() / posting_list_bytes_;
  }

  // Retrieves number of bits required to store the largest PostingListIndex in
  // this block.
  int posting_list_index_bits() const {
    return BitsToStore(max_num_posting_lists());
  }

 private:
  struct BlockHeader {
    // Index of the next block if this block is being chained or part of a free
    // list.
    uint32_t next_block_index;

    // Index to the first PostingListFree in the IndexBlock. This is the start
    // of the free list.
    PostingListIndex free_list_posting_list_index;

    // The size of each posting list in the IndexBlock. This value will be
    // initialized when calling CreateFromUninitializedRegion once and remain
    // unchanged.
    uint32_t posting_list_bytes;
  };

  // Assumes that fd has been opened for write.
  explicit IndexBlock(const Filesystem* filesystem,
                      PostingListSerializer* serializer, int fd,
                      off_t block_file_offset, uint32_t block_size_in_bytes,
                      uint32_t posting_list_bytes)
      : filesystem_(filesystem),
        serializer_(serializer),
        fd_(fd),
        block_file_offset_(block_file_offset),
        block_size_in_bytes_(block_size_in_bytes),
        posting_list_bytes_(posting_list_bytes) {}

  // Resets IndexBlock to hold posting lists of posting_list_bytes size and adds
  // all posting lists to the free list.
  //
  // RETURNS:
  //   - OK on success
  //   - INTERNAL_ERROR on I/O error
  libtextclassifier3::Status Reset();

  // Frees a posting list at posting_list_index, adds it into the free list
  // chain and updates (sets) the given block header instance.
  //
  // - This function is served to avoid redundant block header PWrite when
  //   freeing multiple posting lists.
  // - The caller should provide a BlockHeader instance for updating the free
  //   list chain, and finally sync it to disk.
  //
  // REQUIRES: 0 <= posting_list_index < max_posting_list_bytes()
  //
  // RETURNS:
  //   - OK on success
  //   - INTERNAL_ERROR on I/O error
  libtextclassifier3::Status FreePostingListImpl(
      BlockHeader& header, PostingListIndex posting_list_index);

  // PReads block header from the file.
  //
  // RETURNS:
  //   - A BlockHeader instance on success
  //   - INTERNAL_ERROR on I/O error
  libtextclassifier3::StatusOr<BlockHeader> ReadHeader() const;

  // PReads posting list content at posting_list_index. Note that it can be a
  // freed or allocated posting list.
  //
  // REQUIRES: 0 <= posting_list_index < max_posting_list_bytes()
  //
  // RETURNS:
  //   - A data buffer with size = posting_list_bytes_ on success
  //   - INTERNAL_ERROR on I/O error
  libtextclassifier3::StatusOr<std::unique_ptr<uint8_t[]>> ReadPostingList(
      PostingListIndex posting_list_index) const;

  // PWrites block header to the file.
  //
  // RETURNS:
  //   - OK on success
  //   - INTERNAL_ERROR on I/O error
  libtextclassifier3::Status WriteHeader(const BlockHeader& header);

  // PWrites posting list content at posting_list_index. Note that it can be a
  // freed or allocated posting list.
  //
  // REQUIRES: 0 <= posting_list_index < max_posting_list_bytes() and size of
  //   posting_list_buffer is posting_list_bytes_.
  //
  // RETURNS:
  //   - OK on success
  //   - INTERNAL_ERROR on I/O error
  libtextclassifier3::Status WritePostingList(
      PostingListIndex posting_list_index, const uint8_t* posting_list_buffer);

  // Retrieves the absolute file (fd) offset of a posting list at
  // posting_list_index.
  //
  // REQUIRES: 0 <= posting_list_index < max_posting_list_bytes()
  off_t get_posting_list_file_offset(
      PostingListIndex posting_list_index) const {
    return block_file_offset_ + sizeof(BlockHeader) +
           posting_list_bytes_ * posting_list_index;
  }

  // Retrieves the byte size in the block available for posting lists (excluding
  // the size of block header).
  uint32_t total_posting_lists_bytes() const {
    return block_size_in_bytes_ - sizeof(BlockHeader);
  }

  const Filesystem* filesystem_;  // Does not own.

  PostingListSerializer* serializer_;  // Does not own.

  int fd_;  // Does not own.

  off_t block_file_offset_;
  uint32_t block_size_in_bytes_;
  uint32_t posting_list_bytes_;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_FILE_POSTING_LIST_INDEX_BLOCK_H_
