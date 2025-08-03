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

import android.graphics.ImageFormat;
import android.media.ImageReader;
import android.os.Looper;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.telephony.ims.RtpHeaderExtension;
import android.telephony.imsmedia.IImsVideoSessionCallback;
import android.telephony.imsmedia.ImsMediaSession;
import android.telephony.imsmedia.MediaQualityThreshold;
import android.telephony.imsmedia.VideoConfig;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.view.Surface;

import com.android.telephony.imsmedia.Utils.OpenSessionParams;

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
public class VideoSessionTest extends ImsMediaTest {
    private static final int SESSION_ID = 1;
    private static final int UNUSED = -1;
    private static final int SUCCESS = ImsMediaSession.RESULT_SUCCESS;
    private static final int NO_RESOURCES = ImsMediaSession.RESULT_NO_RESOURCES;
    private static final int RTP = ImsMediaSession.PACKET_TYPE_RTP;
    private static final int RTCP = ImsMediaSession.PACKET_TYPE_RTCP;
    private static final int RESOLUTION_WIDTH = 640;
    private static final int RESOLUTION_HEIGHT = 480;
    private static final long VIDEO_DATA = 1024;
    private static final int PACKET_LOSS = 15;
    private static final Surface PREVIEW_SURFACE = ImageReader.newInstance(
            RESOLUTION_WIDTH, RESOLUTION_HEIGHT, ImageFormat.JPEG, 1).getSurface();
    private static final Surface DISPLAY_SURFACE = ImageReader.newInstance(
            RESOLUTION_WIDTH, RESOLUTION_HEIGHT, ImageFormat.JPEG, 1).getSurface();
    private VideoSession mVideoSession;
    private VideoSession.VideoSessionHandler mHandler;
    private WakeLockManager mWakeLockManager;
    @Mock
    private VideoService mVideoService;
    private VideoListener mVideoListener;
    @Mock
    private VideoLocalSession mVideoLocalSession;
    @Mock
    private IImsVideoSessionCallback mCallback;
    private TestableLooper mLooper;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mVideoSession = new VideoSession(SESSION_ID, mCallback,
                mVideoService, mVideoLocalSession, Looper.myLooper());
        mVideoListener = mVideoSession.getVideoListener();
        mHandler = mVideoSession.getVideoSessionHandler();
        mTestClass = VideoSessionTest.this;
        mWakeLockManager = WakeLockManager.getInstance();
        super.setUp();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
        mWakeLockManager.cleanup();
    }

    private Parcel createParcel(int message, int result, VideoConfig config) {
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

        mVideoSession.openSession(params);
        processAllMessages();
        verify(mVideoService, times(1)).openSession(eq(SESSION_ID), eq(params));
        assertThat(mWakeLockManager.mWakeLock.isHeld()).isEqualTo(false);
    }

    @Test
    public void testCloseSession() {
        mVideoSession.closeSession();
        processAllMessages();
        verify(mVideoService, times(1)).closeSession(eq(SESSION_ID));
        assertThat(mWakeLockManager.mWakeLock.isHeld()).isEqualTo(false);
    }

    @Test
    public void testModifySession() {
        // Modify Session Request
        VideoConfig config = VideoConfigTest.createVideoConfig();
        mVideoSession.modifySession(config);
        processAllMessages();
        verify(mVideoLocalSession, times(1)).modifySession(eq(config));
        assertThat(mWakeLockManager.mWakeLock.isHeld()).isEqualTo(true);

        // Modify Session Response - Success
        mVideoListener.onMessage(
                createParcel(VideoSession.EVENT_MODIFY_SESSION_RESPONSE, SUCCESS, config));
        processAllMessages();
        try {
            verify(mCallback, times(1)).onModifySessionResponse(eq(config), eq(SUCCESS));
        } catch (RemoteException e) {
            fail("Failed to notify modify session response: " + e);
        }

        // Modify Session Response - Failure (NO_RESOURCES)
        mVideoListener.onMessage(
                createParcel(VideoSession.EVENT_MODIFY_SESSION_RESPONSE, NO_RESOURCES, config));
        processAllMessages();
        try {
            verify(mCallback, times(1)).onModifySessionResponse(eq(config), eq(NO_RESOURCES));
        } catch (RemoteException e) {
            fail("Failed to notify modify session response: " + e);
        }
    }

    @Test
    public void testSurfaces() {
        Utils.sendMessage(mHandler, VideoSession.CMD_SET_PREVIEW_SURFACE, PREVIEW_SURFACE);
        processAllMessages();
        verify(mVideoLocalSession, times(1)).setPreviewSurface(eq(PREVIEW_SURFACE));

        Utils.sendMessage(mHandler, VideoSession.CMD_SET_DISPLAY_SURFACE, DISPLAY_SURFACE);
        processAllMessages();
        verify(mVideoLocalSession, times(1)).setDisplaySurface(eq(DISPLAY_SURFACE));
    }

    @Test
    public void testSetMediaQualityThreshold() {
        // Set Media Quality Threshold
        MediaQualityThreshold threshold = MediaQualityThresholdTest.createMediaQualityThreshold();
        mVideoSession.setMediaQualityThreshold(threshold);
        processAllMessages();
        verify(mVideoLocalSession, times(1)).setMediaQualityThreshold(eq(threshold));
    }

    @Test
    public void testRequestVideoDataUsage() {
        mVideoSession.requestVideoDataUsage();
        processAllMessages();
        verify(mVideoLocalSession, times(1)).requestVideoDataUsage();
    }

    @Test
    public void testMediaInactivityInd() {
        // Receive Inactivity - RTP
        Parcel parcel = Parcel.obtain();
        parcel.writeInt(VideoSession.EVENT_MEDIA_INACTIVITY_IND);
        parcel.writeInt(RTP);
        parcel.setDataPosition(0);
        mVideoListener.onMessage(parcel);
        processAllMessages();
        try {
            verify(mCallback, times(1)).notifyMediaInactivity(eq(RTP));
        } catch (RemoteException e) {
            fail("Failed to notify media inactivity: " + e);
        }

        // Receive Inactivity - RTCP
        Parcel parcel2 = Parcel.obtain();
        parcel2.writeInt(VideoSession.EVENT_MEDIA_INACTIVITY_IND);
        parcel2.writeInt(RTCP);
        parcel2.setDataPosition(0);
        mVideoListener.onMessage(parcel2);
        processAllMessages();
        try {
            verify(mCallback, times(1)).notifyMediaInactivity(eq(RTCP));
        } catch (RemoteException e) {
            fail("Failed to notify media inactivity: " + e);
        }
    }

    @Test
    public void testPeerDimensionChanged() {
        // received video frame resolution changed
        Parcel parcel = Parcel.obtain();
        parcel.writeInt(VideoSession.EVENT_PEER_DIMENSION_CHANGED);
        parcel.writeInt(RESOLUTION_WIDTH);
        parcel.writeInt(RESOLUTION_HEIGHT);
        parcel.setDataPosition(0);
        mVideoListener.onMessage(parcel);
        processAllMessages();
        try {
            verify(mCallback, times(1)).onPeerDimensionChanged(
                    eq(RESOLUTION_WIDTH), eq(RESOLUTION_HEIGHT));
        } catch (RemoteException e) {
            fail("Failed to notify peer dimension changed: " + e);
        }
    }

    @Test
    public void testNotifyVideoDataUsage() {
        Parcel parcel = Parcel.obtain();
        parcel.writeInt(VideoSession.EVENT_VIDEO_DATA_USAGE_IND);
        parcel.writeLong(VIDEO_DATA);
        parcel.setDataPosition(0);
        mVideoListener.onMessage(parcel);
        processAllMessages();
        try {
            verify(mCallback, times(1)).notifyVideoDataUsage(eq(VIDEO_DATA));
        } catch (RemoteException e) {
            fail("Failed to notify video data usage: " + e);
        }
    }

    @Test
    public void testFirstMediaPacketReceivedInd() {
        // Receive First MediaPacket Received Indication
        VideoConfig config = VideoConfigTest.createVideoConfig();
        Utils.sendMessage(mHandler, VideoSession.EVENT_FIRST_MEDIA_PACKET_IND, config);
        processAllMessages();
        try {
            verify(mCallback, times(1)).onFirstMediaPacketReceived(eq(config));
        } catch (RemoteException e) {
            fail("Failed to notify first media packet received: " + e);
        }
    }

    @Test
    public void testHeaderExtension() {
        // Send RtpHeaderExtension
        ArrayList extensions = new ArrayList<RtpHeaderExtension>();
        mVideoSession.sendHeaderExtension(extensions);
        processAllMessages();
        verify(mVideoLocalSession, times(1)).sendHeaderExtension(eq(extensions));

        // Receive RtpHeaderExtension
        Utils.sendMessage(mHandler, VideoSession.EVENT_RTP_HEADER_EXTENSION_IND, extensions);
        processAllMessages();
        try {
            verify(mCallback, times(1)).onHeaderExtensionReceived(eq(extensions));
        } catch (RemoteException e) {
            fail("Failed to notify header extension received: " + e);
        }
    }

    @Test
    public void testPacketLossInd() {
        // Receive Packet Loss
        Utils.sendMessage(mHandler, VideoSession.EVENT_NOTIFY_BITRATE_IND, PACKET_LOSS, UNUSED);
        processAllMessages();
        try {
            verify(mCallback, times(1)).notifyBitrate(eq(PACKET_LOSS));
        } catch (RemoteException e) {
            fail("Failed to notify notifyBitrate: " + e);
        }
    }

    @Test
    public void testOpenSessionSuccess() {
        mVideoSession.onOpenSessionSuccess(mVideoLocalSession);
        processAllMessages();
        try {
            verify(mCallback, times(1)).onOpenSessionSuccess(mVideoSession);
            assertThat(mWakeLockManager.mWakeLock.isHeld()).isEqualTo(false);
        } catch (RemoteException e) {
            fail("Failed to notify onOpenSessionSuccess: " + e);
        }
    }

    @Test
    public void testOpenSessionFailure() {
        mVideoSession.onOpenSessionFailure(ImsMediaSession.RESULT_INVALID_PARAM);
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
        mVideoSession.onSessionClosed();
        processAllMessages();
        try {
            verify(mCallback, times(1)).onSessionClosed();
            assertThat(mWakeLockManager.mWakeLock.isHeld()).isEqualTo(false);
        } catch (RemoteException e) {
            fail("Failed to notify onSessionClosed: " + e);
        }
    }
}
