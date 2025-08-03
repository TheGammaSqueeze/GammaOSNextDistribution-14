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

#ifndef __RTP_RECEIVER_INFO_H__
#define __RTP_RECEIVER_INFO_H__

#include <RtpGlobal.h>
#include <RtcpReportBlock.h>

#define RTP_SEQ_MOD        (RTP_ONE << RTP_16)
#define RTP_MAX_DROPOUT    3000
#define RTP_MAX_MISORDER   100
#define RTP_MIN_SEQUENTIAL 0

/**
 * @class   RtpReceiverInfo
 * @brief   It maintains the receiver list for RTP session.
 * This class stores the content of a RTP packet.
 * It can encode and decode a RTP packet based on the information it has.
 */
class RtpReceiverInfo
{
    // SSRC of the source
    RtpDt_UInt32 m_uiSsrc;

    // Status of this SSRC as a sender or receiver
    eRtp_Bool m_bSender;

    // number of Received RTP packets
    RtpDt_UInt32 m_uiTotalRcvdRtpPkts;

    // number of Received octets
    RtpDt_UInt32 m_uiTotalRcvdRtpOcts;

    // IPaddr of this ssrc
    RtpBuffer* m_pobjIpAddr;

    // Port of this ssrc
    RtpDt_UInt16 m_usPort;

    // remote ssrc information
    tRTP_SOURCE m_stRtpSource;

    /** enables m_bIsCsrcFlag if the entry is creted @
        processing of CSRC list in RTP processing */
    eRtp_Bool m_bIsCsrcFlag;

    // Previous Ntp Timestamp
    tRTP_NTP_TIME m_stPrevNtpTimestamp;

    // Previous RTP timestamp
    RtpDt_UInt32 m_prevRtpTimestamp;

    /**
     * The middle 32 bits out of 64 in the NTP timestamp
     * received as part of the most recent RTCP sender report
     * (SR) packet.
     */
    // tRTP_NTP_TIME m_stPreSrTimestamp;
    RtpDt_UInt32 m_stPreSrTimestamp;

    // previous SR NtpTimestamp
    RtpDt_UInt32 m_stLastSrNtpTimestamp;

    // check for first RTP packet
    eRtp_Bool m_bIsFirstRtp;

    // It calculates the fraction Lost
    RtpDt_UInt16 fractionLost();

    /**
     * It determines the number of lost packets after rtcp timer expiry.
     */
    RtpDt_UInt32 findLostRtpPkts();

    /**
     * It calculates delay since last SR
     */
    RtpDt_UInt32 delaySinceLastSR();

public:
    RtpReceiverInfo();

    ~RtpReceiverInfo();

    RtpDt_UInt32 getExtSeqNum();

    eRtp_Bool getCsrcFlag();

    RtpDt_Void setCsrcFlag(IN eRtp_Bool bIsCsrcFlag);

    RtpDt_UInt32 getSsrc();

    RtpDt_Void setSsrc(IN RtpDt_UInt32 uiSsrc);

    eRtp_Bool isSender();

    RtpDt_Void setSenderFlag(IN eRtp_Bool bSender);

    RtpDt_UInt32 getTotalRcvdRtpPkts();

    RtpDt_Void incrTotalRcvdRtpPkts();

    RtpDt_Void incrTotalRcvdRtpOcts(IN RtpDt_UInt32 uiRcvdOcts);

    RtpBuffer* getIpAddr();

    eRTP_STATUS_CODE setIpAddr(IN RtpBuffer* pobjIpAddr);

    RtpDt_UInt16 getPort();

    RtpDt_Void setPort(IN RtpDt_UInt16 usPort);

    RtpDt_UInt32 updateSeq(IN RtpDt_UInt16 usSeq);

    RtpDt_Void initSeq(IN RtpDt_UInt16 usSeq);

    tRTP_NTP_TIME* getpreSrTimestamp();

    RtpDt_Void setpreSrTimestamp(IN tRTP_NTP_TIME* pstNtpTs);

    RtpDt_Void setLastSrNtpTimestamp(IN tRTP_NTP_TIME* pstNtpTs);

    RtpDt_Void setprevRtpTimestamp(IN RtpDt_UInt32 pstRtpTs);

    RtpDt_Void setprevNtpTimestamp(IN tRTP_NTP_TIME* pstNtpTs);

    /**
     * It populates Report Block information
     */
    eRTP_STATUS_CODE populateReportBlock(IN RtcpReportBlock* pobjRepBlk);

    /**
     * It calculates the inter arrival jitter after receiving the RTP packet
     */
    RtpDt_Void calcJitter(IN RtpDt_UInt32 uiRcvRtpTs, IN RtpDt_UInt32 uiSamplingRate);
};

#endif  //__RTP_RECEIVER_INFO_H__

/** @}*/
