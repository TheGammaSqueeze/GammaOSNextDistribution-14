// SPDX-License-Identifier: Apache-2.0 WITH LLVM-exception
// -*- mode: C++ -*-
//
// Copyright 2021 Google LLC
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

#ifndef STG_ORDER_H_
#define STG_ORDER_H_

#include <algorithm>
#include <cstddef>
#include <optional>
#include <utility>
#include <vector>

#include "error.h"

namespace stg {
// Updates a given ordering of items with items from a second ordering,
// incorporating as much of the latter's order as is compatible.
//
// The two orderings are reconciled by starting with the left ordering and
// greedily inserting new items from the right ordering, in a position which
// satisfies that ordering, if possible.
//
// Example, before and after:
//
// indexes1: rose, george, emily
// indexes2: george, ted, emily
//
// indexes1: rose, george, ted, emily
template <typename T>
void ExtendOrder(std::vector<T>& indexes1, const std::vector<T>& indexes2) {
  // keep track of where we can insert in indexes1
  size_t pos = 0;
  for (const auto& value : indexes2) {
    auto found = std::find(indexes1.begin(), indexes1.end(), value);
    if (found == indexes1.end()) {
      // new node, insert at first possible place
      indexes1.insert(indexes1.begin() + pos, value);
      // now pointing at inserted item, point after it
      ++pos;
    } else if (indexes1.begin() + pos <= found) {
      // safe to use the constraint, point after found item
      pos = found - indexes1.begin() + 1;
    }
  }
}

// Permutes the data array according to the permutation.
//
// The vectors must be the same size and permutation must contain [0, size()).
//
// Each data[i] <- data[permutation[i]].
//
// Each permutation[i] <- i.
//
// Example (where the permutation consists of a single cycle), step by step:
//
// data: emily, george, rose, ted
// permutation: 2, 1, 3, 0
//
// from = 0
// initialise to = 0
// resolve cycle by swapping elements
//
// permutation[to = 0] = 2 != from = 2
//   want data[2] = rose at data[0] = emily, swap them
//   also swap to = 0 with permutation[to] = 2
//   data: rose, george, emily, ted
//   permutation 0, 1, 3, 0
//   to = 2
//
// permutation[to = 2] = 3 != from = 0
//   want data[3] = ted at data[2] = emily, swap them
//   also swap to = 2 with permutation[2] = 3
//   data: rose, george, ted, emily
//   permutation 0, 1, 2, 0
//   to = 3
//
// permutation[to = 3] = 0 == from = 0
//   emily is now in right position
//   finish the cycle and set permutation[to = 3] = to = 3
//   permutation 0, 1, 2, 3
template <typename T>
void Permute(std::vector<T>& data, std::vector<size_t>& permutation) {
  const size_t size = permutation.size();
  Check(data.size() == size) << "internal error: bad Permute vectors";
  for (size_t from = 0; from < size; ++from) {
    size_t to = from;
    while (permutation[to] != from) {
      Check(permutation[to] < size) << "internal error: bad Permute index";
      using std::swap;
      swap(data[to], data[permutation[to]]);
      swap(to, permutation[to]);
    }
    permutation[to] = to;
  }
}

// Reorders the data array according to its implicit ordering constraints.
//
// At least one of each pair of positions must be present.
//
// Each pair gives 1 or 2 abstract positions for the corresponding data item.
//
// The first and second positions are interpreted separately, with the first's
// implied ordering having precedence in the event of a conflict.
//
// The real work is done by ExtendOrder and Permute.
//
// In practice the input data are the output of a matching process, consider:
//
// sequence1: rose, george, emily
// sequence2: george, ted, emily
//
// These have the corresponding matches (here just ordered by the matching key;
// this algorithm gives the same result independent of this ordering):
//
// emily:  {{2}, {2}}
// george: {{1}, {0}}
// rose:   {{0}, {} }
// ted:    {{},  {1}}
//
// Now ignore the matching keys.
//
// This function processes the matches into intermediate data structures:
//
// positions1: {{2, 0}, {1, 1}, {0, 2},        }
// positions2: {{2, 0}, {0, 1},         {1, 3},}
//
// The indexes (.second) are sorted by the positions (.first):
//
// positions1: {{0, 2}, {1, 1}, {2, 0}}
// positions2: {{0, 1}, {1, 3}, {2, 0}}
//
// And the positions are discarded:
//
// indexes1: 2, 1, 0
// indexes2: 1, 3, 0
//
// Finally a consistent ordering is made:
//
// indexes1: 2, 1, 3, 0
//
// And this is used to permute the original matching sequence, for clarity
// including the implicit keys here:
//
// rose:   {{0}, {} }
// george: {{1}, {0}}
// ted:    {{},  {1}}
// emily:  {{2}, {2}}
template <typename T>
void Reorder(std::vector<std::pair<std::optional<T>, std::optional<T>>>& data) {
  const auto size = data.size();
  // Split out the ordering constraints as position-index pairs.
  std::vector<std::pair<T, size_t>> positions1;
  positions1.reserve(size);
  std::vector<std::pair<T, size_t>> positions2;
  positions2.reserve(size);
  for (size_t index = 0; index < size; ++index) {
    const auto& [position1, position2] = data[index];
    Check(position1 || position2)
        << "internal error: Reorder constraint with no positions";
    if (position1) {
      positions1.push_back({*position1, index});
    }
    if (position2) {
      positions2.push_back({*position2, index});
    }
  }
  // Order the indexes by the desired positions.
  std::stable_sort(positions1.begin(), positions1.end());
  std::stable_sort(positions2.begin(), positions2.end());
  std::vector<size_t> indexes1;
  indexes1.reserve(size);
  std::vector<size_t> indexes2;
  indexes2.reserve(positions2.size());
  for (const auto& ordered_index : positions1) {
    indexes1.push_back(ordered_index.second);
  }
  for (const auto& ordered_index : positions2) {
    indexes2.push_back(ordered_index.second);
  }
  // Merge the two orderings of indexes.
  ExtendOrder(indexes1, indexes2);
  // Use this to permute the original data array.
  Permute(data, indexes1);
}

}  // namespace stg

#endif  // STG_ORDER_H_
