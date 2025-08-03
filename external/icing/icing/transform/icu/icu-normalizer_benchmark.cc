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
#include "icing/transform/normalizer-factory.h"
#include "icing/transform/normalizer.h"

// Run on a Linux workstation:
//    $ blaze build -c opt --dynamic_mode=off --copt=-gmlt
//    //icing/transform/icu:icu-normalizer_benchmark
//
//    $ blaze-bin/icing/transform/icu/icu-normalizer_benchmark
//    --benchmark_filter=all
//
// Run on an Android device:
//    Make target //icing/transform:normalizer depend on
//    //third_party/icu
//
//    $ blaze build --copt="-DGOOGLE_COMMANDLINEFLAGS_FULL_API=1"
//    --config=android_arm64 -c opt --dynamic_mode=off --copt=-gmlt
//    //icing/transform/icu:icu-normalizer_benchmark
//
//    $ adb push
//    blaze-bin/icing/transform/icu/icu-normalizer_benchmark
//    /data/local/tmp/
//
//    $ adb shell /data/local/tmp/icu-normalizer_benchmark
//    --benchmark_filter=all --adb

// Flag to tell the benchmark that it'll be run on an Android device via adb,
// the benchmark will set up data files accordingly.
ABSL_FLAG(bool, adb, false, "run benchmark via ADB on an Android device");

namespace icing {
namespace lib {

namespace {

void BM_NormalizeUppercase(benchmark::State& state) {
  bool run_via_adb = absl::GetFlag(FLAGS_adb);
  if (!run_via_adb) {
    ICING_ASSERT_OK(icu_data_file_helper::SetUpICUDataFile(
        GetTestFilePath("icing/icu.dat")));
  }

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<Normalizer> normalizer,
      normalizer_factory::Create(
          /*max_term_byte_size=*/std::numeric_limits<int>::max()));

  std::string input_string(state.range(0), 'A');
  for (auto _ : state) {
    normalizer->NormalizeTerm(input_string);
  }
}
BENCHMARK(BM_NormalizeUppercase)
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

void BM_NormalizeAccent(benchmark::State& state) {
  bool run_via_adb = absl::GetFlag(FLAGS_adb);
  if (!run_via_adb) {
    ICING_ASSERT_OK(icu_data_file_helper::SetUpICUDataFile(
        GetTestFilePath("icing/icu.dat")));
  }

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<Normalizer> normalizer,
      normalizer_factory::Create(
          /*max_term_byte_size=*/std::numeric_limits<int>::max()));

  std::string input_string;
  while (input_string.length() < state.range(0)) {
    input_string.append("àáâãā");
  }

  for (auto _ : state) {
    normalizer->NormalizeTerm(input_string);
  }
}
BENCHMARK(BM_NormalizeAccent)
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

void BM_NormalizeGreekAccent(benchmark::State& state) {
  bool run_via_adb = absl::GetFlag(FLAGS_adb);
  if (!run_via_adb) {
    ICING_ASSERT_OK(icu_data_file_helper::SetUpICUDataFile(
        GetTestFilePath("icing/icu.dat")));
  }

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<Normalizer> normalizer,
      normalizer_factory::Create(
          /*max_term_byte_size=*/std::numeric_limits<int>::max()));

  std::string input_string;
  while (input_string.length() < state.range(0)) {
    input_string.append("άὰᾶἀἄ");
  }

  for (auto _ : state) {
    normalizer->NormalizeTerm(input_string);
  }
}
BENCHMARK(BM_NormalizeGreekAccent)
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

void BM_NormalizeHiragana(benchmark::State& state) {
  bool run_via_adb = absl::GetFlag(FLAGS_adb);
  if (!run_via_adb) {
    ICING_ASSERT_OK(icu_data_file_helper::SetUpICUDataFile(
        GetTestFilePath("icing/icu.dat")));
  }

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<Normalizer> normalizer,
      normalizer_factory::Create(
          /*max_term_byte_size=*/std::numeric_limits<int>::max()));

  std::string input_string;
  while (input_string.length() < state.range(0)) {
    input_string.append("あいうえお");
  }

  for (auto _ : state) {
    normalizer->NormalizeTerm(input_string);
  }
}
BENCHMARK(BM_NormalizeHiragana)
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

void BM_UppercaseSubTokenLength(benchmark::State& state) {
  bool run_via_adb = absl::GetFlag(FLAGS_adb);
  if (!run_via_adb) {
    ICING_ASSERT_OK(icu_data_file_helper::SetUpICUDataFile(
        GetTestFilePath("icing/icu.dat")));
  }

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<Normalizer> normalizer,
      normalizer_factory::Create(
          /*max_term_byte_size=*/std::numeric_limits<int>::max()));

  std::string input_string(state.range(0), 'A');
  std::string normalized_input_string(state.range(0), 'a');
  for (auto _ : state) {
    normalizer->FindNormalizedMatchEndPosition(input_string,
                                               normalized_input_string);
  }
}
BENCHMARK(BM_UppercaseSubTokenLength)
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

void BM_AccentSubTokenLength(benchmark::State& state) {
  bool run_via_adb = absl::GetFlag(FLAGS_adb);
  if (!run_via_adb) {
    ICING_ASSERT_OK(icu_data_file_helper::SetUpICUDataFile(
        GetTestFilePath("icing/icu.dat")));
  }

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<Normalizer> normalizer,
      normalizer_factory::Create(
          /*max_term_byte_size=*/std::numeric_limits<int>::max()));

  std::string input_string;
  std::string normalized_input_string;
  while (input_string.length() < state.range(0)) {
    input_string.append("àáâãā");
    normalized_input_string.append("aaaaa");
  }

  for (auto _ : state) {
    normalizer->FindNormalizedMatchEndPosition(input_string,
                                               normalized_input_string);
  }
}
BENCHMARK(BM_AccentSubTokenLength)
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

void BM_HiraganaSubTokenLength(benchmark::State& state) {
  bool run_via_adb = absl::GetFlag(FLAGS_adb);
  if (!run_via_adb) {
    ICING_ASSERT_OK(icu_data_file_helper::SetUpICUDataFile(
        GetTestFilePath("icing/icu.dat")));
  }

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<Normalizer> normalizer,
      normalizer_factory::Create(
          /*max_term_byte_size=*/std::numeric_limits<int>::max()));

  std::string input_string;
  std::string normalized_input_string;
  while (input_string.length() < state.range(0)) {
    input_string.append("あいうえお");
    normalized_input_string.append("アイウエオ");
  }

  for (auto _ : state) {
    normalizer->FindNormalizedMatchEndPosition(input_string,
                                               normalized_input_string);
  }
}
BENCHMARK(BM_HiraganaSubTokenLength)
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
