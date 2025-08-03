/*
 * Copyright (C) 2018 The Android Open Source Project
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
#include <vector>
#include "benchmark/benchmark.h"
#include "logd/LogEvent.h"
#include "stats_event.h"

namespace android {
namespace os {
namespace statsd {

static void writeEventTestFields(AStatsEvent& event) {
    AStatsEvent_writeInt64(&event, 3L);
    AStatsEvent_writeInt32(&event, 2);
    AStatsEvent_writeFloat(&event, 2.0);
    AStatsEvent_writeString(&event, "DemoStringValue");
}

static size_t createStatsEvent(uint8_t* msg, int numElements = 1) {
    AStatsEvent* event = AStatsEvent_obtain();
    AStatsEvent_setAtomId(event, 100);
    for (int i = 0; i < numElements; i++) {
        writeEventTestFields(*event);
    }
    AStatsEvent_build(event);

    size_t size;
    uint8_t* buf = AStatsEvent_getBuffer(event, &size);
    memcpy(msg, buf, size);
    return size;
}

static size_t createStatsEventMedium(uint8_t* msg) {
    return createStatsEvent(msg, 5);
}

static size_t createStatsEventLarge(uint8_t* msg) {
    return createStatsEvent(msg, 10);
}

static size_t createStatsEventExtraLarge(uint8_t* msg) {
    return createStatsEvent(msg, 20);
}

static void BM_LogEventCreation(benchmark::State& state) {
    uint8_t msg[LOGGER_ENTRY_MAX_PAYLOAD];
    const size_t size = createStatsEvent(msg);
    while (state.KeepRunning()) {
        LogEvent event(/*uid=*/ 1000, /*pid=*/ 1001);
        benchmark::DoNotOptimize(event.parseBuffer(msg, size));
    }
}
BENCHMARK(BM_LogEventCreation);

static void BM_LogEventCreationWithPrefetch(benchmark::State& state) {
    uint8_t msg[LOGGER_ENTRY_MAX_PAYLOAD];
    const size_t size = createStatsEvent(msg);
    while (state.KeepRunning()) {
        LogEvent event(/*uid=*/1000, /*pid=*/1001);

        // explicitly parse header first
        const LogEvent::BodyBufferInfo header = event.parseHeader(msg, size);

        // explicitly parse body using the header
        benchmark::DoNotOptimize(event.parseBody(header));
    }
}
BENCHMARK(BM_LogEventCreationWithPrefetch);

static void BM_LogEventCreationWithPrefetchOnly(benchmark::State& state) {
    uint8_t msg[LOGGER_ENTRY_MAX_PAYLOAD];
    const size_t size = createStatsEvent(msg);
    while (state.KeepRunning()) {
        LogEvent event(/*uid=*/1000, /*pid=*/1001);

        // explicitly parse header only and skip the body
        benchmark::DoNotOptimize(event.parseHeader(msg, size));
    }
}
BENCHMARK(BM_LogEventCreationWithPrefetchOnly);

static void BM_LogEventCreationMedium(benchmark::State& state) {
    uint8_t msg[LOGGER_ENTRY_MAX_PAYLOAD];
    const size_t size = createStatsEventMedium(msg);
    while (state.KeepRunning()) {
        LogEvent event(/*uid=*/1000, /*pid=*/1001);

        benchmark::DoNotOptimize(event.parseBuffer(msg, size));
    }
}
BENCHMARK(BM_LogEventCreationMedium);

static void BM_LogEventCreationMediumWithPrefetch(benchmark::State& state) {
    uint8_t msg[LOGGER_ENTRY_MAX_PAYLOAD];
    const size_t size = createStatsEventMedium(msg);
    while (state.KeepRunning()) {
        LogEvent event(/*uid=*/1000, /*pid=*/1001);

        // explicitly parse header first
        const LogEvent::BodyBufferInfo header = event.parseHeader(msg, size);

        // explicitly parse body using the header
        benchmark::DoNotOptimize(event.parseBody(header));
    }
}
BENCHMARK(BM_LogEventCreationMediumWithPrefetch);

