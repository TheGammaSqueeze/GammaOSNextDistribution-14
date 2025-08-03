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

#ifndef ICING_JOIN_QUALIFIED_ID_JOIN_INDEXING_HANDLER_H_
#define ICING_JOIN_QUALIFIED_ID_JOIN_INDEXING_HANDLER_H_

#include <memory>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/index/data-indexing-handler.h"
#include "icing/join/qualified-id-join-index.h"
#include "icing/proto/logging.pb.h"
#include "icing/store/document-id.h"
#include "icing/store/document-store.h"
#include "icing/util/clock.h"
#include "icing/util/tokenized-document.h"

namespace icing {
namespace lib {

class QualifiedIdJoinIndexingHandler : public DataIndexingHandler {
 public:
  // Creates a QualifiedIdJoinIndexingHandler instance which does not take
  // ownership of any input components. All pointers must refer to valid objects
  // that outlive the created QualifiedIdJoinIndexingHandler instance.
  //
  // Returns:
  //   - A QualifiedIdJoinIndexingHandler instance on success
  //   - FAILED_PRECONDITION_ERROR if any of the input pointer is null
  static libtextclassifier3::StatusOr<
      std::unique_ptr<QualifiedIdJoinIndexingHandler>>
  Create(const Clock* clock, const DocumentStore* doc_store,
         QualifiedIdJoinIndex* qualified_id_join_index);

  ~QualifiedIdJoinIndexingHandler() override = default;

  // Handles the joinable qualified id data indexing process: add data into the
  // qualified id join index.
  //
  /// Returns:
  //   - OK on success.
  //   - INVALID_ARGUMENT_ERROR if document_id is invalid OR document_id is less
  //     than or equal to the document_id of a previously indexed document in
  //     non recovery mode.
  //   - INTERNAL_ERROR if any other errors occur.
  //   - Any QualifiedIdJoinIndex errors.
  libtextclassifier3::Status Handle(
      const TokenizedDocument& tokenized_document, DocumentId document_id,
      bool recovery_mode, PutDocumentStatsProto* put_document_stats) override;

 private:
  explicit QualifiedIdJoinIndexingHandler(
      const Clock* clock, const DocumentStore* doc_store,
      QualifiedIdJoinIndex* qualified_id_join_index)
      : DataIndexingHandler(clock),
        doc_store_(*doc_store),
        qualified_id_join_index_(*qualified_id_join_index) {}

  const DocumentStore& doc_store_;                 // Does not own.
  QualifiedIdJoinIndex& qualified_id_join_index_;  // Does not own.
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_JOIN_QUALIFIED_ID_JOIN_INDEXING_HANDLER_H_
