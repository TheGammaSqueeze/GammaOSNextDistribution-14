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

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.support.annotation.VisibleForTesting;
import android.telephony.ims.RtpHeaderExtension;
import android.telephony.imsmedia.IImsVideoSession;
import android.telephony.imsmedia.IImsVideoSessionCallback;
import android.telephony.imsmedia.ImsMediaSession;
import android.telephony.imsmedia.MediaQualityThreshold;
import android.telephony.imsmedia.RtpConfig;
import android.telephony.imsmedia.VideoConfig;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.telephony.imsmedia.Utils.OpenSessionParams;
import com.android.telephony.imsmedia.util.Log;

import java.util.List;

/**
 * Video session binder implementation which handles all video session APIs
 * from the VT applications.
 */
public final class VideoSession extends IImsVideoSession.Stub implements IMediaSession {
    private static final String TAG = "VideoSession";

    public static final int CMD_OPEN_SESSION = 101;
    public static final int CMD_CLOSE_SESSION = 102;
    public static final int CMD_MODIFY_SESSION = 103;
    public static final int CMD_SET_PREVIEW_SURFACE = 104;
    public static final int CMD_SET_DISPLAY_SURFACE = 105;
    public static final int CMD_SEND_RTP_HDR_EXTN = 106;
    public static final int CMD_SET_MEDIA_QUALITY_THRESHOLD = 107;
    public static final int CMD_REQUEST_VIDEO_DATA_USAGE = 108;

    public static final int EVENT_OPEN_SESSION_SUCCESS = 201;
    public static final int EVENT_OPEN_SESSION_FAILURE = 202;
    public static final int EVENT_MODIFY_SESSION_RESPONSE = 203;
    public static final int EVENT_FIRST_MEDIA_PACKET_IND = 204;
    public static final int EVENT_PEER_DIMENSION_CHANGED = 205;
    public static final int EVENT_RTP_HEADER_EXTENSION_IND = 206;
    public static final int EVENT_MEDIA_INACTIVITY_IND = 207;
    public static final int EVENT_NOTIFY_BITRATE_IND = 208;
    public static final int EVENT_VIDEO_DATA_USAGE_IND = 209;
    public static final int EVENT_SESSION_CLOSED = 210;

    private int mSessionId;
    private IImsVideoSessionCallback mCallback;
    private VideoSessionHandler mHandler;
    private VideoService mVideoService;
    private VideoListener mVideoListener;
    private VideoLocalSession mLocalSession;

    VideoSession(final int sessionId, final IImsVideoSessionCallback callback) {
        mSessionId = sessionId;
        mCallback = callback;
        mHandler = new VideoSessionHandler(Looper.getMainLooper());
        Log.d(TAG, "Initialize local video service");
        mVideoService = new VideoService();
        mVideoListener = new VideoListener(mHandler);
        mVideoService.setListener(mVideoListener);
        mVideoListener.setNativeObject(mVideoService.getNativeObject());
    }

    @VisibleForTesting
    VideoSession(final int sessionId,
            final @NonNull IImsVideoSessionCallback callback,
            final @Nullable VideoService videoService,
            final @Nullable VideoLocalSession localSession, Looper looper) {
        mSessionId = sessionId;
        mCallback = callback;
        mHandler = new VideoSessionHandler(looper);
        mVideoService = videoService;
        mLocalSession = localSession;
        mVideoListener = new VideoListener(mHandler);
    }

    @VisibleForTesting
    VideoSessionHandler getVideoSessionHandler() {
        return mHandler;
    }

    @VisibleForTesting
    VideoListener getVideoListener() {
        return mVideoListener;
    }

    @Override
    public void openSession(OpenSessionParams sessionParams) {
        Utils.sendMessage(mHandler, CMD_OPEN_SESSION, sessionParams);
        RtpConfig rtpConfig = sessionParams.getRtpConfig();
        if (rtpConfig != null) {
            WakeLockManager.getInstance().manageWakeLockOnMediaDirectionUpdate(
                    mSessionId, rtpConfig.getMediaDirection());
        }
    }

    @Override
    public void closeSession() {
        Utils.sendMessage(mHandler, CMD_CLOSE_SESSION);
        WakeLockManager.getInstance().manageWakeLockOnMediaDirectionUpdate(
                mSessionId, RtpConfig.MEDIA_DIRECTION_NO_FLOW);
    }

