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

#ifndef STG_SCC_H_
#define STG_SCC_H_

#include <cstddef>
#include <iterator>
#include <memory>
#include <optional>
#include <unordered_map>
#include <utility>
#include <vector>

#include "error.h"

namespace stg {

/*
 * This is a streamlined Strongly-Connected Component finder for use with
 * procedurally generated or explored graphs, where the nodes and edges are not
 * known a priori.
 *
 * REQUIREMENTS
 *
 * The Node type must be copyable and have a suitable hash function.
 *
 * The user code must take the form of a Depth First Search which can be
 * repeatedly invoked on unvisited nodes until the whole graph has been
 * traversed.
 *
 * The user code must always follow edges to child nodes, even if it knows the
 * node has already been visited. The SCC finder needs to know about all edges.
 *
 * The user code must ensure that nodes are not re-examined once they have been
 * assigned to an SCC. The finder does not maintain any state for such nodes.
 *
 * GUARANTEES
 *
 * The SCC finder will ensure each node is examined exactly once.
 *
 * The SCCs will be presented in a topological order, leaves first.
 *
 * Note that within each SCC, nodes will be presented in DFS traversal order,
 * roots first. However, this is just an implementation detail, not a guarantee.
 *
 * USAGE
 *
 * Before examining a node, check it's not been assigned to an SCC already and
 * then call Open. If the node is already "open" (i.e., is already waiting to be
 * assigned to an SCC), this will return an empty optional value and the node
 * should not be examined. If Open succeeds, a numerical node handle will be
 * returned and the node will be recorded as waiting to be assigned to an SCC.
 *
 * Now examine the node, making recursive calls to follow edges to other nodes.
 * Information about the node can be stored provisionally, but must NOT be used
 * to make decisions about whether to revisit it - that is Open's job.
 *
 * Once the examination is done, call Close, passing in the handle. If the node
 * has been identified as the "root" of an SCC, the whole SCC will be returned
 * as a vector of nodes. If any processing needs to be done (such as recording
 * the nodes as visited), this should be done now. Otherwise, an empty vector
 * will be returned.
 *
 * After a top-level DFS has completed, the SCC finder should be carrying no
 * state. This can be verified by calling Empty.
 */
template <typename Node, typename Hash = std::hash<Node>>
class SCC {
 public:
  bool Empty() const {
    return open_.empty() && is_open_.empty() && root_index_.empty();
  }

  std::optional<size_t> Open(const Node& node) {
    // Insertion will fail if the node is already open.
    auto [it, inserted] = is_open_.insert({node, is_open_.size()});
    auto ix = it->second;
    if (!inserted) {
      // Pop indices to nodes which cannot be the root of their SCC.
      while (root_index_.back() > ix) {
        root_index_.pop_back();
      }
      return {};
    }
    // Unvisited, record open node and record root index.
    open_.push_back(node);
    root_index_.push_back(ix);
    return {ix};
  }

  std::vector<Node> Close(size_t ix) {
    std::vector<Node> scc;
    Check(ix < open_.size()) << "internal error: illegal SCC node index";
    if (ix == root_index_.back()) {
      // Close SCC.
      root_index_.pop_back();
      const auto begin = open_.begin() + ix;
      const auto end = open_.end();
      for (auto it = begin; it != end; ++it) {
        is_open_.erase(*it);
      }
      scc.reserve(end - begin);
      std::move(begin, end, std::back_inserter(scc));
      open_.erase(begin, end);
    }
    return scc;
  }

 private:
  std::vector<Node> open_;  // index to node
  std::unordered_map<Node, size_t, Hash> is_open_;  // node to index
  std::vector<size_t> root_index_;
};

}  // namespace stg

#endif  // STG_SCC_H_
