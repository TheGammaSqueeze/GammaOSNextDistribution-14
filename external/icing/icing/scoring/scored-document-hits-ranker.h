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

#ifndef ICING_SCORING_SCORED_DOCUMENT_HITS_RANKER_H_
#define ICING_SCORING_SCORED_DOCUMENT_HITS_RANKER_H_

#include "icing/scoring/scored-document-hit.h"

namespace icing {
namespace lib {

// TODO(sungyc): re-evaluate other similar implementations (e.g. std::sort +
//               std::queue/std::vector). Also revisit the capacity shrinking
//               issue for PopNext().

// ScoredDocumentHitsRanker is an interface class for ranking
// ScoredDocumentHits.
class ScoredDocumentHitsRanker {
 public:
  virtual ~ScoredDocumentHitsRanker() = default;

  // Pop the next top JoinedScoredDocumentHit and return. It is undefined to
  // call PopNext on an empty ranker, so the caller should check if it is not
  // empty before calling.
  //
  // Note: ranker may store ScoredDocumentHit or JoinedScoredDocumentHit. We can
  // add template for this interface, but since JoinedScoredDocumentHit is a
  // superset of ScoredDocumentHit, we unify the return type of PopNext to use
  // the superset type JoinedScoredDocumentHit in order to make it simple, and
  // rankers storing ScoredDocumentHit should convert it to
  // JoinedScoredDocumentHit before returning. It makes the implementation
  // simpler, especially for ResultRetriever, which now only needs to deal with
  // one single return format.
  virtual JoinedScoredDocumentHit PopNext() = 0;

  // Truncates the remaining ScoredDocumentHits to the given size. The best
  // ScoredDocumentHits (according to the ranking policy) should be kept.
  // If new_size is invalid (< 0), or greater or equal to # of remaining
  // ScoredDocumentHits, then no action will be taken. Otherwise truncates the
  // the remaining ScoredDocumentHits to the given size.
  virtual void TruncateHitsTo(int new_size) = 0;

  virtual int size() const = 0;

  virtual bool empty() const = 0;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_SCORING_SCORED_DOCUMENT_HITS_RANKER_H_
