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

#include "icing/index/iterator/doc-hit-info-iterator-not.h"

#include <cstdint>
#include <memory>
#include <utility>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/absl_ports/canonical_errors.h"
#include "icing/absl_ports/str_cat.h"
#include "icing/index/hit/doc-hit-info.h"
#include "icing/index/iterator/doc-hit-info-iterator-all-document-id.h"
#include "icing/index/iterator/doc-hit-info-iterator.h"
#include "icing/store/document-id.h"

namespace icing {
namespace lib {

DocHitInfoIteratorNot::DocHitInfoIteratorNot(
    std::unique_ptr<DocHitInfoIterator> to_be_excluded,
    DocumentId document_id_limit)
    : to_be_excluded_(std::move(to_be_excluded)),
      all_document_id_iterator_(
          DocHitInfoIteratorAllDocumentId(document_id_limit)) {}

libtextclassifier3::Status DocHitInfoIteratorNot::Advance() {
  while (all_document_id_iterator_.Advance().ok()) {
    if (all_document_id_iterator_.doc_hit_info().document_id() <
        to_be_excluded_->doc_hit_info().document_id()) {
      // Since DocumentIds are returned from DocHitInfoIterators in decreasing
      // order, we have passed the last NOT result if we're smaller than its
      // DocumentId. Advance the NOT result if so.
      to_be_excluded_->Advance().IgnoreError();
    }

    if (all_document_id_iterator_.doc_hit_info().document_id() ==
        to_be_excluded_->doc_hit_info().document_id()) {
      // This is a NOT result, skip and Advance to the next result.
      continue;
    }

    // No errors, we've found a valid result
    doc_hit_info_ = all_document_id_iterator_.doc_hit_info();
    return libtextclassifier3::Status::OK;
  }

  // Didn't find a hit, return with error
  doc_hit_info_ = DocHitInfo(kInvalidDocumentId);
  return absl_ports::ResourceExhaustedError("No more DocHitInfos in iterator");
}

libtextclassifier3::StatusOr<DocHitInfoIterator::TrimmedNode>
DocHitInfoIteratorNot::TrimRightMostNode() && {
  // Don't generate suggestion if the last operator is NOT.
  return absl_ports::InvalidArgumentError(
      "Cannot generate suggestion if the last term is NOT operator.");
}

void DocHitInfoIteratorNot::MapChildren(const ChildrenMapper& mapper) {
  to_be_excluded_ = mapper(std::move(to_be_excluded_));
}

std::string DocHitInfoIteratorNot::ToString() const {
  return absl_ports::StrCat("(NOT ", to_be_excluded_->ToString(), ")");
}

}  // namespace lib
}  // namespace icing
