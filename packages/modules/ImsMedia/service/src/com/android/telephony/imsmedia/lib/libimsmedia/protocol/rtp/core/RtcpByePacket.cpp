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

#include <RtcpByePacket.h>
#include <RtpTrace.h>

RtcpByePacket::RtcpByePacket() :
        m_uiSsrcList(std::list<RtpDt_UInt32*>()),
        m_pReason(nullptr)
{
}  // Constructor

RtcpByePacket::~RtcpByePacket()
{
    // delete all ssrc objects
    for (const auto& puiSsrc : m_uiSsrcList)
    {
        delete puiSsrc;
    }
    m_uiSsrcList.clear();

    if (m_pReason != nullptr)
    {
        delete m_pReason;
        m_pReason = nullptr;
    }
}  // Destructor

RtpDt_Void RtcpByePacket::setRtcpHdrInfo(RtcpHeader& objRtcpHeader)
{
    m_objRtcpHdr = objRtcpHeader;
}

RtcpHeader* RtcpByePacket::getRtcpHdrInfo()
{
    return &m_objRtcpHdr;
}

std::list<RtpDt_UInt32*>& RtcpByePacket::getSsrcList()
{
    return m_uiSsrcList;
}

RtpBuffer* RtcpByePacket::getReason()
{
    return m_pReason;
}

RtpDt_Void RtcpByePacket::setReason(IN RtpBuffer* pobjReason)
{
    m_pReason = pobjReason;
}

eRTP_STATUS_CODE RtcpByePacket::decodeByePacket(IN RtpDt_UChar* pucByeBuf, IN RtpDt_UInt16 usByeLen)
{
    RtpDt_UChar ucSsrcCnt = m_objRtcpHdr.getReceptionReportCount();
    // m_uiSsrcList
    while (ucSsrcCnt > RTP_ONE && usByeLen >= RTP_WORD_SIZE)
    {
        RtpDt_UInt32* puiRcvdSsrc = nullptr;
        puiRcvdSsrc = new RtpDt_UInt32();
        if (puiRcvdSsrc == nullptr)
        {
            RTP_TRACE_ERROR("[Memory Error] new returned NULL.", RTP_ZERO, RTP_ZERO);
            return RTP_MEMORY_FAIL;
        }

        (*puiRcvdSsrc) = RtpOsUtil::Ntohl(*(reinterpret_cast<RtpDt_UInt32*>(pucByeBuf)));
        pucByeBuf = pucByeBuf + RTP_WORD_SIZE;

        m_uiSsrcList.push_back(puiRcvdSsrc);
        ucSsrcCnt = ucSsrcCnt - RTP_ONE;
        usByeLen -= RTP_WORD_SIZE;
    }  // while

    if (usByeLen >= RTP_ONE)  // check if optional length is present.
    {
        // m_pReason
        RtpDt_UInt32 uiByte4Data = RtpOsUtil::Ntohl(*(reinterpret_cast<RtpDt_UInt32*>(pucByeBuf)));
        pucByeBuf = pucByeBuf + RTP_ONE;
        uiByte4Data = uiByte4Data >> RTP_24;  // length of "Reason for leaving"
        if (uiByte4Data > RTP_ZERO)
        {
            RtpDt_UChar* pucReason = new RtpDt_UChar[uiByte4Data];
            if (pucReason == nullptr)
            {
                RTP_TRACE_ERROR("[Memory Error] new returned NULL.", RTP_ZERO, RTP_ZERO);
                return RTP_MEMORY_FAIL;
            }

            m_pReason = new RtpBuffer();
            if (m_pReason == nullptr)
            {
                RTP_TRACE_ERROR("[Memory Error] new returned NULL.", RTP_ZERO, RTP_ZERO);
                delete[] pucReason;
                return RTP_MEMORY_FAIL;
            }
            memset(pucReason, RTP_ZERO, uiByte4Data);
            memcpy(pucReason, pucByeBuf, uiByte4Data);
            m_pReason->setBufferInfo(uiByte4Data, pucReason);
        }  // if
    }
    return RTP_SUCCESS;
}  // decodeByePacket

eRTP_STATUS_CODE RtcpByePacket::formByePacket(OUT RtpBuffer* pobjRtcpPktBuf)
{
    RtpDt_UInt32 uiCurPos = pobjRtcpPktBuf->getLength();
    RtpDt_UChar* pucBuffer = pobjRtcpPktBuf->getBuffer();

    uiCurPos = uiCurPos + RTCP_FIXED_HDR_LEN;
    pucBuffer = pucBuffer + uiCurPos;

    for (auto& puiSsrc : m_uiSsrcList)
    {
        // ssrc
        *(reinterpret_cast<RtpDt_UInt32*>(pucBuffer)) = RtpOsUtil::Ntohl(*puiSsrc);
        pucBuffer = pucBuffer + RTP_WORD_SIZE;
        uiCurPos = uiCurPos + RTP_WORD_SIZE;
    }

    // m_pReason
    if (m_pReason != nullptr)
    {
        // length
        *(reinterpret_cast<RtpDt_UChar*>(pucBuffer)) = (RtpDt_UChar)m_pReason->getLength();
        pucBuffer = pucBuffer + RTP_ONE;
        uiCurPos = uiCurPos + RTP_ONE;

        memcpy(pucBuffer, m_pReason->getBuffer(), m_pReason->getLength());
        uiCurPos = uiCurPos + m_pReason->getLength();
    }

    // padding
    {
        RtpDt_UInt32 uiByePktPos = pobjRtcpPktBuf->getLength();
        RtpDt_UInt32 uiByePktLen = uiCurPos - uiByePktPos;

#ifdef ENABLE_PADDING
        RtpDt_UInt32 uiPadLen = uiByePktLen % RTP_WORD_SIZE;
        if (uiPadLen > RTP_ZERO)
        {
            uiPadLen = RTP_WORD_SIZE - uiPadLen;
            uiByePktLen = uiByePktLen + uiPadLen;
            uiCurPos = uiCurPos + uiPadLen;
            pucBuffer = pucBuffer + m_pReason->getLength();
            memset(pucBuffer, RTP_ZERO, uiPadLen);

            pucBuffer = pucBuffer + uiPadLen;
            pucBuffer = pucBuffer - RTP_ONE;
            *(reinterpret_cast<RtpDt_UChar*>(pucBuffer)) = (RtpDt_UChar)uiPadLen;

            // set pad bit in header
            m_objRtcpHdr.setPadding();
            // set length in header
            m_objRtcpHdr.setLength(uiByePktLen);
        }
        else
#endif
        {
            // set length in header
            m_objRtcpHdr.setLength(uiByePktLen);
        }

        pobjRtcpPktBuf->setLength(uiByePktPos);
        m_objRtcpHdr.formRtcpHeader(pobjRtcpPktBuf);
    }  // padding

    // set the current position of the RTCP compound packet
    pobjRtcpPktBuf->setLength(uiCurPos);

    return RTP_SUCCESS;
}  // formByePacket
