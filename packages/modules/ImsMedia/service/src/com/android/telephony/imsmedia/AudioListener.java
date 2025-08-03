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
import android.os.Parcel;
import android.telephony.CallQuality;
import android.telephony.ims.RtpHeaderExtension;
import android.telephony.imsmedia.AudioConfig;
import android.telephony.imsmedia.MediaQualityStatus;
import android.telephony.imsmedia.RtpReceptionStats;

import com.android.telephony.imsmedia.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Audio listener to process JNI messages from local AP based RTP stack
 */
public class AudioListener implements JNIImsMediaListener {
    private static final String TAG = "AudioListener";
    final private Handler mHandler;
    private ImsMediaController.OpenSessionCallback mCallback;
    private long mNativeObject;

    AudioListener(final Handler handler) {
        Log.d(TAG, "AudioListener() -" + AudioListener.this);
        mHandler = handler;
    }

    /**
     * Sets callback to call ImsMediaController to handle responses when
     * openSession method called in @AudioService
     *
     * @param callback A Callback of @ImsMediaController#OpenSessionCallback
     */
    public void setMediaCallback(final ImsMediaController.OpenSessionCallback callback) {
        mCallback = callback;
    }

    /**
     * Sets native object to identify the instance of @BaseManager
     *
     * @param object the native instance of AudioManager
     */
    public void setNativeObject(final long object) {
        mNativeObject = object;
    }

    /**
     * Processes parcel messages from native code and posts messages to {@link AudioSession}
     *
     * @param parcel A parcel from native @AudioManager in libimsmedia library
     */
    @Override
    public void onMessage(final Parcel parcel) {
        final int event = parcel.readInt();
        Log.dc(TAG, "onMessage() -" + AudioListener.this + ", event=" + event);
        switch (event) {
            case AudioSession.EVENT_OPEN_SESSION_SUCCESS:
                final int sessionId = parcel.readInt();
                mCallback.onOpenSessionSuccess(sessionId,
                    new AudioLocalSession(sessionId, mNativeObject));
                break;
            case AudioSession.EVENT_OPEN_SESSION_FAILURE:
                mCallback.onOpenSessionFailure(parcel.readInt(),
                    parcel.readInt());
                break;
            case AudioSession.EVENT_MODIFY_SESSION_RESPONSE:
            case AudioSession.EVENT_ADD_CONFIG_RESPONSE:
            case AudioSession.EVENT_CONFIRM_CONFIG_RESPONSE:
            {
                final int result = parcel.readInt();
                final AudioConfig config = AudioConfig.CREATOR.createFromParcel(parcel);
                Utils.sendMessage(mHandler, event, result, Utils.UNUSED, config);
            }
                break;
            case AudioSession.EVENT_FIRST_MEDIA_PACKET_IND:
            {
                final AudioConfig config = AudioConfig.CREATOR.createFromParcel(parcel);
                Utils.sendMessage(mHandler, event, config);
            }
                break;
            case AudioSession.EVENT_RTP_HEADER_EXTENSION_IND:
            {
                final List<RtpHeaderExtension> extensions = new ArrayList<RtpHeaderExtension>();
                final int listSize = parcel.readInt();
                for (int i = 0; i < listSize; i++) {
                    extensions.add(RtpHeaderExtension.CREATOR.createFromParcel(parcel));
                }
                Utils.sendMessage(mHandler, event, extensions);
            }
                break;
            case AudioSession.EVENT_MEDIA_QUALITY_STATUS_IND:
                Utils.sendMessage(mHandler, event,
                        MediaQualityStatus.CREATOR.createFromParcel(parcel));
                break;
            case AudioSession.EVENT_TRIGGER_ANBR_QUERY_IND:
                final AudioConfig configAnbr = AudioConfig.CREATOR.createFromParcel(parcel);
                Utils.sendMessage(mHandler, event, configAnbr);
                break;
            case AudioSession.EVENT_DTMF_RECEIVED_IND:
                final char dtmfDigit = (char) parcel.readByte();
                final int durationMs = parcel.readInt();
                Utils.sendMessage(mHandler, event, dtmfDigit, durationMs);
                break;
            case AudioSession.EVENT_CALL_QUALITY_CHANGE_IND:
                Utils.sendMessage(mHandler, event, CallQuality.CREATOR.createFromParcel(parcel));
                break;
            case AudioSession.EVENT_SESSION_CLOSED:
                mCallback.onSessionClosed(parcel.readInt());
                break;
            case AudioSession.EVENT_NOTIFY_RECEPTION_STATS:
                Utils.sendMessage(mHandler, event,
                        RtpReceptionStats.CREATOR.createFromParcel(parcel));
                break;
            default:
                break;
        }
    }
}
