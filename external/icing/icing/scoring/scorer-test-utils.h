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

#ifndef ICING_SCORING_SCORER_TEST_UTILS_H_
#define ICING_SCORING_SCORER_TEST_UTILS_H_

#include "icing/proto/scoring.pb.h"

namespace icing {
namespace lib {

enum class ScorerTestingMode { kNormal, kAdvanced };

inline ScoringSpecProto CreateScoringSpecForRankingStrategy(
    ScoringSpecProto::RankingStrategy::Code ranking_strategy,
    ScorerTestingMode testing_mode) {
  ScoringSpecProto scoring_spec;
  if (testing_mode != ScorerTestingMode::kAdvanced) {
    scoring_spec.set_rank_by(ranking_strategy);
    return scoring_spec;
  }
  scoring_spec.set_rank_by(
      ScoringSpecProto::RankingStrategy::ADVANCED_SCORING_EXPRESSION);
  switch (ranking_strategy) {
    case ScoringSpecProto::RankingStrategy::DOCUMENT_SCORE:
      scoring_spec.set_advanced_scoring_expression("this.documentScore()");
      return scoring_spec;
    case ScoringSpecProto::RankingStrategy::CREATION_TIMESTAMP:
      scoring_spec.set_advanced_scoring_expression("this.creationTimestamp()");
      return scoring_spec;
    case ScoringSpecProto::RankingStrategy::USAGE_TYPE1_COUNT:
      scoring_spec.set_advanced_scoring_expression("this.usageCount(1)");
      return scoring_spec;
    case ScoringSpecProto::RankingStrategy::USAGE_TYPE2_COUNT:
      scoring_spec.set_advanced_scoring_expression("this.usageCount(2)");
      return scoring_spec;
    case ScoringSpecProto::RankingStrategy::USAGE_TYPE3_COUNT:
      scoring_spec.set_advanced_scoring_expression("this.usageCount(3)");
      return scoring_spec;
    case ScoringSpecProto::RankingStrategy::USAGE_TYPE1_LAST_USED_TIMESTAMP:
      scoring_spec.set_advanced_scoring_expression(
          "this.usageLastUsedTimestamp(1)");
      return scoring_spec;
    case ScoringSpecProto::RankingStrategy::USAGE_TYPE2_LAST_USED_TIMESTAMP:
      scoring_spec.set_advanced_scoring_expression(
          "this.usageLastUsedTimestamp(2)");
      return scoring_spec;
    case ScoringSpecProto::RankingStrategy::USAGE_TYPE3_LAST_USED_TIMESTAMP:
      scoring_spec.set_advanced_scoring_expression(
          "this.usageLastUsedTimestamp(3)");
      return scoring_spec;
    case ScoringSpecProto::RankingStrategy::RELEVANCE_SCORE:
      scoring_spec.set_advanced_scoring_expression("this.relevanceScore()");
      return scoring_spec;
    case ScoringSpecProto::RankingStrategy::NONE:
    case ScoringSpecProto::RankingStrategy::JOIN_AGGREGATE_SCORE:
    case ScoringSpecProto::RankingStrategy::ADVANCED_SCORING_EXPRESSION:
      scoring_spec.set_rank_by(ranking_strategy);
      return scoring_spec;
  }
}

}  // namespace lib
}  // namespace icing

#endif  // ICING_SCORING_SCORER_TEST_UTILS_H_
