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

#include <audio_utils/BiquadFilter.h>
#include <audio_utils/format.h>

#pragma GCC diagnostic ignored "-Wunused-function"  // we use override array assignment

static constexpr size_t DATA_SIZE = 1024;
// The coefficients is a HPF with sampling frequency as 48000, center frequency as 600,
// and Q as 0.707. As all the coefficients are not zero, they can be used to benchmark
// the non-zero optimization of BiquadFilter.
// The benchmark test will iterate the channel count from 1 to 2. The occupancy will be
// iterate from 1 to 31. In that case, it is possible to test the performance of cases
// with different coefficients as zero.
static constexpr float REF_COEFS[] = {0.9460f, -1.8919f, 0.9460f, -1.8890f, 0.8949f};

static void BM_BiquadFilter1D(benchmark::State& state) {
    using android::audio_utils::BiquadFilter;

    bool doParallel = (state.range(0) == 1);
    // const size_t channelCount = state.range(1);
    const size_t filters = 1;

    std::vector<float> input(DATA_SIZE);
    std::array<float, android::audio_utils::kBiquadNumCoefs> coefs;

    // Initialize input buffer and coefs with deterministic pseudo-random values
    constexpr std::minstd_rand::result_type SEED = 42; // arbitrary choice.
    std::minstd_rand gen(SEED);
    constexpr float amplitude = 1.0f;
    std::uniform_real_distribution<> dis(-amplitude, amplitude);
    for (size_t i = 0; i < DATA_SIZE; ++i) {
        input[i] = dis(gen);
    }

    android::audio_utils::BiquadFilter parallel(filters, coefs);
    std::vector<std::unique_ptr<BiquadFilter<float>>> biquads(filters);
    for (auto& biquad : biquads) {
        biquad.reset(new BiquadFilter<float>(1, coefs));
    }

    // Run the test
    float *data = input.data();
    while (state.KeepRunning()) {
        benchmark::DoNotOptimize(data);
        if (doParallel) {
            parallel.process1D(data, DATA_SIZE);
        } else {
            for (auto& biquad : biquads) {
                biquad->process(data, data, DATA_SIZE);
            }
        }
        benchmark::ClobberMemory();
    }
}

static void BiquadFilter1DArgs(benchmark::internal::Benchmark* b) {
    for (int k = 0; k < 2; k++) // 0 for normal random data, 1 for subnormal random data
         b->Args({k});
}

BENCHMARK(BM_BiquadFilter1D)->Apply(BiquadFilter1DArgs);

