// Copyright (C) 2019 Google LLC
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

#include "testing/base/public/benchmark.h"
#include "gmock/gmock.h"
#include "icing/testing/common-matchers.h"
#include "icing/testing/icu-data-file-helper.h"
#include "icing/testing/test-data.h"
#include "icing/tokenization/language-segmenter-factory.h"
#include "icing/tokenization/language-segmenter.h"
#include "icing/transform/normalizer.h"
#include "unicode/uloc.h"

// Run on a Linux workstation:
//    $ blaze build -c opt --dynamic_mode=off --copt=-gmlt
//    //icing/tokenization:language-segmenter_benchmark
//
//    $ blaze-bin/icing/tokenization/language-segmenter_benchmark
//    --benchmark_filter=all
//
// Run on an Android device:
//    Make target //icing/tokenization:language-segmenter depend on
//    //third_party/icu
//
//    $ blaze build --copt="-DGOOGLE_COMMANDLINEFLAGS_FULL_API=1"
//    --config=android_arm64 -c opt --dynamic_mode=off --copt=-gmlt
//    //icing/tokenization:language-segmenter_benchmark
//
//    $ adb push
//    blaze-bin/icing/tokenization/language-segmenter_benchmark
//    /data/local/tmp/
//
//    $ adb shell /data/local/tmp/language-segmenter_benchmark --benchmark_filter=all
//    --adb

// Flag to tell the benchmark that it'll be run on an Android device via adb,
// the benchmark will set up data files accordingly.
ABSL_FLAG(bool, adb, false, "run benchmark via ADB on an Android device");

namespace icing {
namespace lib {

namespace {

void BM_SegmentNoSpace(benchmark::State& state) {
  bool run_via_adb = absl::GetFlag(FLAGS_adb);
  if (!run_via_adb) {
    ICING_ASSERT_OK(icu_data_file_helper::SetUpICUDataFile(
        GetTestFilePath("icing/icu.dat")));
  }

  language_segmenter_factory::SegmenterOptions options(ULOC_US);
  std::unique_ptr<LanguageSegmenter> language_segmenter =
      language_segmenter_factory::Create(std::move(options)).ValueOrDie();

  std::string input_string(state.range(0), 'A');

  for (auto _ : state) {
    std::unique_ptr<LanguageSegmenter::Iterator> iterator =
        language_segmenter->Segment(input_string).ValueOrDie();
    while (iterator->Advance()) {
      iterator->GetTerm();
    }
  }
}
BENCHMARK(BM_SegmentNoSpace)
    ->Arg(1000)
    ->Arg(2000)
    ->Arg(4000)
    ->Arg(8000)
    ->Arg(16000)
    ->Arg(32000)
    ->Arg(64000)
    ->Arg(128000)
    ->Arg(256000)
    ->Arg(384000)
    ->Arg(512000)
    ->Arg(1024000)
    ->Arg(2048000)
    ->Arg(4096000);

void BM_SegmentWithSpaces(benchmark::State& state) {
  bool run_via_adb = absl::GetFlag(FLAGS_adb);
  if (!run_via_adb) {
    ICING_ASSERT_OK(icu_data_file_helper::SetUpICUDataFile(
        GetTestFilePath("icing/icu.dat")));
  }

  language_segmenter_factory::SegmenterOptions options(ULOC_US);
  std::unique_ptr<LanguageSegmenter> language_segmenter =
      language_segmenter_factory::Create(std::move(options)).ValueOrDie();

  std::string input_string(state.range(0), 'A');
  for (int i = 1; i < input_string.length(); i += 2) {
    input_string[i] = ' ';
  }

  for (auto _ : state) {
    std::unique_ptr<LanguageSegmenter::Iterator> iterator =
        language_segmenter->Segment(input_string).ValueOrDie();
    while (iterator->Advance()) {
      iterator->GetTerm();
    }
  }
}
BENCHMARK(BM_SegmentWithSpaces)
    ->Arg(1000)
    ->Arg(2000)
    ->Arg(4000)
    ->Arg(8000)
    ->Arg(16000)
    ->Arg(32000)
    ->Arg(64000)
    ->Arg(128000)
    ->Arg(256000)
    ->Arg(384000)
    ->Arg(512000)
    ->Arg(1024000)
    ->Arg(2048000)
    ->Arg(4096000);

void BM_SegmentCJK(benchmark::State& state) {
  bool run_via_adb = absl::GetFlag(FLAGS_adb);
  if (!run_via_adb) {
    ICING_ASSERT_OK(icu_data_file_helper::SetUpICUDataFile(
        GetTestFilePath("icing/icu.dat")));
  }

  language_segmenter_factory::SegmenterOptions options(ULOC_US);
  std::unique_ptr<LanguageSegmenter> language_segmenter =
      language_segmenter_factory::Create(std::move(options)).ValueOrDie();

  std::string input_string;
  while (input_string.length() < state.range(0)) {
    input_string.append("你好こんにちは안녕하세요");
  }

  for (auto _ : state) {
    std::unique_ptr<LanguageSegmenter::Iterator> iterator =
        language_segmenter->Segment(input_string).ValueOrDie();
    while (iterator->Advance()) {
      iterator->GetTerm();
    }
  }
}
BENCHMARK(BM_SegmentCJK)
    ->Arg(1000)
    ->Arg(2000)
    ->Arg(4000)
    ->Arg(8000)
    ->Arg(16000)
    ->Arg(32000)
    ->Arg(64000)
    ->Arg(128000)
    ->Arg(256000)
    ->Arg(384000)
    ->Arg(512000)
    ->Arg(1024000)
    ->Arg(2048000)
    ->Arg(4096000);

}  // namespace

}  // namespace lib
}  // namespace icing
