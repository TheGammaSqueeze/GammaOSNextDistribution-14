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

#ifndef ICING_JOIN_AGGREGATION_SCORER_H_
#define ICING_JOIN_AGGREGATION_SCORER_H_

#include <memory>
#include <vector>

#include "icing/proto/search.pb.h"
#include "icing/scoring/scored-document-hit.h"

namespace icing {
namespace lib {

class AggregationScorer {
 public:
  static std::unique_ptr<AggregationScorer> Create(
      const JoinSpecProto& join_spec);

  virtual ~AggregationScorer() = default;

  virtual double GetScore(const ScoredDocumentHit& parent,
                          const std::vector<ScoredDocumentHit>& children) = 0;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_JOIN_AGGREGATION_SCORER_H_
