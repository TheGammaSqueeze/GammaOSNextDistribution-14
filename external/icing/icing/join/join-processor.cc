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

#include "icing/join/join-processor.h"

#include <algorithm>
#include <memory>
#include <optional>
#include <string>
#include <string_view>
#include <unordered_map>
#include <utility>
#include <vector>

#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/absl_ports/canonical_errors.h"
#include "icing/absl_ports/str_cat.h"
#include "icing/join/aggregation-scorer.h"
#include "icing/join/doc-join-info.h"
#include "icing/join/join-children-fetcher.h"
#include "icing/join/qualified-id-join-index.h"
#include "icing/join/qualified-id.h"
#include "icing/proto/schema.pb.h"
#include "icing/proto/scoring.pb.h"
#include "icing/proto/search.pb.h"
#include "icing/schema/joinable-property.h"
#include "icing/scoring/scored-document-hit.h"
#include "icing/store/document-filter-data.h"
#include "icing/store/document-id.h"
#include "icing/store/namespace-fingerprint-identifier.h"
#include "icing/util/status-macros.h"

namespace icing {
namespace lib {

libtextclassifier3::StatusOr<JoinChildrenFetcher>
JoinProcessor::GetChildrenFetcher(
    const JoinSpecProto& join_spec,
    std::vector<ScoredDocumentHit>&& child_scored_document_hits) {
  if (join_spec.parent_property_expression() != kQualifiedIdExpr) {
    // TODO(b/256022027): So far we only support kQualifiedIdExpr for
    // parent_property_expression, we could support more.
    return absl_ports::UnimplementedError(absl_ports::StrCat(
        "Parent property expression must be ", kQualifiedIdExpr));
  }

  ScoredDocumentHitComparator score_comparator(
      /*is_descending=*/join_spec.nested_spec().scoring_spec().order_by() ==
      ScoringSpecProto::Order::DESC);

  if (qualified_id_join_index_->is_v2()) {
    // v2
    // Step 1a: sort child ScoredDocumentHits in document id descending order.
    std::sort(child_scored_document_hits.begin(),
              child_scored_document_hits.end(),
              [](const ScoredDocumentHit& lhs, const ScoredDocumentHit& rhs) {
                return lhs.document_id() > rhs.document_id();
              });

    // Step 1b: group all child ScoredDocumentHits by the document's
    //          schema_type_id.
    std::unordered_map<SchemaTypeId, std::vector<ScoredDocumentHit>>
        schema_to_child_scored_doc_hits_map;
    for (const ScoredDocumentHit& child_scored_document_hit :
         child_scored_document_hits) {
      std::optional<DocumentFilterData> child_doc_filter_data =
          doc_store_->GetAliveDocumentFilterData(
              child_scored_document_hit.document_id(), current_time_ms_);
      if (!child_doc_filter_data) {
        continue;
      }

      schema_to_child_scored_doc_hits_map[child_doc_filter_data
                                              ->schema_type_id()]
          .push_back(child_scored_document_hit);
    }

    // Step 1c: for each schema_type_id, lookup QualifiedIdJoinIndexImplV2 to
    //          fetch all child join data from posting list(s). Convert all
    //          child join data to referenced parent document ids and bucketize
    //          child ScoredDocumentHits by it.
    std::unordered_map<DocumentId, std::vector<ScoredDocumentHit>>
        parent_to_child_docs_map;
    for (auto& [schema_type_id, grouped_child_scored_doc_hits] :
         schema_to_child_scored_doc_hits_map) {
      // Get joinable_property_id of this schema.
      ICING_ASSIGN_OR_RETURN(
          const JoinablePropertyMetadata* metadata,
          schema_store_->GetJoinablePropertyMetadata(
              schema_type_id, join_spec.child_property_expression()));
      if (metadata == nullptr ||
          metadata->value_type != JoinableConfig::ValueType::QUALIFIED_ID) {
        // Currently we only support qualified id, so skip other types.
        continue;
      }

      // Lookup QualifiedIdJoinIndexImplV2.
      ICING_ASSIGN_OR_RETURN(
          std::unique_ptr<QualifiedIdJoinIndex::JoinDataIteratorBase>
              join_index_iter,
          qualified_id_join_index_->GetIterator(
              schema_type_id, /*joinable_property_id=*/metadata->id));

      // - Join index contains all join data of schema_type_id and
      //   join_index_iter will return all of them in (child) document id
      //   descending order.
      // - But we only need join data of child document ids which appear in
      //   grouped_child_scored_doc_hits. Also grouped_child_scored_doc_hits
      //   contain ScoredDocumentHits in (child) document id descending order.
      // - Therefore, we advance 2 iterators to intersect them and get desired
      //   join data.
      auto child_scored_doc_hits_iter = grouped_child_scored_doc_hits.cbegin();
      while (join_index_iter->Advance().ok() &&
             child_scored_doc_hits_iter !=
                 grouped_child_scored_doc_hits.cend()) {
        // Advance child_scored_doc_hits_iter until it points to a
        // ScoredDocumentHit with document id <= the one pointed by
        // join_index_iter.
        while (child_scored_doc_hits_iter !=
                   grouped_child_scored_doc_hits.cend() &&
               child_scored_doc_hits_iter->document_id() >
                   join_index_iter->GetCurrent().document_id()) {
          ++child_scored_doc_hits_iter;
        }

        if (child_scored_doc_hits_iter !=
                grouped_child_scored_doc_hits.cend() &&
            child_scored_doc_hits_iter->document_id() ==
                join_index_iter->GetCurrent().document_id()) {
          // We get a join data whose child document id exists in both join
          // index and grouped_child_scored_doc_hits. Convert its join info to
          // referenced parent document ids and bucketize ScoredDocumentHits by
          // it (putting into parent_to_child_docs_map).
          const NamespaceFingerprintIdentifier& ref_ns_id =
              join_index_iter->GetCurrent().join_info();
          libtextclassifier3::StatusOr<DocumentId> ref_parent_doc_id_or =
              doc_store_->GetDocumentId(ref_ns_id);
          if (ref_parent_doc_id_or.ok()) {
            parent_to_child_docs_map[std::move(ref_parent_doc_id_or)
                                         .ValueOrDie()]
                .push_back(*child_scored_doc_hits_iter);
          }
        }
      }
    }

    // Step 1d: finally, sort each parent's joined child ScoredDocumentHits by
    //          score.
    for (auto& [parent_doc_id, bucketized_child_scored_hits] :
         parent_to_child_docs_map) {
      std::sort(bucketized_child_scored_hits.begin(),
                bucketized_child_scored_hits.end(), score_comparator);
    }

    return JoinChildrenFetcher(join_spec, std::move(parent_to_child_docs_map));
  }

  // v1
  // TODO(b/275121148): deprecate this part after rollout v2.
  std::sort(child_scored_document_hits.begin(),
            child_scored_document_hits.end(), score_comparator);

  // Step 1: group child documents by parent documentId. Currently we only
  //         support QualifiedId joining, so fetch the qualified id content of
  //         child_property_expression, break it down into namespace + uri, and
  //         lookup the DocumentId.
  // The keys of this map are the DocumentIds of the parent docs the child
  // ScoredDocumentHits refer to. The values in this map are vectors of child
  // ScoredDocumentHits that refer to a parent DocumentId.
  std::unordered_map<DocumentId, std::vector<ScoredDocumentHit>>
      map_joinable_qualified_id;
  for (const ScoredDocumentHit& child : child_scored_document_hits) {
    ICING_ASSIGN_OR_RETURN(
        DocumentId ref_doc_id,
        FetchReferencedQualifiedId(child.document_id(),
                                   join_spec.child_property_expression()));
    if (ref_doc_id == kInvalidDocumentId) {
      continue;
    }

    map_joinable_qualified_id[ref_doc_id].push_back(child);
  }
  return JoinChildrenFetcher(join_spec, std::move(map_joinable_qualified_id));
}

libtextclassifier3::StatusOr<std::vector<JoinedScoredDocumentHit>>
JoinProcessor::Join(
    const JoinSpecProto& join_spec,
    std::vector<ScoredDocumentHit>&& parent_scored_document_hits,
    const JoinChildrenFetcher& join_children_fetcher) {
  std::unique_ptr<AggregationScorer> aggregation_scorer =
      AggregationScorer::Create(join_spec);

  std::vector<JoinedScoredDocumentHit> joined_scored_document_hits;
  joined_scored_document_hits.reserve(parent_scored_document_hits.size());

  // Step 2: iterate through all parent documentIds and construct
  //         JoinedScoredDocumentHit for each by looking up
  //         join_children_fetcher.
  for (ScoredDocumentHit& parent : parent_scored_document_hits) {
    ICING_ASSIGN_OR_RETURN(
        std::vector<ScoredDocumentHit> children,
        join_children_fetcher.GetChildren(parent.document_id()));

    double final_score = aggregation_scorer->GetScore(parent, children);
    joined_scored_document_hits.emplace_back(final_score, std::move(parent),
                                             std::move(children));
  }

  return joined_scored_document_hits;
}

libtextclassifier3::StatusOr<DocumentId>
JoinProcessor::FetchReferencedQualifiedId(
    const DocumentId& document_id, const std::string& property_path) const {
  std::optional<DocumentFilterData> filter_data =
      doc_store_->GetAliveDocumentFilterData(document_id, current_time_ms_);
  if (!filter_data) {
    return kInvalidDocumentId;
  }

  ICING_ASSIGN_OR_RETURN(const JoinablePropertyMetadata* metadata,
                         schema_store_->GetJoinablePropertyMetadata(
                             filter_data->schema_type_id(), property_path));
  if (metadata == nullptr ||
      metadata->value_type != JoinableConfig::ValueType::QUALIFIED_ID) {
    // Currently we only support qualified id.
    return kInvalidDocumentId;
  }

  DocJoinInfo info(document_id, metadata->id);
  libtextclassifier3::StatusOr<std::string_view> ref_qualified_id_str_or =
      qualified_id_join_index_->Get(info);
  if (!ref_qualified_id_str_or.ok()) {
    if (absl_ports::IsNotFound(ref_qualified_id_str_or.status())) {
      return kInvalidDocumentId;
    }
    return std::move(ref_qualified_id_str_or).status();
  }

  libtextclassifier3::StatusOr<QualifiedId> ref_qualified_id_or =
      QualifiedId::Parse(std::move(ref_qualified_id_str_or).ValueOrDie());
  if (!ref_qualified_id_or.ok()) {
    // This shouldn't happen because we've validated it during indexing and only
    // put valid qualified id strings into qualified id join index.
    return kInvalidDocumentId;
  }
  QualifiedId qualified_id = std::move(ref_qualified_id_or).ValueOrDie();

  libtextclassifier3::StatusOr<DocumentId> ref_document_id_or =
      doc_store_->GetDocumentId(qualified_id.name_space(), qualified_id.uri());
  if (!ref_document_id_or.ok()) {
    return kInvalidDocumentId;
  }
  return std::move(ref_document_id_or).ValueOrDie();
}

}  // namespace lib
}  // namespace icing
