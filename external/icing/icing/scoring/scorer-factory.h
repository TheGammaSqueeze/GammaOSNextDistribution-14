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

#ifndef ICING_SCORING_SCORER_FACTORY_H_
#define ICING_SCORING_SCORER_FACTORY_H_

#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/join/join-children-fetcher.h"
#include "icing/scoring/scorer.h"
#include "icing/store/document-store.h"

namespace icing {
namespace lib {

namespace scorer_factory {

// Factory function to create a Scorer which does not take ownership of any
// input components (DocumentStore), and all pointers must refer to valid
// objects that outlive the created Scorer instance. The default score will be
// returned only when the scorer fails to find or calculate a score for the
// document.
//
// Returns:
//   A Scorer on success
//   FAILED_PRECONDITION on any null pointer input
//   INVALID_ARGUMENT if fails to create an instance
libtextclassifier3::StatusOr<std::unique_ptr<Scorer>> Create(
    const ScoringSpecProto& scoring_spec, double default_score,
    const DocumentStore* document_store, const SchemaStore* schema_store,
    int64_t current_time_ms,
    const JoinChildrenFetcher* join_children_fetcher = nullptr);

}  // namespace scorer_factory

}  // namespace lib
}  // namespace icing

#endif  // ICING_SCORING_SCORER_FACTORY_H_
