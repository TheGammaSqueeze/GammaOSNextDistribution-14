/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.telephony2.cts;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import android.content.Context;
import android.content.pm.PackageManager;
import android.platform.test.annotations.AppModeFull;
import android.telecom.PhoneAccount;
import android.telecom.TelecomManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.cts.util.TelephonyUtils;
import android.telephony.emergency.EmergencyNumber;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.ShellIdentityUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test TelephonyManager APIs with READ_PHONE_STATE Permission.
 */
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "Cannot grant the runtime permission in instant app mode")
public class TelephonyManagerReadPhoneStatePermissionTest {

    private boolean mHasTelephony;
    TelephonyManager mTelephonyManager = null;
    TelecomManager mTelecomManager = null;

    @Before
    public void setUp() throws Exception {
        mHasTelephony = getContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_TELEPHONY);
        mTelephonyManager =
                (TelephonyManager) getContext().getSystemService(Context.TELEPHONY_SERVICE);
        assertNotNull(mTelephonyManager);
        mTelecomManager =
                (TelecomManager) getContext().getSystemService(Context.TELECOM_SERVICE);
        assertNotNull(mTelecomManager);
    }

    @After
    public void tearDown() throws Exception {
        TelephonyUtils.resetCompatCommand(InstrumentationRegistry.getInstrumentation(),
                TelephonyUtils.CTS_APP_PACKAGE,
                TelephonyUtils.ENABLE_GET_CALL_STATE_PERMISSION_PROTECTION_STRING);
    }

    /**
     * Verify that TelephonyManager APIs requiring READ_PHONE_STATE Permission must work.
     * <p>
     * Requires Permission:
     * {@link android.Manifest.permission#READ_PHONE_STATE}.
     *
     * APIs list:
     * getDeviceSoftwareVersion()
     * getCarrierConfig()
     * getNetworkType()
     * getDataNetworkType()
     * getVoiceNetworkType()
     * getGroupIdLevel1()
     * getLine1AlphaTag()
     * getVoiceMailNumber()
     * getVisualVoicemailPackageName()
     * getVoiceMailAlphaTag()
     * getForbiddenPlmns()
     * isDataRoamingEnabled()
     * getSubscriptionId(@NonNull PhoneAccountHandle phoneAccountHandle)
     * getServiceState()
     * getEmergencyNumberList()
     * getEmergencyNumberList(@EmergencyServiceCategories int categories)
     * getPreferredOpportunisticDataSubscription()
     * isModemEnabledForSlot(int slotIndex)
     * isMultiSimSupported()
     * doesSwitchMultiSimConfigTriggerReboot()
     * getCallState() (when compat fwk enables enforcement)
     * getCallStateForSubscription() (when compat fwk enables enforcement)
     */
    @Test
    public void testTelephonyManagersAPIsRequiringReadPhoneStatePermissions() throws Exception {
        if (!mHasTelephony) {
            return;
        }

        try {
            // We must ensure that compat fwk enables READ_PHONE_STATE enforcement
            TelephonyUtils.enableCompatCommand(InstrumentationRegistry.getInstrumentation(),
                    TelephonyUtils.CTS_APP_PACKAGE,
                    TelephonyUtils.ENABLE_GET_CALL_STATE_PERMISSION_PROTECTION_STRING);
            ShellIdentityUtils.invokeMethodWithShellPermissions(
                    mTelephonyManager, (tm) -> tm.getCallState());
            ShellIdentityUtils.invokeMethodWithShellPermissions(
                    mTelephonyManager, (tm) -> tm.getCallStateForSubscription());
        } catch (SecurityException e) {
            fail("TelephonyManager#getCallState and TelephonyManager#getCallStateForSubscription "
                    + "must not throw a SecurityException because READ_PHONE_STATE permission is "
                    + "granted and TelecomManager#ENABLE_GET_CALL_STATE_PERMISSION_PROTECTION is "
                    + "enabled.");
        }

        int subId = mTelephonyManager.getSubscriptionId();

        try {
            ShellIdentityUtils.invokeMethodWithShellPermissions(
                    mTelephonyManager, (tm) -> tm.getNetworkType());
        } catch (SecurityException e) {
            fail("getNetworkType() must not throw a SecurityException with READ_PHONE_STATE" + e);
        }
        try {
            ShellIdentityUtils.invokeMethodWithShellPermissions(
                    mTelephonyManager, (tm) -> tm.getDeviceSoftwareVersion());
        } catch (SecurityException e) {
            fail("getDeviceSoftwareVersion() must not throw a SecurityException"
                    + " with READ_PHONE_STATE" + e);
        }
        try {
            ShellIdentityUtils.invokeMethodWithShellPermissions(
                    mTelephonyManager, (tm) -> tm.getCarrierConfig());
        } catch (SecurityException e) {
            fail("getCarrierConfig() must not throw a SecurityException"
                    + " with READ_PHONE_STATE" + e);
        }
        try {
            ShellIdentityUtils.invokeMethodWithShellPermissions(
                    mTelephonyManager, (tm) -> tm.getDataNetworkType());
        } catch (SecurityException e) {
            fail("getDataNetworkType() must not throw a SecurityException"
                    + " with READ_PHONE_STATE" + e);
        }
        try {
            ShellIdentityUtils.invokeMethodWithShellPermissions(
                    mTelephonyManager, (tm) -> tm.getVoiceNetworkType());
        } catch (SecurityException e) {
            fail("getVoiceNetworkType() must not throw a SecurityException"
                    + " with READ_PHONE_STATE" + e);
        }
        try {
            ShellIdentityUtils.invokeMethodWithShellPermissions(
                    mTelephonyManager, (tm) -> tm.getGroupIdLevel1());
        } catch (SecurityException e) {
            fail("getGroupIdLevel1() must not throw a SecurityException"
                    + " with READ_PHONE_STATE" + e);
        }
        try {
            ShellIdentityUtils.invokeMethodWithShellPermissions(
                    mTelephonyManager, (tm) -> tm.getLine1AlphaTag());
        } catch (SecurityException e) {
            fail("getLine1AlphaTag() must not throw a SecurityException"
                    + " with READ_PHONE_STATE" + e);
        }
        try {
            ShellIdentityUtils.invokeMethodWithShellPermissions(
                    mTelephonyManager, (tm) -> tm.getVoiceMailNumber());
        } catch (SecurityException e) {
            fail("getVoiceMailNumber() must not throw a SecurityException"
                    + " with READ_PHONE_STATE" + e);
        }
        try {
            ShellIdentityUtils.invokeMethodWithShellPermissions(
                    mTelephonyManager, (tm) -> tm.getVisualVoicemailPackageName());
        } catch (SecurityException e) {
            fail("getVisualVoicemailPackageName() must not throw a SecurityException"
                    + " with READ_PHONE_STATE" + e);
        }
        try {
            ShellIdentityUtils.invokeMethodWithShellPermissions(
                    mTelephonyManager, (tm) -> tm.getVoiceMailAlphaTag());
        } catch (SecurityException e) {
            fail("getVoiceMailAlphaTag() must not throw a SecurityException"
                    + " with READ_PHONE_STATE" + e);
        }
        try {
            ShellIdentityUtils.invokeMethodWithShellPermissions(
                    mTelephonyManager, (tm) -> tm.getForbiddenPlmns());
        } catch (SecurityException e) {
            fail("getForbiddenPlmns() must not throw a SecurityException"
                    + " with READ_PHONE_STATE" + e);
        }
        try {
            ShellIdentityUtils.invokeMethodWithShellPermissions(
                    mTelephonyManager, (tm) -> tm.isDataRoamingEnabled());
        } catch (SecurityException e) {
            fail("isDataRoamingEnabled() must not throw a SecurityException"
                    + " with READ_PHONE_STATE" + e);
        }
        try {
            ShellIdentityUtils.invokeMethodWithShellPermissions(
                    mTelephonyManager, (tm) -> tm.getSubscriptionId(
                            mTelecomManager.getDefaultOutgoingPhoneAccount(
                                    PhoneAccount.SCHEME_TEL)));
        } catch (SecurityException e) {
            fail("getSubscriptionId(phoneAccountHandle) must not throw a SecurityException"
                    + " with READ_PHONE_STATE" + e);
        }
        try {
            ShellIdentityUtils.invokeMethodWithShellPermissions(
                    mTelephonyManager, (tm) -> tm.getServiceState());
        } catch (SecurityException e) {
            fail("getServiceState() must not throw a SecurityException"
                    + " with READ_PHONE_STATE" + e);
        }
        try {
            ShellIdentityUtils.invokeMethodWithShellPermissions(
                    mTelephonyManager, (tm) -> tm.getEmergencyNumberList());
        } catch (SecurityException e) {
            fail("getEmergencyNumberList() must not throw a SecurityException"
                    + " with READ_PHONE_STATE" + e);
        }
        try {
            ShellIdentityUtils.invokeMethodWithShellPermissions(
                    mTelephonyManager, (tm) -> tm.getEmergencyNumberList(
                            EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_POLICE));
        } catch (SecurityException e) {
            fail("getEmergencyNumberList(EMERGENCY_SERVICE_CATEGORY_POLICE) must"
                    + " not throw a SecurityException with READ_PHONE_STATE" + e);
        }
        try {
            ShellIdentityUtils.invokeMethodWithShellPermissions(
                    mTelephonyManager, (tm) -> tm.getPreferredOpportunisticDataSubscription());
        } catch (SecurityException e) {
            fail("getPreferredOpportunisticDataSubscription() must not throw"
                    + " a SecurityException with READ_PHONE_STATE" + e);
        }
        try {
            ShellIdentityUtils.invokeMethodWithShellPermissions(
                    mTelephonyManager, (tm) -> tm.isModemEnabledForSlot(
                            SubscriptionManager.getSlotIndex(subId)));
        } catch (SecurityException e) {
            fail("isModemEnabledForSlot(slotIndex) must not throw a SecurityException"
                    + " with READ_PHONE_STATE" + e);
        }
        try {
            ShellIdentityUtils.invokeMethodWithShellPermissions(
                    mTelephonyManager, (tm) -> tm.isMultiSimSupported());
        } catch (SecurityException e) {
            fail("isMultiSimSupported() must not throw a SecurityException"
                    + " with READ_PHONE_STATE" + e);
        }
        try {
            ShellIdentityUtils.invokeMethodWithShellPermissions(
                    mTelephonyManager, (tm) -> tm.doesSwitchMultiSimConfigTriggerReboot());
        } catch (SecurityException e) {
            fail("doesSwitchMultiSimConfigTriggerReboot() must not throw a SecurityException"
                    + " with READ_PHONE_STATE" + e);
        }
    }

    private static Context getContext() {
        return InstrumentationRegistry.getContext();
    }
}
