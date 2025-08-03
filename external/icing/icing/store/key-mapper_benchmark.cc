// Copyright (C) 2022 Google LLC
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

#include <random>
#include <string>
#include <unordered_map>

#include "testing/base/public/benchmark.h"
#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "icing/absl_ports/str_cat.h"
#include "icing/file/destructible-directory.h"
#include "icing/file/filesystem.h"
#include "icing/store/dynamic-trie-key-mapper.h"
#include "icing/store/key-mapper.h"
#include "icing/store/persistent-hash-map-key-mapper.h"
#include "icing/testing/common-matchers.h"
#include "icing/testing/random-string.h"
#include "icing/testing/tmp-directory.h"

namespace icing {
namespace lib {

namespace {

using ::testing::Eq;
using ::testing::IsTrue;
using ::testing::Not;

class KeyMapperBenchmark {
 public:
  static constexpr int kKeyLength = 20;

  explicit KeyMapperBenchmark()
      : clock(std::make_unique<Clock>()),
        base_dir(GetTestTempDir() + "/key_mapper_benchmark"),
        random_engine(/*seed=*/12345) {}

  std::string GenerateUniqueRandomKeyValuePair(int val,
                                               std::string_view prefix = "") {
    std::string rand_str = absl_ports::StrCat(
        prefix, RandomString(kAlNumAlphabet, kKeyLength, &random_engine));
    while (random_kvps_map.find(rand_str) != random_kvps_map.end()) {
      rand_str = absl_ports::StrCat(
          std::string(prefix),
          RandomString(kAlNumAlphabet, kKeyLength, &random_engine));
    }
    std::pair<std::string, int> entry(rand_str, val);
    random_kvps.push_back(entry);
    random_kvps_map.insert(entry);
    return rand_str;
  }

  template <typename UnknownKeyMapperType>
  libtextclassifier3::StatusOr<std::unique_ptr<KeyMapper<int>>> CreateKeyMapper(
      int max_num_entries) {
    return absl_ports::InvalidArgumentError("Unknown type");
  }

  template <>
  libtextclassifier3::StatusOr<std::unique_ptr<KeyMapper<int>>>
  CreateKeyMapper<DynamicTrieKeyMapper<int>>(int max_num_entries) {
    return DynamicTrieKeyMapper<int>::Create(
        filesystem, base_dir,
        /*maximum_size_bytes=*/128 * 1024 * 1024);
  }

  template <>
  libtextclassifier3::StatusOr<std::unique_ptr<KeyMapper<int>>>
  CreateKeyMapper<PersistentHashMapKeyMapper<int>>(int max_num_entries) {
    std::string working_path =
        absl_ports::StrCat(base_dir, "/", "key_mapper_dir");
    return PersistentHashMapKeyMapper<int>::Create(
        filesystem, std::move(working_path), /*pre_mapping_fbv=*/true,
        max_num_entries, /*average_kv_byte_size=*/kKeyLength + 1 + sizeof(int),
        /*max_load_factor_percent=*/100);
  }

  std::unique_ptr<Clock> clock;

  Filesystem filesystem;
  std::string base_dir;

