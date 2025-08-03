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

#ifndef __RTCP_SDES_PACKET_H__
#define __RTCP_SDES_PACKET_H__

#include <RtpGlobal.h>
#include <RtcpHeader.h>
#include <RtpBuffer.h>
#include <RtcpConfigInfo.h>
#include <RtcpChunk.h>

/**
 * @class    RtcpSdesPacket
 * @brief    It holds sdes packet information
 */
class RtcpSdesPacket
{
private:
    // Rtcp header. m_uiSsrc member of m_objRtcpHdr is not applicable to SDES packet.
    RtcpHeader m_objRtcpHdr;

    // List of SDES chunks (RtcpChunk)
    std::list<RtcpChunk*> m_objSdesChunkList;

public:
    RtcpSdesPacket();

    ~RtcpSdesPacket();

    /**
     * get method for m_objRtcpHdr
     */
    RtcpHeader* getRtcpHdrInfo();

    /**
     * Set RTCP header Information.
     */
    RtpDt_Void setRtcpHdrInfo(RtcpHeader& rtcpHeader);

    /**
     * get method for m_objSdesChunkList
     */
    std::list<RtcpChunk*>& getSdesChunkList();

    /**
     * Decodes and stores the information of the RTCP SDES packet
     * This function does not allocate memory required for decoding.
     *
     * @param[in] pucSdesBuf RTCP SDES packet buffer
     * @param[in] usSdesLen length of the SDES RTCP packet
     * @return RTP_SUCCESS on successful decoding
     */
    eRTP_STATUS_CODE decodeSdesPacket(IN RtpDt_UChar* pucSdesBuf, IN RtpDt_UInt16 usSdesLen,
            IN RtcpConfigInfo* pobjRtcpCfgInfo);

    /**
     * Performs the encoding of the RTCP SDES packet.
     * This function does not allocate memory required for encoding.
     *
     * @param[out] pobjRtcpPktBuf Memory for the buffer is pre-allocated by caller
     *
     * @return RTP_SUCCESS on successful encoding
     */
    eRTP_STATUS_CODE formSdesPacket(OUT RtpBuffer* pobjRtcpPktBuf);

};  // end of RtcpSdesPacket

#endif  //__RTCP_SDES_PACKET_H__

/** @}*/
