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

#ifndef ICING_QUERY_QUERY_RESULTS_H_
#define ICING_QUERY_QUERY_RESULTS_H_

#include <memory>
#include <unordered_set>

#include "icing/index/iterator/doc-hit-info-iterator.h"
#include "icing/query/query-terms.h"
#include "icing/query/query-features.h"

namespace icing {
namespace lib {

struct QueryResults {
  std::unique_ptr<DocHitInfoIterator> root_iterator;
  // A map from section names to sets of terms restricted to those sections.
  // Query terms that are not restricted are found at the entry with key "".
  SectionRestrictQueryTermsMap query_terms;
  // Hit iterators for the text terms in the query. These query_term_iterators
  // are completely separate from the iterators that make the iterator tree
  // beginning with root_iterator.
  // This will only be populated when ranking_strategy == RELEVANCE_SCORE.
  QueryTermIteratorsMap query_term_iterators;
  // Features that are invoked during query execution.
  // The list of possible features is defined in query_features.h.
  std::unordered_set<Feature> features_in_use;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_QUERY_QUERY_RESULTS_H_
