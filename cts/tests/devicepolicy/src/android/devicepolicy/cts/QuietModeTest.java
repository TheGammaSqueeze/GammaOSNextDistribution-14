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

package android.devicepolicy.cts;

import static android.content.Intent.ACTION_MANAGED_PROFILE_AVAILABLE;
import static android.content.Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.devicepolicy.cts.utils.TestArtifactUtils.dumpWindowHierarchy;
import static android.os.UserManager.QUIET_MODE_DISABLE_ONLY_IF_CREDENTIAL_NOT_REQUIRED;

import static com.android.bedstead.nene.permissions.CommonPermissions.INTERACT_ACROSS_USERS_FULL;
import static com.android.bedstead.nene.permissions.CommonPermissions.MODIFY_QUIET_MODE;
import static com.android.queryable.queries.ActivityQuery.activity;
import static com.android.queryable.queries.IntentFilterQuery.intentFilter;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.testng.Assert.assertThrows;

import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.UserHandle;

import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureHasPermission;
import com.android.bedstead.harrier.annotations.EnsureHasWorkProfile;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.permissions.PermissionContext;
import com.android.bedstead.nene.roles.RoleContext;
import com.android.bedstead.nene.users.UserReference;
import com.android.bedstead.nene.utils.Poll;
import com.android.bedstead.testapp.TestApp;
import com.android.bedstead.testapp.TestAppActivityReference;
import com.android.bedstead.testapp.TestAppInstance;
import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.BlockingBroadcastReceiver;
import com.android.queryable.info.ActivityInfo;
import com.android.queryable.queries.Query;

import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests to ensure apps are properly restricted when the user is in quiet mode.
 */
@RunWith(BedsteadJUnit4.class)
public class QuietModeTest {
    private static final double TEST_LATITUDE = 51.5;
    private static final double TEST_LONGITUDE = -0.1;
    private static final float TEST_ACCURACY = 14.0f;

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();
    private static final Context sContext = TestApis.context().instrumentedContext();

    private static final TestApp sTestApp = sDeviceState.testApps().query()
            .whereActivities()
            .contains(activity().where().exported().isTrue())
            .get();

    private static final Query<ActivityInfo> sMainActivityQuery =
            activity()
                    .where().exported().isTrue()
                    .where().intentFilters().contains(
                            intentFilter()
                                    .where().actions().contains(Intent.ACTION_MAIN)
                                    .where().categories().contains(Intent.CATEGORY_HOME));
    private static final TestApp sTestAppWithLauncherActivity =
            sDeviceState.testApps().query()
                    .whereActivities().contains(sMainActivityQuery)
                    .get();
    private static final String PASSWORD = "12345678";

    @Postsubmit(reason = "new test")
    @EnsureHasWorkProfile
    @EnsureHasPermission(INTERACT_ACROSS_USERS_FULL)
    @Test
    public void startActivityInQuietProfile_quietModeDialogShown() throws Exception {
        UserReference workProfile = sDeviceState.workProfile();
        String titleText = sContext.getString(R.string.test_string_1);
        try (TestAppInstance instance = sTestApp.install(workProfile)) {
            // Override "Turn on work apps" dialog title to avoid depending on a particular string.
            TestApis.devicePolicy().resources().strings().set(
                    "Core.UNLAUNCHABLE_APP_WORK_PAUSED_TITLE", R.string.test_string_1);

            TestAppActivityReference activityReference =
                    instance.activities().query().whereActivity().exported().isTrue().get();

            workProfile.setQuietMode(true);

            Intent intent = new Intent()
                    .addFlags(FLAG_ACTIVITY_NEW_TASK)
                    .setComponent(activityReference.component().componentName());
            sContext.startActivityAsUser(intent, new Bundle(), workProfile.userHandle());

            UiObject2 dialogTitle = TestApis.ui().device().wait(
                    Until.findObject(By.text(titleText)), 5000 /* 5s */);
            assertWithMessage("Work mode dialog not shown").that(dialogTitle).isNotNull();
        } catch (AssertionError e) {
            dumpWindowHierarchy("startActivityInQuietProfile_quietModeDialogShown");
            throw e;
        } finally {
            TestApis.devicePolicy().resources().strings()
                    .reset("Core.UNLAUNCHABLE_APP_WORK_PAUSED_TITLE");
            workProfile.setQuietMode(false);
        }
    }

