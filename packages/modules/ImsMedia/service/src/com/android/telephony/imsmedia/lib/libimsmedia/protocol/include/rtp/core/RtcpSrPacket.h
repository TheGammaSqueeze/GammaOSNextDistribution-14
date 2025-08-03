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

#ifndef __RTCP_SR_PACKET_H__
#define __RTCP_SR_PACKET_H__

#include <RtpGlobal.h>
#include <RtcpRrPacket.h>

/**
 * @class    RtcpSrPacket
 * @brief    It holds SR packet information
 */
class RtcpSrPacket
{
private:
    /**
     * It holds the RR packet information
     */
    RtcpRrPacket m_objRrPkt;

    /**
     * NTP timestamp, most significant word
     * NTP timestamp, least significant word
     */
    tRTP_NTP_TIME m_stNtpTimestamp;

    // Rtp time stamp
    RtpDt_UInt32 m_uiRtpTimestamp;

    // Sender's packet count
    RtpDt_UInt32 m_uiSendPktCount;

    // Sender's octet count
    RtpDt_UInt32 m_uiSendOctCount;

public:
    // Constructor
    RtcpSrPacket();

    // Destructor
    ~RtcpSrPacket();

    /**
     * Set RTCP header information.
     */
    RtpDt_Void setRtcpHdrInfo(RtcpHeader& rtcpHeader);

    /**
     * Get RTCP header information.
     */
    RtcpHeader* getRtcpHdrInfo();

    /**
     * get method for m_objRrPkt
     */
    RtcpRrPacket* getRrPktInfo();

    /**
     * get method for m_stNtpTimestamp
     */
    tRTP_NTP_TIME* getNtpTime();

    /**
     * set method for m_uiRtpTimestamp
     */
    RtpDt_Void setRtpTimestamp(IN RtpDt_UInt32 uiRtpTimestamp);

    /**
     *  get method for m_uiRtpTimestamp
     */
    RtpDt_UInt32 getRtpTimestamp();

    /**
     * set method for m_uiSendPktCount
     */
    RtpDt_Void setSendPktCount(IN RtpDt_UInt32 uiPktCount);

    /**
     * get method for m_uiSendPktCount
     */
    RtpDt_UInt32 getSendPktCount();

    /**
     * set method for m_uiSendOctCount
     */
    RtpDt_Void setSendOctetCount(IN RtpDt_UInt32 uiOctetCount);

    /**
     * get method for m_uiSendOctCount
     */
    RtpDt_UInt32 getSendOctetCount();

    /**
     * Decodes and stores the information of the RTCP SR packet
     * This function does not allocate memory required for decoding
     * @param[in] pucSrPktBuf raw buffer for RTCP SR packet
     * @param[in] usSrPktLen length of the RTCP SR packet
     * @param[in] usExtHdrLen profile extension header length
     * @return RTP_SUCCESS on successful decoding
     */
    eRTP_STATUS_CODE decodeSrPacket(
            IN RtpDt_UChar* pucSrPktBuf, IN RtpDt_UInt16 usSrPktLen, IN RtpDt_UInt16 usExtHdrLen);

    /**
     * Performs the encoding of the RTCP SR packet.
     * This function does not allocate memory required for encoding.
     * @param[out] pobjRtcpPktBuf Memory for the buffer is pre-allocated by caller
     * @return RTP_SUCCESS on successful encoding
     */
    eRTP_STATUS_CODE formSrPacket(OUT RtpBuffer* pobjRtcpPktBuf);

};  // end of RtcpSrPacket

#endif  //__RTCP_SR_PACKET_H__

/** @}*/
