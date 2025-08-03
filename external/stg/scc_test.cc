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

#include "scc.h"

#include <algorithm>
#include <cstddef>
#include <cstdint>
#include <iostream>
#include <map>
#include <optional>
#include <random>
#include <set>
#include <sstream>
#include <utility>
#include <vector>

#include <catch2/catch.hpp>

namespace Test {

using stg::SCC;

// Nodes are [0, N), the sets are the out-edges.
typedef std::vector<std::set<size_t>> Graph;

template <typename G>
Graph invent(size_t n, G& gen) {
  Graph graph(n);
  std::uniform_int_distribution<int> toss(0, 1);
  for (auto& node : graph) {
    for (size_t o = 0; o < n; ++o) {
      if (toss(gen)) {
        node.insert(o);
      }
    }
  }
  return graph;
}

// Generate a graph g' where i -> j iff i and j are strongly connected in g.
Graph symmetric_subset_of_reflexive_transitive_closure(Graph g) {
  const size_t n = g.size();
  // compute reflexive, transitive closure using a modified Floyd-Warshall
  for (size_t o = 0; o < n; ++o) {
    // 1. add edge o -> o, for each node o
    g[o].insert(o);
  }
  for (size_t k = 0; k < n; ++k) {
    // 2. for each node k check for paths of the form: i -> ... -> k -> ... -> j
    // where no node after k appears in the ...
    for (size_t i = 0; i < n; ++i) {
      // since we scan the nodes k in order, it suffices to consider just paths:
      // i -> k -> j
      if (g[i].count(k)) {
        // we have i -> k
        for (size_t j = 0; j < n; ++j) {
          if (g[k].count(j)) {
            // and k -> j
            g[i].insert(j);
          }
        }
      }
    }
  }
  // now have edge i -> j iff there is a path from i to j
  for (size_t i = 0; i < n; ++i) {
    for (size_t j = i + 1; j < n; ++j) {
      // discard i -> j if not j -> i and vice versa
      auto ij = g[i].count(j);
      auto ji = g[j].count(i);
      if (ij < ji) {
        g[j].erase(i);
      }
      if (ji < ij) {
        g[i].erase(j);
      }
    }
  }
  // now have edge i -> j iff there is a path from i to j and a path from j to i
  return g;
}

// Generate a graph where i -> j iff i and j are in the same SCC.
Graph scc_strong_connectivity(const std::vector<std::set<size_t>>& sccs) {
  size_t n = 0;
  std::map<size_t, const std::set<size_t>*> edges;
  for (const auto& scc : sccs) {
    for (auto o : scc) {
      if (o >= n) {
        n = o + 1;
      }
      edges[o] = &scc;
    }
  }
  Graph g(n);
  for (size_t o = 0; o < n; ++o) {
    g[o] = *edges[o];
  }
  return g;
}

void dfs(std::set<size_t>& visited, SCC<size_t>& scc, const Graph& g,
         size_t node, std::vector<std::set<size_t>>& sccs) {
  if (visited.count(node)) {
    return;
  }
  auto handle = scc.Open(node);
  if (!handle) {
    return;
  }
  for (auto o : g[node]) {
    dfs(visited, scc, g, o, sccs);
  }
  auto nodes = scc.Close(*handle);
  if (!nodes.empty()) {
    std::set<size_t> scc_set;
    for (auto o : nodes) {
      CHECK(visited.insert(o).second);
      CHECK(scc_set.insert(o).second);
    }
    sccs.push_back(scc_set);
  }
}

void process(const Graph& g) {
  const size_t n = g.size();

  // find SCCs
  std::set<size_t> visited;
  std::vector<std::set<size_t>> sccs;
  for (size_t o = 0; o < n; ++o) {
    // could reuse a single SCC finder but assert stronger invariants this way
    SCC<size_t> scc;
    dfs(visited, scc, g, o, sccs);
    CHECK(scc.Empty());
  }

  // check partition and topological order properties
  std::set<size_t> seen;
  for (const auto& nodes : sccs) {
    CHECK(!nodes.empty());
    for (auto node : nodes) {
      // value in range [0, n)
      CHECK(node < n);
      // value seen at most once
      CHECK(seen.insert(node).second);
    }
    for (auto node : nodes) {
      for (auto o : g[node]) {
        // edges point to nodes in this or earlier SCCs
        CHECK(seen.count(o));
      }
    }
  }
  // exactly n values seen
  CHECK(seen.size() == n);

  // check strong connectivity
  auto g_scc_closure = scc_strong_connectivity(sccs);
  auto g_closure = symmetric_subset_of_reflexive_transitive_closure(g);
  CHECK(g_scc_closure == g_closure);
}

TEST_CASE("randomly-generated graphs") {
  std::ranlux48 gen;
  auto seed = gen();
  // NOTES:
  //   Graphs of size 6 are plenty big enough to shake out bugs.
  //   There are O(2^k^2) possible directed graphs of size k.
  //   Testing costs are O(k^3) so we restrict accordingly.
  uint64_t budget = 10000;
  for (size_t k = 0; k < 7; ++k) {
    uint64_t count = std::min(static_cast<uint64_t>(1) << (k * k),
                              budget / (k ? k * k * k : 1));
    INFO("testing with " << count << " graphs of size " << k);
    for (uint64_t n = 0; n < count; ++n, ++seed) {
      gen.seed(seed);
      Graph g = invent(k, gen);
      std::ostringstream os;
      os << "a graph of " << k << " nodes generated using seed " << seed;
      GIVEN(os.str()) {
        process(g);
      }
    }
  }
}

}  // namespace Test
