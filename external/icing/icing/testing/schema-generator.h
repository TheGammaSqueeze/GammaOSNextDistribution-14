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

#ifndef ICING_TESTING_SCHEMA_GENERATOR_H_
#define ICING_TESTING_SCHEMA_GENERATOR_H_

#include <random>
#include <string>

#include "icing/proto/schema.pb.h"
#include "icing/proto/term.pb.h"

namespace icing {
namespace lib {

class ExactStringPropertyGenerator {
 public:
  PropertyConfigProto operator()(std::string_view name) const {
    PropertyConfigProto prop;
    prop.set_property_name(name.data(), name.length());
    prop.set_data_type(PropertyConfigProto::DataType::STRING);
    prop.set_cardinality(PropertyConfigProto::Cardinality::OPTIONAL);
    StringIndexingConfig* string_indexing_config =
        prop.mutable_string_indexing_config();
    string_indexing_config->set_term_match_type(TermMatchType::EXACT_ONLY);
    string_indexing_config->set_tokenizer_type(
        StringIndexingConfig::TokenizerType::PLAIN);
    return prop;
  }
};

// Schema generator with random number of properties
template <typename Rand, typename PropertyGenerator>
class RandomSchemaGenerator {
 public:
  explicit RandomSchemaGenerator(Rand* rand, PropertyGenerator* prop_generator)
      : rand_(rand), prop_generator_(prop_generator) {}

  SchemaProto GenerateSchema(int num_types, int max_num_properties) {
    SchemaProto schema;
    std::uniform_int_distribution<> dist(1, max_num_properties);
    while (--num_types >= 0) {
      int num_properties = dist(*rand_);
      SetType(schema.add_types(), "Type" + std::to_string(num_types),
              num_properties);
    }
    return schema;
  }

 private:
  void SetType(SchemaTypeConfigProto* type_config, std::string_view name,
               int num_properties) const {
    type_config->set_schema_type(name.data(), name.length());
    while (--num_properties >= 0) {
      std::string prop_name = "Prop" + std::to_string(num_properties);
      (*type_config->add_properties()) = (*prop_generator_)(prop_name);
    }
  }

  Rand* rand_;
  PropertyGenerator* prop_generator_;
};

// Schema generator with number of properties specified by the caller
template <typename PropertyGenerator>
class SchemaGenerator {
 public:
  explicit SchemaGenerator(int num_properties,
                           PropertyGenerator* prop_generator)
      : num_properties_(num_properties), prop_generator_(prop_generator) {}

  SchemaProto GenerateSchema(int num_types) {
    SchemaProto schema;
    while (--num_types >= 0) {
      SetType(schema.add_types(), "Type" + std::to_string(num_types),
              num_properties_);
    }
    return schema;
  }

 private:
  void SetType(SchemaTypeConfigProto* type_config, std::string_view name,
               int num_properties) const {
    type_config->set_schema_type(name.data(), name.length());
    while (--num_properties >= 0) {
      std::string prop_name = "Prop" + std::to_string(num_properties);
      (*type_config->add_properties()) = (*prop_generator_)(prop_name);
    }
  }

  int num_properties_;
  PropertyGenerator* prop_generator_;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_TESTING_SCHEMA_GENERATOR_H_
