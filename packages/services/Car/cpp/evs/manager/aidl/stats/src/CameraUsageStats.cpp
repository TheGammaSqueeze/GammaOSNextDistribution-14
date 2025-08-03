/*
 * Copyright 2022 The Android Open Source Project
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

#include "stats/include/CameraUsageStats.h"

#include <android-base/logging.h>

#include <statslog_evsmanagerd.h>

namespace {

using ::aidl::android::hardware::automotive::evs::BufferDesc;
using ::android::AutoMutex;
using ::android::base::StringAppendF;

// Length of frame roundTrip history
constexpr int32_t kMaxHistoryLength = 100;

}  // namespace

namespace aidl::android::automotive::evs::implementation {

void CameraUsageStats::updateFrameStatsOnArrivalLocked(const std::vector<BufferDesc>& bufs) {
    const auto now = ::android::uptimeMillis();
    for (const auto& b : bufs) {
        auto it = mBufferHistory.find(b.bufferId);
        if (it != mBufferHistory.end()) {
            it->second.timestamp = now;
        } else {
            mBufferHistory.insert({b.bufferId, now});
        }
    }
}

void CameraUsageStats::updateFrameStatsOnReturnLocked(const std::vector<BufferDesc>& bufs) {
    const auto now = ::android::uptimeMillis();
    for (auto& b : bufs) {
        auto it = mBufferHistory.find(b.bufferId);
        if (it == mBufferHistory.end()) {
            LOG(WARNING) << "Buffer " << b.bufferId << " from " << b.deviceId << " is unknown.";
        } else {
            const auto roundTrip = now - it->second.timestamp;
            it->second.history.push(roundTrip);
            it->second.sum += roundTrip;
            if (it->second.history.size() > kMaxHistoryLength) {
                it->second.sum -= it->second.history.front();
                it->second.history.pop();
            }

            if (roundTrip > it->second.peak) {
                it->second.peak = roundTrip;
            }

            if (mStats.framesFirstRoundtripLatency == 0) {
                mStats.framesFirstRoundtripLatency = roundTrip;
            }
        }
    }
}

void CameraUsageStats::framesReceived(int32_t n) {
    AutoMutex lock(mMutex);
    mStats.framesReceived += n;
}

void CameraUsageStats::framesReceived(const std::vector<BufferDesc>& bufs) {
    AutoMutex lock(mMutex);
    mStats.framesReceived += bufs.size();

    updateFrameStatsOnArrivalLocked(bufs);
}

void CameraUsageStats::framesReturned(int32_t n) {
    AutoMutex lock(mMutex);
    mStats.framesReturned += n;
}

void CameraUsageStats::framesReturned(const std::vector<BufferDesc>& bufs) {
    AutoMutex lock(mMutex);
    mStats.framesReturned += bufs.size();

    updateFrameStatsOnReturnLocked(bufs);
}

void CameraUsageStats::framesIgnored(int32_t n) {
    AutoMutex lock(mMutex);
    mStats.framesIgnored += n;
}

void CameraUsageStats::framesSkippedToSync(int32_t n) {
    AutoMutex lock(mMutex);
    mStats.framesSkippedToSync += n;
}

void CameraUsageStats::eventsReceived() {
    AutoMutex lock(mMutex);
    ++mStats.erroneousEventsCount;
}

void CameraUsageStats::updateNumClients(size_t n) {
    AutoMutex lock(mMutex);
    if (n > mStats.peakClientsCount) {
        mStats.peakClientsCount = n;
    }
}

int64_t CameraUsageStats::getTimeCreated() const {
    AutoMutex lock(mMutex);
    return mTimeCreatedMs;
}

int64_t CameraUsageStats::getFramesReceived() const {
    AutoMutex lock(mMutex);
    return mStats.framesReceived;
}

int64_t CameraUsageStats::getFramesReturned() const {
    AutoMutex lock(mMutex);
    return mStats.framesReturned;
}

CameraUsageStatsRecord CameraUsageStats::snapshot() {
    AutoMutex lock(mMutex);

    int32_t sum = 0;
    int32_t peak = 0;
    int32_t len = 0;
    for (auto& [_, rec] : mBufferHistory) {
        sum += rec.sum;
        len += rec.history.size();
        if (peak < rec.peak) {
            peak = rec.peak;
        }
    }

    mStats.framesPeakRoundtripLatency = peak;
    mStats.framesAvgRoundtripLatency = static_cast<double>(sum) / len;
    return mStats;
}

void CameraUsageStats::writeStats() const {
    using ::aidl::android::automotive::evs::stats::EVS_USAGE_STATS_REPORTED;
    using ::aidl::android::automotive::evs::stats::stats_write;
    AutoMutex lock(mMutex);

    // Reports the usage statistics before the destruction
    // EvsUsageStatsReported atom is defined in
    // frameworks/base/cmds/statsd/src/atoms.proto
    const auto duration = ::android::uptimeMillis() - mTimeCreatedMs;
    auto result = stats_write(EVS_USAGE_STATS_REPORTED, mId, mStats.peakClientsCount,
                              mStats.erroneousEventsCount, mStats.framesFirstRoundtripLatency,
                              mStats.framesAvgRoundtripLatency, mStats.framesPeakRoundtripLatency,
                              mStats.framesReceived, mStats.framesIgnored,
                              mStats.framesSkippedToSync, duration);
    if (result < 0) {
        LOG(WARNING) << "Failed to report usage stats";
    }
}

std::string CameraUsageStats::toString(const CameraUsageStatsRecord& record, const char* indent) {
    return record.toString(indent);
}

}  // namespace aidl::android::automotive::evs::implementation
