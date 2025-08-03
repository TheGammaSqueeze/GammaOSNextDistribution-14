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

package android.devicepolicy.cts;

import static android.app.admin.DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT;
import static android.app.admin.DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED;
import static android.app.admin.DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED;
import static android.app.admin.DevicePolicyManager.PERMISSION_POLICY_AUTO_DENY;
import static android.app.admin.DevicePolicyManager.PERMISSION_POLICY_AUTO_GRANT;
import static android.app.admin.DevicePolicyManager.PERMISSION_POLICY_PROMPT;

import static com.android.bedstead.harrier.UserType.WORK_PROFILE;
import static com.android.bedstead.nene.permissions.CommonPermissions.ACCESS_BACKGROUND_LOCATION;
import static com.android.bedstead.nene.permissions.CommonPermissions.ACCESS_COARSE_LOCATION;
import static com.android.bedstead.nene.permissions.CommonPermissions.ACCESS_FINE_LOCATION;
import static com.android.bedstead.nene.permissions.CommonPermissions.ACTIVITY_RECOGNITION;
import static com.android.bedstead.nene.permissions.CommonPermissions.BODY_SENSORS;
import static com.android.bedstead.nene.permissions.CommonPermissions.CAMERA;
import static com.android.bedstead.nene.permissions.CommonPermissions.INTERACT_ACROSS_USERS;
import static com.android.bedstead.nene.permissions.CommonPermissions.READ_CALENDAR;
import static com.android.bedstead.nene.permissions.CommonPermissions.READ_CONTACTS;
import static com.android.bedstead.nene.permissions.CommonPermissions.READ_PHONE_STATE;
import static com.android.bedstead.nene.permissions.CommonPermissions.READ_SMS;
import static com.android.bedstead.nene.permissions.CommonPermissions.WRITE_CALENDAR;
import static com.android.bedstead.nene.utils.Versions.U;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;
import static org.testng.Assert.assertThrows;

import android.app.admin.ManagedSubscriptionsPolicy;
import android.app.admin.RemoteDevicePolicyManager;
import android.content.ComponentName;
import android.provider.Settings;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.AfterClass;
import com.android.bedstead.harrier.annotations.EnsureGlobalSettingSet;
import com.android.bedstead.harrier.annotations.EnsureScreenIsOn;
import com.android.bedstead.harrier.annotations.EnsureUnlocked;
import com.android.bedstead.harrier.annotations.IntTestParameter;
import com.android.bedstead.harrier.annotations.NotificationsTest;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.RequireRunOnWorkProfile;
import com.android.bedstead.harrier.annotations.StringTestParameter;
import com.android.bedstead.harrier.annotations.enterprise.AdditionalQueryParameters;
import com.android.bedstead.harrier.annotations.enterprise.CanSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyAppliesTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyDoesNotApplyTest;
import com.android.bedstead.harrier.annotations.parameterized.IncludeRunOnFinancedDeviceOwnerUser;
import com.android.bedstead.harrier.policies.SetPermissionGrantState;
import com.android.bedstead.harrier.policies.SetPermissionPolicy;
import com.android.bedstead.harrier.policies.SetSensorPermissionGranted;
import com.android.bedstead.harrier.policies.SetSensorPermissionPolicyPromptForOrganizationOwnedWorkProfile;
import com.android.bedstead.harrier.policies.SetSmsPermissionGranted;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.devicepolicy.DeviceOwner;
import com.android.bedstead.nene.devicepolicy.DeviceOwnerType;
import com.android.bedstead.nene.notifications.NotificationListener;
import com.android.bedstead.nene.notifications.NotificationListenerQuerySubject;
import com.android.bedstead.nene.utils.Poll;
import com.android.bedstead.testapp.TestApp;
import com.android.bedstead.testapp.TestAppActivity;
import com.android.bedstead.testapp.TestAppInstance;
import com.android.compatibility.common.util.ApiTest;
import com.android.queryable.annotations.IntegerQuery;
import com.android.queryable.annotations.Query;

import org.junit.Assume;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@RunWith(BedsteadJUnit4.class)
public final class PermissionGrantTest {

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    // From PermissionController ADMIN_AUTO_GRANTED_PERMISSIONS_ALERTING_NOTIFICATION_CHANNEL_ID
    private static final String AUTO_GRANTED_PERMISSIONS_CHANNEL_ID =
            "alerting auto granted permissions";
    private static final String PERMISSION_CONTROLLER_PACKAGE_NAME =
            TestApis.context().instrumentedContext().getPackageManager()
                    .getPermissionControllerPackageName();

    private static final String GRANTABLE_PERMISSION = READ_CALENDAR;
    private static final String DEVELOPMENT_PERMISSION = INTERACT_ACROSS_USERS;
    private static final String PERMISSION_A = READ_CALENDAR;
    private static final String PERMISSION_B = WRITE_CALENDAR;

    @StringTestParameter({
            ACCESS_FINE_LOCATION,
            ACCESS_BACKGROUND_LOCATION,
            ACCESS_COARSE_LOCATION,
            CAMERA,
            ACTIVITY_RECOGNITION,
            BODY_SENSORS})
    @Retention(RetentionPolicy.RUNTIME)
    private @interface SensorPermissionTestParameter {
    }

    @StringTestParameter({
            ACCESS_FINE_LOCATION,
            ACCESS_BACKGROUND_LOCATION,
            ACCESS_COARSE_LOCATION})
    @Retention(RetentionPolicy.RUNTIME)
    private @interface LocationPermissionTestParameter {
    }

    @StringTestParameter({
            // Grantable permission
            READ_CALENDAR,
            READ_SMS, // All DPCs can deny sms permission
            // All DPCs can deny sensor permissions
            ACCESS_FINE_LOCATION,
            ACCESS_BACKGROUND_LOCATION,
            ACCESS_COARSE_LOCATION,
            CAMERA,
            ACTIVITY_RECOGNITION,
            BODY_SENSORS
    })
    @Retention(RetentionPolicy.RUNTIME)
    private @interface DeniablePermissionTestParameter {
    }

    private static final String NON_EXISTING_PACKAGE_NAME = "non.existing.package";
    private static final String NOT_DECLARED_PERMISSION = "not.declared.permission";

