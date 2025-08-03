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

#include "icing/file/posting_list/posting-list-accessor.h"

#include <cstdint>
#include <memory>
#include <utility>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/absl_ports/canonical_errors.h"
#include "icing/file/posting_list/flash-index-storage.h"
#include "icing/file/posting_list/posting-list-identifier.h"
#include "icing/file/posting_list/posting-list-used.h"
#include "icing/util/status-macros.h"

namespace icing {
namespace lib {

libtextclassifier3::Status PostingListAccessor::FlushPreexistingPostingList() {
  if (preexisting_posting_list_->posting_list.size_in_bytes() ==
      storage_->max_posting_list_bytes()) {
    // If this is a max-sized posting list, then sync to disk and keep track of
    // the id.
    ICING_RETURN_IF_ERROR(
        storage_->WritePostingListToDisk(*preexisting_posting_list_));
    prev_block_identifier_ = preexisting_posting_list_->id;
  } else {
    // If this is NOT a max-sized posting list, then our data have outgrown this
    // particular posting list. Move the data into the in-memory posting list
    // and free this posting list.
    //
    // Move will always succeed since in_memory_posting_list_ is max_pl_bytes.
    ICING_RETURN_IF_ERROR(GetSerializer()->MoveFrom(
        /*dst=*/&in_memory_posting_list_,
        /*src=*/&preexisting_posting_list_->posting_list));

    // Now that all the contents of this posting list have been copied, there's
    // no more use for it. Make it available to be used for another posting
    // list.
    ICING_RETURN_IF_ERROR(
        storage_->FreePostingList(std::move(*preexisting_posting_list_)));
  }
  preexisting_posting_list_.reset();
  return libtextclassifier3::Status::OK;
}

libtextclassifier3::Status PostingListAccessor::FlushInMemoryPostingList() {
  // We exceeded max_pl_bytes(). Need to flush in_memory_posting_list_ and
  // update the chain.
  ICING_ASSIGN_OR_RETURN(PostingListHolder holder,
                         storage_->AllocateAndChainMaxSizePostingList(
                             prev_block_identifier_.block_index()));
  ICING_RETURN_IF_ERROR(
      GetSerializer()->MoveFrom(/*dst=*/&holder.posting_list,
                                /*src=*/&in_memory_posting_list_));
  ICING_RETURN_IF_ERROR(storage_->WritePostingListToDisk(holder));

  // Set prev block id only if persist to disk succeeded.
  prev_block_identifier_ = holder.id;
  return libtextclassifier3::Status::OK;
}

PostingListAccessor::FinalizeResult PostingListAccessor::Finalize() && {
  if (preexisting_posting_list_ != nullptr) {
    // Sync to disk.
    return FinalizeResult(
        storage_->WritePostingListToDisk(*preexisting_posting_list_),
        preexisting_posting_list_->id);
  }

  if (GetSerializer()->GetBytesUsed(&in_memory_posting_list_) <= 0) {
    return FinalizeResult(absl_ports::InvalidArgumentError(
                              "Can't finalize an empty PostingListAccessor. "
                              "There's nothing to Finalize!"),
                          PostingListIdentifier::kInvalid);
  }

  libtextclassifier3::StatusOr<PostingListHolder> holder_or;
  if (prev_block_identifier_.is_valid()) {
    // If prev_block_identifier_ is valid, then it means there was a max-sized
    // posting list, so we have to allocate another new max size posting list
    // and chain them together.
    holder_or = storage_->AllocateAndChainMaxSizePostingList(
        prev_block_identifier_.block_index());
  } else {
    // Otherwise, it is the first posting list, and we can use smaller size pl.
    // Note that even if it needs a max-sized posting list here, it is ok to
    // call AllocatePostingList without setting next block index since we don't
    // have any previous posting list to chain and AllocatePostingList will set
    // next block index to kInvalidBlockIndex.
    uint32_t posting_list_bytes =
        GetSerializer()->GetMinPostingListSizeToFit(&in_memory_posting_list_);
    holder_or = storage_->AllocatePostingList(posting_list_bytes);
  }

  if (!holder_or.ok()) {
    return FinalizeResult(std::move(holder_or).status(),
                          prev_block_identifier_);
  }
  PostingListHolder holder = std::move(holder_or).ValueOrDie();

  // Move to allocated area. This should never actually return an error. We know
  // that editor.posting_list() is valid because it wouldn't have successfully
  // returned by AllocatePostingList if it wasn't. We know
  // in_memory_posting_list_ is valid because we created it in-memory. And
  // finally, we know that the data from in_memory_posting_list_ will fit in
  // editor.posting_list() because we requested it be at at least
  // posting_list_bytes large.
  auto status = GetSerializer()->MoveFrom(/*dst=*/&holder.posting_list,
                                          /*src=*/&in_memory_posting_list_);
  if (!status.ok()) {
    return FinalizeResult(std::move(status), prev_block_identifier_);
  }

  status = storage_->WritePostingListToDisk(holder);
  if (!status.ok()) {
    return FinalizeResult(std::move(status), prev_block_identifier_);
  }
  return FinalizeResult(libtextclassifier3::Status::OK, holder.id);
}

}  // namespace lib
}  // namespace icing
