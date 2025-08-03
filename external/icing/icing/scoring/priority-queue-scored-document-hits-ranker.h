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

#ifndef ICING_SCORING_PRIORITY_QUEUE_SCORED_DOCUMENT_HITS_RANKER_H_
#define ICING_SCORING_PRIORITY_QUEUE_SCORED_DOCUMENT_HITS_RANKER_H_

#include <queue>
#include <vector>

#include "icing/scoring/scored-document-hit.h"
#include "icing/scoring/scored-document-hits-ranker.h"

namespace icing {
namespace lib {

// ScoredDocumentHitsRanker interface implementation, based on
// std::priority_queue. We can get next top hit in O(lgN) time.
template <typename ScoredDataType,
          typename Converter = typename ScoredDataType::Converter>
class PriorityQueueScoredDocumentHitsRanker : public ScoredDocumentHitsRanker {
 public:
  explicit PriorityQueueScoredDocumentHitsRanker(
      std::vector<ScoredDataType>&& scored_data_vec, bool is_descending = true);

  ~PriorityQueueScoredDocumentHitsRanker() override = default;

  // Note: ranker may store ScoredDocumentHit or JoinedScoredDocumentHit, so we
  // have template for scored_data_pq_.
  // - JoinedScoredDocumentHit is a superset of ScoredDocumentHit, so we unify
  //   the return type of PopNext to use the superset type
  //   JoinedScoredDocumentHit in order to make it simple, and rankers storing
  //   ScoredDocumentHit should convert it to JoinedScoredDocumentHit before
  //   returning. It makes the implementation simpler, especially for
  //   ResultRetriever, which now only needs to deal with one single return
  //   format.
  // - JoinedScoredDocumentHit has ~2x size of ScoredDocumentHit. Since we cache
  //   ranker (which contains a priority queue of data) in ResultState, if we
  //   store the scored hits in JoinedScoredDocumentHit format directly, then it
  //   doubles the memory usage. Therefore, we still keep the flexibility to
  //   store ScoredDocumentHit or any other types of data, but require PopNext
  //   to convert it to JoinedScoredDocumentHit.
  JoinedScoredDocumentHit PopNext() override;

  void TruncateHitsTo(int new_size) override;

  int size() const override { return scored_data_pq_.size(); }

  bool empty() const override { return scored_data_pq_.empty(); }

 private:
  // Comparator for std::priority_queue. Since std::priority is a max heap
  // (descending order), reverse it if we want ascending order.
  class Comparator {
   public:
    explicit Comparator(bool is_ascending) : is_ascending_(is_ascending) {}

    bool operator()(const ScoredDataType& lhs,
                    const ScoredDataType& rhs) const {
      // STL comparator requirement: equal MUST return false.
      // If writing `return is_ascending_ == !(lhs < rhs)`:
      // - When lhs == rhs, !(lhs < rhs) is true
      // - If is_ascending_ is true, then we return true for equal case!
      if (is_ascending_) {
        return rhs < lhs;
      }
      return lhs < rhs;
    }

   private:
    bool is_ascending_;
  };

  Comparator comparator_;

  // Use priority queue to get top K hits in O(KlgN) time.
  std::priority_queue<ScoredDataType, std::vector<ScoredDataType>, Comparator>
      scored_data_pq_;

  Converter converter_;
};

template <typename ScoredDataType, typename Converter>
PriorityQueueScoredDocumentHitsRanker<ScoredDataType, Converter>::
    PriorityQueueScoredDocumentHitsRanker(
        std::vector<ScoredDataType>&& scored_data_vec, bool is_descending)
    : comparator_(/*is_ascending=*/!is_descending),
      scored_data_pq_(comparator_, std::move(scored_data_vec)) {}

template <typename ScoredDataType, typename Converter>
JoinedScoredDocumentHit
PriorityQueueScoredDocumentHitsRanker<ScoredDataType, Converter>::PopNext() {
  ScoredDataType next_scored_data = scored_data_pq_.top();
  scored_data_pq_.pop();
  return converter_(std::move(next_scored_data));
}

template <typename ScoredDataType, typename Converter>
void PriorityQueueScoredDocumentHitsRanker<
    ScoredDataType, Converter>::TruncateHitsTo(int new_size) {
  if (new_size < 0 || scored_data_pq_.size() <= new_size) {
    return;
  }

  // Copying the best new_size results.
  std::priority_queue<ScoredDataType, std::vector<ScoredDataType>, Comparator>
      new_pq(comparator_);
  for (int i = 0; i < new_size; ++i) {
    new_pq.push(scored_data_pq_.top());
    scored_data_pq_.pop();
  }
  scored_data_pq_ = std::move(new_pq);
}

}  // namespace lib
}  // namespace icing

#endif  // ICING_SCORING_PRIORITY_QUEUE_SCORED_DOCUMENT_HITS_RANKER_H_