    @Postsubmit(reason = "new test")
    @EnsureHasWorkProfile(dpcIsPrimary = true)
    @EnsureHasPermission(INTERACT_ACROSS_USERS_FULL)
    @Test
    public void quietMode_profileStopped() throws Exception {
        UserReference workProfile = sDeviceState.workProfile();

        workProfile.setQuietMode(true);
        try {
            // Profile should be stopped.
            Poll.forValue("profile running", workProfile::isRunning).toBeEqualTo(false)
                    .errorOnFail().await();

            // The DPC shouldn't be suspended.
            assertThat(sDeviceState.dpc().testApp().pkg().isSuspended(workProfile)).isFalse();
        } finally {
            workProfile.setQuietMode(false);
        }
    }

    @ApiTest(apis = "android.os.UserManager#requestQuietModeEnabled")
    @Test
    @EnsureHasWorkProfile
    @Postsubmit(reason = "new test")
    public void requestQuietModeEnabled_callerIsNotForegroundLauncher_throwsSecurityException() {
        try (TestAppInstance testAppInstance = sTestAppWithLauncherActivity.install()) {
            assertThrows(SecurityException.class,
                    () -> testAppInstance.userManager().requestQuietModeEnabled(true,
                            sDeviceState.workProfile().userHandle()));
        }
    }

    @ApiTest(apis = "android.os.UserManager#requestQuietModeEnabled")
    @Test
    @EnsureHasWorkProfile
    @Postsubmit(reason = "new test")
    public void requestQuietModeEnabled_callerIsNotDefaultLauncher_throwsSecurityException() {
        UserReference workProfile = sDeviceState.workProfile();
        try (TestAppInstance testAppInstance = sTestAppWithLauncherActivity.install()) {
            runTestAppInForeground(testAppInstance);

            assertThrows(SecurityException.class,
                    () -> testAppInstance.userManager().requestQuietModeEnabled(true,
                            workProfile.userHandle()));
        } finally {
            workProfile.setQuietMode(false);
        }
    }

    @Ignore("(b/298934576): Flaky as test app is not always set as foreground launcher")
    @ApiTest(apis = "android.os.UserManager#requestQuietModeEnabled")
    @Test
    @EnsureHasWorkProfile
    @Postsubmit(reason = "new test")
    public void requestQuietModeEnabled_callerIsForegroundLauncher_success() {
        UserReference workProfile = sDeviceState.workProfile();
        try (TestAppInstance testAppInstance = sTestAppWithLauncherActivity.install();
            RoleContext r = setTestAppAsForegroundDefaultLauncher(testAppInstance)) {

            testAppInstance.userManager().requestQuietModeEnabled(true,
                    workProfile.userHandle());

            assertThat(workProfile.isQuietModeEnabled()).isTrue();
        } finally {
            workProfile.setQuietMode(false);
        }
    }

    @ApiTest(apis = "android.os.UserManager#requestQuietModeEnabled")
    @Test
    @EnsureHasWorkProfile
    @Postsubmit(reason = "new test")
    public void requestQuietModeEnabled_callerHasModifyQuietModePermission_success() {
        UserReference workProfile = sDeviceState.workProfile();
        try (TestAppInstance testAppInstance = sTestAppWithLauncherActivity.install();
             PermissionContext p = testAppInstance.permissions().withPermission(
                     MODIFY_QUIET_MODE)) {
            testAppInstance.userManager().requestQuietModeEnabled(true,
                    workProfile.userHandle());

            assertThat(workProfile.isQuietModeEnabled()).isTrue();
        } finally {
            workProfile.setQuietMode(false);
        }
    }

