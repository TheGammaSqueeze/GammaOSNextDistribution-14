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
// Author: Giuliano Procida

#include <cstddef>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <optional>
#include <ostream>
#include <sstream>
#include <vector>

#include <catch2/catch.hpp>
#include "abigail_reader.h"
#include "equality.h"
#include "graph.h"
#include "runtime.h"

namespace {

std::filesystem::path filename_to_path(const char* f) {
  return std::filesystem::path("testdata") / f;
}

stg::abixml::Document Read(const char* input) {
  stg::Runtime runtime(std::cerr, false);
  return stg::abixml::Read(runtime, filename_to_path(input));
}

stg::Id Read(stg::Graph& graph, const char* input) {
  stg::Runtime runtime(std::cerr, false);
  return stg::abixml::Read(runtime, graph, filename_to_path(input));
}

struct EqualTreeTestCase {
  const char* name;
  const char* left;
  const char* right;
  bool equal;
};

TEST_CASE("EqualTree") {
  const auto test = GENERATE(
      EqualTreeTestCase(
          {"cleaning",
           "abigail_dirty.xml",
           "abigail_clean.xml",
           true}),
      EqualTreeTestCase(
          {"self comparison",
           "abigail_tree_0.xml",
           "abigail_tree_0.xml",
           true}),
      EqualTreeTestCase(
          {"attribute order is irrelevant",
           "abigail_tree_0.xml",
           "abigail_tree_1.xml",
           true}),
      EqualTreeTestCase(
          {"element order is relevant",
           "abigail_tree_0.xml",
           "abigail_tree_2.xml",
           false}),
      EqualTreeTestCase(
          {"attribute missing",
           "abigail_tree_0.xml",
           "abigail_tree_3.xml",
           false}),
      EqualTreeTestCase(
          {"element missing",
           "abigail_tree_0.xml",
           "abigail_tree_4.xml",
           false}),
      EqualTreeTestCase(
          {"attribute changed",
           "abigail_tree_0.xml",
           "abigail_tree_5.xml",
           false}),
      EqualTreeTestCase(
          {"element changed",
           "abigail_tree_0.xml",
           "abigail_tree_6.xml",
           false}));

  SECTION(test.name) {
    const stg::abixml::Document left_document = Read(test.left);
    const stg::abixml::Document right_document = Read(test.right);
    xmlNodePtr left_root = xmlDocGetRootElement(left_document.get());
    xmlNodePtr right_root = xmlDocGetRootElement(right_document.get());
    stg::abixml::Clean(left_root);
    stg::abixml::Clean(right_root);
    CHECK(stg::abixml::EqualTree(left_root, right_root) == test.equal);
    CHECK(stg::abixml::EqualTree(right_root, left_root) == test.equal);
  }
}

struct SubTreeTestCase {
  const char* name;
  const char* left;
  const char* right;
  bool left_sub_right;
  bool right_sub_left;
};

TEST_CASE("SubTree") {
  const auto test = GENERATE(
      SubTreeTestCase(
          {"self comparison",
           "abigail_tree_0.xml",
           "abigail_tree_0.xml",
           true, true}),
      SubTreeTestCase(
          {"attribute missing",
           "abigail_tree_0.xml",
           "abigail_tree_3.xml",
           false, true}),
      SubTreeTestCase(
          {"element missing",
           "abigail_tree_0.xml",
           "abigail_tree_4.xml",
           false, true}),
      SubTreeTestCase(
          {"member-type access special case",
           "abigail_tree_0.xml",
           "abigail_tree_7.xml",
           true, true}));

  SECTION(test.name) {
    const stg::abixml::Document left_document = Read(test.left);
    const stg::abixml::Document right_document = Read(test.right);
    xmlNodePtr left_root = xmlDocGetRootElement(left_document.get());
    xmlNodePtr right_root = xmlDocGetRootElement(right_document.get());
    stg::abixml::Clean(left_root);
    stg::abixml::Clean(right_root);
    CHECK(stg::abixml::SubTree(left_root, right_root) == test.left_sub_right);
    CHECK(stg::abixml::SubTree(right_root, left_root) == test.right_sub_left);
  }
}

struct TidyTestCase {
  const char* name;
  const std::vector<const char*> files;
};

TEST_CASE("Tidy") {
  const auto test = GENERATE(
      TidyTestCase(
          {"bad DWARF ELF link",
           {"abigail_bad_dwarf_elf_link_0.xml",
            "abigail_bad_dwarf_elf_link_1.xml",
            "abigail_bad_dwarf_elf_link_2.xml"}}),
      TidyTestCase(
          {"anonymous type normalisation",
           {"abigail_anonymous_types_0.xml",
            "abigail_anonymous_types_1.xml",
            "abigail_anonymous_types_2.xml",
            "abigail_anonymous_types_3.xml",
            "abigail_anonymous_types_4.xml"}}),
      TidyTestCase(
          {"duplicate data members",
           {"abigail_duplicate_data_members_0.xml",
            "abigail_duplicate_data_members_1.xml"}}),
      TidyTestCase(
          {"duplicate type resolution - exact duplicate",
           {"abigail_duplicate_types_0.xml",
            "abigail_duplicate_types_1.xml"}}),
      TidyTestCase(
          {"duplicate type resolution - partial duplicate",
           {"abigail_duplicate_types_0.xml",
            "abigail_duplicate_types_2.xml"}}),
      TidyTestCase(
          {"duplicate type resolution - multiple partial duplicates",
           {"abigail_duplicate_types_0.xml",
            "abigail_duplicate_types_3.xml"}}),
      TidyTestCase(
          {"duplicate type resolution - no maximal duplicate",
           {"abigail_duplicate_types_4.xml",
            "abigail_duplicate_types_5.xml"}}),
      TidyTestCase(
          {"duplicate type resolution - different scopes",
           {"abigail_duplicate_types_4.xml",
            "abigail_duplicate_types_6.xml"}}),
      TidyTestCase(
          {"duplicate type resolution - stray anonymous member",
           {"abigail_duplicate_types_7.xml",
            "abigail_duplicate_types_8.xml"}}),
      TidyTestCase(
          {"corpus group handling",
           {"abigail_duplicate_types_0.xml",
            "abigail_duplicate_types_9.xml"}}));

  SECTION(test.name) {
    // Read inputs.
    stg::Graph graph;
    std::vector<stg::Id> ids;
    ids.reserve(test.files.size());
    for (const char* file : test.files) {
      ids.push_back(Read(graph, file));
    }

    // Useless equality cache.
    struct NoCache {
      static std::optional<bool> Query(const stg::Pair&) {
        return std::nullopt;
      }
      void AllSame(const std::vector<stg::Pair>&) {}
      void AllDifferent(const std::vector<stg::Pair>&) {}
    };

    // Check exact equality.
    NoCache cache;
    for (size_t ix = 1; ix < ids.size(); ++ix) {
      CHECK(stg::Equals<NoCache>(graph, cache)(ids[0], ids[ix]));
    }
  }
}

}  // namespace
