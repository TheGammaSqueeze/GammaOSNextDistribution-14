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
#ifndef ICING_QUERY_ADVANCED_QUERY_PARSER_PARAM_H_
#define ICING_QUERY_ADVANCED_QUERY_PARSER_PARAM_H_

#include "icing/text_classifier/lib3/utils/base/status.h"
#include "icing/absl_ports/canonical_errors.h"
#include "icing/query/advanced_query_parser/pending-value.h"
#include "icing/util/status-macros.h"

namespace icing {
namespace lib {

enum class Cardinality {
  kRequired,
  kOptional,
  kVariable,
};

struct Param {
  explicit Param(DataType data_type,
                 Cardinality cardinality = Cardinality::kRequired)
      : data_type(data_type), cardinality(cardinality) {}

  libtextclassifier3::Status Matches(PendingValue& arg) const {
    bool matches = arg.data_type() == data_type;
    // Values of type kText could also potentially be valid kLong values. If
    // we're expecting a kLong and we have a kText, try to parse it as a kLong.
    if (!matches && data_type == DataType::kLong &&
        arg.data_type() == DataType::kText) {
      ICING_RETURN_IF_ERROR(arg.ParseInt());
      matches = true;
    }
    return matches ? libtextclassifier3::Status::OK
                   : absl_ports::InvalidArgumentError(
                         "Provided arg doesn't match required param type.");
  }

  DataType data_type;
  Cardinality cardinality;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_QUERY_ADVANCED_QUERY_PARSER_PARAM_H_
