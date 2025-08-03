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

#include "icing/file/posting_list/index-block.h"

#include <sys/types.h>

#include <cstdint>
#include <memory>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/absl_ports/canonical_errors.h"
#include "icing/absl_ports/str_cat.h"
#include "icing/file/posting_list/posting-list-common.h"
#include "icing/file/posting_list/posting-list-free.h"
#include "icing/file/posting_list/posting-list-used.h"
#include "icing/file/posting_list/posting-list-utils.h"
#include "icing/legacy/core/icing-string-util.h"
#include "icing/util/logging.h"
#include "icing/util/status-macros.h"

namespace icing {
namespace lib {

namespace {

libtextclassifier3::Status ValidatePostingListBytes(
    PostingListSerializer* serializer, uint32_t posting_list_bytes,
    uint32_t block_size) {
  if (posting_list_bytes > IndexBlock::CalculateMaxPostingListBytes(
                               block_size, serializer->GetDataTypeBytes()) ||
      !posting_list_utils::IsValidPostingListSize(
          posting_list_bytes, serializer->GetDataTypeBytes(),
          serializer->GetMinPostingListSize())) {
    return absl_ports::InvalidArgumentError(IcingStringUtil::StringPrintf(
        "Requested posting list size %d is illegal for a flash block with max "
        "posting list size of %d",
        posting_list_bytes,
        IndexBlock::CalculateMaxPostingListBytes(
            block_size, serializer->GetDataTypeBytes())));
  }
  return libtextclassifier3::Status::OK;
}

}  // namespace

/* static */ libtextclassifier3::StatusOr<IndexBlock>
IndexBlock::CreateFromPreexistingIndexBlockRegion(
    const Filesystem* filesystem, PostingListSerializer* serializer, int fd,
    off_t block_file_offset, uint32_t block_size) {
  if (block_size < sizeof(BlockHeader)) {
    return absl_ports::InvalidArgumentError(IcingStringUtil::StringPrintf(
        "Provided block_size %d is too small to fit even the BlockHeader!",
        block_size));
  }

  BlockHeader header;
  if (!filesystem->PRead(fd, &header, sizeof(BlockHeader), block_file_offset)) {
    return absl_ports::InternalError("PRead block header error");
  }

  ICING_RETURN_IF_ERROR(ValidatePostingListBytes(
      serializer, header.posting_list_bytes, block_size));

  return IndexBlock(filesystem, serializer, fd, block_file_offset, block_size,
                    header.posting_list_bytes);
}

/* static */ libtextclassifier3::StatusOr<IndexBlock>
IndexBlock::CreateFromUninitializedRegion(const Filesystem* filesystem,
                                          PostingListSerializer* serializer,
                                          int fd, off_t block_file_offset,
                                          uint32_t block_size,
                                          uint32_t posting_list_bytes) {
  if (block_size < sizeof(BlockHeader)) {
    return absl_ports::InvalidArgumentError(IcingStringUtil::StringPrintf(
        "Provided block_size %d is too small to fit even the BlockHeader!",
        block_size));
  }

  ICING_RETURN_IF_ERROR(
      ValidatePostingListBytes(serializer, posting_list_bytes, block_size));
  IndexBlock block(filesystem, serializer, fd, block_file_offset, block_size,
                   posting_list_bytes);
  ICING_RETURN_IF_ERROR(block.Reset());

  return block;
}

libtextclassifier3::StatusOr<IndexBlock::PostingListAndBlockInfo>
IndexBlock::GetAllocatedPostingList(PostingListIndex posting_list_index) {
  if (posting_list_index >= max_num_posting_lists() || posting_list_index < 0) {
    return absl_ports::InvalidArgumentError(IcingStringUtil::StringPrintf(
        "Cannot get posting list with index %d in IndexBlock with only %d "
        "posting lists.",
        posting_list_index, max_num_posting_lists()));
  }

  // Read out the header from disk.
  ICING_ASSIGN_OR_RETURN(BlockHeader header, ReadHeader());

  // Read out the allocated posting list from disk.
  ICING_ASSIGN_OR_RETURN(std::unique_ptr<uint8_t[]> posting_list_buffer,
                         ReadPostingList(posting_list_index));

  ICING_ASSIGN_OR_RETURN(
      PostingListUsed pl_used,
      PostingListUsed::CreateFromPreexistingPostingListUsedRegion(
          serializer_, std::move(posting_list_buffer), posting_list_bytes_));
  return PostingListAndBlockInfo(
      std::move(pl_used), posting_list_index, header.next_block_index,
      /*has_free_posting_lists_in=*/header.free_list_posting_list_index !=
          kInvalidPostingListIndex);
}

libtextclassifier3::StatusOr<IndexBlock::PostingListAndBlockInfo>
IndexBlock::AllocatePostingList() {
  // Read out the header from disk.
  ICING_ASSIGN_OR_RETURN(BlockHeader header, ReadHeader());

  if (header.free_list_posting_list_index == kInvalidPostingListIndex) {
    return absl_ports::ResourceExhaustedError(
        "No available posting lists to allocate.");
  }

  // Pull one off the free list.
  PostingListIndex posting_list_index = header.free_list_posting_list_index;

  // Read out the posting list from disk.
  ICING_ASSIGN_OR_RETURN(std::unique_ptr<uint8_t[]> posting_list_buffer,
                         ReadPostingList(posting_list_index));
  // Step 1: get the next (chained) free posting list index and set it to block
  //         header.
  ICING_ASSIGN_OR_RETURN(
      PostingListFree pl_free,
      PostingListFree::CreateFromPreexistingPostingListFreeRegion(
          posting_list_buffer.get(), posting_list_bytes_,
          serializer_->GetDataTypeBytes(),
          serializer_->GetMinPostingListSize()));
  header.free_list_posting_list_index = pl_free.get_next_posting_list_index();
  if (header.free_list_posting_list_index != kInvalidPostingListIndex &&
      header.free_list_posting_list_index >= max_num_posting_lists()) {
    ICING_LOG(ERROR)
        << "Free Posting List points to an invalid posting list index!";
    header.free_list_posting_list_index = kInvalidPostingListIndex;
  }

  // Step 2: create PostingListUsed instance. The original content in the above
  //         posting_list_buffer is not important now because
  //         PostingListUsed::CreateFromUnitializedRegion will wipe it out, and
  //         we only need to sync it to disk after initializing.
  ICING_ASSIGN_OR_RETURN(PostingListUsed pl_used,
                         PostingListUsed::CreateFromUnitializedRegion(
                             serializer_, posting_list_bytes_));

  // Sync the initialized posting list (overwrite the original content of
  // PostingListFree) and header to disk.
  ICING_RETURN_IF_ERROR(
      WritePostingList(posting_list_index, pl_used.posting_list_buffer()));
  ICING_RETURN_IF_ERROR(WriteHeader(header));

  return PostingListAndBlockInfo(
      std::move(pl_used), posting_list_index, header.next_block_index,
      /*has_free_posting_lists_in=*/header.free_list_posting_list_index !=
          kInvalidPostingListIndex);
}

libtextclassifier3::Status IndexBlock::FreePostingList(
    PostingListIndex posting_list_index) {
  if (posting_list_index >= max_num_posting_lists() || posting_list_index < 0) {
    return absl_ports::InvalidArgumentError(IcingStringUtil::StringPrintf(
        "Cannot free posting list with index %d in IndexBlock with only %d "
        "posting lists.",
        posting_list_index, max_num_posting_lists()));
  }

  ICING_ASSIGN_OR_RETURN(BlockHeader header, ReadHeader());
  ICING_RETURN_IF_ERROR(FreePostingListImpl(header, posting_list_index));
  ICING_RETURN_IF_ERROR(WriteHeader(header));
  return libtextclassifier3::Status::OK;
}

libtextclassifier3::Status IndexBlock::WritePostingListToDisk(
    const PostingListUsed& posting_list_used,
    PostingListIndex posting_list_index) {
  if (posting_list_index >= max_num_posting_lists() || posting_list_index < 0) {
    return absl_ports::InvalidArgumentError(IcingStringUtil::StringPrintf(
        "Cannot write posting list with index %d in IndexBlock with only %d "
        "posting lists.",
        posting_list_index, max_num_posting_lists()));
  }

  if (posting_list_used.size_in_bytes() != posting_list_bytes_) {
    return absl_ports::InvalidArgumentError(
        "Cannot write posting list into a block with different posting list "
        "bytes");
  }

  if (!posting_list_used.is_dirty()) {
    return libtextclassifier3::Status::OK;
  }

  // Write the allocated posting list to disk.
  return WritePostingList(posting_list_index,
                          posting_list_used.posting_list_buffer());
}

libtextclassifier3::StatusOr<uint32_t> IndexBlock::GetNextBlockIndex() const {
  ICING_ASSIGN_OR_RETURN(BlockHeader header, ReadHeader());
  return header.next_block_index;
}

libtextclassifier3::Status IndexBlock::SetNextBlockIndex(
    uint32_t next_block_index) {
  ICING_ASSIGN_OR_RETURN(BlockHeader header, ReadHeader());
  header.next_block_index = next_block_index;
  ICING_RETURN_IF_ERROR(WriteHeader(header));

  return libtextclassifier3::Status::OK;
}

libtextclassifier3::StatusOr<bool> IndexBlock::HasFreePostingLists() const {
  ICING_ASSIGN_OR_RETURN(BlockHeader header, ReadHeader());
  return header.free_list_posting_list_index != kInvalidPostingListIndex;
}

libtextclassifier3::Status IndexBlock::Reset() {
  BlockHeader header;
  header.free_list_posting_list_index = kInvalidPostingListIndex;
  header.next_block_index = kInvalidBlockIndex;
  header.posting_list_bytes = posting_list_bytes_;

  // Starting with the last posting list, prepend each posting list to the free
  // list. At the end, the beginning of the free list should be the first
  // posting list.
  for (PostingListIndex posting_list_index = max_num_posting_lists() - 1;
       posting_list_index >= 0; --posting_list_index) {
    // Adding the posting list at posting_list_index to the free list will
    // modify both the posting list and also
    // header.free_list_posting_list_index.
    ICING_RETURN_IF_ERROR(FreePostingListImpl(header, posting_list_index));
  }

  // Sync the header to disk.
  ICING_RETURN_IF_ERROR(WriteHeader(header));

  return libtextclassifier3::Status::OK;
}

libtextclassifier3::Status IndexBlock::FreePostingListImpl(
    BlockHeader& header, PostingListIndex posting_list_index) {
  // Read out the posting list from disk.
  ICING_ASSIGN_OR_RETURN(std::unique_ptr<uint8_t[]> posting_list_buffer,
                         ReadPostingList(posting_list_index));

  ICING_ASSIGN_OR_RETURN(PostingListFree plfree,
                         PostingListFree::CreateFromUnitializedRegion(
                             posting_list_buffer.get(), posting_list_bytes(),
                             serializer_->GetDataTypeBytes(),
                             serializer_->GetMinPostingListSize()));

  // Put at the head of the list.
  plfree.set_next_posting_list_index(header.free_list_posting_list_index);
  header.free_list_posting_list_index = posting_list_index;

  // Sync the posting list to disk.
  ICING_RETURN_IF_ERROR(
      WritePostingList(posting_list_index, posting_list_buffer.get()));
  return libtextclassifier3::Status::OK;
}

libtextclassifier3::StatusOr<IndexBlock::BlockHeader> IndexBlock::ReadHeader()
    const {
  BlockHeader header;
  if (!filesystem_->PRead(fd_, &header, sizeof(BlockHeader),
                          block_file_offset_)) {
    return absl_ports::InternalError(
        absl_ports::StrCat("PRead block header error: ", strerror(errno)));
  }
  if (header.posting_list_bytes != posting_list_bytes_) {
    return absl_ports::InternalError(IcingStringUtil::StringPrintf(
        "Inconsistent posting list bytes between block header (%d) and class "
        "instance (%d)",
        header.posting_list_bytes, posting_list_bytes_));
  }
  return header;
}

libtextclassifier3::StatusOr<std::unique_ptr<uint8_t[]>>
IndexBlock::ReadPostingList(PostingListIndex posting_list_index) const {
  auto posting_list_buffer = std::make_unique<uint8_t[]>(posting_list_bytes_);
  if (!filesystem_->PRead(fd_, posting_list_buffer.get(), posting_list_bytes_,
                          get_posting_list_file_offset(posting_list_index))) {
    return absl_ports::InternalError(
        absl_ports::StrCat("PRead posting list error: ", strerror(errno)));
  }
  return posting_list_buffer;
}

libtextclassifier3::Status IndexBlock::WriteHeader(const BlockHeader& header) {
  if (!filesystem_->PWrite(fd_, block_file_offset_, &header,
                           sizeof(BlockHeader))) {
    return absl_ports::InternalError(
        absl_ports::StrCat("PWrite block header error: ", strerror(errno)));
  }
  return libtextclassifier3::Status::OK;
}

libtextclassifier3::Status IndexBlock::WritePostingList(
    PostingListIndex posting_list_index, const uint8_t* posting_list_buffer) {
  if (!filesystem_->PWrite(fd_,
                           get_posting_list_file_offset(posting_list_index),
                           posting_list_buffer, posting_list_bytes_)) {
    return absl_ports::InternalError(
        absl_ports::StrCat("PWrite posting list error: ", strerror(errno)));
  }
  return libtextclassifier3::Status::OK;
}

}  // namespace lib
}  // namespace icing
