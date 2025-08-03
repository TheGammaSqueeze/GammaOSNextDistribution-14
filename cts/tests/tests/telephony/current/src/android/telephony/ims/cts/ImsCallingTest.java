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

package android.telephony.ims.cts;

import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.fail;

import android.annotation.NonNull;
import android.content.Context;
import android.database.ContentObserver;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PersistableBundle;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.telecom.Call;
import android.telecom.PhoneAccount;
import android.telecom.TelecomManager;
import android.telecom.VideoProfile;
import android.telephony.AccessNetworkConstants;
import android.telephony.CallState;
import android.telephony.CarrierConfigManager;
import android.telephony.PreciseCallState;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;
import android.telephony.cts.InCallServiceStateValidator;
import android.telephony.emergency.EmergencyNumber;
import android.telephony.ims.ImsCallProfile;
import android.telephony.ims.ImsCallSessionListener;
import android.telephony.ims.ImsManager;
import android.telephony.ims.ImsMmTelManager;
import android.telephony.ims.ImsStreamMediaProfile;
import android.telephony.ims.MediaQualityStatus;
import android.telephony.ims.feature.MmTelFeature;
import android.telephony.ims.stub.ImsRegistrationImplBase;
import android.text.TextUtils;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

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
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
/** CTS tests for ImsCall . */
@RunWith(AndroidJUnit4.class)
public class ImsCallingTest extends ImsCallingBase {

    protected static final String LOG_TAG = "CtsImsCallingTest";

    private Call mCall1 = null;
    private Call mCall2 = null;
    private Call mCall3 = null;
    private Call mConferenceCall = null;
    private TestImsCallSessionImpl mCallSession1 = null;
    private TestImsCallSessionImpl mCallSession2 = null;
    private TestImsCallSessionImpl mCallSession3 = null;
    private TestImsCallSessionImpl mConfCallSession = null;

    // the timeout to wait result in milliseconds
    private static final int WAIT_UPDATE_TIMEOUT_MS = 3000;
    private static final long WAIT_FOR_STATE_CHANGE_TIMEOUT_MS = 10000;

    private static TelephonyManager sTelephonyManager;
    private static Handler sHandler;

    static {
        initializeLatches();
    }

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    @BeforeClass
    public static void beforeAllTests() throws Exception {
        if (!ImsUtils.shouldTestImsCall()) {
            return;
        }

        sTelephonyManager =
                (TelephonyManager) getContext().getSystemService(Context.TELEPHONY_SERVICE);

        sTestSub = ImsUtils.getPreferredActiveSubId();
        sTestSlot = SubscriptionManager.getSlotIndex(sTestSub);
        if (!isSimReady()) {
            return;
        }

        if (Looper.getMainLooper() == null) {
            Looper.prepareMainLooper();
        }
        sHandler = new Handler(Looper.getMainLooper());

        beforeAllTestsBase();
    }

    private static boolean isSimReady() {
        if (sTelephonyManager.getSimState(sTestSlot) != TelephonyManager.SIM_STATE_READY) {
            Log.d(LOG_TAG, "sim is not present");
            return false;
        }
        return true;
    }

    @AfterClass
    public static void afterAllTests() throws Exception {
        if (!ImsUtils.shouldTestImsCall()) {
            return;
        }

        if (!isSimReady()) {
            return;
        }

        afterAllTestsBase();
    }

    @Before
    public void beforeTest() throws Exception {
        if (!ImsUtils.shouldTestImsCall()) {
            return;
        }

        if (!isSimReady()) {
            fail("This test requires that there is a SIM in the device!");
        }

        // Correctness check: ensure that the subscription hasn't changed between tests.
        int subId = SubscriptionManager.getSubscriptionId(sTestSlot);
        if (subId != sTestSub) {
            fail("The found subId " + subId + " does not match the test sub id " + sTestSub);
        }
    }

    @After
    public void afterTest() throws Exception {
        if (!ImsUtils.shouldTestImsCall()) {
            return;
        }

        if (!isSimReady()) {
            return;
        }

        resetCallSessionObjects();

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

        tearDownEmergencyCalling();
    }

