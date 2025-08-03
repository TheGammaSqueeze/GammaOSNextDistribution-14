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

package com.android.bedstead.testapp;

import com.android.queryable.info.ActivityInfo;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Entry point to activity querying.
 */
public final class TestAppActivities {

    final TestAppInstance mInstance;
    private Set<ActivityInfo> mActivities = null;

    static TestAppActivities create(TestAppInstance instance) {
        TestAppActivities activities = new TestAppActivities(instance);
        return activities;
    }


    private TestAppActivities(TestAppInstance instance) {
        mInstance = instance;
    }

    Set<ActivityInfo> activities() {
        if (mActivities != null) {
            return mActivities;
        }

        mActivities = new HashSet<>(
                mInstance.testApp().mDetails.mActivities.stream().filter(
                        m -> !m.className().startsWith("androidx")).collect(
                Collectors.toSet()));
        return mActivities;
    }

    /**
     * Return any activity included in the test app Manifest.
     *
     * <p>Currently, this will always return the same activity.
     */
    public TestAppActivityReference any() {
        return query().get();
    }

    /**
     * Query for an Activity.
     */
    public TestAppActivitiesQueryBuilder query() {
        return new TestAppActivitiesQueryBuilder(this);
    }
}