    @Override
    public int getSessionId() {
        return mSessionId;
    }

    @Override
    public void modifySession(VideoConfig config) {
        Log.d(TAG, "modifySession: " + Log.hidePii(String.valueOf(config)));
        Utils.sendMessage(mHandler, CMD_MODIFY_SESSION, config);
        WakeLockManager.getInstance().manageWakeLockOnMediaDirectionUpdate(
                mSessionId, config.getMediaDirection());
    }

    @Override
    public void setPreviewSurface(Surface surface) {
        Log.dc(TAG, "setPreviewSurface: " + surface);
        Utils.sendMessage(mHandler, CMD_SET_PREVIEW_SURFACE, surface);
    }

    @Override
    public void setDisplaySurface(Surface surface) {
        Log.dc(TAG, "setDisplaySurface: " + surface);
        Utils.sendMessage(mHandler, CMD_SET_DISPLAY_SURFACE, surface);
    }

    @Override
    public void sendHeaderExtension(List<RtpHeaderExtension> extensions) {
        Log.d(TAG, "sendHeaderExtension" + Log.hidePii(String.valueOf(extensions)));
        Utils.sendMessage(mHandler, CMD_SEND_RTP_HDR_EXTN, extensions);
    }

    @Override
    public void setMediaQualityThreshold(MediaQualityThreshold threshold) {
        Log.d(TAG, "setMediaQualityThreshold: " + Log.hidePii(String.valueOf(threshold)));
        Utils.sendMessage(mHandler, CMD_SET_MEDIA_QUALITY_THRESHOLD, threshold);
    }

    @Override
    public void requestVideoDataUsage() {
        Log.dc(TAG, "requestVideoDataUsage: ");
        Utils.sendMessage(mHandler, CMD_REQUEST_VIDEO_DATA_USAGE);
    }

    @Override
    public void onOpenSessionSuccess(Object session) {
        Utils.sendMessage(mHandler, EVENT_OPEN_SESSION_SUCCESS, session);
    }

    @Override
    public void onOpenSessionFailure(int error) {
        Utils.sendMessage(mHandler, EVENT_OPEN_SESSION_FAILURE, error);
    }

    @Override
    public void onSessionClosed() {
        Utils.sendMessage(mHandler, EVENT_SESSION_CLOSED);
    }

