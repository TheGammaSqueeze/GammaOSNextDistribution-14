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

#ifndef ICING_INDEX_ITERATOR_DOC_HIT_INFO_ITERATOR_NOT_H_
#define ICING_INDEX_ITERATOR_DOC_HIT_INFO_ITERATOR_NOT_H_

#include <cstdint>
#include <memory>
#include <string>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/index/iterator/doc-hit-info-iterator-all-document-id.h"
#include "icing/index/iterator/doc-hit-info-iterator.h"
#include "icing/store/document-id.h"

namespace icing {
namespace lib {

// Iterator that will return all documents that are *not* specified by the
// to_be_excluded_iterator.
//
// NOTE: doc_hit_info_.hit_section_ids_mask() is meaningless for this iterator.
// When this iterator produces a result, it's because the Document was not
// present in the to_be_excluded_iterator. There is no concept of the Document
// having been chosen because it's term was in a specific section. Since we
// don't know anything about the sections for the Document, the
// doc_hit_info.hit_section_ids_mask() is always kSectionIdMaskNone.
class DocHitInfoIteratorNot : public DocHitInfoIterator {
 public:
  // to_be_excluded_iterator: The results of this iterator will be excluded
  //     from this iterator's results.
  // document_id_limit: The DocumentId that represents the most recently added
  //     Document to the DocumentStore
  explicit DocHitInfoIteratorNot(
      std::unique_ptr<DocHitInfoIterator> to_be_excluded_iterator,
      const DocumentId document_id_limit);

  libtextclassifier3::Status Advance() override;

  // The NOT operator is not suppose to be trimmed.
  // We shouldn't generate suggestion for the last term if the last term belongs
  // to NOT operator.
  libtextclassifier3::StatusOr<TrimmedNode> TrimRightMostNode() && override;

  void MapChildren(const ChildrenMapper& mapper) override;

  CallStats GetCallStats() const override {
    return to_be_excluded_->GetCallStats() +
           all_document_id_iterator_.GetCallStats();
  }

  std::string ToString() const override;

 private:
  std::unique_ptr<DocHitInfoIterator> to_be_excluded_;
  DocHitInfoIteratorAllDocumentId all_document_id_iterator_;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_INDEX_ITERATOR_DOC_HIT_INFO_ITERATOR_NOT_H_
