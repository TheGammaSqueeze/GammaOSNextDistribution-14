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
// Author: Siddharth Nayyar

#include <filesystem>
#include <fstream>
#include <sstream>
#include <string>

#include <catch2/catch.hpp>
#include "comparison.h"
#include "graph.h"
#include "input.h"
#include "reader_options.h"
#include "reporting.h"
#include "runtime.h"

namespace {

struct IgnoreTestCase {
  const std::string name;
  const stg::InputFormat format0;
  const std::string file0;
  const stg::InputFormat format1;
  const std::string file1;
  const stg::Ignore ignore;
  const std::string expected_output;
  const bool expected_equals;
};

std::string filename_to_path(const std::string& f) {
  return std::filesystem::path("testdata") / f;
}

stg::Id Read(stg::Runtime& runtime, stg::Graph& graph, stg::InputFormat format,
             const std::string& input) {
  return stg::Read(runtime, graph, format, filename_to_path(input).c_str(),
                   stg::ReadOptions(), nullptr);
}

TEST_CASE("ignore") {
  const auto test = GENERATE(
      IgnoreTestCase(
          {"symbol type presence change",
           stg::InputFormat::ABI,
           "symbol_type_presence_0.xml",
           stg::InputFormat::ABI,
           "symbol_type_presence_1.xml",
           stg::Ignore(),
           "symbol_type_presence_small_diff",
           false}),
      IgnoreTestCase(
          {"symbol type presence change pruned",
           stg::InputFormat::ABI,
           "symbol_type_presence_0.xml",
           stg::InputFormat::ABI,
           "symbol_type_presence_1.xml",
           stg::Ignore(stg::Ignore::SYMBOL_TYPE_PRESENCE),
           "empty",
           true}),
      IgnoreTestCase(
          {"type declaration status change",
           stg::InputFormat::ABI,
           "type_declaration_status_0.xml",
           stg::InputFormat::ABI,
           "type_declaration_status_1.xml",
           stg::Ignore(),
           "type_declaration_status_small_diff",
           false}),
      IgnoreTestCase(
          {"type declaration status change pruned",
           stg::InputFormat::ABI,
           "type_declaration_status_0.xml",
           stg::InputFormat::ABI,
           "type_declaration_status_1.xml",
           stg::Ignore(stg::Ignore::TYPE_DECLARATION_STATUS),
           "empty",
           true}),
      IgnoreTestCase(
          {"primitive type encoding",
           stg::InputFormat::STG,
           "primitive_type_encoding_0.stg",
           stg::InputFormat::STG,
           "primitive_type_encoding_1.stg",
           stg::Ignore(),
           "primitive_type_encoding_small_diff",
           false}),
      IgnoreTestCase(
          {"primitive type encoding ignored",
           stg::InputFormat::STG,
           "primitive_type_encoding_0.stg",
           stg::InputFormat::STG,
           "primitive_type_encoding_1.stg",
           stg::Ignore(stg::Ignore::PRIMITIVE_TYPE_ENCODING),
           "empty",
           true}),
      IgnoreTestCase(
          {"member size",
           stg::InputFormat::STG,
           "member_size_0.stg",
           stg::InputFormat::STG,
           "member_size_1.stg",
           stg::Ignore(),
           "member_size_small_diff",
           false}),
      IgnoreTestCase(
          {"member size ignored",
           stg::InputFormat::STG,
           "member_size_0.stg",
           stg::InputFormat::STG,
           "member_size_1.stg",
           stg::Ignore(stg::Ignore::MEMBER_SIZE),
           "empty",
           true}),
      IgnoreTestCase(
          {"enum underlying type",
           stg::InputFormat::STG,
           "enum_underlying_type_0.stg",
           stg::InputFormat::STG,
           "enum_underlying_type_1.stg",
           stg::Ignore(),
           "enum_underlying_type_small_diff",
           false}),
      IgnoreTestCase(
          {"enum underlying type ignored",
           stg::InputFormat::STG,
           "enum_underlying_type_0.stg",
           stg::InputFormat::STG,
           "enum_underlying_type_1.stg",
           stg::Ignore(stg::Ignore::ENUM_UNDERLYING_TYPE),
           "empty",
           true}),
      IgnoreTestCase(
          {"qualifier",
           stg::InputFormat::STG,
           "qualifier_0.stg",
           stg::InputFormat::STG,
           "qualifier_1.stg",
           stg::Ignore(),
           "qualifier_small_diff",
           false}),
      IgnoreTestCase(
          {"qualifier ignored",
           stg::InputFormat::STG,
           "qualifier_0.stg",
           stg::InputFormat::STG,
           "qualifier_1.stg",
           stg::Ignore(stg::Ignore::QUALIFIER),
           "empty",
           true}),
      IgnoreTestCase(
          {"CRC change",
           stg::InputFormat::STG,
           "crc_change_0.stg",
           stg::InputFormat::STG,
           "crc_change_1.stg",
           stg::Ignore(),
           "crc_change_small_diff",
           false}),
      IgnoreTestCase(
          {"CRC change ignored",
           stg::InputFormat::STG,
           "crc_change_0.stg",
           stg::InputFormat::STG,
           "crc_change_1.stg",
           stg::Ignore(stg::Ignore::SYMBOL_CRC),
           "empty",
           true}),
      IgnoreTestCase(
          {"interface addition",
           stg::InputFormat::STG,
           "interface_addition_0.stg",
           stg::InputFormat::STG,
           "interface_addition_1.stg",
           stg::Ignore(),
           "interface_addition_small_diff",
           false}),
      IgnoreTestCase(
          {"interface addition ignored",
           stg::InputFormat::STG,
           "interface_addition_0.stg",
           stg::InputFormat::STG,
           "interface_addition_1.stg",
           stg::Ignore(stg::Ignore::INTERFACE_ADDITION),
           "empty",
           true}),
      IgnoreTestCase(
          {"type addition",
           stg::InputFormat::STG,
           "type_addition_0.stg",
           stg::InputFormat::STG,
           "type_addition_1.stg",
           stg::Ignore(),
           "type_addition_small_diff",
           false}),
      IgnoreTestCase(
          {"type addition ignored",
           stg::InputFormat::STG,
           "type_addition_0.stg",
           stg::InputFormat::STG,
           "type_addition_1.stg",
           stg::Ignore(stg::Ignore::INTERFACE_ADDITION),
           "empty",
           true}),
      IgnoreTestCase(
          {"type definition addition",
           stg::InputFormat::STG,
           "type_addition_1.stg",
           stg::InputFormat::STG,
           "type_addition_2.stg",
           stg::Ignore(),
           "type_definition_addition_small_diff",
           false}),
      IgnoreTestCase(
          {"type definition addition ignored",
           stg::InputFormat::STG,
           "type_addition_1.stg",
           stg::InputFormat::STG,
           "type_addition_2.stg",
           stg::Ignore(stg::Ignore::TYPE_DEFINITION_ADDITION),
           "empty",
           true})
      );

  SECTION(test.name) {
    std::ostringstream os;
    stg::Runtime runtime(os, false);

    // Read inputs.
    stg::Graph graph;
    const auto id0 = Read(runtime, graph, test.format0, test.file0);
    const auto id1 = Read(runtime, graph, test.format1, test.file1);

    // Compute differences.
    stg::Compare compare{runtime, graph, test.ignore};
    const auto& [equals, comparison] = compare(id0, id1);

    // Write SMALL reports.
    std::ostringstream output;
    if (comparison) {
      stg::NameCache names;
      stg::reporting::Options options{stg::reporting::OutputFormat::SMALL, 0};
      stg::reporting::Reporting reporting{graph, compare.outcomes, options,
                                          names};
      Report(reporting, *comparison, output);
    }

    // Check comparison outcome and report output.
    CHECK(equals == test.expected_equals);
    std::ifstream expected_output_file(filename_to_path(test.expected_output));
    std::ostringstream expected_output;
    expected_output << expected_output_file.rdbuf();
    CHECK(output.str() == expected_output.str());
  }
}

struct ShortReportTestCase {
  const std::string name;
  const std::string xml0;
  const std::string xml1;
  const std::string expected_output;
};

TEST_CASE("short report") {
  const auto test = GENERATE(
      ShortReportTestCase(
          {"crc changes", "crc_0.xml", "crc_1.xml", "crc_changes_short_diff"}),
      ShortReportTestCase({"only crc changes", "crc_only_0.xml",
                           "crc_only_1.xml", "crc_only_changes_short_diff"}),
      ShortReportTestCase({"offset changes", "offset_0.xml", "offset_1.xml",
                           "offset_changes_short_diff"}),
      ShortReportTestCase(
          {"symbols added and removed", "added_removed_symbols_0.xml",
           "added_removed_symbols_1.xml", "added_removed_symbols_short_diff"}),
      ShortReportTestCase({"symbols added and removed only",
                           "added_removed_symbols_only_0.xml",
                           "added_removed_symbols_only_1.xml",
                           "added_removed_symbols_only_short_diff"}));

  SECTION(test.name) {
    std::ostringstream os;
    stg::Runtime runtime(os, false);

    // Read inputs.
    stg::Graph graph;
    const auto id0 = Read(runtime, graph, stg::InputFormat::ABI, test.xml0);
    const auto id1 = Read(runtime, graph, stg::InputFormat::ABI, test.xml1);

    // Compute differences.
    stg::Compare compare{runtime, graph, {}};
    const auto& [equals, comparison] = compare(id0, id1);

    // Write SHORT reports.
    std::stringstream output;
    if (comparison) {
      stg::NameCache names;
      stg::reporting::Options options{stg::reporting::OutputFormat::SHORT, 2};
      stg::reporting::Reporting reporting{graph, compare.outcomes, options,
                                          names};
      Report(reporting, *comparison, output);
    }

    // Check comparison outcome and report output.
    CHECK(equals == false);
    std::ifstream expected_output_file(filename_to_path(test.expected_output));
    std::ostringstream expected_output;
    expected_output << expected_output_file.rdbuf();
    CHECK(output.str() == expected_output.str());
  }
}

TEST_CASE("fidelity diff") {
  std::ostringstream os;
  stg::Runtime runtime(os, false);

  // Read inputs.
  stg::Graph graph;
  const auto id0 =
      Read(runtime, graph, stg::InputFormat::STG, "fidelity_diff_0.stg");
  const auto id1 =
      Read(runtime, graph, stg::InputFormat::STG, "fidelity_diff_1.stg");

  // Compute fidelity diff.
  auto fidelity_diff = stg::GetFidelityTransitions(graph, id0, id1);

  // Write fidelity diff report.
  std::ostringstream report;
  stg::reporting::FidelityDiff(fidelity_diff, report);

  // Check report.
  std::ifstream expected_report_file(filename_to_path("fidelity_diff_report"));
  std::ostringstream expected_report;
  expected_report << expected_report_file.rdbuf();
  CHECK(report.str() == expected_report.str());
}

}  // namespace
