/**
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

package com.android.telephony.imsmedia;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.os.Looper;
import android.os.Parcel;
import android.os.RemoteException;
import android.telephony.CallQuality;
import android.telephony.ims.RtpHeaderExtension;
import android.telephony.imsmedia.AudioConfig;
import android.telephony.imsmedia.IImsAudioSessionCallback;
import android.telephony.imsmedia.ImsMediaSession;
import android.telephony.imsmedia.MediaQualityStatus;
import android.telephony.imsmedia.RtpReceptionStats;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import com.android.telephony.imsmedia.tests.RtpReceptionStatsTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class AudioListenerTest extends ImsMediaTest {
    private static final int SESSION_ID = 1;
    private static final char DTMF_DIGIT = '7';
    private static final int DTMF_DURATION = 120;
    private AudioListener mAudioListener;
    @Mock
    private AudioService mAudioService;
    @Mock
    private AudioLocalSession mMockAudioLocalSession;
    @Mock
    private ImsMediaController.OpenSessionCallback mMockCallback;
    @Mock
    private IImsAudioSessionCallback mMockIImsAudioSessionCallback;
    private AudioConfig mAudioConfig;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        AudioSession audioSession = new AudioSession(SESSION_ID, mMockIImsAudioSessionCallback,
                mAudioService, mMockAudioLocalSession, null, Looper.myLooper());
        AudioSession.AudioSessionHandler handler = audioSession.getAudioSessionHandler();
        mAudioListener = new AudioListener(handler);
        mAudioListener.setMediaCallback(mMockCallback);
        mAudioConfig = AudioConfigTest.createAudioConfig();
        mTestClass = AudioListenerTest.this;
        super.setUp();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    private Parcel createParcel(int event, int result, AudioConfig config) {
        Parcel parcel = Parcel.obtain();
        parcel.writeInt(event);
        parcel.writeInt(result);
        if (config != null) {
            config.writeToParcel(parcel, 0);
        }
        parcel.setDataPosition(0);
        return parcel;
    }

    @Test
    public void testOpenSessionSuccess() {
        mAudioListener.onMessage(createParcel(AudioSession.EVENT_OPEN_SESSION_SUCCESS, SESSION_ID,
                mAudioConfig));
        doNothing().when(mMockCallback).onOpenSessionSuccess(eq(SESSION_ID),
                eq(mMockAudioLocalSession));
        verify(mMockCallback,
                times(1)).onOpenSessionSuccess(eq(SESSION_ID), any(AudioLocalSession.class));
    }

    @Test
    public void testOpenSessionFailure() {
        Parcel parcel = Parcel.obtain();
        parcel.writeInt(AudioSession.EVENT_OPEN_SESSION_FAILURE);
        parcel.writeInt(SESSION_ID);
        parcel.writeInt(ImsMediaSession.RESULT_INVALID_PARAM);
        mAudioConfig.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        mAudioListener.onMessage(parcel);
        doNothing().when(mMockCallback).onOpenSessionFailure(eq(SESSION_ID),
                eq(ImsMediaSession.RESULT_INVALID_PARAM));
        verify(mMockCallback, times(1)).onOpenSessionFailure(eq(SESSION_ID),
                eq(ImsMediaSession.RESULT_INVALID_PARAM));
    }

    @Test
    public void testEventModifySessionResponse() throws RemoteException {
        mAudioListener.onMessage(createParcel(AudioSession.EVENT_MODIFY_SESSION_RESPONSE,
                ImsMediaSession.RESULT_NO_RESOURCES, mAudioConfig));
        processAllMessages();
        verify(mMockIImsAudioSessionCallback,
                times(1)).onModifySessionResponse(eq(mAudioConfig),
                eq(ImsMediaSession.RESULT_NO_RESOURCES));
    }

    @Test
    public void testEventAddConfigResponse() throws RemoteException {
        mAudioListener.onMessage(createParcel(AudioSession.EVENT_ADD_CONFIG_RESPONSE,
                ImsMediaSession.RESULT_NOT_READY, mAudioConfig));
        processAllMessages();
        verify(mMockIImsAudioSessionCallback,
                times(1)).onAddConfigResponse(eq(mAudioConfig),
                eq(ImsMediaSession.RESULT_NOT_READY));
    }

    @Test
    public void testEventConfirmConfigResponse() throws RemoteException {
        mAudioListener.onMessage(createParcel(AudioSession.EVENT_CONFIRM_CONFIG_RESPONSE,
                ImsMediaSession.RESULT_INVALID_PARAM, mAudioConfig));
        processAllMessages();
        verify(mMockIImsAudioSessionCallback,
                times(1)).onConfirmConfigResponse(eq(mAudioConfig),
                eq(ImsMediaSession.RESULT_INVALID_PARAM));
    }

    @Test
    public void testEventFirstMediaPacketInd() throws RemoteException {
        Parcel parcel = Parcel.obtain();
        parcel.writeInt(AudioSession.EVENT_FIRST_MEDIA_PACKET_IND);
        mAudioConfig.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        mAudioListener.onMessage(parcel);
        processAllMessages();
        verify(mMockIImsAudioSessionCallback,
                times(1)).onFirstMediaPacketReceived(eq(mAudioConfig));
    }

    @Test
    public void testEventRtpHeaderExtensionInd() throws RemoteException {
        List<RtpHeaderExtension> extensions = new ArrayList<>();
        extensions.add(new RtpHeaderExtension(1, new byte[]{5}));
        extensions.add(new RtpHeaderExtension(2, new byte[]{10}));
        Parcel parcel = Parcel.obtain();
        parcel.writeInt(AudioSession.EVENT_RTP_HEADER_EXTENSION_IND);
        parcel.writeInt(extensions.size());
        for (RtpHeaderExtension item : extensions) {
            item.writeToParcel(parcel, 0);
        }
        parcel.setDataPosition(0);
        mAudioListener.onMessage(parcel);
        parcel.recycle();
        processAllMessages();
        verify(mMockIImsAudioSessionCallback,
                times(1)).onHeaderExtensionReceived(eq(extensions));
    }

    @Test
    public void testEventMediaQualityStatusInd() throws RemoteException {
        Parcel parcel = Parcel.obtain();
        final MediaQualityStatus status = MediaQualityStatusTest.createMediaQualityStatus();
        parcel.writeInt(AudioSession.EVENT_MEDIA_QUALITY_STATUS_IND);
        status.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        mAudioListener.onMessage(parcel);
        processAllMessages();
        verify(mMockIImsAudioSessionCallback, times(1)).notifyMediaQualityStatus(eq(status));
    }

    @Test
    public void testEventTriggerAnbrQueryInd() throws RemoteException {
        Parcel parcel = Parcel.obtain();
        parcel.writeInt(AudioSession.EVENT_TRIGGER_ANBR_QUERY_IND);
        mAudioConfig.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        mAudioListener.onMessage(parcel);
        processAllMessages();
        verify(mMockIImsAudioSessionCallback,
                times(1)).triggerAnbrQuery(eq(mAudioConfig));
    }

    @Test
    public void testEventDtmfReceivedInd() throws RemoteException {
        Parcel parcel = Parcel.obtain();
        parcel.writeInt(AudioSession.EVENT_DTMF_RECEIVED_IND);
        parcel.writeByte((byte) DTMF_DIGIT);
        parcel.writeInt(DTMF_DURATION);
        parcel.setDataPosition(0);
        mAudioListener.onMessage(parcel);
        processAllMessages();
        verify(mMockIImsAudioSessionCallback,
                times(1)).onDtmfReceived(eq(DTMF_DIGIT), eq(DTMF_DURATION));
    }

    @Test
    public void testEventCallQualityChangeInd() throws RemoteException {
        Parcel parcel = Parcel.obtain();
        parcel.writeInt(AudioSession.EVENT_CALL_QUALITY_CHANGE_IND);
        CallQuality callQuality = CallQualityTest.createCallQuality();
        callQuality.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        mAudioListener.onMessage(parcel);
        processAllMessages();
        verify(mMockIImsAudioSessionCallback,
                times(1)).onCallQualityChanged(eq(callQuality));
    }

    @Test
    public void testEventSessionClosed() throws RemoteException {
        mAudioListener.onMessage(createParcel(AudioSession.EVENT_SESSION_CLOSED, SESSION_ID,
                mAudioConfig));
        doNothing().when(mMockCallback).onSessionClosed(eq(SESSION_ID));
        verify(mMockCallback, times(1)).onSessionClosed(eq(SESSION_ID));
    }

    @Test
    public void testEventNotifyReceptionStats() throws RemoteException {
        Parcel parcel = Parcel.obtain();
        parcel.writeInt(AudioSession.EVENT_NOTIFY_RECEPTION_STATS);
        RtpReceptionStats stats = RtpReceptionStatsTest.createRtpReceptionStats();
        stats.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        mAudioListener.onMessage(parcel);
        processAllMessages();
        verify(mMockIImsAudioSessionCallback,
                times(1)).notifyRtpReceptionStats(eq(stats));
    }
}
