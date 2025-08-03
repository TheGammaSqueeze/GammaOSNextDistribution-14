/*
 * Copyright (C) 2023 The Android Open Source Project
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

#include <gtest/gtest.h>

#include "aidl/SessionTaskMap.h"

using std::literals::chrono_literals::operator""ms;
using std::literals::chrono_literals::operator""ns;

namespace aidl {
namespace google {
namespace hardware {
namespace power {
namespace impl {
namespace pixel {

SessionValueEntry makeSession(int tg) {
    SessionValueEntry sv;
    sv.tgid = tg;
    sv.uid = tg + 1;
    sv.idString = "Sess" + std::to_string(tg);
    sv.isActive = true;
    sv.isAppSession = false;
    sv.lastUpdatedTime = std::chrono::steady_clock::now();
    sv.votes = std::make_shared<Votes>();
    return sv;
}

// Get all sessions associated with taskId
std::vector<int64_t> getSessions(int taskId, const SessionTaskMap &m) {
    std::vector<int64_t> sessionIds;
    m.forEachSessionInTask(
            taskId, [&](int sessionId, const auto & /*sve*/) { sessionIds.push_back(sessionId); });
    std::sort(sessionIds.begin(), sessionIds.end());
    return sessionIds;
}

// Get all tasks associated with sessionId
std::vector<int> getTasks(int64_t sessionId, const SessionTaskMap &m) {
    std::vector<int> tasks;
    m.forEachSessionValTasks([&](int64_t sessId, const auto & /*sve*/, const auto &linkedTasks) {
        if (sessId != sessionId)
            return;
        tasks.insert(std::end(tasks), std::begin(linkedTasks), std::end(linkedTasks));
    });
    std::sort(tasks.begin(), tasks.end());
    return tasks;
}

// Tests ...
TEST(SessionTaskMapTest, add) {
    SessionTaskMap m;
    EXPECT_TRUE(m.add(1, makeSession(1000), {10, 20, 30}));
    EXPECT_TRUE(m.add(2, makeSession(2000), {40, 50}));
    EXPECT_TRUE(m.add(3, makeSession(2000), {60}));
    EXPECT_FALSE(m.add(3, makeSession(2000), {70}));
}

TEST(SessionTaskMapTest, threeWayMappingSessions) {
    SessionTaskMap m;
    m.add(1, makeSession(1000), {10, 20, 30});
    m.add(2, makeSession(2000), {40, 50, 60});
    m.add(3, makeSession(3000), {50});

    // Check three tasks map properly to sessions
    EXPECT_EQ(std::vector<int64_t>({1}), getSessions(10, m));
    EXPECT_EQ(std::vector<int64_t>({1}), getSessions(20, m));
    EXPECT_EQ(std::vector<int64_t>({1}), getSessions(30, m));
    EXPECT_EQ(std::vector<int64_t>({2}), getSessions(40, m));
    EXPECT_EQ(std::vector<int64_t>({2, 3}), getSessions(50, m));
    EXPECT_EQ(std::vector<int64_t>({2}), getSessions(60, m));
}

TEST(SessionTaskMapTest, threeWayMappingTasks) {
    SessionTaskMap m;
    m.add(1, makeSession(1000), {10, 20, 30});
    m.add(2, makeSession(2000), {40, 50, 60});
    m.add(3, makeSession(3000), {50});

    // Check three sessions map properly to tasks
    EXPECT_EQ(std::vector<int>({10, 20, 30}), getTasks(1, m));
    EXPECT_EQ(std::vector<int>({40, 50, 60}), getTasks(2, m));
    EXPECT_EQ(std::vector<int>({50}), getTasks(3, m));
}

TEST(SessionTaskMapTest, removeNonExisting) {
    SessionTaskMap m;
    EXPECT_FALSE(m.remove(1));
}

TEST(SessionTaskMapTest, removeMappingSessions) {
    SessionTaskMap m;
    m.add(1, makeSession(1000), {10, 20, 30});
    m.add(2, makeSession(2000), {40, 50, 60});
    m.add(3, makeSession(3000), {50});

    // remove
    EXPECT_TRUE(m.remove(2));

    // Check that remaining tasks map correctly to sessions
    EXPECT_EQ(std::vector<int64_t>({1}), getSessions(10, m));
    EXPECT_EQ(std::vector<int64_t>({1}), getSessions(20, m));
    EXPECT_EQ(std::vector<int64_t>({1}), getSessions(30, m));
    EXPECT_EQ(std::vector<int64_t>({}), getSessions(40, m));
    EXPECT_EQ(std::vector<int64_t>({3}), getSessions(50, m));
}

