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

package com.android.eventlib.premade;

import android.app.Activity;
import android.content.ComponentName;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.PersistableBundle;

import com.android.eventlib.events.activities.ActivityCreatedEvent;
import com.android.eventlib.events.activities.ActivityDestroyedEvent;
import com.android.eventlib.events.activities.ActivityPausedEvent;
import com.android.eventlib.events.activities.ActivityRestartedEvent;
import com.android.eventlib.events.activities.ActivityResumedEvent;
import com.android.eventlib.events.activities.ActivityStartedEvent;
import com.android.eventlib.events.activities.ActivityStoppedEvent;

/**
 * An {@link Activity} which logs events for all lifecycle events.
 */
public class EventLibActivity extends Activity {
    private String mOverrideActivityClassName;

    public void setOverrideActivityClassName(String overrideActivityClassName) {
        mOverrideActivityClassName = overrideActivityClassName;
    }

    /**
     * Gets the class name of this activity.
     *
     * <p>If the class name has been overridden, that will be returned instead.
     */
    public String getClassName() {
        if (mOverrideActivityClassName != null) {
            return mOverrideActivityClassName;
        }

        return EventLibActivity.class.getName();
    }

    public ComponentName getComponentName() {
        return new ComponentName(getApplication().getPackageName(), getClassName());
    }

    /** Log a {@link ActivityCreatedEvent}. */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        logOnCreate(savedInstanceState, /* persistentState= */ null);
    }

    /** Log a {@link ActivityCreatedEvent}. */
    @Override
    public void onCreate(Bundle savedInstanceState, PersistableBundle persistentState) {
        super.onCreate(savedInstanceState, persistentState);
        logOnCreate(savedInstanceState, persistentState);
    }

    private ActivityInfo mActivityInfo = null;

    private ActivityInfo activityInfo() {
        if (mActivityInfo != null) {
            return mActivityInfo;
        }

        PackageManager packageManager = getPackageManager();
        try {
            mActivityInfo = packageManager.getActivityInfo(getComponentName(), /* flags= */ 0);
        } catch (PackageManager.NameNotFoundException e) {
            throw new AssertionError("Cannot find activity", e);
        }

        return mActivityInfo;
    }

    private void logOnCreate(Bundle savedInstanceState, PersistableBundle persistentState) {
        ActivityCreatedEvent.logger(this, activityInfo(), savedInstanceState)
                .setPersistentState(persistentState)
                .log();
    }

    /** Log a {@link ActivityStartedEvent}. */
    @Override
    protected void onStart() {
        super.onStart();
        ActivityStartedEvent.logger(this, activityInfo()).log();
    }

    /** Log a {@link ActivityRestartedEvent}. */
    @Override
    protected void onRestart() {
        super.onRestart();
        ActivityRestartedEvent.logger(this, activityInfo()).log();
    }

    /** Log a {@link ActivityResumedEvent}. */
    @Override
    protected void onResume() {
        super.onResume();
        ActivityResumedEvent.logger(this, activityInfo()).log();
    }

    /** Log a {@link ActivityPausedEvent}. */
    @Override
    protected void onPause() {
        super.onPause();
        ActivityPausedEvent.logger(this, activityInfo()).log();
    }

    /** Log a {@link ActivityStoppedEvent}. */
    @Override
    protected void onStop() {
        super.onStop();
        ActivityStoppedEvent.logger(this, activityInfo()).log();
    }

    /** Log a {@link ActivityDestroyedEvent}. */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        ActivityDestroyedEvent.logger(this, activityInfo())
                .log();
    }
}
