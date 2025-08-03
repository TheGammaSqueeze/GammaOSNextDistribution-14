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

#ifndef ICING_FILE_POSTING_LIST_FLASH_INDEX_STORAGE_H_
#define ICING_FILE_POSTING_LIST_FLASH_INDEX_STORAGE_H_

#include <cstdint>
#include <memory>
#include <string>
#include <vector>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/file/filesystem.h"
#include "icing/file/posting_list/flash-index-storage-header.h"
#include "icing/file/posting_list/index-block.h"
#include "icing/file/posting_list/posting-list-identifier.h"
#include "icing/file/posting_list/posting-list-used.h"
#include "icing/proto/debug.pb.h"
#include "icing/store/document-id.h"

namespace icing {
namespace lib {

// PostingListHolder: group PostingListUsed, id, and some other useful info for
// callers.
struct PostingListHolder {
  // PostingListUsed owns an in-memory posting list data buffer. The data being
  // interpreted is initialized via PRead from the storage. As such, we should
  // sync it to disk after modifying it.
  PostingListUsed posting_list;

  // The PostingListIdentifier, which identifies both the block index and the
  // posting list index on that block, is also returned for convenience.
  PostingListIdentifier id;

  // Next block index is also returned for convenience. If PostingListUsed is a
  // max-sized posting list, then the caller has to use this value to handle
  // chained max-sized posting list blocks.
  uint32_t next_block_index;

  explicit PostingListHolder(PostingListUsed&& posting_list_in,
                             PostingListIdentifier id_in,
                             uint32_t next_block_index_in)
      : posting_list(std::move(posting_list_in)),
        id(id_in),
        next_block_index(next_block_index_in) {}
};

// The FlashIndexStorage class manages the actual file that makes up blocks for
// posting lists. It allocates IndexBlocks as needed and maintains freelists to
// prevent excessive block fragmentation.
//
// It maintains two types of free lists:
//   1. On-disk, Header free list - This free list is stored in the Header
//      block. There is a free list for every possible posting list size. Each
//      entry for a posting list size contains the block_index of the
//      IndexBlock that starts the free list chain. Each IndexBlock in the free
//      list chain stores the index of the next IndexBlock in the chain.
//   2. In-memory free list - Like the Header free list, there is a free list of
//      every possible posting list size. This free list contains not just the
//      block_index of the available IndexBlock, but also the posting_list_index
//      of the available PostingListUsed within the IndexBlock. This is because,
//      unlike the Header free list, PostingListUseds are not actually freed
//      when added to this free list.
//
// Whether or not the in-memory free list is used can be chosen via the
// in_memory param to the Create factory function.
//
// The advantage of using the in-memory free list is that it reduces the amount
// of flash writes made while editing the index (because actually freeing the
// PostingLists would require writing to that flash block). The disadvantage is
// that it introduces code complexity and potentially leaks blocks if power is
// lost or if FlashIndexStorage is destroyed before emptying the free list.
class FlashIndexStorage {
 public:
  // Creates a FlashIndexStorage at index_filename. in_memory determines whether
  // or not the FlashIndexStorage maintains an in-memory freelist in order to
  // avoid writes to the on-disk freelist.
  //
  // RETURNS:
  //   - On success, a valid instance of FlashIndexStorage
  //   - FAILED_PRECONDITION_ERROR if filesystem or serializer is null
  //   - INTERNAL_ERROR if unable to create a new header or read the existing
  //     one from disk.
  static libtextclassifier3::StatusOr<FlashIndexStorage> Create(
      std::string index_filename, const Filesystem* filesystem,
      PostingListSerializer* serializer, bool in_memory = true);

  // Reads magic from existing file header. We need this during Icing
  // initialization phase to determine the version.
  //
  // RETURNS:
  //   - On success, a valid magic
  //   - FAILED_PRECONDITION_ERROR if filesystem is null
  //   - NOT_FOUND_ERROR if the flash index file doesn't exist
  //   - INTERNAL_ERROR on I/O error
  static libtextclassifier3::StatusOr<int> ReadHeaderMagic(
      const Filesystem* filesystem, const std::string& index_filename);

  FlashIndexStorage(FlashIndexStorage&&) = default;
  FlashIndexStorage(const FlashIndexStorage&) = delete;
  FlashIndexStorage& operator=(FlashIndexStorage&&) = default;
  FlashIndexStorage& operator=(const FlashIndexStorage&) = delete;

  ~FlashIndexStorage();

  // Selects block size to use.
  static uint32_t SelectBlockSize();

  // Retrieves the PostingList referred to by PostingListIdentifier. This
  // posting list must have been previously allocated by a prior call to
  // AllocatePostingList.
  //
  // RETURNS:
  //   - On success, a valid instance of PostingListHolder containing the
  //     requested PostingListUsed.
  //   - Any IndexBlock errors
  libtextclassifier3::StatusOr<PostingListHolder> GetPostingList(
      PostingListIdentifier id) const;

