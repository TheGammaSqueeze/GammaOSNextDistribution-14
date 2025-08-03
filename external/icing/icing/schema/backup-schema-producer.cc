// Copyright (C) 2023 Google LLC
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

#include "icing/schema/backup-schema-producer.h"

#include <string_view>
#include <unordered_map>
#include <vector>

#include "icing/proto/schema.pb.h"
#include "icing/proto/term.pb.h"
#include "icing/schema/property-util.h"
#include "icing/schema/section.h"
#include "icing/util/status-macros.h"

namespace icing {
namespace lib {

namespace {

// Creates a map of property to indexed id count based on the list of indexed
// properties provided by metadata_list.
// For all non-document properties, the value will always be 1.
// For document properties, the value will be the number of nested properties
// that are indexed with that document type.
std::unordered_map<std::string_view, int> CreateIndexedIdCountMap(
    const std::vector<SectionMetadata>* metadata_list) {
  std::unordered_map<std::string_view, int> property_indexed_id_count_map;
  for (const SectionMetadata& metadata : *metadata_list) {
    std::string_view top_level_property;
    size_t separator_pos =
        metadata.path.find(property_util::kPropertyPathSeparator);
    if (separator_pos == std::string::npos) {
      top_level_property = metadata.path;
    } else {
      top_level_property =
          std::string_view(metadata.path.c_str(), separator_pos);
    }
    int& count = property_indexed_id_count_map[top_level_property];
    ++count;
  }
  return property_indexed_id_count_map;
}

// Returns the indices (within schema.types()) of all types that are rollback
// incompatible (old code cannot handle these types if they are unmodified).
//
// Currently, this means types that:
//   1. Use RFC822 tokenization for any properties
//   2. Use more than 16 indexed properties
libtextclassifier3::StatusOr<std::vector<int>>
GetRollbackIncompatibleTypeIndices(const SchemaProto& schema,
                                   const SectionManager& type_manager) {
  std::vector<int> invalid_type_indices;
  for (int i = 0; i < schema.types_size(); ++i) {
    const SchemaTypeConfigProto& type = schema.types(i);
    bool rollback_incompatible = false;
    for (const PropertyConfigProto& property : type.properties()) {
      if (property.string_indexing_config().tokenizer_type() ==
          StringIndexingConfig::TokenizerType::RFC822) {
        rollback_incompatible = true;
        break;
      }
    }
    if (rollback_incompatible) {
      invalid_type_indices.push_back(i);
      continue;
    }

    ICING_ASSIGN_OR_RETURN(const std::vector<SectionMetadata>* metadata_list,
                           type_manager.GetMetadataList(type.schema_type()));
    if (metadata_list->size() > kOldTotalNumSections) {
      invalid_type_indices.push_back(i);
    }
  }
  return invalid_type_indices;
}

}  // namespace

/* static */ libtextclassifier3::StatusOr<BackupSchemaProducer>
BackupSchemaProducer::Create(const SchemaProto& schema,
                             const SectionManager& type_manager) {
  ICING_ASSIGN_OR_RETURN(
      std::vector<int> invalid_type_indices,
      GetRollbackIncompatibleTypeIndices(schema, type_manager));
  if (invalid_type_indices.empty()) {
    return BackupSchemaProducer();
  }

  SchemaProto backup_schema(schema);
  std::unordered_map<std::string_view, int> type_indexed_property_count;
  for (int i : invalid_type_indices) {
    SchemaTypeConfigProto* type = backup_schema.mutable_types(i);

    // This should never cause an error - every type should have an entry in the
    // type_manager.
    ICING_ASSIGN_OR_RETURN(const std::vector<SectionMetadata>* metadata_list,
                           type_manager.GetMetadataList(type->schema_type()));
    int num_indexed_sections = metadata_list->size();
    std::unordered_map<std::string_view, int> property_indexed_id_count_map;
    if (num_indexed_sections > kOldTotalNumSections) {
      property_indexed_id_count_map = CreateIndexedIdCountMap(metadata_list);
    }

    // Step 1. Switch all properties with RFC tokenizer as unindexed.
    for (PropertyConfigProto& property : *type->mutable_properties()) {
      // If the property uses the RFC tokenizer, then we need to set it to NONE
      // and set match type UNKNOWN.
      if (property.string_indexing_config().tokenizer_type() ==
          StringIndexingConfig::TokenizerType::RFC822) {
        property.clear_string_indexing_config();
        --num_indexed_sections;
        property_indexed_id_count_map.erase(property.property_name());
      }
    }

    // Step 2. If there are any types that exceed the old indexed property
    // limit, then mark indexed properties as unindexed until we're back under
    // the limit.
    if (num_indexed_sections <= kOldTotalNumSections) {
      continue;
    }

    // We expect that the last properties were the ones added most recently and
    // are the least crucial, so we do removal in reverse order. This is a bit
    // arbitrary, but we don't really have sufficient information to make this
    // judgment anyways.
    for (auto itr = type->mutable_properties()->rbegin();
         itr != type->mutable_properties()->rend(); ++itr) {
      auto indexed_count_itr =
          property_indexed_id_count_map.find(itr->property_name());
      if (indexed_count_itr == property_indexed_id_count_map.end()) {
        continue;
      }

      // Mark this property as unindexed and subtract all indexed property ids
      // consumed by this property.
      PropertyConfigProto& property = *itr;
      property.clear_document_indexing_config();
      property.clear_string_indexing_config();
      property.clear_integer_indexing_config();
      num_indexed_sections -= indexed_count_itr->second;
      if (num_indexed_sections <= kOldTotalNumSections) {
        break;
      }
    }
  }
  return BackupSchemaProducer(std::move(backup_schema));
}

}  // namespace lib
}  // namespace icing
