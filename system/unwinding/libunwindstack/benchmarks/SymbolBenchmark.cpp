/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <err.h>
#include <inttypes.h>
#include <malloc.h>
#include <stdint.h>

#include <string>
#include <vector>

#include <benchmark/benchmark.h>

#include <unwindstack/Elf.h>
#include <unwindstack/Memory.h>

#include "Utils.h"

class SymbolLookupBenchmark : public benchmark::Fixture {
 public:
  void RunBenchmark(benchmark::State& state, const std::vector<uint64_t>& offsets,
                    const std::string& elf_file, bool expect_found, uint32_t runs = 1) {
    MemoryTracker mem_tracker;
    for (const auto& _ : state) {
      state.PauseTiming();
      mem_tracker.StartTrackingAllocations();
      state.ResumeTiming();

      auto elf_memory = unwindstack::Memory::CreateFileMemory(elf_file, 0);
      unwindstack::Elf elf(elf_memory);
      if (!elf.Init() || !elf.valid()) {
        errx(1, "Internal Error: Cannot open elf: %s", elf_file.c_str());
      }

      unwindstack::SharedString name;
      uint64_t offset;
      for (size_t i = 0; i < runs; i++) {
        for (auto pc : offsets) {
          bool found = elf.GetFunctionName(pc, &name, &offset);
          if (expect_found && !found) {
            errx(1, "expected pc 0x%" PRIx64 " present, but not found.", pc);
          } else if (!expect_found && found) {
            errx(1, "expected pc 0x%" PRIx64 " not present, but found.", pc);
          }
        }
      }

      state.PauseTiming();
      mem_tracker.StopTrackingAllocations();
      state.ResumeTiming();
    }
    mem_tracker.SetBenchmarkCounters(state);
  }

  void RunBenchmark(benchmark::State& state, uint64_t pc, const std::string& elf_file,
                    bool expect_found, uint32_t runs = 1) {
    RunBenchmark(state, std::vector<uint64_t>{pc}, elf_file, expect_found, runs);
  }
};

BENCHMARK_F(SymbolLookupBenchmark, BM_symbol_lookup_not_present)(benchmark::State& state) {
  RunBenchmark(state, 0, GetElfFile(), false);
}

BENCHMARK_F(SymbolLookupBenchmark, BM_symbol_lookup_find_single)(benchmark::State& state) {
  RunBenchmark(state, 0x22b2bc, GetElfFile(), true);
}

BENCHMARK_F(SymbolLookupBenchmark, BM_symbol_lookup_find_single_many_times)
(benchmark::State& state) {
  RunBenchmark(state, 0x22b2bc, GetElfFile(), true, 4096);
}

BENCHMARK_F(SymbolLookupBenchmark, BM_symbol_lookup_find_multiple)(benchmark::State& state) {
  RunBenchmark(state, std::vector<uint64_t>{0x22b2bc, 0xd5d30, 0x1312e8, 0x13582e, 0x1389c8},
               GetElfFile(), true);
}

BENCHMARK_F(SymbolLookupBenchmark, BM_symbol_lookup_not_present_from_sorted)
(benchmark::State& state) {
  RunBenchmark(state, 0, GetSymbolSortedElfFile(), false);
}

BENCHMARK_F(SymbolLookupBenchmark, BM_symbol_lookup_find_single_from_sorted)
(benchmark::State& state) {
  RunBenchmark(state, 0x138638, GetSymbolSortedElfFile(), true);
}

BENCHMARK_F(SymbolLookupBenchmark, BM_symbol_lookup_find_single_many_times_from_sorted)
(benchmark::State& state) {
  RunBenchmark(state, 0x138638, GetSymbolSortedElfFile(), true, 4096);
}

BENCHMARK_F(SymbolLookupBenchmark, BM_symbol_lookup_find_multiple_from_sorted)
(benchmark::State& state) {
  RunBenchmark(state, std::vector<uint64_t>{0x138638, 0x84350, 0x14df18, 0x1f3a38, 0x1f3ca8},
               GetSymbolSortedElfFile(), true);
}

BENCHMARK_F(SymbolLookupBenchmark, BM_symbol_lookup_not_present_from_large_compressed_frame)
(benchmark::State& state) {
  RunBenchmark(state, 0, GetLargeCompressedFrameElfFile(), false);
}

BENCHMARK_F(SymbolLookupBenchmark, BM_symbol_lookup_find_single_from_large_compressed_frame)
(benchmark::State& state) {
  RunBenchmark(state, 0x202aec, GetLargeCompressedFrameElfFile(), true);
}

BENCHMARK_F(SymbolLookupBenchmark,
            BM_symbol_lookup_find_single_many_times_from_large_compressed_frame)
(benchmark::State& state) {
  RunBenchmark(state, 0x202aec, GetLargeCompressedFrameElfFile(), true, 4096);
}

BENCHMARK_F(SymbolLookupBenchmark, BM_symbol_lookup_find_multiple_from_large_compressed_frame)
(benchmark::State& state) {
  RunBenchmark(state, std::vector<uint64_t>{0x202aec, 0x23e74c, 0xd000c, 0x201b10, 0x183060},
               GetLargeCompressedFrameElfFile(), true);
}
