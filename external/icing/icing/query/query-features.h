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

#ifndef ICING_QUERY_QUERY_FEATURES_H_
#define ICING_QUERY_QUERY_FEATURES_H_

#include <string_view>
#include <unordered_set>

namespace icing {
namespace lib {

// A feature used in a query.
// All feature values here must be kept in sync with its counterpart in:
// androidx-main/frameworks/support/appsearch/appsearch/src/main/java/androidx/appsearch/app/Features.java
using Feature = std::string_view;

// This feature relates to the use of the numeric comparison operators in the
// advanced query language. Ex. `price < 10`.
constexpr Feature kNumericSearchFeature =
    "NUMERIC_SEARCH";  // Features#NUMERIC_SEARCH

// This feature relates to the use of the STRING terminal in the advanced query
// language. Ex. `"foo?bar"` is treated as a single term - `foo?bar`.
constexpr Feature kVerbatimSearchFeature =
    "VERBATIM_SEARCH";  // Features#VERBATIM_SEARCH

// This feature covers all additions (other than numeric search and verbatim
// search) to the query language to bring it into better alignment with the list
// filters spec.
// This includes:
//   - support for function calls
//   - expanding support for negation and property restriction expressions
//   - prefix operator '*'
//   - 'NOT' operator
//   - propertyDefined("url")
constexpr Feature kListFilterQueryLanguageFeature =
    "LIST_FILTER_QUERY_LANGUAGE";  // Features#LIST_FILTER_QUERY_LANGUAGE

// This feature relates to the use of the "hasProperty(property_path)" function.
constexpr Feature kHasPropertyFunctionFeature =
    "HAS_PROPERTY_FUNCTION";  // Features#HAS_PROPERTY_FUNCTION

inline std::unordered_set<Feature> GetQueryFeaturesSet() {
  return {kNumericSearchFeature, kVerbatimSearchFeature,
          kListFilterQueryLanguageFeature, kHasPropertyFunctionFeature};
}

}  // namespace lib
}  // namespace icing

#endif  // ICING_QUERY_QUERY_FEATURES_H_
