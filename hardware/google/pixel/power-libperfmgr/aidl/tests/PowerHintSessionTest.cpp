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

#include <aidl/android/hardware/power/SessionTag.h>
#include <gtest/gtest.h>
#include <sys/syscall.h>

#include <chrono>
#include <mutex>
#include <thread>
#include <unordered_map>
#include <vector>

// define private as public to expose the private members for test.
#define private public
#include "aidl/PowerHintSession.h"
#include "aidl/PowerSessionManager.h"

#define gettid() syscall(SYS_gettid)

using std::literals::chrono_literals::operator""ms;
using std::literals::chrono_literals::operator""ns;
using std::literals::chrono_literals::operator""s;

namespace aidl {
namespace google {
namespace hardware {
namespace power {
namespace impl {
namespace pixel {

class PowerHintSessionTest : public ::testing::Test {
  public:
    void SetUp() {
        // create a list of threads
        for (int i = 0; i < numOfThreads; i++) {
            threadIsAlive.emplace_back(true);
            threadList.emplace_back(std::thread([this, threadInd = i]() {
                ALOGI("Test thread %d is running.", (int32_t)gettid());
                {
                    std::lock_guard<std::mutex> lock(m);
                    threadIds[threadInd] = gettid();
                }
                while (threadIsAlive[threadInd]) {
                    std::this_thread::sleep_for(50ms);
                }
                ALOGI("Test thread %d is closed.", (int32_t)gettid());
            }));
        }
        std::this_thread::sleep_for(50ms);

        // create two hint sessions
        for (int i = 0; i < numOfThreads; i++) {
            if (i <= numOfThreads / 2) {
                session1Threads.emplace_back(threadIds[i]);
            }

            if (i >= numOfThreads / 2) {
                session2Threads.emplace_back(threadIds[i]);
            }
        }

        sess1 = ndk::SharedRefBase::make<PowerHintSession>(1, 1, session1Threads, 1000000,
                                                           SessionTag::OTHER);
        sess2 = ndk::SharedRefBase::make<PowerHintSession>(2, 2, session2Threads, 1000000,
                                                           SessionTag::OTHER);
    }

    void TearDown() {
        for (int i = 0; i < numOfThreads; i++) {
            if (threadIsAlive[i]) {
                threadIsAlive[i] = false;
                threadList[i].join();
            }
        }
        threadList.clear();
        threadIds.clear();
        threadIsAlive.clear();
        session1Threads.clear();
        session2Threads.clear();
    }

  protected:
    static const int numOfThreads = 3;
    std::vector<std::thread> threadList;
    std::unordered_map<int, int32_t> threadIds;
    std::vector<bool> threadIsAlive;
    std::mutex m;
    std::vector<int32_t> session1Threads;
    std::vector<int32_t> session2Threads;
    std::shared_ptr<PowerHintSession> sess1;
    std::shared_ptr<PowerHintSession> sess2;

    // close the i-th thread in thread list.
    void closeThread(int i) {
        if (i < 0 || i >= numOfThreads)
            return;
        if (threadIsAlive[i]) {
            threadIsAlive[i] = false;
            threadList[i].join();
        }
    }
};

TEST_F(PowerHintSessionTest, removeDeadThread) {
    ALOGI("Running dead thread test for hint sessions.");
    auto sessManager = sess1->mPSManager;
    ASSERT_EQ(2, sessManager->mSessionTaskMap.mSessions.size());

    // The sessions' thread list doesn't change after thread died until the uclamp
    // min update is triggered.
    int deadThreadInd = numOfThreads / 2;
    auto deadThreadID = threadIds[deadThreadInd];
    closeThread(deadThreadInd);
    ASSERT_EQ(sessManager->mSessionTaskMap.mSessions[sess1->mSessionId].linkedTasks,
              session1Threads);
    ASSERT_EQ(sessManager->mSessionTaskMap.mSessions[sess2->mSessionId].linkedTasks,
              session2Threads);
    ASSERT_EQ(sessManager->mSessionTaskMap.mTasks[deadThreadID].size(), 2);

    // Trigger an update of uclamp min.
    auto tNow = std::chrono::duration_cast<std::chrono::nanoseconds>(
                        std::chrono::high_resolution_clock::now().time_since_epoch())
                        .count();
    WorkDuration wDur(tNow, 1100000);
    sess1->reportActualWorkDuration(std::vector<WorkDuration>{wDur});
    ASSERT_EQ(sessManager->mSessionTaskMap.mTasks[deadThreadID].size(), 1);
    sess2->reportActualWorkDuration(std::vector<WorkDuration>{wDur});
    ASSERT_EQ(sessManager->mSessionTaskMap.mTasks.count(deadThreadID), 0);
    std::erase(session1Threads, deadThreadID);
    std::erase(session2Threads, deadThreadID);
    ASSERT_EQ(sessManager->mSessionTaskMap.mSessions[sess1->mSessionId].linkedTasks,
              session1Threads);
    ASSERT_EQ(sessManager->mSessionTaskMap.mSessions[sess2->mSessionId].linkedTasks,
              session2Threads);

    // Close all the threads in session 1.
    for (int i = 0; i <= numOfThreads / 2; i++) {
        closeThread(i);
    }
    tNow = std::chrono::duration_cast<std::chrono::nanoseconds>(
                   std::chrono::high_resolution_clock::now().time_since_epoch())
                   .count();
    sess1->reportActualWorkDuration(std::vector<WorkDuration>{wDur});
    ASSERT_EQ(2, sessManager->mSessionTaskMap.mSessions.size());  // Session still alive
    ASSERT_EQ(sessManager->mSessionTaskMap.mSessions[sess1->mSessionId].linkedTasks.size(), 0);
}

TEST_F(PowerHintSessionTest, setThreads) {
    auto sessManager = sess1->mPSManager;
    ASSERT_EQ(2, sessManager->mSessionTaskMap.mSessions.size());

    ASSERT_EQ(sessManager->mSessionTaskMap.mSessions[sess1->mSessionId].linkedTasks,
              session1Threads);

    std::vector<int32_t> newSess1Threads;
    for (auto tid : threadIds) {
        newSess1Threads.emplace_back(tid.second);
    }
    sess1->setThreads(newSess1Threads);
    ASSERT_EQ(sessManager->mSessionTaskMap.mSessions[sess1->mSessionId].linkedTasks,
              newSess1Threads);

    sess1->close();
    sess2->close();
}

}  // namespace pixel
}  // namespace impl
}  // namespace power
}  // namespace hardware
}  // namespace google
}  // namespace aidl
