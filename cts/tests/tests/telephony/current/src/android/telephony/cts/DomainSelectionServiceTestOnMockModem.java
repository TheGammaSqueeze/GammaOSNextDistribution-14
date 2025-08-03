/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.telephony.cts;

import static android.telephony.AccessNetworkConstants.AccessNetworkType.EUTRAN;
import static android.telephony.AccessNetworkConstants.AccessNetworkType.UTRAN;
import static android.telephony.BarringInfo.BARRING_SERVICE_TYPE_EMERGENCY;
import static android.telephony.BarringInfo.BarringServiceInfo.BARRING_TYPE_UNCONDITIONAL;
import static android.telephony.DomainSelectionService.SCAN_TYPE_NO_PREFERENCE;
import static android.telephony.DomainSelectionService.SELECTOR_TYPE_CALLING;
import static android.telephony.NetworkRegistrationInfo.DOMAIN_CS;
import static android.telephony.NetworkRegistrationInfo.REGISTRATION_STATE_UNKNOWN;
import static android.telephony.cts.TestDomainSelectionService.LATCH_ON_BARRING_INFO_UPDATED;
import static android.telephony.cts.TestDomainSelectionService.LATCH_ON_DOMAIN_SELECTION;
import static android.telephony.cts.TestDomainSelectionService.LATCH_ON_EMERGENCY_REG_RESULT;
import static android.telephony.cts.TestDomainSelectionService.LATCH_ON_FINISH_SELECTION;
import static android.telephony.cts.TestDomainSelectionService.LATCH_ON_RESELECTION;
import static android.telephony.cts.TestDomainSelectionService.LATCH_ON_SERVICE_STATE_UPDATED;
import static android.telephony.cts.TestDomainSelectionService.LATCH_ON_WWAN_SELECTOR_CALLBACK;
import static android.telephony.mockmodem.IRadioVoiceImpl.LATCH_EMERGENCY_DIAL;
import static android.telephony.mockmodem.IRadioVoiceImpl.LATCH_GET_LAST_CALL_FAIL_CAUSE;
import static android.telephony.mockmodem.MockNetworkService.LATCH_TRIGGER_EMERGENCY_SCAN;

import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeNoException;
import static org.junit.Assume.assumeTrue;

import android.annotation.NonNull;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemProperties;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.telecom.Call;
import android.telecom.PhoneAccount;
import android.telecom.TelecomManager;
import android.telephony.AccessNetworkConstants;
import android.telephony.BarringInfo;
import android.telephony.DisconnectCause;
import android.telephony.DomainSelectionService.SelectionAttributes;
import android.telephony.EmergencyRegistrationResult;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.TelephonyManager;
import android.telephony.mockmodem.MockEmergencyRegResult;
import android.telephony.mockmodem.MockModemManager;
import android.util.Log;
import android.util.SparseArray;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.ShellIdentityUtils;
import com.android.internal.telephony.flags.Flags;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * CTS tests for DomainSelectionService API.
 */
@RunWith(AndroidJUnit4.class)
@RequiresFlagsEnabled(Flags.FLAG_USE_OEM_DOMAIN_SELECTION_SERVICE)
public class DomainSelectionServiceTestOnMockModem extends DomainSelectionCallingBase {
    private static final String LOG_TAG = "DomainSelectionServiceTestOnMockModem";
    private static final boolean VDBG = false;

    // the timeout to wait for result in milliseconds
    private static final int WAIT_UPDATE_TIMEOUT_MS = 3000;

    // the timeout to wait for sim state change in milliseconds
    private static final int WAIT_SIM_STATE_TIMEOUT_MS = 4000;

    private static TelephonyManager sTelephonyManager;
    private static MockModemManager sMockModemManager;

    private static boolean sSupportDomainSelection = false;;
    private static boolean sDisableCall = false;

    static {
        initializeLatches();
    }

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    /** Connects TestDomainSelectionService and MockModem */
    @BeforeClass
    public static void beforeAllTests() throws Exception {
        if (VDBG) Log.d(LOG_TAG, "beforeAllTests");

        if (!hasFeature(PackageManager.FEATURE_TELEPHONY)) {
            return;
        }

        sTelephonyManager =
                (TelephonyManager) getContext().getSystemService(Context.TELEPHONY_SERVICE);

        sSupportDomainSelection =
                ShellIdentityUtils.invokeMethodWithShellPermissions(sTelephonyManager,
                        (tm) -> tm.isDomainSelectionSupported());

        if (!sSupportDomainSelection) {
            return;
        }

        sDisableCall = SystemProperties.getBoolean("ro.telephony.disable-call", false);

        MockModemManager.enforceMockModemDeveloperSetting();
        sMockModemManager = new MockModemManager();
        assertNotNull(sMockModemManager);
        assertTrue(sMockModemManager.connectMockModemService());

        TimeUnit.MILLISECONDS.sleep(WAIT_SIM_STATE_TIMEOUT_MS);

        beforeAllTestsBase();

        sMockModemManager.notifyEmergencyNumberList(sTestSlot,
                new String[] { TEST_EMERGENCY_NUMBER });
    }

