/*
 * Copyright 2023 The Android Open Source Project
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

#include <audio_utils/mutex.h>

#include <android-base/logging.h>
#include <benchmark/benchmark.h>
#include <shared_mutex>
#include <thread>
#include <utils/RWLock.h>
#include <utils/Timers.h>

/*
On Pixel 7 U arm64-v8a

Note: to bump up the scheduler clock frequency, one can use the toybox uclampset:
$ adb shell uclampset -m 1024 /data/benchmarktest64/audio_mutex_benchmark/audio_mutex_benchmark

For simplicity these tests use the regular invocation:
$ atest audio_mutex_benchmark

Benchmark                                                     Time        CPU        Iteration
audio_mutex_benchmark:
  #BM_atomic_add_equals<int32_t>                                 6.49708926103908 ns      6.472003500961985 ns     100653478
  #BM_atomic_add_to_seq_cst<int16_t>                            6.612271967786937 ns      6.581374958282836 ns     106359096
  #BM_atomic_add_to_seq_cst<int32_t>                            6.557587423605134 ns      6.526606701594883 ns     107255215
  #BM_atomic_add_to_seq_cst<int64_t>                            6.611507804022058 ns      6.581571911586092 ns     106345485
  #BM_atomic_add_to_seq_cst<float>                              7.940855618974662 ns      7.903428099126927 ns      88699973
  #BM_atomic_add_to_seq_cst<double>                             7.892445499019113 ns     7.8553659380799665 ns      89357153
  #BM_atomic_add_to_relaxed<int16_t>                            5.184282118879067 ns       5.16066629475692 ns     135599206
  #BM_atomic_add_to_relaxed<int32_t>                           5.1525768457041385 ns       5.13082227851535 ns     135158791
  #BM_atomic_add_to_relaxed<int64_t>                             5.18196641836829 ns      5.159386369625577 ns     135816093
  #BM_atomic_add_to_relaxed<float>                              7.770385032959841 ns       7.73380439539666 ns      90647928
  #BM_atomic_add_to_relaxed<double>                             7.774301916271472 ns      7.738535149984901 ns      90634221
  #BM_atomic_add_to_unordered<int16_t>                         0.3536779780000643 ns    0.35200833499999895 ns    1000000000
  #BM_atomic_add_to_unordered<int32_t>                        0.35392805999993016 ns    0.35196308799999976 ns    1000000000
  #BM_atomic_add_to_unordered<int64_t>                        0.35319002300002467 ns    0.35198640199999964 ns    1000000000
  #BM_atomic_add_to_unordered<float>                             0.70537668831226 ns     0.7020379879090289 ns     997049929
  #BM_atomic_add_to_unordered<double>                          0.7051115684923829 ns     0.7020150599803927 ns     997060395
  #BM_gettid                                                   2.1148936981430118 ns     2.1060756400940295 ns     332362067
  #BM_systemTime                                               45.252249624452496 ns       45.0340629111274 ns      15544150
  #BM_thread_8_variables                                       2.8213460062780684 ns      2.808115782371781 ns     249265925
  #BM_thread_local_8_variables                                  2.821866488468177 ns     2.8083880672269608 ns     249247144
  #BM_StdMutexLockUnlock                                       20.443066322018925 ns     20.352346307194033 ns      34391639
  #BM_RWMutexReadLockUnlock                                     17.16239599718925 ns     17.081916542086816 ns      41022569
  #BM_RWMutexWriteLockUnlock                                   19.853425611091456 ns      19.76218186210376 ns      35401669
  #BM_SharedMutexReadLockUnlock                                  39.3766244173411 ns      39.22216162561333 ns      17844783
  #BM_SharedMutexWriteLockUnlock                                42.54361761104441 ns     42.346080362033284 ns      16528427
  #BM_AudioUtilsMutexLockUnlock                                32.316459678551176 ns     32.165598159419815 ns      21770091
  #BM_AudioUtilsPIMutexLockUnlock                               32.91919379029218 ns      32.77652120372695 ns      21350822
  #BM_StdMutexInitializationLockUnlock                         30.578741677235417 ns      30.43282384371753 ns      22998964
  #BM_RWMutexInitializationReadLockUnlock                      27.291456623695325 ns     27.160228920838776 ns      25787779
  #BM_RWMutexInitializationWriteLockUnlock                     30.177246005611483 ns     30.036540230009923 ns      23284993
  #BM_SharedMutexInitializationReadLockUnlock                  57.531404407024816 ns     57.260222202532894 ns      12223173
  #BM_SharedMutexInitializationWriteLockUnlock                   59.4755204867324 ns      59.19673708562941 ns      11821579
  #BM_AudioUtilsMutexInitializationLockUnlock                   44.70003716328854 ns     44.516311007242074 ns      15719814
  #BM_AudioUtilsPIMutexInitializationLockUnlock                  50.0569598188049 ns      49.76151232748948 ns      14066530
  #BM_StdMutexBlockingConditionVariable/threads:2              21567.811717894936 ns      24430.56515004263 ns         32924
  #BM_AudioUtilsMutexBlockingConditionVariable/threads:2       17895.140258052194 ns       21379.1065317615 ns         64638
  #BM_AudioUtilsPIMutexBlockingConditionVariable/threads:2       16234.8055847075 ns        19758.387649925 ns         53360
  #BM_StdMutexScopedLockUnlock/threads:1                        33.67012270000208 ns      33.51255456709671 ns      20617278
  #BM_StdMutexScopedLockUnlock/threads:2                       334.03488387281124 ns       663.680747963286 ns       4746864
  #BM_StdMutexScopedLockUnlock/threads:4                       128.53316611793798 ns      401.5973070022828 ns       1473748
  #BM_StdMutexScopedLockUnlock/threads:8                        121.5346446122214 ns      431.4386839654385 ns       1614912
  #BM_RWMutexScopedReadLockUnlock/threads:1                      32.1396832808462 ns     31.999380498079336 ns      21767487
  #BM_RWMutexScopedReadLockUnlock/threads:2                    170.84739199998467 ns     339.15160600000127 ns       2000000
  #BM_RWMutexScopedReadLockUnlock/threads:4                     246.6480510579401 ns      975.0323799866918 ns        745584
  #BM_RWMutexScopedReadLockUnlock/threads:8                     238.1892263738593 ns     1826.4869477861284 ns        797336
  #BM_RWMutexScopedWriteLockUnlock/threads:1                    35.00912072790768 ns      34.85540479077848 ns      20082498
  #BM_RWMutexScopedWriteLockUnlock/threads:2                    223.1827494999834 ns      438.1546670000002 ns       2000000
  #BM_RWMutexScopedWriteLockUnlock/threads:4                   486.84123644366286 ns     1672.5719360034366 ns        931300
  #BM_RWMutexScopedWriteLockUnlock/threads:8                      564.87138969739 ns     2151.4437615176666 ns        464504
  #BM_SharedMutexScopedReadLockUnlock/threads:1                 70.36601526045072 ns      69.84103085964486 ns       8484414
  #BM_SharedMutexScopedReadLockUnlock/threads:2                 379.4861527643546 ns      753.3688988828279 ns       1567172
  #BM_SharedMutexScopedReadLockUnlock/threads:4                 347.9172981302136 ns      1101.924198607498 ns        621044
  #BM_SharedMutexScopedReadLockUnlock/threads:8                343.91707104488904 ns      1432.435581480825 ns        946824
  #BM_SharedMutexScopedWriteLockUnlock/threads:1                76.38441149160603 ns      76.09520014228487 ns       7489222
  #BM_SharedMutexScopedWriteLockUnlock/threads:2                  549.22601694552 ns      992.5252101883976 ns        644184
  #BM_SharedMutexScopedWriteLockUnlock/threads:4                4286.404760312738 ns     10445.512869046388 ns        194420
  #BM_SharedMutexScopedWriteLockUnlock/threads:8               2777.3490250002196 ns       9345.93453749998 ns         80000
  #BM_AudioUtilsMutexScopedLockUnlock/threads:1                 67.12805853866571 ns      66.83999986247574 ns      10470891
  #BM_AudioUtilsMutexScopedLockUnlock/threads:2                367.18428717404385 ns      720.3494810108673 ns        988460
  #BM_AudioUtilsMutexScopedLockUnlock/threads:4                247.06318310687618 ns      713.1825332376544 ns        847232
  #BM_AudioUtilsMutexScopedLockUnlock/threads:8                 321.6128632660656 ns     1219.0056378491781 ns       1102016
  #BM_AudioUtilsPIMutexScopedLockUnlock/threads:1               68.06778275108272 ns      67.82319073618316 ns       8610878
  #BM_AudioUtilsPIMutexScopedLockUnlock/threads:2              1740.7659979908651 ns       2080.35904028861 ns        535536
  #BM_AudioUtilsPIMutexScopedLockUnlock/threads:4               44770.96510142291 ns     61714.283330752354 ns         25832
  #BM_AudioUtilsPIMutexScopedLockUnlock/threads:8               31532.62551468983 ns      39864.36350786635 ns         16272
  #BM_StdMutexReverseScopedLockUnlock/threads:1                 33.53062718758725 ns      33.37078532917351 ns      20302060
  #BM_StdMutexReverseScopedLockUnlock/threads:2                  56.8951118357229 ns     110.10677172633986 ns       6485584
  #BM_StdMutexReverseScopedLockUnlock/threads:4                 136.7939034223377 ns      471.5097278019701 ns       3746684
  #BM_StdMutexReverseScopedLockUnlock/threads:8                159.71482571380815 ns      672.6030946954844 ns       1347984
  #BM_AudioUtilsMutexReverseScopedLockUnlock/threads:1          67.08830703993506 ns      66.79194730109215 ns       9299168
  #BM_AudioUtilsMutexReverseScopedLockUnlock/threads:2          349.1989208775619 ns      680.7213221240016 ns       1205424
  #BM_AudioUtilsMutexReverseScopedLockUnlock/threads:4         242.54533324270986 ns      751.2404474633255 ns       1537288
  #BM_AudioUtilsMutexReverseScopedLockUnlock/threads:8           264.199964885266 ns      1063.577426763085 ns        585224
  #BM_AudioUtilsPIMutexReverseScopedLockUnlock/threads:1        68.08892559027429 ns      67.76412547568847 ns      10221231
  #BM_AudioUtilsPIMutexReverseScopedLockUnlock/threads:2        2234.804406832892 ns      2710.598153659913 ns       2438554
  #BM_AudioUtilsPIMutexReverseScopedLockUnlock/threads:4       30793.467662667594 ns      40396.41114605845 ns         20348
  #BM_AudioUtilsPIMutexReverseScopedLockUnlock/threads:8        29475.46524613768 ns     38111.187252376985 ns         20192
  #BM_empty_while                                             0.35276680499998747 ns    0.35103430999999574 ns    1000000000

*/

