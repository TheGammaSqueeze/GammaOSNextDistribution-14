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

import android.os.Parcel;
import android.telephony.ims.RtpHeaderExtension;
import android.telephony.imsmedia.AudioConfig;
import android.telephony.imsmedia.MediaQualityThreshold;

import com.android.telephony.imsmedia.util.Log;

import java.util.List;

/**
 * Audio session implementation for internal AP based RTP stack. This handles all API calls from
 * applications and passes it to native library.
 */
public class AudioLocalSession {
    private static final String TAG = "AudioLocalSession";
    private int mSessionId;
    private long mNativeObject = 0;

    /**
     * Instantiates a new audio session based on AP RTP stack
     *
     * @param sessionId : session identifier
     * @param nativeObject : jni object modifier for calling jni methods
     */
    AudioLocalSession(final int sessionId, final long nativeObject) {
        mSessionId = sessionId;
        mNativeObject = nativeObject;
    }

    /** Returns the unique session identifier */
    public int getSessionId() {
        Log.dc(TAG, "getSessionId");
        return mSessionId;
    }

    /**
     * Send request message with the corresponding arguments to libimsmediajni library to operate
     *
     * @param sessionId : session identifier
     * @param parcel : parcel argument to send to jni
     */
    public void sendRequest(final int sessionId, final Parcel parcel) {
        if (mNativeObject != 0) {
            if (parcel == null) return;
            byte[] data = parcel.marshall();
            JNIImsMediaService.sendMessage(mNativeObject, sessionId, data);
            parcel.recycle();
        }
    }

    /**
     * Modifies the configuration of the RTP session after the session is opened. It can be used
     * modify the direction, access network, codec parameters RTCP configuration, remote address and
     * remote port number. The service will apply if anything changed in this invocation compared to
     * previous and respond the updated the config in ImsMediaSession#onModifySessionResponse() API
     *
     * @param config provides remote end point info and codec details
     */
    public void modifySession(final AudioConfig config) {
        Parcel parcel = Parcel.obtain();
        parcel.writeInt(AudioSession.CMD_MODIFY_SESSION);
        if (config != null) {
            config.writeToParcel(parcel, 0);
        }
        sendRequest(mSessionId, parcel);
    }

    /**
     * Adds a new remote configuration to a RTP session during early media scenarios where the IMS
     * network could add more than one remote endpoint.
     *
     * @param config provides remote end point info and codec details
     */
    public void addConfig(final AudioConfig config) {
        Parcel parcel = Parcel.obtain();
        parcel.writeInt(AudioSession.CMD_ADD_CONFIG);
        if (config != null) {
            config.writeToParcel(parcel, 0);
        }
        sendRequest(mSessionId, parcel);
    }

    /**
     * Deletes a remote configuration from a RTP session during early media scenarios. A session
     * shall have at least one config so this API shall not delete the last config.
     *
     * @param config remote config to be deleted
     */
    public void deleteConfig(final AudioConfig config) {
        Parcel parcel = Parcel.obtain();
        parcel.writeInt(AudioSession.CMD_DELETE_CONFIG);
        if (config != null) {
            config.writeToParcel(parcel, 0);
        }
        sendRequest(mSessionId, parcel);
    }

    /**
     * Confirms a remote configuration for a Rtp session for early media scenarios when there are
     * more than one remote configs. All other early remote configs (potentially including the
     * config created as part of openSession) are auto deleted when one config is confirmed.
     * Confirming a remote configuration is necessary only if additional configurations were
     * created. New remote configurations cannot be added after a remote configuration is confirmed.
     *
     * @param config remote config to be confirmed
     */
    public void confirmConfig(final AudioConfig config) {
        Parcel parcel = Parcel.obtain();
        parcel.writeInt(AudioSession.CMD_CONFIRM_CONFIG);
        if (config != null) {
            config.writeToParcel(parcel, 0);
        }
        sendRequest(mSessionId, parcel);
    }

    /**
     * Send DTMF digit until the duration expires.
     *
     * @param dtmfDigit single char having one of 12 values: 0-9, *, #
     * @param duration of the key press in milliseconds.
     */
    public void sendDtmf(final char dtmfDigit, final int duration) {
        Parcel parcel = Parcel.obtain();
        parcel.writeInt(AudioSession.CMD_SEND_DTMF);
        parcel.writeByte((byte)dtmfDigit);
        parcel.writeInt(duration);
        sendRequest(mSessionId, parcel);
    }

    /**
     * Send RTP header extension to the other party in the next RTP packet.
     *
     * @param extensions List of RTP header extensions to be transmitted
     */
    public void sendHeaderExtension(final List<RtpHeaderExtension> extensions) {
        Parcel parcel = Parcel.obtain();
        parcel.writeInt(AudioSession.CMD_SEND_RTP_HDR_EXTN);
        parcel.writeInt(extensions.size());
        for (RtpHeaderExtension item : extensions) {
            item.writeToParcel(parcel, 0);
        }
        sendRequest(mSessionId, parcel);
    }

    /**
     * Sets the media quality threshold parameters of the session to get media quality
     * notifications.
     *
     * @param threshold media quality thresholds for various quality parameters
     */
    public void setMediaQualityThreshold(final MediaQualityThreshold threshold) {
        Parcel parcel = Parcel.obtain();
        parcel.writeInt(AudioSession.CMD_SET_MEDIA_QUALITY_THRESHOLD);
        if (threshold != null) {
            threshold.writeToParcel(parcel, 0);
        }
        sendRequest(mSessionId, parcel);
    }

    /**
     * Queries the current rtp reception statistics parameters for checking the current status of
     * the rtp stream. It will trigger the notifyRtpReceptionStats() with the RtpReceptionStats.
     *
     * @param intervalMs The interval of the time in milliseconds of the rtp reception notification
     */
    public void requestRtpReceptionStats(final int intervalMs) {
        Parcel parcel = Parcel.obtain();
        parcel.writeInt(AudioSession.CMD_REQUEST_RECEPTION_STATS);
        parcel.writeInt(intervalMs);
        sendRequest(mSessionId, parcel);
    }

    /**
     * Adjust the delay in the jitter buffer to synchronize the audio with the time of video frames
     *
     * @param delayMs The delay to adjust the additional delay to the jitter buffer. The value is
     * always positive.
     */
    public void adjustDelay(final int delayMs) {
        Parcel parcel = Parcel.obtain();
        parcel.writeInt(AudioSession.CMD_ADJUST_DELAY);
        parcel.writeInt(delayMs);
        sendRequest(mSessionId, parcel);
    }
}
