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

import android.hardware.radio.ims.media.IImsMediaSession;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.support.annotation.VisibleForTesting;
import android.telephony.CallQuality;
import android.telephony.ims.RtpHeaderExtension;
import android.telephony.imsmedia.AudioConfig;
import android.telephony.imsmedia.IImsAudioSession;
import android.telephony.imsmedia.IImsAudioSessionCallback;
import android.telephony.imsmedia.ImsMediaSession;
import android.telephony.imsmedia.MediaQualityStatus;
import android.telephony.imsmedia.MediaQualityThreshold;
import android.telephony.imsmedia.RtpConfig;
import android.telephony.imsmedia.RtpReceptionStats;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.telephony.imsmedia.Utils.OpenSessionParams;
import com.android.telephony.imsmedia.util.Log;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Audio session binder implementation which handles all audio session APIs
 * from the VOIP applications.
 */
public final class AudioSession extends IImsAudioSession.Stub implements IMediaSession {
    private static final String TAG = "AudioSession";

    public static final int CMD_OPEN_SESSION = 101;
    public static final int CMD_CLOSE_SESSION = 102;
    public static final int CMD_MODIFY_SESSION = 103;
    public static final int CMD_ADD_CONFIG = 104;
    public static final int CMD_DELETE_CONFIG = 105;
    public static final int CMD_CONFIRM_CONFIG = 106;
    public static final int CMD_SEND_DTMF = 107;
    public static final int CMD_SEND_RTP_HDR_EXTN = 108;
    public static final int CMD_SET_MEDIA_QUALITY_THRESHOLD = 109;
    public static final int CMD_START_DTMF = 110;
    public static final int CMD_STOP_DTMF = 111;
    public static final int CMD_REQUEST_RECEPTION_STATS = 112;
    public static final int CMD_ADJUST_DELAY = 113;

    public static final int EVENT_OPEN_SESSION_SUCCESS = 201;
    public static final int EVENT_OPEN_SESSION_FAILURE = 202;
    public static final int EVENT_MODIFY_SESSION_RESPONSE = 203;
    public static final int EVENT_ADD_CONFIG_RESPONSE = 204;
    public static final int EVENT_CONFIRM_CONFIG_RESPONSE = 205;
    public static final int EVENT_FIRST_MEDIA_PACKET_IND = 206;
    public static final int EVENT_RTP_HEADER_EXTENSION_IND = 207;
    public static final int EVENT_MEDIA_QUALITY_STATUS_IND = 208;
    public static final int EVENT_TRIGGER_ANBR_QUERY_IND = 209;
    public static final int EVENT_DTMF_RECEIVED_IND = 210;
    public static final int EVENT_CALL_QUALITY_CHANGE_IND = 211;
    public static final int EVENT_SESSION_CLOSED = 212;
    public static final int EVENT_NOTIFY_RECEPTION_STATS = 213;

    private static final int DTMF_DEFAULT_DURATION = 140;

    private int mSessionId;
    private AudioOffloadService mOffloadService;
    private AudioOffloadListener mOffloadListener;
    private IImsAudioSessionCallback mCallback;
    private IImsMediaSession mHalSession;
    private AudioSessionHandler mHandler;
    private boolean mIsAudioOffload;
    private AudioService mAudioService;
    private AudioListener mAudioListener;
    private AudioLocalSession mLocalSession;

    AudioSession(final int sessionId, final IImsAudioSessionCallback callback) {
        mSessionId = sessionId;
        mCallback = callback;
        mHandler = new AudioSessionHandler(Looper.getMainLooper());
        if (isAudioOffload()) {
            Log.d(TAG, "Initialize offload service");
            mOffloadService = AudioOffloadService.getInstance();
            mOffloadListener = new AudioOffloadListener(mHandler);
        } else {
            Log.d(TAG, "Initialize local audio service");
            mAudioService = new AudioService();
            mAudioListener = new AudioListener(mHandler);
            mAudioService.setListener(mAudioListener);
            mAudioListener.setNativeObject(mAudioService.getNativeObject());
        }
    }

    @VisibleForTesting
    AudioSession(final int sessionId,
            @NonNull final IImsAudioSessionCallback callback,
            @Nullable final AudioService audioService,
            @Nullable final AudioLocalSession localSession,
            @Nullable final AudioOffloadService offloadService,
            Looper looper) {
        mSessionId = sessionId;
        mCallback = callback;
        mHandler = new AudioSessionHandler(looper);
        mAudioService = audioService;
        mLocalSession = localSession;
        mAudioListener = new AudioListener(mHandler);
        mOffloadService = offloadService;
        mOffloadListener = new AudioOffloadListener(mHandler);
    }

