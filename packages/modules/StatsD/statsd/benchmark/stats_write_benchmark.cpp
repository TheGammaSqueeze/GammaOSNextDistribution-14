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
#include <statslog_statsdtest.h>

#include "benchmark/benchmark.h"

namespace android {
namespace os {
namespace statsd {

static void BM_StatsEventObtain(benchmark::State& state) {
    while (state.KeepRunning()) {
        AStatsEvent* event = AStatsEvent_obtain();
        benchmark::DoNotOptimize(event);
        AStatsEvent_release(event);
    }
}
BENCHMARK(BM_StatsEventObtain);

static void BM_StatsWrite(benchmark::State& state) {
    int32_t parent_uid = 0;
    int32_t isolated_uid = 100;
    int32_t event = 1;
    while (state.KeepRunning()) {
        util::stats_write(util::ISOLATED_UID_CHANGED, parent_uid, isolated_uid, event++);
    }
}
BENCHMARK(BM_StatsWrite);

static void BM_StatsWriteViaQueue(benchmark::State& state) {
    // writes dedicated atom which known to be put into the queue for the test purpose
    int32_t uid = 0;
    int32_t label = 100;
    int32_t a_state = 1;
    while (state.KeepRunning()) {
        benchmark::DoNotOptimize(
                util::stats_write(util::APP_BREADCRUMB_REPORTED, uid, label, a_state++));
    }
}
BENCHMARK(BM_StatsWriteViaQueue);

}  //  namespace statsd
}  //  namespace os
}  //  namespace android
