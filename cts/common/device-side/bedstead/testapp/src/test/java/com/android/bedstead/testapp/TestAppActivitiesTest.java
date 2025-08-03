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

import static com.android.queryable.queries.ActivityQuery.activity;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.users.UserReference;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public final class TestAppActivitiesTest {

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();
    private static final UserReference sUser = TestApis.users().instrumented();

    private static final String EXISTING_ACTIVITY = "android.testapp.activity";
    private static final String NON_EXISTING_ACTIVITY = "non.existing.activity";

    private static final TestApp sTestApp = sDeviceState.testApps().query()
            .whereActivities().contains(
                    activity().where().activityClass().className().isEqualTo(EXISTING_ACTIVITY)
            ).whereActivities().doesNotContain(
                    activity().where().activityClass().className().isEqualTo(NON_EXISTING_ACTIVITY)
            )
            .get();
    private static TestAppInstance sTestAppInstance;

    @Before
    public void setup() {
        sTestAppInstance = sTestApp.install(sUser);
    }

    @After
    public void teardown() {
        sTestAppInstance.uninstall();
    }

    @Test
    public void query_matchesActivity_returnsActivity() {
        TestAppActivityReference activity = sTestAppInstance.activities().query()
                    .whereActivity().activityClass().className().isEqualTo(EXISTING_ACTIVITY)
                    .get();

        assertThat(activity).isNotNull();
    }

    @Test
    public void query_matchesPreviouslyReturnedActivity_throwsException() {
        sTestAppInstance.activities().query()
                .whereActivity().activityClass().className().isEqualTo(EXISTING_ACTIVITY)
                .get();

        assertThrows(IllegalStateException.class, () ->
                sTestAppInstance.activities().query()
                        .whereActivity().activityClass().className().isEqualTo(EXISTING_ACTIVITY)
                        .get());
    }

    @Test
    public void query_doesNotMatchActivity_throwsException() {
        assertThrows(IllegalStateException.class, () ->
                sTestAppInstance.activities().query()
                        .whereActivity().activityClass().className()
                            .isEqualTo(NON_EXISTING_ACTIVITY)
                        .get());
    }

    @Test
    public void any_returnsActivity() {
        TestAppActivityReference activity = sTestAppInstance.activities().any();

        assertThat(activity).isNotNull();
    }

    @Test
    public void query_matchesActivityPreviouslyReturnedByDifferentInstance_returnsActivity() {
        sTestAppInstance.activities().query()
                .whereActivity().activityClass().className().isEqualTo(EXISTING_ACTIVITY)
                .get();

        TestAppInstance testAppInstance2 = sTestApp.instance(sUser);

        assertThat(
                testAppInstance2.activities().query()
                        .whereActivity().activityClass().className().isEqualTo(EXISTING_ACTIVITY)
                        .get()).isNotNull();
    }

    @Test
    public void queryBuilderIsEmpty_isEmpty_returnsTrue() {
        TestAppActivitiesQueryBuilder queryBuilder = sTestAppInstance.activities().query();

        assertThat(queryBuilder.isEmptyQuery()).isTrue();
    }

    @Test
    public void queryBuilderIsEmpty_hasActivityQuery_returnsFalse() {
        TestAppActivitiesQueryBuilder queryBuilder = sTestAppInstance.activities().query();

        queryBuilder.whereActivity().activityClass().className().isNull();

        assertThat(queryBuilder.isEmptyQuery()).isFalse();
    }
}
