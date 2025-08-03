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

#ifndef ICING_INDEX_NUMERIC_POSTING_LIST_INTEGER_INDEX_ACCESSOR_H_
#define ICING_INDEX_NUMERIC_POSTING_LIST_INTEGER_INDEX_ACCESSOR_H_

#include <cstdint>
#include <memory>
#include <vector>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/file/posting_list/flash-index-storage.h"
#include "icing/file/posting_list/posting-list-accessor.h"
#include "icing/file/posting_list/posting-list-identifier.h"
#include "icing/file/posting_list/posting-list-used.h"
#include "icing/index/numeric/integer-index-data.h"
#include "icing/index/numeric/posting-list-integer-index-serializer.h"

namespace icing {
namespace lib {

// TODO(b/259743562): Refactor PostingListAccessor derived classes

// This class is used to provide a simple abstraction for adding integer index
// data to posting lists. PostingListIntegerIndexAccessor handles:
// 1) selection of properly-sized posting lists for the accumulated integer
//    index data during Finalize()
// 2) chaining of max-sized posting lists.
class PostingListIntegerIndexAccessor : public PostingListAccessor {
 public:
  // Creates an empty PostingListIntegerIndexAccessor.
  //
  // RETURNS:
  //   - On success, a valid instance of PostingListIntegerIndexAccessor
  //   - INVALID_ARGUMENT error if storage has an invalid block_size.
  static libtextclassifier3::StatusOr<
      std::unique_ptr<PostingListIntegerIndexAccessor>>
  Create(FlashIndexStorage* storage,
         PostingListIntegerIndexSerializer* serializer);

  // Creates a PostingListIntegerIndexAccessor with an existing posting list
  // identified by existing_posting_list_id.
  //
  // RETURNS:
  //   - On success, a valid instance of PostingListIntegerIndexAccessor
  //   - INVALID_ARGUMENT if storage has an invalid block_size.
  static libtextclassifier3::StatusOr<
      std::unique_ptr<PostingListIntegerIndexAccessor>>
  CreateFromExisting(FlashIndexStorage* storage,
                     PostingListIntegerIndexSerializer* serializer,
                     PostingListIdentifier existing_posting_list_id);

  PostingListSerializer* GetSerializer() override { return serializer_; }

  // Retrieves the next batch of data in the posting list chain.
  //
  // RETURNS:
  //   - On success, a vector of integer index data in the posting list chain
  //   - FAILED_PRECONDITION_ERROR if called on an instance that was created via
  //     Create.
  //   - INTERNAL_ERROR if unable to read the next posting list in the chain or
  //     if the posting list has been corrupted somehow.
  libtextclassifier3::StatusOr<std::vector<IntegerIndexData>>
  GetNextDataBatch();

  // Retrieves all data from the posting list chain and frees all posting
  // list(s).
  //
  // RETURNS:
  //   - On success, a vector of integer index data in the posting list chain
  //   - FAILED_PRECONDITION_ERROR if called on an instance that was created via
  //     Create.
  //   - INTERNAL_ERROR if unable to read the next posting list in the chain or
  //     if the posting list has been corrupted somehow.
  libtextclassifier3::StatusOr<std::vector<IntegerIndexData>>
  GetAllDataAndFree();

  // Prepends one data. This may result in flushing the posting list to disk (if
  // the PostingListIntegerIndexAccessor holds a max-sized posting list that
  // is full) or freeing a pre-existing posting list if it is too small to fit
  // all data necessary.
  //
  // RETURNS:
  //   - OK, on success
  //   - INVALID_ARGUMENT if !data.is_valid() or if data is greater than the
  //     previously added data.
  //   - RESOURCE_EXHAUSTED error if unable to grow the index to allocate a new
  //     posting list.
  libtextclassifier3::Status PrependData(const IntegerIndexData& data);

 private:
  explicit PostingListIntegerIndexAccessor(
      FlashIndexStorage* storage, PostingListUsed in_memory_posting_list,
      PostingListIntegerIndexSerializer* serializer)
      : PostingListAccessor(storage, std::move(in_memory_posting_list)),
        serializer_(serializer) {}

  // Retrieves the next batch of data in the posting list chain.
  //
  // - free_posting_list: a boolean flag indicating whether freeing all posting
  //   lists after retrieving batch data.
  //
  // RETURNS:
  //   - On success, a vector of integer index data in the posting list chain
  //   - FAILED_PRECONDITION_ERROR if called on an instance that was created via
  //     Create.
  //   - INTERNAL_ERROR if unable to read the next posting list in the chain or
  //     if the posting list has been corrupted somehow.
  libtextclassifier3::StatusOr<std::vector<IntegerIndexData>>
  GetNextDataBatchImpl(bool free_posting_list);

  PostingListIntegerIndexSerializer* serializer_;  // Does not own.
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_INDEX_NUMERIC_POSTING_LIST_INTEGER_INDEX_ACCESSOR_H_
