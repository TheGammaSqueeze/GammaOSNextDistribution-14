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

#include <getopt.h>
#include <unistd.h>

#include <cstring>
#include <string>

#include <ditto/arg_parser.h>
#include <ditto/instruction.h>
#include <ditto/logger.h>
#include <ditto/parser.h>
#include <ditto/tracer.h>

int main(int argc, char** argv) {
  dittosuite::Tracer tracer;
  dittosuite::CmdArguments arguments = dittosuite::ParseArguments(argc, argv);
  dittosuite::Instruction::SetArgv(argv);
  dittosuite::Instruction::SetArgc(argc);

  auto benchmark = dittosuite::Parser::GetParser().Parse(arguments.file_path, arguments.parameters);
  tracer.StartSession(std::move(benchmark));

  auto init = dittosuite::Parser::GetParser().GetInit();
  if (init != nullptr) {
    init->SetUp();
    init->Run();
    init->TearDown();
  }

  auto main = dittosuite::Parser::GetParser().GetMain();
  main->SetUp();
  tracer.Start("Benchmark");
  main->Run();
  tracer.End("Benchmark");
  main->TearDown();

  auto result = main->CollectResults("");
  result->Print(arguments.results_output, "");

  auto clean_up = dittosuite::Parser::GetParser().GetCleanUp();
  if (clean_up != nullptr) {
    clean_up->SetUp();
    clean_up->Run();
    clean_up->TearDown();
  }
  return 0;
}
