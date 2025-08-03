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

package com.android.telephony.qns;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import android.net.LinkProperties;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Message;
import android.os.test.TestLooper;
import android.telephony.AccessNetworkConstants;
import android.telephony.CallQuality;
import android.telephony.CallState;
import android.telephony.PreciseCallState;
import android.telephony.PreciseDataConnectionState;
import android.telephony.TelephonyManager;
import android.telephony.data.ApnSetting;
import android.telephony.ims.ImsCallProfile;
import android.telephony.ims.MediaQualityStatus;

import com.android.telephony.qns.QnsCallStatusTracker.ActiveCallTracker.TransportQuality;
import com.android.telephony.qns.QnsCallStatusTracker.CallQualityBlock;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@RunWith(JUnit4.class)
public class QnsCallStatusTrackerTest extends QnsTest {

    QnsCallStatusTracker mCallTracker;
    TestLooper mTestLooper;
    TestLooper mTestLooperListener;
    TestLooper mLowQualityListenerLooper;
    private Handler mImsHandler;
    private Handler mEmergencyHandler;
    private Handler mLowQualityHandler;
    private MockitoSession mMockSession;
    List<CallState> mTestCallStateList = new ArrayList<>();
    int mId = 0;
    HashMap<Integer, Message> mMessageHashMap = new HashMap<>();

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        super.setUp();
        mMockSession = mockitoSession().mockStatic(QnsUtils.class).startMocking();
        mTestLooper = new TestLooper();
        mTestLooperListener = new TestLooper();
        mLowQualityListenerLooper = new TestLooper();
        Mockito.when(sMockContext.getMainLooper()).thenReturn(mTestLooper.getLooper());
        mImsHandler = new Handler(mTestLooperListener.getLooper());
        mEmergencyHandler = new Handler(mTestLooperListener.getLooper());
        mLowQualityHandler = new Handler(mLowQualityListenerLooper.getLooper());
        mMessageHashMap = new HashMap<>();
        when(mMockQnsTimer.registerTimer(isA(Message.class), anyLong())).thenAnswer(
                (Answer<Integer>) invocation -> {
                    Message msg = (Message) invocation.getArguments()[0];
                    long delay = (long) invocation.getArguments()[1];
                    msg.getTarget().sendMessageDelayed(msg, delay);
                    mMessageHashMap.put(++mId, msg);
                    return mId;
                });

