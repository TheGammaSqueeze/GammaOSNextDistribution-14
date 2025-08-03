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

package android.telephony.ims.cts;

import static android.telephony.CarrierConfigManager.ImsVoice.ALERTING_SRVCC_SUPPORT;
import static android.telephony.CarrierConfigManager.ImsVoice.BASIC_SRVCC_SUPPORT;
import static android.telephony.CarrierConfigManager.ImsVoice.MIDCALL_SRVCC_SUPPORT;
import static android.telephony.CarrierConfigManager.ImsVoice.PREALERTING_SRVCC_SUPPORT;
import static android.telephony.PreciseCallState.PRECISE_CALL_STATE_ACTIVE;
import static android.telephony.PreciseCallState.PRECISE_CALL_STATE_HOLDING;
import static android.telephony.PreciseCallState.PRECISE_CALL_STATE_INCOMING;
import static android.telephony.PreciseCallState.PRECISE_CALL_STATE_INCOMING_SETUP;
import static android.telephony.TelephonyManager.SRVCC_STATE_HANDOVER_CANCELED;
import static android.telephony.TelephonyManager.SRVCC_STATE_HANDOVER_STARTED;
import static android.telephony.mockmodem.MockImsService.LATCH_WAIT_FOR_SRVCC_CALL_INFO;
import static android.telephony.mockmodem.MockSimService.MOCK_SIM_PROFILE_ID_TWN_CHT;

import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.telecom.Call;
import android.telecom.PhoneAccount;
import android.telecom.TelecomManager;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.cts.InCallServiceStateValidator;
import android.telephony.ims.ImsCallProfile;
import android.telephony.ims.SrvccCall;
import android.telephony.mockmodem.MockModemManager;
import android.telephony.mockmodem.MockSrvccCall;
import android.util.Pair;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * CTS tests for ImsCall .
 */
@RunWith(AndroidJUnit4.class)
public class ImsCallingTestOnMockModem extends ImsCallingBase {

    private static final String LOG_TAG = "CtsImsCallingTestOnMockModem";
    private static final boolean VDBG = false;

    // the timeout to wait result in milliseconds
    private static final int WAIT_UPDATE_TIMEOUT_MS = 2000;
    // the timeout to wait for SIM_STATE_READY state
    private static final int WAIT_SIM_STATE_READY_MS = 5000;

    private static MockModemManager sMockModemManager;
    private static boolean sSupportsImsHal = false;

    static {
        initializeLatches();
    }

    @BeforeClass
    public static void beforeAllTests() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        TelephonyManager tm = (TelephonyManager) getContext()
                .getSystemService(Context.TELEPHONY_SERVICE);

        Pair<Integer, Integer> halVersion = tm.getHalVersion(TelephonyManager.HAL_SERVICE_IMS);
        if (!(halVersion.equals(TelephonyManager.HAL_VERSION_UNKNOWN)
                || halVersion.equals(TelephonyManager.HAL_VERSION_UNSUPPORTED))) {
            sSupportsImsHal = true;
        }

        if (!sSupportsImsHal) {
            return;
        }

        MockModemManager.enforceMockModemDeveloperSetting();
        sMockModemManager = new MockModemManager();
        assertNotNull(sMockModemManager);
        assertTrue(sMockModemManager.connectMockModemService(MOCK_SIM_PROFILE_ID_TWN_CHT));

        TimeUnit.MILLISECONDS.sleep(WAIT_UPDATE_TIMEOUT_MS);

        int simCardState = tm.getSimCardState();
        assertEquals(TelephonyManager.SIM_STATE_PRESENT, simCardState);

        TimeUnit.MILLISECONDS.sleep(WAIT_SIM_STATE_READY_MS);

        // Check SIM state ready
        simCardState = tm.getSimState();
        assertEquals(TelephonyManager.SIM_STATE_READY, simCardState);

        sTestSub = ImsUtils.getPreferredActiveSubId();

        int subId = SubscriptionManager.getSubscriptionId(sTestSlot);
        assertTrue(SubscriptionManager.isValidSubscriptionId(subId));
        sTestSub = subId;

        assertTrue(sMockModemManager.changeNetworkService(sTestSlot, 310260, true));

        TimeUnit.MILLISECONDS.sleep(WAIT_UPDATE_TIMEOUT_MS);

