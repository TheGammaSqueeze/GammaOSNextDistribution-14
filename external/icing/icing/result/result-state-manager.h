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

#ifndef ICING_RESULT_RESULT_STATE_MANAGER_H_
#define ICING_RESULT_RESULT_STATE_MANAGER_H_

#include <atomic>
#include <memory>
#include <queue>
#include <random>
#include <unordered_map>
#include <unordered_set>

#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/absl_ports/mutex.h"
#include "icing/proto/search.pb.h"
#include "icing/result/page-result.h"
#include "icing/result/result-adjustment-info.h"
#include "icing/result/result-retriever-v2.h"
#include "icing/result/result-state-v2.h"
#include "icing/scoring/scored-document-hits-ranker.h"
#include "icing/util/clock.h"

namespace icing {
namespace lib {

// This should be the same as the default value of
// SearchResultProto.next_page_token.
inline constexpr uint64_t kInvalidNextPageToken = 0;

// 1 hr as the default ttl for a ResultState after being pushed into
// token_queue_.
inline constexpr int64_t kDefaultResultStateTtlInMs = 1LL * 60 * 60 * 1000;

// Used to store and manage ResultState.
class ResultStateManager {
 public:
  explicit ResultStateManager(int max_total_hits,
                              const DocumentStore& document_store);

  ResultStateManager(const ResultStateManager&) = delete;
  ResultStateManager& operator=(const ResultStateManager&) = delete;

  // Creates a new result state, retrieves and returns PageResult for the first
  // page. Also caches the new result state and returns a next_page_token which
  // can be used to fetch more pages from the same result state later. Before
  // caching the result state, adjusts (truncate) the size and evicts some old
  // result states if exceeding the cache size limit. next_page_token will be
  // set to a default value kInvalidNextPageToken if there're no more pages.
  //
  // NOTE: parent_adjustment_info and child_adjustment_info can be nullptr if
  //       there is no requirement to apply adjustment (snippet, projection) to
  //       them.
  //
  // NOTE: it is possible to have empty result for the first page even if the
  //       ranker was not empty before the retrieval, since GroupResultLimiter
  //       may filter out all docs. In this case, the first page is also the
  //       last page and next_page_token will be set to kInvalidNextPageToken.
  //
  // Returns:
  //   A token and PageResult wrapped by std::pair on success
  //   INVALID_ARGUMENT if the input ranker is null or contains no results
  libtextclassifier3::StatusOr<std::pair<uint64_t, PageResult>>
  CacheAndRetrieveFirstPage(
      std::unique_ptr<ScoredDocumentHitsRanker> ranker,
      std::unique_ptr<ResultAdjustmentInfo> parent_adjustment_info,
      std::unique_ptr<ResultAdjustmentInfo> child_adjustment_info,
      const ResultSpecProto& result_spec, const DocumentStore& document_store,
      const ResultRetrieverV2& result_retriever, int64_t current_time_ms)
      ICING_LOCKS_EXCLUDED(mutex_);

  // Retrieves and returns PageResult for the next page.
  // The returned results won't exist in ResultStateManager anymore. If the
  // query has no more pages after this retrieval, the input token will be
  // invalidated.
  //
  // NOTE: it is possible to have empty result for the last page even if the
  //       ranker was not empty before the retrieval, since GroupResultLimiter
  //       may filtered out all remaining docs.
  //
  // Returns:
  //   A token and PageResult wrapped by std::pair on success
  //   NOT_FOUND if failed to find any more results
  libtextclassifier3::StatusOr<std::pair<uint64_t, PageResult>> GetNextPage(
      uint64_t next_page_token, const ResultRetrieverV2& result_retriever,
      int64_t current_time_ms) ICING_LOCKS_EXCLUDED(mutex_);

  // Invalidates the result state associated with the given next-page token.
  void InvalidateResultState(uint64_t next_page_token)
      ICING_LOCKS_EXCLUDED(mutex_);

  // Invalidates all result states / tokens currently in ResultStateManager.
  void InvalidateAllResultStates() ICING_LOCKS_EXCLUDED(mutex_);

  int num_total_hits() const { return num_total_hits_; }

 private:
  absl_ports::shared_mutex mutex_;

  const DocumentStore& document_store_;

  // The maximum number of scored document hits that all result states may
  // have. When a new result state is added such that num_total_hits_ would
  // exceed max_total_hits_, the oldest result states are evicted until
  // num_total_hits_ is below max_total_hits.
  const int max_total_hits_;

  // The number of scored document hits that all result states currently held by
  // the result state manager have.
  std::atomic<int> num_total_hits_;

  // A hash map of (next-page token -> result state)
  std::unordered_map<uint64_t, std::shared_ptr<ResultStateV2>> result_state_map_
      ICING_GUARDED_BY(mutex_);

  // A queue used to track the insertion order of tokens with pushed timestamps.
  std::queue<std::pair<uint64_t, int64_t>> token_queue_
      ICING_GUARDED_BY(mutex_);

  // A set to temporarily store the invalidated tokens before they're finally
  // removed from token_queue_. We store the invalidated tokens to ensure the
  // uniqueness of new generated tokens.
  std::unordered_set<uint64_t> invalidated_token_set_ ICING_GUARDED_BY(mutex_);

  // A random 64-bit number generator
  std::mt19937_64 random_generator_ ICING_GUARDED_BY(mutex_);

  // Puts a new result state into the internal storage and returns a next-page
  // token associated with it. The token is guaranteed to be unique among all
  // currently valid tokens. When the maximum number of result states is
  // reached, the oldest / firstly added result state will be removed to make
  // room for the new state.
  uint64_t Add(std::shared_ptr<ResultStateV2> result_state,
               int64_t current_time_ms) ICING_EXCLUSIVE_LOCKS_REQUIRED(mutex_);

  // Helper method to generate a next-page token that is unique among all
  // existing tokens in token_queue_.
  uint64_t GetUniqueToken() ICING_EXCLUSIVE_LOCKS_REQUIRED(mutex_);

  // Helper method to remove old states to make room for incoming states with
  // size num_hits_to_add.
  void RemoveStatesIfNeeded(int num_hits_to_add)
      ICING_EXCLUSIVE_LOCKS_REQUIRED(mutex_);

  // Helper method to remove a result state from result_state_map_, the token
  // will then be temporarily kept in invalidated_token_set_ until it's finally
  // removed from token_queue_.
  void InternalInvalidateResultState(uint64_t token)
      ICING_EXCLUSIVE_LOCKS_REQUIRED(mutex_);

  // Internal method to invalidate all result states / tokens currently in
  // ResultStateManager. We need this separate method so that other public
  // methods don't need to call InvalidateAllResultStates(). Public methods
  // calling each other may cause deadlock issues.
  void InternalInvalidateAllResultStates()
      ICING_EXCLUSIVE_LOCKS_REQUIRED(mutex_);

  // Internal method to invalidate and remove expired result states / tokens
  // currently in ResultStateManager that were created before
  // current_time - result_state_ttl.
  void InternalInvalidateExpiredResultStates(int64_t result_state_ttl,
                                             int64_t current_time_ms)
      ICING_EXCLUSIVE_LOCKS_REQUIRED(mutex_);
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_RESULT_RESULT_STATE_MANAGER_H_
