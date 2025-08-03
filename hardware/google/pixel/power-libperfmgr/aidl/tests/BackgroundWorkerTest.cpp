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

#include "aidl/BackgroundWorker.h"

namespace aidl {
namespace google {
namespace hardware {
namespace power {
namespace impl {
namespace pixel {

using std::literals::chrono_literals::operator""s;
using std::literals::chrono_literals::operator""ms;
using std::literals::chrono_literals::operator""ns;

constexpr double kTIMING_TOLERANCE_MS = std::chrono::milliseconds(25).count();

// Use this work to package some work identifier val along with time t of when it was
// processed to validate that the waiting time is adhered to as closely as possible
struct work {
    int64_t val{0};
    std::chrono::steady_clock::time_point t;
};

auto getDurationMs(std::chrono::steady_clock::time_point endTime,
                   std::chrono::steady_clock::time_point startTime) {
    return std::chrono::duration<double, std::milli>(endTime - startTime);
}

TEST(PriorityQueueWorkerPool, testSingle) {
    const int pqId = 1;
    std::condition_variable cv;
    std::mutex m;
    std::vector<work> vec;
    vec.reserve(3);

    auto p = std::make_shared<PriorityQueueWorkerPool>(1, "adpf_");
    p->addCallback(pqId, [&](int64_t packageId) {
        std::lock_guard<std::mutex> lock(m);
        vec.push_back({packageId, std::chrono::steady_clock::now()});
        cv.notify_all();
    });

    const auto tNow = std::chrono::steady_clock::now();
    p->schedule(pqId, 500, tNow + 500ms);
    p->schedule(pqId, 100, tNow + 100ms);
    p->schedule(pqId, 300, tNow + 300ms);

    std::unique_lock<std::mutex> lock(m);
    EXPECT_EQ(0, vec.size());
    cv.wait_for(lock, 1500ms, [&]() { return vec.size() == 3; });

    EXPECT_EQ(3, vec.size());
    EXPECT_EQ(100, vec[0].val);
    EXPECT_NEAR(100, getDurationMs(vec[0].t, tNow).count(), kTIMING_TOLERANCE_MS);
    EXPECT_EQ(300, vec[1].val);
    EXPECT_NEAR(300, getDurationMs(vec[1].t, tNow).count(), kTIMING_TOLERANCE_MS);
    EXPECT_EQ(500, vec[2].val);
    EXPECT_NEAR(500, getDurationMs(vec[2].t, tNow).count(), kTIMING_TOLERANCE_MS);
}

TEST(TemplatePriorityQueueWorker, testSingle) {
    std::condition_variable cv;
    std::mutex m;
    std::vector<work> vec;
    vec.reserve(3);

    auto p = std::make_shared<PriorityQueueWorkerPool>(1, "adpf_");
    TemplatePriorityQueueWorker<int> worker{
            [&](int i) {
                std::lock_guard<std::mutex> lock(m);
                vec.push_back({i, std::chrono::steady_clock::now()});
                cv.notify_all();
            },
            p};

    // Would be nice to have a pause option for testing
    const auto tNow = std::chrono::steady_clock::now();
    worker.schedule(303, tNow + 500ms);
    worker.schedule(101, tNow + 100ms);
    worker.schedule(202, tNow + 300ms);

    std::unique_lock<std::mutex> lock(m);
    EXPECT_EQ(0, vec.size());
    cv.wait_for(lock, 1500ms, [&]() { return vec.size() == 3; });

    EXPECT_EQ(3, vec.size());
    EXPECT_EQ(101, vec[0].val);
    EXPECT_NEAR(100, getDurationMs(vec[0].t, tNow).count(), kTIMING_TOLERANCE_MS);
    EXPECT_EQ(202, vec[1].val);
    EXPECT_NEAR(300, getDurationMs(vec[1].t, tNow).count(), kTIMING_TOLERANCE_MS);
    EXPECT_EQ(303, vec[2].val);
    EXPECT_NEAR(500, getDurationMs(vec[2].t, tNow).count(), kTIMING_TOLERANCE_MS);
}

TEST(TemplatePriorityQueueWorker, testDouble) {
    std::condition_variable cv;
    std::mutex m;
    std::vector<work> vec;
    vec.reserve(6);

    auto p = std::make_shared<PriorityQueueWorkerPool>(1, "adpf_");
    TemplatePriorityQueueWorker<int> worker1{
            [&](int i) {
                std::lock_guard<std::mutex> lock(m);
                vec.push_back({i, std::chrono::steady_clock::now()});
                cv.notify_all();
            },
            p};

    TemplatePriorityQueueWorker<std::string> worker2{
            [&](const std::string &s) {
                std::lock_guard<std::mutex> lock(m);
                vec.push_back({atoi(s.c_str()), std::chrono::steady_clock::now()});
                cv.notify_all();
            },
            p};

    // Would be nice to have a pause option for testing
    const auto tNow = std::chrono::steady_clock::now();
    worker1.schedule(5, tNow + 300ms);
    worker1.schedule(1, tNow + 100ms);
    worker1.schedule(3, tNow + 200ms);
    worker2.schedule("2", tNow + 150ms);
    worker2.schedule("4", tNow + 250ms);
    worker2.schedule("6", tNow + 350ms);

    std::unique_lock<std::mutex> lock(m);
    EXPECT_EQ(0, vec.size());
    cv.wait_for(lock, 1500ms, [&]() { return vec.size() == 6; });

    EXPECT_EQ(6, vec.size());
    EXPECT_EQ(1, vec[0].val);
    EXPECT_NEAR(100, getDurationMs(vec[0].t, tNow).count(), kTIMING_TOLERANCE_MS);
    EXPECT_EQ(2, vec[1].val);
    EXPECT_NEAR(150, getDurationMs(vec[1].t, tNow).count(), kTIMING_TOLERANCE_MS);
    EXPECT_EQ(3, vec[2].val);
    EXPECT_NEAR(200, getDurationMs(vec[2].t, tNow).count(), kTIMING_TOLERANCE_MS);
    EXPECT_EQ(4, vec[3].val);
    EXPECT_NEAR(250, getDurationMs(vec[3].t, tNow).count(), kTIMING_TOLERANCE_MS);
    EXPECT_EQ(5, vec[4].val);
    EXPECT_NEAR(300, getDurationMs(vec[4].t, tNow).count(), kTIMING_TOLERANCE_MS);
    EXPECT_EQ(6, vec[5].val);
    EXPECT_NEAR(350, getDurationMs(vec[5].t, tNow).count(), kTIMING_TOLERANCE_MS);
}

}  // namespace pixel
}  // namespace impl
}  // namespace power
}  // namespace hardware
}  // namespace google
}  // namespace aidl
