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

import static junit.framework.Assert.assertEquals;

import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.hardware.radio.ims.media.IImsMedia;
import android.hardware.radio.ims.media.IImsMediaSession;
import android.hardware.radio.ims.media.RtpConfig;
import android.hardware.radio.ims.media.RtpError;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.telephony.CallQuality;
import android.telephony.ims.RtpHeaderExtension;
import android.telephony.imsmedia.AudioConfig;
import android.telephony.imsmedia.IImsAudioSessionCallback;
import android.telephony.imsmedia.ImsMediaSession;
import android.telephony.imsmedia.MediaQualityStatus;
import android.telephony.imsmedia.MediaQualityThreshold;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import com.android.telephony.imsmedia.AudioSession;
import com.android.telephony.imsmedia.Utils;
import com.android.telephony.imsmedia.Utils.OpenSessionParams;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class AudioOffloadTest extends ImsMediaTest {
    private static final int SESSION_ID = 1;
    private static final int DTMF_DURATION = 120;
    private static final int NO_RESOURCES = ImsMediaSession.RESULT_NO_RESOURCES;
    private static final int NO_MEMORY = ImsMediaSession.RESULT_NO_MEMORY;
    private static final int SUCCESS = ImsMediaSession.RESULT_SUCCESS;
    private static final int PACKET_LOSS = 15;
    private static final int JITTER = 200;
    private static final char DTMF_DIGIT = '7';
    private AudioSession audioSession;
    private AudioOffloadListener offloadListener;
    private AudioSession.AudioSessionHandler handler;
    @Mock
    private IImsAudioSessionCallback callback;
    @Mock
    private IImsMedia imsMedia;
    @Mock
    private IImsMediaSession imsMediaSession;
    @Mock
    private AudioOffloadService offloadService;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        offloadService = spy(AudioOffloadService.getInstance());
        doReturn(imsMedia).when(offloadService).getIImsMedia();
        audioSession = new AudioSession(SESSION_ID, callback, null, null, offloadService,
                Looper.myLooper());
        handler = audioSession.getAudioSessionHandler();
        audioSession.setAudioOffload(true);
        offloadListener = audioSession.getOffloadListener();
        audioSession.onOpenSessionSuccess(imsMediaSession);
        mTestClass = AudioOffloadTest.this;
        super.setUp();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
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

        verify(offloadService, times(1)).openSession(eq(SESSION_ID), eq(params));
        try {
            verify(imsMedia, times(1)).openSession(eq(SESSION_ID), any(), eq(null));
        } catch (RemoteException e) {
            fail("Failed to invoke openSession:" + e);
        }

        rtpSocket.close();
        rtcpSocket.close();
    }

    @Test
    public void testCloseSession() {
        audioSession.closeSession();
        processAllMessages();
        verify(offloadService, times(1)).closeSession(eq(SESSION_ID));
    }

    @Test
    public void testModifySession() {
        final AudioConfig inputAudioConfig = AudioConfigTest.createAudioConfig();
        RtpConfig outputRtpConfig = null;

        // Modify Session Request
        audioSession.modifySession(inputAudioConfig);
        processAllMessages();
        try {
            ArgumentCaptor<RtpConfig> argumentCaptor = ArgumentCaptor.forClass(RtpConfig.class);
            verify(imsMediaSession, times(1)).modifySession(argumentCaptor.capture());
            // Get the HAL RtpConfig
            outputRtpConfig = argumentCaptor.getValue();
            // Covert it back to AudioConfig
            final AudioConfig outputAudioConfig = Utils.convertToAudioConfig(outputRtpConfig);
            // Ensure both are same
            assertEquals(inputAudioConfig, outputAudioConfig);
        } catch (RemoteException e) {
            fail("Failed to invoke modifySession: " + e);
        }

        // Modify Session Response - SUCCESS
        offloadListener.onModifySessionResponse(outputRtpConfig, RtpError.NONE);
        processAllMessages();
        try {
            verify(callback, times(1)).onModifySessionResponse(eq(inputAudioConfig), eq(SUCCESS));
        } catch (RemoteException e) {
            fail("Failed to notify modifySessionResponse: " + e);
        }

        // Modify Session Response - FAILURE
        offloadListener.onModifySessionResponse(outputRtpConfig, RtpError.NO_RESOURCES);
        processAllMessages();
        try {
            verify(callback, times(1)).onModifySessionResponse(
                    eq(inputAudioConfig), eq(NO_RESOURCES));
        } catch (RemoteException e) {
            fail("Failed to notify modifySessionResponse: " + e);
        }
    }

    @Test
    public void testAddConfig() {
        final AudioConfig inputAudioConfig = AudioConfigTest.createAudioConfig();
        RtpConfig outputRtpConfig = null;

        // Add Config Request
        audioSession.addConfig(inputAudioConfig);
        processAllMessages();
        try {
            ArgumentCaptor<RtpConfig> argumentCaptor = ArgumentCaptor.forClass(RtpConfig.class);
            verify(imsMediaSession, times(1)).modifySession(argumentCaptor.capture());
            // Get the HAL RtpConfig
            outputRtpConfig = argumentCaptor.getValue();
            // Covert it back to AudioConfig
            final AudioConfig outputAudioConfig = Utils.convertToAudioConfig(outputRtpConfig);
            // Ensure both are same
            assertEquals(inputAudioConfig, outputAudioConfig);
        } catch (RemoteException e) {
            fail("Failed to invoke addConfig: " + e);
        }

        // Add Config Response - SUCCESS
        offloadListener.onModifySessionResponse(outputRtpConfig, RtpError.NONE);
        processAllMessages();
        try {
            verify(callback, times(1)).onModifySessionResponse(eq(inputAudioConfig), eq(SUCCESS));
        } catch (RemoteException e) {
            fail("Failed to notify addConfigResponse: " + e);
        }

        // Add Config Response - FAILURE
        offloadListener.onModifySessionResponse(outputRtpConfig, RtpError.NO_MEMORY);
        processAllMessages();
        try {
            verify(callback, times(1)).onModifySessionResponse(eq(inputAudioConfig), eq(NO_MEMORY));
        } catch (RemoteException e) {
            fail("Failed to notify addConfigResponse: " + e);
        }
    }

    @Test
    public void testsendDtmf() {
        audioSession.sendDtmf(DTMF_DIGIT, DTMF_DURATION);
        processAllMessages();
        try {
            verify(imsMediaSession, times(1)).sendDtmf(eq(DTMF_DIGIT), eq(DTMF_DURATION));
        } catch (RemoteException e) {
            fail("Failed to invoke sendDtmf: " + e);
        }
    }

    @Test
    public void testStartDtmf() {
        audioSession.startDtmf(DTMF_DIGIT);
        processAllMessages();
        try {
            verify(imsMediaSession, times(1)).startDtmf(eq(DTMF_DIGIT));
        } catch (RemoteException e) {
            fail("Failed to invoke startDtmf: " + e);
        }
    }

    @Test
    public void testStopDtmf() {
        audioSession.stopDtmf();
        processAllMessages();
        try {
            verify(imsMediaSession, times(1)).stopDtmf();
        } catch (RemoteException e) {
            fail("Failed to invoke stopDtmf: " + e);
        }

    }

    @Test
    public void testSetMediaQualityThreshold() {
        // Set Media Quality Threshold
        MediaQualityThreshold threshold =
                MediaQualityThresholdTest.createMediaQualityThresholdForHal();
        audioSession.setMediaQualityThreshold(threshold);
        processAllMessages();
        try {
            ArgumentCaptor<android.hardware.radio.ims.media.MediaQualityThreshold> argumentCaptor =
                    ArgumentCaptor.forClass(
                        android.hardware.radio.ims.media.MediaQualityThreshold.class);
            verify(imsMediaSession, times(1)).setMediaQualityThreshold(argumentCaptor.capture());
            // Get the HAL MediaQualityThreshold
            final android.hardware.radio.ims.media.MediaQualityThreshold
                    halThreshold = argumentCaptor.getValue();
            // Covert it back to {@link MediaQualityThreshold}
            final MediaQualityThreshold expectedThreshold =
                    Utils.convertMediaQualityThreshold(halThreshold);
            // Ensure both are same
            assertEquals(threshold, expectedThreshold);
        } catch (RemoteException e) {
            fail("Failed to invoke setMediaQualityThreshold: " + e);
        }

    }

    @Test
    public void testMediaQualityStatusInd() {
        // Receive MediaQualityStatus
        final MediaQualityStatus outputStatus =
                MediaQualityStatusTest.createMediaQualityStatus();
        final android.hardware.radio.ims.media.MediaQualityStatus inputStatus =
                Utils.convertToHalMediaQualityStatus(outputStatus);
        offloadListener.notifyMediaQualityStatus(inputStatus);
        processAllMessages();
        try {
            verify(callback, times(1)).notifyMediaQualityStatus(eq(outputStatus));
        } catch (RemoteException e) {
            fail("Failed to notify media quality status: " + e);
        }
    }

    @Test
    public void testFirstMediaPacketReceivedInd() {
        final AudioConfig outputAudioConfig = AudioConfigTest.createAudioConfig();
        final RtpConfig inputRtpConfig = Utils.convertToRtpConfig(outputAudioConfig);

        // Receive First MediaPacket Received Indication
        offloadListener.onFirstMediaPacketReceived(inputRtpConfig);
        processAllMessages();
        try {
            verify(callback, times(1)).onFirstMediaPacketReceived(eq(outputAudioConfig));
        } catch (RemoteException e) {
            fail("Failed to notify onFirstMediaPacketReceived: " + e);
        }
    }

    @Test
    public void testHeaderExtension() {
        final byte[] arr1 = {1, 2, 3, 4};
        final byte[] arr2 = {4, 2, 3, 4, 6};
        final ArrayList inputExtensions = new ArrayList<RtpHeaderExtension>();
        inputExtensions.add(new RtpHeaderExtension(7, arr1));
        inputExtensions.add(new RtpHeaderExtension(8, arr2));

        List<android.hardware.radio.ims.media.RtpHeaderExtension> halExtensions = null;

        // Send RtpHeaderExtension
        audioSession.sendHeaderExtension(inputExtensions);
        processAllMessages();
        try {
            ArgumentCaptor<List<android.hardware.radio.ims.media.RtpHeaderExtension>>
                    argumentCaptor = ArgumentCaptor.forClass(List.class);
            verify(imsMediaSession, times(1)).sendHeaderExtension(argumentCaptor.capture());
            // Get the HAL RtpHeaderExtension list
            halExtensions = argumentCaptor.getValue();
            // Covert it back to {@link RtpHeaderExtension} list
            final List<RtpHeaderExtension> outputExtensions =
                    halExtensions.stream().map(Utils::convertRtpHeaderExtension)
                    .collect(Collectors.toList());
            // Ensure both are same
            assertEquals(inputExtensions, outputExtensions);
        } catch (RemoteException e) {
            fail("Failed to invoke sendHeaderExtension: " + e);
        }

        // Receive HAL RtpHeaderExtension
        offloadListener.onHeaderExtensionReceived(halExtensions);
        processAllMessages();
        try {
            verify(callback, times(1)).onHeaderExtensionReceived(eq(inputExtensions));
        } catch (RemoteException e) {
            fail("Failed to notify onHeaderExtensionReceived: " + e);
        }
    }

    @Test
    public void testTriggerAnbrQuery() {
        final AudioConfig outputAudioConfig = AudioConfigTest.createAudioConfig();
        final RtpConfig inputRtpConfig = Utils.convertToRtpConfig(outputAudioConfig);

        // Receive triggerAnbrQuery for ANBR
        offloadListener.triggerAnbrQuery(inputRtpConfig);
        processAllMessages();
        try {
            verify(callback, times(1)).triggerAnbrQuery(eq(outputAudioConfig));
        } catch (RemoteException e) {
            fail("Failed to notify triggerAnbrQuery: " + e);
        }
    }

    @Test
    public void testDtmfReceived() {
        // Receive DTMF Received
        offloadListener.onDtmfReceived(DTMF_DIGIT, DTMF_DURATION);
        processAllMessages();
        try {
            verify(callback, times(1)).onDtmfReceived(eq(DTMF_DIGIT), eq(DTMF_DURATION));
        } catch (RemoteException e) {
            fail("Failed to notify onDtmfReceived: " + e);
        }
    }

    @Test
    public void testCallQualityChangedInd() {
        final android.hardware.radio.ims.media.CallQuality inputCallQuality =
                CallQualityTest.createHalCallQuality();
        final CallQuality outputCallQuality = Utils.convertCallQuality(inputCallQuality);

        // Receive Call Quality Changed Indication
        offloadListener.onCallQualityChanged(inputCallQuality);
        processAllMessages();
        try {
            verify(callback, times(1)).onCallQualityChanged(eq(outputCallQuality));
        } catch (RemoteException e) {
            fail("Failed to notify onCallQualityChanged: " + e);
        }
    }
}
