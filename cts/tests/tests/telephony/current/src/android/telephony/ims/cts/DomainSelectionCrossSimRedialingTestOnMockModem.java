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

package android.telephony.ims.cts;

import static android.telephony.AccessNetworkConstants.AccessNetworkType.EUTRAN;
import static android.telephony.AccessNetworkConstants.AccessNetworkType.GERAN;
import static android.telephony.AccessNetworkConstants.AccessNetworkType.UTRAN;
import static android.telephony.BarringInfo.BARRING_SERVICE_TYPE_EMERGENCY;
import static android.telephony.BarringInfo.BarringServiceInfo.BARRING_TYPE_UNCONDITIONAL;
import static android.telephony.CarrierConfigManager.ImsEmergency.KEY_CROSS_STACK_REDIAL_TIMER_SEC_INT;
import static android.telephony.CarrierConfigManager.ImsEmergency.KEY_EMERGENCY_DOMAIN_PREFERENCE_INT_ARRAY;
import static android.telephony.CarrierConfigManager.ImsEmergency.KEY_EMERGENCY_DOMAIN_PREFERENCE_ROAMING_INT_ARRAY;
import static android.telephony.CarrierConfigManager.ImsEmergency.KEY_EMERGENCY_NETWORK_SCAN_TYPE_INT;
import static android.telephony.CarrierConfigManager.ImsEmergency.KEY_EMERGENCY_OVER_CS_ROAMING_SUPPORTED_ACCESS_NETWORK_TYPES_INT_ARRAY;
import static android.telephony.CarrierConfigManager.ImsEmergency.KEY_EMERGENCY_OVER_CS_SUPPORTED_ACCESS_NETWORK_TYPES_INT_ARRAY;
import static android.telephony.CarrierConfigManager.ImsEmergency.KEY_EMERGENCY_OVER_IMS_ROAMING_SUPPORTED_3GPP_NETWORK_TYPES_INT_ARRAY;
import static android.telephony.CarrierConfigManager.ImsEmergency.KEY_EMERGENCY_OVER_IMS_SUPPORTED_3GPP_NETWORK_TYPES_INT_ARRAY;
import static android.telephony.CarrierConfigManager.ImsEmergency.KEY_PREFER_IMS_EMERGENCY_WHEN_VOICE_CALLS_ON_CS_BOOL;
import static android.telephony.CarrierConfigManager.ImsEmergency.KEY_QUICK_CROSS_STACK_REDIAL_TIMER_SEC_INT;
import static android.telephony.CarrierConfigManager.ImsEmergency.KEY_START_QUICK_CROSS_STACK_REDIAL_TIMER_WHEN_REGISTERED_BOOL;
import static android.telephony.CarrierConfigManager.ImsEmergency.REDIAL_TIMER_DISABLED;
import static android.telephony.CarrierConfigManager.ImsEmergency.SCAN_TYPE_NO_PREFERENCE;
import static android.telephony.NetworkRegistrationInfo.REGISTRATION_STATE_HOME;
import static android.telephony.mockmodem.IRadioVoiceImpl.LATCH_EMERGENCY_DIAL;
import static android.telephony.mockmodem.IRadioVoiceImpl.LATCH_GET_LAST_CALL_FAIL_CAUSE;
import static android.telephony.mockmodem.MockSimService.MOCK_SIM_PROFILE_ID_TWN_CHT;

import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.telecom.Call;
import android.telecom.PhoneAccount;
import android.telecom.TelecomManager;
import android.telecom.cts.TestUtils;
import android.telephony.AccessNetworkConstants;
import android.telephony.BarringInfo;
import android.telephony.CarrierConfigManager;
import android.telephony.DisconnectCause;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.cts.util.TelephonyUtils;
import android.telephony.ims.ImsManager;
import android.telephony.ims.ImsMmTelManager;
import android.telephony.ims.ImsReasonInfo;
import android.telephony.ims.feature.ImsFeature;
import android.telephony.ims.stub.ImsFeatureConfiguration;
import android.telephony.mockmodem.MockEmergencyRegResult;
import android.telephony.mockmodem.MockModemManager;
import android.util.SparseArray;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ShellIdentityUtils;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * CTS tests for Cross SIM redialing.
 */
@RunWith(AndroidJUnit4.class)
public class DomainSelectionCrossSimRedialingTestOnMockModem extends ImsCallingBase {

