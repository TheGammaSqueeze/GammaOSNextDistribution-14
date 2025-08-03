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

package com.android.bluetooth.telephony;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothLeCallControl;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.telecom.BluetoothCallQualityReport;
import android.telecom.Call;
import android.telecom.Connection;
import android.telecom.DisconnectCause;
import android.telecom.GatewayInfo;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.MediumTest;
import androidx.test.rule.ServiceTestRule;
import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.hfp.BluetoothHeadsetProxy;
import com.android.bluetooth.tbs.BluetoothLeCallControlProxy;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Tests for {@link BluetoothInCallService}
 */
@MediumTest
@RunWith(AndroidJUnit4.class)
public class BluetoothInCallServiceTest {
    private static final String TAG = "BluetoothInCallServiceTest";

    private static final int TEST_DTMF_TONE = 0;
    private static final String TEST_ACCOUNT_ADDRESS = "//foo.com/";
    private static final int TEST_ACCOUNT_INDEX = 0;

    private static final int CALL_STATE_ACTIVE = 0;
    private static final int CALL_STATE_HELD = 1;
    private static final int CALL_STATE_DIALING = 2;
    private static final int CALL_STATE_ALERTING = 3;
    private static final int CALL_STATE_INCOMING = 4;
    private static final int CALL_STATE_WAITING = 5;
    private static final int CALL_STATE_IDLE = 6;
    private static final int CALL_STATE_DISCONNECTED = 7;
    // Terminate all held or set UDUB("busy") to a waiting call
    private static final int CHLD_TYPE_RELEASEHELD = 0;
    // Terminate all active calls and accepts a waiting/held call
    private static final int CHLD_TYPE_RELEASEACTIVE_ACCEPTHELD = 1;
    // Hold all active calls and accepts a waiting/held call
    private static final int CHLD_TYPE_HOLDACTIVE_ACCEPTHELD = 2;
    // Add all held calls to a conference
    private static final int CHLD_TYPE_ADDHELDTOCONF = 3;

    private TestableBluetoothInCallService mBluetoothInCallService;
    @Rule
    public final ServiceTestRule mServiceRule
            = ServiceTestRule.withTimeout(1, TimeUnit.SECONDS);

    @Mock
    private BluetoothHeadsetProxy mMockBluetoothHeadset;
    @Mock
    private BluetoothLeCallControlProxy mMockBluetoothLeCallControl;
    @Mock
    private BluetoothInCallService.CallInfo mMockCallInfo;
    @Mock
    private TelephonyManager mMockTelephonyManager;
    @Mock
    private Context mContext = ApplicationProvider.getApplicationContext();

    public class TestableBluetoothInCallService extends BluetoothInCallService {
        @Override
        public IBinder onBind(Intent intent) {
            IBinder binder = super.onBind(intent);
            IntentFilter intentFilter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
            intentFilter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
            registerReceiver(mBluetoothAdapterReceiver, intentFilter);
            mTelephonyManager = getSystemService(TelephonyManager.class);
            mTelecomManager = getSystemService(TelecomManager.class);
            return binder;
        }

        @Override
        protected void enforceModifyPermission() {
        }

        protected void setOnCreateCalled(boolean called) {
            mOnCreateCalled = called;
        }
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        InstrumentationRegistry.getInstrumentation().getUiAutomation().adoptShellPermissionIdentity();

        // Create the service Intent.
        Intent serviceIntent =
                new Intent(ApplicationProvider.getApplicationContext(),
                        TestableBluetoothInCallService.class);
        // Bind the service
        mServiceRule.bindService(serviceIntent);

        // Ensure initialization does not actually try to access any of the CallsManager fields.
        // This also works to return null if it is not overwritten later in the test.
        doReturn(null).when(mMockCallInfo).getActiveCall();
        doReturn(null).when(mMockCallInfo)
                .getRingingOrSimulatedRingingCall();
        doReturn(null).when(mMockCallInfo).getHeldCall();
        doReturn(null).when(mMockCallInfo).getOutgoingCall();
        doReturn(0).when(mMockCallInfo).getNumHeldCalls();
        doReturn(false).when(mMockCallInfo).hasOnlyDisconnectedCalls();
        doReturn(true).when(mMockCallInfo).isNullCall(null);
        doReturn(false).when(mMockCallInfo).isNullCall(notNull());

        mBluetoothInCallService = new TestableBluetoothInCallService();
        mBluetoothInCallService.setBluetoothHeadset(mMockBluetoothHeadset);
        mBluetoothInCallService.setBluetoothLeCallControl(mMockBluetoothLeCallControl);
        mBluetoothInCallService.mCallInfo = mMockCallInfo;
        mBluetoothInCallService.setOnCreateCalled(true);
    }

    @After
    public void tearDown() throws Exception {
        mServiceRule.unbindService();
        mBluetoothInCallService = null;
    }

    @Test
    public void testHeadsetAnswerCall() throws Exception {
        BluetoothCall mockCall = createRingingCall(UUID.randomUUID());

        boolean callAnswered = mBluetoothInCallService.answerCall();
        verify(mockCall).answer(any(int.class));

        Assert.assertTrue(callAnswered);
    }

    @Test
    public void testHeadsetAnswerCallNull() throws Exception {
        when(mMockCallInfo.getRingingOrSimulatedRingingCall()).thenReturn(null);

        boolean callAnswered = mBluetoothInCallService.answerCall();
        Assert.assertFalse(callAnswered);
    }

    @Test
    public void testHeadsetHangupCall() throws Exception {
        BluetoothCall mockCall = createForegroundCall(UUID.randomUUID());

        boolean callHungup = mBluetoothInCallService.hangupCall();

        verify(mockCall).disconnect();
        Assert.assertTrue(callHungup);
    }

    @Test
    public void testHeadsetHangupCallNull() throws Exception {
        when(mMockCallInfo.getForegroundCall()).thenReturn(null);

        boolean callHungup = mBluetoothInCallService.hangupCall();
        Assert.assertFalse(callHungup);
    }

    @Test
    public void testHeadsetSendDTMF() throws Exception {
        BluetoothCall mockCall = createForegroundCall(UUID.randomUUID());

        boolean sentDtmf = mBluetoothInCallService.sendDtmf(TEST_DTMF_TONE);

        verify(mockCall).playDtmfTone(eq((char) TEST_DTMF_TONE));
        verify(mockCall).stopDtmfTone();
        Assert.assertTrue(sentDtmf);
    }

    @Test
    public void testHeadsetSendDTMFNull() throws Exception {
        when(mMockCallInfo.getForegroundCall()).thenReturn(null);

        boolean sentDtmf = mBluetoothInCallService.sendDtmf(TEST_DTMF_TONE);
        Assert.assertFalse(sentDtmf);
    }

    @Test
    public void testGetNetworkOperator() throws Exception {
        PhoneAccount fakePhoneAccount = makeQuickAccount("id0", TEST_ACCOUNT_INDEX);
        when(mMockCallInfo.getBestPhoneAccount()).thenReturn(fakePhoneAccount);

        String networkOperator = mBluetoothInCallService.getNetworkOperator();
        Assert.assertEquals(networkOperator, "label0");
    }

    @Test
    public void testGetNetworkOperatorNoPhoneAccount() throws Exception {
        when(mMockCallInfo.getForegroundCall()).thenReturn(null);
        when(mMockTelephonyManager.getNetworkOperatorName()).thenReturn("label1");
        mBluetoothInCallService.mTelephonyManager = mMockTelephonyManager;

        String networkOperator = mBluetoothInCallService.getNetworkOperator();
        Assert.assertEquals(networkOperator, "label1");
    }

    @Test
    public void testGetSubscriberNumber() throws Exception {
        PhoneAccount fakePhoneAccount = makeQuickAccount("id0", TEST_ACCOUNT_INDEX);
        when(mMockCallInfo.getBestPhoneAccount()).thenReturn(fakePhoneAccount);

        String subscriberNumber = mBluetoothInCallService.getSubscriberNumber();
        Assert.assertEquals(subscriberNumber, TEST_ACCOUNT_ADDRESS + TEST_ACCOUNT_INDEX);
    }

    @Test
    public void testGetSubscriberNumberFallbackToTelephony() throws Exception {
        String fakeNumber = "8675309";
        when(mMockCallInfo.getBestPhoneAccount()).thenReturn(null);
        when(mMockTelephonyManager.getLine1Number())
                .thenReturn(fakeNumber);
        mBluetoothInCallService.mTelephonyManager = mMockTelephonyManager;

        String subscriberNumber = mBluetoothInCallService.getSubscriberNumber();
        Assert.assertEquals(subscriberNumber, fakeNumber);
    }

    @Test
    public void testListCurrentCallsOneCall() throws Exception {
        ArrayList<BluetoothCall> calls = new ArrayList<>();
        BluetoothCall activeCall = createActiveCall(UUID.randomUUID());
        when(activeCall.getState()).thenReturn(Call.STATE_ACTIVE);
        calls.add(activeCall);
        mBluetoothInCallService.onCallAdded(activeCall);
        when(activeCall.isConference()).thenReturn(false);
        when(activeCall.getHandle()).thenReturn(Uri.parse("tel:555-000"));
        when(mMockCallInfo.getBluetoothCalls()).thenReturn(calls);

        clearInvocations(mMockBluetoothHeadset);
        mBluetoothInCallService.listCurrentCalls();

        verify(mMockBluetoothHeadset).clccResponse(eq(1), eq(0), eq(0), eq(0), eq(false),
                eq("555000"), eq(PhoneNumberUtils.TOA_Unknown));
        verify(mMockBluetoothHeadset).clccResponse(0, 0, 0, 0, false, null, 0);
    }

    /**
     * Verifies bluetooth call quality reports are properly parceled and set as a call event to
     * Telecom.
     */
    @Test
    public void testBluetoothCallQualityReport() {
        BluetoothCall activeCall = createForegroundCall(UUID.randomUUID());
        when(activeCall.isCallNull()).thenReturn(false);
        mBluetoothInCallService.onCallAdded(activeCall);

        mBluetoothInCallService.sendBluetoothCallQualityReport(
                10, // long timestamp
                20, // int rssi
                30, // int snr
                40, // int retransmissionCount
                50, // int packetsNotReceiveCount
                60 // int negativeAcknowledgementCount
        );

        ArgumentCaptor<Bundle> bundleCaptor = ArgumentCaptor.forClass(Bundle.class);
        verify(activeCall).sendCallEvent(
                eq(BluetoothCallQualityReport.EVENT_BLUETOOTH_CALL_QUALITY_REPORT),
                bundleCaptor.capture());
        Bundle bundle = bundleCaptor.getValue();
        BluetoothCallQualityReport report = (BluetoothCallQualityReport) bundle.get(
                BluetoothCallQualityReport.EXTRA_BLUETOOTH_CALL_QUALITY_REPORT);
        Assert.assertEquals(10, report.getSentTimestampMillis());
        Assert.assertEquals(20, report.getRssiDbm());
        Assert.assertEquals(30, report.getSnrDb());
        Assert.assertEquals(40, report.getRetransmittedPacketsCount());
        Assert.assertEquals(50, report.getPacketsNotReceivedCount());
        Assert.assertEquals(60, report.getNegativeAcknowledgementCount());
    }

