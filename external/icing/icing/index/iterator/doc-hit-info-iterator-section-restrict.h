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

#ifndef ICING_INDEX_ITERATOR_DOC_HIT_INFO_ITERATOR_SECTION_RESTRICT_H_
#define ICING_INDEX_ITERATOR_DOC_HIT_INFO_ITERATOR_SECTION_RESTRICT_H_

#include <cstdint>
#include <memory>
#include <set>
#include <string>
#include <vector>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/index/iterator/doc-hit-info-iterator.h"
#include "icing/index/iterator/section-restrict-data.h"
#include "icing/proto/search.pb.h"
#include "icing/schema/schema-store.h"
#include "icing/schema/section.h"
#include "icing/store/document-id.h"
#include "icing/store/document-store.h"

namespace icing {
namespace lib {

// A iterator that helps filter for DocHitInfos whose term was in a section
// named target_section.
//
// NOTE: This is a little different from the DocHitInfoIteratorFilter class.
// That class is meant to be applied to the root of a query tree and filter over
// all results at the end. This class is more used in the limited scope of a
// term or a small group of terms.
class DocHitInfoIteratorSectionRestrict : public DocHitInfoLeafIterator {
 public:
  // Does not take any ownership, and all pointers must refer to valid objects
  // that outlive the one constructed.
  explicit DocHitInfoIteratorSectionRestrict(
      std::unique_ptr<DocHitInfoIterator> delegate, SectionRestrictData* data);

  // Methods that apply section restrictions to all DocHitInfoLeafIterator nodes
  // inside the provided iterator tree, and return the root of the tree
  // afterwards. These methods do not take any ownership for the raw pointer
  // parameters, which must refer to valid objects that outlive the iterator
  // returned.
  static std::unique_ptr<DocHitInfoIterator> ApplyRestrictions(
      std::unique_ptr<DocHitInfoIterator> iterator,
      const DocumentStore* document_store, const SchemaStore* schema_store,
      std::set<std::string> target_sections, int64_t current_time_ms);
  static std::unique_ptr<DocHitInfoIterator> ApplyRestrictions(
      std::unique_ptr<DocHitInfoIterator> iterator,
      const DocumentStore* document_store, const SchemaStore* schema_store,
      const SearchSpecProto& search_spec, int64_t current_time_ms);
  static std::unique_ptr<DocHitInfoIterator> ApplyRestrictions(
      std::unique_ptr<DocHitInfoIterator> iterator, SectionRestrictData* data);

  libtextclassifier3::Status Advance() override;

  libtextclassifier3::StatusOr<TrimmedNode> TrimRightMostNode() && override;

  CallStats GetCallStats() const override { return delegate_->GetCallStats(); }

  std::string ToString() const override;

  // Note that the DocHitInfoIteratorSectionRestrict can only be applied at
  // DocHitInfoLeafIterator, which can be a term iterator or another
  // DocHitInfoIteratorSectionRestrict.
  //
  // To filter the matching sections, filtering_section_mask should be set to
  // doc_hit_info_.hit_section_ids_mask() held in the outermost
  // DocHitInfoIteratorSectionRestrict, which is equal to the intersection of
  // all hit_section_ids_mask in the DocHitInfoIteratorSectionRestrict chain,
  // since for any two section restrict iterators chained together, the outer
  // one's hit_section_ids_mask is always a subset of the inner one's
  // hit_section_ids_mask.
  void PopulateMatchedTermsStats(
      std::vector<TermMatchInfo>* matched_terms_stats,
      SectionIdMask filtering_section_mask = kSectionIdMaskAll) const override {
    if (doc_hit_info_.document_id() == kInvalidDocumentId) {
      // Current hit isn't valid, return.
      return;
    }
    delegate_->PopulateMatchedTermsStats(
        matched_terms_stats,
        /*filtering_section_mask=*/filtering_section_mask &
            doc_hit_info_.hit_section_ids_mask());
  }

 private:
  std::unique_ptr<DocHitInfoIterator> delegate_;
  // Does not own.
  SectionRestrictData* data_;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_INDEX_ITERATOR_DOC_HIT_INFO_ITERATOR_SECTION_RESTRICT_H_
