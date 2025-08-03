// SPDX-License-Identifier: Apache-2.0 WITH LLVM-exception
// -*- mode: C++ -*-
//
// Copyright 2020-2022 Google LLC
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
// Author: Giuliano Procida
// Author: Ignes Simeonova

#ifndef STG_NAMING_H_
#define STG_NAMING_H_

#include <ostream>
#include <string>
#include <unordered_map>

#include "graph.h"

namespace stg {

// See NAMES.md for conceptual documentation.

enum class Precedence { NIL, POINTER, ARRAY_FUNCTION, ATOMIC };
enum class Side { LEFT, RIGHT };

class Name {
 public:
  explicit Name(const std::string& name)
      : left_(name), precedence_(Precedence::NIL), right_() {}
  Name(const std::string& left, Precedence precedence, const std::string& right)
      : left_(left), precedence_(precedence), right_(right) {}
  Name Add(Side side, Precedence precedence, const std::string& text) const;
  Name Qualify(Qualifier qualifier) const;
  std::ostream& Print(std::ostream& os) const;
  std::string ToString() const;

 private:
  std::string left_;
  Precedence precedence_;
  std::string right_;
};

std::ostream& operator<<(std::ostream& os, const Name& name);

using NameCache = std::unordered_map<Id, Name>;

struct Describe {
  Describe(const Graph& graph, NameCache& names) : graph(graph), names(names) {}
  Name operator()(Id id);
  Name operator()(const Special&);
  Name operator()(const PointerReference&);
  Name operator()(const PointerToMember&);
  Name operator()(const Typedef&);
  Name operator()(const Qualified&);
  Name operator()(const Primitive&);
  Name operator()(const Array&);
  Name operator()(const BaseClass&);
  Name operator()(const Method&);
  Name operator()(const Member&);
  Name operator()(const StructUnion&);
  Name operator()(const Enumeration&);
  Name operator()(const Function&);
  Name operator()(const ElfSymbol&);
  Name operator()(const Interface&);
  const Graph& graph;
  NameCache& names;
};

struct DescribeKind {
  explicit DescribeKind(const Graph& graph) : graph(graph) {}
  std::string operator()(Id id);
  std::string operator()(const BaseClass&);
  std::string operator()(const Method&);
  std::string operator()(const Member&);
  std::string operator()(const ElfSymbol&);
  std::string operator()(const Interface&);
  template <typename Node>
  std::string operator()(const Node&);
  const Graph& graph;
};

struct DescribeExtra {
  explicit DescribeExtra(const Graph& graph) : graph(graph) {}
  std::string operator()(Id id);
  std::string operator()(const ElfSymbol&);
  template <typename Node>
  std::string operator()(const Node&);
  const Graph& graph;
};

}  // namespace stg

#endif  // STG_NAMING_H_
