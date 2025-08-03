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

import androidx.annotation.CheckResult;

import com.android.eventlib.Event;
import com.android.eventlib.EventLogger;
import com.android.eventlib.EventLogsQuery;
import com.android.queryable.info.ActivityInfo;
import com.android.queryable.queries.ActivityQuery;
import com.android.queryable.queries.ActivityQueryHelper;
import com.android.queryable.queries.IntegerQuery;
import com.android.queryable.queries.IntegerQueryHelper;

/**
 * Event logged when {@link Activity#onDestroy()} is called.
 */
public final class ActivityDestroyedEvent extends Event {

    private static final long serialVersionUID = 1;

    /** Begins a query for {@link ActivityDestroyedEvent} events. */
    public static ActivityDestroyedEventQuery queryPackage(String packageName) {
        return new ActivityDestroyedEventQuery(packageName);
    }

    /** {@link EventLogsQuery} for {@link ActivityDestroyedEvent}. */
    public static final class ActivityDestroyedEventQuery
            extends EventLogsQuery<ActivityDestroyedEvent, ActivityDestroyedEventQuery> {

        private static final long serialVersionUID = 1;

        ActivityQueryHelper<ActivityDestroyedEventQuery> mActivity =
                new ActivityQueryHelper<>(this);
        IntegerQuery<ActivityDestroyedEventQuery> mTaskId = new IntegerQueryHelper<>(this);

        private ActivityDestroyedEventQuery(String packageName) {
            super(ActivityDestroyedEvent.class, packageName);
        }

        /** Query {@link Activity}. */
        @CheckResult
        public ActivityQuery<ActivityDestroyedEventQuery> whereActivity() {
            return mActivity;
        }

        /** Query {@code taskId}. */
        @CheckResult
        public IntegerQuery<ActivityDestroyedEventQuery> whereTaskId() {
            return mTaskId;
        }

        @Override
        protected boolean filter(ActivityDestroyedEvent event) {
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
            return toStringBuilder(ActivityDestroyedEvent.class, this)
                    .field("activity", mActivity)
                    .field("taskId", mTaskId)
                    .toString();
        }
    }

    /** Begins logging a {@link ActivityDestroyedEvent}. */
    public static ActivityDestroyedEventLogger logger(Activity activity, android.content.pm.ActivityInfo activityInfo) {
        return new ActivityDestroyedEventLogger(activity, activityInfo);
    }

    /** {@link EventLogger} for {@link ActivityDestroyedEvent}. */
    public static final class ActivityDestroyedEventLogger
            extends EventLogger<ActivityDestroyedEvent> {
        private ActivityDestroyedEventLogger(Activity activity, android.content.pm.ActivityInfo activityInfo) {
            super(activity, new ActivityDestroyedEvent());
            setActivity(activityInfo);
            setTaskId(activity.getTaskId());
        }

        /** Sets the {@link Activity} being destroyed. */
        public ActivityDestroyedEventLogger setActivity(android.content.pm.ActivityInfo activity) {
            mEvent.mActivity = ActivityInfo.builder(activity).build();
            return this;
        }

        /** Sets the task ID for the activity. */
        public ActivityDestroyedEventLogger setTaskId(int taskId) {
            mEvent.mTaskId = taskId;
            return this;
        }
    }

    protected ActivityInfo mActivity;
    protected int mTaskId;

    /** Information about the {@link Activity} destroyed. */
    public ActivityInfo activity() {
        return mActivity;
    }

    /** The Task ID of the Activity. */
    public int taskId() {
        return mTaskId;
    }

    @Override
    public String toString() {
        return "ActivityDestroyedEvent{"
                + ", activity=" + mActivity
                + ", taskId=" + mTaskId
                + ", packageName='" + mPackageName + "'"
                + ", timestamp=" + mTimestamp
                + "}";
    }
}
