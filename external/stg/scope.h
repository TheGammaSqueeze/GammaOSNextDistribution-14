// SPDX-License-Identifier: Apache-2.0 WITH LLVM-exception
// -*- mode: C++ -*-
//
// Copyright 2022-2023 Google LLC
//
// Licensed under the Apache License v2.0 with LLVM Exceptions (the
// "License"); you may not use this file except in compliance with the
// License.  You may obtain a copy of the License at
//
//     https://llvm.org/LICENSE.txt
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
// Author: Ignes Simeonova
// Author: Aleksei Vetrov

#ifndef STG_SCOPE_H_
#define STG_SCOPE_H_

#include <cstddef>
#include <string>

namespace stg {

using Scope = std::string;

class PushScopeName {
 public:
  template <typename Kind>
  PushScopeName(Scope& scope_, Kind&& kind, const std::string& name)
      : scope_name_(scope_), old_size_(scope_name_.size()) {
    if (name.empty()) {
      scope_name_ += "<unnamed ";
      scope_name_ += kind;
      scope_name_ += ">::";
    } else {
      scope_name_ += name;
      scope_name_ += "::";
    }
  }

  PushScopeName(const PushScopeName& other) = delete;
  PushScopeName& operator=(const PushScopeName& other) = delete;
  ~PushScopeName() {
    scope_name_.resize(old_size_);
  }

 private:
  std::string& scope_name_;
  const size_t old_size_;
};

}  // namespace stg

#endif  // STG_SCOPE_H_
