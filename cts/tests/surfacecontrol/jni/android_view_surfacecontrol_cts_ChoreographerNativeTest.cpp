/*
 * Copyright (C) 2016 The Android Open Source Project
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
 *
 */

#include <ChoreographerTestUtils.h>
#include <android/choreographer.h>
#include <android/looper.h>
#include <jni.h>
#include <sys/time.h>
#include <time.h>

#include <chrono>
#include <cmath>
#include <cstdlib>
#include <cstring>
#include <limits>
#include <mutex>
#include <set>
#include <sstream>
#include <string>
#include <thread>
#include <tuple>
#include <vector>

#define LOG_TAG "ChoreographerNativeTest"


using namespace std::chrono_literals;

struct {
    struct {
        jclass clazz;
        jmethodID checkRefreshRateIsCurrentAndSwitch;
    } choreographerNativeTest;
} gJni;

static std::set<int64_t> gSupportedRefreshPeriods;

struct RefreshRateCallback {
    RefreshRateCallback(const char* name): name(name) {}
    std::string name;
    int count{0};
    std::chrono::nanoseconds vsyncPeriod{0LL};
};

struct RefreshRateCallbackWithDisplayManager {
    RefreshRateCallbackWithDisplayManager(const char* name, JNIEnv* env, jobject clazz)
          : name(name), env(env), clazz(clazz) {}
    std::string name;
    JNIEnv* env;
    jobject clazz;
    int count{0};
    std::chrono::nanoseconds vsyncPeriod{0LL};
};

static void refreshRateCallback(int64_t vsyncPeriodNanos, void* data) {
    std::lock_guard<std::mutex> _l(gLock);
    RefreshRateCallback* cb = static_cast<RefreshRateCallback*>(data);
    cb->count++;
    cb->vsyncPeriod = std::chrono::nanoseconds{vsyncPeriodNanos};
}

static void refreshRateCallbackWithDisplayManager(int64_t vsyncPeriodNanos, void* data) {
    std::lock_guard<std::mutex> _l(gLock);
    RefreshRateCallbackWithDisplayManager* cb =
            static_cast<RefreshRateCallbackWithDisplayManager*>(data);
    cb->count++;
    cb->vsyncPeriod = std::chrono::nanoseconds{vsyncPeriodNanos};
    cb->env->CallVoidMethod(cb->clazz,
                            gJni.choreographerNativeTest.checkRefreshRateIsCurrentAndSwitch,
                            static_cast<int>(std::round(1e9f / cb->vsyncPeriod.count())));
}

static std::string dumpSupportedRefreshPeriods() {
    std::stringstream ss;
    ss << "{ ";
    for (const long& period : gSupportedRefreshPeriods) {
        ss << period << ",";
    }
    ss << "}";
    return ss.str();
}

template <class T>
static void verifyRefreshRateCallback(JNIEnv* env, const T& cb, int expectedMin) {
    std::lock_guard<std::mutex> _l(gLock);
    ASSERT(cb.count >= expectedMin, "Choreographer failed to invoke '%s' %d times - actual: %d",
           cb.name.c_str(), expectedMin, cb.count);
    // Unfortunately we can't verify the specific vsync period as public apis
    // don't provide a guarantee that we adhere to a particular refresh rate.
    // The best we can do is check that the reported period is contained in the
    // set of supported periods.
    ASSERT(cb.vsyncPeriod > ZERO,
           "Choreographer failed to report a nonzero refresh period invoking '%s'",
           cb.name.c_str());
    ASSERT(gSupportedRefreshPeriods.count(cb.vsyncPeriod.count()) > 0,
           "Choreographer failed to report a supported refresh period invoking '%s': supported "
           "periods: %s, actual: %lu",
           cb.name.c_str(), dumpSupportedRefreshPeriods().c_str(), cb.vsyncPeriod.count());
}

static void resetRefreshRateCallback(RefreshRateCallback& cb) {
    std::lock_guard<std::mutex> _l(gLock);
    cb.count = 0;
}

static jlong android_view_surfacecontrol_cts_ChoreographerNativeTest_getChoreographer(JNIEnv*, jclass) {
    std::lock_guard<std::mutex> _l{gLock};
    return reinterpret_cast<jlong>(AChoreographer_getInstance());
}

