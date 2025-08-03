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

#include "icing/index/iterator/section-restrict-data.h"

#include <set>
#include <string>
#include <unordered_map>
#include <utility>

#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/schema/schema-store.h"
#include "icing/schema/section.h"

namespace icing {
namespace lib {

SectionIdMask SectionRestrictData::GenerateSectionMask(
    const std::string& schema_type,
    const std::set<std::string>& target_sections) const {
  SectionIdMask section_mask = kSectionIdMaskNone;
  auto section_metadata_list = schema_store_.GetSectionMetadata(schema_type);
  if (!section_metadata_list.ok()) {
    // The current schema doesn't have section metadata.
    return kSectionIdMaskNone;
  }
  for (const SectionMetadata& section_metadata :
       *section_metadata_list.ValueOrDie()) {
    if (target_sections.find(section_metadata.path) != target_sections.end()) {
      section_mask |= UINT64_C(1) << section_metadata.id;
    }
  }
  return section_mask;
}

SectionIdMask SectionRestrictData::ComputeAllowedSectionsMask(
    const std::string& schema_type) {
  if (const auto type_property_mask_itr =
          type_property_masks_.find(schema_type);
      type_property_mask_itr != type_property_masks_.end()) {
    return type_property_mask_itr->second;
  }

  // Section id mask of schema_type is never calculated before, so
  // calculate it here and put it into type_property_masks_.
  // - If type property filters of schema_type or wildcard (*) are
  //   specified, then create a mask according to the filters.
  // - Otherwise, create a mask to match all properties.
  SectionIdMask new_section_id_mask = kSectionIdMaskAll;
  if (const auto itr = type_property_filters_.find(schema_type);
      itr != type_property_filters_.end()) {
    // Property filters defined for given schema type
    new_section_id_mask = GenerateSectionMask(schema_type, itr->second);
  } else if (const auto wildcard_itr = type_property_filters_.find(
                 std::string(SchemaStore::kSchemaTypeWildcard));
             wildcard_itr != type_property_filters_.end()) {
    // Property filters defined for wildcard entry
    new_section_id_mask =
        GenerateSectionMask(schema_type, wildcard_itr->second);
  } else {
    // Do not cache the section mask if no property filters apply to this schema
    // type to avoid taking up unnecessary space.
    return kSectionIdMaskAll;
  }

  type_property_masks_[schema_type] = new_section_id_mask;
  return new_section_id_mask;
}

}  // namespace lib
}  // namespace icing
