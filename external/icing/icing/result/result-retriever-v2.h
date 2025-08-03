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

#ifndef ICING_RESULT_RETRIEVER_V2_H_
#define ICING_RESULT_RETRIEVER_V2_H_

#include <cstdint>
#include <memory>
#include <unordered_map>
#include <utility>
#include <vector>

#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/proto/search.pb.h"
#include "icing/result/page-result.h"
#include "icing/result/result-state-v2.h"
#include "icing/result/snippet-retriever.h"
#include "icing/schema/schema-store.h"
#include "icing/scoring/scored-document-hit.h"
#include "icing/store/document-store.h"
#include "icing/tokenization/language-segmenter.h"
#include "icing/transform/normalizer.h"

namespace icing {
namespace lib {

class GroupResultLimiterV2 {
 public:
  GroupResultLimiterV2() {}

  virtual ~GroupResultLimiterV2() = default;

  // Returns true if the scored_document_hit should be removed.
  virtual bool ShouldBeRemoved(
      const ScoredDocumentHit& scored_document_hit,
      const std::unordered_map<int32_t, int>& entry_id_group_id_map,
      const DocumentStore& document_store,
      std::vector<int>& group_result_limits,
      ResultSpecProto::ResultGroupingType result_group_type,
      int64_t current_time_ms) const;
};

class ResultRetrieverV2 {
 public:
  // Factory function to create a ResultRetrieverV2 which does not take
  // ownership of any input components, and all pointers must refer to valid
  // objects that outlive the created ResultRetrieverV2 instance.
  //
  // Returns:
  //   A ResultRetrieverV2 on success
  //   FAILED_PRECONDITION on any null pointer input
  static libtextclassifier3::StatusOr<std::unique_ptr<ResultRetrieverV2>>
  Create(const DocumentStore* doc_store, const SchemaStore* schema_store,
         const LanguageSegmenter* language_segmenter,
         const Normalizer* normalizer,
         std::unique_ptr<const GroupResultLimiterV2> group_result_limiter =
             std::make_unique<const GroupResultLimiterV2>());

  // Retrieves results (pairs of DocumentProtos and SnippetProtos) with the
  // given ResultState which holds document and snippet information. It pulls
  // out the next top rank documents from ResultState, retrieves the documents
  // from storage, updates ResultState, and finally wraps the result + other
  // information into PageResult. The expected number of documents to return is
  // min(num_per_page, the number of all scored document hits) inside
  // ResultState.
  //
  // The number of snippets to return is based on the total number of snippets
  // needed and number of snippets that have already been returned previously
  // for the same query. The order of results returned will be sorted by
  // scored_document_hit_comparator inside ResultState.
  //
  // An additional boolean value will be returned, indicating if ResultState has
  // remaining documents to be retrieved next round.
  //
  // All errors will be ignored. It will keep retrieving the next document and
  // valid documents will be included in PageResult.
  //
  // Returns:
  //   std::pair<PageResult, bool>
  std::pair<PageResult, bool> RetrieveNextPage(ResultStateV2& result_state,
                                               int64_t current_time_ms) const;

 private:
  explicit ResultRetrieverV2(
      const DocumentStore* doc_store,
      std::unique_ptr<SnippetRetriever> snippet_retriever,
      std::unique_ptr<const GroupResultLimiterV2> group_result_limiter)
      : doc_store_(*doc_store),
        snippet_retriever_(std::move(snippet_retriever)),
        group_result_limiter_(std::move(group_result_limiter)) {}

  const DocumentStore& doc_store_;
  std::unique_ptr<SnippetRetriever> snippet_retriever_;
  const std::unique_ptr<const GroupResultLimiterV2> group_result_limiter_;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_RESULT_RETRIEVER_V2_H_
