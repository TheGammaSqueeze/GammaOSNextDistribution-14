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
// Author: Aleksei Vetrov

#include "type_normalisation.h"

#include <map>
#include <string>
#include <unordered_map>
#include <unordered_set>
#include <vector>

#include "graph.h"

namespace stg {

namespace {

struct ResolveQualifiedChain {
  ResolveQualifiedChain(const Graph& graph,
                        std::unordered_map<Id, Id>& resolved)
      : graph(graph), resolved(resolved) {}

  Id operator()(Id node_id) {
    return graph.Apply<Id>(*this, node_id, node_id);
  }

  Id operator()(const Qualified& x, Id node_id) {
    auto [it, emplaced] = resolved.emplace(node_id, Id::kInvalid);
    if (!emplaced) {
      Check(it->second != Id::kInvalid) << "qualified cycle detected";
      return it->second;
    }
    return it->second = (*this)(x.qualified_type_id);
  }

  template <typename Node>
  Id operator()(const Node&, Id node_id) {
    return node_id;
  }

  const Graph& graph;
  std::unordered_map<Id, Id>& resolved;
};

// Traverse rooted graph and produce mapping from qualified type to
// non-qualified. Produced keys should not intersect with values.
// It also collects all functions seen during traversal.
struct FindQualifiedTypesAndFunctions {
  FindQualifiedTypesAndFunctions(const Graph& graph,
                                 std::unordered_map<Id, Id>& resolved,
                                 std::unordered_set<Id>& functions)
      : graph(graph),
        resolved(resolved),
        functions(functions),
        resolve_qualified_chain(graph, resolved) {}

  void operator()(Id id) {
    if (seen.insert(id).second) {
      graph.Apply<void>(*this, id, id);
    }
  }

  void operator()(const std::vector<Id>& ids) {
    for (const auto& id : ids) {
      (*this)(id);
    }
  }

  void operator()(const std::map<std::string, Id>& x) {
    for (const auto& [_, id] : x) {
      (*this)(id);
    }
  }

  void operator()(const Special&, Id) {}

  void operator()(const PointerReference& x, Id) {
    (*this)(x.pointee_type_id);
  }

  void operator()(const PointerToMember& x, Id) {
    (*this)(x.containing_type_id);
    (*this)(x.pointee_type_id);
  }

  // Typedefs are not considered when looking for useless qualifiers.
  void operator()(const Typedef& x, Id) {
    (*this)(x.referred_type_id);
  }

  void operator()(const Qualified&, Id node_id) {
    (*this)(resolve_qualified_chain(node_id));
  }

  void operator()(const Primitive&, Id) {}

  void operator()(const Array& x, Id) {
    (*this)(x.element_type_id);
  }

  void operator()(const BaseClass& x, Id) {
    (*this)(x.type_id);
  }

  void operator()(const Method& x, Id) {
    (*this)(x.type_id);
  }

  void operator()(const Member& x, Id) {
    (*this)(x.type_id);
  }

  void operator()(const StructUnion& x, Id) {
    if (x.definition.has_value()) {
      auto& definition = x.definition.value();
      (*this)(definition.base_classes);
      (*this)(definition.methods);
      (*this)(definition.members);
    }
  }

  void operator()(const Enumeration& x, Id) {
    if (x.definition.has_value()) {
      (*this)(x.definition->underlying_type_id);
    }
  }

  void operator()(const Function& x, Id node_id) {
    functions.emplace(node_id);
    for (auto& id : x.parameters) {
      (*this)(id);
    }
    (*this)(x.return_type_id);
  }

  void operator()(const ElfSymbol& x, Id) {
    if (x.type_id) {
      (*this)(*x.type_id);
    }
  }

  void operator()(const Interface& x, Id) {
    (*this)(x.symbols);
    (*this)(x.types);
  }

  const Graph& graph;
  std::unordered_map<Id, Id>& resolved;
  std::unordered_set<Id>& functions;
  std::unordered_set<Id> seen;
  ResolveQualifiedChain resolve_qualified_chain;
};

// Remove qualifiers from function parameters and return type.
// "resolved" mapping should have resolutions from qualified type to
// non-qualified. Thus, keys and values should not intersect.
struct RemoveFunctionQualifiers {
  RemoveFunctionQualifiers(Graph& graph,
                           const std::unordered_map<Id, Id>& resolved)
      : graph(graph), resolved(resolved) {}

  void operator()(Id id) {
    graph.Apply<void>(*this, id);
  }

  void operator()(Function& x) {
    for (auto& id : x.parameters) {
      RemoveQualifiers(id);
    }
    RemoveQualifiers(x.return_type_id);
  }

  template<typename Node>
  void operator()(Node&) {
    Die() << "only functions should have qualifiers substituted";
  }

  void RemoveQualifiers(Id& id) {
    const auto it = resolved.find(id);
    if (it != resolved.end()) {
      id = it->second;
      Check(!resolved.count(id)) << "qualifier was resolved to qualifier";
    }
  }

  Graph& graph;
  const std::unordered_map<Id, Id>& resolved;
};

}  // namespace

void RemoveUselessQualifiers(Graph& graph, Id root) {
  std::unordered_map<Id, Id> resolved;
  std::unordered_set<Id> functions;
  FindQualifiedTypesAndFunctions(graph, resolved, functions)(root);

  RemoveFunctionQualifiers remove_qualifiers(graph, resolved);
  for (const auto& id : functions) {
    remove_qualifiers(id);
  }
}

}  // namespace stg
