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

#include "icing/join/qualified-id-join-indexing-handler.h"

#include <cstdint>
#include <limits>
#include <memory>
#include <optional>
#include <string_view>
#include <utility>
#include <vector>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/absl_ports/canonical_errors.h"
#include "icing/join/doc-join-info.h"
#include "icing/join/qualified-id-join-index.h"
#include "icing/join/qualified-id.h"
#include "icing/legacy/core/icing-string-util.h"
#include "icing/proto/logging.pb.h"
#include "icing/schema/joinable-property.h"
#include "icing/store/document-filter-data.h"
#include "icing/store/document-id.h"
#include "icing/store/document-store.h"
#include "icing/store/namespace-fingerprint-identifier.h"
#include "icing/store/namespace-id.h"
#include "icing/util/clock.h"
#include "icing/util/logging.h"
#include "icing/util/status-macros.h"
#include "icing/util/tokenized-document.h"

namespace icing {
namespace lib {

/* static */ libtextclassifier3::StatusOr<
    std::unique_ptr<QualifiedIdJoinIndexingHandler>>
QualifiedIdJoinIndexingHandler::Create(
    const Clock* clock, const DocumentStore* doc_store,
    QualifiedIdJoinIndex* qualified_id_join_index) {
  ICING_RETURN_ERROR_IF_NULL(clock);
  ICING_RETURN_ERROR_IF_NULL(doc_store);
  ICING_RETURN_ERROR_IF_NULL(qualified_id_join_index);

  return std::unique_ptr<QualifiedIdJoinIndexingHandler>(
      new QualifiedIdJoinIndexingHandler(clock, doc_store,
                                         qualified_id_join_index));
}

libtextclassifier3::Status QualifiedIdJoinIndexingHandler::Handle(
    const TokenizedDocument& tokenized_document, DocumentId document_id,
    bool recovery_mode, PutDocumentStatsProto* put_document_stats) {
  std::unique_ptr<Timer> index_timer = clock_.GetNewTimer();

  if (!IsDocumentIdValid(document_id)) {
    return absl_ports::InvalidArgumentError(
        IcingStringUtil::StringPrintf("Invalid DocumentId %d", document_id));
  }

  if (qualified_id_join_index_.last_added_document_id() != kInvalidDocumentId &&
      document_id <= qualified_id_join_index_.last_added_document_id()) {
    if (recovery_mode) {
      // Skip the document if document_id <= last_added_document_id in recovery
      // mode without returning an error.
      return libtextclassifier3::Status::OK;
    }
    return absl_ports::InvalidArgumentError(IcingStringUtil::StringPrintf(
        "DocumentId %d must be greater than last added document_id %d",
        document_id, qualified_id_join_index_.last_added_document_id()));
  }
  qualified_id_join_index_.set_last_added_document_id(document_id);

  if (qualified_id_join_index_.is_v2()) {
    // v2
    std::optional<DocumentFilterData> filter_data =
        doc_store_.GetAliveDocumentFilterData(
            document_id,
            /*current_time_ms=*/std::numeric_limits<int64_t>::min());
    if (!filter_data) {
      // This should not happen.
      return absl_ports::InternalError(
          "Failed to get alive document filter data when indexing");
    }

    for (const JoinableProperty<std::string_view>& qualified_id_property :
         tokenized_document.qualified_id_join_properties()) {
      // Parse all qualified id strings and convert them to
      // NamespaceFingerprintIdentifier.
      std::vector<NamespaceFingerprintIdentifier> ref_doc_ns_fingerprint_ids;
      for (std::string_view ref_qualified_id_str :
           qualified_id_property.values) {
        // Attempt to parse qualified id string to make sure the format is
        // correct.
        auto ref_qualified_id_or = QualifiedId::Parse(ref_qualified_id_str);
        if (!ref_qualified_id_or.ok()) {
          // Skip incorrect format of qualified id string.
          continue;
        }

        QualifiedId ref_qualified_id =
            std::move(ref_qualified_id_or).ValueOrDie();
        auto ref_namespace_id_or =
            doc_store_.GetNamespaceId(ref_qualified_id.name_space());
        if (!ref_namespace_id_or.ok()) {
          // Skip invalid namespace id.
          continue;
        }
        NamespaceId ref_namespace_id =
            std::move(ref_namespace_id_or).ValueOrDie();

        ref_doc_ns_fingerprint_ids.push_back(NamespaceFingerprintIdentifier(
            ref_namespace_id, ref_qualified_id.uri()));
      }

      // Batch add all join data of this (schema_type_id, joinable_property_id)
      // into to the index.
      libtextclassifier3::Status status = qualified_id_join_index_.Put(
          filter_data->schema_type_id(), qualified_id_property.metadata.id,
          document_id, std::move(ref_doc_ns_fingerprint_ids));
      if (!status.ok()) {
        ICING_LOG(WARNING)
            << "Failed to add data into qualified id join index v2 due to: "
            << status.error_message();
        return status;
      }
    }
  } else {
    // v1
    // TODO(b/275121148): deprecate this part after rollout v2.
    for (const JoinableProperty<std::string_view>& qualified_id_property :
         tokenized_document.qualified_id_join_properties()) {
      if (qualified_id_property.values.empty()) {
        continue;
      }

      DocJoinInfo info(document_id, qualified_id_property.metadata.id);
      // Currently we only support single (non-repeated) joinable value under a
      // property.
      std::string_view ref_qualified_id_str = qualified_id_property.values[0];

      // Attempt to parse qualified id string to make sure the format is
      // correct.
      if (!QualifiedId::Parse(ref_qualified_id_str).ok()) {
        // Skip incorrect format of qualified id string to save disk space.
        continue;
      }

      libtextclassifier3::Status status =
          qualified_id_join_index_.Put(info, ref_qualified_id_str);
      if (!status.ok()) {
        ICING_LOG(WARNING)
            << "Failed to add data into qualified id join index due to: "
            << status.error_message();
        return status;
      }
    }
  }

  if (put_document_stats != nullptr) {
    put_document_stats->set_qualified_id_join_index_latency_ms(
        index_timer->GetElapsedMilliseconds());
  }

  return libtextclassifier3::Status::OK;
}

}  // namespace lib
}  // namespace icing
