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

import static android.app.admin.DevicePolicyIdentifiers.getIdentifierForUserRestriction;
import static android.app.admin.TargetUser.GLOBAL_USER_ID;

import static com.android.bedstead.nene.userrestrictions.CommonUserRestrictions.DISALLOW_CELLULAR_2G;
import static com.android.bedstead.nene.userrestrictions.CommonUserRestrictions.DISALLOW_CONFIG_CELL_BROADCASTS;
import static com.android.bedstead.nene.userrestrictions.CommonUserRestrictions.DISALLOW_CONFIG_MOBILE_NETWORKS;
import static com.android.bedstead.nene.userrestrictions.CommonUserRestrictions.DISALLOW_DATA_ROAMING;
import static com.android.bedstead.nene.userrestrictions.CommonUserRestrictions.DISALLOW_OUTGOING_CALLS;
import static com.android.bedstead.nene.userrestrictions.CommonUserRestrictions.DISALLOW_SMS;
import static com.android.bedstead.nene.userrestrictions.CommonUserRestrictions.DISALLOW_ULTRA_WIDEBAND_RADIO;
import static com.android.bedstead.nene.userrestrictions.CommonUserRestrictions.DISALLOW_SIM_GLOBALLY;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;
import static org.testng.Assert.assertThrows;

import android.app.admin.PolicyUpdateResult;
import android.content.Context;
import android.devicepolicy.cts.utils.PolicySetResultUtils;
import android.net.Uri;
import android.os.Bundle;
import android.os.UserManager;
import android.telephony.TelephonyManager;
import android.telephony.data.ApnSetting;

import com.android.bedstead.flags.annotations.RequireFlagsEnabled;
import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureDoesNotHaveUserRestriction;
import com.android.bedstead.harrier.annotations.EnsureHasUserRestriction;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.RequireTelephonySupport;
import com.android.bedstead.harrier.annotations.enterprise.CanSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyAppliesTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyDoesNotApplyTest;
import com.android.bedstead.harrier.policies.DisallowCellular2g;
import com.android.bedstead.harrier.policies.DisallowConfigCellBroadcasts;
import com.android.bedstead.harrier.policies.DisallowConfigMobileNetworks;
import com.android.bedstead.harrier.policies.DisallowDataRoaming;
import com.android.bedstead.harrier.policies.DisallowOutgoingCalls;
import com.android.bedstead.harrier.policies.DisallowSms;
import com.android.bedstead.harrier.policies.DisallowUltraWidebandRadio;
import com.android.bedstead.harrier.policies.DisallowUnmanagedSubscriptionsGlobally;
import com.android.bedstead.harrier.policies.OverrideApn;
import com.android.bedstead.nene.TestApis;
import com.android.compatibility.common.util.ApiTest;
import com.android.interactive.annotations.Interactive;

import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.InetAddress;
import java.util.List;

@RunWith(BedsteadJUnit4.class)
@RequireTelephonySupport
public final class TelephonyTest {

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final Context sContext = TestApis.context().instrumentedContext();

