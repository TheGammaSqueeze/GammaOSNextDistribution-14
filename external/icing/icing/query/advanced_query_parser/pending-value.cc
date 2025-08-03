// Copyright (C) 2023 Google LLC
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
#include "icing/query/advanced_query_parser/pending-value.h"

#include "icing/absl_ports/canonical_errors.h"

namespace icing {
namespace lib {

libtextclassifier3::Status PendingValue::ParseInt() {
  if (data_type_ == DataType::kLong) {
    return libtextclassifier3::Status::OK;
  } else if (data_type_ != DataType::kText) {
    return absl_ports::InvalidArgumentError("Cannot parse value as LONG");
  }
  if (query_term_.is_prefix_val) {
    return absl_ports::InvalidArgumentError(absl_ports::StrCat(
        "Cannot use prefix operator '*' with numeric value: ",
        query_term_.term));
  }
  char* value_end;
  long_val_ = std::strtoll(query_term_.term.c_str(), &value_end, /*base=*/10);
  if (value_end != query_term_.term.c_str() + query_term_.term.length()) {
    return absl_ports::InvalidArgumentError(absl_ports::StrCat(
        "Unable to parse \"", query_term_.term, "\" as number."));
  }
  data_type_ = DataType::kLong;
  query_term_ = {/*term=*/"", /*raw_term=*/"", /*is_prefix_val=*/false};
  return libtextclassifier3::Status::OK;
}

}  // namespace lib
}  // namespace icing
