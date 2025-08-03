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

#ifndef ICING_SCHEMA_SECTION_MANAGER_H_
#define ICING_SCHEMA_SECTION_MANAGER_H_

#include <memory>
#include <string>
#include <string_view>
#include <vector>

#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/proto/document.pb.h"
#include "icing/schema/section.h"
#include "icing/store/document-filter-data.h"
#include "icing/store/key-mapper.h"

namespace icing {
namespace lib {

// This class provides section-related operations. It assigns sections according
// to type configs and extracts section / sections from documents.
// The actual instance is created together with JoinablePropertyManager and both
// of them are wrapped into SchemaTypeManager.
//
// Note: SectionManager assumes schema type ids are consecutive integers
// starting from 0, so it maintains a vector with size
// schema_type_mapper_->num_keys() that maps schema type id to a list (2nd level
// vector) of SectionMetadatas. Therefore, all schema type ids stored in
// schema_type_mapper_ must be in range [0, schema_type_mapper_->num_keys() - 1]
// and unique.
class SectionManager {
 public:
  // Builder class to create a SectionManager which does not take ownership of
  // any input components, and all pointers must refer to valid objects that
  // outlive the created SectionManager instance.
  class Builder {
   public:
    explicit Builder(const KeyMapper<SchemaTypeId>& schema_type_mapper)
        : schema_type_mapper_(schema_type_mapper),
          section_metadata_cache_(schema_type_mapper.num_keys()) {}

    // Checks and appends a new SectionMetadata for the schema type id if the
    // given property config is indexable.
    //
    // Returns:
    //   - OK on success
    //   - INVALID_ARGUMENT_ERROR if schema type id is invalid (not in range [0,
    //     schema_type_mapper_.num_keys() - 1])
    //   - OUT_OF_RANGE_ERROR if # of indexable properties in a single Schema
    //     exceeds the threshold (kTotalNumSections)
    libtextclassifier3::Status ProcessSchemaTypePropertyConfig(
        SchemaTypeId schema_type_id, const PropertyConfigProto& property_config,
        std::string&& property_path);

    // Builds and returns a SectionManager instance.
    std::unique_ptr<SectionManager> Build() && {
      return std::unique_ptr<SectionManager>(new SectionManager(
          schema_type_mapper_, std::move(section_metadata_cache_)));
    }

   private:
    const KeyMapper<SchemaTypeId>& schema_type_mapper_;  // Does not own.
    std::vector<std::vector<SectionMetadata>> section_metadata_cache_;
  };

  SectionManager(const SectionManager&) = delete;
  SectionManager& operator=(const SectionManager&) = delete;

  // Returns the SectionMetadata associated with the SectionId that's in the
  // SchemaTypeId.
  //
  // Returns:
  //   pointer to SectionMetadata on success
  //   INVALID_ARGUMENT if schema type id or section is invalid
  libtextclassifier3::StatusOr<const SectionMetadata*> GetSectionMetadata(
      SchemaTypeId schema_type_id, SectionId section_id) const;

  // Extracts all sections of different types from the given document and group
  // them by type.
  // - Sections are sorted by section id in ascending order.
  // - Section ids start from 0.
  // - Sections with empty content won't be returned.
  //
  // Returns:
  //   A SectionGroup instance on success
  //   NOT_FOUND if the type config name of document is not present in
  //     schema_type_mapper_
  libtextclassifier3::StatusOr<SectionGroup> ExtractSections(
      const DocumentProto& document) const;

  // Returns:
  //   - On success, the section metadatas for the specified type
  //   - NOT_FOUND if the type config name is not present in schema_type_mapper_
  libtextclassifier3::StatusOr<const std::vector<SectionMetadata>*>
  GetMetadataList(const std::string& type_config_name) const;

 private:
  explicit SectionManager(
      const KeyMapper<SchemaTypeId>& schema_type_mapper,
      std::vector<std::vector<SectionMetadata>>&& section_metadata_cache)
      : schema_type_mapper_(schema_type_mapper),
        section_metadata_cache_(std::move(section_metadata_cache)) {}

  // Maps schema types to a densely-assigned unique id.
  const KeyMapper<SchemaTypeId>& schema_type_mapper_;  // Does not own

  // The index of section_metadata_cache_ corresponds to a schema type's
  // SchemaTypeId. At that SchemaTypeId index, we store an inner vector. The
  // inner vector's index corresponds to a section's SectionId. At the SectionId
  // index, we store the SectionMetadata of that section.
  //
  // For example, pretend "email" had a SchemaTypeId of 0 and it had a section
  // called "subject" with a SectionId of 1. Then there would exist a vector
  // that holds the "subject" property's SectionMetadata at index 1. This vector
  // would be stored at index 0 of the section_metadata_cache_ vector.
  const std::vector<std::vector<SectionMetadata>> section_metadata_cache_;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_SCHEMA_SECTION_MANAGER_H_
