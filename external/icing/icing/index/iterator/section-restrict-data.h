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

#ifndef ICING_INDEX_ITERATOR_SECTION_RESTRICT_DATA_H_
#define ICING_INDEX_ITERATOR_SECTION_RESTRICT_DATA_H_

#include <cstdint>
#include <set>
#include <string>
#include <unordered_map>
#include <utility>

#include "icing/schema/schema-store.h"
#include "icing/schema/section.h"
#include "icing/store/document-store.h"

namespace icing {
namespace lib {

class SectionRestrictData {
 public:
  // Does not take any ownership, and all pointers must refer to valid objects
  // that outlive the one constructed.
  SectionRestrictData(const DocumentStore* document_store,
                      const SchemaStore* schema_store, int64_t current_time_ms,
                      std::unordered_map<std::string, std::set<std::string>>
                          type_property_filters)
      : document_store_(*document_store),
        schema_store_(*schema_store),
        current_time_ms_(current_time_ms),
        type_property_filters_(std::move(type_property_filters)) {}

  // Calculates the section mask of allowed sections(determined by the
  // property filters map) for the given schema type and caches the same for any
  // future calls.
  //
  // Returns:
  //  - If type_property_filters_ has an entry for the given schema type or
  //    wildcard(*), return a bitwise or of section IDs in the schema type
  //    that that are also present in the relevant filter list.
  //  - Otherwise, return kSectionIdMaskAll.
  SectionIdMask ComputeAllowedSectionsMask(const std::string& schema_type);

  const DocumentStore& document_store() const { return document_store_; }

  const SchemaStore& schema_store() const { return schema_store_; }

  int64_t current_time_ms() const { return current_time_ms_; }

  const std::unordered_map<std::string, std::set<std::string>>&
  type_property_filters() const {
    return type_property_filters_;
  }

 private:
  const DocumentStore& document_store_;
  const SchemaStore& schema_store_;
  int64_t current_time_ms_;

  // Map of property filters per schema type. Supports wildcard(*) for schema
  // type that will apply to all schema types that are not specifically
  // specified in the mapping otherwise.
  std::unordered_map<std::string, std::set<std::string>> type_property_filters_;
  // Mapping of schema type to the section mask of allowed sections for that
  // schema type. This section mask is lazily calculated based on the
  // specified property filters and cached for any future use.
  std::unordered_map<std::string, SectionIdMask> type_property_masks_;

  // Generates a section mask for the given schema type and the target
  // sections.
  //
  // Returns:
  //  - A bitwise or of section IDs in the schema_type that that are also
  //    present in the target_sections list.
  //  - If none of the sections in the schema_type are present in the
  //    target_sections list, return kSectionIdMaskNone.
  // This is done by doing a bitwise or of the target section ids for the
  // given schema type.
  SectionIdMask GenerateSectionMask(
      const std::string& schema_type,
      const std::set<std::string>& target_sections) const;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_INDEX_ITERATOR_SECTION_RESTRICT_DATA_H_
