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

#include <RtcpSdesPacket.h>
#include <RtpTrace.h>

RtcpSdesPacket::RtcpSdesPacket() :
        m_objSdesChunkList(std::list<RtcpChunk*>())
{
}

RtcpSdesPacket::~RtcpSdesPacket()
{
    // delete all RtcpChunk objects
    for (const auto& pobjSdesChunk : m_objSdesChunkList)
    {
        delete pobjSdesChunk;
    }
    m_objSdesChunkList.clear();
}

RtpDt_Void RtcpSdesPacket::setRtcpHdrInfo(RtcpHeader& rtcpHeader)
{
    m_objRtcpHdr = rtcpHeader;
}

RtcpHeader* RtcpSdesPacket::getRtcpHdrInfo()
{
    return &m_objRtcpHdr;
}

std::list<RtcpChunk*>& RtcpSdesPacket::getSdesChunkList()
{
    return m_objSdesChunkList;
}

eRTP_STATUS_CODE RtcpSdesPacket::decodeSdesPacket(
        IN RtpDt_UChar* pucSdesBuf, IN RtpDt_UInt16 usSdesLen, IN RtcpConfigInfo* pobjRtcpCfgInfo)
{
    RtpDt_UChar unSourceCount = m_objRtcpHdr.getReceptionReportCount();
    while ((unSourceCount > RTP_ZERO) && (usSdesLen > RTP_ZERO))
    {
        RtcpChunk* pobjRtcpChunk = new RtcpChunk();
        if (pobjRtcpChunk == nullptr)
        {
            RTP_TRACE_ERROR("[Memory Error] new returned NULL.", RTP_ZERO, RTP_ZERO);
            return RTP_MEMORY_FAIL;
        }

        RtpDt_UInt16 usChunkSize = RTP_ZERO;
        eRTP_STATUS_CODE eChunkStatus = RTP_FAILURE;

        eChunkStatus = pobjRtcpChunk->decodeRtcpChunk(pucSdesBuf, usChunkSize, pobjRtcpCfgInfo);
        m_objSdesChunkList.push_back(pobjRtcpChunk);
        if (eChunkStatus != RTP_SUCCESS)
        {
            return eChunkStatus;
        }

        RtpDt_UInt32 uiPadLen = RTP_ZERO;
        uiPadLen = usChunkSize % RTP_WORD_SIZE;

        if (uiPadLen != RTP_ZERO)
        {
            uiPadLen = RTP_WORD_SIZE - uiPadLen;
            usChunkSize += uiPadLen;
        }
        pucSdesBuf += usChunkSize;
        usSdesLen -= usChunkSize;
        unSourceCount--;
    }

    return RTP_SUCCESS;
}  // decodeSdesPacket

eRTP_STATUS_CODE RtcpSdesPacket::formSdesPacket(OUT RtpBuffer* pobjRtcpPktBuf)
{
    RtpDt_UInt32 uiSdesPktPos = pobjRtcpPktBuf->getLength();

    RtpDt_UInt32 uiCurPos = uiSdesPktPos;
    uiCurPos = uiCurPos + RTP_WORD_SIZE;

    // SDES packet does not have SSRC in header.
    pobjRtcpPktBuf->setLength(uiCurPos);

    // m_objSdesChunkList
    for (auto& pobjRtcpChunk : m_objSdesChunkList)
    {
        eRTP_STATUS_CODE eChunkStatus = RTP_FAILURE;

        eChunkStatus = pobjRtcpChunk->formRtcpChunk(pobjRtcpPktBuf);

        if (eChunkStatus != RTP_SUCCESS)
        {
            return eChunkStatus;
        }

        RtpDt_UInt32 uiSdesPktLen = RTP_ZERO;

        uiCurPos = pobjRtcpPktBuf->getLength();
        uiSdesPktLen = uiCurPos - uiSdesPktPos;
#ifdef ENABLE_PADDING
        RtpDt_UChar* pucBuffer = pobjRtcpPktBuf->getBuffer();
        pucBuffer = pucBuffer + uiCurPos;
        RtpDt_UInt32 uiPadLen = uiSdesPktLen % RTP_WORD_SIZE;

        if (uiPadLen > RTP_ZERO)
        {
            uiPadLen = RTP_WORD_SIZE - uiPadLen;
            uiSdesPktLen = uiSdesPktLen + uiPadLen;
            uiCurPos = uiCurPos + uiPadLen;
            memset(pucBuffer, RTP_ZERO, uiPadLen);
        }
#endif
        m_objRtcpHdr.setLength(uiSdesPktLen);
    }  // for

    pobjRtcpPktBuf->setLength(uiSdesPktPos);
    m_objRtcpHdr.formPartialRtcpHeader(pobjRtcpPktBuf);

    RTP_TRACE_MESSAGE(
            "formSdesPacket, [SDES packet length] : %d]", m_objRtcpHdr.getLength(), nullptr);

    // set the actual position of the RTCP compound packet
    pobjRtcpPktBuf->setLength(uiCurPos);

    return RTP_SUCCESS;
}  // formSdesPacket