/*******************************************************************
 A test result running on Pixel 4XL for comparison.

 Parameterized Test BM_BiquadFilter1D/A
 <A> is 0 or 1 indicating if the input data is subnormal or not.

 Parameterized Test BM_BiquadFilter<TYPE>/A/B/C
 <A> is 0 or 1 indicating if the input data is subnormal or not.
 <B> is the channel count, starting from 1
 <C> indicates the occupancy of the coefficients as a bitmask (1 - 31) representing
     b0, b1, b2, a0, a1.  31 indicates all Biquad coefficients are non-zero.

-----------------------------------------------------------------------------------
Benchmark                                         Time             CPU   Iterations
-----------------------------------------------------------------------------------
BM_BiquadFilter1D/0                             559 ns          558 ns      1255778
BM_BiquadFilter1D/1                             563 ns          561 ns      1246802
BM_BiquadFilterFloatOptimized/0/1/31           2050 ns         2044 ns       341777
BM_BiquadFilterFloatOptimized/0/2/31           2381 ns         2374 ns       296608
BM_BiquadFilterFloatOptimized/0/3/31           2838 ns         2831 ns       247298
BM_BiquadFilterFloatOptimized/0/4/31           2453 ns         2446 ns       285869
BM_BiquadFilterFloatOptimized/0/5/31           2875 ns         2867 ns       244307
BM_BiquadFilterFloatOptimized/0/6/31           3183 ns         3174 ns       220149
BM_BiquadFilterFloatOptimized/0/7/31           3915 ns         3903 ns       179368
BM_BiquadFilterFloatOptimized/0/8/31           3163 ns         3153 ns       222068
BM_BiquadFilterFloatOptimized/0/9/31           3963 ns         3953 ns       177162
BM_BiquadFilterFloatOptimized/0/10/31          4208 ns         4197 ns       166789
BM_BiquadFilterFloatOptimized/0/11/31          5317 ns         5301 ns       131817
BM_BiquadFilterFloatOptimized/0/12/31          4209 ns         4198 ns       166785
BM_BiquadFilterFloatOptimized/0/13/31          5295 ns         5278 ns       132467
BM_BiquadFilterFloatOptimized/0/14/31          5479 ns         5463 ns       128159
BM_BiquadFilterFloatOptimized/0/15/31          6568 ns         6547 ns       106912
BM_BiquadFilterFloatOptimized/0/16/31          5442 ns         5425 ns       129023
BM_BiquadFilterFloatOptimized/0/17/31          7527 ns         7505 ns        93266
BM_BiquadFilterFloatOptimized/0/18/31          7981 ns         7955 ns        88032
BM_BiquadFilterFloatOptimized/0/19/31          8574 ns         8549 ns        81866
BM_BiquadFilterFloatOptimized/0/20/31          7832 ns         7806 ns        89698
BM_BiquadFilterFloatOptimized/0/21/31          8683 ns         8659 ns        80847
BM_BiquadFilterFloatOptimized/0/22/31          8829 ns         8807 ns        79372
BM_BiquadFilterFloatOptimized/0/23/31          9627 ns         9596 ns        72948
BM_BiquadFilterFloatOptimized/0/24/31          8662 ns         8641 ns        80994
BM_BiquadFilterFloatOptimized/0/1/1             559 ns          558 ns      1255056
BM_BiquadFilterFloatOptimized/0/1/2             649 ns          648 ns      1080979
BM_BiquadFilterFloatOptimized/0/1/3             649 ns          647 ns      1081110
BM_BiquadFilterFloatOptimized/0/1/4             846 ns          844 ns       829190
BM_BiquadFilterFloatOptimized/0/1/5             848 ns          845 ns       829260
BM_BiquadFilterFloatOptimized/0/1/6             842 ns          840 ns       833883
BM_BiquadFilterFloatOptimized/0/1/7             846 ns          844 ns       830816
BM_BiquadFilterFloatOptimized/0/1/8            2181 ns         2175 ns       321856
BM_BiquadFilterFloatOptimized/0/1/9            2247 ns         2241 ns       312645
BM_BiquadFilterFloatOptimized/0/1/10           2038 ns         2032 ns       344762
BM_BiquadFilterFloatOptimized/0/1/11           2044 ns         2038 ns       343491
BM_BiquadFilterFloatOptimized/0/1/12           2051 ns         2045 ns       342775
BM_BiquadFilterFloatOptimized/0/1/13           2047 ns         2041 ns       343409
BM_BiquadFilterFloatOptimized/0/1/14           2041 ns         2035 ns       344295
BM_BiquadFilterFloatOptimized/0/1/15           2050 ns         2044 ns       342031
BM_BiquadFilterFloatOptimized/0/1/16           2049 ns         2042 ns       342867
BM_BiquadFilterFloatOptimized/0/1/17           2047 ns         2042 ns       343005
BM_BiquadFilterFloatOptimized/0/1/18           2040 ns         2034 ns       344447
BM_BiquadFilterFloatOptimized/0/1/19           2050 ns         2044 ns       343828
BM_BiquadFilterFloatOptimized/0/1/20           2049 ns         2044 ns       343190
BM_BiquadFilterFloatOptimized/0/1/21           2048 ns         2042 ns       342839
BM_BiquadFilterFloatOptimized/0/1/22           2040 ns         2035 ns       344409
BM_BiquadFilterFloatOptimized/0/1/23           2048 ns         2043 ns       343306
BM_BiquadFilterFloatOptimized/0/1/24           2049 ns         2043 ns       342812
BM_BiquadFilterFloatOptimized/0/1/25           2049 ns         2043 ns       342580
BM_BiquadFilterFloatOptimized/0/1/26           2039 ns         2033 ns       344247
BM_BiquadFilterFloatOptimized/0/1/27           2046 ns         2040 ns       341970
BM_BiquadFilterFloatOptimized/0/1/28           2050 ns         2045 ns       342407
BM_BiquadFilterFloatOptimized/0/1/29           2046 ns         2041 ns       343675
BM_BiquadFilterFloatOptimized/0/1/30           2041 ns         2035 ns       344616
BM_BiquadFilterFloatOptimized/0/1/31           2051 ns         2046 ns       343258
BM_BiquadFilterFloatOptimized/0/2/1             610 ns          608 ns      1151019
BM_BiquadFilterFloatOptimized/0/2/2             806 ns          804 ns       871214
BM_BiquadFilterFloatOptimized/0/2/3             802 ns          800 ns       876072
BM_BiquadFilterFloatOptimized/0/2/4            1492 ns         1488 ns       471009
BM_BiquadFilterFloatOptimized/0/2/5            1493 ns         1489 ns       469536
BM_BiquadFilterFloatOptimized/0/2/6            1495 ns         1491 ns       469503
BM_BiquadFilterFloatOptimized/0/2/7            1493 ns         1488 ns       470487
BM_BiquadFilterFloatOptimized/0/2/8            2240 ns         2234 ns       313239
BM_BiquadFilterFloatOptimized/0/2/9            2240 ns         2234 ns       313156
BM_BiquadFilterFloatOptimized/0/2/10           2234 ns         2228 ns       313789
BM_BiquadFilterFloatOptimized/0/2/11           2236 ns         2230 ns       313706
BM_BiquadFilterFloatOptimized/0/2/12           2388 ns         2381 ns       293618
BM_BiquadFilterFloatOptimized/0/2/13           2375 ns         2367 ns       295150
BM_BiquadFilterFloatOptimized/0/2/14           2366 ns         2358 ns       293452
BM_BiquadFilterFloatOptimized/0/2/15           2387 ns         2381 ns       292701
BM_BiquadFilterFloatOptimized/0/2/16           2389 ns         2383 ns       292393
BM_BiquadFilterFloatOptimized/0/2/17           2415 ns         2408 ns       292606
BM_BiquadFilterFloatOptimized/0/2/18           2333 ns         2327 ns       302560
BM_BiquadFilterFloatOptimized/0/2/19           2378 ns         2372 ns       301407
BM_BiquadFilterFloatOptimized/0/2/20           2379 ns         2373 ns       297827
BM_BiquadFilterFloatOptimized/0/2/21           2412 ns         2406 ns       293297
BM_BiquadFilterFloatOptimized/0/2/22           2340 ns         2334 ns       296729
BM_BiquadFilterFloatOptimized/0/2/23           2383 ns         2376 ns       293035
BM_BiquadFilterFloatOptimized/0/2/24           2365 ns         2359 ns       294749
BM_BiquadFilterFloatOptimized/0/2/25           2407 ns         2400 ns       293857
BM_BiquadFilterFloatOptimized/0/2/26           2342 ns         2336 ns       301276
BM_BiquadFilterFloatOptimized/0/2/27           2387 ns         2380 ns       296218
BM_BiquadFilterFloatOptimized/0/2/28           2393 ns         2386 ns       304486
BM_BiquadFilterFloatOptimized/0/2/29           2382 ns         2375 ns       296040
BM_BiquadFilterFloatOptimized/0/2/30           2352 ns         2345 ns       296032
BM_BiquadFilterFloatOptimized/0/2/31           2390 ns         2384 ns       295280
BM_BiquadFilterFloatOptimized/0/3/1            1014 ns         1011 ns       692380
BM_BiquadFilterFloatOptimized/0/3/2            1358 ns         1354 ns       516490
BM_BiquadFilterFloatOptimized/0/3/3            1361 ns         1357 ns       514686
BM_BiquadFilterFloatOptimized/0/3/4            2280 ns         2275 ns       307713
BM_BiquadFilterFloatOptimized/0/3/5            2283 ns         2277 ns       307354
BM_BiquadFilterFloatOptimized/0/3/6            2273 ns         2267 ns       308595
BM_BiquadFilterFloatOptimized/0/3/7            2281 ns         2274 ns       307849
BM_BiquadFilterFloatOptimized/0/3/8            2316 ns         2309 ns       303835
BM_BiquadFilterFloatOptimized/0/3/9            2305 ns         2299 ns       304559
BM_BiquadFilterFloatOptimized/0/3/10           2302 ns         2296 ns       304427
BM_BiquadFilterFloatOptimized/0/3/11           2302 ns         2296 ns       304901
BM_BiquadFilterFloatOptimized/0/3/12           2842 ns         2835 ns       246870
BM_BiquadFilterFloatOptimized/0/3/13           2839 ns         2832 ns       246584
BM_BiquadFilterFloatOptimized/0/3/14           2846 ns         2838 ns       246569
BM_BiquadFilterFloatOptimized/0/3/15           2838 ns         2830 ns       246748
BM_BiquadFilterFloatOptimized/0/3/16           2841 ns         2834 ns       247114
BM_BiquadFilterFloatOptimized/0/3/17           2835 ns         2827 ns       247560
BM_BiquadFilterFloatOptimized/0/3/18           2848 ns         2840 ns       246585
BM_BiquadFilterFloatOptimized/0/3/19           2847 ns         2839 ns       246700
BM_BiquadFilterFloatOptimized/0/3/20           2843 ns         2836 ns       246965
BM_BiquadFilterFloatOptimized/0/3/21           2838 ns         2830 ns       247591
BM_BiquadFilterFloatOptimized/0/3/22           2845 ns         2838 ns       246791
BM_BiquadFilterFloatOptimized/0/3/23           2841 ns         2833 ns       247057
BM_BiquadFilterFloatOptimized/0/3/24           2845 ns         2837 ns       246545
BM_BiquadFilterFloatOptimized/0/3/25           2836 ns         2829 ns       247397
BM_BiquadFilterFloatOptimized/0/3/26           2847 ns         2839 ns       246664
BM_BiquadFilterFloatOptimized/0/3/27           2842 ns         2834 ns       247627
BM_BiquadFilterFloatOptimized/0/3/28           2841 ns         2833 ns       247121
BM_BiquadFilterFloatOptimized/0/3/29           2841 ns         2834 ns       246763
BM_BiquadFilterFloatOptimized/0/3/30           2845 ns         2837 ns       246597
BM_BiquadFilterFloatOptimized/0/3/31           2840 ns         2832 ns       246777
BM_BiquadFilterFloatOptimized/0/4/1             649 ns          648 ns      1080107
BM_BiquadFilterFloatOptimized/0/4/2             807 ns          805 ns       869257
BM_BiquadFilterFloatOptimized/0/4/3             801 ns          799 ns       871956
BM_BiquadFilterFloatOptimized/0/4/4             833 ns          831 ns       842148
BM_BiquadFilterFloatOptimized/0/4/5             834 ns          832 ns       841869
BM_BiquadFilterFloatOptimized/0/4/6             834 ns          832 ns       841650
BM_BiquadFilterFloatOptimized/0/4/7             833 ns          831 ns       841856
BM_BiquadFilterFloatOptimized/0/4/8            2198 ns         2192 ns       319428
BM_BiquadFilterFloatOptimized/0/4/9            2198 ns         2192 ns       319357
BM_BiquadFilterFloatOptimized/0/4/10           2208 ns         2202 ns       318871
BM_BiquadFilterFloatOptimized/0/4/11           2199 ns         2194 ns       318145
BM_BiquadFilterFloatOptimized/0/4/12           2459 ns         2452 ns       285278
BM_BiquadFilterFloatOptimized/0/4/13           2367 ns         2361 ns       296930
BM_BiquadFilterFloatOptimized/0/4/14           2506 ns         2500 ns       278066
BM_BiquadFilterFloatOptimized/0/4/15           2448 ns         2441 ns       286096
BM_BiquadFilterFloatOptimized/0/4/16           2450 ns         2443 ns       286116
BM_BiquadFilterFloatOptimized/0/4/17           2508 ns         2501 ns       276874
BM_BiquadFilterFloatOptimized/0/4/18           2366 ns         2359 ns       297429
BM_BiquadFilterFloatOptimized/0/4/19           2437 ns         2430 ns       288050
BM_BiquadFilterFloatOptimized/0/4/20           2455 ns         2448 ns       287233
BM_BiquadFilterFloatOptimized/0/4/21           2381 ns         2374 ns       294302
BM_BiquadFilterFloatOptimized/0/4/22           2510 ns         2503 ns       278301
BM_BiquadFilterFloatOptimized/0/4/23           2457 ns         2450 ns       286840
BM_BiquadFilterFloatOptimized/0/4/24           2427 ns         2420 ns       287276
BM_BiquadFilterFloatOptimized/0/4/25           2531 ns         2525 ns       279592
BM_BiquadFilterFloatOptimized/0/4/26           2382 ns         2375 ns       293634
BM_BiquadFilterFloatOptimized/0/4/27           2453 ns         2446 ns       284497
BM_BiquadFilterFloatOptimized/0/4/28           2454 ns         2447 ns       286420
BM_BiquadFilterFloatOptimized/0/4/29           2368 ns         2362 ns       296231
BM_BiquadFilterFloatOptimized/0/4/30           2522 ns         2515 ns       278613
BM_BiquadFilterFloatOptimized/0/4/31           2448 ns         2440 ns       286406
BM_BiquadFilterFloatOptimized/1/1/1             559 ns          558 ns      1255148
BM_BiquadFilterFloatOptimized/1/1/2             649 ns          648 ns      1081116
BM_BiquadFilterFloatOptimized/1/1/3             649 ns          647 ns      1081221
BM_BiquadFilterFloatOptimized/1/1/4             847 ns          844 ns       829296
BM_BiquadFilterFloatOptimized/1/1/5             848 ns          845 ns       828816
BM_BiquadFilterFloatOptimized/1/1/6             843 ns          840 ns       833346
BM_BiquadFilterFloatOptimized/1/1/7             845 ns          843 ns       829793
BM_BiquadFilterFloatOptimized/1/1/8            2181 ns         2175 ns       321841
BM_BiquadFilterFloatOptimized/1/1/9            2251 ns         2244 ns       311848
BM_BiquadFilterFloatOptimized/1/1/10           2038 ns         2031 ns       344681
BM_BiquadFilterFloatOptimized/1/1/11           2044 ns         2038 ns       342723
BM_BiquadFilterFloatOptimized/1/1/12           2050 ns         2044 ns       341921
BM_BiquadFilterFloatOptimized/1/1/13           2045 ns         2040 ns       342953
BM_BiquadFilterFloatOptimized/1/1/14           2040 ns         2034 ns       343741
BM_BiquadFilterFloatOptimized/1/1/15           2053 ns         2047 ns       343974
BM_BiquadFilterFloatOptimized/1/1/16           2049 ns         2044 ns       342365
BM_BiquadFilterFloatOptimized/1/1/17           2049 ns         2044 ns       343153
BM_BiquadFilterFloatOptimized/1/1/18           2041 ns         2035 ns       344287
BM_BiquadFilterFloatOptimized/1/1/19           2049 ns         2044 ns       341823
BM_BiquadFilterFloatOptimized/1/1/20           2046 ns         2041 ns       342703
BM_BiquadFilterFloatOptimized/1/1/21           2047 ns         2042 ns       342940
BM_BiquadFilterFloatOptimized/1/1/22           2039 ns         2033 ns       344725
BM_BiquadFilterFloatOptimized/1/1/23           2049 ns         2043 ns       342315
BM_BiquadFilterFloatOptimized/1/1/24           2047 ns         2041 ns       342189
BM_BiquadFilterFloatOptimized/1/1/25           2052 ns         2046 ns       342359
BM_BiquadFilterFloatOptimized/1/1/26           2040 ns         2034 ns       343700
BM_BiquadFilterFloatOptimized/1/1/27           2046 ns         2040 ns       342555
BM_BiquadFilterFloatOptimized/1/1/28           2050 ns         2044 ns       343258
BM_BiquadFilterFloatOptimized/1/1/29           2047 ns         2041 ns       343619
BM_BiquadFilterFloatOptimized/1/1/30           2040 ns         2034 ns       344029
BM_BiquadFilterFloatOptimized/1/1/31           2048 ns         2043 ns       341732
BM_BiquadFilterFloatOptimized/1/2/1             610 ns          608 ns      1151198
BM_BiquadFilterFloatOptimized/1/2/2             806 ns          804 ns       871704
BM_BiquadFilterFloatOptimized/1/2/3             801 ns          799 ns       874910
BM_BiquadFilterFloatOptimized/1/2/4            1491 ns         1487 ns       470715
BM_BiquadFilterFloatOptimized/1/2/5            1494 ns         1489 ns       471029
BM_BiquadFilterFloatOptimized/1/2/6            1495 ns         1491 ns       469531
BM_BiquadFilterFloatOptimized/1/2/7            1492 ns         1488 ns       470330
BM_BiquadFilterFloatOptimized/1/2/8            2240 ns         2234 ns       313315
BM_BiquadFilterFloatOptimized/1/2/9            2240 ns         2235 ns       313286
BM_BiquadFilterFloatOptimized/1/2/10           2236 ns         2230 ns       314133
BM_BiquadFilterFloatOptimized/1/2/11           2237 ns         2230 ns       313614
BM_BiquadFilterFloatOptimized/1/2/12           2397 ns         2391 ns       298604
BM_BiquadFilterFloatOptimized/1/2/13           2361 ns         2354 ns       293931
BM_BiquadFilterFloatOptimized/1/2/14           2339 ns         2333 ns       298869
BM_BiquadFilterFloatOptimized/1/2/15           2386 ns         2379 ns       299268
BM_BiquadFilterFloatOptimized/1/2/16           2392 ns         2386 ns       295784
BM_BiquadFilterFloatOptimized/1/2/17           2392 ns         2386 ns       293455
BM_BiquadFilterFloatOptimized/1/2/18           2330 ns         2323 ns       296814
BM_BiquadFilterFloatOptimized/1/2/19           2360 ns         2354 ns       296827
BM_BiquadFilterFloatOptimized/1/2/20           2366 ns         2360 ns       296032
BM_BiquadFilterFloatOptimized/1/2/21           2417 ns         2410 ns       293865
BM_BiquadFilterFloatOptimized/1/2/22           2332 ns         2326 ns       293377
BM_BiquadFilterFloatOptimized/1/2/23           2395 ns         2388 ns       292926
BM_BiquadFilterFloatOptimized/1/2/24           2367 ns         2361 ns       294222
BM_BiquadFilterFloatOptimized/1/2/25           2398 ns         2392 ns       291347
BM_BiquadFilterFloatOptimized/1/2/26           2359 ns         2353 ns       297696
BM_BiquadFilterFloatOptimized/1/2/27           2378 ns         2371 ns       297585
BM_BiquadFilterFloatOptimized/1/2/28           2386 ns         2380 ns       293528
BM_BiquadFilterFloatOptimized/1/2/29           2378 ns         2372 ns       295612
BM_BiquadFilterFloatOptimized/1/2/30           2329 ns         2323 ns       298587
BM_BiquadFilterFloatOptimized/1/2/31           2384 ns         2378 ns       294842
BM_BiquadFilterFloatOptimized/1/3/1            1014 ns         1011 ns       692362
BM_BiquadFilterFloatOptimized/1/3/2            1358 ns         1354 ns       516958
BM_BiquadFilterFloatOptimized/1/3/3            1360 ns         1356 ns       515306
BM_BiquadFilterFloatOptimized/1/3/4            2281 ns         2275 ns       307489
BM_BiquadFilterFloatOptimized/1/3/5            2282 ns         2276 ns       307433
BM_BiquadFilterFloatOptimized/1/3/6            2273 ns         2267 ns       308657
BM_BiquadFilterFloatOptimized/1/3/7            2280 ns         2275 ns       307889
BM_BiquadFilterFloatOptimized/1/3/8            2312 ns         2306 ns       303925
BM_BiquadFilterFloatOptimized/1/3/9            2306 ns         2300 ns       304209
BM_BiquadFilterFloatOptimized/1/3/10           2303 ns         2296 ns       304815
BM_BiquadFilterFloatOptimized/1/3/11           2302 ns         2296 ns       304802
BM_BiquadFilterFloatOptimized/1/3/12           2838 ns         2830 ns       247177
BM_BiquadFilterFloatOptimized/1/3/13           2843 ns         2835 ns       247072
BM_BiquadFilterFloatOptimized/1/3/14           2848 ns         2840 ns       246262
BM_BiquadFilterFloatOptimized/1/3/15           2840 ns         2833 ns       246995
BM_BiquadFilterFloatOptimized/1/3/16           2842 ns         2834 ns       246802
BM_BiquadFilterFloatOptimized/1/3/17           2836 ns         2829 ns       247663
BM_BiquadFilterFloatOptimized/1/3/18           2847 ns         2840 ns       246786
BM_BiquadFilterFloatOptimized/1/3/19           2843 ns         2834 ns       246922
BM_BiquadFilterFloatOptimized/1/3/20           2838 ns         2830 ns       247683
BM_BiquadFilterFloatOptimized/1/3/21           2836 ns         2828 ns       247886
BM_BiquadFilterFloatOptimized/1/3/22           2847 ns         2840 ns       246696
BM_BiquadFilterFloatOptimized/1/3/23           2840 ns         2832 ns       246918
BM_BiquadFilterFloatOptimized/1/3/24           2842 ns         2834 ns       246695
BM_BiquadFilterFloatOptimized/1/3/25           2838 ns         2830 ns       247416
BM_BiquadFilterFloatOptimized/1/3/26           2846 ns         2838 ns       246729
BM_BiquadFilterFloatOptimized/1/3/27           2838 ns         2831 ns       247193
BM_BiquadFilterFloatOptimized/1/3/28           2839 ns         2832 ns       247448
BM_BiquadFilterFloatOptimized/1/3/29           2841 ns         2834 ns       247299
BM_BiquadFilterFloatOptimized/1/3/30           2843 ns         2836 ns       246862
BM_BiquadFilterFloatOptimized/1/3/31           2837 ns         2829 ns       246482
BM_BiquadFilterFloatOptimized/1/4/1             649 ns          648 ns      1080722
BM_BiquadFilterFloatOptimized/1/4/2             807 ns          805 ns       869521
BM_BiquadFilterFloatOptimized/1/4/3             805 ns          803 ns       871377
BM_BiquadFilterFloatOptimized/1/4/4             834 ns          831 ns       841567
BM_BiquadFilterFloatOptimized/1/4/5             834 ns          832 ns       841356
BM_BiquadFilterFloatOptimized/1/4/6             834 ns          832 ns       841467
BM_BiquadFilterFloatOptimized/1/4/7             834 ns          831 ns       841798
BM_BiquadFilterFloatOptimized/1/4/8            2197 ns         2192 ns       319360
BM_BiquadFilterFloatOptimized/1/4/9            2198 ns         2192 ns       319280
BM_BiquadFilterFloatOptimized/1/4/10           2208 ns         2202 ns       318344
BM_BiquadFilterFloatOptimized/1/4/11           2212 ns         2206 ns       316283
BM_BiquadFilterFloatOptimized/1/4/12           2452 ns         2447 ns       286906
BM_BiquadFilterFloatOptimized/1/4/13           2372 ns         2365 ns       295524
BM_BiquadFilterFloatOptimized/1/4/14           2506 ns         2499 ns       280957
BM_BiquadFilterFloatOptimized/1/4/15           2456 ns         2450 ns       285647
BM_BiquadFilterFloatOptimized/1/4/16           2448 ns         2442 ns       285905
BM_BiquadFilterFloatOptimized/1/4/17           2514 ns         2508 ns       279756
BM_BiquadFilterFloatOptimized/1/4/18           2366 ns         2360 ns       296402
BM_BiquadFilterFloatOptimized/1/4/19           2424 ns         2418 ns       288951
BM_BiquadFilterFloatOptimized/1/4/20           2454 ns         2447 ns       287009
BM_BiquadFilterFloatOptimized/1/4/21           2377 ns         2371 ns       294465
BM_BiquadFilterFloatOptimized/1/4/22           2491 ns         2484 ns       278138
BM_BiquadFilterFloatOptimized/1/4/23           2459 ns         2452 ns       284304
BM_BiquadFilterFloatOptimized/1/4/24           2445 ns         2438 ns       288879
BM_BiquadFilterFloatOptimized/1/4/25           2530 ns         2524 ns       278111
BM_BiquadFilterFloatOptimized/1/4/26           2391 ns         2385 ns       295861
BM_BiquadFilterFloatOptimized/1/4/27           2455 ns         2449 ns       286188
BM_BiquadFilterFloatOptimized/1/4/28           2459 ns         2452 ns       284560
BM_BiquadFilterFloatOptimized/1/4/29           2365 ns         2358 ns       297118
BM_BiquadFilterFloatOptimized/1/4/30           2517 ns         2509 ns       280309
BM_BiquadFilterFloatOptimized/1/4/31           2453 ns         2445 ns       286038
BM_BiquadFilterFloatNonOptimized/0/1/31        2043 ns         2036 ns       343632
BM_BiquadFilterFloatNonOptimized/0/2/31        4091 ns         4079 ns       171633
BM_BiquadFilterFloatNonOptimized/0/3/31        6128 ns         6108 ns       114396
BM_BiquadFilterFloatNonOptimized/0/4/31        8170 ns         8146 ns        85861
BM_BiquadFilterFloatNonOptimized/0/5/31       10210 ns        10178 ns        68777
BM_BiquadFilterFloatNonOptimized/0/6/31       12278 ns        12241 ns        57153
BM_BiquadFilterFloatNonOptimized/0/7/31       14304 ns        14262 ns        49100
BM_BiquadFilterFloatNonOptimized/0/8/31       16349 ns        16299 ns        42947
BM_BiquadFilterFloatNonOptimized/0/9/31       18392 ns        18335 ns        38182
BM_BiquadFilterFloatNonOptimized/0/10/31      20440 ns        20378 ns        34354
BM_BiquadFilterFloatNonOptimized/0/11/31      22481 ns        22412 ns        31238
BM_BiquadFilterFloatNonOptimized/0/12/31      24545 ns        24461 ns        28617
BM_BiquadFilterFloatNonOptimized/0/13/31      26585 ns        26496 ns        26424
BM_BiquadFilterFloatNonOptimized/0/14/31      28629 ns        28535 ns        24529
BM_BiquadFilterFloatNonOptimized/0/15/31      30744 ns        30642 ns        22848
BM_BiquadFilterFloatNonOptimized/0/16/31      32951 ns        32843 ns        21318
BM_BiquadFilterFloatNonOptimized/0/17/31      35244 ns        35132 ns        19892
BM_BiquadFilterFloatNonOptimized/0/18/31      37638 ns        37517 ns        18646
BM_BiquadFilterFloatNonOptimized/0/19/31      39639 ns        39512 ns        17722
BM_BiquadFilterFloatNonOptimized/0/20/31      41706 ns        41569 ns        16833
BM_BiquadFilterFloatNonOptimized/0/21/31      43783 ns        43631 ns        16039
BM_BiquadFilterFloatNonOptimized/0/22/31      46027 ns        45875 ns        15246
BM_BiquadFilterFloatNonOptimized/0/23/31      47548 ns        47368 ns        14782
BM_BiquadFilterFloatNonOptimized/0/24/31      49634 ns        49446 ns        14154
BM_BiquadFilterDoubleOptimized/0/1/31          2044 ns         2038 ns       343422
BM_BiquadFilterDoubleOptimized/0/2/31          2556 ns         2548 ns       275213
BM_BiquadFilterDoubleOptimized/0/3/31          2849 ns         2841 ns       245737
BM_BiquadFilterDoubleOptimized/0/4/31          3175 ns         3165 ns       221194
BM_BiquadFilterDoubleNonOptimized/0/1/31       2059 ns         2052 ns       341428
BM_BiquadFilterDoubleNonOptimized/0/2/31       4089 ns         4075 ns       171770
BM_BiquadFilterDoubleNonOptimized/0/3/31       6124 ns         6104 ns       114638
BM_BiquadFilterDoubleNonOptimized/0/4/31       8187 ns         8162 ns        85781

 *******************************************************************/

