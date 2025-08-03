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

/** \addtogroup  RTP_Stack
 *  @{
 */

#ifndef __RTCP_REPORT_BLOCK_H__
#define __RTCP_REPORT_BLOCK_H__

#include <RtpGlobal.h>
#include <RtpBuffer.h>

/**
 * @class    rtcp_report_block
 * @brief    Header file to hold Report block information
 */
class RtcpReportBlock
{
private:
    // Synchronization source.
    RtpDt_UInt32 m_uiSsrc;

    /**
     * The fraction of RTP data packets from source SSRC_n lost since the
     * previous SR or RR packet was sent, expressed as a fixed point
     * number with the binary point at the left edge of the field.
     */
    RtpDt_UChar m_ucFracLost;

    /**
     * The total number of RTP data packets from source SSRC_n that have
     * been lost since the beginning of reception.
     * The number of packets received includes any that are late or duplicated, and hence may
     * be greater than the number expected, so the cumulative number of packets lost may
     * be negative
     */
    RtpDt_Int32 m_uiCumNumPktLost;

    /**
     * extended highest sequence number received
     */
    RtpDt_UInt32 m_uiExtHighSeqRcv;

    /**
     * An estimate of the statistical variance of the RTP data packet
     * interarrival time, measured in timestamp units and expressed as an
     * unsigned integer.
     */
    RtpDt_UInt32 m_uiJitter;

    /**
     * last SR timestamp
     */
    RtpDt_UInt32 m_uiLastSR;

    /**
     * delay since last SR
     */
    RtpDt_UInt32 m_uiDelayLastSR;

public:
    RtcpReportBlock();
    ~RtcpReportBlock();

    /**
     * set method for m_uiSsrc
     */
    RtpDt_Void setSsrc(IN RtpDt_UInt32 uiSsrc);

    /**
     * get method for m_uiSsrc
     */
    RtpDt_UInt32 getSsrc();

    /**
     * set method for m_ucFracLost
     */
    RtpDt_Void setFracLost(IN RtpDt_UChar ucFracLost);

    /**
     * get method for m_ucFracLost
     */
    RtpDt_UChar getFracLost();

    /**
     * set method for m_uiCumNumPktLost
     */
    RtpDt_Void setCumNumPktLost(IN RtpDt_Int32 uiCumNumPktLost);

    /**
     * get method for m_uiCumNumPktLost
     */
    RtpDt_Int32 getCumNumPktLost();

    /**
     * set method for m_uiExtHighSeqRcv
     */
    RtpDt_Void setExtHighSeqRcv(IN RtpDt_UInt32 uiExtHighSeqRcv);

    /**
     * get method for m_uiExtHighSeqRcv
     */
    RtpDt_UInt32 getExtHighSeqRcv();

    /**
     * set method for m_uiJitter
     */
    RtpDt_Void setJitter(IN RtpDt_UInt32 uiJitter);

    /**
     * get method for m_uiJitter
     */
    RtpDt_UInt32 getJitter();

    /**
     * set method for m_uiLastSR
     */
    RtpDt_Void setLastSR(IN RtpDt_UInt32 uiLastSR);

    /**
     * get method for m_uiLastSR
     */
    RtpDt_UInt32 getLastSR();

    /**
     * set method for m_uiDelayLastSR
     */
    RtpDt_Void setDelayLastSR(IN RtpDt_UInt32 uiDelayLastSR);

    /**
     * get method for m_uiDelayLastSR
     */
    RtpDt_UInt32 getDelayLastSR();

    /**
     * Performs the decoding of RTCP Report Block
     */
    eRtp_Bool decodeReportBlock(IN RtpDt_UChar* pcRepBlkBuf);

    /**
     * Performs the encoding of RTCP Report Block
     */
    eRtp_Bool formReportBlock(OUT RtpBuffer* pobjRtcpPktBuf);

};  // end of RtcpReportBlock

#endif  //__RTCP_REPORT_BLOCK_H__

/** @}*/
