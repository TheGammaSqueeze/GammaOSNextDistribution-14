/**
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

package com.android.telephony.imsmedia;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.fail;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.os.Looper;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.telephony.imsmedia.IImsTextSessionCallback;
import android.telephony.imsmedia.ImsMediaSession;
import android.telephony.imsmedia.MediaQualityThreshold;
import android.telephony.imsmedia.TextConfig;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import com.android.telephony.imsmedia.Utils.OpenSessionParams;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.net.DatagramSocket;
import java.net.SocketException;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class TextSessionTest extends ImsMediaTest {
    private static final int SESSION_ID = 1;
    private static final int SUCCESS = ImsMediaSession.RESULT_SUCCESS;
    private static final int NO_RESOURCES = ImsMediaSession.RESULT_NO_RESOURCES;
    private static final int RTP = ImsMediaSession.PACKET_TYPE_RTP;
    private static final int RTCP = ImsMediaSession.PACKET_TYPE_RTCP;
    private static final String TEXT_STREAM = "Hello";
    private TextSession mTextSession;
    private TextSession.TextSessionHandler mHandler;
    private WakeLockManager mWakeLockManager;
    @Mock
    private TextService mTextService;
    private TextListener mTextListener;
    @Mock
    private TextLocalSession mTextLocalSession;
    @Mock
    private IImsTextSessionCallback mCallback;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mTextSession = new TextSession(SESSION_ID, mCallback, mTextService, mTextLocalSession,
                Looper.myLooper());
        mTextListener = mTextSession.getTextListener();
        mHandler = mTextSession.getTextSessionHandler();
        mTestClass = TextSessionTest.this;
        mWakeLockManager = WakeLockManager.getInstance();
        super.setUp();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
        mWakeLockManager.cleanup();
    }

    private Parcel createParcel(int message, int result, TextConfig config) {
        Parcel parcel = Parcel.obtain();
        parcel.writeInt(message);
        parcel.writeInt(result);
        if (config != null) {
            config.writeToParcel(parcel, 0);
        }
        parcel.setDataPosition(0);
        return parcel;
    }

    @Test
    public void testOpenSession() {
        DatagramSocket rtpSocket = null;
        DatagramSocket rtcpSocket = null;

        try {
            rtpSocket = new DatagramSocket();
            rtcpSocket = new DatagramSocket();
        } catch (SocketException e) {
            fail("SocketException:" + e);
        }

        OpenSessionParams params = new OpenSessionParams(
                ParcelFileDescriptor.fromDatagramSocket(rtpSocket),
                ParcelFileDescriptor.fromDatagramSocket(rtcpSocket),
                null, null);

        mTextSession.openSession(params);
        processAllMessages();
        verify(mTextService, times(1)).openSession(eq(SESSION_ID), eq(params));
        assertThat(mWakeLockManager.mWakeLock.isHeld()).isEqualTo(false);
    }

    @Test
    public void testCloseSession() {
        mTextSession.closeSession();
        processAllMessages();
        verify(mTextService, times(1)).closeSession(eq(SESSION_ID));
        assertThat(mWakeLockManager.mWakeLock.isHeld()).isEqualTo(false);
    }

    @Test
    public void testModifySession() {
        // Modify Session Request
        TextConfig config = TextConfigTest.createTextConfig();
        mTextSession.modifySession(config);
        processAllMessages();
        verify(mTextLocalSession, times(1)).modifySession(eq(config));
        assertThat(mWakeLockManager.mWakeLock.isHeld()).isEqualTo(true);

        // Modify Session Response - Success
        mTextListener.onMessage(
                createParcel(TextSession.EVENT_MODIFY_SESSION_RESPONSE, SUCCESS, config));
        processAllMessages();
        try {
            verify(mCallback, times(1)).onModifySessionResponse(eq(config), eq(SUCCESS));
        } catch (RemoteException e) {
            fail("Failed to notify modifySessionResponse: " + e);
        }

        // Modify Session Response - Failure (NO_RESOURCES)
        mTextListener.onMessage(
                createParcel(TextSession.EVENT_MODIFY_SESSION_RESPONSE, NO_RESOURCES, config));
        processAllMessages();
        try {
            verify(mCallback, times(1)).onModifySessionResponse(eq(config), eq(NO_RESOURCES));
        } catch (RemoteException e) {
            fail("Failed to notify modifySessionResponse: " + e);
        }
    }

    @Test
    public void testSendRtt() {
        mTextSession.sendRtt(TEXT_STREAM);
        processAllMessages();
        verify(mTextLocalSession, times(1)).sendRtt(eq(TEXT_STREAM));
    }

    @Test
    public void testSetMediaQualityThreshold() {
        // Set Media Quality Threshold
        MediaQualityThreshold threshold = MediaQualityThresholdTest.createMediaQualityThreshold();
        mTextSession.setMediaQualityThreshold(threshold);
        processAllMessages();
        verify(mTextLocalSession, times(1)).setMediaQualityThreshold(eq(threshold));
    }

    @Test
    public void testMediaInactivityInd() {
        // Receive Inactivity - RTP
        Parcel parcel = Parcel.obtain();
        parcel.writeInt(TextSession.EVENT_MEDIA_INACTIVITY_IND);
        parcel.writeInt(RTP);
        parcel.setDataPosition(0);
        mTextListener.onMessage(parcel);
        processAllMessages();
        try {
            verify(mCallback, times(1)).notifyMediaInactivity(eq(RTP));
        } catch (RemoteException e) {
            fail("Failed to notify notifyMediaInactivity: " + e);
        }

        // Receive Inactivity - RTCP
        Parcel parcel2 = Parcel.obtain();
        parcel2.writeInt(TextSession.EVENT_MEDIA_INACTIVITY_IND);
        parcel2.writeInt(RTCP);
        parcel2.setDataPosition(0);
        mTextListener.onMessage(parcel2);
        processAllMessages();
        try {
            verify(mCallback, times(1)).notifyMediaInactivity(eq(RTCP));
        } catch (RemoteException e) {
            fail("Failed to notify notifyMediaInactivity: " + e);
        }
    }

    @Test
    public void testRttReceived() {
        // Receive onRttReceived
        Utils.sendMessage(mHandler, TextSession.EVENT_RTT_RECEIVED, TEXT_STREAM);
        processAllMessages();
        try {
            verify(mCallback, times(1)).onRttReceived(eq(TEXT_STREAM));
        } catch (RemoteException e) {
            fail("Failed to notify onRttReceived: " + e);
        }
    }

    @Test
    public void testOpenSessionSuccess() {
        mTextSession.onOpenSessionSuccess(mTextLocalSession);
        processAllMessages();
        try {
            verify(mCallback, times(1)).onOpenSessionSuccess(mTextSession);
            assertThat(mWakeLockManager.mWakeLock.isHeld()).isEqualTo(false);
        } catch (RemoteException e) {
            fail("Failed to notify onOpenSessionSuccess: " + e);
        }
    }

    @Test
    public void testOpenSessionFailure() {
        mTextSession.onOpenSessionFailure(ImsMediaSession.RESULT_INVALID_PARAM);
        processAllMessages();
        try {
            verify(mCallback, times(1)).onOpenSessionFailure(ImsMediaSession.RESULT_INVALID_PARAM);
            assertThat(mWakeLockManager.mWakeLock.isHeld()).isEqualTo(false);
        } catch (RemoteException e) {
            fail("Failed to notify onOpenSessionFailure: " + e);
        }
    }

    @Test
    public void testSessionClosed() {
        mTextSession.onSessionClosed();
        processAllMessages();
        try {
            verify(mCallback, times(1)).onSessionClosed();
        } catch (RemoteException e) {
            fail("Failed to notify onSessionClosed: " + e);
        }
    }
}