    @Test
    public void testListCurrentCallsSilentRinging() throws Exception {
        ArrayList<BluetoothCall> calls = new ArrayList<>();
        BluetoothCall silentRingingCall = createActiveCall(UUID.randomUUID());
        when(silentRingingCall.getState()).thenReturn(Call.STATE_RINGING);
        when(silentRingingCall.isSilentRingingRequested()).thenReturn(true);
        calls.add(silentRingingCall);
        mBluetoothInCallService.onCallAdded(silentRingingCall);

        when(silentRingingCall.isConference()).thenReturn(false);
        when(silentRingingCall.getHandle()).thenReturn(Uri.parse("tel:555-000"));
        when(mMockCallInfo.getBluetoothCalls()).thenReturn(calls);
        when(mMockCallInfo.getRingingOrSimulatedRingingCall()).thenReturn(silentRingingCall);

        clearInvocations(mMockBluetoothHeadset);
        mBluetoothInCallService.listCurrentCalls();

        verify(mMockBluetoothHeadset, never()).clccResponse(eq(1), eq(0), eq(0), eq(0), eq(false),
                eq("555000"), eq(PhoneNumberUtils.TOA_Unknown));
        verify(mMockBluetoothHeadset).clccResponse(0, 0, 0, 0, false, null, 0);
    }

    @Test
    public void testConferenceInProgressCDMA() throws Exception {
        // If two calls are being conferenced and updateHeadsetWithCallState runs while this is
        // still occurring, it will look like there is an active and held BluetoothCall still while
        // we are transitioning into a conference.
        // BluetoothCall has been put into a CDMA "conference" with one BluetoothCall on hold.
        ArrayList<BluetoothCall> calls = new ArrayList<>();
        BluetoothCall parentCall = createActiveCall(UUID.randomUUID());
        final BluetoothCall confCall1 = getMockCall(UUID.randomUUID());
        final BluetoothCall confCall2 = createHeldCall(UUID.randomUUID());
        calls.add(parentCall);
        calls.add(confCall1);
        calls.add(confCall2);
        mBluetoothInCallService.onCallAdded(parentCall);
        mBluetoothInCallService.onCallAdded(confCall1);
        mBluetoothInCallService.onCallAdded(confCall2);

        when(mMockCallInfo.getBluetoothCalls()).thenReturn(calls);
        when(confCall1.getState()).thenReturn(Call.STATE_ACTIVE);
        when(confCall2.getState()).thenReturn(Call.STATE_ACTIVE);
        when(confCall1.isIncoming()).thenReturn(false);
        when(confCall2.isIncoming()).thenReturn(true);
        when(confCall1.getGatewayInfo()).thenReturn(
                new GatewayInfo(null, null, Uri.parse("tel:555-0000")));
        when(confCall2.getGatewayInfo()).thenReturn(
                new GatewayInfo(null, null, Uri.parse("tel:555-0001")));
        addCallCapability(parentCall, Connection.CAPABILITY_MERGE_CONFERENCE);
        addCallCapability(parentCall, Connection.CAPABILITY_SWAP_CONFERENCE);
        removeCallCapability(parentCall, Connection.CAPABILITY_CONFERENCE_HAS_NO_CHILDREN);
        Integer confCall1Id = confCall1.getId();
        when(parentCall.getGenericConferenceActiveChildCallId())
                .thenReturn(confCall1Id);
        when(parentCall.isConference()).thenReturn(true);
        List<Integer> childrenIds = Arrays.asList(confCall1.getId(),
                confCall2.getId());
        when(parentCall.getChildrenIds()).thenReturn(childrenIds);
        //Add links from child calls to parent
        Integer parentId = parentCall.getId();
        when(confCall1.getParentId()).thenReturn(parentId);
        when(confCall2.getParentId()).thenReturn(parentId);

        clearInvocations(mMockBluetoothHeadset);
        mBluetoothInCallService.queryPhoneState();
        verify(mMockBluetoothHeadset).phoneStateChanged(eq(1), eq(1), eq(CALL_STATE_IDLE),
                eq(""), eq(128), nullable(String.class));

        when(parentCall.wasConferencePreviouslyMerged()).thenReturn(true);
        List<BluetoothCall> children =
                mBluetoothInCallService.getBluetoothCallsByIds(parentCall.getChildrenIds());
        mBluetoothInCallService.getCallback(parentCall)
                .onChildrenChanged(parentCall, children);
        verify(mMockBluetoothHeadset).phoneStateChanged(eq(1), eq(0), eq(CALL_STATE_IDLE),
                eq(""), eq(128), nullable(String.class));

        when(mMockCallInfo.getHeldCall()).thenReturn(null);
        // Spurious BluetoothCall to onIsConferencedChanged.
        mBluetoothInCallService.getCallback(parentCall)
                .onChildrenChanged(parentCall, children);
        // Make sure the BluetoothCall has only occurred collectively 2 times (not on the third)
        verify(mMockBluetoothHeadset, times(2)).phoneStateChanged(any(int.class),
                any(int.class), any(int.class), nullable(String.class), any(int.class),
                nullable(String.class));
    }

    @Test
    public void testListCurrentCallsCdmaHold() throws Exception {
        // BluetoothCall has been put into a CDMA "conference" with one BluetoothCall on hold.
        List<BluetoothCall> calls = new ArrayList<BluetoothCall>();
        BluetoothCall parentCall = createActiveCall(UUID.randomUUID());
        when(parentCall.getHandle()).thenReturn(Uri.parse("tel:555-0000"));
        final BluetoothCall foregroundCall = getMockCall(UUID.randomUUID());
        when(foregroundCall.getHandle()).thenReturn(Uri.parse("tel:555-0001"));
        final BluetoothCall heldCall = createHeldCall(UUID.randomUUID());
        when(heldCall.getHandle()).thenReturn(Uri.parse("tel:555-0002"));
        calls.add(parentCall);
        calls.add(foregroundCall);
        calls.add(heldCall);
        mBluetoothInCallService.onCallAdded(parentCall);
        mBluetoothInCallService.onCallAdded(foregroundCall);
        mBluetoothInCallService.onCallAdded(heldCall);

        when(mMockCallInfo.getBluetoothCalls()).thenReturn(calls);
        when(foregroundCall.getState()).thenReturn(Call.STATE_ACTIVE);
        when(heldCall.getState()).thenReturn(Call.STATE_ACTIVE);
        when(foregroundCall.isIncoming()).thenReturn(false);
        when(heldCall.isIncoming()).thenReturn(true);
        when(foregroundCall.getGatewayInfo()).thenReturn(
                new GatewayInfo(null, null, Uri.parse("tel:555-0001")));
        when(heldCall.getGatewayInfo()).thenReturn(
                new GatewayInfo(null, null, Uri.parse("tel:555-0002")));
        addCallCapability(parentCall, Connection.CAPABILITY_MERGE_CONFERENCE);
        addCallCapability(parentCall, Connection.CAPABILITY_SWAP_CONFERENCE);
        removeCallCapability(parentCall, Connection.CAPABILITY_CONFERENCE_HAS_NO_CHILDREN);

        Integer foregroundCallId = foregroundCall.getId();
        when(parentCall.getGenericConferenceActiveChildCallId()).thenReturn(foregroundCallId);
        when(parentCall.isConference()).thenReturn(true);
        List<Integer> childrenIds = Arrays.asList(foregroundCall.getId(),
                heldCall.getId());
        when(parentCall.getChildrenIds()).thenReturn(childrenIds);
        //Add links from child calls to parent
        Integer parentId = parentCall.getId();
        when(foregroundCall.getParentId()).thenReturn(parentId);
        when(heldCall.getParentId()).thenReturn(parentId);
        when(parentCall.hasProperty(Call.Details.PROPERTY_GENERIC_CONFERENCE)).thenReturn(true);

        clearInvocations(mMockBluetoothHeadset);
        mBluetoothInCallService.listCurrentCalls();

        verify(mMockBluetoothHeadset).clccResponse(eq(1), eq(0), eq(CALL_STATE_ACTIVE), eq(0),
                eq(false), eq("5550001"), eq(PhoneNumberUtils.TOA_Unknown));
        verify(mMockBluetoothHeadset).clccResponse(eq(2), eq(1), eq(CALL_STATE_HELD), eq(0),
                eq(false), eq("5550002"), eq(PhoneNumberUtils.TOA_Unknown));
        verify(mMockBluetoothHeadset).clccResponse(0, 0, 0, 0, false, null, 0);
    }

    @Test
    public void testListCurrentCallsCdmaConference() throws Exception {
        // BluetoothCall is in a true CDMA conference
        ArrayList<BluetoothCall> calls = new ArrayList<>();
        BluetoothCall parentCall = createActiveCall(UUID.randomUUID());
        final BluetoothCall confCall1 = getMockCall(UUID.randomUUID());
        final BluetoothCall confCall2 = createHeldCall(UUID.randomUUID());
        when(parentCall.getHandle()).thenReturn(Uri.parse("tel:555-0000"));
        when(confCall1.getHandle()).thenReturn(Uri.parse("tel:555-0001"));
        when(confCall2.getHandle()).thenReturn(Uri.parse("tel:555-0002"));
        calls.add(parentCall);
        calls.add(confCall1);
        calls.add(confCall2);
        mBluetoothInCallService.onCallAdded(parentCall);
        mBluetoothInCallService.onCallAdded(confCall1);
        mBluetoothInCallService.onCallAdded(confCall2);

        when(mMockCallInfo.getBluetoothCalls()).thenReturn(calls);
        when(confCall1.getState()).thenReturn(Call.STATE_ACTIVE);
        when(confCall2.getState()).thenReturn(Call.STATE_ACTIVE);
        when(confCall1.isIncoming()).thenReturn(false);
        when(confCall2.isIncoming()).thenReturn(true);
        when(confCall1.getGatewayInfo()).thenReturn(
                new GatewayInfo(null, null, Uri.parse("tel:555-0000")));
        when(confCall2.getGatewayInfo()).thenReturn(
                new GatewayInfo(null, null, Uri.parse("tel:555-0001")));
        removeCallCapability(parentCall, Connection.CAPABILITY_MERGE_CONFERENCE);
        removeCallCapability(parentCall, Connection.CAPABILITY_CONFERENCE_HAS_NO_CHILDREN);
        when(parentCall.wasConferencePreviouslyMerged()).thenReturn(true);
        //when(parentCall.getConferenceLevelActiveCall()).thenReturn(confCall1);
        when(parentCall.isConference()).thenReturn(true);
        List<Integer> childrenIds = Arrays.asList(confCall1.getId(),
                confCall2.getId());
        when(parentCall.getChildrenIds()).thenReturn(childrenIds);
        //Add links from child calls to parent
        Integer parentId = parentCall.getId();
        when(confCall1.getParentId()).thenReturn(parentId);
        when(confCall2.getParentId()).thenReturn(parentId);
        when(parentCall.hasProperty(Call.Details.PROPERTY_GENERIC_CONFERENCE)).thenReturn(true);

        clearInvocations(mMockBluetoothHeadset);
        mBluetoothInCallService.listCurrentCalls();

        verify(mMockBluetoothHeadset).clccResponse(eq(1), eq(0), eq(CALL_STATE_ACTIVE), eq(0),
                eq(true), eq("5550000"), eq(PhoneNumberUtils.TOA_Unknown));
        verify(mMockBluetoothHeadset).clccResponse(eq(2), eq(1), eq(CALL_STATE_ACTIVE), eq(0),
                eq(true), eq("5550001"), eq(PhoneNumberUtils.TOA_Unknown));
        verify(mMockBluetoothHeadset).clccResponse(0, 0, 0, 0, false, null, 0);
    }

