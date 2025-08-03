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
import android.telephony.CallQuality;
import android.telephony.ims.RtpHeaderExtension;
import android.telephony.imsmedia.AudioConfig;
import android.telephony.imsmedia.IImsAudioSessionCallback;
import android.telephony.imsmedia.ImsMediaSession;
import android.telephony.imsmedia.MediaQualityStatus;
import android.telephony.imsmedia.MediaQualityThreshold;
import android.telephony.imsmedia.RtpReceptionStats;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import com.android.telephony.imsmedia.AudioService;
import com.android.telephony.imsmedia.AudioSession;
import com.android.telephony.imsmedia.Utils;
import com.android.telephony.imsmedia.Utils.OpenSessionParams;
import com.android.telephony.imsmedia.tests.RtpReceptionStatsTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.ArrayList;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class AudioSessionTest extends ImsMediaTest {
    private static final int SESSION_ID = 1;
    private static final int DTMF_DURATION = 140;
    private static final int UNUSED = -1;
    private static final int SUCCESS = ImsMediaSession.RESULT_SUCCESS;
    private static final int NO_RESOURCES = ImsMediaSession.RESULT_NO_RESOURCES;
    private static final int PACKET_LOSS = 15;
    private static final int JITTER = 200;
    private static final char DTMF_DIGIT = '7';
    private static final int RECEPTION_DURATION = 10000;
    private static final int DELAY_ADJUSTMENT = 100;
    private AudioSession audioSession;
    private AudioSession.AudioSessionHandler handler;
    private WakeLockManager mWakeLockManager;
    @Mock
    private AudioService audioService;
    private AudioListener audioListener;
    @Mock
    private AudioLocalSession audioLocalSession;
    @Mock
    private IImsAudioSessionCallback callback;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        audioSession = new AudioSession(SESSION_ID, callback,
                audioService, audioLocalSession, null, Looper.myLooper());
        audioListener = audioSession.getAudioListener();
        handler = audioSession.getAudioSessionHandler();
        mTestClass = AudioSessionTest.this;
        mWakeLockManager = WakeLockManager.getInstance();
        super.setUp();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
        mWakeLockManager.cleanup();
    }

    private Parcel createParcel(int message, int result, AudioConfig config) {
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

        audioSession.openSession(params);
        processAllMessages();
        verify(audioService, times(1)).openSession(eq(SESSION_ID), eq(params));
        assertThat(mWakeLockManager.mWakeLock.isHeld()).isEqualTo(false);
    }

    @Test
    public void testCloseSession() {
        audioSession.closeSession();
        processAllMessages();
        verify(audioService, times(1)).closeSession(eq(SESSION_ID));
        assertThat(mWakeLockManager.mWakeLock.isHeld()).isEqualTo(false);
    }

    @Test
    public void testModifySession() {
        // Modify Session Request
        AudioConfig config = AudioConfigTest.createAudioConfig();
        audioSession.modifySession(config);
        processAllMessages();
        verify(audioLocalSession, times(1)).modifySession(eq(config));
        assertThat(mWakeLockManager.mWakeLock.isHeld()).isEqualTo(true);

        // Modify Session Response - Success
        audioListener.onMessage(
            createParcel(AudioSession.EVENT_MODIFY_SESSION_RESPONSE, SUCCESS, config));
        processAllMessages();
        try {
            verify(callback, times(1)).onModifySessionResponse(eq(config), eq(SUCCESS));
        }  catch(RemoteException e) {
            fail("Failed to notify modifySessionResponse: " + e);
        }

        // Modify Session Response - Failure (NO_RESOURCES)
        audioListener.onMessage(
            createParcel(AudioSession.EVENT_MODIFY_SESSION_RESPONSE, NO_RESOURCES, config));
        processAllMessages();
        try {
            verify(callback, times(1)).onModifySessionResponse(eq(config), eq(NO_RESOURCES));
        }  catch(RemoteException e) {
            fail("Failed to notify modifySessionResponse: " + e);
        }
    }

    @Test
    public void testAddConfig() {
        // Add Config Request
        AudioConfig config = AudioConfigTest.createAudioConfig();
        audioSession.addConfig(config);
        processAllMessages();
        verify(audioLocalSession, times(1)).addConfig(eq(config));

        // Add Config Response - Success
        audioListener.onMessage(
            createParcel(AudioSession.EVENT_ADD_CONFIG_RESPONSE, SUCCESS, config));
        processAllMessages();
        try {
            verify(callback, times(1)).onAddConfigResponse(eq(config), eq(SUCCESS));
        }  catch(RemoteException e) {
            fail("Failed to notify addConfigResponse: " + e);
        }

        // Add Config Response - Failure (NO_RESOURCES)
        audioListener.onMessage(
            createParcel(AudioSession.EVENT_ADD_CONFIG_RESPONSE, NO_RESOURCES, config));
        processAllMessages();
        try {
            verify(callback, times(1)).onAddConfigResponse(eq(config), eq(NO_RESOURCES));
        }  catch(RemoteException e) {
            fail("Failed to notify addConfigResponse: " + e);
        }
    }

    @Test
    public void testDeleteConfig() {
        // Delete Config Request
        AudioConfig config = AudioConfigTest.createAudioConfig();
        audioSession.deleteConfig(config);
        processAllMessages();
        verify(audioLocalSession, times(1)).deleteConfig(eq(config));
    }

    @Test
    public void testConfirmConfig() {
        // Confirm Config Request
        AudioConfig config = AudioConfigTest.createAudioConfig();
        audioSession.confirmConfig(config);
        processAllMessages();
        verify(audioLocalSession, times(1)).confirmConfig(eq(config));

        // Confirm Config Response - Success
        audioListener.onMessage(
            createParcel(AudioSession.EVENT_CONFIRM_CONFIG_RESPONSE, SUCCESS, config));
        processAllMessages();
        try {
            verify(callback, times(1)).onConfirmConfigResponse(eq(config), eq(SUCCESS));
        }  catch(RemoteException e) {
            fail("Failed to notify confirmConfigResponse: " + e);
        }

        // Confirm Config Response - Failure (NO_RESOURCES)
        audioListener.onMessage(
            createParcel(AudioSession.EVENT_CONFIRM_CONFIG_RESPONSE, NO_RESOURCES, config));
        processAllMessages();
        try {
            verify(callback, times(1)).onConfirmConfigResponse(eq(config), eq(NO_RESOURCES));
        }  catch(RemoteException e) {
            fail("Failed to notify confirmConfigResponse: " + e);
        }
    }

    @Test
    public void testSendDtmf() {
        audioSession.sendDtmf(DTMF_DIGIT, DTMF_DURATION);
        processAllMessages();
        verify(audioLocalSession, times(1)).sendDtmf(eq(DTMF_DIGIT), eq(DTMF_DURATION));
    }

    @Test
    public void testStartDtmf() {
        audioSession.startDtmf(DTMF_DIGIT);
        processAllMessages();
        verify(audioLocalSession, times(1)).sendDtmf(eq(DTMF_DIGIT), eq(DTMF_DURATION));
    }

    @Test
    public void testSetMediaQualityThreshold() {
        // Set Media Quality Threshold
        MediaQualityThreshold threshold = MediaQualityThresholdTest.createMediaQualityThreshold();
        audioSession.setMediaQualityThreshold(threshold);
        processAllMessages();
        verify(audioLocalSession, times(1)).setMediaQualityThreshold(eq(threshold));
    }

    @Test
    public void testRequestRtpReceptionStats() {
        // Query rtp reception stats
        audioSession.requestRtpReceptionStats(RECEPTION_DURATION);
        processAllMessages();
        verify(audioLocalSession, times(1)).requestRtpReceptionStats(eq(RECEPTION_DURATION));
    }

    @Test
    public void testAdjustDelay() {
        // Apply delay
        audioSession.adjustDelay(DELAY_ADJUSTMENT);
        processAllMessages();
        verify(audioLocalSession, times(1)).adjustDelay(eq(DELAY_ADJUSTMENT));
    }

    @Test
    public void testFirstMediaPacketReceivedInd() {
        // Receive First MediaPacket Received Indication
        AudioConfig config = AudioConfigTest.createAudioConfig();
        Utils.sendMessage(handler, AudioSession.EVENT_FIRST_MEDIA_PACKET_IND, config);
        processAllMessages();
        try {
            verify(callback, times(1)).onFirstMediaPacketReceived(eq(config));
        }  catch(RemoteException e) {
            fail("Failed to notify onFirstMediaPacketReceived: " + e);
        }
    }

    @Test
    public void testHeaderExtension() {
        // Send RtpHeaderExtension
        ArrayList extensions = new ArrayList<RtpHeaderExtension>();
        audioSession.sendHeaderExtension(extensions);
        processAllMessages();
        verify(audioLocalSession, times(1)).sendHeaderExtension(eq(extensions));

        // Receive RtpHeaderExtension
        Utils.sendMessage(handler, AudioSession.EVENT_RTP_HEADER_EXTENSION_IND, extensions);
        processAllMessages();
        try {
            verify(callback, times(1)).onHeaderExtensionReceived(eq(extensions));
        }  catch(RemoteException e) {
            fail("Failed to notify onHeaderExtensionReceived: " + e);
        }
    }

    @Test
    public void testNotifyMediaQualityStatus() {
        // Receive MediaQualityStatus
        MediaQualityStatus status = MediaQualityStatusTest.createMediaQualityStatus();
        Utils.sendMessage(handler, AudioSession.EVENT_MEDIA_QUALITY_STATUS_IND, status);
        processAllMessages();
        try {
            verify(callback, times(1)).notifyMediaQualityStatus(eq(status));
        } catch (RemoteException e) {
            fail("Failed to notify notifyMediaInactivity: " + e);
        }
    }

    @Test
    public void testTriggerAnbrQuery() {
        // Receive triggerAnbrQuery for ANBR
        AudioConfig config = AudioConfigTest.createAudioConfig();
        Utils.sendMessage(handler, AudioSession.EVENT_TRIGGER_ANBR_QUERY_IND, config);
        processAllMessages();
        try {
            verify(callback, times(1)).triggerAnbrQuery(eq(config));
        }  catch (RemoteException e) {
            fail("Failed to notify triggerAnbrQuery: " + e);
        }
    }

    @Test
    public void testDtmfReceived() {
        // Receive onDtmfReceived
        Utils.sendMessage(handler, AudioSession.EVENT_DTMF_RECEIVED_IND, DTMF_DIGIT, DTMF_DURATION);
        processAllMessages();
        try {
            verify(callback, times(1)).onDtmfReceived(eq(DTMF_DIGIT), eq(DTMF_DURATION));
        }  catch (RemoteException e) {
            fail("Failed to notify onDtmfReceived: " + e);
        }
    }

    @Test
    public void testCallQualityChangedInd() {
        // Receive Call Quality Changed Indication
        CallQuality callQuality = CallQualityTest.createCallQuality();
        Utils.sendMessage(handler, AudioSession.EVENT_CALL_QUALITY_CHANGE_IND, callQuality);
        processAllMessages();
        try {
            verify(callback, times(1)).onCallQualityChanged(eq(callQuality));
        } catch (RemoteException e) {
            fail("Failed to notify onCallQualityChanged: " + e);
        }
    }

    @Test
    public void testNotifyRtpReceptionStats() {
        // Receive Rtp reception statistics notification
        RtpReceptionStats stats = RtpReceptionStatsTest.createRtpReceptionStats();
        Utils.sendMessage(handler, AudioSession.EVENT_NOTIFY_RECEPTION_STATS, stats);
        processAllMessages();
        try {
            verify(callback, times(1)).notifyRtpReceptionStats(eq(stats));
        } catch (RemoteException e) {
            fail("Failed to notify RtpReceptionStats: " + e);
        }
    }

    @Test
    public void testOpenSessionSuccess() {
        audioSession.onOpenSessionSuccess(audioLocalSession);
        processAllMessages();
        try {
            verify(callback, times(1)).onOpenSessionSuccess(audioSession);
            assertThat(mWakeLockManager.mWakeLock.isHeld()).isEqualTo(false);
        } catch (RemoteException e) {
            fail("Failed to notify onOpenSessionSuccess: " + e);
        }
    }

    @Test
    public void testOpenSessionFailure() {
        audioSession.onOpenSessionFailure(ImsMediaSession.RESULT_INVALID_PARAM);
        processAllMessages();
        try {
            verify(callback, times(1)).onOpenSessionFailure(ImsMediaSession.RESULT_INVALID_PARAM);
            assertThat(mWakeLockManager.mWakeLock.isHeld()).isEqualTo(false);
        } catch (RemoteException e) {
            fail("Failed to notify onOpenSessionFailure: " + e);
        }
    }

    @Test
    public void testSessionClosed() {
        audioSession.onSessionClosed();
        processAllMessages();
        try {
            verify(callback, times(1)).onSessionClosed();
            assertThat(mWakeLockManager.mWakeLock.isHeld()).isEqualTo(false);
        } catch (RemoteException e) {
            fail("Failed to notify onSessionClosed: " + e);
        }
    }
}
