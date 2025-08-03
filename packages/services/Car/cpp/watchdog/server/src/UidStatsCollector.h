/*
 * Copyright (c) 2021, The Android Open Source Project
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

#ifndef CPP_WATCHDOG_SERVER_SRC_UIDSTATSCOLLECTOR_H_
#define CPP_WATCHDOG_SERVER_SRC_UIDSTATSCOLLECTOR_H_

#include "PackageInfoResolver.h"
#include "UidCpuStatsCollector.h"
#include "UidIoStatsCollector.h"
#include "UidProcStatsCollector.h"

#include <aidl/android/automotive/watchdog/internal/PackageInfo.h>
#include <android-base/result.h>
#include <utils/Mutex.h>
#include <utils/RefBase.h>
#include <utils/StrongPointer.h>

#include <string>
#include <vector>

namespace android {
namespace automotive {
namespace watchdog {

// Forward declaration for testing use only.
namespace internal {

class UidStatsCollectorPeer;

}  // namespace internal

struct UidStats {
    aidl::android::automotive::watchdog::internal::PackageInfo packageInfo;
    int64_t cpuTimeMillis = 0;
    UidIoStats ioStats = {};
    UidProcStats procStats = {};
    // Returns true when package info is available.
    bool hasPackageInfo() const;
    // Returns package name if the |packageInfo| is available. Otherwise, returns the |uid|.
    std::string genericPackageName() const;
    // Returns the uid for the stats;
    uid_t uid() const;
};

// Collector/Aggregator for per-UID I/O and proc stats.
class UidStatsCollectorInterface : public RefBase {
public:
    // Initializes the collector.
    virtual void init() = 0;
    // Collects the per-UID I/O and proc stats.
    virtual android::base::Result<void> collect() = 0;
    // Returns the latest per-uid I/O and proc stats.
    virtual const std::vector<UidStats> latestStats() const = 0;
    // Returns the delta of per-uid I/O and proc stats since the last before collection.
    virtual const std::vector<UidStats> deltaStats() const = 0;
    // Returns true only when the per-UID I/O or proc stats files are accessible.
    virtual bool enabled() const = 0;
};

class UidStatsCollector final : public UidStatsCollectorInterface {
public:
    UidStatsCollector() :
          mPackageInfoResolver(PackageInfoResolver::getInstance()),
          mUidCpuStatsCollector(android::sp<UidCpuStatsCollector>::make()),
          mUidIoStatsCollector(android::sp<UidIoStatsCollector>::make()),
          mUidProcStatsCollector(android::sp<UidProcStatsCollector>::make()) {}

    void init() override {
        Mutex::Autolock lock(mMutex);
        mUidCpuStatsCollector->init();
        mUidIoStatsCollector->init();
        mUidProcStatsCollector->init();
    }

    android::base::Result<void> collect() override;

    const std::vector<UidStats> latestStats() const override {
        Mutex::Autolock lock(mMutex);
        return mLatestStats;
    }

    const std::vector<UidStats> deltaStats() const override {
        Mutex::Autolock lock(mMutex);
        return mDeltaStats;
    }

    bool enabled() const override {
        return mUidIoStatsCollector->enabled() || mUidProcStatsCollector->enabled();
    }

private:
    std::vector<UidStats> process(
            const std::unordered_map<uid_t, UidIoStats>& uidIoStatsByUid,
            const std::unordered_map<uid_t, UidProcStats>& uidProcStatsByUid,
            const std::unordered_map<uid_t, int64_t>& cpuTimeMillisByUid) const;

    // Local PackageInfoResolverInterface instance. Useful to mock in tests.
    sp<PackageInfoResolverInterface> mPackageInfoResolver;

    mutable Mutex mMutex;

    android::sp<UidCpuStatsCollectorInterface> mUidCpuStatsCollector GUARDED_BY(mMutex);

    android::sp<UidIoStatsCollectorInterface> mUidIoStatsCollector GUARDED_BY(mMutex);

    android::sp<UidProcStatsCollectorInterface> mUidProcStatsCollector GUARDED_BY(mMutex);

    std::vector<UidStats> mLatestStats GUARDED_BY(mMutex);

    std::vector<UidStats> mDeltaStats GUARDED_BY(mMutex);

    // For unit tests.
    friend class internal::UidStatsCollectorPeer;
};

}  // namespace watchdog
}  // namespace automotive
}  // namespace android

#endif  //  CPP_WATCHDOG_SERVER_SRC_UIDSTATSCOLLECTOR_H_
