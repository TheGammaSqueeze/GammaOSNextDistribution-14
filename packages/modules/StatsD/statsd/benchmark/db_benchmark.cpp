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

#include "benchmark/benchmark.h"
#include "tests/statsd_test_util.h"
#include "utils/DbUtils.h"

using namespace std;

namespace android {
namespace os {
namespace statsd {
namespace dbutils {

static void BM_insertAtomsIntoDbTablesNewConnection(benchmark::State& state) {
    ConfigKey key = ConfigKey(111, 222);
    int64_t metricId = 0;
    int64_t bucketStartTimeNs = 10000000000;

    unique_ptr<LogEvent> event =
            CreateScreenStateChangedEvent(bucketStartTimeNs, android::view::DISPLAY_STATE_OFF);
    vector<LogEvent> logEvents;
    for (int j = 0; j < state.range(1); ++j) {
        logEvents.push_back(*event.get());
    }
    string err;
    for (auto s : state) {
        for (int metricId = 0; metricId < state.range(0); ++metricId) {
            state.PauseTiming();
            deleteDb(key);
            createTableIfNeeded(key, metricId, *event.get());
            state.ResumeTiming();
            insert(key, metricId, logEvents, err);
        }
    }
    deleteDb(key);
}

BENCHMARK(BM_insertAtomsIntoDbTablesNewConnection)
        ->Args({1, 10})
        ->Args({1, 50})
        ->Args({1, 100})
        ->Args({1, 500})
        ->Args({10, 10})
        ->Args({10, 20});

static void BM_insertAtomsIntoDbTablesReuseConnection(benchmark::State& state) {
    ConfigKey key = ConfigKey(111, 222);
    int64_t metricId = 0;
    int64_t bucketStartTimeNs = 10000000000;

    unique_ptr<LogEvent> event =
            CreateScreenStateChangedEvent(bucketStartTimeNs, android::view::DISPLAY_STATE_OFF);
    vector<LogEvent> logEvents;
    for (int j = 0; j < state.range(1); ++j) {
        logEvents.push_back(*event.get());
    }
    sqlite3* dbHandle = getDb(key);
    string err;
    for (auto s : state) {
        for (int metricId = 0; metricId < state.range(0); ++metricId) {
            state.PauseTiming();
            deleteTable(key, metricId);
            createTableIfNeeded(key, metricId, *event.get());
            state.ResumeTiming();
            insert(key, metricId, logEvents, err);
        }
    }
    closeDb(dbHandle);
    deleteDb(key);
}

BENCHMARK(BM_insertAtomsIntoDbTablesReuseConnection)
        ->Args({1, 10})
        ->Args({1, 50})
        ->Args({1, 100})
        ->Args({1, 500})
        ->Args({10, 10})
        ->Args({10, 20});

static void BM_createDbTables(benchmark::State& state) {
    ConfigKey key = ConfigKey(111, 222);
    int64_t metricId = 0;
    int64_t bucketStartTimeNs = 10000000000;

    unique_ptr<LogEvent> event =
            CreateScreenStateChangedEvent(bucketStartTimeNs, android::view::DISPLAY_STATE_OFF);
    vector<LogEvent> logEvents{*event.get()};
    string err;
    for (auto s : state) {
        state.PauseTiming();
        deleteTable(key, metricId);
        state.ResumeTiming();
        createTableIfNeeded(key, metricId, *event.get());
        insert(key, metricId, logEvents, err);
    }
    deleteDb(key);
}

BENCHMARK(BM_createDbTables);
}  // namespace dbutils
}  // namespace statsd
}  // namespace os
}  // namespace android
