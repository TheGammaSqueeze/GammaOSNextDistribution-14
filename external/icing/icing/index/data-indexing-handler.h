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

#ifndef ICING_INDEX_DATA_INDEXING_HANDLER_H_
#define ICING_INDEX_DATA_INDEXING_HANDLER_H_

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/proto/logging.pb.h"
#include "icing/store/document-id.h"
#include "icing/util/clock.h"
#include "icing/util/tokenized-document.h"

namespace icing {
namespace lib {

// Parent class for indexing different types of data in TokenizedDocument.
class DataIndexingHandler {
 public:
  explicit DataIndexingHandler(const Clock* clock) : clock_(*clock) {}

  virtual ~DataIndexingHandler() = default;

  // Handles the indexing process: add data into the specific type index (e.g.
  // term index, integer index, qualified id type joinable index) for all
  // contents in the corresponding type of data in tokenized_document.
  // For example, IntegerSectionIndexingHandler::Handle should add data into
  // integer index for all contents in tokenized_document.integer_sections.
  //
  // Also it should handle last added DocumentId properly (based on
  // recovery_mode_) to avoid adding previously indexed documents.
  //
  // tokenized_document: document object with different types of tokenized data.
  // document_id:        id of the document.
  // recovery_mode:      decides how to handle document_id <=
  //                     last_added_document_id. If in recovery_mode, then
  //                     Handle() will simply return OK immediately. Otherwise,
  //                     returns INVALID_ARGUMENT_ERROR.
  // put_document_stats: object for collecting stats during indexing. It can be
  //                     nullptr.
  //
  /// Returns:
  //   - OK on success.
  //   - INVALID_ARGUMENT_ERROR if document_id is invalid OR document_id is less
  //     than or equal to the document_id of a previously indexed document in
  //     non recovery mode.
  //   - Any other errors. It depends on each implementation.
  virtual libtextclassifier3::Status Handle(
      const TokenizedDocument& tokenized_document, DocumentId document_id,
      bool recovery_mode, PutDocumentStatsProto* put_document_stats) = 0;

 protected:
  const Clock& clock_;  // Does not own.
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_INDEX_DATA_INDEXING_HANDLER_H_
