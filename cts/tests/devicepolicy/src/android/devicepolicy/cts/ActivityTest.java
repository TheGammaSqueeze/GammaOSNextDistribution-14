/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.devicepolicy.cts;

import static com.android.bedstead.nene.permissions.CommonPermissions.INTERACT_ACROSS_USERS;
import static com.android.bedstead.nene.permissions.CommonPermissions.INTERACT_ACROSS_USERS_FULL;
import static com.android.eventlib.truth.EventLogsSubject.assertThat;
import static com.android.queryable.queries.ActivityQuery.activity;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.content.Intent;
import android.os.Bundle;

import com.android.activitycontext.ActivityContext;
import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureDoesNotHavePermission;
import com.android.bedstead.harrier.annotations.EnsureHasPermission;
import com.android.bedstead.harrier.annotations.EnsureHasWorkProfile;
import com.android.bedstead.harrier.annotations.PermissionTest;
import com.android.bedstead.harrier.annotations.RequireRunOnInitialUser;
import com.android.bedstead.testapp.BaseTestAppActivity;
import com.android.bedstead.testapp.TestApp;
import com.android.bedstead.testapp.TestAppActivityReference;
import com.android.bedstead.testapp.TestAppInstance;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public class ActivityTest {
    private static final int EMPTY_REQUEST_CODE = 0;
    private static final int REQUEST_CODE = 123;

    private static final int ACTIVITY_RESULT_VALUE = 5;

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final TestApp sTestApp = sDeviceState.testApps().query()
            .whereActivities()
            .contains(activity().where().exported().isTrue())
            .get();

    @RequireRunOnInitialUser
    @EnsureHasWorkProfile // Activities need to start on both users
    @PermissionTest({INTERACT_ACROSS_USERS, INTERACT_ACROSS_USERS_FULL})
    public void startActivityForResultAsUser_differentUser_startedSuccessfully()
            throws InterruptedException {
        try (TestAppInstance instance = sTestApp.install(sDeviceState.workProfile())) {
            TestAppActivityReference activityReference =
                    instance.activities().query()
                            .whereActivity().exported().isTrue()
                            .get();

            ActivityContext.runWithContext(activity -> {
                Intent intent = new Intent();
                intent.setComponent(activityReference.component().componentName());
                activity.startActivityForResultAsUser(intent, EMPTY_REQUEST_CODE,
                        sDeviceState.workProfile().userHandle());
            });

            assertThat(activityReference.events().activityStarted()).eventOccurred();
        }
    }

    @Test
    @RequireRunOnInitialUser
    @EnsureHasWorkProfile // Activities need to start on both users
    @EnsureHasPermission(INTERACT_ACROSS_USERS_FULL)
    public void startActivityForResultAsUser_requestCodeAndResultPassedSuccessfully()
            throws InterruptedException {
        try (TestAppInstance instance = sTestApp.install(sDeviceState.workProfile())) {
            TestAppActivityReference activityReference =
                    instance.activities().query()
                            .whereActivity().exported().isTrue()
                            .get();

            ActivityContext.runWithContext(
                    activity -> {
                        Intent intent = new Intent();
                        intent.setComponent(activityReference.component().componentName());
                        intent.putExtra(BaseTestAppActivity.ACTIVITY_RESULT_KEY,
                                ACTIVITY_RESULT_VALUE);
                        activity.startActivityForResultAsUser(intent, REQUEST_CODE,
                                sDeviceState.workProfile().userHandle());
                    },
                    activity -> {
                        try {
                            ActivityContext.ActivityResult result =
                                    activity.blockForActivityResult();
                            assertThat(result.getRequestCode()).isEqualTo(REQUEST_CODE);
                            assertThat(result.getResultCode()).isEqualTo(ACTIVITY_RESULT_VALUE);
                        } catch (InterruptedException e) {
                            throw new AssertionError(e.getMessage());
                        }
                    });
        }
    }

    @Test
    @RequireRunOnInitialUser
    @EnsureHasWorkProfile // Activities need to start on both users
    @EnsureDoesNotHavePermission(
            {INTERACT_ACROSS_USERS, INTERACT_ACROSS_USERS_FULL})
    public void startActivityForResultAsUser_noPermissions_throwsSecurityException() {
        try (TestAppInstance instance = sTestApp.install(sDeviceState.workProfile())) {
            TestAppActivityReference activityReference =
                    instance.activities().query()
                            .whereActivity().exported().isTrue()
                            .get();

            assertThrows(SecurityException.class, () ->
                    ActivityContext.runWithContext(activity -> {
                        Intent intent = new Intent();
                        intent.setComponent(activityReference.component().componentName());
                        activity.startActivityForResultAsUser(intent, EMPTY_REQUEST_CODE,
                                sDeviceState.workProfile().userHandle());
                    })
            );
        }
    }

    @RequireRunOnInitialUser
    @EnsureHasWorkProfile // Activities need to start on both users
    @PermissionTest({INTERACT_ACROSS_USERS, INTERACT_ACROSS_USERS_FULL})
    public void startActivityAsUser_differentUser_startedSuccessfully()
            throws InterruptedException {
        Bundle options = new Bundle();
        try (TestAppInstance instance = sTestApp.install(sDeviceState.workProfile())) {
            TestAppActivityReference activityReference =
                    instance.activities().query()
                            .whereActivity().exported().isTrue()
                            .get();

            ActivityContext.runWithContext(activity -> {
                Intent intent = new Intent();
                intent.setComponent(activityReference.component().componentName());
                activity.startActivityAsUser(intent, options,
                        sDeviceState.workProfile().userHandle());
            });

            assertThat(activityReference.events().activityStarted()).eventOccurred();
        }
    }

    @Test
    @RequireRunOnInitialUser
    @EnsureHasWorkProfile // Activities need to start on both users
    @EnsureDoesNotHavePermission(
            {INTERACT_ACROSS_USERS, INTERACT_ACROSS_USERS_FULL})
    public void startActivityAsUser_noPermissions_throwsSecurityException() {
        Bundle options = new Bundle();
        try (TestAppInstance instance = sTestApp.install(sDeviceState.workProfile())) {
            TestAppActivityReference activityReference =
                    instance.activities().query()
                            .whereActivity().exported().isTrue()
                            .get();

            assertThrows(SecurityException.class, () ->
                    ActivityContext.runWithContext(activity -> {
                        Intent intent = new Intent();
                        intent.setComponent(activityReference.component().componentName());
                        activity.startActivityAsUser(intent, options,
                                sDeviceState.workProfile().userHandle());
                    })
            );
        }
    }
}
