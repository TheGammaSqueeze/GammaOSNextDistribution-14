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

#include "icing/index/iterator/doc-hit-info-iterator-property-in-document.h"

#include <memory>
#include <string>
#include <string_view>
#include <utility>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/absl_ports/canonical_errors.h"
#include "icing/index/hit/doc-hit-info.h"
#include "icing/index/iterator/doc-hit-info-iterator.h"
#include "icing/store/document-id.h"

namespace icing {
namespace lib {

DocHitInfoIteratorPropertyInDocument::DocHitInfoIteratorPropertyInDocument(
    std::unique_ptr<DocHitInfoIterator> meta_hit_iterator)
    : meta_hit_iterator_(std::move(meta_hit_iterator)) {}

libtextclassifier3::Status DocHitInfoIteratorPropertyInDocument::Advance() {
  while (meta_hit_iterator_->Advance().ok()) {
    // Currently, the metadata hits added by PropertyExistenceIndexingHandler
    // can only have a section id of 0, so the section mask has to be 1 << 0.
    if (meta_hit_iterator_->doc_hit_info().hit_section_ids_mask() == (1 << 0)) {
      doc_hit_info_ = meta_hit_iterator_->doc_hit_info();
      // Hits returned by "hasProperty" should not be associated with any
      // section.
      doc_hit_info_.set_hit_section_ids_mask(/*section_id_mask=*/0);
      return libtextclassifier3::Status::OK;
    }
  }

  doc_hit_info_ = DocHitInfo(kInvalidDocumentId);
  return absl_ports::ResourceExhaustedError("No more DocHitInfos in iterator");
}

libtextclassifier3::StatusOr<DocHitInfoIterator::TrimmedNode>
DocHitInfoIteratorPropertyInDocument::TrimRightMostNode() && {
  // Don't generate suggestion if the last operator is this custom function.
  return absl_ports::InvalidArgumentError(
      "Cannot generate suggestion if the last term is hasProperty().");
}

std::string DocHitInfoIteratorPropertyInDocument::ToString() const {
  return meta_hit_iterator_->ToString();
}

}  // namespace lib
}  // namespace icing
