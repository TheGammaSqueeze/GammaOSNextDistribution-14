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

#ifndef CPP_WATCHDOG_SERVER_TESTS_UIDPROCSTATSCOLLECTORTESTUTILS_H_
#define CPP_WATCHDOG_SERVER_TESTS_UIDPROCSTATSCOLLECTORTESTUTILS_H_

#include "UidProcStatsCollector.h"

#include <gmock/gmock.h>

namespace android {
namespace automotive {
namespace watchdog {

MATCHER(CpuCyclesByTidEq, "") {
    const auto& actual = std::get<0>(arg);
    const auto& expected = std::get<1>(arg);
    return actual.first == expected.first && actual.second == expected.second;
}

MATCHER_P(ProcessStatsEq, expected, "") {
    const auto& actual = arg;
    return ::testing::Value(actual.comm, ::testing::Eq(expected.comm)) &&
            ::testing::Value(actual.startTimeMillis, ::testing::Eq(expected.startTimeMillis)) &&
            ::testing::Value(actual.cpuTimeMillis, ::testing::Eq(expected.cpuTimeMillis)) &&
            ::testing::Value(actual.totalCpuCycles, ::testing::Eq(expected.totalCpuCycles)) &&
            ::testing::Value(actual.totalMajorFaults, ::testing::Eq(expected.totalMajorFaults)) &&
            ::testing::Value(actual.totalTasksCount, ::testing::Eq(expected.totalTasksCount)) &&
            ::testing::Value(actual.ioBlockedTasksCount,
                             ::testing::Eq(expected.ioBlockedTasksCount)) &&
            ::testing::Value(actual.cpuCyclesByTid,
                             ::testing::UnorderedPointwise(CpuCyclesByTidEq(),
                                                           expected.cpuCyclesByTid));
}

MATCHER(ProcessStatsByPidEq, "") {
    const auto& actual = std::get<0>(arg);
    const auto& expected = std::get<1>(arg);
    return actual.first == expected.first &&
            ::testing::Value(actual.second, ProcessStatsEq(expected.second));
}

MATCHER_P(UidProcStatsEq, expected, "") {
    const auto& actual = arg;
    return ::testing::Value(actual.cpuTimeMillis, ::testing::Eq(expected.cpuTimeMillis)) &&
            ::testing::Value(actual.cpuCycles, ::testing::Eq(expected.cpuCycles)) &&
            ::testing::Value(actual.totalMajorFaults, ::testing::Eq(expected.totalMajorFaults)) &&
            ::testing::Value(actual.totalTasksCount, ::testing::Eq(expected.totalTasksCount)) &&
            ::testing::Value(actual.ioBlockedTasksCount,
                             ::testing::Eq(expected.ioBlockedTasksCount)) &&
            ::testing::Value(actual.processStatsByPid,
                             ::testing::UnorderedPointwise(ProcessStatsByPidEq(),
                                                           expected.processStatsByPid));
}

}  // namespace watchdog
}  // namespace automotive
}  // namespace android

#endif  // CPP_WATCHDOG_SERVER_TESTS_UIDPROCSTATSCOLLECTORTESTUTILS_H_
