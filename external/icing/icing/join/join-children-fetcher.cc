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

#include "icing/join/join-children-fetcher.h"

#include "icing/absl_ports/canonical_errors.h"
#include "icing/absl_ports/str_cat.h"

namespace icing {
namespace lib {

libtextclassifier3::StatusOr<std::vector<ScoredDocumentHit>>
JoinChildrenFetcher::GetChildren(DocumentId parent_doc_id) const {
  if (join_spec_.parent_property_expression() == kQualifiedIdExpr) {
    if (auto iter = map_joinable_qualified_id_.find(parent_doc_id);
        iter != map_joinable_qualified_id_.end()) {
      return iter->second;
    }
    return std::vector<ScoredDocumentHit>();
  }
  // TODO(b/256022027): So far we only support kQualifiedIdExpr for
  // parent_property_expression, we could support more.
  return absl_ports::UnimplementedError(absl_ports::StrCat(
      "Parent property expression must be ", kQualifiedIdExpr));
}

}  // namespace lib
}  // namespace icing
