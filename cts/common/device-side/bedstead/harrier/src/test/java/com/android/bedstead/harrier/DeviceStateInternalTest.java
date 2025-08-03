/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.bedstead.harrier;

import static com.android.bedstead.harrier.annotations.EnsureHasPermissionKt.ensureHasPermission;
import static com.android.bedstead.harrier.annotations.EnsureHasWorkProfileKt.ensureHasWorkProfile;
import static com.android.bedstead.harrier.annotations.EnsureNoPackageRespondsToIntentKt.ensureNoPackageRespondsToIntent;
import static com.android.bedstead.harrier.annotations.EnsurePackageRespondsToIntentKt.ensurePackageRespondsToIntent;
import static com.android.bedstead.harrier.annotations.RequireNoPackageRespondsToIntentKt.requireNoPackageRespondsToIntent;
import static com.android.bedstead.harrier.annotations.RequirePackageRespondsToIntentKt.requirePackageRespondsToIntent;
import static com.android.bedstead.nene.permissions.CommonPermissions.INTERACT_ACROSS_USERS;
import static com.android.bedstead.nene.permissions.CommonPermissions.INTERACT_ACROSS_USERS_FULL;
import static com.android.bedstead.nene.utils.Assert.assertThrows;
import static com.android.queryable.queries.ActivityQuery.activity;
import static com.android.queryable.queries.IntentFilterQuery.intentFilter;
import static com.google.common.truth.Truth.assertThat;

import android.content.Intent;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.users.UserReference;
import com.android.bedstead.testapp.TestApp;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Use this class to create tests that validate the behavior of {@code DeviceState} components.
 *
 * <p>The tests would comprise multiple steps where each step would execute the implementation
 * of one or more {@code DeviceState} component(s). Device state changes can be asserted after
 * each step.
 *
 * <p>Below is an example of a test that tests the case when an annotation is used twice in a
 * test class, the test asserts that the state created by the first test is reused by the second
 * one.
 *
 * <pre>
 *  {@code
 *  @Test
 *  public void ensureHasWorkProfile_reusesUser() {
 *      AtomicReference<UserReference> user = new AtomicReference<>();
 *
 *      mDeviceState.stepName("createWorkProfile")
 *                 .apply(List.of(ensureHasProfileOwner()), () -> {
 *          user.set(mDeviceState.profileOwner().user());
 *      });
 *
 *      mDeviceState.stepName("reuseWorkProfile")
 *                 .apply(List.of(ensureHasProfileOwner()), () -> {
 *          assertThat(mDeviceState.profileOwner().user()).isEqualTo(user.get());
 *      });
 *  }}
 * </pre>
 */
@RunWith(JUnit4.class)
public final class DeviceStateInternalTest {

    private static final String INTENT_ACTION = "com.android.cts.deviceandprofileowner.CONFIRM";

    private DeviceStateTester mDeviceState;

    @Before
    public void setUp() {
        mDeviceState = new DeviceStateTester();
    }

    @After
    public void tearDown() {
        mDeviceState.tearDown();
    }

    @Test
    public void ensureHasWorkProfile_reusesUser() {
        AtomicReference<UserReference> user = new AtomicReference<>();

        mDeviceState.stepName("createWorkProfile")
                .apply(List.of(ensureHasWorkProfile()), () -> {
            user.set(mDeviceState.workProfile());
        });

        mDeviceState.stepName("reuseWorkProfile")
                .apply(List.of(ensureHasWorkProfile()), () -> {
            assertThat(mDeviceState.workProfile()).isEqualTo(user.get());
        });
    }

    @Test
    public void requirePackageRespondsToIntentAnnotation_packageRespondsToIntent() {
        mDeviceState.testApps().query()
                .whereActivities().contains(
                        activity().where().intentFilters().contains(
                                intentFilter().where().actions().contains(INTENT_ACTION)
                        )
                )
                .get()
                .install();

        mDeviceState.stepName("requirePackageRespondsToIntentAnnotation_packageRespondsToIntent")
                .apply(List.of(requirePackageRespondsToIntent(INTENT_ACTION))
                        , () ->
                                assertThat(
                                        TestApis.context().instrumentedContext().getPackageManager().queryIntentActivities(
                                                new Intent(INTENT_ACTION),
                                                /* flags= */0)
                                ).isNotEmpty());
    }