    @Test
    public void testWaitingCallClccResponse() throws Exception {
        ArrayList<BluetoothCall> calls = new ArrayList<>();
        when(mMockCallInfo.getBluetoothCalls()).thenReturn(calls);
        // This test does not define a value for getForegroundCall(), so this ringing
        // BluetoothCall will be treated as if it is a waiting BluetoothCall
        // when listCurrentCalls() is invoked.
        BluetoothCall waitingCall = createRingingCall(UUID.randomUUID());
        calls.add(waitingCall);
        mBluetoothInCallService.onCallAdded(waitingCall);

        when(waitingCall.isIncoming()).thenReturn(true);
        when(waitingCall.getGatewayInfo()).thenReturn(
                new GatewayInfo(null, null, Uri.parse("tel:555-0000")));
        when(waitingCall.getState()).thenReturn(Call.STATE_RINGING);
        when(waitingCall.isConference()).thenReturn(false);
        when(waitingCall.getHandle()).thenReturn(Uri.parse("tel:555-0000"));

        clearInvocations(mMockBluetoothHeadset);
        mBluetoothInCallService.listCurrentCalls();
        verify(mMockBluetoothHeadset).clccResponse(1, 1, CALL_STATE_WAITING, 0, false,
                "5550000", PhoneNumberUtils.TOA_Unknown);
        verify(mMockBluetoothHeadset).clccResponse(0, 0, 0, 0, false, null, 0);
        verify(mMockBluetoothHeadset, times(2)).clccResponse(anyInt(),
                anyInt(), anyInt(), anyInt(), anyBoolean(), nullable(String.class), anyInt());
    }

    @Test
    public void testNewCallClccResponse() throws Exception {
        ArrayList<BluetoothCall> calls = new ArrayList<>();
        when(mMockCallInfo.getBluetoothCalls()).thenReturn(calls);
        BluetoothCall newCall = createForegroundCall(UUID.randomUUID());
        calls.add(newCall);
        mBluetoothInCallService.onCallAdded(newCall);

        when(newCall.getState()).thenReturn(Call.STATE_NEW);
        when(newCall.isConference()).thenReturn(false);

        clearInvocations(mMockBluetoothHeadset);
        mBluetoothInCallService.listCurrentCalls();
        verify(mMockBluetoothHeadset).clccResponse(0, 0, 0, 0, false, null, 0);
        verify(mMockBluetoothHeadset, times(1)).clccResponse(anyInt(),
                anyInt(), anyInt(), anyInt(), anyBoolean(), nullable(String.class), anyInt());
    }

    @Test
    public void testListCurrentCallsCallHandleChanged() throws Exception {
        mBluetoothInCallService.mTelephonyManager = mMockTelephonyManager;
        when(mMockTelephonyManager.getNetworkCountryIso()).thenReturn("");

        ArrayList<BluetoothCall> calls = new ArrayList<>();
        when(mMockCallInfo.getBluetoothCalls()).thenReturn(calls);
        BluetoothCall activeCall = createForegroundCall(UUID.randomUUID());
        calls.add(activeCall);
        mBluetoothInCallService.onCallAdded(activeCall);

        when(activeCall.getState()).thenReturn(Call.STATE_ACTIVE);
        when(activeCall.isIncoming()).thenReturn(true);
        when(activeCall.isConference()).thenReturn(false);
        when(activeCall.getHandle()).thenReturn(Uri.parse("tel:2135550000"));
        Log.w(TAG, "call handle" + Uri.parse("tel:2135550000"));
        when(activeCall.getGatewayInfo())
                .thenReturn(new GatewayInfo(null, null, Uri.parse("tel:2135550000")));

        clearInvocations(mMockBluetoothHeadset);
        mBluetoothInCallService.listCurrentCalls();
        verify(mMockBluetoothHeadset)
                .clccResponse(
                        1,
                        1,
                        CALL_STATE_ACTIVE,
                        0,
                        false,
                        "2135550000",
                        PhoneNumberUtils.TOA_Unknown);

        // call handle changed
        when(activeCall.getHandle()).thenReturn(Uri.parse("tel:213-555-0000"));
        clearInvocations(mMockBluetoothHeadset);
        Log.w(TAG, "call handle" + Uri.parse("tel:213-555-0000"));
        mBluetoothInCallService.listCurrentCalls();
        verify(mMockBluetoothHeadset)
                .clccResponse(
                        1,
                        1,
                        CALL_STATE_ACTIVE,
                        0,
                        false,
                        "2135550000",
                        PhoneNumberUtils.TOA_Unknown);
    }

    @Test
    public void testRingingCallClccResponse() throws Exception {
        ArrayList<BluetoothCall> calls = new ArrayList<>();
        when(mMockCallInfo.getBluetoothCalls()).thenReturn(calls);
        BluetoothCall ringingCall = createForegroundCall(UUID.randomUUID());
        calls.add(ringingCall);
        mBluetoothInCallService.onCallAdded(ringingCall);

        when(ringingCall.getState()).thenReturn(Call.STATE_RINGING);
        when(ringingCall.isIncoming()).thenReturn(true);
        when(ringingCall.isConference()).thenReturn(false);
        when(ringingCall.getHandle()).thenReturn(Uri.parse("tel:555-0000"));
        when(ringingCall.getGatewayInfo()).thenReturn(
                new GatewayInfo(null, null, Uri.parse("tel:555-0000")));

        clearInvocations(mMockBluetoothHeadset);
        mBluetoothInCallService.listCurrentCalls();
        verify(mMockBluetoothHeadset).clccResponse(1, 1, CALL_STATE_INCOMING, 0, false,
                "5550000", PhoneNumberUtils.TOA_Unknown);
        verify(mMockBluetoothHeadset).clccResponse(0, 0, 0, 0, false, null, 0);
        verify(mMockBluetoothHeadset, times(2)).clccResponse(anyInt(),
                anyInt(), anyInt(), anyInt(), anyBoolean(), nullable(String.class), anyInt());
    }

    @Test
    public void testCallClccCache() throws Exception {
        ArrayList<BluetoothCall> calls = new ArrayList<>();
        when(mMockCallInfo.getBluetoothCalls()).thenReturn(calls);
        BluetoothCall ringingCall = createForegroundCall(UUID.randomUUID());
        calls.add(ringingCall);
        mBluetoothInCallService.onCallAdded(ringingCall);

        when(ringingCall.getState()).thenReturn(Call.STATE_RINGING);
        when(ringingCall.isIncoming()).thenReturn(true);
        when(ringingCall.isConference()).thenReturn(false);
        when(ringingCall.getHandle()).thenReturn(Uri.parse("tel:5550000"));
        when(ringingCall.getGatewayInfo()).thenReturn(
                new GatewayInfo(null, null, Uri.parse("tel:5550000")));

        clearInvocations(mMockBluetoothHeadset);
        mBluetoothInCallService.listCurrentCalls();
        verify(mMockBluetoothHeadset).clccResponse(1, 1, CALL_STATE_INCOMING, 0, false,
                "5550000", PhoneNumberUtils.TOA_Unknown);

        // Test Caching of old BluetoothCall indices in clcc
        when(ringingCall.getState()).thenReturn(Call.STATE_ACTIVE);
        BluetoothCall newHoldingCall = createHeldCall(UUID.randomUUID());
        calls.add(0, newHoldingCall);
        mBluetoothInCallService.onCallAdded(newHoldingCall);

        when(newHoldingCall.getState()).thenReturn(Call.STATE_HOLDING);
        when(newHoldingCall.isIncoming()).thenReturn(true);
        when(newHoldingCall.isConference()).thenReturn(false);
        when(newHoldingCall.getHandle()).thenReturn(Uri.parse("tel:555-0001"));
        when(newHoldingCall.getGatewayInfo()).thenReturn(
                new GatewayInfo(null, null, Uri.parse("tel:555-0001")));

        mBluetoothInCallService.listCurrentCalls();
        verify(mMockBluetoothHeadset).clccResponse(1, 1, CALL_STATE_ACTIVE, 0, false,
                "5550000", PhoneNumberUtils.TOA_Unknown);
        verify(mMockBluetoothHeadset).clccResponse(2, 1, CALL_STATE_HELD, 0, false,
                "5550001", PhoneNumberUtils.TOA_Unknown);
        verify(mMockBluetoothHeadset, times(2)).clccResponse(0, 0, 0, 0, false, null, 0);
    }

    @Test
    public void testAlertingCallClccResponse() throws Exception {
        ArrayList<BluetoothCall> calls = new ArrayList<>();
        when(mMockCallInfo.getBluetoothCalls()).thenReturn(calls);
        BluetoothCall dialingCall = createForegroundCall(UUID.randomUUID());
        calls.add(dialingCall);
        mBluetoothInCallService.onCallAdded(dialingCall);

        when(dialingCall.getState()).thenReturn(Call.STATE_DIALING);
        when(dialingCall.isIncoming()).thenReturn(false);
        when(dialingCall.isConference()).thenReturn(false);
        when(dialingCall.getHandle()).thenReturn(Uri.parse("tel:555-0000"));
        when(dialingCall.getGatewayInfo()).thenReturn(
                new GatewayInfo(null, null, Uri.parse("tel:555-0000")));

        clearInvocations(mMockBluetoothHeadset);
        mBluetoothInCallService.listCurrentCalls();
        verify(mMockBluetoothHeadset).clccResponse(1, 0, CALL_STATE_ALERTING, 0, false,
                "5550000", PhoneNumberUtils.TOA_Unknown);
        verify(mMockBluetoothHeadset).clccResponse(0, 0, 0, 0, false, null, 0);
        verify(mMockBluetoothHeadset, times(2)).clccResponse(anyInt(),
                anyInt(), anyInt(), anyInt(), anyBoolean(), nullable(String.class), anyInt());
    }

    @Test
    public void testHoldingCallClccResponse() throws Exception {
        ArrayList<BluetoothCall> calls = new ArrayList<>();
        when(mMockCallInfo.getBluetoothCalls()).thenReturn(calls);
        BluetoothCall dialingCall = createForegroundCall(UUID.randomUUID());
        calls.add(dialingCall);
        mBluetoothInCallService.onCallAdded(dialingCall);

        when(dialingCall.getState()).thenReturn(Call.STATE_DIALING);
        when(dialingCall.isIncoming()).thenReturn(false);
        when(dialingCall.isConference()).thenReturn(false);
        when(dialingCall.getHandle()).thenReturn(Uri.parse("tel:555-0000"));
        when(dialingCall.getGatewayInfo()).thenReturn(
                new GatewayInfo(null, null, Uri.parse("tel:555-0000")));
        BluetoothCall holdingCall = createHeldCall(UUID.randomUUID());
        calls.add(holdingCall);
        mBluetoothInCallService.onCallAdded(holdingCall);

        when(holdingCall.getState()).thenReturn(Call.STATE_HOLDING);
        when(holdingCall.isIncoming()).thenReturn(true);
        when(holdingCall.isConference()).thenReturn(false);
        when(holdingCall.getHandle()).thenReturn(Uri.parse("tel:555-0001"));
        when(holdingCall.getGatewayInfo()).thenReturn(
                new GatewayInfo(null, null, Uri.parse("tel:555-0001")));

        clearInvocations(mMockBluetoothHeadset);
        mBluetoothInCallService.listCurrentCalls();
        verify(mMockBluetoothHeadset).clccResponse(1, 0, CALL_STATE_ALERTING, 0, false,
                "5550000", PhoneNumberUtils.TOA_Unknown);
        verify(mMockBluetoothHeadset).clccResponse(2, 1, CALL_STATE_HELD, 0, false,
                "5550001", PhoneNumberUtils.TOA_Unknown);
        verify(mMockBluetoothHeadset).clccResponse(0, 0, 0, 0, false, null, 0);
        verify(mMockBluetoothHeadset, times(3)).clccResponse(anyInt(),
                anyInt(), anyInt(), anyInt(), anyBoolean(), nullable(String.class), anyInt());
    }

