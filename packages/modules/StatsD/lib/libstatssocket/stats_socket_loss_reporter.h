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

#pragma once

#include <stddef.h>
#include <stdint.h>

#include <atomic>
#include <thread>
#include <unordered_map>

class StatsSocketLossReporter {
public:
    static StatsSocketLossReporter& getInstance();

    void noteDrop(int32_t error, int32_t atomId);

    /**
     * @brief Dump loss info into statsd as a STATS_SOCKET_LOSS_REPORTED atom instance
     *
     * @param forceDump skip cooldown timer evaluation
     * @return true if atom have been written into the socket successfully
     * @return false if atom have been written into the socket with an error
     */
    void dumpAtomsLossStats(bool forceDump = false);

    ~StatsSocketLossReporter();

private:
    StatsSocketLossReporter();

    void startCooldownTimer(int64_t elapsedRealtimeNanos);
    bool isCooldownTimerActive(int64_t elapsedRealtimeNanos) const;

    const int32_t mUid;
    std::atomic_int64_t mFirstTsNanos = 0;
    std::atomic_int64_t mLastTsNanos = 0;
    std::atomic_int64_t mCooldownTimerFinishAtNanos = 0;

    // Loss info data will be logged to statsd as a regular AStatsEvent
    // which means it needs to obey event size limitations (4kB)
    // for N tag ids the loss info might take N * 12 + 8 + 8 + 4 bytes
    // defining guardrail as a 100 tag ids should limit the atom size to
    // 100 * 12 + 8 + 8 + 4 ~ 1.2kB
    const size_t kMaxAtomTagsCount = 100;

    const int64_t kCoolDownTimerDurationNanos = 10 * 1000 * 1000;  // 10ms

    struct HashPair final {
        template <class TFirst, class TSecond>
        size_t operator()(const std::pair<TFirst, TSecond>& p) const noexcept {
            uintmax_t hash = std::hash<TFirst>{}(p.first);
            hash <<= sizeof(uintmax_t) * 4;
            hash ^= std::hash<TSecond>{}(p.second);
            return std::hash<uintmax_t>{}(hash);
        }
    };

    // guards access to below mLossInfo
    mutable std::mutex mMutex;

    using LossInfoKey = std::pair<int, int>;  // [error, tag]

    // Represents loss info as a counter per [error, tag] pair
    std::unordered_map<LossInfoKey, int, HashPair> mLossInfo;

    // tracks guardrail kMaxAtomTagsCount hit count
    int32_t mOverflowCounter = 0;
};
