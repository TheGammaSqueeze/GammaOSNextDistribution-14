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

#include "aidl/UClampVoter.h"

namespace aidl {
namespace google {
namespace hardware {
namespace power {
namespace impl {
namespace pixel {

using std::literals::chrono_literals::operator""s;
using std::literals::chrono_literals::operator""ms;
using std::literals::chrono_literals::operator""ns;

TEST(VoteRange, active) {
    auto tNow = std::chrono::steady_clock::now();
    VoteRange vr(true, tNow, 200ms);
    EXPECT_TRUE(vr.active());
}

TEST(VoteRange, inActive) {
    auto tNow = std::chrono::steady_clock::now();
    VoteRange vr(false, tNow, 200ms);
    EXPECT_FALSE(vr.active());
}

TEST(VoteRange, defaultConstructorValues) {
    UclampRange uclamp_range;
    EXPECT_EQ(0, uclamp_range.uclampMin);
    EXPECT_EQ(1024, uclamp_range.uclampMax);
}

TEST(VoteRange, isTimeInRange) {
    const auto tNow = std::chrono::steady_clock::now();
    VoteRange vote_range(true, tNow, 250ms);
    EXPECT_FALSE(vote_range.isTimeInRange(tNow - 1ns));
    EXPECT_TRUE(vote_range.isTimeInRange(tNow));
    EXPECT_TRUE(vote_range.isTimeInRange(tNow + 1ns));
    EXPECT_TRUE(vote_range.isTimeInRange(tNow + 250ms));
    EXPECT_FALSE(vote_range.isTimeInRange(tNow + 250ms + 1ns));
}

TEST(VoteRange, isTimeInRangeInActive) {
    const auto tNow = std::chrono::steady_clock::now();
    VoteRange vote_range(true, tNow, 250ms);
    EXPECT_TRUE(vote_range.active());
    vote_range.setActive(false);
    EXPECT_FALSE(vote_range.active());
    EXPECT_FALSE(vote_range.isTimeInRange(tNow));
    EXPECT_FALSE(vote_range.isTimeInRange(tNow + 1ns));
    EXPECT_FALSE(vote_range.isTimeInRange(tNow + 250ms));
    EXPECT_FALSE(vote_range.isTimeInRange(tNow + 250ms + 1ns));
}

TEST(VoteRange, getUclampRange) {
    const auto tNow = std::chrono::steady_clock::now();
    const auto tNext = tNow + 1s;
    const auto tEnd1 = tNow + 4000000001ns;
    const auto tPrev = tNow - 1s;

    VoteRange voteFirst(true, tNow, 4000000000ns);
    EXPECT_FALSE(voteFirst.isTimeInRange(tPrev));
    EXPECT_TRUE(voteFirst.isTimeInRange(tNext));
    EXPECT_FALSE(voteFirst.isTimeInRange(tEnd1));

    Votes votes;
    votes.add(1,
              CpuVote(voteFirst.active(), voteFirst.startTime(), voteFirst.durationNs(), 11, 1024));
    UclampRange ur;

    votes.getUclampRange(&ur, tNext);
    EXPECT_EQ(11, ur.uclampMin);
    EXPECT_EQ(kUclampMax, ur.uclampMax);
}

TEST(UclampVoter, simple) {
    const auto tNow = std::chrono::steady_clock::now();

    auto votes = std::make_shared<Votes>();
    EXPECT_EQ(0, votes->size());

    votes->add(1, CpuVote(true, tNow, 4s, 11, 1024));
    EXPECT_EQ(1, votes->size());

    votes->add(2, CpuVote(true, tNow, 1s, 22, 1024));
    EXPECT_EQ(2, votes->size());

    UclampRange ur1;
    votes->getUclampRange(&ur1, tNow);
    EXPECT_EQ(22, ur1.uclampMin);
    EXPECT_EQ(kUclampMax, ur1.uclampMax);

    UclampRange ur2;
    votes->getUclampRange(&ur2, tNow + 2s);
    EXPECT_EQ(11, ur2.uclampMin);
    EXPECT_EQ(kUclampMax, ur2.uclampMax);

    UclampRange ur3;
    votes->getUclampRange(&ur3, tNow + 5s);
    EXPECT_EQ(0, ur3.uclampMin);
    EXPECT_EQ(1024, ur3.uclampMax);

    EXPECT_FALSE(votes->allTimedOut(tNow + 200ns));
    EXPECT_TRUE(votes->allTimedOut(tNow + 5s));

    EXPECT_TRUE(votes->setUseVote(2, false));
    EXPECT_TRUE(votes->anyTimedOut(tNow + 5s));
    EXPECT_TRUE(votes->setUseVote(2, true));

    EXPECT_FALSE(votes->anyTimedOut(tNow + 200ns));
}

TEST(UclampVoter, overwrite) {
    const auto tNow = std::chrono::steady_clock::now();

    auto votes = std::make_shared<Votes>();
    EXPECT_EQ(0, votes->size());

    votes->add(1, CpuVote(true, tNow, 4s, 11, 1024));
    EXPECT_EQ(1, votes->size());

    votes->add(2, CpuVote(true, tNow, 2s, 22, 1024));
    EXPECT_EQ(2, votes->size());

    UclampRange ucr1;
    votes->getUclampRange(&ucr1, tNow + 1s);
    EXPECT_EQ(22, ucr1.uclampMin);

    votes->add(1, CpuVote(true, tNow, 5s, 32, 1024));
    UclampRange ucr2;
    votes->getUclampRange(&ucr2, tNow + 1s);
    EXPECT_EQ(32, ucr2.uclampMin);
}

TEST(UclampVoter, updateDuration) {
    const auto tNow = std::chrono::steady_clock::now();

    auto votes = std::make_shared<Votes>();
    EXPECT_EQ(0, votes->size());

    votes->add(1, CpuVote(true, tNow, 4s, 11, 1024));
    votes->add(2, CpuVote(true, tNow, 2s, 22, 1024));
    EXPECT_EQ(2, votes->size());

    EXPECT_TRUE(votes->allTimedOut(tNow + 7s));
    votes->updateDuration(1, 8s);
    EXPECT_FALSE(votes->allTimedOut(tNow + 7s));
    votes->updateDuration(5, 10s);
    EXPECT_TRUE(votes->allTimedOut(tNow + 9s));
}

TEST(UclampVoter, loadVoteTest) {
    const int defaultVoteId = 1;
    const int loadVoteId = 2;
    const int uclampMinDefault = 50;
    const int uclampMinInit = 162;
    const int uclampMinHigh = 450;
    const auto tNow = std::chrono::steady_clock::now();
    UclampRange ucr;
    auto votes = std::make_shared<Votes>();

    // Default: min = 50 (original)
    votes->add(defaultVoteId, CpuVote(true, tNow, 400ms, uclampMinDefault, 1024));
    votes->getUclampRange(&ucr, tNow + 100ms);
    EXPECT_EQ(uclampMinDefault, ucr.uclampMin);

    // Default: min = UclampMinInit
    votes->add(defaultVoteId, CpuVote(true, tNow, 400ns, uclampMinInit, 1024));
    // Load: min = uclampMinHigh
    votes->add(loadVoteId, CpuVote(true, tNow, 250ns, uclampMinHigh, 1024));

    // Check that load is enabled
    ucr.uclampMin = 0;
    votes->getUclampRange(&ucr, tNow + 100ns);
    EXPECT_EQ(uclampMinHigh, ucr.uclampMin);

    // Timeout or restore after 1st frame
    // Expect to get 162.
    ucr.uclampMin = 0;
    votes->getUclampRange(&ucr, tNow + 350ns);
    EXPECT_EQ(uclampMinInit, ucr.uclampMin);
}

TEST(GpuCapacityVoter, testIncorrectTyping) {
    const auto now = std::chrono::steady_clock::now();
    Votes votes;
    static constexpr int gpu_vote_id = static_cast<int>(AdpfHintType::ADPF_GPU_CAPACITY);
    static constexpr int cpu_vote_id = static_cast<int>(AdpfHintType::ADPF_CPU_LOAD_UP);

    votes.add(cpu_vote_id, GpuVote(true, now, 250ns, Cycles(1024)));
    EXPECT_FALSE(votes.voteIsActive(cpu_vote_id));
    EXPECT_FALSE(votes.setUseVote(cpu_vote_id, true));
    EXPECT_FALSE(votes.remove(cpu_vote_id));

    votes.add(gpu_vote_id, CpuVote(true, now, 250ns, 66, 77));
    EXPECT_FALSE(votes.voteIsActive(gpu_vote_id));
    EXPECT_FALSE(votes.setUseVote(cpu_vote_id, true));
    EXPECT_FALSE(votes.remove(cpu_vote_id));

    UclampRange range;
    votes.getUclampRange(&range, now);
    EXPECT_EQ(range.uclampMin, 0);
    EXPECT_EQ(range.uclampMax, 1024);

    EXPECT_FALSE(votes.getGpuCapacityRequest(now));
}

TEST(GpuCapacityVoter, testGpuUseVote) {
    const auto now = std::chrono::steady_clock::now();
    Votes votes;
    static constexpr int gpu_vote_id1 = static_cast<int>(AdpfHintType::ADPF_GPU_CAPACITY);
    static constexpr int gpu_vote_id2 = static_cast<int>(AdpfHintType::ADPF_GPU_LOAD_UP);

    votes.add(gpu_vote_id1, GpuVote(true, now, 250ns, Cycles(1024)));
    EXPECT_TRUE(votes.setUseVote(gpu_vote_id1, true));
    EXPECT_FALSE(votes.setUseVote(gpu_vote_id2, true));
}

TEST(GpuCapacityVoter, testBasicVoteActivation) {
    auto const now = std::chrono::steady_clock::now();
    auto const timeout = 1s;
    auto const gpu_vote_id = static_cast<int>(AdpfHintType::ADPF_GPU_CAPACITY);
    Votes votes;

    votes.add(gpu_vote_id, GpuVote(true, now, 250ns, Cycles(100)));

    EXPECT_EQ(votes.size(), 1);
    EXPECT_TRUE(votes.voteIsActive(gpu_vote_id));

    votes.setUseVote(gpu_vote_id, false);
    EXPECT_FALSE(votes.voteIsActive(gpu_vote_id));

    votes.setUseVote(gpu_vote_id, true);
    EXPECT_TRUE(votes.voteIsActive(gpu_vote_id));

    EXPECT_TRUE(votes.remove(gpu_vote_id));
}

TEST(GpuCapacityVoter, testBasicVoteTimeouts) {
    auto const now = std::chrono::steady_clock::now();
    auto const timeout = 1s;
    auto const gpu_vote_id = static_cast<int>(AdpfHintType::ADPF_GPU_CAPACITY);
    Cycles const cycles(100);

    Votes votes;
    votes.add(gpu_vote_id, GpuVote(true, now, timeout, cycles));

    auto capacity = votes.getGpuCapacityRequest(now + 1ns);
    ASSERT_TRUE(capacity);
    EXPECT_EQ(*capacity, cycles);

    auto capacity2 = votes.getGpuCapacityRequest(now + 2 * timeout);
    EXPECT_FALSE(capacity2);
}

TEST(GpuCapacityVoter, testVoteTimeouts) {
    auto const now = std::chrono::steady_clock::now();
    auto const timeout = 1s;
    auto const timeout2 = 10s;
    auto const gpu_vote_id = static_cast<int>(AdpfHintType::ADPF_GPU_CAPACITY);
    auto const cpu_vote_id = static_cast<int>(AdpfHintType::ADPF_CPU_LOAD_UP);
    Cycles const cycles(100);

    Votes votes;
    votes.add(gpu_vote_id, GpuVote(true, now, timeout, cycles));
    votes.add(cpu_vote_id, CpuVote(true, now, timeout2, 66, 88));

    EXPECT_EQ(votes.size(), 2);
    EXPECT_EQ(votes.voteTimeout(gpu_vote_id), now + timeout);

    EXPECT_FALSE(votes.allTimedOut(now + std::chrono::microseconds(56)));
    EXPECT_FALSE(votes.anyTimedOut(now + std::chrono::microseconds(56)));
    EXPECT_FALSE(votes.allTimedOut(now + 2 * timeout));
    EXPECT_TRUE(votes.anyTimedOut(now + 2 * timeout));
    EXPECT_TRUE(votes.allTimedOut(now + 20 * timeout));
    EXPECT_TRUE(votes.anyTimedOut(now + 20 * timeout));
}

TEST(GpuCapacityVoter, testGpuVoteActive) {
    auto const now = std::chrono::steady_clock::now();
    auto const timeout = 1s;
    auto const gpu_vote_id = static_cast<int>(AdpfHintType::ADPF_GPU_CAPACITY);
    Cycles const cycles(100);

    Votes votes;
    votes.add(gpu_vote_id, GpuVote(true, now, timeout, cycles));

    EXPECT_TRUE(votes.voteIsActive(gpu_vote_id));
    auto const gpu_capacity_request = votes.getGpuCapacityRequest(now);
    ASSERT_TRUE(gpu_capacity_request);
    EXPECT_EQ(*gpu_capacity_request, cycles);
    EXPECT_TRUE(votes.setUseVote(gpu_vote_id, false));
    ASSERT_FALSE(votes.getGpuCapacityRequest(now));

    EXPECT_FALSE(votes.voteIsActive(gpu_vote_id));

    EXPECT_EQ(votes.size(), 1);
}
}  // namespace pixel
}  // namespace impl
}  // namespace power
}  // namespace hardware
}  // namespace google
}  // namespace aidl
