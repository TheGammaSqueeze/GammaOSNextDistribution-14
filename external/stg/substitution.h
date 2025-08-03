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

#ifndef STG_SUBSTITUTION_H_
#define STG_SUBSTITUTION_H_

#include <map>
#include <vector>

#include "graph.h"

namespace stg {

// This is a single-node node id substitution function that updates all node
// references according to a given mapping. The caller is responsible for
// determining the nodes to which substitution should apply (e.g., excluding
// orphaned nodes).
//
// The caller must provide a reference to a callable object which should update
// its Id argument only when needed (i.e., when the new value is different).
//
// The Update helpers may be used to update external node id references.
template <typename Updater>
struct Substitute {
  Substitute(Graph& graph, const Updater& updater)
      : graph(graph), updater(updater) {}

  void Update(Id& id) const {
    updater(id);
  }

  void Update(std::vector<Id>& ids) const {
    for (auto& id : ids) {
      Update(id);
    }
  }

  template <typename Key>
  void Update(std::map<Key, Id>& ids) const {
    for (auto& [key, id] : ids) {
      Update(id);
    }
  }

  void operator()(Id id) {
    return graph.Apply<void>(*this, id);
  }

  void operator()(Special&) {}

  void operator()(PointerReference& x) {
    Update(x.pointee_type_id);
  }

  void operator()(PointerToMember& x) {
    Update(x.containing_type_id);
    Update(x.pointee_type_id);
  }

  void operator()(Typedef& x) {
    Update(x.referred_type_id);
  }

  void operator()(Qualified& x) {
    Update(x.qualified_type_id);
  }

  void operator()(Primitive&) {}

  void operator()(Array& x) {
    Update(x.element_type_id);
  }

  void operator()(BaseClass& x) {
    Update(x.type_id);
  }

  void operator()(Method& x) {
    Update(x.type_id);
  }

  void operator()(Member& x) {
    Update(x.type_id);
  }

  void operator()(StructUnion& x) {
    if (x.definition.has_value()) {
      auto& definition = x.definition.value();
      Update(definition.base_classes);
      Update(definition.methods);
      Update(definition.members);
    }
  }

  void operator()(Enumeration& x) {
    if (x.definition.has_value()) {
      auto& definition = x.definition.value();
      Update(definition.underlying_type_id);
    }
  }

  void operator()(Function& x) {
    Update(x.parameters);
    Update(x.return_type_id);
  }

  void operator()(ElfSymbol& x) {
    if (x.type_id) {
      Update(*x.type_id);
    }
  }

  void operator()(Interface& x) {
    Update(x.symbols);
    Update(x.types);
  }

  Graph& graph;
  const Updater& updater;
};

template <class Updater>
Substitute(Graph&, const Updater& updater) -> Substitute<decltype(updater)>;

}  // namespace stg

#endif  // STG_SUBSTITUTION_H_
