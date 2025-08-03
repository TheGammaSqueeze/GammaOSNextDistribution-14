// Copyright (C) 2023 Google LLC
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

#ifndef ICING_JOIN_POSTING_LIST_JOIN_DATA_ACCESSOR_H_
#define ICING_JOIN_POSTING_LIST_JOIN_DATA_ACCESSOR_H_

#include <cstdint>
#include <memory>
#include <utility>
#include <vector>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/absl_ports/canonical_errors.h"
#include "icing/file/posting_list/flash-index-storage.h"
#include "icing/file/posting_list/index-block.h"
#include "icing/file/posting_list/posting-list-accessor.h"
#include "icing/file/posting_list/posting-list-common.h"
#include "icing/file/posting_list/posting-list-identifier.h"
#include "icing/file/posting_list/posting-list-used.h"
#include "icing/join/posting-list-join-data-serializer.h"
#include "icing/legacy/index/icing-bit-util.h"
#include "icing/util/status-macros.h"

namespace icing {
namespace lib {

// This class is used to provide a simple abstraction for adding join data to
// posting lists. PostingListJoinDataAccessor handles:
// 1) selection of properly-sized posting lists for the accumulated join index
//    data during Finalize()
// 2) chaining of max-sized posting lists.
template <typename JoinDataType>
class PostingListJoinDataAccessor : public PostingListAccessor {
 public:
  // Creates an empty PostingListJoinDataAccessor.
  //
  // RETURNS:
  //   - On success, a valid instance of PostingListJoinDataAccessor
  //   - INVALID_ARGUMENT error if storage has an invalid block_size.
  static libtextclassifier3::StatusOr<
      std::unique_ptr<PostingListJoinDataAccessor<JoinDataType>>>
  Create(FlashIndexStorage* storage,
         PostingListJoinDataSerializer<JoinDataType>* serializer);

  // Creates a PostingListJoinDataAccessor with an existing posting list
  // identified by existing_posting_list_id.
  //
  // RETURNS:
  //   - On success, a valid instance of PostingListJoinDataAccessor
  //   - INVALID_ARGUMENT if storage has an invalid block_size.
  static libtextclassifier3::StatusOr<
      std::unique_ptr<PostingListJoinDataAccessor<JoinDataType>>>
  CreateFromExisting(FlashIndexStorage* storage,
                     PostingListJoinDataSerializer<JoinDataType>* serializer,
                     PostingListIdentifier existing_posting_list_id);

  PostingListSerializer* GetSerializer() override { return serializer_; }

  // Retrieves the next batch of data in the posting list chain.
  //
  // RETURNS:
  //   - On success, a vector of join data in the posting list chain
  //   - FAILED_PRECONDITION_ERROR if called on an instance that was created via
  //     Create.
  //   - INTERNAL_ERROR if unable to read the next posting list in the chain or
  //     if the posting list has been corrupted somehow.
  libtextclassifier3::StatusOr<std::vector<JoinDataType>> GetNextDataBatch();

  // Prepends one data. This may result in flushing the posting list to disk (if
  // the PostingListJoinDataAccessor holds a max-sized posting list that is
  // full) or freeing a pre-existing posting list if it is too small to fit all
  // data necessary.
  //
  // RETURNS:
  //   - OK, on success
  //   - INVALID_ARGUMENT if !data.is_valid() or if data is greater than the
  //     previously added data.
  //   - RESOURCE_EXHAUSTED error if unable to grow the index to allocate a new
  //     posting list.
  libtextclassifier3::Status PrependData(const JoinDataType& data);

 private:
  explicit PostingListJoinDataAccessor(
      FlashIndexStorage* storage, PostingListUsed in_memory_posting_list,
      PostingListJoinDataSerializer<JoinDataType>* serializer)
      : PostingListAccessor(storage, std::move(in_memory_posting_list)),
        serializer_(serializer) {}

