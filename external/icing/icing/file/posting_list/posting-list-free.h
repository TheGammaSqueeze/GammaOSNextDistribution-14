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

#ifndef ICING_FILE_POSTING_LIST_POSTING_LIST_FREE_H_
#define ICING_FILE_POSTING_LIST_POSTING_LIST_FREE_H_

#include <cstdint>
#include <cstring>

#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/absl_ports/canonical_errors.h"
#include "icing/file/posting_list/posting-list-common.h"
#include "icing/file/posting_list/posting-list-utils.h"
#include "icing/legacy/core/icing-string-util.h"
#include "icing/util/status-macros.h"

namespace icing {
namespace lib {

// A posting list in the index block's free list.
//
// We re-use the first sizeof(PostingListIndex) bytes of the posting list
// buffer to store a next index for chaining.
class PostingListFree {
 public:
  // Creates a PostingListFree that points to a buffer of size_in_bytes bytes.
  // 'Preexisting' means that posting_list_buffer was previously modified by
  // another instance of PostingListFree.
  //
  // Caller owns the posting_list_buffer and must not free it while using
  // a PostingListFree.
  //
  // RETURNS:
  //   - A valid PostingListFree on success
  //   - INVALID_ARGUMENT if posting_list_utils::IsValidPostingListSize check
  //       fails
  //   - FAILED_PRECONDITION if posting_list_buffer is null
  static libtextclassifier3::StatusOr<PostingListFree>
  CreateFromPreexistingPostingListFreeRegion(void* posting_list_buffer,
                                             uint32_t size_in_bytes,
                                             uint32_t data_type_bytes,
                                             uint32_t min_posting_list_size) {
    ICING_RETURN_ERROR_IF_NULL(posting_list_buffer);
    if (!posting_list_utils::IsValidPostingListSize(
            size_in_bytes, data_type_bytes, min_posting_list_size)) {
      return absl_ports::InvalidArgumentError(IcingStringUtil::StringPrintf(
          "Requested posting list size %d is invalid!", size_in_bytes));
    }
    return PostingListFree(posting_list_buffer, size_in_bytes);
  }

  // Creates a PostingListFree that points to a buffer of size_in_bytes bytes
  // and initializes the content of the buffer so that the returned
  // PostingListFree is empty.
  //
  // Caller owns the posting_list_buffer buffer and must not free it while using
  // a PostingListFree.
  //
  // RETURNS:
  //   - A valid PostingListFree on success
  //   - INVALID_ARGUMENT if posting_list_utils::IsValidPostingListSize check
  //       fails
  //   - FAILED_PRECONDITION if posting_list_buffer is null
  static libtextclassifier3::StatusOr<PostingListFree>
  CreateFromUnitializedRegion(void* posting_list_buffer, uint32_t size_in_bytes,
                              uint32_t data_type_bytes,
                              uint32_t min_posting_list_size) {
    ICING_ASSIGN_OR_RETURN(PostingListFree posting_list_free,
                           CreateFromPreexistingPostingListFreeRegion(
                               posting_list_buffer, size_in_bytes,
                               data_type_bytes, min_posting_list_size));
    posting_list_free.Clear();
    return posting_list_free;
  }

  // Used to store/access the index of the next free posting list in this
  // index block.
  PostingListIndex get_next_posting_list_index() const {
    PostingListIndex posting_list_index;
    memcpy(&posting_list_index, posting_list_buffer_,
           sizeof(posting_list_index));
    return posting_list_index;
  }
  void set_next_posting_list_index(PostingListIndex posting_list_index) {
    memcpy(posting_list_buffer_, &posting_list_index,
           sizeof(posting_list_index));
  }

 private:
  explicit PostingListFree(void* posting_list_buffer, uint32_t size_in_bytes)
      : posting_list_buffer_(static_cast<uint8_t*>(posting_list_buffer)),
        size_in_bytes_(size_in_bytes) {}

  // Reset the current free posting list as unchained free posting list so that
  // there's no next posting list index. This *must* be called if the
  // posting_list_buffer_ region was never used for a previous instance of
  // PostingListFree.
  void Clear() { set_next_posting_list_index(kInvalidPostingListIndex); }

  // A byte array of size size_in_bytes_. The first sizeof(PostingListIndex)
  // bytes which will store the next posting list index, the rest are unused and
  // can be anything.
  uint8_t* posting_list_buffer_;
  [[maybe_unused]] uint32_t size_in_bytes_;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_FILE_POSTING_LIST_POSTING_LIST_FREE_H_
