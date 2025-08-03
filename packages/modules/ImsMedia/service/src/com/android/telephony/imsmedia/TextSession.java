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
import android.telephony.imsmedia.IImsTextSession;
import android.telephony.imsmedia.IImsTextSessionCallback;
import android.telephony.imsmedia.ImsMediaSession;
import android.telephony.imsmedia.MediaQualityThreshold;
import android.telephony.imsmedia.RtpConfig;
import android.telephony.imsmedia.TextConfig;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.telephony.imsmedia.Utils.OpenSessionParams;
import com.android.telephony.imsmedia.util.Log;

/**
 * Text session binder implementation which handles all text session APIs from the text service.
 */
public final class TextSession extends IImsTextSession.Stub implements IMediaSession {
    private static final String TAG = "TextSession";

    public static final int CMD_OPEN_SESSION = 101;
    public static final int CMD_CLOSE_SESSION = 102;
    public static final int CMD_MODIFY_SESSION = 103;
    public static final int CMD_SET_MEDIA_QUALITY_THRESHOLD = 104;
    public static final int CMD_SEND_RTT = 105;

    public static final int EVENT_OPEN_SESSION_SUCCESS = 201;
    public static final int EVENT_OPEN_SESSION_FAILURE = 202;
    public static final int EVENT_MODIFY_SESSION_RESPONSE = 203;
    public static final int EVENT_MEDIA_INACTIVITY_IND = 204;
    public static final int EVENT_RTT_RECEIVED = 205;
    public static final int EVENT_SESSION_CLOSED = 206;

    private int mSessionId;
    private IImsTextSessionCallback mCallback;
    private TextSessionHandler mHandler;
    private TextService mTextService;
    private TextListener mTextListener;
    private TextLocalSession mLocalSession;

    TextSession(final int sessionId, final IImsTextSessionCallback callback) {
        mSessionId = sessionId;
        mCallback = callback;
        mHandler = new TextSessionHandler(Looper.getMainLooper());
        Log.d(TAG, "Initialize local text service");
        mTextService = new TextService();
        mTextListener = new TextListener(mHandler);
        mTextService.setListener(mTextListener);
        mTextListener.setNativeObject(mTextService.getNativeObject());
    }

    @VisibleForTesting
    TextSession(final int sessionId,
            final @NonNull IImsTextSessionCallback callback,
            final @Nullable TextService textService,
            final @Nullable TextLocalSession localSession, Looper looper) {
        mSessionId = sessionId;
        mCallback = callback;
        mHandler = new TextSessionHandler(looper);
        mTextService = textService;
        mLocalSession = localSession;
        mTextListener = new TextListener(mHandler);
    }

    @VisibleForTesting
    TextSessionHandler getTextSessionHandler() {
        return mHandler;
    }

    @VisibleForTesting
    TextListener getTextListener() {
        return mTextListener;
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
    public void modifySession(TextConfig config) {
        Log.d(TAG, "modifySession: " + Log.hidePii(String.valueOf(config)));
        Utils.sendMessage(mHandler, CMD_MODIFY_SESSION, config);
        WakeLockManager.getInstance().manageWakeLockOnMediaDirectionUpdate(
                mSessionId, config.getMediaDirection());
    }

    @Override
    public void setMediaQualityThreshold(MediaQualityThreshold threshold) {
        Log.d(TAG, "setMediaQualityThreshold: " + Log.hidePii(String.valueOf(threshold)));
        Utils.sendMessage(mHandler, CMD_SET_MEDIA_QUALITY_THRESHOLD, threshold);
    }

    @Override
    public void sendRtt(String text) {
        Log.dc(TAG, "sendRtt: ");
        Utils.sendMessage(mHandler, CMD_SEND_RTT, text);
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
     * Text session message mHandler
     */
    class TextSessionHandler extends Handler {
        TextSessionHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            Log.dc(TAG, "handleMessage() -" + TextSessionHandler.this + ", " + msg.what);
            switch (msg.what) {
                case CMD_OPEN_SESSION:
                    handleOpenSession((OpenSessionParams) msg.obj);
                    break;
                case CMD_CLOSE_SESSION:
                    handleCloseSession();
                    break;
                case CMD_MODIFY_SESSION:
                    handleModifySession((TextConfig) msg.obj);
                    break;
                case CMD_SET_MEDIA_QUALITY_THRESHOLD:
                    handleSetMediaQualityThreshold((MediaQualityThreshold) msg.obj);
                    break;
                case CMD_SEND_RTT:
                    handleSendRtt((String) msg.obj);
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
                    handleModifySessionRespose((TextConfig) msg.obj, msg.arg1);
                    break;
                case EVENT_MEDIA_INACTIVITY_IND:
                    handleNotifyMediaInactivityInd(msg.arg1);
                    break;
                case EVENT_RTT_RECEIVED:
                    handleRttReceived((String) msg.obj);
                    break;
                default:
            }
        }
    }

    private void handleOpenSession(OpenSessionParams sessionParams) {
        mTextListener.setMediaCallback(sessionParams.getCallback());
        Log.d(TAG, "handleOpenSession");
        int result = mTextService.openSession(mSessionId, sessionParams);
        if (result != ImsMediaSession.RESULT_SUCCESS) {
            handleOpenFailure(result);
        }
    }

    private void handleCloseSession() {
        mTextService.closeSession(mSessionId);
    }

    private void handleModifySession(TextConfig config) {
        mLocalSession.modifySession(config);
    }

    private void handleSetMediaQualityThreshold(MediaQualityThreshold threshold) {
        mLocalSession.setMediaQualityThreshold(threshold);
    }

    private void handleSendRtt(String text) {
        mLocalSession.sendRtt(text);
    }

    private void handleOpenSuccess(Object session) {
        mLocalSession = (TextLocalSession) session;
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
        }  finally {
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

    private void handleModifySessionRespose(TextConfig config, int error) {
        try {
            if (error != ImsMediaSession.RESULT_SUCCESS) {
                Log.e(TAG, "modifySession failed with error: " + error);
            }
            mCallback.onModifySessionResponse(config, error);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to notify modifySessionResponse: " + e);
        }
    }

    private void handleNotifyMediaInactivityInd(int packetType) {
        try {
            mCallback.notifyMediaInactivity(packetType);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to notify media timeout: " + e);
        }
    }

    private void handleRttReceived(String text) {
        try {
            mCallback.onRttReceived(text);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to notify rtt received: " + e);
        }
    }
}
