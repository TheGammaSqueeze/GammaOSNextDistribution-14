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

#include "ProcDiskStatsCollector.h"

#include <android-base/file.h>
#include <android-base/strings.h>
#include <gmock/gmock.h>

#include <inttypes.h>

namespace android {
namespace automotive {
namespace watchdog {

using ::android::base::StartsWith;
using ::android::base::StringAppendF;
using ::android::base::WriteStringToFile;

namespace {

std::string getDiskStatsLine(const DiskStats& stats) {
    /**
     * /proc/diskstats lines doesn't contain tab spaces instead they have whitespace.
     * Thus write the output in the same format as /proc/diskstats so the tests can reproduce the
     * on device file format.
     */
    std::string buffer;
    StringAppendF(&buffer, "   %d       %d %s", stats.major, stats.minor, stats.deviceName.c_str());
    StringAppendF(&buffer, " %" PRIu64 " %" PRIu64 " %" PRIu64 " %" PRIu64, stats.numReadsCompleted,
                  stats.numReadsMerged, stats.numKibRead * 2, stats.readTimeInMillis);
    StringAppendF(&buffer, " %" PRIu64 " %" PRIu64 " %" PRIu64 " %" PRIu64,
                  stats.numWritesCompleted, stats.numWritesMerged, stats.numKibWritten * 2,
                  stats.writeTimeInMillis);
    StringAppendF(&buffer, " 0 %" PRIu64 " %" PRIu64 " 0 0 0 0 %" PRIu64 " %" PRIu64 "\n",
                  stats.totalIoTimeInMillis, stats.weightedTotalIoTimeInMillis,
                  stats.numFlushCompleted, stats.flushTimeInMillis);
    return buffer;
}

std::string getDiskStatsFile(
        const ProcDiskStatsCollectorInterface::PerPartitionDiskStats& allStats) {
    std::string buffer;
    for (const auto& stats : allStats) {
        StringAppendF(&buffer, "%s", getDiskStatsLine(stats).c_str());
    }
    return buffer;
}

DiskStats aggregateSystemWideDiskStats(
        const ProcDiskStatsCollectorInterface::PerPartitionDiskStats& perPartitionDiskStats) {
    DiskStats systemWideStats;
    for (const auto& stats : perPartitionDiskStats) {
        if (recordStatsForDevice(stats.deviceName)) {
            systemWideStats += stats;
        }
    }
    return systemWideStats;
}

bool isEquals(const DiskStats& lhs, const DiskStats& rhs) {
    auto tieStats = [](const DiskStats& stats) {
        return std::tie(stats.major, stats.minor, stats.deviceName, stats.numReadsCompleted,
                        stats.numReadsMerged, stats.numKibRead, stats.readTimeInMillis,
                        stats.numWritesCompleted, stats.numWritesMerged, stats.numKibWritten,
                        stats.writeTimeInMillis, stats.totalIoTimeInMillis,
                        stats.weightedTotalIoTimeInMillis, stats.numFlushCompleted,
                        stats.flushTimeInMillis);
    };
    return tieStats(lhs) == tieStats(rhs);
}

}  // namespace

TEST(ProcDiskStatsCollectorTest, TestValidStatsFile) {
    ProcDiskStatsCollectorInterface::PerPartitionDiskStats latestDiskStats =
            {{252, 32, "vdc", 120000, 760, 2000, 17190, 15000, 305000, 560000, 190000, 186140,
              213482, 64709, 4505},
             {251, 0, "zram0", 21959, 0, 175672, 868, 113635, 0, 909080, 9320, 25940, 10188, 0, 0},
             {254, 0, "dm-0", 340000, 0, 4000, 104264, 0, 0, 0, 0, 85768, 104264, 0, 0}};
    TemporaryFile tf;
    ASSERT_NE(tf.fd, -1);
    ASSERT_TRUE(WriteStringToFile(getDiskStatsFile(latestDiskStats), tf.path));

    DiskStats expectedDiskStats = aggregateSystemWideDiskStats(latestDiskStats);

    ProcDiskStatsCollector collector(tf.path);
    collector.init();

    ASSERT_TRUE(collector.enabled()) << "Temporary file is inaccessible";
    ASSERT_RESULT_OK(collector.collect());

    auto actualDiskStats = collector.deltaSystemWideDiskStats();

    ASSERT_TRUE(isEquals(expectedDiskStats, actualDiskStats))
            << "Expected 1st collection: '" << getDiskStatsLine(expectedDiskStats) << "'\nActual: '"
            << getDiskStatsLine(actualDiskStats) << "'";

    uint64_t maxUint64 = std::numeric_limits<uint64_t>::max();
    latestDiskStats = {{252, 32, "vdc", maxUint64, 130000, 100, maxUint64, 30000, 1305000, 1560000,
                        800, 386140, 313482, 164709, 14505},
                       {251, 0, "zram0", 21959, 0, 175672, 868, 113635, 0, 909080, 9320, 25940,
                        10188, 0, 0},
                       {254, 0, "dm-0", 3400, 0, 5000, 1104264, 0, 0, 0, 0, 185768, 1104264, 0, 0}};
    ASSERT_TRUE(WriteStringToFile(getDiskStatsFile(latestDiskStats), tf.path));

    expectedDiskStats =
            {0,     0,       "",      maxUint64,          129240, maxUint64 - 900, maxUint64,
             15000, 1000000, 1000000, maxUint64 - 189200, 300000, 1100000,         100000,
             10000};

    ASSERT_RESULT_OK(collector.collect());

    actualDiskStats = collector.deltaSystemWideDiskStats();

    ASSERT_TRUE(isEquals(expectedDiskStats, actualDiskStats))
            << "Expected 2nd collection: '" << getDiskStatsLine(expectedDiskStats) << "'\nActual: '"
            << getDiskStatsLine(actualDiskStats) << "'";
}

TEST(ProcDiskStatsCollectorTest, TestErrorOnInvalidStatsFile) {
    constexpr char contents[] = "252 0 disk 1200 300 0 CORRUPTED DATA\n";
    TemporaryFile tf;
    ASSERT_NE(tf.fd, -1);
    ASSERT_TRUE(WriteStringToFile(contents, tf.path));

    ProcDiskStatsCollector collector(tf.path);
    collector.init();

    ASSERT_TRUE(collector.enabled()) << "Temporary file is inaccessible";
    EXPECT_FALSE(collector.collect().ok()) << "No error returned for invalid file";
}

}  // namespace watchdog
}  // namespace automotive
}  // namespace android