static jboolean android_view_surfacecontrol_cts_ChoreographerNativeTest_prepareChoreographerTests(JNIEnv* env, jclass,
        jlong choreographerPtr, jlongArray supportedRefreshPeriods) {
    std::lock_guard<std::mutex> _l{gLock};
    AChoreographer* choreographer = reinterpret_cast<AChoreographer*>(choreographerPtr);
    const size_t count = env->GetArrayLength(supportedRefreshPeriods);
    const jlong* vals = env->GetLongArrayElements(supportedRefreshPeriods, nullptr);
    for (size_t i = 0; i < count; ++i) {
        gSupportedRefreshPeriods.insert(vals[i]);
    }
    env->ReleaseLongArrayElements(supportedRefreshPeriods, const_cast<jlong*>(vals), JNI_ABORT);
    return choreographer != nullptr;
}

static void
android_view_surfacecontrol_cts_ChoreographerNativeTest_testPostVsyncCallbackWithoutDelayEventuallyRunsCallback(
        JNIEnv* env, jclass, jlong choreographerPtr) {
    AChoreographer* choreographer = reinterpret_cast<AChoreographer*>(choreographerPtr);
    VsyncCallback cb1("cb1", env);
    VsyncCallback cb2("cb2", env);
    auto start = now();

    AChoreographer_postVsyncCallback(choreographer, vsyncCallback, &cb1);
    AChoreographer_postVsyncCallback(choreographer, vsyncCallback, &cb2);
    std::this_thread::sleep_for(NOMINAL_VSYNC_PERIOD * 3);

    verifyCallback(env, cb1, 1, start, NOMINAL_VSYNC_PERIOD * 3);
    verifyCallback(env, cb2, 1, start, NOMINAL_VSYNC_PERIOD * 3);
    {
        std::lock_guard<std::mutex> _l{gLock};
        auto delta = cb2.frameTime - cb1.frameTime;
        ASSERT(delta == ZERO || delta > ZERO && delta < NOMINAL_VSYNC_PERIOD * 2,
               "Callback 1 and 2 have frame times too large of a delta in frame times");
    }

    AChoreographer_postVsyncCallback(choreographer, vsyncCallback, &cb1);
    start = now();
    std::this_thread::sleep_for(NOMINAL_VSYNC_PERIOD * 3);
    verifyCallback(env, cb1, 2, start, NOMINAL_VSYNC_PERIOD * 3);
    verifyCallback(env, cb2, 1, start, ZERO);
}

static void android_view_surfacecontrol_cts_ChoreographerNativeTest_testFrameCallbackDataVsyncIdValid(
        JNIEnv* env, jclass, jlong choreographerPtr) {
    AChoreographer* choreographer = reinterpret_cast<AChoreographer*>(choreographerPtr);
    VsyncCallback cb1("cb1", env);
    auto start = now();

    AChoreographer_postVsyncCallback(choreographer, vsyncCallback, &cb1);
    std::this_thread::sleep_for(NOMINAL_VSYNC_PERIOD * 3);

    verifyCallback(env, cb1, 1, start, NOMINAL_VSYNC_PERIOD * 3);
    std::lock_guard<std::mutex> _l{gLock};
    for (const VsyncCallback::FrameTime& frameTime : cb1.getTimeline()) {
        int64_t vsyncId = frameTime.vsyncId;
        ASSERT(vsyncId >= 0, "Invalid vsync ID");
        ASSERT(std::count_if(cb1.getTimeline().begin(), cb1.getTimeline().end(),
                             [vsyncId](const VsyncCallback::FrameTime& ft) {
                                 return ft.vsyncId == vsyncId;
                             }) == 1,
               "Vsync ID is not unique");
    }
}

