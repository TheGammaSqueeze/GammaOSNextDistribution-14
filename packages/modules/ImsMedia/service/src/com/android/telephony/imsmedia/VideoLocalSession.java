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
import android.telephony.imsmedia.MediaQualityThreshold;
import android.telephony.imsmedia.VideoConfig;
import android.view.Surface;

import com.android.telephony.imsmedia.util.Log;

import java.util.List;

/** Video session implementation for internal AP based RTP stack. This handles
 * all API calls from applications and passes it to native library.
 */
public class VideoLocalSession {
    private static final String TAG = "VideoLocalSession";
    private int mSessionId;
    private long mNativeObject = 0;

    /* Instantiates a new audio session based on AP RTP stack
    *
    * @param sessionId : session identifier
    * @param nativeObject : jni object modifier for calling jni methods
    */
    VideoLocalSession(final int sessionId, final long nativeObject) {
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
     * Modifies the configuration of the RTP session after the session is opened.
     * It can be used modify the direction, access network, codec parameters
     * RTCP configuration, remote address and remote port number. The service will
     * apply if anything changed in this invocation compared to previous and respond
     * the updated the config in ImsMediaSession#onModifySessionResponse() API
     *
     * @param config provides remote end point info and codec details
     */
    public void modifySession(final VideoConfig config) {
        Parcel parcel = Parcel.obtain();
        parcel.writeInt(VideoSession.CMD_MODIFY_SESSION);
        if (config != null) {
            config.writeToParcel(parcel, 0);
        }
        sendRequest(mSessionId, parcel);
    }

    /**
     * Send preview Surface to session to display video tx preview in video streaming.
     * The setPreviewSurafce should be invoked after openSession or modifySession.
     *
     * @param surface the surface to set
     */
    public void setPreviewSurface(final Surface surface) {
        if (mNativeObject != 0) {
            JNIImsMediaService.setPreviewSurface(mNativeObject, mSessionId, surface);
        }
    }

    /**
     * Send display Surface to session to display video rx decoded frames in video streaming.
     * The setDisplaySurface should be invoked after openSession or modifySession.
     *
     * @param surface the surface to set
     */
    public void setDisplaySurface(final Surface surface) {
        if (mNativeObject != 0) {
            JNIImsMediaService.setDisplaySurface(mNativeObject, mSessionId, surface);
        }
    }

    /**
     * Sets the media quality threshold parameters of the session to get
     * media quality notifications.
     *
     * @param threshold media quality thresholds for various quality
     *        parameters
     */
    public void setMediaQualityThreshold(final MediaQualityThreshold threshold) {
        Parcel parcel = Parcel.obtain();
        parcel.writeInt(VideoSession.CMD_SET_MEDIA_QUALITY_THRESHOLD);
        if (threshold != null) {
            threshold.writeToParcel(parcel, 0);
        }
        sendRequest(mSessionId, parcel);
    }

    /**
     * Send RTP header extension to the other party in the next RTP packet.
     *
     * @param extensions List of RTP header extensions to be transmitted
     */
    public void sendHeaderExtension(final List<RtpHeaderExtension> extensions) {
        Log.dc(TAG, "sendHeaderExtension");
        // TODO: add implementation
    }

    /**
     * Request to report current video data usage to get the amount of data usage in current
     * video streaming session.
     */
    public void requestVideoDataUsage() {
        Log.d(TAG, "requestVideoDataUsage");
        Parcel parcel = Parcel.obtain();
        parcel.writeInt(VideoSession.CMD_REQUEST_VIDEO_DATA_USAGE);
        sendRequest(mSessionId, parcel);
    }
}
