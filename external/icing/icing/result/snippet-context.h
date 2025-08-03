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

#ifndef ICING_RESULT_SNIPPET_CONTEXT_H_
#define ICING_RESULT_SNIPPET_CONTEXT_H_

#include "icing/proto/search.pb.h"
#include "icing/proto/term.pb.h"
#include "icing/query/query-terms.h"

namespace icing {
namespace lib {

// Stores data needed for snippeting. With SnippetContext we can fetch snippets
// for queries with multiple pages.
struct SnippetContext {
  explicit SnippetContext(SectionRestrictQueryTermsMap query_terms_in,
                          ResultSpecProto::SnippetSpecProto snippet_spec_in,
                          TermMatchType::Code match_type_in)
      : query_terms(std::move(query_terms_in)),
        snippet_spec(std::move(snippet_spec_in)),
        match_type(match_type_in) {}

  // Query terms that are used to find snippets
  SectionRestrictQueryTermsMap query_terms;

  // Spec that defines some quantities of snippeting
  ResultSpecProto::SnippetSpecProto snippet_spec;

  // Defines how we match each term
  TermMatchType::Code match_type;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_RESULT_SNIPPET_CONTEXT_H_
