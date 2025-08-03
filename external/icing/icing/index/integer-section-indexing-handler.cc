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

#include "icing/index/integer-section-indexing-handler.h"

#include <cstdint>
#include <memory>
#include <utility>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/absl_ports/canonical_errors.h"
#include "icing/index/numeric/numeric-index.h"
#include "icing/legacy/core/icing-string-util.h"
#include "icing/proto/logging.pb.h"
#include "icing/schema/section.h"
#include "icing/store/document-id.h"
#include "icing/util/clock.h"
#include "icing/util/logging.h"
#include "icing/util/status-macros.h"
#include "icing/util/tokenized-document.h"

namespace icing {
namespace lib {

/* static */ libtextclassifier3::StatusOr<
    std::unique_ptr<IntegerSectionIndexingHandler>>
IntegerSectionIndexingHandler::Create(const Clock* clock,
                                      NumericIndex<int64_t>* integer_index) {
  ICING_RETURN_ERROR_IF_NULL(clock);
  ICING_RETURN_ERROR_IF_NULL(integer_index);

  return std::unique_ptr<IntegerSectionIndexingHandler>(
      new IntegerSectionIndexingHandler(clock, integer_index));
}

libtextclassifier3::Status IntegerSectionIndexingHandler::Handle(
    const TokenizedDocument& tokenized_document, DocumentId document_id,
    bool recovery_mode, PutDocumentStatsProto* put_document_stats) {
  std::unique_ptr<Timer> index_timer = clock_.GetNewTimer();

  if (!IsDocumentIdValid(document_id)) {
    return absl_ports::InvalidArgumentError(
        IcingStringUtil::StringPrintf("Invalid DocumentId %d", document_id));
  }

  if (integer_index_.last_added_document_id() != kInvalidDocumentId &&
      document_id <= integer_index_.last_added_document_id()) {
    if (recovery_mode) {
      // Skip the document if document_id <= last_added_document_id in recovery
      // mode without returning an error.
      return libtextclassifier3::Status::OK;
    }
    return absl_ports::InvalidArgumentError(IcingStringUtil::StringPrintf(
        "DocumentId %d must be greater than last added document_id %d",
        document_id, integer_index_.last_added_document_id()));
  }
  integer_index_.set_last_added_document_id(document_id);

  libtextclassifier3::Status status;
  // We have to add integer sections into integer index in reverse order because
  // sections are sorted by SectionId in ascending order, but BasicHit should be
  // added in descending order of SectionId (posting list requirement).
  for (auto riter = tokenized_document.integer_sections().rbegin();
       riter != tokenized_document.integer_sections().rend(); ++riter) {
    const Section<int64_t>& section = *riter;
    std::unique_ptr<NumericIndex<int64_t>::Editor> editor = integer_index_.Edit(
        section.metadata.path, document_id, section.metadata.id);

    for (int64_t key : section.content) {
      status = editor->BufferKey(key);
      if (!status.ok()) {
        ICING_LOG(WARNING)
            << "Failed to buffer keys into integer index due to: "
            << status.error_message();
        break;
      }
    }
    if (!status.ok()) {
      break;
    }

    // Add all the seen keys to the integer index.
    status = std::move(*editor).IndexAllBufferedKeys();
    if (!status.ok()) {
      ICING_LOG(WARNING) << "Failed to add keys into integer index due to: "
                         << status.error_message();
      break;
    }
  }

  if (put_document_stats != nullptr) {
    put_document_stats->set_integer_index_latency_ms(
        index_timer->GetElapsedMilliseconds());
  }

  return status;
}

}  // namespace lib
}  // namespace icing