    @VisibleForTesting
    void setAudioOffload(boolean isOffload) {
        mIsAudioOffload = isOffload;
    }

    @VisibleForTesting
    AudioSessionHandler getAudioSessionHandler() {
        return mHandler;
    }

    @VisibleForTesting
    AudioListener getAudioListener() {
        return mAudioListener;
    }

    AudioOffloadListener getOffloadListener() {
        return mOffloadListener;
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
    public void modifySession(AudioConfig config) {
        Log.d(TAG, "modifySession: " + Log.hidePii(String.valueOf(config)));
        Utils.sendMessage(mHandler, CMD_MODIFY_SESSION, config);
        WakeLockManager.getInstance().manageWakeLockOnMediaDirectionUpdate(
                mSessionId, config.getMediaDirection());
    }

    @Override
    public void addConfig(AudioConfig config) {
        Log.d(TAG, "addConfig: " + Log.hidePii(String.valueOf(config)));
        Utils.sendMessage(mHandler, CMD_ADD_CONFIG, config);
    }

    @Override
    public void deleteConfig(AudioConfig config) {
        Log.d(TAG, "deleteConfig: " + Log.hidePii(String.valueOf(config)));
        Utils.sendMessage(mHandler, CMD_DELETE_CONFIG, config);
    }

    @Override
    public void confirmConfig(AudioConfig config) {
        Log.d(TAG, "confirmConfig: " + Log.hidePii(String.valueOf(config)));
        Utils.sendMessage(mHandler, CMD_CONFIRM_CONFIG, config);
    }

    @Override
    public void sendDtmf(char digit, int duration) {
        Log.dc(TAG, "sendDtmf: digit=" + digit + ",duration=" + duration);
        Utils.sendMessage(mHandler, CMD_SEND_DTMF, duration, Utils.UNUSED, digit);
    }

    @Override
    public void startDtmf(char digit) {
        Log.dc(TAG, "startDtmf: digit=" + digit);
        Utils.sendMessage(mHandler, CMD_START_DTMF, digit);
    }

    @Override
    public void stopDtmf() {
        Log.dc(TAG, "stopDtmf");
        Utils.sendMessage(mHandler, CMD_STOP_DTMF);
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
    public void requestRtpReceptionStats(int intervalMs) {
        Log.d(TAG, "requestRtpReceptionStats: interval=" + intervalMs);
        Utils.sendMessage(mHandler, CMD_REQUEST_RECEPTION_STATS, intervalMs);
    }

    @Override
    public void adjustDelay(int delayMs) {
        Log.d(TAG, "adjustDelay: delay=" + delayMs);
        Utils.sendMessage(mHandler, CMD_ADJUST_DELAY, delayMs);
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

    private boolean isAudioOffload() {
        return mIsAudioOffload;
    }

    /**
     * Audio session message mHandler
     */
    class AudioSessionHandler extends Handler {
        AudioSessionHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage (Message msg) {
            Log.dc(TAG, "handleMessage() -" + AudioSessionHandler.this + ", " + msg.what);
            switch(msg.what) {
                case CMD_OPEN_SESSION:
                    handleOpenSession((OpenSessionParams)msg.obj);
                    break;
                case CMD_CLOSE_SESSION:
                    handleCloseSession();
                    break;
                case CMD_MODIFY_SESSION:
                    handleModifySession((AudioConfig)msg.obj);
                    break;
                case CMD_ADD_CONFIG:
                    handleAddConfig((AudioConfig)msg.obj);
                    break;
                case CMD_DELETE_CONFIG:
                    handleDeleteConfig((AudioConfig)msg.obj);
                    break;
                case CMD_CONFIRM_CONFIG:
                    handleConfirmConfig((AudioConfig)msg.obj);
                    break;
                case CMD_SEND_DTMF:
                    handleSendDtmf((char) msg.obj, msg.arg1);
                    break;
                case CMD_START_DTMF:
                    handleStartDtmf((char) msg.obj);
                    break;
                case CMD_STOP_DTMF:
                    handleStopDtmf();
                    break;
                case CMD_SEND_RTP_HDR_EXTN:
                    handleSendRtpHeaderExtension((List<RtpHeaderExtension>)msg.obj);
                    break;
                case CMD_SET_MEDIA_QUALITY_THRESHOLD:
                    handleSetMediaQualityThreshold((MediaQualityThreshold)msg.obj);
                    break;
                case CMD_REQUEST_RECEPTION_STATS:
                    handleRequestRtpReceptionStats((int) msg.obj);
                    break;
                case CMD_ADJUST_DELAY:
                    handleAdjustDelay((int) msg.obj);
                    break;
                case EVENT_OPEN_SESSION_SUCCESS:
                    handleOpenSuccess(msg.obj);
                    break;
                case EVENT_OPEN_SESSION_FAILURE:
                    handleOpenFailure((int)msg.obj);
                    break;
                case EVENT_SESSION_CLOSED:
                    handleSessionClosed();
                    break;
                case EVENT_MODIFY_SESSION_RESPONSE:
                    handleModifySessionRespose((AudioConfig)msg.obj, msg.arg1);
                    break;
                case EVENT_ADD_CONFIG_RESPONSE:
                    handleAddConfigResponse((AudioConfig)msg.obj, msg.arg1);
                    break;
                case EVENT_CONFIRM_CONFIG_RESPONSE:
                    handleConfirmConfigResponse((AudioConfig)msg.obj, msg.arg1);
                    break;
                case EVENT_FIRST_MEDIA_PACKET_IND:
                    handleFirstMediaPacketInd((AudioConfig)msg.obj);
                    break;
                case EVENT_RTP_HEADER_EXTENSION_IND:
                    handleRtpHeaderExtensionInd((List<RtpHeaderExtension>)msg.obj);
                    break;
                case EVENT_MEDIA_QUALITY_STATUS_IND:
                    handleNotifyMediaQualityStatus((MediaQualityStatus) msg.obj);
                    break;
                case EVENT_TRIGGER_ANBR_QUERY_IND:
                    handleTriggerAnbrQuery((AudioConfig) msg.obj);
                    break;
                case EVENT_DTMF_RECEIVED_IND:
                    handleDtmfReceived((char) msg.arg1, msg.arg2);
                    break;
                case EVENT_CALL_QUALITY_CHANGE_IND:
                    handleCallQualityChangeInd((CallQuality) msg.obj);
                    break;
                case EVENT_NOTIFY_RECEPTION_STATS:
                    handleNotifyReceptionStats((RtpReceptionStats) msg.obj);
                    break;
                default:
            }
        }
    }

    private void handleOpenSession(OpenSessionParams sessionParams) {
        if (isAudioOffload()) {
            mOffloadService.openSession(mSessionId, sessionParams);
        } else {
            mAudioListener.setMediaCallback(sessionParams.getCallback());
            int result = mAudioService.openSession(mSessionId, sessionParams);
            if (result != ImsMediaSession.RESULT_SUCCESS) {
                handleOpenFailure(result);
            }
        }
    }

    private void handleCloseSession() {
        if (isAudioOffload()) {
            mOffloadService.closeSession(mSessionId);
        } else {
            mAudioService.closeSession(mSessionId);
        }
    }

    private void handleModifySession(AudioConfig config) {
        if (isAudioOffload()) {
            try {
                mHalSession.modifySession(Utils.convertToRtpConfig(config));
            } catch (RemoteException e) {
                Log.e(TAG, "modifySession : " + e);
            }
        } else {
            mLocalSession.modifySession(config);
        }
    }

    private void handleAddConfig(AudioConfig config) {
        if (isAudioOffload()) {
            try {
                mHalSession.modifySession(Utils.convertToRtpConfig(config));
            } catch (RemoteException e) {
                Log.e(TAG, "addConfig : " + e);
            }
        } else {
            mLocalSession.addConfig(config);
        }
    }

    private void handleDeleteConfig(AudioConfig config) {
        if (!isAudioOffload()) {
            mLocalSession.deleteConfig(config);
        }
    }

    private void handleConfirmConfig(AudioConfig config) {
        if (!isAudioOffload()) {
            mLocalSession.confirmConfig(config);
        }
    }

    private void handleSendDtmf(char digit, int duration) {
        if (isAudioOffload()) {
            try {
                mHalSession.sendDtmf(digit, duration);
            } catch (RemoteException e) {
                Log.e(TAG, "sendDtmf : " + e);
            }
        } else {
            mLocalSession.sendDtmf(digit, duration);
        }
    }

    private void handleStartDtmf(char digit) {
        if (isAudioOffload()) {
            try {
                mHalSession.startDtmf(digit);
            } catch (RemoteException e) {
                Log.e(TAG, "startDtmf : " + e);
            }
        } else {
            mLocalSession.sendDtmf(digit, DTMF_DEFAULT_DURATION);
        }
    }

    private void handleStopDtmf() {
        if (isAudioOffload()) {
            try {
                mHalSession.stopDtmf();
            } catch (RemoteException e) {
                Log.e(TAG, "stopDtmf : " + e);
            }
        }
    }

    private void handleSendRtpHeaderExtension(List<RtpHeaderExtension> extensions) {
        if (isAudioOffload()) {
            try {
                List<android.hardware.radio.ims.media.RtpHeaderExtension>
                        halExtensions = extensions.stream().map(Utils::convertRtpHeaderExtension)
                                .collect(Collectors.toList());
                mHalSession.sendHeaderExtension(halExtensions);
            } catch (RemoteException e) {
                Log.e(TAG, "sendHeaderExtension : " + e);
            }
        } else {
            mLocalSession.sendHeaderExtension(extensions);
        }
    }

    private void handleSetMediaQualityThreshold(MediaQualityThreshold threshold) {
        if (isAudioOffload()) {
            try {
                mHalSession.setMediaQualityThreshold(Utils.convertMediaQualityThreshold(threshold));
            } catch (RemoteException e) {
                Log.e(TAG, "setMediaQualityThreshold: " + e);
            }
        } else {
            mLocalSession.setMediaQualityThreshold(threshold);
        }
    }

    private void handleRequestRtpReceptionStats(int intervalMs) {
        if (!isAudioOffload()) {
            mLocalSession.requestRtpReceptionStats(intervalMs);
        }
    }

    private void handleAdjustDelay(int delayMs) {
        if (!isAudioOffload()) {
            mLocalSession.adjustDelay(delayMs);
        }
    }

    private void handleOpenSuccess(Object session) {
       if (session instanceof IImsMediaSession) {
            try {
                ((IImsMediaSession)session).setListener(mOffloadListener);
            } catch (RemoteException e) {
                Log.e(TAG, "Failed to notify openSuccess: " + e);
            }
            mHalSession = (IImsMediaSession) session;
        } else {
            mLocalSession = (AudioLocalSession)session;
        }

        try {
            mCallback.onOpenSessionSuccess(this);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to notify openSuccess: " + e);
        }
    }

    private void handleOpenFailure(int error) {
        try {
            mCallback.onOpenSessionFailure(error);
        }  catch (RemoteException e) {
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

    private void handleModifySessionRespose(AudioConfig config, int error) {
        try {
            if (error != ImsMediaSession.RESULT_SUCCESS) {
                Log.e(TAG, "modifySession failed with error: " + error);
            }
            mCallback.onModifySessionResponse(config, error);
        }  catch (RemoteException e) {
            Log.e(TAG, "Failed to notify modifySessionResponse: " + e);
        }
    }

    private void handleAddConfigResponse(AudioConfig config, int error) {
        try {
            mCallback.onAddConfigResponse(config, error);
        }  catch (RemoteException e) {
            Log.e(TAG, "Failed to notify onAddConfigResponse: " + e);
        }
    }

    private void handleConfirmConfigResponse(AudioConfig config, int error) {
        try {
            mCallback.onConfirmConfigResponse(config, error);
        }  catch (RemoteException e) {
            Log.e(TAG, "Failed to notify onConfirmConfigResponse: " + e);
        }
    }

    private void handleFirstMediaPacketInd(AudioConfig config) {
        try {
            mCallback.onFirstMediaPacketReceived(config);
        }  catch (RemoteException e) {
            Log.e(TAG, "Failed to notify first media packet received indication: " + e);
        }
    }

    private void handleRtpHeaderExtensionInd(List<RtpHeaderExtension> extensions) {
        try {
            mCallback.onHeaderExtensionReceived(extensions);
        }  catch (RemoteException e) {
            Log.e(TAG, "Failed to notify RTP header extension: " + e);
        }
    }

    private void handleNotifyMediaQualityStatus(MediaQualityStatus status) {
        try {
            mCallback.notifyMediaQualityStatus(status);
        }  catch (RemoteException e) {
            Log.e(TAG, "Failed to notify media quality status: " + e);
        }
    }

    private void handleTriggerAnbrQuery(AudioConfig config) {
        try {
            mCallback.triggerAnbrQuery(config);
        }  catch (RemoteException e) {
            Log.e(TAG, "Failed to trigger ANBR query: " + e);
        }
    }

    private void handleDtmfReceived(char dtmfDigit, int durationMs) {
        try {
            mCallback.onDtmfReceived(dtmfDigit, durationMs);
        }  catch (RemoteException e) {
            Log.e(TAG, "Failed to Dtmf received: " + e);
        }
    }

    private void handleCallQualityChangeInd(CallQuality callQuality) {
        try {
            mCallback.onCallQualityChanged(callQuality);
        }  catch (RemoteException e) {
            Log.e(TAG, "Failed to notify call quality changed indication: " + e);
        }
    }

    private void handleNotifyReceptionStats(RtpReceptionStats stats) {
        try {
            mCallback.notifyRtpReceptionStats(stats);
        }  catch (RemoteException e) {
            Log.e(TAG, "Failed to notify rtp reception statistics: " + e);
        }
    }
}
