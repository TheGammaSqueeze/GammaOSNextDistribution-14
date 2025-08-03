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

#include "icing/index/iterator/doc-hit-info-iterator-section-restrict.h"

#include <cstdint>
#include <memory>
#include <set>
#include <string>
#include <string_view>
#include <unordered_map>
#include <utility>
#include <vector>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/absl_ports/canonical_errors.h"
#include "icing/absl_ports/str_cat.h"
#include "icing/absl_ports/str_join.h"
#include "icing/index/hit/doc-hit-info.h"
#include "icing/index/iterator/doc-hit-info-iterator.h"
#include "icing/index/iterator/section-restrict-data.h"
#include "icing/proto/search.pb.h"
#include "icing/schema/schema-store.h"
#include "icing/schema/section.h"
#include "icing/store/document-filter-data.h"
#include "icing/store/document-id.h"
#include "icing/store/document-store.h"
#include "icing/util/status-macros.h"

namespace icing {
namespace lib {

// An iterator that simply takes ownership of SectionRestrictData.
class SectionRestrictDataHolderIterator : public DocHitInfoIterator {
 public:
  explicit SectionRestrictDataHolderIterator(
      std::unique_ptr<DocHitInfoIterator> delegate,
      std::unique_ptr<SectionRestrictData> data)
      : delegate_(std::move(delegate)), data_(std::move(data)) {}

  libtextclassifier3::Status Advance() override {
    auto result = delegate_->Advance();
    doc_hit_info_ = delegate_->doc_hit_info();
    return result;
  }

  libtextclassifier3::StatusOr<TrimmedNode> TrimRightMostNode() && override {
    ICING_ASSIGN_OR_RETURN(TrimmedNode trimmed_delegate,
                           std::move(*delegate_).TrimRightMostNode());
    if (trimmed_delegate.iterator_ != nullptr) {
      trimmed_delegate.iterator_ =
          std::make_unique<SectionRestrictDataHolderIterator>(
              std::move(trimmed_delegate.iterator_), std::move(data_));
    }
    return trimmed_delegate;
  }

  void MapChildren(const ChildrenMapper& mapper) override {
    delegate_ = mapper(std::move(delegate_));
  }

  CallStats GetCallStats() const override { return delegate_->GetCallStats(); }

  std::string ToString() const override { return delegate_->ToString(); }

  void PopulateMatchedTermsStats(
      std::vector<TermMatchInfo>* matched_terms_stats,
      SectionIdMask filtering_section_mask) const override {
    return delegate_->PopulateMatchedTermsStats(matched_terms_stats,
                                                filtering_section_mask);
  }

