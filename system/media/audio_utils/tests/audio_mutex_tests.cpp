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

#include <audio_utils/mutex.h>
#include <gtest/gtest.h>

#include <thread>

// Currently tests mutex priority-inheritance (or not) based on flag
// adb shell setprop \
// persist.device_config.aconfig_flags.media_audio.\
// com.android.media.audio.flags.mutex_priority_inheritance true
//
// TODO(b/209491695) enable both PI/non-PI mutex tests regardless of flag.

namespace android {

namespace audio_locks {

// Audio capabilities are typically assigned a partial order -
// but to furnish a proof of deadlock free audio, we create a global order
// (which isn't unique, just used to prove deadlock free access).

// Clang thread-safety can assigns capabilities to mutex-like objects.
// Here we use pointers to audio_utils::mutex*, they can be nullptr (never allocated)
// because they are used solely as a capability declaration, not as a mutex
// instance.
//
// In this example, we have 4 capabilities, labeled cap1, cap2, cap3, cap4.
// We'll show later in the IContainer.h example how we assign
// capabilities to actual mutexes.

// Add highest priority here
inline audio_utils::mutex* cap1;
inline audio_utils::mutex* cap2 ACQUIRED_AFTER(cap1);
inline audio_utils::mutex* cap3 ACQUIRED_AFTER(cap2);
inline audio_utils::mutex* cap4 ACQUIRED_AFTER(cap3);
// Add lowest priority here

// Now ACQUIRED_AFTER() isn't implemented (yet) in Clang Thread-Safety.
// As a solution, we define priority exclusion for lock acquisition.
// if you lock at a capability n, then you cannot hold
// locks at a lower capability (n+1, n+2, ...) otherwise
// there is lock inversion.

#define EXCLUDES_BELOW_4  // no capability below 4.
#define EXCLUDES_4 EXCLUDES(cap4) EXCLUDES_BELOW_4

#define EXCLUDES_BELOW_3 EXCLUDES_4
#define EXCLUDES_3 EXCLUDES(cap3) EXCLUDES_BELOW_3

#define EXCLUDES_BELOW_2 EXCLUDES_3
#define EXCLUDES_2 EXCLUDES(cap2) EXCLUDES_BELOW_2

#define EXCLUDES_BELOW_1 EXCLUDES_2
#define EXCLUDES_1 EXCLUDES(cap1) EXCLUDES_BELOW_1

#define EXCLUDES_ALL EXCLUDES_1

}  // namespace audio_locks

using namespace audio_locks;
// -----------------------------------------

// Example IContainer.h
//
// This is a Container interface with multiple exposed mutexes.
// Since the capabilities file audio_locks.h is global,
// this interface can be used in projects outside of the implementing
// project.

// Here RETURN_CAPABILITY is used to assign a capability to a mutex.

class IContainer {
public:
    virtual ~IContainer() = default;

    // This is an example of returning many mutexes for test purposes.
    // In AudioFlinger interfaces, we may return mutexes for locking
    // AudioFlinger_Mutex, AudioFlinger_ClientMutex, ThreadBase_Mutex, etc. for interface methods.
    //
    // We do not allow access to the mutex when holding a mutex of lower priority, so
    // we use EXCLUDES_....
    virtual audio_utils::mutex& mutex1() const RETURN_CAPABILITY(cap1) EXCLUDES_BELOW_1 = 0;
    virtual audio_utils::mutex& mutex2() const RETURN_CAPABILITY(cap2) EXCLUDES_BELOW_2 = 0;
    virtual audio_utils::mutex& mutex3() const RETURN_CAPABILITY(cap3) EXCLUDES_BELOW_3 = 0;

    // acquires capability 1, can't hold cap1 or lower level locks
    virtual int value1() const EXCLUDES_1 = 0;

    virtual int value1_l() const REQUIRES(cap1) = 0;
    virtual int value2_l() const REQUIRES(cap2) = 0;
    virtual int value3_l() const REQUIRES(cap3) = 0;

    virtual int combo12_l() const REQUIRES(cap1) EXCLUDES_2 = 0;

    // As value1, value2, value3 access requires cap1, cap2, cap3 individually,
    // combo123_lll() requires all three.
    virtual int combo123_lll() const REQUIRES(cap1, cap2, cap3) = 0;

