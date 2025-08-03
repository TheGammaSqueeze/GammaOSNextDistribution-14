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

#ifndef RTCPCONFIG_H
#define RTCPCONFIG_H

#include <binder/Parcel.h>
#include <binder/Parcelable.h>
#include <binder/Status.h>
#include <stdint.h>

namespace android
{

namespace telephony
{

namespace imsmedia
{

/** Native representation of android.telephony.imsmedia.RtcpConfig */

/**
 * The class represents RTCP (Real Time Control Protocol) configurations.
 */
class RtcpConfig : public Parcelable
{
public:
    enum RtcpXrBlockType
    {
        /**
         * RTCP XR (extended report) types are not specified,
         * See RFC 3611 section 4
         */
        FLAG_RTCPXR_NONE = 0,
        /**
         * RTCP XR type Loss RLE Report Block as specified in
         * RFC 3611 section 4.1
         */
        FLAG_RTCPXR_LOSS_RLE_REPORT_BLOCK = 1 << 0,
        /**
         * RTCP XR type Duplicate RLE Report Block as specified in
         * RFC 3611 section 4.2
         */
        FLAG_RTCPXR_DUPLICATE_RLE_REPORT_BLOCK = 1 << 1,
        /**
         * RTCP XR type Packet Receipt Times Report Block as specified in
         * RFC 3611 section 4.3
         */
        FLAG_RTCPXR_PACKET_RECEIPT_TIMES_REPORT_BLOCK = 1 << 2,
        /**
         * RTCP XR type Receiver Reference Time Report Block as specified in
         * RFC 3611 section 4.4
         */
        FLAG_RTCPXR_RECEIVER_REFERENCE_TIME_REPORT_BLOCK = 1 << 3,
        /**
         * RTCP XR type DLRR Report Block as specified in
         * RFC 3611 section 4.5
         */
        FLAG_RTCPXR_DLRR_REPORT_BLOCK = 1 << 4,
        /**
         * RTCP XR type Statistics Summary Report Block as specified in
         * RFC 3611 section 4.6
         */
        FLAG_RTCPXR_STATISTICS_SUMMARY_REPORT_BLOCK = 1 << 5,
        /**
         * RTCP XR type VoIP Metrics Report Block as specified in
         * RFC 3611 section 4.7
         */
        FLAG_RTCPXR_VOIP_METRICS_REPORT_BLOCK = 1 << 6,
    };

    // Default RtcpConfig
    const int32_t kTransmitPort = 0;
    const int32_t kIntervalSec = 0;
    const int32_t kRtcpXrBlockTypes = FLAG_RTCPXR_NONE;

    RtcpConfig();
    RtcpConfig(const RtcpConfig& config);
    virtual ~RtcpConfig();
    RtcpConfig& operator=(const RtcpConfig& config);
    bool operator==(const RtcpConfig& config) const;
    bool operator!=(const RtcpConfig& config) const;
    virtual status_t writeToParcel(Parcel* parcel) const;
    virtual status_t readFromParcel(const Parcel* in);
    void setCanonicalName(const String8& name);
    String8 getCanonicalName();
    void setTransmitPort(const int32_t port);
    int32_t getTransmitPort();
    void setIntervalSec(const int32_t interval);
    int32_t getIntervalSec();
    void setRtcpXrBlockTypes(const int32_t type);
    int32_t getRtcpXrBlockTypes();
    void setDefaultRtcpConfig();

private:
    /** Canonical name that will be sent to all session participants */
    String8 canonicalName;

    /** UDP port number for sending outgoing RTCP packets */
    int32_t transmitPort;

    /**
     * RTCP transmit interval in seconds. The value 0 indicates that RTCP
     * reports shall not be sent to the other party.
     */
    int32_t intervalSec;

    /** Bitmask of RTCP-XR blocks to enable as in RtcpXrReportBlockType */
    int32_t rtcpXrBlockTypes;
};

}  // namespace imsmedia

}  // namespace telephony

}  // namespace android

#endif