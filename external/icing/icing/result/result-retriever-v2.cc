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

#include "icing/result/result-retriever-v2.h"

#include <cstddef>
#include <cstdint>
#include <memory>
#include <string>
#include <unordered_map>
#include <utility>
#include <vector>

#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/absl_ports/mutex.h"
#include "icing/proto/document.pb.h"
#include "icing/proto/search.pb.h"
#include "icing/result/page-result.h"
#include "icing/result/projection-tree.h"
#include "icing/result/projector.h"
#include "icing/result/result-adjustment-info.h"
#include "icing/result/result-state-v2.h"
#include "icing/result/snippet-context.h"
#include "icing/result/snippet-retriever.h"
#include "icing/schema/schema-store.h"
#include "icing/schema/section.h"
#include "icing/scoring/scored-document-hit.h"
#include "icing/store/document-filter-data.h"
#include "icing/store/document-store.h"
#include "icing/store/namespace-id.h"
#include "icing/tokenization/language-segmenter.h"
#include "icing/transform/normalizer.h"
#include "icing/util/logging.h"
#include "icing/util/status-macros.h"

namespace icing {
namespace lib {

namespace {

void ApplyProjection(const ResultAdjustmentInfo* adjustment_info,
                     DocumentProto* document) {
  if (adjustment_info == nullptr) {
    return;
  }

  auto itr = adjustment_info->projection_tree_map.find(document->schema());
  if (itr != adjustment_info->projection_tree_map.end()) {
    projector::Project(itr->second.root().children, document);
  } else {
    auto wildcard_projection_tree_itr =
        adjustment_info->projection_tree_map.find(
            std::string(SchemaStore::kSchemaTypeWildcard));
    if (wildcard_projection_tree_itr !=
        adjustment_info->projection_tree_map.end()) {
      projector::Project(wildcard_projection_tree_itr->second.root().children,
                         document);
    }
  }
}

bool ApplySnippet(ResultAdjustmentInfo* adjustment_info,
                  const SnippetRetriever& snippet_retriever,
                  const DocumentProto& document, SectionIdMask section_id_mask,
                  SearchResultProto::ResultProto* result) {
  if (adjustment_info == nullptr) {
    return false;
  }

  const SnippetContext& snippet_context = adjustment_info->snippet_context;
  int& remaining_num_to_snippet = adjustment_info->remaining_num_to_snippet;

  if (snippet_context.snippet_spec.num_matches_per_property() > 0 &&
      remaining_num_to_snippet > 0) {
    SnippetProto snippet_proto = snippet_retriever.RetrieveSnippet(
        snippet_context.query_terms, snippet_context.match_type,
        snippet_context.snippet_spec, document, section_id_mask);
    *result->mutable_snippet() = std::move(snippet_proto);
    --remaining_num_to_snippet;
    return true;
  }

  return false;
}

}  // namespace

bool GroupResultLimiterV2::ShouldBeRemoved(
    const ScoredDocumentHit& scored_document_hit,
    const std::unordered_map<int32_t, int>& entry_id_group_id_map,
    const DocumentStore& document_store, std::vector<int>& group_result_limits,
    ResultSpecProto::ResultGroupingType result_group_type,
    int64_t current_time_ms) const {
  auto document_filter_data_optional =
      document_store.GetAliveDocumentFilterData(
          scored_document_hit.document_id(), current_time_ms);
  if (!document_filter_data_optional) {
    // The document doesn't exist.
    return true;
  }
  NamespaceId namespace_id =
      document_filter_data_optional.value().namespace_id();
  SchemaTypeId schema_type_id =
      document_filter_data_optional.value().schema_type_id();
  auto entry_id_or = document_store.GetResultGroupingEntryId(
      result_group_type, namespace_id, schema_type_id);
  if (!entry_id_or.ok()) {
    return false;
  }
  int32_t entry_id = entry_id_or.ValueOrDie();
  auto iter = entry_id_group_id_map.find(entry_id);
  if (iter == entry_id_group_id_map.end()) {
    // If a ResultGrouping Entry Id isn't found in entry_id_group_id_map, then
    // there are no limits placed on results from this entry id.
    return false;
  }
  int& count = group_result_limits.at(iter->second);
  if (count <= 0) {
    return true;
  }
  --count;
  return false;
}

libtextclassifier3::StatusOr<std::unique_ptr<ResultRetrieverV2>>
ResultRetrieverV2::Create(
    const DocumentStore* doc_store, const SchemaStore* schema_store,
    const LanguageSegmenter* language_segmenter, const Normalizer* normalizer,
    std::unique_ptr<const GroupResultLimiterV2> group_result_limiter) {
  ICING_RETURN_ERROR_IF_NULL(doc_store);
  ICING_RETURN_ERROR_IF_NULL(schema_store);
  ICING_RETURN_ERROR_IF_NULL(language_segmenter);
  ICING_RETURN_ERROR_IF_NULL(normalizer);
  ICING_RETURN_ERROR_IF_NULL(group_result_limiter);

  ICING_ASSIGN_OR_RETURN(
      std::unique_ptr<SnippetRetriever> snippet_retriever,
      SnippetRetriever::Create(schema_store, language_segmenter, normalizer));

  return std::unique_ptr<ResultRetrieverV2>(
      new ResultRetrieverV2(doc_store, std::move(snippet_retriever),
                            std::move(group_result_limiter)));
}

std::pair<PageResult, bool> ResultRetrieverV2::RetrieveNextPage(
    ResultStateV2& result_state, int64_t current_time_ms) const {
  absl_ports::unique_lock l(&result_state.mutex);

  // For calculating page
  int original_scored_document_hits_ranker_size =
      result_state.scored_document_hits_ranker->size();
  int num_results_with_snippets = 0;

  // Retrieve info
  std::vector<SearchResultProto::ResultProto> results;
  int32_t num_total_bytes = 0;
  while (results.size() < result_state.num_per_page() &&
         !result_state.scored_document_hits_ranker->empty()) {
    JoinedScoredDocumentHit next_best_document_hit =
        result_state.scored_document_hits_ranker->PopNext();
    if (group_result_limiter_->ShouldBeRemoved(
            next_best_document_hit.parent_scored_document_hit(),
            result_state.entry_id_group_id_map(), doc_store_,
            result_state.group_result_limits, result_state.result_group_type(),
            current_time_ms)) {
      continue;
    }

    libtextclassifier3::StatusOr<DocumentProto> document_or = doc_store_.Get(
        next_best_document_hit.parent_scored_document_hit().document_id());
    if (!document_or.ok()) {
      // Skip the document if getting errors.
      ICING_LOG(WARNING) << "Fail to fetch document from document store: "
                         << document_or.status().error_message();
      continue;
    }

    DocumentProto document = std::move(document_or).ValueOrDie();
    // Apply parent projection
    ApplyProjection(result_state.parent_adjustment_info(), &document);

    SearchResultProto::ResultProto result;
    // Add parent snippet if requested.
    if (ApplySnippet(result_state.parent_adjustment_info(), *snippet_retriever_,
                     document,
                     next_best_document_hit.parent_scored_document_hit()
                         .hit_section_id_mask(),
                     &result)) {
      ++num_results_with_snippets;
    }

    // Add the document, itself.
    *result.mutable_document() = std::move(document);
    result.set_score(next_best_document_hit.final_score());

    // Retrieve child documents
    for (const ScoredDocumentHit& child_scored_document_hit :
         next_best_document_hit.child_scored_document_hits()) {
      if (result.joined_results_size() >=
          result_state.max_joined_children_per_parent_to_return()) {
        break;
      }

      libtextclassifier3::StatusOr<DocumentProto> child_document_or =
          doc_store_.Get(child_scored_document_hit.document_id());
      if (!child_document_or.ok()) {
        // Skip the document if getting errors.
        ICING_LOG(WARNING)
            << "Fail to fetch child document from document store: "
            << child_document_or.status().error_message();
        continue;
      }

      DocumentProto child_document = std::move(child_document_or).ValueOrDie();
      ApplyProjection(result_state.child_adjustment_info(), &child_document);

      SearchResultProto::ResultProto* child_result =
          result.add_joined_results();
      // Add child snippet if requested.
      ApplySnippet(result_state.child_adjustment_info(), *snippet_retriever_,
                   child_document,
                   child_scored_document_hit.hit_section_id_mask(),
                   child_result);

      *child_result->mutable_document() = std::move(child_document);
      child_result->set_score(child_scored_document_hit.score());
    }

    size_t result_bytes = result.ByteSizeLong();
    results.push_back(std::move(result));

    // Check if num_total_bytes + result_bytes reaches or exceeds
    // num_total_bytes_per_page_threshold. Use subtraction to avoid integer
    // overflow.
    if (result_bytes >=
        result_state.num_total_bytes_per_page_threshold() - num_total_bytes) {
      break;
    }
    num_total_bytes += result_bytes;
  }

  // Update numbers in ResultState
  result_state.num_returned += results.size();
  result_state.IncrementNumTotalHits(
      result_state.scored_document_hits_ranker->size() -
      original_scored_document_hits_ranker_size);

  bool has_more_results = !result_state.scored_document_hits_ranker->empty();

  return std::make_pair(
      PageResult(std::move(results), num_results_with_snippets,
                 result_state.num_per_page()),
      has_more_results);
}

}  // namespace lib
}  // namespace icing