    /** Restores DomainSelectionService and Radio Hal */
    @AfterClass
    public static void afterAllTests() throws Exception {
        if (VDBG) Log.d(LOG_TAG, "afterAllTests");

        if (!sSupportDomainSelection) {
            return;
        }

        afterAllTestsBase();

        // Rebind all interfaces which is binding to MockModemService to default.
        if (sMockModemManager != null) {
            assertTrue(sMockModemManager.disconnectMockModemService());
            sMockModemManager = null;

            TimeUnit.MILLISECONDS.sleep(WAIT_SIM_STATE_TIMEOUT_MS);
        }
    }

    @Before
    public void beforeTest() throws Exception {
        if (VDBG) Log.d(LOG_TAG, "beforeTest");

        assumeTrue(sSupportDomainSelection);
        try {
            sTelephonyManager.getHalVersion(TelephonyManager.HAL_SERVICE_RADIO);
        } catch (IllegalStateException e) {
            assumeNoException("Skipping tests because Telephony service is null", e);
        }

        if (sMockModemManager != null) {
            unsolBarringInfoChanged(false);
            sMockModemManager.setLastCallFailCause(sTestSlot, DisconnectCause.POWER_OFF);
            sMockModemManager.resetNetworkAllLatchCountdown(sTestSlot);
            sMockModemManager.resetVoiceAllLatchCountdown(sTestSlot);
            MockEmergencyRegResult regResult = getEmergencyRegResult(UTRAN,
                    REGISTRATION_STATE_UNKNOWN, NetworkRegistrationInfo.DOMAIN_CS,
                    false, false, 0, 0, "", "");
            sMockModemManager.setEmergencyRegResult(sTestSlot, regResult);
        }
    }

    @After
    public void afterTest() throws Exception {
        if (VDBG) Log.d(LOG_TAG, "afterTest");

        if (!sSupportDomainSelection) {
            return;
        }

        clearCalls();

        initializeLatches();

        if (sMockModemManager != null) {
            sMockModemManager.clearAllCalls(sTestSlot, DisconnectCause.POWER_OFF);
            sMockModemManager.resetEmergencyNetworkScan(sTestSlot);
            waitForVoiceLatchCountdown(LATCH_GET_LAST_CALL_FAIL_CAUSE);
        }

        if (mServiceCallBack != null && mServiceCallBack.getService() != null) {
            waitForUnboundService();
        }

        tearDownEmergencyCalling();
    }

