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

import static android.app.admin.DevicePolicyIdentifiers.getIdentifierForUserRestriction;

import static com.android.bedstead.harrier.annotations.enterprise.MostImportantCoexistenceTest.LESS_IMPORTANT;
import static com.android.bedstead.harrier.annotations.enterprise.MostImportantCoexistenceTest.MORE_IMPORTANT;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;
import static org.testng.Assert.assertThrows;

import android.app.admin.PolicyState;
import android.app.admin.UserRestrictionPolicyKey;
import android.devicepolicy.cts.utils.PolicyEngineUtils;
import android.os.SystemClock;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.StringTestParameter;
import com.android.bedstead.harrier.annotations.enterprise.CanSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.MostImportantCoexistenceTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyAppliesTest;
import com.android.bedstead.harrier.annotations.parameterized.IncludeRunOnSystemDeviceOwnerUser;
import com.android.bedstead.harrier.annotations.parameterized.IncludeRunOnUnaffiliatedProfileOwnerAdditionalUser;
import com.android.bedstead.harrier.policies.AffiliatedProfileOwnerOnlyUserRestrictions;
import com.android.bedstead.harrier.policies.UserRestrictions;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.devicepolicy.DeviceOwner;
import com.android.bedstead.nene.devicepolicy.DeviceOwnerType;
import com.android.bedstead.nene.userrestrictions.CommonUserRestrictions;
import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.BlockingBroadcastReceiver;

import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@RunWith(BedsteadJUnit4.class)
public final class UserRestrictionsTest {

    @StringTestParameter({
            CommonUserRestrictions.DISALLOW_FACTORY_RESET,
            CommonUserRestrictions.DISALLOW_FUN,
            CommonUserRestrictions.DISALLOW_CREATE_WINDOWS})
    @Retention(RetentionPolicy.RUNTIME)
    private @interface DeviceOwnerOnlyUserRestrictions {
    }

    @StringTestParameter({
            CommonUserRestrictions.DISALLOW_INSTALL_APPS,
            CommonUserRestrictions.DISALLOW_UNINSTALL_APPS,
            CommonUserRestrictions.DISALLOW_CONFIG_CREDENTIALS,
            CommonUserRestrictions.DISALLOW_CONFIG_VPN,
            CommonUserRestrictions.DISALLOW_APPS_CONTROL,
            CommonUserRestrictions.DISALLOW_ADJUST_VOLUME,
            CommonUserRestrictions.DISALLOW_CROSS_PROFILE_COPY_PASTE,
            CommonUserRestrictions.DISALLOW_OUTGOING_BEAM,
            CommonUserRestrictions.ALLOW_PARENT_PROFILE_APP_LINKING,
            CommonUserRestrictions.DISALLOW_SET_USER_ICON,
            CommonUserRestrictions.DISALLOW_AUTOFILL,
            CommonUserRestrictions.DISALLOW_UNIFIED_PASSWORD
    })
    @Retention(RetentionPolicy.RUNTIME)
    private @interface AllDpcAllowedUserRestrictions {
    }

