// Copyright (C) 2020 Google LLC
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

#ifndef ICING_STORE_TOKENIZED_DOCUMENT_H_
#define ICING_STORE_TOKENIZED_DOCUMENT_H_

#include <cstdint>
#include <string>
#include <vector>

#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/proto/document.pb.h"
#include "icing/schema/joinable-property.h"
#include "icing/schema/schema-store.h"
#include "icing/schema/section.h"
#include "icing/tokenization/language-segmenter.h"

namespace icing {
namespace lib {

struct TokenizedSection {
  SectionMetadata metadata;
  std::vector<std::string_view> token_sequence;

  TokenizedSection(SectionMetadata&& metadata_in,
                   std::vector<std::string_view>&& token_sequence_in)
      : metadata(std::move(metadata_in)),
        token_sequence(std::move(token_sequence_in)) {}
};

class TokenizedDocument {
 public:
  static libtextclassifier3::StatusOr<TokenizedDocument> Create(
      const SchemaStore* schema_store,
      const LanguageSegmenter* language_segmenter, DocumentProto document);

  const DocumentProto& document() const { return document_; }

  int32_t num_string_tokens() const {
    int32_t num_string_tokens = 0;
    for (const TokenizedSection& section : tokenized_string_sections_) {
      num_string_tokens += section.token_sequence.size();
    }
    return num_string_tokens;
  }

  const std::vector<TokenizedSection>& tokenized_string_sections() const {
    return tokenized_string_sections_;
  }

  const std::vector<Section<int64_t>>& integer_sections() const {
    return integer_sections_;
  }

  const std::vector<JoinableProperty<std::string_view>>&
  qualified_id_join_properties() const {
    return joinable_property_group_.qualified_id_properties;
  }

 private:
  // Use TokenizedDocument::Create() to instantiate.
  explicit TokenizedDocument(
      DocumentProto&& document,
      std::vector<TokenizedSection>&& tokenized_string_sections,
      std::vector<Section<int64_t>>&& integer_sections,
      JoinablePropertyGroup&& joinable_property_group)
      : document_(std::move(document)),
        tokenized_string_sections_(std::move(tokenized_string_sections)),
        integer_sections_(std::move(integer_sections)),
        joinable_property_group_(std::move(joinable_property_group)) {}

  DocumentProto document_;
  std::vector<TokenizedSection> tokenized_string_sections_;
  std::vector<Section<int64_t>> integer_sections_;
  JoinablePropertyGroup joinable_property_group_;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_STORE_TOKENIZED_DOCUMENT_H_
