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

#ifndef RTPCONFIG_H
#define RTPCONFIG_H

#include <binder/Parcel.h>
#include <binder/Parcelable.h>
#include <binder/Status.h>
#include <RtcpConfig.h>
#include <RtpContextParams.h>
#include <AnbrMode.h>
#include <stdint.h>

namespace android
{

namespace telephony
{

namespace imsmedia
{

/** Native representation of android.telephony.imsmedia.RtpConfig */

/**
 * The class to encapsulate RTP (Real Time Protocol) configurations
 */
class RtpConfig : public Parcelable
{
public:
    enum MediaDirection
    {
        /** Device neither transmits nor receives any RTP */
        MEDIA_DIRECTION_NO_FLOW,
        /**
         * Device transmits outgoing RTP but but doesn't receive incoming RTP.
         * Eg. Other party muted the call
         */
        MEDIA_DIRECTION_SEND_ONLY,
        /**
         * Device receives the incoming RTP but doesn't transmit any outgoing RTP.
         * Eg. User muted the call
         */
        MEDIA_DIRECTION_RECEIVE_ONLY,
        /** Device transmits and receives RTP in both the directions */
        MEDIA_DIRECTION_SEND_RECEIVE,
        /** No RTP flow however RTCP continues to flow. Eg. HOLD */
        MEDIA_DIRECTION_INACTIVE,
    };

    virtual ~RtpConfig();
    RtpConfig& operator=(const RtpConfig& config);
    bool operator==(const RtpConfig& c2) const;
    bool operator!=(const RtpConfig& c2) const;
    virtual status_t writeToParcel(Parcel* parcel) const;
    virtual status_t readFromParcel(const Parcel* in);
    void setMediaDirection(const int32_t direction);
    int32_t getMediaDirection();
    void setAccessNetwork(const int32_t network);
    int32_t getAccessNetwork();
    void setRemoteAddress(const String8& address);
    String8 getRemoteAddress();
    void setRemotePort(const int32_t port);
    int32_t getRemotePort();
    void setRtcpConfig(const RtcpConfig& config);
    RtcpConfig getRtcpConfig();
    void setDscp(const int8_t dscp);
    int8_t getDscp();
    void setRxPayloadTypeNumber(const int8_t num);
    int8_t getRxPayloadTypeNumber();
    void setTxPayloadTypeNumber(const int8_t num);
    int8_t getTxPayloadTypeNumber();
    void setSamplingRateKHz(const int8_t sample);
    int8_t getSamplingRateKHz();
    RtpContextParams getRtpContextParams();
    void setRtpContextParams(RtpContextParams& rtpContextParams);
    void setAnbrMode(const AnbrMode& anbrMode);
    AnbrMode getAnbrMode();

protected:
    RtpConfig(int32_t type);
    RtpConfig(RtpConfig* config);
    RtpConfig(const RtpConfig& config);

    /* definition of uninitialized port number*/
    const static int32_t UNINITIALIZED_PORT = -1;
    /* media types */
    const static int32_t TYPE_AUDIO = 0;
    const static int32_t TYPE_VIDEO = 1;
    const static int32_t TYPE_TEXT = 2;

    /**
     * @brief media type.
     */
    int32_t type;

    /**
     * @brief RTP media flow direction
     */
    int32_t direction;
    /**
     * @brief source Radio Access Network to RTP stack
     */
    int32_t accessNetwork;
    /**
     * @brief ip address of other party
     */
    String8 remoteAddress;
    /**
     * @brief port number of other party
     */
    int32_t remotePort;
    /**
     * @brief Rtcp configuration
     */
    RtcpConfig rtcpConfig;
    /**
     * @brief Differentiated Services Field Code Point value, see RFC 2474
     */
    int8_t dscp;
    /**
     * @brief Static or dynamic payload type number negotiated through the SDP for
     * the incoming RTP packets. This value shall be matched with the PT value
     * of the incoming RTP header. Values 0 to 127, see RFC 3551 section 6
     */
    int8_t rxPayloadTypeNumber;
    /**
     * @brief Static or dynamic payload type number negotiated through the SDP for
     * the outgoing RTP packets. This value shall be set to the PT value
     * of the outgoing RTP header. Values 0 to 127, see RFC 3551 section 6
     */
    int8_t txPayloadTypeNumber;
    /**
     * @brief Sampling rate in kHz
     */
    int8_t samplingRateKHz;

    /**
     * @brief Holds the rtp parameters such as ssrc, timestamp and sequence number. Used to transfer
     * RTP context information between RTP stacks (ex: during handover). Helps to maintain contunity
     * in RTP steams even after switching between RTP stacks.
     */
    RtpContextParams rtpContextParams;

    /**
     * @brief anbrMode The codec mode of the current activated codec in the EvsParams
     * and the AmrParams
     */
    AnbrMode anbrMode;
};

}  // namespace imsmedia

}  // namespace telephony

}  // namespace android

#endif
