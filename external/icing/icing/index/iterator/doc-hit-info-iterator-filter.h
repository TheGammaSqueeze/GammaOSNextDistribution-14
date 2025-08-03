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

#ifndef ICING_INDEX_ITERATOR_DOC_HIT_INFO_ITERATOR_FILTER_H_
#define ICING_INDEX_ITERATOR_DOC_HIT_INFO_ITERATOR_FILTER_H_

#include <cstdint>
#include <memory>
#include <string>
#include <string_view>
#include <unordered_set>
#include <utility>
#include <vector>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/index/iterator/doc-hit-info-iterator.h"
#include "icing/schema/schema-store.h"
#include "icing/store/document-store.h"
#include "icing/store/namespace-id.h"

namespace icing {
namespace lib {

// A iterator that helps filter out DocHitInfos associated with non-existing
// document ids.
class DocHitInfoIteratorFilter : public DocHitInfoIterator {
 public:
  struct Options {
    // List of namespaces that documents must have. An empty vector means that
    // all namespaces are valid, and no documents will be filtered out.
    //
    // Note that if we want to reference the strings in namespaces later, ensure
    // that the caller who passed the Options class outlives the
    // DocHitInfoIteratorFilter.
    std::vector<std::string_view> namespaces;

    // List of schema types that documents must have. An empty vector means that
    // all schema types are valid, and no documents will be filtered out.
    //
    // Note that if we want to reference the strings in schema types later,
    // ensure that the caller who passed the Options class outlives the
    // DocHitInfoIteratorFilter.
    std::vector<std::string_view> schema_types;
  };

  explicit DocHitInfoIteratorFilter(
      std::unique_ptr<DocHitInfoIterator> delegate,
      const DocumentStore* document_store, const SchemaStore* schema_store,
      const Options& options, int64_t current_time_ms);

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
    delegate_->PopulateMatchedTermsStats(matched_terms_stats,
                                         filtering_section_mask);
  }

 private:
  std::unique_ptr<DocHitInfoIterator> delegate_;
  const DocumentStore& document_store_;
  const SchemaStore& schema_store_;
  const Options options_;
  std::unordered_set<NamespaceId> target_namespace_ids_;
  std::unordered_set<SchemaTypeId> target_schema_type_ids_;
  int64_t current_time_ms_;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_INDEX_ITERATOR_DOC_HIT_INFO_ITERATOR_FILTER_H_
