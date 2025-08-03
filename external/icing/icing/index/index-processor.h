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

#ifndef ICING_INDEX_INDEX_PROCESSOR_H_
#define ICING_INDEX_INDEX_PROCESSOR_H_

#include <cstdint>
#include <memory>
#include <vector>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/index/data-indexing-handler.h"
#include "icing/proto/logging.pb.h"
#include "icing/store/document-id.h"
#include "icing/util/tokenized-document.h"

namespace icing {
namespace lib {

class IndexProcessor {
 public:
  explicit IndexProcessor(std::vector<std::unique_ptr<DataIndexingHandler>>&&
                              data_indexing_handlers,
                          const Clock* clock, bool recovery_mode = false)
      : data_indexing_handlers_(std::move(data_indexing_handlers)),
        clock_(*clock),
        recovery_mode_(recovery_mode) {}

  // Add tokenized document to the index, associated with document_id. If the
  // number of tokens in the document exceeds max_tokens_per_document, then only
  // the first max_tokens_per_document will be added to the index. All tokens of
  // length exceeding max_token_length will be shortened to max_token_length.
  //
  // Indexing a document *may* trigger an index merge. If a merge fails, then
  // all content in the index will be lost.
  //
  // If put_document_stats is present, the fields related to indexing will be
  // populated.
  //
  // Returns:
  //   - OK on success.
  //   - Any DataIndexingHandler errors.
  libtextclassifier3::Status IndexDocument(
      const TokenizedDocument& tokenized_document, DocumentId document_id,
      PutDocumentStatsProto* put_document_stats = nullptr);

 private:
  std::vector<std::unique_ptr<DataIndexingHandler>> data_indexing_handlers_;
  const Clock& clock_;  // Does not own.
  bool recovery_mode_;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_INDEX_INDEX_PROCESSOR_H_
