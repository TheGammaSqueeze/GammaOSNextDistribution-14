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

package com.android.eventlib.events.activities;

import android.app.Activity;
import android.os.Bundle;
import android.os.PersistableBundle;

import androidx.annotation.CheckResult;

import com.android.eventlib.Event;
import com.android.eventlib.EventLogger;
import com.android.eventlib.EventLogsQuery;
import com.android.queryable.info.ActivityInfo;
import com.android.queryable.queries.ActivityQuery;
import com.android.queryable.queries.ActivityQueryHelper;
import com.android.queryable.queries.BundleQueryHelper;
import com.android.queryable.queries.IntegerQuery;
import com.android.queryable.queries.IntegerQueryHelper;
import com.android.queryable.queries.PersistableBundleQuery;
import com.android.queryable.queries.PersistableBundleQueryHelper;
import com.android.queryable.util.SerializableParcelWrapper;

/**
 * Event logged when {@link Activity#onCreate(Bundle)} or
 * {@link Activity#onCreate(Bundle, PersistableBundle)} is called.
 */
public final class ActivityCreatedEvent extends Event {

    private static final long serialVersionUID = 1;

    /** Begins a query for {@link ActivityCreatedEvent} events. */
    public static ActivityCreatedEventQuery queryPackage(String packageName) {
        return new ActivityCreatedEventQuery(packageName);
    }

    /** {@link EventLogsQuery} for {@link ActivityCreatedEvent}. */
    public static final class ActivityCreatedEventQuery
            extends EventLogsQuery<ActivityCreatedEvent, ActivityCreatedEventQuery> {

        private static final long serialVersionUID = 1;

        ActivityQueryHelper<ActivityCreatedEventQuery> mActivity = new ActivityQueryHelper<>(this);
        BundleQueryHelper<ActivityCreatedEventQuery> mSavedInstanceState =
                new BundleQueryHelper<>(this);
        PersistableBundleQueryHelper<ActivityCreatedEventQuery> mPersistentState =
                new PersistableBundleQueryHelper<>(this);
        IntegerQuery<ActivityCreatedEventQuery> mTaskId = new IntegerQueryHelper<>(this);

        private ActivityCreatedEventQuery(String packageName) {
            super(ActivityCreatedEvent.class, packageName);
        }

        /**
         * Query {@code savedInstanceState} {@link Bundle} passed into
         * {@link Activity#onCreate(Bundle)} or
         * {@link Activity#onCreate(Bundle, PersistableBundle)}.
         */
        @CheckResult
        public BundleQueryHelper<ActivityCreatedEventQuery> whereSavedInstanceState() {
            return mSavedInstanceState;
        }

        /**
         * Query {@code persistentState} {@link PersistableBundle} passed into
         * {@link Activity#onCreate(Bundle, PersistableBundle)}.
         */
        @CheckResult
        public PersistableBundleQuery<ActivityCreatedEventQuery> wherePersistentState() {
            return mPersistentState;
        }

        /** Query {@link Activity}. */
        @CheckResult
        public ActivityQuery<ActivityCreatedEventQuery> whereActivity() {
            return mActivity;
        }

        /** Query {@code taskId}. */
        @CheckResult
        public IntegerQuery<ActivityCreatedEventQuery> whereTaskId() {
            return mTaskId;
        }

        @Override
        protected boolean filter(ActivityCreatedEvent event) {
            if (!mSavedInstanceState.matches(event.mSavedInstanceState)) {
                return false;
            }
            if (!mPersistentState.matches(event.mPersistentState)) {
                return false;
            }
            if (!mActivity.matches(event.mActivity)) {
                return false;
            }
            if (!mTaskId.matches(event.mTaskId)) {
                return false;
            }
            return true;
        }

        @Override
        public String describeQuery(String fieldName) {
            return toStringBuilder(ActivityCreatedEvent.class, this)
                    .field("savedInstanceState", mSavedInstanceState)
                    .field("persistentState", mPersistentState)
                    .field("activity", mActivity)
                    .field("taskId", mTaskId)
                    .toString();
        }
    }

    /** Begins logging a {@link ActivityCreatedEvent}. */
    public static ActivityCreatedEventLogger logger(Activity activity, android.content.pm.ActivityInfo activityInfo, Bundle savedInstanceState) {
        return new ActivityCreatedEventLogger(activity, activityInfo, savedInstanceState);
    }

    /** {@link EventLogger} for {@link ActivityCreatedEvent}. */
    public static final class ActivityCreatedEventLogger extends EventLogger<ActivityCreatedEvent> {
        private ActivityCreatedEventLogger(Activity activity, android.content.pm.ActivityInfo activityInfo, Bundle savedInstanceState) {
            super(activity, new ActivityCreatedEvent());
            setSavedInstanceState(savedInstanceState);
            setTaskId(activity.getTaskId());
            setActivity(activityInfo);
        }

        public ActivityCreatedEventLogger setActivity(android.content.pm.ActivityInfo activityInfo) {
            mEvent.mActivity = ActivityInfo.builder()
                    .activityClass(activityInfo.name)
                    .exported(activityInfo.exported)
                    .build();
            return this;
        }

        public ActivityCreatedEventLogger setSavedInstanceState(Bundle savedInstanceState) {
            mEvent.mSavedInstanceState = new SerializableParcelWrapper<>(savedInstanceState);
            return this;
        }

        public ActivityCreatedEventLogger setPersistentState(PersistableBundle persistentState) {
            mEvent.mPersistentState = new SerializableParcelWrapper<>(persistentState);
            return this;
        }

        /** Sets the task ID for the activity. */
        public ActivityCreatedEventLogger setTaskId(int taskId) {
            mEvent.mTaskId = taskId;
            return this;
        }
    }

    protected SerializableParcelWrapper<Bundle> mSavedInstanceState;
    protected SerializableParcelWrapper<PersistableBundle> mPersistentState;
    protected ActivityInfo mActivity;
    protected int mTaskId;

    /**
     * The {@code savedInstanceState} {@link Bundle} passed into
     * {@link Activity#onCreate(Bundle)} or
     * {@link Activity#onCreate(Bundle, PersistableBundle)}.
     */
    public Bundle savedInstanceState() {
        if (mSavedInstanceState == null) {
            return null;
        }
        return mSavedInstanceState.get();
    }

    /**
     * The {@code persistentState} {@link PersistableBundle} passed into
     * {@link Activity#onCreate(Bundle, PersistableBundle)}.
     */
    public PersistableBundle persistentState() {
        if (mPersistentState == null) {
            return null;
        }
        return mPersistentState.get();
    }

    /** Information about the {@link Activity} started. */
    public ActivityInfo activity() {
        return mActivity;
    }

    /** The Task ID of the Activity. */
    public int taskId() {
        return mTaskId;
    }

    @Override
    public String toString() {
        return "ActivityCreatedEvent{"
                + " savedInstanceState=" + savedInstanceState()
                + ", persistentState=" + persistentState()
                + ", activity=" + mActivity
                + ", taskId=" + mTaskId
                + ", packageName='" + mPackageName + "'"
                + ", timestamp=" + mTimestamp
                + "}";
    }
}
