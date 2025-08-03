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

#ifndef ICING_SCHEMA_SCHEMA_TYPE_MANAGER_H_
#define ICING_SCHEMA_SCHEMA_TYPE_MANAGER_H_

#include <memory>
#include <string>
#include <unordered_set>
#include <vector>

#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/schema/joinable-property-manager.h"
#include "icing/schema/schema-util.h"
#include "icing/schema/section-manager.h"
#include "icing/store/document-filter-data.h"
#include "icing/store/key-mapper.h"

namespace icing {
namespace lib {

// This class is a wrapper of SectionManager and JoinablePropertyManager.
class SchemaTypeManager {
 public:
  // Schema type ids are continuous, and so we use a vector instead of an
  // unordered map for the mappings.
  using SchemaTypeIdToPropertiesVector =
      std::vector<std::unordered_set<std::string>>;
  // Factory function to create a SchemaTypeManager which does not take
  // ownership of any input components, and all pointers must refer to valid
  // objects that outlive the created SchemaTypeManager instance.
  //
  // Returns:
  //   - A SchemaTypeManager on success
  //   - FAILED_PRECONDITION_ERROR on any null pointer input
  //   - OUT_OF_RANGE_ERROR if # of indexable properties in a single Schema
  //     exceeds the threshold (kTotalNumSections, kTotalNumJoinableProperties)
  //   - INVALID_ARGUMENT_ERROR if type_config_map contains incorrect
  //     information that causes errors (e.g. invalid schema type id, cycle
  //     dependency in nested schema)
  //   - NOT_FOUND_ERROR if any nested schema name is not found in
  //     type_config_map
  static libtextclassifier3::StatusOr<std::unique_ptr<SchemaTypeManager>>
  Create(const SchemaUtil::TypeConfigMap& type_config_map,
         const KeyMapper<SchemaTypeId>* schema_type_mapper);

  const SectionManager& section_manager() const { return *section_manager_; }

  const JoinablePropertyManager& joinable_property_manager() const {
    return *joinable_property_manager_;
  }

 private:
  explicit SchemaTypeManager(
      std::unique_ptr<SectionManager> section_manager,
      std::unique_ptr<JoinablePropertyManager> joinable_property_manager)
      : section_manager_(std::move(section_manager)),
        joinable_property_manager_(std::move(joinable_property_manager)) {}

  std::unique_ptr<SectionManager> section_manager_;

  std::unique_ptr<JoinablePropertyManager> joinable_property_manager_;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_SCHEMA_SCHEMA_TYPE_MANAGER_H_