    @Test
    public void testListCurrentCallsImsConference() throws Exception {
        ArrayList<BluetoothCall> calls = new ArrayList<>();
        BluetoothCall parentCall = createActiveCall(UUID.randomUUID());

        addCallCapability(parentCall, Connection.CAPABILITY_CONFERENCE_HAS_NO_CHILDREN);
        when(parentCall.isConference()).thenReturn(true);
        when(parentCall.getState()).thenReturn(Call.STATE_ACTIVE);
        when(parentCall.isIncoming()).thenReturn(true);
        when(parentCall.getHandle()).thenReturn(Uri.parse("tel:555-0000"));
        when(mMockCallInfo.getBluetoothCalls()).thenReturn(calls);

        calls.add(parentCall);
        mBluetoothInCallService.onCallAdded(parentCall);

        clearInvocations(mMockBluetoothHeadset);
        mBluetoothInCallService.listCurrentCalls();

        verify(mMockBluetoothHeadset).clccResponse(eq(1), eq(1), eq(CALL_STATE_ACTIVE), eq(0),
                eq(true), eq("5550000"), eq(129));
        verify(mMockBluetoothHeadset).clccResponse(0, 0, 0, 0, false, null, 0);
    }

    @Test
    public void testListCurrentCallsHeldImsCepConference() throws Exception {
        ArrayList<BluetoothCall> calls = new ArrayList<>();
        BluetoothCall parentCall = createHeldCall(UUID.randomUUID());
        BluetoothCall childCall1 = createActiveCall(UUID.randomUUID());
        BluetoothCall childCall2 = createActiveCall(UUID.randomUUID());
        when(parentCall.getHandle()).thenReturn(Uri.parse("tel:555-0000"));
        when(childCall1.getHandle()).thenReturn(Uri.parse("tel:555-0001"));
        when(childCall2.getHandle()).thenReturn(Uri.parse("tel:555-0002"));

        calls.add(parentCall);
        calls.add(childCall1);
        calls.add(childCall2);
        mBluetoothInCallService.onCallAdded(parentCall);
        mBluetoothInCallService.onCallAdded(childCall1);
        mBluetoothInCallService.onCallAdded(childCall2);

        addCallCapability(parentCall, Connection.CAPABILITY_MANAGE_CONFERENCE);
        Integer parentId = parentCall.getId();
        when(childCall1.getParentId()).thenReturn(parentId);
        when(childCall2.getParentId()).thenReturn(parentId);
        List<Integer> childrenIds = Arrays.asList(childCall1.getId(),
                childCall2.getId());
        when(parentCall.getChildrenIds()).thenReturn(childrenIds);

        when(parentCall.isConference()).thenReturn(true);
        when(parentCall.getState()).thenReturn(Call.STATE_HOLDING);
        when(childCall1.getState()).thenReturn(Call.STATE_ACTIVE);
        when(childCall2.getState()).thenReturn(Call.STATE_ACTIVE);
        when(parentCall.hasProperty(Call.Details.PROPERTY_GENERIC_CONFERENCE)).thenReturn(true);
        when(parentCall.isIncoming()).thenReturn(true);
        when(mMockCallInfo.getBluetoothCalls()).thenReturn(calls);

        clearInvocations(mMockBluetoothHeadset);
        mBluetoothInCallService.listCurrentCalls();

        verify(mMockBluetoothHeadset).clccResponse(eq(1), eq(0), eq(CALL_STATE_HELD), eq(0),
                eq(true), eq("5550001"), eq(PhoneNumberUtils.TOA_Unknown));
        verify(mMockBluetoothHeadset).clccResponse(eq(2), eq(0), eq(CALL_STATE_HELD), eq(0),
                eq(true), eq("5550002"), eq(PhoneNumberUtils.TOA_Unknown));
        verify(mMockBluetoothHeadset).clccResponse(0, 0, 0, 0, false, null, 0);
    }

    @Test
    public void testListCurrentCallsConferenceGetChildrenIsEmpty() throws Exception {
        ArrayList<BluetoothCall> calls = new ArrayList<>();
        BluetoothCall conferenceCall = createActiveCall(UUID.randomUUID());
        when(conferenceCall.getHandle()).thenReturn(Uri.parse("tel:555-1234"));

        addCallCapability(conferenceCall, Connection.CAPABILITY_MANAGE_CONFERENCE);
        when(conferenceCall.isConference()).thenReturn(true);
        when(conferenceCall.getState()).thenReturn(Call.STATE_ACTIVE);
        when(conferenceCall.hasProperty(Call.Details.PROPERTY_GENERIC_CONFERENCE)).thenReturn(true);
        when(conferenceCall.can(Connection.CAPABILITY_CONFERENCE_HAS_NO_CHILDREN))
                .thenReturn(false);
        when(conferenceCall.isIncoming()).thenReturn(true);
        when(mMockCallInfo.getBluetoothCalls()).thenReturn(calls);

        calls.add(conferenceCall);
        mBluetoothInCallService.onCallAdded(conferenceCall);

        clearInvocations(mMockBluetoothHeadset);
        mBluetoothInCallService.listCurrentCalls();
        verify(mMockBluetoothHeadset).clccResponse(
                eq(1), eq(1), eq(0), eq(0), eq(true), eq("5551234"), eq(129));
    }

    @Test
    public void testListCurrentCallsConferenceEmptyChildrenInference() throws Exception {
        mBluetoothInCallService.mTelephonyManager = mMockTelephonyManager;
        when(mMockTelephonyManager.getNetworkCountryIso()).thenReturn("");

        ArrayList<BluetoothCall> calls = new ArrayList<>();
        when(mMockCallInfo.getBluetoothCalls()).thenReturn(calls);

        // active call is added
        BluetoothCall activeCall = createActiveCall(UUID.randomUUID());
        calls.add(activeCall);
        mBluetoothInCallService.onCallAdded(activeCall);

        when(activeCall.getState()).thenReturn(Call.STATE_ACTIVE);
        when(activeCall.isIncoming()).thenReturn(false);
        when(activeCall.isConference()).thenReturn(false);
        when(activeCall.getHandle()).thenReturn(Uri.parse("tel:555-0001"));
        when(activeCall.getGatewayInfo())
                .thenReturn(new GatewayInfo(null, null, Uri.parse("tel:555-0001")));

        // holding call is added
        BluetoothCall holdingCall = createHeldCall(UUID.randomUUID());
        calls.add(holdingCall);
        mBluetoothInCallService.onCallAdded(holdingCall);

        when(holdingCall.getState()).thenReturn(Call.STATE_HOLDING);
        when(holdingCall.isIncoming()).thenReturn(true);
        when(holdingCall.isConference()).thenReturn(false);
        when(holdingCall.getHandle()).thenReturn(Uri.parse("tel:555-0002"));
        when(holdingCall.getGatewayInfo())
                .thenReturn(new GatewayInfo(null, null, Uri.parse("tel:555-0002")));

        // needs to have at least one CLCC response before merge to enable call inference
        clearInvocations(mMockBluetoothHeadset);
        mBluetoothInCallService.listCurrentCalls();
        verify(mMockBluetoothHeadset)
                .clccResponse(
                        1, 0, CALL_STATE_ACTIVE, 0, false, "5550001", PhoneNumberUtils.TOA_Unknown);
        verify(mMockBluetoothHeadset)
                .clccResponse(
                        2, 1, CALL_STATE_HELD, 0, false, "5550002", PhoneNumberUtils.TOA_Unknown);
        calls.clear();

        // calls merged for conference call
        DisconnectCause cause = new DisconnectCause(DisconnectCause.OTHER);
        when(activeCall.getDisconnectCause()).thenReturn(cause);
        when(holdingCall.getDisconnectCause()).thenReturn(cause);
        mBluetoothInCallService.onCallRemoved(activeCall, true);
        mBluetoothInCallService.onCallRemoved(holdingCall, true);

        BluetoothCall conferenceCall = createActiveCall(UUID.randomUUID());
        addCallCapability(conferenceCall, Connection.CAPABILITY_MANAGE_CONFERENCE);

        when(conferenceCall.getHandle()).thenReturn(Uri.parse("tel:555-1234"));
        when(conferenceCall.isConference()).thenReturn(true);
        when(conferenceCall.getState()).thenReturn(Call.STATE_ACTIVE);
        when(conferenceCall.hasProperty(Call.Details.PROPERTY_GENERIC_CONFERENCE)).thenReturn(true);
        when(conferenceCall.can(Connection.CAPABILITY_CONFERENCE_HAS_NO_CHILDREN))
                .thenReturn(false);
        when(conferenceCall.isIncoming()).thenReturn(true);
        when(mMockCallInfo.getBluetoothCalls()).thenReturn(calls);

        // parent call arrived, but children have not, then do inference on children
        calls.add(conferenceCall);
        Assert.assertEquals(calls.size(), 1);
        mBluetoothInCallService.onCallAdded(conferenceCall);

        clearInvocations(mMockBluetoothHeadset);
        mBluetoothInCallService.listCurrentCalls();
        verify(mMockBluetoothHeadset)
                .clccResponse(
                        1, 0, CALL_STATE_ACTIVE, 0, true, "5550001", PhoneNumberUtils.TOA_Unknown);
        verify(mMockBluetoothHeadset)
                .clccResponse(
                        2, 1, CALL_STATE_ACTIVE, 0, true, "5550002", PhoneNumberUtils.TOA_Unknown);

        // real children arrive, no change on CLCC response
        calls.add(activeCall);
        mBluetoothInCallService.onCallAdded(activeCall);
        when(activeCall.isConference()).thenReturn(true);
        calls.add(holdingCall);
        mBluetoothInCallService.onCallAdded(holdingCall);
        when(holdingCall.getState()).thenReturn(Call.STATE_ACTIVE);
        when(holdingCall.isConference()).thenReturn(true);
        when(conferenceCall.getChildrenIds()).thenReturn(new ArrayList<>(Arrays.asList(1, 2)));

        clearInvocations(mMockBluetoothHeadset);
        mBluetoothInCallService.listCurrentCalls();
        verify(mMockBluetoothHeadset)
                .clccResponse(
                        1, 0, CALL_STATE_ACTIVE, 0, true, "5550001", PhoneNumberUtils.TOA_Unknown);
        verify(mMockBluetoothHeadset)
                .clccResponse(
                        2, 1, CALL_STATE_ACTIVE, 0, true, "5550002", PhoneNumberUtils.TOA_Unknown);

        // when call is terminated, children first removed, then parent
        cause = new DisconnectCause(DisconnectCause.LOCAL);
        when(activeCall.getDisconnectCause()).thenReturn(cause);
        when(holdingCall.getDisconnectCause()).thenReturn(cause);
        mBluetoothInCallService.onCallRemoved(activeCall, true);
        mBluetoothInCallService.onCallRemoved(holdingCall, true);
        calls.remove(activeCall);
        calls.remove(holdingCall);
        Assert.assertEquals(calls.size(), 1);

        clearInvocations(mMockBluetoothHeadset);
        mBluetoothInCallService.listCurrentCalls();
        verify(mMockBluetoothHeadset).clccResponse(0, 0, 0, 0, false, null, 0);
        verify(mMockBluetoothHeadset, times(1))
                .clccResponse(
                        anyInt(),
                        anyInt(),
                        anyInt(),
                        anyInt(),
                        anyBoolean(),
                        nullable(String.class),
                        anyInt());

        // when parent is removed
        when(conferenceCall.getDisconnectCause()).thenReturn(cause);
        calls.remove(conferenceCall);
        mBluetoothInCallService.onCallRemoved(conferenceCall, true);

        clearInvocations(mMockBluetoothHeadset);
        mBluetoothInCallService.listCurrentCalls();
        verify(mMockBluetoothHeadset).clccResponse(0, 0, 0, 0, false, null, 0);
        verify(mMockBluetoothHeadset, times(1))
                .clccResponse(
                        anyInt(),
                        anyInt(),
                        anyInt(),
                        anyInt(),
                        anyBoolean(),
                        nullable(String.class),
                        anyInt());
    }

