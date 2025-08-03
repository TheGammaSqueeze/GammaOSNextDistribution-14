// Copyright (C) 2022 The Android Open Source Project
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
#include <filesystem>
#include <string>

#include <benchmark/benchmark.h>
#include <ditto/arg_parser.h>
#include <ditto/logger.h>
#include <ditto/parser.h>

template <class... Args>
void BM_DittoBench(benchmark::State& state, Args&&... args) {
  for (auto _ : state) {
    auto args_tuple = std::make_tuple(std::move(args)...);

    std::error_code errorCode;
    auto myPath = std::filesystem::path("/proc/self/exe");
    auto executable_path = std::filesystem::canonical(myPath, errorCode);
    auto project_path = executable_path.parent_path();
    std::string ditto_path = project_path.string() + "/" + std::get<0>(args_tuple);

    // LOGW("Opening ditto file: " + ditto_path);
    //  TODO error code?

    dittosuite::CmdArguments arguments = {.results_output = dittosuite::ResultsOutput::kNull,
                                          .file_path = ditto_path};

    dittosuite::Parser::GetParser().Parse(arguments.file_path, arguments.parameters);

    auto init = dittosuite::Parser::GetParser().GetInit();
    if (init) {
      init->SetUp();
      init->Run();
      init->TearDown();
    }

    auto main = dittosuite::Parser::GetParser().GetMain();
    main->SetUp();
    main->Run();
    main->TearDown();

    auto result = main->CollectResults("");
    double duration_ns = result->GetSamples("duration")[0];
    state.SetIterationTime(duration_ns / 1000 / 1000 / 1000);

    auto clean_up = dittosuite::Parser::GetParser().GetCleanUp();
    if (clean_up) {
      clean_up->SetUp();
      clean_up->Run();
      clean_up->TearDown();
    }
  }
}

BENCHMARK_CAPTURE(BM_DittoBench, read_random_native, "example/android/read_random_native.ditto")->UseManualTime();
BENCHMARK_CAPTURE(BM_DittoBench, read_random_fuse, "example/android/read_random_fuse.ditto")->UseManualTime();
BENCHMARK_CAPTURE(BM_DittoBench, write_random_native, "example/android/write_random_native.ditto")->UseManualTime();
BENCHMARK_CAPTURE(BM_DittoBench, write_random_fuse, "example/android/write_random_fuse.ditto")->UseManualTime();

BENCHMARK_MAIN();
