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

#include "filter.h"

#include <string>
#include <tuple>
#include <vector>

#include <catch2/catch.hpp>

namespace Test {

TEST_CASE("bad syntax cases") {
  const std::vector<std::string> cases = {
    "",
    "\x1B",
    ":",
    "a:",
    "!",
    "a!",
    "(a",
    "(",
    ")",
    "()",
    "(a))",
    "&",
    "&a",
    "a&",
    "|",
    "|a",
    "|&",
    "a||b",
    "a&&b",
  };

  for (const auto& expression : cases) {
    GIVEN("filter: " + expression) {
      CHECK_THROWS(stg::MakeFilter(expression));
    }
  }
}

TEST_CASE("hand-curated cases") {
  const std::string testdata = "testdata";
  const std::vector<
      std::tuple<std::string,
                 std::vector<std::string>,
                 std::vector<std::string>>> cases = {
    {"a",           {"a"}, {"b"}},
    {"! a",         {"b"}, {"a"}},
    {"a | b",       {"a", "b"}, {"c"}},
    {"a & b",       {}, {"a", "b", "c"}},
    {"! a | b",     {"b", "c"}, {"a"}},
    {"! a & b",     {"b"}, {"a", "c"}},
    {" a | ! b",    {"a", "c"}, {"b"}},
    {" a & ! b",    {"a"}, {"b", "c"}},
    {"! a | ! b",   {"a", "b", "c"}, {}},
    {"! a & ! b",   {"c"}, {"a", "b"}},
    {"!(a | b)",    {"c"}, {"a", "b"}},
    {"!(a & b)",    {"a", "b", "c"}, {}},
    {"a & b | c",   {"c"}, {"a", "b"}},
    {"a | b & c",   {"a"}, {"b", "c"}},
    {"!a & b | c",  {"b", "c"}, {"a"}},
    {"!a | b & c",  {"b", "c"}, {"a"}},
    {"a & !b | c",  {"a", "c"}, {"b"}},
    {"a | !b & c",  {"a", "c"}, {"b"}},
    {"a & b | !c",  {"a", "b"}, {"c"}},
    {"a | b & !c",  {"a", "b"}, {"c"}},
    {"!*",          {}, {"", "a", "ab"}},
    {"*",           {"", "a", "ab"}, {}},
    {"a*",          {"a", "ab", "abc"}, {"", "b", "ba"}},
    {"a?",          {"aa", "ab"}, {"", "a", "aaa"}},
    {"*c",          {"c", "ac", "abc"}, {"", "a", "ca"}},
    {"?c",          {"ac"}, {"", "c", "ca", "abc"}},
    {"!(a)",        {"b"}, {"a"}},
    {"!(!(a))",     {"a"}, {"b"}},
    {"!(!(!(a)))",  {"b"}, {"a"}},
    {"!a",          {"b"}, {"a"}},
    {"!!a",         {"a"}, {"b"}},
    {"!!!a",        {"b"}, {"a"}},
    {":/dev/null",  {}, {"", "a"}},
    {"!:/dev/null", {"", "a"}, {}},
    {":" + testdata + "/symbol_list", {"one"}, {"#", "bad"}},
    {"!:" + testdata + "/symbol_list", {"", " "}, {"two"}},
  };

  for (const auto& [expression, ins, outs] : cases) {
    GIVEN("filter: " + expression) {
      auto filter = stg::MakeFilter(expression);
      for (const auto& in : ins) {
        GIVEN("in: " + in) {
          CHECK((*filter)(in));
        }
      }
      for (const auto& out : outs) {
        GIVEN("out: " + out) {
          CHECK(!(*filter)(out));
        }
      }
    }
  }
}

}  // namespace Test