struct StateSpaceChannelOptimizedOptions
        : public  android::audio_utils::details::DefaultBiquadConstOptions
{
    // Overrides the Biquad Filter type
    template <typename T_, typename F_>
    using FilterType = android::audio_utils::BiquadStateSpace<
            T_, F_, true /* SEPARATE_CHANNEL_OPTIMIZATION */>;
};

template <typename F>
static void BM_BiquadFilter(benchmark::State& state, bool optimized) {
    bool isSubnormal = (state.range(0) == 1);
    const size_t channelCount = state.range(1);
    const size_t occupancy = state.range(2);

    std::vector<F> input(DATA_SIZE * channelCount);
    std::vector<F> output(DATA_SIZE * channelCount);
    std::array<F, android::audio_utils::kBiquadNumCoefs> coefs;

    // Initialize input buffer and coefs with deterministic pseudo-random values
    std::minstd_rand gen(occupancy);
    const F amplitude = isSubnormal ? std::numeric_limits<F>::min() * 0.1 : 1.;
    std::uniform_real_distribution<> dis(-amplitude, amplitude);
    for (size_t i = 0; i < DATA_SIZE * channelCount; ++i) {
        input[i] = dis(gen);
    }
    for (size_t i = 0; i < coefs.size(); ++i) {
        coefs[i] = (occupancy >> i & 1) * REF_COEFS[i];
    }

    android::audio_utils::BiquadFilter<
            F, true /* SAME_COEF_PER_CHANNEL */, StateSpaceChannelOptimizedOptions>
    biquadFilter(channelCount, coefs, optimized);

    // Run the test
    while (state.KeepRunning()) {
        benchmark::DoNotOptimize(input.data());
        benchmark::DoNotOptimize(output.data());
        biquadFilter.process(output.data(), input.data(), DATA_SIZE);
        benchmark::ClobberMemory();
    }
    state.SetComplexityN(state.range(1));  // O(channelCount)
}

