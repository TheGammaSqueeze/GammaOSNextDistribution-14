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

#include <memory>
#include <regex>
#include <string>

#include "benchmark/benchmark.h"
// #include "re2/re2.h"
#include "utils/Regex.h"

using android::os::statsd::Regex;
using namespace std;

static void removeTrailingCharacters(string& str, const string& characters) {
    str.erase(str.find_last_not_of(characters) + 1, string::npos);
}

static void removeLeadingCharacters(string& str, const char charToRemove) {
    str.erase(0, min(str.find_first_not_of(charToRemove), str.size() - 1));
}

static void removeTrailingNumbers(string& str) {
    int i = str.length() - 1;
    while (i >= 0 && isdigit(str[i])) {
        str.erase(i, 1);
        i--;
    }
}

static void BM_RemoveTrailingCharacters(benchmark::State& state) {
    const string prefix(state.range(0), 'a' + rand() % 26);
    const string suffix(state.range(1), '0' + rand() % 10);
    const string input = prefix + suffix;
    const string characters("0123456789");
    for (auto _ : state) {
        string str = input;
        removeTrailingCharacters(str, characters);
        benchmark::DoNotOptimize(str);
    }
}
BENCHMARK(BM_RemoveTrailingCharacters)->RangeMultiplier(2)->RangePair(0, 20, 0, 20);

static void BM_RemoveTrailingNumbers(benchmark::State& state) {
    const string prefix(state.range(0), 'a' + rand() % 26);
    const string suffix(state.range(1), '0' + rand() % 10);
    const string input = prefix + suffix;
    for (auto _ : state) {
        string str = input;
        removeTrailingNumbers(str);
        benchmark::DoNotOptimize(str);
    }
}
BENCHMARK(BM_RemoveTrailingNumbers)->RangeMultiplier(2)->RangePair(0, 20, 0, 20);

static void BM_RemoveTrailingNumbersCppRegex(benchmark::State& state) {
    static const regex re(R"([\d]+$)");
    const string prefix(state.range(0), 'a' + rand() % 26);
    const string suffix(state.range(1), '0' + rand() % 10);
    const string input = prefix + suffix;
    for (auto _ : state) {
        string str = input;
        benchmark::DoNotOptimize(regex_replace(str, re, ""));
    }
}
BENCHMARK(BM_RemoveTrailingNumbersCppRegex)->RangeMultiplier(2)->RangePair(0, 20, 0, 20);

static void BM_RemoveTrailingNumbersCRegex(benchmark::State& state) {
    unique_ptr<Regex> re = Regex::create(R"([0-9]+$)");
    const string prefix(state.range(0), 'a' + rand() % 26);
    const string suffix(state.range(1), '0' + rand() % 10);
    const string input = prefix + suffix;
    for (auto _ : state) {
        string str = input;
        benchmark::DoNotOptimize(re->replace(str, ""));
    }
}
BENCHMARK(BM_RemoveTrailingNumbersCRegex)->RangeMultiplier(2)->RangePair(0, 20, 0, 20);

// To run RE2 benchmark locally, libregex_re2 under external/regex_re2 needs to be made visible to
// statsd_benchmark.
// static void BM_RemoveTrailingNumbersRe2(benchmark::State& state) {
//     using namespace re2;
//     static const RE2 re2{R"([\d]+$)"};
//     const string prefix(state.range(0), 'a' + rand() % 26);
//     const string suffix(state.range(1), '0' + rand() % 10);
//     const string input = prefix + suffix;
//     for (auto _ : state) {
//         string str = input;
//         RE2::Replace(&str, re2, "");
//         benchmark::DoNotOptimize(str);
//     }
// }
// BENCHMARK(BM_RemoveTrailingNumbersRe2)->RangeMultiplier(2)->RangePair(0, 20, 0, 20);
