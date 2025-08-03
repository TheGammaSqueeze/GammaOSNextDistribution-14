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

#include <limits>
#include <memory>
#include <random>
#include <string>

#include "testing/base/public/benchmark.h"
#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "icing/file/destructible-directory.h"
#include "icing/file/file-backed-vector.h"
#include "icing/file/filesystem.h"
#include "icing/file/memory-mapped-file.h"
#include "icing/testing/common-matchers.h"
#include "icing/testing/tmp-directory.h"

namespace icing {
namespace lib {

namespace {

class FileBackedVectorBenchmark {
 public:
  explicit FileBackedVectorBenchmark()
      : base_dir_(GetTestTempDir() + "/file_backed_vector_benchmark"),
        file_path_(base_dir_ + "/test_vector"),
        ddir_(&filesystem_, base_dir_),
        random_engine_(/*seed=*/12345) {}

  const Filesystem& filesystem() const { return filesystem_; }
  const std::string& file_path() const { return file_path_; }
  std::default_random_engine& random_engine() { return random_engine_; }

 private:
  Filesystem filesystem_;
  std::string base_dir_;
  std::string file_path_;
  DestructibleDirectory ddir_;

  std::default_random_engine random_engine_;
};

// Benchmark Set() (without extending vector, i.e. the index should be in range
// [0, num_elts - 1].
void BM_Set(benchmark::State& state) {
  int num_elts = state.range(0);

  FileBackedVectorBenchmark fbv_benchmark;

  fbv_benchmark.filesystem().DeleteFile(fbv_benchmark.file_path().c_str());
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<FileBackedVector<int>> fbv,
      FileBackedVector<int>::Create(
          fbv_benchmark.filesystem(), fbv_benchmark.file_path(),
          MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC));

  // Extend to num_elts
  ICING_ASSERT_OK(fbv->Set(num_elts - 1, 0));

  std::uniform_int_distribution<> distrib(0, num_elts - 1);
  for (auto _ : state) {
    int idx = distrib(fbv_benchmark.random_engine());
    ICING_ASSERT_OK(fbv->Set(idx, idx));
  }
}
BENCHMARK(BM_Set)
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

// Benchmark single Append(). Equivalent to Set(fbv->num_elements(), val), which
// extends the vector every round.
void BM_Append(benchmark::State& state) {
  FileBackedVectorBenchmark fbv_benchmark;

  fbv_benchmark.filesystem().DeleteFile(fbv_benchmark.file_path().c_str());
  ICING_ASSERT_OK_AND_ASSIGN(
      std::unique_ptr<FileBackedVector<int>> fbv,
      FileBackedVector<int>::Create(
          fbv_benchmark.filesystem(), fbv_benchmark.file_path(),
          MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC));

  std::uniform_int_distribution<> distrib(0, std::numeric_limits<int>::max());
  for (auto _ : state) {
    ICING_ASSERT_OK(fbv->Append(distrib(fbv_benchmark.random_engine())));
  }
}
BENCHMARK(BM_Append);

// Benchmark appending many elements.
void BM_AppendMany(benchmark::State& state) {
  int num = state.range(0);

  FileBackedVectorBenchmark fbv_benchmark;

  for (auto _ : state) {
    state.PauseTiming();
    fbv_benchmark.filesystem().DeleteFile(fbv_benchmark.file_path().c_str());
    ICING_ASSERT_OK_AND_ASSIGN(
        std::unique_ptr<FileBackedVector<int>> fbv,
        FileBackedVector<int>::Create(
            fbv_benchmark.filesystem(), fbv_benchmark.file_path(),
            MemoryMappedFile::Strategy::READ_WRITE_AUTO_SYNC));
    state.ResumeTiming();

    for (int i = 0; i < num; ++i) {
      ICING_ASSERT_OK(fbv->Append(i));
    }

    // Since destructor calls PersistToDisk, to avoid calling it twice, we reset
    // the unique pointer to invoke destructor instead of calling PersistToDisk
    // explicitly, so in this case PersistToDisk will be called only once.
    fbv.reset();
  }
}
BENCHMARK(BM_AppendMany)
    ->Arg(1 << 5)
    ->Arg(1 << 6)
    ->Arg(1 << 7)
    ->Arg(1 << 8)
    ->Arg(1 << 9)
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