    @Test
    public void requirePackageRespondsToIntentAnnotation_workProfile_packageRespondsToIntent() {
        mDeviceState.stepName("createWorkProfile")
                .apply(List.of(ensureHasWorkProfile())
                , () ->
                                mDeviceState.testApps().query().whereActivities().contains(
                                        activity().where().intentFilters().contains(
                                                intentFilter().where().actions().contains(INTENT_ACTION)))
                                        .get()
                                        .install(mDeviceState.workProfile()));

        mDeviceState.stepName("requirePackageRespondsToIntentAnnotation_workProfile_packageRespondsToIntent")
                .apply(List.of(requirePackageRespondsToIntent(INTENT_ACTION, UserType.WORK_PROFILE), ensureHasPermission(INTERACT_ACROSS_USERS, INTERACT_ACROSS_USERS_FULL))
                        , () ->
                                assertThat(
                                        TestApis.context().androidContextAsUser(mDeviceState.workProfile()).getPackageManager().queryIntentActivities(
                                                new Intent(INTENT_ACTION),
                                                /* flags= */0)
                                ).isNotEmpty());
    }

    @Test
    public void requirePackageRespondsToIntentAnnotation_noPackageRespondsToIntent() {
        assertThrows(NeneException.class
                , () ->
                        mDeviceState
                                .stepName("requirePackageRespondsToIntentAnnotation_noPackageRespondsToIntent")
                                .apply(List.of(requirePackageRespondsToIntent("INTENT_ACTION"))
                                        , () -> {})
        );
    }

    @Test
    public void requireNoPackageRespondsToIntentAnnotation_noPackageRespondsToIntent() {
        mDeviceState.stepName("requireNoPackageRespondsToIntentAnnotation_noPackageRespondsToIntent")
                .apply(List.of(requireNoPackageRespondsToIntent("INTENT_ACTION"))
                        , () ->
                                assertThat(
                                        TestApis.context().instrumentedContext().getPackageManager().queryIntentActivities(
                                                new Intent("INTENT_ACTION"),
                                                /* flags= */0)
                                ).isEmpty());
    }

    @Test
    public void requireNoPackageRespondsToIntentAnnotation_packageRespondsToIntent() {
        mDeviceState.testApps().query()
                .whereActivities().contains(
                        activity().where().intentFilters().contains(
                                intentFilter().where().actions().contains(INTENT_ACTION)
                        )
                )
                .get()
                .install();

        assertThrows(NeneException.class
                , () ->
                        mDeviceState
                                .stepName("requireNoPackageRespondsToIntentAnnotation_packageRespondsToIntent")
                                .apply(List.of(requireNoPackageRespondsToIntent(INTENT_ACTION))
                                        , () -> {})
        );
    }

    @Test
    public void ensurePackageRespondsToIntentAnnotation() {
        mDeviceState.stepName("ensurePackageRespondsToIntentAnnotation")
                .apply(List.of(ensurePackageRespondsToIntent(INTENT_ACTION))
                        , () -> {});
    }

    @Test
    public void ensurePackageRespondsToIntentAnnotation_throwsException() {
        assertThrows(NeneException.class
                , () ->
                        mDeviceState
                                .stepName("ensurePackageRespondsToIntentAnnotation_throwsException")
                                .apply(List.of(ensurePackageRespondsToIntent("INTENT_ACTION"))
                                        , () -> {})
        );
    }

    @Test
    public void ensureNoPackageRespondsToIntentAnnotation() {
        TestApp testApp = mDeviceState.testApps().query()
                .whereActivities().contains(
                        activity().where().intentFilters().contains(
                                intentFilter().where().actions().contains(INTENT_ACTION)
                        )
                )
                .get();

        try (var unused = testApp.install()) {
            mDeviceState.stepName("ensureNoPackageRespondsToIntentAnnotation")
                    .apply(List.of(ensureNoPackageRespondsToIntent(INTENT_ACTION))
                            , () ->
                                    assertThat(TestApis.packages().find(testApp.packageName()).installedOnUser()).isFalse()
                    );
        }
    }
}
