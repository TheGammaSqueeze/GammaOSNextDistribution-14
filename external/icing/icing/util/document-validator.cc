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

#include "icing/util/document-validator.h"

#include <cstdint>
#include <unordered_set>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/absl_ports/canonical_errors.h"
#include "icing/proto/document.pb.h"
#include "icing/proto/schema.pb.h"
#include "icing/schema/schema-util.h"
#include "icing/util/status-macros.h"

namespace icing {
namespace lib {

using PropertyConfigMap =
    std::unordered_map<std::string_view, const PropertyConfigProto*>;

DocumentValidator::DocumentValidator(const SchemaStore* schema_store)
    : schema_store_(schema_store) {}

libtextclassifier3::Status DocumentValidator::Validate(
    const DocumentProto& document, int depth) {
  if (document.namespace_().empty()) {
    return absl_ports::InvalidArgumentError("Field 'namespace' is empty.");
  }

  // Only require a non-empty uri on top-level documents.
  if (depth == 0 && document.uri().empty()) {
    return absl_ports::InvalidArgumentError("Field 'uri' is empty.");
  }

  if (document.schema().empty()) {
    return absl_ports::InvalidArgumentError(
        absl_ports::StrCat("Field 'schema' is empty for key: (",
                           document.namespace_(), ", ", document.uri(), ")."));
  }

  if (document.score() < 0) {
    return absl_ports::InvalidArgumentError("Field 'score' is negative.");
  }

  if (document.creation_timestamp_ms() < 0) {
    return absl_ports::InvalidArgumentError(
        "Field 'creation_timestamp_ms' is negative.");
  }

  if (document.ttl_ms() < 0) {
    return absl_ports::InvalidArgumentError("Field 'ttl_ms' is negative.");
  }

  // TODO(b/144458732): Implement a more robust version of
  // ICING_ASSIGN_OR_RETURN that can support error logging.
  auto type_config_or = schema_store_->GetSchemaTypeConfig(document.schema());
  if (!type_config_or.ok()) {
    ICING_LOG(ERROR) << type_config_or.status().error_message()
                     << "Error while validating document ("
                     << document.namespace_() << ", " << document.uri() << ")";
    return type_config_or.status();
  }
  const SchemaTypeConfigProto* type_config =
      std::move(type_config_or).ValueOrDie();

  int32_t num_required_properties_actual = 0;
  SchemaUtil::ParsedPropertyConfigs parsed_property_configs =
      SchemaUtil::ParsePropertyConfigs(*type_config);
  std::unordered_set<std::string_view> unique_properties;

  for (const PropertyProto& property : document.properties()) {
    if (property.name().empty()) {
      return absl_ports::InvalidArgumentError(absl_ports::StrCat(
          "Field 'name' is empty in PropertyProto for key: (",
          document.namespace_(), ", ", document.uri(), ")."));
    }

    if (!unique_properties.insert(property.name()).second) {
      // Failed to insert because of duplicate property name
      return absl_ports::AlreadyExistsError(absl_ports::StrCat(
          "Property name '", property.name(), "' already exists for key: (",
          document.namespace_(), ", ", document.uri(), ")."));
    }

    const auto& property_iter =
        parsed_property_configs.property_config_map.find(property.name());
    if (property_iter == parsed_property_configs.property_config_map.end()) {
      return absl_ports::NotFoundError(absl_ports::StrCat(
          "Property config '", property.name(), "' not found for key: (",
          document.namespace_(), ", ", document.uri(),
          ") of type: ", document.schema(), "."));
    }
    const PropertyConfigProto& property_config = *property_iter->second;

    // Get the property value size according to data type.
    int value_size = 0;
    if (property_config.data_type() == PropertyConfigProto::DataType::STRING) {
      value_size = property.string_values_size();
    } else if (property_config.data_type() ==
               PropertyConfigProto::DataType::INT64) {
      value_size = property.int64_values_size();
    } else if (property_config.data_type() ==
               PropertyConfigProto::DataType::DOUBLE) {
      value_size = property.double_values_size();
    } else if (property_config.data_type() ==
               PropertyConfigProto::DataType::BOOLEAN) {
      value_size = property.boolean_values_size();
    } else if (property_config.data_type() ==
               PropertyConfigProto::DataType::BYTES) {
      value_size = property.bytes_values_size();
    } else if (property_config.data_type() ==
               PropertyConfigProto::DataType::DOCUMENT) {
      value_size = property.document_values_size();
    }

    if (property_config.cardinality() ==
        PropertyConfigProto::Cardinality::OPTIONAL) {
      if (value_size != 0 && value_size != 1) {
        return absl_ports::InvalidArgumentError(IcingStringUtil::StringPrintf(
            "Property '%s' is optional but %d elements are "
            "found for key: (%s, %s).",
            property.name().c_str(), value_size, document.namespace_().c_str(),
            document.uri().c_str()));
      }
    } else if (property_config.cardinality() ==
               PropertyConfigProto::Cardinality::REQUIRED) {
      if (value_size != 1) {
        return absl_ports::InvalidArgumentError(IcingStringUtil::StringPrintf(
            "Property '%s' with only 1 value is required but "
            "%d elements are found for key: (%s, %s).",
            property.name().c_str(), value_size, document.namespace_().c_str(),
            document.uri().c_str()));
      }
      num_required_properties_actual++;
    }

    // We put the validation for nested DocumentProto at last separately
    // because it takes longer time to run. If any of the previous validations
    // fail, we don't need to validate the extra documents.
    if (property_config.data_type() ==
        PropertyConfigProto::DataType::DOCUMENT) {
      ICING_ASSIGN_OR_RETURN(
          const std::unordered_set<SchemaTypeId>* nested_type_ids_expected,
          schema_store_->GetSchemaTypeIdsWithChildren(
              property_config.schema_type()));
      for (const DocumentProto& nested_document : property.document_values()) {
        libtextclassifier3::StatusOr<SchemaTypeId> nested_document_type_id_or =
            schema_store_->GetSchemaTypeId(nested_document.schema());
        if (!nested_document_type_id_or.ok() ||
            nested_type_ids_expected->count(
                nested_document_type_id_or.ValueOrDie()) == 0) {
          return absl_ports::InvalidArgumentError(absl_ports::StrCat(
              "Property '", property.name(), "' should be type or subtype of '",
              property_config.schema_type(), "' but actual value has type '",
              nested_document.schema(), "' for key: (", document.namespace_(),
              ", ", document.uri(), ")."));
        }
        ICING_RETURN_IF_ERROR(Validate(nested_document, depth + 1));
      }
    }
  }
  if (num_required_properties_actual <
      parsed_property_configs.required_properties.size()) {
    return absl_ports::InvalidArgumentError(
        absl_ports::StrCat("One or more required fields missing for key: (",
                           document.namespace_(), ", ", document.uri(), ")."));
  }
  return libtextclassifier3::Status::OK;
}

}  // namespace lib
}  // namespace icing
