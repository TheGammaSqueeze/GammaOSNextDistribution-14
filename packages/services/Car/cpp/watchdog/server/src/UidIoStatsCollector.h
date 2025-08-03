/*
 * Copyright (c) 2020, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef CPP_WATCHDOG_SERVER_SRC_UIDIOSTATSCOLLECTOR_H_
#define CPP_WATCHDOG_SERVER_SRC_UIDIOSTATSCOLLECTOR_H_

#include <android-base/result.h>
#include <android-base/stringprintf.h>
#include <utils/Mutex.h>
#include <utils/RefBase.h>

#include <stdint.h>

#include <string>
#include <unordered_map>

namespace android {
namespace automotive {
namespace watchdog {

constexpr const char* kUidIoStatsPath = "/proc/uid_io/stats";

enum UidState {
    FOREGROUND = 0,
    BACKGROUND,
    UID_STATES,
};

enum MetricType {
    READ_BYTES = 0,  // bytes read (from storage layer)
    WRITE_BYTES,     // bytes written (to storage layer)
    FSYNC_COUNT,     // number of fsync syscalls
    METRIC_TYPES,
};

// Defines the per-UID I/O stats.
class UidIoStats final {
public:
    UidIoStats() : metrics{{0}} {};
    UidIoStats(int64_t fgRdBytes, int64_t bgRdBytes, int64_t fgWrBytes, int64_t bgWrBytes,
               int64_t fgFsync, int64_t bgFsync) {
        metrics[READ_BYTES][FOREGROUND] = fgRdBytes;
        metrics[READ_BYTES][BACKGROUND] = bgRdBytes;
        metrics[WRITE_BYTES][FOREGROUND] = fgWrBytes;
        metrics[WRITE_BYTES][BACKGROUND] = bgWrBytes;
        metrics[FSYNC_COUNT][FOREGROUND] = fgFsync;
        metrics[FSYNC_COUNT][BACKGROUND] = bgFsync;
    }
    UidIoStats& operator-=(const UidIoStats& rhs);
    bool operator==(const UidIoStats& stats) const {
        return memcmp(&metrics, &stats.metrics, sizeof(metrics)) == 0;
    }
    int64_t sumReadBytes() const {
        const auto& [fgBytes, bgBytes] =
                std::tuple(metrics[READ_BYTES][FOREGROUND], metrics[READ_BYTES][BACKGROUND]);
        return (std::numeric_limits<int64_t>::max() - fgBytes) > bgBytes
                ? (fgBytes + bgBytes)
                : std::numeric_limits<int64_t>::max();
    }
    int64_t sumWriteBytes() const {
        const auto& [fgBytes, bgBytes] =
                std::tuple(metrics[WRITE_BYTES][FOREGROUND], metrics[WRITE_BYTES][BACKGROUND]);
        return (std::numeric_limits<int64_t>::max() - fgBytes) > bgBytes
                ? (fgBytes + bgBytes)
                : std::numeric_limits<int64_t>::max();
    }
    bool isZero() const;
    std::string toString() const;
    int64_t metrics[METRIC_TYPES][UID_STATES];
};

// Collector/Parser for `/proc/uid_io/stats`.
class UidIoStatsCollectorInterface : public RefBase {
public:
    // Initializes the collector.
    virtual void init() = 0;
    // Collects the per-UID I/O stats.
    virtual android::base::Result<void> collect() = 0;
    // Returns the latest per-uid I/O stats.
    virtual const std::unordered_map<uid_t, UidIoStats> latestStats() const = 0;
    // Returns the delta of per-uid I/O stats since the last before collection.
    virtual const std::unordered_map<uid_t, UidIoStats> deltaStats() const = 0;
    // Returns true only when the per-UID I/O stats file is accessible.
    virtual bool enabled() const = 0;
    // Returns the path for the per-UID I/O stats file.
    virtual const std::string filePath() const = 0;
};

class UidIoStatsCollector final : public UidIoStatsCollectorInterface {
public:
    explicit UidIoStatsCollector(const std::string& path = kUidIoStatsPath) : kPath(path) {}

    ~UidIoStatsCollector() {}

    void init() override {
        Mutex::Autolock lock(mMutex);
        // Note: Verify proc file access outside the constructor. Otherwise, the unittests of
        // dependent classes would call the constructor before mocking and get killed due to
        // sepolicy violation.
        mEnabled = access(kPath.c_str(), R_OK) == 0;
    }

    android::base::Result<void> collect() override;

    const std::unordered_map<uid_t, UidIoStats> latestStats() const override {
        Mutex::Autolock lock(mMutex);
        return mLatestStats;
    }

    const std::unordered_map<uid_t, UidIoStats> deltaStats() const override {
        Mutex::Autolock lock(mMutex);
        return mDeltaStats;
    }

    bool enabled() const override {
        Mutex::Autolock lock(mMutex);
        return mEnabled;
    }

    const std::string filePath() const override { return kPath; }

private:
    // Reads the contents of |kPath|.
    android::base::Result<std::unordered_map<uid_t, UidIoStats>> readUidIoStatsLocked() const;

    // Path to uid_io stats file. Default path is |kUidIoStatsPath|.
    const std::string kPath;

    // Makes sure only one collection is running at any given time.
    mutable Mutex mMutex;

    // True if |kPath| is accessible.
    bool mEnabled GUARDED_BY(mMutex);

    // Latest dump from the file at |kPath|.
    std::unordered_map<uid_t, UidIoStats> mLatestStats GUARDED_BY(mMutex);

    // Delta of per-UID I/O stats since last before collection.
    std::unordered_map<uid_t, UidIoStats> mDeltaStats GUARDED_BY(mMutex);
};

}  // namespace watchdog
}  // namespace automotive
}  // namespace android

#endif  //  CPP_WATCHDOG_SERVER_SRC_UIDIOSTATSCOLLECTOR_H_
