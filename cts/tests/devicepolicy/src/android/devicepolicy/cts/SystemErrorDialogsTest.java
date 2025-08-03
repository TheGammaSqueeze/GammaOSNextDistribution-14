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

import static android.provider.Settings.Secure.SHOW_FIRST_CRASH_DIALOG_DEV_OPTION;

import static com.android.bedstead.harrier.UserType.ADDITIONAL_USER;
import static com.android.bedstead.harrier.UserType.INITIAL_USER;
import static com.android.bedstead.nene.userrestrictions.CommonUserRestrictions.DISALLOW_SYSTEM_ERROR_DIALOGS;
import static com.android.queryable.queries.ActivityQuery.activity;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.provider.Settings;

import androidx.test.uiautomator.By;
import androidx.test.uiautomator.Until;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureDoesNotHaveUserRestriction;
import com.android.bedstead.harrier.annotations.EnsureGlobalSettingSet;
import com.android.bedstead.harrier.annotations.EnsureHasUserRestriction;
import com.android.bedstead.harrier.annotations.EnsureSecureSettingSet;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.UserTest;
import com.android.bedstead.harrier.annotations.enterprise.CanSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest;
import com.android.bedstead.harrier.policies.DisallowSystemErrorDialogs;
import com.android.bedstead.harrier.policies.DisallowSystemErrorDialogsPermissionBased;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.testapp.TestAppInstance;
import com.android.compatibility.common.util.ApiTest;

import com.google.common.truth.Truth;

import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public final class SystemErrorDialogsTest {

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final long CRASH_DIALOG_WAIT_MILLIS = 2_000;

    @CannotSetPolicyTest(
            policy = DisallowSystemErrorDialogs.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_SYSTEM_ERROR_DIALOGS")
    public void addUserRestriction_disallowSystemErrorDialogs_cannotSet_throwsException() {
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                        sDeviceState.dpc().componentName(), DISALLOW_SYSTEM_ERROR_DIALOGS));
    }

    @CanSetPolicyTest(policy = DisallowSystemErrorDialogs.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_SYSTEM_ERROR_DIALOGS")
    // TODO: Add restriction for target U+
    // TODO: Test that this is actually global
    public void addUserRestriction_disallowSystemErrorDialogs_isSet() {
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(),
                    DISALLOW_SYSTEM_ERROR_DIALOGS);

            assertThat(TestApis.devicePolicy()
                    .userRestrictions().isSet(DISALLOW_SYSTEM_ERROR_DIALOGS))
                    .isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_SYSTEM_ERROR_DIALOGS);
        }
    }

    @CanSetPolicyTest(policy = DisallowSystemErrorDialogsPermissionBased.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_SYSTEM_ERROR_DIALOGS")
    // TODO: Add restriction for target U+
    // TODO: Test that this is actually global
    public void addUserRestrictionGlobally_disallowSystemErrorDialogs_isSet() {
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestrictionGlobally(
                    DISALLOW_SYSTEM_ERROR_DIALOGS);

            assertThat(TestApis.devicePolicy()
                    .userRestrictions().isSet(DISALLOW_SYSTEM_ERROR_DIALOGS))
                    .isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_SYSTEM_ERROR_DIALOGS);
        }
    }

    @CanSetPolicyTest(policy = DisallowSystemErrorDialogs.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_SYSTEM_ERROR_DIALOGS")
    // TODO: Add restriction for target U+
    // TODO: Test that this is actually global
    public void clearUserRestriction_disallowSystemErrorDialogs_isNotSet() {
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(),
                    DISALLOW_SYSTEM_ERROR_DIALOGS);

            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_SYSTEM_ERROR_DIALOGS);

            assertThat(TestApis.devicePolicy().userRestrictions()
                    .isSet(DISALLOW_SYSTEM_ERROR_DIALOGS))
                    .isFalse();
        } finally {
            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_SYSTEM_ERROR_DIALOGS);
        }
    }

    @EnsureDoesNotHaveUserRestriction(DISALLOW_SYSTEM_ERROR_DIALOGS)
    @UserTest({INITIAL_USER, ADDITIONAL_USER})
    @EnsureSecureSettingSet(key = SHOW_FIRST_CRASH_DIALOG_DEV_OPTION, value = "1")
    @EnsureGlobalSettingSet(
            key = Settings.Global.ACTIVITY_MANAGER_CONSTANTS, value = "min_crash_interval=500")
    @Ignore("b/279876672 crash dialogs do not show reliably in test")
    public void appCrashes_disallowSystemErrorDialogsNotSet_alwaysShowSystemErrorDialogsEnabled_showsDialog() {
        try (TestAppInstance t = sDeviceState.testApps().query()
                .whereActivities().contains(
                        activity().where().exported().isTrue()
                ).get()
                .install()) {
            t.activities().query().whereActivity().exported().isTrue().get().start();

            t.testApp().pkg().runningProcess().crash();

            Truth.assertWithMessage("Expected crash dialog to show").that(
                    TestApis.ui().device().wait(
                            Until.findObject(
                                    By.res("android", "aerr_app_info")),
                            CRASH_DIALOG_WAIT_MILLIS))
                    .isNotNull();
        }
    }

    @EnsureHasUserRestriction(DISALLOW_SYSTEM_ERROR_DIALOGS)
    @UserTest({INITIAL_USER, ADDITIONAL_USER})
    @EnsureSecureSettingSet(key = SHOW_FIRST_CRASH_DIALOG_DEV_OPTION, value = "1")
    @EnsureGlobalSettingSet(
            key = Settings.Global.ACTIVITY_MANAGER_CONSTANTS, value = "min_crash_interval=500")
    public void appCrashes_disallowSystemErrorDialogsSet_alwaysShowSystemErrorDialogsEnabled_doesNotShowDialog() {
        try (TestAppInstance t = sDeviceState.testApps().query()
                .whereActivities().contains(
                        activity().where().exported().isTrue()
                ).get()
                .install()) {
            t.activities().query().whereActivity().exported().isTrue().get().start();

            t.testApp().pkg().runningProcess().crash();

            Truth.assertWithMessage("Expected crash dialog to show").that(
                            TestApis.ui().device().wait(
                                    Until.findObject(
                                            By.res(
                                                    "android", "aerr_app_info")),
                                    CRASH_DIALOG_WAIT_MILLIS))
                    .isNull();
        }
    }
}