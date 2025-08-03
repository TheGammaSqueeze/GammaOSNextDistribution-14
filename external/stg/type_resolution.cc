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
// Author: Giuliano Procida

#include "type_resolution.h"

#include <cstddef>
#include <map>
#include <string>
#include <utility>
#include <vector>

#include "graph.h"
#include "runtime.h"
#include "unification.h"

namespace stg {

namespace {

// Collect named type definition and declaration nodes.
struct NamedTypes {
  NamedTypes(Runtime& runtime, const Graph& graph)
      : graph(graph),
        seen(Id(0)),
        nodes(runtime, "named_types.nodes"),
        types(runtime, "named_types.types"),
        definitions(runtime, "named_types.definitions"),
        declarations(runtime, "named_types.declarations") {
    seen.Reserve(graph.Limit());
  }

  enum class Tag { STRUCT, UNION, ENUM, TYPEDEF };
  using Type = std::pair<Tag, std::string>;
  struct Info {
    std::vector<Id> definitions;
    std::vector<Id> declarations;
  };

  void operator()(const std::vector<Id>& ids) {
    for (auto id : ids) {
      (*this)(id);
    }
  }

  void operator()(const std::map<std::string, Id>& x) {
    for (const auto& [_, id] : x) {
      (*this)(id);
    }
  }

  // main entry point
  void operator()(Id id) {
    if (seen.Insert(id)) {
      ++nodes;
      graph.Apply<void>(*this, id, id);
    }
  }

  Info& GetInfo(Tag tag, const std::string& name) {
    auto [it, inserted] = type_info.insert({{tag, name}, {}});
    if (inserted) {
      ++types;
    }
    return it->second;
  }

  // Graph function implementation
  void operator()(const Special&, Id) {}

  void operator()(const PointerReference& x, Id) {
    (*this)(x.pointee_type_id);
  }

  void operator()(const PointerToMember& x, Id) {
    (*this)(x.containing_type_id);
    (*this)(x.pointee_type_id);
  }

  void operator()(const Typedef& x, Id id) {
    auto& info = GetInfo(Tag::TYPEDEF, x.name);
    info.definitions.push_back(id);
    ++definitions;
    (*this)(x.referred_type_id);
  }

  void operator()(const Qualified& x, Id) {
    (*this)(x.qualified_type_id);
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

  void operator()(const StructUnion& x, Id id) {
    auto tag = x.kind == StructUnion::Kind::STRUCT ? Tag::STRUCT : Tag::UNION;
    const auto& name = x.name;
    const bool named = !name.empty();
    auto& info = GetInfo(tag, name);
    if (x.definition.has_value()) {
      if (named) {
        info.definitions.push_back(id);
        ++definitions;
      }
      const auto& definition = *x.definition;
      (*this)(definition.base_classes);
      (*this)(definition.methods);
      (*this)(definition.members);
    } else if (named) {
      info.declarations.push_back(id);
      ++declarations;
    }
  }

  void operator()(const Enumeration& x, Id id) {
    const auto& name = x.name;
    const bool named = !name.empty();
    auto& info = GetInfo(Tag::ENUM, name);
    if (x.definition) {
      if (named) {
        info.definitions.push_back(id);
        ++definitions;
      }
    } else if (named) {
      info.declarations.push_back(id);
      ++declarations;
    }
  }

  void operator()(const Function& x, Id) {
    (*this)(x.return_type_id);
    (*this)(x.parameters);
  }

  void operator()(const ElfSymbol& x, Id) {
    if (x.type_id.has_value()) {
      (*this)(x.type_id.value());
    }
  }

  void operator()(const Interface& x, Id) {
    (*this)(x.symbols);
    (*this)(x.types);
  }

  const Graph& graph;
  // ordered map for consistency and sequential processing of related types
  std::map<Type, Info> type_info;
  DenseIdSet seen;
  Counter nodes;
  Counter types;
  Counter definitions;
  Counter declarations;
};

}  // namespace

void ResolveTypes(Runtime& runtime, Graph& graph, Unification& unification,
                  const std::vector<Id>& roots) {
  const Time total(runtime, "resolve.total");

  // collect named types
  NamedTypes named_types(runtime, graph);
  {
    const Time time(runtime, "resolve.collection");
    for (const Id& root : roots) {
      named_types(root);
    }
  }

  {
    const Time time(runtime, "resolve.unification");
    Counter definition_unified(runtime, "resolve.definition.unified");
    Counter definition_not_unified(runtime, "resolve.definition.not_unified");
    Counter declaration_unified(runtime, "resolve.declaration.unified");
    for (auto& [_, info] : named_types.type_info) {
      // try to unify the type definitions
      auto& definitions = info.definitions;
      std::vector<Id> distinct_definitions;
      while (!definitions.empty()) {
        const Id candidate = definitions[0];
        std::vector<Id> todo;
        distinct_definitions.push_back(candidate);
        for (size_t i = 1; i < definitions.size(); ++i) {
          if (unification.Unify(definitions[i], candidate)) {
            // unification succeeded
            ++definition_unified;
          } else {
            // unification failed, conflicting definitions
            todo.push_back(definitions[i]);
            ++definition_not_unified;
          }
        }
        std::swap(todo, definitions);
      }
      // if no conflicts, map all declarations to the definition
      if (distinct_definitions.size() == 1) {
        const Id candidate = distinct_definitions[0];
        for (auto id : info.declarations) {
          unification.Union(id, candidate);
          ++declaration_unified;
        }
      }
    }
  }
}

}  // namespace stg