    @Test
    public void testQueryPhoneState() throws Exception {
        BluetoothCall ringingCall = createRingingCall(UUID.randomUUID());
        when(ringingCall.getHandle()).thenReturn(Uri.parse("tel:5550000"));

        clearInvocations(mMockBluetoothHeadset);
        mBluetoothInCallService.queryPhoneState();

        verify(mMockBluetoothHeadset).phoneStateChanged(eq(0), eq(0), eq(CALL_STATE_INCOMING),
                eq("5550000"), eq(PhoneNumberUtils.TOA_Unknown), nullable(String.class));
    }

    @Test
    public void testCDMAConferenceQueryState() throws Exception {
        BluetoothCall parentConfCall = createActiveCall(UUID.randomUUID());
        final BluetoothCall confCall1 = getMockCall(UUID.randomUUID());
        final BluetoothCall confCall2 = getMockCall(UUID.randomUUID());
        mBluetoothInCallService.onCallAdded(confCall1);
        mBluetoothInCallService.onCallAdded(confCall2);
        when(parentConfCall.getHandle()).thenReturn(Uri.parse("tel:555-0000"));
        addCallCapability(parentConfCall, Connection.CAPABILITY_SWAP_CONFERENCE);
        removeCallCapability(parentConfCall, Connection.CAPABILITY_CONFERENCE_HAS_NO_CHILDREN);
        when(parentConfCall.wasConferencePreviouslyMerged()).thenReturn(true);
        when(parentConfCall.isConference()).thenReturn(true);
        List<Integer> childrenIds = Arrays.asList(confCall1.getId(),
                confCall2.getId());
        when(parentConfCall.getChildrenIds()).thenReturn(childrenIds);

        clearInvocations(mMockBluetoothHeadset);
        mBluetoothInCallService.queryPhoneState();
        verify(mMockBluetoothHeadset).phoneStateChanged(eq(1), eq(0), eq(CALL_STATE_IDLE),
                eq(""), eq(128), nullable(String.class));
    }

    @Test
    public void testProcessChldTypeReleaseHeldRinging() throws Exception {
        BluetoothCall ringingCall = createRingingCall(UUID.randomUUID());
        Log.i("BluetoothInCallService", "asdf start " + Integer.toString(ringingCall.hashCode()));

        boolean didProcess = mBluetoothInCallService.processChld(CHLD_TYPE_RELEASEHELD);

        verify(ringingCall).reject(eq(false), nullable(String.class));
        Assert.assertTrue(didProcess);
    }

    @Test
    public void testProcessChldTypeReleaseHeldHold() throws Exception {
        BluetoothCall onHoldCall = createHeldCall(UUID.randomUUID());
        boolean didProcess = mBluetoothInCallService.processChld(CHLD_TYPE_RELEASEHELD);

        verify(onHoldCall).disconnect();
        Assert.assertTrue(didProcess);
    }

    @Test
    public void testProcessChldReleaseActiveRinging() throws Exception {
        BluetoothCall activeCall = createActiveCall(UUID.randomUUID());
        BluetoothCall ringingCall = createRingingCall(UUID.randomUUID());

        boolean didProcess = mBluetoothInCallService.processChld(
                CHLD_TYPE_RELEASEACTIVE_ACCEPTHELD);

        verify(activeCall).disconnect();
        verify(ringingCall).answer(any(int.class));
        Assert.assertTrue(didProcess);
    }

    @Test
    public void testProcessChldReleaseActiveHold() throws Exception {
        BluetoothCall activeCall = createActiveCall(UUID.randomUUID());
        BluetoothCall heldCall = createHeldCall(UUID.randomUUID());

        boolean didProcess = mBluetoothInCallService.processChld(
                CHLD_TYPE_RELEASEACTIVE_ACCEPTHELD);

        verify(activeCall).disconnect();
        // BluetoothCall unhold will occur as part of CallsManager auto-unholding
        // the background BluetoothCall on its own.
        Assert.assertTrue(didProcess);
    }

    @Test
    public void testProcessChldHoldActiveRinging() throws Exception {
        BluetoothCall ringingCall = createRingingCall(UUID.randomUUID());

        boolean didProcess = mBluetoothInCallService.processChld(
                CHLD_TYPE_HOLDACTIVE_ACCEPTHELD);

        verify(ringingCall).answer(any(int.class));
        Assert.assertTrue(didProcess);
    }

    @Test
    public void testProcessChldHoldActiveUnhold() throws Exception {
        BluetoothCall heldCall = createHeldCall(UUID.randomUUID());

        boolean didProcess = mBluetoothInCallService.processChld(
                CHLD_TYPE_HOLDACTIVE_ACCEPTHELD);

        verify(heldCall).unhold();
        Assert.assertTrue(didProcess);
    }

    @Test
    public void testProcessChldHoldActiveHold() throws Exception {
        BluetoothCall activeCall = createActiveCall(UUID.randomUUID());
        addCallCapability(activeCall, Connection.CAPABILITY_HOLD);

        boolean didProcess = mBluetoothInCallService.processChld(
                CHLD_TYPE_HOLDACTIVE_ACCEPTHELD);

        verify(activeCall).hold();
        Assert.assertTrue(didProcess);
    }

    @Test
    public void testProcessChldAddHeldToConfHolding() throws Exception {
        BluetoothCall activeCall = createActiveCall(UUID.randomUUID());
        addCallCapability(activeCall, Connection.CAPABILITY_MERGE_CONFERENCE);

        boolean didProcess = mBluetoothInCallService.processChld(CHLD_TYPE_ADDHELDTOCONF);

        verify(activeCall).mergeConference();
        Assert.assertTrue(didProcess);
    }

    @Test
    public void testProcessChldAddHeldToConf() throws Exception {
        BluetoothCall activeCall = createActiveCall(UUID.randomUUID());
        removeCallCapability(activeCall, Connection.CAPABILITY_MERGE_CONFERENCE);
        BluetoothCall conferenceableCall = getMockCall(UUID.randomUUID());
        ArrayList<Integer> conferenceableCalls = new ArrayList<>();
        conferenceableCalls.add(conferenceableCall.getId());
        mBluetoothInCallService.onCallAdded(conferenceableCall);

        when(activeCall.getConferenceableCalls()).thenReturn(conferenceableCalls);

        boolean didProcess = mBluetoothInCallService.processChld(CHLD_TYPE_ADDHELDTOCONF);

        verify(activeCall).conference(conferenceableCall);
        Assert.assertTrue(didProcess);
    }

    @Test
    public void testProcessChldHoldActiveSwapConference() throws Exception {
        // Create an active CDMA BluetoothCall with a BluetoothCall on hold
        // and simulate a swapConference().
        BluetoothCall parentCall = createActiveCall(UUID.randomUUID());
        final BluetoothCall foregroundCall = getMockCall(UUID.randomUUID());
        when(foregroundCall.getHandle()).thenReturn(Uri.parse("tel:555-0001"));
        final BluetoothCall heldCall = createHeldCall(UUID.randomUUID());
        when(heldCall.getHandle()).thenReturn(Uri.parse("tel:555-0002"));
        addCallCapability(parentCall, Connection.CAPABILITY_SWAP_CONFERENCE);
        removeCallCapability(parentCall, Connection.CAPABILITY_CONFERENCE_HAS_NO_CHILDREN);
        when(parentCall.isConference()).thenReturn(true);
        when(parentCall.wasConferencePreviouslyMerged()).thenReturn(false);
        when(heldCall.getHandle()).thenReturn(Uri.parse("tel:555-0000"));
        List<Integer> childrenIds = Arrays.asList(foregroundCall.getId(),
                heldCall.getId());
        when(parentCall.getChildrenIds()).thenReturn(childrenIds);

        clearInvocations(mMockBluetoothHeadset);
        boolean didProcess = mBluetoothInCallService.processChld(
                CHLD_TYPE_HOLDACTIVE_ACCEPTHELD);

        verify(parentCall).swapConference();
        verify(mMockBluetoothHeadset).phoneStateChanged(eq(1), eq(1), eq(CALL_STATE_IDLE), eq(""),
                eq(128), nullable(String.class));
        Assert.assertTrue(didProcess);
    }

    // Testing the CallsManager Listener Functionality on Bluetooth
    @Test
    public void testOnCallAddedRinging() throws Exception {
        BluetoothCall ringingCall = createRingingCall(UUID.randomUUID());
        when(ringingCall.getHandle()).thenReturn(Uri.parse("tel:555000"));

        mBluetoothInCallService.onCallAdded(ringingCall);

        verify(mMockBluetoothHeadset).phoneStateChanged(eq(0), eq(0), eq(CALL_STATE_INCOMING),
                eq("555000"), eq(PhoneNumberUtils.TOA_Unknown), nullable(String.class));
    }

    @Test
    public void testSilentRingingCallState() throws Exception {
        BluetoothCall ringingCall = createRingingCall(UUID.randomUUID());
        when(ringingCall.isSilentRingingRequested()).thenReturn(true);
        when(ringingCall.getHandle()).thenReturn(Uri.parse("tel:555000"));

        mBluetoothInCallService.onCallAdded(ringingCall);

        verify(mMockBluetoothHeadset, never()).phoneStateChanged(anyInt(), anyInt(), anyInt(),
                anyString(), anyInt(), nullable(String.class));
    }

    @Test
    public void testOnCallAddedCdmaActiveHold() throws Exception {
        // BluetoothCall has been put into a CDMA "conference" with one BluetoothCall on hold.
        BluetoothCall parentCall = createActiveCall(UUID.randomUUID());
        final BluetoothCall foregroundCall = getMockCall(UUID.randomUUID());
        final BluetoothCall heldCall = createHeldCall(UUID.randomUUID());
        when(foregroundCall.getHandle()).thenReturn(Uri.parse("tel:555-0001"));
        when(heldCall.getHandle()).thenReturn(Uri.parse("tel:555-0002"));
        addCallCapability(parentCall, Connection.CAPABILITY_MERGE_CONFERENCE);
        removeCallCapability(parentCall, Connection.CAPABILITY_CONFERENCE_HAS_NO_CHILDREN);
        when(parentCall.isConference()).thenReturn(true);
        List<Integer> childrenIds = Arrays.asList(foregroundCall.getId(),
                heldCall.getId());
        when(parentCall.getChildrenIds()).thenReturn(childrenIds);

        mBluetoothInCallService.onCallAdded(parentCall);

        verify(mMockBluetoothHeadset).phoneStateChanged(eq(1), eq(1), eq(CALL_STATE_IDLE),
                eq(""), eq(128), nullable(String.class));
    }

