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

#ifndef ICING_JOIN_JOIN_CHILDREN_FETCHER_H_
#define ICING_JOIN_JOIN_CHILDREN_FETCHER_H_

#include <unordered_map>
#include <vector>

#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/proto/search.pb.h"
#include "icing/scoring/scored-document-hit.h"
#include "icing/store/document-id.h"

namespace icing {
namespace lib {

// A class that provides the GetChildren method for joins to fetch all children
// documents given a parent document id.
//
// Internally, the class maintains a map for each joinable value type that
// groups children according to the joinable values. Currently we only support
// QUALIFIED_ID joining, in which the joinable value type is document id.
class JoinChildrenFetcher {
 public:
  explicit JoinChildrenFetcher(
      const JoinSpecProto& join_spec,
      std::unordered_map<DocumentId, std::vector<ScoredDocumentHit>>&&
          map_joinable_qualified_id)
      : join_spec_(join_spec),
        map_joinable_qualified_id_(std::move(map_joinable_qualified_id)) {}

  // Get a vector of children ScoredDocumentHit by parent document id.
  //
  // TODO(b/256022027): Implement property value joins with types of string and
  // int. In these cases, GetChildren should look up join index to fetch
  // joinable property value of the given parent_doc_id according to
  // join_spec_.parent_property_expression, and then fetch children by the
  // corresponding map in this class using the joinable property value.
  //
  // Returns:
  //   The vector of results on success.
  //   UNIMPLEMENTED_ERROR if the join type specified by join_spec is not
  //   supported.
  libtextclassifier3::StatusOr<std::vector<ScoredDocumentHit>> GetChildren(
      DocumentId parent_doc_id) const;

 private:
  static constexpr std::string_view kQualifiedIdExpr = "this.qualifiedId()";

  const JoinSpecProto& join_spec_;  // Does not own!

  // The map that groups children by qualified id used to support QualifiedId
  // joining. The joining type is document id.
  std::unordered_map<DocumentId, std::vector<ScoredDocumentHit>>
      map_joinable_qualified_id_;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_JOIN_JOIN_CHILDREN_FETCHER_H_
