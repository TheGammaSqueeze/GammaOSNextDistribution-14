/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.adservices.service.profiling;

/** Profiler class for timing/benchmarking classes. */
public class Profiler {
    private final String mTag;
    private boolean mIsTestEnv;

    /** For use in test environments. */
    public static Profiler createInstance(String tag) {
        return new Profiler(tag, false);
    }

    /** For use in non-test environments. */
    public static Profiler createNoOpInstance(String tag) {
        return new Profiler(tag, true);
    }

    private Profiler(String tag, boolean isTestEnv) {
        mTag = tag;
        mIsTestEnv = isTestEnv;
    }

    /** @param name name of the metric being measured */
    public StopWatch start(String name) {
        return mIsTestEnv ? new FakeStopWatch() : new LogcatStopWatch(mTag, name);
    }
}
