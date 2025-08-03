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

#ifndef ICING_INDEX_ITERATOR_DOC_HIT_INFO_ITERATOR_PROPERTY_IN_DOCUMENT_H_
#define ICING_INDEX_ITERATOR_DOC_HIT_INFO_ITERATOR_PROPERTY_IN_DOCUMENT_H_

#include <cstdint>
#include <memory>
#include <string>
#include <utility>
#include <vector>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/index/iterator/doc-hit-info-iterator.h"
#include "icing/schema/section.h"
#include "icing/store/document-id.h"

namespace icing {
namespace lib {

// The iterator returned by the "hasProperty" function in advanced query that
// post-processes metadata hits added by PropertyExistenceIndexingHandler.
// Specifically, it filters out hits that are not recognized as metadata, and
// always set hit_section_ids_mask to 0.
//
// It is marked as a subclass of DocHitInfoLeafIterator because section
// restriction should not be passed down to meta_hit_iterator.
class DocHitInfoIteratorPropertyInDocument : public DocHitInfoLeafIterator {
 public:
  explicit DocHitInfoIteratorPropertyInDocument(
      std::unique_ptr<DocHitInfoIterator> meta_hit_iterator);

  libtextclassifier3::Status Advance() override;

  libtextclassifier3::StatusOr<TrimmedNode> TrimRightMostNode() && override;

  CallStats GetCallStats() const override {
    return meta_hit_iterator_->GetCallStats();
  }

  std::string ToString() const override;

  void PopulateMatchedTermsStats(
      std::vector<TermMatchInfo>* matched_terms_stats,
      SectionIdMask filtering_section_mask = kSectionIdMaskAll) const override {
    if (doc_hit_info_.document_id() == kInvalidDocumentId) {
      // Current hit isn't valid, return.
      return;
    }
    meta_hit_iterator_->PopulateMatchedTermsStats(matched_terms_stats,
                                                  filtering_section_mask);
  }

 private:
  std::unique_ptr<DocHitInfoIterator> meta_hit_iterator_;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_INDEX_ITERATOR_DOC_HIT_INFO_ITERATOR_PROPERTY_IN_DOCUMENT_H_