    private static final TestApp sTestApp = sDeviceState.testApps().query()
            .wherePermissions().contains(
                    READ_SMS,
                    CAMERA,
                    ACTIVITY_RECOGNITION,
                    BODY_SENSORS,
                    READ_CONTACTS,
                    ACCESS_FINE_LOCATION,
                    ACCESS_BACKGROUND_LOCATION,
                    ACCESS_COARSE_LOCATION
            ).wherePermissions().doesNotContain(
                    NOT_DECLARED_PERMISSION
            ).get();
    private static final TestApp sNotInstalledTestApp = sDeviceState.testApps().query()
            .wherePermissions().contains(GRANTABLE_PERMISSION)
            .whereActivities().isNotEmpty().get();
    private static TestAppInstance sTestAppInstance =
            sTestApp.install(TestApis.users().instrumented());

    @Before
    public void setup() {
        // TODO(279404339): For some reason this is sometimes uninstalled
        sTestAppInstance =
                sTestApp.install(TestApis.users().instrumented());
    }

    @AfterClass
    public static void teardownClass() {
        sTestAppInstance.uninstall();
    }

  @CanSetPolicyTest(policy = SetPermissionGrantState.class)
  @Ignore("b/290932414")
  public void denyPermission_setsGrantState(@DeniablePermissionTestParameter String permission) {
        int existingGrantState = sDeviceState.dpc().devicePolicyManager()
                .getPermissionGrantState(sDeviceState.dpc().componentName(),
                        sTestApp.packageName(), permission);
        try {
            boolean wasSet = sDeviceState.dpc().devicePolicyManager()
                    .setPermissionGrantState(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            permission, PERMISSION_GRANT_STATE_DENIED);

            assertWithMessage("setPermissionGrantState did not return true")
                    .that(wasSet).isTrue();
            assertWithMessage("Permission grant state should be set to denied but was not")
                    .that(sDeviceState.dpc().devicePolicyManager().getPermissionGrantState(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            permission))
                    .isEqualTo(PERMISSION_GRANT_STATE_DENIED);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                    sDeviceState.dpc().componentName(), sTestApp.packageName(),
                    permission, existingGrantState);
        }
    }

    @CanSetPolicyTest(policy = SetPermissionGrantState.class)
    public void grantPermission_setsGrantState() {
        int existingGrantState = sDeviceState.dpc().devicePolicyManager()
                .getPermissionGrantState(sDeviceState.dpc().componentName(),
                        sTestApp.packageName(), GRANTABLE_PERMISSION);

        try {
            boolean wasSet = sDeviceState.dpc().devicePolicyManager()
                    .setPermissionGrantState(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            GRANTABLE_PERMISSION, PERMISSION_GRANT_STATE_GRANTED);

            assertWithMessage("setPermissionGrantState did not return true")
                    .that(wasSet).isTrue();
            assertWithMessage("Permission grant state should be set to granted but was not")
                    .that(sDeviceState.dpc().devicePolicyManager().getPermissionGrantState(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            GRANTABLE_PERMISSION))
                    .isEqualTo(PERMISSION_GRANT_STATE_GRANTED);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                    sDeviceState.dpc().componentName(), sTestApp.packageName(),
                    GRANTABLE_PERMISSION, existingGrantState);
        }
    }

  @PolicyAppliesTest(policy = SetPermissionGrantState.class)
  @Ignore("b/290932414")
  public void denyPermission_permissionIsDenied(
      @DeniablePermissionTestParameter String permission) {
        int existingGrantState = sDeviceState.dpc().devicePolicyManager()
                .getPermissionGrantState(sDeviceState.dpc().componentName(),
                        sTestApp.packageName(), permission);
        try {
            sTestApp.pkg().grantPermission(TestApis.users().instrumented(), permission);
            sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                    sDeviceState.dpc().componentName(), sTestApp.packageName(),
                    permission, PERMISSION_GRANT_STATE_DENIED);

            assertWithMessage("Permission should not be granted but was").that(
                    sTestApp.pkg().hasPermission(permission)).isFalse();

            // TODO(b/204041462): Test that the app cannot request the permission
        } finally {
            sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                    sDeviceState.dpc().componentName(), sTestApp.packageName(),
                    permission, existingGrantState);
            sTestApp.pkg().denyPermission(TestApis.users().instrumented(), permission);
        }
    }

    @PolicyAppliesTest(policy = SetPermissionGrantState.class)
    public void grantPermission_permissionIsGranted() {
        int existingGrantState = sDeviceState.dpc().devicePolicyManager()
                .getPermissionGrantState(sDeviceState.dpc().componentName(),
                        sTestApp.packageName(), GRANTABLE_PERMISSION);
        try {
            sTestApp.pkg().denyPermission(TestApis.users().instrumented(),
                    GRANTABLE_PERMISSION);
            sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                    sDeviceState.dpc().componentName(), sTestApp.packageName(),
                    GRANTABLE_PERMISSION, PERMISSION_GRANT_STATE_GRANTED);

            assertWithMessage("Permission should be granted but was not").that(
                    sTestApp.pkg().hasPermission(GRANTABLE_PERMISSION)).isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                    sDeviceState.dpc().componentName(), sTestApp.packageName(),
                    GRANTABLE_PERMISSION, existingGrantState);
            sTestApp.pkg().denyPermission(TestApis.users().instrumented(),
                    GRANTABLE_PERMISSION);
        }
    }

  @PolicyDoesNotApplyTest(policy = SetPermissionGrantState.class)
  @Ignore("b/290932414")
  public void denyPermission_doesNotApply_permissionIsNotDenied(
      @DeniablePermissionTestParameter String permission) {
        try {
            sTestApp.pkg().grantPermission(TestApis.users().instrumented(), permission);

            sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                    sDeviceState.dpc().componentName(), sTestApp.packageName(),
                    permission, PERMISSION_GRANT_STATE_DENIED);

            assertWithMessage("Permission should not be denied but was").that(
                    sTestApp.pkg().hasPermission(permission)).isTrue();
        } finally {
            sTestApp.pkg().denyPermission(TestApis.users().instrumented(), permission);
        }
    }

  @PolicyDoesNotApplyTest(policy = SetPermissionGrantState.class)
  @AdditionalQueryParameters(
      forTestApp = "dpc",
      query = @Query(targetSdkVersion = @IntegerQuery(isLessThan = U)))
  @Ignore("b/290932414")
  public void grantPermission_doesNotApply_permissionIsNotGranted(
      @DeniablePermissionTestParameter String permission) {
        try {
            sTestApp.pkg().denyPermission(TestApis.users().instrumented(), permission);

            sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                    sDeviceState.dpc().componentName(), sTestApp.packageName(),
                    permission, PERMISSION_GRANT_STATE_GRANTED);

            assertWithMessage("Permission should not be granted but was").that(
                    sTestApp.pkg().hasPermission(permission)).isFalse();
        } finally {
            sTestApp.pkg().denyPermission(TestApis.users().instrumented(), permission);
        }
    }

    @CannotSetPolicyTest(policy = SetPermissionGrantState.class)
    public void grantPermission_cannotBeSet_throwsException(
            @DeniablePermissionTestParameter String permission) {
        assertThrows(SecurityException.class, () -> sDeviceState.dpc().devicePolicyManager()
                .setPermissionGrantState(sDeviceState.dpc().componentName(), sTestApp.packageName(),
                        permission, PERMISSION_GRANT_STATE_GRANTED));
    }

    // TODO(b/204041462): Add test that the user can manually grant sensor permissions

    @CanSetPolicyTest(policy = SetPermissionGrantState.class)
    public void grantDevelopmentPermission_cannotGrant() {
        int existingGrantState = sDeviceState.dpc().devicePolicyManager()
                .getPermissionGrantState(sDeviceState.dpc().componentName(),
                        sTestApp.packageName(), DEVELOPMENT_PERMISSION);
        try {
            boolean wasSet =
                    sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            DEVELOPMENT_PERMISSION, PERMISSION_GRANT_STATE_GRANTED);

            assertWithMessage("setPermissionGrantState did not return false")
                    .that(wasSet).isFalse();
            assertWithMessage("Permission grant state should not be set to granted but was")
                    .that(sDeviceState.dpc().devicePolicyManager().getPermissionGrantState(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            DEVELOPMENT_PERMISSION))
                    .isNotEqualTo(PERMISSION_GRANT_STATE_GRANTED);
            assertWithMessage("Permission should not be granted but was")
                    .that(sTestApp.pkg().hasPermission(
                    DEVELOPMENT_PERMISSION)).isFalse();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                    sDeviceState.dpc().componentName(), sTestApp.packageName(),
                    DEVELOPMENT_PERMISSION, existingGrantState);
        }
    }

    @CanSetPolicyTest(policy = SetPermissionGrantState.class)
    public void denyDevelopmentPermission_cannotDeny() {
        int existingGrantState = sDeviceState.dpc().devicePolicyManager()
                .getPermissionGrantState(sDeviceState.dpc().componentName(),
                        sTestApp.packageName(), DEVELOPMENT_PERMISSION);
        try {
            boolean wasSet =
                    sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            DEVELOPMENT_PERMISSION, PERMISSION_GRANT_STATE_DENIED);

            assertWithMessage("setPermissionGrantState did not return false")
                    .that(wasSet).isFalse();
            assertWithMessage("Permission grant state should not be set to granted but was")
                    .that(sDeviceState.dpc().devicePolicyManager().getPermissionGrantState(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            DEVELOPMENT_PERMISSION))
                    .isNotEqualTo(PERMISSION_GRANT_STATE_DENIED);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                    sDeviceState.dpc().componentName(), sTestApp.packageName(),
                    DEVELOPMENT_PERMISSION, existingGrantState);
        }
    }

    @CanSetPolicyTest(policy = SetPermissionGrantState.class)
    public void setDevelopmentPermissionToDefault_cannotSet() {
        int existingGrantState = sDeviceState.dpc().devicePolicyManager()
                .getPermissionGrantState(sDeviceState.dpc().componentName(),
                        sTestApp.packageName(), DEVELOPMENT_PERMISSION);
        try {
            boolean wasSet =
                    sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            DEVELOPMENT_PERMISSION, PERMISSION_GRANT_STATE_DEFAULT);

            assertWithMessage("setPermissionGrantState did not return false")
                    .that(wasSet).isFalse();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                    sDeviceState.dpc().componentName(), sTestApp.packageName(),
                    DEVELOPMENT_PERMISSION, existingGrantState);
        }
    }

    @CanSetPolicyTest(policy = SetSmsPermissionGranted.class)
    public void grantSmsPermission_setsGrantState() {
        int existingGrantState = sDeviceState.dpc().devicePolicyManager()
                .getPermissionGrantState(sDeviceState.dpc().componentName(),
                        sTestApp.packageName(), READ_SMS);
        try {
            boolean wasSet =
                    sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            READ_SMS, PERMISSION_GRANT_STATE_GRANTED);

            assertWithMessage("setPermissionGrantState did not return true")
                    .that(wasSet).isTrue();
            assertWithMessage("Permission grant state should be set to granted but was not")
                    .that(sDeviceState.dpc().devicePolicyManager().getPermissionGrantState(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            READ_SMS))
                    .isEqualTo(PERMISSION_GRANT_STATE_GRANTED);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                    sDeviceState.dpc().componentName(), sTestApp.packageName(),
                    READ_SMS, existingGrantState);
        }
    }

    @CanSetPolicyTest(policy = SetSensorPermissionGranted.class)
    @Ignore("(282111883)")
    public void grantSensorPermission_setsGrantState(
            @SensorPermissionTestParameter String permission) {
        int existingGrantState = sDeviceState.dpc().devicePolicyManager()
                .getPermissionGrantState(sDeviceState.dpc().componentName(),
                        sTestApp.packageName(), permission);
        try {
            boolean wasSet =
                    sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            permission, PERMISSION_GRANT_STATE_GRANTED);

            assertWithMessage("setPermissionGrantState did not return true")
                    .that(wasSet).isTrue();
            assertWithMessage("Permission grant state should be set to granted but was not")
                    .that(sDeviceState.dpc().devicePolicyManager().getPermissionGrantState(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            permission))
                    .isEqualTo(PERMISSION_GRANT_STATE_GRANTED);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                    sDeviceState.dpc().componentName(), sTestApp.packageName(),
                    permission, existingGrantState);
        }
    }

    @PolicyAppliesTest(policy = SetSmsPermissionGranted.class)
    public void grantSmsPermission_permissionIsGranted() {
        int existingGrantState = sDeviceState.dpc().devicePolicyManager()
                .getPermissionGrantState(sDeviceState.dpc().componentName(),
                        sTestApp.packageName(), READ_SMS);
        try {
            sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                    sDeviceState.dpc().componentName(), sTestApp.packageName(),
                    READ_SMS, PERMISSION_GRANT_STATE_GRANTED);

            assertWithMessage("Permission should be granted but was not").that(
                    sTestApp.pkg().hasPermission(READ_SMS)).isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                    sDeviceState.dpc().componentName(), sTestApp.packageName(),
                    READ_SMS, existingGrantState);
        }
    }

    @PolicyAppliesTest(policy = SetSensorPermissionGranted.class)
    public void grantSensorPermission_permissionIsGranted(
            @SensorPermissionTestParameter String permission) {
        int existingGrantState = sDeviceState.dpc().devicePolicyManager()
                .getPermissionGrantState(sDeviceState.dpc().componentName(),
                        sTestApp.packageName(), permission);
        try {
            sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                    sDeviceState.dpc().componentName(), sTestApp.packageName(),
                    permission, PERMISSION_GRANT_STATE_GRANTED);

            assertWithMessage("Permission should be granted but was not").that(
                    sTestApp.pkg().hasPermission(permission)).isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                    sDeviceState.dpc().componentName(), sTestApp.packageName(),
                    permission, existingGrantState);
        }
    }

    @PolicyDoesNotApplyTest(policy = SetSmsPermissionGranted.class)
    public void grantSmsPermission_doesNotApplyToUser_permissionIsNotGranted() {
        int existingGrantState = sDeviceState.dpc().devicePolicyManager()
                .getPermissionGrantState(sDeviceState.dpc().componentName(),
                        sTestApp.packageName(), READ_SMS);
        sTestApp.pkg().denyPermission(READ_SMS);

        try {
            sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                    sDeviceState.dpc().componentName(), sTestApp.packageName(),
                    READ_SMS, PERMISSION_GRANT_STATE_GRANTED);

            assertWithMessage("Permission should not be granted but was").that(
                    sTestApp.pkg().hasPermission(READ_SMS)).isFalse();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                    sDeviceState.dpc().componentName(), sTestApp.packageName(),
                    READ_SMS, existingGrantState);
        }
    }

    @Ignore("b/322955833")
    @PolicyDoesNotApplyTest(policy = SetSensorPermissionGranted.class)
    public void grantSensorPermission_doesNotApplyToUser_permissionIsNotGranted(
            @SensorPermissionTestParameter String permission) {
        int existingGrantState = sDeviceState.dpc().devicePolicyManager()
                .getPermissionGrantState(sDeviceState.dpc().componentName(),
                        sTestApp.packageName(), permission);
        try {
            sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                    sDeviceState.dpc().componentName(), sTestApp.packageName(),
                    permission, PERMISSION_GRANT_STATE_GRANTED);

            assertWithMessage("Permission should not be granted but was").that(
                    sTestApp.pkg().hasPermission(permission)).isFalse();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                    sDeviceState.dpc().componentName(), sTestApp.packageName(),
                    permission, existingGrantState);
        }
    }

    @CannotSetPolicyTest(policy = SetSmsPermissionGranted.class, includeNonDeviceAdminStates = false)
    public void grantSmsPermission_cannotBeApplied_returnsTrueButDoesNotSetGrantState() {
        skipTestForFinancedDevice();
        Assume.assumeFalse("Parent throws exception", sDeviceState.dpc().isParentInstance());

        int existingGrantState = sDeviceState.dpc().devicePolicyManager()
                .getPermissionGrantState(sDeviceState.dpc().componentName(),
                        sTestApp.packageName(), READ_SMS);
        try {
            boolean wasSet =
                    sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            READ_SMS, PERMISSION_GRANT_STATE_GRANTED);

            assertWithMessage("setPermissionGrantState did not return true")
                    .that(wasSet).isTrue();
            assertWithMessage("Permission grant state should not be set to granted but was")
                    .that(sDeviceState.dpc().devicePolicyManager().getPermissionGrantState(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            READ_SMS))
                    .isNotEqualTo(PERMISSION_GRANT_STATE_GRANTED);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                    sDeviceState.dpc().componentName(), sTestApp.packageName(),
                    READ_SMS, existingGrantState);
        }
    }

    @AdditionalQueryParameters(
            forTestApp = "dpc",
            query = @Query(targetSdkVersion =
            @IntegerQuery(isLessThan = U))
    )
    @CannotSetPolicyTest(policy = SetSensorPermissionGranted.class)
    public void grantSensorPermission_cannotBeApplied_returnsTrueButDoesNotSetGrantState(
            @SensorPermissionTestParameter String permission) {
        skipTestForFinancedDevice();
        Assume.assumeFalse("Parent throws exception", sDeviceState.dpc().isParentInstance());

        int existingGrantState = sDeviceState.dpc().devicePolicyManager()
                .getPermissionGrantState(sDeviceState.dpc().componentName(),
                        sTestApp.packageName(), permission);
        assumeFalse(existingGrantState == PERMISSION_GRANT_STATE_GRANTED);

        try {
            boolean wasSet =
                    sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            permission, PERMISSION_GRANT_STATE_GRANTED);

            assertWithMessage("setPermissionGrantState did not return true")
                    .that(wasSet).isTrue();
            assertWithMessage("Permission grant state should not be set to granted but was")
                    .that(sDeviceState.dpc().devicePolicyManager().getPermissionGrantState(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            permission))
                    .isNotEqualTo(PERMISSION_GRANT_STATE_GRANTED);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                    sDeviceState.dpc().componentName(), sTestApp.packageName(),
                    permission, existingGrantState);
        }
    }

    @AdditionalQueryParameters(
            forTestApp = "dpc",
            query = @Query(targetSdkVersion =
            @IntegerQuery(isGreaterThanOrEqualTo = U))
    )
    @Ignore("b/273496614 - Reenable once the unicorn APIs are unflagged.")
    @CannotSetPolicyTest(policy = SetSensorPermissionGranted.class)
    public void grantSensorPermission_cannotBeApplied_throwsSecurityException(
            @SensorPermissionTestParameter String permission) {
        assertThrows(SecurityException.class, () ->
                sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                    sDeviceState.dpc().componentName(), sTestApp.packageName(),
                    permission, PERMISSION_GRANT_STATE_GRANTED
                )
        );
    }

    @CannotSetPolicyTest(policy = SetPermissionGrantState.class)
    public void getPermissionGrantState_notAllowed_throwsException() {
        assertThrows(SecurityException.class, () -> {
            sDeviceState.dpc().devicePolicyManager().getPermissionGrantState(
                    sDeviceState.dpc().componentName(), sTestApp.packageName(),
                    GRANTABLE_PERMISSION);
        });
    }

    @CannotSetPolicyTest(policy = SetPermissionPolicy.class)
    public void setPermissionPolicy_notAllowed_throwsException() {
        assertThrows(SecurityException.class, () -> {
            sDeviceState.dpc().devicePolicyManager().setPermissionPolicy(
                    sDeviceState.dpc().componentName(), PERMISSION_POLICY_AUTO_GRANT);
        });
    }

    @CanSetPolicyTest(policy = SetPermissionPolicy.class)
    public void setPermissionPolicy_setsPolicy(@IntTestParameter(
            {PERMISSION_POLICY_AUTO_GRANT, PERMISSION_POLICY_AUTO_DENY}) int policy) {
        try {
            sDeviceState.dpc().devicePolicyManager().setPermissionPolicy(
                    sDeviceState.dpc().componentName(), policy);

            assertThat(sDeviceState.dpc().devicePolicyManager().getPermissionPolicy(
                            sDeviceState.dpc().componentName())).isEqualTo(policy);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setPermissionPolicy(
                    sDeviceState.dpc().componentName(), PERMISSION_POLICY_PROMPT);
        }
    }

    @PolicyAppliesTest(policy = SetPermissionPolicy.class)
    @EnsureScreenIsOn
    @EnsureUnlocked
    public void setPermissionPolicy_grant_automaticallyGrantsPermissions() {
        assumeTrue("Test requires showing activities",
                TestApis.users().instrumented().canShowActivities());
        try (TestAppInstance testApp = sNotInstalledTestApp.install()) {
            // We install fresh so the permissions are not granted
            sDeviceState.dpc().devicePolicyManager().setPermissionPolicy(
                    sDeviceState.dpc().componentName(), PERMISSION_POLICY_AUTO_GRANT);

            TestAppActivity activity = testApp.activities().any().start().activity();
            activity.requestPermissions(new String[]{GRANTABLE_PERMISSION}, /* requestCode= */ 0);

            Poll.forValue("Permission granted",
                    () -> sNotInstalledTestApp.pkg().hasPermission(GRANTABLE_PERMISSION))
                    .toBeEqualTo(true)
                    .errorOnFail()
                    .await();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setPermissionPolicy(
                    sDeviceState.dpc().componentName(), PERMISSION_POLICY_PROMPT);
        }
    }

    @PolicyAppliesTest(policy = SetPermissionPolicy.class)
    @EnsureScreenIsOn
    @EnsureUnlocked
    public void setPermissionPolicy_deny_automaticallyDeniesPermissions() {
        assumeTrue("Test requires showing activities",
                TestApis.users().instrumented().canShowActivities());
        try (TestAppInstance testApp = sNotInstalledTestApp.install()) {
            // We install fresh so the permissions are not granted
            sDeviceState.dpc().devicePolicyManager().setPermissionPolicy(
                    sDeviceState.dpc().componentName(), PERMISSION_POLICY_AUTO_DENY);

            TestAppActivity activity = testApp.activities().any().start().activity();
            activity.requestPermissions(new String[]{GRANTABLE_PERMISSION}, /* requestCode= */ 0);

            Poll.forValue("Permission not granted",
                    () -> sNotInstalledTestApp.pkg().hasPermission(GRANTABLE_PERMISSION))
                    .toBeEqualTo(false)
                    .errorOnFail()
                    .await();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setPermissionPolicy(
                    sDeviceState.dpc().componentName(), PERMISSION_POLICY_PROMPT);
        }
    }

    @PolicyAppliesTest(policy = SetSensorPermissionGranted.class)
    @NotificationsTest
    public void grantLocationPermission_userNotified(
            @LocationPermissionTestParameter String permission) {
        int existingGrantState = sDeviceState.dpc().devicePolicyManager()
                .getPermissionGrantState(sDeviceState.dpc().componentName(),
                        sTestApp.packageName(), permission);
        sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                sDeviceState.dpc().componentName(), sTestApp.packageName(),
                permission, PERMISSION_GRANT_STATE_DEFAULT);
        try (NotificationListener notifications = TestApis.notifications().createListener()) {
            sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                    sDeviceState.dpc().componentName(), sTestApp.packageName(),
                    permission, PERMISSION_GRANT_STATE_GRANTED);

            NotificationListenerQuerySubject.assertThat(notifications.query()
                    .wherePackageName().isEqualTo(PERMISSION_CONTROLLER_PACKAGE_NAME)
                    .whereNotification().channelId().isEqualTo(
                            AUTO_GRANTED_PERMISSIONS_CHANNEL_ID)
            ).wasPosted();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                    sDeviceState.dpc().componentName(), sTestApp.packageName(),
                    permission, existingGrantState);
        }
    }

    @CanSetPolicyTest(policy = SetPermissionGrantState.class)
    public void setPermissionGrantState_permissionIsNotDeclared_doesNotSetGrantState() {
        try {
            boolean wasSet = sDeviceState.dpc().devicePolicyManager()
                    .setPermissionGrantState(sDeviceState.dpc().componentName(),
                            sTestApp.packageName(),
                            NOT_DECLARED_PERMISSION, PERMISSION_GRANT_STATE_DENIED);

            assertWithMessage("setPermissionGrantState did not return false")
                    .that(wasSet).isFalse();
            assertWithMessage("Permission grant state should not be changed but was")
                    .that(sDeviceState.dpc().devicePolicyManager().getPermissionGrantState(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            NOT_DECLARED_PERMISSION))
                    .isEqualTo(PERMISSION_GRANT_STATE_DEFAULT);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                    sDeviceState.dpc().componentName(), sTestApp.packageName(),
                    NOT_DECLARED_PERMISSION, PERMISSION_GRANT_STATE_DEFAULT);
        }
    }

    @PolicyAppliesTest(policy = SetPermissionPolicy.class)
    public void setPermissionGrantStateDeny_autoGrantPermission_deniesPermissions(
            @DeniablePermissionTestParameter String permission) {
        assumeTrue("Test requires showing activities",
                TestApis.users().instrumented().canShowActivities());
        try (TestAppInstance testApp = sNotInstalledTestApp.install()) {
            // We install fresh so the permissions are not granted
            sDeviceState.dpc().devicePolicyManager()
                    .setPermissionGrantState(sDeviceState.dpc().componentName(),
                            sNotInstalledTestApp.packageName(),
                            permission, PERMISSION_GRANT_STATE_DENIED);
            sDeviceState.dpc().devicePolicyManager().setPermissionPolicy(
                    sDeviceState.dpc().componentName(), PERMISSION_POLICY_AUTO_GRANT);

            TestAppActivity activity = testApp.activities().any().start().activity();
            activity.requestPermissions(new String[]{ permission }, /* requestCode= */ 0);

            Poll.forValue("Permission not granted",
                    () -> sNotInstalledTestApp.pkg().hasPermission(permission))
                    .toBeEqualTo(false)
                    .errorOnFail()
                    .await();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                    sDeviceState.dpc().componentName(), sNotInstalledTestApp.packageName(),
                    permission, PERMISSION_GRANT_STATE_DEFAULT);
            sDeviceState.dpc().devicePolicyManager().setPermissionPolicy(
                    sDeviceState.dpc().componentName(), PERMISSION_POLICY_PROMPT);
        }
    }

    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setPermissionPolicy")
    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = SetPermissionPolicy.class)
    public void setPermissionPolicy_sensorPermissions_autoGrantPermission_denies(
            @SensorPermissionTestParameter String permission) {
        assumeTrue("Test requires showing activities",
                TestApis.users().instrumented().canShowActivities());
        // The IT admin cannot grant sensor permissions on work profile devices (PO).
        try (TestAppInstance testApp = sNotInstalledTestApp.install()) {
            // We install fresh so the permissions are not granted
            sDeviceState.dpc().devicePolicyManager().setPermissionPolicy(
                    sDeviceState.dpc().componentName(), PERMISSION_POLICY_AUTO_GRANT);

            TestAppActivity activity = testApp.activities().any().start().activity();
            activity.requestPermissions(new String[]{ permission }, /* requestCode= */ 0);

            Poll.forValue("Permission not granted",
                            () -> sNotInstalledTestApp.pkg().hasPermission(permission))
                    .toBeEqualTo(false)
                    .errorOnFail()
                    .await();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setPermissionPolicy(
                    sDeviceState.dpc().componentName(), PERMISSION_POLICY_PROMPT);
        }
    }

    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setPermissionPolicy")
    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = SetPermissionPolicy.class)
    public void setPermissionPolicy_smsPermission_autoGrantPermission_denies() {
        assumeTrue("Test requires showing activities",
                TestApis.users().instrumented().canShowActivities());
        // The IT admin cannot grant sms permission on work profile devices (PO).
        try (TestAppInstance testApp = sNotInstalledTestApp.install()) {
            // We install fresh so the permissions are not granted
            sDeviceState.dpc().devicePolicyManager().setPermissionPolicy(
                    sDeviceState.dpc().componentName(), PERMISSION_POLICY_AUTO_GRANT);

            TestAppActivity activity = testApp.activities().any().start().activity();
            activity.requestPermissions(new String[]{ READ_SMS }, /* requestCode= */ 0);

            Poll.forValue("Permission not granted",
                            () -> sNotInstalledTestApp.pkg().hasPermission(READ_SMS))
                    .toBeEqualTo(false)
                    .errorOnFail()
                    .await();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setPermissionPolicy(
                    sDeviceState.dpc().componentName(), PERMISSION_POLICY_PROMPT);
        }
    }

    @PolicyAppliesTest(policy = SetPermissionPolicy.class)
    public void setPermissionGrantStateDeny_autoDenyPermission_deniesPermissions(
            @DeniablePermissionTestParameter String permission) {
        assumeTrue("Test requires showing activities",
                TestApis.users().instrumented().canShowActivities());
        try (TestAppInstance testApp = sNotInstalledTestApp.install()) {
            // We install fresh so the permissions are not granted
            sDeviceState.dpc().devicePolicyManager()
                    .setPermissionGrantState(sDeviceState.dpc().componentName(),
                            sNotInstalledTestApp.packageName(),
                            permission, PERMISSION_GRANT_STATE_DENIED);
            sDeviceState.dpc().devicePolicyManager().setPermissionPolicy(
                    sDeviceState.dpc().componentName(), PERMISSION_POLICY_AUTO_DENY);

            TestAppActivity activity = testApp.activities().any().start().activity();
            activity.requestPermissions(new String[]{ permission }, /* requestCode= */ 0);

            Poll.forValue("Permission granted",
                    () -> sNotInstalledTestApp.pkg().hasPermission(permission))
                    .toBeEqualTo(false)
                    .errorOnFail()
                    .await();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                    sDeviceState.dpc().componentName(), sNotInstalledTestApp.packageName(),
                    permission, PERMISSION_GRANT_STATE_DEFAULT);
            sDeviceState.dpc().devicePolicyManager().setPermissionPolicy(
                    sDeviceState.dpc().componentName(), PERMISSION_POLICY_PROMPT);
        }
    }

    @PolicyAppliesTest(policy = SetPermissionPolicy.class)
    @EnsureScreenIsOn
    @EnsureUnlocked
    public void setPermissionGrantStateDeny_promptPermission_deniesPermissions() {
        assumeTrue("Test requires showing activities",
                TestApis.users().instrumented().canShowActivities());
        try (TestAppInstance testApp = sNotInstalledTestApp.install()) {
            // We install fresh so the permissions are not granted
            sDeviceState.dpc().devicePolicyManager()
                    .setPermissionGrantState(sDeviceState.dpc().componentName(),
                            sNotInstalledTestApp.packageName(),
                            ACCESS_FINE_LOCATION, PERMISSION_GRANT_STATE_DENIED);
            sDeviceState.dpc().devicePolicyManager().setPermissionPolicy(
                    sDeviceState.dpc().componentName(), PERMISSION_POLICY_PROMPT);

            TestAppActivity activity = testApp.activities().any().start().activity();
            activity.requestPermissions(
                    new String[]{ ACCESS_FINE_LOCATION }, /* requestCode= */ 0);

            Poll.forValue("Permission granted",
                    () -> sNotInstalledTestApp.pkg().hasPermission(ACCESS_FINE_LOCATION))
                    .toBeEqualTo(false)
                    .errorOnFail()
                    .await();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                    sDeviceState.dpc().componentName(), sNotInstalledTestApp.packageName(),
                    ACCESS_FINE_LOCATION, PERMISSION_GRANT_STATE_DEFAULT);
        }
    }

    @PolicyAppliesTest(policy = SetPermissionPolicy.class)
    public void setPermissionStateGranted_autoDenyPermission_grantsPermissions() {
        assumeTrue("Test requires showing activities",
                TestApis.users().instrumented().canShowActivities());
        try (TestAppInstance testApp = sNotInstalledTestApp.install()) {
            // We install fresh so the permissions are not granted
            sDeviceState.dpc().devicePolicyManager()
                    .setPermissionGrantState(sDeviceState.dpc().componentName(),
                            testApp.packageName(),
                            GRANTABLE_PERMISSION, PERMISSION_GRANT_STATE_GRANTED);
            sDeviceState.dpc().devicePolicyManager().setPermissionPolicy(
                    sDeviceState.dpc().componentName(), PERMISSION_POLICY_AUTO_DENY);

            TestAppActivity activity = testApp.activities().any().start().activity();
            activity.requestPermissions(
                    new String[]{ GRANTABLE_PERMISSION }, /* requestCode= */ 0);

            Poll.forValue("Permission granted",
                            () -> sNotInstalledTestApp.pkg().hasPermission(GRANTABLE_PERMISSION))
                    .toBeEqualTo(true)
                    .errorOnFail()
                    .await();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                    sDeviceState.dpc().componentName(), sNotInstalledTestApp.packageName(),
                    GRANTABLE_PERMISSION, PERMISSION_GRANT_STATE_DEFAULT);
            sDeviceState.dpc().devicePolicyManager().setPermissionPolicy(
                    sDeviceState.dpc().componentName(), PERMISSION_POLICY_PROMPT);
        }
    }

    @PolicyAppliesTest(policy = SetPermissionPolicy.class)
    @EnsureScreenIsOn
    @EnsureUnlocked
    public void setPermissionStateGranted_promptPermission_grantsPermissions() {
        assumeTrue("Test requires showing activities",
                TestApis.users().instrumented().canShowActivities());
        try (TestAppInstance testApp = sNotInstalledTestApp.install()) {
            // We install fresh so the permissions are not granted
            sDeviceState.dpc().devicePolicyManager()
                    .setPermissionGrantState(sDeviceState.dpc().componentName(),
                            sNotInstalledTestApp.packageName(),
                            GRANTABLE_PERMISSION, PERMISSION_GRANT_STATE_GRANTED);
            sDeviceState.dpc().devicePolicyManager().setPermissionPolicy(
                    sDeviceState.dpc().componentName(), PERMISSION_POLICY_PROMPT);

            TestAppActivity activity = testApp.activities().any().start().activity();
            activity.requestPermissions(
                    new String[]{ GRANTABLE_PERMISSION },  /* requestCode= */ 0);

            Poll.forValue("Permission granted",
                            () -> sNotInstalledTestApp.pkg().hasPermission(GRANTABLE_PERMISSION))
                    .toBeEqualTo(true)
                    .errorOnFail()
                    .await();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                    sDeviceState.dpc().componentName(), sNotInstalledTestApp.packageName(),
                    GRANTABLE_PERMISSION, PERMISSION_GRANT_STATE_DEFAULT);
        }
    }

    @AdditionalQueryParameters(
            forTestApp = "dpc",
            query = @Query(targetSdkVersion =
            @IntegerQuery(isLessThan = U))
    )
    @PolicyAppliesTest(
            policy = SetSensorPermissionPolicyPromptForOrganizationOwnedWorkProfile.class)
    public void setSensorPermissionStateGranted_promptPermission_denyAsPermissionCantBeGrantedAutomatically(
            @SensorPermissionTestParameter String permission) {
        assumeTrue("Test requires showing activities",
                TestApis.users().instrumented().canShowActivities());
        try (TestAppInstance testApp = sNotInstalledTestApp.install()) {
            // We install fresh so the permissions are not granted
            sDeviceState.dpc().devicePolicyManager()
                    .setPermissionGrantState(sDeviceState.dpc().componentName(),
                            sNotInstalledTestApp.packageName(),
                            permission, PERMISSION_GRANT_STATE_GRANTED);
            sDeviceState.dpc().devicePolicyManager().setPermissionPolicy(
                    sDeviceState.dpc().componentName(), PERMISSION_POLICY_PROMPT);

            TestAppActivity activity = testApp.activities().any().start().activity();
            activity.requestPermissions(new String[]{ permission },  /* requestCode= */ 0);

            Poll.forValue("Permission granted",
                            () -> sNotInstalledTestApp.pkg().hasPermission(permission))
                    .toBeEqualTo(false)
                    .errorOnFail()
                    .await();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                    sDeviceState.dpc().componentName(), sNotInstalledTestApp.packageName(),
                    permission, PERMISSION_GRANT_STATE_DEFAULT);
        }
    }

    @CanSetPolicyTest(policy = SetPermissionGrantState.class)
    public void setPermissionGrantState_appIsNotInstalled_doesNotSetGrantState() {
        try {
            boolean wasSet = sDeviceState.dpc().devicePolicyManager()
                    .setPermissionGrantState(
                            sDeviceState.dpc().componentName(), NON_EXISTING_PACKAGE_NAME,
                            GRANTABLE_PERMISSION, PERMISSION_GRANT_STATE_DENIED);

            assertWithMessage("setPermissionGrantState did not return false")
                    .that(wasSet).isFalse();
            assertWithMessage("Permission grant state should not be changed but was")
                    .that(sDeviceState.dpc().devicePolicyManager().getPermissionGrantState(
                            sDeviceState.dpc().componentName(), NON_EXISTING_PACKAGE_NAME,
                            GRANTABLE_PERMISSION))
                    .isEqualTo(PERMISSION_GRANT_STATE_DEFAULT);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                    sDeviceState.dpc().componentName(), sTestApp.packageName(),
                    GRANTABLE_PERMISSION, PERMISSION_GRANT_STATE_DEFAULT);
        }
    }

    @CanSetPolicyTest(policy = SetPermissionGrantState.class)
    public void setPermissionGrantStateDefault_wasPreviouslyGranted_permissionStaysGranted() {
        int existingGrantState = sDeviceState.dpc().devicePolicyManager()
                .getPermissionGrantState(sDeviceState.dpc().componentName(),
                        sTestApp.packageName(), GRANTABLE_PERMISSION);

        try {
            sDeviceState.dpc().devicePolicyManager()
                    .setPermissionGrantState(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            GRANTABLE_PERMISSION, PERMISSION_GRANT_STATE_GRANTED);
            boolean wasSet = sDeviceState.dpc().devicePolicyManager()
                    .setPermissionGrantState(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            GRANTABLE_PERMISSION, PERMISSION_GRANT_STATE_DEFAULT);

            assertWithMessage("setPermissionGrantState did not return true")
                    .that(wasSet).isTrue();
            assertWithMessage("Permission grant state should be set to default but was not")
                    .that(sDeviceState.dpc().devicePolicyManager().getPermissionGrantState(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            GRANTABLE_PERMISSION))
                    .isEqualTo(PERMISSION_GRANT_STATE_DEFAULT);
            assertWithMessage("Permission should be granted but was not").that(
                    sTestApp.pkg().hasPermission(GRANTABLE_PERMISSION)).isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                    sDeviceState.dpc().componentName(), sTestApp.packageName(),
                    GRANTABLE_PERMISSION, existingGrantState);
        }
    }

    @CanSetPolicyTest(policy = SetPermissionGrantState.class)
    public void setPermissionGrantStateDefault_wasPreviouslyDenied_permissionStaysDenied() {
        int existingGrantState = sDeviceState.dpc().devicePolicyManager()
                .getPermissionGrantState(sDeviceState.dpc().componentName(),
                        sTestApp.packageName(), GRANTABLE_PERMISSION);

        try {
            sDeviceState.dpc().devicePolicyManager()
                    .setPermissionGrantState(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            GRANTABLE_PERMISSION, PERMISSION_GRANT_STATE_DENIED);
            boolean wasSet = sDeviceState.dpc().devicePolicyManager()
                    .setPermissionGrantState(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            GRANTABLE_PERMISSION, PERMISSION_GRANT_STATE_DEFAULT);

            assertWithMessage("setPermissionGrantState did not return true")
                    .that(wasSet).isTrue();
            assertWithMessage("Permission grant state should be set to default but was not")
                    .that(sDeviceState.dpc().devicePolicyManager().getPermissionGrantState(
                            sDeviceState.dpc().componentName(), sTestApp.packageName(),
                            GRANTABLE_PERMISSION))
                    .isEqualTo(PERMISSION_GRANT_STATE_DEFAULT);
            assertWithMessage("Permission should be denied but was not").that(
                    sTestApp.pkg().hasPermission(GRANTABLE_PERMISSION)).isFalse();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                    sDeviceState.dpc().componentName(), sTestApp.packageName(),
                    GRANTABLE_PERMISSION, existingGrantState);
        }
    }

    @Test
    @IncludeRunOnFinancedDeviceOwnerUser
    public void grantReadPhoneStatePermission_setsGrantState() {
        int existingGrantState = sDeviceState.dpc().devicePolicyManager()
                .getPermissionGrantState(sDeviceState.dpc().componentName(),
                        sDeviceState.dpc().packageName(), READ_PHONE_STATE);

        try {
            boolean wasSet = sDeviceState.dpc().devicePolicyManager()
                    .setPermissionGrantState(
                            sDeviceState.dpc().componentName(), sDeviceState.dpc().packageName(),
                            READ_PHONE_STATE, PERMISSION_GRANT_STATE_GRANTED);

            assertWithMessage("setPermissionGrantState did not return true")
                    .that(wasSet).isTrue();
            assertWithMessage("Permission grant state should be set to granted but was not")
                    .that(sDeviceState.dpc().devicePolicyManager().getPermissionGrantState(
                            sDeviceState.dpc().componentName(), sDeviceState.dpc().packageName(),
                            READ_PHONE_STATE))
                    .isEqualTo(PERMISSION_GRANT_STATE_GRANTED);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setPermissionGrantState(
                    sDeviceState.dpc().componentName(), sDeviceState.dpc().packageName(),
                    READ_PHONE_STATE, existingGrantState);
        }
    }

    @EnsureGlobalSettingSet(key =
            Settings.Global.ALLOW_WORK_PROFILE_TELEPHONY_FOR_NON_DPM_ROLE_HOLDERS, value = "1")
    @RequireRunOnWorkProfile(isOrganizationOwned = true)
    @Test
    @Ignore("b/300397938")
    public void grantSmsPermission_orgOwnedDeviceWithManagedSubscriptionsPolicySet_granted() {
        RemoteDevicePolicyManager devicePolicyManager = sDeviceState.profileOwner(
                WORK_PROFILE).devicePolicyManager();
        ComponentName componentName = sDeviceState.profileOwner(WORK_PROFILE).componentName();
        int existingGrantState = devicePolicyManager.getPermissionGrantState(componentName,
                sTestApp.packageName(), READ_SMS);
        try {
            devicePolicyManager.setManagedSubscriptionsPolicy(new ManagedSubscriptionsPolicy(
                    ManagedSubscriptionsPolicy.TYPE_ALL_MANAGED_SUBSCRIPTIONS));

            boolean wasSet = devicePolicyManager.setPermissionGrantState(componentName,
                    sTestApp.packageName(), READ_SMS, PERMISSION_GRANT_STATE_GRANTED);

            assertWithMessage("setPermissionGrantState did not return true").that(wasSet).isTrue();
            assertWithMessage("Permission should be granted but was not")
                    .that(sTestApp.pkg().hasPermission(READ_SMS)).isTrue();
        } finally {
            devicePolicyManager.setPermissionGrantState(componentName, sTestApp.packageName(),
                    READ_SMS, existingGrantState);
            devicePolicyManager.setManagedSubscriptionsPolicy(new ManagedSubscriptionsPolicy(
                    ManagedSubscriptionsPolicy.TYPE_ALL_PERSONAL_SUBSCRIPTIONS));
        }
    }

    @ApiTest(apis = "android.app.admin.DevicePolicyManager#setPermissionPolicy")
    @Postsubmit(reason = "new test")
    @PolicyAppliesTest(policy = SetPermissionPolicy.class)
    public void setPermissionPolicy_autoGrant_checkMultiplePermissionsInGroup_allPermissionsGranted() {
        assumeTrue("Test requires showing activities",
                TestApis.users().instrumented().canShowActivities());
        int existingPermissionPolicy =
                sDeviceState.dpc().devicePolicyManager().getPermissionPolicy(
                        sDeviceState.dpc().componentName());

        try (TestAppInstance testAppInstance = sNotInstalledTestApp.install()) {
            sDeviceState.dpc().devicePolicyManager().setPermissionPolicy(
                    sDeviceState.dpc().componentName(), PERMISSION_POLICY_AUTO_GRANT);
            TestAppActivity activity = testAppInstance.activities().any().start().activity();
            activity.requestPermissions(
                    new String[]{ PERMISSION_A, PERMISSION_B }, /* requestCode= */ 0);

            Poll.forValue("Granted permission: " + PERMISSION_A, () ->
                            sNotInstalledTestApp.pkg().hasPermission(PERMISSION_A))
                    .toBeEqualTo(true)
                    .errorOnFail()
                    .await();
            Poll.forValue("Granted permission: " + PERMISSION_B, () ->
                            sNotInstalledTestApp.pkg().hasPermission(PERMISSION_B))
                    .toBeEqualTo(true)
                    .errorOnFail()
                    .await();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setPermissionPolicy(
                    sDeviceState.dpc().componentName(), existingPermissionPolicy);
        }
    }

    private void skipTestForFinancedDevice() {
        DeviceOwner deviceOwner = TestApis.devicePolicy().getDeviceOwner();

        // TODO(): Determine a pattern to special case states so that they are not considered in
        //  tests.
        assumeFalse(deviceOwner != null && deviceOwner.getType() == DeviceOwnerType.FINANCED);
    }
}
