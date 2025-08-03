/*
 * Copyright (c) 2022, The Android Open Source Project
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

#ifndef CPP_WATCHDOG_SERVER_SRC_UIDCPUSTATSCOLLECTOR_H_
#define CPP_WATCHDOG_SERVER_SRC_UIDCPUSTATSCOLLECTOR_H_

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

inline constexpr char kShowUidCpuTimeFile[] = "/proc/uid_cputime/show_uid_stat";

// Collector/Parser for `/proc/uid_cputime/show_uid_stat`.
class UidCpuStatsCollectorInterface : public RefBase {
public:
    // Initializes the collector.
    virtual void init() = 0;
    // Collects the per-UID CPU stats.
    virtual android::base::Result<void> collect() = 0;
    // Returns the latest per-UID CPU stats.
    virtual const std::unordered_map<uid_t, int64_t> latestStats() const = 0;
    // Returns the delta of per-UID CPU stats since the last before collection.
    virtual const std::unordered_map<uid_t, int64_t> deltaStats() const = 0;
    // Returns true only when the per-UID CPU stats file is accessible.
    virtual bool enabled() const = 0;
    // Returns the path for the per-UID CPU stats file.
    virtual const std::string filePath() const = 0;
};

class UidCpuStatsCollector final : public UidCpuStatsCollectorInterface {
public:
    explicit UidCpuStatsCollector(const std::string& path = kShowUidCpuTimeFile) : mPath(path) {}

    ~UidCpuStatsCollector() {}

    void init() override {
        Mutex::Autolock lock(mMutex);
        // Note: Verify proc file access outside the constructor. Otherwise, the unittests of
        // dependent classes would call the constructor before mocking and get killed due to
        // sepolicy violation.
        mEnabled = access(mPath.c_str(), R_OK) == 0;
    }

    android::base::Result<void> collect() override;

    const std::unordered_map<uid_t, int64_t> latestStats() const override {
        Mutex::Autolock lock(mMutex);
        return mLatestStats;
    }

    const std::unordered_map<uid_t, int64_t> deltaStats() const override {
        Mutex::Autolock lock(mMutex);
        return mDeltaStats;
    }

    bool enabled() const override {
        Mutex::Autolock lock(mMutex);
        return mEnabled;
    }

    const std::string filePath() const override { return mPath; }

private:
    // Path to show_uid_stat file. Default path is |kShowUidCpuTimeFile|.
    const std::string mPath;

    // Makes sure only one collection is running at any given time.
    mutable Mutex mMutex;

    // True if |mPath| is accessible.
    bool mEnabled GUARDED_BY(mMutex);

    // Latest dump from the file at |mPath|.
    std::unordered_map<uid_t, int64_t> mLatestStats GUARDED_BY(mMutex);

    // Delta of per-UID CPU stats since last before collection.
    std::unordered_map<uid_t, int64_t> mDeltaStats GUARDED_BY(mMutex);
};

}  // namespace watchdog
}  // namespace automotive
}  // namespace android

#endif  // CPP_WATCHDOG_SERVER_SRC_UIDCPUSTATSCOLLECTOR_H_
