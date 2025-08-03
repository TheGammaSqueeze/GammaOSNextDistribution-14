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

#ifndef ICING_SCORING_ADVANCED_SCORING_ADVANCED_SCORER_H_
#define ICING_SCORING_ADVANCED_SCORING_ADVANCED_SCORER_H_

#include <memory>
#include <vector>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/join/join-children-fetcher.h"
#include "icing/schema/schema-store.h"
#include "icing/scoring/advanced_scoring/score-expression.h"
#include "icing/scoring/bm25f-calculator.h"
#include "icing/scoring/scorer.h"
#include "icing/store/document-store.h"

namespace icing {
namespace lib {

class AdvancedScorer : public Scorer {
 public:
  // Returns:
  //   A AdvancedScorer instance on success
  //   FAILED_PRECONDITION on any null pointer input
  //   INVALID_ARGUMENT if fails to create an instance
  static libtextclassifier3::StatusOr<std::unique_ptr<AdvancedScorer>> Create(
      const ScoringSpecProto& scoring_spec, double default_score,
      const DocumentStore* document_store, const SchemaStore* schema_store,
      int64_t current_time_ms,
      const JoinChildrenFetcher* join_children_fetcher = nullptr);

  double GetScore(const DocHitInfo& hit_info,
                  const DocHitInfoIterator* query_it) override {
    libtextclassifier3::StatusOr<double> result =
        score_expression_->eval(hit_info, query_it);
    if (!result.ok()) {
      ICING_LOG(ERROR) << "Got an error when scoring a document:\n"
                       << result.status().error_message();
      return default_score_;
    }
    return std::move(result).ValueOrDie();
  }

  void PrepareToScore(
      std::unordered_map<std::string, std::unique_ptr<DocHitInfoIterator>>*
          query_term_iterators) override {
    if (query_term_iterators == nullptr || query_term_iterators->empty()) {
      return;
    }
    bm25f_calculator_->PrepareToScore(query_term_iterators);
  }

  bool is_constant() const { return score_expression_->is_constant_double(); }

 private:
  explicit AdvancedScorer(std::unique_ptr<ScoreExpression> score_expression,
                          std::unique_ptr<SectionWeights> section_weights,
                          std::unique_ptr<Bm25fCalculator> bm25f_calculator,
                          double default_score)
      : score_expression_(std::move(score_expression)),
        section_weights_(std::move(section_weights)),
        bm25f_calculator_(std::move(bm25f_calculator)),
        default_score_(default_score) {
    if (is_constant()) {
      ICING_LOG(WARNING)
          << "The advanced scoring expression will evaluate to a constant.";
    }
  }

  std::unique_ptr<ScoreExpression> score_expression_;
  std::unique_ptr<SectionWeights> section_weights_;
  std::unique_ptr<Bm25fCalculator> bm25f_calculator_;
  double default_score_;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_SCORING_ADVANCED_SCORING_ADVANCED_SCORER_H_