// ---

template<typename Integer>
static void BM_atomic_add_equals(benchmark::State &state) {
    Integer i = 10;
    std::atomic<Integer> dst;
    while (state.KeepRunning()) {
        dst += i;
    }
    LOG(DEBUG) << __func__ << "  " << dst.load();
}

BENCHMARK(BM_atomic_add_equals<int32_t>);

template <typename T>
static void BM_atomic_add_to(benchmark::State &state, std::memory_order order) {
    int64_t i64 = 10;
    std::atomic<T> dst;
    while (state.KeepRunning()) {
        android::audio_utils::atomic_add_to(dst, i64, order);
    }
    LOG(DEBUG) << __func__ << "  " << dst.load();
}

// Avoid macro issues with the comma.
template <typename T>
static void BM_atomic_add_to_seq_cst(benchmark::State &state) {
    BM_atomic_add_to<T>(state, std::memory_order_seq_cst);
}

BENCHMARK(BM_atomic_add_to_seq_cst<int16_t>);

BENCHMARK(BM_atomic_add_to_seq_cst<int32_t>);

BENCHMARK(BM_atomic_add_to_seq_cst<int64_t>);

BENCHMARK(BM_atomic_add_to_seq_cst<float>);

BENCHMARK(BM_atomic_add_to_seq_cst<double>);

