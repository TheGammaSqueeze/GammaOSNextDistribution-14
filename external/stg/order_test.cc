// SPDX-License-Identifier: Apache-2.0 WITH LLVM-exception
// -*- mode: C++ -*-
//
// Copyright 2021-2022 Google LLC
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

#include "order.h"

#include <cstddef>
#include <optional>
#include <random>
#include <sstream>
#include <string>
#include <tuple>
#include <utility>
#include <vector>

#include <catch2/catch.hpp>

namespace Test {

// Safe for small k!
size_t Factorial(size_t k) {
  size_t count = 1;
  for (size_t i = 1; i <= k; ++i) {
    count *= i;
  }
  return count;
}

TEST_CASE("hand-curated permutation") {
  std::vector<std::string> data = {"emily", "george", "rose", "ted"};
  std::vector<size_t> permutation = {2, 1, 3, 0};
  const std::vector<std::string> expected = {"rose", "george", "ted", "emily"};
  const std::vector<size_t> identity = {0, 1, 2, 3};
  stg::Permute(data, permutation);
  CHECK(data == expected);
  CHECK(permutation == identity);
}

template <typename G>
std::vector<size_t> MakePermutation(size_t k, G& gen) {
  std::vector<size_t> result(k);
  for (size_t i = 0; i < k; ++i) {
    result[i] = i;
  }
  for (size_t i = 0; i < k; ++i) {
    // pick one of [i, k)
    std::uniform_int_distribution<size_t> toss(i, k - 1);
    auto pick = toss(gen);
    using std::swap;
    swap(result[i], result[pick]);
  }
  return result;
}

TEST_CASE("randomly-generated permutations") {
  std::ranlux48 gen;
  auto seed = gen();
  // NOTES:
  //   Permutations of size 6 are plenty big enough to shake out bugs.
  ///  There are k! permutations of size k. Testing costs are O(k).
  for (size_t k = 0; k < 7; ++k) {
    const auto count = Factorial(k);
    INFO("testing with " << count << " permutations of size " << k);
    std::vector<size_t> identity(k);
    for (size_t i = 0; i < k; ++i) {
      identity[i] = i;
    }
    for (size_t n = 0; n < count; ++n, ++seed) {
      gen.seed(seed);
      auto permutation = MakePermutation(k, gen);
      std::ostringstream os;
      os << "permutation of " << k << " numbers generated using seed " << seed;
      GIVEN(os.str()) {
        // NOTE: We could test with something other than [0, k) as the data, but
        // let's just say "parametric polymorphism" and move on.
        auto permutation_copy = permutation;
        auto identity_copy = identity;
        stg::Permute(identity_copy, permutation_copy);
        for (size_t i = 0; i < k; ++i) {
          // permutation_copy should be the identity
          CHECK(permutation_copy[i] == i);
          // identity_copy should now be the same as permutation
          CHECK(identity_copy[i] == permutation[i]);
        }
      }
    }
  }
}

TEST_CASE("randomly-generating ordering sequences, fully-matching") {
  std::ranlux48 gen;
  auto seed = gen();
  // NOTES:
  //   Permutations of size 6 are plenty big enough to shake out bugs.
  ///  There are k! permutations of size k. Testing costs are O(k^2).
  for (size_t k = 0; k < 7; ++k) {
    const auto count = Factorial(k);
    INFO("testing with " << count << " random orderings of size " << k);
    for (size_t n = 0; n < count; ++n, ++seed) {
      gen.seed(seed);
      const auto order1 = MakePermutation(k, gen);
      auto order1_copy = order1;
      auto order2 = MakePermutation(k, gen);
      std::ostringstream os;
      os << "orderings of " << k << " numbers generated using seed " << seed;
      GIVEN(os.str()) {
        stg::ExtendOrder(order1_copy, order2);
        for (size_t i = 0; i < k; ++i) {
          // order1_copy should be unchanged
          CHECK(order1_copy[i] == order1[i]);
        }
      }
    }
  }
}

TEST_CASE("randomly-generating ordering sequences, disjoint") {
  std::ranlux48 gen;
  auto seed = gen();
  // NOTES:
  //   Orderings of size 4 are plenty big enough to shake out bugs.
  ///  There are k! permutations of size k. Testing costs are O(k^2).
  for (size_t k = 0; k < 5; ++k) {
    const auto count = Factorial(k);
    INFO("testing with " << count << " random orderings of size " << k);
    for (size_t n = 0; n < count; ++n, ++seed) {
      gen.seed(seed);
      const auto order1 = MakePermutation(k, gen);
      auto order1_copy = order1;
      auto order2 = MakePermutation(k, gen);
      for (size_t i = 0; i < k; ++i) {
        order2[i] += k;
      }
      const auto order2_copy = order2;
      std::ostringstream os;
      os << "orderings of " << k << " numbers generated using seed " << seed;
      GIVEN(os.str()) {
        stg::ExtendOrder(order1_copy, order2);
        for (size_t i = 0; i < k; ++i) {
          // order2 should appear as the first part
          CHECK(order1_copy[i] == order2[i]);
          // order1 should appear as the second part
          CHECK(order1_copy[i + k] == order1[i]);
        }
      }
    }
  }
}

TEST_CASE("randomly-generating ordering sequences, single overlap") {
  std::ranlux48 gen;
  auto seed = gen();
  // NOTES:
  //   Orderings of size 4 are plenty big enough to shake out bugs.
  ///  There are k! permutations of size k. Testing costs are O(k^2).
  for (size_t k = 1; k < 5; ++k) {
    const auto count = Factorial(k);
    INFO("testing with " << count << " random orderings of size " << k);
    for (size_t n = 0; n < count; ++n, ++seed) {
      gen.seed(seed);
      const auto order1 = MakePermutation(k, gen);
      auto order1_copy = order1;
      auto order2 = MakePermutation(k, gen);
      for (size_t i = 0; i < k; ++i) {
        order2[i] += k - 1;
      }
      const auto pivot = k - 1;
      const auto order2_copy = order2;
      std::ostringstream os;
      os << "orderings of " << k << " numbers generated using seed " << seed;
      GIVEN(os.str()) {
        stg::ExtendOrder(order1_copy, order2);
        CHECK(order1_copy.size() == 2 * k - 1);
        // order2 pre, order1 pre, pivot, order2 post, order1 post
        size_t ix = 0;
        size_t ix1 = 0;
        size_t ix2 = 0;
        while (order2[ix2] != pivot) {
          CHECK(order1_copy[ix++] == order2[ix2++]);
        }
        while (order1[ix1] != pivot) {
          CHECK(order1_copy[ix++] == order1[ix1++]);
        }
        ++ix2;
        ++ix1;
        CHECK(order1_copy[ix++] == pivot);
        while (ix2 < k) {
          CHECK(order1_copy[ix++] == order2[ix2++]);
        }
        while (ix1 < k) {
          CHECK(order1_copy[ix++] == order1[ix1++]);
        }
      }
    }
  }
}

TEST_CASE("hand-curated ordering sequences") {
  using Sequence = std::vector<std::string>;
  // NOTES:
  //   The output sequence MUST include the first sequence as a subsequence.
  //   The second sequence's ordering is respected as far as possible.
  std::vector<std::tuple<Sequence, Sequence, Sequence>> cases = {
    {{"rose", "george", "emily"}, {"george", "ted", "emily"},
      {"rose", "george", "ted", "emily"}},
    {{}, {}, {}},
    {{"a"}, {}, {"a"}},
    {{}, {"a"}, {"a"}},
    {{"a", "z"}, {}, {"a", "z"}},
    {{}, {"a", "z"}, {"a", "z"}},
    {{"a", "b", "c"}, {"c", "d"}, {"a", "b", "c", "d"}},
    {{"a", "b", "d"}, {"b", "c", "d"}, {"a", "b", "c", "d"}},
    {{"a", "c", "d"}, {"a", "b", "c"}, {"a", "b", "c", "d"}},
    {{"b", "c", "d"}, {"a", "b"}, {"a", "b", "c", "d"}},
    {{"a", "z"}, {"z", "a", "q"}, {"a", "z", "q"}},
  };
  for (const auto& [order1, order2, expected] : cases) {
    auto order1_copy = order1;
    auto order2_copy = order2;
    stg::ExtendOrder(order1_copy, order2_copy);
    CHECK(order1_copy == expected);
  }
}

TEST_CASE("hand-curated reorderings with input order randomisation") {
  using Constraint = std::pair<std::optional<size_t>, std::optional<size_t>>;
  using Constraints = std::vector<Constraint>;
  // NOTES:
  //   item removed at position x: {x}, {}
  //   item added at position y: {}, {y}
  //   item modified at positions x and y: {x}, {y}
  //   input item order should be irrelevant to output order
  std::vector<std::pair<Constraints, Constraints>> cases = {
    {
      {
        {{2}, {2}},  // emily
        {{1}, {0}},  // george
        {{0}, {}},   // rose
        {{}, {1}},   // ted
      },
      {
        {{0}, {}},   // rose
        {{1}, {0}},  // george
        {{}, {1}},   // ted
        {{2}, {2}},  // emily
      },
    },
    {{}, {}},
    {{{{0}, {0}}}, {{{0}, {0}}}},
    {{{{0}, {}}}, {{{0}, {}}}},
    {{{{}, {0}}}, {{{}, {0}}}},
    {{{{}, {2}}, {{}, {1}}, {{}, {0}}},
     {{{}, {0}}, {{}, {1}}, {{}, {2}}}},
    {{{{2}, {}}, {{1}, {}}, {{0}, {}}},
     {{{0}, {}}, {{1}, {}}, {{2}, {}}}},
    // int b; int c; -> int b; int a; int c
    {{{{}, {1}}, {{0}, {0}}, {{1}, {2}}},
     {{{0}, {0}}, {{}, {1}}, {{1}, {2}}}},
  };
  std::ranlux48 gen;
  auto seed = gen();
  for (const auto& [given, expected] : cases) {
    const auto k = given.size();
    const auto count = Factorial(k);
    INFO("testing with " << count << " random input orderings");
    for (size_t n = 0; n < count; ++n, ++seed) {
      gen.seed(seed);
      std::ostringstream os;
      os << "permutation of " << k << " items generated using seed " << seed;
      GIVEN(os.str()) {
        auto permutation = MakePermutation(k, gen);
        auto copy = given;
        stg::Permute(copy, permutation);
        stg::Reorder(copy);
        CHECK(copy == expected);
      }
    }
  }
}

}  // namespace Test
