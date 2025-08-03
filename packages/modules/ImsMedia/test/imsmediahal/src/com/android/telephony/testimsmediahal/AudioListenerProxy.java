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

import android.hardware.radio.ims.media.IImsMediaListener;
import android.hardware.radio.ims.media.IImsMediaSession;
import android.hardware.radio.ims.media.IImsMediaSessionListener;
import android.hardware.radio.ims.media.RtpConfig;
import android.hardware.radio.ims.media.RtpHeaderExtension;
import android.os.Parcel;
import android.os.RemoteException;
import android.telephony.imsmedia.AudioConfig;
import android.telephony.imsmedia.MediaQualityStatus;
import android.util.Log;

import com.android.telephony.imsmedia.AudioSession;
import com.android.telephony.imsmedia.JNIImsMediaListener;
import com.android.telephony.imsmedia.Utils;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of proxy Listener class used to ge response back
 * from {@link libimsmedia} and sent back to {@link ImsMediaController}.
 * Set appropriate listener for sending call back to {@link ImsMediaController}.
 */

class AudioListenerProxy implements JNIImsMediaListener {

    private static String TAG = "ImsMediaAudioListener";

    private IImsMediaSessionListener mMediaSessionListener;
    private IImsMediaListener mListener;
    private static AudioListenerProxy mInstance;
    private IImsMediaSession mMediaSession;
    private int mSessionId;

    public void setMediaSessionListener(IImsMediaSessionListener mediaSessionListener) {
        mMediaSessionListener = mediaSessionListener;
    }

    public void setImsMediaListener(IImsMediaListener listener) {
        mListener = listener;
    }

    public void setSessionId(int sessionId)
    {
        mSessionId = sessionId;
    }

    public static AudioListenerProxy getInstance() {
        if(mInstance == null)
        {
            mInstance = new AudioListenerProxy();
        }
        return mInstance;
    }


    @Override
    public void onMessage(Parcel parcel) {
        final int event = parcel.readInt();
        Log.d(TAG, "onMessage=" + event);
        switch (event) {
            case AudioSession.EVENT_OPEN_SESSION_SUCCESS:
                final int sessionId = parcel.readInt();

                mMediaSession = new IImsMediaSessionImpl(mSessionId);

                try {
                    mListener.onOpenSessionSuccess(sessionId,
                    mMediaSession);
                } catch(RemoteException e) {
                    Log.e(TAG, "Failed to notify openSuccess: " + e);
                }
                break;
            case AudioSession.EVENT_OPEN_SESSION_FAILURE:
                final int sessionId1 = parcel.readInt();
                final int result = parcel.readInt();
                try {
                    mListener.onOpenSessionFailure(sessionId1,
                    result);
                } catch(RemoteException e) {
                    Log.e(TAG, "Failed to notify openFailure: " + e);
                }
                break;
            case AudioSession.EVENT_SESSION_CLOSED:
                final int sessionId2 = parcel.readInt();
                try {
                    mListener.onSessionClosed(sessionId2);
                } catch (RemoteException e) {
                    Log.e(TAG, "Failed to notify SessionClosed: " + e);
                }
                break;
            case AudioSession.EVENT_MODIFY_SESSION_RESPONSE:
                final int result1 = parcel.readInt();
                final AudioConfig config = AudioConfig.CREATOR.createFromParcel(parcel);
                final RtpConfig rtpConfig = Utils.convertToRtpConfig(config);

                try {
                    mMediaSessionListener.onModifySessionResponse(rtpConfig, result1);
                } catch(RemoteException e) {
                    Log.e(TAG, "Failed to notify modify session: " + e);
                }
                break;
            case AudioSession.EVENT_FIRST_MEDIA_PACKET_IND:
                final AudioConfig mediaIndCfg = AudioConfig.CREATOR.createFromParcel(parcel);
                final RtpConfig mediaIndRtpCfg = Utils.convertToRtpConfig(mediaIndCfg);

                try {
                    mMediaSessionListener.onFirstMediaPacketReceived(mediaIndRtpCfg);
                } catch(RemoteException e) {
                    Log.e(TAG, "Failed to notify first media packet received: " + e);
                }
                break;
            case AudioSession.EVENT_RTP_HEADER_EXTENSION_IND:
                final List<RtpHeaderExtension> extensions = new ArrayList<RtpHeaderExtension>();
                final int listSize = parcel.readInt();
                for (int i = 0; i < listSize; i++) {
                    extensions.add(RtpHeaderExtension.CREATOR.createFromParcel(parcel));
                }

                try {
                    mMediaSessionListener.onHeaderExtensionReceived(extensions);
                } catch(RemoteException e) {
                    Log.e(TAG, "Failed to notify rtp header extension: " + e);
                }
                break;
            case AudioSession.EVENT_MEDIA_QUALITY_STATUS_IND:
                final MediaQualityStatus status =
                        MediaQualityStatus.CREATOR.createFromParcel(parcel);
                try {
                    mMediaSessionListener.notifyMediaQualityStatus(
                            Utils.convertToHalMediaQualityStatus(status));
                } catch(RemoteException e) {
                    Log.e(TAG, "Failed to notify media quality status: " + e);
                }
                break;
            case AudioSession.EVENT_TRIGGER_ANBR_QUERY_IND:
                final AudioConfig anbrNotiCfg = AudioConfig.CREATOR.createFromParcel(parcel);
                final RtpConfig anbrNotiRtpCfg = Utils.convertToRtpConfig(anbrNotiCfg);

                try {
                    mMediaSessionListener.triggerAnbrQuery(anbrNotiRtpCfg);
                } catch (RemoteException e) {
                    Log.e(TAG, "Failed to trigger ANBR query: " + e);
                }
                break;
            case AudioSession.EVENT_DTMF_RECEIVED_IND:
                final char dtmfDigit = (char) parcel.readByte();
                final int durationMs = parcel.readInt();

                try {
                    mMediaSessionListener.onDtmfReceived(dtmfDigit, durationMs);
                } catch (RemoteException e) {
                    Log.e(TAG, "Failed to DTMF received: " + e);
                }
                break;
            default:
                Log.d(TAG, "unidentified event.");
                break;
            }
        }
    }
