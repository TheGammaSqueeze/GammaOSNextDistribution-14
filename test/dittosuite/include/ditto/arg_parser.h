// Copyright (C) 2021 The Android Open Source Project
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

#pragma once

#include <ditto/logger.h>
#include <ditto/parser.h>
#include <ditto/result.h>

#include <getopt.h>
#include <unistd.h>

#include <cstring>

namespace dittosuite {
struct CmdArguments {
  ResultsOutput results_output = ResultsOutput::kReport;
  std::string file_path;
  std::vector<std::string> parameters;
};
CmdArguments ParseArguments(int argc, char** argv);
ResultsOutput ArgToResultsOutput(const std::string_view optarg);

}  // namespace dittosuite
