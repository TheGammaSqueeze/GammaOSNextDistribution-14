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

#include "icing/schema/schema-type-manager.h"

#include <memory>
#include <utility>

#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/absl_ports/canonical_errors.h"
#include "icing/schema/joinable-property-manager.h"
#include "icing/schema/property-util.h"
#include "icing/schema/schema-property-iterator.h"
#include "icing/schema/schema-util.h"
#include "icing/schema/section-manager.h"
#include "icing/store/document-filter-data.h"
#include "icing/store/key-mapper.h"
#include "icing/util/status-macros.h"

namespace icing {
namespace lib {

/* static */ libtextclassifier3::StatusOr<std::unique_ptr<SchemaTypeManager>>
SchemaTypeManager::Create(const SchemaUtil::TypeConfigMap& type_config_map,
                          const KeyMapper<SchemaTypeId>* schema_type_mapper) {
  ICING_RETURN_ERROR_IF_NULL(schema_type_mapper);

  SectionManager::Builder section_manager_builder(*schema_type_mapper);
  JoinablePropertyManager::Builder joinable_property_manager_builder(
      *schema_type_mapper);

  for (const auto& [type_config_name, type_config] : type_config_map) {
    ICING_ASSIGN_OR_RETURN(SchemaTypeId schema_type_id,
                           schema_type_mapper->Get(type_config_name));

    // Use iterator to traverse all leaf properties of the schema.
    SchemaPropertyIterator iterator(type_config, type_config_map);
    while (true) {
      libtextclassifier3::Status status = iterator.Advance();
      if (!status.ok()) {
        if (absl_ports::IsOutOfRange(status)) {
          break;
        }
        return status;
      }

      // Process section (indexable property)
      if (iterator.GetCurrentPropertyIndexable()) {
        ICING_RETURN_IF_ERROR(
            section_manager_builder.ProcessSchemaTypePropertyConfig(
                schema_type_id, iterator.GetCurrentPropertyConfig(),
                iterator.GetCurrentPropertyPath()));
      }

      // Process joinable property
      ICING_RETURN_IF_ERROR(
          joinable_property_manager_builder.ProcessSchemaTypePropertyConfig(
              schema_type_id, iterator.GetCurrentPropertyConfig(),
              iterator.GetCurrentPropertyPath()));
    }

    // Process unknown property paths in the indexable_nested_properties_list.
    // These property paths should consume sectionIds but are currently
    // not indexed.
    //
    // SectionId assignment order:
    // - We assign section ids to known (existing) properties first in alphabet
    //   order.
    // - After handling all known properties, we assign section ids to all
    //   unknown (non-existent) properties that are specified in the
    //  indexable_nested_properties_list.
    // - As a result, assignment of the entire section set is not done
    //   alphabetically, but assignment is still deterministic and alphabetical
    //   order is preserved inside the known properties and unknown properties
    //   sets individually.
    for (const auto& property_path :
         iterator.unknown_indexable_nested_property_paths()) {
      PropertyConfigProto unknown_property_config;
      unknown_property_config.set_property_name(std::string(
          property_util::SplitPropertyPathExpr(property_path).back()));
      unknown_property_config.set_data_type(
          PropertyConfigProto::DataType::UNKNOWN);

      ICING_RETURN_IF_ERROR(
          section_manager_builder.ProcessSchemaTypePropertyConfig(
              schema_type_id, unknown_property_config,
              std::string(property_path)));
    }
  }

  return std::unique_ptr<SchemaTypeManager>(new SchemaTypeManager(
      std::move(section_manager_builder).Build(),
      std::move(joinable_property_manager_builder).Build()));
}

}  // namespace lib
}  // namespace icing