    @Test
    public void testOnCallRemoved() throws Exception {
        BluetoothCall activeCall = createActiveCall(UUID.randomUUID());
        mBluetoothInCallService.onCallAdded(activeCall);
        doReturn(null).when(mMockCallInfo).getActiveCall();
        when(activeCall.getHandle()).thenReturn(Uri.parse("tel:555-0001"));

        mBluetoothInCallService.onCallRemoved(activeCall, true /* forceRemoveCallback */);

        verify(mMockBluetoothHeadset).phoneStateChanged(eq(0), eq(0), eq(CALL_STATE_IDLE),
                eq(""), eq(128), nullable(String.class));
    }

    @Test
    public void testOnDetailsChangeExternalRemovesCall() throws Exception {
        BluetoothCall activeCall = createActiveCall(UUID.randomUUID());
        mBluetoothInCallService.onCallAdded(activeCall);
        doReturn(null).when(mMockCallInfo).getActiveCall();
        when(activeCall.getHandle()).thenReturn(Uri.parse("tel:555-0001"));

        when(activeCall.isExternalCall()).thenReturn(true);
        mBluetoothInCallService.getCallback(activeCall).onDetailsChanged(activeCall, null);

        verify(mMockBluetoothHeadset).phoneStateChanged(eq(0), eq(0), eq(CALL_STATE_IDLE),
                eq(""), eq(128), nullable(String.class));
    }

    @Test
    public void testOnDetailsChangeExternalAddsCall() throws Exception {
        BluetoothCall activeCall = createActiveCall(UUID.randomUUID());
        mBluetoothInCallService.onCallAdded(activeCall);
        when(activeCall.getHandle()).thenReturn(Uri.parse("tel:555-0001"));
        BluetoothInCallService.CallStateCallback callBack = mBluetoothInCallService.getCallback(
                activeCall);

        when(activeCall.isExternalCall()).thenReturn(true);
        callBack.onDetailsChanged(activeCall, null);

        when(activeCall.isExternalCall()).thenReturn(false);
        callBack.onDetailsChanged(activeCall, null);

        verify(mMockBluetoothHeadset).phoneStateChanged(eq(1), eq(0), eq(CALL_STATE_IDLE),
                eq(""), eq(128), nullable(String.class));
    }

    @Test
    public void testOnCallStateChangedConnectingCall() throws Exception {
        BluetoothCall activeCall = getMockCall(UUID.randomUUID());
        BluetoothCall connectingCall = getMockCall(UUID.randomUUID());
        when(connectingCall.getState()).thenReturn(Call.STATE_CONNECTING);
        ArrayList<BluetoothCall> calls = new ArrayList<>();
        calls.add(connectingCall);
        calls.add(activeCall);
        mBluetoothInCallService.onCallAdded(connectingCall);
        mBluetoothInCallService.onCallAdded(activeCall);
        when(mMockCallInfo.getBluetoothCalls()).thenReturn(calls);

        mBluetoothInCallService.getCallback(activeCall)
                .onStateChanged(activeCall, Call.STATE_HOLDING);

        verify(mMockBluetoothHeadset, never()).phoneStateChanged(anyInt(), anyInt(), anyInt(),
                anyString(), anyInt(), nullable(String.class));
    }

    @Test
    public void testOnCallAddedAudioProcessing() throws Exception {
        BluetoothCall call = getMockCall(UUID.randomUUID());
        when(call.getState()).thenReturn(Call.STATE_AUDIO_PROCESSING);
        mBluetoothInCallService.onCallAdded(call);

        verify(mMockBluetoothHeadset, never()).phoneStateChanged(anyInt(), anyInt(), anyInt(),
                anyString(), anyInt(), nullable(String.class));
    }

    @Test
    public void testOnCallStateChangedRingingToAudioProcessing() throws Exception {
        BluetoothCall ringingCall = createRingingCall(UUID.randomUUID());
        when(ringingCall.getHandle()).thenReturn(Uri.parse("tel:555000"));

        mBluetoothInCallService.onCallAdded(ringingCall);

        verify(mMockBluetoothHeadset).phoneStateChanged(eq(0), eq(0), eq(CALL_STATE_INCOMING),
                eq("555000"), eq(PhoneNumberUtils.TOA_Unknown), nullable(String.class));

        when(ringingCall.getState()).thenReturn(Call.STATE_AUDIO_PROCESSING);
        when(mMockCallInfo.getRingingOrSimulatedRingingCall()).thenReturn(null);

        mBluetoothInCallService.getCallback(ringingCall)
                .onStateChanged(ringingCall, Call.STATE_AUDIO_PROCESSING);

        verify(mMockBluetoothHeadset).phoneStateChanged(eq(0), eq(0), eq(CALL_STATE_IDLE),
                eq(""), eq(128), nullable(String.class));
    }

    @Test
    public void testOnCallStateChangedAudioProcessingToSimulatedRinging() throws Exception {
        BluetoothCall ringingCall = createRingingCall(UUID.randomUUID());
        when(ringingCall.getHandle()).thenReturn(Uri.parse("tel:555-0000"));
        mBluetoothInCallService.onCallAdded(ringingCall);
        mBluetoothInCallService.getCallback(ringingCall)
                .onStateChanged(ringingCall, Call.STATE_SIMULATED_RINGING);

        verify(mMockBluetoothHeadset).phoneStateChanged(eq(0), eq(0), eq(CALL_STATE_INCOMING),
                eq("555-0000"), eq(PhoneNumberUtils.TOA_Unknown), nullable(String.class));
    }

    @Test
    public void testOnCallStateChangedAudioProcessingToActive() throws Exception {
        BluetoothCall activeCall = createActiveCall(UUID.randomUUID());
        when(activeCall.getState()).thenReturn(Call.STATE_ACTIVE);
        mBluetoothInCallService.onCallAdded(activeCall);
        mBluetoothInCallService.getCallback(activeCall)
                .onStateChanged(activeCall, Call.STATE_ACTIVE);

        verify(mMockBluetoothHeadset).phoneStateChanged(eq(1), eq(0), eq(CALL_STATE_IDLE),
                eq(""), eq(128), nullable(String.class));
    }

    @Test
    public void testOnCallStateChangedDialing() throws Exception {
        BluetoothCall activeCall = createActiveCall(UUID.randomUUID());

        // make "mLastState" STATE_CONNECTING
        BluetoothInCallService.CallStateCallback callback =
                mBluetoothInCallService.new CallStateCallback(Call.STATE_CONNECTING);
        mBluetoothInCallService.mCallbacks.put(
                activeCall.getId(), callback);

        mBluetoothInCallService.mCallbacks.get(activeCall.getId())
                .onStateChanged(activeCall, Call.STATE_DIALING);

        verify(mMockBluetoothHeadset, never()).phoneStateChanged(anyInt(), anyInt(), anyInt(),
                anyString(), anyInt(), nullable(String.class));
    }

    @Test
    public void testOnCallStateChangedAlerting() throws Exception {
        BluetoothCall outgoingCall = createOutgoingCall(UUID.randomUUID());
        mBluetoothInCallService.onCallAdded(outgoingCall);
        mBluetoothInCallService.getCallback(outgoingCall)
                .onStateChanged(outgoingCall, Call.STATE_DIALING);

        verify(mMockBluetoothHeadset).phoneStateChanged(eq(0), eq(0), eq(CALL_STATE_DIALING),
                eq(""), eq(128), nullable(String.class));
        verify(mMockBluetoothHeadset).phoneStateChanged(eq(0), eq(0), eq(CALL_STATE_ALERTING),
                eq(""), eq(128), nullable(String.class));
    }

    @Test
    public void testOnCallStateChangedDisconnected() throws Exception {
        BluetoothCall disconnectedCall = createDisconnectedCall(UUID.randomUUID());
        doReturn(true).when(mMockCallInfo).hasOnlyDisconnectedCalls();
        mBluetoothInCallService.onCallAdded(disconnectedCall);
        mBluetoothInCallService.getCallback(disconnectedCall)
                .onStateChanged(disconnectedCall, Call.STATE_DISCONNECTED);
        verify(mMockBluetoothHeadset).phoneStateChanged(eq(0), eq(0), eq(CALL_STATE_DISCONNECTED),
                eq(""), eq(128), nullable(String.class));
    }

    @Test
    public void testOnCallStateChanged() throws Exception {
        BluetoothCall ringingCall = createRingingCall(UUID.randomUUID());
        when(ringingCall.getHandle()).thenReturn(Uri.parse("tel:555-0000"));
        mBluetoothInCallService.onCallAdded(ringingCall);

        verify(mMockBluetoothHeadset).phoneStateChanged(eq(0), eq(0), eq(CALL_STATE_INCOMING),
                eq("555-0000"), eq(PhoneNumberUtils.TOA_Unknown), nullable(String.class));

        //Switch to active
        doReturn(null).when(mMockCallInfo).getRingingOrSimulatedRingingCall();
        when(mMockCallInfo.getActiveCall()).thenReturn(ringingCall);

        mBluetoothInCallService.getCallback(ringingCall)
                .onStateChanged(ringingCall, Call.STATE_ACTIVE);

        verify(mMockBluetoothHeadset).phoneStateChanged(eq(1), eq(0), eq(CALL_STATE_IDLE),
                eq(""), eq(128), nullable(String.class));
    }

    @Test
    public void testOnCallStateChangedGSMSwap() throws Exception {
        BluetoothCall heldCall = createHeldCall(UUID.randomUUID());
        when(heldCall.getHandle()).thenReturn(Uri.parse("tel:555-0000"));
        mBluetoothInCallService.onCallAdded(heldCall);
        doReturn(2).when(mMockCallInfo).getNumHeldCalls();

        clearInvocations(mMockBluetoothHeadset);
        mBluetoothInCallService.getCallback(heldCall)
                .onStateChanged(heldCall, Call.STATE_HOLDING);

        verify(mMockBluetoothHeadset, never()).phoneStateChanged(eq(0), eq(2), eq(CALL_STATE_HELD),
                eq("5550000"), eq(PhoneNumberUtils.TOA_Unknown), nullable(String.class));
    }

