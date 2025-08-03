/*
 * Copyright (C) 2023 The Android Open Source Project
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
#include <cstdlib>
#include <ctime>
#include <vector>

#include "benchmark/benchmark.h"

namespace android {
namespace os {
namespace statsd {

namespace {

template <typename ContainerType>
void benchmarkFunctionForVector(std::vector<ContainerType>& vec, int capacity) {
    ContainerType result = false;
    for (int i = 0; i < capacity; i++) {
        vec[i] = !result;
        result = !result;
    }

    int resultInt = 0;
    for (int i = 0; i < capacity; i++) {
        resultInt += vec[i];
    }

    // Make sure the variable is not optimized away by compiler
    benchmark::DoNotOptimize(vec);
    benchmark::DoNotOptimize(resultInt);
}

template <typename ContainerType>
void benchmarkStdFillForVector(std::vector<ContainerType>& vec, int capacity) {
    std::fill(vec.begin(), vec.end(), true);
    int resultInt = 0;
    for (int i = 0; i < capacity; i++) {
        resultInt += vec[i];
    }

    // Make sure the variable is not optimized away by compiler
    benchmark::DoNotOptimize(vec);
    benchmark::DoNotOptimize(resultInt);
}

}  //  namespace

static void BM_BasicVectorBoolUsage(benchmark::State& state) {
    const int capacity = state.range(0);
    std::vector<bool> vec(capacity);

    while (state.KeepRunning()) {
        benchmarkFunctionForVector<bool>(vec, capacity);
    }
}
BENCHMARK(BM_BasicVectorBoolUsage)->Args({5})->Args({10})->Args({20})->Args({50})->Args({100});

static void BM_VectorBoolStdFill(benchmark::State& state) {
    const int capacity = state.range(0);
    std::vector<bool> vec(capacity);

    while (state.KeepRunning()) {
        benchmarkStdFillForVector<bool>(vec, capacity);
    }
}
BENCHMARK(BM_VectorBoolStdFill)->Args({5})->Args({10})->Args({20})->Args({50})->Args({100});

static void BM_BasicVectorInt8Usage(benchmark::State& state) {
    const int capacity = state.range(0);
    std::vector<int8_t> vec(capacity);

    while (state.KeepRunning()) {
        benchmarkFunctionForVector<int8_t>(vec, capacity);
    }
}
BENCHMARK(BM_BasicVectorInt8Usage)->Args({5})->Args({10})->Args({20})->Args({50})->Args({100});

static void BM_VectorInt8StdFill(benchmark::State& state) {
    const int capacity = state.range(0);
    std::vector<int8_t> vec(capacity);

    while (state.KeepRunning()) {
        benchmarkStdFillForVector<int8_t>(vec, capacity);
    }
}
BENCHMARK(BM_VectorInt8StdFill)->Args({5})->Args({10})->Args({20})->Args({50})->Args({100});

}  //  namespace statsd
}  //  namespace os
}  //  namespace android