 private:
  std::unique_ptr<DocHitInfoIterator> delegate_;
  std::unique_ptr<SectionRestrictData> data_;
};

DocHitInfoIteratorSectionRestrict::DocHitInfoIteratorSectionRestrict(
    std::unique_ptr<DocHitInfoIterator> delegate, SectionRestrictData* data)
    : delegate_(std::move(delegate)), data_(data) {}

std::unique_ptr<DocHitInfoIterator>
DocHitInfoIteratorSectionRestrict::ApplyRestrictions(
    std::unique_ptr<DocHitInfoIterator> iterator,
    const DocumentStore* document_store, const SchemaStore* schema_store,
    std::set<std::string> target_sections, int64_t current_time_ms) {
  std::unordered_map<std::string, std::set<std::string>> type_property_filters;
  type_property_filters[std::string(SchemaStore::kSchemaTypeWildcard)] =
      std::move(target_sections);
  auto data = std::make_unique<SectionRestrictData>(
      document_store, schema_store, current_time_ms, type_property_filters);
  std::unique_ptr<DocHitInfoIterator> result =
      ApplyRestrictions(std::move(iterator), data.get());
  return std::make_unique<SectionRestrictDataHolderIterator>(std::move(result),
                                                             std::move(data));
}

std::unique_ptr<DocHitInfoIterator>
DocHitInfoIteratorSectionRestrict::ApplyRestrictions(
    std::unique_ptr<DocHitInfoIterator> iterator,
    const DocumentStore* document_store, const SchemaStore* schema_store,
    const SearchSpecProto& search_spec, int64_t current_time_ms) {
  std::unordered_map<std::string, std::set<std::string>> type_property_filters;
  // TODO(b/294274922): Add support for polymorphism in type property filters.
  for (const TypePropertyMask& type_property_mask :
       search_spec.type_property_filters()) {
    type_property_filters[type_property_mask.schema_type()] =
        std::set<std::string>(type_property_mask.paths().begin(),
                              type_property_mask.paths().end());
  }
  auto data = std::make_unique<SectionRestrictData>(
      document_store, schema_store, current_time_ms, type_property_filters);
  std::unique_ptr<DocHitInfoIterator> result =
      ApplyRestrictions(std::move(iterator), data.get());
  return std::make_unique<SectionRestrictDataHolderIterator>(std::move(result),
                                                             std::move(data));
}

std::unique_ptr<DocHitInfoIterator>
DocHitInfoIteratorSectionRestrict::ApplyRestrictions(
    std::unique_ptr<DocHitInfoIterator> iterator, SectionRestrictData* data) {
  ChildrenMapper mapper;
  mapper = [&data, &mapper](std::unique_ptr<DocHitInfoIterator> iterator)
      -> std::unique_ptr<DocHitInfoIterator> {
    if (iterator->is_leaf()) {
      return std::make_unique<DocHitInfoIteratorSectionRestrict>(
          std::move(iterator), data);
    } else {
      iterator->MapChildren(mapper);
      return iterator;
    }
  };
  return mapper(std::move(iterator));
}

libtextclassifier3::Status DocHitInfoIteratorSectionRestrict::Advance() {
  doc_hit_info_ = DocHitInfo(kInvalidDocumentId);
  while (delegate_->Advance().ok()) {
    DocumentId document_id = delegate_->doc_hit_info().document_id();

    auto data_optional = data_->document_store().GetAliveDocumentFilterData(
        document_id, data_->current_time_ms());
    if (!data_optional) {
      // Ran into some error retrieving information on this hit, skip
      continue;
    }

    // Guaranteed that the DocumentFilterData exists at this point
    SchemaTypeId schema_type_id = data_optional.value().schema_type_id();
    auto schema_type_or = data_->schema_store().GetSchemaType(schema_type_id);
    if (!schema_type_or.ok()) {
      // Ran into error retrieving schema type, skip
      continue;
    }
    const std::string* schema_type = std::move(schema_type_or).ValueOrDie();
    SectionIdMask allowed_sections_mask =
        data_->ComputeAllowedSectionsMask(*schema_type);

    // A hit can be in multiple sections at once, need to check which of the
    // section ids match the sections allowed by type_property_masks_. This can
    // be done by doing a bitwise and of the section_id_mask in the doc hit and
    // the allowed_sections_mask.
    SectionIdMask section_id_mask =
        delegate_->doc_hit_info().hit_section_ids_mask() &
        allowed_sections_mask;

    // Return this document if:
    //  - the sectionIdMask is not empty after applying property filters, or
    //  - no property filters apply for its schema type (allowed_sections_mask
    //    == kSectionIdMaskAll). This is needed to ensure that in case of empty
    //    query (which uses doc-hit-info-iterator-all-document-id), where
    //    section_id_mask is kSectionIdMaskNone, doc hits with no property
    //    restrictions don't get filtered out. Doc hits for schema types for
    //    whom property filters are specified will still get filtered out.
    if (allowed_sections_mask == kSectionIdMaskAll ||
        section_id_mask != kSectionIdMaskNone) {
      doc_hit_info_ = delegate_->doc_hit_info();
      doc_hit_info_.set_hit_section_ids_mask(section_id_mask);
      return libtextclassifier3::Status::OK;
    }
    // Didn't find a matching section name for this hit. Continue.
  }

  // Didn't find anything on the delegate iterator.
  return absl_ports::ResourceExhaustedError("No more DocHitInfos in iterator");
}

libtextclassifier3::StatusOr<DocHitInfoIterator::TrimmedNode>
DocHitInfoIteratorSectionRestrict::TrimRightMostNode() && {
  ICING_ASSIGN_OR_RETURN(TrimmedNode trimmed_delegate,
                         std::move(*delegate_).TrimRightMostNode());
  // TrimRightMostNode is only used by suggestion processor to process query
  // expression, so an entry for wildcard should always be present in
  // type_property_filters_ when code flow reaches here. If the InternalError
  // below is returned, that means TrimRightMostNode hasn't been called in the
  // right context.
  const auto it = data_->type_property_filters().find("*");
  if (it == data_->type_property_filters().end()) {
    return absl_ports::InternalError(
        "A wildcard entry should always be present in type property filters "
        "whenever TrimRightMostNode() is called for "
        "DocHitInfoIteratorSectionRestrict");
  }
  const std::set<std::string>& target_sections = it->second;
  if (target_sections.empty()) {
    return absl_ports::InternalError(
        "Target sections should not be empty whenever TrimRightMostNode() is "
        "called for DocHitInfoIteratorSectionRestrict");
  }
  if (trimmed_delegate.iterator_ == nullptr) {
    // TODO(b/228240987): Update TrimmedNode and downstream code to handle
    // multiple section restricts.
    trimmed_delegate.target_section_ = std::move(*target_sections.begin());
    return trimmed_delegate;
  }
  trimmed_delegate.iterator_ =
      std::unique_ptr<DocHitInfoIteratorSectionRestrict>(
          new DocHitInfoIteratorSectionRestrict(
              std::move(trimmed_delegate.iterator_), std::move(data_)));
  return std::move(trimmed_delegate);
}

std::string DocHitInfoIteratorSectionRestrict::ToString() const {
  std::string output = "";
  for (auto it = data_->type_property_filters().cbegin();
       it != data_->type_property_filters().cend(); it++) {
    std::string paths = absl_ports::StrJoin(it->second, ",");
    output += (it->first) + ":" + (paths) + "; ";
  }
  std::string result = "{" + output.substr(0, output.size() - 2) + "}: ";
  return absl_ports::StrCat(result, delegate_->ToString());
}

}  // namespace lib
}  // namespace icing