    @ApiTest(apis = "android.os.UserManager#requestQuietModeEnabled")
    @Test
    @EnsureHasWorkProfile
    @EnsureHasPermission(MODIFY_QUIET_MODE)
    @Postsubmit(reason = "new test")
    public void requestQuietModeEnabled_true_managedProfileUnavailableBroadcastSent() {
        UserReference workProfile = sDeviceState.workProfile();
        try (BlockingBroadcastReceiver receiver = new BlockingBroadcastReceiver(
                sContext, ACTION_MANAGED_PROFILE_UNAVAILABLE).register()) {
            workProfile.setQuietMode(true);

            Intent intent = receiver.awaitForBroadcast();
            assertThat(intent.getAction())
                    .isEqualTo(ACTION_MANAGED_PROFILE_UNAVAILABLE);
        } finally {
            workProfile.setQuietMode(false);
        }
    }

    @ApiTest(apis = "android.os.UserManager#requestQuietModeEnabled")
    @Test
    @EnsureHasWorkProfile
    @EnsureHasPermission(MODIFY_QUIET_MODE)
    @Postsubmit(reason = "new test")
    public void requestQuietModeEnabled_false_managedProfileAvailableBroadcastSent() {
        UserReference workProfile = sDeviceState.workProfile();
        workProfile.setQuietMode(true);
        try (BlockingBroadcastReceiver receiver = new BlockingBroadcastReceiver(
                sContext, ACTION_MANAGED_PROFILE_AVAILABLE).register()) {
            workProfile.setQuietMode(false);

            assertThat(receiver.awaitForBroadcast().getAction())
                    .isEqualTo(ACTION_MANAGED_PROFILE_AVAILABLE);
        } finally {
            workProfile.setQuietMode(false);
        }
    }

    @Ignore("(b/298934576): Flaky as test app is not always set as foreground launcher")
    @ApiTest(apis = "android.os.UserManager#requestQuietModeEnabled")
    @Test
    @EnsureHasWorkProfile
    @Postsubmit(reason = "new test")
    public void requestQuietModeEnabled_false_credentialsSet_isNotDisabled() {
        UserReference workProfile = sDeviceState.workProfile();
        try (TestAppInstance testAppInstance = sTestAppWithLauncherActivity.install();
            RoleContext r = setTestAppAsForegroundDefaultLauncher(testAppInstance)) {
            workProfile.setPassword(PASSWORD);
            UserHandle workProfileUserHandle = workProfile.userHandle();
            testAppInstance.userManager().requestQuietModeEnabled(true,
                    workProfileUserHandle,
                    QUIET_MODE_DISABLE_ONLY_IF_CREDENTIAL_NOT_REQUIRED);

            testAppInstance.userManager().requestQuietModeEnabled(false,
                    workProfileUserHandle,
                    QUIET_MODE_DISABLE_ONLY_IF_CREDENTIAL_NOT_REQUIRED);

            assertThat(testAppInstance.userManager().isQuietModeEnabled(
                    workProfileUserHandle)).isTrue();
        } finally {
            workProfile.clearPassword(PASSWORD);
            workProfile.setQuietMode(false);
        }
    }

    private static RoleContext setTestAppAsForegroundDefaultLauncher(TestAppInstance testAppInstance) {
        RoleContext c = setTestAppAsDefaultLauncher();
        runTestAppInForeground(testAppInstance);
        return c;
    }

    private static void runTestAppInForeground(TestAppInstance testAppInstance) {
        TestAppActivityReference launcherActivity =
                testAppInstance.activities().query().whereActivity().intentFilters()
                        .contains(intentFilter().where().actions().contains(
                                        Intent.ACTION_MAIN)
                                .where().categories().contains(Intent.CATEGORY_HOME)).get();
        launcherActivity.start();
    }

    private static RoleContext setTestAppAsDefaultLauncher() {
        return sTestAppWithLauncherActivity.pkg().setAsRoleHolder(RoleManager.ROLE_HOME);
    }
}
