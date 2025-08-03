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

import static android.telephony.mockmodem.MockSimService.MOCK_SIM_PROFILE_ID_TWN_CHT;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.radio.network.Domain;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.PersistableBundle;
import android.telecom.Call;
import android.telecom.PhoneAccount;
import android.telecom.TelecomManager;
import android.telephony.AccessNetworkConstants;
import android.telephony.CarrierConfigManager;
import android.telephony.DisconnectCause;
import android.telephony.NetworkRegistrationInfo;
import android.telephony.ServiceState;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;
import android.telephony.cts.InCallServiceStateValidator;
import android.telephony.ims.ImsReasonInfo;
import android.telephony.ims.feature.MmTelFeature;
import android.telephony.ims.stub.ImsRegistrationImplBase;
import android.telephony.mockmodem.MockModemManager;
import android.util.Log;

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
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class CallDomainSelectionTestOnMockModem extends ImsCallingBase {

    private static final String TAG = CallDomainSelectionTestOnMockModem.class.getSimpleName();

    // the timeout to wait for result in milliseconds
    private static final int WAIT_UPDATE_TIMEOUT_MS = 5000;
    private static final String TEST_DIAL_NUMBER = "12345";
    private static final String TEST_DIAL_WPS_NUMBER = "*2729647512345";
    public static boolean sIsDomainSelectionSupported;
    private static MockModemManager sMockModemManager;
    private static HandlerThread sCallStateChangeCallbackHandlerThread;
    private static Handler sCallStateChangeCallbackHandler;
    private static CallStateListener sCallStateCallback;
    private static TelephonyManager sTelephonyManager;
    private static TelecomManager sTelecomManager;
    private final Executor mCallStateChangeExecutor = Runnable::run;
    private final Object mCallStateChangeLock = new Object();

    private int mCallState;

    static {
        initializeLatches();
    }

    @BeforeClass
    public static void beforeAllTests() throws Exception {
        Log.d(TAG, "ims.cts beforeAllTests");
        if (!ImsUtils.shouldTestImsService()) {
            Log.d(TAG, "ims.cts shouldTestImsService returned false");
            return;
        }

        // Check if domain selection feature is enabled.
        sTelephonyManager =
                (TelephonyManager) getContext().getSystemService(Context.TELEPHONY_SERVICE);
        assertNotNull(sTelephonyManager);

        sTelecomManager =
                (TelecomManager)
                        InstrumentationRegistry.getInstrumentation()
                                .getContext()
                                .getSystemService(Context.TELECOM_SERVICE);
        assertNotNull(sTelecomManager);

        sIsDomainSelectionSupported =
                ShellIdentityUtils.invokeMethodWithShellPermissions(
                        sTelephonyManager, (tm) -> tm.isDomainSelectionSupported());
        if (!sIsDomainSelectionSupported) {
            Log.d(TAG, "ims.cts sIsDomainSelectionSupported is false");
            return;
        }

        // Load mock SIM profile.
        MockModemManager.enforceMockModemDeveloperSetting();
        sMockModemManager = new MockModemManager();
        assertNotNull(sMockModemManager);
        assertTrue(sMockModemManager.connectMockModemService(MOCK_SIM_PROFILE_ID_TWN_CHT));

        sCallStateChangeCallbackHandlerThread = new HandlerThread("TelephonyManagerCallbackTest");
        sCallStateChangeCallbackHandlerThread.start();
        sCallStateChangeCallbackHandler =
                new Handler(sCallStateChangeCallbackHandlerThread.getLooper());

        TimeUnit.MILLISECONDS.sleep(WAIT_UPDATE_TIMEOUT_MS);

        int simCardState = sTelephonyManager.getSimCardState();
        assertEquals(TelephonyManager.SIM_STATE_PRESENT, simCardState);

        TimeUnit.MILLISECONDS.sleep(WAIT_UPDATE_TIMEOUT_MS);

        // Check SIM state ready
        simCardState = sTelephonyManager.getSimState();
        assertEquals(TelephonyManager.SIM_STATE_READY, simCardState);

        sTestSub = ImsUtils.getPreferredActiveSubId();

        int sub = SubscriptionManager.getSubscriptionId(sTestSlot);
        if (SubscriptionManager.isValidSubscriptionId(sub)) {
            sTestSub = sub;
            beforeAllTestsBase();
            Log.d(TAG, "beforeAllTestsBase called with valid subscription");
        }
    }

    @Before
    public void setup() throws Exception {
        assumeTrue(sMockModemManager != null);
    }

    @AfterClass
    public static void afterAllTests() throws Exception {
        Log.d(TAG, "after class");
        if (!hasFeature(PackageManager.FEATURE_TELEPHONY)) {
            return;
        }

        if (sCallStateChangeCallbackHandlerThread != null) {
            sCallStateChangeCallbackHandlerThread.quitSafely();
        }

        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        if (!sIsDomainSelectionSupported) {
            return;
        }

        afterAllTestsBase();

        // Rebind all interfaces which is binding to MockModemService to default.
        if (sMockModemManager != null) {
            sMockModemManager.removeSimCard(sTestSlot);
            assertTrue(sMockModemManager.disconnectMockModemService());
            sMockModemManager = null;

            TimeUnit.MILLISECONDS.sleep(WAIT_UPDATE_TIMEOUT_MS);
        }
    }

    @After
    public void afterTest() throws Exception {
        // Out of service
        if (sMockModemManager != null) {
            sMockModemManager.changeNetworkService(
                    sTestSlot, MOCK_SIM_PROFILE_ID_TWN_CHT, false, Domain.CS | Domain.PS);
        }
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        if (!mCalls.isEmpty() && (mCurrentCallId != null)) {
            Call call = mCalls.get(mCurrentCallId);
            call.disconnect();
        }

        // Set the untracked CountDownLatches which are reset in ServiceCallBack
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

    private void placeOutgoingCall(String phoneNumber) {
        Log.d(TAG, "Start dialing call");
        final Uri address = Uri.fromParts(PhoneAccount.SCHEME_TEL, phoneNumber, null);
        sTelecomManager.placeCall(address, null);
    }

    private boolean isCsDialing() {
        return sMockModemManager.getNumberOfOngoingCSCalls(sTestSlot) > 0;
    }

    private boolean isPsDialing() throws Exception {
        Thread.sleep(2000);

        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_ADDED, WAIT_FOR_CALL_STATE));

        Call call = getCall(mCurrentCallId);
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DIALING, WAIT_FOR_CALL_STATE));

        waitForCallSession();
        TestImsCallSessionImpl callSession =
                sServiceConnector.getCarrierService().getMmTelFeature().getImsCallsession();

        isCallActive(call, callSession);
        Log.d(TAG, "Call is active in PS");
        return true;
    }

    private int getActiveSubId(int phoneId) {
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity("android.permission.READ_PRIVILEGED_PHONE_STATE");

        int[] allSubs =
                getContext()
                        .getSystemService(SubscriptionManager.class)
                        .getActiveSubscriptionIdList();
        int subsLength = allSubs.length;
        Log.d(TAG, " Active Sub length is " + subsLength);

        assertTrue(phoneId <= (subsLength - 1));

        return allSubs[phoneId];
    }

    private class CallStateListener extends TelephonyCallback
            implements TelephonyCallback.CallStateListener {
        @Override
        public void onCallStateChanged(int state) {
            synchronized (mCallStateChangeLock) {
                mCallState = state;
                Log.d(TAG, "Call state change to " + mCallState);
                mCallStateChangeLock.notify();
            }
        }
    }

    private int getRegState(int domain, int subId) {
        int reg;

        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity("android.permission.READ_PHONE_STATE");

        ServiceState ss = sTelephonyManager.createForSubscriptionId(subId).getServiceState();
        assertNotNull(ss);

        NetworkRegistrationInfo nri =
                ss.getNetworkRegistrationInfo(domain, AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
        assertNotNull(nri);

        reg = nri.getRegistrationState();
        Log.d(TAG, "SS: " + NetworkRegistrationInfo.registrationStateToString(reg));

        return reg;
    }

    @Test
    public void testCombinedAttachPsSelected() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        sMockModemManager.changeNetworkService(
                sTestSlot, MOCK_SIM_PROFILE_ID_TWN_CHT, true, Domain.CS | Domain.PS);
        TimeUnit.SECONDS.sleep(1);

        bindImsService();
        mServiceCallBack = new ServiceCallBack();
        InCallServiceStateValidator.setCallbacks(mServiceCallBack);

        int subId = getActiveSubId(sTestSlot);

        assertEquals(
                NetworkRegistrationInfo.REGISTRATION_STATE_HOME,
                getRegState(NetworkRegistrationInfo.DOMAIN_PS, subId));

        assertEquals(
                NetworkRegistrationInfo.REGISTRATION_STATE_HOME,
                getRegState(NetworkRegistrationInfo.DOMAIN_CS, subId));

        // Register call state change callback
        mCallState = TelephonyManager.CALL_STATE_IDLE;
        sCallStateChangeCallbackHandler.post(
                () -> {
                    sCallStateCallback = new CallStateListener();
                    ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                            sTelephonyManager,
                            (tm) ->
                                    tm.registerTelephonyCallback(
                                            mCallStateChangeExecutor, sCallStateCallback));
                });

        placeOutgoingCall(TEST_DIAL_NUMBER);
        TimeUnit.SECONDS.sleep(1);

        // Verify call state
        synchronized (mCallStateChangeLock) {
            if (mCallState == TelephonyManager.CALL_STATE_IDLE) {
                Log.d(TAG, "Wait for call state change to offhook");
                mCallStateChangeLock.wait(WAIT_UPDATE_TIMEOUT_MS);
            }
            assertEquals(TelephonyManager.CALL_STATE_OFFHOOK, mCallState);
        }
        assertTrue(sTelephonyManager.isImsRegistered());

        assertTrue(isPsDialing());

        // Hang up the call
        Log.d(TAG, "Hangup call");
        sTelecomManager.endCall();
        TimeUnit.SECONDS.sleep(1);

        // Unregister call state change callback
        sTelephonyManager.unregisterTelephonyCallback(sCallStateCallback);
        waitForUnboundService();
        sCallStateCallback = null;
    }

    @Test
    public void testVoiceCallOverPs() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        sMockModemManager.changeNetworkService(
                sTestSlot, MOCK_SIM_PROFILE_ID_TWN_CHT, true, Domain.PS);
        TimeUnit.SECONDS.sleep(1);

        bindImsService();
        mServiceCallBack = new ServiceCallBack();
        InCallServiceStateValidator.setCallbacks(mServiceCallBack);

        int subId = getActiveSubId(sTestSlot);

        assertEquals(
                NetworkRegistrationInfo.REGISTRATION_STATE_HOME,
                getRegState(NetworkRegistrationInfo.DOMAIN_PS, subId));

        assertEquals(
                NetworkRegistrationInfo.REGISTRATION_STATE_NOT_REGISTERED_SEARCHING,
                getRegState(NetworkRegistrationInfo.DOMAIN_CS, subId));

        // Register call state change callback
        mCallState = TelephonyManager.CALL_STATE_IDLE;
        sCallStateChangeCallbackHandler.post(
                () -> {
                    sCallStateCallback = new CallStateListener();
                    ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                            sTelephonyManager,
                            (tm) ->
                                    tm.registerTelephonyCallback(
                                            mCallStateChangeExecutor, sCallStateCallback));
                });

        placeOutgoingCall(TEST_DIAL_NUMBER);
        TimeUnit.SECONDS.sleep(1);

        // Verify call state
        synchronized (mCallStateChangeLock) {
            if (mCallState == TelephonyManager.CALL_STATE_IDLE) {
                Log.d(TAG, "Wait for call state change to offhook");
                mCallStateChangeLock.wait(WAIT_UPDATE_TIMEOUT_MS);
            }
            assertEquals(TelephonyManager.CALL_STATE_OFFHOOK, mCallState);
        }
        assertTrue(sTelephonyManager.isImsRegistered());

        assertTrue(isPsDialing());

        // Hang up the call
        Log.d(TAG, "Hangup call");
        sTelecomManager.endCall();
        TimeUnit.SECONDS.sleep(1);

        // Unregister call state change callback
        sTelephonyManager.unregisterTelephonyCallback(sCallStateCallback);
        sCallStateCallback = null;
        waitForUnboundService();
    }

    @Test
    public void testVoiceCallOverCs() throws Exception {
        sMockModemManager.changeNetworkService(
                sTestSlot, MOCK_SIM_PROFILE_ID_TWN_CHT, true, Domain.CS);
        TimeUnit.SECONDS.sleep(1);

        int subId = getActiveSubId(sTestSlot);

        assertEquals(
                NetworkRegistrationInfo.REGISTRATION_STATE_HOME, getRegState(Domain.CS, subId));
        assertEquals(
                NetworkRegistrationInfo.REGISTRATION_STATE_NOT_REGISTERED_SEARCHING,
                getRegState(Domain.PS, subId));

        // Register call state change callback
        mCallState = TelephonyManager.CALL_STATE_IDLE;
        sCallStateChangeCallbackHandler.post(
                () -> {
                    sCallStateCallback = new CallStateListener();
                    ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                            sTelephonyManager,
                            (tm) ->
                                    tm.registerTelephonyCallback(
                                            mCallStateChangeExecutor, sCallStateCallback));
                });

        placeOutgoingCall(TEST_DIAL_NUMBER);
        TimeUnit.SECONDS.sleep(1);

        // Verify call state
        synchronized (mCallStateChangeLock) {
            if (mCallState == TelephonyManager.CALL_STATE_IDLE) {
                Log.d(TAG, "Wait for the CS call state change to offhook");
                mCallStateChangeLock.wait(WAIT_UPDATE_TIMEOUT_MS);
            }
            assertEquals(TelephonyManager.CALL_STATE_OFFHOOK, mCallState);
        }

        assertFalse(sTelephonyManager.isImsRegistered());

        // call is in CS
        assertTrue(sTelecomManager.isInCall());
        assertTrue(isCsDialing());

        // Hang up the call
        Log.d(TAG, "Hangup call");
        sTelecomManager.endCall();
        TimeUnit.SECONDS.sleep(1);

        // Unregister call state change callback
        sTelephonyManager.unregisterTelephonyCallback(sCallStateCallback);
        sCallStateCallback = null;
    }

    @Test
    public void testVoiceCallOverVoWifi() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        sMockModemManager.changeNetworkService(
                sTestSlot, MOCK_SIM_PROFILE_ID_TWN_CHT, true, Domain.PS | Domain.CS);
        TimeUnit.SECONDS.sleep(1);

        bindImsService(ImsRegistrationImplBase.REGISTRATION_TECH_IWLAN);
        mServiceCallBack = new ServiceCallBack();
        InCallServiceStateValidator.setCallbacks(mServiceCallBack);

        int subId = getActiveSubId(sTestSlot);

        assertEquals(
                NetworkRegistrationInfo.REGISTRATION_STATE_HOME,
                getRegState(NetworkRegistrationInfo.DOMAIN_PS, subId));

        assertEquals(
                NetworkRegistrationInfo.REGISTRATION_STATE_HOME,
                getRegState(NetworkRegistrationInfo.DOMAIN_CS, subId));

        // Register call state change callback
        mCallState = TelephonyManager.CALL_STATE_IDLE;
        sCallStateChangeCallbackHandler.post(
                () -> {
                    sCallStateCallback = new CallStateListener();
                    ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                            sTelephonyManager,
                            (tm) ->
                                    tm.registerTelephonyCallback(
                                            mCallStateChangeExecutor, sCallStateCallback));
                });

        placeOutgoingCall(TEST_DIAL_NUMBER);
        TimeUnit.SECONDS.sleep(1);

        // Verify call state
        synchronized (mCallStateChangeLock) {
            if (mCallState == TelephonyManager.CALL_STATE_IDLE) {
                Log.d(TAG, "Wait for call state change to offhook");
                mCallStateChangeLock.wait(WAIT_UPDATE_TIMEOUT_MS);
            }
            assertEquals(TelephonyManager.CALL_STATE_OFFHOOK, mCallState);
        }
        assertTrue(sTelephonyManager.isImsRegistered());

        assertTrue(isPsDialing());

        // Hang up the call
        Log.d(TAG, "Hangup call");
        sTelecomManager.endCall();
        TimeUnit.SECONDS.sleep(1);

        // Unregister call state change callback
        sTelephonyManager.unregisterTelephonyCallback(sCallStateCallback);
        sCallStateCallback = null;
        waitForUnboundService();
    }

    @Test
    public void testCallWhenImsForSmsOnlyAndCsfbSupported() throws Exception {
        sMockModemManager.changeNetworkService(
                sTestSlot, MOCK_SIM_PROFILE_ID_TWN_CHT, true, Domain.CS | Domain.PS);
        TimeUnit.SECONDS.sleep(1);

        MmTelFeature.MmTelCapabilities capabilities =
                new MmTelFeature.MmTelCapabilities(
                        MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_SMS);

        bindImsServiceForCapabilities(ImsRegistrationImplBase.REGISTRATION_TECH_LTE, capabilities);
        mServiceCallBack = new ServiceCallBack();
        InCallServiceStateValidator.setCallbacks(mServiceCallBack);
        int subId = getActiveSubId(sTestSlot);

        assertEquals(
                NetworkRegistrationInfo.REGISTRATION_STATE_HOME,
                getRegState(NetworkRegistrationInfo.DOMAIN_PS, subId));
        assertEquals(
                NetworkRegistrationInfo.REGISTRATION_STATE_HOME,
                getRegState(NetworkRegistrationInfo.DOMAIN_CS, subId));

        // Register call state change callback
        mCallState = TelephonyManager.CALL_STATE_IDLE;
        sCallStateChangeCallbackHandler.post(
                () -> {
                    sCallStateCallback = new CallStateListener();
                    ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                            sTelephonyManager,
                            (tm) ->
                                    tm.registerTelephonyCallback(
                                            mCallStateChangeExecutor, sCallStateCallback));
                });

        placeOutgoingCall(TEST_DIAL_NUMBER);
        TimeUnit.SECONDS.sleep(1);

        // Verify call state
        synchronized (mCallStateChangeLock) {
            if (mCallState == TelephonyManager.CALL_STATE_IDLE) {
                Log.d(TAG, "Wait for the call state change to offhook");
                mCallStateChangeLock.wait(WAIT_UPDATE_TIMEOUT_MS);
            }
            assertEquals(TelephonyManager.CALL_STATE_OFFHOOK, mCallState);
        }

        assertTrue(isCsDialing());

        // Hang up the call
        Log.d(TAG, "Hangup call");
        sTelecomManager.endCall();
        TimeUnit.SECONDS.sleep(1);

        // Unregister call state change callback
        sTelephonyManager.unregisterTelephonyCallback(sCallStateCallback);
        sCallStateCallback = null;
        waitForUnboundService();
    }

    @Test
    public void testCallWhenImsForSmsOnlyAndCsfbNotSupported() throws Exception {
        sMockModemManager.changeNetworkService(
                sTestSlot, MOCK_SIM_PROFILE_ID_TWN_CHT, true, Domain.PS);
        TimeUnit.SECONDS.sleep(1);

        MmTelFeature.MmTelCapabilities capabilities =
                new MmTelFeature.MmTelCapabilities(
                        MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_SMS);
        bindImsServiceForCapabilities(ImsRegistrationImplBase.REGISTRATION_TECH_LTE, capabilities);
        mServiceCallBack = new ServiceCallBack();
        InCallServiceStateValidator.setCallbacks(mServiceCallBack);
        int subId = getActiveSubId(sTestSlot);

        assertEquals(
                NetworkRegistrationInfo.REGISTRATION_STATE_HOME,
                getRegState(NetworkRegistrationInfo.DOMAIN_PS, subId));

        assertEquals(
                NetworkRegistrationInfo.REGISTRATION_STATE_NOT_REGISTERED_SEARCHING,
                getRegState(NetworkRegistrationInfo.DOMAIN_CS, subId));

        // Register call state change callback
        mCallState = TelephonyManager.CALL_STATE_IDLE;
        sCallStateChangeCallbackHandler.post(
                () -> {
                    sCallStateCallback = new CallStateListener();
                    ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                            sTelephonyManager,
                            (tm) ->
                                    tm.registerTelephonyCallback(
                                            mCallStateChangeExecutor, sCallStateCallback));
                });

        placeOutgoingCall(TEST_DIAL_NUMBER);
        TimeUnit.SECONDS.sleep(1);

        // Verify call state
        synchronized (mCallStateChangeLock) {
            if (mCallState == TelephonyManager.CALL_STATE_IDLE) {
                Log.d(TAG, "Wait for the call state update");
                mCallStateChangeLock.wait(WAIT_UPDATE_TIMEOUT_MS);
            }
            assertNotEquals(TelephonyManager.CALL_STATE_OFFHOOK, mCallState);
        }

        assertFalse(isCsDialing());
        assertFalse(sTelecomManager.isInCall());
        TimeUnit.SECONDS.sleep(1);

        // Unregister call state change callback
        sTelephonyManager.unregisterTelephonyCallback(sCallStateCallback);
        sCallStateCallback = null;
        waitForUnboundService();
    }

    @Test
    public void testVoiceCallInNoService() throws Exception {
        sMockModemManager.changeNetworkService(
                sTestSlot, MOCK_SIM_PROFILE_ID_TWN_CHT, false, Domain.CS | Domain.PS);
        TimeUnit.SECONDS.sleep(1);

        int subId = getActiveSubId(sTestSlot);

        assertEquals(
                NetworkRegistrationInfo.REGISTRATION_STATE_NOT_REGISTERED_SEARCHING,
                getRegState(NetworkRegistrationInfo.DOMAIN_PS, subId));

        assertEquals(
                NetworkRegistrationInfo.REGISTRATION_STATE_NOT_REGISTERED_SEARCHING,
                getRegState(NetworkRegistrationInfo.DOMAIN_CS, subId));

        placeOutgoingCall(TEST_DIAL_NUMBER);
        TimeUnit.SECONDS.sleep(2);

        assertFalse(sTelecomManager.isInCall());

        TimeUnit.SECONDS.sleep(1);
    }

    @Test
    public void testPsVoiceCallEndedWhenRinging() throws Exception {
        sMockModemManager.changeNetworkService(
                sTestSlot, MOCK_SIM_PROFILE_ID_TWN_CHT, true, Domain.PS);

        bindImsService();
        mServiceCallBack = new ServiceCallBack();
        InCallServiceStateValidator.setCallbacks(mServiceCallBack);
        int subId = getActiveSubId(sTestSlot);

        assertEquals(
                NetworkRegistrationInfo.REGISTRATION_STATE_HOME,
                getRegState(NetworkRegistrationInfo.DOMAIN_PS, subId));
        assertEquals(
                NetworkRegistrationInfo.REGISTRATION_STATE_NOT_REGISTERED_SEARCHING,
                getRegState(NetworkRegistrationInfo.DOMAIN_CS, subId));

        assertTrue(sTelephonyManager.isImsRegistered(subId));

        // Register call state change callback
        mCallState = TelephonyManager.CALL_STATE_IDLE;
        sCallStateChangeCallbackHandler.post(
                () -> {
                    sCallStateCallback = new CallStateListener();
                    ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                            sTelephonyManager,
                            (tm) ->
                                    tm.registerTelephonyCallback(
                                            mCallStateChangeExecutor, sCallStateCallback));
                });

        placeOutgoingCall(TEST_DIAL_NUMBER);
        TimeUnit.SECONDS.sleep(1);

        // Verify call state
        synchronized (mCallStateChangeLock) {
            if (mCallState == TelephonyManager.CALL_STATE_IDLE) {
                Log.d(TAG, "Wait for call state change from IDLE");
                mCallStateChangeLock.wait(WAIT_UPDATE_TIMEOUT_MS);
            }
            Log.d(TAG, "Hangup call");
            sTelecomManager.endCall();
        }
        TimeUnit.SECONDS.sleep(1);
        assertFalse(sTelecomManager.isInCall());

        // Unregister call state change callback
        sTelephonyManager.unregisterTelephonyCallback(sCallStateCallback);
        sCallStateCallback = null;
        waitForUnboundService();
    }

    @Test
    public void testCsVoiceCallEndedWhenAlerting() throws Exception {
        sMockModemManager.changeNetworkService(
                sTestSlot, MOCK_SIM_PROFILE_ID_TWN_CHT, true, Domain.CS);

        int subId = getActiveSubId(sTestSlot);

        assertEquals(
                NetworkRegistrationInfo.REGISTRATION_STATE_NOT_REGISTERED_SEARCHING,
                getRegState(NetworkRegistrationInfo.DOMAIN_PS, subId));
        assertEquals(
                NetworkRegistrationInfo.REGISTRATION_STATE_HOME,
                getRegState(NetworkRegistrationInfo.DOMAIN_CS, subId));

        assertFalse(sTelephonyManager.isImsRegistered(subId));

        // Register call state change callback
        mCallState = TelephonyManager.CALL_STATE_IDLE;
        sCallStateChangeCallbackHandler.post(
                () -> {
                    sCallStateCallback = new CallStateListener();
                    ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                            sTelephonyManager,
                            (tm) ->
                                    tm.registerTelephonyCallback(
                                            mCallStateChangeExecutor, sCallStateCallback));
                });

        placeOutgoingCall(TEST_DIAL_NUMBER);
        TimeUnit.SECONDS.sleep(1);

        // Verify call state
        synchronized (mCallStateChangeLock) {
            if (mCallState == TelephonyManager.CALL_STATE_IDLE) {
                Log.d(TAG, "Wait for call state change from IDLE");
                mCallStateChangeLock.wait(WAIT_UPDATE_TIMEOUT_MS);
            }
            Log.d(TAG, "Hangup call");
            sTelecomManager.endCall();
        }
        TimeUnit.SECONDS.sleep(1);
        assertFalse(sTelecomManager.isInCall());

        // Unregister call state change callback
        sTelephonyManager.unregisterTelephonyCallback(sCallStateCallback);
        sCallStateCallback = null;
    }

    @Test
    public void testPsCallEndOnPeerDevice() throws Exception {
        sMockModemManager.changeNetworkService(
                sTestSlot, MOCK_SIM_PROFILE_ID_TWN_CHT, true, Domain.PS | Domain.CS);

        bindImsService();
        mServiceCallBack = new ServiceCallBack();
        InCallServiceStateValidator.setCallbacks(mServiceCallBack);
        int subId = getActiveSubId(sTestSlot);

        assertEquals(
                NetworkRegistrationInfo.REGISTRATION_STATE_HOME,
                getRegState(NetworkRegistrationInfo.DOMAIN_PS, subId));
        assertEquals(
                NetworkRegistrationInfo.REGISTRATION_STATE_HOME,
                getRegState(NetworkRegistrationInfo.DOMAIN_CS, subId));

        assertTrue(sTelephonyManager.isImsRegistered(subId));

        // Register call state change callback
        mCallState = TelephonyManager.CALL_STATE_IDLE;
        sCallStateChangeCallbackHandler.post(
                () -> {
                    sCallStateCallback = new CallStateListener();
                    ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                            sTelephonyManager,
                            (tm) ->
                                    tm.registerTelephonyCallback(
                                            mCallStateChangeExecutor, sCallStateCallback));
                });

        placeOutgoingCall(TEST_DIAL_NUMBER);
        TimeUnit.SECONDS.sleep(1);

        // Verify call state
        synchronized (mCallStateChangeLock) {
            if (mCallState == TelephonyManager.CALL_STATE_IDLE) {
                Log.d(TAG, "Wait for call state change from IDLE");
                mCallStateChangeLock.wait(WAIT_UPDATE_TIMEOUT_MS);
            }
            assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DIALING, WAIT_FOR_CALL_STATE));
            waitForCallSession();
            TestImsCallSessionImpl callSession =
                    sServiceConnector.getCarrierService().getMmTelFeature().getImsCallsession();
            Log.d(TAG, "Terminate call from remote");
            callSession.terminateIncomingCall();
        }
        TimeUnit.SECONDS.sleep(1);
        assertFalse(sTelecomManager.isInCall());

        // Unregister call state change callback
        sTelephonyManager.unregisterTelephonyCallback(sCallStateCallback);
        sCallStateCallback = null;
        waitForUnboundService();
    }

    @Test
    public void testWifiCallEndOnPeerDevice() throws Exception {
        sMockModemManager.changeNetworkService(
                sTestSlot, MOCK_SIM_PROFILE_ID_TWN_CHT, true, Domain.PS | Domain.CS);
        TimeUnit.SECONDS.sleep(1);

        bindImsService(ImsRegistrationImplBase.REGISTRATION_TECH_IWLAN);
        mServiceCallBack = new ServiceCallBack();
        InCallServiceStateValidator.setCallbacks(mServiceCallBack);

        int subId = getActiveSubId(sTestSlot);

        assertEquals(
                NetworkRegistrationInfo.REGISTRATION_STATE_HOME,
                getRegState(NetworkRegistrationInfo.DOMAIN_PS, subId));

        assertEquals(
                NetworkRegistrationInfo.REGISTRATION_STATE_HOME,
                getRegState(NetworkRegistrationInfo.DOMAIN_CS, subId));

        // Register call state change callback
        mCallState = TelephonyManager.CALL_STATE_IDLE;
        sCallStateChangeCallbackHandler.post(
                () -> {
                    sCallStateCallback = new CallStateListener();
                    ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                            sTelephonyManager,
                            (tm) ->
                                    tm.registerTelephonyCallback(
                                            mCallStateChangeExecutor, sCallStateCallback));
                });

        placeOutgoingCall(TEST_DIAL_NUMBER);
        TimeUnit.SECONDS.sleep(1);

        // Verify call state
        synchronized (mCallStateChangeLock) {
            if (mCallState == TelephonyManager.CALL_STATE_IDLE) {
                Log.d(TAG, "Wait for call state change from IDLE");
                mCallStateChangeLock.wait(WAIT_UPDATE_TIMEOUT_MS);
            }
            waitForCallSession();
            TestImsCallSessionImpl callSession =
                    sServiceConnector.getCarrierService().getMmTelFeature().getImsCallsession();
            Log.d(TAG, "Terminate call from remote");
            assertTrue(isPsDialing());
            callSession.terminate(ImsReasonInfo.CODE_USER_TERMINATED_BY_REMOTE);
        }

        TimeUnit.SECONDS.sleep(1);
        assertFalse(sTelecomManager.isInCall());

        // Unregister call state change callback
        sTelephonyManager.unregisterTelephonyCallback(sCallStateCallback);
        sCallStateCallback = null;
        waitForUnboundService();
    }

    @Test
    public void testCsCallEndOnPeerDevice() throws Exception {
        sMockModemManager.changeNetworkService(
                sTestSlot, MOCK_SIM_PROFILE_ID_TWN_CHT, true, Domain.CS);
        TimeUnit.SECONDS.sleep(1);

        int subId = getActiveSubId(sTestSlot);

        assertEquals(
                NetworkRegistrationInfo.REGISTRATION_STATE_HOME, getRegState(Domain.CS, subId));
        assertEquals(
                NetworkRegistrationInfo.REGISTRATION_STATE_NOT_REGISTERED_SEARCHING,
                getRegState(Domain.PS, subId));

        // Register call state change callback
        mCallState = TelephonyManager.CALL_STATE_IDLE;
        sCallStateChangeCallbackHandler.post(
                () -> {
                    sCallStateCallback = new CallStateListener();
                    ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                            sTelephonyManager,
                            (tm) ->
                                    tm.registerTelephonyCallback(
                                            mCallStateChangeExecutor, sCallStateCallback));
                });

        placeOutgoingCall(TEST_DIAL_NUMBER);
        TimeUnit.SECONDS.sleep(1);

        // Verify call state
        synchronized (mCallStateChangeLock) {
            if (mCallState == TelephonyManager.CALL_STATE_IDLE) {
                Log.d(TAG, "Wait for the CS call state change to offhook");
                mCallStateChangeLock.wait(WAIT_UPDATE_TIMEOUT_MS);
            }
        }

        assertTrue(isCsDialing());
        assertFalse(sTelephonyManager.isImsRegistered());

        // Hang up the call
        Log.d(TAG, "Hangup call");
        sMockModemManager.clearAllCalls(sTestSlot, DisconnectCause.BUSY);
        assertFalse(isCsDialing());
        TimeUnit.SECONDS.sleep(1);

        // Unregister call state change callback
        sTelephonyManager.unregisterTelephonyCallback(sCallStateCallback);
        sCallStateCallback = null;
    }

    @Ignore("For internal test purpose only")
    @Test
    public void testMultipleVoLteCalls() throws Exception {
        sMockModemManager.changeNetworkService(
                sTestSlot, MOCK_SIM_PROFILE_ID_TWN_CHT, true, Domain.PS);
        bindImsService();
        mServiceCallBack = new ServiceCallBack();
        InCallServiceStateValidator.setCallbacks(mServiceCallBack);
        TimeUnit.SECONDS.sleep(1);

        int subId = getActiveSubId(sTestSlot);

        assertTrue(sTelephonyManager.isImsRegistered(subId));

        assertEquals(
                NetworkRegistrationInfo.REGISTRATION_STATE_HOME, getRegState(Domain.PS, subId));
        assertEquals(
                NetworkRegistrationInfo.REGISTRATION_STATE_NOT_REGISTERED_SEARCHING,
                getRegState(Domain.CS, subId));

        // Register call state change callback
        mCallState = TelephonyManager.CALL_STATE_IDLE;
        sCallStateChangeCallbackHandler.post(
                () -> {
                    sCallStateCallback = new CallStateListener();
                    ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                            sTelephonyManager,
                            (tm) ->
                                    tm.registerTelephonyCallback(
                                            mCallStateChangeExecutor, sCallStateCallback));
                });

        // place first call
        placeOutgoingCall(TEST_DIAL_NUMBER);
        TimeUnit.SECONDS.sleep(1);
        // Verify call state
        synchronized (mCallStateChangeLock) {
            if (mCallState == TelephonyManager.CALL_STATE_IDLE) {
                Log.d(TAG, "Wait for call state change to offhook");
                mCallStateChangeLock.wait(WAIT_UPDATE_TIMEOUT_MS);
            }
            assertEquals(TelephonyManager.CALL_STATE_OFFHOOK, mCallState);
        }

        Call call1 = getCall(mCurrentCallId);

        assertTrue(isPsDialing());

        // place second call
        placeOutgoingCall(TEST_DIAL_NUMBER + 1);
        TimeUnit.SECONDS.sleep(1);
        // call1 on hold
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_HOLDING, WAIT_FOR_CALL_STATE));
        assertEquals("Call is not on Hold", Call.STATE_HOLDING, call1.getDetails().getState());

        Call call2 = getCall(mCurrentCallId);
        assertTrue(isPsDialing());

        // Swap the call
        ImsUtils.waitInCurrentState(WAIT_IN_CURRENT_STATE);
        call1.unhold();
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_HOLDING, WAIT_FOR_CALL_STATE));
        assertEquals("Call2 is not on Hold", Call.STATE_HOLDING, call2.getDetails().getState());

        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_ACTIVE, WAIT_FOR_CALL_STATE));
        assertEquals("Call1 is not Active", Call.STATE_ACTIVE, call1.getDetails().getState());

        // After successful call swap disconnect the call
        call1.disconnect();
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DISCONNECTING, WAIT_FOR_CALL_STATE));
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DISCONNECTED, WAIT_FOR_CALL_STATE));
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOVED, WAIT_FOR_CALL_STATE));

        // Wait till second call is in active state
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return true;
                    }

                    @Override
                    public Object actual() {
                        return call2.getDetails().getState() == Call.STATE_ACTIVE;
                    }
                },
                WAIT_FOR_CALL_STATE_ACTIVE,
                "Call in Active State");

        // Call 2 is active
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_ACTIVE, WAIT_FOR_CALL_STATE));

        // disconnect call 2
        call2.disconnect();
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DISCONNECTING, WAIT_FOR_CALL_STATE));
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DISCONNECTED, WAIT_FOR_CALL_STATE));

        // Unregister call state change callback
        sTelephonyManager.unregisterTelephonyCallback(sCallStateCallback);
        sCallStateCallback = null;
        waitForUnboundService();
    }

    @Ignore("For internal test purpose only")
    @Test
    public void testMultipleVoLteCallsWithIncomingCall() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        sMockModemManager.changeNetworkService(
                sTestSlot, MOCK_SIM_PROFILE_ID_TWN_CHT, true, Domain.PS);

        bindImsService();
        mServiceCallBack = new ServiceCallBack();
        InCallServiceStateValidator.setCallbacks(mServiceCallBack);
        TimeUnit.SECONDS.sleep(1);
        int subId = getActiveSubId(sTestSlot);

        assertEquals(
                NetworkRegistrationInfo.REGISTRATION_STATE_HOME, getRegState(Domain.PS, subId));
        assertEquals(
                NetworkRegistrationInfo.REGISTRATION_STATE_NOT_REGISTERED_SEARCHING,
                getRegState(Domain.CS, subId));

        // Register call state change callback
        mCallState = TelephonyManager.CALL_STATE_IDLE;
        sCallStateChangeCallbackHandler.post(
                () -> {
                    sCallStateCallback = new CallStateListener();
                    ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                            sTelephonyManager,
                            (tm) ->
                                    tm.registerTelephonyCallback(
                                            mCallStateChangeExecutor, sCallStateCallback));
                });

        Bundle extras = new Bundle();
        extras.putBoolean("android.telephony.ims.feature.extra.IS_USSD", false);
        extras.putBoolean("android.telephony.ims.feature.extra.IS_UNKNOWN_CALL", false);
        extras.putString("android:imsCallID", String.valueOf(++sCounter));
        extras.putLong("android:phone_id", 123456);

        // place incoming call
        sServiceConnector.getCarrierService().getMmTelFeature().onIncomingCallReceived(extras);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_ADDED, WAIT_FOR_CALL_STATE));

        Call mtCall = null;
        if (mCurrentCallId != null) {
            mtCall = getCall(mCurrentCallId);
            if (mtCall.getDetails().getState() == Call.STATE_RINGING) {
                mtCall.answer(0);
            }
        }

        // Place outgoing call
        placeOutgoingCall(TEST_DIAL_NUMBER);
        assertTrue(isPsDialing());
        Call moCall = getCall(mCurrentCallId);
        // Register call state change callback

        // Verify call state change
        synchronized (mCallStateChangeLock) {
            Log.d(TAG, "Wait for call state change");
            mCallStateChangeLock.wait(WAIT_UPDATE_TIMEOUT_MS);
            moCall.answer(0);
        }

        assertEquals("MO call is not Active ", Call.STATE_ACTIVE, moCall.getDetails().getState());
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_HOLDING, WAIT_FOR_CALL_STATE));
        assertEquals("MT call is not on Hold ", Call.STATE_HOLDING, mtCall.getDetails().getState());

        moCall.disconnect();
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DISCONNECTING, WAIT_FOR_CALL_STATE));
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOVED, WAIT_FOR_CALL_STATE));

        // mtCall should activate
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_ACTIVE, WAIT_FOR_CALL_STATE));
        assertEquals("MT call is not Active ", Call.STATE_ACTIVE, mtCall.getDetails().getState());

        mtCall.disconnect();
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DISCONNECTING, WAIT_FOR_CALL_STATE));

        // Unregister call state change callback
        sTelephonyManager.unregisterTelephonyCallback(sCallStateCallback);
        sCallStateCallback = null;
        waitForUnboundService();
    }

    @Ignore("For internal test purpose only")
    @Test
    public void testMultipleVoWifiCalls() throws Exception {
        sMockModemManager.changeNetworkService(
                sTestSlot, MOCK_SIM_PROFILE_ID_TWN_CHT, true, Domain.PS);
        bindImsService(ImsRegistrationImplBase.REGISTRATION_TECH_IWLAN);
        mServiceCallBack = new ServiceCallBack();
        InCallServiceStateValidator.setCallbacks(mServiceCallBack);
        TimeUnit.SECONDS.sleep(1);

        int subId = getActiveSubId(sTestSlot);

        assertTrue(sTelephonyManager.isImsRegistered(subId));

        assertEquals(
                NetworkRegistrationInfo.REGISTRATION_STATE_HOME, getRegState(Domain.PS, subId));
        assertEquals(
                NetworkRegistrationInfo.REGISTRATION_STATE_NOT_REGISTERED_SEARCHING,
                getRegState(Domain.CS, subId));

        // Register call state change callback
        mCallState = TelephonyManager.CALL_STATE_IDLE;
        sCallStateChangeCallbackHandler.post(
                () -> {
                    sCallStateCallback = new CallStateListener();
                    ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                            sTelephonyManager,
                            (tm) ->
                                    tm.registerTelephonyCallback(
                                            mCallStateChangeExecutor, sCallStateCallback));
                });

        // place first call
        placeOutgoingCall(TEST_DIAL_NUMBER);
        TimeUnit.SECONDS.sleep(1);
        // Verify call state
        synchronized (mCallStateChangeLock) {
            if (mCallState == TelephonyManager.CALL_STATE_IDLE) {
                Log.d(TAG, "Wait for call state change to offhook");
                mCallStateChangeLock.wait(WAIT_UPDATE_TIMEOUT_MS);
            }
            assertEquals(TelephonyManager.CALL_STATE_OFFHOOK, mCallState);
        }

        Call call1 = getCall(mCurrentCallId);

        assertTrue(isPsDialing());

        // place second call
        placeOutgoingCall(TEST_DIAL_NUMBER + 1);
        TimeUnit.SECONDS.sleep(1);
        // call1 on hold
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_HOLDING, WAIT_FOR_CALL_STATE));
        assertEquals("Call is not on Hold", Call.STATE_HOLDING, call1.getDetails().getState());

        Call call2 = getCall(mCurrentCallId);
        assertTrue(isPsDialing());

        // Swap the call
        ImsUtils.waitInCurrentState(WAIT_IN_CURRENT_STATE);
        call1.unhold();
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_HOLDING, WAIT_FOR_CALL_STATE));
        assertEquals("Call2 is not on Hold", Call.STATE_HOLDING, call2.getDetails().getState());

        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_ACTIVE, WAIT_FOR_CALL_STATE));
        assertEquals("Call1 is not Active", Call.STATE_ACTIVE, call1.getDetails().getState());

        // After successful call swap disconnect the call
        call1.disconnect();
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DISCONNECTING, WAIT_FOR_CALL_STATE));
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DISCONNECTED, WAIT_FOR_CALL_STATE));
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOVED, WAIT_FOR_CALL_STATE));

        // Wait till second call is in active state
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return true;
                    }

                    @Override
                    public Object actual() {
                        return call2.getDetails().getState() == Call.STATE_ACTIVE;
                    }
                },
                WAIT_FOR_CALL_STATE_ACTIVE,
                "Call in Active State");

        // Call 2 is active
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_ACTIVE, WAIT_FOR_CALL_STATE));

        // disconnect call 2
        call2.disconnect();
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DISCONNECTING, WAIT_FOR_CALL_STATE));
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DISCONNECTED, WAIT_FOR_CALL_STATE));

        // Unregister call state change callback
        sTelephonyManager.unregisterTelephonyCallback(sCallStateCallback);
        sCallStateCallback = null;
        waitForUnboundService();
    }

    @Test
    public void testWpsCallOverPs() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }
        assumeTrue("This test requires WPS support over IMS", isWpsOverImsSupported());
        sMockModemManager.changeNetworkService(
                sTestSlot, MOCK_SIM_PROFILE_ID_TWN_CHT, true, Domain.PS);
        TimeUnit.SECONDS.sleep(1);

        bindImsService();
        mServiceCallBack = new ServiceCallBack();
        InCallServiceStateValidator.setCallbacks(mServiceCallBack);

        int subId = getActiveSubId(sTestSlot);

        assertEquals(
                NetworkRegistrationInfo.REGISTRATION_STATE_HOME,
                getRegState(NetworkRegistrationInfo.DOMAIN_PS, subId));

        assertEquals(
                NetworkRegistrationInfo.REGISTRATION_STATE_NOT_REGISTERED_SEARCHING,
                getRegState(NetworkRegistrationInfo.DOMAIN_CS, subId));

        // Register call state change callback
        mCallState = TelephonyManager.CALL_STATE_IDLE;
        sCallStateChangeCallbackHandler.post(
                () -> {
                    sCallStateCallback = new CallStateListener();
                    ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                            sTelephonyManager,
                            (tm) ->
                                    tm.registerTelephonyCallback(
                                            mCallStateChangeExecutor, sCallStateCallback));
                });

        placeOutgoingCall(TEST_DIAL_WPS_NUMBER);
        TimeUnit.SECONDS.sleep(1);

        // Verify call state
        synchronized (mCallStateChangeLock) {
            if (mCallState == TelephonyManager.CALL_STATE_IDLE) {
                Log.d(TAG, "Wait for call state change to offhook");
                mCallStateChangeLock.wait(WAIT_UPDATE_TIMEOUT_MS);
            }
            assertEquals(TelephonyManager.CALL_STATE_OFFHOOK, mCallState);
        }
        assertTrue(sTelephonyManager.isImsRegistered());

        assertTrue(isPsDialing());

        // Hang up the call
        Log.d(TAG, "Hangup call");
        sTelecomManager.endCall();
        TimeUnit.SECONDS.sleep(1);

        // Unregister call state change callback
        sTelephonyManager.unregisterTelephonyCallback(sCallStateCallback);
        sCallStateCallback = null;
        waitForUnboundService();
    }

    @Test
    public void testWpsCallOverCs() throws Exception {
        sMockModemManager.changeNetworkService(
                sTestSlot, MOCK_SIM_PROFILE_ID_TWN_CHT, true, Domain.CS);
        TimeUnit.SECONDS.sleep(1);

        int subId = getActiveSubId(sTestSlot);

        assertEquals(
                NetworkRegistrationInfo.REGISTRATION_STATE_HOME, getRegState(Domain.CS, subId));
        assertEquals(
                NetworkRegistrationInfo.REGISTRATION_STATE_NOT_REGISTERED_SEARCHING,
                getRegState(Domain.PS, subId));

        // Register call state change callback
        mCallState = TelephonyManager.CALL_STATE_IDLE;
        sCallStateChangeCallbackHandler.post(
                () -> {
                    sCallStateCallback = new CallStateListener();
                    ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                            sTelephonyManager,
                            (tm) ->
                                    tm.registerTelephonyCallback(
                                            mCallStateChangeExecutor, sCallStateCallback));
                });

        placeOutgoingCall(TEST_DIAL_WPS_NUMBER);
        TimeUnit.SECONDS.sleep(1);

        // Verify call state
        synchronized (mCallStateChangeLock) {
            if (mCallState == TelephonyManager.CALL_STATE_IDLE) {
                Log.d(TAG, "Wait for the CS call state change to offhook");
                mCallStateChangeLock.wait(WAIT_UPDATE_TIMEOUT_MS);
            }
            assertEquals(TelephonyManager.CALL_STATE_OFFHOOK, mCallState);
        }

        assertFalse(sTelephonyManager.isImsRegistered());

        // call is in CS
        assertTrue(sTelecomManager.isInCall());
        assertTrue(isCsDialing());

        // Hang up the call
        Log.d(TAG, "Hangup call");
        sTelecomManager.endCall();
        TimeUnit.SECONDS.sleep(1);

        // Unregister call state change callback
        sTelephonyManager.unregisterTelephonyCallback(sCallStateCallback);
        sCallStateCallback = null;
    }

    @Test
    public void testWpsCallOverCsWithImsOverWifiRegistered() throws Exception {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        assumeFalse("This test does not required WPS support over IMS", isWpsOverImsSupported());

        sMockModemManager.changeNetworkService(
                sTestSlot, MOCK_SIM_PROFILE_ID_TWN_CHT, true, Domain.PS | Domain.CS);
        TimeUnit.SECONDS.sleep(1);

        bindImsService(ImsRegistrationImplBase.REGISTRATION_TECH_IWLAN);
        mServiceCallBack = new ServiceCallBack();
        InCallServiceStateValidator.setCallbacks(mServiceCallBack);

        int subId = getActiveSubId(sTestSlot);

        assertEquals(
                NetworkRegistrationInfo.REGISTRATION_STATE_HOME,
                getRegState(NetworkRegistrationInfo.DOMAIN_PS, subId));

        assertEquals(
                NetworkRegistrationInfo.REGISTRATION_STATE_HOME,
                getRegState(NetworkRegistrationInfo.DOMAIN_CS, subId));

        assertTrue(sTelephonyManager.isImsRegistered());

        // Register call state change callback
        mCallState = TelephonyManager.CALL_STATE_IDLE;
        sCallStateChangeCallbackHandler.post(
                () -> {
                    sCallStateCallback = new CallStateListener();
                    ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                            sTelephonyManager,
                            (tm) ->
                                    tm.registerTelephonyCallback(
                                            mCallStateChangeExecutor, sCallStateCallback));
                });

        placeOutgoingCall(TEST_DIAL_WPS_NUMBER);
        TimeUnit.SECONDS.sleep(1);

        // Verify call state
        synchronized (mCallStateChangeLock) {
            if (mCallState == TelephonyManager.CALL_STATE_IDLE) {
                Log.d(TAG, "Wait for call state change to offhook");
                mCallStateChangeLock.wait(WAIT_UPDATE_TIMEOUT_MS);
            }
            assertEquals(TelephonyManager.CALL_STATE_OFFHOOK, mCallState);
        }

        assertTrue(isCsDialing());

        // Hang up the call
        Log.d(TAG, "Hangup call");
        sTelecomManager.endCall();
        TimeUnit.SECONDS.sleep(1);

        // Unregister call state change callback
        sTelephonyManager.unregisterTelephonyCallback(sCallStateCallback);
        sCallStateCallback = null;
        waitForUnboundService();
    }

    private boolean isWpsOverImsSupported() {
        CarrierConfigManager configManager =
                (CarrierConfigManager)
                        getContext().getSystemService(Context.CARRIER_CONFIG_SERVICE);
        PersistableBundle carrierConfig = configManager.getConfigForSubId(sTestSub);
        return carrierConfig.getBoolean(CarrierConfigManager.KEY_SUPPORT_WPS_OVER_IMS_BOOL);
    }

    private void waitForCallSession() {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return true;
                    }

                    @Override
                    public Object actual() {
                        return sServiceConnector
                                        .getCarrierService()
                                        .getMmTelFeature()
                                        .getImsCallsession()
                                != null;
                    }
                },
                WAIT_FOR_CONDITION,
                "CallSession Created");
    }
}
