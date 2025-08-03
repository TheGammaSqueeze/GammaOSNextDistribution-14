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

#ifndef ICING_JOIN_JOIN_PROCESSOR_H_
#define ICING_JOIN_JOIN_PROCESSOR_H_

#include <cstdint>
#include <string>
#include <string_view>
#include <vector>

#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/join/join-children-fetcher.h"
#include "icing/join/qualified-id-join-index.h"
#include "icing/proto/search.pb.h"
#include "icing/schema/schema-store.h"
#include "icing/scoring/scored-document-hit.h"
#include "icing/store/document-store.h"

namespace icing {
namespace lib {

class JoinProcessor {
 public:
  static constexpr std::string_view kQualifiedIdExpr = "this.qualifiedId()";

  explicit JoinProcessor(const DocumentStore* doc_store,
                         const SchemaStore* schema_store,
                         const QualifiedIdJoinIndex* qualified_id_join_index,
                         int64_t current_time_ms)
      : doc_store_(doc_store),
        schema_store_(schema_store),
        qualified_id_join_index_(qualified_id_join_index),
        current_time_ms_(current_time_ms) {}

  // Get a JoinChildrenFetcher used to fetch all children documents by a parent
  // document id.
  //
  // Returns:
  //   A JoinChildrenFetcher instance on success.
  //   UNIMPLEMENTED_ERROR if the join type specified by join_spec is not
  //   supported.
  libtextclassifier3::StatusOr<JoinChildrenFetcher> GetChildrenFetcher(
      const JoinSpecProto& join_spec,
      std::vector<ScoredDocumentHit>&& child_scored_document_hits);

  libtextclassifier3::StatusOr<std::vector<JoinedScoredDocumentHit>> Join(
      const JoinSpecProto& join_spec,
      std::vector<ScoredDocumentHit>&& parent_scored_document_hits,
      const JoinChildrenFetcher& join_children_fetcher);

 private:
  // Fetches referenced document id of the given document under the given
  // property path.
  //
  // TODO(b/256022027): validate joinable property (and its upper-level) should
  //                    not have REPEATED cardinality.
  //
  // Returns:
  //   - A valid referenced document id on success
  //   - kInvalidDocumentId if the given document is not found, doesn't have
  //     qualified id joinable type for the given property_path, or doesn't have
  //     joinable value (an optional property)
  //   - Any other QualifiedIdJoinIndex errors
  libtextclassifier3::StatusOr<DocumentId> FetchReferencedQualifiedId(
      const DocumentId& document_id, const std::string& property_path) const;

  const DocumentStore* doc_store_;  // Does not own.
  const SchemaStore* schema_store_;  // Does not own.
  const QualifiedIdJoinIndex* qualified_id_join_index_;  // Does not own.
  int64_t current_time_ms_;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_JOIN_JOIN_PROCESSOR_H_