    /**
     * Video session message mHandler
     */
    class VideoSessionHandler extends Handler {
        VideoSessionHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            Log.dc(TAG, "handleMessage() -" + VideoSessionHandler.this + ", " + msg.what);
            switch(msg.what) {
                case CMD_OPEN_SESSION:
                    handleOpenSession((OpenSessionParams) msg.obj);
                    break;
                case CMD_CLOSE_SESSION:
                    handleCloseSession();
                    break;
                case CMD_MODIFY_SESSION:
                    handleModifySession((VideoConfig) msg.obj);
                    break;
                case CMD_SET_PREVIEW_SURFACE:
                    handleSetPreviewSurface((Surface) msg.obj);
                    break;
                case CMD_SET_DISPLAY_SURFACE:
                    handleSetDisplaySurface((Surface) msg.obj);
                    break;
                case CMD_SEND_RTP_HDR_EXTN:
                    handleSendRtpHeaderExtension((List<RtpHeaderExtension>) msg.obj);
                    break;
                case CMD_SET_MEDIA_QUALITY_THRESHOLD:
                    handleSetMediaQualityThreshold((MediaQualityThreshold) msg.obj);
                    break;
                case CMD_REQUEST_VIDEO_DATA_USAGE:
                    handleRequestVideoDataUsage();
                    break;
                case EVENT_OPEN_SESSION_SUCCESS:
                    handleOpenSuccess(msg.obj);
                    break;
                case EVENT_OPEN_SESSION_FAILURE:
                    handleOpenFailure((int) msg.obj);
                    break;
                case EVENT_SESSION_CLOSED:
                    handleSessionClosed();
                    break;
                case EVENT_MODIFY_SESSION_RESPONSE:
                    handleModifySessionRespose((VideoConfig) msg.obj, msg.arg1);
                    break;
                case EVENT_FIRST_MEDIA_PACKET_IND:
                    handleFirstMediaPacketInd((VideoConfig) msg.obj);
                    break;
                case EVENT_PEER_DIMENSION_CHANGED:
                    handlePeerDimensionChanged(msg.arg1, msg.arg2);
                    break;
                case EVENT_RTP_HEADER_EXTENSION_IND:
                    handleRtpHeaderExtensionInd((List<RtpHeaderExtension>) msg.obj);
                    break;
                case EVENT_MEDIA_INACTIVITY_IND:
                    handleNotifyMediaInactivityInd(msg.arg1);
                    break;
                case EVENT_NOTIFY_BITRATE_IND:
                    handleNotifyBitrateInd(msg.arg1);
                    break;
                case EVENT_VIDEO_DATA_USAGE_IND:
                    handleNotifyVideoDataUsage((long) msg.obj);
                    break;
                default:
            }
        }
    }

    private void handleOpenSession(OpenSessionParams sessionParams) {
        mVideoListener.setMediaCallback(sessionParams.getCallback());
        int result = mVideoService.openSession(mSessionId, sessionParams);
        if (result != ImsMediaSession.RESULT_SUCCESS) {
            handleOpenFailure(result);
        }
    }

    private void handleCloseSession() {
        mVideoService.closeSession(mSessionId);
    }

    private void handleModifySession(VideoConfig config) {
        mLocalSession.modifySession(config);
    }

    private void handleSetPreviewSurface(Surface surface) {
        mLocalSession.setPreviewSurface(surface);
    }

    private void handleSetDisplaySurface(Surface surface) {
        mLocalSession.setDisplaySurface(surface);
    }

    private void handleSendRtpHeaderExtension(List<RtpHeaderExtension> extensions) {
        mLocalSession.sendHeaderExtension(extensions);
    }

    private void handleSetMediaQualityThreshold(MediaQualityThreshold threshold) {
        mLocalSession.setMediaQualityThreshold(threshold);
    }

    private void handleRequestVideoDataUsage() {
        mLocalSession.requestVideoDataUsage();
    }

    private void handleOpenSuccess(Object session) {
        mLocalSession = (VideoLocalSession) session;
        try {
            mCallback.onOpenSessionSuccess(this);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to notify openSuccess: " + e);
        }
    }

    private void handleOpenFailure(int error) {
        try {
            mCallback.onOpenSessionFailure(error);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to notify openFailure: " + e);
        } finally {
            WakeLockManager.getInstance().manageWakeLockOnMediaDirectionUpdate(
                    mSessionId, RtpConfig.MEDIA_DIRECTION_NO_FLOW);
        }
    }

    private void handleSessionClosed() {
        try {
            mCallback.onSessionClosed();
        }  catch (RemoteException e) {
            Log.e(TAG, "Failed to notify SessionClosed: " + e);
        }
    }

    private void handleModifySessionRespose(VideoConfig config, int error) {
        try {
            mCallback.onModifySessionResponse(config, error);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to notify modifySessionResponse: " + e);
        }
    }

    private void handleFirstMediaPacketInd(VideoConfig config) {
        try {
            mCallback.onFirstMediaPacketReceived(config);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to notify first media packet received indication: " + e);
        }
    }

    private void handlePeerDimensionChanged(int width, int height) {
        try {
            mCallback.onPeerDimensionChanged(width, height);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to notify dimension changed: " + e);
        }
    }

    private void handleRtpHeaderExtensionInd(List<RtpHeaderExtension> extensions) {
        try {
            mCallback.onHeaderExtensionReceived(extensions);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to notify RTP header extension: " + e);
        }
    }

    private void handleNotifyMediaInactivityInd(int packetType) {
        try {
            mCallback.notifyMediaInactivity(packetType);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to notify media timeout: " + e);
        }
    }

    private void handleNotifyBitrateInd(int percentage) {
        try {
            mCallback.notifyBitrate(percentage);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to notify packet loss: " + e);
        }
    }

    private void handleNotifyVideoDataUsage(long bytes) {
        try {
            mCallback.notifyVideoDataUsage(bytes);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to notify video data usage: " + e);
        }
    }
}
