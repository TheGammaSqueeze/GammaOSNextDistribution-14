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

package com.android.telephony.testimsmediahal;

import android.hardware.radio.ims.media.IImsMedia;
import android.hardware.radio.ims.media.IImsMediaListener;
import android.hardware.radio.ims.media.LocalEndPoint;
import android.hardware.radio.ims.media.RtpConfig;
import android.os.Parcel;
import android.telephony.imsmedia.AudioConfig;
import android.util.Log;

import com.android.telephony.imsmedia.AudioSession;
import com.android.telephony.imsmedia.Utils;

/**
 * Instantiates the {@link AudioListenerProxy} and {@link JNIConnector}
 * {@link JNIConnector} used to connect with {@link libimsmediahaljni}
 * to send session modification request to {@link libimsmedia}
 * {@link AudioListenerProxy} used to get respose from {@link libimsmedia}
 */

public class IImsMediaImpl extends IImsMedia.Stub {

    private static final String TAG = "IImsMediaImpl";
    private AudioListenerProxy mMediaResponse;
    private static JNIConnector connector;

    public IImsMediaImpl() {
        Log.d(TAG, "Instantiated");
        mMediaResponse = AudioListenerProxy.getInstance();
        connector = JNIConnector.getInstance();
    }

    @Override
    public String getInterfaceHash() {
        return IImsMedia.HASH;
    }

    @Override
    public int getInterfaceVersion() {
        return IImsMedia.VERSION;
    }

    @Override
    public void setListener(IImsMediaListener mediaListener) {
        Log.d(TAG, "setListener");
        mMediaResponse.setImsMediaListener(mediaListener);
    }

    /**
     * Opening Media session.
     *
     * @param sessionId     unique identifier of session.
     * @param localEndPoint used to get rtp/rtcp socket file descriptors.
     * @param config        HAl RtpConfig used configure session related params.
     */

    @Override
    public void openSession(int sessionId, LocalEndPoint localEndPoint, RtpConfig config) {
        Log.d(TAG, "openSession");
        connector.connectJni(sessionId);
        mMediaResponse.setSessionId(sessionId);
        Parcel parcel = Parcel.obtain();

        parcel.writeInt(AudioSession.CMD_OPEN_SESSION);

        final int socketFdRtp = (localEndPoint.rtpFd != null) ? localEndPoint.rtpFd.getFd() : -1;
        final int socketFdRtcp = (localEndPoint.rtcpFd != null) ? localEndPoint.rtcpFd.getFd() : -1;

        parcel.writeInt(socketFdRtp);
        parcel.writeInt(socketFdRtcp);

        if (config != null) {
            AudioConfig audioConfig = (AudioConfig) Utils.convertToAudioConfig(config);
            audioConfig.writeToParcel(parcel, 0);
        }
        connector.sendRequest(sessionId, parcel);
    }

    /**
     * Closing Media session on libimsmedia side.
     *
     * @param sessionId unique sessionId used to close respective session.
     */

    @Override
    public void closeSession(int sessionId) {
        Log.d(TAG, "closeSession");
        Parcel parcel = Parcel.obtain();
        parcel.writeInt(AudioSession.CMD_CLOSE_SESSION);
        connector.sendRequest(sessionId, parcel);
    }
}
