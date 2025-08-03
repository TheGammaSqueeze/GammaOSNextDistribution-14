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
#include <random>
#include <vector>

#include "benchmark/benchmark.h"
#include "socket/LogEventFilter.h"

namespace android {
namespace os {
namespace statsd {

namespace {

constexpr int kAtomIdsCount = 500;         //  Filter size setup
constexpr int kAtomIdsSampleCount = 3000;  //  Queries number

std::vector<int> generateSampleAtomIdsList() {
    std::vector<int> atomIds(kAtomIdsSampleCount);

    std::default_random_engine generator;

    // Get atoms ids which are not in the filter to test behavior when set is searched for an
    // an absent key
    // Expected atoms ids are in a range 1..3000, random & evenly distributes
    std::uniform_int_distribution<int> distribution(1, kAtomIdsSampleCount);

    for (int i = 0; i < kAtomIdsSampleCount; ++i) {
        atomIds[i] = distribution(generator);
    }

    return atomIds;
}

template <typename T>
T generateAtomIds() {
    T atomIds;

    std::default_random_engine generator;
    std::uniform_int_distribution<int> distribution(1, kAtomIdsCount);

    for (int i = 0; i < kAtomIdsCount; ++i) {
        atomIds.insert(distribution(generator));
    }

    return atomIds;
}

// Used to setup filter
const std::set<int> kAtomIdsSet = generateAtomIds<std::set<int>>();
const std::unordered_set<int> kAtomIdsUnorderedSet = generateAtomIds<std::unordered_set<int>>();

const std::set<int> kAtomIdsSet2 = generateAtomIds<std::set<int>>();
const std::unordered_set<int> kAtomIdsUnorderedSet2 = generateAtomIds<std::unordered_set<int>>();

const std::set<int> kAtomIdsSet3 = generateAtomIds<std::set<int>>();
const std::unordered_set<int> kAtomIdsUnorderedSet3 = generateAtomIds<std::unordered_set<int>>();

const std::set<int> kAtomIdsSet4 = generateAtomIds<std::set<int>>();
const std::unordered_set<int> kAtomIdsUnorderedSet4 = generateAtomIds<std::unordered_set<int>>();

// Used to perform sample quieries
const std::vector<int> kSampleIdsList = generateSampleAtomIdsList();

}  // namespace

static void BM_LogEventFilterUnorderedSet(benchmark::State& state) {
    while (state.KeepRunning()) {
        LogEventFilter eventFilter;
        // populate
        eventFilter.setAtomIds(kAtomIdsUnorderedSet, nullptr);
        // many fetches
        for (const auto& atomId : kSampleIdsList) {
            benchmark::DoNotOptimize(eventFilter.isAtomInUse(atomId));
        }
    }
}
BENCHMARK(BM_LogEventFilterUnorderedSet);

static void BM_LogEventFilterUnorderedSet2Consumers(benchmark::State& state) {
    while (state.KeepRunning()) {
        LogEventFilter eventFilter;
        // populate
        eventFilter.setAtomIds(kAtomIdsUnorderedSet, &kAtomIdsUnorderedSet);
        eventFilter.setAtomIds(kAtomIdsUnorderedSet2, &kAtomIdsUnorderedSet2);
        eventFilter.setAtomIds(kAtomIdsUnorderedSet3, &kAtomIdsUnorderedSet);
        eventFilter.setAtomIds(kAtomIdsUnorderedSet4, &kAtomIdsUnorderedSet2);
        // many fetches
        for (const auto& atomId : kSampleIdsList) {
            benchmark::DoNotOptimize(eventFilter.isAtomInUse(atomId));
        }
    }
}
BENCHMARK(BM_LogEventFilterUnorderedSet2Consumers);

static void BM_LogEventFilterSet(benchmark::State& state) {
    while (state.KeepRunning()) {
        LogEventFilterGeneric<std::set<int>> eventFilter;
        // populate
        eventFilter.setAtomIds(kAtomIdsSet, nullptr);
        // many fetches
        for (const auto& atomId : kSampleIdsList) {
            benchmark::DoNotOptimize(eventFilter.isAtomInUse(atomId));
        }
    }
}
BENCHMARK(BM_LogEventFilterSet);

static void BM_LogEventFilterSet2Consumers(benchmark::State& state) {
    while (state.KeepRunning()) {
        LogEventFilterGeneric<std::set<int>> eventFilter;
        // populate
        eventFilter.setAtomIds(kAtomIdsSet, &kAtomIdsSet);
        eventFilter.setAtomIds(kAtomIdsSet2, &kAtomIdsSet2);
        eventFilter.setAtomIds(kAtomIdsSet3, &kAtomIdsSet);
        eventFilter.setAtomIds(kAtomIdsSet4, &kAtomIdsSet2);
        // many fetches
        for (const auto& atomId : kSampleIdsList) {
            benchmark::DoNotOptimize(eventFilter.isAtomInUse(atomId));
        }
    }
}
BENCHMARK(BM_LogEventFilterSet2Consumers);

}  //  namespace statsd
}  //  namespace os
}  //  namespace android
