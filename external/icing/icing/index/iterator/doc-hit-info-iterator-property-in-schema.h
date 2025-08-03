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

#ifndef ICING_INDEX_ITERATOR_DOC_HIT_INFO_ITERATOR_PROPERTY_IN_SCHEMA_H_
#define ICING_INDEX_ITERATOR_DOC_HIT_INFO_ITERATOR_PROPERTY_IN_SCHEMA_H_

#include <cstdint>
#include <memory>
#include <string>
#include <string_view>
#include <utility>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/index/iterator/doc-hit-info-iterator.h"
#include "icing/schema/schema-store.h"
#include "icing/store/document-store.h"

namespace icing {
namespace lib {

// An iterator that helps filter for DocHitInfos whose schemas define the
// properties named in target_properties_.
class DocHitInfoIteratorPropertyInSchema : public DocHitInfoIterator {
 public:
  // Does not take any ownership, and all pointers must refer to valid objects
  // that outlive the one constructed. The delegate should be at minimum be
  // a DocHitInfoIteratorAllDocumentId, but other optimizations are possible,
  // cf. go/icing-property-in-schema-existence.
  explicit DocHitInfoIteratorPropertyInSchema(
      std::unique_ptr<DocHitInfoIterator> delegate,
      const DocumentStore* document_store, const SchemaStore* schema_store,
      std::set<std::string> target_sections, int64_t current_time_ms);

  libtextclassifier3::Status Advance() override;

  libtextclassifier3::StatusOr<TrimmedNode> TrimRightMostNode() && override;

  void MapChildren(const ChildrenMapper& mapper) override {
    delegate_ = mapper(std::move(delegate_));
  }

  CallStats GetCallStats() const override { return delegate_->GetCallStats(); }

  std::string ToString() const override;

  void PopulateMatchedTermsStats(
      std::vector<TermMatchInfo>* matched_terms_stats,
      SectionIdMask filtering_section_mask = kSectionIdMaskAll) const override {
    if (doc_hit_info_.document_id() == kInvalidDocumentId) {
      // Current hit isn't valid, return.
      return;
    }
    delegate_->PopulateMatchedTermsStats(matched_terms_stats,
                                         filtering_section_mask);
  }

 private:
  std::unique_ptr<DocHitInfoIterator> delegate_;
  const DocumentStore& document_store_;
  const SchemaStore& schema_store_;

  std::set<std::string> target_properties_;
  int64_t current_time_ms_;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_INDEX_ITERATOR_DOC_HIT_INFO_ITERATOR_PROPERTY_IN_SCHEMA_H_
