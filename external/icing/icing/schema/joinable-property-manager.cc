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

#include "icing/schema/joinable-property-manager.h"

#include <memory>
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
#include "icing/schema/joinable-property.h"
#include "icing/schema/property-util.h"
#include "icing/store/document-filter-data.h"
#include "icing/util/status-macros.h"

namespace icing {
namespace lib {

namespace {

// Helper function to append a new joinable property metadata
libtextclassifier3::Status AppendNewJoinablePropertyMetadata(
    JoinablePropertyManager::JoinablePropertyMetadataListWrapper*
        metadata_list_wrapper,
    std::string&& concatenated_path,
    PropertyConfigProto::DataType::Code data_type,
    JoinableConfig::ValueType::Code value_type) {
  // Validates next joinable property id, makes sure that joinable property id
  // is the same as the list index so that we could find any joinable property
  // metadata by id in O(1) later.
  JoinablePropertyId new_id = static_cast<JoinablePropertyId>(
      metadata_list_wrapper->metadata_list.size());
  if (!IsJoinablePropertyIdValid(new_id)) {
    // Max number of joinable properties reached
    return absl_ports::OutOfRangeError(
        IcingStringUtil::StringPrintf("Too many properties to be joinable, max "
                                      "number of properties allowed: %d",
                                      kTotalNumJoinableProperties));
  }

  // Creates joinable property metadata
  metadata_list_wrapper->metadata_list.push_back(JoinablePropertyMetadata(
      new_id, data_type, value_type, std::move(concatenated_path)));
  metadata_list_wrapper->property_path_to_id_map.insert(
      {metadata_list_wrapper->metadata_list.back().path, new_id});
  return libtextclassifier3::Status::OK;
}

template <typename T>
void AppendJoinablePropertyContent(
    JoinablePropertyMetadata joinable_property_metadata,
    libtextclassifier3::StatusOr<std::vector<T>>&& joinable_property_content_or,
    std::vector<JoinableProperty<T>>& joinable_property_out) {
  if (!joinable_property_content_or.ok()) {
    return;
  }

  std::vector<T> joinable_property_content =
      std::move(joinable_property_content_or).ValueOrDie();
  if (!joinable_property_content.empty()) {
    // Adds to result vector if joinable property is found in document
    joinable_property_out.emplace_back(std::move(joinable_property_metadata),
                                       std::move(joinable_property_content));
  }
}

}  // namespace

libtextclassifier3::Status
JoinablePropertyManager::Builder::ProcessSchemaTypePropertyConfig(
    SchemaTypeId schema_type_id, const PropertyConfigProto& property_config,
    std::string&& property_path) {
  if (schema_type_id < 0 ||
      schema_type_id >=
          static_cast<int64_t>(joinable_property_metadata_cache_.size())) {
    return absl_ports::InvalidArgumentError("Invalid schema type id");
  }

  switch (property_config.data_type()) {
    case PropertyConfigProto::DataType::STRING: {
      if (property_config.joinable_config().value_type() ==
          JoinableConfig::ValueType::QUALIFIED_ID) {
        ICING_RETURN_IF_ERROR(AppendNewJoinablePropertyMetadata(
            &joinable_property_metadata_cache_[schema_type_id],
            std::move(property_path), PropertyConfigProto::DataType::STRING,
            JoinableConfig::ValueType::QUALIFIED_ID));
      }
      break;
    }
    default: {
      // Skip other data types.
      break;
    }
  }
  return libtextclassifier3::Status::OK;
}

libtextclassifier3::StatusOr<JoinablePropertyGroup>
JoinablePropertyManager::ExtractJoinableProperties(
    const DocumentProto& document) const {
  ICING_ASSIGN_OR_RETURN(
      const std::vector<JoinablePropertyMetadata>* metadata_list,
      GetMetadataList(document.schema()));
  JoinablePropertyGroup joinable_property_group;
  for (const JoinablePropertyMetadata& joinable_property_metadata :
       *metadata_list) {
    switch (joinable_property_metadata.data_type) {
      case PropertyConfigProto::DataType::STRING: {
        if (joinable_property_metadata.value_type ==
            JoinableConfig::ValueType::QUALIFIED_ID) {
          AppendJoinablePropertyContent(
              joinable_property_metadata,
              property_util::ExtractPropertyValuesFromDocument<
                  std::string_view>(document, joinable_property_metadata.path),
              joinable_property_group.qualified_id_properties);
        }
        break;
      }
      default: {
        // Skip other data types.
        break;
      }
    }
  }
  return joinable_property_group;
}

libtextclassifier3::StatusOr<const JoinablePropertyMetadata*>
JoinablePropertyManager::GetJoinablePropertyMetadata(
    SchemaTypeId schema_type_id, const std::string& property_path) const {
  if (schema_type_id < 0 ||
      schema_type_id >=
          static_cast<int64_t>(joinable_property_metadata_cache_.size())) {
    return absl_ports::InvalidArgumentError("Invalid schema type id");
  }

  const auto iter = joinable_property_metadata_cache_[schema_type_id]
                        .property_path_to_id_map.find(property_path);
  if (iter == joinable_property_metadata_cache_[schema_type_id]
                  .property_path_to_id_map.end()) {
    return nullptr;
  }

  JoinablePropertyId joinable_property_id = iter->second;
  return &joinable_property_metadata_cache_[schema_type_id]
              .metadata_list[joinable_property_id];
}

libtextclassifier3::StatusOr<const JoinablePropertyMetadata*>
JoinablePropertyManager::GetJoinablePropertyMetadata(
    SchemaTypeId schema_type_id,
    JoinablePropertyId joinable_property_id) const {
  if (schema_type_id < 0 ||
      schema_type_id >=
          static_cast<int64_t>(joinable_property_metadata_cache_.size())) {
    return absl_ports::InvalidArgumentError("Invalid schema type id");
  }
  if (!IsJoinablePropertyIdValid(joinable_property_id)) {
    return absl_ports::InvalidArgumentError(IcingStringUtil::StringPrintf(
        "Invalid joinable property id %d", joinable_property_id));
  }

  const std::vector<JoinablePropertyMetadata>& metadata_list =
      joinable_property_metadata_cache_[schema_type_id].metadata_list;
  if (joinable_property_id >= metadata_list.size()) {
    return absl_ports::InvalidArgumentError(IcingStringUtil::StringPrintf(
        "Joinable property with id %d doesn't exist in type config id %d",
        joinable_property_id, schema_type_id));
  }

  // The index of metadata list is the same as the joinable property id, so we
  // can use joinable property id as the index.
  return &metadata_list[joinable_property_id];
}

libtextclassifier3::StatusOr<const std::vector<JoinablePropertyMetadata>*>
JoinablePropertyManager::GetMetadataList(
    const std::string& type_config_name) const {
  ICING_ASSIGN_OR_RETURN(SchemaTypeId schema_type_id,
                         schema_type_mapper_.Get(type_config_name));
  return &joinable_property_metadata_cache_.at(schema_type_id).metadata_list;
}

}  // namespace lib
}  // namespace icing
