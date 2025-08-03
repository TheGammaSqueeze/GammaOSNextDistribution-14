// Copyright (C) 2023 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#include <ditto/arg_parser.h>
#include <gtest/gtest.h>


namespace dittosuite {

class DittoResult : public testing::TestWithParam<std::tuple<ResultsOutput, std::string_view>> {};

TEST_P(DittoResult, ArgParseString) {
  auto const& param = GetParam();
  ASSERT_EQ(ArgToResultsOutput(std::get<1>(param)), (std::get<0>(param)));
}

INSTANTIATE_TEST_SUITE_P(String, DittoResult,
                         testing::Values(std::make_tuple(ResultsOutput::kReport, "report"),
                                         std::make_tuple(ResultsOutput::kCsv, "csv"),
                                         std::make_tuple(ResultsOutput::kPb, "pb"),
                                         std::make_tuple(ResultsOutput::kNull, "null")));

INSTANTIATE_TEST_SUITE_P(Number, DittoResult,
                         testing::Values(std::make_tuple(ResultsOutput::kReport, "0"),
                                         std::make_tuple(ResultsOutput::kCsv, "1"),
                                         std::make_tuple(ResultsOutput::kPb, "2"),
                                         std::make_tuple(ResultsOutput::kNull, "-1")));

INSTANTIATE_TEST_SUITE_P(Invalid, DittoResult,
                         testing::Values(std::make_tuple(ResultsOutput::kReport, "UNKNOWN"),
                                         std::make_tuple(ResultsOutput::kReport, "-2"),
                                         std::make_tuple(ResultsOutput::kReport, "3")));

}  // namespace dittosuite