static void android_view_surfacecontrol_cts_ChoreographerNativeTest_testFrameCallbackDataDeadlineInFuture(
        JNIEnv* env, jclass, jlong choreographerPtr) {
    AChoreographer* choreographer = reinterpret_cast<AChoreographer*>(choreographerPtr);
    VsyncCallback cb1("cb1", env);
    auto start = now();

    AChoreographer_postVsyncCallback(choreographer, vsyncCallback, &cb1);
    std::this_thread::sleep_for(NOMINAL_VSYNC_PERIOD * 3);

    verifyCallback(env, cb1, 1, start, NOMINAL_VSYNC_PERIOD * 3);
    std::lock_guard<std::mutex> _l{gLock};
    std::vector<VsyncCallback::FrameTime> frameTimelines = cb1.getTimeline();
    for (auto [i, lastValue] = std::tuple{0, cb1.frameTime}; i < frameTimelines.size(); i++) {
        auto deadline = std::chrono::nanoseconds{frameTimelines[i].deadline};
        ASSERT(deadline > std::chrono::nanoseconds{start}, "Deadline must be after start time");
        ASSERT(deadline > cb1.frameTime, "Deadline must be after frame time");
        ASSERT(deadline > lastValue, "Deadline must be greater than last frame deadline");
        lastValue = deadline;
    }
    // To avoid API fragmentation, enforce there are at least a certain amount of frame timeline
    // choices, by number of choices or by the last deadline timestamp.
    ASSERT(std::chrono::nanoseconds{frameTimelines[frameTimelines.size() - 1].deadline} >
           start + std::chrono::nanoseconds{45ms},
           "Not enough later choices for frame timelines");
}

static void
android_view_surfacecontrol_cts_ChoreographerNativeTest_testFrameCallbackDataExpectedPresentTimeInFuture(
        JNIEnv* env, jclass, jlong choreographerPtr) {
    AChoreographer* choreographer = reinterpret_cast<AChoreographer*>(choreographerPtr);
    VsyncCallback cb1("cb1", env);
    auto start = now();

    AChoreographer_postVsyncCallback(choreographer, vsyncCallback, &cb1);
    std::this_thread::sleep_for(NOMINAL_VSYNC_PERIOD * 3);

    verifyCallback(env, cb1, 1, start, NOMINAL_VSYNC_PERIOD * 3);
    std::lock_guard<std::mutex> _l{gLock};
    for (auto [i, lastValue] = std::tuple{0, cb1.frameTime}; i < cb1.getTimeline().size(); i++) {
        auto expectedPresentTime =
                std::chrono::nanoseconds(cb1.getTimeline()[i].expectedPresentTime);
        auto deadline = std::chrono::nanoseconds(cb1.getTimeline()[i].deadline);
        ASSERT(expectedPresentTime > cb1.frameTime,
               "Expected present time must be after frame time");
        ASSERT(expectedPresentTime > deadline, "Expected present time must be after deadline");
        ASSERT(expectedPresentTime > lastValue,
               "Expected present time must be greater than last frame expected present time");
        lastValue = expectedPresentTime;
    }
}

static void android_view_surfacecontrol_cts_ChoreographerNativeTest_testPostCallback64WithoutDelayEventuallyRunsCallback(
        JNIEnv* env, jclass, jlong choreographerPtr) {
    AChoreographer* choreographer = reinterpret_cast<AChoreographer*>(choreographerPtr);
    Callback cb1("cb1");
    Callback cb2("cb2");
    auto start = now();

    AChoreographer_postFrameCallback64(choreographer, frameCallback64, &cb1);
    AChoreographer_postFrameCallback64(choreographer, frameCallback64, &cb2);
    std::this_thread::sleep_for(NOMINAL_VSYNC_PERIOD * 3);

    verifyCallback(env, cb1, 1, start, NOMINAL_VSYNC_PERIOD * 3);
    verifyCallback(env, cb2, 1, start, NOMINAL_VSYNC_PERIOD * 3);
    {
        std::lock_guard<std::mutex> _l{gLock};
        auto delta = cb2.frameTime - cb1.frameTime;
        ASSERT(delta == ZERO || delta > ZERO && delta < NOMINAL_VSYNC_PERIOD * 2,
                "Callback 1 and 2 have frame times too large of a delta in frame times");
    }

    AChoreographer_postFrameCallback64(choreographer, frameCallback64, &cb1);
    start = now();
    std::this_thread::sleep_for(NOMINAL_VSYNC_PERIOD * 3);
    verifyCallback(env, cb1, 2, start, NOMINAL_VSYNC_PERIOD * 3);
    verifyCallback(env, cb2, 1, start, ZERO);
}