    @Test
    public void testOnParentOnChildrenChanged() throws Exception {
        // Start with two calls that are being merged into a CDMA conference call. The
        // onIsConferencedChanged method will be called multiple times during the call. Make sure
        // that the bluetooth phone state is updated properly.
        BluetoothCall parentCall = createActiveCall(UUID.randomUUID());
        BluetoothCall activeCall = getMockCall(UUID.randomUUID());
        BluetoothCall heldCall = createHeldCall(UUID.randomUUID());
        mBluetoothInCallService.onCallAdded(parentCall);
        mBluetoothInCallService.onCallAdded(activeCall);
        mBluetoothInCallService.onCallAdded(heldCall);
        Integer parentId = parentCall.getId();
        when(activeCall.getParentId()).thenReturn(parentId);
        when(heldCall.getParentId()).thenReturn(parentId);

        ArrayList<Integer> calls = new ArrayList<>();
        calls.add(activeCall.getId());

        when(parentCall.getChildrenIds()).thenReturn(calls);
        when(parentCall.isConference()).thenReturn(true);

        removeCallCapability(parentCall, Connection.CAPABILITY_CONFERENCE_HAS_NO_CHILDREN);
        addCallCapability(parentCall, Connection.CAPABILITY_SWAP_CONFERENCE);
        when(parentCall.wasConferencePreviouslyMerged()).thenReturn(false);

        clearInvocations(mMockBluetoothHeadset);
        // Be sure that onIsConferencedChanged rejects spurious changes during set up of
        // CDMA "conference"
        mBluetoothInCallService.getCallback(activeCall).onParentChanged(activeCall);
        verify(mMockBluetoothHeadset, never()).phoneStateChanged(anyInt(), anyInt(), anyInt(),
                anyString(), anyInt(), nullable(String.class));

        mBluetoothInCallService.getCallback(heldCall).onParentChanged(heldCall);
        verify(mMockBluetoothHeadset, never()).phoneStateChanged(anyInt(), anyInt(), anyInt(),
                anyString(), anyInt(), nullable(String.class));

        mBluetoothInCallService.getCallback(parentCall)
                .onChildrenChanged(
                        parentCall,
                        mBluetoothInCallService.getBluetoothCallsByIds(calls));
        verify(mMockBluetoothHeadset, never()).phoneStateChanged(anyInt(), anyInt(), anyInt(),
                anyString(), anyInt(), nullable(String.class));

        calls.add(heldCall.getId());
        mBluetoothInCallService.onCallAdded(heldCall);
        mBluetoothInCallService.getCallback(parentCall)
                .onChildrenChanged(
                        parentCall,
                        mBluetoothInCallService.getBluetoothCallsByIds(calls));
        verify(mMockBluetoothHeadset).phoneStateChanged(eq(1), eq(1), eq(CALL_STATE_IDLE),
                eq(""), eq(128), nullable(String.class));
    }

    @Test
    public void testBluetoothAdapterReceiver() throws Exception {
        BluetoothCall ringingCall = createRingingCall(UUID.randomUUID());
        when(ringingCall.getHandle()).thenReturn(Uri.parse("tel:5550000"));

        Intent intent = new Intent(BluetoothAdapter.ACTION_STATE_CHANGED);
        intent.putExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_ON);
        clearInvocations(mMockBluetoothHeadset);
        mBluetoothInCallService.mBluetoothAdapterReceiver
                = mBluetoothInCallService.new BluetoothAdapterReceiver();
        mBluetoothInCallService.mBluetoothAdapterReceiver
                .onReceive(mBluetoothInCallService, intent);