        beforeAllTestsBase();
    }

    @AfterClass
    public static void afterAllTests() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        if (!sSupportsImsHal) {
            return;
        }

        afterAllTestsBase();

        // Rebind all interfaces which is binding to MockModemService to default.
        if (sMockModemManager != null) {
            assertTrue(sMockModemManager.disconnectMockModemService());
            sMockModemManager = null;

            TimeUnit.MILLISECONDS.sleep(WAIT_UPDATE_TIMEOUT_MS);
        }
    }

    @Before
    public void beforeTest() throws Exception {
        assumeTrue(ImsUtils.shouldTestImsService());
        assumeTrue(sSupportsImsHal);

        if (sMockModemManager != null) {
            sMockModemManager.resetImsAllLatchCountdown();
        }
    }

    @After
    public void afterTest() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        if (!sSupportsImsHal) {
            return;
        }

        if (!mCalls.isEmpty() && (mCurrentCallId != null)) {
            Call call = mCalls.get(mCurrentCallId);
            call.disconnect();
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
    }

    @Test
    public void testSrvccActiveCall() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        assumeTrue(sSupportsImsHal);

        bindImsService();

        // Place outgoing call
        Call call = placeOutgoingCall();

        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DIALING, WAIT_FOR_CALL_STATE));

        TimeUnit.MILLISECONDS.sleep(WAIT_UPDATE_TIMEOUT_MS);
        TestImsCallSessionImpl callSession = sServiceConnector.getCarrierService().getMmTelFeature()
                .getImsCallsession();

        isCallActive(call, callSession);

        List<SrvccCall> profiles = new ArrayList<>();
        List<SrvccCall> effectiveProfiles = new ArrayList<>();
        PersistableBundle bundle = new PersistableBundle();
        bundle.putIntArray(
                CarrierConfigManager.ImsVoice.KEY_SRVCC_TYPE_INT_ARRAY,
                new int[] {
                        BASIC_SRVCC_SUPPORT,
                });
        SrvccCall srvccProfile = new SrvccCall(
                callSession.getCallId(),
                PRECISE_CALL_STATE_ACTIVE,
                callSession.getCallProfile());
        profiles.add(srvccProfile);
        effectiveProfiles.add(srvccProfile);

        verifySrvccTypeFiltered(bundle, profiles, effectiveProfiles);

        profiles.clear();
        effectiveProfiles.clear();

        ImsUtils.waitInCurrentState(WAIT_IN_CURRENT_STATE);
        call.disconnect();

        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DISCONNECTING, WAIT_FOR_CALL_STATE));
        isCallDisconnected(call, callSession);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOVED, WAIT_FOR_CALL_STATE));
        waitForUnboundService();
    }

    @Test
    @Ignore("b/259254356 - This api is not public yet")
    public void testSendAnbrQuery() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        assumeTrue(sSupportsImsHal);

        bindImsService();

        // Place outgoing call
        Call call = placeOutgoingCall();

        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DIALING, WAIT_FOR_CALL_STATE));

        TestImsCallSessionImpl callSession = sServiceConnector.getCarrierService().getMmTelFeature()
                .getImsCallsession();

        isCallActive(call, callSession);

        sMockModemManager.resetAnbrValues(sTestSlot);
        callSession.callSessionSendAnbrQuery(2, 1, 24400);
        TimeUnit.MILLISECONDS.sleep(500);

        int[] retValues = sMockModemManager.getAnbrValues(sTestSlot);
        assertNotNull(retValues);
        assertEquals(2, retValues[0]);
        assertEquals(1, retValues[1]);
        assertEquals(24400, retValues[2]);

        call.disconnect();
        waitForUnboundService();
    }

    @Test
    @Ignore("b/259254356 - This api is not public yet")
    public void testNotifyAnbr() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        assumeTrue(sSupportsImsHal);

        bindImsService();

        // Place outgoing call
        Call call = placeOutgoingCall();

        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DIALING, WAIT_FOR_CALL_STATE));

        TestImsCallSessionImpl callSession = sServiceConnector.getCarrierService().getMmTelFeature()
                .getImsCallsession();

        isCallActive(call, callSession);

        callSession.resetAnbrValues();
        assertTrue(sMockModemManager.notifyAnbr(sTestSlot, 2, 1, 24400));
        TimeUnit.MILLISECONDS.sleep(500);

        int[] retValues = callSession.getAnbrValues();
        assertNotNull(retValues);
        assertEquals(2, retValues[0]);
        assertEquals(1, retValues[1]);
        assertEquals(24400, retValues[2]);

        call.disconnect();
        waitForUnboundService();
    }

    @Test
    public void testSrvccIncomingCall() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        assumeTrue(sSupportsImsHal);

        bindImsService();
        mServiceCallBack = new ServiceCallBack();
        InCallServiceStateValidator.setCallbacks(mServiceCallBack);

        Bundle extras = new Bundle();
        sServiceConnector.getCarrierService().getMmTelFeature().onIncomingCallReceived(extras);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_ADDED, WAIT_FOR_CALL_STATE));

        Call call = getCall(mCurrentCallId);
        if (call.getDetails().getState() == call.STATE_RINGING) {
            TimeUnit.MILLISECONDS.sleep(5000);

            TestImsCallSessionImpl callSession = sServiceConnector.getCarrierService()
                    .getMmTelFeature().getImsCallsession();

            List<SrvccCall> profiles = new ArrayList<>();
            List<SrvccCall> effectiveProfiles = new ArrayList<>();
            PersistableBundle bundle = new PersistableBundle();
            bundle.putIntArray(
                    CarrierConfigManager.ImsVoice.KEY_SRVCC_TYPE_INT_ARRAY,
                    new int[] {
                            BASIC_SRVCC_SUPPORT,
                    });
            SrvccCall srvccProfile = new SrvccCall(
                    callSession.getCallId(),
                    PRECISE_CALL_STATE_INCOMING,
                    callSession.getCallProfile());
            profiles.add(srvccProfile);

            verifySrvccTypeFiltered(bundle, profiles, effectiveProfiles);

            bundle = new PersistableBundle();
            bundle.putIntArray(
                    CarrierConfigManager.ImsVoice.KEY_SRVCC_TYPE_INT_ARRAY,
                    new int[] {
                            BASIC_SRVCC_SUPPORT,
                            ALERTING_SRVCC_SUPPORT,
                    });
            effectiveProfiles.add(srvccProfile);

            verifySrvccTypeFiltered(bundle, profiles, effectiveProfiles);

            profiles.clear();
            effectiveProfiles.clear();

            call.answer(0);
        }

        TestImsCallSessionImpl callSession = sServiceConnector.getCarrierService().getMmTelFeature()
                .getImsCallsession();

        isCallActive(call, callSession);

        ImsUtils.waitInCurrentState(WAIT_IN_CURRENT_STATE);
        callSession.terminateIncomingCall();

        isCallDisconnected(call, callSession);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOVED, WAIT_FOR_CALL_STATE));
        waitForUnboundService();
    }

    @Test
    public void testSrvccPreAlertingIncomingCall() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        assumeTrue(sSupportsImsHal);

        bindImsService();

        List<SrvccCall> profiles = new ArrayList<>();
        List<SrvccCall> effectiveProfiles = new ArrayList<>();
        PersistableBundle bundle = new PersistableBundle();
        bundle.putIntArray(
                CarrierConfigManager.ImsVoice.KEY_SRVCC_TYPE_INT_ARRAY,
                new int[] {
                        BASIC_SRVCC_SUPPORT,
                });
        SrvccCall srvccProfile = new SrvccCall("",
                PRECISE_CALL_STATE_INCOMING_SETUP, new ImsCallProfile());
        profiles.add(srvccProfile);

        verifySrvccTypeFiltered(bundle, profiles, effectiveProfiles);

        bundle = new PersistableBundle();
        bundle.putIntArray(
                CarrierConfigManager.ImsVoice.KEY_SRVCC_TYPE_INT_ARRAY,
                new int[] {
                        BASIC_SRVCC_SUPPORT,
                        PREALERTING_SRVCC_SUPPORT,
                });
        effectiveProfiles.add(srvccProfile);

        verifySrvccTypeFiltered(bundle, profiles, effectiveProfiles);

        profiles.clear();
        effectiveProfiles.clear();
    }

    @Test
    public void testSrvccHoldingCall() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        assumeTrue(sSupportsImsHal);

        bindImsService();

        // Place outgoing call
        Call call = placeOutgoingCall();

        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DIALING, WAIT_FOR_CALL_STATE));

        TimeUnit.MILLISECONDS.sleep(WAIT_UPDATE_TIMEOUT_MS);
        TestImsCallSessionImpl callSession = sServiceConnector.getCarrierService().getMmTelFeature()
                .getImsCallsession();

        isCallActive(call, callSession);

        ImsUtils.waitInCurrentState(WAIT_IN_CURRENT_STATE);
        // Put on hold
        call.hold();
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_HOLDING, WAIT_FOR_CALL_STATE));

        List<SrvccCall> profiles = new ArrayList<>();
        List<SrvccCall> effectiveProfiles = new ArrayList<>();
        PersistableBundle bundle = new PersistableBundle();
        bundle.putIntArray(
                CarrierConfigManager.ImsVoice.KEY_SRVCC_TYPE_INT_ARRAY,
                new int[] {
                        BASIC_SRVCC_SUPPORT,
                });
        SrvccCall srvccProfile = new SrvccCall(
                callSession.getCallId(),
                PRECISE_CALL_STATE_HOLDING,
                callSession.getCallProfile());
        profiles.add(srvccProfile);

        verifySrvccTypeFiltered(bundle, profiles, effectiveProfiles);

        bundle = new PersistableBundle();
        bundle.putIntArray(
                CarrierConfigManager.ImsVoice.KEY_SRVCC_TYPE_INT_ARRAY,
                new int[] {
                        BASIC_SRVCC_SUPPORT,
                        MIDCALL_SRVCC_SUPPORT,
                });
        effectiveProfiles.add(srvccProfile);

        verifySrvccTypeFiltered(bundle, profiles, effectiveProfiles);

        profiles.clear();
        effectiveProfiles.clear();

        ImsUtils.waitInCurrentState(WAIT_IN_CURRENT_STATE);
        call.disconnect();

        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DISCONNECTING, WAIT_FOR_CALL_STATE));
        isCallDisconnected(call, callSession);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOVED, WAIT_FOR_CALL_STATE));
        waitForUnboundService();
    }

    private void verifySrvccStateChange(int state) throws Exception {
        assertTrue(sMockModemManager.srvccStateNotify(sTestSlot, state));
        sServiceConnector.getCarrierService().getMmTelFeature()
                .getSrvccStateLatch().await(WAIT_UPDATE_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        assertEquals(state,
                sServiceConnector.getCarrierService().getMmTelFeature().getSrvccState());
    }

    private void verifySrvccTypeFiltered(PersistableBundle bundle,
            List<SrvccCall> profiles, List<SrvccCall> effectiveProfiles)
            throws Exception {
        // Trigger carrier config changed
        overrideCarrierConfig(bundle);

        // HANDOVER_STARTED
        verifySrvccStateChange(SRVCC_STATE_HANDOVER_STARTED);

        sServiceConnector.getCarrierService().getMmTelFeature().notifySrvccCall(profiles);
        assertTrue(waitForMockImsStateLatchCountdown(LATCH_WAIT_FOR_SRVCC_CALL_INFO));

        List<MockSrvccCall> srvccCalls = sMockModemManager.getSrvccCalls(sTestSlot);
        assertNotNull(srvccCalls);
        assertEquals(effectiveProfiles.size(), srvccCalls.size());

        // HANDOVER_CANCELED
        verifySrvccStateChange(SRVCC_STATE_HANDOVER_CANCELED);
    }

    private Call placeOutgoingCall() throws Exception {
        mServiceCallBack = new ServiceCallBack();
        InCallServiceStateValidator.setCallbacks(mServiceCallBack);

        TelecomManager telecomManager = (TelecomManager) InstrumentationRegistry
                .getInstrumentation().getContext().getSystemService(Context.TELECOM_SERVICE);

        final Uri imsUri = Uri.fromParts(PhoneAccount.SCHEME_TEL, String.valueOf(++sCounter), null);
        Bundle extras = new Bundle();

        // Place outgoing call
        telecomManager.placeCall(imsUri, extras);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_ADDED, WAIT_FOR_CALL_STATE));

        Call call = getCall(mCurrentCallId);

        return call;
    }

    public boolean waitForMockImsStateLatchCountdown(int latchIndex) {
        return waitForMockImsStateLatchCountdown(latchIndex, WAIT_UPDATE_TIMEOUT_MS);
    }

    public boolean waitForMockImsStateLatchCountdown(int latchIndex, int waitMs) {
        return sMockModemManager.waitForImsLatchCountdown(latchIndex, waitMs);
    }
}
