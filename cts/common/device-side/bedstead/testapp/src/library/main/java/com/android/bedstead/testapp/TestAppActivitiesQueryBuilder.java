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

import android.content.ComponentName;

import com.android.bedstead.nene.TestApis;
import com.android.queryable.Queryable;
import com.android.queryable.queries.ActivityQuery;
import com.android.queryable.queries.ActivityQueryHelper;

import java.util.Iterator;

/**
 * Builder for activity queries.
 */
public final class TestAppActivitiesQueryBuilder implements Queryable {

    private final TestAppActivities mTestAppActivities;

    private ActivityQueryHelper<TestAppActivitiesQueryBuilder> mActivity =
            new ActivityQueryHelper<>(this);

    TestAppActivitiesQueryBuilder(TestAppActivities testAppActivities) {
        mTestAppActivities = testAppActivities;
    }

    /** Query by activity. */
    public ActivityQuery<TestAppActivitiesQueryBuilder> whereActivity() {
        return mActivity;
    }

    /** Get the activity which matches the query. */
    public TestAppActivityReference get() {
        Iterator<com.android.queryable.info.ActivityInfo> activityIterator =
                mTestAppActivities.activities().iterator();

        while (activityIterator.hasNext()) {
            com.android.queryable.info.ActivityInfo activity = activityIterator.next();
            if (ActivityQueryHelper.matches(mActivity, activity)) {
                activityIterator.remove();
                return new UnresolvedTestAppActivity(mTestAppActivities.mInstance,
                        TestApis.packages().component(
                                new ComponentName(
                                        mTestAppActivities.mInstance.packageName(),
                                        activity.className())));
            }
        }

        throw new IllegalStateException(
                "No matching unused activity for query. Found activities: "
                        + mTestAppActivities.activities());
    }

    @Override
    public boolean isEmptyQuery() {
        return Queryable.isEmptyQuery(mActivity);
    }

    @Override
    public String describeQuery(String fieldName) {
        return "{" + Queryable.joinQueryStrings(
                mActivity.describeQuery("activity")
        ) + "}";
    }
}
