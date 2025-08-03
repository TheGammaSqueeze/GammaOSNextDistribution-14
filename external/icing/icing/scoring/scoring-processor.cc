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

#include "icing/scoring/scoring-processor.h"

#include <limits>
#include <memory>
#include <string>
#include <unordered_map>
#include <utility>
#include <vector>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/index/hit/doc-hit-info.h"
#include "icing/index/iterator/doc-hit-info-iterator.h"
#include "icing/proto/scoring.pb.h"
#include "icing/scoring/ranker.h"
#include "icing/scoring/scored-document-hit.h"
#include "icing/scoring/scorer-factory.h"
#include "icing/scoring/scorer.h"
#include "icing/store/document-store.h"
#include "icing/util/status-macros.h"

namespace icing {
namespace lib {

namespace {
constexpr double kDefaultScoreInDescendingOrder = 0;
constexpr double kDefaultScoreInAscendingOrder =
    std::numeric_limits<double>::max();
}  // namespace

libtextclassifier3::StatusOr<std::unique_ptr<ScoringProcessor>>
ScoringProcessor::Create(const ScoringSpecProto& scoring_spec,
                         const DocumentStore* document_store,
                         const SchemaStore* schema_store,
                         int64_t current_time_ms,
                         const JoinChildrenFetcher* join_children_fetcher) {
  ICING_RETURN_ERROR_IF_NULL(document_store);
  ICING_RETURN_ERROR_IF_NULL(schema_store);

  bool is_descending_order =
      scoring_spec.order_by() == ScoringSpecProto::Order::DESC;

  ICING_ASSIGN_OR_RETURN(
      std::unique_ptr<Scorer> scorer,
      scorer_factory::Create(scoring_spec,
                             is_descending_order
                                 ? kDefaultScoreInDescendingOrder
                                 : kDefaultScoreInAscendingOrder,
                             document_store, schema_store, current_time_ms,
                             join_children_fetcher));
  // Using `new` to access a non-public constructor.
  return std::unique_ptr<ScoringProcessor>(
      new ScoringProcessor(std::move(scorer)));
}

std::vector<ScoredDocumentHit> ScoringProcessor::Score(
    std::unique_ptr<DocHitInfoIterator> doc_hit_info_iterator, int num_to_score,
    std::unordered_map<std::string, std::unique_ptr<DocHitInfoIterator>>*
        query_term_iterators,
    QueryStatsProto::SearchStats* search_stats) {
  std::vector<ScoredDocumentHit> scored_document_hits;
  scorer_->PrepareToScore(query_term_iterators);

  while (doc_hit_info_iterator->Advance().ok() && num_to_score-- > 0) {
    const DocHitInfo& doc_hit_info = doc_hit_info_iterator->doc_hit_info();
    // TODO(b/144955274) Calculate hit demotion factor from HitScore
    double hit_demotion_factor = 1.0;
    // The final score of the doc_hit_info = score of doc * demotion factor of
    // hit.
    double score =
        scorer_->GetScore(doc_hit_info, doc_hit_info_iterator.get()) *
        hit_demotion_factor;
    scored_document_hits.emplace_back(
        doc_hit_info.document_id(), doc_hit_info.hit_section_ids_mask(), score);
  }

  if (search_stats != nullptr) {
    search_stats->set_num_documents_scored(scored_document_hits.size());
    DocHitInfoIterator::CallStats iterator_call_stats =
        doc_hit_info_iterator->GetCallStats();
    search_stats->set_num_fetched_hits_lite_index(
        iterator_call_stats.num_leaf_advance_calls_lite_index);
    search_stats->set_num_fetched_hits_main_index(
        iterator_call_stats.num_leaf_advance_calls_main_index);
    search_stats->set_num_fetched_hits_integer_index(
        iterator_call_stats.num_leaf_advance_calls_integer_index);
  }

  return scored_document_hits;
}

}  // namespace lib
}  // namespace icing
