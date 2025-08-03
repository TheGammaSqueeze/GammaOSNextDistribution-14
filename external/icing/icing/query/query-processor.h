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

#ifndef ICING_QUERY_QUERY_PROCESSOR_H_
#define ICING_QUERY_QUERY_PROCESSOR_H_

#include <cstdint>
#include <memory>

#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/index/index.h"
#include "icing/index/numeric/numeric-index.h"
#include "icing/proto/logging.pb.h"
#include "icing/proto/search.pb.h"
#include "icing/query/query-results.h"
#include "icing/schema/schema-store.h"
#include "icing/store/document-store.h"
#include "icing/tokenization/language-segmenter.h"
#include "icing/transform/normalizer.h"
#include "icing/util/clock.h"

namespace icing {
namespace lib {

// Processes SearchSpecProtos and retrieves the specified DocHitInfos that
// satisfies the query and its restrictions. This does not perform any scoring,
// and returns matched documents in a descending DocumentId order.
class QueryProcessor {
 public:
  // Factory function to create a QueryProcessor which does not take ownership
  // of any input components, and all pointers must refer to valid objects that
  // outlive the created QueryProcessor instance.
  //
  // Returns:
  //   An QueryProcessor on success
  //   FAILED_PRECONDITION if any of the pointers is null.
  static libtextclassifier3::StatusOr<std::unique_ptr<QueryProcessor>> Create(
      Index* index, const NumericIndex<int64_t>* numeric_index,
      const LanguageSegmenter* language_segmenter, const Normalizer* normalizer,
      const DocumentStore* document_store, const SchemaStore* schema_store,
      const Clock* clock);

  // Parse the search configurations (including the query, any additional
  // filters, etc.) in the SearchSpecProto into one DocHitInfoIterator.
  //
  // When ranking_strategy == RELEVANCE_SCORE, the root_iterator and the
  // query_term_iterators returned will keep term frequency information
  // internally, so that term frequency stats will be collected when calling
  // PopulateMatchedTermsStats to the iterators.
  //
  // Returns:
  //   On success,
  //     - One iterator that represents the entire query
  //     - A map representing the query terms and any section restrictions
  //   INVALID_ARGUMENT if query syntax is incorrect and cannot be tokenized
  //   INTERNAL_ERROR on all other errors
  libtextclassifier3::StatusOr<QueryResults> ParseSearch(
      const SearchSpecProto& search_spec,
      ScoringSpecProto::RankingStrategy::Code ranking_strategy,
      int64_t current_time_ms,
      QueryStatsProto::SearchStats* search_stats = nullptr);

 private:
  explicit QueryProcessor(Index* index,
                          const NumericIndex<int64_t>* numeric_index,
                          const LanguageSegmenter* language_segmenter,
                          const Normalizer* normalizer,
                          const DocumentStore* document_store,
                          const SchemaStore* schema_store, const Clock* clock);

  // Parse the query into a one DocHitInfoIterator that represents the root of a
  // query tree in our new Advanced Query Language.
  //
  // Returns:
  //   On success,
  //     - One iterator that represents the entire query
  //   INVALID_ARGUMENT if query syntax is incorrect and cannot be tokenized
  libtextclassifier3::StatusOr<QueryResults> ParseAdvancedQuery(
      const SearchSpecProto& search_spec,
      ScoringSpecProto::RankingStrategy::Code ranking_strategy,
      int64_t current_time_ms,
      QueryStatsProto::SearchStats* search_stats) const;

  // Parse the query into a one DocHitInfoIterator that represents the root of a
  // query tree.
  //
  // Returns:
  //   On success,
  //     - One iterator that represents the entire query
  //     - A map representing the query terms and any section restrictions
  //   INVALID_ARGUMENT if query syntax is incorrect and cannot be tokenized
  //   INTERNAL_ERROR on all other errors
  libtextclassifier3::StatusOr<QueryResults> ParseRawQuery(
      const SearchSpecProto& search_spec,
      ScoringSpecProto::RankingStrategy::Code ranking_strategy,
      int64_t current_time_ms);

  // Not const because we could modify/sort the hit buffer in the lite index at
  // query time.
  Index& index_;                                 // Does not own.
  const NumericIndex<int64_t>& numeric_index_;   // Does not own.
  const LanguageSegmenter& language_segmenter_;  // Does not own.
  const Normalizer& normalizer_;                 // Does not own.
  const DocumentStore& document_store_;          // Does not own.
  const SchemaStore& schema_store_;              // Does not own.
  const Clock& clock_;                           // Does not own.
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_QUERY_QUERY_PROCESSOR_H_