template <typename T>
static void BM_atomic_add_to_relaxed(benchmark::State &state) {
    BM_atomic_add_to<T>(state, std::memory_order_relaxed);
}

BENCHMARK(BM_atomic_add_to_relaxed<int16_t>);

BENCHMARK(BM_atomic_add_to_relaxed<int32_t>);

BENCHMARK(BM_atomic_add_to_relaxed<int64_t>);

BENCHMARK(BM_atomic_add_to_relaxed<float>);

BENCHMARK(BM_atomic_add_to_relaxed<double>);

template <typename T>
static void BM_atomic_add_to_unordered(benchmark::State &state) {
    int64_t i64 = 10;
    android::audio_utils::unordered_atomic<T> dst;
    while (state.KeepRunning()) {
        android::audio_utils::atomic_add_to(dst, i64, std::memory_order_relaxed);
    }
    LOG(DEBUG) << __func__ << "  " << dst.load();
}

BENCHMARK(BM_atomic_add_to_unordered<int16_t>);

BENCHMARK(BM_atomic_add_to_unordered<int32_t>);

BENCHMARK(BM_atomic_add_to_unordered<int64_t>);

BENCHMARK(BM_atomic_add_to_unordered<float>);

BENCHMARK(BM_atomic_add_to_unordered<double>);

