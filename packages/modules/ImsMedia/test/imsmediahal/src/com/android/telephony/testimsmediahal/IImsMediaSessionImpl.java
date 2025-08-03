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

import android.hardware.radio.ims.media.IImsMediaSession;
import android.hardware.radio.ims.media.IImsMediaSessionListener;
import android.hardware.radio.ims.media.MediaQualityThreshold;
import android.hardware.radio.ims.media.RtpConfig;
import android.hardware.radio.ims.media.RtpHeaderExtension;
import android.os.Parcel;
import android.telephony.imsmedia.AudioConfig;
import android.util.Log;

import com.android.telephony.imsmedia.AudioSession;
import com.android.telephony.imsmedia.Utils;

import java.util.List;

/**
 * MediaSession implementation class for adding modifying media session.
 * Class is creating JNI connection with {@link libimsmedia} via
 * {@link libimsmediahaljni}.
 */

public class IImsMediaSessionImpl extends IImsMediaSession.Stub {

    private static final String TAG = "IImsMediaSessionImpl";

    private int mSessionId;
    private AudioListenerProxy mNativeListener;
    private static JNIConnector connector;

    /**
     * @constructor of the IIMsMediaSession class.
     *
     * @param sessionId used to identify session.
     */

    public IImsMediaSessionImpl(int sessionId) {
        Log.d(TAG, "Instantiated");
        mSessionId = sessionId;
        mNativeListener = AudioListenerProxy.getInstance();
        connector = JNIConnector.getInstance();
    }

    @Override
    public String getInterfaceHash() {
        return IImsMediaSession.HASH;
    }

    @Override
    public int getInterfaceVersion() {
        return IImsMediaSession.VERSION;
    }

    @Override
    public void setListener(IImsMediaSessionListener mediaSessionListener) {

        mNativeListener.setMediaSessionListener(mediaSessionListener);
    }

    @Override
    public void modifySession(RtpConfig config) {
        Log.d(TAG, "modifyConfig: " + config);
        Parcel parcel = Parcel.obtain();
        parcel.writeInt(AudioSession.CMD_MODIFY_SESSION);

        if (config != null) {
            AudioConfig audioConfig = Utils.convertToAudioConfig(config);
            audioConfig.writeToParcel(parcel, 0);
        }
        connector.sendRequest(mSessionId, parcel);
    }

    @Override
    public void sendDtmf(char dtmfDigit, int duration) {
        Log.d(TAG, "sendDtmf: digit= " + dtmfDigit + ", duration=" + duration);
        Parcel parcel = Parcel.obtain();
        parcel.writeInt(AudioSession.CMD_SEND_DTMF);
        parcel.writeByte((byte) dtmfDigit);
        parcel.writeInt(duration);
        connector.sendRequest(mSessionId, parcel);
    }

    @Override
    public void startDtmf(char dtmfDigit) {
        Log.d(TAG, "startDtmf: digit= " + dtmfDigit);
        Parcel parcel = Parcel.obtain();
        parcel.writeInt(AudioSession.CMD_START_DTMF);
        parcel.writeByte((byte) dtmfDigit);
        connector.sendRequest(mSessionId, parcel);
    }

    @Override
    public void stopDtmf() {
        Log.d(TAG, "stopDtmf");
        Parcel parcel = Parcel.obtain();
        parcel.writeInt(AudioSession.CMD_STOP_DTMF);
        connector.sendRequest(mSessionId, parcel);
    }

    @Override
    public void sendHeaderExtension(List<RtpHeaderExtension> data) {
        Log.d(TAG, "sendHeaderExtension: " + data);
        Parcel parcel = Parcel.obtain();
        parcel.writeInt(AudioSession.CMD_SEND_RTP_HDR_EXTN);
        parcel.writeInt(data.size());
        for (RtpHeaderExtension item : data) {
            item.writeToParcel(parcel, 0);
        }
        connector.sendRequest(mSessionId, parcel);
    }

    @Override
    public void setMediaQualityThreshold(MediaQualityThreshold threshold) {
        Log.d(TAG, "setMediaQualityThreshold: " + threshold);
        Parcel parcel = Parcel.obtain();
        parcel.writeInt(AudioSession.CMD_SET_MEDIA_QUALITY_THRESHOLD);
        if (threshold != null) {
            threshold.writeToParcel(parcel, 0);
        }
        connector.sendRequest(mSessionId, parcel);
    }
}
