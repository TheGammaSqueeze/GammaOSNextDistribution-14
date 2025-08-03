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

#ifndef ICING_INDEX_PROPERTY_EXISTENCE_INDEXING_HANDLER_H_
#define ICING_INDEX_PROPERTY_EXISTENCE_INDEXING_HANDLER_H_

#include <memory>
#include <string_view>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/index/index.h"
#include "icing/proto/logging.pb.h"
#include "icing/store/document-id.h"
#include "icing/util/clock.h"
#include "icing/util/tokenized-document.h"

namespace icing {
namespace lib {

inline constexpr std::string_view kPropertyExistenceTokenPrefix =
    "\xFF_HAS_\xFF";

// This class is meant to be owned by TermIndexingHandler. Instead of using this
// handler directly, callers should use TermIndexingHandler to index documents.
//
// This handler will not check or set last_added_document_id of the index, and
// it will not merge or sort the lite index either.
class PropertyExistenceIndexingHandler {
 public:
  // Creates a PropertyExistenceIndexingHandler instance which does not take
  // ownership of any input components. All pointers must refer to valid objects
  // that outlive the created PropertyExistenceIndexingHandler instance.
  //
  // Returns:
  //   - A PropertyExistenceIndexingHandler instance on success
  //   - FAILED_PRECONDITION_ERROR if any of the input pointer is null
  static libtextclassifier3::StatusOr<
      std::unique_ptr<PropertyExistenceIndexingHandler>>
  Create(const Clock* clock, Index* index);

  ~PropertyExistenceIndexingHandler() = default;

  // Handles the property existence indexing process: add hits for metadata
  // tokens used to index property existence.
  //
  // For example, if the passed in document has string properties "propA",
  // "propB" and "propC.propD", and document property "propC", this handler will
  // add the following metadata token to the index.
  // - kPropertyExistenceTokenPrefix + "propA"
  // - kPropertyExistenceTokenPrefix + "propB"
  // - kPropertyExistenceTokenPrefix + "propC"
  // - kPropertyExistenceTokenPrefix + "propC.propD"
  //
  /// Returns:
  //   - OK on success
  //   - RESOURCE_EXHAUSTED_ERROR if the index is full and can't add anymore
  //     content.
  //   - INTERNAL_ERROR if any other errors occur.
  libtextclassifier3::Status Handle(const TokenizedDocument& tokenized_document,
                                    DocumentId document_id,
                                    PutDocumentStatsProto* put_document_stats);

 private:
  explicit PropertyExistenceIndexingHandler(const Clock& clock, Index* index)
      : clock_(clock), index_(*index) {}

  const Clock& clock_;  // Does not own.
  Index& index_;        // Does not own.
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_INDEX_PROPERTY_EXISTENCE_INDEXING_HANDLER_H_
