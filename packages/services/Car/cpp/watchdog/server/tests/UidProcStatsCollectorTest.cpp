/*
 * Copyright 2020 The Android Open Source Project
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

#include "ProcPidDir.h"
#include "UidProcStatsCollector.h"
#include "UidProcStatsCollectorTestUtils.h"

#include <android-base/file.h>
#include <android-base/stringprintf.h>
#include <gmock/gmock.h>

#include <inttypes.h>

#include <algorithm>
#include <string>

namespace android {
namespace automotive {
namespace watchdog {

using ::android::automotive::watchdog::testing::populateProcPidDir;
using ::android::base::StringAppendF;
using ::android::base::StringPrintf;
using ::testing::UnorderedPointwise;

namespace {

MATCHER(UidProcStatsByUidEq, "") {
    const auto& actual = std::get<0>(arg);
    const auto& expected = std::get<1>(arg);
    return actual.first == expected.first &&
            ExplainMatchResult(UidProcStatsEq(expected.second), actual.second, result_listener);
}

std::string pidStatusStr(pid_t pid, uid_t uid) {
    return StringPrintf("Pid:\t%" PRIu32 "\nTgid:\t%" PRIu32 "\nUid:\t%" PRIu32 "\n", pid, pid,
                        uid);
}

std::string toString(const std::unordered_map<uid_t, UidProcStats>& uidProcStatsByUid) {
    std::string buffer;
    StringAppendF(&buffer, "Number of UIDs: %" PRIi32 "\n",
                  static_cast<int>(uidProcStatsByUid.size()));
    for (const auto& [uid, stats] : uidProcStatsByUid) {
        StringAppendF(&buffer, "{UID: %d, %s}", uid, stats.toString().c_str());
    }
    return buffer;
}

int64_t ticksToMillis(int32_t clockTicks) {
    return (clockTicks * 1000) / sysconf(_SC_CLK_TCK);
}

}  // namespace

TEST(UidProcStatsCollectorTest, TestValidStatFiles) {
    std::unordered_map<pid_t, std::vector<pid_t>> pidToTids = {
            {1, {1, 453}},
            {1000, {1000, 1100}},
    };

    std::unordered_map<pid_t, std::string> perProcessStat = {
            {1, "1 (init) S 0 0 0 0 0 0 0 0 220 0 6 4 0 0 0 0 2 0 19\n"},
            {1000, "1000 (system_server) D 1 0 0 0 0 0 0 0 600 0 8000 4000 0 0 0 0 2 0 13400\n"},
    };

    std::unordered_map<pid_t, std::string> perProcessStatus = {
            {1, pidStatusStr(1, 0)},
            {1000, pidStatusStr(1000, 10001234)},
    };

    std::unordered_map<pid_t, std::string> perThreadStat = {
            {1, "1 (init) S 0 0 0 0 0 0 0 0 200 0 3 2 0 0 0 0 2 0 19\n"},
            {453, "453 (init) D 0 0 0 0 0 0 0 0 20 0 3 2 0 0 0 0 2 0 275\n"},
            {1000, "1000 (system_server) D 1 0 0 0 0 0 0 0 250 0 4000 2000 0 0 0 0 2 0 13400\n"},
            {1100, "1100 (system_server) D 1 0 0 0 0 0 0 0 350 0 4000 2000 0 0 0 0 2 0 13900\n"},
    };

    std::unordered_map<pid_t, std::string> perThreadTimeInState = {
            {1, "cpu0\n300000 5\n1700000 0\ncpu4\n710000 0\n1800000 0\ncpu7\n2000000 0"},
            {453, "cpu0\n300000 0\n1700000 0\ncpu4\n710000 0\n1800000 5\ncpu7\n2000000 0"},
            {1000,
             "cpu0\n300000 0\n1700000 1000\ncpu4\n710000 1000\n1800000 3000\ncpu7\n2000000 6000"},
            {1100, "cpu0\n300000 0\n1700000 0\ncpu4\n710000 0\n1800000 1000\ncpu7\n2000000 0"},
    };

    std::unordered_map<uid_t, UidProcStats> expected =
            {{0,
              UidProcStats{.cpuTimeMillis = ticksToMillis(10),
                           .cpuCycles = 105000000,
                           .totalMajorFaults = 220,
                           .totalTasksCount = 2,
                           .ioBlockedTasksCount = 1,
                           .processStatsByPid = {{1,
                                                  {"init",
                                                   ticksToMillis(19),
                                                   ticksToMillis(10),
                                                   105000000,
                                                   220,
                                                   2,
                                                   1,
                                                   {{1, 15000000}, {453, 90000000}}}}}}},
             {10001234,
              UidProcStats{.cpuTimeMillis = ticksToMillis(12'000),
                           .cpuCycles = 216100000000,
                           .totalMajorFaults = 600,
                           .totalTasksCount = 2,
                           .ioBlockedTasksCount = 2,
                           .processStatsByPid = {
                                   {1000,
                                    {"system_server",
                                     ticksToMillis(13'400),
                                     ticksToMillis(12'000),
                                     216100000000,
                                     600,
                                     2,
                                     2,
                                     {{1000, 198100000000}, {1100, 18000000000}}}}}}}};

    TemporaryDir firstSnapshot;
    ASSERT_RESULT_OK(populateProcPidDir(firstSnapshot.path, pidToTids, perProcessStat,
                                        perProcessStatus, perThreadStat, perThreadTimeInState));

    UidProcStatsCollector collector(firstSnapshot.path);
    collector.init();

    ASSERT_TRUE(collector.enabled())
            << "Files under the path `" << firstSnapshot.path << "` are inaccessible";
    ASSERT_RESULT_OK(collector.collect());

    auto actual = collector.deltaStats();

    EXPECT_THAT(actual, UnorderedPointwise(UidProcStatsByUidEq(), expected))
            << "First snapshot doesn't match.\nExpected:\n"
            << toString(expected) << "\nActual:\n"
            << toString(actual);

    pidToTids = {
            {1, {1, 453}}, {1000, {1000, 1400}},  // TID 1100 terminated and 1400 instantiated.
    };

    perProcessStat = {
            {1, "1 (init) S 0 0 0 0 0 0 0 0 920 0 10 10 0 0 0 0 2 0 19\n"},
            {1000, "1000 (system_server) R 1 0 0 0 0 0 0 0 1550 0 10000 8000 0 0 0 0 2 0 13400\n"},
    };

    perThreadStat = {
            {1, "1 (init) S 0 0 0 0 0 0 0 0 600 0 5 5 0 0 0 0 2 0 19\n"},
            {453, "453 (init) S 0 0 0 0 0 0 0 0 320 0 5 5 0 0 0 0 2 0 275\n"},
            {1000, "1000 (system_server) R 1 0 0 0 0 0 0 0 600 0 5000 2000 0 0 0 0 2 0 13400\n"},
            // TID 1100 hits +400 major page faults before terminating. This is counted against
            // PID 1000's perProcessStat.
            {1400, "1400 (system_server) S 1 0 0 0 0 0 0 0 200 0 5000 2000 0 0 0 0 2 0 8977476\n"},
    };

    perThreadTimeInState = {
            {1, "cpu0\n300000 5\n1700000 0\ncpu4\n710000 0\n1800000 0\ncpu7\n2000000 10"},
            {453, "cpu0\n300000 0\n1700000 0\ncpu4\n710000 0\n1800000 5\ncpu7\n2000000 0"},
            {1000,
             "cpu0\n300000 0\n1700000 1000\ncpu4\n710000 1000\n1800000 3000\ncpu7\n2000000 6000"},
            {1400, "cpu0\n300000 6000\n1700000 0\ncpu4\n710000 0\n1800000 0\ncpu7\n2000000 0"},
    };

    expected = {{0,
                 {.cpuTimeMillis = ticksToMillis(10),
                  .cpuCycles = 200'000'000,
                  .totalMajorFaults = 700,
                  .totalTasksCount = 2,
                  .ioBlockedTasksCount = 0,
                  .processStatsByPid = {{1,
                                         {"init",
                                          ticksToMillis(19),
                                          ticksToMillis(10),
                                          200'000'000,
                                          700,
                                          2,
                                          0,
                                          {{1, 200'000'000}, {453, 0}}}}}}},
                {10001234,
                 {.cpuTimeMillis = ticksToMillis(6'000),
                  .cpuCycles = 18'000'000'000,
                  .totalMajorFaults = 950,
                  .totalTasksCount = 2,
                  .ioBlockedTasksCount = 0,
                  .processStatsByPid = {{1000,
                                         {"system_server",
                                          ticksToMillis(13'400),
                                          ticksToMillis(6'000),
                                          18'000'000'000,
                                          950,
                                          2,
                                          0,
                                          {{1000, 0}, {1400, 18'000'000'000}}}}}}}};

    TemporaryDir secondSnapshot;
    ASSERT_RESULT_OK(populateProcPidDir(secondSnapshot.path, pidToTids, perProcessStat,
                                        perProcessStatus, perThreadStat, perThreadTimeInState));

    collector.mPath = secondSnapshot.path;

    ASSERT_TRUE(collector.enabled())
            << "Files under the path `" << secondSnapshot.path << "` are inaccessible";
    ASSERT_RESULT_OK(collector.collect());

    actual = collector.deltaStats();
    EXPECT_THAT(actual, UnorderedPointwise(UidProcStatsByUidEq(), expected))
            << "Second snapshot doesn't match.\nExpected:\n"
            << toString(expected) << "\nActual:\n"
            << toString(actual);
}

TEST(UidProcStatsCollectorTest, TestHandlesProcessTerminationBetweenScanningAndParsing) {
    std::unordered_map<pid_t, std::vector<pid_t>> pidToTids = {
            {1, {1}},
            {100, {100}},          // Process terminates after scanning PID directory.
            {1000, {1000}},        // Process terminates after reading stat file.
            {2000, {2000}},        // Process terminates after scanning task directory.
            {3000, {3000, 3300}},  // TID 3300 terminates after scanning task directory.
    };

    std::unordered_map<pid_t, std::string> perProcessStat = {
            {1, "1 (init) S 0 0 0 0 0 0 0 0 220 0 10 10 0 0 0 0 1 0 19\n"},
            // Process 100 terminated.
            {1000, "1000 (system_server) R 1 0 0 0 0 0 0 0 600 0 20 20 0 0 0 0 1 0 1000\n"},
            {2000, "2000 (logd) R 1 0 0 0 0 0 0 0 1200 0 30 30 0 0 0 0 1 0 4567\n"},
            {3000, "3000 (disk I/O) R 1 0 0 0 0 0 0 0 10300 0 40 40 0 0 0 0 2 0 67890\n"},
    };

    std::unordered_map<pid_t, std::string> perProcessStatus = {
            {1, pidStatusStr(1, 0)},
            // Process 1000 terminated.
            {2000, pidStatusStr(2000, 10001234)},
            {3000, pidStatusStr(3000, 10001234)},
    };

    std::unordered_map<pid_t, std::string> perThreadStat = {
            {1, "1 (init) S 0 0 0 0 0 0 0 0 200 0 10 10 0 0 0 0 1 0 19\n"},
            // Process 2000 terminated.
            {3000, "3000 (disk I/O) R 1 0 0 0 0 0 0 0 2400 0 30 30 0 0 0 0 2 0 67890\n"},
            // TID 3300 terminated.
    };

    std::unordered_map<pid_t, std::string> perThreadTimeInState = {
            {1, "cpu0\n300000 10\n1700000 10\ncpu4\n710000 0\n1800000 0\ncpu7\n2000000 0"},
            // Process 3000 terminated.
    };

    std::unordered_map<uid_t, UidProcStats> expected =
            {{0,
              UidProcStats{.cpuTimeMillis = ticksToMillis(20),
                           .cpuCycles = 200'000'000,
                           .totalMajorFaults = 220,
                           .totalTasksCount = 1,
                           .ioBlockedTasksCount = 0,
                           .processStatsByPid = {{1,
                                                  {"init",
                                                   ticksToMillis(19),
                                                   ticksToMillis(20),
                                                   200'000'000,
                                                   220,
                                                   1,
                                                   0,
                                                   {{1, 200'000'000}}}}}}},
             {10001234,
              UidProcStats{.cpuTimeMillis = ticksToMillis(140),
                           .cpuCycles = 0,
                           .totalMajorFaults = 11500,
                           .totalTasksCount = 2,
                           .ioBlockedTasksCount = 0,
                           .processStatsByPid = {{2000,
                                                  {"logd",
                                                   ticksToMillis(4567),
                                                   ticksToMillis(60),
                                                   0,
                                                   1200,
                                                   1,
                                                   0,
                                                   {}}},
                                                 {3000,
                                                  {"disk I/O",
                                                   ticksToMillis(67890),
                                                   ticksToMillis(80),
                                                   0,
                                                   10'300,
                                                   1,
                                                   0,
                                                   {}}}}}}};

    TemporaryDir procDir;
    ASSERT_RESULT_OK(populateProcPidDir(procDir.path, pidToTids, perProcessStat, perProcessStatus,
                                        perThreadStat, perThreadTimeInState));

    UidProcStatsCollector collector(procDir.path);
    collector.init();

    ASSERT_TRUE(collector.enabled())
            << "Files under the path `" << procDir.path << "` are inaccessible";
    ASSERT_RESULT_OK(collector.collect());

    auto actual = collector.deltaStats();
    EXPECT_THAT(actual, UnorderedPointwise(UidProcStatsByUidEq(), expected))
            << "Proc pid contents doesn't match.\nExpected:\n"
            << toString(expected) << "\nActual:\n"
            << toString(actual);
}

TEST(UidProcStatsCollectorTest, TestHandlesPidTidReuse) {
    std::unordered_map<pid_t, std::vector<pid_t>> pidToTids = {
            {1, {1, 367, 453, 589}},
            {1000, {1000}},
            {2345, {2345}},
    };

    std::unordered_map<pid_t, std::string> perProcessStat = {
            {1, "1 (init) S 0 0 0 0 0 0 0 0 1200 0 40 40 0 0 0 0 4 0 19\n"},
            {1000, "1000 (system_server) R 1 0 0 0 0 0 0 0 250 0 10 10 0 0 0 0 1 0 1000\n"},
            {2345, "2345 (logd) R 1 0 0 0 0 0 0 0 54354 0 10 10 0 0 0 0 1 0 456\n"},
    };

    std::unordered_map<pid_t, std::string> perProcessStatus = {
            {1, pidStatusStr(1, 0)},
            {1000, pidStatusStr(1000, 10001234)},
            {2345, pidStatusStr(2345, 10001234)},
    };

    std::unordered_map<pid_t, std::string> perThreadStat = {
            {1, "1 (init) S 0 0 0 0 0 0 0 0 200 0 10 10 0 0 0 0 4 0 19\n"},
            {367, "367 (init) S 0 0 0 0 0 0 0 0 400 0 10 10 0 0 0 0 4 0 100\n"},
            {453, "453 (init) S 0 0 0 0 0 0 0 0 100 0 10 10 0 0 0 0 4 0 275\n"},
            {589, "589 (init) D 0 0 0 0 0 0 0 0 500 0 10 10 0 0 0 0 4 0 600\n"},
            {1000, "1000 (system_server) R 1 0 0 0 0 0 0 0 250 0 10 10 0 0 0 0 1 0 1000\n"},
            {2345, "2345 (logd) R 1 0 0 0 0 0 0 0 54354 0 10 10 0 0 0 0 1 0 456\n"},
    };

    std::unordered_map<pid_t, std::string> perThreadTimeInState = {
            {1, "cpu0\n300000 20\n1700000 0\ncpu4\n710000 0\n1800000 0\ncpu7\n2000000 0"},
            {367, "cpu0\n300000 0\n1700000 20\ncpu4\n710000 0\n1800000 0\ncpu7\n2000000 0"},
            {453, "cpu0\n300000 0\n1700000 0\ncpu4\n710000 0\n1800000 20\ncpu7\n2000000 0"},
            {589, "cpu0\n300000 0\n1700000 0\ncpu4\n710000 0\n1800000 0\ncpu7\n2000000 20"},
            {1000, "cpu0\n300000 20\n1700000 0\ncpu4\n710000 0\n1800000 0\ncpu7\n2000000 0"},
            {2345, "cpu0\n300000 0\n1700000 0\ncpu4\n710000 0\n1800000 20\ncpu7\n2000000 0"},
    };

    std::unordered_map<uid_t, UidProcStats> expected =
            {{0,
              UidProcStats{.cpuTimeMillis = ticksToMillis(80),
                           .cpuCycles = 1'160'000'000,
                           .totalMajorFaults = 1200,
                           .totalTasksCount = 4,
                           .ioBlockedTasksCount = 1,
                           .processStatsByPid = {{1,
                                                  {"init",
                                                   ticksToMillis(19),
                                                   ticksToMillis(80),
                                                   1'160'000'000,
                                                   1200,
                                                   4,
                                                   1,
                                                   {{1, 60'000'000},
                                                    {367, 340'000'000},
                                                    {453, 360'000'000},
                                                    {589, 400'000'000}}}}}}},
             {10001234,
              UidProcStats{.cpuTimeMillis = ticksToMillis(40),
                           .cpuCycles = 420'000'000,
                           .totalMajorFaults = 54'604,
                           .totalTasksCount = 2,
                           .ioBlockedTasksCount = 0,
                           .processStatsByPid = {{1000,
                                                  {"system_server",
                                                   ticksToMillis(1000),
                                                   ticksToMillis(20),
                                                   60'000'000,
                                                   250,
                                                   1,
                                                   0,
                                                   {{1000, 60'000'000}}}},
                                                 {2345,
                                                  {"logd",
                                                   ticksToMillis(456),
                                                   ticksToMillis(20),
                                                   360'000'000,
                                                   54'354,
                                                   1,
                                                   0,
                                                   {{2345, 360'000'000}}}}}}}};

    TemporaryDir firstSnapshot;
    ASSERT_RESULT_OK(populateProcPidDir(firstSnapshot.path, pidToTids, perProcessStat,
                                        perProcessStatus, perThreadStat, perThreadTimeInState));

    UidProcStatsCollector collector(firstSnapshot.path);
    collector.init();

    ASSERT_TRUE(collector.enabled())
            << "Files under the path `" << firstSnapshot.path << "` are inaccessible";
    ASSERT_RESULT_OK(collector.collect());

    auto actual = collector.deltaStats();

    EXPECT_THAT(actual, UnorderedPointwise(UidProcStatsByUidEq(), expected))
            << "First snapshot doesn't match.\nExpected:\n"
            << toString(expected) << "\nActual:\n"
            << toString(actual);

    pidToTids = {
            {1, {1, 589}},       // TID 589 reused by the same process.
            {367, {367, 2000}},  // TID 367 reused as a PID. PID 2000 reused as a TID.
            // PID 1000 reused as a new PID. TID 453 reused by a different PID.
            {1000, {1000, 453}},
    };

    perProcessStat = {
            {1, "1 (init) S 0 0 0 0 0 0 0 0 1800 0 60 60 0 0 0 0 2 0 19\n"},
            {367, "367 (system_server) R 1 0 0 0 0 0 0 0 100 0 30 30 0 0 0 0 2 0 3450\n"},
            {1000, "1000 (logd) R 1 0 0 0 0 0 0 0 2000 0 20 20 0 0 0 0 2 0 4650\n"},
    };

    perProcessStatus = {
            {1, pidStatusStr(1, 0)},
            {367, pidStatusStr(367, 10001234)},
            {1000, pidStatusStr(1000, 10001234)},
    };

    perThreadStat = {
            {1, "1 (init) S 0 0 0 0 0 0 0 0 500 0 20 20 0 0 0 0 2 0 19\n"},
            {589, "589 (init) S 0 0 0 0 0 0 0 0 300 0 10 10 0 0 0 0 2 0 2345\n"},
            {367, "367 (system_server) R 1 0 0 0 0 0 0 0 50 0 15 15 0 0 0 0 2 0 3450\n"},
            {2000, "2000 (system_server) R 1 0 0 0 0 0 0 0 50 0 15 15 0 0 0 0 2 0 3670\n"},
            {1000, "1000 (logd) R 1 0 0 0 0 0 0 0 200 0 10 10 0 0 0 0 2 0 4650\n"},
            {453, "453 (logd) D 1 0 0 0 0 0 0 0 1800 0 10 10 0 0 0 0 2 0 4770\n"},
    };

    perThreadTimeInState = {
            {1, "cpu0\n300000 20\n1700000 20\ncpu4\n710000 0\n1800000 0\ncpu7\n2000000 0"},
            {589, "cpu0\n300000 20\n1700000 0\ncpu4\n710000 0\n1800000 0\ncpu7\n2000000 20"},
            {367, "cpu0\n300000 0\n1700000 0\ncpu4\n710000 30\n1800000 0\ncpu7\n2000000 0"},
            {2000, "cpu0\n300000 0\n1700000 0\ncpu4\n710000 0\n1800000 0\ncpu7\n2000000 30"},
            {1000, "cpu0\n300000 0\n1700000 0\ncpu4\n710000 0\n1800000 20\ncpu7\n2000000 0"},
            {453, "cpu0\n300000 20\n1700000 0\ncpu4\n710000 0\n1800000 0\ncpu7\n2000000 0"},
    };

    expected = {{0,
                 UidProcStats{.cpuTimeMillis = ticksToMillis(40),
                              .cpuCycles = 400'000'000,
                              .totalMajorFaults = 600,
                              .totalTasksCount = 2,
                              .ioBlockedTasksCount = 0,
                              .processStatsByPid = {{1,
                                                     {"init",
                                                      ticksToMillis(19),
                                                      ticksToMillis(40),
                                                      400'000'000,
                                                      600,
                                                      2,
                                                      0,
                                                      {{1, 340'000'000}, {589, 60'000'000}}}}}}},
                {10001234,
                 UidProcStats{.cpuTimeMillis = ticksToMillis(100),
                              .cpuCycles = 1'233'000'000,
                              .totalMajorFaults = 2100,
                              .totalTasksCount = 4,
                              .ioBlockedTasksCount = 1,
                              .processStatsByPid = {{367,
                                                     {"system_server",
                                                      ticksToMillis(3450),
                                                      ticksToMillis(60),
                                                      813'000'000,
                                                      100,
                                                      2,
                                                      0,
                                                      {{367, 213'000'000}, {2000, 600'000'000}}}},
                                                    {1000,
                                                     {"logd",
                                                      ticksToMillis(4650),
                                                      ticksToMillis(40),
                                                      420'000'000,
                                                      2000,
                                                      2,
                                                      1,
                                                      {{1000, 360'000'000},
                                                       {453, 60'000'000}}}}}}}};

    TemporaryDir secondSnapshot;
    ASSERT_RESULT_OK(populateProcPidDir(secondSnapshot.path, pidToTids, perProcessStat,
                                        perProcessStatus, perThreadStat, perThreadTimeInState));

    collector.mPath = secondSnapshot.path;

    ASSERT_TRUE(collector.enabled())
            << "Files under the path `" << secondSnapshot.path << "` are inaccessible";
    ASSERT_RESULT_OK(collector.collect());

    actual = collector.deltaStats();

    EXPECT_THAT(actual, UnorderedPointwise(UidProcStatsByUidEq(), expected))
            << "Second snapshot doesn't match.\nExpected:\n"
            << toString(expected) << "\nActual:\n"
            << toString(actual);
}

TEST(UidProcStatsCollectorTest, TestErrorOnCorruptedProcessStatFile) {
    std::unordered_map<pid_t, std::vector<pid_t>> pidToTids = {
            {1, {1}},
    };

    std::unordered_map<pid_t, std::string> perProcessStat = {
            {1, "1 (init) S 0 0 0 0 0 0 0 0 200 0 0 0 CORRUPTED DATA\n"},
    };

    std::unordered_map<pid_t, std::string> perProcessStatus = {
            {1, pidStatusStr(1, 0)},
    };

    std::unordered_map<pid_t, std::string> perThreadStat = {
            {1, "1 (init) S 0 0 0 0 0 0 0 0 200 0 0 0 0 0 0 0 1 0 19\n"},
    };

    std::unordered_map<pid_t, std::string> perThreadTimeInState = {
            {1, "cpu0\n300000 0\n1700000 10\ncpu4\n710000 0\n1800000 0\ncpu7\n2000000 0"},
    };

    TemporaryDir procDir;
    ASSERT_RESULT_OK(populateProcPidDir(procDir.path, pidToTids, perProcessStat, perProcessStatus,
                                        perThreadStat, perThreadTimeInState));

    UidProcStatsCollector collector(procDir.path);
    collector.init();

    ASSERT_TRUE(collector.enabled())
            << "Files under the path `" << procDir.path << "` are inaccessible";
    ASSERT_FALSE(collector.collect().ok()) << "No error returned for invalid process stat file";
}

TEST(UidProcStatsCollectorTest, TestErrorOnCorruptedProcessStatusFile) {
    std::unordered_map<pid_t, std::vector<pid_t>> pidToTids = {
            {1, {1}},
    };

    std::unordered_map<pid_t, std::string> perProcessStat = {
            {1, "1 (init) S 0 0 0 0 0 0 0 0 200 0 0 0 0 0 0 0 1 0 19\n"},
    };

    std::unordered_map<pid_t, std::string> perProcessStatus = {
            {1, "Pid:\t1\nTgid:\t1\nCORRUPTED DATA\n"},
    };

    std::unordered_map<pid_t, std::string> perThreadStat = {
            {1, "1 (init) S 0 0 0 0 0 0 0 0 200 0 0 0 0 0 0 0 1 0 19\n"},
    };

    std::unordered_map<pid_t, std::string> perThreadTimeInState = {
            {1, "cpu0\n300000 0\n1700000 10\ncpu4\n710000 0\n1800000 0\ncpu7\n2000000 0"},
    };

    TemporaryDir procDir;
    ASSERT_RESULT_OK(populateProcPidDir(procDir.path, pidToTids, perProcessStat, perProcessStatus,
                                        perThreadStat, perThreadTimeInState));

    UidProcStatsCollector collector(procDir.path);
    collector.init();

    ASSERT_TRUE(collector.enabled())
            << "Files under the path `" << procDir.path << "` are inaccessible";
    ASSERT_FALSE(collector.collect().ok()) << "No error returned for invalid process status file";
}

TEST(UidProcStatsCollectorTest, TestErrorOnProcessStatusFileWithNoUid) {
    std::unordered_map<pid_t, std::vector<pid_t>> pidToTids = {
            {1, {1}},
    };

    std::unordered_map<pid_t, std::string> perProcessStat = {
            {1, "1 (init) S 0 0 0 0 0 0 0 0 200 0 0 0 0 0 0 0 1 0 19\n"},
    };

    std::unordered_map<pid_t, std::string> perProcessStatus = {
            {1, "Pid:\t1\nTgid:\t1\n"},
    };

    std::unordered_map<pid_t, std::string> perThreadStat = {
            {1, "1 (init) S 0 0 0 0 0 0 0 0 200 0 0 0 0 0 0 0 1 0 19\n"},
    };

    std::unordered_map<pid_t, std::string> perThreadTimeInState = {
            {1, "cpu0\n300000 0\n1700000 10\ncpu4\n710000 0\n1800000 0\ncpu7\n2000000 0"},
    };

    TemporaryDir procDir;
    ASSERT_RESULT_OK(populateProcPidDir(procDir.path, pidToTids, perProcessStat, perProcessStatus,
                                        perThreadStat, perThreadTimeInState));

    UidProcStatsCollector collector(procDir.path);
    collector.init();

    ASSERT_TRUE(collector.enabled())
            << "Files under the path `" << procDir.path << "` are inaccessible";
    ASSERT_FALSE(collector.collect().ok())
            << "No error returned for process status file without uid";
}

TEST(UidProcStatsCollectorTest, TestErrorOnProcessStatusFileWithNoTgid) {
    std::unordered_map<pid_t, std::vector<pid_t>> pidToTids = {
            {1, {1}},
    };

    std::unordered_map<pid_t, std::string> perProcessStat = {
            {1, "1 (init) S 0 0 0 0 0 0 0 0 200 0 0 0 0 0 0 0 1 0 19\n"},
    };

    std::unordered_map<pid_t, std::string> perProcessStatus = {
            {1, "Pid:\t1\nUid:\t1\n"},
    };

    std::unordered_map<pid_t, std::string> perThreadStat = {
            {1, "1 (init) S 0 0 0 0 0 0 0 0 200 0 0 0 0 0 0 0 1 0 19\n"},
    };

    std::unordered_map<pid_t, std::string> perThreadTimeInState = {
            {1, "cpu0\n300000 0\n1700000 10\ncpu4\n710000 0\n1800000 0\ncpu7\n2000000 0"},
    };

    TemporaryDir procDir;
    ASSERT_RESULT_OK(populateProcPidDir(procDir.path, pidToTids, perProcessStat, perProcessStatus,
                                        perThreadStat, perThreadTimeInState));

    UidProcStatsCollector collector(procDir.path);
    collector.init();

    ASSERT_TRUE(collector.enabled())
            << "Files under the path `" << procDir.path << "` are inaccessible";
    ASSERT_FALSE(collector.collect().ok())
            << "No error returned for process status file without tgid";
}

TEST(UidProcStatsCollectorTest, TestErrorOnCorruptedThreadStatFile) {
    std::unordered_map<pid_t, std::vector<pid_t>> pidToTids = {
            {1, {1, 234}},
    };

    std::unordered_map<pid_t, std::string> perProcessStat = {
            {1, "1 (init) S 0 0 0 0 0 0 0 0 200 0 0 0 0 0 0 0 2 0 678\n"},
    };

    std::unordered_map<pid_t, std::string> perProcessStatus = {
            {1, pidStatusStr(1, 0)},
    };

    std::unordered_map<pid_t, std::string> perThreadStat = {
            {1, "1 (init) S 0 0 0 0 0 0 0 0 200 0 0 0 0 0 0 0 2 0 678\n"},
            {234, "234 (init) D 0 0 0 0 0 0 0 0 200 0 0 0 CORRUPTED DATA\n"},
    };

    std::unordered_map<pid_t, std::string> perThreadTimeInState = {
            {1, "cpu0\n300000 0\n1700000 10\ncpu4\n710000 0\n1800000 0\ncpu7\n2000000 0"},
    };

    TemporaryDir procDir;
    ASSERT_RESULT_OK(populateProcPidDir(procDir.path, pidToTids, perProcessStat, perProcessStatus,
                                        perThreadStat, perThreadTimeInState));

    UidProcStatsCollector collector(procDir.path);
    collector.init();

    ASSERT_TRUE(collector.enabled())
            << "Files under the path `" << procDir.path << "` are inaccessible";
    ASSERT_FALSE(collector.collect().ok()) << "No error returned for invalid thread stat file";
}

TEST(UidProcStatsCollectorTest, TestErrorOnCorruptedThreadTimeInStateFile) {
    std::unordered_map<pid_t, std::vector<pid_t>> pidToTids = {
            {1, {1, 234}},
    };

    std::unordered_map<pid_t, std::string> perProcessStat = {
            {1, "1 (init) S 0 0 0 0 0 0 0 0 200 0 0 0 0 0 0 0 2 0 678\n"},
    };

    std::unordered_map<pid_t, std::string> perProcessStatus = {
            {1, pidStatusStr(1, 0)},
    };

    std::unordered_map<pid_t, std::string> perThreadStat = {
            {1, "1 (init) S 0 0 0 0 0 0 0 0 200 0 0 0 0 0 0 0 2 0 678\n"},
            {234, "234 (init) D 0 0 0 0 0 0 0 0 200 0 0 0 0 0 0 0 2 0 500\n"},
    };

    std::unordered_map<pid_t, std::string> perThreadTimeInState = {
            {1, "cpu0\n300000 0\n1700000 10\ncpu4\n710000 0\n1800000 0\n"},
            {234, "cpu0\n300000 0\n1700000 10\ncpu4\n710000 0\n1800000 CORRUPTED\n DATA"},
    };

    TemporaryDir procDir;
    ASSERT_RESULT_OK(populateProcPidDir(procDir.path, pidToTids, perProcessStat, perProcessStatus,
                                        perThreadStat, perThreadTimeInState));

    UidProcStatsCollector collector(procDir.path);
    collector.init();

    ASSERT_TRUE(collector.enabled())
            << "Files under the path `" << procDir.path << "` are inaccessible";
    ASSERT_FALSE(collector.collect().ok()) << "No error returned for invalid thread stat file";
}

TEST(UidProcStatsCollectorTest, TestHandlesSpaceInCommName) {
    std::unordered_map<pid_t, std::vector<pid_t>> pidToTids = {
            {1, {1}},
    };

    std::unordered_map<pid_t, std::string> perProcessStat = {
            {1,
             "1 (random process name with space) S 0 0 0 0 0 0 0 0 200 0 10 10 0 0 0 0 1 0 19\n"},
    };

    std::unordered_map<pid_t, std::string> perProcessStatus = {
            {1, pidStatusStr(1, 0)},
    };

    std::unordered_map<pid_t, std::string> perThreadStat = {
            {1,
             "1 (random process name with space) S 0 0 0 0 0 0 0 0 200 0 10 10 0 0 0 0 1 0 19\n"},
    };

    std::unordered_map<pid_t, std::string> perThreadTimeInState = {
            {1, "cpu0\n300000 0\n1700000 20\ncpu4\n710000 0\n1800000 0\ncpu7\n2000000 0"},
    };

    std::unordered_map<uid_t, UidProcStats> expected = {
            {0,
             UidProcStats{.cpuTimeMillis = ticksToMillis(20),
                          .cpuCycles = 340'000'000,
                          .totalMajorFaults = 200,
                          .totalTasksCount = 1,
                          .ioBlockedTasksCount = 0,
                          .processStatsByPid = {{1,
                                                 {"random process name with space",
                                                  ticksToMillis(19),
                                                  ticksToMillis(20),
                                                  340'000'000,
                                                  200,
                                                  1,
                                                  0,
                                                  {{1, 340'000'000}}}}}}}};

    TemporaryDir procDir;
    ASSERT_RESULT_OK(populateProcPidDir(procDir.path, pidToTids, perProcessStat, perProcessStatus,
                                        perThreadStat, perThreadTimeInState));

    UidProcStatsCollector collector(procDir.path);
    collector.init();

    ASSERT_TRUE(collector.enabled())
            << "Files under the path `" << procDir.path << "` are inaccessible";
    ASSERT_RESULT_OK(collector.collect());

    auto actual = collector.deltaStats();

    EXPECT_THAT(actual, UnorderedPointwise(UidProcStatsByUidEq(), expected))
            << "Proc pid contents doesn't match.\nExpected:\n"
            << toString(expected) << "\nActual:\n"
            << toString(actual);
}

TEST(UidProcStatsCollectorTest, TestHandlesTimeInStateFileDisabledWithNoFile) {
    std::unordered_map<pid_t, std::vector<pid_t>> pidToTids = {
            {1, {1}},
    };

    std::unordered_map<pid_t, std::string> perProcessStat = {
            {1, "1 (init) S 0 0 0 0 0 0 0 0 200 0 10 10 0 0 0 0 1 0 19\n"},
    };

    std::unordered_map<pid_t, std::string> perProcessStatus = {
            {1, pidStatusStr(1, 0)},
    };

    std::unordered_map<pid_t, std::string> perThreadStat = {
            {1, "1 (init) S 0 0 0 0 0 0 0 0 200 0 10 10 0 0 0 0 1 0 19\n"},
    };

    // No time_in_state file present in procfs

    std::unordered_map<uid_t, UidProcStats> expected = {
            {0,
             UidProcStats{.cpuTimeMillis = ticksToMillis(20),
                          .cpuCycles = 0,
                          .totalMajorFaults = 200,
                          .totalTasksCount = 1,
                          .ioBlockedTasksCount = 0,
                          .processStatsByPid = {{1,
                                                 {"init",
                                                  ticksToMillis(19),
                                                  ticksToMillis(20),
                                                  0,
                                                  200,
                                                  1,
                                                  0,
                                                  {}}}}}}};

    TemporaryDir procDir;
    ASSERT_RESULT_OK(populateProcPidDir(procDir.path, pidToTids, perProcessStat, perProcessStatus,
                                        perThreadStat, {}));

    UidProcStatsCollector collector(procDir.path);
    collector.init();

    ASSERT_TRUE(collector.enabled())
            << "Files under the path `" << procDir.path << "` are inaccessible";
    ASSERT_RESULT_OK(collector.collect());

    auto actual = collector.deltaStats();

    EXPECT_THAT(actual, UnorderedPointwise(UidProcStatsByUidEq(), expected))
            << "Proc pid contents doesn't match.\nExpected:\n"
            << toString(expected) << "\nActual:\n"
            << toString(actual);
}

TEST(UidProcStatsCollectorTest, TestHandlesTimeInStateFileDisabledWithEmptyFile) {
    std::unordered_map<pid_t, std::vector<pid_t>> pidToTids = {
            {1, {1}},
    };

    std::unordered_map<pid_t, std::string> perProcessStat = {
            {1, "1 (init) S 0 0 0 0 0 0 0 0 200 0 10 10 0 0 0 0 1 0 19\n"},
    };

    std::unordered_map<pid_t, std::string> perProcessStatus = {
            {1, pidStatusStr(1, 0)},
    };

    std::unordered_map<pid_t, std::string> perThreadStat = {
            {1, "1 (init) S 0 0 0 0 0 0 0 0 200 0 10 10 0 0 0 0 1 0 19\n"},
    };

    std::unordered_map<pid_t, std::string> perThreadTimeInState = {
            {1, ""},
    };

    std::unordered_map<uid_t, UidProcStats> expected = {
            {0,
             UidProcStats{.cpuTimeMillis = ticksToMillis(20),
                          .cpuCycles = 0,
                          .totalMajorFaults = 200,
                          .totalTasksCount = 1,
                          .ioBlockedTasksCount = 0,
                          .processStatsByPid = {{1,
                                                 {"init",
                                                  ticksToMillis(19),
                                                  ticksToMillis(20),
                                                  0,
                                                  200,
                                                  1,
                                                  0,
                                                  {}}}}}}};

    TemporaryDir procDir;
    ASSERT_RESULT_OK(populateProcPidDir(procDir.path, pidToTids, perProcessStat, perProcessStatus,
                                        perThreadStat, {}));

    UidProcStatsCollector collector(procDir.path);
    collector.init();

    ASSERT_TRUE(collector.enabled())
            << "Files under the path `" << procDir.path << "` are inaccessible";
    ASSERT_RESULT_OK(collector.collect());

    auto actual = collector.deltaStats();

    EXPECT_THAT(actual, UnorderedPointwise(UidProcStatsByUidEq(), expected))
            << "Proc pid contents doesn't match.\nExpected:\n"
            << toString(expected) << "\nActual:\n"
            << toString(actual);
}

TEST(UidProcStatsCollectorTest, TestHandlesTimeInStateFileDisabledWithZeroCpuCycles) {
    std::unordered_map<pid_t, std::vector<pid_t>> pidToTids = {
            {1, {1}},
    };

    std::unordered_map<pid_t, std::string> perProcessStat = {
            {1, "1 (init) S 0 0 0 0 0 0 0 0 200 0 10 10 0 0 0 0 1 0 19\n"},
    };

    std::unordered_map<pid_t, std::string> perProcessStatus = {
            {1, pidStatusStr(1, 0)},
    };

    std::unordered_map<pid_t, std::string> perThreadStat = {
            {1, "1 (init) S 0 0 0 0 0 0 0 0 200 0 10 10 0 0 0 0 1 0 19\n"},
    };

    std::unordered_map<pid_t, std::string> perThreadTimeInState = {
            {1, "cpu0\n300000 0\n1700000 0\ncpu4\n710000 0\n1800000 0\ncpu7\n2000000 0"},
    };

    std::unordered_map<uid_t, UidProcStats> expected = {
            {0,
             UidProcStats{.cpuTimeMillis = ticksToMillis(20),
                          .cpuCycles = 0,
                          .totalMajorFaults = 200,
                          .totalTasksCount = 1,
                          .ioBlockedTasksCount = 0,
                          .processStatsByPid = {{1,
                                                 {"init",
                                                  ticksToMillis(19),
                                                  ticksToMillis(20),
                                                  0,
                                                  200,
                                                  1,
                                                  0,
                                                  {}}}}}}};

    TemporaryDir procDir;
    ASSERT_RESULT_OK(populateProcPidDir(procDir.path, pidToTids, perProcessStat, perProcessStatus,
                                        perThreadStat, {}));

    UidProcStatsCollector collector(procDir.path);
    collector.init();

    ASSERT_TRUE(collector.enabled())
            << "Files under the path `" << procDir.path << "` are inaccessible";
    ASSERT_RESULT_OK(collector.collect());

    auto actual = collector.deltaStats();

    EXPECT_THAT(actual, UnorderedPointwise(UidProcStatsByUidEq(), expected))
            << "Proc pid contents doesn't match.\nExpected:\n"
            << toString(expected) << "\nActual:\n"
            << toString(actual);
}

TEST(UidProcStatsCollectorTest, TestHandlesNoTimeInStateFileDuringCollection) {
    std::unordered_map<pid_t, std::vector<pid_t>> pidToTids = {
            {1, {1, 234}},
    };

    std::unordered_map<pid_t, std::string> perProcessStat = {
            {1, "1 (init) S 0 0 0 0 0 0 0 0 210 0 15 15 0 0 0 0 2 0 19\n"},
    };

    std::unordered_map<pid_t, std::string> perProcessStatus = {
            {1, pidStatusStr(1, 0)},
    };

    std::unordered_map<pid_t, std::string> perThreadStat = {
            {1, "1 (init) S 0 0 0 0 0 0 0 0 200 0 10 10 0 0 0 0 2 0 19\n"},
            {234, "1 (init) S 0 0 0 0 0 0 0 0 10 0 5 5 0 0 0 0 2 0 19\n"},
    };

    std::unordered_map<pid_t, std::string> perThreadTimeInState = {
            {1, "cpu0\n300000 0\n1700000 20\ncpu4\n710000 0\n1800000 0\ncpu7\n2000000 0"},
            // No time_in_state file present for TID 234
    };

    std::unordered_map<uid_t, UidProcStats> expected = {
            {0,
             UidProcStats{.cpuTimeMillis = ticksToMillis(30),
                          .cpuCycles = 340'000'000,
                          .totalMajorFaults = 210,
                          .totalTasksCount = 2,
                          .ioBlockedTasksCount = 0,
                          .processStatsByPid = {{1,
                                                 {"init",
                                                  ticksToMillis(19),
                                                  ticksToMillis(30),
                                                  340'000'000,
                                                  210,
                                                  2,
                                                  0,
                                                  {{1, 340'000'000}}}}}}}};

    TemporaryDir procDir;
    ASSERT_RESULT_OK(populateProcPidDir(procDir.path, pidToTids, perProcessStat, perProcessStatus,
                                        perThreadStat, perThreadTimeInState));

    UidProcStatsCollector collector(procDir.path);
    collector.init();

    ASSERT_TRUE(collector.enabled())
            << "Files under the path `" << procDir.path << "` are inaccessible";
    ASSERT_RESULT_OK(collector.collect());

    auto actual = collector.deltaStats();

    EXPECT_THAT(actual, UnorderedPointwise(UidProcStatsByUidEq(), expected))
            << "Proc pid contents doesn't match.\nExpected:\n"
            << toString(expected) << "\nActual:\n"
            << toString(actual);
}

TEST(UidProcStatsCollectorTest, TestUidProcStatsCollectorContentsFromDevice) {
    UidProcStatsCollector collector;
    collector.init();

    ASSERT_TRUE(collector.enabled()) << "/proc/[pid]/.* files are inaccessible";
    ASSERT_RESULT_OK(collector.collect());

    const auto& processStats = collector.deltaStats();

    // The below check should pass because there should be at least one process.
    EXPECT_GT(processStats.size(), static_cast<size_t>(0));
}

}  // namespace watchdog
}  // namespace automotive
}  // namespace android
