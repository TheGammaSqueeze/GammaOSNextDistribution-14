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

#include "icing/scoring/scored-document-hit.h"

namespace icing {
namespace lib {

JoinedScoredDocumentHit ScoredDocumentHit::Converter::operator()(
    ScoredDocumentHit&& scored_doc_hit) const {
  double final_score = scored_doc_hit.score();
  return JoinedScoredDocumentHit(
      final_score,
      /*parent_scored_document_hit=*/std::move(scored_doc_hit),
      /*child_scored_document_hits=*/{});
}

}  // namespace lib
}  // namespace icing
