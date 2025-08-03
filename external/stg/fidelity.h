// SPDX-License-Identifier: Apache-2.0 WITH LLVM-exception
// -*- mode: C++ -*-
//
// Copyright 2023 Google LLC
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
// Author: Siddharth Nayyar

#ifndef STG_FIDELITY_H_
#define STG_FIDELITY_H_

#include <cstddef>
#include <functional>
#include <ostream>
#include <string>
#include <unordered_map>
#include <utility>
#include <vector>

#include "graph.h"

namespace stg {

enum class SymbolFidelity {
  ABSENT = 0,
  UNTYPED = 1,
  TYPED = 2,
};

enum class TypeFidelity {
  ABSENT = 0,
  DECLARATION_ONLY = 1,
  FULLY_DEFINED = 2,
};

using SymbolFidelityTransition = std::pair<SymbolFidelity, SymbolFidelity>;
using TypeFidelityTransition = std::pair<TypeFidelity, TypeFidelity>;

std::ostream& operator<<(std::ostream& os, SymbolFidelity x);
std::ostream& operator<<(std::ostream& os, TypeFidelity x);
std::ostream& operator<<(std::ostream& os, SymbolFidelityTransition x);
std::ostream& operator<<(std::ostream& os, TypeFidelityTransition x);

}  // namespace stg

namespace std {

template <>
struct hash<stg::SymbolFidelityTransition> {
  size_t operator()(const stg::SymbolFidelityTransition& x) const {
    return static_cast<size_t>(x.first) << 2 | static_cast<size_t>(x.second);
  }
};

template <>
struct hash<stg::TypeFidelityTransition> {
  size_t operator()(const stg::TypeFidelityTransition& x) const {
    return static_cast<size_t>(x.first) << 2 | static_cast<size_t>(x.second);
  }
};

}  // namespace std

namespace stg {

struct FidelityDiff {
  std::unordered_map<SymbolFidelityTransition, std::vector<std::string>>
      symbol_transitions;
  std::unordered_map<TypeFidelityTransition, std::vector<std::string>>
      type_transitions;
};

FidelityDiff GetFidelityTransitions(const Graph& graph, Id root1, Id root2);

}  // namespace stg

#endif  // STG_FIDELITY_H_
