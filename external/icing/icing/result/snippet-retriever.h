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

#ifndef ICING_SNIPPET_RETRIEVER_H_
#define ICING_SNIPPET_RETRIEVER_H_

#include <memory>

#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/proto/document.pb.h"
#include "icing/proto/search.pb.h"
#include "icing/proto/term.pb.h"
#include "icing/query/query-terms.h"
#include "icing/schema/schema-store.h"
#include "icing/schema/section.h"
#include "icing/tokenization/language-segmenter.h"
#include "icing/transform/normalizer.h"

namespace icing {
namespace lib {

// This class provides functions to retrieve snippets from documents. Snippets
// are retrieved anywhere that content in the document matches query_terms
// according to match_type. The behavior of snippet population is determined by
// the SnippetSpecProto.
//
// This class does not take ownership of any of the provided pointers. The only
// constraint for the lifecycle of this class is that it must be shorter than
// that of the provided pointers.
class SnippetRetriever {
 public:
  // Factory function to create a SnippetRetriever which does not take ownership
  // of any input components, and all pointers must refer to valid objects that
  // outlive the created SnippetRetriever instance.
  //
  // Returns:
  //   A SnippetRetriever on success
  //   FAILED_PRECONDITION on any null pointer input
  static libtextclassifier3::StatusOr<std::unique_ptr<SnippetRetriever>> Create(
      const SchemaStore* schema_store,
      const LanguageSegmenter* language_segmenter,
      const Normalizer* normalizer);

  // Retrieve the snippet information for content in document. terms in
  // query_terms are matched to content in document according to match_type.
  // Only sections identified in section_id_mask are considered.
  //
  // Returns an empty SnippetProto if no snippets were found.
  SnippetProto RetrieveSnippet(
      const SectionRestrictQueryTermsMap& query_terms,
      TermMatchType::Code match_type,
      const ResultSpecProto::SnippetSpecProto& snippet_spec,
      const DocumentProto& document, SectionIdMask section_id_mask) const;

 private:
  explicit SnippetRetriever(const SchemaStore* schema_store,
                            const LanguageSegmenter* language_segmenter,
                            const Normalizer* normalizer)
      : schema_store_(*schema_store),
        language_segmenter_(*language_segmenter),
        normalizer_(*normalizer) {}

  const SchemaStore& schema_store_;
  const LanguageSegmenter& language_segmenter_;
  const Normalizer& normalizer_;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_SNIPPET_RETRIEVER_H_