    @CannotSetPolicyTest(policy = DisallowConfigCellBroadcasts.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CONFIG_CELL_BROADCASTS")
    public void setUserRestriction_disallowConfigCellBroadcasts_cannotSet_throwsException() {
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                        sDeviceState.dpc().componentName(), DISALLOW_CONFIG_CELL_BROADCASTS));
    }

    @PolicyAppliesTest(policy = DisallowConfigCellBroadcasts.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CONFIG_CELL_BROADCASTS")
    public void setUserRestriction_disallowConfigCellBroadcasts_isSet() {
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_CONFIG_CELL_BROADCASTS);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_CONFIG_CELL_BROADCASTS))
                    .isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_CONFIG_CELL_BROADCASTS);
        }
    }

    @PolicyDoesNotApplyTest(policy = DisallowConfigCellBroadcasts.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CONFIG_CELL_BROADCASTS")
    public void setUserRestriction_disallowConfigCellBroadcasts_isNotSet() {
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_CONFIG_CELL_BROADCASTS);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_CONFIG_CELL_BROADCASTS))
                    .isFalse();
        } finally {

            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_CONFIG_CELL_BROADCASTS);
        }
    }

    @EnsureDoesNotHaveUserRestriction(DISALLOW_CONFIG_CELL_BROADCASTS)
    @Test
    @Postsubmit(reason = "new test")
    @Interactive
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CONFIG_CELL_BROADCASTS")
    public void disallowConfigCellBroadcastsIsNotSet_todo() throws Exception {
        // TODO: Test
    }

    @EnsureHasUserRestriction(DISALLOW_CONFIG_CELL_BROADCASTS)
    @Test
    @Postsubmit(reason = "new test")
    @Interactive
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CONFIG_CELL_BROADCASTS")
    public void disallowConfigCellBroadcasatsIsSet_todo() throws Exception {
        // TODO: Test
    }

    @CannotSetPolicyTest(policy = DisallowConfigMobileNetworks.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CONFIG_MOBILE_NETWORKS")
    public void setUserRestriction_disallowConfigMobileNetworks_cannotSet_throwsException() {
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                        sDeviceState.dpc().componentName(), DISALLOW_CONFIG_MOBILE_NETWORKS));
    }

    @PolicyAppliesTest(policy = DisallowConfigMobileNetworks.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CONFIG_MOBILE_NETWORKS")
    public void setUserRestriction_disallowConfigMobileNetworks_isSet() {
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_CONFIG_MOBILE_NETWORKS);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_CONFIG_MOBILE_NETWORKS))
                    .isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_CONFIG_MOBILE_NETWORKS);
        }
    }

    @PolicyDoesNotApplyTest(policy = DisallowConfigMobileNetworks.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CONFIG_MOBILE_NETWORKS")
    public void setUserRestriction_disallowConfigMobileNetworks_isNotSet() {
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_CONFIG_MOBILE_NETWORKS);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_CONFIG_MOBILE_NETWORKS))
                    .isFalse();
        } finally {

            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_CONFIG_MOBILE_NETWORKS);
        }
    }

    @EnsureDoesNotHaveUserRestriction(DISALLOW_CONFIG_MOBILE_NETWORKS)
    @Test
    @Postsubmit(reason = "new test")
    @Interactive
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CONFIG_MOBILE_NETWORKS")
    public void disallowConfigMobileNetworksIsNotSet_todo() throws Exception {
        // TODO: Test
    }

    @EnsureHasUserRestriction(DISALLOW_CONFIG_MOBILE_NETWORKS)
    @Test
    @Postsubmit(reason = "new test")
    @Interactive
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CONFIG_MOBILE_NETWORKS")
    public void disallowConfigMobileNetworksIsSet_todo() throws Exception {
        // TODO: Test
    }

    @CannotSetPolicyTest(policy = DisallowOutgoingCalls.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_OUTGOING_CALLS")
    public void setUserRestriction_disallowOutgoingCalls_cannotSet_throwsException() {
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                        sDeviceState.dpc().componentName(), DISALLOW_OUTGOING_CALLS));
    }

    @PolicyAppliesTest(policy = DisallowOutgoingCalls.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_OUTGOING_CALLS")
    @Ignore // this test is unclear because DISALLOW_OUTGOING_CALLS is default on secondary users
    public void setUserRestriction_disallowOutgoingCalls_isSet() {
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_OUTGOING_CALLS);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_OUTGOING_CALLS))
                    .isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_OUTGOING_CALLS);
        }
    }

    @PolicyDoesNotApplyTest(policy = DisallowOutgoingCalls.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_OUTGOING_CALLS")
    @Ignore // this test is unclear because DISALLOW_OUTGOING_CALLS is default on secondary users
    public void setUserRestriction_disallowOutgoingCalls_isNotSet() {
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_OUTGOING_CALLS);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_OUTGOING_CALLS))
                    .isFalse();
        } finally {

            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_OUTGOING_CALLS);
        }
    }

    @EnsureDoesNotHaveUserRestriction(DISALLOW_OUTGOING_CALLS)
    @Test
    @Postsubmit(reason = "new test")
    @Interactive
    @ApiTest(apis = "android.os.UserManager#DISALLOW_OUTGOING_CALLS")
    public void disallowOutgoingCallsIsNotSet_todo() throws Exception {
        // TODO: Test
    }

    @EnsureHasUserRestriction(DISALLOW_OUTGOING_CALLS)
    @Test
    @Postsubmit(reason = "new test")
    @Interactive
    @ApiTest(apis = "android.os.UserManager#DISALLOW_OUTGOING_CALLS")
    public void disallowOutgoingCallsIsSet_todo() throws Exception {
        // TODO: Test
    }

    @CannotSetPolicyTest(policy = DisallowSms.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_SMS")
    public void setUserRestriction_disallowSms_cannotSet_throwsException() {
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                        sDeviceState.dpc().componentName(), DISALLOW_SMS));
    }

    @PolicyAppliesTest(policy = DisallowSms.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_SMS")
    @Ignore // this test is unclear because DISALLOW_OUTGOING_CALLS is default on secondary users
    public void setUserRestriction_disallowSms_isSet() {
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_SMS);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_SMS))
                    .isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_SMS);
        }
    }

    @PolicyDoesNotApplyTest(policy = DisallowSms.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_SMS")
    @Ignore // this test is unclear because DISALLOW_OUTGOING_CALLS is default on secondary users
    public void setUserRestriction_disallowSms_isNotSet() {
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_SMS);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_SMS))
                    .isFalse();
        } finally {

            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_SMS);
        }
    }

    @EnsureDoesNotHaveUserRestriction(DISALLOW_SMS)
    @Test
    @Postsubmit(reason = "new test")
    @Interactive
    @ApiTest(apis = "android.os.UserManager#DISALLOW_SMS")
    public void disallowSmsIsNotSet_todo() throws Exception {
        // TODO: Test
    }

    @EnsureHasUserRestriction(DISALLOW_SMS)
    @Test
    @Postsubmit(reason = "new test")
    @Interactive
    @ApiTest(apis = "android.os.UserManager#DISALLOW_SMS")
    public void disallowSmsIsSet_todo() throws Exception {
        // TODO: Test
    }

    @CannotSetPolicyTest(policy = DisallowDataRoaming.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_DATA_ROAMING")
    public void setUserRestriction_disallowDataRoaming_cannotSet_throwsException() {
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                        sDeviceState.dpc().componentName(), DISALLOW_DATA_ROAMING));
    }

    @PolicyAppliesTest(policy = DisallowDataRoaming.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_DATA_ROAMING")
    public void setUserRestriction_disallowDataRoaming_isSet() {
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_DATA_ROAMING);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_DATA_ROAMING))
                    .isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_DATA_ROAMING);
        }
    }

    @PolicyDoesNotApplyTest(policy = DisallowDataRoaming.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_DATA_ROAMING")
    public void setUserRestriction_disallowDataRoaming_isNotSet() {
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_DATA_ROAMING);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_DATA_ROAMING))
                    .isFalse();
        } finally {

            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_DATA_ROAMING);
        }
    }

    @EnsureDoesNotHaveUserRestriction(DISALLOW_DATA_ROAMING)
    @Test
    @Postsubmit(reason = "new test")
    @Interactive
    @ApiTest(apis = "android.os.UserManager#DISALLOW_DATA_ROAMING")
    public void disallowDataRoamingIsNotSet_todo() throws Exception {
        // TODO: Test
    }

    @EnsureHasUserRestriction(DISALLOW_DATA_ROAMING)
    @Test
    @Postsubmit(reason = "new test")
    @Interactive
    @ApiTest(apis = "android.os.UserManager#DISALLOW_DATA_ROAMING")
    public void disallowDataRoamingIsSet_todo() throws Exception {
        // TODO: Test
    }

    @CannotSetPolicyTest(policy = DisallowCellular2g.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CELLULAR_2G")
    public void setUserRestriction_disallowCellular2g_cannotSet_throwsException() {
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                        sDeviceState.dpc().componentName(), DISALLOW_CELLULAR_2G));
    }

    @PolicyAppliesTest(policy = DisallowCellular2g.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CELLULAR_2G")
    public void setUserRestriction_disallowCellular2g_isSet() {
        try {
            assumeTrue(isTelephonyCapableOfSettingNetworkTypes());

            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_CELLULAR_2G);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_CELLULAR_2G))
                    .isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_CELLULAR_2G);
        }
    }

    @PolicyDoesNotApplyTest(policy = DisallowCellular2g.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CELLULAR_2G")
    public void setUserRestriction_disallowCellular2g_isNotSet() {
        try {
            assumeTrue(isTelephonyCapableOfSettingNetworkTypes());

            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_CELLULAR_2G);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_CELLULAR_2G))
                    .isFalse();
        } finally {

            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_CELLULAR_2G);
        }
    }

    @PolicyAppliesTest(policy = DisallowCellular2g.class)
    // PolicySetResult broadcasts depend on the coexistence feature
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CELLULAR_2G")
    public void addDisallowCellular2g_notTelephonyCapable_sendHardwareUnsupportedToAdmin() {
        try {
            assumeFalse(isTelephonyCapableOfSettingNetworkTypes());

            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), UserManager.DISALLOW_CELLULAR_2G);

            PolicySetResultUtils.assertPolicySetResultReceived(sDeviceState,
                    getIdentifierForUserRestriction(UserManager.DISALLOW_CELLULAR_2G),
                    PolicyUpdateResult.RESULT_FAILURE_HARDWARE_LIMITATION, GLOBAL_USER_ID,
                    new Bundle());
        } finally {

            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_CELLULAR_2G);
        }
    }

    @PolicyAppliesTest(policy = DisallowCellular2g.class)
    // PolicySetResult broadcasts depend on the coexistence feature
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CELLULAR_2G")
    public void addDisallowCellular2g_telephonyCapable_sendSuccessToAdmin() {
        try {
            assumeTrue(isTelephonyCapableOfSettingNetworkTypes());

            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), UserManager.DISALLOW_CELLULAR_2G);

            PolicySetResultUtils.assertPolicySetResultReceived(sDeviceState,
                    getIdentifierForUserRestriction(UserManager.DISALLOW_CELLULAR_2G),
                    PolicyUpdateResult.RESULT_POLICY_SET, GLOBAL_USER_ID, new Bundle());
        } finally {

            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_CELLULAR_2G);
        }
    }

    @EnsureDoesNotHaveUserRestriction(DISALLOW_CELLULAR_2G)
    @Test
    @Postsubmit(reason = "new test")
    @Interactive
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CELLULAR_2G")
    public void disallowCellular2gIsNotSet_todo() throws Exception {
        // TODO: Test
    }

    @EnsureHasUserRestriction(DISALLOW_CELLULAR_2G)
    @Test
    @Postsubmit(reason = "new test")
    @Interactive
    @ApiTest(apis = "android.os.UserManager#DISALLOW_CELLULAR_2G")
    public void disallowCellular2gIsSet_todo() throws Exception {
        // TODO: Test
    }

    @CannotSetPolicyTest(policy = DisallowUltraWidebandRadio.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_ULTRA_WIDEBAND_RADIO")
    public void setUserRestriction_disallowUltraWidebandRadio_cannotSet_throwsException() {
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                        sDeviceState.dpc().componentName(), DISALLOW_ULTRA_WIDEBAND_RADIO));
    }

    @PolicyAppliesTest(policy = DisallowUltraWidebandRadio.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_ULTRA_WIDEBAND_RADIO")
    public void setUserRestriction_disallowUltraWidebandRadio_isSet() {
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_ULTRA_WIDEBAND_RADIO);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_ULTRA_WIDEBAND_RADIO))
                    .isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_ULTRA_WIDEBAND_RADIO);
        }
    }

    @PolicyDoesNotApplyTest(policy = DisallowUltraWidebandRadio.class)
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_ULTRA_WIDEBAND_RADIO")
    public void setUserRestriction_disallowUltraWidebandRadio_isNotSet() {
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_ULTRA_WIDEBAND_RADIO);

            assertThat(TestApis.devicePolicy().userRestrictions().isSet(DISALLOW_ULTRA_WIDEBAND_RADIO))
                    .isFalse();
        } finally {

            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_ULTRA_WIDEBAND_RADIO);
        }
    }

    @EnsureDoesNotHaveUserRestriction(DISALLOW_ULTRA_WIDEBAND_RADIO)
    @Test
    @Postsubmit(reason = "new test")
    @Interactive
    @ApiTest(apis = "android.os.UserManager#DISALLOW_ULTRA_WIDEBAND_RADIO")
    public void disallowUltraWidebandRadioIsNotSet_todo() throws Exception {
        // TODO: Test
    }

    @EnsureHasUserRestriction(DISALLOW_ULTRA_WIDEBAND_RADIO)
    @Test
    @Postsubmit(reason = "new test")
    @Interactive
    @ApiTest(apis = "android.os.UserManager#DISALLOW_ULTRA_WIDEBAND_RADIO")
    public void disallowUltraWidebandRadioIsSet_todo() throws Exception {
        // TODO: Test
    }

    @CannotSetPolicyTest(
            policy = DisallowUnmanagedSubscriptionsGlobally.class,
            includeNonDeviceAdminStates = false)
    @Test
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_SIM_GLOBALLY")
    @RequireFlagsEnabled(android.app.admin.flags.Flags.FLAG_ESIM_MANAGEMENT_ENABLED)
    public void
            setUserRestriction_disallowUnmanagedSubscriptionsGlobally_cannotSet_throwsException() {
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                        sDeviceState.dpc().componentName(),
                        DISALLOW_SIM_GLOBALLY));
    }

    @PolicyAppliesTest(policy = DisallowUnmanagedSubscriptionsGlobally.class)
    @Test
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_SIM_GLOBALLY")
    @RequireFlagsEnabled(android.app.admin.flags.Flags.FLAG_ESIM_MANAGEMENT_ENABLED)
    public void setUserRestriction_disallowUnmanagedSubscriptionsGlobally_isSet() {
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_SIM_GLOBALLY);

            assertThat(
                    TestApis
                        .devicePolicy()
                        .userRestrictions()
                        .isSet(DISALLOW_SIM_GLOBALLY))
                    .isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_SIM_GLOBALLY);
        }
    }

    @PolicyDoesNotApplyTest(policy = DisallowUnmanagedSubscriptionsGlobally.class)
    @Test
    @Postsubmit(reason = "new test")
    @ApiTest(apis = "android.os.UserManager#DISALLOW_SIM_GLOBALLY")
    @RequireFlagsEnabled(android.app.admin.flags.Flags.FLAG_ESIM_MANAGEMENT_ENABLED)
    public void setUserRestriction_disallowUnmanagedSubscriptionsGlobally_isNotSet() {
        try {
            sDeviceState.dpc().devicePolicyManager().addUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_SIM_GLOBALLY);

            assertThat(
                    TestApis
                            .devicePolicy()
                            .userRestrictions()
                            .isSet(DISALLOW_SIM_GLOBALLY))
                    .isFalse();
        } finally {
            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    sDeviceState.dpc().componentName(), DISALLOW_SIM_GLOBALLY);
        }
    }

        private static final String TEST_APN_NAME = "testEnterpriseApnName";
    private static final String UPDATE_APN_NAME = "updateEnterpriseApnName";
    private static final String TEST_ENTRY_NAME = "testEnterpriseEntryName";
    private static final String UPDATE_ETNRY_NAME = "updateEnterpriseEntryName";
    private static final String TEST_OPERATOR_NUMERIC = "123456789";
    private static final int TEST_PROXY_PORT = 123;
    private static final String TEST_PROXY_ADDRESS = "123.123.123.123";
    private static final Uri TEST_MMSC = Uri.parse("http://www.google.com");
    private static final String TEST_USER_NAME = "testUser";
    private static final String TEST_PASSWORD = "testPassword";
    private static final int TEST_AUTH_TYPE = ApnSetting.AUTH_TYPE_CHAP;
    private static final int TEST_APN_TYPE_BITMASK = ApnSetting.TYPE_ENTERPRISE;
    private static final int TEST_APN_TYPE_BITMASK_WRONG = ApnSetting.TYPE_DEFAULT;
    private static final int TEST_PROTOCOL = ApnSetting.PROTOCOL_IPV4V6;
    private static final int TEST_NETWORK_TYPE_BITMASK = TelephonyManager.NETWORK_TYPE_CDMA;
    private static final int TEST_MVNO_TYPE = ApnSetting.MVNO_TYPE_GID;
    private static final boolean TEST_ENABLED = true;
    private static final int TEST_CARRIER_ID = 100;
    private static final int UPDATE_CARRIER_ID = 101;

    private static final ApnSetting TEST_APN_FULL = new ApnSetting.Builder()
            .setApnName(TEST_APN_NAME)
            .setEntryName(TEST_ENTRY_NAME)
            .setOperatorNumeric(TEST_OPERATOR_NUMERIC)
            .setProxyAddress(TEST_PROXY_ADDRESS)
            .setProxyPort(TEST_PROXY_PORT)
            .setMmsc(TEST_MMSC)
            .setMmsProxyAddress(TEST_PROXY_ADDRESS)
            .setMmsProxyPort(TEST_PROXY_PORT)
            .setUser(TEST_USER_NAME)
            .setPassword(TEST_PASSWORD)
            .setAuthType(TEST_AUTH_TYPE)
            .setApnTypeBitmask(TEST_APN_TYPE_BITMASK)
            .setProtocol(TEST_PROTOCOL)
            .setRoamingProtocol(TEST_PROTOCOL)
            .setNetworkTypeBitmask(TEST_NETWORK_TYPE_BITMASK)
            .setMvnoType(TEST_MVNO_TYPE)
            .setCarrierEnabled(TEST_ENABLED)
            .setCarrierId(TEST_CARRIER_ID)
            .build();

    private static final ApnSetting UPDATE_APN = new ApnSetting.Builder()
            .setApnName(UPDATE_APN_NAME)
            .setEntryName(UPDATE_ETNRY_NAME)
            .setOperatorNumeric(TEST_OPERATOR_NUMERIC)
            .setProxyAddress(TEST_PROXY_ADDRESS)
            .setProxyPort(TEST_PROXY_PORT)
            .setMmsc(TEST_MMSC)
            .setMmsProxyAddress(TEST_PROXY_ADDRESS)
            .setMmsProxyPort(TEST_PROXY_PORT)
            .setUser(TEST_USER_NAME)
            .setPassword(TEST_PASSWORD)
            .setAuthType(TEST_AUTH_TYPE)
            .setApnTypeBitmask(TEST_APN_TYPE_BITMASK)
            .setProtocol(TEST_PROTOCOL)
            .setRoamingProtocol(TEST_PROTOCOL)
            .setNetworkTypeBitmask(TEST_NETWORK_TYPE_BITMASK)
            .setMvnoType(TEST_MVNO_TYPE)
            .setCarrierEnabled(TEST_ENABLED)
            .setCarrierId(UPDATE_CARRIER_ID)
            .build();

    private static InetAddress getProxyInetAddress(String proxyAddress) throws Exception {
        return InetAddress.getByName(proxyAddress);
    }

    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#addOverrideApn",
            "android.app.admin.DevicePolicyManager#getOverrideApn"})
    @CanSetPolicyTest(policy = OverrideApn.class)
    @Test
    public void addOverrideApn_overrideApnIsAdded() throws Exception {
        int insertedId = 0;
        try {
            insertedId = sDeviceState.dpc().devicePolicyManager().addOverrideApn(
                    sDeviceState.dpc().componentName(), TEST_APN_FULL);
            List<ApnSetting> apnList = sDeviceState.dpc().devicePolicyManager()
                    .getOverrideApns(sDeviceState.dpc().componentName());

            assertThat(insertedId).isNotEqualTo(0);
            assertThat(apnList).hasSize(1);
            ApnSetting receivedApn = apnList.get(0);
            assertApnSettingEqual(receivedApn, TEST_APN_FULL);
        } finally {
            if (insertedId != 0) {
                sDeviceState.dpc().devicePolicyManager()
                        .removeOverrideApn(sDeviceState.dpc().componentName(), insertedId);
            }
        }
    }

    @ApiTest(apis = "android.app.admin.DevicePolicyManager#updateOverrideApn")
    @CanSetPolicyTest(policy = OverrideApn.class)
    @Test
    public void updateOverrideApn_isUpdated() throws Exception {
        int insertedId = 0;
        try {
            insertedId = sDeviceState.dpc().devicePolicyManager().addOverrideApn(
                    sDeviceState.dpc().componentName(), TEST_APN_FULL);
            boolean result = sDeviceState.dpc().devicePolicyManager().updateOverrideApn(
                    sDeviceState.dpc().componentName(), insertedId, UPDATE_APN);
            List<ApnSetting> apnList = sDeviceState.dpc().devicePolicyManager()
                    .getOverrideApns(sDeviceState.dpc().componentName());

            assertThat(result).isTrue();
            ApnSetting receivedApn = apnList.get(0);
            assertApnSettingEqual(receivedApn, UPDATE_APN);
        } finally {
            if (insertedId != 0) {
                sDeviceState.dpc().devicePolicyManager()
                        .removeOverrideApn(sDeviceState.dpc().componentName(), insertedId);
            }
        }
    }

    // TODO: Migrate the remaining parts of DeviceOwnerTest#testOverrideApn and
    //  add @CannotSetPolicyTests and ideally @PolicyAppliesTest and @PolicyDoesNotApplyTest

    private boolean isTelephonyCapableOfSettingNetworkTypes() {
        TelephonyManager tm = sContext.getSystemService(TelephonyManager.class);
        try {
            return (tm != null && tm.isRadioInterfaceCapabilitySupported(
                    TelephonyManager.CAPABILITY_USES_ALLOWED_NETWORK_TYPES_BITMASK));

        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private void assertApnSettingEqual(ApnSetting setting1, ApnSetting setting2) throws Exception {
        assertThat(setting1.getOperatorNumeric()).isEqualTo(setting2.getOperatorNumeric());
        assertThat(setting1.getEntryName()).isEqualTo(setting2.getEntryName());
        assertThat(setting1.getProxyAddress()).isEqualTo(
                getProxyInetAddress(setting2.getProxyAddressAsString()));
        assertThat(setting1.getProxyAddressAsString()).isEqualTo(setting2.getProxyAddressAsString());
        assertThat(setting1.getProxyPort()).isEqualTo(setting2.getProxyPort());
        assertThat(setting1.getMmsc()).isEqualTo(setting2.getMmsc());
        assertThat(setting1.getMmsProxyAddress()).isEqualTo(
                getProxyInetAddress(setting2.getProxyAddressAsString()));
        assertThat(setting1.getMmsProxyAddressAsString()).isEqualTo(setting2.getMmsProxyAddressAsString());
        assertThat(setting1.getMmsProxyPort()).isEqualTo(setting2.getMmsProxyPort());
        assertThat(setting1.getUser()).isEqualTo(setting2.getUser());
        assertThat(setting1.getPassword()).isEqualTo(setting2.getPassword());
        assertThat(setting1.getAuthType()).isEqualTo(setting2.getAuthType());
        assertThat(setting1.getProtocol()).isEqualTo(setting2.getProtocol());
        assertThat(setting1.getRoamingProtocol()).isEqualTo(setting2.getRoamingProtocol());
        assertThat(setting1.isEnabled()).isEqualTo(setting2.isEnabled());
        assertThat(setting1.getMvnoType()).isEqualTo(setting2.getMvnoType());
        assertThat(setting1.getNetworkTypeBitmask()).isEqualTo(setting2.getNetworkTypeBitmask());
        assertThat(setting1.getCarrierId()).isEqualTo(setting2.getCarrierId());
    }
}
