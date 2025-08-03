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

#include <gtest/gtest.h>

#include <algorithm>
#include <condition_variable>
#include <numeric>

#include "Telemetry.h"
#include "TelemetryStatsd.h"

namespace android::nn::telemetry {

constexpr auto kNoTiming = std::numeric_limits<uint64_t>::max();
constexpr auto kNoAggregateTiming = AtomValue::AccumulatedTiming{};
constexpr ModelArchHash kExampleModelArchHash = {1, 2, 3};
constexpr const char* kExampleDeviceId = "driver1=version1,driver2=version2";
constexpr auto kLongTime = std::chrono::seconds(60 * 60 * 24);

const AtomKey kExampleKey = {
        .isExecution = true,
        .modelArchHash = kExampleModelArchHash,
        .deviceId = kExampleDeviceId,
        .executionMode = ExecutionMode::SYNC,
        .errorCode = ANEURALNETWORKS_NO_ERROR,
        .inputDataClass = DataClass::FLOAT32,
        .outputDataClass = DataClass::FLOAT32,
        .fallbackToCpuFromError = false,
        .introspectionEnabled = false,
        .cacheEnabled = false,
        .hasControlFlow = false,
        .hasDynamicTemporaries = false,
};

// This class is thread-safe.
class Signal {
   public:
    void signal() {
        {
            std::lock_guard hold(mMutex);
            mSignalled = true;
        }
        mWaitForSignal.notify_all();
    }

    void wait() {
        std::unique_lock lock(mMutex);
        mWaitForSignal.wait(lock, [this]() REQUIRES(mMutex) { return mSignalled; });
    }

