// Copyright (C) 2021 Google LLC
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

#ifndef ICING_STORE_SUGGESTION_RESULT_CHECKER_IMPL_H_
#define ICING_STORE_SUGGESTION_RESULT_CHECKER_IMPL_H_

#include "icing/schema/section.h"
#include "icing/store/document-id.h"
#include "icing/store/document-store.h"
#include "icing/store/namespace-id.h"
#include "icing/store/suggestion-result-checker.h"

namespace icing {
namespace lib {

class SuggestionResultCheckerImpl : public SuggestionResultChecker {
 public:
  explicit SuggestionResultCheckerImpl(
      const DocumentStore* document_store, const SchemaStore* schema_store,
      std::unordered_set<NamespaceId> target_namespace_ids,
      std::unordered_map<NamespaceId, std::unordered_set<DocumentId>>
          document_id_filter_map,
      std::unordered_set<SchemaTypeId> target_schema_type_ids,
      std::unordered_map<SchemaTypeId, SectionIdMask> property_filter_map,
      std::string target_section, std::unordered_set<DocumentId> search_base,
      int64_t current_time_ms)
      : document_store_(*document_store),
        schema_store_(*schema_store),
        target_namespace_ids_(std::move(target_namespace_ids)),
        document_id_filter_map_(std::move(document_id_filter_map)),
        target_schema_type_ids_(std::move(target_schema_type_ids)),
        property_filter_map_(std::move(property_filter_map)),
        target_section_(std::move(target_section)),
        search_base_(std::move(search_base)),
        current_time_ms_(current_time_ms) {}

  bool MatchesTargetNamespace(NamespaceId namespace_id) const {
    return target_namespace_ids_.empty() ||
           target_namespace_ids_.find(namespace_id) !=
               target_namespace_ids_.end();
  }

  bool MatchesTargetDocumentIds(NamespaceId namespace_id,
                                DocumentId document_id) const {
    if (document_id_filter_map_.empty()) {
      return true;
    }
    auto document_ids_itr = document_id_filter_map_.find(namespace_id);
    // The client doesn't set desired document ids in this namespace, or the
    // client doesn't want this document.
    return document_ids_itr == document_id_filter_map_.end() ||
           document_ids_itr->second.find(document_id) !=
               document_ids_itr->second.end();
  }

  bool MatchesTargetSchemaType(SchemaTypeId schema_type_id) const {
    return target_schema_type_ids_.empty() ||
           target_schema_type_ids_.find(schema_type_id) !=
               target_schema_type_ids_.end();
  }

  bool MatchesTargetSection(SchemaTypeId schema_type_id,
                            SectionId section_id) const {
    if (target_section_.empty()) {
      return true;
    }
    auto section_metadata_or =
        schema_store_.GetSectionMetadata(schema_type_id, section_id);
    if (!section_metadata_or.ok()) {
      // cannot find the target section metadata.
      return false;
    }
    const SectionMetadata* section_metadata = section_metadata_or.ValueOrDie();
    return section_metadata->path == target_section_;
  }

  bool MatchesSearchBase(DocumentId document_id) const {
    return search_base_.empty() ||
           search_base_.find(document_id) != search_base_.end();
  }

  bool MatchesPropertyFilter(SchemaTypeId schema_type_id,
                             SectionId section_id) const {
    if (property_filter_map_.empty()) {
      return true;
    }
    auto section_mask_itr = property_filter_map_.find(schema_type_id);
    return section_mask_itr == property_filter_map_.end() ||
           (section_mask_itr->second & (UINT64_C(1) << section_id)) != 0;
  }

  bool BelongsToTargetResults(DocumentId document_id,
                              SectionId section_id) const override {
    // Get the document filter data first.
    auto document_filter_data_optional_ =
        document_store_.GetAliveDocumentFilterData(document_id,
                                                   current_time_ms_);
    if (!document_filter_data_optional_) {
      // The document doesn't exist.
      return false;
    }
    DocumentFilterData document_filter_data =
        document_filter_data_optional_.value();

    if (!MatchesTargetNamespace(document_filter_data.namespace_id())) {
      return false;
    }
    if (!MatchesTargetDocumentIds(document_filter_data.namespace_id(),
                                  document_id)) {
      return false;
    }
    if (!MatchesTargetSchemaType(document_filter_data.schema_type_id())) {
      return false;
    }
    if (!MatchesTargetSection(document_filter_data.schema_type_id(),
                              section_id)) {
      return false;
    }
    if (!MatchesSearchBase(document_id)) {
      return false;
    }
    if (!MatchesPropertyFilter(document_filter_data.schema_type_id(),
                               section_id)) {
      return false;
    }
    return true;
  }
  const DocumentStore& document_store_;
  const SchemaStore& schema_store_;
  std::unordered_set<NamespaceId> target_namespace_ids_;
  std::unordered_map<NamespaceId, std::unordered_set<DocumentId>>
      document_id_filter_map_;
  std::unordered_set<SchemaTypeId> target_schema_type_ids_;
  std::unordered_map<SchemaTypeId, SectionIdMask> property_filter_map_;
  std::string target_section_;
  std::unordered_set<DocumentId> search_base_;
  int64_t current_time_ms_;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_STORE_SUGGESTION_RESULT_CHECKER_IMPL_H_