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

#ifndef ICING_MONKEY_TEST_MONKEY_TEST_GENERATORS_H_
#define ICING_MONKEY_TEST_MONKEY_TEST_GENERATORS_H_

#include <cstdint>
#include <random>
#include <string>
#include <string_view>
#include <unordered_map>
#include <unordered_set>
#include <vector>

#include "icing/monkey_test/monkey-test-common-words.h"
#include "icing/monkey_test/monkey-test-util.h"
#include "icing/monkey_test/monkey-tokenized-document.h"
#include "icing/proto/schema.pb.h"
#include "icing/proto/term.pb.h"
#include "icing/util/clock.h"

namespace icing {
namespace lib {

// A random schema generator used for monkey testing.
class MonkeySchemaGenerator {
 public:
  struct UpdateSchemaResult {
    SchemaProto schema;
    bool is_invalid_schema;
    std::unordered_set<std::string> schema_types_deleted;
    std::unordered_set<std::string> schema_types_incompatible;
    std::unordered_set<std::string> schema_types_index_incompatible;
  };

  explicit MonkeySchemaGenerator(
      MonkeyTestRandomEngine* random,
      const IcingMonkeyTestRunnerConfiguration* config)
      : random_(random), config_(config) {}

  SchemaProto GenerateSchema();

  UpdateSchemaResult UpdateSchema(const SchemaProto& schema);

 private:
  PropertyConfigProto GenerateProperty(
      const SchemaTypeConfigProto& type_config,
      PropertyConfigProto::Cardinality::Code cardinality,
      TermMatchType::Code term_match_type);

  void UpdateProperty(const SchemaTypeConfigProto& type_config,
                      PropertyConfigProto& property,
                      UpdateSchemaResult& result);

  SchemaTypeConfigProto GenerateType();

  void UpdateType(SchemaTypeConfigProto& type_config,
                  UpdateSchemaResult& result);

  int num_types_generated_ = 0;
  // A map from type name to the number of properties generated in the
  // corresponding types.
  std::unordered_map<std::string, int> num_properties_generated_;

  MonkeyTestRandomEngine* random_;                    // Does not own.
  const IcingMonkeyTestRunnerConfiguration* config_;  // Does not own.
};

// A random document generator used for monkey testing.
// When num_uris is 0, all documents generated get different URIs. Otherwise,
// URIs will be randomly picked from a set with num_uris elements.
// Same for num_namespaces.
class MonkeyDocumentGenerator {
 public:
  explicit MonkeyDocumentGenerator(
      MonkeyTestRandomEngine* random, const SchemaProto* schema,
      const IcingMonkeyTestRunnerConfiguration* config)
      : random_(random), schema_(schema), config_(config) {}

  const SchemaTypeConfigProto& GetType() const {
    std::uniform_int_distribution<> dist(0, schema_->types_size() - 1);
    return schema_->types(dist(*random_));
  }

  std::string_view GetToken() const {
    // TODO: Instead of randomly picking tokens from the language set
    // kCommonWords, we can make some words more common than others to simulate
    // term frequencies in the real world. This can help us get extremely large
    // posting lists.
    std::uniform_int_distribution<> dist(0, kCommonWords.size() - 1);
    return kCommonWords[dist(*random_)];
  }

  std::string GetNamespace() const;

  std::string GetUri() const;

  int GetNumTokens() const;

  std::vector<std::string> GetPropertyContent() const;

  MonkeyTokenizedDocument GenerateDocument();

 private:
  MonkeyTestRandomEngine* random_;                    // Does not own.
  const SchemaProto* schema_;                         // Does not own.
  const IcingMonkeyTestRunnerConfiguration* config_;  // Does not own.

  uint32_t num_docs_generated_ = 0;
  Clock clock_;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_MONKEY_TEST_MONKEY_TEST_GENERATORS_H_