  std::default_random_engine random_engine;
  std::vector<std::pair<std::string, int>> random_kvps;
  std::unordered_map<std::string, int> random_kvps_map;
};

// Benchmark the total time of putting num_keys (specified by Arg) unique random
// key value pairs.
template <typename KeyMapperType>
void BM_PutMany(benchmark::State& state) {
  int num_keys = state.range(0);

  KeyMapperBenchmark benchmark;
  for (int i = 0; i < num_keys; ++i) {
    benchmark.GenerateUniqueRandomKeyValuePair(i);
  }

  for (auto _ : state) {
    state.PauseTiming();
    benchmark.filesystem.DeleteDirectoryRecursively(benchmark.base_dir.c_str());
    DestructibleDirectory ddir(&benchmark.filesystem, benchmark.base_dir);
    ASSERT_THAT(ddir.is_valid(), IsTrue());
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<KeyMapper<int>> key_mapper,
        benchmark.CreateKeyMapper<KeyMapperType>(num_keys));
    ASSERT_THAT(key_mapper->num_keys(), Eq(0));
    state.ResumeTiming();

    for (int i = 0; i < num_keys; ++i) {
      ICING_ASSERT_OK(key_mapper->Put(benchmark.random_kvps[i].first,
                                      benchmark.random_kvps[i].second));
    }

    // Explicit calls PersistToDisk.
    ICING_ASSERT_OK(key_mapper->PersistToDisk());

    state.PauseTiming();
    ASSERT_THAT(key_mapper->num_keys(), Eq(num_keys));
    // The destructor of IcingDynamicTrie doesn't implicitly call PersistToDisk,
    // while PersistentHashMap does. Thus, we reset the unique pointer to invoke
    // destructor in the pause timing block, so in this case PersistToDisk will
    // be included into the benchmark only once.
    key_mapper.reset();
    state.ResumeTiming();
  }
}
BENCHMARK(BM_PutMany<DynamicTrieKeyMapper<int>>)
    ->Arg(1 << 10)
    ->Arg(1 << 11)
    ->Arg(1 << 12)
    ->Arg(1 << 13)
    ->Arg(1 << 14)
    ->Arg(1 << 15)
    ->Arg(1 << 16)
    ->Arg(1 << 17)
    ->Arg(1 << 18)
    ->Arg(1 << 19)
    ->Arg(1 << 20);
BENCHMARK(BM_PutMany<PersistentHashMapKeyMapper<int>>)
    ->Arg(1 << 10)
    ->Arg(1 << 11)
    ->Arg(1 << 12)
    ->Arg(1 << 13)
    ->Arg(1 << 14)
    ->Arg(1 << 15)
    ->Arg(1 << 16)
    ->Arg(1 << 17)
    ->Arg(1 << 18)
    ->Arg(1 << 19)
    ->Arg(1 << 20);

// Benchmark the average time of putting 1 unique random key value pair. The
// result will be affected by # of iterations, so use --benchmark_max_iters=k
// and --benchmark_min_iters=k to force # of iterations to be fixed.
template <typename KeyMapperType>
void BM_Put(benchmark::State& state) {
  KeyMapperBenchmark benchmark;
  benchmark.filesystem.DeleteDirectoryRecursively(benchmark.base_dir.c_str());
  DestructibleDirectory ddir(&benchmark.filesystem, benchmark.base_dir);
  ASSERT_THAT(ddir.is_valid(), IsTrue());

  // The overhead of state.PauseTiming is too large and affects the benchmark
  // result a lot, so pre-generate enough kvps to avoid calling too many times
  // state.PauseTiming for GenerateUniqueRandomKeyValuePair in the benchmark
  // for-loop.
  int MAX_PREGEN_KVPS = 1 << 22;
  for (int i = 0; i < MAX_PREGEN_KVPS; ++i) {
    benchmark.GenerateUniqueRandomKeyValuePair(i);
  }

  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<KeyMapper<int>> key_mapper,
      benchmark.CreateKeyMapper<KeyMapperType>(/*max_num_entries=*/1 << 22));
  ASSERT_THAT(key_mapper->num_keys(), Eq(0));

  int cnt = 0;
  for (auto _ : state) {
    if (cnt >= MAX_PREGEN_KVPS) {
      state.PauseTiming();
      benchmark.GenerateUniqueRandomKeyValuePair(cnt);
      state.ResumeTiming();
    }

    ICING_ASSERT_OK(key_mapper->Put(benchmark.random_kvps[cnt].first,
                                    benchmark.random_kvps[cnt].second));
    ++cnt;
  }
}
BENCHMARK(BM_Put<DynamicTrieKeyMapper<int>>);
BENCHMARK(BM_Put<PersistentHashMapKeyMapper<int>>);

// Benchmark the average time of getting 1 existing key value pair from the key
// mapper with size num_keys (specified by Arg).
template <typename KeyMapperType>
void BM_Get(benchmark::State& state) {
  int num_keys = state.range(0);

  KeyMapperBenchmark benchmark;
  benchmark.filesystem.DeleteDirectoryRecursively(benchmark.base_dir.c_str());
  DestructibleDirectory ddir(&benchmark.filesystem, benchmark.base_dir);
  ASSERT_THAT(ddir.is_valid(), IsTrue());

  // Create a key mapper with num_keys entries.
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<KeyMapper<int>> key_mapper,
      benchmark.CreateKeyMapper<KeyMapperType>(num_keys));
  for (int i = 0; i < num_keys; ++i) {
    ICING_ASSERT_OK(
        key_mapper->Put(benchmark.GenerateUniqueRandomKeyValuePair(i), i));
  }
  ASSERT_THAT(key_mapper->num_keys(), Eq(num_keys));