static void BM_LogEventCreationMediumWithPrefetchOnly(benchmark::State& state) {
    uint8_t msg[LOGGER_ENTRY_MAX_PAYLOAD];
    const size_t size = createStatsEventMedium(msg);
    while (state.KeepRunning()) {
        LogEvent event(/*uid=*/1000, /*pid=*/1001);

        // explicitly parse header only and skip the body
        benchmark::DoNotOptimize(event.parseHeader(msg, size));
    }
}
BENCHMARK(BM_LogEventCreationMediumWithPrefetchOnly);

static void BM_LogEventCreationLarge(benchmark::State& state) {
    uint8_t msg[LOGGER_ENTRY_MAX_PAYLOAD];
    const size_t size = createStatsEventLarge(msg);
    while (state.KeepRunning()) {
        LogEvent event(/*uid=*/1000, /*pid=*/1001);

        benchmark::DoNotOptimize(event.parseBuffer(msg, size));
    }
}
BENCHMARK(BM_LogEventCreationLarge);

static void BM_LogEventCreationLargeWithPrefetch(benchmark::State& state) {
    uint8_t msg[LOGGER_ENTRY_MAX_PAYLOAD];
    const size_t size = createStatsEventLarge(msg);
    while (state.KeepRunning()) {
        LogEvent event(/*uid=*/1000, /*pid=*/1001);

        // explicitly parse header first
        const LogEvent::BodyBufferInfo header = event.parseHeader(msg, size);

        // explicitly parse body using the header
        benchmark::DoNotOptimize(event.parseBody(header));
    }
}
BENCHMARK(BM_LogEventCreationLargeWithPrefetch);

static void BM_LogEventCreationLargeWithPrefetchOnly(benchmark::State& state) {
    uint8_t msg[LOGGER_ENTRY_MAX_PAYLOAD];
    const size_t size = createStatsEventLarge(msg);
    while (state.KeepRunning()) {
        LogEvent event(/*uid=*/1000, /*pid=*/1001);

        // explicitly parse header only and skip the body
        benchmark::DoNotOptimize(event.parseHeader(msg, size));
    }
}
BENCHMARK(BM_LogEventCreationLargeWithPrefetchOnly);

static void BM_LogEventCreationExtraLarge(benchmark::State& state) {
    uint8_t msg[LOGGER_ENTRY_MAX_PAYLOAD];
    const size_t size = createStatsEventExtraLarge(msg);
    while (state.KeepRunning()) {
        LogEvent event(/*uid=*/1000, /*pid=*/1001);

        benchmark::DoNotOptimize(event.parseBuffer(msg, size));
    }
}
BENCHMARK(BM_LogEventCreationExtraLarge);

static void BM_LogEventCreationExtraLargeWithPrefetch(benchmark::State& state) {
    uint8_t msg[LOGGER_ENTRY_MAX_PAYLOAD];
    const size_t size = createStatsEventExtraLarge(msg);
    while (state.KeepRunning()) {
        LogEvent event(/*uid=*/1000, /*pid=*/1001);

        // explicitly parse header first
        const LogEvent::BodyBufferInfo header = event.parseHeader(msg, size);

        // explicitly parse body using the header
        benchmark::DoNotOptimize(event.parseBody(header));
    }
}
BENCHMARK(BM_LogEventCreationExtraLargeWithPrefetch);

static void BM_LogEventCreationExtraLargeWithPrefetchOnly(benchmark::State& state) {
    uint8_t msg[LOGGER_ENTRY_MAX_PAYLOAD];
    const size_t size = createStatsEventExtraLarge(msg);
    while (state.KeepRunning()) {
        LogEvent event(/*uid=*/1000, /*pid=*/1001);

        // explicitly parse header only and skip the body
        benchmark::DoNotOptimize(event.parseHeader(msg, size));
    }
}
BENCHMARK(BM_LogEventCreationExtraLargeWithPrefetchOnly);

}  //  namespace statsd
}  //  namespace os
}  //  namespace android
