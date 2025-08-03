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

#ifndef ICING_QUERY_QUERY_UTILS_H_
#define ICING_QUERY_QUERY_UTILS_H_

#include "icing/index/iterator/doc-hit-info-iterator-filter.h"
#include "icing/proto/search.pb.h"

namespace icing {
namespace lib {

DocHitInfoIteratorFilter::Options GetFilterOptions(
    const SearchSpecProto& search_spec);

}  // namespace lib
}  // namespace icing

#endif  // ICING_QUERY_QUERY_UTILS_H_
