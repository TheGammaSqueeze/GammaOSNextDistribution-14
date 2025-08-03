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

package com.android.bedstead.nene.activities;

import static android.app.ActivityManager.LOCK_TASK_MODE_NONE;

import android.content.Intent;
import android.util.Log;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.annotations.Experimental;
import com.android.bedstead.nene.packages.ComponentReference;
import com.android.bedstead.nene.utils.Poll;

/**
 * A wrapper around a specific Activity instance.
 */
@Experimental
public class Activity<E> {

    private static final String TAG = "BedsteadActivity";

    /*
     * Note that methods in this class must not rely on the activity existing within the current
     * process. These APIs must support activities which exist in any process (and they can be
     * interacted with using the {@link NeneActivity} interface.
     *
     * If an API is only suitable for activies in the current process, that API should be added to
     * {@link LocalActivity}.
     */

    private final E mActivityInstance;
    private final NeneActivity mActivity;

    Activity(E activityInstance, NeneActivity activity) {
        mActivityInstance = activityInstance;
        mActivity = activity;
    }

    /**
     * Calls {@link android.app.Activity#startLockTask()} and blocks until the activity has entered
     * lock task mode.
     */
    @Experimental
    public void startLockTask() {
        Log.d(TAG, "startLockTask() on " + mActivity);
        mActivity.startLockTask();

        // TODO(scottjonathan): What if we're already in lock task mode when we start it here?
        //  find another thing to poll on
        Poll.forValue("Lock task mode state", () -> TestApis.activities().getLockTaskModeState())
                .toNotBeEqualTo(LOCK_TASK_MODE_NONE)
                .errorOnFail()
                .await();
    }

    /**
     * Calls {@link android.app.Activity#stopLockTask()} and blocks until the activity has exited
     * lock task mode.
     */
    @Experimental
    public void stopLockTask() {
        Log.d(TAG, "stopLockTask() on " + mActivity);
        mActivity.stopLockTask();

        // TODO(scottjonathan): What if we're already in lock task mode when we start it here?
        //  find another thing to poll
        Poll.forValue("Lock task mode state", () -> TestApis.activities().getLockTaskModeState())
                .toBeEqualTo(LOCK_TASK_MODE_NONE)
                .errorOnFail()
                .await();
    }

    /**
     * Calls {@link android.app.Activity#startActivity(Intent)} and blocks until the activity has
     * started.
     *
     * <p>If a specific component is specified, this will block until that component is in the
     * foreground. Otherwise, it will block only until the foreground activity has changed.
     */
    @Experimental
    public void startActivity(Intent intent) {
        Log.d(TAG, "startActivity(): " + intent);
        if (intent.getComponent() == null) {
            ComponentReference startActivity = TestApis.activities().foregroundActivity();
            mActivity.startActivity(intent);
            Poll.forValue("Foreground activity", () -> TestApis.activities().foregroundActivity())
                    .toNotBeEqualTo(startActivity)
                    .errorOnFail()
                    .await();
        } else {
            mActivity.startActivity(intent);
            ComponentReference component = new ComponentReference(intent.getComponent());
            Poll.forValue("Foreground activity", () -> TestApis.activities().foregroundActivity())
                    .toBeEqualTo(component)
                    .errorOnFail()
                    .await();
        }
    }

    /**
     * Gets the original activity to make calls on directly.
     */
    public E activity() {
        return mActivityInstance;
    }
}
