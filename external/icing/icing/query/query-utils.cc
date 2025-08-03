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

#include "icing/query/query-utils.h"

#include <string_view>
#include <vector>

namespace icing {
namespace lib {

DocHitInfoIteratorFilter::Options GetFilterOptions(
    const SearchSpecProto& search_spec) {
  DocHitInfoIteratorFilter::Options options;

  if (search_spec.namespace_filters_size() > 0) {
    options.namespaces =
        std::vector<std::string_view>(search_spec.namespace_filters().begin(),
                                      search_spec.namespace_filters().end());
  }

  if (search_spec.schema_type_filters_size() > 0) {
    options.schema_types =
        std::vector<std::string_view>(search_spec.schema_type_filters().begin(),
                                      search_spec.schema_type_filters().end());
  }
  return options;
}

}  // namespace lib
}  // namespace icing
