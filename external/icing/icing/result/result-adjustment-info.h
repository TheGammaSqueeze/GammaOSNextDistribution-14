// Copyright (C) 2023 Google LLC
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

#ifndef ICING_RESULT_RESULT_ADJUSTMENT_INFO_H_
#define ICING_RESULT_RESULT_ADJUSTMENT_INFO_H_

#include <string>
#include <unordered_map>

#include "icing/proto/scoring.pb.h"
#include "icing/proto/search.pb.h"
#include "icing/result/projection-tree.h"
#include "icing/result/snippet-context.h"
#include "icing/schema/schema-store.h"

namespace icing {
namespace lib {

// A wrapper struct for information used in result retrieval.
// - Snippet
// - Projection
struct ResultAdjustmentInfo {
  // Information needed for snippeting.
  SnippetContext snippet_context;

  // Remaining # of docs to snippet.
  int remaining_num_to_snippet;

  // Information needed for projection.
  std::unordered_map<std::string, ProjectionTree> projection_tree_map;

  explicit ResultAdjustmentInfo(const SearchSpecProto& search_spec,
                                const ScoringSpecProto& scoring_spec,
                                const ResultSpecProto& result_spec,
                                const SchemaStore* schema_store,
                                SectionRestrictQueryTermsMap query_terms);
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_RESULT_RESULT_ADJUSTMENT_INFO_H_
