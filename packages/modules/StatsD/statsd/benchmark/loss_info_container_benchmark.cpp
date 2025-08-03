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
#include <cstdlib>
#include <ctime>
#include <map>
#include <unordered_map>
#include <unordered_set>
#include <vector>

#include "benchmark/benchmark.h"

namespace android {
namespace os {
namespace statsd {

namespace {

std::vector<int> generateRandomIds(int count, int maxRange) {
    std::srand(std::time(nullptr));

    std::unordered_set<int> unique_values;

    while (unique_values.size() <= count) {
        unique_values.insert(std::rand() % maxRange);
    }

    std::vector<int> result(unique_values.begin(), unique_values.end());

    return result;
}

const int kMaxAtomId = 100000;
const int kMaxErrorCode = 20;

std::vector<std::pair<std::vector<int>, std::vector<int>>> generateIdsAndErrorsVectors(
        const std::vector<int>& idsCounts, const std::vector<int>& errorsCounts) {
    std::vector<std::pair<std::vector<int>, std::vector<int>>> result;
    for (const int idCount : idsCounts) {
        for (const int errorCount : errorsCounts) {
            auto ids = generateRandomIds(idCount, kMaxAtomId);
            auto errors = generateRandomIds(errorCount, kMaxErrorCode);
            result.push_back(std::make_pair(ids, errors));
        }
    }
    return result;
}

const std::vector<std::pair<std::vector<int>, std::vector<int>>> kRandomIdsAndErrorsPairs =
        generateIdsAndErrorsVectors({1, 5, 10, 50}, {1, 2, 5, 10});

struct TestVector {
    std::vector<int> errors;
    std::vector<int> tags;
};

std::vector<TestVector> generateTestVector(
        int count,
        const std::vector<std::pair<std::vector<int>, std::vector<int>>>& idsAndErrorsPairs) {
    std::srand(std::time(nullptr));

    std::vector<TestVector> result;

    for (const auto& idsAndErrors : idsAndErrorsPairs) {
        TestVector testVector;

        testVector.errors.reserve(count);
        testVector.tags.reserve(count);

        for (int i = 0; i < count; i++) {
            const int randomAtomIdFromReferenceList =
                    idsAndErrors.first[std::rand() % idsAndErrors.first.size()];
            const int randomErrorFromReferenceList =
                    idsAndErrors.second[std::rand() % idsAndErrors.second.size()];

            testVector.errors.push_back(randomErrorFromReferenceList);
            testVector.tags.push_back(randomAtomIdFromReferenceList);
        }
        result.push_back(testVector);
    }

    return result;
}

constexpr int kTestVectorSize = 4096;
constexpr int kMaxAtomTagsCount = 100;

const std::vector<TestVector> kTestVectors =
        generateTestVector(kTestVectorSize, kRandomIdsAndErrorsPairs);

struct LossInfoVector {
    // using vectors is more memory efficient
    // using vectors fits well with the dump API implementation - no need to transform data
    // before writing into AStatsEvent since it is aligned with repeated int32 fields
    std::vector<int> errors;
    std::vector<int> tags;
    std::vector<int> counts;

    bool noteLossInfo(int error, int tag) {
        // linear search is Ok here since we do not expect to see many tags, usually 1-5 per uid
        // exception is system server where we see 10s atoms
        size_t locatedTagIndex = 0;
        for (locatedTagIndex = 0; locatedTagIndex < errors.size(); ++locatedTagIndex) {
            // is there already logged an atom with tag == atomId
            if (errors[locatedTagIndex] == error && tags[locatedTagIndex] == tag) {
                ++counts[locatedTagIndex];
                return true;
            }
        }

        // if pair [error, atomId] is not found and guardrail is not reached yet store loss
        // counter
        if (locatedTagIndex == errors.size() && tags.size() < kMaxAtomTagsCount) {
            errors.push_back(error);
            tags.push_back(tag);
            counts.push_back(1);
        } else {
            return false;
        }
        return true;
    }
};

using LossInfoKey = std::pair<int, int>;  // [error, tag]

template <typename T>
struct LossInfoMap {
    // using maps is more CPU efficient however will require some postprocessing before
    // writing into the socket
    T countsPerErrorTag;

    bool noteLossInfo(int error, int tag) {
        LossInfoKey key = std::make_pair(error, tag);
        auto counterIt = countsPerErrorTag.find(key);

        if (counterIt != countsPerErrorTag.end()) {
            ++counterIt->second;
        } else if (countsPerErrorTag.size() < kMaxAtomTagsCount) {
            countsPerErrorTag[key] = 1;
        } else {
            return false;
        }

        return true;
    }
};

}  // namespace

struct hash_pair final {
    template <class TFirst, class TSecond>
    size_t operator()(const std::pair<TFirst, TSecond>& p) const noexcept {
        uintmax_t hash = std::hash<TFirst>{}(p.first);
        hash <<= sizeof(uintmax_t) * 4;
        hash ^= std::hash<TSecond>{}(p.second);
        return std::hash<uintmax_t>{}(hash);
    }
};

static void BM_LossInfoCollectionAndDumpViaVector(benchmark::State& state) {
    const TestVector& testVector = kTestVectors[state.range(0)];
    LossInfoVector lossInfo;

    while (state.KeepRunning()) {
        int res = 0;
        for (int i = 0; i < kTestVectorSize; i++) {
            res += lossInfo.noteLossInfo(testVector.errors[i], testVector.tags[i]);
        }
        benchmark::DoNotOptimize(res);
    }
}
BENCHMARK(BM_LossInfoCollectionAndDumpViaVector)
        ->Args({0})
        ->Args({1})
        ->Args({2})
        ->Args({3})
        ->Args({4})
        ->Args({5})
        ->Args({6})
        ->Args({7})
        ->Args({8})
        ->Args({9})
        ->Args({10})
        ->Args({11})
        ->Args({12})
        ->Args({13})
        ->Args({14})
        ->Args({15});

static void BM_LossInfoCollectionAndDumpViaUnorderedMap(benchmark::State& state) {
    const TestVector& testVector = kTestVectors[state.range(0)];
    LossInfoMap<std::unordered_map<LossInfoKey, int, hash_pair>> lossInfo;

    while (state.KeepRunning()) {
        int res = 0;
        for (int i = 0; i < kTestVectorSize; i++) {
            res += lossInfo.noteLossInfo(testVector.errors[i], testVector.tags[i]);
        }
        benchmark::DoNotOptimize(res);
    }
}
BENCHMARK(BM_LossInfoCollectionAndDumpViaUnorderedMap)
        ->Args({0})
        ->Args({1})
        ->Args({2})
        ->Args({3})
        ->Args({4})
        ->Args({5})
        ->Args({6})
        ->Args({7})
        ->Args({8})
        ->Args({9})
        ->Args({10})
        ->Args({11})
        ->Args({12})
        ->Args({13})
        ->Args({14})
        ->Args({15});

}  //  namespace statsd
}  //  namespace os
}  //  namespace android
