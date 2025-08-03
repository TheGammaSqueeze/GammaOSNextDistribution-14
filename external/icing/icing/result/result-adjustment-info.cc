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

#include "icing/result/result-adjustment-info.h"

#include <string>
#include <unordered_map>

#include "icing/proto/scoring.pb.h"
#include "icing/proto/search.pb.h"
#include "icing/proto/term.pb.h"
#include "icing/result/projection-tree.h"
#include "icing/result/snippet-context.h"
#include "icing/schema/schema-store.h"

namespace icing {
namespace lib {

namespace {

SnippetContext CreateSnippetContext(const SearchSpecProto& search_spec,
                                    const ResultSpecProto& result_spec,
                                    SectionRestrictQueryTermsMap query_terms) {
  if (result_spec.snippet_spec().num_to_snippet() > 0 &&
      result_spec.snippet_spec().num_matches_per_property() > 0) {
    // Needs snippeting
    return SnippetContext(std::move(query_terms), result_spec.snippet_spec(),
                          search_spec.term_match_type());
  }
  return SnippetContext(/*query_terms_in=*/{},
                        ResultSpecProto::SnippetSpecProto::default_instance(),
                        TermMatchType::UNKNOWN);
}

}  // namespace

ResultAdjustmentInfo::ResultAdjustmentInfo(
    const SearchSpecProto& search_spec, const ScoringSpecProto& scoring_spec,
    const ResultSpecProto& result_spec, const SchemaStore* schema_store,
    SectionRestrictQueryTermsMap query_terms)
    : snippet_context(CreateSnippetContext(search_spec, result_spec,
                                           std::move(query_terms))),
      remaining_num_to_snippet(snippet_context.snippet_spec.num_to_snippet()) {
  for (const SchemaStore::ExpandedTypePropertyMask& type_field_mask :
       schema_store->ExpandTypePropertyMasks(
           result_spec.type_property_masks())) {
    projection_tree_map.insert(
        {type_field_mask.schema_type, ProjectionTree(type_field_mask)});
  }
}

}  // namespace lib
}  // namespace icing
