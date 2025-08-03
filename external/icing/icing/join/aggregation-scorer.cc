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
#include <memory>
#include <numeric>
#include <vector>

#include "icing/proto/search.pb.h"
#include "icing/scoring/scored-document-hit.h"

namespace icing {
namespace lib {

class CountAggregationScorer : public AggregationScorer {
 public:
  double GetScore(const ScoredDocumentHit& parent,
                  const std::vector<ScoredDocumentHit>& children) override {
    return children.size();
  }
};

class MinAggregationScorer : public AggregationScorer {
 public:
  double GetScore(const ScoredDocumentHit& parent,
                  const std::vector<ScoredDocumentHit>& children) override {
    if (children.empty()) {
      // Return 0 if there is no child document.
      // For non-empty children with negative scores, they are considered "worse
      // than" 0, so it is correct to return 0 for empty children to assign it a
      // rank higher than non-empty children with negative scores.
      return 0.0;
    }
    return std::min_element(children.begin(), children.end(),
                            [](const ScoredDocumentHit& lhs,
                               const ScoredDocumentHit& rhs) -> bool {
                              return lhs.score() < rhs.score();
                            })
        ->score();
  }
};

class AverageAggregationScorer : public AggregationScorer {
 public:
  double GetScore(const ScoredDocumentHit& parent,
                  const std::vector<ScoredDocumentHit>& children) override {
    if (children.empty()) {
      // Return 0 if there is no child document.
      // For non-empty children with negative scores, they are considered "worse
      // than" 0, so it is correct to return 0 for empty children to assign it a
      // rank higher than non-empty children with negative scores.
      return 0.0;
    }
    return std::reduce(
               children.begin(), children.end(), 0.0,
               [](double prev, const ScoredDocumentHit& item) -> double {
                 return prev + item.score();
               }) /
           children.size();
  }
};

class MaxAggregationScorer : public AggregationScorer {
 public:
  double GetScore(const ScoredDocumentHit& parent,
                  const std::vector<ScoredDocumentHit>& children) override {
    if (children.empty()) {
      // Return 0 if there is no child document.
      // For non-empty children with negative scores, they are considered "worse
      // than" 0, so it is correct to return 0 for empty children to assign it a
      // rank higher than non-empty children with negative scores.
      return 0.0;
    }
    return std::max_element(children.begin(), children.end(),
                            [](const ScoredDocumentHit& lhs,
                               const ScoredDocumentHit& rhs) -> bool {
                              return lhs.score() < rhs.score();
                            })
        ->score();
  }
};

class SumAggregationScorer : public AggregationScorer {
 public:
  double GetScore(const ScoredDocumentHit& parent,
                  const std::vector<ScoredDocumentHit>& children) override {
    return std::reduce(
        children.begin(), children.end(), 0.0,
        [](double prev, const ScoredDocumentHit& item) -> double {
          return prev + item.score();
        });
  }
};

class DefaultAggregationScorer : public AggregationScorer {
 public:
  double GetScore(const ScoredDocumentHit& parent,
                  const std::vector<ScoredDocumentHit>& children) override {
    return parent.score();
  }
};

std::unique_ptr<AggregationScorer> AggregationScorer::Create(
    const JoinSpecProto& join_spec) {
  switch (join_spec.aggregation_scoring_strategy()) {
    case JoinSpecProto::AggregationScoringStrategy::COUNT:
      return std::make_unique<CountAggregationScorer>();
    case JoinSpecProto::AggregationScoringStrategy::MIN:
      return std::make_unique<MinAggregationScorer>();
    case JoinSpecProto::AggregationScoringStrategy::AVG:
      return std::make_unique<AverageAggregationScorer>();
    case JoinSpecProto::AggregationScoringStrategy::MAX:
      return std::make_unique<MaxAggregationScorer>();
    case JoinSpecProto::AggregationScoringStrategy::SUM:
      return std::make_unique<SumAggregationScorer>();
    case JoinSpecProto::AggregationScoringStrategy::NONE:
      // No aggregation strategy means using parent document score, so fall
      // through to return DefaultAggregationScorer.
      [[fallthrough]];
    default:
      return std::make_unique<DefaultAggregationScorer>();
  }
}

}  // namespace lib
}  // namespace icing
