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

#include "icing/schema/section-manager.h"

#include <algorithm>
#include <cstdint>
#include <string>
#include <string_view>
#include <utility>
#include <vector>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/absl_ports/canonical_errors.h"
#include "icing/legacy/core/icing-string-util.h"
#include "icing/proto/document.pb.h"
#include "icing/proto/schema.pb.h"
#include "icing/proto/term.pb.h"
#include "icing/schema/property-util.h"
#include "icing/schema/section.h"
#include "icing/store/document-filter-data.h"
#include "icing/store/key-mapper.h"
#include "icing/util/status-macros.h"

namespace icing {
namespace lib {

namespace {

// Helper function to append a new section metadata
libtextclassifier3::Status AppendNewSectionMetadata(
    std::vector<SectionMetadata>* metadata_list,
    std::string&& concatenated_path,
    const PropertyConfigProto& property_config) {
  // Validates next section id, makes sure that section id is the same as the
  // list index so that we could find any section metadata by id in O(1) later.
  SectionId new_section_id = static_cast<SectionId>(metadata_list->size());
  if (!IsSectionIdValid(new_section_id)) {
    // Max number of sections reached
    return absl_ports::OutOfRangeError(IcingStringUtil::StringPrintf(
        "Too many properties to be indexed, max number of properties "
        "allowed: %d",
        kMaxSectionId - kMinSectionId + 1));
  }

  // Creates section metadata
  metadata_list->push_back(SectionMetadata(
      new_section_id, property_config.data_type(),
      property_config.string_indexing_config().tokenizer_type(),
      property_config.string_indexing_config().term_match_type(),
      property_config.integer_indexing_config().numeric_match_type(),
      std::move(concatenated_path)));
  return libtextclassifier3::Status::OK;
}

template <typename T>
void AppendSection(
    SectionMetadata section_metadata,
    libtextclassifier3::StatusOr<std::vector<T>>&& section_content_or,
    std::vector<Section<T>>& sections_out) {
  if (!section_content_or.ok()) {
    return;
  }

  std::vector<T> section_content = std::move(section_content_or).ValueOrDie();
  if (!section_content.empty()) {
    // Adds to result vector if section is found in document
    sections_out.emplace_back(std::move(section_metadata),
                              std::move(section_content));
  }
}

}  // namespace

libtextclassifier3::Status
SectionManager::Builder::ProcessSchemaTypePropertyConfig(
    SchemaTypeId schema_type_id, const PropertyConfigProto& property_config,
    std::string&& property_path) {
  if (schema_type_id < 0 || schema_type_id >= section_metadata_cache_.size()) {
    return absl_ports::InvalidArgumentError("Invalid schema type id");
  }

  // We don't need to check if the property is indexable. This method will
  // only be called properties that should consume sectionIds, even if the
  // property's indexing configuration itself is not indexable.
  // This would be the case for unknown and non-indexable property paths that
  // are defined in the indexable_nested_properties_list.
  ICING_RETURN_IF_ERROR(
      AppendNewSectionMetadata(&section_metadata_cache_[schema_type_id],
                               std::move(property_path), property_config));
  return libtextclassifier3::Status::OK;
}

libtextclassifier3::StatusOr<const SectionMetadata*>
SectionManager::GetSectionMetadata(SchemaTypeId schema_type_id,
                                   SectionId section_id) const {
  if (schema_type_id < 0 || schema_type_id >= section_metadata_cache_.size()) {
    return absl_ports::InvalidArgumentError("Invalid schema type id");
  }
  if (!IsSectionIdValid(section_id)) {
    return absl_ports::InvalidArgumentError(IcingStringUtil::StringPrintf(
        "Section id %d is greater than the max value %d", section_id,
        kMaxSectionId));
  }

  const std::vector<SectionMetadata>& section_metadatas =
      section_metadata_cache_[schema_type_id];
  if (section_id >= section_metadatas.size()) {
    return absl_ports::InvalidArgumentError(IcingStringUtil::StringPrintf(
        "Section with id %d doesn't exist in type config with id %d",
        section_id, schema_type_id));
  }

  // The index of metadata list is the same as the section id, so we can use
  // section id as the index.
  return &section_metadatas[section_id];
}

libtextclassifier3::StatusOr<SectionGroup> SectionManager::ExtractSections(
    const DocumentProto& document) const {
  ICING_ASSIGN_OR_RETURN(const std::vector<SectionMetadata>* metadata_list,
                         GetMetadataList(document.schema()));
  SectionGroup section_group;
  for (const SectionMetadata& section_metadata : *metadata_list) {
    switch (section_metadata.data_type) {
      case PropertyConfigProto::DataType::STRING: {
        if (section_metadata.term_match_type == TermMatchType::UNKNOWN ||
            section_metadata.tokenizer ==
                StringIndexingConfig::TokenizerType::NONE) {
          // Skip if term-match type is UNKNOWN, or if the tokenizer-type is
          // NONE.
          break;
        }
        AppendSection(
            section_metadata,
            property_util::ExtractPropertyValuesFromDocument<std::string_view>(
                document, section_metadata.path),
            section_group.string_sections);
        break;
      }
      case PropertyConfigProto::DataType::INT64: {
        if (section_metadata.numeric_match_type ==
            IntegerIndexingConfig::NumericMatchType::UNKNOWN) {
          // Skip if numeric-match type is UNKNOWN.
          break;
        }
        AppendSection(section_metadata,
                      property_util::ExtractPropertyValuesFromDocument<int64_t>(
                          document, section_metadata.path),
                      section_group.integer_sections);
        break;
      }
      default: {
        // Skip other data types.
        break;
      }
    }
  }
  return section_group;
}

libtextclassifier3::StatusOr<const std::vector<SectionMetadata>*>
SectionManager::GetMetadataList(const std::string& type_config_name) const {
  ICING_ASSIGN_OR_RETURN(SchemaTypeId schema_type_id,
                         schema_type_mapper_.Get(type_config_name));
  return &section_metadata_cache_.at(schema_type_id);
}

}  // namespace lib
}  // namespace icing
