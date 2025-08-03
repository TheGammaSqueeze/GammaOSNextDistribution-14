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

#ifndef ICING_SCORING_SCORER_H_
#define ICING_SCORING_SCORER_H_

#include <memory>
#include <unordered_map>

#include "icing/index/hit/doc-hit-info.h"
#include "icing/index/iterator/doc-hit-info-iterator.h"
#include "icing/proto/scoring.pb.h"

namespace icing {
namespace lib {

// Scorer calculates scores for documents.
class Scorer {
 public:
  virtual ~Scorer() = default;

  // Returns a non-negative score of a document. The score can be a
  // document-associated score which comes from the DocumentProto directly, an
  // accumulated score, a relevance score, or even an inferred score. If it
  // fails to find or calculate a score, the user-provided default score will be
  // returned.
  //
  // Some examples of possible scores:
  // 1. Document-associated scores: document score, creation timestamp score.
  // 2. Accumulated scores: usage count score.
  // 3. Inferred scores: a score calculated by a machine learning model.
  // 4. Relevance score: computed as BM25F score.
  //
  // NOTE: This method is performance-sensitive as it's called for every
  // potential result document. We're trying to avoid returning StatusOr<double>
  // to save a little more time and memory.
  virtual double GetScore(const DocHitInfo& hit_info,
                          const DocHitInfoIterator* query_it = nullptr) = 0;

  // Currently only overriden by the RelevanceScoreScorer.
  // NOTE: the query_term_iterators map must
  // outlive the scorer, see bm25f-calculator for more details.
  virtual void PrepareToScore(
      std::unordered_map<std::string, std::unique_ptr<DocHitInfoIterator>>*
          query_term_iterators) {}
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_SCORING_SCORER_H_