static void android_view_surfacecontrol_cts_ChoreographerNativeTest_testPostCallback64WithDelayEventuallyRunsCallback(
        JNIEnv* env, jclass, jlong choreographerPtr) {
    AChoreographer* choreographer = reinterpret_cast<AChoreographer*>(choreographerPtr);
    Callback cb1 = Callback("cb1");
    auto start = now();

    auto delay = std::chrono::duration_cast<std::chrono::milliseconds>(DELAY_PERIOD).count();
    AChoreographer_postFrameCallbackDelayed64(choreographer, frameCallback64, &cb1, delay);

    std::this_thread::sleep_for(NOMINAL_VSYNC_PERIOD * 3);
    verifyCallback(env, cb1, 0, start, ZERO);

    std::this_thread::sleep_for(DELAY_PERIOD);
    verifyCallback(env, cb1, 1, start, DELAY_PERIOD + NOMINAL_VSYNC_PERIOD * 3);
}

static void android_view_surfacecontrol_cts_ChoreographerNativeTest_testPostCallbackWithoutDelayEventuallyRunsCallback(
        JNIEnv* env, jclass, jlong choreographerPtr) {
    AChoreographer* choreographer = reinterpret_cast<AChoreographer*>(choreographerPtr);
    Callback cb1("cb1");
    Callback cb2("cb2");
    auto start = now();
    const auto delay = NOMINAL_VSYNC_PERIOD * 3;
    // Delay calculations are known to be broken on 32-bit systems (overflow),
    // so we skip testing the delay on such systems by setting this to ZERO.
    const auto delayToTest = sizeof(long) == sizeof(int64_t) ? delay : ZERO;

    AChoreographer_postFrameCallback(choreographer, frameCallback, &cb1);
    AChoreographer_postFrameCallback(choreographer, frameCallback, &cb2);
    std::this_thread::sleep_for(delay);

    verifyCallback(env, cb1, 1, start, delayToTest);
    verifyCallback(env, cb2, 1, start, delayToTest);

    // This delta can only be reliably calculated on 64-bit systems.  We skip this
    // part of the test on systems known to be broken.
    if (sizeof(long) == sizeof(int64_t)) {
        std::lock_guard<std::mutex> _l{gLock};
        auto delta = cb2.frameTime - cb1.frameTime;
        ASSERT(delta == ZERO || delta > ZERO && delta < NOMINAL_VSYNC_PERIOD * 2,
                "Callback 1 and 2 have frame times too large of a delta in frame times");
    }

    AChoreographer_postFrameCallback(choreographer, frameCallback, &cb1);
    start = now();
    std::this_thread::sleep_for(delay);

    verifyCallback(env, cb1, 2, start, delayToTest);
    verifyCallback(env, cb2, 1, start, ZERO);
}

static void android_view_surfacecontrol_cts_ChoreographerNativeTest_testPostCallbackWithDelayEventuallyRunsCallback(
        JNIEnv* env, jclass, jlong choreographerPtr) {
    if (sizeof(long) != sizeof(int64_t)) {
        // skip test for known broken states.
        return;
    }

    AChoreographer* choreographer = reinterpret_cast<AChoreographer*>(choreographerPtr);
    Callback cb1("cb1");
    auto start = now();

    auto delay = std::chrono::duration_cast<std::chrono::milliseconds>(DELAY_PERIOD).count();
    AChoreographer_postFrameCallbackDelayed(choreographer, frameCallback, &cb1, delay);

    std::this_thread::sleep_for(NOMINAL_VSYNC_PERIOD * 3);
    verifyCallback(env, cb1, 0, start, ZERO);

    std::this_thread::sleep_for(DELAY_PERIOD);
    const auto delayToTest =
            sizeof(long) == sizeof(int64_t) ? DELAY_PERIOD + NOMINAL_VSYNC_PERIOD * 3 : ZERO;
    verifyCallback(env, cb1, 1, start, delayToTest);
}

