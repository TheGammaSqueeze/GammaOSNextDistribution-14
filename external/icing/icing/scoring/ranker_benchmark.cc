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

#include <cstdlib>
#include <random>

#include "testing/base/public/benchmark.h"
#include "icing/scoring/ranker.h"
#include "icing/scoring/scored-document-hit.h"

namespace icing {
namespace lib {

namespace {
// Run on a Linux workstation:
//    $ blaze build -c opt --dynamic_mode=off --copt=-gmlt
//    //icing/scoring:ranker_benchmark
//
//    $ blaze-bin/icing/scoring/ranker_benchmark --benchmark_filter=all
//    --benchmark_memory_usage
//
// Run on an Android device:
//    $ blaze build --copt="-DGOOGLE_COMMANDLINEFLAGS_FULL_API=1"
//    --config=android_arm64 -c opt --dynamic_mode=off --copt=-gmlt
//    //icing/scoring:ranker_benchmark
//
//    $ adb push blaze-bin/icing/scoring/ranker_benchmark
//    /data/local/tmp/
//
//    $ adb shell /data/local/tmp/ranker_benchmark --benchmark_filter=all

void BM_GetTopN(benchmark::State& state) {
  int num_to_score = state.range(0);
  int num_to_return = state.range(1);

  std::mt19937_64 random_generator;
  std::uniform_real_distribution<double> distribution(
      1, std::numeric_limits<double>::max());

  std::vector<ScoredDocumentHit> scored_document_hits;
  scored_document_hits.reserve(num_to_score);
  for (int i = 0; i < num_to_score; i++) {
    scored_document_hits.emplace_back(/*document_id=*/0,
                                      /*hit_section_id_mask=*/0,
                                      /*score=*/distribution(random_generator));
  }

  const ScoredDocumentHitComparator scored_document_hit_comparator(
      /*is_descending=*/true);

  for (auto _ : state) {
    // Pauses timer so that the cost of copying data is not included.
    state.PauseTiming();
    std::vector<ScoredDocumentHit> scored_document_hits_copy =
        scored_document_hits;
    state.ResumeTiming();

    BuildHeapInPlace(&scored_document_hits_copy,
                     scored_document_hit_comparator);
    auto result =
        PopTopResultsFromHeap(&scored_document_hits_copy, num_to_return,
                              scored_document_hit_comparator);
  }
}
BENCHMARK(BM_GetTopN)
    ->ArgPair(1000, 10)  // (num_to_score, num_to_return)
    ->ArgPair(3000, 10)
    ->ArgPair(5000, 10)
    ->ArgPair(7000, 10)
    ->ArgPair(9000, 10)
    ->ArgPair(11000, 10)
    ->ArgPair(13000, 10)
    ->ArgPair(15000, 10)
    ->ArgPair(17000, 10)
    ->ArgPair(19000, 10)
    ->ArgPair(1000, 20)
    ->ArgPair(3000, 20)
    ->ArgPair(5000, 20)
    ->ArgPair(7000, 20)
    ->ArgPair(9000, 20)
    ->ArgPair(11000, 20)
    ->ArgPair(13000, 20)
    ->ArgPair(15000, 20)
    ->ArgPair(17000, 20)
    ->ArgPair(19000, 20)
    ->ArgPair(1000, 30)
    ->ArgPair(3000, 30)
    ->ArgPair(5000, 30)
    ->ArgPair(7000, 30)
    ->ArgPair(9000, 30)
    ->ArgPair(11000, 30)
    ->ArgPair(13000, 30)
    ->ArgPair(15000, 30)
    ->ArgPair(17000, 30)
    ->ArgPair(19000, 30);

void BM_PopTopResultsFromHeap(benchmark::State& state) {
  int num_to_score = state.range(0);
  int num_to_return = state.range(1);

  std::mt19937_64 random_generator;
  std::uniform_real_distribution<double> distribution(
      1, std::numeric_limits<double>::max());

  std::vector<ScoredDocumentHit> scored_document_hits;
  scored_document_hits.reserve(num_to_score);
  for (int i = 0; i < num_to_score; i++) {
    scored_document_hits.emplace_back(/*document_id=*/0,
                                      /*hit_section_id_mask=*/0,
                                      /*score=*/distribution(random_generator));
  }

  const ScoredDocumentHitComparator scored_document_hit_comparator(
      /*is_descending=*/true);

  for (auto _ : state) {
    // Pauses timer so that the cost of copying data and building a heap are not
    // included.
    state.PauseTiming();
    std::vector<ScoredDocumentHit> scored_document_hits_copy =
        scored_document_hits;
    BuildHeapInPlace(&scored_document_hits_copy,
                     scored_document_hit_comparator);
    state.ResumeTiming();

    auto result =
        PopTopResultsFromHeap(&scored_document_hits_copy, num_to_return,
                              scored_document_hit_comparator);
  }
}
BENCHMARK(BM_PopTopResultsFromHeap)
    ->ArgPair(20000, 100)  // (num_to_score, num_to_return)
    ->ArgPair(20000, 300)
    ->ArgPair(20000, 500)
    ->ArgPair(20000, 700)
    ->ArgPair(20000, 900)
    ->ArgPair(20000, 1100)
    ->ArgPair(20000, 1300)
    ->ArgPair(20000, 1500)
    ->ArgPair(20000, 1700)
    ->ArgPair(20000, 1900);
}  // namespace

}  // namespace lib
}  // namespace icing
