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

#include <memory>

#include "testing/base/public/benchmark.h"
#include "icing/testing/common-matchers.h"
#include "icing/transform/normalizer-factory.h"
#include "icing/transform/normalizer.h"

// Run on a Linux workstation:
//    $ blaze build -c opt --dynamic_mode=off --copt=-gmlt
//    //icing/transform/map:map-normalizer_benchmark
//
//    $ blaze-bin/icing/transform/map/map-normalizer_benchmark
//    --benchmark_filter=all
//
// Run on an Android device:
//    $ blaze build --copt="-DGOOGLE_COMMANDLINEFLAGS_FULL_API=1"
//    --config=android_arm64 -c opt --dynamic_mode=off --copt=-gmlt
//    //icing/transform/map:map-normalizer_benchmark
//
//    $ adb push
//    blaze-bin/icing/transform/map/map-normalizer_benchmark
//    /data/local/tmp/
//
//    $ adb shell /data/local/tmp/map-normalizer_benchmark --benchmark_filter=all
namespace icing {
namespace lib {

namespace {

void BM_NormalizeUppercase(benchmark::State& state) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<Normalizer> normalizer,
      normalizer_factory::Create(
          /*max_term_byte_size=*/std::numeric_limits<int>::max()));

  std::string input_string(state.range(0), 'A');

  // Warms up. map-normalizer may need to load a static map when being invoked
  // the first time. It takes about 0.05ms on a Pixel3 XL.
  normalizer->NormalizeTerm(input_string);

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
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<Normalizer> normalizer,
      normalizer_factory::Create(
          /*max_term_byte_size=*/std::numeric_limits<int>::max()));

  std::string input_string;
  while (input_string.length() < state.range(0)) {
    input_string.append("àáâãā");
  }

  // Warms up. map-normalizer may need to load a static map when being invoked
  // the first time. It takes about 0.05ms on a Pixel3 XL.
  normalizer->NormalizeTerm(input_string);

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

void BM_NormalizeHiragana(benchmark::State& state) {
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<Normalizer> normalizer,
      normalizer_factory::Create(
          /*max_term_byte_size=*/std::numeric_limits<int>::max()));

  std::string input_string;
  while (input_string.length() < state.range(0)) {
    input_string.append("あいうえお");
  }

  // Warms up. map-normalizer may need to load a static map when being invoked
  // the first time. It takes about 0.05ms on a Pixel3 XL.
  normalizer->NormalizeTerm(input_string);

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