// Benchmark gettid().  The mutex class uses this to get the linux thread id.
static void BM_gettid(benchmark::State &state) {
    int32_t value = 0;
    while (state.KeepRunning()) {
        value ^= android::audio_utils::gettid_wrapper();  // ensure the return value used.
    }
    ALOGD("%s: value:%d", __func__, value);
}

BENCHMARK(BM_gettid);

// Benchmark systemTime().  The mutex class uses this for timing.
static void BM_systemTime(benchmark::State &state) {
    int64_t value = 0;
    while (state.KeepRunning()) {
        value ^= systemTime();
    }
    ALOGD("%s: value:%lld", __func__, (long long)value);
}

BENCHMARK(BM_systemTime);

// Benchmark access to 8 thread local storage variables by compiler built_in __thread.
__thread volatile int tls_value1 = 1;
__thread volatile int tls_value2 = 2;
__thread volatile int tls_value3 = 3;
__thread volatile int tls_value4 = 4;
__thread volatile int tls_value5 = 5;
__thread volatile int tls_value6 = 6;
__thread volatile int tls_value7 = 7;
__thread volatile int tls_value8 = 8;

static void BM_thread_8_variables(benchmark::State &state) {
    while (state.KeepRunning()) {
        tls_value1 ^= tls_value1 ^ tls_value2 ^ tls_value3 ^ tls_value4 ^
                tls_value5 ^ tls_value6 ^ tls_value7 ^ tls_value8;
    }
    ALOGD("%s: value:%d", __func__, tls_value1);
}

BENCHMARK(BM_thread_8_variables);

// Benchmark access to 8 thread local storage variables through the
// the C/C++ 11 standard thread_local.
thread_local volatile int tlsa_value1 = 1;
thread_local volatile int tlsa_value2 = 2;
thread_local volatile int tlsa_value3 = 3;
thread_local volatile int tlsa_value4 = 4;
thread_local volatile int tlsa_value5 = 5;
thread_local volatile int tlsa_value6 = 6;
thread_local volatile int tlsa_value7 = 7;
thread_local volatile int tlsa_value8 = 8;

static void BM_thread_local_8_variables(benchmark::State &state) {
    while (state.KeepRunning()) {
        tlsa_value1 ^= tlsa_value1 ^ tlsa_value2 ^ tlsa_value3 ^ tlsa_value4 ^
                tlsa_value5 ^ tlsa_value6 ^ tlsa_value7 ^ tlsa_value8;
    }
    ALOGD("%s: value:%d", __func__, tlsa_value1);
}

BENCHMARK(BM_thread_local_8_variables);

// ---

// std::mutex is the reference mutex that we compare against.
// It is based on Bionic pthread_mutex* support.

// RWLock is a specialized Android mutex class based on
// Bionic pthread_rwlock* which in turn is based on the
// original ART shared reader mutex.

// Test shared read lock performance.
class RWReadMutex : private android::RWLock {
public:
    void lock() { readLock(); }
    bool try_lock() { return tryReadLock() == 0; }
    using android::RWLock::unlock;
};

// Test exclusive write lock performance.
class RWWriteMutex : private android::RWLock {
public:
    void lock() { writeLock(); }
    bool try_lock() { return tryWriteLock() == 0; }
    using android::RWLock::unlock;
};

