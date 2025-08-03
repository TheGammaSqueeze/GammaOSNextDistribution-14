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
 */

package android.platform.test.scenario;

import android.os.SystemClock;
import android.platform.test.rule.TestWatcher;
import android.util.Log;

import org.junit.runner.Description;

/** This rule will sleep for a given amount of time at the end of each test method. */
public class SleepAtTestFinishRule extends TestWatcher {
    private static final String LOG_TAG = SleepAtTestFinishRule.class.getSimpleName();

    private final long mMillis;

    public SleepAtTestFinishRule(long millis) {
        mMillis = millis;
    }

    @Override
    protected void finished(Description description) {
        Log.v(LOG_TAG, String.format("Sleeping for %d ms", mMillis));
        SystemClock.sleep(mMillis);
        Log.v(LOG_TAG, String.format("Done sleeping for %d ms", mMillis));
    }
}
