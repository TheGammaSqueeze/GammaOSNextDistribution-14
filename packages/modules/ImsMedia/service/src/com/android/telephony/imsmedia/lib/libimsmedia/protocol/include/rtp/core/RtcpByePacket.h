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

#ifndef __RTCP_BYE_PACKET_H__
#define __RTCP_BYE_PACKET_H__

#include <RtpGlobal.h>
#include <RtpBuffer.h>
#include <RtcpHeader.h>
#include <list>

/**
 * @class    RtcpByePacket
 * @brief    It holds RTCP BYE information. Provides rtcp BYE packet packing and parsing function.
 */
class RtcpByePacket
{
private:
    RtcpHeader m_objRtcpHdr;
    std::list<RtpDt_UInt32*> m_uiSsrcList;
    RtpBuffer* m_pReason;

public:
    RtcpByePacket();
    ~RtcpByePacket();

    /**
     *  Set method for m_objRtcpHdr
     */
    RtpDt_Void setRtcpHdrInfo(RtcpHeader& objRtcpHeader);

    /**
     *  Get method for m_objRtcpHdr
     */
    RtcpHeader* getRtcpHdrInfo();

    /**
     * Get method for m_uiSsrcList
     */
    std::list<RtpDt_UInt32*>& getSsrcList();

    /**
     * Get method for m_pReason
     */
    RtpBuffer* getReason();

    /**
     * Set method for m_pAppData
     */
    RtpDt_Void setReason(IN RtpBuffer* pobjReason);

    /**
     * Decodes and stores the information of the RTCP BYE packet
     * This function does not allocate memory required for decoding.
     * @param pucByeBuf received RTCP BYE packet
     * @param usByeLen length of the RTCP BYE packet
     */
    eRTP_STATUS_CODE decodeByePacket(IN RtpDt_UChar* pucByeBuf, IN RtpDt_UInt16 usByeLen);

    /**
     * Performs the encoding of the RTCP BYE packet.
     * This function does not allocate memory required for encoding.
     * @param pobjRtcpPktBuf Memory for the buffer is pre-allocated by caller
     */
    eRTP_STATUS_CODE formByePacket(OUT RtpBuffer* pobjRtcpPktBuf);

};  // end of RtcpByePacket

#endif  //__RTCP_BYE_PACKET_H__

/** @}*/