    @Test
    public void testDomainSelectionServiceEmergencyCallCanceled() throws Exception {
        if (VDBG) Log.d(LOG_TAG, "testDomainSelectionServiceEmergencyCallCanceled");

        setupForEmergencyCalling();

        TestDomainSelectionService testService = sServiceConnector.getTestService();
        assertNotNull(testService);

        Call call = placeOutgoingCall(TEST_EMERGENCY_NUMBER);

        // onDomainSelection()
        assertTrue(testService.waitForLatchCountdown(LATCH_ON_DOMAIN_SELECTION));
        assertNotNull(testService.getTransportSelectorCallback());

        call.disconnect();

        // onCreated
        assertTrue(testService.onCreated());

        // finishSelection()
        assertTrue(testService.waitForLatchCountdown(LATCH_ON_FINISH_SELECTION));
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DISCONNECTED, WAIT_FOR_CALL_STATE));
    }

    @Test
    public void testDomainSelectionServiceEmergencyCallTerminateSelection() throws Exception {
        if (VDBG) Log.d(LOG_TAG, "testDomainSelectionServiceEmergencyCallTerminateSelection");

        setupForEmergencyCalling();

        TestDomainSelectionService testService = sServiceConnector.getTestService();
        assertNotNull(testService);

        placeOutgoingCall(TEST_EMERGENCY_NUMBER);

        // onDomainSelection()
        assertTrue(testService.waitForLatchCountdown(LATCH_ON_DOMAIN_SELECTION));
        assertNotNull(testService.getTransportSelectorCallback());

        // onSelectionTerminated()
        assertTrue(testService.terminateSelection(DisconnectCause.POWER_OFF));
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DISCONNECTED, WAIT_FOR_CALL_STATE));
    }

    @Test
    public void testDomainSelectionServiceEmergencyCallReselectDomain() throws Exception {
        if (VDBG) Log.d(LOG_TAG, "testDomainSelectionServiceEmergencyCallReselectDomain");

        if (sDisableCall) return;

        setupForEmergencyCalling();

        TestDomainSelectionService testService = sServiceConnector.getTestService();
        assertNotNull(testService);

        placeOutgoingCall(TEST_EMERGENCY_NUMBER);

        // onDomainSelection()
        assertTrue(testService.waitForLatchCountdown(LATCH_ON_DOMAIN_SELECTION));
        assertNotNull(testService.getTransportSelectorCallback());
        assertTrue(testService.waitForLatchCountdown(LATCH_ON_SERVICE_STATE_UPDATED));
        assertTrue(testService.waitForLatchCountdown(LATCH_ON_BARRING_INFO_UPDATED));

        assertTrue(testService.onCreated());

        // onWwanSelected()
        assertTrue(testService.onWwanSelected());
        assertTrue(testService.waitForLatchCountdown(LATCH_ON_WWAN_SELECTOR_CALLBACK));
        assertNotNull(testService.getWwanSelectorCallback());

        // onDomainSelected(DOMAIN_CS)
        assertTrue(testService.onDomainSelected(DOMAIN_CS));
        assertTrue(waitForVoiceLatchCountdown(LATCH_EMERGENCY_DIAL));

        // call disconnected
        sMockModemManager.clearAllCalls(sTestSlot, DisconnectCause.ERROR_UNSPECIFIED);

        assertTrue(waitForVoiceLatchCountdown(LATCH_GET_LAST_CALL_FAIL_CAUSE));

        // reselectDomain()
        assertTrue(testService.waitForLatchCountdown(LATCH_ON_RESELECTION));

        // dial CS again
        assertTrue(testService.onDomainSelected(DOMAIN_CS));
        assertTrue(waitForVoiceLatchCountdown(LATCH_EMERGENCY_DIAL));
    }

    @Test
    public void testDomainSelectionServiceEmergencyCall() throws Exception {
        if (VDBG) Log.d(LOG_TAG, "testDomainSelectionServiceEmergencyCall");

        if (sDisableCall) return;

        setupForEmergencyCalling();

        TestDomainSelectionService testService = sServiceConnector.getTestService();
        assertNotNull(testService);

        placeOutgoingCall(TEST_EMERGENCY_NUMBER);

        // onDomainSelection()
        assertTrue(testService.waitForLatchCountdown(LATCH_ON_DOMAIN_SELECTION));
        assertNotNull(testService.getTransportSelectorCallback());
        assertTrue(testService.waitForLatchCountdown(LATCH_ON_SERVICE_STATE_UPDATED));
        assertTrue(testService.waitForLatchCountdown(LATCH_ON_BARRING_INFO_UPDATED));

        SelectionAttributes attr = testService.getSelectionAttributes();
        assertNotNull(attr);
        assertEquals(SELECTOR_TYPE_CALLING, attr.getSelectorType());
        assertTrue(attr.isEmergency());
        assertNotNull(attr.getAddress());
        assertNotNull(attr.getAddress().getSchemeSpecificPart());
        assertEquals(TEST_EMERGENCY_NUMBER, attr.getAddress().getSchemeSpecificPart());

        assertTrue(testService.onCreated());

        // onWwanSelected()
        assertTrue(testService.onWwanSelected());
        assertTrue(testService.waitForLatchCountdown(LATCH_ON_WWAN_SELECTOR_CALLBACK));
        assertNotNull(testService.getWwanSelectorCallback());

        // onDomainSelected(DOMAIN_CS)
        assertTrue(testService.onDomainSelected(DOMAIN_CS));
        assertTrue(waitForVoiceLatchCountdown(LATCH_EMERGENCY_DIAL));

        // CS call state will be changed to ACTIVE in MockModem automatically.
        // finishSelection()
        assertTrue(testService.waitForLatchCountdown(LATCH_ON_FINISH_SELECTION));
    }

    @Test
    public void testDomainSelectionServiceEmergencyNetworkScan() throws Exception {
        if (VDBG) Log.d(LOG_TAG, "testDomainSelectionServiceEmergencyNetworkScan");

        setupForEmergencyCalling();

        TestDomainSelectionService testService = sServiceConnector.getTestService();
        assertNotNull(testService);

        placeOutgoingCall(TEST_EMERGENCY_NUMBER);

        assertTrue(testService.waitForLatchCountdown(LATCH_ON_DOMAIN_SELECTION));
        assertTrue(testService.onCreated());
        assertTrue(testService.onWwanSelected());
        assertTrue(testService.waitForLatchCountdown(LATCH_ON_WWAN_SELECTOR_CALLBACK));

        // onRequestEmergencyNetworkScan
        List<Integer> preferredNetworks = new ArrayList<>();
        preferredNetworks.add(Integer.valueOf(EUTRAN));
        preferredNetworks.add(Integer.valueOf(UTRAN));

        assertTrue(testService.requestEmergencyNetworkScan(
                preferredNetworks, SCAN_TYPE_NO_PREFERENCE));
        assertTrue(waitForNetworkLatchCountdown(LATCH_TRIGGER_EMERGENCY_SCAN));

        MockEmergencyRegResult regResult = getEmergencyRegResult(UTRAN,
                REGISTRATION_STATE_UNKNOWN, NetworkRegistrationInfo.DOMAIN_CS,
                false, false, 0, 0, "310", "260");
        sMockModemManager.unsolEmergencyNetworkScanResult(sTestSlot, regResult);

        assertTrue(testService.waitForLatchCountdown(LATCH_ON_EMERGENCY_REG_RESULT));

        EmergencyRegistrationResult receivedResult = testService.getEmergencyRegResult();

        assertNotNull(receivedResult);
        assertEquals(regResult.getAccessNetwork(), receivedResult.getAccessNetwork());
        assertEquals(regResult.getRegState(), receivedResult.getRegState());
        assertEquals(regResult.getDomain(), receivedResult.getDomain());
        assertEquals(regResult.getNwProvidedEmc(), receivedResult.getNwProvidedEmc());
        assertEquals(regResult.getNwProvidedEmf(), receivedResult.getNwProvidedEmf());
        assertEquals(regResult.isVopsSupported(), receivedResult.isVopsSupported());
        assertEquals(regResult.isEmcBearerSupported(), receivedResult.isEmcBearerSupported());
        assertEquals(regResult.getMcc(), receivedResult.getMcc());
        assertEquals(regResult.getMnc(), receivedResult.getMnc());
        assertEquals("us", receivedResult.getCountryIso());
    }

    private Call placeOutgoingCall(String address) throws Exception {
        mServiceCallBack = new ServiceCallBack();
        InCallServiceStateValidator.setCallbacks(mServiceCallBack);

        TelecomManager telecomManager =
                (TelecomManager) getContext().getSystemService(Context.TELECOM_SERVICE);

        final Uri imsUri = Uri.fromParts(PhoneAccount.SCHEME_TEL, address, null);
        Bundle extras = new Bundle();

        // Place outgoing call
        telecomManager.placeCall(imsUri, extras);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_ADDED, WAIT_FOR_CALL_STATE));

        Call call = getCall(getCurrentCallId());

        assertNotNull(call);
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DIALING, WAIT_FOR_CALL_STATE));

        return call;
    }

    private static void unsolBarringInfoChanged(boolean barred) {
        SparseArray<BarringInfo.BarringServiceInfo> serviceInfos = new SparseArray<>();
        if (barred) {
            serviceInfos.put(BARRING_SERVICE_TYPE_EMERGENCY,
                    new BarringInfo.BarringServiceInfo(BARRING_TYPE_UNCONDITIONAL, false, 0, 0));
        }
        assertTrue(sMockModemManager.unsolBarringInfoChanged(sTestSlot, serviceInfos));
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

    private boolean waitForNetworkLatchCountdown(int latchIndex) {
        return waitForNetworkLatchCountdown(latchIndex, WAIT_UPDATE_TIMEOUT_MS);
    }

    private boolean waitForNetworkLatchCountdown(int latchIndex, int waitMs) {
        return sMockModemManager.waitForNetworkLatchCountdown(sTestSlot, latchIndex, waitMs);
    }

    private boolean waitForVoiceLatchCountdown(int latchIndex) {
        return waitForVoiceLatchCountdown(latchIndex, WAIT_UPDATE_TIMEOUT_MS);
    }

    private boolean waitForVoiceLatchCountdown(int latchIndex, int waitMs) {
        return sMockModemManager.waitForVoiceLatchCountdown(sTestSlot, latchIndex, waitMs);
    }
}
