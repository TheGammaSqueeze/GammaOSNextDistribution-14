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

#include "icing/result/result-state-manager.h"

#include <memory>
#include <queue>
#include <utility>

#include "icing/result/page-result.h"
#include "icing/result/result-adjustment-info.h"
#include "icing/result/result-retriever-v2.h"
#include "icing/result/result-state-v2.h"
#include "icing/scoring/scored-document-hits-ranker.h"
#include "icing/util/clock.h"
#include "icing/util/logging.h"
#include "icing/util/status-macros.h"

namespace icing {
namespace lib {

ResultStateManager::ResultStateManager(int max_total_hits,
                                       const DocumentStore& document_store)
    : document_store_(document_store),
      max_total_hits_(max_total_hits),
      num_total_hits_(0),
      random_generator_(GetSteadyTimeNanoseconds()) {}

libtextclassifier3::StatusOr<std::pair<uint64_t, PageResult>>
ResultStateManager::CacheAndRetrieveFirstPage(
    std::unique_ptr<ScoredDocumentHitsRanker> ranker,
    std::unique_ptr<ResultAdjustmentInfo> parent_adjustment_info,
    std::unique_ptr<ResultAdjustmentInfo> child_adjustment_info,
    const ResultSpecProto& result_spec, const DocumentStore& document_store,
    const ResultRetrieverV2& result_retriever, int64_t current_time_ms) {
  if (ranker == nullptr) {
    return absl_ports::InvalidArgumentError("Should not provide null ranker");
  }

  // Create shared pointer of ResultState.
  // ResultState should be created by ResultStateManager only.
  std::shared_ptr<ResultStateV2> result_state = std::make_shared<ResultStateV2>(
      std::move(ranker), std::move(parent_adjustment_info),
      std::move(child_adjustment_info), result_spec, document_store);

  // Retrieve docs outside of ResultStateManager critical section.
  // Will enter ResultState critical section inside ResultRetriever.
  auto [page_result, has_more_results] =
      result_retriever.RetrieveNextPage(*result_state, current_time_ms);
  if (!has_more_results) {
    // No more pages, won't store ResultState, returns directly
    return std::make_pair(kInvalidNextPageToken, std::move(page_result));
  }

  // ResultState has multiple pages, storing it
  int num_hits_to_add = 0;
  {
    // ResultState critical section
    absl_ports::unique_lock l(&result_state->mutex);

    result_state->scored_document_hits_ranker->TruncateHitsTo(max_total_hits_);
    result_state->RegisterNumTotalHits(&num_total_hits_);
    num_hits_to_add = result_state->scored_document_hits_ranker->size();
  }

  // It is fine to exit ResultState critical section, since it is just created
  // above and only this thread (this call stack) has access to it. Thus, it
  // won't be changed during the gap before we enter ResultStateManager critical
  // section.
  uint64_t next_page_token = kInvalidNextPageToken;
  {
    // ResultStateManager critical section
    absl_ports::unique_lock l(&mutex_);

    // Remove expired result states first.
    InternalInvalidateExpiredResultStates(kDefaultResultStateTtlInMs,
                                          current_time_ms);
    // Remove states to make room for this new state.
    RemoveStatesIfNeeded(num_hits_to_add);
    // Generate a new unique token and add it into result_state_map_.
    next_page_token = Add(std::move(result_state), current_time_ms);
  }

  return std::make_pair(next_page_token, std::move(page_result));
}

uint64_t ResultStateManager::Add(std::shared_ptr<ResultStateV2> result_state,
                                 int64_t current_time_ms) {
  uint64_t new_token = GetUniqueToken();

  result_state_map_.emplace(new_token, std::move(result_state));
  // Tracks the insertion order
  token_queue_.push(std::make_pair(new_token, current_time_ms));

  return new_token;
}

libtextclassifier3::StatusOr<std::pair<uint64_t, PageResult>>
ResultStateManager::GetNextPage(uint64_t next_page_token,
                                const ResultRetrieverV2& result_retriever,
                                int64_t current_time_ms) {
  std::shared_ptr<ResultStateV2> result_state = nullptr;
  {
    // ResultStateManager critical section
    absl_ports::unique_lock l(&mutex_);

    // Remove expired result states before fetching
    InternalInvalidateExpiredResultStates(kDefaultResultStateTtlInMs,
                                          current_time_ms);

    const auto& state_iterator = result_state_map_.find(next_page_token);
    if (state_iterator == result_state_map_.end()) {
      return absl_ports::NotFoundError("next_page_token not found");
    }
    result_state = state_iterator->second;
  }

  // Retrieve docs outside of ResultStateManager critical section.
  // Will enter ResultState critical section inside ResultRetriever.
  auto [page_result, has_more_results] =
      result_retriever.RetrieveNextPage(*result_state, current_time_ms);

  if (!has_more_results) {
    {
      // ResultStateManager critical section
      absl_ports::unique_lock l(&mutex_);

      InternalInvalidateResultState(next_page_token);
    }

    next_page_token = kInvalidNextPageToken;
  }
  return std::make_pair(next_page_token, std::move(page_result));
}

void ResultStateManager::InvalidateResultState(uint64_t next_page_token) {
  if (next_page_token == kInvalidNextPageToken) {
    return;
  }

  absl_ports::unique_lock l(&mutex_);

  InternalInvalidateResultState(next_page_token);
}

void ResultStateManager::InvalidateAllResultStates() {
  absl_ports::unique_lock l(&mutex_);
  InternalInvalidateAllResultStates();
}

void ResultStateManager::InternalInvalidateAllResultStates() {
  // We don't have to reset num_total_hits_ (to 0) here, since clearing
  // result_state_map_ will "eventually" invoke the destructor of ResultState
  // (which decrements num_total_hits_) and num_total_hits_ will become 0.
  result_state_map_.clear();
  invalidated_token_set_.clear();
  token_queue_ = std::queue<std::pair<uint64_t, int64_t>>();
}

uint64_t ResultStateManager::GetUniqueToken() {
  uint64_t new_token = random_generator_();
  // There's a small chance of collision between the random numbers, here we're
  // trying to avoid any collisions by checking the keys.
  while (result_state_map_.find(new_token) != result_state_map_.end() ||
         invalidated_token_set_.find(new_token) !=
             invalidated_token_set_.end() ||
         new_token == kInvalidNextPageToken) {
    new_token = random_generator_();
  }
  return new_token;
}

void ResultStateManager::RemoveStatesIfNeeded(int num_hits_to_add) {
  if (result_state_map_.empty() || token_queue_.empty()) {
    return;
  }

  // 1. Check if this new result_state would take up the entire result state
  // manager budget.
  if (num_hits_to_add > max_total_hits_) {
    // This single result state will exceed our budget. Drop everything else to
    // accomodate it.
    InternalInvalidateAllResultStates();
    return;
  }

  // 2. Remove any tokens that were previously invalidated.
  while (!token_queue_.empty() &&
         invalidated_token_set_.find(token_queue_.front().first) !=
             invalidated_token_set_.end()) {
    invalidated_token_set_.erase(token_queue_.front().first);
    token_queue_.pop();
  }

  // 3. If we're over budget, remove states from oldest to newest until we fit
  // into our budget.
  // Note: num_total_hits_ may not be decremented immediately after invalidating
  // a result state, since other threads may still hold the shared pointer.
  // Thus, we have to check if token_queue_ is empty or not, since it is
  // possible that num_total_hits_ is non-zero and still greater than
  // max_total_hits_ when token_queue_ is empty. Still "eventually" it will be
  // decremented after the last thread releases the shared pointer.
  while (!token_queue_.empty() && num_total_hits_ > max_total_hits_) {
    InternalInvalidateResultState(token_queue_.front().first);
    token_queue_.pop();
  }
  invalidated_token_set_.clear();
}

void ResultStateManager::InternalInvalidateResultState(uint64_t token) {
  // Removes the entry in result_state_map_ and insert the token into
  // invalidated_token_set_. The entry in token_queue_ can't be easily removed
  // right now (may need O(n) time), so we leave it there and later completely
  // remove the token in RemoveStatesIfNeeded().
  auto itr = result_state_map_.find(token);
  if (itr != result_state_map_.end()) {
    // We don't have to decrement num_total_hits_ here, since erasing the shared
    // ptr instance will "eventually" invoke the destructor of ResultState and
    // it will handle this.
    result_state_map_.erase(itr);
    invalidated_token_set_.insert(token);
  }
}

void ResultStateManager::InternalInvalidateExpiredResultStates(
    int64_t result_state_ttl, int64_t current_time_ms) {
  while (!token_queue_.empty() &&
         current_time_ms - token_queue_.front().second >= result_state_ttl) {
    auto itr = result_state_map_.find(token_queue_.front().first);
    if (itr != result_state_map_.end()) {
      // We don't have to decrement num_total_hits_ here, since erasing the
      // shared ptr instance will "eventually" invoke the destructor of
      // ResultState and it will handle this.
      result_state_map_.erase(itr);
    } else {
      // Since result_state_map_ and invalidated_token_set_ are mutually
      // exclusive, we remove the token from invalidated_token_set_ only if it
      // isn't present in result_state_map_.
      invalidated_token_set_.erase(token_queue_.front().first);
    }
    token_queue_.pop();
  }
}

}  // namespace lib
}  // namespace icing
