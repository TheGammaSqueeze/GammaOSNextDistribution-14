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

#ifndef ICING_RESULT_PAGE_RESULT_H_
#define ICING_RESULT_PAGE_RESULT_H_

#include <vector>

#include "icing/proto/search.pb.h"

namespace icing {
namespace lib {

// Contains information of the search result of one page.
struct PageResult {
  PageResult(std::vector<SearchResultProto::ResultProto> results_in,
             int num_results_with_snippets_in, int requested_page_size_in)
      : results(std::move(results_in)),
        num_results_with_snippets(num_results_with_snippets_in),
        requested_page_size(requested_page_size_in) {}

  // Results of one page
  std::vector<SearchResultProto::ResultProto> results;

  // Number of results with snippets.
  int num_results_with_snippets;

  // The page size for this query. This should always be >= results.size().
  int requested_page_size;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_RESULT_PAGE_RESULT_H_
