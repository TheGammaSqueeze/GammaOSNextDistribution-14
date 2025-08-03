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

#include "ProcDiskStatsCollector.h"

#include <android-base/file.h>
#include <android-base/parseint.h>
#include <log/log.h>

#include <inttypes.h>

#include <algorithm>
#include <limits>

namespace android {
namespace automotive {
namespace watchdog {

using ::android::base::Error;
using ::android::base::Join;
using ::android::base::ParseInt;
using ::android::base::ParseUint;
using ::android::base::ReadFileToString;
using ::android::base::Result;
using ::android::base::Split;
using ::android::base::StartsWith;
using ::android::base::StringPrintf;
using ::android::base::StringReplace;
using ::android::base::Trim;

namespace {

/*
 * Format of a line in /proc/diskstats.
 * <major #> <minor #> <device name> <# of reads completed> <# of reads merged> <# of sectors read>\
 * <# of milliseconds spent reading> <# of writes completed> <# of writes merged> \
 * <# of sectors written> <# of milliseconds spent writing> <# of I/Os currently in progress> \
 * <# of milliseconds spent doing I/Os> <weighted # of milliseconds spent doing I/Os> \
 * <# of discards completed> <# of discards merged> <# of sectors discarded> \
 * <# of milliseconds spent discarding> <# of flush requests completed> \
 * <# of milliseconds spent flushing>
 *
 * All fields except the duration fields after the device name field are reported as unsigned long
 * values. Duration fields are reported as unsigned int values. The reported values may overflow and
 * the application should deal with it.
 */
Result<DiskStats> parseDiskStatsLine(const std::string& line) {
    std::vector<std::string> fields = Split(Trim(line), " ");
    for (auto it = fields.begin(); it != fields.end();) {
        if (it->empty() || std::all_of(it->begin(), it->end(), isspace)) {
            it = fields.erase(it);
            continue;
        }
        ++it;
    }
    uint64_t sectorsRead = 0;
    uint64_t sectorsWritten = 0;
    DiskStats diskStats;
    if (fields.size() < 14 || !ParseInt(fields[0], &diskStats.major) ||
        !ParseInt(fields[1], &diskStats.minor) ||
        !ParseUint(fields[3], &diskStats.numReadsCompleted) ||
        !ParseUint(fields[4], &diskStats.numReadsMerged) || !ParseUint(fields[5], &sectorsRead) ||
        !ParseUint(fields[6], &diskStats.readTimeInMillis) ||
        !ParseUint(fields[7], &diskStats.numWritesCompleted) ||
        !ParseUint(fields[8], &diskStats.numWritesMerged) ||
        !ParseUint(fields[9], &sectorsWritten) ||
        !ParseUint(fields[10], &diskStats.writeTimeInMillis) ||
        !ParseUint(fields[12], &diskStats.totalIoTimeInMillis) ||
        !ParseUint(fields[13], &diskStats.weightedTotalIoTimeInMillis)) {
        return Error() << "Failed to parse from line fields: '" << Join(fields, "', '") << "'";
    }
    diskStats.deviceName = fields[2];
    // Kernel sector size is 512 bytes. Therefore, 2 sectors == 1 KiB.
    diskStats.numKibRead = sectorsRead / 2;
    diskStats.numKibWritten = sectorsWritten / 2;
    if (fields.size() >= 20 &&
        (!ParseUint(fields[18], &diskStats.numFlushCompleted) ||
         !ParseUint(fields[19], &diskStats.flushTimeInMillis))) {
        return Error() << "Failed to parse flush stats from line fields: '" << Join(fields, "', '")
                       << "'";
    }
    return diskStats;
}

Result<ProcDiskStatsCollector::PerPartitionDiskStats> readDiskStatsFile(const std::string& path) {
    std::string buffer;
    if (!ReadFileToString(path, &buffer)) {
        return Error() << "ReadFileToString failed";
    }
    std::vector<std::string> lines = Split(std::move(buffer), "\n");
    if (lines.empty()) {
        return Error() << "File is empty";
    }
    ProcDiskStatsCollector::PerPartitionDiskStats perPartitionDiskStats;
    for (const auto& line : lines) {
        if (line.empty()) {
            continue;
        }
        if (auto diskStats = parseDiskStatsLine(line); !diskStats.ok()) {
            return diskStats.error();
        } else if (recordStatsForDevice(diskStats->deviceName)) {
            perPartitionDiskStats.emplace(std::move(*diskStats));
        }
    }
    if (perPartitionDiskStats.empty()) {
        return Error() << "No valid partition disk stats available";
    }
    return perPartitionDiskStats;
}

ProcDiskStatsCollector::PerPartitionDiskStats diffPerPartitionDiskStats(
        const ProcDiskStatsCollector::PerPartitionDiskStats& minuend,
        const ProcDiskStatsCollector::PerPartitionDiskStats& subtrahend) {
    ProcDiskStatsCollector::PerPartitionDiskStats diff;
    for (const auto& minuendStats : minuend) {
        if (auto subtrahendStats = subtrahend.find(minuendStats);
            subtrahendStats != subtrahend.end()) {
            auto diffStats = minuendStats;
            diffStats -= *subtrahendStats;
            diff.emplace(std::move(diffStats));
        } else {
            diff.emplace(minuendStats);
        }
    }
    return diff;
}

DiskStats aggregateSystemWideDiskStats(
        const ProcDiskStatsCollector::PerPartitionDiskStats&& perPartitionDiskStats) {
    DiskStats systemWideStats;
    for (const auto& stats : perPartitionDiskStats) {
        systemWideStats += stats;
    }
    return systemWideStats;
}

}  // namespace

DiskStats& DiskStats::operator-=(const DiskStats& rhs) {
    auto diff = [](const uint64_t& l, const uint64_t& r) -> uint64_t {
        // Disk stats may overflow so handling it here.
        return l >= r ? (l - r) : ((std::numeric_limits<uint64_t>::max() - r) + l);
    };
    numReadsCompleted = diff(numReadsCompleted, rhs.numReadsCompleted);
    numReadsMerged = diff(numReadsMerged, rhs.numReadsMerged);
    numKibRead = diff(numKibRead, rhs.numKibRead);
    readTimeInMillis = diff(readTimeInMillis, rhs.readTimeInMillis);
    numWritesCompleted = diff(numWritesCompleted, rhs.numWritesCompleted);
    numWritesMerged = diff(numWritesMerged, rhs.numWritesMerged);
    numKibWritten = diff(numKibWritten, rhs.numKibWritten);
    writeTimeInMillis = diff(writeTimeInMillis, rhs.writeTimeInMillis);
    totalIoTimeInMillis = diff(totalIoTimeInMillis, rhs.totalIoTimeInMillis);
    weightedTotalIoTimeInMillis =
            diff(weightedTotalIoTimeInMillis, rhs.weightedTotalIoTimeInMillis);
    numFlushCompleted = diff(numFlushCompleted, rhs.numFlushCompleted);
    flushTimeInMillis = diff(flushTimeInMillis, rhs.flushTimeInMillis);
    return *this;
}

DiskStats& DiskStats::operator+=(const DiskStats& rhs) {
    auto sum = [](const uint64_t& l, const uint64_t& r) -> uint64_t {
        return (std::numeric_limits<uint64_t>::max() - l) > r
                ? (l + r)
                : std::numeric_limits<uint64_t>::max();
    };
    numReadsCompleted = sum(numReadsCompleted, rhs.numReadsCompleted);
    numReadsMerged = sum(numReadsMerged, rhs.numReadsMerged);
    numKibRead = sum(numKibRead, rhs.numKibRead);
    readTimeInMillis = sum(readTimeInMillis, rhs.readTimeInMillis);
    numWritesCompleted = sum(numWritesCompleted, rhs.numWritesCompleted);
    numWritesMerged = sum(numWritesMerged, rhs.numWritesMerged);
    numKibWritten = sum(numKibWritten, rhs.numKibWritten);
    writeTimeInMillis = sum(writeTimeInMillis, rhs.writeTimeInMillis);
    totalIoTimeInMillis = sum(totalIoTimeInMillis, rhs.totalIoTimeInMillis);
    weightedTotalIoTimeInMillis = sum(weightedTotalIoTimeInMillis, rhs.weightedTotalIoTimeInMillis);
    numFlushCompleted = sum(numFlushCompleted, rhs.numFlushCompleted);
    flushTimeInMillis = sum(flushTimeInMillis, rhs.flushTimeInMillis);
    return *this;
}

size_t DiskStats::HashByPartition::operator()(const DiskStats& stats) const {
    return std::hash<std::string>{}(
            StringPrintf("%d.%d.%s", stats.major, stats.minor, stats.deviceName.c_str()));
}

bool DiskStats::EqualByPartition::operator()(const DiskStats& lhs, const DiskStats& rhs) const {
    return lhs.major == rhs.major && lhs.minor == rhs.minor && lhs.deviceName == rhs.deviceName;
}

Result<void> ProcDiskStatsCollector::collect() {
    if (!mEnabled) {
        return Error() << "Failed to access " << kPath;
    }

    Mutex::Autolock lock(mMutex);
    if (auto latestPerPartitionDiskStats = readDiskStatsFile(kPath);
        !latestPerPartitionDiskStats.ok()) {
        return Error() << "Failed to read per-partition disk stats from '" << kPath
                       << "': " << latestPerPartitionDiskStats.error();
    } else {
        mDeltaSystemWideDiskStats = aggregateSystemWideDiskStats(
                diffPerPartitionDiskStats(*latestPerPartitionDiskStats,
                                          mLatestPerPartitionDiskStats));
        mLatestPerPartitionDiskStats = *latestPerPartitionDiskStats;
    }
    return {};
}

}  // namespace watchdog
}  // namespace automotive
}  // namespace android