  PostingListJoinDataSerializer<JoinDataType>* serializer_;  // Does not own.
};

template <typename JoinDataType>
/* static */ libtextclassifier3::StatusOr<
    std::unique_ptr<PostingListJoinDataAccessor<JoinDataType>>>
PostingListJoinDataAccessor<JoinDataType>::Create(
    FlashIndexStorage* storage,
    PostingListJoinDataSerializer<JoinDataType>* serializer) {
  uint32_t max_posting_list_bytes = IndexBlock::CalculateMaxPostingListBytes(
      storage->block_size(), serializer->GetDataTypeBytes());
  ICING_ASSIGN_OR_RETURN(PostingListUsed in_memory_posting_list,
                         PostingListUsed::CreateFromUnitializedRegion(
                             serializer, max_posting_list_bytes));
  return std::unique_ptr<PostingListJoinDataAccessor<JoinDataType>>(
      new PostingListJoinDataAccessor<JoinDataType>(
          storage, std::move(in_memory_posting_list), serializer));
}

template <typename JoinDataType>
/* static */ libtextclassifier3::StatusOr<
    std::unique_ptr<PostingListJoinDataAccessor<JoinDataType>>>
PostingListJoinDataAccessor<JoinDataType>::CreateFromExisting(
    FlashIndexStorage* storage,
    PostingListJoinDataSerializer<JoinDataType>* serializer,
    PostingListIdentifier existing_posting_list_id) {
  ICING_ASSIGN_OR_RETURN(
      std::unique_ptr<PostingListJoinDataAccessor<JoinDataType>> pl_accessor,
      Create(storage, serializer));
  ICING_ASSIGN_OR_RETURN(PostingListHolder holder,
                         storage->GetPostingList(existing_posting_list_id));
  pl_accessor->preexisting_posting_list_ =
      std::make_unique<PostingListHolder>(std::move(holder));
  return pl_accessor;
}

// Returns the next batch of join data for the provided posting list.
template <typename JoinDataType>
libtextclassifier3::StatusOr<std::vector<JoinDataType>>
PostingListJoinDataAccessor<JoinDataType>::GetNextDataBatch() {
  if (preexisting_posting_list_ == nullptr) {
    if (has_reached_posting_list_chain_end_) {
      return std::vector<JoinDataType>();
    }
    return absl_ports::FailedPreconditionError(
        "Cannot retrieve data from a PostingListJoinDataAccessor that was not "
        "created from a preexisting posting list.");
  }
  ICING_ASSIGN_OR_RETURN(
      std::vector<JoinDataType> batch,
      serializer_->GetData(&preexisting_posting_list_->posting_list));
  uint32_t next_block_index = kInvalidBlockIndex;
  // Posting lists will only be chained when they are max-sized, in which case
  // next_block_index will point to the next block for the next posting list.
  // Otherwise, next_block_index can be kInvalidBlockIndex or be used to point
  // to the next free list block, which is not relevant here.
  if (preexisting_posting_list_->posting_list.size_in_bytes() ==
      storage_->max_posting_list_bytes()) {
    next_block_index = preexisting_posting_list_->next_block_index;
  }

  if (next_block_index != kInvalidBlockIndex) {
    // Since we only have to deal with next block for max-sized posting list
    // block, max_num_posting_lists is 1 and posting_list_index_bits is
    // BitsToStore(1).
    PostingListIdentifier next_posting_list_id(
        next_block_index, /*posting_list_index=*/0,
        /*posting_list_index_bits=*/BitsToStore(1));
    ICING_ASSIGN_OR_RETURN(PostingListHolder holder,
                           storage_->GetPostingList(next_posting_list_id));
    preexisting_posting_list_ =
        std::make_unique<PostingListHolder>(std::move(holder));
  } else {
    has_reached_posting_list_chain_end_ = true;
    preexisting_posting_list_.reset();
  }
  return batch;
}

template <typename JoinDataType>
libtextclassifier3::Status
PostingListJoinDataAccessor<JoinDataType>::PrependData(
    const JoinDataType& data) {
  PostingListUsed& active_pl = (preexisting_posting_list_ != nullptr)
                                   ? preexisting_posting_list_->posting_list
                                   : in_memory_posting_list_;
  libtextclassifier3::Status status =
      serializer_->PrependData(&active_pl, data);
  if (!absl_ports::IsResourceExhausted(status)) {
    return status;
  }
  // There is no more room to add data to this current posting list! Therefore,
  // we need to either move those data to a larger posting list or flush this
  // posting list and create another max-sized posting list in the chain.
  if (preexisting_posting_list_ != nullptr) {
    ICING_RETURN_IF_ERROR(FlushPreexistingPostingList());
  } else {
    ICING_RETURN_IF_ERROR(FlushInMemoryPostingList());
  }

  // Re-add data. Should always fit since we just cleared
  // in_memory_posting_list_. It's fine to explicitly reference
  // in_memory_posting_list_ here because there's no way of reaching this line
  // while preexisting_posting_list_ is still in use.
  return serializer_->PrependData(&in_memory_posting_list_, data);
}

}  // namespace lib
}  // namespace icing

#endif  // ICING_JOIN_POSTING_LIST_JOIN_DATA_ACCESSOR_H_
