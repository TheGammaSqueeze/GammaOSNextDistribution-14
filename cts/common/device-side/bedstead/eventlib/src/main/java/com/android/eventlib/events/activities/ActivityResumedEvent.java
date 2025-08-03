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
 * Event logged when {@link Activity#onResume()}} is called.
 */
public final class ActivityResumedEvent extends Event {

    private static final long serialVersionUID = 1;

    /** Begins a query for {@link ActivityResumedEvent} events. */
    public static ActivityResumedEventQuery queryPackage(String packageName) {
        return new ActivityResumedEventQuery(packageName);
    }

    /** {@link EventLogsQuery} for {@link ActivityResumedEvent}. */
    public static final class ActivityResumedEventQuery
            extends EventLogsQuery<ActivityResumedEvent, ActivityResumedEventQuery> {

        private static final long serialVersionUID = 1;

        ActivityQueryHelper<ActivityResumedEventQuery> mActivity =
                new ActivityQueryHelper<>(this);
        IntegerQuery<ActivityResumedEventQuery> mTaskId = new IntegerQueryHelper<>(this);

        private ActivityResumedEventQuery(String packageName) {
            super(ActivityResumedEvent.class, packageName);
        }

        /** Query {@link Activity}. */
        @CheckResult
        public ActivityQuery<ActivityResumedEventQuery> whereActivity() {
            return mActivity;
        }

        /** Query {@code taskId}. */
        @CheckResult
        public IntegerQuery<ActivityResumedEventQuery> whereTaskId() {
            return mTaskId;
        }

        @Override
        protected boolean filter(ActivityResumedEvent event) {
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
            return toStringBuilder(ActivityResumedEvent.class, this)
                    .field("activity", mActivity)
                    .field("taskId", mTaskId)
                    .toString();
        }
    }

    /** Begins logging a {@link ActivityResumedEvent}. */
    public static ActivityResumedEventLogger logger(Activity activity, android.content.pm.ActivityInfo activityInfo) {
        return new ActivityResumedEventLogger(activity, activityInfo);
    }

    /** {@link EventLogger} for {@link ActivityResumedEvent}. */
    public static final class ActivityResumedEventLogger
            extends EventLogger<ActivityResumedEvent> {
        private ActivityResumedEventLogger(Activity activity, android.content.pm.ActivityInfo activityInfo) {
            super(activity, new ActivityResumedEvent());
            setActivity(activityInfo);
            setTaskId(activity.getTaskId());
        }

        /** Sets the {@link Activity} being resumed. */
        public ActivityResumedEventLogger setActivity(android.content.pm.ActivityInfo activity) {
            mEvent.mActivity = ActivityInfo.builder(activity).build();
            return this;
        }

        /** Sets the task ID for the activity. */
        public ActivityResumedEventLogger setTaskId(int taskId) {
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
        return "ActivityResumedEvent{"
                + ", activity=" + mActivity
                + ", taskId=" + mTaskId
                + ", packageName='" + mPackageName + "'"
                + ", timestamp=" + mTimestamp
                + "}";
    }
}
