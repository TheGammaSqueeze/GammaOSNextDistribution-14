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
#include "icing/query/advanced_query_parser/function.h"

#include "icing/absl_ports/canonical_errors.h"
#include "icing/absl_ports/str_cat.h"
#include "icing/util/status-macros.h"

namespace icing {
namespace lib {

/*static*/ libtextclassifier3::StatusOr<Function> Function::Create(
    DataType return_type, std::string name, std::vector<Param> params,
    Function::EvalFunction eval) {
  bool has_had_optional = false;
  for (int i = 0; i < params.size(); ++i) {
    switch (params.at(i).cardinality) {
      case Cardinality::kVariable:
        if (i != params.size() - 1) {
          return absl_ports::InvalidArgumentError(
              "Can only specify a variable param as the final param.");
        }
        break;
      case Cardinality::kOptional:
        has_had_optional = true;
        break;
      case Cardinality::kRequired:
        if (has_had_optional) {
          return absl_ports::InvalidArgumentError(
              "Can't specify optional params followed by required params.");
        }
        break;
    }
  }
  return Function(return_type, std::move(name), std::move(params),
                  std::move(eval));
}

libtextclassifier3::StatusOr<PendingValue> Function::Eval(
    std::vector<PendingValue>&& args) const {
  for (int i = 0; i < params_.size() || i < args.size(); ++i) {
    if (i < args.size() && i < params_.size()) {
      ICING_RETURN_IF_ERROR(params_.at(i).Matches(args.at(i)));
    } else if (i >= params_.size()) {
      // There are remaining args. This would happen if the final arg is
      // kVariable.
      if (params_.empty() ||
          params_.rbegin()->cardinality != Cardinality::kVariable) {
        return absl_ports::InvalidArgumentError(absl_ports::StrCat(
            "Expected to find only ", std::to_string(params_.size()),
            " arguments, but found ", std::to_string(args.size())));
      }
      ICING_RETURN_IF_ERROR(params_.rbegin()->Matches(args.at(i)));
    } else if (params_.at(i).cardinality == Cardinality::kRequired) {
      // There are no more args, but there are still params to check for. If
      // These params are kRequired, then there is an error.
      return absl_ports::InvalidArgumentError(absl_ports::StrCat(
          "Expected to find ", std::to_string(i + 1), "th argument, but only ",
          std::to_string(args.size()), " arguments provided."));
    }
  }
  return eval_(std::move(args));
}

}  // namespace lib
}  // namespace icing