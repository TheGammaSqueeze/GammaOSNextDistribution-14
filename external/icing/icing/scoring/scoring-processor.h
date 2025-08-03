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

#ifndef ICING_SCORING_SCORING_PROCESSOR_H_
#define ICING_SCORING_SCORING_PROCESSOR_H_

#include <cstdint>
#include <memory>
#include <string>
#include <unordered_map>
#include <utility>
#include <vector>

#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/index/iterator/doc-hit-info-iterator.h"
#include "icing/join/join-children-fetcher.h"
#include "icing/proto/logging.pb.h"
#include "icing/proto/scoring.pb.h"
#include "icing/schema/schema-store.h"
#include "icing/scoring/scored-document-hit.h"
#include "icing/scoring/scorer.h"
#include "icing/store/document-store.h"

namespace icing {
namespace lib {

// ScoringProcessor is the top-level class that handles scoring.
class ScoringProcessor {
 public:
  // Factory function to create a ScoringProcessor which does not take ownership
  // of any input components, and all pointers must refer to valid objects that
  // outlive the created ScoringProcessor instance.
  //
  // Returns:
  //   A ScoringProcessor on success
  //   FAILED_PRECONDITION on any null pointer input
  static libtextclassifier3::StatusOr<std::unique_ptr<ScoringProcessor>> Create(
      const ScoringSpecProto& scoring_spec, const DocumentStore* document_store,
      const SchemaStore* schema_store, int64_t current_time_ms,
      const JoinChildrenFetcher* join_children_fetcher = nullptr);

  // Assigns scores to DocHitInfos from the given DocHitInfoIterator and returns
  // a vector of ScoredDocumentHits. The size of results is no more than
  // num_to_score. The order of results is the same as DocHitInfos from
  // DocHitInfoIterator.
  //
  // If necessary, query_term_iterators is used to compute the BM25F relevance
  // score. NOTE: if the scoring spec doesn't require a scoring strategy, all
  // ScoredDocumentHits will be assigned a default score 0.
  std::vector<ScoredDocumentHit> Score(
      std::unique_ptr<DocHitInfoIterator> doc_hit_info_iterator,
      int num_to_score,
      std::unordered_map<std::string, std::unique_ptr<DocHitInfoIterator>>*
          query_term_iterators = nullptr,
      QueryStatsProto::SearchStats* search_stats = nullptr);

 private:
  explicit ScoringProcessor(std::unique_ptr<Scorer> scorer)
      : scorer_(std::move(scorer)) {}

  // The component that assigns scores to documents.
  std::unique_ptr<Scorer> scorer_;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_SCORING_SCORING_PROCESSOR_H_