        doAnswer(invocation -> {
            int timerId = (int) invocation.getArguments()[0];
            Message msg = mMessageHashMap.get(timerId);
            if (msg != null && msg.getTarget() != null) {
                msg.getTarget().removeMessages(msg.what, msg.obj);
            }
            return null;
        }).when(mMockQnsTimer).unregisterTimer(anyInt());
        mCallTracker = new QnsCallStatusTracker(
                mMockQnsTelephonyListener, mMockQnsConfigManager, mMockQnsTimer, 0,
                mTestLooper.getLooper());
        mCallTracker.registerCallTypeChangedListener(
                NetworkCapabilities.NET_CAPABILITY_IMS, mImsHandler, 1, null);
        mCallTracker.registerCallTypeChangedListener(
                NetworkCapabilities.NET_CAPABILITY_EIMS, mEmergencyHandler, 1, null);
        mCallTracker.getActiveCallTracker()
                .registerLowMediaQualityListener(mLowQualityHandler, 1, null);
        lenient().when(QnsUtils.getOtherTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WLAN))
                .thenReturn(AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
        lenient().when(QnsUtils.getOtherTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WWAN))
                .thenReturn(AccessNetworkConstants.TRANSPORT_TYPE_WLAN);
        lenient()
                .when(QnsUtils.getOtherTransportType(AccessNetworkConstants.TRANSPORT_TYPE_INVALID))
                .thenReturn(AccessNetworkConstants.TRANSPORT_TYPE_INVALID);
    }

    @After
    public void tearDown() {
        mTestCallStateList.clear();
        mCallTracker.unregisterCallTypeChangedListener(
                NetworkCapabilities.NET_CAPABILITY_IMS, mImsHandler);
        mCallTracker.unregisterCallTypeChangedListener(
                NetworkCapabilities.NET_CAPABILITY_EIMS, mEmergencyHandler);
        mCallTracker.getActiveCallTracker()
                .unregisterLowMediaQualityListener(mLowQualityHandler);
        if (mMockSession != null) {
            mMockSession.finishMocking();
            mMockSession = null;
        }
    }

    @Test
    public void testForVoiceCallTypeChangedScenarios() {

        mTestCallStateList.add(new CallState.Builder(PreciseCallState.PRECISE_CALL_STATE_DIALING)
                .setImsCallType(ImsCallProfile.CALL_TYPE_VOICE)
                .setImsCallServiceType(ImsCallProfile.SERVICE_TYPE_NORMAL).build());
        mCallTracker.updateCallState(mTestCallStateList);

        Message msg = mTestLooperListener.nextMessage();
        assertEquals(mImsHandler, msg.getTarget());
        assertNotEquals(mEmergencyHandler, msg.getTarget());
        assertNotNull(msg);
        QnsAsyncResult result = (QnsAsyncResult) msg.obj;
        assertNotNull(result.mResult);
        assertEquals(QnsConstants.CALL_TYPE_VOICE, (int) result.mResult);
        assertFalse(mCallTracker.isCallIdle());
        assertTrue(mCallTracker.isCallIdle(NetworkCapabilities.NET_CAPABILITY_EIMS));
        assertFalse(mCallTracker.isCallIdle(NetworkCapabilities.NET_CAPABILITY_IMS));

        // Test2:
        mTestCallStateList.clear();
        mTestCallStateList.add(new CallState.Builder(PreciseCallState.PRECISE_CALL_STATE_ACTIVE)
                .setImsCallType(ImsCallProfile.CALL_TYPE_VOICE)
                .setImsCallServiceType(ImsCallProfile.SERVICE_TYPE_NORMAL).build());
        mCallTracker.updateCallState(mTestCallStateList);
        msg = mTestLooperListener.nextMessage();
        // Should not notify if call type is not changed
        assertNull(msg);

        // Test3:
        mTestCallStateList.clear();
        mCallTracker.updateCallState(mTestCallStateList);
        msg = mTestLooperListener.nextMessage();
        assertNotNull(msg);
        result = (QnsAsyncResult) msg.obj;
        assertNotNull(result.mResult);
        assertEquals(QnsConstants.CALL_TYPE_IDLE, (int) result.mResult);
        assertTrue(mCallTracker.isCallIdle());
        assertTrue(mCallTracker.isCallIdle(NetworkCapabilities.NET_CAPABILITY_EIMS));
        assertTrue(mCallTracker.isCallIdle(NetworkCapabilities.NET_CAPABILITY_IMS));
    }

    @Test
    public void testForEmergencyCallTypeChangedScenarios() {
        mTestCallStateList.add(new CallState.Builder(PreciseCallState.PRECISE_CALL_STATE_INCOMING)
                .setImsCallType(ImsCallProfile.CALL_TYPE_VOICE)
                .setImsCallServiceType(ImsCallProfile.SERVICE_TYPE_NORMAL).build());
        mCallTracker.updateCallState(mTestCallStateList);

        Message msg = mTestLooperListener.nextMessage();
        assertEquals(mImsHandler, msg.getTarget());
        assertNotNull(msg);
        QnsAsyncResult result = (QnsAsyncResult) msg.obj;
        assertNotNull(result.mResult);
        assertEquals(QnsConstants.CALL_TYPE_VOICE, (int) result.mResult);
        assertFalse(mCallTracker.isCallIdle());
        assertTrue(mCallTracker.isCallIdle(NetworkCapabilities.NET_CAPABILITY_EIMS));
        assertFalse(mCallTracker.isCallIdle(NetworkCapabilities.NET_CAPABILITY_IMS));
        assertFalse(mCallTracker.isCallIdle(NetworkCapabilities.NET_CAPABILITY_MMS));

        mTestCallStateList.clear();
        mTestCallStateList.add(new CallState.Builder(PreciseCallState.PRECISE_CALL_STATE_ACTIVE)
                .setImsCallType(ImsCallProfile.CALL_TYPE_VOICE)
                .setImsCallServiceType(ImsCallProfile.SERVICE_TYPE_NORMAL).build());
        mCallTracker.updateCallState(mTestCallStateList);
        msg = mTestLooperListener.nextMessage();
        // Should not notify if call type is not changed
        assertNull(msg);

        mTestCallStateList.clear();
        mTestCallStateList.add(
                new CallState.Builder(PreciseCallState.PRECISE_CALL_STATE_DISCONNECTING)
                        .setImsCallType(ImsCallProfile.CALL_TYPE_VOICE)
                        .setImsCallServiceType(ImsCallProfile.SERVICE_TYPE_NORMAL).build());
        mTestCallStateList.add(
                new CallState.Builder(PreciseCallState.PRECISE_CALL_STATE_ACTIVE)
                .setImsCallType(ImsCallProfile.CALL_TYPE_VOICE)
                .setImsCallServiceType(ImsCallProfile.SERVICE_TYPE_EMERGENCY).build());
        mCallTracker.updateCallState(mTestCallStateList);
        msg = mTestLooperListener.nextMessage();
        assertNotNull(msg);
        assertEquals(mEmergencyHandler, msg.getTarget());
        result = (QnsAsyncResult) msg.obj;
        assertNotNull(result.mResult);
        assertEquals(QnsConstants.CALL_TYPE_EMERGENCY, (int) result.mResult);
        assertFalse(mCallTracker.isCallIdle());
        assertFalse(mCallTracker.isCallIdle(NetworkCapabilities.NET_CAPABILITY_EIMS));
        assertFalse(mCallTracker.isCallIdle(NetworkCapabilities.NET_CAPABILITY_IMS));

        mTestCallStateList.clear();
        mTestCallStateList.add(new CallState.Builder(PreciseCallState.PRECISE_CALL_STATE_ACTIVE)
                .setImsCallType(ImsCallProfile.CALL_TYPE_VOICE)
                .setImsCallServiceType(ImsCallProfile.SERVICE_TYPE_EMERGENCY).build());
        mCallTracker.updateCallState(mTestCallStateList);
        msg = mTestLooperListener.nextMessage();
        assertNotNull(msg);
        assertEquals(mImsHandler, msg.getTarget());
        result = (QnsAsyncResult) msg.obj;
        assertNotNull(result.mResult);
        assertEquals(QnsConstants.CALL_TYPE_IDLE, (int) result.mResult);
        assertFalse(mCallTracker.isCallIdle());
        assertFalse(mCallTracker.isCallIdle(NetworkCapabilities.NET_CAPABILITY_EIMS));
        assertTrue(mCallTracker.isCallIdle(NetworkCapabilities.NET_CAPABILITY_IMS));

        mTestCallStateList.clear();
        mTestCallStateList.add(new CallState.Builder(PreciseCallState.PRECISE_CALL_STATE_ALERTING)
                .setImsCallType(ImsCallProfile.CALL_TYPE_VOICE)
                .setImsCallServiceType(ImsCallProfile.SERVICE_TYPE_NORMAL).build());
        mCallTracker.updateCallState(mTestCallStateList);
        msg = mTestLooperListener.nextMessage();
        assertNotNull(msg);
        assertEquals(mEmergencyHandler, msg.getTarget());
        result = (QnsAsyncResult) msg.obj;
        assertNotNull(result.mResult);
        assertEquals(QnsConstants.CALL_TYPE_IDLE, (int) result.mResult);
        msg = mTestLooperListener.nextMessage();
        assertNotNull(msg);
        assertEquals(mImsHandler, msg.getTarget());
        result = (QnsAsyncResult) msg.obj;
        assertNotNull(result.mResult);
        assertEquals(QnsConstants.CALL_TYPE_VOICE, (int) result.mResult);
        assertFalse(mCallTracker.isCallIdle());
        assertTrue(mCallTracker.isCallIdle(NetworkCapabilities.NET_CAPABILITY_EIMS));
        assertFalse(mCallTracker.isCallIdle(NetworkCapabilities.NET_CAPABILITY_IMS));
    }

    @Test
    public void testForVideoCallTypeChangedScenarios() {
        mTestCallStateList.add(new CallState.Builder(PreciseCallState.PRECISE_CALL_STATE_ACTIVE)
                .setImsCallType(ImsCallProfile.CALL_TYPE_VOICE)
                .setImsCallServiceType(ImsCallProfile.SERVICE_TYPE_NORMAL).build());
        mTestCallStateList.add(new CallState.Builder(PreciseCallState.PRECISE_CALL_STATE_INCOMING)
                .setImsCallType(ImsCallProfile.CALL_TYPE_VT)
                .setImsCallServiceType(ImsCallProfile.SERVICE_TYPE_NORMAL).build());
        mCallTracker.updateCallState(mTestCallStateList);
        Message msg = mTestLooperListener.nextMessage();
        assertNotNull(msg);
        QnsAsyncResult result = (QnsAsyncResult) msg.obj;
        assertNotNull(result.mResult);
        assertEquals(QnsConstants.CALL_TYPE_VOICE, (int) result.mResult);
        assertFalse(mCallTracker.isCallIdle()); // for IMS calls only
        assertTrue(mCallTracker.isCallIdle(NetworkCapabilities.NET_CAPABILITY_EIMS));
        assertFalse(mCallTracker.isCallIdle(NetworkCapabilities.NET_CAPABILITY_IMS));

        mTestCallStateList.clear();
        mTestCallStateList.add(new CallState.Builder(PreciseCallState.PRECISE_CALL_STATE_ACTIVE)
                .setImsCallType(ImsCallProfile.CALL_TYPE_VOICE)
                .setImsCallServiceType(ImsCallProfile.SERVICE_TYPE_NORMAL).build());
        mTestCallStateList.add(new CallState.Builder(PreciseCallState.PRECISE_CALL_STATE_WAITING)
                .setImsCallType(ImsCallProfile.CALL_TYPE_VT)
                .setImsCallServiceType(ImsCallProfile.SERVICE_TYPE_NORMAL).build());
        mCallTracker.updateCallState(mTestCallStateList);
        msg = mTestLooperListener.nextMessage();
        // Video call is still in waiting state, QNS call type changed event should not be happened.
        assertNull(msg);
        assertFalse(mCallTracker.isCallIdle()); // for IMS calls only
        assertTrue(mCallTracker.isCallIdle(NetworkCapabilities.NET_CAPABILITY_EIMS));
        assertFalse(mCallTracker.isCallIdle(NetworkCapabilities.NET_CAPABILITY_IMS));

        mTestCallStateList.clear();
        mTestCallStateList.add(new CallState.Builder(PreciseCallState.PRECISE_CALL_STATE_ACTIVE)
                .setImsCallType(ImsCallProfile.CALL_TYPE_VOICE)
                .setImsCallServiceType(ImsCallProfile.SERVICE_TYPE_NORMAL).build());
        mTestCallStateList.add(new CallState.Builder(PreciseCallState.PRECISE_CALL_STATE_HOLDING)
                .setImsCallType(ImsCallProfile.CALL_TYPE_VT)
                .setImsCallServiceType(ImsCallProfile.SERVICE_TYPE_NORMAL).build());
        mCallTracker.updateCallState(mTestCallStateList);
        msg = mTestLooperListener.nextMessage();
        assertNotNull(msg);
        result = (QnsAsyncResult) msg.obj;
        assertNotNull(result.mResult);
        assertEquals(QnsConstants.CALL_TYPE_VIDEO, (int) result.mResult);
        assertFalse(mCallTracker.isCallIdle()); // for IMS calls only
        assertTrue(mCallTracker.isCallIdle(NetworkCapabilities.NET_CAPABILITY_EIMS));
        assertFalse(mCallTracker.isCallIdle(NetworkCapabilities.NET_CAPABILITY_IMS));

        mTestCallStateList.clear();
        mTestCallStateList.add(new CallState.Builder(PreciseCallState.PRECISE_CALL_STATE_ACTIVE)
                .setImsCallType(ImsCallProfile.CALL_TYPE_VT)
                .setImsCallServiceType(ImsCallProfile.SERVICE_TYPE_NORMAL).build());
        mTestCallStateList.add(new CallState.Builder(PreciseCallState.PRECISE_CALL_STATE_HOLDING)
                .setImsCallType(ImsCallProfile.CALL_TYPE_VOICE)
                .setImsCallServiceType(ImsCallProfile.SERVICE_TYPE_NORMAL).build());
        mCallTracker.updateCallState(mTestCallStateList);
        msg = mTestLooperListener.nextMessage();
        assertNull(msg);
        assertFalse(mCallTracker.isCallIdle()); // for IMS calls only
        assertTrue(mCallTracker.isCallIdle(NetworkCapabilities.NET_CAPABILITY_EIMS));
        assertFalse(mCallTracker.isCallIdle(NetworkCapabilities.NET_CAPABILITY_IMS));

        mTestCallStateList.clear();
        mTestCallStateList.add(new CallState.Builder(PreciseCallState.PRECISE_CALL_STATE_HOLDING)
                .setImsCallType(ImsCallProfile.CALL_TYPE_VOICE)
                .setImsCallServiceType(ImsCallProfile.SERVICE_TYPE_NORMAL).build());
        mCallTracker.updateCallState(mTestCallStateList);
        msg = mTestLooperListener.nextMessage();
        assertNotNull(msg);
        assertEquals(mImsHandler, msg.getTarget());
        result = (QnsAsyncResult) msg.obj;
        assertNotNull(result.mResult);
        assertEquals(QnsConstants.CALL_TYPE_VOICE, (int) result.mResult);
        assertFalse(mCallTracker.isCallIdle());
        assertTrue(mCallTracker.isCallIdle(NetworkCapabilities.NET_CAPABILITY_EIMS));
        assertFalse(mCallTracker.isCallIdle(NetworkCapabilities.NET_CAPABILITY_IMS));

        mTestCallStateList.clear();
        mTestCallStateList.add(new CallState.Builder(PreciseCallState.PRECISE_CALL_STATE_ACTIVE)
                .setImsCallType(ImsCallProfile.CALL_TYPE_VOICE)
                .setImsCallServiceType(ImsCallProfile.SERVICE_TYPE_NORMAL).build());
        mTestCallStateList.add(new CallState.Builder(PreciseCallState.PRECISE_CALL_STATE_INCOMING)
                .setImsCallType(ImsCallProfile.CALL_TYPE_VT)
                .setImsCallServiceType(ImsCallProfile.SERVICE_TYPE_NORMAL).build());
        mCallTracker.updateCallState(mTestCallStateList);
        msg = mTestLooperListener.nextMessage();
        assertNull(msg);
        assertFalse(mCallTracker.isCallIdle());

        mTestCallStateList.clear();
        mTestCallStateList.add(new CallState.Builder(PreciseCallState.PRECISE_CALL_STATE_ACTIVE)
                .setImsCallType(ImsCallProfile.CALL_TYPE_VT)
                .setImsCallServiceType(ImsCallProfile.SERVICE_TYPE_NORMAL).build());
        mCallTracker.updateCallState(mTestCallStateList);
        msg = mTestLooperListener.nextMessage();
        assertNotNull(msg);
        assertEquals(mImsHandler, msg.getTarget());
        result = (QnsAsyncResult) msg.obj;
        assertNotNull(result.mResult);
        assertEquals(QnsConstants.CALL_TYPE_VIDEO, (int) result.mResult);
        assertFalse(mCallTracker.isCallIdle());

        mTestCallStateList.clear();
        mTestCallStateList.add(new CallState.Builder(PreciseCallState.PRECISE_CALL_STATE_ALERTING)
                .setImsCallType(ImsCallProfile.CALL_TYPE_VT)
                .setImsCallServiceType(ImsCallProfile.SERVICE_TYPE_NORMAL).build());
        mCallTracker.updateCallState(mTestCallStateList);
        msg = mTestLooperListener.nextMessage();
        assertNotNull(msg);
        assertEquals(mImsHandler, msg.getTarget());
        result = (QnsAsyncResult) msg.obj;
        assertNotNull(result.mResult);
        assertEquals(QnsConstants.CALL_TYPE_IDLE, (int) result.mResult);
        assertFalse(mCallTracker.isCallIdle());
        assertTrue(mCallTracker.isCallIdle(NetworkCapabilities.NET_CAPABILITY_EIMS));
        assertFalse(mCallTracker.isCallIdle(NetworkCapabilities.NET_CAPABILITY_IMS));

        mTestCallStateList.clear();
        mCallTracker.updateCallState(mTestCallStateList);
        msg = mTestLooperListener.nextMessage();
        assertNull(msg);
        assertTrue(mCallTracker.isCallIdle());
        assertTrue(mCallTracker.isCallIdle(NetworkCapabilities.NET_CAPABILITY_EIMS));
        assertTrue(mCallTracker.isCallIdle(NetworkCapabilities.NET_CAPABILITY_IMS));
    }

    @Test
    public void testUnregisterCallTypeChangedListener() {
        mTestCallStateList.add(new CallState.Builder(PreciseCallState.PRECISE_CALL_STATE_ACTIVE)
                .setImsCallType(ImsCallProfile.CALL_TYPE_VOICE)
                .setImsCallServiceType(ImsCallProfile.SERVICE_TYPE_NORMAL).build());
        mTestCallStateList.add(new CallState.Builder(PreciseCallState.PRECISE_CALL_STATE_INCOMING)
                .setImsCallType(ImsCallProfile.CALL_TYPE_VT)
                .setImsCallServiceType(ImsCallProfile.SERVICE_TYPE_NORMAL).build());
        mCallTracker.updateCallState(mTestCallStateList);
        Message msg = mTestLooperListener.nextMessage();
        assertNotNull(msg);
        QnsAsyncResult result = (QnsAsyncResult) msg.obj;
        assertNotNull(result.mResult);
        assertEquals(QnsConstants.CALL_TYPE_VOICE, (int) result.mResult);
        assertFalse(mCallTracker.isCallIdle()); // for IMS calls only

        mCallTracker.unregisterCallTypeChangedListener(
                NetworkCapabilities.NET_CAPABILITY_IMS, mImsHandler);
        mCallTracker.unregisterCallTypeChangedListener(
                NetworkCapabilities.NET_CAPABILITY_EIMS, mEmergencyHandler);

        mTestCallStateList.clear();
        mCallTracker.updateCallState(mTestCallStateList);
        msg = mTestLooperListener.nextMessage();
        assertNull(msg);

        mTestCallStateList.add(new CallState.Builder(PreciseCallState.PRECISE_CALL_STATE_ACTIVE)
                .setImsCallType(ImsCallProfile.CALL_TYPE_VOICE)
                .setImsCallServiceType(ImsCallProfile.SERVICE_TYPE_EMERGENCY).build());
        mCallTracker.updateCallState(mTestCallStateList);
        msg = mTestLooperListener.nextMessage();
        assertNull(msg);
    }

    @Test
    public void testEmergencyOverImsCallTypeChangedScenarios() {
        PreciseDataConnectionState emergencyDataStatus =
                new PreciseDataConnectionState.Builder()
                        .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_INVALID)
                        .setState(TelephonyManager.DATA_DISCONNECTED)
                        .setNetworkType(AccessNetworkConstants.AccessNetworkType.EUTRAN)
                        .setApnSetting(
                                new ApnSetting.Builder()
                                        .setApnTypeBitmask(ApnSetting.TYPE_EMERGENCY)
                                        .setApnName("sos")
                                        .setEntryName("sos")
                                        .build())
                        .setLinkProperties(new LinkProperties())
                        .build();
        PreciseDataConnectionState imsDataStatus =
                new PreciseDataConnectionState.Builder()
                        .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WLAN)
                        .setState(TelephonyManager.DATA_CONNECTED)
                        .setNetworkType(AccessNetworkConstants.AccessNetworkType.IWLAN)
                        .setApnSetting(
                                new ApnSetting.Builder()
                                        .setApnTypeBitmask(ApnSetting.TYPE_IMS)
                                        .setApnName("ims")
                                        .setEntryName("ims")
                                        .build())
                        .build();

        when(mMockQnsTelephonyListener.getLastPreciseDataConnectionState(
                        NetworkCapabilities.NET_CAPABILITY_EIMS))
                .thenReturn(emergencyDataStatus);
        when(mMockQnsTelephonyListener.getLastPreciseDataConnectionState(
                        NetworkCapabilities.NET_CAPABILITY_IMS))
                .thenReturn(imsDataStatus);
        // Test1:
        mTestCallStateList.add(new CallState.Builder(PreciseCallState.PRECISE_CALL_STATE_ACTIVE)
                .setImsCallType(ImsCallProfile.CALL_TYPE_VOICE)
                .setImsCallServiceType(ImsCallProfile.SERVICE_TYPE_EMERGENCY).build());
        mCallTracker.updateCallState(mTestCallStateList);
        Message msg = mTestLooperListener.nextMessage();
        assertNotNull(msg);
        assertEquals(mImsHandler, msg.getTarget());
        QnsAsyncResult result = (QnsAsyncResult) msg.obj;
        assertNotNull(result.mResult);
        assertEquals(QnsConstants.CALL_TYPE_EMERGENCY, (int) result.mResult);
        assertFalse(mCallTracker.isCallIdle(NetworkCapabilities.NET_CAPABILITY_IMS));
        assertTrue(mCallTracker.isCallIdle(NetworkCapabilities.NET_CAPABILITY_EIMS));

        // Test2:
        mTestCallStateList.clear();
        mTestCallStateList.add(new CallState.Builder(PreciseCallState.PRECISE_CALL_STATE_ACTIVE)
                .setImsCallType(ImsCallProfile.CALL_TYPE_VOICE)
                .setImsCallServiceType(ImsCallProfile.SERVICE_TYPE_EMERGENCY).build());
        mCallTracker.updateCallState(mTestCallStateList);
        msg = mTestLooperListener.nextMessage();
        // Should not notify if call type is not changed
        assertNull(msg);

        mTestCallStateList.clear();
        mTestCallStateList.add(new CallState.Builder(PreciseCallState.PRECISE_CALL_STATE_ACTIVE)
                .setImsCallType(ImsCallProfile.CALL_TYPE_VOICE)
                .setImsCallServiceType(ImsCallProfile.SERVICE_TYPE_EMERGENCY).build());
        mTestCallStateList.add(new CallState.Builder(PreciseCallState.PRECISE_CALL_STATE_WAITING)
                .setImsCallType(ImsCallProfile.CALL_TYPE_VOICE)
                .setImsCallServiceType(ImsCallProfile.SERVICE_TYPE_NORMAL).build());
        mCallTracker.updateCallState(mTestCallStateList);
        msg = mTestLooperListener.nextMessage();
        // Should not notify if call type is not changed
        assertNull(msg);

        mTestCallStateList.clear();
        mTestCallStateList.add(new CallState.Builder(PreciseCallState.PRECISE_CALL_STATE_ACTIVE)
                .setImsCallType(ImsCallProfile.CALL_TYPE_VOICE)
                .setImsCallServiceType(ImsCallProfile.SERVICE_TYPE_EMERGENCY).build());
        mCallTracker.updateCallState(mTestCallStateList);
        msg = mTestLooperListener.nextMessage();
        // Should not notify if call type is not changed
        assertNull(msg);

        // Test3:
        imsDataStatus =
                new PreciseDataConnectionState.Builder()
                        .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_INVALID)
                        .setState(TelephonyManager.DATA_DISCONNECTED)
                        .setNetworkType(AccessNetworkConstants.AccessNetworkType.EUTRAN)
                        .setApnSetting(
                                new ApnSetting.Builder()
                                        .setApnTypeBitmask(ApnSetting.TYPE_IMS)
                                        .setApnName("ims")
                                        .setEntryName("ims")
                                        .build())
                        .build();
        Mockito.clearInvocations(mMockQnsTelephonyListener);
        when(mMockQnsTelephonyListener.getLastPreciseDataConnectionState(
                        NetworkCapabilities.NET_CAPABILITY_IMS))
                .thenReturn(imsDataStatus);

        mTestCallStateList.clear();
        mCallTracker.updateCallState(mTestCallStateList);
        msg = mTestLooperListener.nextMessage();
        assertNotNull(msg);
        assertEquals(mImsHandler, msg.getTarget());
        result = (QnsAsyncResult) msg.obj;
        assertNotNull(result.mResult);
        assertEquals(QnsConstants.CALL_TYPE_IDLE, (int) result.mResult);

        // Test4:
        emergencyDataStatus =
                new PreciseDataConnectionState.Builder()
                        .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WLAN)
                        .setState(TelephonyManager.DATA_CONNECTED)
                        .setNetworkType(AccessNetworkConstants.AccessNetworkType.IWLAN)
                        .setApnSetting(
                                new ApnSetting.Builder()
                                        .setApnTypeBitmask(ApnSetting.TYPE_EMERGENCY)
                                        .setApnName("sos")
                                        .setEntryName("sos")
                                        .build())
                        .build();
        Mockito.clearInvocations(mMockQnsTelephonyListener);
        when(mMockQnsTelephonyListener.getLastPreciseDataConnectionState(
                        NetworkCapabilities.NET_CAPABILITY_EIMS))
                .thenReturn(emergencyDataStatus);
        imsDataStatus =
                new PreciseDataConnectionState.Builder()
                        .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WLAN)
                        .setState(TelephonyManager.DATA_CONNECTED)
                        .setNetworkType(AccessNetworkConstants.AccessNetworkType.IWLAN)
                        .setApnSetting(
                                new ApnSetting.Builder()
                                        .setApnTypeBitmask(ApnSetting.TYPE_IMS)
                                        .setApnName("ims")
                                        .setEntryName("ims")
                                        .build())
                        .build();
        Mockito.clearInvocations(mMockQnsTelephonyListener);
        when(mMockQnsTelephonyListener.getLastPreciseDataConnectionState(
                        NetworkCapabilities.NET_CAPABILITY_IMS))
                .thenReturn(imsDataStatus);
        mTestCallStateList.clear();
        mTestCallStateList.add(new CallState.Builder(PreciseCallState.PRECISE_CALL_STATE_ACTIVE)
                .setImsCallType(ImsCallProfile.CALL_TYPE_VOICE)
                .setImsCallServiceType(ImsCallProfile.SERVICE_TYPE_EMERGENCY).build());
        mCallTracker.updateCallState(mTestCallStateList);
        msg = mTestLooperListener.nextMessage();
        assertNotNull(msg);
        assertEquals(mEmergencyHandler, msg.getTarget());
        result = (QnsAsyncResult) msg.obj;
        assertNotNull(result.mResult);
        assertEquals(QnsConstants.CALL_TYPE_EMERGENCY, (int) result.mResult);

        mTestCallStateList.clear();
        mCallTracker.updateCallState(mTestCallStateList);
        msg = mTestLooperListener.nextMessage();
        assertNotNull(msg);
        assertEquals(mEmergencyHandler, msg.getTarget());
        result = (QnsAsyncResult) msg.obj;
        assertNotNull(result.mResult);
        assertEquals(QnsConstants.CALL_TYPE_IDLE, (int) result.mResult);
    }


    @Test
    public void testActiveCallTrackerOnOff() {
        // Test1:
        mTestCallStateList.add(new CallState.Builder(PreciseCallState.PRECISE_CALL_STATE_INCOMING)
                .setImsCallType(ImsCallProfile.CALL_TYPE_VOICE)
                .setImsCallServiceType(ImsCallProfile.SERVICE_TYPE_NORMAL).build());
        mCallTracker.updateCallState(mTestCallStateList);
        QnsCallStatusTracker.ActiveCallTracker activeCallTracker =
                mCallTracker.getActiveCallTracker();
        assertNotNull(activeCallTracker);
        assertEquals(QnsConstants.CALL_TYPE_VOICE, activeCallTracker.getCallType());
        assertEquals(NetworkCapabilities.NET_CAPABILITY_IMS, activeCallTracker.getNetCapability());

        // Test2:
        mTestCallStateList.clear();
        mTestCallStateList.add(new CallState.Builder(PreciseCallState.PRECISE_CALL_STATE_ACTIVE)
                .setImsCallType(ImsCallProfile.CALL_TYPE_VOICE)
                .setImsCallServiceType(ImsCallProfile.SERVICE_TYPE_NORMAL).build());
        mCallTracker.updateCallState(mTestCallStateList);
        assertEquals(QnsConstants.CALL_TYPE_VOICE, activeCallTracker.getCallType());
        assertEquals(NetworkCapabilities.NET_CAPABILITY_IMS, activeCallTracker.getNetCapability());

        // Test3:
        mTestCallStateList.clear();
        mCallTracker.updateCallState(mTestCallStateList);
        assertEquals(QnsConstants.CALL_TYPE_IDLE, activeCallTracker.getCallType());
        assertEquals(QnsConstants.INVALID_VALUE, activeCallTracker.getNetCapability());

        // Test4:
        mTestCallStateList.add(new CallState.Builder(PreciseCallState.PRECISE_CALL_STATE_DIALING)
                .setImsCallType(ImsCallProfile.CALL_TYPE_VOICE)
                .setImsCallServiceType(ImsCallProfile.SERVICE_TYPE_EMERGENCY).build());
        mCallTracker.updateCallState(mTestCallStateList);
        assertEquals(QnsConstants.CALL_TYPE_IDLE, activeCallTracker.getCallType());
        assertEquals(QnsConstants.INVALID_VALUE, activeCallTracker.getNetCapability());

        // Test5:
        mTestCallStateList.clear();
        mTestCallStateList.add(new CallState.Builder(PreciseCallState.PRECISE_CALL_STATE_ACTIVE)
                .setImsCallType(ImsCallProfile.CALL_TYPE_VOICE)
                .setImsCallServiceType(ImsCallProfile.SERVICE_TYPE_EMERGENCY).build());
        mCallTracker.updateCallState(mTestCallStateList);
        assertEquals(QnsConstants.CALL_TYPE_EMERGENCY, activeCallTracker.getCallType());
        assertEquals(NetworkCapabilities.NET_CAPABILITY_EIMS, activeCallTracker.getNetCapability());

        // Test6:
        mTestCallStateList.clear();
        mCallTracker.updateCallState(mTestCallStateList);
        assertEquals(QnsConstants.CALL_TYPE_IDLE, activeCallTracker.getCallType());
        assertEquals(QnsConstants.INVALID_VALUE, activeCallTracker.getNetCapability());
    }

    @Test
    public void testActiveCallTrackerCallTypeUpdate() {
        // Test1:
        mTestCallStateList.add(new CallState.Builder(PreciseCallState.PRECISE_CALL_STATE_INCOMING)
                .setImsCallType(ImsCallProfile.CALL_TYPE_VOICE)
                .setImsCallServiceType(ImsCallProfile.SERVICE_TYPE_NORMAL).build());
        mCallTracker.updateCallState(mTestCallStateList);
        QnsCallStatusTracker.ActiveCallTracker activeCallTracker =
                mCallTracker.getActiveCallTracker();
        assertNotNull(activeCallTracker);
        assertEquals(QnsConstants.CALL_TYPE_VOICE, activeCallTracker.getCallType());
        assertEquals(NetworkCapabilities.NET_CAPABILITY_IMS, activeCallTracker.getNetCapability());

        // Test2:
        mTestCallStateList.clear();
        mTestCallStateList.add(new CallState.Builder(PreciseCallState.PRECISE_CALL_STATE_ACTIVE)
                .setImsCallType(ImsCallProfile.CALL_TYPE_VOICE)
                .setImsCallServiceType(ImsCallProfile.SERVICE_TYPE_NORMAL).build());
        mCallTracker.updateCallState(mTestCallStateList);
        assertEquals(QnsConstants.CALL_TYPE_VOICE, activeCallTracker.getCallType());
        assertEquals(NetworkCapabilities.NET_CAPABILITY_IMS, activeCallTracker.getNetCapability());

        // Test3:
        mTestCallStateList.clear();
        mTestCallStateList.add(new CallState.Builder(PreciseCallState.PRECISE_CALL_STATE_ACTIVE)
                .setImsCallType(ImsCallProfile.CALL_TYPE_VT)
                .setImsCallServiceType(ImsCallProfile.SERVICE_TYPE_NORMAL).build());
        mCallTracker.updateCallState(mTestCallStateList);
        assertEquals(QnsConstants.CALL_TYPE_VIDEO, activeCallTracker.getCallType());
        assertEquals(NetworkCapabilities.NET_CAPABILITY_IMS, activeCallTracker.getNetCapability());

        // Test4:
        mTestCallStateList.clear();
        mCallTracker.updateCallState(mTestCallStateList);
        assertEquals(QnsConstants.CALL_TYPE_IDLE, activeCallTracker.getCallType());
        assertEquals(QnsConstants.INVALID_VALUE, activeCallTracker.getNetCapability());
    }

    @Test
    public void testForActiveCallTrackerGetQualityLevel() {
        PreciseDataConnectionState imsDataStatusOnWwan =
                new PreciseDataConnectionState.Builder()
                        .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WWAN)
                        .setState(TelephonyManager.DATA_CONNECTED)
                        .setNetworkType(TelephonyManager.NETWORK_TYPE_LTE)
                        .setApnSetting(
                                new ApnSetting.Builder()
                                        .setApnTypeBitmask(ApnSetting.TYPE_IMS)
                                        .setApnName("ims")
                                        .setEntryName("ims")
                                        .build())
                        .build();

        PreciseDataConnectionState imsDataStatusOnWlan =
                new PreciseDataConnectionState.Builder()
                        .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WLAN)
                        .setState(TelephonyManager.DATA_CONNECTED)
                        .setNetworkType(TelephonyManager.NETWORK_TYPE_IWLAN)
                        .setApnSetting(
                                new ApnSetting.Builder()
                                        .setApnTypeBitmask(ApnSetting.TYPE_IMS)
                                        .setApnName("ims")
                                        .setEntryName("ims")
                                        .build())
                        .build();

        when(mMockQnsTelephonyListener.getLastPreciseDataConnectionState(
                NetworkCapabilities.NET_CAPABILITY_IMS))
                .thenReturn(imsDataStatusOnWlan);

        // Test1:
        mTestCallStateList.add(new CallState.Builder(PreciseCallState.PRECISE_CALL_STATE_INCOMING)
                .setImsCallType(ImsCallProfile.CALL_TYPE_VOICE)
                .setImsCallServiceType(ImsCallProfile.SERVICE_TYPE_NORMAL).build());
        mCallTracker.updateCallState(mTestCallStateList);
        QnsCallStatusTracker.ActiveCallTracker activeCallTracker =
                mCallTracker.getActiveCallTracker();
        assertNotNull(activeCallTracker);
        assertEquals(QnsConstants.CALL_TYPE_VOICE, activeCallTracker.getCallType());
        assertEquals(NetworkCapabilities.NET_CAPABILITY_IMS, activeCallTracker.getNetCapability());

        // Test2:
        mTestCallStateList.clear();
        mTestCallStateList.add(new CallState.Builder(PreciseCallState.PRECISE_CALL_STATE_ACTIVE)
                .setImsCallType(ImsCallProfile.CALL_TYPE_VOICE)
                .setImsCallServiceType(ImsCallProfile.SERVICE_TYPE_NORMAL).build());
        mCallTracker.updateCallState(mTestCallStateList);
        assertEquals(QnsConstants.CALL_TYPE_VOICE, activeCallTracker.getCallType());
        assertEquals(NetworkCapabilities.NET_CAPABILITY_IMS, activeCallTracker.getNetCapability());
        assertEquals(
                AccessNetworkConstants.TRANSPORT_TYPE_WLAN, activeCallTracker.getTransportType());
        // check TransportQuality is assigned for the current transport type.
        TransportQuality transportQuality =
                activeCallTracker.getLastTransportQuality(activeCallTracker.getTransportType());
        assertEquals(activeCallTracker.getTransportType(), transportQuality.mTransportType);
        assertNotNull(transportQuality.mCallQualityBlockList);
        transportQuality.mLowRtpQualityReportedTime = -1;

        mTestCallStateList.clear();
        mTestCallStateList.add(new CallState.Builder(PreciseCallState.PRECISE_CALL_STATE_ACTIVE)
                .setImsCallType(ImsCallProfile.CALL_TYPE_VOICE)
                .setImsCallServiceType(ImsCallProfile.SERVICE_TYPE_NORMAL)
                .setCallQuality(new CallQuality.Builder()
                        .setUplinkCallQualityLevel(CallQuality.CALL_QUALITY_FAIR)
                        .setDownlinkCallQualityLevel(CallQuality.CALL_QUALITY_GOOD).build())
                .build());
        long firstCallQaulityUpdateTime = 10000;
        lenient().when(QnsUtils.getSystemElapsedRealTime()).thenReturn(firstCallQaulityUpdateTime);
        mCallTracker.updateCallState(mTestCallStateList);
        mTestLooper.dispatchAll();

        transportQuality =
                activeCallTracker.getLastTransportQuality(activeCallTracker.getTransportType());
        assertNull(activeCallTracker
                .getLastTransportQuality(AccessNetworkConstants.TRANSPORT_TYPE_WWAN));
        assertEquals(1, transportQuality.mCallQualityBlockList.size());
        CallQualityBlock qualityBlock = transportQuality.mCallQualityBlockList.get(0);
        assertEquals(firstCallQaulityUpdateTime, qualityBlock.mCreatedElapsedTime);
        assertEquals(CallQuality.CALL_QUALITY_FAIR, qualityBlock.mUpLinkLevel);
        assertEquals(CallQuality.CALL_QUALITY_GOOD, qualityBlock.mDownLinkLevel);

        long testTime = 15000;
        lenient().when(QnsUtils.getSystemElapsedRealTime()).thenReturn(testTime);
        assertEquals((testTime - firstCallQaulityUpdateTime) * qualityBlock.mUpLinkLevel,
                qualityBlock.getUpLinkQualityVolume());
        assertEquals((testTime - firstCallQaulityUpdateTime) * qualityBlock.mDownLinkLevel,
                qualityBlock.getDownLinkQualityVolume());

        long handoverTime = 20000;
        lenient().when(QnsUtils.getSystemElapsedRealTime()).thenReturn(handoverTime);
        activeCallTracker.onDataConnectionStatusChanged(imsDataStatusOnWwan);
        TransportQuality oldTransportQuality =
                activeCallTracker.getLastTransportQuality(
                        AccessNetworkConstants.TRANSPORT_TYPE_WLAN);
        TransportQuality newTransportQuality =
                activeCallTracker.getLastTransportQuality(activeCallTracker.getTransportType());
        assertEquals(1, oldTransportQuality.mCallQualityBlockList.size());
        CallQualityBlock oldQualityBlock = oldTransportQuality.getLastCallQualityBlock();
        CallQualityBlock newQualityBlock = newTransportQuality.getLastCallQualityBlock();
        assertEquals(firstCallQaulityUpdateTime, oldQualityBlock.mCreatedElapsedTime);
        assertEquals(
                handoverTime - firstCallQaulityUpdateTime, oldQualityBlock.mDurationMillis);
        assertEquals(CallQuality.CALL_QUALITY_FAIR, oldQualityBlock.mUpLinkLevel);
        assertEquals(CallQuality.CALL_QUALITY_GOOD, oldQualityBlock.mDownLinkLevel);

        assertEquals(1, newTransportQuality.mCallQualityBlockList.size());
        assertEquals(handoverTime, newQualityBlock.mCreatedElapsedTime);
        assertEquals(0, newQualityBlock.mDurationMillis);
        assertEquals(CallQuality.CALL_QUALITY_FAIR, newQualityBlock.mUpLinkLevel);
        assertEquals(CallQuality.CALL_QUALITY_GOOD, newQualityBlock.mDownLinkLevel);

        long testTime2 = 22000;
        lenient().when(QnsUtils.getSystemElapsedRealTime()).thenReturn(testTime2);
        assertEquals((handoverTime - firstCallQaulityUpdateTime)
                * oldQualityBlock.mUpLinkLevel, oldQualityBlock.getUpLinkQualityVolume());
        assertEquals((handoverTime - firstCallQaulityUpdateTime)
                * oldQualityBlock.mDownLinkLevel, oldQualityBlock.getDownLinkQualityVolume());
        assertEquals((testTime2 - handoverTime) * newQualityBlock.mUpLinkLevel,
                newQualityBlock.getUpLinkQualityVolume());
        assertEquals((testTime2 - handoverTime) * newQualityBlock.mDownLinkLevel,
                newQualityBlock.getDownLinkQualityVolume());

        long secondCallQualityUpdateTime = 25000;
        lenient().when(QnsUtils.getSystemElapsedRealTime()).thenReturn(secondCallQualityUpdateTime);
        mTestCallStateList.clear();
        mTestCallStateList.add(new CallState.Builder(PreciseCallState.PRECISE_CALL_STATE_ACTIVE)
                .setImsCallType(ImsCallProfile.CALL_TYPE_VOICE)
                .setImsCallServiceType(ImsCallProfile.SERVICE_TYPE_NORMAL)
                .setCallQuality(new CallQuality.Builder()
                        .setUplinkCallQualityLevel(CallQuality.CALL_QUALITY_GOOD)
                        .setDownlinkCallQualityLevel(CallQuality.CALL_QUALITY_FAIR).build())
                .build());
        mCallTracker.updateCallState(mTestCallStateList);
        mTestLooper.dispatchAll();
        TransportQuality testTransportQuality =
                activeCallTracker.getLastTransportQuality(activeCallTracker.getTransportType());
        assertEquals(2, newTransportQuality.mCallQualityBlockList.size());
        CallQualityBlock prev = testTransportQuality.mCallQualityBlockList.get(0);
        assertEquals(CallQuality.CALL_QUALITY_FAIR, prev.mUpLinkLevel);
        assertEquals(CallQuality.CALL_QUALITY_GOOD, prev.mDownLinkLevel);
        assertEquals(handoverTime, prev.mCreatedElapsedTime);
        assertEquals(secondCallQualityUpdateTime - handoverTime, prev.mDurationMillis);

        newQualityBlock = testTransportQuality.getLastCallQualityBlock();
        assertEquals(secondCallQualityUpdateTime, newQualityBlock.mCreatedElapsedTime);
        assertEquals(0, newQualityBlock.mDurationMillis);
        assertEquals(CallQuality.CALL_QUALITY_GOOD, newQualityBlock.mUpLinkLevel);
        assertEquals(CallQuality.CALL_QUALITY_FAIR, newQualityBlock.mDownLinkLevel);

        long handoverTime2 = 30000;
        lenient().when(QnsUtils.getSystemElapsedRealTime()).thenReturn(handoverTime2);
        activeCallTracker.onDataConnectionStatusChanged(imsDataStatusOnWlan);

        oldTransportQuality =
                activeCallTracker.getLastTransportQuality(
                        AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
        newTransportQuality =
                activeCallTracker.getLastTransportQuality(activeCallTracker.getTransportType());
        assertEquals(2, oldTransportQuality.mCallQualityBlockList.size());
        oldQualityBlock = oldTransportQuality.getLastCallQualityBlock();
        newQualityBlock = newTransportQuality.getLastCallQualityBlock();
        assertEquals(secondCallQualityUpdateTime, oldQualityBlock.mCreatedElapsedTime);
        assertEquals(
                handoverTime2 - secondCallQualityUpdateTime, oldQualityBlock.mDurationMillis);
        assertEquals(CallQuality.CALL_QUALITY_GOOD, oldQualityBlock.mUpLinkLevel);
        assertEquals(CallQuality.CALL_QUALITY_FAIR, oldQualityBlock.mDownLinkLevel);

        assertEquals(1, newTransportQuality.mCallQualityBlockList.size());
        assertEquals(handoverTime2, newQualityBlock.mCreatedElapsedTime);
        assertEquals(0, newQualityBlock.mDurationMillis);
        assertEquals(CallQuality.CALL_QUALITY_GOOD, newQualityBlock.mUpLinkLevel);
        assertEquals(CallQuality.CALL_QUALITY_FAIR, newQualityBlock.mDownLinkLevel);

        // Test3:
        long callEndTime = 40000;
        lenient().when(QnsUtils.getSystemElapsedRealTime()).thenReturn(callEndTime);
        mTestCallStateList.clear();
        mCallTracker.updateCallState(mTestCallStateList);
        assertEquals(QnsConstants.CALL_TYPE_IDLE, activeCallTracker.getCallType());
        assertEquals(QnsConstants.INVALID_VALUE, activeCallTracker.getNetCapability());

        oldTransportQuality = activeCallTracker.getLastTransportQuality(
                        AccessNetworkConstants.TRANSPORT_TYPE_WLAN);
        assertEquals(1, oldTransportQuality.mCallQualityBlockList.size());
        oldQualityBlock = oldTransportQuality.getLastCallQualityBlock();
        assertEquals(handoverTime2, oldQualityBlock.mCreatedElapsedTime);
        assertEquals(
                callEndTime - handoverTime2, oldQualityBlock.mDurationMillis);
        assertEquals(CallQuality.CALL_QUALITY_GOOD, oldQualityBlock.mUpLinkLevel);
        assertEquals(CallQuality.CALL_QUALITY_FAIR, oldQualityBlock.mDownLinkLevel);

        List<TransportQuality> wlanTransportQualityList =
                activeCallTracker.getTransportQualityList(
                        AccessNetworkConstants.TRANSPORT_TYPE_WLAN);
        List<TransportQuality> wwanTransportQualityList =
                activeCallTracker.getTransportQualityList(
                        AccessNetworkConstants.TRANSPORT_TYPE_WWAN);
        assertEquals(2, wlanTransportQualityList.size());
        assertEquals(1, wwanTransportQualityList.size());
        long expectedUplinkQualityLevelWlan =
                ((handoverTime - firstCallQaulityUpdateTime) * CallQuality.CALL_QUALITY_FAIR
                + (callEndTime - handoverTime2) * CallQuality.CALL_QUALITY_GOOD)
                / ((handoverTime - firstCallQaulityUpdateTime)
                    + (callEndTime - handoverTime2));
        assertEquals(expectedUplinkQualityLevelWlan, activeCallTracker
                .getUpLinkQualityLevelDuringCall(AccessNetworkConstants.TRANSPORT_TYPE_WLAN));
        long expectedDownLinkQualityLevelWlan =
                ((handoverTime - firstCallQaulityUpdateTime) * CallQuality.CALL_QUALITY_GOOD
                        + (callEndTime - handoverTime2) * CallQuality.CALL_QUALITY_FAIR)
                        / ((handoverTime - firstCallQaulityUpdateTime)
                        + (callEndTime - handoverTime2));
        assertEquals(expectedDownLinkQualityLevelWlan, activeCallTracker
                .getDownLinkQualityLevelDuringCall(AccessNetworkConstants.TRANSPORT_TYPE_WLAN));
    }

    @Test
    public void testMediaQualityBreachedWithJitter() {
        QnsCarrierConfigManager.RtpMetricsConfig config =
                new QnsCarrierConfigManager.RtpMetricsConfig(120, 30, 5, 10);
        when(mMockQnsConfigManager.getRTPMetricsData()).thenReturn(config);

        PreciseDataConnectionState imsDataStatusOnWlan =
                new PreciseDataConnectionState.Builder()
                        .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WLAN)
                        .setState(TelephonyManager.DATA_CONNECTED)
                        .setNetworkType(TelephonyManager.NETWORK_TYPE_IWLAN)
                        .setApnSetting(
                                new ApnSetting.Builder()
                                        .setApnTypeBitmask(ApnSetting.TYPE_IMS)
                                        .setApnName("ims")
                                        .setEntryName("ims")
                                        .build())
                        .build();

        when(mMockQnsTelephonyListener.getLastPreciseDataConnectionState(
                NetworkCapabilities.NET_CAPABILITY_IMS))
                .thenReturn(imsDataStatusOnWlan);
        mTestCallStateList.add(new CallState.Builder(PreciseCallState.PRECISE_CALL_STATE_INCOMING)
                .setImsCallType(ImsCallProfile.CALL_TYPE_VOICE)
                .setImsCallServiceType(ImsCallProfile.SERVICE_TYPE_NORMAL).build());
        mCallTracker.updateCallState(mTestCallStateList);
        QnsCallStatusTracker.ActiveCallTracker activeCallTracker =
                mCallTracker.getActiveCallTracker();
        assertNotNull(activeCallTracker);
        assertEquals(QnsConstants.CALL_TYPE_VOICE, activeCallTracker.getCallType());
        assertEquals(NetworkCapabilities.NET_CAPABILITY_IMS, activeCallTracker.getNetCapability());
        mTestCallStateList.clear();
        mTestCallStateList.add(new CallState.Builder(PreciseCallState.PRECISE_CALL_STATE_ACTIVE)
                .setImsCallType(ImsCallProfile.CALL_TYPE_VOICE)
                .setImsCallServiceType(ImsCallProfile.SERVICE_TYPE_NORMAL).build());
        mCallTracker.updateCallState(mTestCallStateList);
        assertEquals(QnsConstants.CALL_TYPE_VOICE, activeCallTracker.getCallType());
        assertEquals(NetworkCapabilities.NET_CAPABILITY_IMS, activeCallTracker.getNetCapability());

        MediaQualityStatus status =
                new MediaQualityStatus(
                        "1", MediaQualityStatus.MEDIA_SESSION_TYPE_AUDIO,
                        AccessNetworkConstants.TRANSPORT_TYPE_WLAN,
                        10 /*packetLossRate*/, 130 /*jitter*/, 0 /*inactivityTime*/);
        activeCallTracker.onMediaQualityStatusChanged(status);
        mTestLooper.dispatchAll();
        Message msg = mLowQualityListenerLooper.nextMessage();
        assertNotNull(msg);
        assertEquals(mLowQualityHandler, msg.getTarget());
        QnsAsyncResult result = (QnsAsyncResult) msg.obj;
        assertNotNull(result.mResult);
        assertEquals(1 << QnsConstants.RTP_LOW_QUALITY_REASON_JITTER, (int) result.mResult);
    }

    @Test
    public void testMediaQualityBreachedWithNoRtp() {
        QnsCarrierConfigManager.RtpMetricsConfig config =
                new QnsCarrierConfigManager.RtpMetricsConfig(120, 30, 5, 10000);
        when(mMockQnsConfigManager.getRTPMetricsData()).thenReturn(config);

        PreciseDataConnectionState emergencyDataStatusOnWwan =
                new PreciseDataConnectionState.Builder()
                        .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WWAN)
                        .setState(TelephonyManager.DATA_CONNECTED)
                        .setNetworkType(TelephonyManager.NETWORK_TYPE_LTE)
                        .setApnSetting(
                                new ApnSetting.Builder()
                                        .setApnTypeBitmask(ApnSetting.TYPE_EMERGENCY)
                                        .setApnName("sos")
                                        .setEntryName("sos")
                                        .build())
                        .build();

        when(mMockQnsTelephonyListener.getLastPreciseDataConnectionState(
                NetworkCapabilities.NET_CAPABILITY_EIMS))
                .thenReturn(emergencyDataStatusOnWwan);
        mTestCallStateList.add(new CallState.Builder(PreciseCallState.PRECISE_CALL_STATE_ALERTING)
                .setImsCallType(ImsCallProfile.CALL_TYPE_VOICE)
                .setImsCallServiceType(ImsCallProfile.SERVICE_TYPE_EMERGENCY).build());
        mCallTracker.updateCallState(mTestCallStateList);
        QnsCallStatusTracker.ActiveCallTracker activeCallTracker =
                mCallTracker.getActiveCallTracker();
        assertNotNull(activeCallTracker);
        assertEquals(QnsConstants.CALL_TYPE_IDLE, activeCallTracker.getCallType());
        assertEquals(QnsConstants.INVALID_VALUE, activeCallTracker.getNetCapability());
        mTestCallStateList.clear();
        mTestCallStateList.add(new CallState.Builder(PreciseCallState.PRECISE_CALL_STATE_ACTIVE)
                .setImsCallType(ImsCallProfile.CALL_TYPE_VOICE)
                .setImsCallServiceType(ImsCallProfile.SERVICE_TYPE_EMERGENCY).build());
        mCallTracker.updateCallState(mTestCallStateList);
        assertEquals(QnsConstants.CALL_TYPE_EMERGENCY, activeCallTracker.getCallType());
        assertEquals(NetworkCapabilities.NET_CAPABILITY_EIMS, activeCallTracker.getNetCapability());

        MediaQualityStatus status =
                new MediaQualityStatus(
                        "1", MediaQualityStatus.MEDIA_SESSION_TYPE_AUDIO,
                        AccessNetworkConstants.TRANSPORT_TYPE_WLAN,
                        10 /*packetLossRate*/, 70 /*jitter*/, 11000 /*inactivityTime*/);
        activeCallTracker.onMediaQualityStatusChanged(status);
        mTestLooper.dispatchAll();
        Message msg = mLowQualityListenerLooper.nextMessage();
        assertNotNull(msg);
        assertEquals(mLowQualityHandler, msg.getTarget());
        QnsAsyncResult result = (QnsAsyncResult) msg.obj;
        assertNotNull(result.mResult);
        assertEquals(1 << QnsConstants.RTP_LOW_QUALITY_REASON_NO_RTP, (int) result.mResult);
    }

    @Test
    public void testMediaQualityBreachedWithPacketLoss() {
        QnsCarrierConfigManager.RtpMetricsConfig config =
                new QnsCarrierConfigManager.RtpMetricsConfig(120, 30, 5000, 10000);
        when(mMockQnsConfigManager.getRTPMetricsData()).thenReturn(config);

        PreciseDataConnectionState emergencyDataStatusOnWwan =
                new PreciseDataConnectionState.Builder()
                        .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WWAN)
                        .setState(TelephonyManager.DATA_CONNECTED)
                        .setNetworkType(TelephonyManager.NETWORK_TYPE_LTE)
                        .setApnSetting(
                                new ApnSetting.Builder()
                                        .setApnTypeBitmask(ApnSetting.TYPE_EMERGENCY)
                                        .setApnName("sos")
                                        .setEntryName("sos")
                                        .build())
                        .build();

        when(mMockQnsTelephonyListener.getLastPreciseDataConnectionState(
                NetworkCapabilities.NET_CAPABILITY_EIMS))
                .thenReturn(emergencyDataStatusOnWwan);
        mTestCallStateList.add(new CallState.Builder(PreciseCallState.PRECISE_CALL_STATE_ALERTING)
                .setImsCallType(ImsCallProfile.CALL_TYPE_VOICE)
                .setImsCallServiceType(ImsCallProfile.SERVICE_TYPE_EMERGENCY).build());
        mCallTracker.updateCallState(mTestCallStateList);
        QnsCallStatusTracker.ActiveCallTracker activeCallTracker =
                mCallTracker.getActiveCallTracker();
        assertNotNull(activeCallTracker);
        assertEquals(QnsConstants.CALL_TYPE_IDLE, activeCallTracker.getCallType());
        assertEquals(QnsConstants.INVALID_VALUE, activeCallTracker.getNetCapability());
        mTestCallStateList.clear();
        mTestCallStateList.add(new CallState.Builder(PreciseCallState.PRECISE_CALL_STATE_ACTIVE)
                .setImsCallType(ImsCallProfile.CALL_TYPE_VOICE)
                .setImsCallServiceType(ImsCallProfile.SERVICE_TYPE_EMERGENCY).build());
        mCallTracker.updateCallState(mTestCallStateList);
        assertEquals(QnsConstants.CALL_TYPE_EMERGENCY, activeCallTracker.getCallType());
        assertEquals(NetworkCapabilities.NET_CAPABILITY_EIMS, activeCallTracker.getNetCapability());

        MediaQualityStatus status =
                new MediaQualityStatus(
                        "1", MediaQualityStatus.MEDIA_SESSION_TYPE_AUDIO,
                        AccessNetworkConstants.TRANSPORT_TYPE_WLAN,
                        35 /*packetLossRate*/, 70 /*jitter*/, 7000 /*inactivityTime*/);
        activeCallTracker.onMediaQualityStatusChanged(status);
        mTestLooper.dispatchAll();
        Message msg = mLowQualityListenerLooper.nextMessage();
        assertNull(msg);
        mTestLooper.moveTimeForward(config.mPktLossTime - 1000);
        mTestLooper.dispatchAll();
        msg = mLowQualityListenerLooper.nextMessage();
        assertNull(msg);
        mTestLooper.moveTimeForward(2000);
        mTestLooper.dispatchAll();
        msg = mLowQualityListenerLooper.nextMessage();
        assertNotNull(msg);
        assertEquals(mLowQualityHandler, msg.getTarget());
        QnsAsyncResult result = (QnsAsyncResult) msg.obj;
        assertNotNull(result.mResult);
        assertEquals(
                1 << QnsConstants.RTP_LOW_QUALITY_REASON_PACKET_LOSS, (int) result.mResult);
    }

    @Test
    public void testMediaQualityBreachedWithPacketLossAdvanced() {
        QnsCarrierConfigManager.RtpMetricsConfig config =
                new QnsCarrierConfigManager.RtpMetricsConfig(120, 30, 12000, 10000);
        when(mMockQnsConfigManager.getRTPMetricsData()).thenReturn(config);

        PreciseDataConnectionState emergencyDataStatusOnWwan =
                new PreciseDataConnectionState.Builder()
                        .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WWAN)
                        .setState(TelephonyManager.DATA_CONNECTED)
                        .setNetworkType(TelephonyManager.NETWORK_TYPE_LTE)
                        .setApnSetting(
                                new ApnSetting.Builder()
                                        .setApnTypeBitmask(ApnSetting.TYPE_EMERGENCY)
                                        .setApnName("sos")
                                        .setEntryName("sos")
                                        .build())
                        .build();

        when(mMockQnsTelephonyListener.getLastPreciseDataConnectionState(
                NetworkCapabilities.NET_CAPABILITY_EIMS))
                .thenReturn(emergencyDataStatusOnWwan);
        mTestCallStateList.add(new CallState.Builder(PreciseCallState.PRECISE_CALL_STATE_ALERTING)
                .setImsCallType(ImsCallProfile.CALL_TYPE_VOICE)
                .setImsCallServiceType(ImsCallProfile.SERVICE_TYPE_EMERGENCY).build());
        mCallTracker.updateCallState(mTestCallStateList);
        QnsCallStatusTracker.ActiveCallTracker activeCallTracker =
                mCallTracker.getActiveCallTracker();
        assertNotNull(activeCallTracker);
        assertEquals(QnsConstants.CALL_TYPE_IDLE, activeCallTracker.getCallType());
        assertEquals(QnsConstants.INVALID_VALUE, activeCallTracker.getNetCapability());
        mTestCallStateList.clear();
        mTestCallStateList.add(new CallState.Builder(PreciseCallState.PRECISE_CALL_STATE_ACTIVE)
                .setImsCallType(ImsCallProfile.CALL_TYPE_VOICE)
                .setImsCallServiceType(ImsCallProfile.SERVICE_TYPE_EMERGENCY).build());
        mCallTracker.updateCallState(mTestCallStateList);
        assertEquals(QnsConstants.CALL_TYPE_EMERGENCY, activeCallTracker.getCallType());
        assertEquals(NetworkCapabilities.NET_CAPABILITY_EIMS, activeCallTracker.getNetCapability());

        MediaQualityStatus status =
                new MediaQualityStatus(
                        "1", MediaQualityStatus.MEDIA_SESSION_TYPE_AUDIO,
                        AccessNetworkConstants.TRANSPORT_TYPE_WLAN,
                        35 /*packetLossRate*/, 70 /*jitter*/, 7000 /*inactivityTime*/);
        activeCallTracker.onMediaQualityStatusChanged(status);
        mTestLooper.dispatchAll();
        Message msg = mLowQualityListenerLooper.nextMessage();
        assertNull(msg);
        mTestLooper.moveTimeForward(config.mPktLossTime / 3);
        mTestLooper.dispatchAll();
        msg = mLowQualityListenerLooper.nextMessage();
        assertNull(msg);

        status =
                new MediaQualityStatus(
                        "1", MediaQualityStatus.MEDIA_SESSION_TYPE_AUDIO,
                        AccessNetworkConstants.TRANSPORT_TYPE_WLAN,
                        10 /*packetLossRate*/, 70 /*jitter*/, 0 /*inactivityTime*/);
        activeCallTracker.onMediaQualityStatusChanged(status);
        mTestLooper.dispatchAll();
        msg = mLowQualityListenerLooper.nextMessage();
        assertNull(msg);
        mTestLooper.moveTimeForward(4000);
        mTestLooper.dispatchAll();
        msg = mLowQualityListenerLooper.nextMessage();
        assertNotNull(msg);
        assertEquals(mLowQualityHandler, msg.getTarget());
        QnsAsyncResult result = (QnsAsyncResult) msg.obj;
        assertNotNull(result.mResult);
        assertEquals(0, (int) result.mResult);

        status =
                new MediaQualityStatus(
                        "1", MediaQualityStatus.MEDIA_SESSION_TYPE_AUDIO,
                        AccessNetworkConstants.TRANSPORT_TYPE_WLAN,
                        33 /*packetLossRate*/, 70 /*jitter*/, 7000 /*inactivityTime*/);
        activeCallTracker.onMediaQualityStatusChanged(status);
        mTestLooper.dispatchAll();
        msg = mLowQualityListenerLooper.nextMessage();
        assertNull(msg);
        mTestLooper.moveTimeForward(config.mPktLossTime / 3);
        mTestLooper.dispatchAll();
        msg = mLowQualityListenerLooper.nextMessage();
        assertNull(msg);

        status =
                new MediaQualityStatus(
                        "1", MediaQualityStatus.MEDIA_SESSION_TYPE_AUDIO,
                        AccessNetworkConstants.TRANSPORT_TYPE_WLAN,
                        10 /*packetLossRate*/, 70 /*jitter*/, 0 /*inactivityTime*/);
        activeCallTracker.onMediaQualityStatusChanged(status);
        mTestLooper.dispatchAll();
        msg = mLowQualityListenerLooper.nextMessage();
        assertNull(msg);
        mTestLooper.moveTimeForward(2000);
        mTestLooper.dispatchAll();
        msg = mLowQualityListenerLooper.nextMessage();
        assertNull(msg);

        status =
                new MediaQualityStatus(
                        "1", MediaQualityStatus.MEDIA_SESSION_TYPE_AUDIO,
                        AccessNetworkConstants.TRANSPORT_TYPE_WLAN,
                        33 /*packetLossRate*/, 70 /*jitter*/, 7000 /*inactivityTime*/);
        activeCallTracker.onMediaQualityStatusChanged(status);
        mTestLooper.dispatchAll();
        msg = mLowQualityListenerLooper.nextMessage();
        assertNull(msg);
        mTestLooper.moveTimeForward(config.mPktLossTime * 2 / 3);
        mTestLooper.dispatchAll();
        msg = mLowQualityListenerLooper.nextMessage();
        assertNotNull(msg);
        assertEquals(mLowQualityHandler, msg.getTarget());
        result = (QnsAsyncResult) msg.obj;
        assertNotNull(result.mResult);
        assertEquals(
                1 << QnsConstants.RTP_LOW_QUALITY_REASON_PACKET_LOSS, (int) result.mResult);
    }

    @Test
    public void testHandlingLowQualityEventAtHandover() {
        QnsCarrierConfigManager.RtpMetricsConfig config =
                new QnsCarrierConfigManager.RtpMetricsConfig(120, 30, 12000, 10000);
        when(mMockQnsConfigManager.getRTPMetricsData()).thenReturn(config);
        PreciseDataConnectionState imsDataStatusOnWlan =
                new PreciseDataConnectionState.Builder()
                        .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WLAN)
                        .setState(TelephonyManager.DATA_CONNECTED)
                        .setNetworkType(TelephonyManager.NETWORK_TYPE_IWLAN)
                        .setApnSetting(
                                new ApnSetting.Builder()
                                        .setApnTypeBitmask(ApnSetting.TYPE_EMERGENCY)
                                        .setApnName("ims")
                                        .setEntryName("ims")
                                        .build())
                        .build();
        PreciseDataConnectionState imsDataStatusOnWwan =
                new PreciseDataConnectionState.Builder()
                        .setTransportType(AccessNetworkConstants.TRANSPORT_TYPE_WWAN)
                        .setState(TelephonyManager.DATA_CONNECTED)
                        .setNetworkType(TelephonyManager.NETWORK_TYPE_LTE)
                        .setApnSetting(
                                new ApnSetting.Builder()
                                        .setApnTypeBitmask(ApnSetting.TYPE_EMERGENCY)
                                        .setApnName("ims")
                                        .setEntryName("ims")
                                        .build())
                        .build();
        when(mMockQnsTelephonyListener.getLastPreciseDataConnectionState(
                NetworkCapabilities.NET_CAPABILITY_IMS))
                .thenReturn(imsDataStatusOnWwan);
        mTestCallStateList.add(new CallState.Builder(PreciseCallState.PRECISE_CALL_STATE_DIALING)
                .setImsCallType(ImsCallProfile.CALL_TYPE_VOICE)
                .setImsCallServiceType(ImsCallProfile.SERVICE_TYPE_NORMAL).build());
        mCallTracker.updateCallState(mTestCallStateList);
        QnsCallStatusTracker.ActiveCallTracker activeCallTracker =
                mCallTracker.getActiveCallTracker();
        assertNotNull(activeCallTracker);
        assertEquals(QnsConstants.CALL_TYPE_VOICE, activeCallTracker.getCallType());
        assertEquals(NetworkCapabilities.NET_CAPABILITY_IMS, activeCallTracker.getNetCapability());
        mTestCallStateList.clear();
        mTestCallStateList.add(new CallState.Builder(PreciseCallState.PRECISE_CALL_STATE_ACTIVE)
                .setImsCallType(ImsCallProfile.CALL_TYPE_VOICE)
                .setImsCallServiceType(ImsCallProfile.SERVICE_TYPE_NORMAL).build());
        mCallTracker.updateCallState(mTestCallStateList);
        assertEquals(QnsConstants.CALL_TYPE_VOICE, activeCallTracker.getCallType());
        assertEquals(NetworkCapabilities.NET_CAPABILITY_IMS, activeCallTracker.getNetCapability());

        MediaQualityStatus status =
                new MediaQualityStatus(
                        "1", MediaQualityStatus.MEDIA_SESSION_TYPE_AUDIO,
                        AccessNetworkConstants.TRANSPORT_TYPE_WWAN,
                        35 /*packetLossRate*/, 70 /*jitter*/, 0 /*inactivityTime*/);
        activeCallTracker.onMediaQualityStatusChanged(status);
        mTestLooper.dispatchAll();
        Message msg = mLowQualityListenerLooper.nextMessage();
        assertNull(msg);
        mTestLooper.moveTimeForward(4000);
        mTestLooper.dispatchAll();
        msg = mLowQualityListenerLooper.nextMessage();
        assertNull(msg);

        activeCallTracker.onDataConnectionStatusChanged(imsDataStatusOnWlan);

        mTestLooper.moveTimeForward(10000);
        mTestLooper.dispatchAll();
        msg = mLowQualityListenerLooper.nextMessage();
        assertNull(msg);

        mTestLooper.moveTimeForward(2500);
        mTestLooper.dispatchAll();
        msg = mLowQualityListenerLooper.nextMessage();
        assertNotNull(msg);
        assertEquals(mLowQualityHandler, msg.getTarget());
        QnsAsyncResult result = (QnsAsyncResult) msg.obj;
        assertNotNull(result.mResult);
        assertEquals(
                1 << QnsConstants.RTP_LOW_QUALITY_REASON_PACKET_LOSS, (int) result.mResult);

        mTestLooper.moveTimeForward(1000);
        mTestLooper.dispatchAll();
        msg = mLowQualityListenerLooper.nextMessage();
        assertNull(msg);
        activeCallTracker.onDataConnectionStatusChanged(imsDataStatusOnWlan);
        mTestLooper.moveTimeForward(1000);
        mTestLooper.dispatchAll();
        msg = mLowQualityListenerLooper.nextMessage();
        assertNull(msg);

        status =
                new MediaQualityStatus(
                        "1", MediaQualityStatus.MEDIA_SESSION_TYPE_AUDIO,
                        AccessNetworkConstants.TRANSPORT_TYPE_WLAN,
                        5 /*packetLossRate*/, 20 /*jitter*/, 0 /*inactivityTime*/);
        activeCallTracker.onMediaQualityStatusChanged(status);
        mTestLooper.dispatchAll();
        msg = mLowQualityListenerLooper.nextMessage();
        assertNull(msg);
        mTestLooper.moveTimeForward(2100);
        mTestLooper.dispatchAll();
        msg = mLowQualityListenerLooper.nextMessage();
        assertNull(msg);
        mTestLooper.moveTimeForward(3100);
        mTestLooper.dispatchAll();
        msg = mLowQualityListenerLooper.nextMessage();
        assertNotNull(msg);
        assertEquals(mLowQualityHandler, msg.getTarget());
        result = (QnsAsyncResult) msg.obj;
        assertNotNull(result.mResult);
        assertEquals(0, (int) result.mResult);

        mTestLooper.moveTimeForward(10000);
        mTestLooper.dispatchAll();
        status =
                new MediaQualityStatus(
                        "1", MediaQualityStatus.MEDIA_SESSION_TYPE_AUDIO,
                        AccessNetworkConstants.TRANSPORT_TYPE_WLAN,
                        100 /*packetLossRate*/, 20 /*jitter*/, 11000 /*inactivityTime*/);
        activeCallTracker.onMediaQualityStatusChanged(status);
        mTestLooper.dispatchAll();
        msg = mLowQualityListenerLooper.nextMessage();
        assertNotNull(msg);
        assertEquals(mLowQualityHandler, msg.getTarget());
        result = (QnsAsyncResult) msg.obj;
        assertNotNull(result.mResult);
        assertEquals((1 << QnsConstants.RTP_LOW_QUALITY_REASON_NO_RTP)
                + (1 << QnsConstants.RTP_LOW_QUALITY_REASON_PACKET_LOSS), (int) result.mResult);

        status =
                new MediaQualityStatus(
                        "1", MediaQualityStatus.MEDIA_SESSION_TYPE_AUDIO,
                        AccessNetworkConstants.TRANSPORT_TYPE_WLAN,
                        12 /*packetLossRate*/, 20 /*jitter*/, 0 /*inactivityTime*/);
        activeCallTracker.onMediaQualityStatusChanged(status);
        mTestLooper.dispatchAll();
        msg = mLowQualityListenerLooper.nextMessage();
        assertNull(msg);
        mTestLooper.moveTimeForward(2000);
        mTestLooper.dispatchAll();
        msg = mLowQualityListenerLooper.nextMessage();
        assertNull(msg);

        activeCallTracker.onDataConnectionStatusChanged(imsDataStatusOnWwan);
        mTestLooper.moveTimeForward(2000);
        mTestLooper.dispatchAll();
        msg = mLowQualityListenerLooper.nextMessage();
        assertNull(msg);
        mTestLooper.moveTimeForward(1100);
        mTestLooper.dispatchAll();
        msg = mLowQualityListenerLooper.nextMessage();
        assertNotNull(msg);
        assertEquals(mLowQualityHandler, msg.getTarget());
        result = (QnsAsyncResult) msg.obj;
        assertNotNull(result.mResult);
        assertEquals(0, (int) result.mResult);
    }

    @Test
    public void testOnSrvccStateChanged() {
        mTestCallStateList.add(new CallState.Builder(PreciseCallState.PRECISE_CALL_STATE_ACTIVE)
                .setImsCallType(ImsCallProfile.CALL_TYPE_VOICE)
                .setImsCallServiceType(ImsCallProfile.SERVICE_TYPE_EMERGENCY).build());
        mCallTracker.updateCallState(mTestCallStateList);
        Message msg = mTestLooperListener.nextMessage();
        assertNotNull(msg);
        assertEquals(mEmergencyHandler, msg.getTarget());
        QnsAsyncResult result = (QnsAsyncResult) msg.obj;
        assertNotNull(result.mResult);
        assertEquals(QnsConstants.CALL_TYPE_EMERGENCY, (int) result.mResult);
        mCallTracker.onSrvccStateChangedInternal(TelephonyManager.SRVCC_STATE_HANDOVER_COMPLETED);
        msg = mTestLooperListener.nextMessage();
        assertNotNull(msg);
        result = (QnsAsyncResult) msg.obj;
        assertNotNull(result.mResult);
        assertEquals(QnsConstants.CALL_TYPE_IDLE, (int) result.mResult);

        mCallTracker.onSrvccStateChangedInternal(TelephonyManager.SRVCC_STATE_HANDOVER_STARTED);
        msg = mTestLooperListener.nextMessage();
        assertNull(msg);
    }

    @Test
    public void isIdleState() {
        mTestCallStateList.clear();
        mCallTracker.updateCallState(mTestCallStateList);
        assertTrue(mCallTracker.isCallIdle());
    }

    @Test
    public void testThresholdBreached() {
        QnsCarrierConfigManager.RtpMetricsConfig config =
                new QnsCarrierConfigManager.RtpMetricsConfig(120, 30, 12000, 10000);
        when(mMockQnsConfigManager.getRTPMetricsData()).thenReturn(config);

        MediaQualityStatus status =
                new MediaQualityStatus(
                        "1", MediaQualityStatus.MEDIA_SESSION_TYPE_AUDIO,
                        AccessNetworkConstants.TRANSPORT_TYPE_WWAN,
                        30 /*packetLossRate*/, 0 /*jitter*/, 0 /*inactivityTime*/);
        assertEquals(1 << QnsConstants.RTP_LOW_QUALITY_REASON_PACKET_LOSS,
                mCallTracker.getActiveCallTracker().thresholdBreached(status));
        status =
                new MediaQualityStatus(
                        "1", MediaQualityStatus.MEDIA_SESSION_TYPE_AUDIO,
                        AccessNetworkConstants.TRANSPORT_TYPE_WWAN,
                        29 /*packetLossRate*/, 0 /*jitter*/, 0 /*inactivityTime*/);
        assertEquals(0, mCallTracker.getActiveCallTracker().thresholdBreached(status));

        status =
                new MediaQualityStatus(
                        "1", MediaQualityStatus.MEDIA_SESSION_TYPE_AUDIO,
                        AccessNetworkConstants.TRANSPORT_TYPE_WWAN,
                        0 /*packetLossRate*/, 120 /*jitter*/, 0 /*inactivityTime*/);
        assertEquals(1 << QnsConstants.RTP_LOW_QUALITY_REASON_JITTER,
                mCallTracker.getActiveCallTracker().thresholdBreached(status));
        status =
                new MediaQualityStatus(
                        "1", MediaQualityStatus.MEDIA_SESSION_TYPE_AUDIO,
                        AccessNetworkConstants.TRANSPORT_TYPE_WWAN,
                        0 /*packetLossRate*/, 119 /*jitter*/, 0 /*inactivityTime*/);
        assertEquals(0, mCallTracker.getActiveCallTracker().thresholdBreached(status));

        status =
                new MediaQualityStatus(
                        "1", MediaQualityStatus.MEDIA_SESSION_TYPE_AUDIO,
                        AccessNetworkConstants.TRANSPORT_TYPE_WWAN,
                        0 /*packetLossRate*/, 0 /*jitter*/, 10000 /*inactivityTime*/);
        assertEquals(1 << QnsConstants.RTP_LOW_QUALITY_REASON_NO_RTP,
                mCallTracker.getActiveCallTracker().thresholdBreached(status));
        status =
                new MediaQualityStatus(
                        "1", MediaQualityStatus.MEDIA_SESSION_TYPE_AUDIO,
                        AccessNetworkConstants.TRANSPORT_TYPE_WWAN,
                        9 /*packetLossRate*/, 0 /*jitter*/, 9999 /*inactivityTime*/);
        assertEquals(0, mCallTracker.getActiveCallTracker().thresholdBreached(status));
    }
}