static void android_view_surfacecontrol_cts_ChoreographerNativeTest_testPostCallbackMixedWithoutDelayEventuallyRunsCallback(
        JNIEnv* env, jclass, jlong choreographerPtr) {
    AChoreographer* choreographer = reinterpret_cast<AChoreographer*>(choreographerPtr);
    Callback cb1("cb1");
    Callback cb64("cb64");
    auto start = now();

    AChoreographer_postFrameCallback(choreographer, frameCallback, &cb1);
    AChoreographer_postFrameCallback64(choreographer, frameCallback64, &cb64);
    std::this_thread::sleep_for(NOMINAL_VSYNC_PERIOD * 3);

    verifyCallback(env, cb1, 1, start, ZERO);
    verifyCallback(env, cb64, 1, start, NOMINAL_VSYNC_PERIOD * 3);

    // This delta can only be reliably calculated on 64-bit systems.  We skip this
    // part of the test on systems known to be broken.
    if (sizeof(long) == sizeof(int64_t)) {
        std::lock_guard<std::mutex> _l{gLock};
        auto delta = cb64.frameTime - cb1.frameTime;
        ASSERT(delta == ZERO || delta > ZERO && delta < NOMINAL_VSYNC_PERIOD * 2,
                "Callback 1 and 2 have frame times too large of a delta in frame times");
    }

    AChoreographer_postFrameCallback64(choreographer, frameCallback64, &cb64);
    start = now();
    std::this_thread::sleep_for(NOMINAL_VSYNC_PERIOD * 3);
    verifyCallback(env, cb1, 1, start, ZERO);
    verifyCallback(env, cb64, 2, start, NOMINAL_VSYNC_PERIOD * 3);
}

static void android_view_surfacecontrol_cts_ChoreographerNativeTest_testPostCallbackMixedWithDelayEventuallyRunsCallback(
        JNIEnv* env, jclass, jlong choreographerPtr) {
    AChoreographer* choreographer = reinterpret_cast<AChoreographer*>(choreographerPtr);
    Callback cb1("cb1");
    Callback cb64("cb64");
    auto start = now();

    auto delay = std::chrono::duration_cast<std::chrono::milliseconds>(DELAY_PERIOD).count();
    AChoreographer_postFrameCallbackDelayed(choreographer, frameCallback, &cb1, delay);
    AChoreographer_postFrameCallbackDelayed64(choreographer, frameCallback64, &cb64, delay);

    std::this_thread::sleep_for(NOMINAL_VSYNC_PERIOD * 3);
    verifyCallback(env, cb1, 0, start, ZERO);
    verifyCallback(env, cb64, 0, start, ZERO);

    std::this_thread::sleep_for(DELAY_PERIOD);
    verifyCallback(env, cb64, 1, start, DELAY_PERIOD + NOMINAL_VSYNC_PERIOD * 3);
    const auto delayToTestFor32Bit =
            sizeof(long) == sizeof(int64_t) ? DELAY_PERIOD + NOMINAL_VSYNC_PERIOD * 3 : ZERO;
    verifyCallback(env, cb1, 1, start, delayToTestFor32Bit);
}

static void android_view_surfacecontrol_cts_ChoreographerNativeTest_testRefreshRateCallback(
        JNIEnv* env, jclass, jlong choreographerPtr) {
    AChoreographer* choreographer = reinterpret_cast<AChoreographer*>(choreographerPtr);
    RefreshRateCallback cb("cb");

    AChoreographer_registerRefreshRateCallback(choreographer, refreshRateCallback, &cb);

    // Give the display system time to push an initial callback.
    std::this_thread::sleep_for(NOMINAL_VSYNC_PERIOD * 10);
    verifyRefreshRateCallback<RefreshRateCallback>(env, cb, 1);
    AChoreographer_unregisterRefreshRateCallback(choreographer, refreshRateCallback, &cb);
}

static void android_view_surfacecontrol_cts_ChoreographerNativeTest_testUnregisteringRefreshRateCallback(
        JNIEnv* env, jclass, jlong choreographerPtr) {
    AChoreographer* choreographer = reinterpret_cast<AChoreographer*>(choreographerPtr);
    RefreshRateCallback cb1("cb1");
    RefreshRateCallback cb2("cb2");

    AChoreographer_registerRefreshRateCallback(choreographer, refreshRateCallback, &cb1);

    // Give the display system time to push an initial callback.
    std::this_thread::sleep_for(NOMINAL_VSYNC_PERIOD * 10);
    verifyRefreshRateCallback<RefreshRateCallback>(env, cb1, 1);

    AChoreographer_unregisterRefreshRateCallback(choreographer, refreshRateCallback, &cb1);
    // Flush out pending callback events for the callback
    std::this_thread::sleep_for(NOMINAL_VSYNC_PERIOD * 10);
    resetRefreshRateCallback(cb1);

    AChoreographer_registerRefreshRateCallback(choreographer, refreshRateCallback, &cb2);
    // Verify that cb2 is called on registration, but not cb1.
    std::this_thread::sleep_for(NOMINAL_VSYNC_PERIOD * 10);
    verifyRefreshRateCallback<RefreshRateCallback>(env, cb1, 0);
    verifyRefreshRateCallback<RefreshRateCallback>(env, cb2, 1);
    AChoreographer_unregisterRefreshRateCallback(choreographer, refreshRateCallback, &cb2);
}