  // Allocates and returns a PostingListHolder containing a PostingListUsed that
  // can fit min_posting_list_bytes.
  //
  // RETURNS:
  //   - On success, a valid instance of PostingListHolder containing the
  //     requested PostingListUsed.
  //   - INVALID_ARGUMENT_ERROR if min_posting_list_bytes >
  //     max_posting_list_bytes()
  //   - RESOURCE_EXHAUSTED_ERROR if unable to grow the index to create a
  //     PostingListUsed of the requested size.
  //   - Any IndexBlock errors
  libtextclassifier3::StatusOr<PostingListHolder> AllocatePostingList(
      uint32_t min_posting_list_bytes);

  // Allocates a new IndexBlock with a single max-sized PostingListUsed. This
  // chains index blocks by setting the next_block_index field of this new
  // block's header to be prev_block_index and returns a PostingListHolder
  // containing a max-sized PostingListUsed.
  //
  // RETURNS:
  //   - On success, a valid instance of PostingListHolder containing the
  //     requested PostingListUsed.
  //   - RESOURCE_EXHAUSTED_ERROR if unable to grow the index to create a
  //     PostingListUsed of max size
  //   - Any IndexBlock errors
  libtextclassifier3::StatusOr<PostingListHolder>
  AllocateAndChainMaxSizePostingList(uint32_t prev_block_index);

  // Frees the PostingListUsed that this holder holds.
  //
  // RETURNS:
  //   - OK on success
  //   - Any IndexBlock errors
  libtextclassifier3::Status FreePostingList(PostingListHolder&& holder);

  // Writes back the PostingListUsed that this holder holds to disk.
  //
  // RETURNS:
  //   - OK on success
  //   - Any IndexBlock errors
  libtextclassifier3::Status WritePostingListToDisk(
      const PostingListHolder& holder);

  // Discards all existing data by deleting the existing file and
  // re-initializing a new one.
  //
  // RETURNS:
  //   - OK on success
  //   - INTERNAL_ERROR if unable to delete existing files or initialize a new
  //     file with header
  libtextclassifier3::Status Reset();

  // Used to track the largest docid indexed in the index.
  DocumentId get_last_indexed_docid() const {
    return header_block_->header()->last_indexed_docid;
  }
  void set_last_indexed_docid(DocumentId docid) {
    header_block_->header()->last_indexed_docid = docid;
  }

  // Updates the header and persists all changes to the index to disk. Returns
  // true on success.
  bool PersistToDisk();

  // Returns the size of the index file in bytes.
  int64_t GetDiskUsage() const {
    return filesystem_->GetDiskUsage(storage_sfd_.get());
  }

  // Returns the size of the index file used to contains data.
  uint64_t GetElementsSize() const {
    // Element size is the same as disk size excluding the header block.
    return GetDiskUsage() - block_size();
  }

  int num_blocks() const { return num_blocks_; }

  // Gets the byte size of max sized posting list.
  uint32_t max_posting_list_bytes() const {
    return IndexBlock::CalculateMaxPostingListBytes(
        block_size(), serializer_->GetDataTypeBytes());
  }

  // Info about the index based on the block size.
  int block_size() const { return header_block_->header()->block_size; }

  // Num blocks starts at 1 since the first block is the header.
  bool empty() const { return num_blocks_ <= 1; }

  // The percentage of the maximum index size that is free. Allocated blocks are
  // treated as fully used, even if they are only partially used. In this way,
  // min_free_fraction is a lower bound of available space.
  double min_free_fraction() const {
    return 1.0 - static_cast<double>(num_blocks_) / kMaxBlockIndex;
  }

  const PostingListSerializer* serializer() const { return serializer_; }
  PostingListSerializer* serializer() { return serializer_; }

  // TODO(b/222349894) Convert the string output to a protocol buffer instead.
  void GetDebugInfo(DebugInfoVerbosity::Code verbosity, std::string* out) const;

 private:
  explicit FlashIndexStorage(const Filesystem* filesystem,
                             std::string&& index_filename,
                             PostingListSerializer* serializer,
                             bool has_in_memory_freelists)
      : filesystem_(filesystem),
        index_filename_(std::move(index_filename)),
        serializer_(serializer),
        num_blocks_(0),
        has_in_memory_freelists_(has_in_memory_freelists) {}

  // Init the index from persistence. Create if file does not exist. We do not
  // erase corrupt files.
  //
  // Returns false if unable to create a new header or if the existing one is
  // corrupt.
  bool Init();

  // Create or open the header block. Returns true on success.
  bool InitHeader();

