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

#include "icing/index/term-indexing-handler.h"

#include <memory>
#include <string>
#include <utility>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/absl_ports/canonical_errors.h"
#include "icing/index/index.h"
#include "icing/index/property-existence-indexing-handler.h"
#include "icing/index/string-section-indexing-handler.h"
#include "icing/legacy/core/icing-string-util.h"
#include "icing/proto/logging.pb.h"
#include "icing/store/document-id.h"
#include "icing/transform/normalizer.h"
#include "icing/util/clock.h"
#include "icing/util/logging.h"
#include "icing/util/status-macros.h"
#include "icing/util/tokenized-document.h"

namespace icing {
namespace lib {

/* static */ libtextclassifier3::StatusOr<std::unique_ptr<TermIndexingHandler>>
TermIndexingHandler::Create(const Clock* clock, const Normalizer* normalizer,
                            Index* index,
                            bool build_property_existence_metadata_hits) {
  ICING_RETURN_ERROR_IF_NULL(clock);
  ICING_RETURN_ERROR_IF_NULL(normalizer);
  ICING_RETURN_ERROR_IF_NULL(index);

  // Property existence index handler
  std::unique_ptr<PropertyExistenceIndexingHandler>
      property_existence_indexing_handler = nullptr;
  if (build_property_existence_metadata_hits) {
    ICING_ASSIGN_OR_RETURN(
        property_existence_indexing_handler,
        PropertyExistenceIndexingHandler::Create(clock, index));
  }
  // String section index handler
  ICING_ASSIGN_OR_RETURN(
      std::unique_ptr<StringSectionIndexingHandler>
          string_section_indexing_handler,
      StringSectionIndexingHandler::Create(normalizer, index));

  return std::unique_ptr<TermIndexingHandler>(new TermIndexingHandler(
      clock, index, std::move(property_existence_indexing_handler),
      std::move(string_section_indexing_handler)));
}

libtextclassifier3::Status TermIndexingHandler::Handle(
    const TokenizedDocument& tokenized_document, DocumentId document_id,
    bool recovery_mode, PutDocumentStatsProto* put_document_stats) {
  std::unique_ptr<Timer> index_timer = clock_.GetNewTimer();

  if (index_.last_added_document_id() != kInvalidDocumentId &&
      document_id <= index_.last_added_document_id()) {
    if (recovery_mode) {
      // Skip the document if document_id <= last_added_document_id in recovery
      // mode without returning an error.
      return libtextclassifier3::Status::OK;
    }
    return absl_ports::InvalidArgumentError(IcingStringUtil::StringPrintf(
        "DocumentId %d must be greater than last added document_id %d",
        document_id, index_.last_added_document_id()));
  }
  index_.set_last_added_document_id(document_id);

  libtextclassifier3::Status status = libtextclassifier3::Status::OK;
  if (property_existence_indexing_handler_ != nullptr) {
    status = property_existence_indexing_handler_->Handle(
        tokenized_document, document_id, put_document_stats);
  }
  if (status.ok()) {
    status = string_section_indexing_handler_->Handle(
        tokenized_document, document_id, put_document_stats);
  }

  if (put_document_stats != nullptr) {
    put_document_stats->set_term_index_latency_ms(
        index_timer->GetElapsedMilliseconds());
  }

  // Check if we should merge when we're either successful or we've hit resource
  // exhausted.
  bool should_merge =
      (status.ok() || absl_ports::IsResourceExhausted(status)) &&
      index_.WantsMerge();

  // Check and sort the LiteIndex HitBuffer if we don't need to merge.
  if (!should_merge && index_.LiteIndexNeedSort()) {
    std::unique_ptr<Timer> sort_timer = clock_.GetNewTimer();
    index_.SortLiteIndex();

    if (put_document_stats != nullptr) {
      put_document_stats->set_lite_index_sort_latency_ms(
          sort_timer->GetElapsedMilliseconds());
    }
  }

  // Attempt index merge if needed.
  if (should_merge) {
    ICING_LOG(INFO) << "Merging the index at docid " << document_id << ".";

    std::unique_ptr<Timer> merge_timer = clock_.GetNewTimer();
    libtextclassifier3::Status merge_status = index_.Merge();

    if (!merge_status.ok()) {
      ICING_LOG(ERROR) << "Index merging failed. Clearing index.";
      if (!index_.Reset().ok()) {
        return absl_ports::InternalError(IcingStringUtil::StringPrintf(
            "Unable to reset to clear index after merge failure. Merge "
            "failure=%d:%s",
            merge_status.error_code(), merge_status.error_message().c_str()));
      } else {
        return absl_ports::DataLossError(IcingStringUtil::StringPrintf(
            "Forced to reset index after merge failure. Merge failure=%d:%s",
            merge_status.error_code(), merge_status.error_message().c_str()));
      }
    }

    if (put_document_stats != nullptr) {
      put_document_stats->set_index_merge_latency_ms(
          merge_timer->GetElapsedMilliseconds());
    }
  }
  return status;
}

}  // namespace lib
}  // namespace icing