// std::shared_mutex lock/unlock behavior is default exclusive.
// We subclass to create the shared reader equivalent.
//
// Unfortunately std::shared_mutex implementation can contend on an internal
// mutex with multiple readers (even with no writers), resulting in worse lock performance
// than other shared mutexes.
// This is due to the portability desire in the original reference implementation:
// https://www.open-std.org/jtc1/sc22/wg21/docs/papers/2007/n2406.html#shared_mutex_imp
class SharedReadMutex : private std::shared_mutex {
public:
    void lock() { std::shared_mutex::lock_shared(); }
    bool try_lock() { return std::shared_mutex::try_lock_shared(); }
    void unlock() { std::shared_mutex::unlock_shared(); }
};

// audio_utils mutex is designed to have mutex order checking, statistics,
// deadlock detection, and priority inheritance capabilities,
// so it is higher overhead than just the std::mutex that it is based upon.
//
// audio_utils mutex without priority inheritance.
class AudioMutex : public android::audio_utils::mutex {
public:
    AudioMutex() : android::audio_utils::mutex(false /* priority_inheritance */) {}
};

// audio_utils mutex with priority inheritance.
class AudioPIMutex : public android::audio_utils::mutex {
public:
    AudioPIMutex() : android::audio_utils::mutex(true /* priority_inheritance */) {}
};

template <typename Mutex>
void MutexLockUnlock(benchmark::State& state) {
    Mutex m;
    while (state.KeepRunning()) {
        m.lock();
        m.unlock();
    }
}

static void BM_StdMutexLockUnlock(benchmark::State &state) {
    MutexLockUnlock<std::mutex>(state);
}

// Benchmark repeated mutex lock/unlock from a single thread
// using the mutex.
BENCHMARK(BM_StdMutexLockUnlock);

static void BM_RWMutexReadLockUnlock(benchmark::State& state) {
    MutexLockUnlock<RWReadMutex>(state);
}

BENCHMARK(BM_RWMutexReadLockUnlock);

static void BM_RWMutexWriteLockUnlock(benchmark::State& state) {
    MutexLockUnlock<RWWriteMutex>(state);
}

BENCHMARK(BM_RWMutexWriteLockUnlock);

static void BM_SharedMutexReadLockUnlock(benchmark::State& state) {
    MutexLockUnlock<SharedReadMutex>(state);
}

BENCHMARK(BM_SharedMutexReadLockUnlock);

static void BM_SharedMutexWriteLockUnlock(benchmark::State& state) {
    MutexLockUnlock<std::shared_mutex>(state);
}

BENCHMARK(BM_SharedMutexWriteLockUnlock);

static void BM_AudioUtilsMutexLockUnlock(benchmark::State &state) {
    MutexLockUnlock<AudioMutex>(state);
}

BENCHMARK(BM_AudioUtilsMutexLockUnlock);

static void BM_AudioUtilsPIMutexLockUnlock(benchmark::State &state) {
    MutexLockUnlock<AudioPIMutex>(state);
}

BENCHMARK(BM_AudioUtilsPIMutexLockUnlock);

// ---

template <typename Mutex>
void MutexInitializationLockUnlock(benchmark::State& state) {
    while (state.KeepRunning()) {
        Mutex m;
        m.lock();
        m.unlock();
    }
}

static void BM_StdMutexInitializationLockUnlock(benchmark::State &state) {
    MutexInitializationLockUnlock<std::mutex>(state);
}

// Benchmark repeated mutex creation then lock/unlock from a single thread
// using the mutex.
BENCHMARK(BM_StdMutexInitializationLockUnlock);

static void BM_RWMutexInitializationReadLockUnlock(benchmark::State& state) {
    MutexInitializationLockUnlock<RWReadMutex>(state);
}

BENCHMARK(BM_RWMutexInitializationReadLockUnlock);

static void BM_RWMutexInitializationWriteLockUnlock(benchmark::State& state) {
    MutexInitializationLockUnlock<RWWriteMutex>(state);
}

BENCHMARK(BM_RWMutexInitializationWriteLockUnlock);

static void BM_SharedMutexInitializationReadLockUnlock(benchmark::State& state) {
    MutexInitializationLockUnlock<SharedReadMutex>(state);
}