    // We cannot use any of mutex1, mutex2, mutex3, as they are acquired
    // within the combo123() method.
    // If we happen to know EXCLUDES_1 > EXCLUDES2 > EXCLUDES_3
    // we could just use EXCLUDES_1.  We include all 3 here.
    virtual int combo123() const EXCLUDES_1 EXCLUDES_2 EXCLUDES_3 = 0;
};

// -----------------------------------------
//
// Example Container.cpp
//
// The container implemented the IContainer interface.
//
// We see here how the RETURN_CAPABILITY() is used to assign capability to mutexes.

class Container : public IContainer {
public:
    // Here we implement the mutexes that has the global capabilities.
    audio_utils::mutex& mutex1() const override RETURN_CAPABILITY(cap1) EXCLUDES_BELOW_1 {
        return mMutex1;
    }

    audio_utils::mutex& mutex2() const override RETURN_CAPABILITY(cap2) EXCLUDES_BELOW_2 {
        return mMutex2;
    }

    audio_utils::mutex& mutex3() const override RETURN_CAPABILITY(cap3) EXCLUDES_BELOW_3 {
        return mMutex3;
    }

    // We use EXCLUDES_1 (which is prevents cap1 and all lesser priority mutexes)
    // EXCLUDES(cap1) is not sufficient because if we acquire cap1,
    // we ALSO can't hold lower level locks.
    int value1() const override EXCLUDES_1 {

        // Locking is through mutex1() to get the proper capability.
        audio_utils::lock_guard l(mutex1());
        return value1_l();
    }
    int value1_l() const override REQUIRES(cap1) {
        return v1_;
    }
    int value2_l() const override REQUIRES(cap2) {
        return v2_;
    }
    int value3_l() const override REQUIRES(cap3)  {
        return v3_;
    }

    // This requires cap1, acquires cap2.
    int combo12_l() const override REQUIRES(cap1) EXCLUDES_2 {

        // Fails  return value1_l() + value2_l() + value3_l();
        audio_utils::lock_guard l(mutex2());
        return value1_l() + value2_l();
    }

#if 0
    // fails!
    int combo123_lll() REQUIRES(cap1, cap2, cap2) {
        return value1_l() + value2_l() + value3_l();
    }
#endif

    // We can use REQUIRES(cap1, cap2, cap3)
    //         or REQUIRES(cap1, cap2, mutex3())
    //
    // The REQUIRES expression must be valid as the first line in the function body.
    // This means that if the expression has visibility issues (accesses
    // a private base class member from a derived class, etc.) it won't work.
    // Quick test: Does a decltype(expr) work as the first line?
    // If not, then REQUIRES(expr) won't either.
    //
    int combo123_lll() const override REQUIRES(cap1, cap2, mutex3()) {
        return value1_l() + value2_l() + value3_l();
    }

#if 1
    // In this example, the 3 exclusions are needed because we acquire
    // 3 locks.  If we happen to know EXCLUDES_1 > EXCLUDES2 > EXCLUDES_3
    // we could just use EXCLUDES_1.  We include all 3 here.
    int combo123() const override EXCLUDES_1 EXCLUDES_2 EXCLUDES_3 {
        audio_utils::lock_guard l1(mutex1());
        audio_utils::lock_guard l2(mutex2());
        audio_utils::lock_guard l3(mutex3());

        return value1_l() + value2_l() + value3_l();
    }

#else
    // THIS FAILS
    int combo123() const override EXCLUDES_1 EXCLUDES_2 EXCLUDES_3 {
        audio_utils::lock_guard l2(mutex2());
        audio_utils::lock_guard l1(mutex1());  // 2 is acquired before 1.
        audio_utils::lock_guard l3(mutex3());

        return value1_l() + value2_l() + value3_l();
    }
#endif

private:
    // The actual implementation mutexes are never directly exposed.
    mutable audio_utils::mutex mMutex1;
    mutable audio_utils::mutex mMutex2;
    mutable audio_utils::mutex mMutex3;