   private:
    mutable std::mutex mMutex;
    mutable std::condition_variable mWaitForSignal;
    mutable bool mSignalled GUARDED_BY(mMutex) = false;
};

bool operator==(const AtomValue::AccumulatedTiming& lhs, const AtomValue::AccumulatedTiming& rhs) {
    constexpr auto toTuple = [](const AtomValue::AccumulatedTiming& v) {
        return std::tie(v.sumTime, v.minTime, v.maxTime, v.sumSquaredTime, v.count);
    };
    return toTuple(lhs) == toTuple(rhs);
}

bool operator==(const AtomValue& lhs, const AtomValue& rhs) {
    constexpr auto toTuple = [](const AtomValue& v) {
        return std::tie(v.count, v.compilationTimeMillis, v.durationRuntimeMicros,
                        v.durationDriverMicros, v.durationHardwareMicros);
    };
    return toTuple(lhs) == toTuple(rhs);
}

AtomValue::AccumulatedTiming accumulatedTimingsFrom(std::initializer_list<int64_t> values) {
    CHECK_GT(values.size(), 0u);
    const int64_t sumTime = std::accumulate(values.begin(), values.end(), 0, std::plus<>{});
    const int64_t sumSquaredTime = std::accumulate(
            values.begin(), values.end(), 0, [](int64_t acc, int64_t v) { return acc + v * v; });
    const auto [minIt, maxIt] = std::minmax_element(values.begin(), values.end());
    return {
            .sumTime = sumTime,
            .minTime = *minIt,
            .maxTime = *maxIt,
            .sumSquaredTime = sumSquaredTime,
            .count = static_cast<int32_t>(values.size()),
    };
}

TEST(StatsdTelemetryTest, AtomKeyEquality) {
    EXPECT_EQ(kExampleKey, kExampleKey);
}

TEST(StatsdTelemetryTest, AtomKeyLessThan) {
    const auto key1 = kExampleKey;
    auto key2 = key1;
    key2.errorCode = ANEURALNETWORKS_DEAD_OBJECT;
    EXPECT_LT(key1, key2);
}

TEST(StatsdTelemetryTest, CombineAtomValues) {
    AtomValue value1 = {
            .count = 3,
            .compilationTimeMillis = accumulatedTimingsFrom({50, 100, 150}),
    };
    const AtomValue value2 = {
            .count = 1,
            .compilationTimeMillis = accumulatedTimingsFrom({75}),
    };
    const AtomValue valueResult = {
            .count = 4,
            .compilationTimeMillis = accumulatedTimingsFrom({50, 75, 100, 150}),
    };

    combineAtomValues(&value1, value2);
    EXPECT_EQ(value1, valueResult);
}

TEST(StatsdTelemetryTest, CombineAtomValueWithLeftIdentity) {
    AtomValue value1 = {};
    const AtomValue value2 = {
            .count = 1,
            .compilationTimeMillis = accumulatedTimingsFrom({75}),
    };
    const AtomValue valueResult = value2;

    combineAtomValues(&value1, value2);
    EXPECT_EQ(value1, valueResult);
}

TEST(StatsdTelemetryTest, CombineAtomValueWithRightIdentity) {
    AtomValue value1 = {
            .count = 3,
            .compilationTimeMillis = accumulatedTimingsFrom({50, 100, 150}),
    };
    const AtomValue value2 = {};
    const AtomValue valueResult = value1;

    combineAtomValues(&value1, value2);
    EXPECT_EQ(value1, valueResult);
}

TEST(StatsdTelemetryTest, AtomAggregatorStartEmpty) {
    AtomAggregator aggregator;
    EXPECT_TRUE(aggregator.empty());
}

TEST(StatsdTelemetryTest, AtomAggregatorNotEmptyAfterPush) {
    AtomAggregator aggregator;
    aggregator.push({kExampleKey, {}});
    EXPECT_FALSE(aggregator.empty());
}

TEST(StatsdTelemetryTest, AtomAggregatorEmptyAfterPop) {
    AtomAggregator aggregator;
    aggregator.push({kExampleKey, {}});
    const auto [k, v] = aggregator.pop();
    EXPECT_TRUE(aggregator.empty());
    EXPECT_EQ(k, kExampleKey);
}

TEST(StatsdTelemetryTest, AtomAggregatorTwoDifferentKeys) {
    const auto key1 = kExampleKey;
    auto key2 = key1;
    key2.executionMode = ExecutionMode::ASYNC;
    const auto value1 = AtomValue{.count = 2};
    const auto value2 = AtomValue{.count = 3};

    AtomAggregator aggregator;
    aggregator.push({key1, value1});
    aggregator.push({key2, value2});

    const auto [resultKey, resultValue] = aggregator.pop();

    EXPECT_EQ(resultKey, key1);
    EXPECT_EQ(resultValue, value1);
    EXPECT_FALSE(aggregator.empty());
}

TEST(StatsdTelemetryTest, AtomAggregatorTwoSameKeys) {
    const auto key1 = kExampleKey;
    const auto value1 = AtomValue{.count = 2};
    const auto value2 = AtomValue{.count = 3};

    AtomAggregator aggregator;
    aggregator.push({key1, value1});
    aggregator.push({key1, value2});

    const auto [resultKey, resultValue] = aggregator.pop();

    EXPECT_EQ(resultKey, key1);
    EXPECT_EQ(resultValue, AtomValue{.count = 5});
    EXPECT_TRUE(aggregator.empty());
}

TEST(StatsdTelemetryTest, AtomAggregatorPush) {
    const AtomKey key1 = kExampleKey;
    AtomKey key2 = key1;
    key2.executionMode = ExecutionMode::ASYNC;
    const auto value1 = AtomValue{.count = 2};
    const auto value2 = AtomValue{.count = 3};
    const auto value3 = AtomValue{.count = 6};

    AtomAggregator aggregator;
    aggregator.push({key1, value1});
    aggregator.push({key2, value2});
    aggregator.push({key1, value3});

    const auto [resultKey1, resultValue1] = aggregator.pop();
    const auto [resultKey2, resultValue2] = aggregator.pop();

    EXPECT_EQ(resultKey1, key1);
    EXPECT_EQ(resultKey2, key2);
    EXPECT_EQ(resultValue1, AtomValue{.count = 8});
    EXPECT_EQ(resultValue2, AtomValue{.count = 3});
    EXPECT_TRUE(aggregator.empty());
}

TEST(StatsdTelemetryTest, AsyncLoggerTeardownWhileWaitingForData) {
    constexpr auto fn = [](Atom&& /*atom*/) {};
    const auto start = Clock::now();
    { AsyncLogger logger(fn, kLongTime); }
    const auto elapsed = Clock::now() - start;
    EXPECT_LT(elapsed, kLongTime);
}

TEST(StatsdTelemetryTest, AsyncLoggerTeardownDuringSleep) {
    Signal loggingOccurred;
    auto fn = [&loggingOccurred](Atom&& /*atom*/) mutable { loggingOccurred.signal(); };

    const auto start = Clock::now();
    {
        AsyncLogger logger(fn, kLongTime);
        logger.write({});
        loggingOccurred.wait();
    }
    const auto elapsed = Clock::now() - start;

    EXPECT_LT(elapsed, kLongTime);
}

TEST(StatsdTelemetryTest, AsyncLoggerVerifyQuietPeriod) {
    std::atomic<uint32_t> count = 0;
    Signal loggingOccurred;
    const auto fn = [&count, &loggingOccurred](Atom&& /*atom*/) {
        ++count;
        loggingOccurred.signal();
    };

    {
        AsyncLogger logger(fn, kLongTime);
        logger.write({});
        loggingOccurred.wait();

        // At this point, logger is in the quiet period because it has already logged once. Send
        // many more atoms and ensure the logging function is not called a second time.
        for (int32_t error = ANEURALNETWORKS_NO_ERROR; error <= ANEURALNETWORKS_DEAD_OBJECT;
             ++error) {
            auto key = kExampleKey;
            key.errorCode = error;
            logger.write({key, AtomValue{.count = 1}});
        }
    }

    EXPECT_EQ(count, 1u);
}

TEST(StatsdTelemetryTest, AsyncLoggerVerifyAllDataSent) {
    const uint32_t targetCount = ANEURALNETWORKS_DEAD_OBJECT - ANEURALNETWORKS_NO_ERROR + 1;
    std::atomic<uint32_t> count = 0;
    Signal allDataSent;
    const auto fn = [&count, &allDataSent](Atom&& /*atom*/) {
        const uint32_t currentCount = ++count;
        if (currentCount == targetCount) {
            allDataSent.signal();
        }
    };

    {
        AsyncLogger logger(fn, std::chrono::nanoseconds(0));
        for (int32_t error = ANEURALNETWORKS_NO_ERROR; error <= ANEURALNETWORKS_DEAD_OBJECT;
             ++error) {
            auto key = kExampleKey;
            key.errorCode = error;
            logger.write({key, AtomValue{.count = 1}});
        }
        allDataSent.wait();
    }

    EXPECT_EQ(count, targetCount);
}

TEST(StatsdTelemetryTest, createAtomFromCompilationInfoWhenNoError) {
    const DiagnosticCompilationInfo info{
            .modelArchHash = kExampleModelArchHash.data(),
            .deviceId = kExampleDeviceId,
            .errorCode = ANEURALNETWORKS_NO_ERROR,
            .inputDataClass = DataClass::FLOAT32,
            .outputDataClass = DataClass::QUANT,
            .compilationTimeNanos = 10'000'000u,
            .fallbackToCpuFromError = false,
            .introspectionEnabled = true,
            .hasControlFlow = false,
            .hasDynamicTemporaries = true,
    };

    const auto [key, value] = createAtomFrom(&info);

    EXPECT_FALSE(key.isExecution);
    EXPECT_EQ(key.modelArchHash, kExampleModelArchHash);
    EXPECT_EQ(key.deviceId, kExampleDeviceId);
    EXPECT_EQ(key.executionMode, ExecutionMode::SYNC);
    EXPECT_EQ(key.errorCode, info.errorCode);
    EXPECT_EQ(key.inputDataClass, info.inputDataClass);
    EXPECT_EQ(key.outputDataClass, info.outputDataClass);
    EXPECT_EQ(key.fallbackToCpuFromError, info.fallbackToCpuFromError);
    EXPECT_EQ(key.introspectionEnabled, info.introspectionEnabled);
    EXPECT_EQ(key.cacheEnabled, info.cacheEnabled);
    EXPECT_EQ(key.hasControlFlow, info.hasControlFlow);
    EXPECT_EQ(key.hasDynamicTemporaries, info.hasDynamicTemporaries);

    EXPECT_EQ(value.count, 1);

    const auto compilationTimeMillis =
            accumulatedTimingsFrom({static_cast<int64_t>(info.compilationTimeNanos / 1'000'000u)});
    EXPECT_EQ(value.compilationTimeMillis, compilationTimeMillis);

    EXPECT_EQ(value.durationRuntimeMicros, kNoAggregateTiming);
    EXPECT_EQ(value.durationDriverMicros, kNoAggregateTiming);
    EXPECT_EQ(value.durationHardwareMicros, kNoAggregateTiming);
}

TEST(StatsdTelemetryTest, createAtomFromCompilationInfoWhenError) {
    const DiagnosticCompilationInfo info{
            .modelArchHash = kExampleModelArchHash.data(),
            .deviceId = kExampleDeviceId,
            .errorCode = ANEURALNETWORKS_OP_FAILED,
            .inputDataClass = DataClass::FLOAT32,
            .outputDataClass = DataClass::QUANT,
            .compilationTimeNanos = kNoTiming,
            .fallbackToCpuFromError = true,
            .introspectionEnabled = false,
            .hasControlFlow = true,
            .hasDynamicTemporaries = false,
    };

    const auto [key, value] = createAtomFrom(&info);

    EXPECT_FALSE(key.isExecution);
    EXPECT_EQ(key.modelArchHash, kExampleModelArchHash);
    EXPECT_EQ(key.deviceId, kExampleDeviceId);
    EXPECT_EQ(key.executionMode, ExecutionMode::SYNC);
    EXPECT_EQ(key.errorCode, info.errorCode);
    EXPECT_EQ(key.inputDataClass, info.inputDataClass);
    EXPECT_EQ(key.outputDataClass, info.outputDataClass);
    EXPECT_EQ(key.fallbackToCpuFromError, info.fallbackToCpuFromError);
    EXPECT_EQ(key.introspectionEnabled, info.introspectionEnabled);
    EXPECT_EQ(key.cacheEnabled, info.cacheEnabled);
    EXPECT_EQ(key.hasControlFlow, info.hasControlFlow);
    EXPECT_EQ(key.hasDynamicTemporaries, info.hasDynamicTemporaries);

    EXPECT_EQ(value.count, 1);

    EXPECT_EQ(value.compilationTimeMillis, kNoAggregateTiming);
    EXPECT_EQ(value.durationRuntimeMicros, kNoAggregateTiming);
    EXPECT_EQ(value.durationDriverMicros, kNoAggregateTiming);
    EXPECT_EQ(value.durationHardwareMicros, kNoAggregateTiming);
}

TEST(StatsdTelemetryTest, createAtomFromExecutionInfoWhenNoError) {
    const DiagnosticExecutionInfo info{
            .modelArchHash = kExampleModelArchHash.data(),
            .deviceId = kExampleDeviceId,
            .executionMode = ExecutionMode::SYNC,
            .inputDataClass = DataClass::FLOAT32,
            .outputDataClass = DataClass::QUANT,
            .errorCode = ANEURALNETWORKS_NO_ERROR,
            .durationRuntimeNanos = 350'000u,
            .durationDriverNanos = 350'000u,
            .durationHardwareNanos = 350'000u,
            .introspectionEnabled = false,
            .cacheEnabled = true,
            .hasControlFlow = false,
            .hasDynamicTemporaries = true,
    };

    const auto [key, value] = createAtomFrom(&info);

    EXPECT_TRUE(key.isExecution);
    EXPECT_EQ(key.modelArchHash, kExampleModelArchHash);
    EXPECT_EQ(key.deviceId, kExampleDeviceId);
    EXPECT_EQ(key.executionMode, info.executionMode);
    EXPECT_EQ(key.errorCode, info.errorCode);
    EXPECT_EQ(key.inputDataClass, info.inputDataClass);
    EXPECT_EQ(key.outputDataClass, info.outputDataClass);
    EXPECT_FALSE(key.fallbackToCpuFromError);
    EXPECT_EQ(key.introspectionEnabled, info.introspectionEnabled);
    EXPECT_EQ(key.cacheEnabled, info.cacheEnabled);
    EXPECT_EQ(key.hasControlFlow, info.hasControlFlow);
    EXPECT_EQ(key.hasDynamicTemporaries, info.hasDynamicTemporaries);

    EXPECT_EQ(value.count, 1);

    EXPECT_EQ(value.compilationTimeMillis, kNoAggregateTiming);

    const auto durationRuntimeMicros =
            accumulatedTimingsFrom({static_cast<int64_t>(info.durationRuntimeNanos / 1'000u)});
    const auto durationDriverMicros =
            accumulatedTimingsFrom({static_cast<int64_t>(info.durationDriverNanos / 1'000u)});
    const auto durationHardwareMicros =
            accumulatedTimingsFrom({static_cast<int64_t>(info.durationHardwareNanos / 1'000u)});

    EXPECT_EQ(value.durationRuntimeMicros, durationRuntimeMicros);
    EXPECT_EQ(value.durationDriverMicros, durationDriverMicros);
    EXPECT_EQ(value.durationHardwareMicros, durationHardwareMicros);
}

TEST(StatsdTelemetryTest, createAtomFromExecutionInfoWhenError) {
    const DiagnosticExecutionInfo info{
            .modelArchHash = kExampleModelArchHash.data(),
            .deviceId = kExampleDeviceId,
            .executionMode = ExecutionMode::SYNC,
            .inputDataClass = DataClass::FLOAT32,
            .outputDataClass = DataClass::QUANT,
            .errorCode = ANEURALNETWORKS_OP_FAILED,
            .durationRuntimeNanos = kNoTiming,
            .durationDriverNanos = kNoTiming,
            .durationHardwareNanos = kNoTiming,
            .introspectionEnabled = true,
            .cacheEnabled = false,
            .hasControlFlow = true,
            .hasDynamicTemporaries = false,
    };

    const auto [key, value] = createAtomFrom(&info);

    EXPECT_TRUE(key.isExecution);
    EXPECT_EQ(key.modelArchHash, kExampleModelArchHash);
    EXPECT_EQ(key.deviceId, kExampleDeviceId);
    EXPECT_EQ(key.executionMode, info.executionMode);
    EXPECT_EQ(key.errorCode, info.errorCode);
    EXPECT_EQ(key.inputDataClass, info.inputDataClass);
    EXPECT_EQ(key.outputDataClass, info.outputDataClass);
    EXPECT_FALSE(key.fallbackToCpuFromError);
    EXPECT_EQ(key.introspectionEnabled, info.introspectionEnabled);
    EXPECT_EQ(key.cacheEnabled, info.cacheEnabled);
    EXPECT_EQ(key.hasControlFlow, info.hasControlFlow);
    EXPECT_EQ(key.hasDynamicTemporaries, info.hasDynamicTemporaries);

    EXPECT_EQ(value.count, 1);

    EXPECT_EQ(value.compilationTimeMillis, kNoAggregateTiming);
    EXPECT_EQ(value.durationRuntimeMicros, kNoAggregateTiming);
    EXPECT_EQ(value.durationDriverMicros, kNoAggregateTiming);
    EXPECT_EQ(value.durationHardwareMicros, kNoAggregateTiming);
}

}  // namespace android::nn::telemetry
