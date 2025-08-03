/*
 * Copyright (C) 2024 The Android Open Source Project
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

using namespace std;
namespace android {
namespace os {
namespace statsd {

static void BM_OnLogEvent(benchmark::State& state) {
    StatsdConfig config;
    auto wakelockAcquireMatcher = CreateAcquireWakelockAtomMatcher();
    *config.add_atom_matcher() = wakelockAcquireMatcher;

    *config.add_event_metric() =
            createEventMetric("Event", wakelockAcquireMatcher.id(), /* condition */ nullopt);

    for (int atomId = 1000; atomId < 2000; atomId++) {
        auto matcher = CreateSimpleAtomMatcher("name" + to_string(atomId), atomId);
        *config.add_atom_matcher() = CreateSimpleAtomMatcher("name" + to_string(atomId), atomId);
        *config.add_event_metric() = createEventMetric("Event" + to_string(atomId), matcher.id(),
                                                       /* condition */ nullopt);
    }

    ConfigKey cfgKey;
    std::vector<std::unique_ptr<LogEvent>> events;
    vector<int> attributionUids = {111};
    vector<string> attributionTags = {"App1"};
    for (int i = 1; i <= 10; i++) {
        events.push_back(CreateAcquireWakelockEvent(2 + i, attributionUids, attributionTags,
                                                    "wl" + to_string(i)));
    }

    sp<StatsLogProcessor> processor = CreateStatsLogProcessor(1, 1, config, cfgKey);

    for (auto _ : state) {
        for (const auto& event : events) {
            processor->OnLogEvent(event.get());
        }
    }
}
BENCHMARK(BM_OnLogEvent);

}  // namespace statsd
}  // namespace os
}  // namespace android
