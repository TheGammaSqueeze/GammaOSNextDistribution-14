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

package android.telephony.imsmedia;

import android.annotation.IntDef;
import android.hardware.radio.ims.media.RtpError;
import android.os.IBinder;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Implemented by classes that encapsulates RTP session eg. Audio, Video or RTT
 *
 * Use the instanceof keyword to determine the underlying type.
 *
 * @hide
 */
public interface ImsMediaSession {
    int SESSION_TYPE_AUDIO = 0;
    int SESSION_TYPE_VIDEO = 1;
    int SESSION_TYPE_RTT = 2;

    /** @hide */
    @IntDef(value = {
            SESSION_TYPE_AUDIO,
            SESSION_TYPE_VIDEO,
            SESSION_TYPE_RTT,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface SessionType {
    }

    @Retention(RetentionPolicy.SOURCE)
    public @interface SessionState {
    }

    /** Real Time Protocol, see RFC 3550 */
    int PACKET_TYPE_RTP = 0;
    /** Real Time Control Protocol, see RFC 3550 */
    int PACKET_TYPE_RTCP = 1;

    /** @hide */
    @IntDef(value = {
            PACKET_TYPE_RTP,
            PACKET_TYPE_RTCP,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface PacketType {
    }

    /** Result of a session operation is successful */
    int RESULT_SUCCESS = RtpError.NONE;
    /** Failed because of invalid parameters passed in the request */
    int RESULT_INVALID_PARAM = RtpError.INVALID_PARAM;
    /** The RTP stack is not ready to handle the request */
    int RESULT_NOT_READY = RtpError.NOT_READY;
    /** Unable to handle the request due to memory allocation failure */
    int RESULT_NO_MEMORY = RtpError.NO_MEMORY;
    /**
     * Unable to handle the request due to no sufficient resources such as audio, codec
     */
    int RESULT_NO_RESOURCES = RtpError.NO_RESOURCES;
    /** The requested port number is not available */
    int RESULT_PORT_UNAVAILABLE = RtpError.PORT_UNAVAILABLE;
    /** The request is not supported by the vendor implementation */
    int RESULT_NOT_SUPPORTED = RtpError.NOT_SUPPORTED;

    /** @hide */
    @IntDef(value = {
            RESULT_SUCCESS,
            RESULT_INVALID_PARAM,
            RESULT_NOT_READY,
            RESULT_NO_MEMORY,
            RESULT_NO_RESOURCES,
            RESULT_PORT_UNAVAILABLE,
            RESULT_NOT_SUPPORTED,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface SessionOperationResult {
    }

    /** @hide */
    public IBinder getBinder();

    /** Returns the unique session identifier */
    public int getSessionId();

    /**
     * Modifies the configuration of the RTP session after the session is opened. It can be used to
     * modify the direction, access network, codec parameters, {@link RtcpConfig}, remote address
     * and remote port number. The service will apply if anything changed in this invocation
     * compared to previous and respond the updated {@link RtpConfig} in
     * {@link ImsMediaSession#onModifySessionResponse()} API.
     *
     * @param config provides remote end point info and codec details
     */
    void modifySession(final RtpConfig config);

    /**
     * Sets the media quality threshold parameters of the session to get
     * media quality notifications.
     *
     * @param threshold media quality thresholds for various quality
     *                  parameters
     */
    void setMediaQualityThreshold(final MediaQualityThreshold threshold);
}
