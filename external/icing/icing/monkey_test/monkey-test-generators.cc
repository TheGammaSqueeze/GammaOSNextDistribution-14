// Copyright (C) 2022 Google LLC
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

#include "icing/monkey_test/monkey-test-generators.h"

#include <array>
#include <cstdint>
#include <random>
#include <string>
#include <string_view>
#include <unordered_set>
#include <utility>
#include <vector>

#include "icing/absl_ports/str_cat.h"
#include "icing/absl_ports/str_join.h"
#include "icing/document-builder.h"
#include "icing/monkey_test/monkey-test-util.h"
#include "icing/monkey_test/monkey-tokenized-document.h"
#include "icing/proto/schema.pb.h"
#include "icing/proto/term.pb.h"
#include "icing/schema/section.h"

namespace icing {
namespace lib {

namespace {

constexpr std::array<PropertyConfigProto::Cardinality::Code, 3> kCardinalities =
    {PropertyConfigProto::Cardinality::REPEATED,
     PropertyConfigProto::Cardinality::OPTIONAL,
     PropertyConfigProto::Cardinality::REQUIRED};

constexpr std::array<TermMatchType::Code, 3> kTermMatchTypes = {
    TermMatchType::UNKNOWN, TermMatchType::EXACT_ONLY, TermMatchType::PREFIX};

PropertyConfigProto::Cardinality::Code GetRandomCardinality(
    MonkeyTestRandomEngine* random) {
  std::uniform_int_distribution<> dist(0, kCardinalities.size() - 1);
  return kCardinalities[dist(*random)];
}

TermMatchType::Code GetRandomTermMatchType(MonkeyTestRandomEngine* random) {
  std::uniform_int_distribution<> dist(0, kTermMatchTypes.size() - 1);
  return kTermMatchTypes[dist(*random)];
}

// TODO: Update this function when supporting document_indexing_config.
bool IsIndexableProperty(const PropertyConfigProto& property) {
  return property.string_indexing_config().term_match_type() !=
         TermMatchType::UNKNOWN;
}

void SetStringIndexingConfig(PropertyConfigProto& property,
                             TermMatchType::Code term_match_type) {
  if (term_match_type != TermMatchType::UNKNOWN) {
    StringIndexingConfig* string_indexing_config =
        property.mutable_string_indexing_config();
    string_indexing_config->set_term_match_type(term_match_type);
    // TODO: Try to add different TokenizerTypes. VERBATIM, RFC822, and URL are
    // the remaining candidates to consider.
    string_indexing_config->set_tokenizer_type(
        StringIndexingConfig::TokenizerType::PLAIN);
  } else {
    property.clear_string_indexing_config();
  }
}

}  // namespace

SchemaProto MonkeySchemaGenerator::GenerateSchema() {
  SchemaProto schema;
  for (int i = 0; i < config_->num_types; ++i) {
    *schema.add_types() = GenerateType();
  }
  return schema;
}

MonkeySchemaGenerator::UpdateSchemaResult MonkeySchemaGenerator::UpdateSchema(
    const SchemaProto& schema) {
  UpdateSchemaResult result = {std::move(schema)};
  SchemaProto& new_schema = result.schema;

  // Delete up to 2 existing types.
  std::uniform_int_distribution<> num_types_to_delete_dist(0, 2);
  for (int num_types_to_delete = num_types_to_delete_dist(*random_);
       num_types_to_delete >= 0; --num_types_to_delete) {
    if (new_schema.types_size() > 0) {
      std::uniform_int_distribution<> dist(0, new_schema.types_size() - 1);
      int index_to_delete = dist(*random_);
      result.schema_types_deleted.insert(
          new_schema.types(index_to_delete).schema_type());
      new_schema.mutable_types()->SwapElements(index_to_delete,
                                               new_schema.types_size() - 1);
      new_schema.mutable_types()->RemoveLast();
    }
  }

  // Updating about 1/3 of existing types.
  for (int i = 0; i < new_schema.types_size(); ++i) {
    std::uniform_int_distribution<> dist(0, 2);
    if (dist(*random_) == 0) {
      UpdateType(*new_schema.mutable_types(i), result);
    }
  }

  // Add up to 2 new types.
  std::uniform_int_distribution<> num_types_to_add_dist(0, 2);
  for (int num_types_to_add = num_types_to_add_dist(*random_);
       num_types_to_add >= 0; --num_types_to_add) {
    *new_schema.add_types() = GenerateType();
  }

  return result;
}

PropertyConfigProto MonkeySchemaGenerator::GenerateProperty(
    const SchemaTypeConfigProto& type_config,
    PropertyConfigProto::Cardinality::Code cardinality,
    TermMatchType::Code term_match_type) {
  PropertyConfigProto prop;
  prop.set_property_name(
      "MonkeyTestProp" +
      std::to_string(num_properties_generated_[type_config.schema_type()]++));
  // TODO: Perhaps in future iterations we will want to generate more than just
  // string properties.
  prop.set_data_type(PropertyConfigProto::DataType::STRING);
  prop.set_cardinality(cardinality);
  SetStringIndexingConfig(prop, term_match_type);
  return prop;
}

void MonkeySchemaGenerator::UpdateProperty(
    const SchemaTypeConfigProto& type_config, PropertyConfigProto& property,
    UpdateSchemaResult& result) {
  PropertyConfigProto::Cardinality::Code new_cardinality =
      GetRandomCardinality(random_);
  if (new_cardinality != property.cardinality()) {
    // Only do compatible cardinality update for now, otherwise it would be hard
    // to track which documents will be invalid after updating the schema.
    //
    // The following type of updates are not allowed:
    // - optional -> required
    // - repeated -> optional
    // - repeated -> required
    if (property.cardinality() == PropertyConfigProto::Cardinality::OPTIONAL &&
        new_cardinality == PropertyConfigProto::Cardinality::REQUIRED) {
      return;
    }
    if (property.cardinality() == PropertyConfigProto::Cardinality::REPEATED &&
        (new_cardinality == PropertyConfigProto::Cardinality::OPTIONAL ||
         new_cardinality == PropertyConfigProto::Cardinality::REQUIRED)) {
      return;
    }
    property.set_cardinality(new_cardinality);
  }

  if (property.data_type() == PropertyConfigProto::DataType::STRING) {
    TermMatchType::Code new_term_match_type = GetRandomTermMatchType(random_);
    if (new_term_match_type !=
        property.string_indexing_config().term_match_type()) {
      SetStringIndexingConfig(property, new_term_match_type);
      result.schema_types_index_incompatible.insert(type_config.schema_type());
    }
  }
}

SchemaTypeConfigProto MonkeySchemaGenerator::GenerateType() {
  SchemaTypeConfigProto type_config;
  type_config.set_schema_type("MonkeyTestType" +
                              std::to_string(num_types_generated_++));
  std::uniform_int_distribution<> possible_num_properties_dist(
      0, config_->possible_num_properties.size() - 1);
  int total_num_properties =
      config_->possible_num_properties[possible_num_properties_dist(*random_)];

  int num_indexed_properties = 0;
  for (int i = 0; i < total_num_properties; ++i) {
    TermMatchType::Code term_match_type = TermMatchType::UNKNOWN;
    if (num_indexed_properties < kTotalNumSections) {
      term_match_type = GetRandomTermMatchType(random_);
    }
    if (term_match_type != TermMatchType::UNKNOWN) {
      num_indexed_properties += 1;
    }
    (*type_config.add_properties()) = GenerateProperty(
        type_config, GetRandomCardinality(random_), term_match_type);
  }
  return type_config;
}

void MonkeySchemaGenerator::UpdateType(SchemaTypeConfigProto& type_config,
                                       UpdateSchemaResult& result) {
  // Delete up to 4 existing property.
  std::uniform_int_distribution<> num_properties_to_delete_dist(0, 4);
  for (int num_properties_to_delete = num_properties_to_delete_dist(*random_);
       num_properties_to_delete >= 0; --num_properties_to_delete) {
    if (type_config.properties_size() > 0) {
      std::uniform_int_distribution<> dist(0,
                                           type_config.properties_size() - 1);
      int index_to_delete = dist(*random_);
      // Only delete a required property for now, otherwise it would be hard
      // to track which documents will be invalid after updating the schema.
      if (type_config.properties(index_to_delete).cardinality() !=
          PropertyConfigProto::Cardinality::REQUIRED) {
        continue;
      }
      if (IsIndexableProperty(type_config.properties(index_to_delete))) {
        result.schema_types_index_incompatible.insert(
            type_config.schema_type());
      }
      // Removing a property will cause the type to be considered as
      // incompatible.
      result.schema_types_incompatible.insert(type_config.schema_type());

      type_config.mutable_properties()->SwapElements(
          index_to_delete, type_config.properties_size() - 1);
      type_config.mutable_properties()->RemoveLast();
    }
  }

  // Updating about 1/3 of existing properties.
  for (int i = 0; i < type_config.properties_size(); ++i) {
    std::uniform_int_distribution<> dist(0, 2);
    if (dist(*random_) == 0) {
      UpdateProperty(type_config, *type_config.mutable_properties(i), result);
    }
  }

  // Add up to 4 new properties.
  std::uniform_int_distribution<> num_types_to_add_dist(0, 4);
  for (int num_types_to_add = num_types_to_add_dist(*random_);
       num_types_to_add >= 0; --num_types_to_add) {
    PropertyConfigProto::Cardinality::Code new_cardinality =
        GetRandomCardinality(random_);
    // Adding a required property will make all document of this type invalid.
    if (new_cardinality == PropertyConfigProto::Cardinality::REQUIRED) {
      result.schema_types_incompatible.insert(type_config.schema_type());
    }
    PropertyConfigProto new_property = GenerateProperty(
        type_config, new_cardinality, GetRandomTermMatchType(random_));
    if (IsIndexableProperty(new_property)) {
      result.schema_types_index_incompatible.insert(type_config.schema_type());
    }
    (*type_config.add_properties()) = std::move(new_property);
  }

  int num_indexed_properties = 0;
  for (int i = 0; i < type_config.properties_size(); ++i) {
    if (IsIndexableProperty(type_config.properties(i))) {
      ++num_indexed_properties;
    }
  }

  if (num_indexed_properties > kTotalNumSections) {
    result.is_invalid_schema = true;
  }
}

std::string MonkeyDocumentGenerator::GetNamespace() const {
  uint32_t name_space;
  // When num_namespaces is 0, all documents generated get different namespaces.
  // Otherwise, namespaces will be randomly picked from a set with
  // num_namespaces elements.
  if (config_->num_namespaces == 0) {
    name_space = num_docs_generated_;
  } else {
    std::uniform_int_distribution<> dist(0, config_->num_namespaces - 1);
    name_space = dist(*random_);
  }
  return absl_ports::StrCat("namespace", std::to_string(name_space));
}

std::string MonkeyDocumentGenerator::GetUri() const {
  uint32_t uri;
  // When num_uris is 0, all documents generated get different URIs. Otherwise,
  // URIs will be randomly picked from a set with num_uris elements.
  if (config_->num_uris == 0) {
    uri = num_docs_generated_;
  } else {
    std::uniform_int_distribution<> dist(0, config_->num_uris - 1);
    uri = dist(*random_);
  }
  return absl_ports::StrCat("uri", std::to_string(uri));
}

int MonkeyDocumentGenerator::GetNumTokens() const {
  std::uniform_int_distribution<> dist(
      0, config_->possible_num_tokens_.size() - 1);
  int n = config_->possible_num_tokens_[dist(*random_)];
  // Add some noise
  std::uniform_real_distribution<> real_dist(0.5, 1);
  float p = real_dist(*random_);
  return n * p;
}

std::vector<std::string> MonkeyDocumentGenerator::GetPropertyContent() const {
  std::vector<std::string> content;
  int num_tokens = GetNumTokens();
  while (num_tokens) {
    content.push_back(std::string(GetToken()));
    --num_tokens;
  }
  return content;
}

MonkeyTokenizedDocument MonkeyDocumentGenerator::GenerateDocument() {
  MonkeyTokenizedDocument document;
  const SchemaTypeConfigProto& type_config = GetType();
  const std::string& name_space = GetNamespace();
  DocumentBuilder doc_builder =
      DocumentBuilder()
          .SetNamespace(name_space)
          .SetSchema(type_config.schema_type())
          .SetUri(GetUri())
          .SetCreationTimestampMs(clock_.GetSystemTimeMilliseconds());
  for (const PropertyConfigProto& prop : type_config.properties()) {
    std::vector<std::string> prop_content = GetPropertyContent();
    doc_builder.AddStringProperty(prop.property_name(),
                                  absl_ports::StrJoin(prop_content, " "));
    // No matter whether the property is indexable currently, we have to create
    // a section for it since a non-indexable property can become indexable
    // after a schema type change. The in-memory icing will automatically skip
    // sections that are non-indexable at the time of search requests.
    MonkeyTokenizedSection section = {prop.property_name(),
                                      std::move(prop_content)};
    document.tokenized_sections.push_back(std::move(section));
  }
  document.document = doc_builder.Build();
  ++num_docs_generated_;
  return document;
}

}  // namespace lib
}  // namespace icing
