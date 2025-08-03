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

#define LOG_TAG "carwatchdogd"

#include "UidIoStatsCollector.h"

#include <android-base/file.h>
#include <android-base/parseint.h>
#include <android-base/stringprintf.h>
#include <android-base/strings.h>
#include <log/log.h>

#include <inttypes.h>

#include <string>
#include <unordered_map>
#include <utility>
#include <vector>

namespace android {
namespace automotive {
namespace watchdog {

using ::android::base::Error;
using ::android::base::ParseInt;
using ::android::base::ParseUint;
using ::android::base::ReadFileToString;
using ::android::base::Result;
using ::android::base::Split;
using ::android::base::StringPrintf;

namespace {

bool parseUidIoStats(const std::string& data, UidIoStats* stats, uid_t* uid) {
    std::vector<std::string> fields = Split(data, " ");
    if (fields.size() < 11 || !ParseUint(fields[0], uid) ||
        !ParseInt(fields[3], &stats->metrics[READ_BYTES][FOREGROUND]) ||
        !ParseInt(fields[4], &stats->metrics[WRITE_BYTES][FOREGROUND]) ||
        !ParseInt(fields[7], &stats->metrics[READ_BYTES][BACKGROUND]) ||
        !ParseInt(fields[8], &stats->metrics[WRITE_BYTES][BACKGROUND]) ||
        !ParseInt(fields[9], &stats->metrics[FSYNC_COUNT][FOREGROUND]) ||
        !ParseInt(fields[10], &stats->metrics[FSYNC_COUNT][BACKGROUND])) {
        ALOGW("Invalid uid I/O stats: \"%s\"", data.c_str());
        return false;
    }
    return true;
}

}  // namespace

UidIoStats& UidIoStats::operator-=(const UidIoStats& rhs) {
    const auto diff = [](int64_t lhs, int64_t rhs) -> int64_t { return lhs > rhs ? lhs - rhs : 0; };
    metrics[READ_BYTES][FOREGROUND] =
            diff(metrics[READ_BYTES][FOREGROUND], rhs.metrics[READ_BYTES][FOREGROUND]);
    metrics[READ_BYTES][BACKGROUND] =
            diff(metrics[READ_BYTES][BACKGROUND], rhs.metrics[READ_BYTES][BACKGROUND]);
    metrics[WRITE_BYTES][FOREGROUND] =
            diff(metrics[WRITE_BYTES][FOREGROUND], rhs.metrics[WRITE_BYTES][FOREGROUND]);
    metrics[WRITE_BYTES][BACKGROUND] =
            diff(metrics[WRITE_BYTES][BACKGROUND], rhs.metrics[WRITE_BYTES][BACKGROUND]);
    metrics[FSYNC_COUNT][FOREGROUND] =
            diff(metrics[FSYNC_COUNT][FOREGROUND], rhs.metrics[FSYNC_COUNT][FOREGROUND]);
    metrics[FSYNC_COUNT][BACKGROUND] =
            diff(metrics[FSYNC_COUNT][BACKGROUND], rhs.metrics[FSYNC_COUNT][BACKGROUND]);
    return *this;
}

bool UidIoStats::isZero() const {
    for (int i = 0; i < METRIC_TYPES; i++) {
        for (int j = 0; j < UID_STATES; j++) {
            if (metrics[i][j]) {
                return false;
            }
        }
    }
    return true;
}

std::string UidIoStats::toString() const {
    return StringPrintf("FgRdBytes:%" PRIi64 " BgRdBytes:%" PRIi64 " FgWrBytes:%" PRIi64
                        " BgWrBytes:%" PRIi64 " FgFsync:%" PRIi64 " BgFsync:%" PRIi64,
                        metrics[READ_BYTES][FOREGROUND], metrics[READ_BYTES][BACKGROUND],
                        metrics[WRITE_BYTES][FOREGROUND], metrics[WRITE_BYTES][BACKGROUND],
                        metrics[FSYNC_COUNT][FOREGROUND], metrics[FSYNC_COUNT][BACKGROUND]);
}

Result<void> UidIoStatsCollector::collect() {
    if (!mEnabled) {
        return Error() << "Can not access " << kPath;
    }

    Mutex::Autolock lock(mMutex);
    const auto& uidIoStatsByUid = readUidIoStatsLocked();
    if (!uidIoStatsByUid.ok() || uidIoStatsByUid->empty()) {
        return Error() << "Failed to get UID IO stats: " << uidIoStatsByUid.error();
    }

    mDeltaStats.clear();
    for (const auto& [uid, uidIoStats] : *uidIoStatsByUid) {
        if (uidIoStats.isZero()) {
            continue;
        }
        UidIoStats deltaStats = uidIoStats;
        if (const auto it = mLatestStats.find(uid); it != mLatestStats.end()) {
            if (deltaStats -= it->second; deltaStats.isZero()) {
                continue;
            }
        }
        mDeltaStats[uid] = deltaStats;
    }
    mLatestStats = *uidIoStatsByUid;
    return {};
}

Result<std::unordered_map<uid_t, UidIoStats>> UidIoStatsCollector::readUidIoStatsLocked() const {
    std::string buffer;
    if (!ReadFileToString(kPath, &buffer)) {
        return Error() << "ReadFileToString failed for " << kPath;
    }
    std::unordered_map<uid_t, UidIoStats> uidIoStatsByUid;
    std::vector<std::string> lines = Split(std::move(buffer), "\n");
    for (const auto& line : lines) {
        if (line.empty() || !line.compare(0, 4, "task")) {
            /* Skip per-task stats as CONFIG_UID_SYS_STATS_DEBUG is not set in the kernel and
             * the collected data is aggregated only per-UID.
             */
            continue;
        }
        uid_t uid;
        UidIoStats uidIoStats;
        if (!parseUidIoStats(line, &uidIoStats, &uid)) {
            return Error() << "Failed to parse the contents of " << kPath;
        }
        uidIoStatsByUid[uid] = uidIoStats;
    }
    return uidIoStatsByUid;
}

}  // namespace watchdog
}  // namespace automotive
}  // namespace android