    int v1_ GUARDED_BY(cap1) = 1;
    int v2_ GUARDED_BY(mutex2()) = 2;
    int v3_ GUARDED_BY(cap3) = 3;
};

TEST(audio_mutex_tests, Container) {
    Container c;

    EXPECT_EQ(1, c.value1()); // success

#if 0
    // fails
    {
        audio_utils::lock_guard(c.mutex1());
        EXPECT_EQ(3, c.combo12_l());
    }
#endif

    {
        audio_utils::lock_guard l(c.mutex1());
        EXPECT_EQ(3, c.combo12_l()); // success
    }
}

// Test access through the IContainer interface -
// see that mutex checking is done without knowledge of
// the actual implementation.

TEST(audio_mutex_tests, Interface) {
    Container c;
    IContainer *i = static_cast<IContainer*>(&c);

    EXPECT_EQ(1, i->value1()); // success

#if 0
    // fails
    {
        audio_utils::lock_guard(c.mutex1());
        EXPECT_EQ(3, c.combo12_l());
    }
#endif

    {
        audio_utils::lock_guard l(i->mutex1());
        EXPECT_EQ(3, i->combo12_l()); // success
    }

    ALOGD("%s: %s", __func__, audio_utils::mutex::all_stats_to_string().c_str());
}

TEST(audio_mutex_tests, Stack) {
    android::audio_utils::atomic_stack<int, int, 2> as;

    // set up stack
    EXPECT_EQ(0UL, as.size());
    as.push(1, 10);
    EXPECT_EQ(1UL, as.size());
    as.push(2, 20);
    EXPECT_EQ(2UL, as.size());

    // 3rd item exceeds the stack capacity.
    as.push(3, 30);
    // 2 items tracked (subset)
    EXPECT_EQ(2UL, as.size());
    // 3 items total.
    EXPECT_EQ(3UL, as.true_size());

    const auto& bot = as.bottom();
    const auto& top = as.top();

    // these are the 2 items tracked:
    EXPECT_EQ(1, bot.first.load());
    EXPECT_EQ(10, bot.second.load());

    EXPECT_EQ(3, top.first.load());
    EXPECT_EQ(30, top.second.load());

    // remove the bottom item.
    EXPECT_EQ(true, as.remove(1));
    EXPECT_EQ(1UL, as.size());
    EXPECT_EQ(2UL, as.true_size());

    // now remove the "virtual" item.
    // (actually any non-existing item value works).
    EXPECT_EQ(true, as.remove(2));
    EXPECT_EQ(1UL, as.size());
    EXPECT_EQ(1UL, as.true_size());

    // now an invalid removal
    EXPECT_EQ(false, as.remove(5));
    EXPECT_EQ(1UL, as.size());
    EXPECT_EQ(1UL, as.true_size());

    // now remove the final item.
    EXPECT_EQ(true, as.remove(3));
    EXPECT_EQ(0UL, as.size());
    EXPECT_EQ(0UL, as.true_size());
}

TEST(audio_mutex_tests, RecursiveLockDetection) {
    constexpr pid_t pid = 0;  // avoid registry shutdown.
    android::audio_utils::thread_mutex_info<int, int, 8 /* stack depth */> tmi(pid);

    // set up stack
    tmi.push_held(50, 1);
    tmi.push_held(40, 2);
    tmi.push_held(30, 3);
    EXPECT_EQ(3UL, tmi.stack().size());

    // remove bottom.
    tmi.remove_held(50);
    EXPECT_EQ(2UL, tmi.stack().size());

    // test recursive lock detection.

    // same order, same item is recursive.
    const auto& recursive = tmi.check_held(30, 3);
    EXPECT_EQ(30, recursive.first.load());
    EXPECT_EQ(3, recursive.second.load());

    // same order but different item (10 != 30) is OK.
    const auto& nil = tmi.check_held(10, 3);
    EXPECT_EQ(0, nil.first.load());
    EXPECT_EQ(0, nil.second.load());
}

TEST(audio_mutex_tests, OrderDetection) {
    constexpr pid_t pid = 0;  // avoid registry shutdown.
    android::audio_utils::thread_mutex_info<int, int, 8 /* stack depth */> tmi(pid);

    // set up stack
    tmi.push_held(50, 1);
    tmi.push_held(40, 2);
    tmi.push_held(30, 3);
    EXPECT_EQ(3UL, tmi.stack().size());

    // remove middle
    tmi.remove_held(40);
    EXPECT_EQ(2UL, tmi.stack().size());

    // test inversion detection.

    // lower order is a problem 1 < 3.
    const auto& inversion = tmi.check_held(1, 1);
    EXPECT_EQ(30, inversion.first.load());
    EXPECT_EQ(3, inversion.second.load());

    // higher order is OK.
    const auto& nil2 = tmi.check_held(4, 4);
    EXPECT_EQ(0, nil2.first.load());
    EXPECT_EQ(0, nil2.second.load());
}

// The following tests are evaluated for the android::audio_utils::mutex
// Non-Priority Inheritance and Priority Inheritance cases.

class MutexTestSuite : public testing::TestWithParam<bool> {};

// Test that the mutex aborts on recursion (if the abort flag is set).

TEST_P(MutexTestSuite, FatalRecursiveMutex)
NO_THREAD_SAFETY_ANALYSIS {
    if (!android::audio_utils::AudioMutexAttributes::abort_on_recursion_check_
            || !audio_utils::mutex_get_enable_flag()) {
        ALOGD("Test FatalRecursiveMutex skipped");
        return;
    }

    using Mutex = android::audio_utils::mutex;
    using LockGuard = android::audio_utils::lock_guard;
    const bool priority_inheritance = GetParam();

    Mutex m(priority_inheritance);
    LockGuard lg(m);

    // Can't lock ourselves again.
    ASSERT_DEATH(m.lock(), ".*recursive mutex.*");
}

// Test that the mutex aborts on lock order inversion (if the abort flag is set).

TEST_P(MutexTestSuite, FatalLockOrder)
NO_THREAD_SAFETY_ANALYSIS {
    if (!android::audio_utils::AudioMutexAttributes::abort_on_order_check_
            || !audio_utils::mutex_get_enable_flag()) {
        ALOGD("Test FatalLockOrder skipped");
        return;
    }

    using Mutex = android::audio_utils::mutex;
    using LockGuard = android::audio_utils::lock_guard;
    const bool priority_inheritance = GetParam();

    Mutex m1{priority_inheritance, (android::audio_utils::AudioMutexAttributes::order_t)1};
    Mutex m2{priority_inheritance, (android::audio_utils::AudioMutexAttributes::order_t)2};

    LockGuard lg2(m2);
    // m1 must be locked before m2 as 1 < 2.
    ASSERT_DEATH(m1.lock(), ".*mutex order.*");
}

// Test that the mutex aborts on lock order inversion (if the abort flag is set).

TEST_P(MutexTestSuite, UnexpectedUnlock)
NO_THREAD_SAFETY_ANALYSIS {
    if (!android::audio_utils::AudioMutexAttributes::abort_on_invalid_unlock_
            || !audio_utils::mutex_get_enable_flag()) {
        ALOGD("Test UnexpectedUnlock skipped");
        return;
    }

    using Mutex = android::audio_utils::mutex;
    const bool priority_inheritance = GetParam();

    Mutex m1{priority_inheritance, (android::audio_utils::AudioMutexAttributes::order_t)1};
    ASSERT_DEATH(m1.unlock(), ".*mutex unlock.*");
}

TEST_P(MutexTestSuite, TimedLock) {
    using ConditionVariable = android::audio_utils::condition_variable;
    using Mutex = android::audio_utils::mutex;
    using UniqueLock = android::audio_utils::unique_lock;
    const bool priority_inheritance = GetParam();

    Mutex m{priority_inheritance}, m1{priority_inheritance};
    ConditionVariable cv;
    bool quit = false;  // GUARDED_BY(m)

    std::atomic<pid_t> tid1{};

    // launch thread.
    std::thread t1([&]() {
        UniqueLock ul1(m1);
        UniqueLock ul(m);
        tid1 = android::audio_utils::gettid_wrapper();
        while (!quit) {
            cv.wait(ul, [&]{ return quit; });
            if (quit) break;
        }
    });

    // ensure thread tid1 has acquired all locks.
    while (tid1 == 0) { usleep(1000); }

    // try lock for 1s
    constexpr int64_t kTimeoutNs = 1'000'000'000;
    {
        //  verify timed out state.
        const int64_t beginNs = systemTime();
        const bool success = m1.try_lock(kTimeoutNs);
        const int64_t endNs = systemTime();
        const int64_t diffNs = endNs - beginNs;

        if (success) m1.unlock();
        EXPECT_GT(diffNs, kTimeoutNs);
        EXPECT_FALSE(success);
    }

    // exit the thread
    {
        UniqueLock ul(m);

        quit = true;
        cv.notify_one();
    }

    t1.join();

    {
        // verify success state.
        const int64_t beginNs = systemTime();
        const bool success = m1.try_lock(kTimeoutNs);
        const int64_t endNs = systemTime();
        const int64_t diffNs = endNs - beginNs;

        if (success) m1.unlock();
        constexpr int64_t kSuccessLockNs = kTimeoutNs / 4;
        EXPECT_LT(diffNs, kSuccessLockNs);
        EXPECT_TRUE(success);
    }
}

// Test the deadlock detection algorithm for a single wait chain
// (no cycle).

TEST_P(MutexTestSuite, DeadlockDetection) {
    using Mutex = android::audio_utils::mutex;
    using UniqueLock = android::audio_utils::unique_lock;
    using ConditionVariable = android::audio_utils::condition_variable;
    const bool priority_inheritance = GetParam();

    // order checked below.
    constexpr size_t kOrder1 = 1;
    constexpr size_t kOrder2 = 2;
    constexpr size_t kOrder3 = 3;
    static_assert(Mutex::attributes_t::order_size_ > kOrder3);

    Mutex m1{priority_inheritance, static_cast<Mutex::attributes_t::order_t>(kOrder1)};
    Mutex m2{priority_inheritance, static_cast<Mutex::attributes_t::order_t>(kOrder2)};
    Mutex m3{priority_inheritance, static_cast<Mutex::attributes_t::order_t>(kOrder3)};
    Mutex m4{priority_inheritance};
    Mutex m{priority_inheritance};
    ConditionVariable cv;
    bool quit = false;  // GUARDED_BY(m)
    std::atomic<pid_t> tid1{}, tid2{}, tid3{}, tid4{};

    std::thread t4([&]() {
        UniqueLock ul4(m4);
        UniqueLock ul(m);
        tid4 = android::audio_utils::gettid_wrapper();
        while (!quit) {
            cv.wait(ul, [&]{ return quit; });
            if (quit) break;
        }
    });

    while (tid4 == 0) { usleep(1000); }

    std::thread t3([&]() {
        UniqueLock ul3(m3);
        tid3 = android::audio_utils::gettid_wrapper();
        UniqueLock ul4(m4);
    });

    while (tid3 == 0) { usleep(1000); }

    std::thread t2([&]() {
        UniqueLock ul2(m2);
        tid2 = android::audio_utils::gettid_wrapper();
        UniqueLock ul3(m3);
    });

    while (tid2 == 0) { usleep(1000); }

    std::thread t1([&]() {
        UniqueLock ul1(m1);
        tid1 = android::audio_utils::gettid_wrapper();
        UniqueLock ul2(m2);
    });

    while (tid1 == 0) { usleep(1000); }

    // we know that the threads will now block in the proper order.
    // however, we need to wait for the block to happen.
    // this part is racy unless we check the thread state or use
    // futexes directly in our mutex (which allows atomic accuracy of wait).
    usleep(20000);

    const auto deadlockInfo = android::audio_utils::mutex::deadlock_detection(tid1);

    // no cycle.
    EXPECT_EQ(false, deadlockInfo.has_cycle);

    // no condition_variable detected
    EXPECT_EQ(false, deadlockInfo.cv_detected);

    // thread1 is waiting on a chain of 3 other threads.
    const auto chain = deadlockInfo.chain;
    const size_t chain_size = chain.size();
    EXPECT_EQ(3u, chain_size);

    const auto default_idx = static_cast<size_t>(Mutex::attributes_t::order_default_);
    if (chain_size > 0) {
        EXPECT_EQ(tid2, chain[0].first);
        EXPECT_EQ(Mutex::attributes_t::order_names_[kOrder2], chain[0].second);
    }
    if (chain_size > 1) {
        EXPECT_EQ(tid3, chain[1].first);
        EXPECT_EQ(Mutex::attributes_t::order_names_[kOrder3], chain[1].second);
    }
    if (chain_size > 2) {
        EXPECT_EQ(tid4, chain[2].first);
        EXPECT_EQ(Mutex::attributes_t::order_names_[default_idx], chain[2].second);
    }

    ALOGD("%s", android::audio_utils::mutex::all_threads_to_string().c_str());

    {
        UniqueLock ul(m);

        quit = true;
        cv.notify_one();
    }

    t4.join();
    t3.join();
    t2.join();
    t1.join();
}

TEST_P(MutexTestSuite, DeadlockConditionVariableDetection) {
    using Mutex = android::audio_utils::mutex;
    using UniqueLock = android::audio_utils::unique_lock;
    using ConditionVariable = android::audio_utils::condition_variable;
    const bool priority_inheritance = GetParam();

    // order checked below.
    constexpr size_t kOrder1 = 1;
    constexpr size_t kOrder2 = 2;
    constexpr size_t kOrder3 = 3;
    static_assert(Mutex::attributes_t::order_size_ > kOrder3);

    Mutex m1{priority_inheritance, static_cast<Mutex::attributes_t::order_t>(kOrder1)};
    Mutex m2{priority_inheritance, static_cast<Mutex::attributes_t::order_t>(kOrder2)};
    Mutex m3{priority_inheritance, static_cast<Mutex::attributes_t::order_t>(kOrder3)};
    Mutex m4{priority_inheritance};
    Mutex m{priority_inheritance};
    ConditionVariable cv, cv2, cv4;
    bool quit = false;  // GUARDED_BY(m)
    std::atomic<pid_t> tid1{}, tid2{}, tid3{}, tid4{};

    std::thread t4([&]() {
        // UniqueLock ul4(m4);
        UniqueLock ul(m);
        tid4 = android::audio_utils::gettid_wrapper();
        while (!quit) {
            cv.wait(ul, [&]{ return quit; });
            if (quit) break;
        }
    });

    while (tid4 == 0) { usleep(1000); }

    std::thread t3([&]() {
        UniqueLock ul3(m3);
        tid3 = android::audio_utils::gettid_wrapper();
        UniqueLock ul4(m4);
        while (!quit) {
            // use the wait method with the notifier_tid metadata.
            cv4.wait(ul4, [&]{ return quit; }, tid4);
            if (quit) break;
        }
    });

    while (tid3 == 0) { usleep(1000); }

    std::thread t2([&]() {
        // UniqueLock ul2(m2);
        tid2 = android::audio_utils::gettid_wrapper();
        UniqueLock ul3(m3);
    });

    while (tid2 == 0) { usleep(1000); }

    std::thread t1([&]() {
        UniqueLock ul1(m1);
        tid1 = android::audio_utils::gettid_wrapper();
        UniqueLock ul2(m2);
        while (!quit) {
            // use the wait method with the notifier_tid metadata.
            cv2.wait(ul2, [&]{ return quit; }, tid2);
            if (quit) break;
        }
    });

    while (tid1 == 0) { usleep(1000); }

    // we know that the threads will now block in the proper order.
    // however, we need to wait for the block to happen.
    // this part is racy unless we check the thread state or use
    // futexes directly in our mutex (which allows atomic accuracy of wait).
    usleep(20000);

    const auto deadlockInfo = android::audio_utils::mutex::deadlock_detection(tid1);

    // no cycle.
    EXPECT_EQ(false, deadlockInfo.has_cycle);

    // condition_variable detected
    EXPECT_EQ(true, deadlockInfo.cv_detected);

    // thread1 is waiting on a chain of 3 other threads.
    const auto chain = deadlockInfo.chain;
    const size_t chain_size = chain.size();
    EXPECT_EQ(3u, chain_size);

    const auto default_idx = static_cast<size_t>(Mutex::attributes_t::order_default_);
    if (chain_size > 0) {
        EXPECT_EQ(tid2, chain[0].first);
        EXPECT_EQ(std::string("cv-").append(Mutex::attributes_t::order_names_[kOrder2]).c_str(),
                chain[0].second);
    }
    if (chain_size > 1) {
        EXPECT_EQ(tid3, chain[1].first);
        EXPECT_EQ(Mutex::attributes_t::order_names_[kOrder3], chain[1].second);
    }
    if (chain_size > 2) {
        EXPECT_EQ(tid4, chain[2].first);
        EXPECT_EQ(std::string("cv-").append(
                Mutex::attributes_t::order_names_[default_idx]).c_str(), chain[2].second);
    }

    ALOGD("%s", android::audio_utils::mutex::all_threads_to_string().c_str());

    {
        UniqueLock ul(m);
        quit = true;
        cv.notify_one();
    }
    {
        UniqueLock ul2(m);
        cv2.notify_one();
    }
    {
        UniqueLock ul4(m);
        cv4.notify_one();
    }

    t4.join();
    t3.join();
    t2.join();
    t1.join();
}

INSTANTIATE_TEST_SUITE_P(Mutex_NonPI_and_PI, MutexTestSuite, testing::Values(false, true));

} // namespace android
