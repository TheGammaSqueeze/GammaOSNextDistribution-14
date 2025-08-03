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

#define LOG_TAG "carwatchdogd"

#include "UidStatsCollector.h"

#include <algorithm>
#include <unordered_map>
#include <unordered_set>

namespace android {
namespace automotive {
namespace watchdog {

using ::android::sp;
using ::android::base::Error;
using ::android::base::Result;

bool UidStats::hasPackageInfo() const {
    return !packageInfo.packageIdentifier.name.empty();
}

uid_t UidStats::uid() const {
    return static_cast<uid_t>(packageInfo.packageIdentifier.uid);
}

std::string UidStats::genericPackageName() const {
    if (hasPackageInfo()) {
        return packageInfo.packageIdentifier.name;
    }
    return std::to_string(packageInfo.packageIdentifier.uid);
}

Result<void> UidStatsCollector::collect() {
    Mutex::Autolock lock(mMutex);
    if (mUidIoStatsCollector->enabled()) {
        if (const auto& result = mUidIoStatsCollector->collect(); !result.ok()) {
            return Error() << "Failed to collect per-uid I/O stats: " << result.error();
        }
    }
    if (mUidProcStatsCollector->enabled()) {
        if (const auto& result = mUidProcStatsCollector->collect(); !result.ok()) {
            return Error() << "Failed to collect per-uid process stats: " << result.error();
        }
    }
    if (mUidCpuStatsCollector->enabled()) {
        if (const auto& result = mUidCpuStatsCollector->collect(); !result.ok()) {
            return Error() << "Failed to collect per-uid CPU stats: " << result.error();
        }
    }

    mLatestStats =
            process(mUidIoStatsCollector->latestStats(), mUidProcStatsCollector->latestStats(),
                    mUidCpuStatsCollector->latestStats());
    mDeltaStats = process(mUidIoStatsCollector->deltaStats(), mUidProcStatsCollector->deltaStats(),
                          mUidCpuStatsCollector->deltaStats());
    return {};
}

std::vector<UidStats> UidStatsCollector::process(
        const std::unordered_map<uid_t, UidIoStats>& uidIoStatsByUid,
        const std::unordered_map<uid_t, UidProcStats>& uidProcStatsByUid,
        const std::unordered_map<uid_t, int64_t>& cpuTimeMillisByUid) const {
    if (uidIoStatsByUid.empty() && uidProcStatsByUid.empty() && cpuTimeMillisByUid.empty()) {
        return std::vector<UidStats>();
    }
    std::unordered_set<uid_t> uidSet;
    for (const auto& [uid, _] : uidIoStatsByUid) {
        uidSet.insert(uid);
    }
    for (const auto& [uid, _] : uidProcStatsByUid) {
        uidSet.insert(uid);
    }
    for (const auto& [uid, _] : cpuTimeMillisByUid) {
        uidSet.insert(uid);
    }
    std::vector<uid_t> uids;
    for (const auto& uid : uidSet) {
        uids.push_back(uid);
    }
    const auto packageInfoByUid = mPackageInfoResolver->getPackageInfosForUids(uids);
    std::vector<UidStats> uidStats;
    for (const auto& uid : uids) {
        UidStats curUidStats;
        if (const auto it = packageInfoByUid.find(uid); it != packageInfoByUid.end()) {
            curUidStats.packageInfo = it->second;
        } else {
            curUidStats.packageInfo.packageIdentifier.uid = uid;
        }
        if (const auto it = uidIoStatsByUid.find(uid); it != uidIoStatsByUid.end()) {
            curUidStats.ioStats = it->second;
        }
        if (const auto it = cpuTimeMillisByUid.find(uid); it != cpuTimeMillisByUid.end()) {
            curUidStats.cpuTimeMillis = it->second;
        }
        if (const auto it = uidProcStatsByUid.find(uid); it != uidProcStatsByUid.end()) {
            curUidStats.procStats = it->second;
            if (curUidStats.cpuTimeMillis == 0) {
                curUidStats.cpuTimeMillis = curUidStats.procStats.cpuTimeMillis;
            }
        }
        uidStats.emplace_back(std::move(curUidStats));
    }
    return uidStats;
}

}  // namespace watchdog
}  // namespace automotive
}  // namespace android
