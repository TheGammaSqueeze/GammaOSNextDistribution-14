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

#ifndef ICING_TESTING_DOCUMENT_GENERATOR_H_
#define ICING_TESTING_DOCUMENT_GENERATOR_H_

#include <random>
#include <string>
#include <vector>

#include "icing/document-builder.h"
#include "icing/proto/document.pb.h"
#include "icing/proto/schema.pb.h"

namespace icing {
namespace lib {

class EvenDistributionNamespaceSelector {
 public:
  explicit EvenDistributionNamespaceSelector(
      const std::vector<std::string>& namespaces)
      : namespaces_(&namespaces), num_invocations_(0) {}
  const std::string& operator()() {
    return namespaces_->at(num_invocations_++ % namespaces_->size());
  }

 private:
  const std::vector<std::string>* namespaces_;
  int num_invocations_;
};

class EvenDistributionTypeSelector {
 public:
  explicit EvenDistributionTypeSelector(const SchemaProto& schema)
      : schema_(&schema), num_invocations_(0) {}
  const SchemaTypeConfigProto& operator()() {
    return schema_->types(num_invocations_++ % schema_->types_size());
  }

 private:
  const SchemaProto* schema_;
  int num_invocations_;
};

template <typename Rand>
class UniformDistributionLanguageTokenGenerator {
 public:
  explicit UniformDistributionLanguageTokenGenerator(
      const std::vector<std::string>& language, Rand* r)
      : language_(&language),
        rand_(r),
        dist_(0, language.size() - 1),
        num_invocations_(0) {}
  const std::string& operator()() { return language_->at(dist_(*rand_)); }

 private:
  const std::vector<std::string>* language_;
  Rand* rand_;
  std::uniform_int_distribution<> dist_;
  int num_invocations_;
};

template <typename NamespaceSelector, typename TypeSelector,
          typename TokenGenerator>
class DocumentGenerator {
 public:
  explicit DocumentGenerator(NamespaceSelector* namespaces,
                             TypeSelector* schema_types, TokenGenerator* tokens,
                             int doc_content_size)
      : namespaces_(namespaces),
        schema_types_(schema_types),
        tokens_(tokens),
        doc_content_size_(doc_content_size),
        num_docs_generated_(0) {}

  DocumentProto generateDoc() {
    const SchemaTypeConfigProto& type_config = (*schema_types_)();
    const std::string& name_space = (*namespaces_)();
    DocumentBuilder doc_builder = DocumentBuilder()
                                      .SetNamespace(name_space)
                                      .SetSchema(type_config.schema_type())
                                      .SetUri(GetUri());
    // Reserve room to add a token for the namespace in the first section. This
    // ensures that each document will contain at least one token that will be
    // stable across all runs.
    std::string starting_content = name_space + " ";
    // Distribute content evenly between all properties, but add a token with
    // the namespace to the first property.
    int prop_content_size = (doc_content_size_ - starting_content.length()) /
                            type_config.properties_size();
    for (const PropertyConfigProto& prop : type_config.properties()) {
      doc_builder.AddStringProperty(
          prop.property_name(),
          starting_content + GetPropertyContent(prop_content_size, name_space));
      // We've added the namespace token now. No need for more starting_content.
      starting_content.clear();
    }
    ++num_docs_generated_;
    return doc_builder.Build();
  }

 private:
  std::string GetUri() { return std::to_string(num_docs_generated_); }
  std::string GetPropertyContent(int content_size,
                                 const std::string& name_space) {
    std::string content;
    while (content.size() < content_size) {
      content += " " + (*tokens_)();
    }
    return content;
  }

  NamespaceSelector* namespaces_;
  TypeSelector* schema_types_;
  TokenGenerator* tokens_;
  int doc_content_size_;
  int num_docs_generated_;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_TESTING_DOCUMENT_GENERATOR_H_
