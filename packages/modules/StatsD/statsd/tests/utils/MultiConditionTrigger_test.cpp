/*
 * Copyright (C) 2020 The Android Open Source Project
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
#include "utils/MultiConditionTrigger.h"

#include <gtest/gtest.h>

#include <chrono>
#include <set>
#include <thread>
#include <vector>

#include "tests/statsd_test_util.h"

#ifdef __ANDROID__

using namespace std;
using std::this_thread::sleep_for;

namespace android {
namespace os {
namespace statsd {

TEST(MultiConditionTrigger, TestMultipleConditions) {
    int numConditions = 5;
    string t1 = "t1", t2 = "t2", t3 = "t3", t4 = "t4", t5 = "t5";
    set<string> conditionNames = {t1, t2, t3, t4, t5};

    mutex lock;
    condition_variable cv;
    bool triggerCalled = false;

    // Mark done as true and notify in the done.
    MultiConditionTrigger trigger(conditionNames, [&lock, &cv, &triggerCalled] {
        {
            lock_guard lg(lock);
            triggerCalled = true;
        }
        cv.notify_all();
    });

    vector<thread> threads;
    vector<int> done(numConditions, 0);

    int i = 0;
    for (const string& conditionName : conditionNames) {
        threads.emplace_back([&done, &conditionName, &trigger, i] {
            sleep_for(chrono::milliseconds(3));
            done[i] = 1;
            trigger.markComplete(conditionName);
        });
        i++;
    }

    unique_lock<mutex> unique_lk(lock);
    cv.wait(unique_lk, [&triggerCalled] {
        return triggerCalled;
    });

    for (i = 0; i < numConditions; i++) {
        EXPECT_EQ(done[i], 1);
    }

    for (i = 0; i < numConditions; i++) {
        threads[i].join();
    }
}

TEST(MultiConditionTrigger, TestNoConditions) {
    mutex lock;
    condition_variable cv;
    bool triggerCalled = false;

    MultiConditionTrigger trigger({}, [&lock, &cv, &triggerCalled] {
        {
            lock_guard lg(lock);
            triggerCalled = true;
        }
        cv.notify_all();
    });

    unique_lock<mutex> unique_lk(lock);
    cv.wait(unique_lk, [&triggerCalled] { return triggerCalled; });
    EXPECT_TRUE(triggerCalled);
    // Ensure that trigger occurs immediately if no events need to be completed.
}

TEST(MultiConditionTrigger, TestMarkCompleteCalledBySameCondition) {
    string t1 = "t1", t2 = "t2";
    set<string> conditionNames = {t1, t2};

    mutex lock;
    condition_variable cv;
    bool triggerCalled = false;

    MultiConditionTrigger trigger(conditionNames, [&lock, &cv, &triggerCalled] {
        {
            lock_guard lg(lock);
            triggerCalled = true;
        }
        cv.notify_all();
    });

    trigger.markComplete(t1);
    trigger.markComplete(t1);

    // Ensure that the trigger still hasn't fired.
    {
        lock_guard lg(lock);
        EXPECT_FALSE(triggerCalled);
    }

    trigger.markComplete(t2);
    unique_lock<mutex> unique_lk(lock);
    cv.wait(unique_lk, [&triggerCalled] { return triggerCalled; });
    EXPECT_TRUE(triggerCalled);
}

TEST(MultiConditionTrigger, TestTriggerOnlyCalledOnce) {
    string t1 = "t1";
    set<string> conditionNames = {t1};

    mutex lock;
    condition_variable cv;
    bool triggerCalled = false;
    int triggerCount = 0;

    MultiConditionTrigger trigger(conditionNames, [&lock, &cv, &triggerCalled, &triggerCount] {
        {
            lock_guard lg(lock);
            triggerCount++;
            triggerCalled = true;
        }
        cv.notify_all();
    });

    trigger.markComplete(t1);

    // Ensure that the trigger fired.
    {
        unique_lock<mutex> unique_lk(lock);
        cv.wait(unique_lk, [&triggerCalled] { return triggerCalled; });
        EXPECT_TRUE(triggerCalled);
        EXPECT_EQ(triggerCount, 1);
        triggerCalled = false;
    }

    trigger.markComplete(t1);

    // Ensure that the trigger does not fire again.
    {
        unique_lock<mutex> unique_lk(lock);
        cv.wait_for(unique_lk, chrono::milliseconds(5), [&triggerCalled] { return triggerCalled; });
        EXPECT_FALSE(triggerCalled);
        EXPECT_EQ(triggerCount, 1);
    }
}

namespace {

class TriggerDependency {
public:
    TriggerDependency(mutex& lock, condition_variable& cv, bool& triggerCalled, int& triggerCount)
        : mLock(lock), mCv(cv), mTriggerCalled(triggerCalled), mTriggerCount(triggerCount) {
    }

    void someMethod() {
        lock_guard lg(mLock);
        mTriggerCount++;
        mTriggerCalled = true;
        mCv.notify_all();
    }

private:
    mutex& mLock;
    condition_variable& mCv;
    bool& mTriggerCalled;
    int& mTriggerCount;
};

}  // namespace

TEST(MultiConditionTrigger, TestTriggerHasSleep) {
    const string t1 = "t1";
    set<string> conditionNames = {t1};

    mutex lock;
    condition_variable cv;
    bool triggerCalled = false;
    int triggerCount = 0;

    {
        TriggerDependency dependency(lock, cv, triggerCalled, triggerCount);
        MultiConditionTrigger trigger(conditionNames, [&dependency] {
            std::this_thread::sleep_for(std::chrono::milliseconds(50));
            dependency.someMethod();
        });
        trigger.markComplete(t1);

        // Here dependency instance will go out of scope and the thread within MultiConditionTrigger
        // after delay will try to call method of already destroyed class instance
        // with leading crash if trigger execution thread is detached in MultiConditionTrigger
        // Instead since the MultiConditionTrigger destructor happens before TriggerDependency
        // destructor, MultiConditionTrigger destructor is waiting on execution thread termination
        // with thread::join
    }
    // At this moment the executor thread guaranteed terminated by MultiConditionTrigger destructor

    // Ensure that the trigger fired.
    {
        unique_lock<mutex> unique_lk(lock);
        cv.wait(unique_lk, [&triggerCalled] { return triggerCalled; });
        EXPECT_TRUE(triggerCalled);
        EXPECT_EQ(triggerCount, 1);
    }
}

TEST(MultiConditionTrigger, TestTriggerHasSleepEarlyTermination) {
    const string t1 = "t1";
    set<string> conditionNames = {t1};

    mutex lock;
    condition_variable cv;
    bool triggerCalled = false;
    int triggerCount = 0;

    std::condition_variable triggerTerminationFlag;
    std::mutex triggerTerminationFlagMutex;
    bool terminationRequested = false;

    // used for error threshold tolerance due to wait_for() is involved
    const int64_t errorThresholdMs = 25;
    const int64_t triggerEarlyTerminationDelayMs = 100;
    const int64_t triggerStartNs = getElapsedRealtimeNs();
    {
        TriggerDependency dependency(lock, cv, triggerCalled, triggerCount);
        MultiConditionTrigger trigger(
                conditionNames, [&dependency, &triggerTerminationFlag, &triggerTerminationFlagMutex,
                                 &lock, &triggerCalled, &cv, &terminationRequested] {
                    std::unique_lock<std::mutex> lk(triggerTerminationFlagMutex);
                    if (triggerTerminationFlag.wait_for(
                                lk, std::chrono::seconds(1),
                                [&terminationRequested] { return terminationRequested; })) {
                        // triggerTerminationFlag was notified - early termination is requested
                        lock_guard lg(lock);
                        triggerCalled = true;
                        cv.notify_all();
                        return;
                    }
                    dependency.someMethod();
                });
        trigger.markComplete(t1);

        // notify to terminate trigger executor thread after triggerEarlyTerminationDelayMs
        std::this_thread::sleep_for(std::chrono::milliseconds(triggerEarlyTerminationDelayMs));
        {
            std::unique_lock<std::mutex> lk(triggerTerminationFlagMutex);
            terminationRequested = true;
        }
        triggerTerminationFlag.notify_all();
    }
    // At this moment the executor thread guaranteed terminated by MultiConditionTrigger destructor

    // check that test duration is closer to 100ms rather to 1s
    const int64_t triggerEndNs = getElapsedRealtimeNs();
    EXPECT_LE(NanoToMillis(triggerEndNs - triggerStartNs),
              triggerEarlyTerminationDelayMs + errorThresholdMs);

    // Ensure that the trigger fired but not the dependency.someMethod().
    {
        unique_lock<mutex> unique_lk(lock);
        cv.wait(unique_lk, [&triggerCalled] { return triggerCalled; });
        EXPECT_TRUE(triggerCalled);
        EXPECT_EQ(triggerCount, 0);
    }
}

}  // namespace statsd
}  // namespace os
}  // namespace android
#else
GTEST_LOG_(INFO) << "This test does nothing.\n";
#endif