static void android_view_surfacecontrol_cts_ChoreographerNativeTest_testMultipleRefreshRateCallbacks(
        JNIEnv* env, jclass, jlong choreographerPtr) {
    AChoreographer* choreographer = reinterpret_cast<AChoreographer*>(choreographerPtr);
    RefreshRateCallback cb1("cb1");
    RefreshRateCallback cb2("cb2");

    AChoreographer_registerRefreshRateCallback(choreographer, refreshRateCallback, &cb1);
    AChoreographer_registerRefreshRateCallback(choreographer, refreshRateCallback, &cb2);

    // Give the display system time to push an initial refresh rate change.
    // Polling the event will allow both callbacks to be triggered.
    std::this_thread::sleep_for(NOMINAL_VSYNC_PERIOD * 10);
    verifyRefreshRateCallback<RefreshRateCallback>(env, cb1, 1);
    verifyRefreshRateCallback<RefreshRateCallback>(env, cb2, 1);

    AChoreographer_unregisterRefreshRateCallback(choreographer, refreshRateCallback, &cb1);
    AChoreographer_unregisterRefreshRateCallback(choreographer, refreshRateCallback, &cb2);
}

static void android_view_surfacecontrol_cts_ChoreographerNativeTest_testAttemptToAddRefreshRateCallbackTwiceDoesNotAddTwice(
        JNIEnv* env, jclass, jlong choreographerPtr) {
    AChoreographer* choreographer = reinterpret_cast<AChoreographer*>(choreographerPtr);
    RefreshRateCallback cb1("cb1");
    RefreshRateCallback cb2("cb2");

    AChoreographer_registerRefreshRateCallback(choreographer, refreshRateCallback, &cb1);
    AChoreographer_registerRefreshRateCallback(choreographer, refreshRateCallback, &cb1);

    // Give the display system time to push an initial callback.
    std::this_thread::sleep_for(NOMINAL_VSYNC_PERIOD * 10);
    verifyRefreshRateCallback<RefreshRateCallback>(env, cb1, 1);

    AChoreographer_unregisterRefreshRateCallback(choreographer, refreshRateCallback, &cb1);
    // Flush out pending callback events for the callback
    std::this_thread::sleep_for(NOMINAL_VSYNC_PERIOD * 10);
    resetRefreshRateCallback(cb1);

    AChoreographer_registerRefreshRateCallback(choreographer, refreshRateCallback, &cb2);
    // Verify that cb1 is not called again, even thiough it was registered once
    // and unregistered again
    std::this_thread::sleep_for(NOMINAL_VSYNC_PERIOD * 10);
    verifyRefreshRateCallback<RefreshRateCallback>(env, cb1, 0);
    AChoreographer_unregisterRefreshRateCallback(choreographer, refreshRateCallback, &cb2);
}