TEST(SessionTaskMapTest, removeMappingTasks) {
    SessionTaskMap m;
    EXPECT_FALSE(m.remove(1));

    m.add(1, makeSession(1000), {10, 20, 30});
    m.add(2, makeSession(2000), {40, 50, 60});
    m.add(3, makeSession(3000), {50});

    // remove
    EXPECT_TRUE(m.remove(2));
    EXPECT_FALSE(m.remove(2));

    // Check that remaining tasks map correctly to sessions
    EXPECT_EQ(std::vector<int>({10, 20, 30}), getTasks(1, m));
    EXPECT_EQ(std::vector<int>({}), getTasks(2, m));
    EXPECT_EQ(std::vector<int>({50}), getTasks(3, m));
}

TEST(SessionTaskMapTest, findEmpty) {
    SessionTaskMap m;
    EXPECT_EQ(nullptr, m.findSession(1));
}

TEST(SessionTaskMapTest, findSessionExists) {
    SessionTaskMap m;
    EXPECT_TRUE(m.add(1, makeSession(1000), {}));
    EXPECT_NE(nullptr, m.findSession(1));
}

TEST(SessionTaskMapTest, findSessionEmptyExistsEmpty) {
    SessionTaskMap m;
    EXPECT_EQ(nullptr, m.findSession(1));
    EXPECT_TRUE(m.add(1, makeSession(1000), {}));
    EXPECT_NE(nullptr, m.findSession(1));
    EXPECT_TRUE(m.remove(1));
    EXPECT_EQ(nullptr, m.findSession(1));
}

TEST(SessionTaskMapTest, sizeTasks) {
    SessionTaskMap m;
    EXPECT_EQ(0, m.sizeTasks());
    EXPECT_TRUE(m.add(1, makeSession(1000), {10, 20, 30}));
    EXPECT_TRUE(m.add(2, makeSession(2000), {40, 50, 60}));
    EXPECT_EQ(6, m.sizeTasks());
}

TEST(SessionTaskMapTest, sizeSessions) {
    SessionTaskMap m;
    EXPECT_EQ(0, m.sizeSessions());
    EXPECT_TRUE(m.add(1, makeSession(1000), {10, 20, 30}));
    EXPECT_TRUE(m.add(2, makeSession(2000), {40, 50, 60}));
    EXPECT_EQ(2, m.sizeSessions());
}

TEST(SessionTaskMapTest, replace) {
    SessionTaskMap m;

    // Add three sessions where sessions 2 and 3 have shared threads
    EXPECT_TRUE(m.add(1, makeSession(1000), {10, 20, 30}));
    EXPECT_TRUE(m.add(2, makeSession(2000), {20}));

    std::vector<pid_t> addedThreads;
    std::vector<pid_t> removedThreads;

    m.replace(1, {10, 40}, &addedThreads, &removedThreads);
    EXPECT_EQ(1, addedThreads.size());
    EXPECT_EQ(40, addedThreads[0]);

    EXPECT_EQ(1, removedThreads.size());
    EXPECT_EQ(30, removedThreads[0]);
}

TEST(SessionTaskMapTest, remove) {
    SessionTaskMap m;
    auto tNow = std::chrono::steady_clock::now();
    const int64_t sessionId = 1;
    SessionValueEntry sve;
    sve.isAppSession = true;
    sve.votes = std::make_shared<Votes>();
    sve.votes->add(sessionId, CpuVote(true, tNow, 400ms, 123, 1024));
    m.add(sessionId, sve, {10, 20, 30});
    EXPECT_TRUE(m.isAnyAppSessionActive(tNow));
    EXPECT_TRUE(m.remove(sessionId));
    EXPECT_FALSE(m.isAnyAppSessionActive(tNow));
}

TEST(SessionTaskMapTest, isAnyAppActive) {
    SessionTaskMap m;
    auto tNow = std::chrono::steady_clock::now();

    EXPECT_FALSE(m.isAnyAppSessionActive(tNow));

    const int sessionId = 1000;
    SessionValueEntry sv;
    sv.isActive = true;
    sv.isAppSession = true;
    sv.lastUpdatedTime = tNow;
    sv.votes = std::make_shared<Votes>();
    sv.votes->add(1, CpuVote(true, tNow, 400ms, 123, 1024));

    EXPECT_TRUE(m.add(sessionId, sv, {10, 20, 30}));
    EXPECT_TRUE(m.isAnyAppSessionActive(tNow));
    EXPECT_FALSE(m.isAnyAppSessionActive(tNow + 500ms));
}

int getVoteMin(const SessionTaskMap &m, int64_t taskId, std::chrono::steady_clock::time_point t) {
    int uclampMin;
    int uclampMax;
    m.getTaskVoteRange(taskId, t, &uclampMin, &uclampMax);
    return uclampMin;
}

