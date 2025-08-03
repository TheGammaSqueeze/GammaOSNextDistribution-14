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

#include <RtcpRrPacket.h>
#include <RtcpReportBlock.h>
#include <RtpTrace.h>

RtcpRrPacket::RtcpRrPacket() :
        m_objReportBlkList(std::list<RtcpReportBlock*>()),
        m_pobjExt(nullptr)
{
}

RtcpRrPacket::~RtcpRrPacket()
{
    // delete all RtcpReportBlock objects
    for (const auto& pobjReptBlk : m_objReportBlkList)
    {
        delete pobjReptBlk;
    }
    m_objReportBlkList.clear();

    if (m_pobjExt != nullptr)
    {
        delete m_pobjExt;
        m_pobjExt = nullptr;
    }
}

RtpDt_Void RtcpRrPacket::addReportBlkElm(IN RtcpReportBlock* pobjReptBlk)
{
    m_objReportBlkList.push_back(pobjReptBlk);
}

RtpDt_Void RtcpRrPacket::setRtcpHdrInfo(RtcpHeader& objRtcpHdr)
{
    m_objRtcpHdr = objRtcpHdr;
}

RtcpHeader* RtcpRrPacket::getRtcpHdrInfo()
{
    return &m_objRtcpHdr;
}

std::list<RtcpReportBlock*>& RtcpRrPacket::getReportBlockList()
{
    return m_objReportBlkList;
}

RtpBuffer* RtcpRrPacket::getExtHdrInfo()
{
    return m_pobjExt;
}

RtpDt_Void RtcpRrPacket::setExtHdrInfo(IN RtpBuffer* pobjExtHdr)
{
    m_pobjExt = pobjExtHdr;
}

eRTP_STATUS_CODE RtcpRrPacket::decodeRrPacket(
        IN RtpDt_UChar* pucRrBuf, IN RtpDt_UInt16& usRrLen, IN RtpDt_UInt16 usProfExtLen)
{
    RtpDt_UInt16 usRepBlkLen = usRrLen - usProfExtLen;
    while (usRepBlkLen >= RTP_24)
    {
        RtcpReportBlock* pobjRptBlk = new RtcpReportBlock();
        if (pobjRptBlk == nullptr)
        {
            RTP_TRACE_ERROR("[Memory Error] new returned NULL.", RTP_ZERO, RTP_ZERO);
            return RTP_MEMORY_FAIL;
        }
        pobjRptBlk->decodeReportBlock(pucRrBuf);
        pucRrBuf = pucRrBuf + RTP_24;
        usRepBlkLen = usRepBlkLen - RTP_24;
        addReportBlkElm(pobjRptBlk);
    }

    // profile specific extensions
    if (usProfExtLen > RTP_ZERO)
    {
        RtpDt_UChar* pcProfExtBuf = new RtpDt_UChar[usProfExtLen];
        if (pcProfExtBuf == nullptr)
        {
            RTP_TRACE_ERROR("[Memory Error] new returned NULL.", RTP_ZERO, RTP_ZERO);
            return RTP_MEMORY_FAIL;
        }

        m_pobjExt = new RtpBuffer();
        if (m_pobjExt == nullptr)
        {
            RTP_TRACE_ERROR("[Memory Error] new returned NULL.", RTP_ZERO, RTP_ZERO);
            delete[] pcProfExtBuf;
            return RTP_MEMORY_FAIL;
        }

        memcpy(pcProfExtBuf, pucRrBuf, usProfExtLen);
        m_pobjExt->setBufferInfo(usProfExtLen, pcProfExtBuf);
    }

    return RTP_SUCCESS;
}  // decodeRrPacket

eRTP_STATUS_CODE RtcpRrPacket::formRrPacket(OUT RtpBuffer* pobjRtcpPktBuf, IN eRtp_Bool bHdrInfo)
{
    RTP_TRACE_MESSAGE("formRrPacket", 0, 0);
    RtpDt_UInt32 uiRtPktPos = pobjRtcpPktBuf->getLength();

    if (bHdrInfo == RTP_TRUE)
    {
        RtpDt_UInt32 uiRepBlkPos = uiRtPktPos + RTP_EIGHT;
        pobjRtcpPktBuf->setLength(uiRepBlkPos);
    }

    // m_objReportBlkList
    for (auto& pobjRepBlk : m_objReportBlkList)
    {
        pobjRepBlk->formReportBlock(pobjRtcpPktBuf);
    }  // for

    RtpDt_UInt32 uiCurPos = pobjRtcpPktBuf->getLength();
#ifdef ENABLE_RTCPEXT
    if (m_pobjExt != nullptr)
    {
        RtpDt_UChar* pucExtHdr = m_pobjExt->getBuffer();
        RtpDt_UInt32 uiExtHdrLen = m_pobjExt->getLength();
        RtpDt_UChar* pucBuffer = pobjRtcpPktBuf->getBuffer();
        memcpy(pucBuffer + uiCurPos, pucExtHdr, uiExtHdrLen);
        uiCurPos = uiCurPos + uiExtHdrLen;
        pobjRtcpPktBuf->setLength(uiCurPos);
    }  // extension header
#endif
    if (bHdrInfo == RTP_TRUE)
    {
        RtpDt_UInt32 uiRrPktLen = uiCurPos - uiRtPktPos;

#ifdef ENABLE_PADDING
        RtpDt_UInt32 uiPadLen = uiRrPktLen % RTP_WORD_SIZE;
        if (uiPadLen > RTP_ZERO)
        {
            uiPadLen = RTP_WORD_SIZE - uiPadLen;
            uiRrPktLen = uiRrPktLen + uiPadLen;
            uiCurPos = uiCurPos + uiPadLen;
            RtpDt_UChar* pucBuffer = pobjRtcpPktBuf->getBuffer();
            pucBuffer = pucBuffer + uiCurPos;
            memset(pucBuffer, RTP_ZERO, uiPadLen);

            pucBuffer = pucBuffer + uiPadLen;
            pucBuffer = pucBuffer - RTP_ONE;
            *(reinterpret_cast<RtpDt_UChar*>(pucBuffer)) = (RtpDt_UChar)uiPadLen;

            // set pad bit in header
            m_objRtcpHdr.setPadding();
            // set length in header
            m_objRtcpHdr.setLength(uiRrPktLen);
        }
        else
#endif
        {
            // set length in header
            m_objRtcpHdr.setLength(uiRrPktLen);
        }

        pobjRtcpPktBuf->setLength(uiRtPktPos);
        m_objRtcpHdr.formRtcpHeader(pobjRtcpPktBuf);
    }
    // set the actual position of the RTCP compound packet
    pobjRtcpPktBuf->setLength(uiCurPos);

    return RTP_SUCCESS;
}  // formRrPacket
