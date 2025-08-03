// SPDX-License-Identifier: Apache-2.0 WITH LLVM-exception
// -*- mode: C++ -*-
//
// Copyright 2022 Google LLC
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

#include "fingerprint.h"

#include <cstdint>
#include <map>
#include <string>
#include <unordered_map>
#include <unordered_set>
#include <utility>
#include <vector>

#include "graph.h"
#include "hashing.h"
#include "runtime.h"
#include "scc.h"

namespace stg {
namespace {

struct Hasher {
  Hasher(Runtime& runtime, const Graph& graph,
         std::unordered_map<Id, HashValue>& hashes,
         std::unordered_set<Id>& todo)
      : graph(graph), hashes(hashes), todo(todo),
        non_trivial_scc_size(runtime, "fingerprint.non_trivial_scc_size") {}

  // Graph function implementation
  HashValue operator()(const Special& x) {
    switch (x.kind) {
      case Special::Kind::VOID:
        return hash('O');
      case Special::Kind::VARIADIC:
        return hash('V');
      case Special::Kind::NULLPTR:
        return hash('L');
    }
  }

  HashValue operator()(const PointerReference& x) {
    return hash('P', static_cast<uint32_t>(x.kind), (*this)(x.pointee_type_id));
  }

  HashValue operator()(const PointerToMember& x) {
    return hash('N', (*this)(x.containing_type_id), (*this)(x.pointee_type_id));
  }

  HashValue operator()(const Typedef& x) {
    todo.insert(x.referred_type_id);
    return hash('T', x.name);
  }

  HashValue operator()(const Qualified& x) {
    return hash('Q', static_cast<uint32_t>(x.qualifier),
                (*this)(x.qualified_type_id));
  }

  HashValue operator()(const Primitive& x) {
    return hash('i', x.name);
  }

  HashValue operator()(const Array& x) {
    return hash('A', x.number_of_elements, (*this)(x.element_type_id));
  }

  HashValue operator()(const BaseClass& x) {
    return hash('B', (*this)(x.type_id));
  }

  HashValue operator()(const Method& x) {
    return hash('M', x.mangled_name, x.name, (*this)(x.type_id));
  }

  HashValue operator()(const Member& x) {
    return hash('D', x.name, x.offset, (*this)(x.type_id));
  }

  HashValue operator()(const StructUnion& x) {
    auto h = hash('U', static_cast<uint32_t>(x.kind), x.name);
    if (x.definition.has_value()) {
      h = hash(h, '1');
      const auto& definition = *x.definition;
      ToDo(definition.base_classes);
      ToDo(definition.methods);
      if (x.name.empty()) {
        for (auto id : definition.members) {
          h = hash(h, (*this)(id));
        }
      } else {
        ToDo(definition.members);
      }
    } else {
      h = hash(h, '0');
    }
    return h;
  }

  HashValue operator()(const Enumeration& x) {
    auto h = hash('E', x.name);
    if (x.definition.has_value()) {
      h = hash(h, '1');
      const auto& definition = *x.definition;
      todo.insert(definition.underlying_type_id);
      if (x.name.empty()) {
        for (const auto& e : definition.enumerators) {
          h = hash(h, e.first);
        }
      }
    } else {
      h = hash(h, '0');
    }
    return h;
  }

  HashValue operator()(const Function& x) {
    auto h = hash('F', (*this)(x.return_type_id));
    for (const auto& parameter : x.parameters) {
      h = hash(h, (*this)(parameter));
    }
    return h;
  }

  HashValue operator()(const ElfSymbol& x) {
    if (x.type_id.has_value()) {
      todo.insert(x.type_id.value());
    }
    return hash('S', x.symbol_name);
  }

  HashValue operator()(const Interface& x) {
    ToDo(x.symbols);
    ToDo(x.types);
    return hash('Z');
  }

  // main entry point
  HashValue operator()(Id id) {
    // Check if the id already has a fingerprint.
    const auto it = hashes.find(id);
    if (it != hashes.end()) {
      return it->second;
    }

    // Record the id with Strongly-Connected Component finder.
    auto handle = scc.Open(id);
    if (!handle) {
      // Already open.
      //
      // Return a dummy fingerprint.
      return HashValue(0);
    }
    // Comparison opened, need to close it before returning.

    auto result = graph.Apply<HashValue>(*this, id);

    // Check for a complete Strongly-Connected Component.
    auto ids = scc.Close(*handle);
    if (ids.empty()) {
      // Note that result is tentative as the SCC is still open.
      return result;
    }

    // Closed SCC.
    //
    // Note that result is the combination of every fingerprint in the SCC via
    // the DFS spanning tree.
    //
    // All nodes in a non-trivial SCCs are given the same fingerprint, but
    // non-trivial SCCs should be extremely rare.
    const auto size = ids.size();
    if (size > 1) {
      result = HashValue(size);
      non_trivial_scc_size.Add(size);
    }
    for (auto id : ids) {
      hashes.insert({id, result});
    }
    return result;
  }

  void ToDo(const std::vector<Id>& ids) {
    for (auto id : ids) {
      todo.insert(id);
    }
  }

  void ToDo(const std::map<std::string, Id>& ids) {
    for (const auto& [_, id] : ids) {
      todo.insert(id);
    }
  }

  const Graph& graph;
  std::unordered_map<Id, HashValue>& hashes;
  std::unordered_set<Id> &todo;
  Histogram non_trivial_scc_size;
  SCC<Id> scc;

  // Function object: (Args...) -> HashValue
  Hash hash;
};

}  // namespace

std::unordered_map<Id, HashValue> Fingerprint(
    Runtime& runtime, const Graph& graph, Id root) {
  const Time x(runtime, "hash nodes");
  std::unordered_map<Id, HashValue> hashes;
  std::unordered_set<Id> todo;
  Hasher hasher(runtime, graph, hashes, todo);
  todo.insert(root);
  while (!todo.empty()) {
    for (auto id : std::exchange(todo, {})) {
      hasher(id);
    }
  }
  return hashes;
}

}  // namespace stg
