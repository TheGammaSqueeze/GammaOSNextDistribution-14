/*
 * Copyright (C) 2021 The Android Open Source Project
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

#include <android/choreographer.h>
#include <android/log.h>
#include <android/looper.h>
#include <android/trace.h>
#include <jni.h>
#include <jniAssert.h>
#include <sys/time.h>
#include <time.h>

#include <chrono>
#include <cinttypes>
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

using namespace std::chrono_literals;
static constexpr std::chrono::nanoseconds NOMINAL_VSYNC_PERIOD{16ms};
static constexpr std::chrono::nanoseconds DELAY_PERIOD{NOMINAL_VSYNC_PERIOD * 5};
static constexpr std::chrono::nanoseconds ZERO{std::chrono::nanoseconds::zero()};

#define NANOS_PER_SECOND 1000000000LL
static int64_t systemTime() {
    struct timespec time;
    int result = clock_gettime(CLOCK_MONOTONIC, &time);
    if (result < 0) {
        return -errno;
    }
    return (time.tv_sec * NANOS_PER_SECOND) + time.tv_nsec;
}

static std::mutex gLock;
struct Callback {
    Callback(const char* name) : name(name) {}
    std::string name;
    int count{0};
    std::chrono::nanoseconds frameTime{0LL};
};

struct VsyncCallback : Callback {
    VsyncCallback(const char* name, JNIEnv* env) : Callback(name), env(env) {}

    struct FrameTime {
        FrameTime(const AChoreographerFrameCallbackData* callbackData, int index)
              : vsyncId(AChoreographerFrameCallbackData_getFrameTimelineVsyncId(callbackData,
                                                                                index)),
                expectedPresentTime(
                        AChoreographerFrameCallbackData_getFrameTimelineExpectedPresentationTimeNanos(
                                callbackData, index)),
                deadline(AChoreographerFrameCallbackData_getFrameTimelineDeadlineNanos(callbackData,
                                                                                       index)) {}

        const AVsyncId vsyncId{-1};
        const int64_t expectedPresentTime{-1};
        const int64_t deadline{-1};
    };

    void populate(const AChoreographerFrameCallbackData* callbackData) {
        size_t index = AChoreographerFrameCallbackData_getPreferredFrameTimelineIndex(callbackData);
        preferredFrameTimelineIndex = index;
        size_t length = AChoreographerFrameCallbackData_getFrameTimelinesLength(callbackData);
        ASSERT(index < length, "Preferred frame timeline index out of bounds");
        timelines.reserve(length);

        for (int i = 0; i < length; i++) {
            timelines.push_back(FrameTime(callbackData, i));
        }
    }

    size_t getPreferredFrameTimelineIndex() const { return preferredFrameTimelineIndex; }
    const std::vector<FrameTime>& getTimeline() const { return timelines; }

private:
    JNIEnv* env;
    size_t preferredFrameTimelineIndex{std::numeric_limits<size_t>::max()};
    std::vector<FrameTime> timelines;
};

static void vsyncCallback(int64_t frameTimeNanos, void* data) {
    std::lock_guard<std::mutex> _l(gLock);
    ATrace_beginSection("vsyncCallback base");
    Callback* cb = static_cast<Callback*>(data);
    cb->count++;
    cb->frameTime = std::chrono::nanoseconds{frameTimeNanos};
    ATrace_endSection();
}

static void vsyncCallback(const AChoreographerFrameCallbackData* callbackData, void* data) {
    ATrace_beginSection("vsyncCallback");
    vsyncCallback(AChoreographerFrameCallbackData_getFrameTimeNanos(callbackData), data);

    VsyncCallback* cb = static_cast<VsyncCallback*>(data);
    cb->populate(callbackData);
    ATrace_endSection();
}

static void frameCallback64(int64_t frameTimeNanos, void* data) {
    vsyncCallback(frameTimeNanos, data);
}

static void frameCallback(long frameTimeNanos, void* data) {
    vsyncCallback((int64_t)frameTimeNanos, data);
}

static std::chrono::nanoseconds now() {
    return std::chrono::steady_clock::now().time_since_epoch();
}

static void verifyCallback(JNIEnv* env, const Callback& cb, int expectedCount,
                           std::chrono::nanoseconds startTime, std::chrono::nanoseconds maxTime) {
    std::lock_guard<std::mutex> _l{gLock};
    ASSERT(cb.count == expectedCount, "Choreographer failed to invoke '%s' %d times - actual: %d",
           cb.name.c_str(), expectedCount, cb.count);
    if (maxTime > ZERO) {
        ASSERT(cb.frameTime - startTime < maxTime,
               "Callback '%s' has incorrect frame time in invocation %d", cb.name.c_str(),
               expectedCount);
    }
}