    @StringTestParameter(value = {
            CommonUserRestrictions.DISALLOW_CONFIG_WIFI,
            CommonUserRestrictions.DISALLOW_CONFIG_LOCALE,
            CommonUserRestrictions.DISALLOW_MODIFY_ACCOUNTS,
            CommonUserRestrictions.DISALLOW_INSTALL_APPS,
            CommonUserRestrictions.DISALLOW_UNINSTALL_APPS,
            CommonUserRestrictions.DISALLOW_SHARE_LOCATION,
            CommonUserRestrictions.DISALLOW_INSTALL_UNKNOWN_SOURCES,
            CommonUserRestrictions.DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY,
            CommonUserRestrictions.DISALLOW_CONFIG_BLUETOOTH,
            CommonUserRestrictions.DISALLOW_BLUETOOTH,
            CommonUserRestrictions.DISALLOW_BLUETOOTH_SHARING,
            CommonUserRestrictions.DISALLOW_USB_FILE_TRANSFER,
            CommonUserRestrictions.DISALLOW_CONFIG_CREDENTIALS,
            CommonUserRestrictions.DISALLOW_REMOVE_USER,
            CommonUserRestrictions.DISALLOW_REMOVE_MANAGED_PROFILE,
            CommonUserRestrictions.DISALLOW_DEBUGGING_FEATURES,
            CommonUserRestrictions.DISALLOW_CONFIG_VPN,
            CommonUserRestrictions.DISALLOW_CONFIG_DATE_TIME,
            CommonUserRestrictions.DISALLOW_CONFIG_TETHERING,
            CommonUserRestrictions.DISALLOW_NETWORK_RESET,
            CommonUserRestrictions.DISALLOW_FACTORY_RESET,
            CommonUserRestrictions.DISALLOW_ADD_USER,
            CommonUserRestrictions.DISALLOW_ADD_MANAGED_PROFILE,
            CommonUserRestrictions.DISALLOW_ADD_CLONE_PROFILE,
            CommonUserRestrictions.DISALLOW_ADD_PRIVATE_PROFILE,
            CommonUserRestrictions.ENSURE_VERIFY_APPS,
            CommonUserRestrictions.DISALLOW_CONFIG_CELL_BROADCASTS,
            CommonUserRestrictions.DISALLOW_CONFIG_MOBILE_NETWORKS,
            CommonUserRestrictions.DISALLOW_APPS_CONTROL,
            CommonUserRestrictions.DISALLOW_MOUNT_PHYSICAL_MEDIA,
            CommonUserRestrictions.DISALLOW_UNMUTE_MICROPHONE,
            CommonUserRestrictions.DISALLOW_ADJUST_VOLUME,
            CommonUserRestrictions.DISALLOW_FUN,
            CommonUserRestrictions.DISALLOW_CREATE_WINDOWS,
            CommonUserRestrictions.DISALLOW_SYSTEM_ERROR_DIALOGS,
            CommonUserRestrictions.DISALLOW_CROSS_PROFILE_COPY_PASTE,
            CommonUserRestrictions.DISALLOW_OUTGOING_BEAM,
            CommonUserRestrictions.DISALLOW_WALLPAPER,
            CommonUserRestrictions.DISALLOW_SAFE_BOOT,
            CommonUserRestrictions.ALLOW_PARENT_PROFILE_APP_LINKING,
            CommonUserRestrictions.DISALLOW_RECORD_AUDIO,
            CommonUserRestrictions.DISALLOW_CAMERA,
            CommonUserRestrictions.DISALLOW_RUN_IN_BACKGROUND,
            CommonUserRestrictions.DISALLOW_DATA_ROAMING,
            CommonUserRestrictions.DISALLOW_SET_USER_ICON,
            CommonUserRestrictions.DISALLOW_SET_WALLPAPER,
            CommonUserRestrictions.DISALLOW_OEM_UNLOCK,
            CommonUserRestrictions.DISALLOW_UNMUTE_DEVICE,
            CommonUserRestrictions.DISALLOW_AUTOFILL,
            CommonUserRestrictions.DISALLOW_CONTENT_CAPTURE,
            CommonUserRestrictions.DISALLOW_CONTENT_SUGGESTIONS,
            CommonUserRestrictions.DISALLOW_USER_SWITCH,
            CommonUserRestrictions.DISALLOW_UNIFIED_PASSWORD,
            CommonUserRestrictions.DISALLOW_CONFIG_LOCATION,
            CommonUserRestrictions.DISALLOW_AIRPLANE_MODE,
            CommonUserRestrictions.DISALLOW_CONFIG_BRIGHTNESS,
            CommonUserRestrictions.DISALLOW_SHARE_INTO_MANAGED_PROFILE,
            CommonUserRestrictions.DISALLOW_AMBIENT_DISPLAY,
            CommonUserRestrictions.DISALLOW_CONFIG_SCREEN_TIMEOUT,
            CommonUserRestrictions.DISALLOW_PRINTING,
            CommonUserRestrictions.DISALLOW_CONFIG_PRIVATE_DNS,
            CommonUserRestrictions.DISALLOW_MICROPHONE_TOGGLE,
            CommonUserRestrictions.DISALLOW_CAMERA_TOGGLE,
            CommonUserRestrictions.DISALLOW_CHANGE_WIFI_STATE,
            CommonUserRestrictions.DISALLOW_WIFI_TETHERING,
            CommonUserRestrictions.DISALLOW_SHARING_ADMIN_CONFIGURED_WIFI,
            CommonUserRestrictions.DISALLOW_WIFI_DIRECT,
            CommonUserRestrictions.DISALLOW_ADD_WIFI_CONFIG,
            CommonUserRestrictions.DISALLOW_SIM_GLOBALLY
    })
    @Retention(RetentionPolicy.RUNTIME)
    private @interface AllUserRestrictions {
    }

