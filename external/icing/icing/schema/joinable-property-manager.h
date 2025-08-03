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

#ifndef ICING_SCHEMA_JOINABLE_PROPERTY_MANAGER_H_
#define ICING_SCHEMA_JOINABLE_PROPERTY_MANAGER_H_

#include <memory>
#include <string>
#include <unordered_map>
#include <vector>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/proto/document.pb.h"
#include "icing/schema/joinable-property.h"
#include "icing/store/document-filter-data.h"
#include "icing/store/key-mapper.h"

namespace icing {
namespace lib {

// This class provides joinable-property-related operations. It assigns joinable
// properties according to JoinableConfig and extracts joinable property values
// from documents.
class JoinablePropertyManager {
 public:
  // A wrapper class that contains a vector of metadatas and property path to
  // JoinablePropertyId reverse lookup map.
  struct JoinablePropertyMetadataListWrapper {
    std::vector<JoinablePropertyMetadata> metadata_list;
    std::unordered_map<std::string, JoinablePropertyId> property_path_to_id_map;
  };

  // Builder class to create a JoinablePropertyManager which does not take
  // ownership of any input components, and all pointers must refer to valid
  // objects that outlive the created JoinablePropertyManager instance.
  class Builder {
   public:
    explicit Builder(const KeyMapper<SchemaTypeId>& schema_type_mapper)
        : schema_type_mapper_(schema_type_mapper),
          joinable_property_metadata_cache_(schema_type_mapper.num_keys()) {}

    // Checks and appends a new JoinablePropertyMetadata for the schema type id
    // if the given property config is joinable.
    //
    // Returns:
    //   - OK on success
    //   - INVALID_ARGUMENT_ERROR if schema type id is invalid (not in range [0,
    //     schema_type_mapper_.num_keys() - 1])
    //   - OUT_OF_RANGE_ERROR if # of joinable properties in a single Schema
    //     exceeds the threshold (kTotalNumJoinableProperties)
    libtextclassifier3::Status ProcessSchemaTypePropertyConfig(
        SchemaTypeId schema_type_id, const PropertyConfigProto& property_config,
        std::string&& property_path);

    // Builds and returns a JoinablePropertyManager instance.
    std::unique_ptr<JoinablePropertyManager> Build() && {
      return std::unique_ptr<JoinablePropertyManager>(
          new JoinablePropertyManager(
              schema_type_mapper_,
              std::move(joinable_property_metadata_cache_)));
    }

   private:
    const KeyMapper<SchemaTypeId>& schema_type_mapper_;  // Does not own.
    std::vector<JoinablePropertyMetadataListWrapper>
        joinable_property_metadata_cache_;
  };

  JoinablePropertyManager(const JoinablePropertyManager&) = delete;
  JoinablePropertyManager& operator=(const JoinablePropertyManager&) = delete;

  // Extracts all joinable property contents of different types from the given
  // document and group them by joinable value type.
  // - Joinable properties are sorted by joinable property id in ascending
  //   order.
  // - Joinable property ids start from 0.
  // - Joinable properties with empty content won't be returned.
  //
  // Returns:
  //   - A JoinablePropertyGroup instance on success
  //   - NOT_FOUND_ERROR if the type config name of document is not present in
  //     schema_type_mapper_
  libtextclassifier3::StatusOr<JoinablePropertyGroup> ExtractJoinableProperties(
      const DocumentProto& document) const;

  // Returns the JoinablePropertyMetadata associated with property_path that's
  // in the SchemaTypeId.
  //
  // Returns:
  //   - Valid pointer to JoinablePropertyMetadata on success
  //   - nullptr if property_path doesn't exist (or is not joinable) in the
  //     joinable metadata list of the schema
  //   - INVALID_ARGUMENT_ERROR if schema type id is invalid
  libtextclassifier3::StatusOr<const JoinablePropertyMetadata*>
  GetJoinablePropertyMetadata(SchemaTypeId schema_type_id,
                              const std::string& property_path) const;

  // Returns the JoinablePropertyMetadata associated with the JoinablePropertyId
  // that's in the SchemaTypeId.
  //
  // Returns:
  //   - Valid pointer to JoinablePropertyMetadata on success
  //   - INVALID_ARGUMENT_ERROR if schema type id or JoinablePropertyId is
  //     invalid
  libtextclassifier3::StatusOr<const JoinablePropertyMetadata*>
  GetJoinablePropertyMetadata(SchemaTypeId schema_type_id,
                              JoinablePropertyId joinable_property_id) const;

  // Returns:
  //   - On success, the joinable property metadatas for the specified type
  //   - NOT_FOUND_ERROR if the type config name is not present in
  //     schema_type_mapper_
  libtextclassifier3::StatusOr<const std::vector<JoinablePropertyMetadata>*>
  GetMetadataList(const std::string& type_config_name) const;

 private:
  explicit JoinablePropertyManager(
      const KeyMapper<SchemaTypeId>& schema_type_mapper,
      std::vector<JoinablePropertyMetadataListWrapper>&&
          joinable_property_metadata_cache)
      : schema_type_mapper_(schema_type_mapper),
        joinable_property_metadata_cache_(joinable_property_metadata_cache) {}

  // Maps schema types to a densely-assigned unique id.
  const KeyMapper<SchemaTypeId>& schema_type_mapper_;  // Does not own

  // The index of joinable_property_metadata_cache_ corresponds to a schema
  // type's SchemaTypeId. At that SchemaTypeId index, we store a
  // JoinablePropertyMetadataListWrapper instance. The metadata list's index
  // corresponds to a joinable property's JoinablePropertyId. At the
  // JoinablePropertyId index, we store the JoinablePropertyMetadata of that
  // joinable property.
  //
  // For example, suppose "email" has a SchemaTypeId of 0 and it has a joinable
  // property called "senderQualifiedId" with a JoinablePropertyId of 1. Then
  // the "senderQualifiedId" property's JoinablePropertyMetadata will be at
  // joinable_property_metadata_cache_[0].metadata_list[1], and
  // joinable_property_metadata_cache_[0]
  //     .property_path_to_id_map["senderQualifiedId"]
  // will be 1.
  const std::vector<JoinablePropertyMetadataListWrapper>
      joinable_property_metadata_cache_;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_SCHEMA_JOINABLE_PROPERTY_MANAGER_H_
