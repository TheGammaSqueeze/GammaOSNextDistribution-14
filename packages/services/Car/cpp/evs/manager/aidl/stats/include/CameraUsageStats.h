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

#ifndef CPP_EVS_MANAGER_AIDL_STATS_INCLUDE_CAMERAUSAGESTATS_H
#define CPP_EVS_MANAGER_AIDL_STATS_INCLUDE_CAMERAUSAGESTATS_H

#include <aidl/android/hardware/automotive/evs/BufferDesc.h>
#include <android-base/stringprintf.h>
#include <utils/Mutex.h>
#include <utils/RefBase.h>
#include <utils/SystemClock.h>

#include <cinttypes>
#include <queue>
#include <unordered_map>

namespace aidl::android::automotive::evs::implementation {

namespace aidlevs = ::aidl::android::hardware::automotive::evs;

struct CameraUsageStatsRecord {
public:
    // Time a snapshot is generated
    nsecs_t timestamp;

    // Total number of frames received
    int64_t framesReceived;

    // Total number of frames returned to EVS HAL
    int64_t framesReturned;

    // Number of frames ignored because no clients are listening
    int64_t framesIgnored;

    // Number of frames skipped to synchronize camera frames
    int64_t framesSkippedToSync;

    // Roundtrip latency of the very first frame after the stream started.
    int64_t framesFirstRoundtripLatency;

    // Peak mFrame roundtrip latency
    int64_t framesPeakRoundtripLatency;

    // Average mFrame roundtrip latency
    double framesAvgRoundtripLatency;

    // Number of the erroneous streaming events
    int32_t erroneousEventsCount;

    // Peak number of active clients
    int32_t peakClientsCount;

    // Calculates a delta between two records
    CameraUsageStatsRecord& operator-=(const CameraUsageStatsRecord& rhs) {
        // Only calculates differences in the frame statistics
        framesReceived = framesReceived - rhs.framesReceived;
        framesReturned = framesReturned - rhs.framesReturned;
        framesIgnored = framesIgnored - rhs.framesIgnored;
        framesSkippedToSync = framesSkippedToSync - rhs.framesSkippedToSync;
        erroneousEventsCount = erroneousEventsCount - rhs.erroneousEventsCount;

        return *this;
    }

    friend CameraUsageStatsRecord operator-(CameraUsageStatsRecord lhs,
                                            const CameraUsageStatsRecord& rhs) noexcept {
        lhs -= rhs;  // reuse compound assignment
        return lhs;
    }

    // Constructs a string that shows collected statistics
    std::string toString(const char* indent = "") const {
        std::string buffer;
        ::android::base::StringAppendF(&buffer,
                                       "%sTime Collected: @%" PRId64 "ms\n"
                                       "%sFrames Received: %" PRId64 "\n"
                                       "%sFrames Returned: %" PRId64 "\n"
                                       "%sFrames Ignored : %" PRId64 "\n"
                                       "%sFrames Skipped To Sync: %" PRId64 "\n"
                                       "%sFrames First Roundtrip: %" PRId64 "\n"
                                       "%sFrames Peak Roundtrip: %" PRId64 "\n"
                                       "%sFrames Average Roundtrip: %f\n"
                                       "%sPeak Number of Clients: %" PRId32 "\n\n",
                                       indent, ns2ms(timestamp), indent, framesReceived, indent,
                                       framesReturned, indent, framesIgnored, indent,
                                       framesSkippedToSync, indent, framesFirstRoundtripLatency,
                                       indent, framesPeakRoundtripLatency, indent,
                                       framesAvgRoundtripLatency, indent, peakClientsCount);

        return buffer;
    }
};

struct BufferRecord {
    BufferRecord(int64_t timestamp) : timestamp(timestamp), sum(0), peak(0) {}

    // Recent processing time
    std::queue<int32_t> history;

    // Timestamp on the buffer arrival
    int64_t timestamp;

    // Sum of processing times
    int64_t sum;

    // Peak processing time
    int64_t peak;
};

class CameraUsageStats : public ::android::RefBase {
public:
    CameraUsageStats(int32_t id) :
          mMutex(::android::Mutex()),
          mId(id),
          mTimeCreatedMs(::android::uptimeMillis()),
          mStats({}) {}

    void framesReceived(int32_t n = 1) EXCLUDES(mMutex);
    void framesReturned(int32_t n = 1) EXCLUDES(mMutex);
    void framesReceived(const std::vector<aidlevs::BufferDesc>& bufs) EXCLUDES(mMutex);
    void framesReturned(const std::vector<aidlevs::BufferDesc>& bufs) EXCLUDES(mMutex);
    void framesIgnored(int32_t n = 1) EXCLUDES(mMutex);
    void framesSkippedToSync(int32_t n = 1) EXCLUDES(mMutex);
    void eventsReceived() EXCLUDES(mMutex);
    int64_t getTimeCreated() const EXCLUDES(mMutex);
    int64_t getFramesReceived() const EXCLUDES(mMutex);
    int64_t getFramesReturned() const EXCLUDES(mMutex);
    void updateNumClients(size_t n) EXCLUDES(mMutex);
    void updateFrameStatsOnArrivalLocked(const std::vector<aidlevs::BufferDesc>& bufs)
            REQUIRES(mMutex);
    void updateFrameStatsOnReturnLocked(const std::vector<aidlevs::BufferDesc>& bufs)
            REQUIRES(mMutex);

    // Returns the statistics collected so far
    CameraUsageStatsRecord snapshot() EXCLUDES(mMutex);

    // Reports the usage statistics
    void writeStats() const EXCLUDES(mMutex);

    // Generates a string with current statistics
    static std::string toString(const CameraUsageStatsRecord& record, const char* indent = "");

private:
    // Mutex to protect a collection record
    mutable ::android::Mutex mMutex;

    // Unique identifier
    int32_t mId;

    // Time this object was created
    int64_t mTimeCreatedMs;

    // Usage statistics to collect
    CameraUsageStatsRecord mStats GUARDED_BY(mMutex);

    // Frame buffer histories
    std::unordered_map<int, BufferRecord> mBufferHistory GUARDED_BY(mMutex);
};

}  // namespace aidl::android::automotive::evs::implementation

#endif  // CPP_EVS_MANAGER_AIDL_STATS_INCLUDE_CAMERAUSAGESTATS_H
