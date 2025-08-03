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

#include "icing/util/tokenized-document.h"

#include <string>
#include <string_view>
#include <vector>

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/proto/document.pb.h"
#include "icing/schema/joinable-property.h"
#include "icing/schema/schema-store.h"
#include "icing/schema/section.h"
#include "icing/tokenization/language-segmenter.h"
#include "icing/tokenization/tokenizer-factory.h"
#include "icing/tokenization/tokenizer.h"
#include "icing/util/document-validator.h"
#include "icing/util/status-macros.h"

namespace icing {
namespace lib {

namespace {

libtextclassifier3::StatusOr<std::vector<TokenizedSection>> Tokenize(
    const SchemaStore* schema_store,
    const LanguageSegmenter* language_segmenter,
    const std::vector<Section<std::string_view>>& string_sections) {
  std::vector<TokenizedSection> tokenized_string_sections;
  for (const Section<std::string_view>& section : string_sections) {
    ICING_ASSIGN_OR_RETURN(std::unique_ptr<Tokenizer> tokenizer,
                           tokenizer_factory::CreateIndexingTokenizer(
                               section.metadata.tokenizer, language_segmenter));
    std::vector<std::string_view> token_sequence;
    for (std::string_view subcontent : section.content) {
      ICING_ASSIGN_OR_RETURN(std::unique_ptr<Tokenizer::Iterator> itr,
                             tokenizer->Tokenize(subcontent));
      while (itr->Advance()) {
        std::vector<Token> batch_tokens = itr->GetTokens();
        for (const Token& token : batch_tokens) {
          token_sequence.push_back(token.text);
        }
      }
    }
    tokenized_string_sections.emplace_back(SectionMetadata(section.metadata),
                                           std::move(token_sequence));
  }

  return tokenized_string_sections;
}

}  // namespace

/* static */ libtextclassifier3::StatusOr<TokenizedDocument>
TokenizedDocument::Create(const SchemaStore* schema_store,
                          const LanguageSegmenter* language_segmenter,
                          DocumentProto document) {
  DocumentValidator validator(schema_store);
  ICING_RETURN_IF_ERROR(validator.Validate(document));

  ICING_ASSIGN_OR_RETURN(SectionGroup section_group,
                         schema_store->ExtractSections(document));

  ICING_ASSIGN_OR_RETURN(JoinablePropertyGroup joinable_property_group,
                         schema_store->ExtractJoinableProperties(document));

  // Tokenize string sections
  ICING_ASSIGN_OR_RETURN(
      std::vector<TokenizedSection> tokenized_string_sections,
      Tokenize(schema_store, language_segmenter,
               section_group.string_sections));

  return TokenizedDocument(std::move(document),
                           std::move(tokenized_string_sections),
                           std::move(section_group.integer_sections),
                           std::move(joinable_property_group));
}

}  // namespace lib
}  // namespace icing