// This test must be run on the UI thread for fine-grained control of looper
// scheduling.
static void android_view_surfacecontrol_cts_ChoreographerNativeTest_testRefreshRateCallbackMixedWithFrameCallbacks(
        JNIEnv* env, jclass, jlong choreographerPtr) {
    AChoreographer* choreographer = reinterpret_cast<AChoreographer*>(choreographerPtr);
    RefreshRateCallback cb("cb");

    AChoreographer_registerRefreshRateCallback(choreographer, refreshRateCallback, &cb);

    Callback cb1("cb1");
    Callback cb64("cb64");
    auto start = now();

    auto vsyncPeriod = std::chrono::duration_cast<std::chrono::milliseconds>(
                           NOMINAL_VSYNC_PERIOD)
                           .count();
    auto delay = std::chrono::duration_cast<std::chrono::milliseconds>(DELAY_PERIOD).count();
    AChoreographer_postFrameCallbackDelayed(choreographer, frameCallback, &cb1, delay);
    AChoreographer_postFrameCallbackDelayed64(choreographer, frameCallback64, &cb64, delay);

    std::this_thread::sleep_for(DELAY_PERIOD + NOMINAL_VSYNC_PERIOD * 10);
    // Ensure that callbacks are seen by the looper instance at approximately
    // the same time, and provide enough time for the looper instance to process
    // the delayed callback and the requested vsync signal if needed.
    int pollResult;
    do {
        pollResult = ALooper_pollOnce(vsyncPeriod * 5, nullptr, nullptr, nullptr);
    } while (pollResult != ALOOPER_POLL_TIMEOUT && pollResult != ALOOPER_POLL_ERROR);
    verifyRefreshRateCallback<RefreshRateCallback>(env, cb, 1);
    verifyCallback(env, cb64, 1, start,
                   DELAY_PERIOD + NOMINAL_VSYNC_PERIOD * 15);
    const auto delayToTestFor32Bit =
        sizeof(long) == sizeof(int64_t)
            ? DELAY_PERIOD + NOMINAL_VSYNC_PERIOD * 15
            : ZERO;
    verifyCallback(env, cb1, 1, start, delayToTestFor32Bit);
    AChoreographer_unregisterRefreshRateCallback(choreographer, refreshRateCallback, &cb);
}

// This test cannot be run on the UI thread because it relies on callbacks to be dispatched on the
// application UI thread.
static void
android_view_surfacecontrol_cts_ChoreographerNativeTest_testRefreshRateCallbacksAreSyncedWithDisplayManager(
        JNIEnv* env, jobject clazz) {
    // Test harness choreographer is not on the main thread, so create a thread-local choreographer
    // instance.
    ALooper_prepare(0);
    AChoreographer* choreographer = AChoreographer_getInstance();
    RefreshRateCallbackWithDisplayManager cb("cb", env, clazz);

    AChoreographer_registerRefreshRateCallback(choreographer, refreshRateCallbackWithDisplayManager,
                                               &cb);

    auto delayPeriod = std::chrono::duration_cast<std::chrono::milliseconds>(DELAY_PERIOD).count();

    const size_t numRuns = 1000;
    int previousCount = 0;
    for (int i = 0; i < numRuns; ++i) {
        const size_t numTries = 5;
        for (int j = 0; j < numTries; j++) {
            // In theory we only need to poll once because the test harness configuration should
            // enforce that we won't get spurious callbacks. In practice, there may still be
            // spurious callbacks due to hotplug or other display events that aren't suppressed. So
            // we add some slack by retrying a few times, but we stop at the first refresh rate
            // callback (1) to keep the test runtime reasonably short, and (2) to keep the test
            // under better control so that it does not spam the system with refresh rate changes.
            int result = ALooper_pollOnce(delayPeriod * 5, nullptr, nullptr, nullptr);
            ASSERT(result == ALOOPER_POLL_CALLBACK, "Callback failed on run: %d with error: %d", i,
                   result);
            if (previousCount != cb.count) {
                verifyRefreshRateCallback<RefreshRateCallbackWithDisplayManager>(env, cb,
                                                                                 previousCount + 1);
                previousCount = cb.count;
                break;
            }

            ASSERT(j < numTries - 1, "No callback observed for run: %d", i);
        }
    }
    AChoreographer_unregisterRefreshRateCallback(choreographer, refreshRateCallback, &cb);
}