BENCHMARK(BM_SharedMutexInitializationReadLockUnlock);

static void BM_SharedMutexInitializationWriteLockUnlock(benchmark::State& state) {
    MutexInitializationLockUnlock<std::shared_mutex>(state);
}

BENCHMARK(BM_SharedMutexInitializationWriteLockUnlock);

static void BM_AudioUtilsMutexInitializationLockUnlock(benchmark::State &state) {
    MutexInitializationLockUnlock<AudioMutex>(state);
}

BENCHMARK(BM_AudioUtilsMutexInitializationLockUnlock);

static void BM_AudioUtilsPIMutexInitializationLockUnlock(benchmark::State &state) {
    MutexInitializationLockUnlock<AudioPIMutex>(state);
}

BENCHMARK(BM_AudioUtilsPIMutexInitializationLockUnlock);

// ---

static constexpr size_t THREADS = 2;

template <typename Mutex, typename UniqueLock, typename ConditionVariable>
class MutexBlockingConditionVariable {
    Mutex m;
    ConditionVariable cv[THREADS];
    bool wake[THREADS];

public:
    void run(benchmark::State& state) {
        const size_t local = state.thread_index();
        const size_t remote = (local + 1) % THREADS;
        if (local == 0) wake[local] = true;
        for (auto _ : state) {
            UniqueLock ul(m);
            cv[local].wait(ul, [&]{ return wake[local]; });
            wake[remote] = true;
            wake[local] = false;
            cv[remote].notify_one();
        }
        UniqueLock ul(m);
        wake[remote] = true;
        cv[remote].notify_one();
    }
};

MutexBlockingConditionVariable<std::mutex,
            std::unique_lock<std::mutex>,
            std::condition_variable> CvStd;

static void BM_StdMutexBlockingConditionVariable(benchmark::State &state) {
    CvStd.run(state);
}

// Benchmark 2 threads that use condition variables to wake each other up,
// where only one thread is active at a given time.  This benchmark
// uses mutex, unique_lock, and condition_variable.
BENCHMARK(BM_StdMutexBlockingConditionVariable)->Threads(THREADS);

MutexBlockingConditionVariable<AudioMutex,
        android::audio_utils::unique_lock,
        android::audio_utils::condition_variable> CvAu;

static void BM_AudioUtilsMutexBlockingConditionVariable(benchmark::State &state) {
    CvAu.run(state);
}

BENCHMARK(BM_AudioUtilsMutexBlockingConditionVariable)->Threads(THREADS);

MutexBlockingConditionVariable<AudioPIMutex,
        android::audio_utils::unique_lock,
        android::audio_utils::condition_variable> CvAuPI;

static void BM_AudioUtilsPIMutexBlockingConditionVariable(benchmark::State &state) {
    CvAuPI.run(state);
}

BENCHMARK(BM_AudioUtilsPIMutexBlockingConditionVariable)->Threads(THREADS);

// ---

// Benchmark scoped_lock where two threads try to obtain the
// same 2 locks with the same initial acquisition order.
// This uses std::scoped_lock.

static constexpr size_t THREADS_SCOPED = 8;

template <typename Mutex, typename ScopedLock>
class MutexScopedLockUnlock {
    const bool reverse_;
    Mutex m1_, m2_;
    int counter_ = 0;

public:
    MutexScopedLockUnlock(bool reverse = false) : reverse_(reverse) {}

    void run(benchmark::State& state) {
        const size_t index = state.thread_index();
        for (auto _ : state) {
            if (!reverse_ || index & 1) {
                ScopedLock s1(m1_, m2_);
                ++counter_;
            } else {
                ScopedLock s1(m2_, m1_);
                ++counter_;
            }
        }
    }
};

MutexScopedLockUnlock<std::mutex,
        std::scoped_lock<std::mutex, std::mutex>> ScopedStd;

static void BM_StdMutexScopedLockUnlock(benchmark::State &state) {
    ScopedStd.run(state);
}

BENCHMARK(BM_StdMutexScopedLockUnlock)->ThreadRange(1, THREADS_SCOPED);

MutexScopedLockUnlock<RWReadMutex,
        std::scoped_lock<RWReadMutex, RWReadMutex>> ScopedRwRead;

