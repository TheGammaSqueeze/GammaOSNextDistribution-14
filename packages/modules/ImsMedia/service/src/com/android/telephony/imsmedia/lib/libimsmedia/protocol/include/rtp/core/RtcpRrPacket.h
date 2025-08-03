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

#ifndef __RTCP_RR_PACKET_H__
#define __RTCP_RR_PACKET_H__

#include <RtpGlobal.h>
#include <RtcpHeader.h>
#include <RtpBuffer.h>
#include <RtcpReportBlock.h>
#include <list>

/**
 * @class         rtcp_rr_packet
 * @brief         It holds RR packet information
 */
class RtcpRrPacket
{
private:
    // RTCP header information
    RtcpHeader m_objRtcpHdr;

    // List of RtcpReportBlock objects
    std::list<RtcpReportBlock*> m_objReportBlkList;

    /**
     * Extension header buffer. This is encoded and given by app.
     * After decoding, ExtractExtHeaders, will update this with the extension
     * header buffer
     */
    RtpBuffer* m_pobjExt;

    /**
     * It adds RtcpReportBlock object to m_objReportBlkList
     */
    RtpDt_Void addReportBlkElm(IN RtcpReportBlock* pobjReptBlk);

public:
    // Constructor
    RtcpRrPacket();
    // Destructor
    ~RtcpRrPacket();

    /**
     * Set method for m_objRtcpHdr
     */
    RtpDt_Void setRtcpHdrInfo(RtcpHeader& objRtcpHdr);

    /**
     * Get method for m_objRtcpHdr
     */
    RtcpHeader* getRtcpHdrInfo();

    /**
     * get method for m_objReportBlkList
     */
    std::list<RtcpReportBlock*>& getReportBlockList();

    /**
     * get method for m_pobjExt
     */
    RtpBuffer* getExtHdrInfo();

    /**
     * set method for m_pobjExt
     */
    RtpDt_Void setExtHdrInfo(IN RtpBuffer* pobjExtHdr);

    /**
     * Decodes and stores the information of the RTCP RR packet
     * This function does not allocate memory required for decoding.
     *
     * @param pucRrBuf      Rr packet buffer
     * @param usRrLen       Rr packet length
     * @param usExtHdrLen   RTCP extension header length
     *
     * @return RTP_SUCCESS on successful decoding
     */
    eRTP_STATUS_CODE decodeRrPacket(
            IN RtpDt_UChar* pucRrBuf, IN RtpDt_UInt16& usRrLen, IN RtpDt_UInt16 usProfExtLen);

    /**
     * Performs the encoding of the RTCP RR packet.
     * This function does not allocate memory required for encoding.
     *
     * @param pobjRtcpPktBuf    Memory for the buffer is pre-allocated by caller
     * @param bHdrInfo          tells RTCP header shall be encoded or not.
     *
     * @return RTP_SUCCESS on successful encoding
     */
    eRTP_STATUS_CODE formRrPacket(OUT RtpBuffer* pobjRtcpPktBuf, IN eRtp_Bool bHdrInfo);

};  // end of RtcpRrPacket

#endif  //__RTCP_RR_PACKET_H__

/** @}*/