  // Create a new header block for an empty index file.
  bool CreateHeader();

  // Loads the header stored at the beginning of the index file and validates
  // the values stored in it.
  bool OpenHeader(int64_t file_size);

  // Adds the IndexBlock referred to by block_index in the on-disk free list
  // with index block_info_index.
  void AddToOnDiskFreeList(uint32_t block_index, int block_info_index,
                           IndexBlock* index_block);

  // Removes the IndexBlock referred to by block_index from the Header free list
  // with index block_info_index.
  //
  // RETURNS:
  //   - OK on success
  //   - Any IndexBlock errors
  libtextclassifier3::Status RemoveFromOnDiskFreeList(uint32_t block_index,
                                                      int block_info_index,
                                                      IndexBlock* index_block);

  // RETURNS:
  //   - On success, a valid PostingListHolder created from the first entry of
  //     the in-memory freelist at block_info_index
  //   - OUT_OF_RANGE_ERROR if in_memory_freelists_ contains
  //     PostingListIdentifier with block_index >= num_blocks_
  //   - NOT_FOUND_ERROR if there was no entry in the freelist
  //   - Any IndexBlock errors
  libtextclassifier3::StatusOr<PostingListHolder>
  GetPostingListFromInMemoryFreeList(int block_info_index);

  // RETURNS:
  //   - On success, a valid PostingListHolder created from the first entry of
  //     the on-disk freelist at block_info_index
  //   - OUT_OF_RANGE_ERROR if header()->index_block_infos[block_info_index]
  //     contains block_index >= num_blocks_
  //   - NOT_FOUND_ERROR if there was no entry in the freelist
  //   - Any IndexBlock errors
  libtextclassifier3::StatusOr<PostingListHolder>
  GetPostingListFromOnDiskFreeList(int block_info_index);

  // Returns:
  //   - On success, a valid PostingListHolder created from a newly allocated
  //     IndexBlock.
  //   - RESOURCE_EXHAUSTED if the index couldn't be grown to fit a new
  //     IndexBlock.
  //   - Any IndexBlock errors
  libtextclassifier3::StatusOr<PostingListHolder> AllocateNewPostingList(
      int block_info_index);

  // Returns:
  //   - On success, a newly created IndexBlock at block_index with posting
  //     lists of size posting_list_size
  //   - OUT_OF_RANGE_ERROR if block_index >= num_blocks_
  //   - Any IndexBlock errors
  libtextclassifier3::StatusOr<IndexBlock> CreateIndexBlock(
      uint32_t block_index, uint32_t posting_list_size) const;

  // Returns:
  //   - On success, the IndexBlock that exists at block_index
  //   - OUT_OF_RANGE_ERROR if block_index >= num_blocks_
  //   - Any IndexBlock errors
  libtextclassifier3::StatusOr<IndexBlock> GetIndexBlock(
      uint32_t block_index) const;

  // Add a new block to the end of the file and return its block
  // index. Returns kInvalidBlockIndex if unable to grow the index file.
  int GrowIndex();

  // Return the index into index_block_infos of the smallest posting_list free
  // list that can fit posting_list_bytes or -1 if posting_list_bytes exceeds
  // the max-sized posting list.
  int FindBestIndexBlockInfo(uint32_t posting_list_bytes) const;

  // Flushes the in-memory free list to disk.
  //
  // RETURNS:
  //   - OK on success
  //   - Any IndexBlock errors
  libtextclassifier3::Status FlushInMemoryFreeList();

  const Filesystem* filesystem_;  // not owned; can't be null
  std::string index_filename_;

  PostingListSerializer* serializer_;  // not owned; can't be null

  // We open the index file into this fd.
  ScopedFd storage_sfd_;

  int num_blocks_;  // can be inferred from index file size

  std::unique_ptr<HeaderBlock> header_block_;

  // In-memory cache of free posting lists.
  struct FreeList {
    // Experimentally determined that high watermark for largest
    // freelist was ~3500.
    static constexpr size_t kMaxSize = 4096;

    // Push a new PostingListIdentifier if there is space.
    void Push(PostingListIdentifier id);

    // Attempt to pop a PostingListIdentifier.
    //
    // RETURNS:
    //  - identifier of a free posting list, on success
    //  - NOT_FOUND if there are no free posting lists on this free list.
    libtextclassifier3::StatusOr<PostingListIdentifier> TryPop();

    std::string DebugString() const;

   private:
    std::vector<PostingListIdentifier> free_list_;
    int free_list_size_high_watermark_ = 0;
    int num_dropped_free_list_entries_ = 0;
  };
  std::vector<FreeList> in_memory_freelists_;

  bool has_in_memory_freelists_;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_FILE_POSTING_LIST_FLASH_INDEX_STORAGE_H_
