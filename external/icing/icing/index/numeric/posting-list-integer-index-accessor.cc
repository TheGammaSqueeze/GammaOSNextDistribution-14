// Copyright (C) 2022 Google LLC
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

#include "icing/index/numeric/posting-list-integer-index-accessor.h"

#include <cstdint>
#include <memory>
#include <vector>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/absl_ports/canonical_errors.h"
#include "icing/file/posting_list/flash-index-storage.h"
#include "icing/file/posting_list/index-block.h"
#include "icing/file/posting_list/posting-list-identifier.h"
#include "icing/file/posting_list/posting-list-used.h"
#include "icing/index/numeric/integer-index-data.h"
#include "icing/index/numeric/posting-list-integer-index-serializer.h"
#include "icing/util/status-macros.h"

namespace icing {
namespace lib {

/* static */ libtextclassifier3::StatusOr<
    std::unique_ptr<PostingListIntegerIndexAccessor>>
PostingListIntegerIndexAccessor::Create(
    FlashIndexStorage* storage, PostingListIntegerIndexSerializer* serializer) {
  uint32_t max_posting_list_bytes = IndexBlock::CalculateMaxPostingListBytes(
      storage->block_size(), serializer->GetDataTypeBytes());
  ICING_ASSIGN_OR_RETURN(PostingListUsed in_memory_posting_list,
                         PostingListUsed::CreateFromUnitializedRegion(
                             serializer, max_posting_list_bytes));
  return std::unique_ptr<PostingListIntegerIndexAccessor>(
      new PostingListIntegerIndexAccessor(
          storage, std::move(in_memory_posting_list), serializer));
}

/* static */ libtextclassifier3::StatusOr<
    std::unique_ptr<PostingListIntegerIndexAccessor>>
PostingListIntegerIndexAccessor::CreateFromExisting(
    FlashIndexStorage* storage, PostingListIntegerIndexSerializer* serializer,
    PostingListIdentifier existing_posting_list_id) {
  ICING_ASSIGN_OR_RETURN(
      std::unique_ptr<PostingListIntegerIndexAccessor> pl_accessor,
      Create(storage, serializer));
  ICING_ASSIGN_OR_RETURN(PostingListHolder holder,
                         storage->GetPostingList(existing_posting_list_id));
  pl_accessor->preexisting_posting_list_ =
      std::make_unique<PostingListHolder>(std::move(holder));
  return pl_accessor;
}

// Returns the next batch of integer index data for the provided posting list.
libtextclassifier3::StatusOr<std::vector<IntegerIndexData>>
PostingListIntegerIndexAccessor::GetNextDataBatch() {
  return GetNextDataBatchImpl(/*free_posting_list=*/false);
}

libtextclassifier3::StatusOr<std::vector<IntegerIndexData>>
PostingListIntegerIndexAccessor::GetAllDataAndFree() {
  if (preexisting_posting_list_ == nullptr) {
    return absl_ports::FailedPreconditionError(
        "Cannot retrieve data from a PostingListIntegerIndexAccessor that "
        "was not created from a preexisting posting list.");
  }

  std::vector<IntegerIndexData> all_data;
  while (true) {
    ICING_ASSIGN_OR_RETURN(std::vector<IntegerIndexData> batch,
                           GetNextDataBatchImpl(/*free_posting_list=*/true));
    if (batch.empty()) {
      break;
    }
    std::move(batch.begin(), batch.end(), std::back_inserter(all_data));
  }

  return all_data;
}

libtextclassifier3::Status PostingListIntegerIndexAccessor::PrependData(
    const IntegerIndexData& data) {
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

libtextclassifier3::StatusOr<std::vector<IntegerIndexData>>
PostingListIntegerIndexAccessor::GetNextDataBatchImpl(bool free_posting_list) {
  if (preexisting_posting_list_ == nullptr) {
    if (has_reached_posting_list_chain_end_) {
      return std::vector<IntegerIndexData>();
    }
    return absl_ports::FailedPreconditionError(
        "Cannot retrieve data from a PostingListIntegerIndexAccessor that "
        "was not created from a preexisting posting list.");
  }
  ICING_ASSIGN_OR_RETURN(
      std::vector<IntegerIndexData> batch,
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

  if (free_posting_list) {
    ICING_RETURN_IF_ERROR(
        storage_->FreePostingList(std::move(*preexisting_posting_list_)));
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

}  // namespace lib
}  // namespace icing