  std::uniform_int_distribution<> distrib(0, num_keys - 1);
  std::default_random_engine e(/*seed=*/12345);
  for (auto _ : state) {
    int idx = distrib(e);
    ICING_ASSERT_OK_AND_ASSIGN(
        int val, key_mapper->Get(benchmark.random_kvps[idx].first));
    ASSERT_THAT(val, Eq(benchmark.random_kvps[idx].second));
  }
}
BENCHMARK(BM_Get<DynamicTrieKeyMapper<int>>)
    ->Arg(1 << 10)
    ->Arg(1 << 11)
    ->Arg(1 << 12)
    ->Arg(1 << 13)
    ->Arg(1 << 14)
    ->Arg(1 << 15)
    ->Arg(1 << 16)
    ->Arg(1 << 17)
    ->Arg(1 << 18)
    ->Arg(1 << 19)
    ->Arg(1 << 20);
BENCHMARK(BM_Get<PersistentHashMapKeyMapper<int>>)
    ->Arg(1 << 10)
    ->Arg(1 << 11)
    ->Arg(1 << 12)
    ->Arg(1 << 13)
    ->Arg(1 << 14)
    ->Arg(1 << 15)
    ->Arg(1 << 16)
    ->Arg(1 << 17)
    ->Arg(1 << 18)
    ->Arg(1 << 19)
    ->Arg(1 << 20);

// Benchmark the total time of iterating through all key value pairs of the key
// mapper with size num_keys (specified by Arg).
template <typename KeyMapperType>
void BM_Iterator(benchmark::State& state) {
  int num_keys = state.range(0);

  KeyMapperBenchmark benchmark;
  benchmark.filesystem.DeleteDirectoryRecursively(benchmark.base_dir.c_str());
  DestructibleDirectory ddir(&benchmark.filesystem, benchmark.base_dir);
  ASSERT_THAT(ddir.is_valid(), IsTrue());

  // Create a key mapper with num_keys entries.
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<KeyMapper<int>> key_mapper,
      benchmark.CreateKeyMapper<KeyMapperType>(num_keys));
  for (int i = 0; i < num_keys; ++i) {
    ICING_ASSERT_OK(
        key_mapper->Put(benchmark.GenerateUniqueRandomKeyValuePair(i), i));
  }
  ASSERT_THAT(key_mapper->num_keys(), Eq(num_keys));

  for (auto _ : state) {
    auto iter = key_mapper->GetIterator();
    int cnt = 0;
    while (iter->Advance()) {
      ++cnt;
      std::string key(iter->GetKey());
      int value = iter->GetValue();
      auto it = benchmark.random_kvps_map.find(key);
      ASSERT_THAT(it, Not(Eq(benchmark.random_kvps_map.end())));
      ASSERT_THAT(it->second, Eq(value));
    }
    ASSERT_THAT(cnt, Eq(num_keys));
  }
}
BENCHMARK(BM_Iterator<DynamicTrieKeyMapper<int>>)
    ->Arg(1 << 10)
    ->Arg(1 << 11)
    ->Arg(1 << 12)
    ->Arg(1 << 13)
    ->Arg(1 << 14)
    ->Arg(1 << 15)
    ->Arg(1 << 16)
    ->Arg(1 << 17)
    ->Arg(1 << 18)
    ->Arg(1 << 19)
    ->Arg(1 << 20);
BENCHMARK(BM_Iterator<PersistentHashMapKeyMapper<int>>)
    ->Arg(1 << 10)
    ->Arg(1 << 11)
    ->Arg(1 << 12)
    ->Arg(1 << 13)
    ->Arg(1 << 14)
    ->Arg(1 << 15)
    ->Arg(1 << 16)
    ->Arg(1 << 17)
    ->Arg(1 << 18)
    ->Arg(1 << 19)
    ->Arg(1 << 20);

}  // namespace

}  // namespace lib
}  // namespace icing