TEST(SessionTaskMapTest, votesEdgeCaseOverlap) {
    SessionTaskMap m;

    // Sess 1: 10
    EXPECT_TRUE(m.add(1, makeSession(1000), {10}));
    const auto t0 = std::chrono::steady_clock::now();
    const int voteMax = 1000;

    // Session  Vote  UClamp  [Time start----------------Time End]  Delta
    // 1        1     111     [20----60]                            40
    // 1        2     122              [60-85]                      25
    // 1        3     133              [60--90]                     30
    m.addVote(1, 1, 111, voteMax, t0 + 20ns, 40ns);
    m.addVote(1, 2, 122, voteMax, t0 + 60ns, 25ns);
    m.addVote(1, 3, 133, voteMax, t0 + 60ns, 30ns);

    // Before any votes active
    EXPECT_EQ(0, getVoteMin(m, 10, t0 + 0ns));
    // First vote active
    EXPECT_EQ(111, getVoteMin(m, 10, t0 + 20ns));
    // In middle of first vote
    EXPECT_EQ(111, getVoteMin(m, 10, t0 + 35ns));
    // First, second, and third votes active
    EXPECT_EQ(133, getVoteMin(m, 10, t0 + 60ns));
    // Second and third votes are being used
    EXPECT_EQ(133, getVoteMin(m, 10, t0 + 61ns));
    // Third vote is being used
    EXPECT_EQ(133, getVoteMin(m, 10, t0 + 86ns));
    // No votes active
    EXPECT_EQ(0, getVoteMin(m, 10, t0 + 91ns));
}

TEST(SessionTaskMapTest, votesEdgeCaseNoOverlap) {
    SessionTaskMap m;
    // Sess 2: 30
    EXPECT_TRUE(m.add(2, makeSession(2000), {20}));
    const auto t0 = std::chrono::steady_clock::now();
    const int voteMax = 1000;

    // Session  Vote  UClamp  [Time start----------------Time End]  Delta
    // 2        1     211       [30-55]                             25
    // 2        2     222                       [100-135]           35
    // 2        3     233                                [140-180]  40
    m.addVote(2, 1, 211, voteMax, t0 + 30ns, 25ns);
    m.addVote(2, 2, 222, voteMax, t0 + 100ns, 35ns);
    m.addVote(2, 3, 233, voteMax, t0 + 140ns, 40ns);

    // No votes active yet
    EXPECT_EQ(0, getVoteMin(m, 20, t0 + 0ns));
    // First vote active
    EXPECT_EQ(211, getVoteMin(m, 20, t0 + 30ns));
    // Second vote active
    EXPECT_EQ(222, getVoteMin(m, 20, t0 + 100ns));
    // Third vote active
    EXPECT_EQ(233, getVoteMin(m, 20, t0 + 140ns));
    // No votes active
    EXPECT_EQ(0, getVoteMin(m, 20, t0 + 181ns));
}

TEST(SessionTaskMapTest, TwoSessionsOneInactive) {
    const auto tNow = std::chrono::steady_clock::now();
    SessionTaskMap m;

    {
        SessionValueEntry sv;
        sv.isActive = true;
        sv.isAppSession = true;
        sv.lastUpdatedTime = tNow;
        sv.votes = std::make_shared<Votes>();
        sv.votes->add(11, CpuVote(true, tNow, 400ms, 111, 1024));
        EXPECT_TRUE(m.add(1001, sv, {10, 20, 30}));
    }

    {
        SessionValueEntry sv;
        sv.isActive = true;
        sv.isAppSession = true;
        sv.lastUpdatedTime = tNow;
        sv.votes = std::make_shared<Votes>();
        sv.votes->add(22, CpuVote(true, tNow, 400ms, 222, 1024));
        EXPECT_TRUE(m.add(2001, sv, {10, 20, 30}));
    }

    UclampRange uclampRange;
    m.getTaskVoteRange(10, tNow + 10ns, &uclampRange.uclampMin, &uclampRange.uclampMax);
    EXPECT_EQ(222, uclampRange.uclampMin);

    auto sessItr = m.findSession(2001);
    EXPECT_NE(nullptr, sessItr);
    sessItr->isActive = false;

    uclampRange.uclampMin = 0;
    uclampRange.uclampMax = 1024;
    m.getTaskVoteRange(10, tNow + 10ns, &uclampRange.uclampMin, &uclampRange.uclampMax);
    EXPECT_EQ(111, uclampRange.uclampMin);
}

}  // namespace pixel
}  // namespace impl
}  // namespace power
}  // namespace hardware
}  // namespace google
}  // namespace aidl