    private static final String TEST_EMERGENCY_NUMBER = "998877665544332211";

    // the timeout to wait for latch countdonw in milliseconds
    private static final int WAIT_LATCH_TIMEOUT_MS = 10000;

    // the timeout to wait for result in milliseconds
    private static final int WAIT_UPDATE_TIMEOUT_MS = 5000;

    // the timeout to wait for request in milliseconds
    private static final int WAIT_REQUEST_TIMEOUT_MS = 3000;

    // the cross sim redialing timer in seconds
    private static final int CROSS_STACK_TIMEOUT_SEC = 3;

    private static MockModemManager sMockModemManager;

    private static boolean sSupportDomainSelection = false;;
    private static boolean sVoLteEnabled = false;

    private static int sOtherSlot = 1;
    private static int sModemCount = 1;

    static {
        initializeLatches();
    }

    @BeforeClass
    public static void beforeAllTests() throws Exception {
        if (!hasFeature(PackageManager.FEATURE_TELEPHONY)) {
            return;
        }

        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        TelephonyManager telephonyManager = (TelephonyManager) getContext()
                .getSystemService(Context.TELEPHONY_SERVICE);

        sModemCount = telephonyManager.getActiveModemCount();

        if (sModemCount < 2) {
            return;
        }

        sSupportDomainSelection =
                ShellIdentityUtils.invokeMethodWithShellPermissions(telephonyManager,
                        (tm) -> tm.isDomainSelectionSupported());

        if (!sSupportDomainSelection) {
            return;
        }

        TestUtils.setSystemDialerOverride(
                InstrumentationRegistry.getInstrumentation(), INCALL_COMPONENT);

        MockModemManager.enforceMockModemDeveloperSetting();
        sMockModemManager = new MockModemManager();
        assertNotNull(sMockModemManager);
        assertTrue(sMockModemManager.connectMockModemService(MOCK_SIM_PROFILE_ID_TWN_CHT));

        TimeUnit.MILLISECONDS.sleep(WAIT_UPDATE_TIMEOUT_MS);

        int simCardState = telephonyManager.getSimCardState();
        assertEquals(TelephonyManager.SIM_STATE_PRESENT, simCardState);

        TimeUnit.MILLISECONDS.sleep(WAIT_UPDATE_TIMEOUT_MS);

        // Check SIM state ready
        simCardState = telephonyManager.getSimState();
        assertEquals(TelephonyManager.SIM_STATE_READY, simCardState);

        sTestSub = ImsUtils.getPreferredActiveSubId();

        int sub = SubscriptionManager.getSubscriptionId(sTestSlot);
        if (SubscriptionManager.isValidSubscriptionId(sub)) {
            sTestSub = sub;
        }

        assertTrue(sMockModemManager.changeNetworkService(sTestSlot, 310260, true));

        TimeUnit.MILLISECONDS.sleep(WAIT_UPDATE_TIMEOUT_MS);

        beforeAllTestsBase();

        ImsManager imsManager = getContext().getSystemService(ImsManager.class);
        ImsMmTelManager mmTelManager = imsManager.getImsMmTelManager(sTestSub);
        sVoLteEnabled = ShellIdentityUtils.invokeMethodWithShellPermissions(mmTelManager,
                ImsMmTelManager::isAdvancedCallingSettingEnabled);

        sMockModemManager.notifyEmergencyNumberList(sTestSlot,
                new String[] { TEST_EMERGENCY_NUMBER });
    }

    @AfterClass
    public static void afterAllTests() throws Exception {
        if (!hasFeature(PackageManager.FEATURE_TELEPHONY)) {
            return;
        }

        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        if (sModemCount < 2) {
            return;
        }

        if (!sSupportDomainSelection) {
            return;
        }

        ImsManager imsManager = getContext().getSystemService(ImsManager.class);
        ImsMmTelManager mmTelManager = imsManager.getImsMmTelManager(sTestSub);
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mmTelManager,
                (m) -> m.setAdvancedCallingSettingEnabled(sVoLteEnabled));

        afterAllTestsBase();

        // Rebind all interfaces which is binding to MockModemService to default.
        if (sMockModemManager != null) {
            assertTrue(sMockModemManager.disconnectMockModemService());
            sMockModemManager = null;

            TimeUnit.MILLISECONDS.sleep(WAIT_UPDATE_TIMEOUT_MS);
        }

