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

#include "icing/join/aggregation-scorer.h"

#include <algorithm>
#include <iterator>
#include <memory>
#include <vector>

#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "icing/proto/search.pb.h"
#include "icing/schema/section.h"
#include "icing/scoring/scored-document-hit.h"

namespace icing {
namespace lib {

namespace {

using ::testing::DoubleEq;

struct AggregationScorerTestParam {
  double ans;
  JoinSpecProto::AggregationScoringStrategy::Code scoring_strategy;
  double parent_score;
  std::vector<double> child_scores;

  explicit AggregationScorerTestParam(
      double ans_in,
      JoinSpecProto::AggregationScoringStrategy::Code scoring_strategy_in,
      double parent_score_in, std::vector<double> child_scores_in)
      : ans(ans_in),
        scoring_strategy(scoring_strategy_in),
        parent_score(std::move(parent_score_in)),
        child_scores(std::move(child_scores_in)) {}
};

class AggregationScorerTest
    : public ::testing::TestWithParam<AggregationScorerTestParam> {};

TEST_P(AggregationScorerTest, GetScore) {
  static constexpr DocumentId kDefaultDocumentId = 0;

  const AggregationScorerTestParam& param = GetParam();
  // Test AggregationScorer by creating some ScoredDocumentHits for parent and
  // child documents. DocumentId and SectionIdMask won't affect the aggregation
  // score calculation, so just simply set default values.
  // Parent document
  ScoredDocumentHit parent_scored_document_hit(
      kDefaultDocumentId, kSectionIdMaskNone, param.parent_score);
  // Child documents
  std::vector<ScoredDocumentHit> child_scored_document_hits;
  child_scored_document_hits.reserve(param.child_scores.size());
  std::transform(param.child_scores.cbegin(), param.child_scores.cend(),
                 std::back_inserter(child_scored_document_hits),
                 [](double score) -> ScoredDocumentHit {
                   return ScoredDocumentHit(kDefaultDocumentId,
                                            kSectionIdMaskNone, score);
                 });

  JoinSpecProto join_spec;
  join_spec.set_aggregation_scoring_strategy(param.scoring_strategy);
  std::unique_ptr<AggregationScorer> scorer =
      AggregationScorer::Create(join_spec);
  EXPECT_THAT(
      scorer->GetScore(parent_scored_document_hit, child_scored_document_hits),
      DoubleEq(param.ans));
}

INSTANTIATE_TEST_SUITE_P(
    CountAggregationScorerTest, AggregationScorerTest,
    testing::Values(
        // General case.
        AggregationScorerTestParam(
            /*ans_in=*/5, JoinSpecProto::AggregationScoringStrategy::COUNT,
            /*parent_score_in=*/98,
            /*child_scores_in=*/{8, 3, 1, 4, 7}),
        // Only one child.
        AggregationScorerTestParam(
            /*ans_in=*/1, JoinSpecProto::AggregationScoringStrategy::COUNT,
            /*parent_score_in=*/98,
            /*child_scores_in=*/{123}),
        // No child.
        AggregationScorerTestParam(
            /*ans_in=*/0, JoinSpecProto::AggregationScoringStrategy::COUNT,
            /*parent_score_in=*/98,
            /*child_scores_in=*/{})));

INSTANTIATE_TEST_SUITE_P(
    MinAggregationScorerTest, AggregationScorerTest,
    testing::Values(
        // General case.
        AggregationScorerTestParam(
            /*ans_in=*/1, JoinSpecProto::AggregationScoringStrategy::MIN,
            /*parent_score_in=*/98,
            /*child_scores_in=*/{8, 3, 1, 4, 7}),
        // Only one child, greater than parent.
        AggregationScorerTestParam(
            /*ans_in=*/123, JoinSpecProto::AggregationScoringStrategy::MIN,
            /*parent_score_in=*/98,
            /*child_scores_in=*/{123}),
        // Only one child, smaller than parent.
        AggregationScorerTestParam(
            /*ans_in=*/50, JoinSpecProto::AggregationScoringStrategy::MIN,
            /*parent_score_in=*/98,
            /*child_scores_in=*/{50}),
        // No child.
        AggregationScorerTestParam(
            /*ans_in=*/0, JoinSpecProto::AggregationScoringStrategy::MIN,
            /*parent_score_in=*/98,
            /*child_scores_in=*/{})));

INSTANTIATE_TEST_SUITE_P(
    AverageAggregationScorerTest, AggregationScorerTest,
    testing::Values(
        // General case.
        AggregationScorerTestParam(
            /*ans_in=*/4.6, JoinSpecProto::AggregationScoringStrategy::AVG,
            /*parent_score_in=*/98,
            /*child_scores_in=*/{8, 3, 1, 4, 7}),
        // Only one child.
        AggregationScorerTestParam(
            /*ans_in=*/123, JoinSpecProto::AggregationScoringStrategy::AVG,
            /*parent_score_in=*/98,
            /*child_scores_in=*/{123}),
        // No child.
        AggregationScorerTestParam(
            /*ans_in=*/0, JoinSpecProto::AggregationScoringStrategy::AVG,
            /*parent_score_in=*/98,
            /*child_scores_in=*/{})));

INSTANTIATE_TEST_SUITE_P(
    MaxAggregationScorerTest, AggregationScorerTest,
    testing::Values(
        // General case.
        AggregationScorerTestParam(
            /*ans_in=*/8, JoinSpecProto::AggregationScoringStrategy::MAX,
            /*parent_score_in=*/98,
            /*child_scores_in=*/{8, 3, 1, 4, 7}),
        // Only one child, greater than parent.
        AggregationScorerTestParam(
            /*ans_in=*/123, JoinSpecProto::AggregationScoringStrategy::MAX,
            /*parent_score_in=*/98,
            /*child_scores_in=*/{123}),
        // Only one child, smaller than parent.
        AggregationScorerTestParam(
            /*ans_in=*/50, JoinSpecProto::AggregationScoringStrategy::MAX,
            /*parent_score_in=*/98,
            /*child_scores_in=*/{50}),
        // No child.
        AggregationScorerTestParam(
            /*ans_in=*/0, JoinSpecProto::AggregationScoringStrategy::MAX,
            /*parent_score_in=*/98,
            /*child_scores_in=*/{})));

INSTANTIATE_TEST_SUITE_P(
    SumAggregationScorerTest, AggregationScorerTest,
    testing::Values(
        // General case.
        AggregationScorerTestParam(
            /*ans_in=*/23, JoinSpecProto::AggregationScoringStrategy::SUM,
            /*parent_score_in=*/98,
            /*child_scores_in=*/{8, 3, 1, 4, 7}),
        // Only one child.
        AggregationScorerTestParam(
            /*ans_in=*/123, JoinSpecProto::AggregationScoringStrategy::SUM,
            /*parent_score_in=*/98,
            /*child_scores_in=*/{123}),
        // No child.
        AggregationScorerTestParam(
            /*ans_in=*/0, JoinSpecProto::AggregationScoringStrategy::SUM,
            /*parent_score_in=*/0,
            /*child_scores_in=*/{})));

INSTANTIATE_TEST_SUITE_P(
    DefaultAggregationScorerTest, AggregationScorerTest,
    testing::Values(
        // General case.
        AggregationScorerTestParam(
            /*ans_in=*/98, JoinSpecProto::AggregationScoringStrategy::NONE,
            /*parent_score_in=*/98,
            /*child_scores_in=*/{8, 3, 1, 4, 7}),
        // Only one child, greater than parent.
        AggregationScorerTestParam(
            /*ans_in=*/98, JoinSpecProto::AggregationScoringStrategy::NONE,
            /*parent_score_in=*/98,
            /*child_scores_in=*/{123}),
        // Only one child, smaller than parent.
        AggregationScorerTestParam(
            /*ans_in=*/98, JoinSpecProto::AggregationScoringStrategy::NONE,
            /*parent_score_in=*/98,
            /*child_scores_in=*/{50}),
        // No child.
        AggregationScorerTestParam(
            /*ans_in=*/98, JoinSpecProto::AggregationScoringStrategy::NONE,
            /*parent_score_in=*/98,
            /*child_scores_in=*/{})));

}  // namespace

}  // namespace lib
}  // namespace icing
