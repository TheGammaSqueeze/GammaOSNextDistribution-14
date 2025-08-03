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

#include "fidelity.h"

#include <map>
#include <ostream>
#include <set>
#include <string>
#include <unordered_map>
#include <utility>
#include <vector>

#include "graph.h"
#include "naming.h"

namespace stg {

namespace {

struct Fidelity {
  Fidelity(const Graph& graph, NameCache& name_cache)
      : graph(graph), describe(graph, name_cache), seen(Id(0)) {
    seen.Reserve(graph.Limit());
  }

  void operator()(Id);
  void operator()(const std::vector<Id>&);
  void operator()(const std::map<std::string, Id>&);
  void operator()(const Special&, Id);
  void operator()(const PointerReference&, Id);
  void operator()(const PointerToMember&, Id);
  void operator()(const Typedef&, Id);
  void operator()(const Qualified&, Id);
  void operator()(const Primitive&, Id);
  void operator()(const Array&, Id);
  void operator()(const BaseClass&, Id);
  void operator()(const Method&, Id);
  void operator()(const Member&, Id);
  void operator()(const StructUnion&, Id);
  void operator()(const Enumeration&, Id);
  void operator()(const Function&, Id);
  void operator()(const ElfSymbol&, Id);
  void operator()(const Interface&, Id);

  const Graph& graph;
  Describe describe;
  DenseIdSet seen;
  std::unordered_map<std::string, SymbolFidelity> symbols;
  std::unordered_map<std::string, TypeFidelity> types;
};

void Fidelity::operator()(Id id) {
  if (seen.Insert(id)) {
    graph.Apply<void>(*this, id, id);
  }
}

void Fidelity::operator()(const std::vector<Id>& x) {
  for (auto id : x) {
    (*this)(id);
  }
}

void Fidelity::operator()(const std::map<std::string, Id>& x) {
  for (const auto& [_, id] : x) {
    (*this)(id);
  }
}

void Fidelity::operator()(const Special&, Id) {}

void Fidelity::operator()(const PointerReference& x, Id) {
  (*this)(x.pointee_type_id);
}

void Fidelity::operator()(const PointerToMember& x, Id) {
  (*this)(x.containing_type_id);
  (*this)(x.pointee_type_id);
}

void Fidelity::operator()(const Typedef& x, Id) {
  (*this)(x.referred_type_id);
}

void Fidelity::operator()(const Qualified& x, Id) {
  (*this)(x.qualified_type_id);
}

void Fidelity::operator()(const Primitive&, Id) {}

void Fidelity::operator()(const Array& x, Id) {
  (*this)(x.element_type_id);
}

void Fidelity::operator()(const BaseClass& x, Id) {
  (*this)(x.type_id);
}

void Fidelity::operator()(const Method& x, Id) {
  (*this)(x.type_id);
}

void Fidelity::operator()(const Member& x, Id) {
  (*this)(x.type_id);
}

void Fidelity::operator()(const StructUnion& x, Id id) {
  if (!x.name.empty()) {
    auto [it, _] =
        types.emplace(describe(id).ToString(), TypeFidelity::DECLARATION_ONLY);
    if (x.definition) {
      it->second = TypeFidelity::FULLY_DEFINED;
    }
  }
  if (x.definition) {
    (*this)(x.definition->base_classes);
    (*this)(x.definition->methods);
    (*this)(x.definition->members);
  }
}

void Fidelity::operator()(const Enumeration& x, Id id) {
  if (!x.name.empty()) {
    auto [it, _] =
        types.emplace(describe(id).ToString(), TypeFidelity::DECLARATION_ONLY);
    if (x.definition) {
      it->second = TypeFidelity::FULLY_DEFINED;
    }
  }
}

void Fidelity::operator()(const Function& x, Id) {
  (*this)(x.return_type_id);
  (*this)(x.parameters);
}

void Fidelity::operator()(const ElfSymbol& x, Id) {
  auto symbol = VersionedSymbolName(x);
  auto [it, _] = symbols.emplace(symbol, SymbolFidelity::UNTYPED);
  if (x.type_id) {
    it->second = SymbolFidelity::TYPED;
    (*this)(*x.type_id);
  }
}

void Fidelity::operator()(const Interface& x, Id) {
  (*this)(x.symbols);
  (*this)(x.types);
}

template <typename T>
std::set<std::string> GetKeys(
    const std::unordered_map<std::string, T>& x1,
    const std::unordered_map<std::string, T>& x2) {
  std::set<std::string> keys;
  for (const auto& [key, _] : x1) {
    keys.insert(key);
  }
  for (const auto& [key, _] : x2) {
    keys.insert(key);
  }
  return keys;
}

void InsertTransition(FidelityDiff& diff, SymbolFidelityTransition transition,
                      const std::string& symbol) {
  diff.symbol_transitions[transition].push_back(symbol);
}

void InsertTransition(FidelityDiff& diff, TypeFidelityTransition transition,
                      const std::string& type) {
  diff.type_transitions[transition].push_back(type);
}

template <typename T>
void InsertTransitions(FidelityDiff& diff,
                       const std::unordered_map<std::string, T>& x1,
                       const std::unordered_map<std::string, T>& x2) {
  for (const auto& key : GetKeys(x1, x2)) {
    auto it1 = x1.find(key);
    auto it2 = x2.find(key);
    auto transition = std::make_pair(it1 == x1.end() ? T() : it1->second,
                                     it2 == x2.end() ? T() : it2->second);
    InsertTransition(diff, transition, key);
  }
}

}  // namespace

std::ostream& operator<<(std::ostream& os, SymbolFidelity x) {
  switch (x) {
    case SymbolFidelity::ABSENT:
      return os << "ABSENT";
    case SymbolFidelity::TYPED:
      return os << "TYPED";
    case SymbolFidelity::UNTYPED:
      return os << "UNTYPED";
  }
}

std::ostream& operator<<(std::ostream& os, TypeFidelity x) {
  switch (x) {
    case TypeFidelity::ABSENT:
      return os << "ABSENT";
    case TypeFidelity::DECLARATION_ONLY:
      return os << "DECLARATION_ONLY";
    case TypeFidelity::FULLY_DEFINED:
      return os << "FULLY_DEFINED";
  }
}

std::ostream& operator<<(std::ostream& os, SymbolFidelityTransition x) {
  return os << "symbol(s) changed from " << x.first << " to " << x.second;
}

std::ostream& operator<<(std::ostream& os, TypeFidelityTransition x) {
  return os << "type(s) changed from " << x.first << " to " << x.second;
}

FidelityDiff GetFidelityTransitions(const Graph& graph, Id root1, Id root2) {
  NameCache name_cache;
  Fidelity fidelity1(graph, name_cache);
  Fidelity fidelity2(graph, name_cache);
  fidelity1(root1);
  fidelity2(root2);

  FidelityDiff diff;
  InsertTransitions(diff, fidelity1.symbols, fidelity2.symbols);
  InsertTransitions(diff, fidelity1.types, fidelity2.types);
  return diff;
}

}  // namespace stg
