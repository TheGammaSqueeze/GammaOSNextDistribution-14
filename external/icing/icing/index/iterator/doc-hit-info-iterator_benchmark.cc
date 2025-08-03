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

#include <vector>

#include "testing/base/public/benchmark.h"
#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "icing/index/hit/doc-hit-info.h"
#include "icing/index/iterator/doc-hit-info-iterator-and.h"
#include "icing/index/iterator/doc-hit-info-iterator-test-util.h"
#include "icing/index/iterator/doc-hit-info-iterator.h"
#include "icing/schema/section.h"
#include "icing/store/document-id.h"

namespace icing {
namespace lib {

namespace {

// Run on a Linux workstation:
//    $ blaze build -c opt --dynamic_mode=off --copt=-gmlt
//    //icing/index/iterator:doc-hit-info-iterator_benchmark
//
//    $
//    blaze-bin/icing/index/iterator/doc-hit-info-iterator_benchmark
//    --benchmark_filter=all
//
// Run on an Android device:
//    $ blaze build --config=android_arm64 -c opt --dynamic_mode=off
//    --copt=-gmlt
//    //icing/index/iterator:doc-hit-info-iterator_benchmark
//
//    $ adb push
//    blaze-bin/icing/index/iterator/doc-hit-info-iterator_benchmark
//    /data/local/tmp/
//
//    $ adb shell /data/local/tmp/doc-hit-info-iterator_benchmark
//    --benchmark_filter=all

// Functor to be used with std::generate to create a container of DocHitInfos.
// DocHitInfos are generated starting at docid starting_docid and continuing at
// every n docid.
// Ex. std::vector<DocHitInfo> infos(6);
//     std::generate(infos.begin(), infos.end(), GeneratorEveryOtherN(25, 5));
// infos will now hold: {DocHitInfo(25), DocHitInfo(20), DocHitInfo(15),
//                       DocHitInfo(10), DocHitInfo(5), DocHitInfo(0)}
struct GeneratorEveryOtherN {
  explicit GeneratorEveryOtherN(DocumentId starting_docid, int n)
      : current_docid(starting_docid), interval(n) {}

  DocHitInfo operator()() {
    DocHitInfo info(current_docid, kSectionIdMaskAll);
    current_docid -= interval;
    return info;
  }

  DocumentId current_docid;
  int interval;
};

void BM_DocHitInfoIteratorAndBenchmark(benchmark::State& state) {
  // First iterator: If starting_docid is 1024 and interval is 2, docids
  // [1024, 1022, 1020, 1018, ..., 2, 0]
  DocumentId starting_docid = state.range(0);
  int interval = state.range(1);
  std::vector<DocHitInfo> first_infos((starting_docid / interval) + 1);
  std::generate(first_infos.begin(), first_infos.end(),
                GeneratorEveryOtherN(starting_docid, interval));
  std::unique_ptr<DocHitInfoIterator> first_iter =
      std::make_unique<DocHitInfoIteratorDummy>(first_infos);

  // Second iterator: An iterator with 1/4 of the hits as first_iter. If
  // starting_docid is 1024 and interval is 2, docids
  // [1024, 1016, 1008, 1000, ..., 8, 0]
  interval *= 4;
  std::vector<DocHitInfo> second_infos((starting_docid / interval) + 1);
  std::generate(second_infos.begin(), second_infos.end(),
                GeneratorEveryOtherN(starting_docid, interval));
  std::unique_ptr<DocHitInfoIterator> second_iter =
      std::make_unique<DocHitInfoIteratorDummy>(second_infos);

  std::vector<std::unique_ptr<DocHitInfoIterator>> iters;
  iters.push_back(std::move(first_iter));
  iters.push_back(std::move(second_iter));
  std::unique_ptr<DocHitInfoIterator> and_iter =
      CreateAndIterator(std::move(iters));
  for (auto _ : state) {
    while (and_iter->Advance().ok()) {
      // Intentionally left blank.
    }
  }
}
BENCHMARK(BM_DocHitInfoIteratorAndBenchmark)
    ->ArgPair(1024, 1)
    ->ArgPair(1024, 2)
    ->ArgPair(1024, 4)
    ->ArgPair(1024, 16)
    ->ArgPair(1024, 128)
    ->ArgPair(8192, 1)
    ->ArgPair(8192, 2)
    ->ArgPair(8192, 4)
    ->ArgPair(8192, 16)
    ->ArgPair(8192, 128)
    ->ArgPair(65536, 1)
    ->ArgPair(65536, 2)
    ->ArgPair(65536, 4)
    ->ArgPair(65536, 16)
    ->ArgPair(65536, 128);

void BM_DocHitInfoIteratorAndNaryBenchmark(benchmark::State& state) {
  // First iterator: If starting_docid is 1024 and interval is 2, docids
  // [1024, 1022, 1020, 1018, ..., 2, 0]
  DocumentId starting_docid = state.range(0);
  int interval = state.range(1);
  std::vector<DocHitInfo> first_infos((starting_docid / interval) + 1);
  std::generate(first_infos.begin(), first_infos.end(),
                GeneratorEveryOtherN(starting_docid, interval));
  std::unique_ptr<DocHitInfoIterator> first_iter =
      std::make_unique<DocHitInfoIteratorDummy>(first_infos);

  // Second iterator: An iterator with 1/2 of the hits as first_iter. If
  // starting_docid is 1024 and interval is 2, docids
  // [1024, 1020, 1016, 1012, ..., 4, 0]
  interval *= 2;
  std::vector<DocHitInfo> second_infos((starting_docid / interval) + 1);
  std::generate(second_infos.begin(), second_infos.end(),
                GeneratorEveryOtherN(starting_docid, interval));
  std::unique_ptr<DocHitInfoIterator> second_iter =
      std::make_unique<DocHitInfoIteratorDummy>(second_infos);

  // Third iterator: An iterator with 1/4 of the hits as first_iter. If
  // starting_docid is 1024 and interval is 2, docids
  // [1024, 1016, 1008, 1000, ..., 8, 0]
  interval *= 4;
  std::vector<DocHitInfo> third_infos((starting_docid / interval) + 1);
  std::generate(third_infos.begin(), third_infos.end(),
                GeneratorEveryOtherN(starting_docid, interval));
  std::unique_ptr<DocHitInfoIterator> third_iter =
      std::make_unique<DocHitInfoIteratorDummy>(third_infos);

  std::vector<std::unique_ptr<DocHitInfoIterator>> iters;
  iters.push_back(std::move(first_iter));
  iters.push_back(std::move(second_iter));
  iters.push_back(std::move(third_iter));
  std::unique_ptr<DocHitInfoIterator> and_iter =
      CreateAndIterator(std::move(iters));
  for (auto _ : state) {
    while (and_iter->Advance().ok()) {
      // Intentionally left blank.
    }
  }
}
BENCHMARK(BM_DocHitInfoIteratorAndNaryBenchmark)
    ->ArgPair(1024, 1)
    ->ArgPair(1024, 2)
    ->ArgPair(1024, 4)
    ->ArgPair(1024, 16)
    ->ArgPair(1024, 128)
    ->ArgPair(8192, 1)
    ->ArgPair(8192, 2)
    ->ArgPair(8192, 4)
    ->ArgPair(8192, 16)
    ->ArgPair(8192, 128)
    ->ArgPair(65536, 1)
    ->ArgPair(65536, 2)
    ->ArgPair(65536, 4)
    ->ArgPair(65536, 16)
    ->ArgPair(65536, 128);

}  // namespace

}  // namespace lib
}  // namespace icing
