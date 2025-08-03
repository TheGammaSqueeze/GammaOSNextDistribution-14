/*
 * Copyright (C) 2021 The Android Open Source Project
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

#ifndef ANDROID_FRAMEWORKS_ML_NN_RUNTIME_TELEMETRY_STATSD_H
#define ANDROID_FRAMEWORKS_ML_NN_RUNTIME_TELEMETRY_STATSD_H

#include <android-base/thread_annotations.h>

#include <array>
#include <condition_variable>
#include <functional>
#include <limits>
#include <map>
#include <mutex>
#include <queue>
#include <string>
#include <thread>
#include <utility>
#include <vector>

#include "Telemetry.h"

namespace android::nn::telemetry {

using ModelArchHash = std::array<uint8_t, BYTE_SIZE_OF_MODEL_ARCH_HASH>;

constexpr int64_t kSumTimeDefault = 0;
constexpr int64_t kMinTimeDefault = std::numeric_limits<int64_t>::max();
constexpr int64_t kMaxTimeDefault = std::numeric_limits<int64_t>::min();

// For CompilationCompleted: isExecution = false, executionMode = SYNC, errorCode = 0
// For CompilationFailed: isExecution = false, executionMode = SYNC, errorCode != 0
// For ExecutionCompleted: isExecution = true, errorCode = 0, fallbackToCpuFromError = false
// For ExecutionFailed: isExecution = true, errorCode != 0, fallbackToCpuFromError = false
struct AtomKey {
    bool isExecution;
    ModelArchHash modelArchHash;
    std::string deviceId;
    ExecutionMode executionMode;
    int32_t errorCode;
    DataClass inputDataClass;
    DataClass outputDataClass;
    bool fallbackToCpuFromError;
    bool introspectionEnabled;
    bool cacheEnabled;
    bool hasControlFlow;
    bool hasDynamicTemporaries;
};

bool operator==(const AtomKey& lhs, const AtomKey& rhs);
bool operator<(const AtomKey& lhs, const AtomKey& rhs);

// For CompilationCompleted, all timings except compilationTimeMillis omitted
// For CompilationFailed, all timings omitted
// For ExecutionCompleted, compilationTimeMillis timing omitted
// For ExecutionFailed, all timings omitted
struct AtomValue {
    int32_t count = 0;

    // AccumulatedTiming stores all the information needed to calculate the average, min, max, and
    // standard deviation of all the accumulated timings. When count == 0, AccumulatedTiming is
    // ignored. When count > 0:
    // * average = sumTime / count
    // * minimum = minTime
    // * maximum = maxTime
    // * variance = sumSquaredTime / count - average * average
    // * standard deviation = sqrt(variance)
    // * sample standard deviation = sqrt(variance * count / (count - 1))
    struct AccumulatedTiming {
        int64_t sumTime = kSumTimeDefault;
        int64_t minTime = kMinTimeDefault;
        int64_t maxTime = kMaxTimeDefault;
        // Sum of each squared timing, e.g.: t1^2 + t2^2 + ... + tn^2
        int64_t sumSquaredTime = kSumTimeDefault;
        int32_t count = 0;
    };
    AccumulatedTiming compilationTimeMillis;
    AccumulatedTiming durationRuntimeMicros;
    AccumulatedTiming durationDriverMicros;
    AccumulatedTiming durationHardwareMicros;
};

void combineAtomValues(AtomValue* acculatedValue, const AtomValue& value);

// Atom type to be sent to Statsd Telemetry
using Atom = std::pair<AtomKey, AtomValue>;

// Helper class to locally aggregate and retrieve telemetry atoms.
class AtomAggregator {
   public:
    bool empty() const;

    void push(Atom&& atom);

    // Precondition: !empty()
    Atom pop();

   private:
    std::map<AtomKey, AtomValue> mAggregate;
    // Pointer to keys of mAggregate to ensure atoms are logged in a fair order. Using pointers into
    // a std::map is guaranteed to work because references to elements are guaranteed to be valid
    // until that element is erased.
    std::queue<const AtomKey*> mOrder;
};

using LoggerFn = std::function<void(Atom&&)>;

// AsyncLogger minimizes the call to `write`, so that the calling thread which handles the
// compilation or execution is not slowed down by writing to statsd. Instead, AsyncLogger
// contains a dedicated thread that will handle logging to statsd in the background.
// This class is thread-safe.
class AsyncLogger {
   public:
    AsyncLogger(LoggerFn logger, Duration loggingQuietPeriodDuration);
    AsyncLogger(const AsyncLogger&) = delete;
    AsyncLogger(AsyncLogger&&) = delete;
    AsyncLogger& operator=(const AsyncLogger&) = delete;
    AsyncLogger& operator=(AsyncLogger&&) = delete;
    ~AsyncLogger();

    void write(Atom&& atom);

   private:
    enum class Result {
        SUCCESS,
        TEARDOWN,
    };

    // Precondition: output != nullptr
    // Precondition: output->empty()
    Result takeAll(std::vector<Atom>* output, bool blockUntilDataIsAvailable);

    Result sleepFor(Duration duration);

    mutable std::mutex mMutex;
    mutable std::condition_variable mNotEmptyOrTeardown;
    mutable std::vector<Atom> mChannel GUARDED_BY(mMutex);
    mutable bool mTeardown GUARDED_BY(mMutex) = false;
    std::thread mThread;
};

// Create an Atom from a diagnostic info object.
Atom createAtomFrom(const DiagnosticCompilationInfo* info);
Atom createAtomFrom(const DiagnosticExecutionInfo* info);

// Log an Atom to statsd from a diagnostic info object.
void logCompilationToStatsd(const DiagnosticCompilationInfo* info);
void logExecutionToStatsd(const DiagnosticExecutionInfo* info);

}  // namespace android::nn::telemetry

#endif  // ANDROID_FRAMEWORKS_ML_NN_RUNTIME_TELEMETRY_STATSD_H