        verify(mMockBluetoothHeadset).phoneStateChanged(eq(0), eq(0), eq(CALL_STATE_INCOMING),
                eq("5550000"), eq(PhoneNumberUtils.TOA_Unknown), nullable(String.class));
    }

    @Test
    public void testClear() {
        doNothing().when(mContext).unregisterReceiver(any(
                BluetoothInCallService.BluetoothAdapterReceiver.class));
        mBluetoothInCallService.attachBaseContext(mContext);
        mBluetoothInCallService.mBluetoothAdapterReceiver
                = mBluetoothInCallService.new BluetoothAdapterReceiver();
        Assert.assertNotNull(mBluetoothInCallService.mBluetoothAdapterReceiver);
        Assert.assertNotNull(mBluetoothInCallService.mBluetoothHeadset);

        mBluetoothInCallService.clear();

        Assert.assertNull(mBluetoothInCallService.mBluetoothAdapterReceiver);
        Assert.assertNull(mBluetoothInCallService.mBluetoothHeadset);
    }

    @Test
    public void testGetBearerTechnology() {
        mBluetoothInCallService.mTelephonyManager = mMockTelephonyManager;

        when(mMockTelephonyManager.getDataNetworkType()).thenReturn(
                TelephonyManager.NETWORK_TYPE_GSM);
        Assert.assertEquals(mBluetoothInCallService.getBearerTechnology(),
                BluetoothLeCallControlProxy.BEARER_TECHNOLOGY_GSM);

        when(mMockTelephonyManager.getDataNetworkType()).thenReturn(
                TelephonyManager.NETWORK_TYPE_GPRS);
        Assert.assertEquals(mBluetoothInCallService.getBearerTechnology(),
                BluetoothLeCallControlProxy.BEARER_TECHNOLOGY_2G);

        when(mMockTelephonyManager.getDataNetworkType()).thenReturn(
                TelephonyManager.NETWORK_TYPE_EVDO_B);
        Assert.assertEquals(mBluetoothInCallService.getBearerTechnology(),
                BluetoothLeCallControlProxy.BEARER_TECHNOLOGY_3G);

        when(mMockTelephonyManager.getDataNetworkType()).thenReturn(
                TelephonyManager.NETWORK_TYPE_TD_SCDMA);
        Assert.assertEquals(mBluetoothInCallService.getBearerTechnology(),
                BluetoothLeCallControlProxy.BEARER_TECHNOLOGY_WCDMA);

        when(mMockTelephonyManager.getDataNetworkType()).thenReturn(
                TelephonyManager.NETWORK_TYPE_LTE);
        Assert.assertEquals(mBluetoothInCallService.getBearerTechnology(),
                BluetoothLeCallControlProxy.BEARER_TECHNOLOGY_LTE);

        when(mMockTelephonyManager.getDataNetworkType()).thenReturn(
                TelephonyManager.NETWORK_TYPE_1xRTT);
        Assert.assertEquals(mBluetoothInCallService.getBearerTechnology(),
                BluetoothLeCallControlProxy.BEARER_TECHNOLOGY_CDMA);

        when(mMockTelephonyManager.getDataNetworkType()).thenReturn(
                TelephonyManager.NETWORK_TYPE_HSPAP);
        Assert.assertEquals(mBluetoothInCallService.getBearerTechnology(),
                BluetoothLeCallControlProxy.BEARER_TECHNOLOGY_4G);

        when(mMockTelephonyManager.getDataNetworkType()).thenReturn(
                TelephonyManager.NETWORK_TYPE_IWLAN);
        Assert.assertEquals(mBluetoothInCallService.getBearerTechnology(),
                BluetoothLeCallControlProxy.BEARER_TECHNOLOGY_WIFI);

        when(mMockTelephonyManager.getDataNetworkType()).thenReturn(
                TelephonyManager.NETWORK_TYPE_NR);
        Assert.assertEquals(mBluetoothInCallService.getBearerTechnology(),
                BluetoothLeCallControlProxy.BEARER_TECHNOLOGY_5G);

        when(mMockTelephonyManager.getDataNetworkType()).thenReturn(
                TelephonyManager.NETWORK_TYPE_LTE_CA);
        Assert.assertEquals(mBluetoothInCallService.getBearerTechnology(),
                BluetoothLeCallControlProxy.BEARER_TECHNOLOGY_GSM);
    }

    @Test
    public void testGetTbsTerminationReason() {
        BluetoothCall call = getMockCall(UUID.randomUUID());

        when(call.getDisconnectCause()).thenReturn(null);
        Assert.assertEquals(mBluetoothInCallService.getTbsTerminationReason(call),
                BluetoothLeCallControl.TERMINATION_REASON_FAIL);

        DisconnectCause cause = new DisconnectCause(DisconnectCause.BUSY, null, null, null, 1);
        when(call.getDisconnectCause()).thenReturn(cause);
        Assert.assertEquals(mBluetoothInCallService.getTbsTerminationReason(call),
                BluetoothLeCallControl.TERMINATION_REASON_LINE_BUSY);

        cause = new DisconnectCause(DisconnectCause.REJECTED, null, null, null, 1);
        when(call.getDisconnectCause()).thenReturn(cause);
        Assert.assertEquals(mBluetoothInCallService.getTbsTerminationReason(call),
                BluetoothLeCallControl.TERMINATION_REASON_REMOTE_HANGUP);

        cause = new DisconnectCause(DisconnectCause.LOCAL, null, null, null, 1);
        when(call.getDisconnectCause()).thenReturn(cause);
        mBluetoothInCallService.mIsTerminatedByClient = false;
        Assert.assertEquals(mBluetoothInCallService.getTbsTerminationReason(call),
                BluetoothLeCallControl.TERMINATION_REASON_SERVER_HANGUP);

        cause = new DisconnectCause(DisconnectCause.LOCAL, null, null, null, 1);
        when(call.getDisconnectCause()).thenReturn(cause);
        mBluetoothInCallService.mIsTerminatedByClient = true;
        Assert.assertEquals(mBluetoothInCallService.getTbsTerminationReason(call),
                BluetoothLeCallControl.TERMINATION_REASON_CLIENT_HANGUP);

        cause = new DisconnectCause(DisconnectCause.ERROR, null, null, null, 1);
        when(call.getDisconnectCause()).thenReturn(cause);
        Assert.assertEquals(mBluetoothInCallService.getTbsTerminationReason(call),
                BluetoothLeCallControl.TERMINATION_REASON_NETWORK_CONGESTION);

        cause = new DisconnectCause(
                DisconnectCause.CONNECTION_MANAGER_NOT_SUPPORTED, null, null, null, 1);
        when(call.getDisconnectCause()).thenReturn(cause);
        Assert.assertEquals(mBluetoothInCallService.getTbsTerminationReason(call),
                BluetoothLeCallControl.TERMINATION_REASON_INVALID_URI);

        cause = new DisconnectCause(DisconnectCause.ERROR, null, null, null, 1);
        when(call.getDisconnectCause()).thenReturn(cause);
        Assert.assertEquals(mBluetoothInCallService.getTbsTerminationReason(call),
                BluetoothLeCallControl.TERMINATION_REASON_NETWORK_CONGESTION);
    }

    @Test
    public void testOnCreate() {
        ApplicationInfo applicationInfo = new ApplicationInfo();
        applicationInfo.targetSdkVersion = Build.VERSION_CODES.S;
        when(mContext.getApplicationInfo()).thenReturn(applicationInfo);
        mBluetoothInCallService.attachBaseContext(mContext);
        mBluetoothInCallService.setOnCreateCalled(false);
        Assert.assertNull(mBluetoothInCallService.mBluetoothAdapterReceiver);

        mBluetoothInCallService.onCreate();

        Assert.assertNotNull(mBluetoothInCallService.mBluetoothAdapterReceiver);
        Assert.assertTrue(mBluetoothInCallService.mOnCreateCalled);
    }

    @Test
    public void testOnDestroy() {
        Assert.assertTrue(mBluetoothInCallService.mOnCreateCalled);

        mBluetoothInCallService.onDestroy();

        Assert.assertFalse(mBluetoothInCallService.mOnCreateCalled);
    }

    @Test
    public void testLeCallControlCallback_onAcceptCall_withUnknownCallId() {
        BluetoothLeCallControlProxy callControlProxy = mock(BluetoothLeCallControlProxy.class);
        mBluetoothInCallService.mBluetoothLeCallControl = callControlProxy;
        BluetoothLeCallControl.Callback callback =
                mBluetoothInCallService.mBluetoothLeCallControlCallback;

        int requestId = 1;
        UUID unknownCallId = UUID.randomUUID();
        callback.onAcceptCall(requestId, unknownCallId);

        verify(callControlProxy).requestResult(
                requestId, BluetoothLeCallControl.RESULT_ERROR_UNKNOWN_CALL_ID);
    }

    @Test
    public void testLeCallControlCallback_onTerminateCall_withUnknownCallId() {
        BluetoothLeCallControlProxy callControlProxy = mock(BluetoothLeCallControlProxy.class);
        mBluetoothInCallService.mBluetoothLeCallControl = callControlProxy;
        BluetoothLeCallControl.Callback callback =
                mBluetoothInCallService.mBluetoothLeCallControlCallback;

        int requestId = 1;
        UUID unknownCallId = UUID.randomUUID();
        callback.onTerminateCall(requestId, unknownCallId);

        verify(callControlProxy).requestResult(
                requestId, BluetoothLeCallControl.RESULT_ERROR_UNKNOWN_CALL_ID);
    }

    @Test
    public void testLeCallControlCallback_onHoldCall_withUnknownCallId() {
        BluetoothLeCallControlProxy callControlProxy = mock(BluetoothLeCallControlProxy.class);
        mBluetoothInCallService.mBluetoothLeCallControl = callControlProxy;
        BluetoothLeCallControl.Callback callback =
                mBluetoothInCallService.mBluetoothLeCallControlCallback;

        int requestId = 1;
        UUID unknownCallId = UUID.randomUUID();
        callback.onHoldCall(requestId, unknownCallId);

        verify(callControlProxy).requestResult(
                requestId, BluetoothLeCallControl.RESULT_ERROR_UNKNOWN_CALL_ID);
    }

    @Test
    public void testLeCallControlCallback_onUnholdCall_withUnknownCallId() {
        BluetoothLeCallControlProxy callControlProxy = mock(BluetoothLeCallControlProxy.class);
        mBluetoothInCallService.mBluetoothLeCallControl = callControlProxy;
        BluetoothLeCallControl.Callback callback =
                mBluetoothInCallService.mBluetoothLeCallControlCallback;

        int requestId = 1;
        UUID unknownCallId = UUID.randomUUID();
        callback.onUnholdCall(requestId, unknownCallId);

        verify(callControlProxy).requestResult(
                requestId, BluetoothLeCallControl.RESULT_ERROR_UNKNOWN_CALL_ID);
    }

    @Test
    public void testLeCallControlCallback_onJoinCalls() {
        BluetoothLeCallControlProxy callControlProxy = mock(BluetoothLeCallControlProxy.class);
        mBluetoothInCallService.mBluetoothLeCallControl = callControlProxy;
        BluetoothLeCallControl.Callback callback =
                mBluetoothInCallService.mBluetoothLeCallControlCallback;

        int requestId = 1;
        UUID baseCallId = UUID.randomUUID();
        UUID firstJoiningCallId = UUID.randomUUID();
        UUID secondJoiningCallId = UUID.randomUUID();
        List<UUID> uuids = new ArrayList<>(Arrays.asList(baseCallId, firstJoiningCallId,
                secondJoiningCallId));
        ArrayList<BluetoothCall> calls = new ArrayList<>();
        BluetoothCall baseCall = createActiveCall(baseCallId);
        BluetoothCall firstCall = createRingingCall(firstJoiningCallId);
        BluetoothCall secondCall = createRingingCall(secondJoiningCallId);
        when(baseCall.getState()).thenReturn(Call.STATE_ACTIVE);
        when(firstCall.getState()).thenReturn(Call.STATE_RINGING);
        when(secondCall.getState()).thenReturn(Call.STATE_RINGING);
        calls.add(baseCall);
        calls.add(firstCall);
        calls.add(secondCall);
        mBluetoothInCallService.onCallAdded(baseCall);
        mBluetoothInCallService.onCallAdded(firstCall);
        mBluetoothInCallService.onCallAdded(secondCall);
        when(baseCall.getHandle()).thenReturn(Uri.parse("tel:111-111"));
        when(firstCall.getHandle()).thenReturn(Uri.parse("tel:222-222"));
        when(secondCall.getHandle()).thenReturn(Uri.parse("tel:333-333"));
        when(mMockCallInfo.getBluetoothCalls()).thenReturn(calls);

        doReturn(baseCall).when(mMockCallInfo).getCallByCallId(baseCallId);
        doReturn(firstCall).when(mMockCallInfo).getCallByCallId(firstJoiningCallId);
        doReturn(secondCall).when(mMockCallInfo).getCallByCallId(secondJoiningCallId);

        callback.onJoinCalls(requestId, uuids);

        verify(callControlProxy).requestResult(
                requestId, BluetoothLeCallControl.RESULT_SUCCESS);
        verify(baseCall, times(2)).conference(any(BluetoothCall.class));
    }

    @Test
    public void testLeCallControlCallback_onJoinCalls_omitDoubledCalls() {
        BluetoothLeCallControlProxy callControlProxy = mock(BluetoothLeCallControlProxy.class);
        mBluetoothInCallService.mBluetoothLeCallControl = callControlProxy;
        BluetoothLeCallControl.Callback callback =
                mBluetoothInCallService.mBluetoothLeCallControlCallback;

        int requestId = 1;
        UUID baseCallId = UUID.randomUUID();
        UUID firstJoiningCallId = UUID.randomUUID();
        List<UUID> uuids = new ArrayList<>(Arrays.asList(baseCallId, firstJoiningCallId,
                firstJoiningCallId));
        ArrayList<BluetoothCall> calls = new ArrayList<>();
        BluetoothCall baseCall = createActiveCall(baseCallId);
        BluetoothCall firstCall = createRingingCall(firstJoiningCallId);
        when(baseCall.getState()).thenReturn(Call.STATE_ACTIVE);
        when(firstCall.getState()).thenReturn(Call.STATE_RINGING);
        calls.add(baseCall);
        calls.add(firstCall);
        mBluetoothInCallService.onCallAdded(baseCall);
        mBluetoothInCallService.onCallAdded(firstCall);
        when(baseCall.getHandle()).thenReturn(Uri.parse("tel:111-111"));
        when(firstCall.getHandle()).thenReturn(Uri.parse("tel:222-222"));
        when(mMockCallInfo.getBluetoothCalls()).thenReturn(calls);

        doReturn(baseCall).when(mMockCallInfo).getCallByCallId(baseCallId);
        doReturn(firstCall).when(mMockCallInfo).getCallByCallId(firstJoiningCallId);

        callback.onJoinCalls(requestId, uuids);

        verify(callControlProxy).requestResult(
                requestId, BluetoothLeCallControl.RESULT_SUCCESS);
        verify(baseCall, times(1)).conference(any(BluetoothCall.class));
    }

    @Test
    public void testLeCallControlCallback_onJoinCalls_omitNullCalls() {
        BluetoothLeCallControlProxy callControlProxy = mock(BluetoothLeCallControlProxy.class);
        mBluetoothInCallService.mBluetoothLeCallControl = callControlProxy;
        BluetoothLeCallControl.Callback callback =
                mBluetoothInCallService.mBluetoothLeCallControlCallback;

        int requestId = 1;
        UUID baseCallId = UUID.randomUUID();
        UUID firstJoiningCallId = UUID.randomUUID();
        UUID secondJoiningCallId = UUID.randomUUID();
        List<UUID> uuids = new ArrayList<>(Arrays.asList(baseCallId, firstJoiningCallId,
                secondJoiningCallId));
        ArrayList<BluetoothCall> calls = new ArrayList<>();
        BluetoothCall baseCall = createActiveCall(baseCallId);
        BluetoothCall firstCall = createRingingCall(firstJoiningCallId);
        BluetoothCall secondCall = createRingingCall(secondJoiningCallId);
        when(baseCall.getState()).thenReturn(Call.STATE_ACTIVE);
        when(firstCall.getState()).thenReturn(Call.STATE_RINGING);
        when(secondCall.getState()).thenReturn(Call.STATE_RINGING);
        calls.add(baseCall);
        calls.add(firstCall);
        calls.add(secondCall);
        mBluetoothInCallService.onCallAdded(baseCall);
        mBluetoothInCallService.onCallAdded(firstCall);
        mBluetoothInCallService.onCallAdded(secondCall);
        when(baseCall.getHandle()).thenReturn(Uri.parse("tel:111-111"));
        when(firstCall.getHandle()).thenReturn(Uri.parse("tel:222-222"));
        when(secondCall.getHandle()).thenReturn(Uri.parse("tel:333-333"));
        when(mMockCallInfo.getBluetoothCalls()).thenReturn(calls);

        doReturn(baseCall).when(mMockCallInfo).getCallByCallId(null);
        doReturn(firstCall).when(mMockCallInfo).getCallByCallId(firstJoiningCallId);
        doReturn(secondCall).when(mMockCallInfo).getCallByCallId(secondJoiningCallId);

        callback.onJoinCalls(requestId, uuids);

        verify(callControlProxy).requestResult(
                requestId, BluetoothLeCallControl.RESULT_SUCCESS);
        verify(firstCall, times(1)).conference(any(BluetoothCall.class));
    }

    private void addCallCapability(BluetoothCall call, int capability) {
        when(call.can(capability)).thenReturn(true);
    }

    private void removeCallCapability(BluetoothCall call, int capability) {
        when(call.can(capability)).thenReturn(false);
    }

    private BluetoothCall createActiveCall(UUID uuid) {
        BluetoothCall call = getMockCall(uuid);
        when(mMockCallInfo.getActiveCall()).thenReturn(call);
        return call;
    }

    private BluetoothCall createRingingCall(UUID uuid) {
        BluetoothCall call = getMockCall(uuid);
        Log.i("BluetoothInCallService", "asdf creaete " + Integer.toString(call.hashCode()));
        when(mMockCallInfo.getRingingOrSimulatedRingingCall()).thenReturn(call);
        return call;
    }

    private BluetoothCall createHeldCall(UUID uuid) {
        BluetoothCall call = getMockCall(uuid);
        when(mMockCallInfo.getHeldCall()).thenReturn(call);
        return call;
    }

    private BluetoothCall createOutgoingCall(UUID uuid) {
        BluetoothCall call = getMockCall(uuid);
        when(mMockCallInfo.getOutgoingCall()).thenReturn(call);
        return call;
    }

    private BluetoothCall createDisconnectedCall(UUID uuid) {
        BluetoothCall call = getMockCall(uuid);
        when(mMockCallInfo.getCallByState(Call.STATE_DISCONNECTED)).thenReturn(call);
        return call;
    }

    private BluetoothCall createForegroundCall(UUID uuid) {
        BluetoothCall call = getMockCall(uuid);
        when(mMockCallInfo.getForegroundCall()).thenReturn(call);
        return call;
    }

    private static ComponentName makeQuickConnectionServiceComponentName() {
        return new ComponentName("com.android.server.telecom.tests",
                "com.android.server.telecom.tests.MockConnectionService");
    }

    private static PhoneAccountHandle makeQuickAccountHandle(String id) {
        return new PhoneAccountHandle(makeQuickConnectionServiceComponentName(), id,
                Binder.getCallingUserHandle());
    }

    private PhoneAccount.Builder makeQuickAccountBuilder(String id, int idx) {
        return new PhoneAccount.Builder(makeQuickAccountHandle(id), "label" + idx);
    }

    private PhoneAccount makeQuickAccount(String id, int idx) {
        return makeQuickAccountBuilder(id, idx)
                .setAddress(Uri.parse(TEST_ACCOUNT_ADDRESS + idx))
                .setSubscriptionAddress(Uri.parse("tel:555-000" + idx))
                .setCapabilities(idx)
                .setShortDescription("desc" + idx)
                .setIsEnabled(true)
                .build();
    }

    private BluetoothCall getMockCall(UUID uuid) {
        BluetoothCall call = mock(com.android.bluetooth.telephony.BluetoothCall.class);
        Integer integerUuid = uuid.hashCode();
        when(call.getId()).thenReturn(integerUuid);
        return call;
    }
}