static void BM_BiquadFilterFloatOptimized(benchmark::State& state) {
    BM_BiquadFilter<float>(state, true /* optimized */);
}

static void BM_BiquadFilterFloatNonOptimized(benchmark::State& state) {
    BM_BiquadFilter<float>(state, false /* optimized */);
}

static void BM_BiquadFilterDoubleOptimized(benchmark::State& state) {
    BM_BiquadFilter<double>(state, true /* optimized */);
}

static void BM_BiquadFilterDoubleNonOptimized(benchmark::State& state) {
    BM_BiquadFilter<double>(state, false /* optimized */);
}

static void BiquadFilterQuickArgs(benchmark::internal::Benchmark* b) {
    constexpr int CHANNEL_COUNT_BEGIN = 1;
    constexpr int CHANNEL_COUNT_END = 24;
    for (int k = 0; k < 1; k++) { // 0 for normal random data, 1 for subnormal random data
        for (int i = CHANNEL_COUNT_BEGIN; i <= CHANNEL_COUNT_END; ++i) {
            int j = (1 << android::audio_utils::kBiquadNumCoefs) - 1; // Full
            b->Args({k, i, j});
        }
    }
}

static void BiquadFilterFullArgs(benchmark::internal::Benchmark* b) {
    constexpr int CHANNEL_COUNT_BEGIN = 1;
    constexpr int CHANNEL_COUNT_END = 4;
    for (int k = 0; k < 2; k++) { // 0 for normal random data, 1 for subnormal random data
        for (int i = CHANNEL_COUNT_BEGIN; i <= CHANNEL_COUNT_END; ++i) {
            for (int j = 1; j < (1 << android::audio_utils::kBiquadNumCoefs); ++j) { // Occupancy
                b->Args({k, i, j});
            }
        }
    }
}

static void BiquadFilterDoubleArgs(benchmark::internal::Benchmark* b) {
    constexpr int CHANNEL_COUNT_BEGIN = 1;
    constexpr int CHANNEL_COUNT_END = 4;
    for (int k = 0; k < 1; k++) { // 0 for normal random data, 1 for subnormal random data
        for (int i = CHANNEL_COUNT_BEGIN; i <= CHANNEL_COUNT_END; ++i) {
            int j = (1 << android::audio_utils::kBiquadNumCoefs) - 1; // Full
            b->Args({k, i, j});
        }
    }
}

BENCHMARK(BM_BiquadFilterFloatOptimized)->Apply(BiquadFilterQuickArgs);
BENCHMARK(BM_BiquadFilterFloatOptimized)->Apply(BiquadFilterFullArgs);
// Other tests of interest
BENCHMARK(BM_BiquadFilterFloatNonOptimized)->Apply(BiquadFilterQuickArgs);
BENCHMARK(BM_BiquadFilterDoubleOptimized)->Apply(BiquadFilterDoubleArgs);
BENCHMARK(BM_BiquadFilterDoubleNonOptimized)->Apply(BiquadFilterDoubleArgs);

BENCHMARK_MAIN();
