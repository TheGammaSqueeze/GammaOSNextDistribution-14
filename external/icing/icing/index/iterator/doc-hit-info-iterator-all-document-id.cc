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

#include "icing/index/iterator/doc-hit-info-iterator-all-document-id.h"

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/absl_ports/canonical_errors.h"
#include "icing/index/hit/doc-hit-info.h"
#include "icing/index/iterator/doc-hit-info-iterator.h"
#include "icing/store/document-id.h"

namespace icing {
namespace lib {

DocHitInfoIteratorAllDocumentId::DocHitInfoIteratorAllDocumentId(
    const DocumentId document_id_limit)
    : document_id_limit_(document_id_limit),
      current_document_id_(document_id_limit) {}

libtextclassifier3::Status DocHitInfoIteratorAllDocumentId::Advance() {
  if (!IsDocumentIdValid(current_document_id_)) {
    // Reached the end, set these to invalid values and return
    doc_hit_info_ = DocHitInfo(kInvalidDocumentId);
    return absl_ports::ResourceExhaustedError(
        "No more DocHitInfos in iterator");
  }
  doc_hit_info_.set_document_id(current_document_id_--);
  return libtextclassifier3::Status::OK;
}

libtextclassifier3::StatusOr<DocHitInfoIterator::TrimmedNode>
DocHitInfoIteratorAllDocumentId::TrimRightMostNode() && {
  // The all document id node should be trimmed.
  TrimmedNode node = {nullptr, /*term=*/"", /*term_start_index_=*/0,
                      /*unnormalized_term_length_=*/0};
  return node;
}
}  // namespace lib
}  // namespace icing