static void BM_RWMutexScopedReadLockUnlock(benchmark::State &state) {
    ScopedRwRead.run(state);
}

BENCHMARK(BM_RWMutexScopedReadLockUnlock)->ThreadRange(1, THREADS_SCOPED);

MutexScopedLockUnlock<RWWriteMutex,
        std::scoped_lock<RWWriteMutex, RWWriteMutex>> ScopedRwWrite;

static void BM_RWMutexScopedWriteLockUnlock(benchmark::State &state) {
    ScopedRwWrite.run(state);
}

BENCHMARK(BM_RWMutexScopedWriteLockUnlock)->ThreadRange(1, THREADS_SCOPED);

MutexScopedLockUnlock<SharedReadMutex,
        std::scoped_lock<SharedReadMutex, SharedReadMutex>> ScopedSharedRead;

static void BM_SharedMutexScopedReadLockUnlock(benchmark::State &state) {
    ScopedSharedRead.run(state);
}

BENCHMARK(BM_SharedMutexScopedReadLockUnlock)->ThreadRange(1, THREADS_SCOPED);

MutexScopedLockUnlock<std::shared_mutex,
        std::scoped_lock<std::shared_mutex, std::shared_mutex>> ScopedSharedWrite;

static void BM_SharedMutexScopedWriteLockUnlock(benchmark::State &state) {
    ScopedSharedWrite.run(state);
}

BENCHMARK(BM_SharedMutexScopedWriteLockUnlock)->ThreadRange(1, THREADS_SCOPED);

MutexScopedLockUnlock<AudioMutex,
        android::audio_utils::scoped_lock<
                AudioMutex, AudioMutex>> ScopedAu;

static void BM_AudioUtilsMutexScopedLockUnlock(benchmark::State &state) {
    ScopedAu.run(state);
}

BENCHMARK(BM_AudioUtilsMutexScopedLockUnlock)->ThreadRange(1, THREADS_SCOPED);

MutexScopedLockUnlock<AudioPIMutex,
        android::audio_utils::scoped_lock<
                AudioPIMutex, AudioPIMutex>> ScopedAuPI;

static void BM_AudioUtilsPIMutexScopedLockUnlock(benchmark::State &state) {
    ScopedAuPI.run(state);
}

BENCHMARK(BM_AudioUtilsPIMutexScopedLockUnlock)->ThreadRange(1, THREADS_SCOPED);

MutexScopedLockUnlock<std::mutex,
        std::scoped_lock<std::mutex, std::mutex>> ReverseScopedStd(true);

static void BM_StdMutexReverseScopedLockUnlock(benchmark::State &state) {
    ReverseScopedStd.run(state);
}

// Benchmark scoped_lock with odd thread having reversed scoped_lock mutex acquisition order.
// This uses std::scoped_lock.
BENCHMARK(BM_StdMutexReverseScopedLockUnlock)->ThreadRange(1, THREADS_SCOPED);

MutexScopedLockUnlock<AudioMutex,
        android::audio_utils::scoped_lock<
                AudioMutex, AudioMutex>> ReverseScopedAu(true);

static void BM_AudioUtilsMutexReverseScopedLockUnlock(benchmark::State &state) {
    ReverseScopedAu.run(state);
}

BENCHMARK(BM_AudioUtilsMutexReverseScopedLockUnlock)->ThreadRange(1, THREADS_SCOPED);

MutexScopedLockUnlock<AudioPIMutex,
        android::audio_utils::scoped_lock<
                AudioPIMutex, AudioPIMutex>> ReverseScopedAuPI(true);

static void BM_AudioUtilsPIMutexReverseScopedLockUnlock(benchmark::State &state) {
    ReverseScopedAuPI.run(state);
}

BENCHMARK(BM_AudioUtilsPIMutexReverseScopedLockUnlock)->ThreadRange(1, THREADS_SCOPED);

static void BM_empty_while(benchmark::State &state) {

    while (state.KeepRunning()) {
        ;
    }
    ALOGD("%s", android::audio_utils::mutex::all_stats_to_string().c_str());
}

// Benchmark to see the cost of doing nothing.
BENCHMARK(BM_empty_while);

BENCHMARK_MAIN();
