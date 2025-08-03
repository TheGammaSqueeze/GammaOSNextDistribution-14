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
package com.android.eventlib.events.activities

import android.content.ComponentName
import com.android.bedstead.nene.TestApis
import com.android.bedstead.nene.activities.Activity
import com.android.bedstead.nene.activities.NeneActivity
import com.android.bedstead.nene.users.UserReference
import com.android.eventlib.events.activities.ActivityCreatedEvent.ActivityCreatedEventQuery
import com.android.eventlib.events.activities.ActivityDestroyedEvent.ActivityDestroyedEventQuery
import com.android.eventlib.events.activities.ActivityPausedEvent.ActivityPausedEventQuery
import com.android.eventlib.events.activities.ActivityRestartedEvent.ActivityRestartedEventQuery
import com.android.eventlib.events.activities.ActivityResumedEvent.ActivityResumedEventQuery
import com.android.eventlib.events.activities.ActivityStartedEvent.ActivityStartedEventQuery
import com.android.eventlib.events.activities.ActivityStoppedEvent.ActivityStoppedEventQuery

/**
 * Quick access to event queries about activities.
 */
interface ActivityEvents {
    /**
     * Query for when an activity is created.
     *
     *
     * Additional filters can be added to the returned object.
     *
     *
     * `#poll` can be used to fetch results, and the result can be asserted on.
     */
    fun activityCreated(): ActivityCreatedEventQuery

    /**
     * Query for when an activity is destroyed.
     *
     *
     * Additional filters can be added to the returned object.
     *
     *
     * `#poll` can be used to fetch results, and the result can be asserted on.
     */
    fun activityDestroyed(): ActivityDestroyedEventQuery

    /**
     * Query for when an activity is paused.
     *
     *
     * Additional filters can be added to the returned object.
     *
     *
     * `#poll` can be used to fetch results, and the result can be asserted on.
     */
    fun activityPaused(): ActivityPausedEventQuery

    /**
     * Query for when an activity is restarted.
     *
     *
     * Additional filters can be added to the returned object.
     *
     *
     * `#poll` can be used to fetch results, and the result can be asserted on.
     */
    fun activityRestarted(): ActivityRestartedEventQuery

    /**
     * Query for when an activity is resumed.
     *
     *
     * Additional filters can be added to the returned object.
     *
     *
     * `#poll` can be used to fetch results, and the result can be asserted on.
     */
    fun activityResumed(): ActivityResumedEventQuery

    /**
     * Query for when an activity is started.
     *
     *
     * Additional filters can be added to the returned object.
     *
     *
     * `#poll` can be used to fetch results, and the result can be asserted on.
     */
    fun activityStarted(): ActivityStartedEventQuery

    /**
     * Query for when an activity is stopped.
     *
     *
     * Additional filters can be added to the returned object.
     *
     *
     * `#poll` can be used to fetch results, and the result can be asserted on.
     */
    fun activityStopped(): ActivityStoppedEventQuery

    companion object {
        /** Access events for activity.  */
        @JvmStatic
        fun forActivity(activity: NeneActivity): ActivityEvents =
            ActivityEventsImpl(activity)

        /** Access events for activity.  */
        @JvmStatic
        fun forActivity(activity: Activity<NeneActivity>): ActivityEvents =
            ActivityEventsImpl(activity.activity())

        /** Access events for activity.  */
        @JvmStatic
        @JvmOverloads
        fun forActivity(
            componentName: ComponentName,
            user: UserReference = TestApis.users().instrumented()
        ): ActivityEvents =
            ActivityEventsImpl(componentName, user.userHandle())
    }
}