    @StringTestParameter({
            CommonUserRestrictions.DISALLOW_OUTGOING_CALLS,
            CommonUserRestrictions.DISALLOW_SMS
    })
    @Retention(RetentionPolicy.RUNTIME)
    private @interface DefaultSecondaryUserRestrictions {
    }

    private static final String ANY_USER_RESTRICTION = CommonUserRestrictions.DISALLOW_CONFIG_WIFI;

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    @Test
    @IncludeRunOnSystemDeviceOwnerUser
    @IncludeRunOnUnaffiliatedProfileOwnerAdditionalUser
    @Postsubmit(reason = "new test")
    public void getUserRestrictions_allDefaultUserRestrictions_returnFalse(
            @AllUserRestrictions String restriction) {
        assertThat(sDeviceState.dpc().devicePolicyManager()
                .getUserRestrictions(sDeviceState.dpc().componentName())
                .getBoolean(restriction))
                .isFalse();
    }

    @CanSetPolicyTest(policy = UserRestrictions.class)
    @Postsubmit(reason = "new test")
    public void getUserRestrictions_containsAddedRestriction(
            @AllDpcAllowedUserRestrictions String restriction) {
        boolean hasRestrictionOriginally = sDeviceState.dpc()
                .userManager().hasUserRestriction(restriction);

        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), restriction);

            assertThat(sDeviceState.dpc().devicePolicyManager()
                    .getUserRestrictions(sDeviceState.dpc().componentName())
                    .getBoolean(restriction))
                    .isTrue();
        } finally {
            if (!hasRestrictionOriginally) {
                sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                        sDeviceState.dpc().componentName(), restriction);
            }
        }
    }

    @CanSetPolicyTest(policy = UserRestrictions.class)
    @Postsubmit(reason = "new test")
    public void hasUserRestriction_containsAddedRestriction(
            @AllDpcAllowedUserRestrictions String restriction) {
        boolean hasRestrictionOriginally = sDeviceState.dpc()
                .userManager().hasUserRestriction(restriction);

        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), restriction);

            assertThat(sDeviceState.dpc().userManager().hasUserRestriction(restriction)).isTrue();
        } finally {
            if (!hasRestrictionOriginally) {
                sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                        sDeviceState.dpc().componentName(), restriction);
            }
        }
    }

    @CanSetPolicyTest(
            policy = com.android.bedstead.harrier.policies.DeviceOwnerOnlyUserRestrictions.class)
    @Postsubmit(reason = "new test")
    public void hasUserRestriction_clearUserRestriction_restrictionIsRemoved(
            @AllDpcAllowedUserRestrictions String restriction) {
        boolean hasRestrictionOriginally = sDeviceState.dpc()
                .userManager().hasUserRestriction(restriction);

        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), restriction);

            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), restriction);

            assertThat(sDeviceState.dpc().userManager().hasUserRestriction(restriction))
                    .isFalse();
        } finally {
            if (hasRestrictionOriginally) {
                sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                        sDeviceState.dpc().componentName(), restriction);
            } else {
                sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                        sDeviceState.dpc().componentName(), restriction);
            }
        }
    }

    @CanSetPolicyTest(policy = AffiliatedProfileOwnerOnlyUserRestrictions.class)
    @Postsubmit(reason = "new test")
    public void clearDefaultUserRestriction_restrictionIsNotRemoved(
            @DefaultSecondaryUserRestrictions String restriction) {
        boolean hasRestrictionOriginally = sDeviceState.dpc()
                .userManager().hasUserRestriction(restriction);

        try {
            assumeTrue(hasRestrictionOriginally);
            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), restriction);

            assertThat(sDeviceState.dpc().userManager().hasUserRestriction(restriction))
                    .isTrue();
        } catch (SecurityException e) {
            // expect an exception for restrictions that cannot be set by a profile owner
            assertThat(e.getMessage())
                    .isEqualTo("Profile owner cannot set user restriction " + restriction);
        } finally {
            if (hasRestrictionOriginally) {
                try {
                    sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                            sDeviceState.dpc().componentName(), restriction);
                } catch (SecurityException e) {
                    // expect an exception for restrictions that cannot be set by a profile owner
                }
            }
        }
    }

    @CanSetPolicyTest(policy = UserRestrictions.class)
    @Postsubmit(reason = "new test")
    public void getUserRestriction_clearUserRestriction_restrictionIsRemoved(
            @AllDpcAllowedUserRestrictions String restriction) {
        boolean hasRestrictionOriginally = sDeviceState.dpc()
                .userManager().hasUserRestriction(restriction);

        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), restriction);

            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), restriction);

            assertThat(sDeviceState.dpc().devicePolicyManager()
                    .getUserRestrictions(sDeviceState.dpc().componentName())
                    .getBoolean(restriction))
                    .isFalse();
        } finally {
            if (hasRestrictionOriginally) {
                sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                        sDeviceState.dpc().componentName(), restriction);
            } else {
                sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                        sDeviceState.dpc().componentName(), restriction);
            }
        }
    }

    @CannotSetPolicyTest(
            policy = com.android.bedstead.harrier.policies.DeviceOwnerOnlyUserRestrictions.class)
    @Postsubmit(reason = "new test")
    @com.android.bedstead.harrier.annotations.RequireNotHeadlessSystemUserMode(
            reason = "Since ag/I94c63d0492034af39608c3d81700f71e89e37d0e we special case main user "
                    + "which is not taken care of in tests currently")
    public void addUserRestriction_deviceOwnerOnlyRestriction_throwsSecurityException(
            @DeviceOwnerOnlyUserRestrictions String restriction) {
        skipTestForFinancedDevice();

        try {
            assertThrows(SecurityException.class, () -> {
                sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                        sDeviceState.dpc().componentName(), restriction);
            });
        } finally {
            try {
                sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                        sDeviceState.dpc().componentName(), restriction);
            } catch (SecurityException e) {
                // Expected exception if the dpc is not able to change user restrictions
            }
        }
    }

    @PolicyAppliesTest(policy = UserRestrictions.class)
    @Postsubmit(reason = "new test")
    public void addUserRestriction_sendsBroadcastToReceiversInUser() {
        try (BlockingBroadcastReceiver broadcastReceiver =
                     sDeviceState.registerBroadcastReceiver(
                             CommonUserRestrictions.ACTION_USER_RESTRICTIONS_CHANGED)) {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), ANY_USER_RESTRICTION);
            broadcastReceiver.awaitForBroadcastOrFail();
        } finally {
            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), ANY_USER_RESTRICTION);
        }
    }

    @PolicyAppliesTest(policy = UserRestrictions.class)
    @Postsubmit(reason = "new test")
    public void clearUserRestriction_sendsBroadcastToReceiversInUser() {
        sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                sDeviceState.dpc().componentName(), ANY_USER_RESTRICTION);
        try (BlockingBroadcastReceiver broadcastReceiver =
                     sDeviceState.registerBroadcastReceiver(
                             CommonUserRestrictions.ACTION_USER_RESTRICTIONS_CHANGED)) {

            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), ANY_USER_RESTRICTION);
            broadcastReceiver.awaitForBroadcastOrFail();
        }
    }

    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#addUserRestriction"})
    @MostImportantCoexistenceTest(policy = UserRestrictions.class)
    @Ignore("test used for local verification only")
    public void addUserRestriction_setByDPCAndPermission_DPCRemoved_stillEnforced() {
        try {
            sDeviceState.testApp(MORE_IMPORTANT).devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), CommonUserRestrictions.DISALLOW_CAMERA);
            sDeviceState.testApp(LESS_IMPORTANT).devicePolicyManager().addUserRestriction(
                    /* componentName= */ null, CommonUserRestrictions.DISALLOW_CAMERA);

            // Remove DPC
            sDeviceState.dpc().devicePolicyManager().clearDeviceOwnerApp(
                    sDeviceState.dpc().packageName());

            PolicyState<Boolean> policyState = PolicyEngineUtils.getBooleanPolicyState(
                    new UserRestrictionPolicyKey(
                            getIdentifierForUserRestriction(CommonUserRestrictions.DISALLOW_CAMERA),
                            CommonUserRestrictions.DISALLOW_CAMERA),
                    TestApis.users().instrumented().userHandle());
            assertThat(policyState.getCurrentResolvedPolicy()).isTrue();
            assertThat(sDeviceState.testApp(LESS_IMPORTANT).devicePolicyManager()
                    .getUserRestrictions(/* componentName= */ null)
                    .getBoolean(CommonUserRestrictions.DISALLOW_CAMERA))
                    .isTrue();
            assertThat(sDeviceState.testApp(LESS_IMPORTANT).userManager()
                    .hasUserRestriction(CommonUserRestrictions.DISALLOW_CAMERA))
                    .isTrue();
        } finally {
            try {
                sDeviceState.testApp(LESS_IMPORTANT).devicePolicyManager().clearUserRestriction(
                        /* componentName= */ null, CommonUserRestrictions.DISALLOW_CAMERA);
            } catch (Exception e) {
                // expected if app was uninstalled
            }
            try {
                sDeviceState.testApp(MORE_IMPORTANT).devicePolicyManager().clearUserRestriction(
                        sDeviceState.dpc().componentName(), CommonUserRestrictions.DISALLOW_CAMERA);
            } catch (Exception e) {
                // expected if app was uninstalled
            }
        }
    }

    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#addUserRestriction"})
    @MostImportantCoexistenceTest(policy = UserRestrictions.class)
    @Ignore("test used for local verification only")
    public void addUserRestriction_setByPermission_appRemoved_notEnforced() {
        try {
            sDeviceState.testApp(LESS_IMPORTANT).devicePolicyManager().addUserRestriction(
                    /* componentName= */ null, CommonUserRestrictions.DISALLOW_CAMERA);

            // uninstall app
            sDeviceState.testApp(LESS_IMPORTANT).uninstall();
            SystemClock.sleep(500);

            PolicyState<Boolean> policyState = PolicyEngineUtils.getBooleanPolicyState(
                    new UserRestrictionPolicyKey(
                            getIdentifierForUserRestriction(CommonUserRestrictions.DISALLOW_CAMERA),
                            CommonUserRestrictions.DISALLOW_CAMERA),
                    TestApis.users().instrumented().userHandle());
            if (policyState != null) {
                assertThat(policyState.getCurrentResolvedPolicy()).isFalse();
            }
            assertThat(sDeviceState.testApp(MORE_IMPORTANT).userManager()
                    .hasUserRestriction(CommonUserRestrictions.DISALLOW_CAMERA))
                    .isFalse();
        } finally {
            try {
                sDeviceState.testApp(LESS_IMPORTANT).devicePolicyManager().clearUserRestriction(
                        /* componentName= */ null, CommonUserRestrictions.DISALLOW_CAMERA);
            } catch (Exception e) {
                // expected if app was uninstalled
            }
            try {
                sDeviceState.testApp(MORE_IMPORTANT).devicePolicyManager().clearUserRestriction(
                        sDeviceState.dpc().componentName(), CommonUserRestrictions.DISALLOW_CAMERA);
            } catch (Exception e) {
                // expected if app was uninstalled
            }
        }
    }

    private void skipTestForFinancedDevice() {
        DeviceOwner deviceOwner = TestApis.devicePolicy().getDeviceOwner();

        // TODO(): Determine a pattern to special case states so that they are not considered in
        //  tests.
        assumeFalse(deviceOwner != null && deviceOwner.getType() == DeviceOwnerType.FINANCED);
    }
}
