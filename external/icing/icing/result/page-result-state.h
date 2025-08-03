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

#ifndef ICING_RESULT_PAGE_RESULT_STATE_H_
#define ICING_RESULT_PAGE_RESULT_STATE_H_

#include <cstdint>
#include <vector>

#include "icing/result/projection-tree.h"
#include "icing/result/snippet-context.h"
#include "icing/scoring/scored-document-hit.h"

namespace icing {
namespace lib {

// Contains information of results of one page.
struct PageResultState {
  PageResultState(std::vector<ScoredDocumentHit> scored_document_hits_in,
                  uint64_t next_page_token_in,
                  SnippetContext snippet_context_in,
                  std::unordered_map<std::string, ProjectionTree> tree_map,
                  int num_previously_returned_in, int num_per_page_in)
      : scored_document_hits(std::move(scored_document_hits_in)),
        next_page_token(next_page_token_in),
        snippet_context(std::move(snippet_context_in)),
        projection_tree_map(std::move(tree_map)),
        num_previously_returned(num_previously_returned_in),
        requested_page_size(num_per_page_in) {}

  // Results of one page
  std::vector<ScoredDocumentHit> scored_document_hits;

  // Token used to fetch the next page
  uint64_t next_page_token;

  // Information needed for snippeting.
  SnippetContext snippet_context;

  // Information needed for projection.
  std::unordered_map<std::string, ProjectionTree> projection_tree_map;

  // Number of results that have been returned in previous pages.
  int num_previously_returned;

  // The page size for this query. This should always be >=
  // scored_document_hits.size();
  int requested_page_size;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_RESULT_PAGE_RESULT_STATE_H_
