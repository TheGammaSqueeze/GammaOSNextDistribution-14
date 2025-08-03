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

#ifndef ICING_INDEX_TERM_INDEXING_HANDLER_H_
#define ICING_INDEX_TERM_INDEXING_HANDLER_H_

#include <memory>
#include <utility>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/index/data-indexing-handler.h"
#include "icing/index/index.h"
#include "icing/index/property-existence-indexing-handler.h"
#include "icing/index/string-section-indexing-handler.h"
#include "icing/proto/logging.pb.h"
#include "icing/store/document-id.h"
#include "icing/transform/normalizer.h"
#include "icing/util/clock.h"
#include "icing/util/tokenized-document.h"

namespace icing {
namespace lib {

class TermIndexingHandler : public DataIndexingHandler {
 public:
  // Creates a TermIndexingHandler instance which does not take
  // ownership of any input components. All pointers must refer to valid objects
  // that outlive the created TermIndexingHandler instance.
  //
  // Returns:
  //   - A TermIndexingHandler instance on success
  //   - FAILED_PRECONDITION_ERROR if any of the input pointer is null
  static libtextclassifier3::StatusOr<std::unique_ptr<TermIndexingHandler>>
  Create(const Clock* clock, const Normalizer* normalizer, Index* index,
         bool build_property_existence_metadata_hits);

  ~TermIndexingHandler() override = default;

  // Handles term indexing process:
  // - Checks if document_id > last_added_document_id.
  // - Updates last_added_document_id to document_id.
  // - Handles PropertyExistenceIndexingHandler.
  // - Handles StringSectionIndexingHandler.
  // - Sorts the lite index if necessary.
  // - Merges the lite index into the main index if necessary.
  //
  /// Returns:
  //   - OK on success
  //   - INVALID_ARGUMENT_ERROR if document_id is less than or equal to the
  //     document_id of a previously indexed document in non recovery mode.
  //   - RESOURCE_EXHAUSTED_ERROR if the index is full and can't add anymore
  //     content.
  //   - DATA_LOSS_ERROR if an attempt to merge the index fails and both indices
  //     are cleared as a result.
  //   - INTERNAL_ERROR if any other errors occur.
  //   - Any main/lite index errors.
  libtextclassifier3::Status Handle(
      const TokenizedDocument& tokenized_document, DocumentId document_id,
      bool recovery_mode, PutDocumentStatsProto* put_document_stats) override;

 private:
  explicit TermIndexingHandler(const Clock* clock, Index* index,
                               std::unique_ptr<PropertyExistenceIndexingHandler>
                                   property_existence_indexing_handler,
                               std::unique_ptr<StringSectionIndexingHandler>
                                   string_section_indexing_handler)
      : DataIndexingHandler(clock),
        index_(*index),
        property_existence_indexing_handler_(
            std::move(property_existence_indexing_handler)),
        string_section_indexing_handler_(
            std::move(string_section_indexing_handler)) {}

  Index& index_;  // Does not own.

  std::unique_ptr<PropertyExistenceIndexingHandler>
      property_existence_indexing_handler_;  // Nullable
  std::unique_ptr<StringSectionIndexingHandler>
      string_section_indexing_handler_;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_INDEX_TERM_INDEXING_HANDLER_H_
