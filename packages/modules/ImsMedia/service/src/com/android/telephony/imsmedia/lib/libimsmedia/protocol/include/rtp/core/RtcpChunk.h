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

#ifndef __RTCP_CHUNK_H__
#define __RTCP_CHUNK_H__

#include <RtpGlobal.h>
#include <RtcpConfigInfo.h>
#include <RtpBuffer.h>
#include <list>

/**
 * @class RtcpChunk
 *
 * It holds RTCP Chunk information
 */
class RtcpChunk
{
private:
    RtpDt_UInt32 m_uiSsrc;
    std::list<tRTCP_SDES_ITEM*> m_stSdesItemList;

public:
    RtcpChunk();
    ~RtcpChunk();

    /**
     * set method for m_uiSsrc
     */
    RtpDt_Void setSsrc(IN RtpDt_UInt32 uiSsrc);

    /**
     * get method for m_uiSsrc
     */
    RtpDt_UInt32 getSsrc();

    /**
     * get method for m_stSdesItemList
     */
    std::list<tRTCP_SDES_ITEM*>& getSdesItemList();

    /**
     * Decodes and stores the information of the RTCP CHUNKS
     * This function does not allocate memory required for decoding.
     * @param pucChunkBuf Received RTCP chunk buffer
     * @param usChunkLen Length of the RTCP chunk
     * @param pobjRtcpCfgInfo RTCP configuration information
     */
    eRTP_STATUS_CODE decodeRtcpChunk(IN RtpDt_UChar* pucChunkBuf, IN RtpDt_UInt16& usChunkLen,
            IN RtcpConfigInfo* pobjRtcpCfgInfo);

    /**
     * Performs the encoding of the RTCP CHUNKS.
     * This function does not allocate memory required for encoding.
     * @param pobjRtcpPktBuf Memory for the buffer is pre-allocated by caller
     * @param uiStartPos from which RTCP CHUNKS shall be encoded.
     */
    eRTP_STATUS_CODE formRtcpChunk(OUT RtpBuffer* pobjRtcpPktBuf);

};  // end of RtcpChunk

#endif  //__RTCP_CHUNK_H__
/** @}*/
