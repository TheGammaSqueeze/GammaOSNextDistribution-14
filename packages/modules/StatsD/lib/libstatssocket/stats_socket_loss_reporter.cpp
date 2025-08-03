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

#include <stats_event.h>
#include <stats_socket_loss_reporter.h>
#include <unistd.h>

#include <vector>

#include "stats_statsdsocketlog.h"
#include "utils.h"

StatsSocketLossReporter::StatsSocketLossReporter() : mUid(getuid()) {
}

StatsSocketLossReporter::~StatsSocketLossReporter() {
    // try to dump loss stats since there might be pending data which have been not sent earlier
    // due to:
    // - cool down timer was active
    // - no input atoms to trigger loss info dump after cooldown timer expired
    dumpAtomsLossStats(true);
}

StatsSocketLossReporter& StatsSocketLossReporter::getInstance() {
    static StatsSocketLossReporter instance;
    return instance;
}

void StatsSocketLossReporter::noteDrop(int32_t error, int32_t atomId) {
    using namespace android::os::statsdsocket;

    const int64_t currentRealtimeTsNanos = get_elapsed_realtime_ns();

    // The intention is to skip self counting, however the timestamps still need to be updated
    // to know when was last failed attempt to log atom.
    // This is required for more accurate cool down timer work
    if (mFirstTsNanos == 0) {
        mFirstTsNanos.store(currentRealtimeTsNanos, std::memory_order_relaxed);
    }
    mLastTsNanos.store(currentRealtimeTsNanos, std::memory_order_relaxed);

    if (atomId == STATS_SOCKET_LOSS_REPORTED) {
        // avoid self counting due to write to socket might fail during dumpAtomsLossStats()
        // also due to mutex is not re-entrant and is already locked by dumpAtomsLossStats() API,
        // return to avoid deadlock
        // alternative is to consider std::recursive_mutex
        return;
    }

    std::unique_lock<std::mutex> lock(mMutex);

    // using unordered_map is more CPU efficient vs vectors, however will require some
    // postprocessing before writing into the socket
    const LossInfoKey key = std::make_pair(error, atomId);
    auto counterIt = mLossInfo.find(key);
    if (counterIt != mLossInfo.end()) {
        ++counterIt->second;
    } else if (mLossInfo.size() < kMaxAtomTagsCount) {
        mLossInfo[key] = 1;
    } else {
        mOverflowCounter++;
    }
}

void StatsSocketLossReporter::dumpAtomsLossStats(bool forceDump) {
    using namespace android::os::statsdsocket;

    const int64_t currentRealtimeTsNanos = get_elapsed_realtime_ns();

    if (!forceDump && isCooldownTimerActive(currentRealtimeTsNanos)) {
        // To avoid socket flooding with more STATS_SOCKET_LOSS_REPORTED atoms,
        // which have high probability of write failures, the cooldown timer approach is applied:
        // - start cooldown timer for 10us for every failed dump
        // - before writing STATS_SOCKET_LOSS_REPORTED do check the timestamp to keep some delay
        return;
    }

    // intention to hold mutex here during the stats_write() to avoid data copy overhead
    std::unique_lock<std::mutex> lock(mMutex);
    if (mLossInfo.size() == 0) {
        return;
    }

    // populate temp vectors to be written into the socket
    std::vector<int> errors(mLossInfo.size());
    std::vector<int> tags(mLossInfo.size());
    std::vector<int> counts(mLossInfo.size());

    auto lossInfoIt = mLossInfo.begin();
    for (size_t i = 0; i < mLossInfo.size(); i++, lossInfoIt++) {
        const LossInfoKey& key = lossInfoIt->first;
        errors[i] = key.first;
        tags[i] = key.second;
        counts[i] = lossInfoIt->second;
    }

    // below call might lead to socket loss event - intention is to avoid self counting
    const int ret = stats_write(STATS_SOCKET_LOSS_REPORTED, mUid, mFirstTsNanos, mLastTsNanos,
                                mOverflowCounter, errors, tags, counts);
    if (ret > 0) {
        // Otherwise, in case of failure we preserve all socket loss information between dumps.
        // When above write failed - the socket loss stats are not discarded
        // and would be re-send during next attempt.
        mOverflowCounter = 0;
        mLossInfo.clear();

        mFirstTsNanos.store(0, std::memory_order_relaxed);
        mLastTsNanos.store(0, std::memory_order_relaxed);
    }
    // since the delay before next attempt is significantly larger than this API call
    // duration it is ok to have correctness of timestamp in a range of 10us
    startCooldownTimer(currentRealtimeTsNanos);
}

void StatsSocketLossReporter::startCooldownTimer(int64_t elapsedRealtimeNanos) {
    mCooldownTimerFinishAtNanos = elapsedRealtimeNanos + kCoolDownTimerDurationNanos;
}

bool StatsSocketLossReporter::isCooldownTimerActive(int64_t elapsedRealtimeNanos) const {
    return mCooldownTimerFinishAtNanos > elapsedRealtimeNanos;
}