    @Test
    public void testOutGoingCall() throws Exception {
        if (!ImsUtils.shouldTestImsCall()) {
            return;
        }

        bindImsService();
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
        waitForCallSessionToNotBe(null);
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DIALING, WAIT_FOR_CALL_STATE));

        TestImsCallSessionImpl callSession = sServiceConnector.getCarrierService().getMmTelFeature()
                .getImsCallsession();

        isCallActive(call, callSession);
        call.disconnect();

        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DISCONNECTING, WAIT_FOR_CALL_STATE));
        isCallDisconnected(call, callSession);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOVED, WAIT_FOR_CALL_STATE));
        waitForUnboundService();
    }

    @Test
    public void testOutGoingCallStartFailed() throws Exception {
        if (!ImsUtils.shouldTestImsCall()) {
            return;
        }

        bindImsService();
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

        waitForCallSessionToNotBe(null);
        TestImsCallSessionImpl callSession =
                sServiceConnector.getCarrierService().getMmTelFeature().getImsCallsession();
        assertNotNull("Unable to get callSession, its null", callSession);
        callSession.addTestType(TestImsCallSessionImpl.TEST_TYPE_MO_FAILED);

        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DIALING, WAIT_FOR_CALL_STATE));
        isCallDisconnected(call, callSession);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOVED, WAIT_FOR_CALL_STATE));
        waitForUnboundService();
    }

    @Test
    public void testIncomingCall() throws Exception {
        if (!ImsUtils.shouldTestImsCall()) {
            return;
        }
        bindImsService();
        mServiceCallBack = new ServiceCallBack();
        InCallServiceStateValidator.setCallbacks(mServiceCallBack);

        Bundle extras = new Bundle();
        sServiceConnector.getCarrierService().getMmTelFeature().onIncomingCallReceived(extras);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_ADDED, WAIT_FOR_CALL_STATE));

        Call call = getCall(mCurrentCallId);
        if (call.getDetails().getState() == Call.STATE_RINGING) {
            call.answer(0);
        }

        waitForCallSessionToNotBe(null);
        TestImsCallSessionImpl callSession =
                sServiceConnector.getCarrierService().getMmTelFeature().getImsCallsession();

        isCallActive(call, callSession);
        callSession.terminateIncomingCall();

        isCallDisconnected(call, callSession);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOVED, WAIT_FOR_CALL_STATE));
        waitForUnboundService();
    }

    @Test
    public void testIncomingCallReturnListener() throws Exception {
        if (!ImsUtils.shouldTestImsCall()) {
            return;
        }
        bindImsService();
        mServiceCallBack = new ServiceCallBack();
        InCallServiceStateValidator.setCallbacks(mServiceCallBack);

        Bundle extras = new Bundle();
        ImsCallSessionListener isl = sServiceConnector.getCarrierService().getMmTelFeature()
                .onIncomingCallReceivedReturnListener(extras);
        assertTrue("failed to get ImsCallSessionListener..", Objects.nonNull(isl));
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_ADDED, WAIT_FOR_CALL_STATE));

        Call call = getCall(mCurrentCallId);
        if (call.getDetails().getState() == Call.STATE_RINGING) {
            call.answer(0);
        }

        waitForCallSessionToNotBe(null);
        TestImsCallSessionImpl callSession =
                sServiceConnector.getCarrierService().getMmTelFeature().getImsCallsession();

        isCallActive(call, callSession);
        callSession.terminateIncomingCall();

        isCallDisconnected(call, callSession);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOVED, WAIT_FOR_CALL_STATE));
        waitForUnboundService();
    }

    @Test
    public void testIncomingCallReject() throws Exception {
        if (!ImsUtils.shouldTestImsCall()) {
            return;
        }
        bindImsService();
        mServiceCallBack = new ServiceCallBack();
        InCallServiceStateValidator.setCallbacks(mServiceCallBack);

        Bundle extras = new Bundle();
        sServiceConnector.getCarrierService().getMmTelFeature().onIncomingCallReceived(extras);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_ADDED, WAIT_FOR_CALL_STATE));

        Call call = getCall(mCurrentCallId);
        if (call.getDetails().getState() == Call.STATE_RINGING) {
            call.reject(504);
        }

        waitForCallSessionToNotBe(null);
        TestImsCallSessionImpl callSession =
                sServiceConnector.getCarrierService().getMmTelFeature().getImsCallsession();

        isCallDisconnected(call, callSession);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOVED, WAIT_FOR_CALL_STATE));
        waitForUnboundService();
    }

    @Test
    public void testOutGoingCallForExecutor() throws Exception {
        if (!ImsUtils.shouldTestImsCall()) {
            return;
        }

        sServiceConnector.setExecutorTestType(true);
        bindImsService();

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
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DIALING, WAIT_FOR_CALL_STATE));

        waitForCallSessionToNotBe(null);
        TestImsCallSessionImpl callSession =
                sServiceConnector.getCarrierService().getMmTelFeature().getImsCallsession();

        isCallActive(call, callSession);
        call.disconnect();

        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DISCONNECTING, WAIT_FOR_CALL_STATE));
        isCallDisconnected(call, callSession);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOVED, WAIT_FOR_CALL_STATE));
        waitForUnboundService();
    }

    @Test
    public void testOutGoingCallHoldResume() throws Exception {
        if (!ImsUtils.shouldTestImsCall()) {
            return;
        }

        bindImsService();
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
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DIALING, WAIT_FOR_CALL_STATE));

        waitForCallSessionToNotBe(null);
        TestImsCallSessionImpl callSession =
                sServiceConnector.getCarrierService().getMmTelFeature().getImsCallsession();

        isCallActive(call, callSession);

        // Put on hold
        call.hold();
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_HOLDING, WAIT_FOR_CALL_STATE));

        ImsUtils.waitInCurrentState(WAIT_IN_CURRENT_STATE);
        // Put on resume
        call.unhold();
        isCallActive(call, callSession);
        call.disconnect();

        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DISCONNECTING, WAIT_FOR_CALL_STATE));
        isCallDisconnected(call, callSession);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOVED, WAIT_FOR_CALL_STATE));
        waitForUnboundService();
    }

    @Test
    public void testOutGoingCallHoldFailure() throws Exception {
        if (!ImsUtils.shouldTestImsCall()) {
            return;
        }

        bindImsService();
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
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DIALING, WAIT_FOR_CALL_STATE));

        waitForCallSessionToNotBe(null);
        TestImsCallSessionImpl callSession =
                sServiceConnector.getCarrierService().getMmTelFeature().getImsCallsession();

        isCallActive(call, callSession);
        callSession.addTestType(TestImsCallSessionImpl.TEST_TYPE_HOLD_FAILED);

        ImsUtils.waitInCurrentState(WAIT_IN_CURRENT_STATE);
        call.hold();
        assertTrue("call is not in Active State", (call.getDetails().getState()
                == call.STATE_ACTIVE));

        call.disconnect();

        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DISCONNECTING, WAIT_FOR_CALL_STATE));
        isCallDisconnected(call, callSession);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOVED, WAIT_FOR_CALL_STATE));
        waitForUnboundService();
    }

    @Test
    public void testOutGoingCallResumeFailure() throws Exception {
        if (!ImsUtils.shouldTestImsCall()) {
            return;
        }

        bindImsService();
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
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DIALING, WAIT_FOR_CALL_STATE));

        waitForCallSessionToNotBe(null);
        TestImsCallSessionImpl callSession =
                sServiceConnector.getCarrierService().getMmTelFeature().getImsCallsession();

        isCallActive(call, callSession);

        // Put on hold
        call.hold();
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_HOLDING, WAIT_FOR_CALL_STATE));

        callSession.addTestType(TestImsCallSessionImpl.TEST_TYPE_RESUME_FAILED);
        ImsUtils.waitInCurrentState(WAIT_IN_CURRENT_STATE);
        call.unhold();
        assertTrue("Call is not in Hold State", (call.getDetails().getState()
                == call.STATE_HOLDING));

        call.disconnect();

        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DISCONNECTING, WAIT_FOR_CALL_STATE));
        isCallDisconnected(call, callSession);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOVED, WAIT_FOR_CALL_STATE));
        waitForUnboundService();
    }

    @Test
    public void testOutGoingCallReceivedHoldResume() throws Exception {
        if (!ImsUtils.shouldTestImsCall()) {
            return;
        }

        bindImsService();
        mServiceCallBack = new ServiceCallBack();
        InCallServiceStateValidator.setCallbacks(mServiceCallBack);

        TelecomManager telecomManager = (TelecomManager) InstrumentationRegistry
                .getInstrumentation().getContext().getSystemService(Context.TELECOM_SERVICE);

        final Uri imsUri = Uri.fromParts(PhoneAccount.SCHEME_TEL, String.valueOf(++sCounter), null);
        Bundle extras = new Bundle();

        telecomManager.placeCall(imsUri, extras);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_ADDED, WAIT_FOR_CALL_STATE));

        Call call = getCall(mCurrentCallId);
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DIALING, WAIT_FOR_CALL_STATE));

        waitForCallSessionToNotBe(null);
        TestImsCallSessionImpl callSession =
                sServiceConnector.getCarrierService().getMmTelFeature().getImsCallsession();
        isCallActive(call, callSession);

        // received hold and checking it is still active and received the connection event
        // EVENT_CALL_REMOTELY_HELD
        callSession.sendHoldReceived();
        assertTrue("Call is not in Active State", (call.getDetails().getState()
                == Call.STATE_ACTIVE));
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOTELY_HELD, WAIT_FOR_CALL_STATE));

        // received resume and checking it is still active and received the connection event
        // EVENT_CALL_REMOTELY_UNHELD
        callSession.sendResumeReceived();
        assertTrue("Call is not in Active State", (call.getDetails().getState()
                == Call.STATE_ACTIVE));
        assertTrue(
                callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOTELY_UNHELD, WAIT_FOR_CALL_STATE));

        call.disconnect();

        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DISCONNECTING, WAIT_FOR_CALL_STATE));
        isCallDisconnected(call, callSession);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOVED, WAIT_FOR_CALL_STATE));
        waitForUnboundService();
    }

    @Test
    public void testOutGoingIncomingMultiCall() throws Exception {
        if (!ImsUtils.shouldTestImsCall()) {
            return;
        }

        bindImsService();
        mServiceCallBack = new ServiceCallBack();
        InCallServiceStateValidator.setCallbacks(mServiceCallBack);

        TelecomManager telecomManager = (TelecomManager) InstrumentationRegistry
                .getInstrumentation().getContext().getSystemService(Context.TELECOM_SERVICE);

        final Uri imsUri = Uri.fromParts(PhoneAccount.SCHEME_TEL, String.valueOf(++sCounter), null);
        Bundle extras = new Bundle();

        // Place outgoing call
        telecomManager.placeCall(imsUri, extras);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_ADDED, WAIT_FOR_CALL_STATE));

        Call moCall = getCall(mCurrentCallId);
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DIALING, WAIT_FOR_CALL_STATE));

        waitForCallSessionToNotBe(null);
        TestImsCallSessionImpl moCallSession =
                sServiceConnector.getCarrierService().getMmTelFeature().getImsCallsession();
        isCallActive(moCall, moCallSession);
        assertTrue("Call is not in Active State", (moCall.getDetails().getState()
                == Call.STATE_ACTIVE));

        extras.putBoolean("android.telephony.ims.feature.extra.IS_USSD", false);
        extras.putBoolean("android.telephony.ims.feature.extra.IS_UNKNOWN_CALL", false);
        extras.putString("android:imsCallID", String.valueOf(++sCounter));
        extras.putLong("android:phone_id", 123456);
        sServiceConnector.getCarrierService().getMmTelFeature().onIncomingCallReceived(extras);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_ADDED, WAIT_FOR_CALL_STATE));

        Call mtCall = null;
        if (mCurrentCallId != null) {
            mtCall = getCall(mCurrentCallId);
            if (mtCall.getDetails().getState() == Call.STATE_RINGING) {
                mtCall.answer(0);
            }
        }

        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_HOLDING, WAIT_FOR_CALL_STATE));
        waitForCallSessionToNotBe(null);
        TestImsCallSessionImpl mtCallSession = sServiceConnector.getCarrierService()
                .getMmTelFeature().getImsCallsession();
        isCallActive(mtCall, mtCallSession);
        assertTrue("Call is not in Active State", (mtCall.getDetails().getState()
                == Call.STATE_ACTIVE));

        mtCall.disconnect();
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DISCONNECTING, WAIT_FOR_CALL_STATE));
        isCallDisconnected(mtCall, mtCallSession);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOVED, WAIT_FOR_CALL_STATE));

        isCallActive(moCall, moCallSession);
        assertTrue("Call is not in Active State", (moCall.getDetails().getState()
                == Call.STATE_ACTIVE));

        moCall.disconnect();
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DISCONNECTING, WAIT_FOR_CALL_STATE));
        isCallDisconnected(moCall, moCallSession);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOVED, WAIT_FOR_CALL_STATE));

        waitForUnboundService();
    }

    @Test
    public void testOutGoingIncomingMultiCallAcceptTerminate() throws Exception {
        if (!ImsUtils.shouldTestImsCall()) {
            return;
        }

        bindImsService();
        mServiceCallBack = new ServiceCallBack();
        InCallServiceStateValidator.setCallbacks(mServiceCallBack);

        TelecomManager telecomManager = (TelecomManager) InstrumentationRegistry
                .getInstrumentation().getContext().getSystemService(Context.TELECOM_SERVICE);

        final Uri imsUri = Uri.fromParts(PhoneAccount.SCHEME_TEL, String.valueOf(++sCounter), null);
        Bundle extras = new Bundle();

        // Place outgoing call
        telecomManager.placeCall(imsUri, extras);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_ADDED, WAIT_FOR_CALL_STATE));

        Call moCall = getCall(mCurrentCallId);
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DIALING, WAIT_FOR_CALL_STATE));

        waitForCallSessionToNotBe(null);
        TestImsCallSessionImpl moCallSession =
                sServiceConnector.getCarrierService().getMmTelFeature().getImsCallsession();
        isCallActive(moCall, moCallSession);
        assertTrue("Call is not in Active State", (moCall.getDetails().getState()
                == Call.STATE_ACTIVE));

        extras.putBoolean("android.telephony.ims.feature.extra.IS_USSD", false);
        extras.putBoolean("android.telephony.ims.feature.extra.IS_UNKNOWN_CALL", false);
        extras.putString("android:imsCallID", String.valueOf(++sCounter));
        extras.putLong("android:phone_id", 123456);
        sServiceConnector.getCarrierService().getMmTelFeature().onIncomingCallReceived(extras);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_ADDED, WAIT_FOR_CALL_STATE));
        waitForCallSessionToNotBe(null);
        TestImsCallSessionImpl mtCallSession =
                sServiceConnector.getCarrierService().getMmTelFeature().getImsCallsession();
        // do not generate an auto hold response here, need to simulate a timing issue.
        moCallSession.addTestType(TestImsCallSessionImpl.TEST_TYPE_HOLD_NO_RESPONSE);

        Call mtCall = null;
        if (mCurrentCallId != null) {
            mtCall = getCall(mCurrentCallId);
            if (mtCall.getDetails().getState() == Call.STATE_RINGING) {
                mtCall.answer(0);
            }
        }

        ImsUtils.waitInCurrentState(WAIT_IN_CURRENT_STATE);
        // simulate user hanging up the MT call at the same time as accept.
        mtCallSession.terminateIncomingCall();
        isCallDisconnected(mtCall, mtCallSession);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOVED, WAIT_FOR_CALL_STATE));

        // then send hold response, which should be reversed, since MT call was disconnected.
        moCallSession.sendHoldResponse();

        // MO call should move back to active.
        isCallActive(moCall, moCallSession);
        assertTrue("Call is not in Active State", (moCall.getDetails().getState()
                == Call.STATE_ACTIVE));

        moCall.disconnect();
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DISCONNECTING, WAIT_FOR_CALL_STATE));
        isCallDisconnected(moCall, moCallSession);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOVED, WAIT_FOR_CALL_STATE));

        waitForUnboundService();
    }

    @Test
    public void testOutGoingIncomingMultiCallHoldFailedTerminateByRemote() throws Exception {
        if (!ImsUtils.shouldTestImsCall()) {
            return;
        }

        bindImsService();
        mServiceCallBack = new ServiceCallBack();
        InCallServiceStateValidator.setCallbacks(mServiceCallBack);

        TelecomManager telecomManager = (TelecomManager) InstrumentationRegistry
                .getInstrumentation().getContext().getSystemService(Context.TELECOM_SERVICE);

        final Uri imsUri = Uri.fromParts(PhoneAccount.SCHEME_TEL, String.valueOf(++sCounter), null);
        Bundle extras = new Bundle();

        telecomManager.placeCall(imsUri, extras);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_ADDED, WAIT_FOR_CALL_STATE));

        Call moCall = getCall(mCurrentCallId);
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DIALING, WAIT_FOR_CALL_STATE));

        waitForCallSessionToNotBe(null);
        TestImsCallSessionImpl moCallSession =
                sServiceConnector.getCarrierService().getMmTelFeature().getImsCallsession();
        isCallActive(moCall, moCallSession);
        assertTrue("Call is not in Active State", (moCall.getDetails().getState()
                == Call.STATE_ACTIVE));

        sServiceConnector.getCarrierService().getMmTelFeature().onIncomingCallReceived(extras);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_ADDED, WAIT_FOR_CALL_STATE));
        waitForCallSessionToNotBe(null);
        TestImsCallSessionImpl mtCallSession =
                sServiceConnector.getCarrierService().getMmTelFeature().getImsCallsession();
        moCallSession.addTestType(TestImsCallSessionImpl.TEST_TYPE_HOLD_NO_RESPONSE);

        Call mtCall = null;
        if (mCurrentCallId != null) {
            mtCall = getCall(mCurrentCallId);
            if (mtCall.getDetails().getState() == Call.STATE_RINGING) {
                mtCall.answer(0);
            }
        }

        ImsUtils.waitInCurrentState(WAIT_IN_CURRENT_STATE);
        // received holdFailed because the other party of the outgoing call terminated the call
        waitCallRenegotiating(moCallSession);
        moCallSession.sendHoldFailRemoteTerminated();
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOVED, WAIT_FOR_CALL_STATE));
        isCallDisconnected(moCall, moCallSession);

        // incoming call accept again
        mtCall.answer(0);
        isCallActive(mtCall, mtCallSession);
        assertTrue("Call is not in Active State", (mtCall.getDetails().getState()
                == Call.STATE_ACTIVE));

        mtCall.disconnect();
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DISCONNECTING, WAIT_FOR_CALL_STATE));
        isCallDisconnected(mtCall, mtCallSession);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOVED, WAIT_FOR_CALL_STATE));
        waitForUnboundService();
    }

    @Test
    public void testOutGoingCallSwap() throws Exception {
        if (!ImsUtils.shouldTestImsCall()) {
            return;
        }

        bindImsService();
        mServiceCallBack = new ServiceCallBack();
        InCallServiceStateValidator.setCallbacks(mServiceCallBack);
        addOutgoingCalls();

        // Swap the call
        ImsUtils.waitInCurrentState(WAIT_IN_CURRENT_STATE);
        mCall1.unhold();
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_HOLDING, WAIT_FOR_CALL_STATE));
        assertTrue("Call is not in Hold State", (mCall2.getDetails().getState()
                == Call.STATE_HOLDING));
        isCallActive(mCall1, mCallSession1);

        // After successful call swap disconnect the call
        mCall1.disconnect();
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DISCONNECTING, WAIT_FOR_CALL_STATE));
        isCallDisconnected(mCall1, mCallSession1);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOVED, WAIT_FOR_CALL_STATE));

        //Wait till second call is in active state
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return true;
                    }

                    @Override
                    public Object actual() {
                        return (mCall2.getDetails().getState() == Call.STATE_ACTIVE)
                                ? true : false;
                    }
                }, WAIT_FOR_CALL_STATE_ACTIVE, "Call in Active State");

        isCallActive(mCall2, mCallSession2);
        mCall2.disconnect();
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DISCONNECTING, WAIT_FOR_CALL_STATE));
        isCallDisconnected(mCall2, mCallSession2);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOVED, WAIT_FOR_CALL_STATE));
        waitForUnboundService();
    }

    @Test
    public void testOutGoingCallSwapFail() throws Exception {
        if (!ImsUtils.shouldTestImsCall()) {
            return;
        }

        bindImsService();
        mServiceCallBack = new ServiceCallBack();
        InCallServiceStateValidator.setCallbacks(mServiceCallBack);
        addOutgoingCalls();

        mCallSession1.addTestType(TestImsCallSessionImpl.TEST_TYPE_RESUME_FAILED);
        // Swap the call
        ImsUtils.waitInCurrentState(WAIT_IN_CURRENT_STATE);
        mCall1.unhold();
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_HOLDING, WAIT_FOR_CALL_STATE));
        assertTrue("Call is not in Hold State", (mCall1.getDetails().getState()
                == Call.STATE_HOLDING));

        // Wait till second call is in active state
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return true;
                    }

                    @Override
                    public Object actual() {
                        return (mCall2.getDetails().getState() == Call.STATE_ACTIVE)
                                ? true : false;
                    }
                }, WAIT_FOR_CALL_STATE_ACTIVE, "Call in Active State");

        isCallActive(mCall2, mCallSession2);
        mCallSession1.removeTestType(TestImsCallSessionImpl.TEST_TYPE_RESUME_FAILED);
        mCall2.disconnect();
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DISCONNECTING, WAIT_FOR_CALL_STATE));
        isCallDisconnected(mCall2, mCallSession2);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOVED, WAIT_FOR_CALL_STATE));

        // Wait till second call is in active state
        isCallActive(mCall1, mCallSession1);
        mCall1.disconnect();
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DISCONNECTING, WAIT_FOR_CALL_STATE));
        isCallDisconnected(mCall1, mCallSession1);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOVED, WAIT_FOR_CALL_STATE));
        waitForUnboundService();
    }

    @Test
    public void testConferenceCall() throws Exception {
        if (!ImsUtils.shouldTestImsCall()) {
            return;
        }
        Log.i(LOG_TAG, "testConference ");
        bindImsService();
        mServiceCallBack = new ServiceCallBack();
        InCallServiceStateValidator.setCallbacks(mServiceCallBack);

        makeConferenceCall();

        //Disconnect the conference call.
        mConferenceCall.disconnect();

        //Verify conference participant connections are disconnected.
        assertParticiapantAddedToConference(0);
        isCallDisconnected(mConferenceCall, mConfCallSession);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOVED, WAIT_FOR_CALL_STATE));
        waitForUnboundService();
    }

    @Test
    public void testConferenceCallFailure() throws Exception {
        if (!ImsUtils.shouldTestImsCall()) {
            return;
        }
        Log.i(LOG_TAG, "testConferenceCallFailure ");
        bindImsService();
        mServiceCallBack = new ServiceCallBack();
        InCallServiceStateValidator.setCallbacks(mServiceCallBack);
        addOutgoingCalls();
        mCallSession2.addTestType(TestImsCallSessionImpl.TEST_TYPE_CONFERENCE_FAILED);
        addConferenceCall(mCall1, mCall2);

        ImsUtils.waitInCurrentState(WAIT_IN_CURRENT_STATE_MERGE_FAILED);
        //Verify foreground call is in Active state after merge failed.
        assertTrue("Call is not in Active State", (mCall2.getDetails().getState()
                == Call.STATE_ACTIVE));
        //Verify background call is in Hold state after merge failed.
        assertTrue("Call is not in Holding State", (mCall1.getDetails().getState()
                == Call.STATE_HOLDING));

        mCall2.disconnect();
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DISCONNECTING, WAIT_FOR_CALL_STATE));
        isCallDisconnected(mCall2, mCallSession2);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOVED, WAIT_FOR_CALL_STATE));

        //Wait till background call is in active state
        isCallActive(mCall1, mCallSession1);
        mCall1.disconnect();
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DISCONNECTING, WAIT_FOR_CALL_STATE));
        isCallDisconnected(mCall1, mCallSession1);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOVED, WAIT_FOR_CALL_STATE));
        waitForUnboundService();
    }

    @Test
    public void testConferenceCallFailureByRemoteTerminated() throws Exception {
        if (!ImsUtils.shouldTestImsCall()) {
            return;
        }

        bindImsService();
        mServiceCallBack = new ServiceCallBack();
        InCallServiceStateValidator.setCallbacks(mServiceCallBack);
        addOutgoingCalls();
        mCallSession2.addTestType(
                TestImsCallSessionImpl.TEST_TYPE_CONFERENCE_FAILED_REMOTE_TERMINATED);
        addConferenceCall(mCall1, mCall2);
        ImsUtils.waitInCurrentState(WAIT_IN_CURRENT_STATE);

        // first call is terminated by remote
        mCallSession1.sendTerminatedByRemote();
        // received mergeFailed due to terminated first call
        mCallSession2.sendMergedFailed();

        ImsUtils.waitInCurrentState(WAIT_IN_CURRENT_STATE);
        // verify foreground call is in Active state after merge failed.
        assertTrue("Call is not in Active State", (mCall2.getDetails().getState()
                == Call.STATE_ACTIVE));
        // verify background call is in Disconnected state.
        isCallDisconnected(mCall1, mCallSession1);
        assertTrue("Call is not in Disconnected State", (mCall1.getDetails().getState()
                == Call.STATE_DISCONNECTED));

        mCall2.disconnect();
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DISCONNECTING, WAIT_FOR_CALL_STATE));
        isCallDisconnected(mCall2, mCallSession2);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOVED, WAIT_FOR_CALL_STATE));
        waitForUnboundService();
    }

    @Test
    public void testCallJoinExistingConferenceCall() throws Exception {
        if (!ImsUtils.shouldTestImsCall()) {
            return;
        }

        bindImsService();
        mServiceCallBack = new ServiceCallBack();
        InCallServiceStateValidator.setCallbacks(mServiceCallBack);

        makeConferenceCall();
        // add third outgoing call, third call : active , conference call : hold
        addThirdOutgoingCall();
        mCallSession3.addTestType(TestImsCallSessionImpl.TEST_TYPE_JOIN_EXIST_CONFERENCE);

        mCall3.conference(mConferenceCall);
        // Wait for merge complete for the third call:
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_MERGE_COMPLETE, WAIT_FOR_CALL_STATE));
        isCallActive(mConferenceCall, mConfCallSession);

        ImsUtils.waitInCurrentState(WAIT_IN_CURRENT_STATE);
        // verify third call disconnected after conference Merge success
        assertParticiapantDisconnected(mCall3);

        // verify conference participant connections are connected.
        assertParticiapantAddedToConference(3);

        mConferenceCall.disconnect();

        assertParticiapantAddedToConference(0);
        isCallDisconnected(mConferenceCall, mConfCallSession);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOVED, WAIT_FOR_CALL_STATE));
        waitForUnboundService();
    }

    @RequiresFlagsEnabled(Flags.FLAG_CONFERENCE_HOLD_UNHOLD_CHANGED_TO_SEND_MESSAGE)
    @Test
    public void testCallJoinExistingConferenceCallAfterCallSwap() throws Exception {
        if (!ImsUtils.shouldTestImsCall()) {
            return;
        }

        bindImsService();
        mServiceCallBack = new ServiceCallBack();
        InCallServiceStateValidator.setCallbacks(mServiceCallBack);

        makeConferenceCall();
        // add third outgoing call, third call : active , conference call : hold
        addThirdOutgoingCall();

        // swap the call
        ImsUtils.waitInCurrentState(WAIT_IN_CURRENT_STATE);
        mConferenceCall.unhold();
        isCallHolding(mCall3, mCallSession3);
        assertTrue("Call is not in Hold State", (mCall3.getDetails().getState()
                == Call.STATE_HOLDING));
        isCallActive(mConferenceCall, mConfCallSession);

        mConfCallSession.addTestType(
                TestImsCallSessionImpl.TEST_TYPE_JOIN_EXIST_CONFERENCE_AFTER_SWAP);

        // merge call
        mConferenceCall.conference(mCall3);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_MERGE_START, WAIT_FOR_CALL_STATE));

        // verify third call disconnected.
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOVED, WAIT_FOR_CALL_STATE));
        assertParticiapantDisconnected(mCall3);

        // verify conference participant connections are connected.
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_MERGE_COMPLETE, WAIT_FOR_CALL_STATE));
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CHILDREN_CHANGED, WAIT_FOR_CALL_STATE));
        assertParticiapantAddedToConference(3);

        isCallActive(mConferenceCall, mConfCallSession);

        // disconnect the conference call.
        mConferenceCall.disconnect();

        // verify conference participant connections are disconnected.
        assertParticiapantAddedToConference(0);
        isCallDisconnected(mConferenceCall, mConfCallSession);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOVED, WAIT_FOR_CALL_STATE));
        waitForUnboundService();
    }

    @RequiresFlagsEnabled(Flags.FLAG_CONFERENCE_HOLD_UNHOLD_CHANGED_TO_SEND_MESSAGE)
    @Test
    public void testCallJoinExistingConferenceCallAfterCallSwapFail() throws Exception {
        if (!ImsUtils.shouldTestImsCall()) {
            return;
        }

        bindImsService();
        mServiceCallBack = new ServiceCallBack();
        InCallServiceStateValidator.setCallbacks(mServiceCallBack);

        makeConferenceCall();
        // add third outgoing call, third call : active , conference call : hold
        addThirdOutgoingCall();

        // swap the call
        ImsUtils.waitInCurrentState(WAIT_IN_CURRENT_STATE);
        mConferenceCall.unhold();
        isCallHolding(mCall3, mCallSession3);
        assertTrue("Call is not in Hold State", (mCall3.getDetails().getState()
                == Call.STATE_HOLDING));
        isCallActive(mConferenceCall, mConfCallSession);

        // fail to merge
        mConfCallSession.addTestType(
                TestImsCallSessionImpl.TEST_TYPE_JOIN_EXIST_CONFERENCE_FAILED_AFTER_SWAP);

        mConferenceCall.conference(mCall3);
        ImsUtils.waitInCurrentState(WAIT_IN_CURRENT_STATE_MERGE_FAILED);

        // verify foreground call is in Active state after merge failed.
        assertTrue("Call is not in Active State", (mConferenceCall.getDetails().getState()
                == Call.STATE_ACTIVE));
        // verify background call is in Hold state after merge failed.
        assertTrue("Call is not in Holding State", (mCall3.getDetails().getState()
                == Call.STATE_HOLDING));

        // disconnect the conference call.
        mConferenceCall.disconnect();

        // verify conference participant connections are disconnected.
        assertParticiapantAddedToConference(0);
        isCallDisconnected(mConferenceCall, mConfCallSession);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOVED, WAIT_FOR_CALL_STATE));

        // verify third call is active
        isCallActive(mCall3, mCallSession3);
        mCall3.disconnect();
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DISCONNECTING, WAIT_FOR_CALL_STATE));
        isCallDisconnected(mCall3, mCallSession3);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOVED, WAIT_FOR_CALL_STATE));
        waitForUnboundService();
    }

    @Test
    public void testSetCallAudioHandler() throws Exception {
        if (!ImsUtils.shouldTestImsCall()) {
            return;
        }

        bindImsService();
        mServiceCallBack = new ServiceCallBack();
        InCallServiceStateValidator.setCallbacks(mServiceCallBack);

        TelecomManager telecomManager = (TelecomManager) InstrumentationRegistry
                .getInstrumentation().getContext().getSystemService(Context.TELECOM_SERVICE);
        AudioManager mAudioManager = (AudioManager) InstrumentationRegistry
                .getInstrumentation().getContext().getSystemService(Context.AUDIO_SERVICE);

        final Uri imsUri = Uri.fromParts(PhoneAccount.SCHEME_TEL, String.valueOf(++sCounter), null);
        Bundle extras = new Bundle();

        mAudioManager.setMode(AudioManager.MODE_NORMAL);
        Log.i(LOG_TAG, "testSetCallAudioHandler - Reset AudioMode: " + mAudioManager.getMode());

        // Place outgoing call
        telecomManager.placeCall(imsUri, extras);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_ADDED, WAIT_FOR_CALL_STATE));

        Call call = getCall(mCurrentCallId);
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DIALING, WAIT_FOR_CALL_STATE));

        waitForCallSessionToNotBe(null);
        TimeUnit.MILLISECONDS.sleep(WAIT_UPDATE_TIMEOUT_MS);

        TestImsCallSessionImpl callSession =
                sServiceConnector.getCarrierService().getMmTelFeature().getImsCallsession();

        isCallActive(call, callSession);

        sServiceConnector.getCarrierService().getMmTelFeature()
                .setCallAudioHandler(MmTelFeature.AUDIO_HANDLER_ANDROID);
        TimeUnit.MILLISECONDS.sleep(WAIT_UPDATE_TIMEOUT_MS);

        assertNotEquals(AudioManager.MODE_NORMAL, mAudioManager.getMode());
        assertEquals(AudioManager.MODE_IN_COMMUNICATION, mAudioManager.getMode());

        call.disconnect();
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DISCONNECTING, WAIT_FOR_CALL_STATE));

        // Place the 2nd outgoing call
        telecomManager.placeCall(imsUri, extras);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_ADDED, WAIT_FOR_CALL_STATE));

        call = getCall(mCurrentCallId);
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DIALING, WAIT_FOR_CALL_STATE));

        waitForCallSessionToNotBe(null);
        TimeUnit.MILLISECONDS.sleep(WAIT_UPDATE_TIMEOUT_MS);
        callSession = sServiceConnector.getCarrierService().getMmTelFeature().getImsCallsession();

        isCallActive(call, callSession);

        sServiceConnector.getCarrierService().getMmTelFeature()
                .setCallAudioHandler(MmTelFeature.AUDIO_HANDLER_BASEBAND);
        TimeUnit.MILLISECONDS.sleep(WAIT_UPDATE_TIMEOUT_MS);

        assertNotEquals(AudioManager.MODE_NORMAL, mAudioManager.getMode());
        assertEquals(AudioManager.MODE_IN_CALL, mAudioManager.getMode());

        call.disconnect();

        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DISCONNECTING, WAIT_FOR_CALL_STATE));
        isCallDisconnected(call, callSession);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOVED, WAIT_FOR_CALL_STATE));
        waitForUnboundService();
    }

    @Test
    public void testNotifyCallStateChanged() throws Exception {
        if (!ImsUtils.shouldTestImsCall()) {
            return;
        }

        bindImsService();
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
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DIALING, WAIT_FOR_CALL_STATE));

        waitForCallSessionToNotBe(null);
        TestImsCallSessionImpl callSession =
                sServiceConnector.getCarrierService().getMmTelFeature().getImsCallsession();

        isCallActive(call, callSession);
        String callSessionId = callSession.getCallId();

        LinkedBlockingQueue<List<CallState>> queue = new LinkedBlockingQueue<>();
        ImsCallingTest.TestTelephonyCallbackForCallStateChange testCb =
                new ImsCallingTest.TestTelephonyCallbackForCallStateChange(queue);

        // test registration without permission
        try {
            sTelephonyManager.registerTelephonyCallback(Runnable::run, testCb);
            fail("registerTelephonyCallback requires READ_PRECISE_PHONE_STATE permission.");
        } catch (SecurityException e) {
            //expected
        }

        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                sTelephonyManager, (tm) -> tm.registerTelephonyCallback(Runnable::run, testCb));

        // Expect to receive cached CallState in TelephonyRegistry when the listener register event.
        List<CallState> callStateList = queue.poll(ImsUtils.TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        assertNotNull(callStateList);
        assertEquals(1, callStateList.size());
        assertEquals(
                PreciseCallState.PRECISE_CALL_STATE_ACTIVE, callStateList.get(0).getCallState());
        assertEquals(callSessionId, callStateList.get(0).getImsCallSessionId());
        assertEquals(ImsCallProfile.CALL_TYPE_VOICE, callStateList.get(0).getImsCallType());
        assertEquals(ImsCallProfile.SERVICE_TYPE_NORMAL,
                callStateList.get(0).getImsCallServiceType());

        // Hold Call.
        ImsStreamMediaProfile mediaProfile = new ImsStreamMediaProfile(
                ImsStreamMediaProfile.AUDIO_QUALITY_AMR,
                ImsStreamMediaProfile.DIRECTION_SEND_RECEIVE,
                ImsStreamMediaProfile.VIDEO_QUALITY_QCIF,
                ImsStreamMediaProfile.DIRECTION_SEND_RECEIVE,
                ImsStreamMediaProfile.RTT_MODE_DISABLED);
        callSession.hold(mediaProfile);

        //Check receiving CallState change callback.
        callStateList = queue.poll(ImsUtils.TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        assertNotNull(callStateList);
        assertEquals(1, callStateList.size());
        assertEquals(
                PreciseCallState.PRECISE_CALL_STATE_HOLDING, callStateList.get(0).getCallState());
        assertEquals(callSessionId, callStateList.get(0).getImsCallSessionId());
        assertEquals(ImsCallProfile.CALL_TYPE_VOICE, callStateList.get(0).getImsCallType());
        assertEquals(ImsCallProfile.SERVICE_TYPE_NORMAL,
                callStateList.get(0).getImsCallServiceType());

        call.disconnect();

        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DISCONNECTING, WAIT_FOR_CALL_STATE));
        isCallDisconnected(call, callSession);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOVED, WAIT_FOR_CALL_STATE));
        waitForUnboundService();
    }

    @Test
    public void testNotifyMediaCallStatusChanged() throws Exception {
        if (!ImsUtils.shouldTestImsCall()) {
            return;
        }

        bindImsService();
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
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DIALING, WAIT_FOR_CALL_STATE));

        waitForCallSessionToNotBe(null);
        TestImsCallSessionImpl callSession =
                sServiceConnector.getCarrierService().getMmTelFeature().getImsCallsession();

        isCallActive(call, callSession);
        String callSessionId = callSession.getCallId();

        LinkedBlockingQueue<MediaQualityStatus> queue = new LinkedBlockingQueue<>();
        ImsCallingTest.TestTelephonyCallback testCb =
                new ImsCallingTest.TestTelephonyCallback(queue);

        // test registration without permission
        try {
            sTelephonyManager.registerTelephonyCallback(Runnable::run, testCb);
            fail("registerTelephonyCallback requires READ_PRECISE_PHONE_STATE permission.");
        } catch (SecurityException e) {
            //expected
        }

        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                sTelephonyManager,
                (tm) -> tm.registerTelephonyCallback(Runnable::run, testCb));

        MediaQualityStatus status = queue.poll(ImsUtils.TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        assertNotNull(status);
        assertEquals(callSessionId, status.getCallSessionId());
        assertEquals(MediaQualityStatus.MEDIA_SESSION_TYPE_AUDIO, status.getMediaSessionType());
        assertEquals(AccessNetworkConstants.TRANSPORT_TYPE_WWAN, status.getTransportType());
        assertEquals(0, status.getRtpPacketLossRate());
        assertEquals(0, status.getRtpJitterMillis());
        assertEquals(0, status.getRtpInactivityMillis());

        //Notify a new media quality status.
        sServiceConnector.getCarrierService().getMmTelFeature()
                .notifyMediaQualityStatusChanged(new MediaQualityStatus(callSessionId,
                        MediaQualityStatus.MEDIA_SESSION_TYPE_AUDIO,
                        AccessNetworkConstants.TRANSPORT_TYPE_WWAN,
                        TEST_RTP_THRESHOLD_PACKET_LOSS_RATE,
                        TEST_RTP_THRESHOLD_JITTER_MILLIS,
                        TEST_RTP_THRESHOLD_INACTIVITY_TIME_MILLIS));

        status = queue.poll(ImsUtils.TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        assertNotNull(status);
        assertEquals(callSessionId, status.getCallSessionId());
        assertEquals(MediaQualityStatus.MEDIA_SESSION_TYPE_AUDIO, status.getMediaSessionType());
        assertEquals(AccessNetworkConstants.TRANSPORT_TYPE_WWAN, status.getTransportType());
        assertEquals(TEST_RTP_THRESHOLD_PACKET_LOSS_RATE, status.getRtpPacketLossRate());
        assertEquals(TEST_RTP_THRESHOLD_JITTER_MILLIS, status.getRtpJitterMillis());
        assertEquals(TEST_RTP_THRESHOLD_INACTIVITY_TIME_MILLIS, status.getRtpInactivityMillis());

        call.disconnect();

        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DISCONNECTING, WAIT_FOR_CALL_STATE));
        isCallDisconnected(call, callSession);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOVED, WAIT_FOR_CALL_STATE));
        waitForUnboundService();
    }

    @Test
    public void testOutGoingEmergencyCall() throws Exception {
        if (!ImsUtils.shouldTestImsCall()) {
            return;
        }

        boolean supportDomainSelection =
                ShellIdentityUtils.invokeMethodWithShellPermissions(sTelephonyManager,
                        (tm) -> tm.isDomainSelectionSupported());

        if (supportDomainSelection) {
            return;
        }

        LinkedBlockingQueue<List<CallState>> queue = new LinkedBlockingQueue<>();
        ImsCallingTest.TestTelephonyCallbackForCallStateChange testCb =
                new ImsCallingTest.TestTelephonyCallbackForCallStateChange(queue);
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                sTelephonyManager, (tm) -> tm.registerTelephonyCallback(Runnable::run, testCb));

        testCb.setTestEmergencyNumber(TEST_EMERGENCY_NUMBER);
        setupForEmergencyCalling(TEST_EMERGENCY_NUMBER);
        assertTrue(testCb.waitForTestEmergencyNumberConfigured());

        bindImsService();
        mServiceCallBack = new ServiceCallBack();
        InCallServiceStateValidator.setCallbacks(mServiceCallBack);

        // Place outgoing emergency call
        TelecomManager telecomManager = (TelecomManager) InstrumentationRegistry
                .getInstrumentation().getContext().getSystemService(Context.TELECOM_SERVICE);
        telecomManager.placeCall(TEST_EMERGENCY_URI, new Bundle());

        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_ADDED, WAIT_FOR_CALL_STATE));
        Call call = getCall(mCurrentCallId);
        waitForCallSessionToNotBe(null);
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DIALING, WAIT_FOR_CALL_STATE));

        assertTrue(testCb.waitForOutgoingEmergencyCall(TEST_EMERGENCY_NUMBER));
        assertTrue(testCb.waitForCallActive());

        TestImsCallSessionImpl callSession = sServiceConnector.getCarrierService().getMmTelFeature()
                .getImsCallsession();
        isCallActive(call, callSession);

        call.disconnect();
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DISCONNECTING, WAIT_FOR_CALL_STATE));
        isCallDisconnected(call, callSession);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOVED, WAIT_FOR_CALL_STATE));
        waitForUnboundService();
    }

    @Test
    @RequiresFlagsEnabled(
            Flags.FLAG_TERMINATE_ACTIVE_VIDEO_CALL_WHEN_ACCEPTING_SECOND_VIDEO_CALL_AS_AUDIO_ONLY)
    public void testMultipleVideoCallAcceptTerminate() throws Exception {
        if (!ImsUtils.shouldTestImsCall()) {
            return;
        }

        PersistableBundle bundle = new PersistableBundle();
        bundle.putBoolean(CarrierConfigManager.KEY_DROP_VIDEO_CALL_WHEN_ANSWERING_AUDIO_CALL_BOOL,
                true);
        bundle.putBoolean(CarrierConfigManager.KEY_CARRIER_WFC_IMS_AVAILABLE_BOOL, true);
        bundle.putBoolean(CarrierConfigManager.KEY_CARRIER_DEFAULT_WFC_IMS_ENABLED_BOOL, false);
        overrideCarrierConfig(bundle);

        // Precondition : WFC disabled
        Uri callingUri = Uri.withAppendedPath(
                SubscriptionManager.WFC_ENABLED_CONTENT_URI, "" + sTestSub);
        CountDownLatch contentObservedLatch = new CountDownLatch(1);
        ContentObserver observer = createObserver(callingUri, contentObservedLatch);

        ImsManager imsManager = getContext().getSystemService(ImsManager.class);
        ImsMmTelManager mMmTelManager = imsManager.getImsMmTelManager(sTestSub);

        boolean isEnabled = ShellIdentityUtils.invokeMethodWithShellPermissions(mMmTelManager,
                ImsMmTelManager::isVoWiFiSettingEnabled);
        if (isEnabled) {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mMmTelManager,
                    (m) -> m.setVoWiFiSettingEnabled(false));
        }
        waitForLatch(contentObservedLatch, observer);

        MmTelFeature.MmTelCapabilities capabilities =
                new MmTelFeature.MmTelCapabilities(
                        MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_VOICE
                                | MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_VIDEO);

        bindImsServiceForCapabilities(ImsRegistrationImplBase.REGISTRATION_TECH_IWLAN,
                capabilities);

        ImsStreamMediaProfile mediaProfile = new ImsStreamMediaProfile(
                ImsStreamMediaProfile.AUDIO_QUALITY_AMR,
                ImsStreamMediaProfile.DIRECTION_SEND_RECEIVE,
                ImsStreamMediaProfile.VIDEO_QUALITY_QVGA_PORTRAIT,
                ImsStreamMediaProfile.DIRECTION_SEND_RECEIVE,
                ImsStreamMediaProfile.RTT_MODE_DISABLED);
        sServiceConnector.getCarrierService().getMmTelFeature()
                .setImsStreamProfileForVt(mediaProfile);

        mServiceCallBack = new ServiceCallBack();
        InCallServiceStateValidator.setCallbacks(mServiceCallBack);

        TelecomManager telecomManager = (TelecomManager) InstrumentationRegistry
                .getInstrumentation().getContext().getSystemService(Context.TELECOM_SERVICE);

        final Uri imsUri = Uri.fromParts(PhoneAccount.SCHEME_TEL, String.valueOf(++sCounter), null);
        Bundle extras = new Bundle();
        extras.putInt(TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE,
                VideoProfile.STATE_BIDIRECTIONAL);
        extras.putInt(TelecomManager.EXTRA_CALL_NETWORK_TYPE, TelephonyManager.NETWORK_TYPE_IWLAN);

        // MO Call
        telecomManager.placeCall(imsUri, extras);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_ADDED, WAIT_FOR_CALL_STATE));

        Call moCall = getCall(mCurrentCallId);
        waitForCallSessionToNotBe(null);
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DIALING, WAIT_FOR_CALL_STATE));

        TestImsCallSessionImpl moCallSession = sServiceConnector.getCarrierService()
                .getMmTelFeature().getImsCallsession();

        isCallActive(moCall, moCallSession);

        // MT Call
        extras.putBoolean("android.telephony.ims.feature.extra.IS_USSD", false);
        extras.putBoolean("android.telephony.ims.feature.extra.IS_UNKNOWN_CALL", false);
        extras.putString("android:imsCallID", String.valueOf(++sCounter));
        extras.putLong("android:phone_id", 123456);
        sServiceConnector.getCarrierService().getMmTelFeature().onIncomingVtCallReceived(extras);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_ADDED, WAIT_FOR_CALL_STATE));
        waitForCallSessionToNotBe(null);
        TestImsCallSessionImpl mtCallSession =
                sServiceConnector.getCarrierService().getMmTelFeature().getImsCallsession();

        Call mtCall = null;
        if (mCurrentCallId != null) {
            mtCall = getCall(mCurrentCallId);
            if (mtCall.getDetails().getState() == Call.STATE_RINGING) {
                mtCall.answer(VideoProfile.STATE_AUDIO_ONLY);
            }
        }

        // MO call should be terminated
        isCallDisconnected(moCall, moCallSession);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOVED, WAIT_FOR_CALL_STATE));

        isCallActive(mtCall, mtCallSession);
        mtCall.disconnect();

        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DISCONNECTING, WAIT_FOR_CALL_STATE));
        isCallDisconnected(mtCall, mtCallSession);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOVED, WAIT_FOR_CALL_STATE));

        // Return the WFC activation status to before testing.
        if (isEnabled) {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mMmTelManager,
                    (m) -> m.setVoWiFiSettingEnabled(true));
        }
        waitForLatch(contentObservedLatch, observer);

        waitForUnboundService();
    }

    private ContentObserver createObserver(Uri observerUri, CountDownLatch latch) {
        ContentObserver observer = new ContentObserver(sHandler) {
            @Override
            public void onChange(boolean selfChange, Uri uri) {
                if (observerUri.equals(uri)) {
                    latch.countDown();
                }
            }
        };
        getContext().getContentResolver().registerContentObserver(observerUri, true, observer);
        return observer;
    }

    private void waitForLatch(CountDownLatch latch, ContentObserver observer) {
        try {
            // Wait for the ContentObserver to fire signalling the change.
            latch.await(ImsUtils.TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            fail("Interrupted Exception waiting for latch countdown:" + e.getMessage());
        } finally {
            getContext().getContentResolver().unregisterContentObserver(observer);
        }
    }

    private class TestTelephonyCallback extends TelephonyCallback
            implements TelephonyCallback.MediaQualityStatusChangedListener {
        LinkedBlockingQueue<MediaQualityStatus> mTestMediaQualityStatusQueue;
        TestTelephonyCallback(LinkedBlockingQueue<MediaQualityStatus> queue) {
            mTestMediaQualityStatusQueue = queue;
        }
        @Override
        public void onMediaQualityStatusChanged(@NonNull MediaQualityStatus status) {
            mTestMediaQualityStatusQueue.offer(status);
        }
    }

    private class TestTelephonyCallbackForCallStateChange extends TelephonyCallback implements
            TelephonyCallback.CallAttributesListener,
            TelephonyCallback.PreciseCallStateListener,
            TelephonyCallback.OutgoingEmergencyCallListener,
            TelephonyCallback.EmergencyNumberListListener {
        LinkedBlockingQueue<List<CallState>> mTestCallStateListeQueue;
        private EmergencyNumber mLastOutgoingEmergencyNumber;
        private String mTestEmergencyNumber;
        private Semaphore mOutgoingEmergencyCallSemaphore = new Semaphore(0);
        private Semaphore mActiveCallStateSemaphore = new Semaphore(0);
        private Semaphore mTestEmergencyNumberSemaphore = new Semaphore(0);
        TestTelephonyCallbackForCallStateChange(LinkedBlockingQueue<List<CallState>> queue) {
            mTestCallStateListeQueue = queue;
        }
        @Override
        public void onCallStatesChanged(@NonNull List<CallState> states) {
            mTestCallStateListeQueue.offer(states);
        }

        @Override
        public void onPreciseCallStateChanged(@NonNull PreciseCallState callState) {
            Log.i(LOG_TAG, "onPreciseCallStateChanged: state=" + callState);
            if (callState.getForegroundCallState() == PreciseCallState.PRECISE_CALL_STATE_ACTIVE) {
                mActiveCallStateSemaphore.release();
            }
        }

        @Override
        public void onOutgoingEmergencyCall(EmergencyNumber emergencyNumber, int subscriptionId) {
            Log.i(LOG_TAG, "onOutgoingEmergencyCall: emergencyNumber=" + emergencyNumber);
            mLastOutgoingEmergencyNumber = emergencyNumber;
            mOutgoingEmergencyCallSemaphore.release();
        }

        @Override
        public void onEmergencyNumberListChanged(@NonNull Map<Integer,
                        List<EmergencyNumber>> emergencyNumberList) {
            if (!TextUtils.isEmpty(mTestEmergencyNumber)) {
                for (List<EmergencyNumber> emergencyNumbers : emergencyNumberList.values()) {
                    Log.i(LOG_TAG, "onEmergencyNumberListChanged: emergencyNumbers="
                            + emergencyNumbers.stream().map(Object::toString).collect(
                                    Collectors.joining(", ")));
                    for (EmergencyNumber emergencyNumber : emergencyNumbers) {
                        if (TextUtils.equals(mTestEmergencyNumber, emergencyNumber.getNumber())) {
                            mTestEmergencyNumberSemaphore.release();
                            break;
                        }
                    }
                }
            }
        }

        public boolean waitForCallActive() {
            try {
                if (!mActiveCallStateSemaphore.tryAcquire(
                        WAIT_FOR_STATE_CHANGE_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                    Log.e(LOG_TAG, "Timed out to receive active call state");
                    return false;
                }
            } catch (InterruptedException ex) {
                Log.e(LOG_TAG, "waitForCallActive: ex=" + ex);
                return false;
            }
            return true;
        }

        public boolean waitForOutgoingEmergencyCall(String expectedNumber) {
            try {
                if (!mOutgoingEmergencyCallSemaphore.tryAcquire(
                        WAIT_FOR_STATE_CHANGE_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                    Log.e(LOG_TAG, "Timed out to receive OutgoingEmergencyCall");
                    return false;
                }
            } catch (InterruptedException ex) {
                Log.e(LOG_TAG, "waitForOutgoingEmergencyCall: ex=" + ex);
                return false;
            }

            // At this point we can only be sure that we got AN update, but not necessarily the one
            // we are looking for; wait until we see the state we want before verifying further.
            waitUntilConditionIsTrueOrTimeout(
                    new Condition() {
                        @Override
                        public Object expected() {
                            return true;
                        }

                        @Override
                        public Object actual() {
                            return mLastOutgoingEmergencyNumber != null
                                    && mLastOutgoingEmergencyNumber.getNumber().equals(
                                            expectedNumber);
                        }
                    },
                    WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                    "Expected emergency number: " + expectedNumber);
            return TextUtils.equals(expectedNumber, mLastOutgoingEmergencyNumber.getNumber());
        }

        public void setTestEmergencyNumber(String testEmergencyNumber) {
            mTestEmergencyNumber = testEmergencyNumber;
        }

        public boolean waitForTestEmergencyNumberConfigured() {
            try {
                if (!mTestEmergencyNumberSemaphore.tryAcquire(
                        WAIT_FOR_STATE_CHANGE_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                    Log.e(LOG_TAG, "Timed out to receive expected test emergency number "
                            + "configured");
                    return false;
                }
            } catch (InterruptedException ex) {
                Log.e(LOG_TAG, "waitForTestEmergencyNumberConfigured: ex=" + ex);
                return false;
            }
            return true;
        }
    }

    void addConferenceCall(Call call1, Call call2) {
        InCallServiceStateValidator inCallService = mServiceCallBack.getService();

        // Verify that the calls have each other on their conferenceable list before proceeding
        List<Call> callConfList = new ArrayList<>();
        callConfList.add(call2);
        assertCallConferenceableList(call1, callConfList);

        callConfList.clear();
        callConfList.add(call1);
        assertCallConferenceableList(call2, callConfList);

        call2.conference(call1);
    }

    void assertCallConferenceableList(final Call call, final List<Call> conferenceableList) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return conferenceableList;
                    }

                    @Override
                    public Object actual() {
                        return call.getConferenceableCalls();
                    }
                }, WAIT_FOR_CONDITION,
                        "Call: " + call + " does not have the correct conferenceable call list."
        );
    }

    private void assertParticiapantDisconnected(Call call) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return true;
                    }

                    @Override
                    public Object actual() {
                        return ((call.getState() == Call.STATE_DISCONNECTED)) ? true : false;
                    }
                }, WAIT_FOR_CONDITION, "Call Disconnected");
    }

    private void assertParticiapantAddedToConference(int count) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return true;
                    }

                    @Override
                    public Object actual() {
                        return (mParticipantCount == count) ? true : false;
                    }
                }, WAIT_FOR_CONDITION, "Call Added");
    }

    private void addOutgoingCalls() throws Exception {
        TelecomManager telecomManager = (TelecomManager) InstrumentationRegistry
                .getInstrumentation().getContext().getSystemService(Context.TELECOM_SERVICE);

        // Place first outgoing call
        final Uri imsUri1 = Uri.fromParts(PhoneAccount.SCHEME_TEL, String.valueOf(++sCounter),
                null);
        Bundle extras1 = new Bundle();

        telecomManager.placeCall(imsUri1, extras1);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_ADDED, WAIT_FOR_CALL_STATE));

        mCall1 = getCall(mCurrentCallId);
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DIALING, WAIT_FOR_CALL_STATE));
        waitForCallSessionToNotBe(null);
        mCallSession1 = sServiceConnector.getCarrierService().getMmTelFeature().getImsCallsession();
        isCallActive(mCall1, mCallSession1);
        assertTrue("Call is not in Active State", (mCall1.getDetails().getState()
                == Call.STATE_ACTIVE));

        // Place second outgoing call
        final Uri imsUri2 = Uri.fromParts(PhoneAccount.SCHEME_TEL, String.valueOf(++sCounter),
                null);
        Bundle extras2 = new Bundle();

        telecomManager.placeCall(imsUri2, extras2);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_ADDED, WAIT_FOR_CALL_STATE));

        mCall2 = getCall(mCurrentCallId);
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DIALING, WAIT_FOR_CALL_STATE));
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_HOLDING, WAIT_FOR_CALL_STATE));
        assertTrue("Call is not in Hold State", (mCall1.getDetails().getState()
                == Call.STATE_HOLDING));

        // Wait till the object of TestImsCallSessionImpl for second call created.
        waitForCallSessionToNotBe(mCallSession1);
        mCallSession2 = sServiceConnector.getCarrierService().getMmTelFeature().getImsCallsession();
        isCallActive(mCall2, mCallSession2);
        assertTrue("Call is not in Active State", (mCall2.getDetails().getState()
                == Call.STATE_ACTIVE));
    }

    private void addThirdOutgoingCall() {
        // add a third outgoing call when a conference call already exists.
        TelecomManager telecomManager = (TelecomManager) InstrumentationRegistry
                .getInstrumentation().getContext().getSystemService(Context.TELECOM_SERVICE);
        final Uri imsUri3 = Uri.fromParts(PhoneAccount.SCHEME_TEL, String.valueOf(++sCounter),
                null);
        Bundle extras3 = new Bundle();

        telecomManager.placeCall(imsUri3, extras3);
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_ADDED, WAIT_FOR_CALL_STATE));
        waitNextCallAdded(String.valueOf(sCounter));

        mCall3 = getCall(mCurrentCallId);
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_DIALING, WAIT_FOR_CALL_STATE));
        assertTrue(callingTestLatchCountdown(LATCH_IS_CALL_HOLDING, WAIT_FOR_CALL_STATE));
        assertTrue("Call is not in Hold State", (mConferenceCall.getDetails().getState()
                == Call.STATE_HOLDING));

        waitForCallSessionToNotBe(mCallSession2);
        mCallSession3 =
                sServiceConnector.getCarrierService().getMmTelFeature().getImsCallsession();

        isCallActive(mCall3, mCallSession3);
        assertTrue("Call is not in Active State", (mCall3.getDetails().getState()
                == Call.STATE_ACTIVE));
    }

    private void waitForCallSessionToNotBe(TestImsCallSessionImpl previousCallSession) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return true;
                    }

                    @Override
                    public Object actual() {
                        TestMmTelFeature mmtelfeatue = sServiceConnector.getCarrierService()
                                .getMmTelFeature();
                        return (mmtelfeatue.getImsCallsession() != previousCallSession) ? true
                                : false;
                    }
                }, WAIT_FOR_CONDITION, "CallSession Created");
    }

    private void waitNextCallAdded(String expectedUri) {
        callingTestLatchCountdown(LATCH_IS_ON_CALL_ADDED, WAIT_FOR_CALL_STATE);
        final String[] actualUri = {getCall(mCurrentCallId).getDetails().getHandle().toString()};
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return true;
                    }

                    @Override
                    public Object actual() {
                        if (!actualUri[0].contains(expectedUri)) {
                            assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CALL_ADDED,
                                    WAIT_FOR_CALL_STATE));
                            actualUri[0] = getCall(
                                    mCurrentCallId).getDetails().getHandle().toString();
                            return false;
                        }
                        return true;
                    }
                }, WAIT_FOR_CONDITION, "Next Call added");
    }

    private void waitCallRenegotiating(TestImsCallSessionImpl callSession) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return true;
                    }

                    @Override
                    public Object actual() {
                        return callSession.isRenegotiating() ? true : false;
                    }
                }, WAIT_FOR_CONDITION, callSession.getState() + ", waitCallRenegotiating");
    }

    private void makeConferenceCall() throws Exception {
        // Initialize the MERGE_START latch with a count of 2 (one for each call of the conference):
        overrideLatchCount(LATCH_IS_ON_MERGE_START, 2);

        addOutgoingCalls();
        addConferenceCall(mCall1, mCall2);

        // Wait for merge start first and second call
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_MERGE_START, WAIT_FOR_CALL_STATE));
        // Wait for merge complete background call:
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_MERGE_COMPLETE, WAIT_FOR_CALL_STATE));
        // Wait for remove first call
        callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOVED, WAIT_FOR_CALL_STATE);
        // Wait for merge complete foreground call:
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_MERGE_COMPLETE, WAIT_FOR_CALL_STATE));
        // Wait for conference call added
        assertTrue(
                callingTestLatchCountdown(LATCH_IS_ON_CONFERENCE_CALL_ADDED, WAIT_FOR_CALL_STATE));
        // Wait for remove second call
        callingTestLatchCountdown(LATCH_IS_ON_CALL_REMOVED, WAIT_FOR_CALL_STATE);
        // Wait to add participants in conference
        assertTrue("Conference call is not added", mServiceCallBack.getService()
                .getConferenceCallCount() > 0);

        mConferenceCall = mServiceCallBack.getService().getLastConferenceCall();
        assertNotNull("Unable to add conference call, its null", mConferenceCall);

        ConferenceHelper confHelper = sServiceConnector.getCarrierService().getMmTelFeature()
                .getConferenceHelper();

        mConfCallSession = confHelper.getConferenceSession();
        isCallActive(mConferenceCall, mConfCallSession);
        assertTrue("Conference call is not Active", mConfCallSession.isInCall());

        //Verify mCall1 and mCall2 disconnected after conference Merge success
        assertParticiapantDisconnected(mCall1);
        assertParticiapantDisconnected(mCall2);

        //Verify conference participant connections are connected.
        assertTrue(callingTestLatchCountdown(LATCH_IS_ON_CHILDREN_CHANGED, WAIT_FOR_CALL_STATE));
        assertParticiapantAddedToConference(2);

        // Since the conference call has been made, remove session1&2 from the confHelper session.
        confHelper.removeSession(mCallSession1);
        confHelper.removeSession(mCallSession2);
    }

    private void resetCallSessionObjects() {
        mCall1 = mCall2 = mCall3 = mConferenceCall = null;
        mCallSession1 = mCallSession2 = mCallSession3 = mConfCallSession = null;
        if (sServiceConnector.getCarrierService() == null
                || sServiceConnector.getCarrierService().getMmTelFeature() == null) {
            return;
        }
        ConferenceHelper confHelper = sServiceConnector.getCarrierService().getMmTelFeature()
                .getConferenceHelper();
        if (confHelper != null) {
            confHelper.clearSessions();
        }
    }
}
