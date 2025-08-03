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

package android.server.wm.jetpack.embedding;

import static android.server.wm.activity.lifecycle.LifecycleConstants.ON_CREATE;
import static android.server.wm.activity.lifecycle.LifecycleConstants.ON_DESTROY;
import static android.server.wm.activity.lifecycle.LifecycleConstants.ON_PAUSE;
import static android.server.wm.activity.lifecycle.LifecycleConstants.ON_RESUME;
import static android.server.wm.activity.lifecycle.LifecycleConstants.ON_START;
import static android.server.wm.activity.lifecycle.LifecycleConstants.ON_STOP;
import static android.server.wm.activity.lifecycle.TransitionVerifier.transition;

import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.net.Uri;
import android.os.Bundle;
import android.server.wm.activity.lifecycle.EventLog;
import android.server.wm.activity.lifecycle.EventLog.EventLogClient;
import android.server.wm.activity.lifecycle.EventTracker;
import android.server.wm.jetpack.extensions.util.TestValueCountConsumer;

import org.junit.After;
import org.junit.Before;

/**
 * Base test class for the {@link androidx.window.extensions} implementation provided on the device
 * (and only if one is available) for the Activity Embedding functionality and Activity lifecycle
 * callbacks.
 */
public class ActivityEmbeddingLifecycleTestBase extends ActivityEmbeddingTestBase {
    protected static final String TEST_OWNER = "TEST_OWNER";
    protected static final String ON_SPLIT_STATES_UPDATED = "ON_SPLIT_STATES_UPDATED";

    protected EventLog mEventLog;
    protected EventTracker mLifecycleTracker;

    private EventLogClient mEventLogClient;
    private LifecycleCallbacks mLifecycleCallbacks;

    @Before
    @Override
    public void setUp() {
        super.setUp();
        mSplitInfoConsumer = new SplitInfoLifecycleConsumer<>();
        mActivityEmbeddingComponent.setSplitInfoCallback(mSplitInfoConsumer);

        mEventLogClient = EventLogClient.create(TEST_OWNER, mInstrumentation.getTargetContext(),
                Uri.parse("content://android.server.wm.jetpack.logprovider"));

        // Log transitions for all activities that belong to this app.
        mEventLog = new EventLog();
        mEventLog.clear();

        // Track transitions and allow waiting for pending activity states.
        mLifecycleTracker = new EventTracker(mEventLog);
        mLifecycleCallbacks = new LifecycleCallbacks();
        mApplication.registerActivityLifecycleCallbacks(mLifecycleCallbacks);
    }

    @After
    @Override
    public void tearDown() throws Throwable {
        super.tearDown();
        mApplication.unregisterActivityLifecycleCallbacks(mLifecycleCallbacks);
        if (mEventLogClient != null) {
            mEventLogClient.close();
        }
    }

    protected void waitAndAssertActivityOnStop(Class<? extends Activity> activityClass) {
        mLifecycleTracker.waitAndAssertActivityCurrentState(activityClass, ON_STOP);
    }

    protected void waitAndAssertActivityOnDestroy(Class<? extends Activity> activityClass) {
        mLifecycleTracker.waitAndAssertActivityCurrentState(activityClass, ON_DESTROY);
    }

    protected void waitAndAssertSplitStatesUpdated() {
        assertTrue("Split state change must be observed",
                mLifecycleTracker.waitForConditionWithTimeout(() -> mEventLog.getLog().contains(
                        transition(TEST_OWNER, ON_SPLIT_STATES_UPDATED))));
    }

    private final class LifecycleCallbacks implements
            Application.ActivityLifecycleCallbacks {
        @Override
        public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
            mEventLogClient.onCallback(ON_CREATE, activity);
        }

        @Override
        public void onActivityStarted(Activity activity) {
            mEventLogClient.onCallback(ON_START, activity);
        }

        @Override
        public void onActivityResumed(Activity activity) {
            mEventLogClient.onCallback(ON_RESUME, activity);
        }

        @Override
        public void onActivityPaused(Activity activity) {
            mEventLogClient.onCallback(ON_PAUSE, activity);
        }

        @Override
        public void onActivityStopped(Activity activity) {
            mEventLogClient.onCallback(ON_STOP, activity);
        }

        @Override
        public void onActivityDestroyed(Activity activity) {
            mEventLogClient.onCallback(ON_DESTROY, activity);
        }

        @Override
        public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
        }
    }

    private final class SplitInfoLifecycleConsumer<T> extends TestValueCountConsumer<T> {
        @Override
        public void accept(T value) {
            super.accept(value);
            mEventLogClient.onCallback(ON_SPLIT_STATES_UPDATED, TEST_OWNER);
        }
    }
}
