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

import android.hardware.radio.ims.media.IImsMedia;
import android.hardware.radio.ims.media.IImsMediaListener;
import android.hardware.radio.ims.media.IImsMediaSession;
import android.hardware.radio.ims.media.LocalEndPoint;
import android.hardware.radio.ims.media.RtpConfig;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.telephony.imsmedia.AudioConfig;

import com.android.telephony.imsmedia.Utils.OpenSessionParams;
import com.android.telephony.imsmedia.util.Log;

/**
 * This connects to IImsMedia HAL and invokes all the HAL APIs
 */
public class AudioOffloadService {

    private static final String LOG_TAG = "AudioOffloadService";

    private IImsMedia mImsMedia;
    private ImsMediaController.OpenSessionCallback mMediaControllerCallback;
    private static AudioOffloadService sInstance;
    private ImsMediaListener listener;

    private AudioOffloadService() {
        listener = new ImsMediaListener();
        initMediaHal();
    }

    public static AudioOffloadService getInstance() {
        if (sInstance == null) {
            sInstance = new AudioOffloadService();
        }

        return sInstance;
    }

    // Initializes the HAL
    private synchronized void initMediaHal() {
        Log.d(LOG_TAG, "initMediaHal");

        try {
            mImsMedia = IImsMedia.Stub.asInterface(ServiceManager.waitForDeclaredService(
                    IImsMedia.DESCRIPTOR + "/default"));
            mImsMedia.setListener(listener);
        } catch (Exception e) {
            Log.e(LOG_TAG, "initMediaHal: Exception: " + e);
            return;
        }
    }

    public IImsMedia getIImsMedia() {

        if (mImsMedia != null) {
            return mImsMedia;
        }

        // Reconnect to ImsMedia HAL
        initMediaHal();

        return mImsMedia;
    }

    public void openSession(int sessionId, OpenSessionParams sessionParams) {
        final LocalEndPoint lep = new LocalEndPoint();
        final RtpConfig rtpConfig = Utils.convertToRtpConfig(
                (AudioConfig)sessionParams.getRtpConfig());

        /**
         * Store the reference to the media MediaControllerCallback so that it can
         * be used for notifying the onOpenSuccess() or onOpenFailure() callbacks.
         */
        mMediaControllerCallback = sessionParams.getCallback();

        /** Create LocalEndPoint from {@link OpenSessionParams} */
        lep.rtpFd = sessionParams.getRtpFd();
        lep.rtcpFd = sessionParams.getRtcpFd();
        lep.modemId = 1; // TODO : Use the logical modem ID

        try {
            getIImsMedia().openSession(sessionId, lep, rtpConfig);
        } catch (RemoteException e) {
            Log.e(LOG_TAG, "openSession: " + e);
        }
    }

    public void closeSession(int sessionId) {
        try {
            getIImsMedia().closeSession(sessionId);
        } catch (RemoteException e) {
            Log.e(LOG_TAG, "closeSession: " + e);
        }
    }

    private class ImsMediaListener extends IImsMediaListener.Stub {

        @Override
        public String getInterfaceHash() {
            return IImsMediaListener.HASH;
        }

        @Override
        public int getInterfaceVersion() {
            return IImsMediaListener.VERSION;
        }

        @Override
        public void onOpenSessionSuccess(int sessionId, IImsMediaSession session) {
            mMediaControllerCallback.onOpenSessionSuccess(sessionId, session);
        }

        @Override
        public void onOpenSessionFailure(int sessionId, int error) {
            mMediaControllerCallback.onOpenSessionFailure(sessionId, error);
        }

        @Override
        public void onSessionClosed(int sessionId) {
            mMediaControllerCallback.onSessionClosed(sessionId);
        }
    }
}
