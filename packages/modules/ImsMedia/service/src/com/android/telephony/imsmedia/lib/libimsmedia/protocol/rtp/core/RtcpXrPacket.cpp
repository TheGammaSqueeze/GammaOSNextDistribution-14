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

#include <RtcpXrPacket.h>
#include <RtpTrace.h>
#include <RtpSession.h>

RtcpXrPacket::RtcpXrPacket() :
        m_reportBlk(nullptr)
{
}

RtcpXrPacket::~RtcpXrPacket()
{
    if (m_reportBlk)
    {
        delete (m_reportBlk);
        m_reportBlk = nullptr;
    }
}

RtcpHeader* RtcpXrPacket::getRtcpHdrInfo()
{
    return &m_objRtcpHdr;
}

RtpDt_Void RtcpXrPacket::setRtcpHdrInfo(RtcpHeader& header)
{
    m_objRtcpHdr = header;
}

RtpBuffer* RtcpXrPacket::getReportBlk()
{
    return m_reportBlk;
}

RtpDt_Void RtcpXrPacket::setReportBlk(IN RtpBuffer* reportBlk)
{
    m_reportBlk = reportBlk;
}

eRTP_STATUS_CODE RtcpXrPacket::decodeRtcpXrPacket(
        IN RtpDt_UChar* pucRtcpXrBuf, IN RtpDt_UInt16 usRtcpXrLen, IN RtpDt_UChar ucPktType)
{
    (RtpDt_Void) pucRtcpXrBuf;
    (RtpDt_Void) usRtcpXrLen;
    (RtpDt_Void) ucPktType;
    RTP_TRACE_WARNING("decodeRtcpXrPacket not implemented.", RTP_ZERO, RTP_ZERO);

    /* TODO: Currently, there is no requirement to handle XR packets. Returning success to avoid
    RTCP decoding issues. */
    return RTP_SUCCESS;
}

eRTP_STATUS_CODE RtcpXrPacket::formRtcpXrPacket(OUT RtpBuffer* pobjRtcpPktBuf)
{
    RtpDt_UInt32 uiXrPktPos = pobjRtcpPktBuf->getLength();
    RtpDt_UInt32 uiCurPos = pobjRtcpPktBuf->getLength();
    RtpDt_UChar* pucBuffer = pobjRtcpPktBuf->getBuffer();

    if (!pucBuffer)
    {
        RTP_TRACE_ERROR("formXrPacket with null buffer", RTP_ZERO, RTP_ZERO);
        return RTP_FAILURE;
    }

    uiCurPos = uiCurPos + RTCP_FIXED_HDR_LEN;
    pucBuffer = pucBuffer + uiCurPos;

    // set the report block buffer
    RtpBuffer* pReportBlk = this->getReportBlk();
    memcpy(pucBuffer, pReportBlk->getBuffer(), pReportBlk->getLength());

    uiCurPos = uiCurPos + pReportBlk->getLength();

    // padding
    RtpDt_UInt32 uiXrPktLen = uiCurPos - uiXrPktPos;
#ifdef ENABLE_PADDING
    RtpDt_UInt32 uiPadLen = RTP_ZERO;
    uiPadLen = uiXrPktLen % RTP_WORD_SIZE;
    if (uiPadLen > RTP_ZERO)
    {
        uiPadLen = RTP_WORD_SIZE - uiPadLen;
        uiXrPktLen = uiXrPktLen + uiPadLen;
        uiCurPos = uiCurPos + uiPadLen;
        pucBuffer = pucBuffer + pReportBlk->getLength();
        memset(pucBuffer, RTP_ZERO, uiPadLen);

        pucBuffer = pucBuffer + uiPadLen;
        pucBuffer = pucBuffer - RTP_ONE;
        *(reinterpret_cast<RtpDt_UChar*>(pucBuffer)) = (RtpDt_UChar)uiPadLen;

        // set pad bit in header
        m_objRtcpHdr.setPadding();
        // set length in header
        m_objRtcpHdr.setLength(uiXrPktLen);
    }
    else
#endif
    {
        // set length in header
        m_objRtcpHdr.setLength(uiXrPktLen);
    }

    pobjRtcpPktBuf->setLength(uiXrPktPos);
    m_objRtcpHdr.formRtcpHeader(pobjRtcpPktBuf);

    // set the current position of the RTCP compound packet
    pobjRtcpPktBuf->setLength(uiCurPos);

    return RTP_SUCCESS;
}
