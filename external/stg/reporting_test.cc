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
// Author: Siddharth Nayyar

#include "reporting.h"

#include <filesystem>
#include <fstream>
#include <sstream>
#include <string>

#include <catch2/catch.hpp>
#include "fidelity.h"

namespace stg {
namespace {

std::string filename_to_path(const std::string& f) {
  return std::filesystem::path("testdata") / f;
}

TEST_CASE("fidelity diff") {
  stg::FidelityDiff diff = {
      .symbol_transitions =
          {
              {{SymbolFidelity::TYPED, SymbolFidelity::UNTYPED},
               {"symbol1", "symbol2"}},
              {{SymbolFidelity::UNTYPED, SymbolFidelity::TYPED}, {"symbol3"}},
              {{SymbolFidelity::ABSENT, SymbolFidelity::UNTYPED},
               {"symbol4", "symbol5"}},
              {{SymbolFidelity::ABSENT, SymbolFidelity::TYPED}, {"symbol6"}},
              {{SymbolFidelity::TYPED, SymbolFidelity::ABSENT},
               {"symbol7", "symbol8"}},
              {{SymbolFidelity::UNTYPED, SymbolFidelity::ABSENT}, {"symbol9"}},
          },
      .type_transitions =
          {{{TypeFidelity::FULLY_DEFINED, TypeFidelity::ABSENT},
            {"struct s1", "union u1"}},
           {{TypeFidelity::DECLARATION_ONLY, TypeFidelity::ABSENT},
            {"struct s2"}},
           {{TypeFidelity::FULLY_DEFINED, TypeFidelity::DECLARATION_ONLY},
            {"enum e1"}},
           {{TypeFidelity::ABSENT, TypeFidelity::DECLARATION_ONLY},
            {"union u2"}},
           {{TypeFidelity::DECLARATION_ONLY, TypeFidelity::FULLY_DEFINED},
            {"enum e2", "union u3"}},
           {{TypeFidelity::ABSENT, TypeFidelity::FULLY_DEFINED},
            {"struct s3"}}},
  };

  std::ostringstream report;
  CHECK(reporting::FidelityDiff(diff, report));

  std::ifstream expected_report_file(filename_to_path("fidelity_diff_report"));
  std::ostringstream expected_report;
  expected_report << expected_report_file.rdbuf();
  CHECK(report.str() == expected_report.str());
}

}  // namespace
}  // namespace stg
