/*
 * Copyright 2020 The Android Open Source Project
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

#include <array>
#include <climits>
#include <cstdlib>
#include <random>
#include <vector>

#include <benchmark/benchmark.h>

#include <audio_utils/intrinsic_utils.h>
#include <audio_utils/format.h>

static void BM_Intrinsic(benchmark::State& state) {
    using D = float;
    using namespace android::audio_utils::intrinsics;
    constexpr size_t SIMD_LENGTH = 4;

    // Possible testing types:
    using vec = android::audio_utils::intrinsics::internal_array_t<D, SIMD_LENGTH>;
    //using vec = float32x4_t;
    //using vec = float32x4x4_t;

    constexpr size_t DATA_SIZE = 1024;
    D a[DATA_SIZE];
    D b[DATA_SIZE];
    D c[DATA_SIZE];
    D d[DATA_SIZE];

    constexpr std::minstd_rand::result_type SEED = 42; // arbitrary choice.
    std::minstd_rand gen(SEED);
    const D amplitude = 1.0f;
    std::uniform_real_distribution<> dis(-amplitude, amplitude);
    for (size_t i = 0; i < DATA_SIZE; ++i) {
        a[i] = dis(gen);
        b[i] = dis(gen);
        c[i] = dis(gen);
    }

    while (state.KeepRunning()) {
        for (size_t i = 0; i < DATA_SIZE; i += sizeof(vec) / sizeof(D)) {
            const vec av = vld1<vec>(a + i);
            const vec bv = vld1<vec>(b + i);
            const vec cv = vld1<vec>(c + i);
            const vec dv = vmla(cv, av, bv);
            vst1(d + i, dv);
        }
        benchmark::DoNotOptimize(d[0]);
        benchmark::ClobberMemory();
    }
    //fprintf(stderr, "%f: %f %f\n %f", d[0], c[0], a[0], b[0]);
    state.SetComplexityN(state.range(0));
}

// A simple test using the VMLA intrinsic.
// One can alter either the intrinsic code or the compilation flags to see the benefit.
// Recommend using objdump to view the assembly.
static void BM_IntrinsicArgs(benchmark::internal::Benchmark* b) {
    for (int k = 0; k < 2; k++) // 0 for normal random data, 1 for subnormal random data
         b->Args({k});
}

BENCHMARK(BM_Intrinsic)->Apply(BM_IntrinsicArgs);

BENCHMARK_MAIN();
