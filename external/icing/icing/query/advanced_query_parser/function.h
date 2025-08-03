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
#ifndef ICING_QUERY_ADVANCED_QUERY_PARSER_FUNCTION_H_
#define ICING_QUERY_ADVANCED_QUERY_PARSER_FUNCTION_H_

#include <functional>
#include <string>
#include <utility>
#include <vector>

#include "icing/text_classifier/lib3/utils/base/statusor.h"
#include "icing/query/advanced_query_parser/param.h"
#include "icing/query/advanced_query_parser/pending-value.h"

namespace icing {
namespace lib {

class Function {
 public:
  using EvalFunction = std::function<libtextclassifier3::StatusOr<PendingValue>(
      std::vector<PendingValue>&&)>;

  static libtextclassifier3::StatusOr<Function> Create(
      DataType return_type, std::string name, std::vector<Param> params,
      EvalFunction eval);

  Function(const Function& rhs) = default;
  Function(Function&& rhs) = default;

  Function& operator=(const Function& rhs) = default;
  Function& operator=(Function&& rhs) = default;

  const std::string& name() const { return name_; }

  libtextclassifier3::StatusOr<PendingValue> Eval(
      std::vector<PendingValue>&& args) const;

 private:
  Function(DataType return_type, std::string name, std::vector<Param> params,
           EvalFunction eval)
      : name_(std::move(name)),
        params_(std::move(params)),
        eval_(std::move(eval)),
        return_type_(return_type) {}

  std::string name_;
  std::vector<Param> params_;
  EvalFunction eval_;
  DataType return_type_;
};

}  // namespace lib
}  // namespace icing

#endif  // ICING_QUERY_ADVANCED_QUERY_PARSER_FUNCTION_H_
