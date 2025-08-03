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

package com.android.telephony.imsmedia;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.os.Looper;
import android.os.Parcel;
import android.os.RemoteException;
import android.telephony.imsmedia.IImsVideoSessionCallback;
import android.telephony.imsmedia.ImsMediaSession;
import android.telephony.imsmedia.VideoConfig;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
public class VideoListenerTest extends ImsMediaTest {
    private static final int SESSION_ID = 1;
    private static final long VIDEO_DATA = 1024;
    private static final int RESOLUTION_WIDTH = 640;
    private static final int RESOLUTION_HEIGHT = 480;
    private static final long NATIVE_OBJECT = 1234L;
    private VideoListener mVideoListener;
    @Mock
    private VideoService mVideoService;
    @Mock
    private VideoLocalSession mMockVideoLocalSession;
    @Mock
    private ImsMediaController.OpenSessionCallback mMockCallback;
    @Mock
    private IImsVideoSessionCallback mMockIImsVideoSessionCallback;
    private VideoConfig mVideoConfig;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        VideoSession videoSession = new VideoSession(SESSION_ID, mMockIImsVideoSessionCallback,
                mVideoService, mMockVideoLocalSession, Looper.myLooper());
        VideoSession.VideoSessionHandler handler = videoSession.getVideoSessionHandler();
        mVideoListener = new VideoListener(handler);
        mVideoListener.setMediaCallback(mMockCallback);
        mVideoListener.setNativeObject(NATIVE_OBJECT);
        mVideoConfig = VideoConfigTest.createVideoConfig();
        mTestClass = VideoListenerTest.this;
        super.setUp();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    private Parcel createParcel(int event, int result) {
        Parcel parcel = Parcel.obtain();
        parcel.writeInt(event);
        parcel.writeInt(result);
        parcel.setDataPosition(0);
        return parcel;
    }

    @Test
    public void testOpenSessionSuccess() {
        Parcel parcel = createParcel(VideoSession.EVENT_OPEN_SESSION_SUCCESS, SESSION_ID);
        mVideoListener.onMessage(parcel);
        doNothing().when(mMockCallback).onOpenSessionSuccess(eq(SESSION_ID),
                eq(mMockVideoLocalSession));
        parcel.recycle();
        verify(mMockCallback,
                times(1)).onOpenSessionSuccess(eq(SESSION_ID), any(VideoLocalSession.class));
    }

    @Test
    public void testOpenSessionFailure() {
        Parcel parcel = Parcel.obtain();
        parcel.writeInt(VideoSession.EVENT_OPEN_SESSION_FAILURE);
        parcel.writeInt(SESSION_ID);
        parcel.writeInt(ImsMediaSession.RESULT_INVALID_PARAM);
        parcel.setDataPosition(0);
        mVideoListener.onMessage(parcel);
        doNothing().when(mMockCallback).onOpenSessionFailure(eq(SESSION_ID),
                eq(ImsMediaSession.RESULT_INVALID_PARAM));
        parcel.recycle();
        verify(mMockCallback, times(1)).onOpenSessionFailure(eq(SESSION_ID),
                eq(ImsMediaSession.RESULT_INVALID_PARAM));
    }

    @Test
    public void testEventModifySessionResponse() throws RemoteException {
        Parcel parcel = Parcel.obtain();
        parcel.writeInt(VideoSession.EVENT_MODIFY_SESSION_RESPONSE);
        parcel.writeInt(ImsMediaSession.RESULT_NO_RESOURCES);
        mVideoConfig.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        mVideoListener.onMessage(parcel);
        processAllMessages();
        parcel.recycle();
        verify(mMockIImsVideoSessionCallback,
                times(1)).onModifySessionResponse(eq(mVideoConfig),
                eq(ImsMediaSession.RESULT_NO_RESOURCES));
    }

    @Test
    public void testEventFirstMediaPacketInd() throws RemoteException {
        Parcel parcel = Parcel.obtain();
        parcel.writeInt(VideoSession.EVENT_FIRST_MEDIA_PACKET_IND);
        mVideoConfig.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        mVideoListener.onMessage(parcel);
        processAllMessages();
        parcel.recycle();
        verify(mMockIImsVideoSessionCallback,
                times(1)).onFirstMediaPacketReceived(eq(mVideoConfig));
    }

    @Test
    public void testEventPeerDimensionChanged() throws RemoteException {
        Parcel parcel = Parcel.obtain();
        parcel.writeInt(VideoSession.EVENT_PEER_DIMENSION_CHANGED);
        parcel.writeInt(RESOLUTION_WIDTH);
        parcel.writeInt(RESOLUTION_HEIGHT);
        parcel.setDataPosition(0);
        mVideoListener.onMessage(parcel);
        processAllMessages();
        parcel.recycle();
        verify(mMockIImsVideoSessionCallback,
                times(1)).onPeerDimensionChanged(eq(RESOLUTION_WIDTH),
                eq(RESOLUTION_HEIGHT));
    }

    @Test
    public void testEventMediaInactivityInd() throws RemoteException {
        Parcel parcel = createParcel(VideoSession.EVENT_MEDIA_INACTIVITY_IND,
                ImsMediaSession.PACKET_TYPE_RTP);
        mVideoListener.onMessage(parcel);
        processAllMessages();
        parcel.recycle();
        verify(mMockIImsVideoSessionCallback,
                times(1)).notifyMediaInactivity(eq(ImsMediaSession.PACKET_TYPE_RTP));
    }

    @Test
    public void testEventNotifyBitrateInd() throws RemoteException {
        Parcel parcel = createParcel(VideoSession.EVENT_NOTIFY_BITRATE_IND,
                ImsMediaSession.PACKET_TYPE_RTCP);
        mVideoListener.onMessage(parcel);
        processAllMessages();
        parcel.recycle();
        verify(mMockIImsVideoSessionCallback,
                times(1)).notifyBitrate(eq(ImsMediaSession.PACKET_TYPE_RTCP));
    }

    @Test
    public void testEventVideoDataUsageInd() throws RemoteException {
        Parcel parcel = Parcel.obtain();
        parcel.writeInt(VideoSession.EVENT_VIDEO_DATA_USAGE_IND);
        parcel.writeLong(VIDEO_DATA);
        parcel.setDataPosition(0);
        mVideoListener.onMessage(parcel);
        processAllMessages();
        parcel.recycle();
        verify(mMockIImsVideoSessionCallback,
                times(1)).notifyVideoDataUsage(eq(VIDEO_DATA));
    }

    @Test
    public void testEventSessionClosed() {
        Parcel parcel = createParcel(VideoSession.EVENT_SESSION_CLOSED, SESSION_ID);
        mVideoListener.onMessage(parcel);
        doNothing().when(mMockCallback).onSessionClosed(eq(SESSION_ID));
        parcel.recycle();
        verify(mMockCallback, times(1)).onSessionClosed(eq(SESSION_ID));
    }
}