        TestUtils.clearSystemDialerOverride(InstrumentationRegistry.getInstrumentation());
    }

    @Before
    public void beforeTest() throws Exception {
        assumeTrue(hasFeature(PackageManager.FEATURE_TELEPHONY));
        assumeTrue(ImsUtils.shouldTestImsService());
        assumeTrue(sModemCount > 1);
        assumeTrue(sSupportDomainSelection);

        if (sMockModemManager != null) {
            unsolBarringInfoChanged(sTestSlot, false, false);
            setLastCallFailCause(sTestSlot, DisconnectCause.POWER_OFF);
            resetNetworkAllLatchCountdown(sTestSlot);
            resetVoiceAllLatchCountdown(sTestSlot);

            unsolBarringInfoChanged(sOtherSlot, false, false);
            setLastCallFailCause(sOtherSlot, DisconnectCause.POWER_OFF);
            resetNetworkAllLatchCountdown(sOtherSlot);
            resetVoiceAllLatchCountdown(sOtherSlot);
        }
    }

    @After
    public void afterTest() throws Exception {
        if (!mCalls.isEmpty() && (mCurrentCallId != null)) {
            Call call = mCalls.get(mCurrentCallId);
            call.disconnect();
        }

        if (sMockModemManager != null) {
            clearAllCalls(sTestSlot, DisconnectCause.POWER_OFF);
            resetEmergencyNetworkScan(sTestSlot);
            unsolBarringInfoChanged(sTestSlot, false, true);
            waitForVoiceLatchCountdown(sTestSlot,
                    LATCH_GET_LAST_CALL_FAIL_CAUSE, WAIT_REQUEST_TIMEOUT_MS);

            clearAllCalls(sOtherSlot, DisconnectCause.POWER_OFF);
            resetEmergencyNetworkScan(sOtherSlot);
            unsolBarringInfoChanged(sOtherSlot, false, true);
            waitForVoiceLatchCountdown(sOtherSlot,
                    LATCH_GET_LAST_CALL_FAIL_CAUSE, WAIT_REQUEST_TIMEOUT_MS);
            sMockModemManager.removeSimCard(sOtherSlot);
        }

        if (mServiceCallBack != null && mServiceCallBack.getService() != null) {
            waitForUnboundService();
        }

        //Set the untracked CountDownLatches which are reseted in ServiceCallBack
        for (int i = 0; i < LATCH_MAX; i++) {
            sLatches[i] = new CountDownLatch(1);
        }

        if (sServiceConnector != null && sIsBound) {
            TestImsService imsService = sServiceConnector.getCarrierService();
            sServiceConnector.disconnectCarrierImsService();
            sIsBound = false;
            imsService.waitForExecutorFinish();
        }

        TelephonyUtils.endBlockSuppression(InstrumentationRegistry.getInstrumentation());
    }

    @Ignore("For internal test only.")
    @Test
    public void testCrossStackSlot0ThenSlot1() throws Exception {
        // Setup pre-condition
        unsolBarringInfoChanged(sTestSlot, false, false);
        unsolBarringInfoChanged(sOtherSlot, false, false);

        PersistableBundle bundle = getDefaultPersistableBundle();
        overrideCarrierConfig(bundle);

        MockEmergencyRegResult regResult = getEmergencyRegResult(UTRAN, REGISTRATION_STATE_HOME,
                NetworkRegistrationInfo.DOMAIN_CS, false, false, 0, 0, "", "");
        setEmergencyRegResult(sTestSlot, regResult);
        setEmergencyRegResult(sOtherSlot, regResult);

        bindImsServiceUnregistered();

        placeOutgoingCall(TEST_EMERGENCY_NUMBER);

        assertTrue(waitForVoiceLatchCountdown(sTestSlot, LATCH_EMERGENCY_DIAL));

        clearAllCalls(sTestSlot, DisconnectCause.EMERGENCY_TEMP_FAILURE);
        waitForVoiceLatchCountdown(sTestSlot, LATCH_GET_LAST_CALL_FAIL_CAUSE);

        TimeUnit.MILLISECONDS.sleep(WAIT_REQUEST_TIMEOUT_MS);

        assertTrue(waitForVoiceLatchCountdown(sOtherSlot, LATCH_EMERGENCY_DIAL));
    }

    private void placeOutgoingCall(String address) throws Exception {
        TelecomManager telecomManager = (TelecomManager) InstrumentationRegistry
                .getInstrumentation().getContext().getSystemService(Context.TELECOM_SERVICE);

        final Uri imsUri = Uri.fromParts(PhoneAccount.SCHEME_TEL, address, null);
        Bundle extras = new Bundle();

        // Place outgoing call
        telecomManager.placeCall(imsUri, extras);
    }

    public void bindImsServiceUnregistered() throws Exception  {
        // Connect to the ImsService with the MmTel feature.
        assertTrue(sServiceConnector.connectCarrierImsService(new ImsFeatureConfiguration.Builder()
                .addFeature(sTestSlot, ImsFeature.FEATURE_MMTEL)
                .addFeature(sTestSlot, ImsFeature.FEATURE_EMERGENCY_MMTEL)
                .build()));
        sIsBound = true;
        // The MmTelFeature is created when the ImsService is bound. If it wasn't created, then the
        // Framework did not call it.
        sServiceConnector.getCarrierService().waitForLatchCountdown(
                TestImsService.LATCH_CREATE_MMTEL);
        assertNotNull("ImsService created, but ImsService#createMmTelFeature was not called!",
                sServiceConnector.getCarrierService().getMmTelFeature());

        sServiceConnector.getCarrierService().waitForLatchCountdown(
                TestImsService.LATCH_MMTEL_CAP_SET);

        // Set Deregistered
        sServiceConnector.getCarrierService().getImsService().getRegistrationForSubscription(
                sTestSlot, sTestSub).onDeregistered(
                        new ImsReasonInfo(ImsReasonInfo.CODE_REGISTRATION_ERROR,
                                ImsReasonInfo.CODE_UNSPECIFIED, ""));

        Thread.sleep(3000);
    }

    private static PersistableBundle getDefaultPersistableBundle() {
        int[] imsRats = new int[] { EUTRAN };
        int[] csRats = new int[] { UTRAN, GERAN };
        int[] domainPreference = new int[] {
                CarrierConfigManager.ImsEmergency.DOMAIN_PS_3GPP,
                CarrierConfigManager.ImsEmergency.DOMAIN_CS
                };
        boolean imsWhenVoiceOnCs = false;
        int scanType = SCAN_TYPE_NO_PREFERENCE;
        int crossStackTimer = 120;
        int quickCrossStackTimer = REDIAL_TIMER_DISABLED;
        boolean quickTimerWhenInService = true;

        return getPersistableBundle(imsRats, csRats,
                domainPreference, imsWhenVoiceOnCs, scanType,
                crossStackTimer, quickCrossStackTimer, quickTimerWhenInService);
    }

    private static PersistableBundle getPersistableBundle(
            @Nullable int[] imsRats, @Nullable int[] csRats,
            @Nullable int[] domainPreference,
            boolean imsWhenVoiceOnCs, int scanType,
            int crossStackTimer, int quickCrossStackTimer, boolean quickTimerWhenInService) {

        PersistableBundle bundle  = new PersistableBundle();
        if (imsRats != null) {
            bundle.putIntArray(
                    KEY_EMERGENCY_OVER_IMS_SUPPORTED_3GPP_NETWORK_TYPES_INT_ARRAY, imsRats);
            bundle.putIntArray(
                    KEY_EMERGENCY_OVER_IMS_ROAMING_SUPPORTED_3GPP_NETWORK_TYPES_INT_ARRAY, imsRats);
        }
        if (csRats != null) {
            bundle.putIntArray(
                    KEY_EMERGENCY_OVER_CS_SUPPORTED_ACCESS_NETWORK_TYPES_INT_ARRAY, csRats);
            bundle.putIntArray(
                    KEY_EMERGENCY_OVER_CS_ROAMING_SUPPORTED_ACCESS_NETWORK_TYPES_INT_ARRAY, csRats);
        }
        if (domainPreference != null) {
            bundle.putIntArray(KEY_EMERGENCY_DOMAIN_PREFERENCE_INT_ARRAY, domainPreference);
            bundle.putIntArray(KEY_EMERGENCY_DOMAIN_PREFERENCE_ROAMING_INT_ARRAY, domainPreference);
        }
        bundle.putBoolean(KEY_PREFER_IMS_EMERGENCY_WHEN_VOICE_CALLS_ON_CS_BOOL, imsWhenVoiceOnCs);
        bundle.putInt(KEY_EMERGENCY_NETWORK_SCAN_TYPE_INT, scanType);

        bundle.putInt(KEY_CROSS_STACK_REDIAL_TIMER_SEC_INT, crossStackTimer);
        bundle.putInt(KEY_QUICK_CROSS_STACK_REDIAL_TIMER_SEC_INT, quickCrossStackTimer);
        bundle.putBoolean(KEY_START_QUICK_CROSS_STACK_REDIAL_TIMER_WHEN_REGISTERED_BOOL,
                quickTimerWhenInService);


        return bundle;
    }

    private static MockEmergencyRegResult getEmergencyRegResult(
            @AccessNetworkConstants.RadioAccessNetworkType int accessNetwork,
            @NetworkRegistrationInfo.RegistrationState int regState,
            @NetworkRegistrationInfo.Domain int domain,
            boolean isVopsSupported, boolean isEmcBearerSupported, int emc, int emf,
            @NonNull String mcc, @NonNull String mnc) {
        return new MockEmergencyRegResult(accessNetwork, regState,
                domain, isVopsSupported, isEmcBearerSupported,
                emc, emf, mcc, mnc);
    }

    private static void unsolBarringInfoChanged(int slotId, boolean barred, boolean noAssert) {
        SparseArray<BarringInfo.BarringServiceInfo> serviceInfos = new SparseArray<>();
        if (barred) {
            serviceInfos.put(BARRING_SERVICE_TYPE_EMERGENCY,
                    new BarringInfo.BarringServiceInfo(BARRING_TYPE_UNCONDITIONAL, false, 0, 0));
        }
        if (noAssert) {
            sMockModemManager.unsolBarringInfoChanged(slotId, serviceInfos);
        } else {
            assertTrue(sMockModemManager.unsolBarringInfoChanged(slotId, serviceInfos));
        }
    }

    private boolean waitForVoiceLatchCountdown(int slotId, int latchIndex) {
        return waitForVoiceLatchCountdown(slotId, latchIndex, WAIT_LATCH_TIMEOUT_MS);
    }

    private boolean waitForVoiceLatchCountdown(int slotId, int latchIndex, int waitMs) {
        return sMockModemManager.waitForVoiceLatchCountdown(slotId, latchIndex, waitMs);
    }

    private void resetVoiceAllLatchCountdown(int slotId) {
        sMockModemManager.resetVoiceAllLatchCountdown(slotId);
    }

    private void setLastCallFailCause(int slotId, int cause) {
        sMockModemManager.setLastCallFailCause(slotId, cause);
    }

    private void clearAllCalls(int slotId, int cause) {
        sMockModemManager.clearAllCalls(slotId, cause);
    }

    private boolean waitForNetworkLatchCountdown(int slotId, int latchIndex) {
        return waitForNetworkLatchCountdown(slotId, latchIndex, WAIT_LATCH_TIMEOUT_MS);
    }

    public boolean waitForNetworkLatchCountdown(int slotId, int latchIndex, int waitMs) {
        return sMockModemManager.waitForNetworkLatchCountdown(slotId, latchIndex, waitMs);
    }

    private void resetNetworkAllLatchCountdown(int slotId) {
        sMockModemManager.resetNetworkAllLatchCountdown(slotId);
    }

    private void resetEmergencyNetworkScan(int slotId) {
        sMockModemManager.resetEmergencyNetworkScan(slotId);
    }

    private void setEmergencyRegResult(int slotId, MockEmergencyRegResult regResult) {
        sMockModemManager.setEmergencyRegResult(slotId, regResult);
    }

    private void unsolEmergencyNetworkScanResult(int slotId) throws Exception {
        MockEmergencyRegResult regResult = getEmergencyRegResult(UTRAN,
                REGISTRATION_STATE_HOME, NetworkRegistrationInfo.DOMAIN_CS,
                false, false, 0, 0, "", "");
        sMockModemManager.unsolEmergencyNetworkScanResult(slotId, regResult);
        waitForVoiceLatchCountdown(slotId, LATCH_EMERGENCY_DIAL);
    }
}