static JNINativeMethod gMethods[] = {
        {"nativeGetChoreographer", "()J",
         (void*)android_view_surfacecontrol_cts_ChoreographerNativeTest_getChoreographer},
        {"nativePrepareChoreographerTests", "(J[J)Z",
         (void*)android_view_surfacecontrol_cts_ChoreographerNativeTest_prepareChoreographerTests},
        {"nativeTestPostVsyncCallbackWithoutDelayEventuallyRunsCallbacks", "(J)V",
         (void*)android_view_surfacecontrol_cts_ChoreographerNativeTest_testPostVsyncCallbackWithoutDelayEventuallyRunsCallback},
        {"nativeTestFrameCallbackDataVsyncIdValid", "(J)V",
         (void*)android_view_surfacecontrol_cts_ChoreographerNativeTest_testFrameCallbackDataVsyncIdValid},
        {"nativeTestFrameCallbackDataDeadlineInFuture", "(J)V",
         (void*)android_view_surfacecontrol_cts_ChoreographerNativeTest_testFrameCallbackDataDeadlineInFuture},
        {"nativeTestFrameCallbackDataExpectedPresentTimeInFuture", "(J)V",
         (void*)android_view_surfacecontrol_cts_ChoreographerNativeTest_testFrameCallbackDataExpectedPresentTimeInFuture},
        {"nativeTestPostCallback64WithoutDelayEventuallyRunsCallbacks", "(J)V",
         (void*)android_view_surfacecontrol_cts_ChoreographerNativeTest_testPostCallback64WithoutDelayEventuallyRunsCallback},
        {"nativeTestPostCallback64WithDelayEventuallyRunsCallbacks", "(J)V",
         (void*)android_view_surfacecontrol_cts_ChoreographerNativeTest_testPostCallback64WithDelayEventuallyRunsCallback},
        {"nativeTestPostCallbackWithoutDelayEventuallyRunsCallbacks", "(J)V",
         (void*)android_view_surfacecontrol_cts_ChoreographerNativeTest_testPostCallbackWithoutDelayEventuallyRunsCallback},
        {"nativeTestPostCallbackWithDelayEventuallyRunsCallbacks", "(J)V",
         (void*)android_view_surfacecontrol_cts_ChoreographerNativeTest_testPostCallbackWithDelayEventuallyRunsCallback},
        {"nativeTestPostCallbackMixedWithoutDelayEventuallyRunsCallbacks", "(J)V",
         (void*)android_view_surfacecontrol_cts_ChoreographerNativeTest_testPostCallbackMixedWithoutDelayEventuallyRunsCallback},
        {"nativeTestPostCallbackMixedWithDelayEventuallyRunsCallbacks", "(J)V",
         (void*)android_view_surfacecontrol_cts_ChoreographerNativeTest_testPostCallbackMixedWithDelayEventuallyRunsCallback},
        {"nativeTestRefreshRateCallback", "(J)V",
         (void*)android_view_surfacecontrol_cts_ChoreographerNativeTest_testRefreshRateCallback},
        {"nativeTestUnregisteringRefreshRateCallback", "(J)V",
         (void*)android_view_surfacecontrol_cts_ChoreographerNativeTest_testUnregisteringRefreshRateCallback},
        {"nativeTestMultipleRefreshRateCallbacks", "(J)V",
         (void*)android_view_surfacecontrol_cts_ChoreographerNativeTest_testMultipleRefreshRateCallbacks},
        {"nativeTestAttemptToAddRefreshRateCallbackTwiceDoesNotAddTwice", "(J)V",
         (void*)android_view_surfacecontrol_cts_ChoreographerNativeTest_testAttemptToAddRefreshRateCallbackTwiceDoesNotAddTwice},
        {"nativeTestRefreshRateCallbackMixedWithFrameCallbacks", "(J)V",
         (void*)android_view_surfacecontrol_cts_ChoreographerNativeTest_testRefreshRateCallbackMixedWithFrameCallbacks},
        {"nativeTestRefreshRateCallbacksAreSyncedWithDisplayManager", "()V",
         (void*)android_view_surfacecontrol_cts_ChoreographerNativeTest_testRefreshRateCallbacksAreSyncedWithDisplayManager},
};

int register_android_view_surfacecontrol_cts_ChoreographerNativeTest(JNIEnv* env)
{
    jclass clazz = env->FindClass("android/view/surfacecontrol/cts/ChoreographerNativeTest");
    gJni.choreographerNativeTest.clazz = static_cast<jclass>(env->NewGlobalRef(clazz));
    gJni.choreographerNativeTest.checkRefreshRateIsCurrentAndSwitch =
            env->GetMethodID(clazz, "checkRefreshRateIsCurrentAndSwitch", "(I)V");
    return env->RegisterNatives(clazz, gMethods,
            sizeof(gMethods) / sizeof(JNINativeMethod));
}
